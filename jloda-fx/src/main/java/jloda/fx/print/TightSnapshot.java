/*
 *  TightSnapshot.java Copyright (C) 2025 Daniel H. Huson
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

package jloda.fx.print;

import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Transform;

public final class TightSnapshot {

	private TightSnapshot() {
	}

	/**
	 * Snapshot only the content bounding box of a large pane without allocating a full-size image.
	 *
	 * @param pane the parent node containing the drawing
	 * @param bbox content bounds in pane-local coordinates (small compared to pane)
	 * @param fill background fill, e.g. Color.WHITE
	 * @param dpi  requested dpi (72 = 1:1). Use 300 for print-quality.
	 * @param pad  padding in pane units
	 */
	public static WritableImage snapshotBBoxTight(Node pane, Rectangle2D bbox, Color fill, int dpi, double pad) {
		if (pane == null || bbox == null) return null;

		pane.applyCss();
		if (pane instanceof Parent parent) {
			parent.layout();
		}

		// Pad bbox
		var minX = bbox.getMinX() - pad;
		var minY = bbox.getMinY() - pad;
		var w = bbox.getWidth() + 2 * pad;
		var h = bbox.getHeight() + 2 * pad;
		if (w <= 0 || h <= 0) return null;

		// Cap scale so we don't allocate ridiculous images
		var maxTex = 4000; // for iOS; avoid 4096 exactly
		var desiredScale = dpi / 72.0;
		var scale = capScaleForSnapshot(desiredScale, w, h);
		scale = Math.min(scale, capScaleForMaxTexture(desiredScale, w, h, maxTex));

		var pxW = (int) Math.ceil(w * scale);
		var pxH = (int) Math.ceil(h * scale);

		// extra safety
		pxW = Math.min(pxW, maxTex);
		pxH = Math.min(pxH, maxTex);

		if (pxW <= 0 || pxH <= 0) return null;

		// Clip pane to bbox region (so snapshot only renders that region)
		var oldClip = pane.getClip();
		Rectangle clip = new Rectangle(minX, minY, w, h);
		pane.setClip(clip);

		try {
			var sp = new SnapshotParameters();
			sp.setFill(fill == null ? Color.TRANSPARENT : fill);
			// Translate bbox to origin, then scale
			sp.setTransform(Transform.translate(-minX, -minY).createConcatenation(Transform.scale(scale, scale)));
			return pane.snapshot(sp, new WritableImage(pxW, pxH));
		} finally {
			pane.setClip(oldClip);
		}
	}

	private static double capScaleForSnapshot(double desiredScale, double localW, double localH) {
		final var MAX_DIM = 4096;
		final var MAX_PIXELS = 50_000_000L; // ~200MB RGBA

		var scale = desiredScale;

		var dimScale = Math.min(MAX_DIM / localW, MAX_DIM / localH);
		scale = Math.min(scale, dimScale);

		var area = localW * localH;
		if (area > 0) {
			var pixelScale = Math.sqrt(MAX_PIXELS / area);
			scale = Math.min(scale, pixelScale);
		}
		return Math.max(scale, 1e-6);
	}

	private static double capScaleForMaxTexture(double desiredScale, double localW, double localH, int maxTex) {
		if (localW <= 0 || localH <= 0) return 1.0;
		// Keep a safety margin; avoid landing exactly on maxTex.
		final var margin = 32;
		var limit = Math.max(1, maxTex - margin);

		var sx = limit / localW;
		var sy = limit / localH;

		return Math.max(1e-6, Math.min(desiredScale, Math.min(sx, sy)));
	}
}