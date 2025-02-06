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

package net.aoqia.loader.impl.game.zomboid;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.aoqia.loader.impl.LeafLoaderImpl;
import net.aoqia.loader.impl.util.ExceptionUtil;
import net.aoqia.loader.impl.util.LoaderUtil;
import net.aoqia.loader.impl.util.SimpleClassPath;
import org.objectweb.asm.*;

public final class ZomboidVersionLookup {
    private static final Pattern VERSION_PATTERN = Pattern.compile(
        // Modern versions: 41.78.16, optional -unstable suffix for unstable build.
        "\\d+\\.\\d+\\.\\d+(-unstable\\.\\d+)?"
    );
    private static final Pattern RELEASE_PATTERN = Pattern.compile("\\d+\\.\\d+\\.\\d+");
    private static final Pattern RELEASE_JARNAME_PATTERN = Pattern.compile(".+?(?:\\d+\\.|-)(\\d+\\.\\d+\\.\\d+).+");
    private static final Pattern UNSTABLE_RELEASE_PATTERN = Pattern.compile(".+-unstable\\.(\\d+)");
    private static final Pattern UNSTABLE_RELEASE_JARNAME_PATTERN = Pattern.compile(
        ".+?(?:\\d+\\.|-)(\\d+\\.\\d+\\.\\d+-unstable\\.\\d+).+");
    private static final String STRING_DESC = "Ljava/lang/String;";

    public static ZomboidVersion getVersion(List<Path> gameJars, String entrypointClass, String versionName) {
        ZomboidVersion.Builder builder = new ZomboidVersion.Builder();

        if (versionName != null) {
            builder.setIdLookup(versionName);
        }

        try (SimpleClassPath cp = new SimpleClassPath(gameJars)) {
            // Determine class version
            if (entrypointClass != null) {
                try (InputStream is = cp.getInputStream(LoaderUtil.getClassFileName(entrypointClass))) {
                    DataInputStream dis = new DataInputStream(is);

                    if (dis.readInt() == 0xCAFEBABE) {
                        dis.readUnsignedShort();
                        builder.setClassVersion(dis.readUnsignedShort());
                    }
                }
            }

            // Check various known files for version information if unknown
            if (versionName == null) {
                fillVersionFromJar(cp, builder);
            }
        } catch (IOException e) {
            throw ExceptionUtil.wrap(e);
        }

        return builder.build();
    }

