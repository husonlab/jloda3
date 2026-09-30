# jloda3

This is the third major release of the JLODA java library of data-structures and algorithms, written by Daniel Huson,
2002-2026. It is used in the CatReNet, Diamer, Dendroscope3, Megan7, PhyloParallelograms, PhyloSketch2, SplitsTree6
and Tegula projects.

The library has been split into multiple parts:

- jloda-core contains core classes that do not use either Swing or JavaFX. New in September 2026:
  `jloda.graph.layout.RectilinearLayout` straightens a drawing of a graph on a grid, so that edges run horizontally
  or vertically where possible and diagonally otherwise, as in hand-drawn haplotype networks. It is used by the
  network view of SplitsTree6 and by PhyloSketch2.
- jloda-swing contains Swing-specific classes
- jloda-fx contains JavaFX-specific classes
- jloda-connect contains code to open a SQLite database file. This is currently isolated because it causes problems when transpiling to iOS
- jloda-megan contains GUI-free code for reading meganized DAA files and for accessing the MEGAN classification
  database (taxonomy and GTDB trees, names and ranks); it is shared with MEGAN

- jloda-phylogeny is a new part (October 2025) in which new algorithms for phylogenetics will be made available
  using minimalistic functional interfaces to allow easy integration into other programs. It contains
  two algorithms based on displacement optimization (DO algorithms),
  one for rooted network layout (`NetworkDisplacementOptimization`) and one for tanglegrams for trees/networks
  (`TanglegramDisplacementOptimization`), and the layout of rooted trees and networks (`LayoutRootedPhylogeny`:
  rectangular, circular, radial or triangular)

You will need to install the libraries provided here before building one of the above-mentioned projects.
