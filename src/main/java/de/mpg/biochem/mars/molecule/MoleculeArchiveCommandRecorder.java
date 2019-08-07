package de.mpg.biochem.mars.molecule;

public interface MoleculeArchiveCommandRecorder {
	public MoleculeArchive<Molecule, MarsImageMetadata, MoleculeArchiveProperties> getArchive();
}
