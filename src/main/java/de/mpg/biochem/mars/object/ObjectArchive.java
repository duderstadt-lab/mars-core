/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2023 Karl Duderstadt
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

import com.fasterxml.jackson.core.JsonParser;
import de.mpg.biochem.mars.io.MoleculeArchiveSource;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.molecule.AbstractMoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.stream.Stream;

public class ObjectArchive extends
	AbstractMoleculeArchive<MartianObject, MarsOMEMetadata, ObjectArchiveProperties, ObjectArchiveIndex>
{

	public ObjectArchive(String name) {
		super(name);
	}

	public ObjectArchive(File file) throws IOException {
		super(file);
	}

	public ObjectArchive(String name, MarsTable table) {
		super(name, table);
	}

	public ObjectArchive(String name, File file) throws
			IOException
	{
		super(name, file);
	}

	/**
	 * Constructor for loading a MoleculeArchive. A yama file can be given or a
	 * yama virtual store directory. Virtual mode will automatically be activated
	 * if a directory is provided.
	 *
	 * @param uri The URI to load the archive from.
	 * @throws IOException if there is a problem with the file location.
	 */
	public ObjectArchive(URI uri) throws
			IOException
	{
		super(uri);
	}

	/**
	 * Constructor for loading a MoleculeArchive from a MoleculeArchiveSource.
	 *
	 * @param source The MoleculeArchiveSource to load the archive from.
	 * @throws IOException if there is a problem with the file location.
	 */
	public ObjectArchive(MoleculeArchiveSource source) throws
			IOException
	{
		super(source);
	}

	public Stream<MartianObject> objects() {
		return this.moleculeMap.keySet().stream().map(this::get);
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
		return new MarsOMEMetadata(jParser);
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
	public ObjectArchiveIndex createIndex(JsonParser jParser) throws IOException {
		return new ObjectArchiveIndex(jParser);
	}
}
