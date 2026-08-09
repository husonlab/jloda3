module jloda_metagenomics {
	requires jloda_core;
	requires java.sql;
	requires org.xerial.sqlitejdbc;

	// exports are added as packages are populated (step 1-2: DAA reading)
	exports megan8.data;
	exports megan8.daa.connector;
	exports megan8.daa.io;
	exports megan8.io;
}
