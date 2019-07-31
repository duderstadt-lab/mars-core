package de.mpg.biochem.mars.molecule;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;

public class SingleMoleculeArchiveProperties extends AbstractMoleculeArchiveProperties {
	
	public SingleMoleculeArchiveProperties() {
		super();
	}
	
	public SingleMoleculeArchiveProperties(JsonParser jParser) throws IOException {
		super(jParser);
	}

	@Override
	public void merge(MoleculeArchiveProperties properties, String archiveName) {
		super.merge(properties, archiveName);
	}
}