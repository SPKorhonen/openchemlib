# OpenChemLib
# Open Source Chemistry Library

## Fractal dimension
The fractal dimension program calculates the fractal dimension for molecules. The main class is
com.actelion.research.chem.properties.fractaldimension.FractalDimensionMoleculeMain.java

### SMILES code for five molecules
C(C(C1)C2)C3CC2CC1C3
O=C(C[C@@H]1OCC=C2[C@H](C3)[C@@H]1[C@H]1[C@@]4(CC5)[C@H]3N5C2)N1c1c4cccc1
Cc(ccc(Cl)c1)c1N(CC1)CCN1C(C(C1)COc(cc2)c1cc2OC)=O
CCCCC/C=C\C/C=C\C/C=C\C/C=C\CCCC(NCCO)=O
OCC(C(C(C1O)O)O)OC1O

### Java command line example to run the calculation on Linux
java -server -Xmx1g -classpath openChemLib.jar com.actelion.research.chem.properties.fractaldimension.FractalDimensionMoleculeMain $*

