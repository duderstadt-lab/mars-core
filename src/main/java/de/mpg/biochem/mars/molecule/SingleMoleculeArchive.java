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
	
	protected SingleMoleculeArchiveProperties createProperties() {
		return new SingleMoleculeArchiveProperties();
	}
	
	protected SingleMoleculeArchiveProperties createProperties(JsonParser jParser) throws IOException {
		return new SingleMoleculeArchiveProperties(jParser);
	}
	
	protected SdmmImageMetadata createImageMetadata(JsonParser jParser) throws IOException {
		return new SdmmImageMetadata(jParser);
	}
	
	protected SdmmImageMetadata createImageMetadata(String metaUID) {
		return new SdmmImageMetadata(metaUID);
	}
	
	protected SingleMolecule createMolecule() {
		return new SingleMolecule();
	}
	
	protected SingleMolecule createMolecule(JsonParser jParser) throws IOException {
		return new SingleMolecule(jParser);
	}
	
	protected SingleMolecule createMolecule(String UID) {
		return new SingleMolecule(UID);
	}
	
	protected SingleMolecule createMolecule(String UID, MarsTable table) {
		return new SingleMolecule(UID, table);
	}
	
	public String getSingleMolecule() {
		return "I am a SingleMolecule Archive";
	}
}
