package de.mpg.biochem.mars.molecule;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.table.MarsTable;

public class DefaultMolecule extends AbstractMolecule {

	public DefaultMolecule() {
		super();
	}
	
	public DefaultMolecule(JsonParser jParser) throws IOException {
		super(jParser);
	}
	
	public DefaultMolecule(String UID) {
		super(UID);
	}

	public DefaultMolecule(String UID, MarsTable dataTable) {
		super(UID, dataTable);
	}
}
