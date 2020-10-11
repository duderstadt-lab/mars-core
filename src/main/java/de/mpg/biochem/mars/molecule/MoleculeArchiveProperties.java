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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;

import de.mpg.biochem.mars.metadata.MarsMetadata;

/**
 * Global properties of Molecule Archives are stored in MoleculeArchiveProperties including 
 * comments, number of each record type, as well as tag, parameter, Table column,
 * region and position name indexes. Information you would like to know without having to 
 * read the entire archive record-by-record.
 * 
 * @author Karl Duderstadt
 */
public interface MoleculeArchiveProperties<M extends Molecule, I extends MarsMetadata> extends JsonConvertibleRecord {
	
	/**
	 * Get the Json input schema for the archive. Returns a string
	 * with the date for the schema definition using to save the archive
	 * that has just been opened. Or null if for archives created with 
	 * early versions of mars-core.
	 * 
	 * @return The input schema.
	 */
	String getInputSchema();
	
	/**
	 * Add a molecule tag to the global set that contains a record of all unique tag 
	 * names that are being used.
	 * 
	 * @param tag The tag to add.
	 */	
	void addTag(String tag);
	
	/**
	 * Add molecule tags to the global set that contains a record of all unique tag 
	 * names that are being used.
	 * 
	 * @param tags Set of tags to add.
	 */
	void addAllTags(Set<String> tags);
	
	/**
	 * Get the set of molecule tag names in use.
	 * 
	 * @return The tag set.
	 */
	Set<String> getTagSet();
	
	/**
	 * Redefine the set of molecule tags in use. 
	 * 
	 * @param tagSet The tag set.
	 */
	void setTagSet(Set<String> tagSet);
	
	/**
	 * Add a channel index to the global set that contains a record of all unique indexes 
	 * that are represented in the archive.
	 * 
	 * @param channel The channel for this molecule.
	 */
	void addChannel(int channel);
	
	/**
	 * Add molecule channel indexes to the global set that 
	 * contains a record of all unique channel indexes 
	 * that are represented in the archive.
	 * 
	 * @param channels The channels to add.
	 */
	void addAllChannels(Set<Integer> channels);
	
	/**
	 * Get the set of channel indexes for molecules in the archive.
	 * 
	 * @return The channel set.
	 */
	Set<Integer> getChannelSet();
	
	/**
	 * Redefine the set of molecule channels in use. 
	 * Integers with an index starting at 0. 
	 * 
	 * @param channelSet The set of channels.
	 */
	void setChannelSet(Set<Integer> channelSet);
	
	/**
	 * Add a molecule parameter name to the global set that contains a record of all
	 * unique parameter names that are being used.
	 * 
	 * @param parameterName The parameter name to add.
	 */
	void addParameter(String parameterName);
	
	/**
	 * Add molecule parameter names to the global set that contains a record of all unique parameter 
	 * names that are being used.
	 * 
	 * @param parameters The parameters to add.
	 */
	void addAllParameters(Set<String> parameters);
	
	/**
	 * Get the set of molecule parameter names in use.
	 * 
	 * @return The set of parameters.
	 */
	Set<String> getParameterSet();
	
	/**
	 * Redefine the set of molecule parameter names in use.
	 * 
	 * @param parameterSet The set of parameters.
	 */
	void setParameterSet(Set<String> parameterSet);
	
	/**
	 * Add a molecule position name to the global set that contains a record of all unique position 
	 * names that are being used.
	 * 
	 * @param position The position name to add.
	 */
	void addPosition(String position);
	
	/**
	 * Add molecule position names to the global set that contains a record of all unique position 
	 * names that are being used.
	 * 
	 * @param positions The positions names to add.
	 */
	void addAllPositions(Set<String> positions);
	
	/**
	 * Get the set of molecule position names in use.
	 * 
	 * @return The position set.
	 */
	Set<String> getPositionSet();
	
	/**
	 * Redefine the set of molecule position names in use. 
	 * 
	 * @param positionSet The position set.
	 */
	void setPositionSet(Set<String> positionSet);
	
	/**
	 * Add a molecule region name to the global set that contains a record of all unique region 
	 * names that are being used.
	 * 
	 * @param region The region name to add.
	 */	
	void addRegion(String region);
	
	/**
	 * Add molecule region names to the global set that contains a record of all unique region 
	 * names that are being used.
	 * 
	 * @param regions The region names to add.
	 */
	void addAllRegions(Set<String> regions);
	
	/**
	 * Get the set of molecule region names in use.
	 * 
	 * @return The set of region names.
	 */
	Set<String> getRegionSet();
	
	/**
	 * Redefine the set of molecule region names in use. 
	 * 
	 * @param regionSet The region set.
	 */
	void setRegionSet(Set<String> regionSet);
	
