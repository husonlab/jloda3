module jloda_fx {
	uses jloda.fx.service.UpdateService;
	requires javafx.controls;
	requires javafx.graphics;
	requires javafx.fxml;
	requires com.google.zxing;

	requires java.desktop;

	requires jloda_core;
	requires jloda_phylogeny;

	requires org.apache.xmlgraphics.batik.anim;
	requires org.apache.xmlgraphics.batik.bridge;
	requires org.apache.xmlgraphics.batik.gvt;
	requires org.apache.xmlgraphics.batik.util;
	requires org.apache.xmlgraphics.fop.core;
	requires org.apache.xmlgraphics.commons;

	exports jloda.fx.dialog;
	exports jloda.fx.colorscale;
	exports jloda.fx.control;
	exports jloda.fx.control.table;
	exports jloda.fx.control.sliderhistogram;

	exports jloda.fx.find;
	exports jloda.fx.graph;
	exports jloda.fx.label;
	exports jloda.fx.message;
	exports jloda.fx.notifications;

	exports jloda.fx.selection;
	exports jloda.fx.service;

	exports jloda.fx.shapes;
	exports jloda.fx.undo;
	exports jloda.fx.util;
	exports jloda.fx.window;
	exports jloda.fx.icons;

	exports jloda.fx.qr;

	opens jloda.fx.colorscale;
	opens jloda.fx.dialog;
	opens jloda.fx.label;
	opens jloda.fx.control.table;
	opens jloda.fx.control.sliderhistogram;
	opens jloda.fx.find;
	opens jloda.fx.message;
	opens jloda.fx.notifications;

	opens jloda.fx.icons;

	opens jloda.resources.css;

	exports jloda.fx.geom;
	exports jloda.fx.workflow;
	exports jloda.fx.selection.rubberband;
	exports jloda.fx.thirdparty;
	exports jloda.fx.windownotifications;

	exports jloda.fx.phylo.embed;
	exports jloda.fx.print;
}