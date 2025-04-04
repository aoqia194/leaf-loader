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
package dev.aoqia.leaf.loader.impl.launch.knot;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.util.ReEntranceLock;

import dev.aoqia.leaf.loader.impl.launch.LeafLauncherBase;
import dev.aoqia.leaf.loader.impl.util.UrlUtil;

public class MixinServiceKnot implements IMixinService, IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
	static IMixinTransformer transformer;

	private final ReEntranceLock lock;

	public MixinServiceKnot() {
		lock = new ReEntranceLock(1);
	}

	public byte[] getClassBytes(String name, String transformedName) throws IOException {
		return LeafLauncherBase.getLauncher().getClassByteArray(name, true);
	}

	public byte[] getClassBytes(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
		byte[] classBytes = LeafLauncherBase.getLauncher().getClassByteArray(name, runTransformers);

		if (classBytes != null) {
			return classBytes;
		} else {
			throw new ClassNotFoundException(name);
		}
	}

	@Override
	public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
		return getClassNode(name, true);
	}

	@Override
	public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
		return getClassNode(name, runTransformers, 0);
	}

	@Override
	public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags) throws ClassNotFoundException, IOException {
		ClassReader reader = new ClassReader(getClassBytes(name, runTransformers));
		ClassNode node = new ClassNode();
		reader.accept(node, readerFlags);
		return node;
	}

	@Override
	public URL[] getClassPath() {
		// Mixin 0.7.x only uses getClassPath() to find itself; we implement CodeSource correctly,
		// so this is unnecessary.
		return new URL[0];
	}

	@Override
	public Class<?> findClass(String name) throws ClassNotFoundException {
		return LeafLauncherBase.getLauncher().getTargetClassLoader().loadClass(name);
	}

	@Override
	public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
		return Class.forName(name, initialize, LeafLauncherBase.getLauncher().getTargetClassLoader());
	}

	@Override
	public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
		return Class.forName(name, initialize, Knot.class.getClassLoader());
	}

	@Override
	public String getName() {
		return LeafLauncherBase.getLauncher() instanceof Knot ? "Knot/Leaf" : "Launchwrapper/Leaf";
	}

	@Override
	public boolean isValid() {
		return true;
	}

	@Override
	public void prepare() { }

	@Override
	public MixinEnvironment.Phase getInitialPhase() {
		return MixinEnvironment.Phase.PREINIT;
	}

	@Override
	public void offer(IMixinInternal internal) {
		if (internal instanceof IMixinTransformerFactory) {
			transformer = ((IMixinTransformerFactory) internal).createTransformer();
		}
	}

	@Override
	public void init() {
	}

	@Override
	public void beginPhase() { }

	@Override
	public void checkEnv(Object bootSource) { }

	@Override
	public ReEntranceLock getReEntranceLock() {
		return lock;
	}

	@Override
	public IClassProvider getClassProvider() {
		return this;
	}

	@Override
	public IClassBytecodeProvider getBytecodeProvider() {
		return this;
	}

	@Override
	public ITransformerProvider getTransformerProvider() {
		return this;
	}

	@Override
	public IClassTracker getClassTracker() {
		return this;
	}

	@Override
	public IMixinAuditTrail getAuditTrail() {
		return null;
	}

	@Override
	public Collection<String> getPlatformAgents() {
		return Collections.singletonList("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault");
	}

	@Override
	public IContainerHandle getPrimaryContainer() {
		return new ContainerHandleURI(UrlUtil.LOADER_CODE_SOURCE.toUri());
	}

	@Override
	public Collection<IContainerHandle> getMixinContainers() {
		return Collections.emptyList();
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return LeafLauncherBase.getLauncher().getResourceAsStream(name);
	}

	@Override
	public void registerInvalidClass(String className) { }

	@Override
	public boolean isClassLoaded(String className) {
		return LeafLauncherBase.getLauncher().isClassLoaded(className);
	}

	@Override
	public String getClassRestrictions(String className) {
		return "";
	}

	@Override
	public Collection<ITransformer> getTransformers() {
		return Collections.emptyList();
	}

	@Override
	public Collection<ITransformer> getDelegatedTransformers() {
		return Collections.emptyList();
	}

	@Override
	public void addTransformerExclusion(String name) { }

	@Override
	public String getSideName() {
		return LeafLauncherBase.getLauncher().getEnvironmentType().name();
	}

	@Override
	public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() {
		return MixinEnvironment.CompatibilityLevel.JAVA_8;
	}

	@Override
	public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
		return MixinEnvironment.CompatibilityLevel.JAVA_22;
	}

	@Override
	public ILogger getLogger(String name) {
		return MixinLogger.get(name);
	}

	static IMixinTransformer getTransformer() {
		return transformer;
	}
}
