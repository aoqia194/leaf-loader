package dev.aoqia.leaf.loader.impl.game.zomboid.patch;

import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.aoqia.leaf.loader.impl.game.patch.GamePatch;
import dev.aoqia.leaf.loader.impl.launch.LeafLauncher;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class LogTypePatch extends GamePatch {
    private static final int ENUM_FIELD_FLAGS = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM;
	@Override
	public void process(LeafLauncher launcher, Function<String, ClassNode> classSource,
	                    Consumer<ClassNode> classEmitter) {
		final String leafDebugType = "Leaf";
		final String debugTypeClassName = "zombie.debug.DebugType";
        final String debugTypeClassPath = debugTypeClassName.replace('.', '/');
		final String debugTypeClassSig = "L" + debugTypeClassPath + ";";
		final ClassNode debugTypeClass = classSource.apply(debugTypeClassName);
		if (debugTypeClass == null) {
			throw new RuntimeException("Could not find DebugType game class.");
		}

        int enumSize = (int) debugTypeClass.fields.stream().filter(fieldNode -> {
            return (fieldNode.access & ENUM_FIELD_FLAGS) == ENUM_FIELD_FLAGS;
        }).count();

		// Add field itself
		debugTypeClass.fields.add(new FieldNode(ENUM_FIELD_FLAGS, leafDebugType, debugTypeClassSig, null, null));

		// Add to clinit

		final MethodNode clinit = findMethod(debugTypeClass, (method) -> method.name.equals("<clinit>"));
		if (clinit == null) {
			throw new RuntimeException("Failed to find DebugType clinit method!");
		}
		{
            AbstractInsnNode valuesCall = findInsn(clinit, node -> {
                if (node instanceof MethodInsnNode) {
                    MethodInsnNode methodInsnNode = (MethodInsnNode) node;
                    return methodInsnNode.name.equals("$values")
                        && methodInsnNode.desc.equals("()[" + debugTypeClassSig);
                }

                return false;
            }, true);

			ListIterator<AbstractInsnNode> iter = clinit.instructions.iterator(
                clinit.instructions.indexOf(valuesCall) - 2);
			iter.add(new TypeInsnNode(Opcodes.NEW, debugTypeClassPath));
			iter.add(new InsnNode(Opcodes.DUP));
			iter.add(new LdcInsnNode(leafDebugType));
			iter.add(new IntInsnNode(Opcodes.BIPUSH, enumSize));
			iter.add(new InsnNode(Opcodes.ICONST_0));
			iter.add(new TypeInsnNode(Opcodes.ANEWARRAY, debugTypeClass.name));
			iter.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, debugTypeClass.name, "<init>",
                "(Ljava/lang/String;I[" + debugTypeClassSig + ")V"));
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
			iter.set(new IntInsnNode(Opcodes.BIPUSH, enumSize + 1));
			moveBefore(iter, Opcodes.ARETURN);
			iter.add(new InsnNode(Opcodes.DUP));
			iter.add(new IntInsnNode(Opcodes.BIPUSH, enumSize));
			iter.add(new FieldInsnNode(Opcodes.GETSTATIC, debugTypeClass.name, leafDebugType,
					debugTypeClassSig));
			iter.add(new InsnNode(Opcodes.AASTORE));
		}

		classEmitter.accept(debugTypeClass);
	}
}
