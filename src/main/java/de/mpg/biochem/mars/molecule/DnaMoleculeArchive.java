/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2020 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.mpg.biochem.mars.molecule;

import java.io.File;
import java.io.IOException;

import org.scijava.Context;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEUtils;
import de.mpg.biochem.mars.metadata.OLDMarsMetadata;
import de.mpg.biochem.mars.table.MarsTable;

public class DnaMoleculeArchive extends AbstractMoleculeArchive<DnaMolecule, MarsOMEMetadata, DnaMoleculeArchiveProperties, DnaMoleculeArchiveIndex> {
	
	public DnaMoleculeArchive(String name) {
		super(name);
	}
	
	public DnaMoleculeArchive(File file) throws IOException, JsonParseException {
		super(file);
	}
	
	public DnaMoleculeArchive(String name, MarsTable table) {
		super(name, table);
	}
	
	public DnaMoleculeArchive(String name, File file) throws JsonParseException, IOException {
		super(name, file);
	}
	
	public DnaMoleculeArchiveProperties createProperties() {
		return new DnaMoleculeArchiveProperties();
	}
	
	public DnaMoleculeArchiveProperties createProperties(JsonParser jParser) throws IOException {
		return new DnaMoleculeArchiveProperties(jParser);
	}
	
	/**
	 * Create MarsOMEMetadata record using JsonParser stream.
	 */
	public MarsOMEMetadata createMetadata(JsonParser jParser) throws IOException {
		if (properties().getInputSchema() == null)
			return MarsOMEUtils.translateDNAMetadataToMarsOMEMetadata(new OLDMarsMetadata(jParser));
		else
			return new MarsOMEMetadata(jParser);
	}
	
	public MarsOMEMetadata createMetadata(String metaUID) {
		return new MarsOMEMetadata(metaUID);
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

	@Override
	public DnaMoleculeArchiveIndex createIndex() {
		return new DnaMoleculeArchiveIndex();
	}

	@Override
	public DnaMoleculeArchiveIndex createIndex(JsonParser jParser) throws IOException {
		return new DnaMoleculeArchiveIndex(jParser);
	}
}
