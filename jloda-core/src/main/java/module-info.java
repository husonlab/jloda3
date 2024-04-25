module jloda_core {
	requires java.desktop;

	exports jloda.graph;
	exports jloda.graph.io;
	exports jloda.graph.algorithms;
	exports jloda.graph.fmm;

	exports jloda.phylo;
	exports jloda.util;
	exports jloda.util.interval;
	exports jloda.util.parse;
	exports jloda.util.progress;

	exports jloda.seq;
	exports jloda.thirdparty;
	exports jloda.resources;
	opens jloda.resources.icons;
	opens jloda.resources.icons.dialog;
	opens jloda.resources.icons.sun;
	exports jloda.kmers;
	exports jloda.kmers.bloomfilter;
	exports jloda.kmers.mash;
	exports jloda.phylo.algorithms;

	exports Jama;
	exports Jama.util;
}