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
import de.mpg.biochem.mars.molecule.commands.*;

/**
 * Implementation of {@link AbstractMoleculeArchive} for default archives used for
 * routine single molecule time-series datasets composed of {@link SingleMolecule} 
 * molecule records, {@link SdmmImageMetadata} metadata records, and 
 * {@link SingleMoleculeArchiveProperties} archive properties.
 * <p>
 * For a more extensive explanation of uses and features of molecule archives see 
 * {@link AbstractMoleculeArchive}.
 * <p>
 * @author Karl Duderstadt
 */
public class SingleMoleculeArchive extends AbstractMoleculeArchive<SingleMolecule, SdmmImageMetadata, SingleMoleculeArchiveProperties> {
	
	/**
	 * Creates an empty SingleMoleculeArchive with the given name. 
	 * 
	 * @param name Name of the empty SingleMoleculeArchive to create.
	 */
	public SingleMoleculeArchive(String name) {
		super(name);
	}
	
	/**
	 * Constructor for loading a SingleMoleculeArchive. A
	 * yama file can be given or a yama virtual 
	 * store directory. Virtual mode will automatically
	 * be activated if a directory is provided.
	 * <p>
	 * MoleculeArchives should typically be opened using the
	 * {@link ImportMoleculeArchiveCommand}, which automatically
	 * detect the type and open the archive accordingly.
	 * <p>
	 * @param file The file or directory to load the archive from.
	 * 
	 * @throws JsonParseException if there is a problem parsing the file provided.
	 * @throws IOException if there is a problem with the file location.
	 */
	public SingleMoleculeArchive(File file) throws IOException, JsonParseException {
		super(file);
	}
	
	/**
	 * Constructor for loading a SingleMoleculeArchive. A
	 * yama file can be given or a yama virtual 
	 * store directory. Virtual mode will automatically
	 * be activated if a directory is provided.
	 * 
	 * If the MoleculeArchiveService is provided the statusService
	 * will be retrieved and when working in Fiji the progress
	 * shows up in the bar as molecule records are loaded.
	 * 
	 * <p>
	 * MoleculeArchives should typically be opened using the
	 * {@link ImportMoleculeArchiveCommand}, which automatically
	 * detect the type and open the archive accordingly.
	 * <p>
	 * @param name The name of the archive.
	 * @param file The file or directory to load the archive from.
	 * @param moleculeArchiveService The MoleculeArchiveService from
	 * the current context.
	 * 
	 * @throws JsonParseException if there is a parsing exception.
	 * @throws IOException if there is a problem with the file provided.
	 */
	public SingleMoleculeArchive(String name, File file, MoleculeArchiveService moleculeArchiveService) throws JsonParseException, IOException {
		super(name, file, moleculeArchiveService);
	}
	
	/**
	 * Constructor for building a SingleMoleculeArchive from a MarsTable.
	 * The table provided must contain a molecule column. The integer values
	 * in the molecule column determine the grouping for creation of 
	 * molecule records.
	 * 
	 * Status will be reported during processing by retrieving the StatusService
	 * from the MoleculeArchiveService instance.
	 * 
	 * @param name The name of the archive.
	 * @param table A MarsTable to build the archive from.
	 * @param moleculeArchiveService The MoleculeArchiveService from
	 * the current context.
	 */
	public SingleMoleculeArchive(String name, MarsTable table, MoleculeArchiveService moleculeArchiveService) {
		super(name, table, moleculeArchiveService);
	}
	
	/**
	 * Create empty SingleMoleculeArchiveProperties record.
	 */
	public SingleMoleculeArchiveProperties createProperties() {
		return new SingleMoleculeArchiveProperties();
	}
	
	/**
	 * Create SingleMoleculeArchiveProperties record using JsonParser stream.
	 */
	public SingleMoleculeArchiveProperties createProperties(JsonParser jParser) throws IOException {
		return new SingleMoleculeArchiveProperties(jParser);
	}
	
	/**
	 * Create SdmmImageMetadata record using JsonParser stream.
	 */
	public SdmmImageMetadata createMetadata(JsonParser jParser) throws IOException {
		return new SdmmImageMetadata(jParser);
	}
	
	/**
	 * Create empty SdmmImageMetadata record with the metaUID specified.
	 */
	public SdmmImageMetadata createMetadata(String metaUID) {
		return new SdmmImageMetadata(metaUID);
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
}
