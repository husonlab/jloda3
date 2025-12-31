package jloda.fx.print;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

public final class ImageCropper {

	private ImageCropper() {
	}

	/**
	 * Crop an image by trimming fully-white rows/columns from the outside in, then keep a padding border.
	 *
	 * @param image    input image
	 * @param padding  number of pixels of white border to keep around detected content
	 * @param whiteTol tolerance in [0..1]. A pixel is "white" if each RGB channel >= 1 - whiteTol
	 *                 Example: 0.02 is strict, 0.08 is more forgiving.
	 * @param alphaTol tolerance in [0..1] for alpha. Pixels with alpha <= alphaTol are treated as white/background.
	 * @return cropped image (WritableImage). If image is all white, returns original image.
	 */
	public static WritableImage cropWhiteMargins(Image image, int padding, double whiteTol, double alphaTol) {
		if (image == null) return null;

		var w = (int) Math.round(image.getWidth());
		var h = (int) Math.round(image.getHeight());
		if (w <= 0 || h <= 0) return null;

		var pr = image.getPixelReader();
		if (pr == null) return null;

		// Scan bounds
		var top = 0;
		var bottom = h - 1;
		var left = 0;
		var right = w - 1;

		// Trim top
		while (top <= bottom && rowIsWhite(pr, w, top, whiteTol, alphaTol)) top++;
		// Trim bottom
		while (bottom >= top && rowIsWhite(pr, w, bottom, whiteTol, alphaTol)) bottom--;
		// Trim left
		while (left <= right && colIsWhite(pr, h, left, whiteTol, alphaTol)) left++;
		// Trim right
		while (right >= left && colIsWhite(pr, h, right, whiteTol, alphaTol)) right--;

		// If everything is white (or transparent), return original (or return a 1x1 white image if preferred)
		if (top > bottom || left > right) {
			return new WritableImage(pr, 0, 0, w, h);
		}

		// Apply padding
		var x0 = Math.max(0, left - padding);
		var y0 = Math.max(0, top - padding);
		var x1 = Math.min(w - 1, right + padding);
		var y1 = Math.min(h - 1, bottom + padding);

		var cw = x1 - x0 + 1;
		var ch = y1 - y0 + 1;

		return new WritableImage(pr, x0, y0, cw, ch);
	}

	private static boolean rowIsWhite(PixelReader pr, int width, int y, double whiteTol, double alphaTol) {
		var minChannel = 1.0 - whiteTol;
		for (var x = 0; x < width; x++) {
			var c = pr.getColor(x, y);
			if (!isWhiteOrTransparent(c, minChannel, alphaTol)) {
				return false;
			}
		}
		return true;
	}

	private static boolean colIsWhite(PixelReader pr, int height, int x, double whiteTol, double alphaTol) {
		var minChannel = 1.0 - whiteTol;
		for (var y = 0; y < height; y++) {
			var c = pr.getColor(x, y);
			if (!isWhiteOrTransparent(c, minChannel, alphaTol)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isWhiteOrTransparent(Color c, double minChannel, double alphaTol) {
		if (c.getOpacity() <= alphaTol) return true; // treat transparent as background
		return c.getRed() >= minChannel && c.getGreen() >= minChannel && c.getBlue() >= minChannel;
	}
}