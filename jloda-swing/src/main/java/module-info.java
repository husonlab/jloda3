module jloda_swing {
	requires jloda_core;

	requires java.desktop;

	requires VectorGraphics2D;

	exports jloda.swing.commands;
	exports jloda.swing.director;
	exports jloda.swing.export;
	exports jloda.swing.export.gifEncode;
	exports jloda.swing.find;
	exports jloda.swing.format;
	exports jloda.swing.graphview;
	exports jloda.swing.message;
	exports jloda.swing.util;
	exports jloda.swing.util.lang;

	exports jloda.swing.window;
}