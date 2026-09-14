package jloda.fx.print;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Labeled;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.transform.Transform;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class ContentBoundsUtil {

	private ContentBoundsUtil() {
	}

	/**
	 * the tight bounding box, in the node's local coordinates, of what the node actually paints, found by
	 * rendering the node and detecting the non-background (non-white, opaque) pixels. Unlike
	 * {@link #computeContentBoundsLocal}, which unions the geometric bounds of every descendant (and so
	 * includes full-size transparent panes), this reflects only painted content, exactly as the raster crop
	 * used for "Copy Image" does. Intended to supply crop bounds to the vector exporters.
	 *
	 * @param node the node to measure, must be in a scene
	 * @param pad  padding, in local units, to keep around the detected content
	 * @return tight content bounds in node-local coordinates, or null if nothing is painted
	 */
	public static Bounds renderedContentBoundsLocal(Node node, double pad) {
		if (node == null || node.getScene() == null) return null;
		node.applyCss();
		if (node instanceof Parent parent) parent.layout();

		var bil = node.getBoundsInLocal();
		var w = bil.getWidth();
		var h = bil.getHeight();
		if (w <= 0 || h <= 0) return null;

		// render at scale 1, but cap the pixel count so a huge (zoomed) drawing does not allocate a giant image;
		// a slightly coarser bbox is fine as we pad it and clamp back to the node's own bounds
		var maxPixels = 8_000_000.0;
		var scale = (w * h > maxPixels) ? Math.sqrt(maxPixels / (w * h)) : 1.0;
		var pxW = Math.max(1, (int) Math.ceil(w * scale));
		var pxH = Math.max(1, (int) Math.ceil(h * scale));

		var sp = new SnapshotParameters();
		sp.setFill(Color.WHITE);
		// map local point p to pixel (p - min) * scale, so pixel (px,py) corresponds to local (min + px/scale)
		sp.setTransform(Transform.scale(scale, scale)
				.createConcatenation(Transform.translate(-bil.getMinX(), -bil.getMinY())));

		var image = node.snapshot(sp, new WritableImage(pxW, pxH));
		var rect = ImageCropper.contentRectangle(image, 0.02, 0.1);
		if (rect == null) return null;

		var minX = bil.getMinX() + rect.getMinX() / scale - pad;
		var minY = bil.getMinY() + rect.getMinY() / scale - pad;
		var maxX = bil.getMinX() + (rect.getMinX() + rect.getWidth()) / scale + pad;
		var maxY = bil.getMinY() + (rect.getMinY() + rect.getHeight()) / scale + pad;

		// clamp to the node's own bounds
		minX = Math.max(minX, bil.getMinX());
		minY = Math.max(minY, bil.getMinY());
		maxX = Math.min(maxX, bil.getMaxX());
		maxY = Math.min(maxY, bil.getMaxY());
		if (maxX <= minX || maxY <= minY) return null;

		return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
	}

	public static Rectangle2D computeContentBoundsLocal(Node root) {
		if (root == null || root.getScene() == null) return null;

		// Ensure CSS/layout is up-to-date for the entire subtree
		root.applyCss();
		if (root instanceof Parent parent) {
			parent.layout();
		}

		double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

		for (Node n : allDescendants(root)) {
			if (!n.isVisible() || n.getOpacity() <= 1e-6) continue;

			Bounds b = bestBounds(n);
			if (b == null || b.getWidth() <= 0 || b.getHeight() <= 0) continue;

			// Convert those bounds to scene coords, then back to root local via 4 corners
			Bounds bScene = n.localToScene(b);

			Point2D p1 = root.sceneToLocal(bScene.getMinX(), bScene.getMinY());
			Point2D p2 = root.sceneToLocal(bScene.getMaxX(), bScene.getMinY());
			Point2D p3 = root.sceneToLocal(bScene.getMaxX(), bScene.getMaxY());
			Point2D p4 = root.sceneToLocal(bScene.getMinX(), bScene.getMaxY());

			double loX = Math.min(Math.min(p1.getX(), p2.getX()), Math.min(p3.getX(), p4.getX()));
			double loY = Math.min(Math.min(p1.getY(), p2.getY()), Math.min(p3.getY(), p4.getY()));
			double hiX = Math.max(Math.max(p1.getX(), p2.getX()), Math.max(p3.getX(), p4.getX()));
			double hiY = Math.max(Math.max(p1.getY(), p2.getY()), Math.max(p3.getY(), p4.getY()));

			minX = Math.min(minX, loX);
			minY = Math.min(minY, loY);
			maxX = Math.max(maxX, hiX);
			maxY = Math.max(maxY, hiY);
		}

		if (!Double.isFinite(minX) || !Double.isFinite(minY)) return null;
		return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
	}

	/**
	 * Choose bounds that best reflect rendered geometry.
	 * For text, boundsInParent is often more reliable after layout.
	 */
	private static Bounds bestBounds(Node n) {
		// For text nodes, prefer layout-derived bounds
		if (n instanceof TextFlow || n instanceof Labeled || n instanceof Text) {
			Bounds bp = n.getBoundsInParent();
			if (bp != null && bp.getWidth() > 0 && bp.getHeight() > 0) {
				// boundsInParent is in parent coords; convert to local-ish by using boundsInLocal with transforms later.
				// But we pass bounds to localToScene(...) which expects LOCAL bounds.
				// So for these nodes we use layoutBounds (local) and rely on localToScene transform.
				Bounds lb = n.getLayoutBounds();
				if (lb != null && lb.getWidth() > 0 && lb.getHeight() > 0) return lb;
				Bounds bl = n.getBoundsInLocal();
				if (bl != null && bl.getWidth() > 0 && bl.getHeight() > 0) return bl;
			}
			Bounds lb = n.getLayoutBounds();
			if (lb != null && lb.getWidth() > 0 && lb.getHeight() > 0) return lb;
		}

		// Default: boundsInLocal is fine for shapes/groups after layout
		Bounds bl = n.getBoundsInLocal();
		if (bl != null && bl.getWidth() > 0 && bl.getHeight() > 0) return bl;

		// Fallback to layoutBounds
		Bounds lb = n.getLayoutBounds();
		if (lb != null && lb.getWidth() > 0 && lb.getHeight() > 0) return lb;

		return null;
	}

	private static List<Node> allDescendants(Node root) {
		var list = new ArrayList<Node>();
		var stack = new ArrayDeque<Node>();
		stack.push(root);
		list.add(root);
		while (!stack.isEmpty()) {
			var v = stack.pop();
			if (v instanceof Parent parent) {
				for (var ch : parent.getChildrenUnmodifiable()) {
					list.add(ch);
					if (ch instanceof Parent cp) stack.push(cp);
				}
			}
		}
		return list;
	}
}