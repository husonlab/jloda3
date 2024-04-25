# jloda3

This is the third major release of the JLODA java library of data-structures and algorithms, written by Daniel Huson,
2002-2024. It is used in SplitsTree, Dendroscope, PhyloSketch and MEGAN.

The library has been split into multiple parts:

- jloda-core contains core classes that do not use either Swing or JavaFX.
- jloda-swing contains Swing-specific classes
- jloda-fx contains JavaFX-specific classes
- jloda-connect contains code to open a SQLite database file. This is currently isolated because it causes problems when transpiling to iOS

You will need to install the libraries provided here before building SplitsTree6 or MEGAN7.
