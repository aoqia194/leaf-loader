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
package dev.aoqia.leaf.loader.language;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import dev.aoqia.leaf.loader.api.LeafLoader;
import org.objectweb.asm.ClassReader;

import dev.aoqia.leaf.api.EnvType;
import dev.aoqia.leaf.loader.impl.launch.LeafLauncherBase;
import dev.aoqia.leaf.loader.impl.util.LoaderUtil;

@Deprecated
public class JavaLanguageAdapter implements LanguageAdapter {
	private static boolean canApplyInterface(String itfString) throws IOException {
		// TODO: Be a bit more involved
		switch (itfString) {
		case "dev/aoqia/leaf/api/ClientModInitializer":
			if (LeafLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
				return false;
			}

			break;
		case "dev/aoqia/leaf/api/DedicatedServerModInitializer":
			if (LeafLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
				return false;
			}
		}

		InputStream stream = LeafLauncherBase.getLauncher().getResourceAsStream(LoaderUtil.getClassFileName(itfString));
		if (stream == null) return false;

		ClassReader reader = new ClassReader(stream);

		for (String s : reader.getInterfaces()) {
			if (!canApplyInterface(s)) {
				stream.close();
				return false;
			}
		}

		stream.close();
		return true;
	}

	public static Class<?> getClass(String className, Options options) throws ClassNotFoundException, IOException {
		InputStream stream = LeafLauncherBase.getLauncher().getResourceAsStream(LoaderUtil.getClassFileName(className));
		if (stream == null) throw new ClassNotFoundException("Could not find or load class " + className);

		ClassReader reader = new ClassReader(stream);

		for (String s : reader.getInterfaces()) {
			if (!canApplyInterface(s)) {
				switch (options.getMissingSuperclassBehavior()) {
				case RETURN_NULL:
					stream.close();
					return null;
				case CRASH:
				default:
					stream.close();
					throw new ClassNotFoundException("Could not find or load class " + s);
				}
			}
		}

		stream.close();
		return LeafLauncherBase.getClass(className);
	}

	@Override
	public Object createInstance(Class<?> modClass, Options options) throws LanguageAdapterException {
		try {
			Constructor<?> constructor = modClass.getDeclaredConstructor();
			return constructor.newInstance();
		} catch (NoSuchMethodException e) {
			throw new LanguageAdapterException("Could not find constructor for class " + modClass.getName() + "!", e);
		} catch (IllegalAccessException e) {
			throw new LanguageAdapterException("Could not access constructor of class " + modClass.getName() + "!", e);
		} catch (InvocationTargetException | IllegalArgumentException | InstantiationException e) {
			throw new LanguageAdapterException("Could not instantiate class " + modClass.getName() + "!", e);
		}
	}
}