	/**
	 * Set the number of molecule in the archive.
	 * 
	 * @param numMolecules Total molecule count.
	 */
	void setNumberOfMolecules(int numMolecules);
	
	/**
	 * Get the number of molecule in the archive.
	 * 
	 * @return Total molecule count.
	 */
	int getNumberOfMolecules();
	
	/**
	 * Set the number of MarsMetadata records in the archive.
	 * 
	 * @param numMetadata Total metadata count.
	 */
	void setNumberOfMetadatas(int numMetadata);
	
	/**
	 * Get the number of MarsMetadata records in the archive.
	 * 
	 * @return Total metadata count.
	 */
	int getNumberOfMetadatas();
	
	/**
	 * Add a column name to the unique set of column names 
	 * in use in molecule DataTables.
	 * 
	 * @param column The column name to add.
	 */
	void addColumn(String column);
	
	/**
	 * Add column names to the unique set of column names 
	 * in use in molecule Tables.
	 * 
	 * @param columns The set column names to add.
	 */
	void addAllColumns(Set<String> columns);
	
	/**
	 * Add column names to the unique set of column names 
	 * in use in molecule Tables.
	 * 
	 * @param columns The list of column names to add.
	 */
	void addAllColumns(ArrayList<String> columns);
	
	/**
	 * Redefine the unique set of column names 
	 * in use in molecule Tables.
	 * 
	 * @param moleculeDataTableColumnSet The set of molecule table columns names.
	 */
	void setColumnSet(Set<String> moleculeDataTableColumnSet);
	
	/**
	 * Get the unique set of column names 
	 * in use in molecule Tables.
	 * 
	 * @return The set of column names.
	 */
	Set<String> getColumnSet();
	
	/**
	 * Add a segment table name to the unique set of segment table 
	 * names found in molecule records.
	 * 
	 * @param segmentTableName The segment table names.
	 */
	void addSegmentsTableName(ArrayList<String> segmentTableName);
	
	/**
	 * Add segment table names to the unique set of segment table names found in 
	 * molecule records.
	 * 
	 * @param segmentTableNames The segment Table names.
	 */
	void addAllSegmentsTableNames(Set<ArrayList<String>> segmentTableNames);
	
	/**
	 * Redefine the unique set of segment table names found in molecule records.
	 * 
	 * @param moleculeSegmentTableNames The molecule segment table names.
	 */
	void setSegmentsTableNames(Set<ArrayList<String>> moleculeSegmentTableNames);
	
	/**
	 * Get the unique set of segment table names found in molecule records.
	 * 
	 * @return The set of lists of segment table names.
	 */
	Set<ArrayList<String>> getSegmentsTableNames();
	
	/**
	 * Get archive comments.
	 * 
	 * @return The comments.
	 */
	String getComments();
	
	/**
	 * Add to archive comments.
	 * 
	 * @param comment The comment to add.
	 */
	void addComment(String comment);
	
	/**
	 * Overwrite archive comments with new set of comments.
	 * 
	 * @param comments The comments to set.
	 */
	void setComments(String comments);
	
	/**
	 * Used to during merge MoleculeArchive merge events to merge the properties
	 * of another archive into this one.
	 * 
	 * @param properties MoleculeArchiveProperties record to merge into this one.
	 * @param archiveName Name of the archive that is being merged with this one.
	 */
	void merge(MoleculeArchiveProperties<M, I> properties, String archiveName);
	
	/**
	 * Save the archive properties to a file.
	 * 
	 * @param directory Folder to save to.
	 * @param jfactory JsonFactory to use when saving.
	 * @param fileExtension The file extension (.json or .sml).
	 */
	void save(File directory, JsonFactory jfactory, String fileExtension) throws IOException;
	
	/**
	 * Clear contents of all global sets and records counts. Does not clear
	 * comments. Used when rebuilding indexes.
	 */
	void clear();
	
	/**
	 * Update global sets to include molecule properties.
	 * 
	 * @param molecule The {@link Molecule} to add the properties from.
	 */
	void addMoleculeProperties(M molecule);
	
	/**
	 * Update global sets to include metadata properties.
	 * 
	 * @param metadata The {@link MarsMetadata} to add the properties from.
	 */
	void addMetadataProperties(I metadata);
	
	/**
	 * Set the parent {@link MoleculeArchive} that this record is stored in.
	 * 
	 * @param archive The {@link MoleculeArchive} holding this record.
	 */
	void setParent(MoleculeArchive<? extends Molecule, ? extends MarsMetadata, ? extends MoleculeArchiveProperties<?,?>, ? extends MoleculeArchiveIndex<?,?>> archive);
}
