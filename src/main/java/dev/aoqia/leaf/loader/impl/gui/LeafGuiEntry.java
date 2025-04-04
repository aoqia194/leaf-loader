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
package dev.aoqia.leaf.loader.impl.gui;

import java.awt.GraphicsEnvironment;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import dev.aoqia.leaf.loader.impl.LeafLoaderImpl;
import dev.aoqia.leaf.loader.impl.game.GameProvider;
import dev.aoqia.leaf.loader.impl.gui.LeafStatusTree.FabricBasicButtonType;
import dev.aoqia.leaf.loader.impl.gui.LeafStatusTree.FabricStatusTab;
import dev.aoqia.leaf.loader.impl.gui.LeafStatusTree.LeafTreeWarningLevel;
import dev.aoqia.leaf.loader.impl.util.LoaderUtil;
import dev.aoqia.leaf.loader.impl.util.Localization;
import dev.aoqia.leaf.loader.impl.util.UrlUtil;
import dev.aoqia.leaf.loader.impl.util.log.Log;
import dev.aoqia.leaf.loader.impl.util.log.LogCategory;

/** The main entry point for all fabric-based stuff. */
public final class LeafGuiEntry {
	/** Opens the given {@link LeafStatusTree} in a new swing window.
	 *
	 * @throws Exception if something went wrong while opening the window. */
	public static void open(LeafStatusTree tree) throws Exception {
		GameProvider provider = LeafLoaderImpl.INSTANCE.tryGetGameProvider();

		if (provider == null && LoaderUtil.hasAwtSupport()
				|| provider != null && provider.hasAwtSupport()) {
			FabricMainWindow.open(tree, true);
		} else {
			openForked(tree);
		}
	}

	private static void openForked(LeafStatusTree tree) throws IOException, InterruptedException {
		Path javaBinDir = LoaderUtil.normalizePath(Paths.get(System.getProperty("java.home"), "bin"));
		String[] executables = { "javaw.exe", "java.exe", "java" };
		Path javaPath = null;

		for (String executable : executables) {
			Path path = javaBinDir.resolve(executable);

			if (Files.isRegularFile(path)) {
				javaPath = path;
				break;
			}
		}

		if (javaPath == null) throw new RuntimeException("can't find java executable in "+javaBinDir);

		Process process = new ProcessBuilder(javaPath.toString(), "-Xmx100M", "-cp", UrlUtil.LOADER_CODE_SOURCE.toString(), LeafGuiEntry.class.getName())
				.redirectOutput(ProcessBuilder.Redirect.INHERIT)
				.redirectError(ProcessBuilder.Redirect.INHERIT)
				.start();

		final Thread shutdownHook = new Thread(process::destroy);

		Runtime.getRuntime().addShutdownHook(shutdownHook);

		try (DataOutputStream os = new DataOutputStream(process.getOutputStream())) {
			tree.writeTo(os);
		}

		int rVal = process.waitFor();

		Runtime.getRuntime().removeShutdownHook(shutdownHook);

		if (rVal != 0) throw new IOException("subprocess exited with code "+rVal);
	}

	public static void main(String[] args) throws Exception {
		LeafStatusTree tree = new LeafStatusTree(new DataInputStream(System.in));
		FabricMainWindow.open(tree, true);
		System.exit(0);
	}

	/** @param exitAfter If true then this will call {@link System#exit(int)} after showing the gui, otherwise this will
	 *            return normally. */
	public static void displayCriticalError(Throwable exception, boolean exitAfter) {
		Log.error(LogCategory.GENERAL, "A critical error occurred", exception);

		displayError(Localization.format("gui.error.header"), exception, exitAfter);
	}

	public static void displayError(String mainText, Throwable exception, boolean exitAfter) {
		displayError(mainText, exception, tree -> {
			StringWriter error = new StringWriter();
			error.append(mainText);

			if (exception != null) {
				error.append(System.lineSeparator());
				exception.printStackTrace(new PrintWriter(error));
			}

			tree.addButton(Localization.format("gui.button.copyError"), FabricBasicButtonType.CLICK_MANY).withClipboard(error.toString());
		}, exitAfter);
	}

	public static void displayError(String mainText, Throwable exception, Consumer<LeafStatusTree> treeCustomiser, boolean exitAfter) {
		GameProvider provider = LeafLoaderImpl.INSTANCE.tryGetGameProvider();

		if (!GraphicsEnvironment.isHeadless() && (provider == null || provider.canOpenErrorGui())) {
			String title = "Leaf Loader " + LeafLoaderImpl.VERSION;
			LeafStatusTree tree = new LeafStatusTree(title, mainText);
			FabricStatusTab crashTab = tree.addTab(Localization.format("gui.tab.crash"));

			if (exception != null) {
				crashTab.node.addCleanedException(exception);
			} else {
				crashTab.node.addMessage(Localization.format("gui.error.missingException"), LeafTreeWarningLevel.NONE);
			}

			// Maybe add an "open mods folder" button?
			// or should that be part of the main tree's right-click menu?
			tree.addButton(Localization.format("gui.button.exit"), FabricBasicButtonType.CLICK_ONCE).makeClose();
			treeCustomiser.accept(tree);

			try {
				open(tree);
			} catch (Exception e) {
				if (exitAfter) {
					Log.warn(LogCategory.GENERAL, "Failed to open the error gui!", e);
				} else {
					throw new RuntimeException("Failed to open the error gui!", e);
				}
			}
		}

		if (exitAfter) {
			System.exit(1);
		}
	}
}
