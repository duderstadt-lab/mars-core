/*******************************************************************************
 * Copyright (C) 2019, Duderstadt Lab
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
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
