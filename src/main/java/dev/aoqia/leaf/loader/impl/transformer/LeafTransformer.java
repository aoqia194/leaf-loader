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
package dev.aoqia.leaf.loader.impl.transformer;

import java.util.Set;

import dev.aoqia.leaf.api.EnvType;
import dev.aoqia.leaf.loader.impl.LeafLoaderImpl;
import dev.aoqia.leaf.loader.impl.game.GameProvider.BuiltinTransform;
import dev.aoqia.leaf.loader.impl.launch.LeafLauncherBase;

import net.fabricmc.accesswidener.AccessWidenerClassVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

public final class LeafTransformer {
    public static byte[] transform(boolean isDevelopment, EnvType envType, String name,
        byte[] bytes) {
        Set<BuiltinTransform> transforms = LeafLoaderImpl.INSTANCE.getGameProvider()
            .getBuiltinTransforms(name);

        final boolean transformAccess =
            transforms.contains(BuiltinTransform.WIDEN_ALL_PACKAGE_ACCESS) &&
            LeafLauncherBase.getLauncher().getMappingConfiguration().requiresPackageAccessHack();
        final boolean environmentStrip = transforms.contains(BuiltinTransform.STRIP_ENVIRONMENT);
        final boolean applyAccessWidener = transforms.contains(BuiltinTransform.CLASS_TWEAKS) &&
                                           LeafLoaderImpl.INSTANCE.getAccessWidener()
                                               .getTargets()
                                               .contains(name);
        if (!transformAccess && !environmentStrip && !applyAccessWidener) {
            return bytes;
        }

        ClassReader classReader = new ClassReader(bytes);
        ClassWriter classWriter = new ClassWriter(classReader, 0);
        ClassVisitor visitor = classWriter;
        int visitorCount = 0;

        if (applyAccessWidener) {
            visitor = AccessWidenerClassVisitor.createClassVisitor(LeafLoaderImpl.ASM_VERSION,
                visitor, LeafLoaderImpl.INSTANCE.getAccessWidener());
            visitorCount++;
        }

        if (transformAccess) {
            visitor = new PackageAccessFixer(LeafLoaderImpl.ASM_VERSION, visitor);
            visitorCount++;
        }

        if (environmentStrip) {
            EnvironmentStrippingData stripData = new EnvironmentStrippingData(
                LeafLoaderImpl.ASM_VERSION, envType.toString());
            classReader.accept(stripData, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);

            if (stripData.stripEntireClass()) {
                throw new RuntimeException(
                    "Cannot load class " + name + " in environment type " + envType);
            }

            if (!stripData.isEmpty()) {
                visitor = new ClassStripper(LeafLoaderImpl.ASM_VERSION, visitor,
                    stripData.getStripInterfaces(), stripData.getStripFields(),
                    stripData.getStripMethods());
                visitorCount++;
            }
        }

        if (visitorCount <= 0) {
            return bytes;
        }

        classReader.accept(visitor, 0);
        return classWriter.toByteArray();
    }
}
