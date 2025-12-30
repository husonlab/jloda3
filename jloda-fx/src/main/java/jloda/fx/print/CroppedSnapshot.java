package jloda.fx.print;

import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;

public final class CroppedSnapshot {

	private CroppedSnapshot() {
	}

	/**
	 * Creates a snapshot of only the content bounding box of the given pane (Parent).
	 * Works reliably even when content is small relative to the pane and when nested transforms exist.
	 *
	 * @param pane       the parent node to snapshot
	 * @param background background fill
	 * @param dpi        72 for 1:1, 300 for print-quality
	 * @param pad        padding in pane local units (e.g. 5..15) to avoid stroke/text clipping
	 */
	public static WritableImage snapshotContentBBox(Node pane, Color background, int dpi, double pad) {
		if (pane == null || pane.getScene() == null) return null;

		// Ensure layout is current
		pane.applyCss();
		if (pane instanceof Parent parent) {
			parent.layout();
		}
		Rectangle2D bbox = ContentBoundsUtil.computeContentBoundsLocal(pane);
		if (bbox == null) return null;

		// Pad bbox (important for text ascenders and stroke widths)
		double minX = bbox.getMinX() - pad;
		double minY = bbox.getMinY() - pad;
		double w = bbox.getWidth() + 2 * pad;
		double h = bbox.getHeight() + 2 * pad;

		// Full snapshot at DPI
		double scale = dpi / 72.0;
		SnapshotParameters sp = new SnapshotParameters();
		sp.setFill(background == null ? Color.TRANSPARENT : background);
		sp.setTransform(Transform.scale(scale, scale));

		WritableImage full = pane.snapshot(sp, null);

		// Map bbox (pane local) -> pixel coords in the full snapshot
		Bounds paneBounds = pane.getBoundsInLocal(); // coordinate system of the snapshot
		int x = (int) Math.floor((minX - paneBounds.getMinX()) * scale);
		int y = (int) Math.floor((minY - paneBounds.getMinY()) * scale);
		int cw = (int) Math.ceil(w * scale);
		int ch = (int) Math.ceil(h * scale);

		// Clamp to image bounds
		x = Math.max(0, x);
		y = Math.max(0, y);
		cw = Math.min(cw, (int) full.getWidth() - x);
		ch = Math.min(ch, (int) full.getHeight() - y);

		if (cw <= 0 || ch <= 0) return null;

		PixelReader pr = full.getPixelReader();
		return new WritableImage(pr, x, y, cw, ch);
	}
}