    public static void fillVersionFromJar(SimpleClassPath cp, ZomboidVersion.Builder builder) {
        try {
            InputStream is;

            // version.json - contains version and target release for most if not all releases
            //            if ((is = cp.getInputStream("version.json")) != null && fromVersionJson(is, builder)) {
            //                return;
            //            }

            // constant return value of Core#getGameVersion().
            if ((is = cp.getInputStream("zombie/core/Core.class")) != null &&
                fromAnalyzer(is, new MethodConstantRetVisitor("gameVersion"), builder)) {
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        builder.setFromFileName(cp.getPaths().get(0).getFileName().toString());
    }

    private static <T extends ClassVisitor & Analyzer>
    boolean fromAnalyzer(InputStream is, T analyzer, ZomboidVersion.Builder builder) {
        String result = analyze(is, analyzer);

        if (result != null) {
            builder.setId(result);
            return true;
        } else {
            return false;
        }
    }

    private static <T extends ClassVisitor & Analyzer> String analyze(InputStream is, T analyzer) {
        try {
            ClassReader cr = new ClassReader(is);
            cr.accept(analyzer, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

            return analyzer.getResult();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                // ignored
            }
        }

        return null;
    }

    public static ZomboidVersion getVersionExceptClassVersion(Path gameJar) {
        ZomboidVersion.Builder builder = new ZomboidVersion.Builder();

        try (SimpleClassPath cp = new SimpleClassPath(Collections.singletonList(gameJar))) {
            fillVersionFromJar(cp, builder);
        } catch (IOException e) {
            throw ExceptionUtil.wrap(e);
        }

        return builder.build();
    }

    public static String getRelease(String version) {
        if (RELEASE_PATTERN.matcher(version).matches() || UNSTABLE_RELEASE_PATTERN.matcher(version).matches()) {
            return version;
        }
        assert isProbableVersion(version);

        int pos = version.indexOf("-unstable");
        if (pos >= 0) {
            return version.substring(0, pos);
        }

        // Try to get version info from jar name as last resort.
        Matcher matcher = UNSTABLE_RELEASE_JARNAME_PATTERN.matcher(version);
        if (matcher.find()) {
            return matcher.group(1);
        }

        matcher = RELEASE_JARNAME_PATTERN.matcher(version);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private static boolean isProbableVersion(String str) {
        return VERSION_PATTERN.matcher(str).matches();
    }

    /**
     * Returns the probable version contained in the given string, or null if the string doesn't contain a version.
     */
    private static String findProbableVersion(String str) {
        Matcher matcher = VERSION_PATTERN.matcher(str);

        if (matcher.find()) {
            return matcher.group();
        } else {
            return null;
        }
    }

    private interface Analyzer {
        String getResult();
    }

    private static final class FieldStringConstantVisitor extends ClassVisitor implements Analyzer {
        private final String fieldName;
        private String className;
        private String result;

        FieldStringConstantVisitor(String fieldName) {
            super(LeafLoaderImpl.ASM_VERSION);
            this.fieldName = fieldName;
        }

        @Override
        public String getResult() {
            return result;
        }

        @Override
        public void visit(int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces) {
            this.className = name;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (result == null && name.equals(fieldName) && descriptor.equals(STRING_DESC) && value instanceof String) {
                result = (String) value;
            }

            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions) {
            if (result != null || !name.equals("<clinit>")) {
                return null;
            }

            // capture LDC ".." followed by PUTSTATIC this.fieldName
            return new InsnFwdMethodVisitor() {
                String lastLdc;

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                    if (result == null
                        && lastLdc != null
                        && opcode == Opcodes.PUTSTATIC
                        && owner.equals(className)
                        && name.equals(fieldName)
                        && descriptor.equals(STRING_DESC)) {
                        result = lastLdc;
                    }

                    lastLdc = null;
                }

                @Override
                public void visitLdcInsn(Object value) {
                    String str;

                    if (value instanceof String && isProbableVersion(str = (String) value)) {
                        lastLdc = str;
                    } else {
                        lastLdc = null;
                    }
                }

                @Override
                protected void visitAnyInsn() {
                    lastLdc = null;
                }
            };
        }
    }

    private static final class MethodStringConstantContainsVisitor extends ClassVisitor implements Analyzer {
        private final String methodOwner;
        private final String methodName;
        private String result;

        MethodStringConstantContainsVisitor(String methodOwner, String methodName) {
            super(LeafLoaderImpl.ASM_VERSION);

            this.methodOwner = methodOwner;
            this.methodName = methodName;
        }

        @Override
        public String getResult() {
            return result;
        }

        @Override
        public MethodVisitor visitMethod(int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions) {
            if (result != null) {
                return null;
            }

            // capture LDC ".." followed by INVOKE methodOwner.methodName
            return new InsnFwdMethodVisitor() {
                String lastLdc;

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                    if (result == null
                        && lastLdc != null
                        && owner.equals(methodOwner)
                        && name.equals(methodName)
                        && descriptor.startsWith("(" + STRING_DESC + ")")) {
                        result = lastLdc;
                    }

                    lastLdc = null;
                }

                @Override
                public void visitLdcInsn(Object value) {
                    if (value instanceof String) {
                        lastLdc = findProbableVersion((String) value);
                    } else {
                        lastLdc = null;
                    }
                }

                @Override
                protected void visitAnyInsn() {
                    lastLdc = null;
                }
            };
        }
    }

    private static final class MethodConstantRetVisitor extends ClassVisitor implements Analyzer {
        private final String methodName;
        private String result;

        MethodConstantRetVisitor(String methodName) {
            super(LeafLoaderImpl.ASM_VERSION);

            this.methodName = methodName;
        }

        @Override
        public String getResult() {
            return result;
        }

        @Override
        public MethodVisitor visitMethod(int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions) {
            if (result != null
                || methodName != null && !name.equals(methodName)
                || !descriptor.endsWith(STRING_DESC)
                || descriptor.charAt(descriptor.length() - STRING_DESC.length() - 1) != ')') {
                return null;
            }

            // capture LDC ".." followed by ARETURN
            return new InsnFwdMethodVisitor() {
                String lastLdc;

                @Override
                public void visitInsn(int opcode) {
                    if (result == null
                        && lastLdc != null
                        && opcode == Opcodes.ARETURN) {
                        result = lastLdc;
                    }

                    lastLdc = null;
                }

                @Override
                public void visitLdcInsn(Object value) {
                    String str;

                    if (value instanceof String && isProbableVersion(str = (String) value)) {
                        lastLdc = str;
                    } else {
                        lastLdc = null;
                    }
                }

                @Override
                protected void visitAnyInsn() {
                    lastLdc = null;
                }
            };
        }
    }

    private static final class MethodConstantVisitor extends ClassVisitor implements Analyzer {
        private static final String STARTING_MESSAGE = "Starting Zomboid server version ";
        private static final String CLASSIC_PREFIX = "Zomboid ";

        private final String methodNameHint;
        private String result;
        private boolean foundInMethodHint;

        MethodConstantVisitor(String methodNameHint) {
            super(LeafLoaderImpl.ASM_VERSION);

            this.methodNameHint = methodNameHint;
        }

        @Override
        public String getResult() {
            return result;
        }

        @Override
        public MethodVisitor visitMethod(int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions) {
            final boolean isRequestedMethod = name.equals(methodNameHint);

            if (result != null && !isRequestedMethod) {
                return null;
            }

            return new MethodVisitor(LeafLoaderImpl.ASM_VERSION) {
                @Override
                public void visitLdcInsn(Object value) {
                    if ((result == null || !foundInMethodHint && isRequestedMethod) && value instanceof String) {
                        String str = (String) value;

                        if (isProbableVersion(str)) {
                            result = str;
                            foundInMethodHint = isRequestedMethod;
                        }
                    }
                }
            };
        }
    }

    private abstract static class InsnFwdMethodVisitor extends MethodVisitor {
        InsnFwdMethodVisitor() {
            super(LeafLoaderImpl.ASM_VERSION);
        }

        @Override
        public void visitInsn(int opcode) {
            visitAnyInsn();
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            visitAnyInsn();
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            visitAnyInsn();
        }

        @Override
        public void visitTypeInsn(int opcode, java.lang.String type) {
            visitAnyInsn();
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            visitAnyInsn();
        }

        @Override
        public void visitMethodInsn(int opcode,
            java.lang.String owner,
            java.lang.String name,
            java.lang.String descriptor,
            boolean isInterface) {
            visitAnyInsn();
        }

        @Override
        public void visitInvokeDynamicInsn(java.lang.String name,
            java.lang.String descriptor,
            Handle bootstrapMethodHandle,
            Object... bootstrapMethodArguments) {
            visitAnyInsn();
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            visitAnyInsn();
        }

        @Override
        public void visitLdcInsn(Object value) {
            visitAnyInsn();
        }

        @Override
        public void visitIincInsn(int var, int increment) {
            visitAnyInsn();
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            visitAnyInsn();
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            visitAnyInsn();
        }

        @Override
        public void visitMultiANewArrayInsn(java.lang.String descriptor, int numDimensions) {
            visitAnyInsn();
        }

        protected abstract void visitAnyInsn();
    }

    private static final class FieldTypeCaptureVisitor extends ClassVisitor implements Analyzer {
        private String type;

        FieldTypeCaptureVisitor() {
            super(LeafLoaderImpl.ASM_VERSION);
        }

        @Override
        public String getResult() {
            return type;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (type == null && descriptor.startsWith("L") && !descriptor.startsWith("Ljava/")) {
                type = descriptor.substring(1, descriptor.length() - 1);
            }

            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions) {
            return null;
        }
    }
}
