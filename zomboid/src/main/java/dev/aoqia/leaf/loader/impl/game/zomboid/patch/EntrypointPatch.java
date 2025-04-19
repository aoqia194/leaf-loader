/*
 * Copyright 2025 aoqia, FabricMC
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
package dev.aoqia.leaf.loader.impl.game.zomboid.patch;

import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.aoqia.leaf.api.EnvType;
import dev.aoqia.leaf.loader.impl.game.patch.GamePatch;
import dev.aoqia.leaf.loader.impl.game.zomboid.Hooks;
import dev.aoqia.leaf.loader.impl.launch.LeafLauncher;
import dev.aoqia.leaf.loader.impl.launch.LeafLauncherBase;
import dev.aoqia.leaf.loader.impl.util.log.Log;
import dev.aoqia.leaf.loader.impl.util.log.LogCategory;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public class EntrypointPatch extends GamePatch {
    @Override
    public void process(LeafLauncher launcher,
        Function<String, ClassNode> classSource,
        Consumer<ClassNode> classEmitter) {
        EnvType type = launcher.getEnvironmentType();
        String entrypoint = launcher.getEntrypoint();
        if (!entrypoint.startsWith("zombie.")) {
            return;
        }

        ClassNode mainClass = classSource.apply(entrypoint);

        if (mainClass == null) {
            throw new RuntimeException("Could not load main class " + entrypoint + "!");
        }

        // Main game entrypoint search. Hook just under DebugLog.init() call in both enviros.

        // Find the main method in MainScreenState.
        MethodNode mainMethod = findMethod(mainClass, (method) -> {
            return method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V") &&
                   isPublicStatic(method.access);
        });

        if (mainMethod == null) {
            throw new RuntimeException("Could not find main method in " + entrypoint + "!");
        }

        // Attempt to find the DebugLog.init() function call and store the instruction.
        MethodInsnNode debugLogInitInsn = (MethodInsnNode) findInsn(mainMethod, (insn) -> {
            return insn instanceof MethodInsnNode && insn.getOpcode() == Opcodes.INVOKESTATIC &&
                   ((MethodInsnNode) insn).name.equals("init") &&
                   ((MethodInsnNode) insn).owner.equals("zombie/debug/DebugLog");
        }, false);
        if (debugLogInitInsn == null) {
            throw new RuntimeException(
                "Could not find DebugLog init method in " + entrypoint + "!");
        }

        Log.debug(LogCategory.GAME_PATCH, "Patching main function %s%s", mainMethod.name,
            mainMethod.desc);

        ListIterator<AbstractInsnNode> iter = mainMethod.instructions.iterator();
        moveAfter(iter, debugLogInitInsn);
        // Init leaf's logger so that we can not use the builtin logger. Moved from game provider.
        iter.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
            Type.getInternalName(LeafLauncherBase.class), "getLauncher",
            "()L" + Type.getInternalName(LeafLauncher.class) + ";", false));
        iter.add(new InsnNode(Opcodes.ICONST_1));
        iter.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Hooks.INTERNAL_NAME, "setupLogHandler",
            "(L" + Type.getInternalName(LeafLauncher.class) + ";Z)V"));
        // Set up ZomboidFileSystem.instance.getCacheDir() as first arg.
        iter.add(new FieldInsnNode(Opcodes.GETSTATIC, "zombie/ZomboidFileSystem", "instance",
            "Lzombie/ZomboidFileSystem;"));
        iter.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "zombie/ZomboidFileSystem",
            "getCacheDir", "()Ljava/lang/String;"));
        // Set up the class itself as the second arg.
        iter.add(new LdcInsnNode(Type.getObjectType(entrypoint.replace(".", "/"))));
        // Add the final startClient/startServer method call.
        iter.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Hooks.INTERNAL_NAME,
            type == EnvType.CLIENT ? "startClient" : "startServer",
            "(Ljava/lang/String;Ljava/lang/Class;)V", false));

        classEmitter.accept(mainClass);
    }
}
