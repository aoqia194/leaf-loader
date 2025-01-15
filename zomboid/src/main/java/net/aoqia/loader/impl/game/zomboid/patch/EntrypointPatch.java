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
import org.objectweb.asm.tree.*;

import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

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

    private void finishEntrypoint(EnvType type, ListIterator<AbstractInsnNode> it) {
        String methodName = String.format("start%s", type == EnvType.CLIENT ? "Client" : "Server");
        it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Hooks.INTERNAL_NAME, methodName,
            "(Ljava/io/File;Ljava/lang/Object;)V", false));
    }

    @Override
    public void process(LeafLauncher launcher, Function<String, ClassNode> classSource,
        Consumer<ClassNode> classEmitter) {
        EnvType envType = launcher.getEnvironmentType();
        String entrypoint = launcher.getEntrypoint();
        Version gameVersion = getGameVersion();

        if (!entrypoint.startsWith("zomboid.")) {
            return;
        }
        boolean serverHasFile = true;

        ClassNode mainClass = classSource.apply(entrypoint);
        if (mainClass == null) {
            throw new RuntimeException("Could not load main class " + entrypoint + "!");
        }

        // Find the main() method in MainScreenState or GameServer.
        MethodNode mainMethod = findMethod(mainClass, (method) -> {
            return method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V") &&
                   isPublicStatic(method.access);
        });

        if (mainMethod == null) {
            throw new RuntimeException("Could not find main method in " + entrypoint + "!");
        }

        // Find main init instructuction.
        final Predicate<AbstractInsnNode> pred = (insn) -> {
            return insn.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode) insn).name.equals("<init>") &&
                   hasSuperClass(((MethodInsnNode) insn).owner, mainClass.name, classSource);
        };

        MethodInsnNode mainInsn = (MethodInsnNode) findInsn(mainMethod, pred, true);
        if (mainInsn == null) {
            throw new RuntimeException("Could not find game instruction in " + entrypoint + "!");
        }

        String gameEntrypoint = mainInsn.owner.replace('/', '.');
        serverHasFile = mainInsn.desc.startsWith("(Ljava/io/File;");
        Log.debug(LogCategory.GAME_PATCH, "Found game constructor: %s -> %s", entrypoint, gameEntrypoint);

        ClassNode gameClass;
        if (gameEntrypoint.equals(entrypoint)) {
            gameClass = mainClass;
        } else {
            gameClass = classSource.apply(gameEntrypoint);
            if (gameClass == null) {
                throw new RuntimeException("Could not load game class " + gameEntrypoint + "!");
            }
        }

        MethodNode gameMethod = null;

        gameMethod = findMethod(mainClass, (method) -> {
            return method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V") &&
                   isPublicStatic(method.access);
        });


        if (gameMethod == null) {
            throw new RuntimeException("Could not find game constructor method in " + gameClass.name + "!");
        }

        classEmitter.accept(gameClass);
    }

    private boolean hasSuperClass(String cls, String superCls, Function<String, ClassNode> classSource) {
        if (cls.contains("$") || (!cls.startsWith("net/minecraft") && cls.contains("/"))) {
            return false;
        }

        ClassNode classNode = classSource.apply(cls);

        return classNode != null && classNode.superName.equals(superCls);
    }

    private boolean hasStrInMethod(String cls, String methodName, String methodDesc, String str,
        Function<String, ClassNode> classSource) {
        if (cls.contains("$") || (!cls.startsWith("net/minecraft") && cls.contains("/"))) {
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

    private Version getGameVersion() {
        try {
            return Version.parse(gameProvider.getNormalizedGameVersion());
        } catch (VersionParsingException e) {
            throw new RuntimeException(e);
        }
    }
}