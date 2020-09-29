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

import java.util.ArrayList;
import java.util.Set;

import de.mpg.biochem.mars.kcp.commands.KCPCommand;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.MarsPosition;
import de.mpg.biochem.mars.util.MarsRegion;

/**
 * Molecule records act as the storage location for molecule properties. Molecule records are stored 
 * in {@link MoleculeArchive}s to allow for fast and efficient retrieval and optimal organization. 
 * <p>
 * Molecule records are designed to allow for storage of many different kinds
 * of single-molecule time-series data. They contain a primary {@link MarsTable} 
 * with molecule properties typically with each row containing features of a given time point. 
 * This may include position or intensity information. To facilitate efficient and reproducible 
 * processing molecule records may also contain calculated parameters, tags, notes, and 
 * kinetic change point segment {@link MarsTable}s generated by {@link KCPCommand}. 
 * Molecule records are assigned a random UID string at the time of creation derived from 
 * a base58 encoded UUID for readability. This serves as their primary identifier within 
 * {@link MoleculeArchive}s and for a range of transformations and merging operations. 
 * Molecule records also have a UID string for corresponding {@link MarsMetadata} records, 
 * which contain the experiments metadata information from. 
 * </p>
 * <p>
 * Molecule records can be saved to Json for storage when done processing. They are then either 
 * stored as an array within MoleculeArchive .yama files or as individual sml or json files within 
 * .yama.store directories.
 * </p>
 * @author Karl Duderstadt
 */
public interface Molecule extends JsonConvertibleRecord, MarsRecord {
	
	/**
	 * Set the UID of the {@link MarsMetadata} record associated with
	 * this molecule. The {@link MarsMetadata} contains information about
	 * the data collection (Timing of frames, colors, collection date, etc...)
	 * 
	 * @param metadataUID The new metadata UID to set.
	 */
	void setMetadataUID(String metadataUID);
	
	/**
	 * Get the UID of the {@link MarsMetadata} record associated with
	 * this molecule. The {@link MarsMetadata} contains information about
	 * the data collection (Timing of frames, colors, collection date, etc...)
	 * 
	 * @return Return a JSON string representation of the molecule.
	 */
	String getMetadataUID();
	
	/**
	 * Set the image position for this molecule record.
	 * 
	 * @param position The Position.
	 */
	void setImage(int image);
	
	/**
	 * Get the image index for this molecule record. An integer
	 * value starting at zero. If image index is not set this value
	 * will be -1.
	 * 
	 * @return Return the image index for this molecule record.
	 */
	int getImage();
	
	/**
	 * Set the channel for this molecule record.
	 * 
	 * @param channel The channel.
	 */
	void setChannel(int channel);
	
	/**
	 * Get the channel for this molecule record. An integer
	 * value starting at zero. If channel is not set this value
	 * will be -1.
	 * 
	 * @return Return the channel for this molecule record.
	 */
	int getChannel();
	
	/**
	 * Get the {@link MarsTable} holding the primary data for
	 * this molecule record.
	 * 
	 * @return The primary table for this molecule record.
	 */
	MarsTable getTable();
	
	/**
	 * Set the {@link MarsTable} holding the primary data for
	 * this molecule record. Usually this is tracking or intensity 
	 * as a function of time.
	 * 
	 * @param table The {@link MarsTable} to add or update in the 
	 * molecule record.
	 */
	void setTable(MarsTable table);
	
	@Deprecated
	MarsTable getDataTable();
	
	@Deprecated
	void setDataTable(MarsTable table);
	
	/**
	 * Add or update a segments table ({@link MarsTable}) generated 
	 * using the x column and y column names. The {@link KCPCommand} performs
	 * kinetic change point analysis generating segments to fit regions
	 * of a trace. The information about these segments is added using
	 * this method.
	 * 
	 * @param xColumn The name of the column used for x for KCP analysis.
	 * @param yColumn The name of the column used for y for KCP analysis.
	 * @param segmentsTable The {@link MarsTable} to add that contains the 
	 * segments.
	 */
	void putSegmentsTable(String xColumn, String yColumn, MarsTable segmentsTable);
	
