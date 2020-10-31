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

package de.mpg.biochem.mars.metadata;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

import de.mpg.biochem.mars.molecule.AbstractMarsRecord;
import de.mpg.biochem.mars.molecule.JsonConvertibleRecord;
import de.mpg.biochem.mars.molecule.MarsBdvSource;
import de.mpg.biochem.mars.molecule.MarsRecord;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.table.MarsTable;
import ome.xml.meta.OMEXMLMetadata;

/**
 * MarsImageMetadata records store image metadata and all information about
 * specific data collections, imaging settings, frame timing. Mapping of
 * frames/slices to actual real time. These records can also include readouts
 * from other instruments connected to microscopes.
 * <p>
 * These records may also contain BigDataViewer registration coordinates, video
 * locations, and channel names. The log contained in these records will contain
 * a history of commands run on this dataset and the molecule records associated
 * with it that are stored in the same {@link MoleculeArchive}. The
 * {@link MarsTable} inherited from {@link AbstractMarsRecord} will contain
 * metadata for each frame in individual rows in order of collection.
 * </p>
 * <p>
 * This record format is designed for use with single-molecule time-series data
 * collected on TIRF microscopes or bead tracking microscopes. Therefore, these
 * data are assumed to be 2D only. If individual colors are recorded in separate
 * frames their information should be merged into a single row contained in the
 * {@link MarsTable}.
 * </p>
 * 
 * @author Karl Duderstadt
 */
public interface MarsMetadata extends JsonConvertibleRecord, MarsRecord {

	void populateMetadata(OMEXMLMetadata md);

	void setImage(MarsOMEImage image, int imageIndex);

	MarsOMEImage getImage(int imageIndex);

	MarsOMEPlane getPlane(int imageIndex, int planeIndex);

	boolean hasPlane(int imageIndex, int planeIndex);

	MarsOMEPlane getPlane(int imageIndex, int Z, int C, int T);

	int getImageCount();

	Stream<MarsOMEImage> images();

	/**
	 * Set the name of the microscope used for data collection. This is just for
	 * record keeping. There are no predefined setting based on microscope names.
	 * 
	 * @param Microscope Name of the microscope used for collection.
	 */
	void setMicroscopeName(String Microscope);

	/**
	 * Get the name of the microscope used for data collection. This is just for
	 * record keeping. There are no predefined setting based on microscope names.
	 * 
	 * @return Name of the microscope used for collection.
	 */
	String getMicroscopeName();

	/**
	 * Get the Date when these data were collected.
	 * 
	 * @return The date when the metadata was collected.
	 */
	String getCollectionDate();

	/**
	 * Get the Source Directory where the images are stored.
	 * 
	 * @return Directory where the images are stored.
	 */
	String getSourceDirectory();

	/**
	 * Set the Source Directory where the images are stored.
	 * 
	 * @param path The string file path.
	 */
	void setSourceDirectory(String path);

	/**
	 * Used to merge another MarsMetadata record into this one.
	 * 
	 * @param metadata MarsMetadata to merge into this one.
	 */
	void merge(MarsMetadata metadata);

	/**
	 * Add to the log that contains the history of processing steps conducted on
	 * this dataset and the associated molecule records contained in the same
	 * {@link MoleculeArchive}.
	 * 
	 * @param str Message to add to the log.
	 */
	void log(String str);

	/**
	 * Add to the log that contains the history of processing steps conducted on
	 * this dataset and the associated molecule records contained in the same
	 * {@link MoleculeArchive}. Start a new line after adding the message.
	 * 
	 * @param str Message to add to the log.
	 */
	void logln(String str);

	/**
	 * Get the log that contains the history of processing steps conducted on this
	 * dataset and the associated molecule records contained in the same
	 * {@link MoleculeArchive}.
	 * 
	 * @return The processing log for this metadata record.
	 */
	String getLog();

	/**
	 * Get the {@link MarsBdvSource} with the name provided.
	 * 
	 * @param name Name of the MarsBdvSource to retrieve.
	 * @return Mars BigDataViewer Source with the name given.
	 */
	MarsBdvSource getBdvSource(String name);

	/**
	 * Add or update the {@link MarsBdvSource} with the name provided. All
	 * {@link MarsBdvSource} are unique so record will be overwritten if they have
	 * the same name.
	 * 
	 * @param source Mars BigDataViewer Source to add to the MarsImageMetadata
	 *          record.
	 */
	void putBdvSource(MarsBdvSource source);

	/**
	 * Remove the {@link MarsBdvSource} with the name provided.
	 * 
	 * @param name Name of the MarsBdvSource to remove.
	 */
	void removeBdvSource(String name);

	/**
	 * Get the Collection of BigDataViewer sources with each in
	 * {@link MarsBdvSource} format.
	 * 
	 * @return Collection of MarsBdvSources in the record.
	 */
	Collection<MarsBdvSource> getBdvSources();

	/**
	 * Get the set of BigDataViewer source names.
	 * 
	 * @return Names of MarsBdvSources in the record.
	 */
	Set<String> getBdvSourceNames();

	/**
	 * Check if this record contains the BigDataViewer with the name provided.
	 * 
	 * @param name Name of the MarsBdvSource to check for.
	 * @return True if a source with the name given is found.
	 */
	boolean hasBdvSource(String name);
}
