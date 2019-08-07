package de.mpg.biochem.mars.molecule;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.table.MarsTable;

public class DnaMoleculeArchive extends AbstractMoleculeArchive<DnaMolecule, SdmmImageMetadata, SingleMoleculeArchiveProperties> {
	
	public DnaMoleculeArchive(String name) {
		super(name);
	}
	
	public DnaMoleculeArchive(File file) throws IOException, JsonParseException {
		super(file);
	}
	
	public DnaMoleculeArchive(String name, MarsTable table, MoleculeArchiveService moleculeArchiveService) {
		super(name, table, moleculeArchiveService);
	}
	
	public DnaMoleculeArchive(String name, File file, MoleculeArchiveService moleculeArchiveService) throws JsonParseException, IOException {
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
	
	public DnaMolecule createMolecule() {
		return new DnaMolecule();
	}
	
	public DnaMolecule createMolecule(JsonParser jParser) throws IOException {
		return new DnaMolecule(jParser);
	}
	
	public DnaMolecule createMolecule(String UID) {
		return new DnaMolecule(UID);
	}
	
	public DnaMolecule createMolecule(String UID, MarsTable table) {
		return new DnaMolecule(UID, table);
	}
}
