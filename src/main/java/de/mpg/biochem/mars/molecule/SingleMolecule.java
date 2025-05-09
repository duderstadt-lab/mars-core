/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2025 Karl Duderstadt
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

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.kcp.commands.KCPCommand;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.table.MarsTable;

/**
 * SingleMolecule records act as the storage location for an array of molecule
 * properties. Molecule records are stored in {@link AbstractMoleculeArchive}s
 * to allow for fast and efficient retrieval and optimal organization.
 * <p>
 * Molecule records are designed to allow for storage of many different kinds of
 * single-molecule time-series data. They contain a primary {@link MarsTable}
 * (or DataTable) with molecule properties typically as a function of time/slice
 * of a video. This may include position or intensity information. To facilitate
 * efficient and reproducible processing Molecule records may also contain
 * calculated parameters, tags, notes, and kinetic change point segment
 * {@link MarsTable}s generated by {@link KCPCommand}. Molecule records are
 * assigned a random UID string at the time of creation derived from a base58
 * encoded UUID for readability. This serves as their primary identifier within
 * {@link AbstractMoleculeArchive}s and for a range of transformations and
 * merging operations. Molecule records also have a UID string for corresponding
 * {@link MarsMetadata} records, which contain information about the imaging
 * settings, the timing of frames etc.. during data collection.
 * </p>
 * <p>
 * Molecule records can be saved to JSON for storage when done processing. They
 * are then either stored as an array within MoleculeArchive .yama files or as
 * individual json files within .yama.store directories.
 * </p>
 * 
 * @author Karl Duderstadt
 */

public class SingleMolecule extends AbstractMolecule {

	/**
	 * Constructor for creating an empty Molecule record.
	 */
	public SingleMolecule() {
		super();
	}

	/**
	 * Constructor for loading a Molecule record from a file. Typically, used when
	 * streaming records into memory when loading a
	 * {@link AbstractMoleculeArchive} or when a record is retrieved from the
	 * virtual store.
	 * 
	 * @param jParser A JsonParser at the start of the molecule record json for
	 *          loading the molecule record from a file.
	 * @throws IOException Thrown if unable to parse Json from JsonParser stream.
	 */
	public SingleMolecule(JsonParser jParser) throws IOException {
		super(jParser);
	}

	/**
	 * Constructor for creating an empty Molecule record with the specified UID.
	 * 
	 * @param UID The unique identifier for this Molecule record.
	 */
	public SingleMolecule(String UID) {
		super(UID);
	}

	/**
	 * Constructor for creating a new Molecule record with the specified UID and
	 * the {@link MarsTable} given as the DataTable.
	 * 
	 * @param UID The unique identifier for this Molecule record.
	 * @param dataTable The {@link MarsTable} to use for initialization.
	 */
	public SingleMolecule(String UID, MarsTable dataTable) {
		super(UID, dataTable);
	}
}
