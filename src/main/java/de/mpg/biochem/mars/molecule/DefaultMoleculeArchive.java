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

package de.mpg.biochem.mars.molecule;

import com.fasterxml.jackson.core.JsonParser;
import de.mpg.biochem.mars.io.MoleculeArchiveSource;
import de.mpg.biochem.mars.io.MoleculeArchiveVirtualSource;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEUtils;
import de.mpg.biochem.mars.metadata.OLDMarsMetadata;
import de.mpg.biochem.mars.molecule.commands.ImportVirtualStoreCommand;
import de.mpg.biochem.mars.table.MarsTable;

import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * Default implementation of {@link AbstractMoleculeArchive}.
 * 
 * @author Karl Duderstadt
 */
public class DefaultMoleculeArchive extends
	AbstractMoleculeArchive<DefaultMolecule, MarsOMEMetadata, DefaultMoleculeArchiveProperties, DefaultMoleculeArchiveIndex>
{

	/**
	 * Creates an empty DefaultMoleculeArchive with the given name.
	 * 
	 * @param name Name of the empty DefaultMoleculeArchive to create.
	 */
	public DefaultMoleculeArchive(String name) {
		super(name);
	}

	/**
	 * Constructor for loading a MoleculeArchive. A yama file can be given or a
	 * yama virtual store directory. Virtual mode will automatically be activated
	 * if a directory is provided.
	 * <p>
	 * MoleculeArchives should typically be opened using the
	 * {@link ImportVirtualStoreCommand}, which automatically detect the type and
	 * open the archive accordingly.
	 * <p>
	 * 
	 * @param file The file or directory to load the archive from.
	 * @throws IOException if there is a problem with the file location.
	 */
	public DefaultMoleculeArchive(File file) throws IOException {
		super(file);
	}

	/**
	 * Constructor for loading a MoleculeArchive. A yama file can be given or a
	 * yama virtual store directory. Virtual mode will automatically be activated
	 * if a directory is provided. If the MoleculeArchiveService is provided the
	 * statusService will be retrieved and when working in Fiji the progress shows
	 * up in the bar as molecule records are loaded.
	 * <p>
	 * MoleculeArchives should typically be opened using the
	 * {@link ImportVirtualStoreCommand}, which automatically detect the type and
	 * open the archive accordingly.
	 * <p>
	 * 
	 * @param name The name of the archive.
	 * @param file The file or directory to load the archive from.
	 * @throws IOException if there is a problem with the file provided.
	 */
	public DefaultMoleculeArchive(String name, File file)
		throws IOException
	{
		super(name, file);
	}

	/**
	 * Constructor for building a molecule archive from a MarsTable. The table
	 * provided must contain a molecule column. The integer values in the molecule
	 * column determine the grouping for creation of molecule records. Status will
	 * be reported during processing by retrieving the StatusService from the
	 * MoleculeArchiveService instance.
	 * 
	 * @param name The name of the archive.
	 * @param table A MarsTable to build the archive from.
	 */
	public DefaultMoleculeArchive(String name, MarsTable table) {
		super(name, table);
	}

	/**
	 * Constructor for loading a MoleculeArchive. A yama file can be given or a
	 * yama virtual store directory. Virtual mode will automatically be activated
	 * if a directory is provided.
	 *
	 * @param uri The URI to load the archive from.
	 * @throws IOException if there is a problem with the file location.
	 */
	public DefaultMoleculeArchive(URI uri) throws
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
	public DefaultMoleculeArchive(MoleculeArchiveSource source) throws
			IOException
	{
		super(source);
	}

	/**
	 * Constructor for loading a MoleculeArchive from a MoleculeArchiveVirtualSource.
	 *
	 * @param virtualSource The MoleculeArchiveVirtualSource to load the archive from.
	 * @throws IOException if there is a problem with the file location.
	 */
	public DefaultMoleculeArchive(MoleculeArchiveVirtualSource virtualSource) throws
			IOException
	{
		super(virtualSource);
	}

	/**
	 * Create empty DefaultMoleculeArchiveProperties record.
	 */
	public DefaultMoleculeArchiveProperties createProperties() {
		return new DefaultMoleculeArchiveProperties();
	}

	/**
	 * Create DefaultMoleculeArchiveProperties record using JsonParser stream.
	 */
	public DefaultMoleculeArchiveProperties createProperties(JsonParser jParser)
		throws IOException
	{
		return new DefaultMoleculeArchiveProperties(jParser);
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
	 * Create empty DefaultMarsImageMetadata record with the metaUID specified.
	 */
	public MarsOMEMetadata createMetadata(String metaUID) {
		return new MarsOMEMetadata(metaUID);
	}

	/**
	 * Create empty DefaultMolecule record.
	 */
	public DefaultMolecule createMolecule() {
		return new DefaultMolecule();
	}

	/**
	 * Create DefaultMolecule record using the JsonParser stream given.
	 */
	public DefaultMolecule createMolecule(JsonParser jParser) throws IOException {
		return new DefaultMolecule(jParser);
	}

	/**
	 * Create empty DefaultMolecule record with the UID specified.
	 */
	public DefaultMolecule createMolecule(String UID) {
		return new DefaultMolecule(UID);
	}

	/**
	 * Create DefaultMolecule record using the UID and {@link MarsTable}
	 * specified.
	 */
	public DefaultMolecule createMolecule(String UID, MarsTable table) {
		return new DefaultMolecule(UID, table);
	}

	@Override
	public DefaultMoleculeArchiveIndex createIndex() {
		return new DefaultMoleculeArchiveIndex();
	}

	@Override
	public DefaultMoleculeArchiveIndex createIndex(JsonParser jParser)
		throws IOException
	{
		return new DefaultMoleculeArchiveIndex(jParser);
	}
}
