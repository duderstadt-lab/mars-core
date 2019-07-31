package de.mpg.biochem.mars.molecule;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.table.MarsTable;

public class SingleMoleculeArchive extends AbstractMoleculeArchive<SingleMolecule, SdmmImageMetadata, SingleMoleculeArchiveProperties> {
	
	public SingleMoleculeArchive(String name) {
		super(name);
	}
	
	public SingleMoleculeArchive(File file) throws IOException, JsonParseException {
		super(file);
	}
	
	public SingleMoleculeArchive(String name, MarsTable table, MoleculeArchiveService moleculeArchiveService) {
		super(name, table, moleculeArchiveService);
	}
	
	public SingleMoleculeArchive(String name, File file, MoleculeArchiveService moleculeArchiveService) throws JsonParseException, IOException {
		super(name, file, moleculeArchiveService);
	}
	
	public SingleMoleculeArchiveProperties createProperties() {
		return new SingleMoleculeArchiveProperties();
	}
	
	public SingleMoleculeArchiveProperties createProperties(JsonParser jParser) throws IOException {
		return new SingleMoleculeArchiveProperties(jParser);
	}
	
	public SdmmImageMetadata createImageMetadata(JsonParser jParser) throws IOException {
		return new SdmmImageMetadata(jParser);
	}
	
	public SdmmImageMetadata createImageMetadata(String metaUID) {
		return new SdmmImageMetadata(metaUID);
	}
	
	public SingleMolecule createMolecule() {
		return new SingleMolecule();
	}
	
	public SingleMolecule createMolecule(JsonParser jParser) throws IOException {
		return new SingleMolecule(jParser);
	}
	
	public SingleMolecule createMolecule(String UID) {
		return new SingleMolecule(UID);
	}
	
	public SingleMolecule createMolecule(String UID, MarsTable table) {
		return new SingleMolecule(UID, table);
	}
}
