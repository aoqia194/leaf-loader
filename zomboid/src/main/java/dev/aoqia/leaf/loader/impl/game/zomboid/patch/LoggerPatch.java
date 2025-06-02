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

import dev.aoqia.leaf.loader.impl.game.patch.GamePatch;
import dev.aoqia.leaf.loader.impl.launch.LeafLauncher;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class LoggerPatch extends GamePatch {
    @Override
    public void process(LeafLauncher launcher, Function<String, ClassNode> classSource,
        Consumer<ClassNode> classEmitter) {
        // Add Leaf value to DebugType enum.
        final String leafDebugType = "Leaf";
        final String debugTypeClassName = "zombie.debug.DebugType";
        final String debugTypeClassSig = "L" + debugTypeClassName.replace('.', '/') + ";";
        final ClassNode debugTypeClass = classSource.apply(debugTypeClassName);
        if (debugTypeClass == null) {
            throw new RuntimeException("Could not find DebugType game class.");
        }

        int enumFieldCount = debugTypeClass.fields.size();
        // Ignore Default class field that b42 sets
        if (debugTypeClass.fields.get(enumFieldCount - 2).name.equalsIgnoreCase("default")) {
            enumFieldCount--;
        }

        // Add field itself
        debugTypeClass.fields.add(
            new FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM,
                leafDebugType, "Lzombie/debug/DebugType;", null, null));

        // Add to clinit
        final MethodNode clinit = findMethod(debugTypeClass,
            (method) -> method.name.equals("<clinit>"));
        if (clinit == null) {
            throw new RuntimeException("Failed to find DebugType clinit method!");
        }
        {
            ListIterator<AbstractInsnNode> iter = clinit.instructions.iterator();
            moveBefore(iter, Opcodes.INVOKESTATIC);
            iter.add(new TypeInsnNode(Opcodes.NEW, "zombie/debug/DebugType"));
            iter.add(new InsnNode(Opcodes.DUP));
            iter.add(new LdcInsnNode(leafDebugType));
            iter.add(new IntInsnNode(Opcodes.BIPUSH, enumFieldCount - 1));
            iter.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, debugTypeClass.name, "<init>",
                "(Ljava/lang/String;I)V"));
            iter.add(new FieldInsnNode(Opcodes.PUTSTATIC, debugTypeClass.name, leafDebugType,
                debugTypeClassSig));
        }

        // Add to $values()
        final MethodNode values = findMethod(debugTypeClass,
            (method) -> method.name.equals("$values"));
        if (values == null) {
            throw new RuntimeException("Failed to find synthetic DebugType $values method!");
        }
        {
            ListIterator<AbstractInsnNode> iter = values.instructions.iterator();
            moveBefore(iter, Opcodes.BIPUSH);
            iter.next();
            iter.set(new IntInsnNode(Opcodes.BIPUSH, enumFieldCount));
            moveBefore(iter, Opcodes.ARETURN);
            iter.add(new InsnNode(Opcodes.DUP));
            iter.add(new IntInsnNode(Opcodes.BIPUSH, enumFieldCount - 1));
            iter.add(new FieldInsnNode(Opcodes.GETSTATIC, debugTypeClass.name, leafDebugType,
                debugTypeClassSig));
            iter.add(new InsnNode(Opcodes.AASTORE));
        }

        classEmitter.accept(debugTypeClass);
    }
}
