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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.MarsPosition;
import de.mpg.biochem.mars.util.MarsRegion;

public interface MarsRecord extends JsonConvertibleRecord {
	
	/**
	 * Get the UID for this record.
	 * 
	 * @return Returns the UID.
	 */
	String getUID();
	
	/**
	 * Get notes for this record. Notes can be added during manual sorting
	 * to point out a feature or important detail about the current record.
	 * 
	 * @return Returns a string containing any notes associated with this record.
	 */
	String getNotes();
	
	/**
	 * Sets the notes for this record. Notes can be added during manual sorting
	 * to point out a feature or important detail about the current record.
	 * 
	 * @param Notes Any notes about this record.
	 */
	void setNotes(String Notes);
	
	/**
	 * Add to any notes already in the record.
	 *  
	 * @param Note String with the note to add to the record.
	 */
	void addNote(String Note);
	
	/**
	 * Add a string tag to the record. Tags are used for marking individual
	 * record to sorting and processing with subsets of records.
	 *  
	 * @param tag The string tag to be added.
	 */
	void addTag(String tag);
	
	/**
	 * Check if the record has a tag.
	 *  
	 * @param tag The string tag to check for.
	 * @return Returns true if the has the tag
	 * and false if the record doesn't.
	 */
	boolean hasTag(String tag);
	
	/**
	 * Check if the has no tags.
	 *  
	 * @return Returns true if the record has no tags.
	 */
	boolean hasNoTags();
	
	/**
	 * Get the set of all tags.
	 *  
	 * @return Returns the set of tags for this record.
	 */
	LinkedHashSet<String> getTags();
	
	/**
	 * Get an array list of all tags.
	 *  
	 * @return Returns the set of tags for this record as an array.
	 */
	String[] getTagsArray();
	
	/**
	 * Remove a string tag from the record.
	 *  
	 * @param tag The string tag to remove.
	 */
	void removeTag(String tag);
	
	/**
	 * Remove all tags from the record.
	 */
	void removeAllTags();
	
	/**
	 * Add or update a parameter value. Parameters are used to store single 
	 * values associated with the record. For example, this can be the 
	 * start and stop times for a region of interest. Or calculated features
	 * such as the slope or variance. Storing parameters with the record data
	 * allows for easier and more efficient processing and data extraction.
	 *  
	 * @param parameter The string parameter name.
	 * @param value The double value to set for the parameter name.
	 */
	void setParameter(String parameter, double value);
	
	/**
	 * Remove all parameter values from the record.
	 */
	void removeAllParameters();
	
	/**
	 * Remove parameter. Removes the name and value pair.
	 * 
	 * @param parameter The parameter name to remove.
	 */
	void removeParameter(String parameter);
	
	/**
	 * Get the value of a parameter.
	 * 
	 * @param parameter The string parameter name to retrieve the value for.
	 * @return Returns the double value for the parameter name given.
	 */
	double getParameter(String parameter);
	
	/**
	 * Get the value of a parameter.
	 * 
	 * @param parameter The string parameter name to retrieve the value for.
	 * @return Returns the double value for the parameter name given.
	 */
	boolean hasParameter(String parameter);
	
	/**
	 * Get the map for all parameters.
	 * 
	 * @return Returns the map of parameter names to values.
	 */
	LinkedHashMap<String, Double> getParameters();
	
	/**
	 * Get the {@link MarsTable} DataTable holding the primary data for
	 * this molecule record.
	 * 
	 * @return The primary DataTable for this molecule record.
	 */
	MarsTable getDataTable();
	
	/**
	 * Set the {@link MarsTable} holding the primary data for
	 * this molecule record. Usually this is tracking or intensity 
	 * as a function of time.
	 * 
	 * @param table The {@link MarsTable} to add or update in the 
	 * molecule record.
	 */
	void setDataTable(MarsTable table);
	
	public void putRegion(MarsRegion regionOfInterest);
	
	public MarsRegion getRegion(String name);
	
	public boolean hasRegion(String name);
	
	public void removeRegion(String name);
	
	public Set<String> getRegionNames();
	
	public void putPosition(MarsPosition positionOfInterest);
	
	public MarsPosition getPosition(String name);
	
	public boolean hasPosition(String name);
	
	public void removePosition(String name);
	
	public Set<String> getPositionNames();
	
	/**
	 * Set the parent {@link MoleculeArchive} that this record is stored in.
	 * 
	 * @param archive The {@link MoleculeArchive} holding this record.
	 */
	void setParent(MoleculeArchive<? extends Molecule, ? extends MarsMetadata, ? extends MoleculeArchiveProperties> archive);
}
