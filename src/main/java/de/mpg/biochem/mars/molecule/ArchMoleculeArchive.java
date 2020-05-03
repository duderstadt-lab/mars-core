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

import org.scijava.Context;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.metadata.*;

public class ArchMoleculeArchive extends AbstractMoleculeArchive<ArchMolecule, MarsOMEMetadata, SingleMoleculeArchiveProperties> {
	
	public ArchMoleculeArchive(final Context context, String name) {
		super(context, name);
	}
	
	public ArchMoleculeArchive(final Context context, File file) throws IOException, JsonParseException {
		super(context, file);
	}
	
	public ArchMoleculeArchive(final Context context, String name, MarsTable table) {
		super(context, name, table);
	}
	
	public ArchMoleculeArchive(final Context context, String name, File file) throws JsonParseException, IOException {
		super(context, name, file);
	}
	
	public SingleMoleculeArchiveProperties createProperties() {
		return new SingleMoleculeArchiveProperties();
	}
	
	public SingleMoleculeArchiveProperties createProperties(JsonParser jParser) throws IOException {
		return new SingleMoleculeArchiveProperties(jParser);
	}
	
	public MarsOMEMetadata createMetadata(final Context context, JsonParser jParser) throws IOException {
		return new MarsOMEMetadata(context, jParser);
	}
	
	public MarsOMEMetadata createMetadata(final Context context, String metaUID) {
		return new MarsOMEMetadata(context, metaUID);
	}
	
	public ArchMolecule createMolecule() {
		return new ArchMolecule();
	}
	
	public ArchMolecule createMolecule(JsonParser jParser) throws IOException {
		return new ArchMolecule(jParser);
	}
	
	public ArchMolecule createMolecule(String UID) {
		return new ArchMolecule(UID);
	}
	
	public ArchMolecule createMolecule(String UID, MarsTable table) {
		return new ArchMolecule(UID, table);
	}
}
