package de.mpg.biochem.mars.molecule;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;

public class DefaultMoleculeArchiveProperties extends AbstractMoleculeArchiveProperties {
	//There is no implementation. 
	public DefaultMoleculeArchiveProperties() {
		super();
	}
	
	public DefaultMoleculeArchiveProperties(JsonParser jParser) throws IOException {
		super(jParser);
	}
	
	@Override
	public void merge(MoleculeArchiveProperties properties, String archiveName) {
		super.merge(properties, archiveName);
	}
}