	/**
	 * Add or update a segments table ({@link MarsTable}) generated 
	 * using the x column, y column and region names. The {@link KCPCommand} performs
	 * kinetic change point analysis generating segments to fit regions
	 * of a trace. The information about these segments is added using
	 * this method.
	 * 
	 * @param xColumn The name of the column used for x for KCP analysis.
	 * @param yColumn The name of the column used for y for KCP analysis.
	 * @param region The name of the region used for analysis.
	 * @param segmentsTable The {@link MarsTable} to add that contains the 
	 * segments.
	 */
	void putSegmentsTable(String xColumn, String yColumn, String region, MarsTable segmentsTable);
	
	/**
	 * Retrieve a segments table ({@link MarsTable}) generated 
	 * using xColumn and yColumn.
	 * 
	 * @param xColumn The name of the x column used for analysis.
	 * @param yColumn The name of the y column used for analysis.
	 * @return The segments table.
	 */	
	MarsTable getSegmentsTable(String xColumn, String yColumn);
	
	/**
	 * Retrieve a Segments table ({@link MarsTable}) generated 
	 * using xColum, yColumn and region names.
	 * 
	 * @param xColumn The name of the x column used for analysis.
	 * @param yColumn The name of the y column used for analysis.
	 * @param region The name of the region used for analysis.
	 * @return The segments table generated using the columns specified.
	 */	
	MarsTable getSegmentsTable(String xColumn, String yColumn, String region);
	
	/**
	 * Check if record has a segments table ({@link MarsTable}) generated 
	 * using xColumn and yColumn.
	 * 
	 * @param xColumn The name of the x column used for analysis.
	 * @param yColumn The name of the y column used for analysis.
	 * @return Returns true if the table exists and false if not.
	 */	
	boolean hasSegmentsTable(String xColumn, String yColumn);
	
	/**
	 * Check if record has a Segments table ({@link MarsTable}) generated 
	 * using xColumn, yColumn and region names.
	 * 
	 * @param xColumn The name of the x column used for analysis.
	 * @param yColumn The name of the y column used for analysis.
	 * @param region The name of the region used for analysis.
	 * @return Returns true if the table exists and false if not.
	 */	
	boolean hasSegmentsTable(String xColumn, String yColumn, String region);
	
	/**
	 * Retrieve a segments table ({@link MarsTable}) generated 
	 * using x column, y column and region names provided in index positions 0, 1 and 
	 * 2 of an ArrayList, respectively.
	 * 
	 * @param tableColumnNames The list of x column, y column and region names.
	 * @return The MarsTable generated using the columns specified.
	 */		
	MarsTable getSegmentsTable(ArrayList<String> tableColumnNames);
	
	/**
	 * Remove the segments table ({@link MarsTable}) generated 
	 * using x column, y column, and region names provided as a list.
	 * 
	 * @param tableColumnNames List of xColumn, yColumn, and Region of the segment table to remove.
	 */
	void removeSegmentsTable(ArrayList<String> tableColumnNames);
	
	/**
	 * Remove the segments table ({@link MarsTable}) generated 
	 * using xColumn and y Column.
	 * 
	 * @param xColumn The name of the x column used for analysis.
	 * @param yColumn The name of the y column used for analysis.
	 */
	void removeSegmentsTable(String xColumn, String yColumn);
	
	/**
	 * Remove the segments table ({@link MarsTable}) generated 
	 * using x column, y column, and region names provided.
	 * 
	 * @param xColumn The name of the x column used for analysis.
	 * @param yColumn The name of the y column used for analysis.
	 * @param region The name of the region used for analysis.
	 */
	void removeSegmentsTable(String xColumn, String yColumn, String region);
	
	/**
	/**
	 * Get the set of segment table names as lists of x and y column names.
	 * 
	 * @return The set of ArrayLists holding the x and y column and region names at
	 * index positions 0, 1 and 2, respectively.
	 */
	Set<ArrayList<String>> getSegmentsTableNames();
}
