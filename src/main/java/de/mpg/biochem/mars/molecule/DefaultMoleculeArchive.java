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
	
	protected DefaultMoleculeArchiveProperties createProperties() {
		return new DefaultMoleculeArchiveProperties();
	}
	
	protected DefaultMoleculeArchiveProperties createProperties(JsonParser jParser) throws IOException {
		return new DefaultMoleculeArchiveProperties(jParser);
	}
	
	protected DefaultMarsImageMetadata createImageMetadata(JsonParser jParser) throws IOException {
		return new DefaultMarsImageMetadata(jParser);
	}
	
	protected DefaultMarsImageMetadata createImageMetadata(String metaUID) {
		return new DefaultMarsImageMetadata(metaUID);
	}
	
	protected DefaultMolecule createMolecule() {
		return new DefaultMolecule();
	}
	
	protected DefaultMolecule createMolecule(JsonParser jParser) throws IOException {
		return new DefaultMolecule(jParser);
	}
	
	protected DefaultMolecule createMolecule(String UID) {
		return new DefaultMolecule(UID);
	}
	
	protected DefaultMolecule createMolecule(String UID, MarsTable table) {
		return new DefaultMolecule(UID, table);
	}

}
