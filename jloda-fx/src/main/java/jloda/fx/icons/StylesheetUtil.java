/*
 *  StyleSheetUtil.java Copyright (C) 2026 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package jloda.fx.icons;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Scene;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * ensures that style sheet is attached to scene
 * Daniel Huson, 1.2026
 */
public final class StylesheetUtil {
	private StylesheetUtil() {
	}

	// Weak keys prevent leaks if scenes are discarded
	private static final Set<Scene> INITIALIZED =
			Collections.newSetFromMap(new WeakHashMap<>());

	public static void ensureOnScene(Node node, java.util.function.Supplier<String> stylesheetSupplier) {
		Scene scene = node.getScene();
		if (scene != null) {
			ensure(scene, stylesheetSupplier);
			return;
		}

		// Scene not available yet -> attach one-shot listener
		ChangeListener<Scene> listener = new ChangeListener<>() {
			@Override
			public void changed(ObservableValue<? extends Scene> obs, Scene oldScene, Scene newScene) {
				if (newScene != null) {
					try {
						ensure(newScene, stylesheetSupplier);
					} finally {
						node.sceneProperty().removeListener(this);
					}
				}
			}
		};
		node.sceneProperty().addListener(listener);
	}

	private static void ensure(Scene scene, java.util.function.Supplier<String> stylesheetSupplier) {
		final String url;
		try {
			url = stylesheetSupplier.get();
		} catch (RuntimeException ex) {
			// Make the real cause obvious in the console
			System.err.println("StylesheetUtil: failed to obtain stylesheet URL: " + ex);
			throw ex;
		}

		// If it's already present, we're done (even if we haven't "initialized" this scene before)
		if (containsStylesheet(scene, url)) {
			INITIALIZED.add(scene); // optional: record as initialized to suppress future checks
			return;
		}

		// Optional: avoid doing any further work more than once per scene.
		// (We still did the contains-check above, which is cheap.)
		if (!INITIALIZED.add(scene)) {
			// Another call already handled adding it (or decided it wasn't needed)
			return;
		}

		scene.getStylesheets().add(0, url);
	}

	private static boolean containsStylesheet(Scene scene, String url) {
		final String norm = normalizeUrl(url);
		for (String existing : scene.getStylesheets()) {
			if (norm.equals(normalizeUrl(existing))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Normalize URLs enough to avoid duplicates caused by minor string differences.
	 * We avoid aggressive normalization to preserve JavaFX URL semantics.
	 */
	private static String normalizeUrl(String url) {
		if (url == null) return "";
		try {
			var uri = new URI(url);
			// Normalize path segments like /a/../b
			uri = uri.normalize();
			return uri.toString();
		} catch (URISyntaxException e) {
			// Fall back to trimmed original if it's not a strict URI (should be rare)
			return url.trim();
		}
	}
}