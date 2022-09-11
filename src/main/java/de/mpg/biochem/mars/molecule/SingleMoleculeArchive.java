/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2022 Karl Duderstadt
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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;

import java.io.File;
import java.io.IOException;

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEUtils;
import de.mpg.biochem.mars.metadata.OLDMarsMetadata;
import de.mpg.biochem.mars.molecule.commands.ImportVirtualStoreCommand;
import de.mpg.biochem.mars.table.MarsTable;

/**
 * Implementation of {@link AbstractMoleculeArchive} for default archives used
 * for routine single molecule time-series datasets composed of
 * {@link SingleMolecule} molecule records, {@link MarsMetadata} metadata
 * records, and {@link SingleMoleculeArchiveProperties} archive properties.
 * <p>
 * For a more extensive explanation of uses and features of molecule archives
 * see {@link AbstractMoleculeArchive}.
 * <p>
 * 
 * @author Karl Duderstadt
 */
public class SingleMoleculeArchive extends
	AbstractMoleculeArchive<SingleMolecule, MarsOMEMetadata, SingleMoleculeArchiveProperties, SingleMoleculeArchiveIndex>
{

	/**
	 * Creates an empty SingleMoleculeArchive with the given name.
	 * 
	 * @param name Name of the empty SingleMoleculeArchive to create.
	 */
	public SingleMoleculeArchive(String name) {
		super(name);
	}

	/**
	 * Constructor for loading a SingleMoleculeArchive. A yama file can be given
	 * or a yama virtual store directory. Virtual mode will automatically be
	 * activated if a directory is provided.
	 * <p>
	 * MoleculeArchives should typically be opened using the
	 * {@link ImportVirtualStoreCommand}, which automatically detect the type and
	 * open the archive accordingly.
	 * <p>
	 * 
	 * @param file The file or directory to load the archive from.
	 * @throws JsonParseException if there is a problem parsing the file provided.
	 * @throws IOException if there is a problem with the file location.
	 */
	public SingleMoleculeArchive(File file) throws IOException,
		JsonParseException
	{
		super(file);
	}

	/**
	 * Constructor for loading a SingleMoleculeArchive. A yama file can be given
	 * or a yama virtual store directory. Virtual mode will automatically be
	 * activated if a directory is provided. If the MoleculeArchiveService is
	 * provided the statusService will be retrieved and when working in Fiji the
	 * progress shows up in the bar as molecule records are loaded.
	 * <p>
	 * MoleculeArchives should typically be opened using the
	 * {@link ImportVirtualStoreCommand}, which automatically detect the type and
	 * open the archive accordingly.
	 * <p>
	 * 
	 * @param name The name of the archive.
	 * @param file The file or directory to load the archive from.
	 * @throws JsonParseException if there is a parsing exception.
	 * @throws IOException if there is a problem with the file provided.
	 */
	public SingleMoleculeArchive(String name, File file)
		throws JsonParseException, IOException
	{
		super(name, file);
	}

	/**
	 * Constructor for building a Molecule Archive from a MarsTable. The
	 * Molecule Archive will contain one Molecule record with the table
	 * provided.
	 * 
	 * @param name The name of the archive.
	 * @param table A MarsTable to build the archive from.
	 */
	public SingleMoleculeArchive(String name, MarsTable table) {
		super(name, table);
	}
	
	/**
	 * Constructor for building a molecule archive from a MarsTable. The table
	 * provided must contain a column for the molecule index. The integer values in the index
	 * column determine the grouping for creation of molecule records. Status will
	 * be reported during processing by retrieving the StatusService from the
	 * MoleculeArchiveService instance.
	 * 
	 * @param name The name of the archive.
	 * @param table A MarsTable to build the archive from.
	 * @param indexColumnName Molecule index column. 
	 */
	public SingleMoleculeArchive(String name, MarsTable table, String indexColumnName) {
		super(name, table, indexColumnName);
	}

	/**
	 * Create empty SingleMoleculeArchiveProperties record.
	 */
	@Override
	public SingleMoleculeArchiveProperties createProperties() {
		return new SingleMoleculeArchiveProperties();
	}

	/**
	 * Create SingleMoleculeArchiveProperties record using JsonParser stream.
	 */
	public SingleMoleculeArchiveProperties createProperties(JsonParser jParser)
		throws IOException
	{
		return new SingleMoleculeArchiveProperties(jParser);
	}

	/**
	 * Create MarsOMEMetadata record using JsonParser stream.
	 */
	public MarsOMEMetadata createMetadata(JsonParser jParser) throws IOException {
		if (properties().getInputSchema() == null) return MarsOMEUtils
			.translateToMarsOMEMetadata(new OLDMarsMetadata(jParser));
		else return new MarsOMEMetadata(jParser);
	}

	/**
	 * Create empty MarsOMEMetadata record with the metaUID specified.
	 */
	public MarsOMEMetadata createMetadata(String metaUID) {
		return new MarsOMEMetadata(metaUID);
	}

	/**
	 * Create empty SingleMolecule record.
	 */
	public SingleMolecule createMolecule() {
		return new SingleMolecule();
	}

	/**
	 * Create SingleMolecule record using the JsonParser stream given.
	 */
	public SingleMolecule createMolecule(JsonParser jParser) throws IOException {
		return new SingleMolecule(jParser);
	}

	/**
	 * Create empty SingleMolecule record with the UID specified.
	 */
	public SingleMolecule createMolecule(String UID) {
		return new SingleMolecule(UID);
	}

	/**
	 * Create SingleMolecule record using the UID and {@link MarsTable} specified.
	 */
	public SingleMolecule createMolecule(String UID, MarsTable table) {
		return new SingleMolecule(UID, table);
	}

	@Override
	public SingleMoleculeArchiveIndex createIndex() {
		return new SingleMoleculeArchiveIndex();
	}

	@Override
	public SingleMoleculeArchiveIndex createIndex(JsonParser jParser)
		throws IOException
	{
		return new SingleMoleculeArchiveIndex(jParser);
	}
}
