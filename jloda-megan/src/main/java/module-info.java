module jloda_megan {
	requires jloda_core;
	requires java.sql;
	requires org.xerial.sqlitejdbc;

	// meganized-DAA reading (step 1-2)
	exports jloda.megan.data;
	exports jloda.megan.daa.connector;
	exports jloda.megan.daa.io;
	exports jloda.megan.io;

	// classification database access: taxonomy/GTDB trees, names, ranks (step 3)
	exports jloda.megan.classification;
	exports jloda.megan.classification.data;
	exports jloda.megan.classification.db;
}
