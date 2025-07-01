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
package dev.aoqia.leaf.loader.impl.game.zomboid;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.aoqia.leaf.loader.impl.LeafLoaderImpl;
import dev.aoqia.leaf.loader.impl.util.ExceptionUtil;
import dev.aoqia.leaf.loader.impl.util.LoaderUtil;
import dev.aoqia.leaf.loader.impl.util.SimpleClassPath;

import org.objectweb.asm.*;

public final class ZomboidVersionLookup {
    private static final Pattern VERSION_PATTERN = Pattern.compile(
        // Modern versions: 41.78.16, optional -unstable suffix for unstable build.
        "\\d+\\.\\d+\\.\\d+(-unstable\\.\\d+)?"
    );
    private static final Pattern RELEASE_PATTERN = Pattern.compile("\\d+\\.\\d+\\.\\d+");
    private static final Pattern RELEASE_JARNAME_PATTERN =
        Pattern.compile(".+?(?:\\d+\\.|-)(\\d+\\.\\d+\\.\\d+).+");
    private static final Pattern UNSTABLE_RELEASE_PATTERN = Pattern.compile(".+-unstable\\.(\\d+)");
    private static final Pattern UNSTABLE_RELEASE_JARNAME_PATTERN =
        Pattern.compile(".+?(?:\\d+\\.|-)(\\d+\\.\\d+\\.\\d+-unstable\\.\\d+).+");
    private static final Pattern UNSTABLE_REVISION_PATTERN = Pattern.compile("\\s*rev:(\\d+).+");

    public static ZomboidVersion getVersion(List<Path> gameJars, String entrypointClass,
        String versionName) {
        ZomboidVersion.Builder builder = new ZomboidVersion.Builder();

        if (versionName != null) {
            builder.setIdLookup(versionName);
        }

        try (SimpleClassPath cp = new SimpleClassPath(gameJars)) {
            // Determine class version
            if (entrypointClass != null) {
                try (InputStream is = cp.getInputStream(
                    LoaderUtil.getClassFileName(entrypointClass))) {
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

    public static ZomboidVersion getVersionExceptClassVersion(Path gameJar) {
        ZomboidVersion.Builder builder = new ZomboidVersion.Builder();

        try (SimpleClassPath cp = new SimpleClassPath(Collections.singletonList(gameJar))) {
            fillVersionFromJar(cp, builder);
        } catch (IOException e) {
            throw ExceptionUtil.wrap(e);
        }

        return builder.build();
    }

    public static void fillVersionFromJar(SimpleClassPath cp, ZomboidVersion.Builder builder) {
        try {
            InputStream is = cp.getInputStream("zombie/core/Core.class");
            if (is != null && fromAnalyzer(is, new GameVersionVisitor(), builder)) {
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
        }

        return false;
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

    public static String getRelease(String version) {
        if (RELEASE_PATTERN.matcher(version).matches() ||
            UNSTABLE_RELEASE_PATTERN.matcher(version).matches()) {
            return version;
        }

        if (isProbableVersion(version)) {
            int pos = version.indexOf("-unstable");
            if (pos >= 0) {
                return version.substring(0, pos);
            }
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

    private static String findProbableRevisionNumber(String str) {
        if (str == null) {
            return null;
        }

        Matcher matcher = UNSTABLE_REVISION_PATTERN.matcher(str);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Returns the probable version contained in the given string, or null if the string doesn't
     * contain a version.
     */
    private static String findProbableVersion(String str) {
        Matcher matcher = VERSION_PATTERN.matcher(str);
        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }

    private interface Analyzer {
        String getResult();
    }

    private static final class GameVersionVisitor extends ClassVisitor implements Analyzer {
        private int patch = -1;
        private int major = -1;
        private int minor = -1;
        private int revision = -1;

        // Visit the BIPUSH 41, BIPUSH 78, LDC "" (for 41.78.16) instructions
        // and store these statics, including revision number (only present on 42+)

        GameVersionVisitor() {
            super(LeafLoaderImpl.ASM_VERSION);
        }

        @Override
        public String getResult() {
            if (revision == -1) {
                return String.format("%d.%d.%d", major, minor, patch);
            }

            return String.format("%d.%d.%d-unstable.%d", major, minor, patch, revision);
        }



        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
            String signature, String[] exceptions) {
            // If all version data set, no need to visit anything else.
            if (major != -1 && minor != -1 && patch != -1 && revision != -1) {
                return null;
            }

            // Capture first two BIPISH instructions from the GameVersion constructor.
            // These are the MAJOR and MINOR numbers respectively.
            if (major == -1 && name.equals("<clinit>")) {
                return new InsnFwdMethodVisitor() {
                    @Override
                    public void visitIntInsn(int opcode, int operand) {
                        if (opcode != Opcodes.BIPUSH) {
                            return;
                        }

                        if (major == -1) {
                            major = operand;
                        } else if (minor == -1) {
                            minor = operand;
                        }
                    }

                    @Override
                    protected void visitAnyInsn() {}
                };
            }

            // Capture LDC instruction for patch number.
            if (patch == -1 && name.equals("getVersion")) {
                return new InsnFwdMethodVisitor() {
                    @Override
                    public void visitInvokeDynamicInsn(String name, String descriptor,
                        Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                        if (patch != -1) {
                            return;
                        }

                        Object arg = bootstrapMethodArguments[0];
                        if (arg instanceof String) {
                            patch = Integer.parseInt(((String) arg).replace(".", "").trim());
                        }
                    }

                    @Override
                    protected void visitAnyInsn() {}
                };
            }

            // Capture LDC instruction (revision number) from getSVNRevisionString() function.
            // If this function doesn't exist, it means we are on a build older than `42.0.0`.
            if (revision == -1 && name.equals("getSVNRevisionString")) {
                return new InsnFwdMethodVisitor() {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (revision != -1) {
                            return;
                        }

                        if (value instanceof String) {
                            revision = Integer.parseInt(findProbableRevisionNumber((String) value));
                        }
                    }

                    @Override
                    protected void visitAnyInsn() {}
                };
            }

            return null;
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
        public void visitTypeInsn(int opcode, String type) {
            visitAnyInsn();
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            visitAnyInsn();
        }

        @Override
        public void visitMethodInsn(int opcode,
            String owner,
            String name,
            String descriptor,
            boolean isInterface) {
            visitAnyInsn();
        }

        @Override
        public void visitInvokeDynamicInsn(String name,
            String descriptor,
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
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            visitAnyInsn();
        }

        protected abstract void visitAnyInsn();
    }
}
