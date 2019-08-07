package de.mpg.biochem.mars.molecule;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.table.MarsTable;

public class DefaultMoleculeArchive extends AbstractMoleculeArchive<DefaultMolecule, DefaultMarsImageMetadata, DefaultMoleculeArchiveProperties> {
	
	public DefaultMoleculeArchive(String name) {
		super(name);
	}
	
	public DefaultMoleculeArchive(File file) throws IOException, JsonParseException {
		super(file);
	}
	
	public DefaultMoleculeArchive(String name, MarsTable table, MoleculeArchiveService moleculeArchiveService) {
		super(name, table, moleculeArchiveService);
	}
	
	public DefaultMoleculeArchive(String name, File file, MoleculeArchiveService moleculeArchiveService) throws JsonParseException, IOException {
		super(name, file, moleculeArchiveService);
	}
	
	public DefaultMoleculeArchiveProperties createProperties() {
		return new DefaultMoleculeArchiveProperties();
	}
	
	public DefaultMoleculeArchiveProperties createProperties(JsonParser jParser) throws IOException {
		return new DefaultMoleculeArchiveProperties(jParser);
	}
	
	public DefaultMarsImageMetadata createImageMetadata(JsonParser jParser) throws IOException {
		return new DefaultMarsImageMetadata(jParser);
	}
	
	public DefaultMarsImageMetadata createImageMetadata(String metaUID) {
		return new DefaultMarsImageMetadata(metaUID);
	}
	
	public DefaultMolecule createMolecule() {
		return new DefaultMolecule();
	}
	
	public DefaultMolecule createMolecule(JsonParser jParser) throws IOException {
		return new DefaultMolecule(jParser);
	}
	
	public DefaultMolecule createMolecule(String UID) {
		return new DefaultMolecule(UID);
	}
	
	public DefaultMolecule createMolecule(String UID, MarsTable table) {
		return new DefaultMolecule(UID, table);
	}

}
