package de.mpg.biochem.mars.molecule;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.table.MarsTable;

public class DnaMolecule extends AbstractMolecule {

	public DnaMolecule() {
		super();
	}
	
	public DnaMolecule(JsonParser jParser) throws IOException {
		super(jParser);
	}
	
	public DnaMolecule(String UID) {
		super(UID);
	}

	public DnaMolecule(String UID, MarsTable dataTable) {
		super(UID, dataTable);
	}
}
