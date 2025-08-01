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
import dev.aoqia.leaf.loader.impl.game.zomboid.Hooks;
import dev.aoqia.leaf.loader.impl.launch.LeafLauncher;
import dev.aoqia.leaf.loader.impl.util.log.Log;
import dev.aoqia.leaf.loader.impl.util.log.LogCategory;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class BrandingPatch extends GamePatch {
    @Override
    public void process(LeafLauncher launcher,
        Function<String, ClassNode> classSource,
        Consumer<ClassNode> classEmitter) {
        ClassNode gameWindowClass = classSource.apply("zombie.GameWindow");

        if (gameWindowClass == null) {
            return;
        }

        if (applyBrandingPatch(gameWindowClass)) {
            classEmitter.accept(gameWindowClass);
        }
    }

    private boolean applyBrandingPatch(ClassNode classNode) {
        MethodNode methodNode = findMethod(classNode, method -> {
            return method.name.equals("InitDisplay") && method.desc.equals("()V") &&
                   isPublicStatic(method.access);
        });

        if (methodNode == null) {
            throw new RuntimeException(
                "Could not find InitDisplay method in " + classNode.name + "!");
        }

        Log.debug(LogCategory.GAME_PATCH, "Applying brand name hook to %s#%s",
            classNode.name, methodNode.name);

        ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator();
        while (it.hasNext()) {
            if (it.next().getOpcode() == Opcodes.LDC) {
                it.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    Hooks.INTERNAL_NAME,
                    "setWindowTitle",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    false));
                it.next();

                return true;
            }
        }

        return false;
    }
}
