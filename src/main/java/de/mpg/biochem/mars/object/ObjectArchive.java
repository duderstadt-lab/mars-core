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

package de.mpg.biochem.mars.object;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.metadata.*;
import de.mpg.biochem.mars.molecule.AbstractMoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;

public class ObjectArchive extends
	AbstractMoleculeArchive<MartianObject, MarsOMEMetadata, ObjectArchiveProperties, ObjectArchiveIndex>
{

	public ObjectArchive(String name) {
		super(name);
	}

	public ObjectArchive(File file) throws IOException, JsonParseException {
		super(file);
	}

	public ObjectArchive(String name, MarsTable table) {
		super(name, table);
	}

	public ObjectArchive(String name, File file) throws JsonParseException,
		IOException
	{
		super(name, file);
	}

	public ObjectArchiveProperties createProperties() {
		return new ObjectArchiveProperties();
	}

	public ObjectArchiveProperties createProperties(JsonParser jParser)
		throws IOException
	{
		return new ObjectArchiveProperties(jParser);
	}

	/**
	 * Create MarsOMEMetadata record using JsonParser stream.
	 */
	public MarsOMEMetadata createMetadata(JsonParser jParser) throws IOException {
		if (properties().getInputSchema() == null) return MarsOMEUtils
			.translateToMarsOMEMetadata(new OLDMarsMetadata(jParser));
		else return new MarsOMEMetadata(jParser);
	}

	public MarsOMEMetadata createMetadata(String metaUID) {
		return new MarsOMEMetadata(metaUID);
	}

	public MartianObject createMolecule() {
		return new MartianObject();
	}

	public MartianObject createMolecule(JsonParser jParser) throws IOException {
		return new MartianObject(jParser);
	}

	public MartianObject createMolecule(String UID) {
		return new MartianObject(UID);
	}

	public MartianObject createMolecule(String UID, MarsTable table) {
		return new MartianObject(UID, table);
	}

	@Override
	public ObjectArchiveIndex createIndex() {
		return new ObjectArchiveIndex();
	}

	@Override
	public ObjectArchiveIndex createIndex(JsonParser jParser)
		throws IOException
	{
		return new ObjectArchiveIndex(jParser);
	}
}
