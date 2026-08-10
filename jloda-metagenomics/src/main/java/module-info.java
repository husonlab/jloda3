module jloda_metagenomics {
	requires jloda_core;
	requires java.sql;
	requires org.xerial.sqlitejdbc;

	// meganized-DAA reading (step 1-2)
	exports megan8.data;
	exports megan8.daa.connector;
	exports megan8.daa.io;
	exports megan8.io;

	// classification database access: taxonomy/GTDB trees, names, ranks (step 3)
	exports megan8.classification;
	exports megan8.classification.data;
	exports megan8.classification.db;
}
