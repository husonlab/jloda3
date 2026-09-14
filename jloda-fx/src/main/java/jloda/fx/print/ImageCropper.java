package jloda.fx.print;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;

public final class ImageCropper {

	/**
	 * when set, {@link #cropMargins} trims against the image's own background colour rather than against
	 * white, so that content drawn on a coloured background is cropped to the content rather than left
	 * fully extended. Off by default, a program that wants it sets it once at startup
	 */
	public static boolean CROP_TO_BACKGROUND = false;

	/**
	 * the share of the image's outer frame that one colour must cover before it is taken to be the background
	 */
	public static double MIN_BACKGROUND_SHARE = 0.5;

	private ImageCropper() {
	}

	/**
	 * Crop an image by trimming margins, either of white or, when {@link #CROP_TO_BACKGROUND} is set, of the
	 * image's own background colour.
	 *
	 * @param image    input image
	 * @param padding  number of pixels of margin to keep around detected content
	 * @param tol      tolerance in [0..1] on each RGB channel
	 * @param alphaTol tolerance in [0..1] for alpha. Pixels with alpha <= alphaTol are treated as background.
	 * @return cropped image (WritableImage)
	 */
	public static WritableImage cropMargins(Image image, int padding, double tol, double alphaTol) {
		return CROP_TO_BACKGROUND ? cropBackgroundMargins(image, padding, tol, alphaTol)
				: cropWhiteMargins(image, padding, tol, alphaTol);
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
		return cropMarginsOfColor(image, Color.WHITE, padding, whiteTol, alphaTol);
	}

	/**
	 * Crop an image by trimming rows/columns that hold nothing but the image's background colour, then keep a
	 * padding border. The background colour is the one that dominates the image's outer frame, so a spherical
	 * tiling drawn on blue is cropped to the tiling rather than left fully extended. If no single colour
	 * covers {@link #MIN_BACKGROUND_SHARE} of the frame then the content reaches the edge and the image is
	 * returned uncropped.
	 *
	 * @param image    input image
	 * @param padding  number of pixels of background border to keep around detected content
	 * @param tol      tolerance in [0..1]. A pixel is background if each RGB channel is within tol of it
	 * @param alphaTol tolerance in [0..1] for alpha. Pixels with alpha <= alphaTol are treated as background.
	 * @return cropped image (WritableImage)
	 */
	public static WritableImage cropBackgroundMargins(Image image, int padding, double tol, double alphaTol) {
		if (image == null) return null;
		var background = detectBackgroundColor(image, MIN_BACKGROUND_SHARE);
		if (background == null) { // no colour dominates the frame, so there is no margin to trim
			var pr = image.getPixelReader();
			if (pr == null) return null;
			return new WritableImage(pr, 0, 0, (int) Math.round(image.getWidth()), (int) Math.round(image.getHeight()));
		}
		return cropMarginsOfColor(image, background, padding, tol, alphaTol);
	}

	/**
	 * the colour that dominates the image's outer frame, or null if no single colour covers at least minShare
	 * of it. Colours are binned at 32 levels per channel, so that antialiasing does not split the background
	 * across bins, and the colour returned is the mean of its bin. Transparent pixels form a bin of their own,
	 * reported as {@link Color#TRANSPARENT}
	 *
	 * @return background colour, or null
	 */
	public static Color detectBackgroundColor(Image image, double minShare) {
		if (image == null) return null;
		var w = (int) Math.round(image.getWidth());
		var h = (int) Math.round(image.getHeight());
		if (w <= 0 || h <= 0) return null;
		var pr = image.getPixelReader();
		if (pr == null) return null;

		final Map<Integer, double[]> bins = new HashMap<>(); // key -> count, red sum, green sum, blue sum
		var total = 0;
		for (var x = 0; x < w; x++) {
			tally(bins, pr.getColor(x, 0));
			tally(bins, pr.getColor(x, h - 1));
			total += 2;
		}
		for (var y = 1; y < h - 1; y++) {
			tally(bins, pr.getColor(0, y));
			tally(bins, pr.getColor(w - 1, y));
			total += 2;
		}
		if (total == 0) return null;

		var bestKey = 0;
		double[] best = null;
		for (var entry : bins.entrySet()) {
			if (best == null || entry.getValue()[0] > best[0]) {
				bestKey = entry.getKey();
				best = entry.getValue();
			}
		}
		if (best == null || best[0] < minShare * total) return null;
		if (bestKey == TRANSPARENT_BIN) return Color.TRANSPARENT;
		return Color.color(best[1] / best[0], best[2] / best[0], best[3] / best[0]);
	}

