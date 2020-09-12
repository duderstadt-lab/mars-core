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

import de.mpg.biochem.mars.metadata.MarsMetadata;

/**
 * Global properties of Molecule Archives are stored in MoleculeArchiveProperties including 
 * comments, number of each record type, as well as tag, parameter, Table column,
 * region and position name indexes. Information you would like to know without having to 
 * read the entire archive record-by-record.
 * 
 * @author Karl Duderstadt
 */
public interface MoleculeArchiveProperties extends JsonConvertibleRecord {
	
	/**
	 * Get the Json input schema for the archive. Returns a string
	 * with the date for the schema definition using to save the archive
	 * that has just been opened. Or null if for archives created with 
	 * early versions of mars-core.
	 */
	String getInputSchema();
	
	/**
	 * Add a molecule tag to the global set that contains a record of all unique tag 
	 * names that are being used.
	 */	
	void addTag(String tag);
	
	/**
	 * Add molecule tags to the global set that contains a record of all unique tag 
	 * names that are being used.
	 */
	void addAllTags(Set<String> tags);
	
	/**
	 * Get the set of molecule tag names in use.
	 */
	Set<String> getTagSet();
	
	/**
	 * Redefine the set of molecule tags in use. 
	 */
	void setTagSet(Set<String> tagSet);
	
	/**
	 * Add a channel index to the global set that contains a record of all unique indexes 
	 * that are represented in the archive.
	 */
	void addChannel(int channel);
	
	/**
	 * Add molecule channel indexes to the global set that 
	 * contains a record of all unique channel indexes 
	 * that are represented in the archive.
	 */
	void addAllChannels(Set<Integer> channels);
	
	/**
	 * Get the set of channel indexes for molecules in the archive.
	 */
	Set<Integer> getChannelSet();
	
	/**
	 * Redefine the set of molecule channels in use. 
	 * Integers with an index starting at 0. 
	 */
	void setChannelSet(Set<Integer> channelSet);
	
	/**
	 * Add a molecule parameter name to the global set that contains a record of all
	 * unique parameter names that are being used.
	 */
	void addParameter(String parameterName);
	
	/**
	 * Add molecule parameter names to the global set that contains a record of all unique parameter 
	 * names that are being used.
	 */
	void addAllParameters(Set<String> parameters);
	
	/**
	 * Get the set of molecule parameter names in use.
	 */
	Set<String> getParameterSet();
	
	/**
	 * Redefine the set of molecule parameter names in use.
	 */
	void setParameterSet(Set<String> parameterSet);
	
	/**
	 * Add a molecule position name to the global set that contains a record of all unique position 
	 * names that are being used.
	 */
	void addPosition(String position);
	
	/**
	 * Add molecule position names to the global set that contains a record of all unique position 
	 * names that are being used.
	 */
	void addAllPositions(Set<String> positions);
	
	/**
	 * Get the set of molecule position names in use.
	 */
	Set<String> getPositionSet();
	
	/**
	 * Redefine the set of molecule position names in use. 
	 */
	void setPositionSet(Set<String> positionSet);
	
	/**
	 * Add a molecule region name to the global set that contains a record of all unique region 
	 * names that are being used.
	 */	
	void addRegion(String region);
	
	/**
	 * Add molecule region names to the global set that contains a record of all unique region 
	 * names that are being used.
	 */
	void addAllRegions(Set<String> regions);
	
	/**
	 * Get the set of molecule region names in use.
	 */
	Set<String> getRegionSet();
	
	/**
	 * Redefine the set of molecule region names in use. 
	 */
	void setRegionSet(Set<String> regionSet);
	
	/**
	 * Set the number of molecule in the archive.
	 */
	void setNumberOfMolecules(int numMolecules);
	
	/**
	 * Get the number of molecule in the archive.
	 */
	int getNumberOfMolecules();
	
	/**
	 * Set the number of MarsMetadata records in the archive.
	 */
	void setNumberOfMetadatas(int numMetadata);
	
	/**
	 * Get the number of MarsMetadata records in the archive.
	 */
	int getNumberOfMetadatas();
	
	/**
	 * Add a column name to the unique set of column names 
	 * in use in molecule DataTables.
	 */
	void addColumn(String column);
	
	/**
	 * Add column names to the unique set of column names 
	 * in use in molecule Tables.
	 */
	void addAllColumns(Set<String> columns);
	
	/**
	 * Add column names to the unique set of column names 
	 * in use in molecule Tables.
	 */
	void addAllColumns(ArrayList<String> columns);
	
	/**
	 * Redefine the unique set of column names 
	 * in use in molecule Tables.
	 */
	void setColumnSet(Set<String> moleculeDataTableColumnSet);
	
	/**
	 * Get the unique set of column names 
	 * in use in molecule Tables.
	 */
	Set<String> getColumnSet();
	
	/**
	 * Add a segment table name to the unique set of segment table 
	 * names found in molecule records.
	 */
	void addSegmentsTableName(ArrayList<String> segmentTableName);
	
	/**
	 * Add segment table names to the unique set of segment table names found in 
	 * molecule records.
	 */
	void addAllSegmentsTableNames(Set<ArrayList<String>> segmentTableNames);
	
	/**
	 * Redefine the unique set of segment table names found in molecule records.
	 */
	void setSegmentsTableNames(Set<ArrayList<String>> moleculeSegmentTableNames);
	
	/**
	 * Get the unique set of segment table names found in molecule records.
	 */
	Set<ArrayList<String>> getSegmentsTableNames();
	
	/**
	 * Get archive comments.
	 */
	String getComments();
	
	/**
	 * Add to archive comments.
	 */
	void addComment(String comment);
	
	/**
	 * Overwrite archive comments with new set of comments.
	 */
	void setComments(String comments);
	
	/**
	 * Used to during merge MoleculeArchive merge events to merge the properties
	 * of another archive into this one.
	 * 
	 * @param properties MoleculeArchiveProperties record to merge into this one.
	 * @param archiveName Name of the archive that is being merged with this one.
	 */
	void merge(MoleculeArchiveProperties properties, String archiveName);
	
	/**
	 * Set the parent {@link MoleculeArchive} that this record is stored in.
	 * 
	 * @param archive The {@link MoleculeArchive} holding this record.
	 */
	void setParent(MoleculeArchive<? extends Molecule, ? extends MarsMetadata, ? extends MoleculeArchiveProperties> archive);
}
