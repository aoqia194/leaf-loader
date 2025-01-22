/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.aoqia.loader.impl.game.zomboid.patch;

import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Function;

import net.aoqia.api.EnvType;
import net.aoqia.loader.api.Version;
import net.aoqia.loader.api.VersionParsingException;
import net.aoqia.loader.api.metadata.version.VersionPredicate;
import net.aoqia.loader.impl.game.patch.GamePatch;
import net.aoqia.loader.impl.game.zomboid.Hooks;
import net.aoqia.loader.impl.game.zomboid.ZomboidGameProvider;
import net.aoqia.loader.impl.launch.LeafLauncher;
import net.aoqia.loader.impl.util.log.Log;
import net.aoqia.loader.impl.util.log.LogCategory;
import net.aoqia.loader.impl.util.version.VersionPredicateParser;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public class EntrypointPatch extends GamePatch {
    private final ZomboidGameProvider gameProvider;

    public EntrypointPatch(ZomboidGameProvider gameProvider) {
        this.gameProvider = gameProvider;
    }

    private static VersionPredicate createVersionPredicate(String predicate) {
        try {
            return VersionPredicateParser.parse(predicate);
        } catch (VersionParsingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void process(LeafLauncher launcher,
        Function<String, ClassNode> classSource,
        Consumer<ClassNode> classEmitter) {
        EnvType type = launcher.getEnvironmentType();
        String entrypoint = launcher.getEntrypoint();
        Version gameVersion = getGameVersion();

        if (!entrypoint.startsWith("zombie.")) {
            return;
        }

        ClassNode mainClass = classSource.apply(entrypoint);

        if (mainClass == null) {
            throw new RuntimeException("Could not load main class " + entrypoint + "!");
        }

        // Main -> Game entrypoint search
        // -- CLIENT --
        // 41.78.16 - MainScreenState#main() has a GameWindow.InitGameThread() or RenderThread.init() call.
        // -- SERVER --
        // TODO: Do it

        // Find the main method in MainScreenState.
        MethodNode mainMethod = findMethod(mainClass, (method) -> {
            return method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V") &&
                   isPublicStatic(method.access);
        });

        if (mainMethod == null) {
            throw new RuntimeException("Could not find main method in " + entrypoint + "!");
        }

        AbstractInsnNode gameWindowInitInsn = null;
        // Attempt to find the RenderThread.init() function call and store the instruction.
        for (AbstractInsnNode insn : mainMethod.instructions) {
            if (insn.getOpcode() == Opcodes.INVOKESTATIC && insn instanceof MethodInsnNode) {
                final MethodInsnNode methodInsn = (MethodInsnNode) insn;

                if (methodInsn.name.equals("init") && methodInsn.owner.equals("zombie/core/opengl/RenderThread")) {
                    gameWindowInitInsn = methodInsn;
                }
            }
        }

        if (gameWindowInitInsn == null) {
            throw new RuntimeException("Could not find RenderThread init method in " + entrypoint + "!");
        }

        boolean patched = false;
        Log.debug(LogCategory.GAME_PATCH, "Patching main function %s%s", mainMethod.name, mainMethod.desc);

        // Iterate through main() instructions and find where cacheDir is.
        ListIterator<AbstractInsnNode> consIt = mainMethod.instructions.iterator();
        while (consIt.hasNext()) {
            AbstractInsnNode insn = consIt.next();
            if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) insn).name.equals("getCacheDir")) {
                MethodInsnNode insnNode = ((MethodInsnNode) insn);
                Log.debug(LogCategory.GAME_PATCH, "getCacheDir is found at %s/%s", insnNode.owner, insnNode.name);

                // Add the entrypoint hook just before the RenderThread.init() call.
                moveBefore(consIt, gameWindowInitInsn);

                // Set up ZomboidFileSystem.instance.getCacheDir() as the first arg.
                consIt.add(new FieldInsnNode(Opcodes.GETSTATIC,
                    insnNode.owner,
                    "instance",
                    "Lzombie/ZomboidFileSystem;"));
                consIt.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                    insnNode.owner,
                    insnNode.name,
                    insnNode.desc,
                    false));
                // Set up the class itself as the second arg.
                consIt.add(new LdcInsnNode(Type.getObjectType(entrypoint.replace(".", "/"))));
                // Send to startClient/startServer
                finishEntrypoint(type, consIt);

                patched = true;
                break;
            }
        }

        if (!patched) {
            throw new RuntimeException("Game constructor patch not applied!");
        }

        classEmitter.accept(mainClass);
    }

    private Version getGameVersion() {
        try {
            return Version.parse(gameProvider.getNormalizedGameVersion());
        } catch (VersionParsingException e) {
            throw new RuntimeException(e);
        }
    }

    private void finishEntrypoint(EnvType type, ListIterator<AbstractInsnNode> it) {
        String methodName = String.format("start%s", type == EnvType.CLIENT ? "Client" : "Server");
        it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Hooks.INTERNAL_NAME, methodName,
            "(Ljava/lang/String;Ljava/lang/Class;)V", false));
    }

    private boolean hasSuperClass(String cls, String superCls, Function<String, ClassNode> classSource) {
        if (cls.contains("$") || (!cls.startsWith("zombie") && cls.contains("/"))) {
            return false;
        }

        ClassNode classNode = classSource.apply(cls);

        return classNode != null && classNode.superName.equals(superCls);
    }

    private boolean hasStrInMethod(String cls, String methodName, String methodDesc, String str,
        Function<String, ClassNode> classSource) {
        if (cls.contains("$") || (!cls.startsWith("zombie") && cls.contains("/"))) {
            return false;
        }

        ClassNode node = classSource.apply(cls);
        if (node == null) {
            return false;
        }

        for (MethodNode method : node.methods) {
            if (method.name.equals(methodName) && method.desc.equals(methodDesc)) {
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;

                        if (cst instanceof String) {
                            if (cst.equals(str)) {
                                return true;
                            }
                        }
                    }
                }

                break;
            }
        }

        return false;
    }
}