	private static final int TRANSPARENT_BIN = -1;

	private static void tally(Map<Integer, double[]> bins, Color c) {
		final int key;
		if (c.getOpacity() == 0)
			key = TRANSPARENT_BIN;
		else key = (((int) (c.getRed() * 255) >> 3) << 10) | (((int) (c.getGreen() * 255) >> 3) << 5) | ((int) (c.getBlue() * 255) >> 3);
		var bin = bins.computeIfAbsent(key, k -> new double[4]);
		bin[0]++;
		bin[1] += c.getRed();
		bin[2] += c.getGreen();
		bin[3] += c.getBlue();
	}

	/**
	 * the tight bounding rectangle, in image pixels, of the content that is neither the background colour
	 * (white, or the detected background when {@link #CROP_TO_BACKGROUND} is set) nor transparent. This is the
	 * same detection that {@link #cropMargins} uses, but returns the rectangle rather than a cropped image, so
	 * that a vector export can crop to exactly what the raster crop would have kept. No padding is applied.
	 *
	 * @return content rectangle in pixels, or null if the image is empty or entirely background
	 */
	public static Rectangle2D contentRectangle(Image image, double tol, double alphaTol) {
		if (image == null) return null;
		var w = (int) Math.round(image.getWidth());
		var h = (int) Math.round(image.getHeight());
		if (w <= 0 || h <= 0) return null;
		var pr = image.getPixelReader();
		if (pr == null) return null;

		Color background = Color.WHITE;
		if (CROP_TO_BACKGROUND) {
			var detected = detectBackgroundColor(image, MIN_BACKGROUND_SHARE);
			if (detected != null) background = detected;
		}

		var top = 0;
		var bottom = h - 1;
		var left = 0;
		var right = w - 1;
		while (top <= bottom && rowIsBackground(pr, w, top, background, tol, alphaTol)) top++;
		while (bottom >= top && rowIsBackground(pr, w, bottom, background, tol, alphaTol)) bottom--;
		while (left <= right && colIsBackground(pr, h, left, background, tol, alphaTol)) left++;
		while (right >= left && colIsBackground(pr, h, right, background, tol, alphaTol)) right--;

		if (top > bottom || left > right) return null; // all background
		return new Rectangle2D(left, top, right - left + 1, bottom - top + 1);
	}

	/**
	 * trims rows and columns that hold nothing but the given colour, or transparency, and keeps a padding border
	 *
	 * @return cropped image. If the whole image is background, returns the original image
	 */
	private static WritableImage cropMarginsOfColor(Image image, Color background, int padding, double tol, double alphaTol) {
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
		while (top <= bottom && rowIsBackground(pr, w, top, background, tol, alphaTol)) top++;
		// Trim bottom
		while (bottom >= top && rowIsBackground(pr, w, bottom, background, tol, alphaTol)) bottom--;
		// Trim left
		while (left <= right && colIsBackground(pr, h, left, background, tol, alphaTol)) left++;
		// Trim right
		while (right >= left && colIsBackground(pr, h, right, background, tol, alphaTol)) right--;

		// If everything is background (or transparent), return original (or return a 1x1 image if preferred)
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

	private static boolean rowIsBackground(PixelReader pr, int width, int y, Color background, double tol, double alphaTol) {
		for (var x = 0; x < width; x++) {
			if (!isBackgroundOrTransparent(pr.getColor(x, y), background, tol, alphaTol)) {
				return false;
			}
		}
		return true;
	}

	private static boolean colIsBackground(PixelReader pr, int height, int x, Color background, double tol, double alphaTol) {
		for (var y = 0; y < height; y++) {
			if (!isBackgroundOrTransparent(pr.getColor(x, y), background, tol, alphaTol)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isBackgroundOrTransparent(Color c, Color background, double tol, double alphaTol) {
		if (c.getOpacity() <= alphaTol) return true; // treat transparent as background
		if (background.getOpacity() == 0) return false; // the background is transparency itself
		if (background.equals(Color.WHITE)) { // white keeps its original, one-sided test
			var minChannel = 1.0 - tol;
			return c.getRed() >= minChannel && c.getGreen() >= minChannel && c.getBlue() >= minChannel;
		}
		return Math.abs(c.getRed() - background.getRed()) <= tol
			   && Math.abs(c.getGreen() - background.getGreen()) <= tol
			   && Math.abs(c.getBlue() - background.getBlue()) <= tol;
	}
}
