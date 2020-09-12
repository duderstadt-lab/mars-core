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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import de.mpg.biochem.mars.kcp.commands.KCPCommand;
import de.mpg.biochem.mars.metadata.AbstractMarsMetadata;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.MarsPosition;
import de.mpg.biochem.mars.util.MarsRegion;

/**
 * Basic interface for Mars records. 
 * <p>
 * All {@link MarsRecord}s have a basic set of properties including a UID, notes, 
 * tags, parameters, {@link MarsRegion}s, and {@link MarsPosition}s. {@link MarsRecord}s
 * can be serialized to and from Json.
 * <p>
 * This basic set of properties is extended for storage of molecule information and metadata information in
 * {@link Molecule}, {@link AbstractMolecule}, {@link MarsMetadata}, {@link AbstractMarsMetadata}.
 * </p>
 * @author Karl Duderstadt
 */
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
	 * @param notes Any notes about this record.
	 */
	void setNotes(String notes);
	
	/**
	 * Add to any notes already in the record.
	 *  
	 * @param note String with the note to add to the record.
	 */
	void addNote(String note);
	
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
	 * Add or update a {@link MarsRegion}. This can be a region of
	 * interest for further analysis steps: slope calculations or
	 * KCP calculations {@link KCPCommand}. Region names are unique. If a region that
	 * has this name already exists in the record it will be
	 * overwritten by this method.
	 *  
	 * @param regionOfInterest The region to add to the record.
	 */
	void putRegion(MarsRegion regionOfInterest);
	
	/**
	 * Get a {@link MarsRegion}. Region names are
	 * unique. Only one copy of each region can be 
	 * stored in the record.
	 *  
	 * @param name The name of the region to retrieve.
	 * @return The MarsRegion with the name given.
	 */
	MarsRegion getRegion(String name);
	
	/**
	 * Check if the record contains a {@link MarsRegion}
	 * using the name.
	 *  
	 * @param name The name of the region to check for.
	 * @return True if a region with name exists, false otherwise.
	 */
	boolean hasRegion(String name);
	
	/**
	 * Remove a {@link MarsRegion} from the record using the name.
	 *  
	 * @param name The name of the region to remove.
	 */
	void removeRegion(String name);
	
	/**
	 * Remove all {@link MarsRegion}s from the record.
	 */
	void removeAllRegions();
	
	/**
	 * Get the set of region names contained in this record.
	 * 
	 * @return The set of region names.
	 */
	public Set<String> getRegionNames();
	
	/**
	 * Add or update a {@link MarsPosition}. This can be a position of
	 * interest for further analysis steps. Position names are unique. 
	 * If a position with has this name already exists in the record it 
	 * will be overwritten by this method.
	 *  
	 * @param positionOfInterest The position to add to the record.
	 */
	public void putPosition(MarsPosition positionOfInterest);
	
	/**
	 * Get a {@link MarsPosition}. Position names are
	 * unique. Only one copy of each region can be 
	 * stored in the record.
	 *  
	 * @param name The name of the position to retrieve.
	 * @return The MarsPosition with the name given.
	 */
	public MarsPosition getPosition(String name);
	
	/**
	 * Check if the record contains a {@link MarsPosition}
	 * using the name.
	 *  
	 * @param name The name of the position to check for.
	 * @return True if the position exists, false otherwise.
	 */
	public boolean hasPosition(String name);
	
	/**
	 * Remove a {@link MarsPosition} from the record using the name.
	 *  
	 * @param name The name of the position to remove.
	 */
	public void removePosition(String name);
	
	/**
	 * Get the set of position names contained in this record.
	 * 
	 * @return The set of MarsPosition names.
	 */
	public Set<String> getPositionNames();
	
	/**
	 * Remove all {@link MarsPosition}s from the record.
	 */
	void removeAllPositions();
	
	/**
	 * Set the parent {@link MoleculeArchive} that this record is stored in.
	 * 
	 * @param archive The {@link MoleculeArchive} holding this record.
	 */
	void setParent(MoleculeArchive<? extends Molecule, ? extends MarsMetadata, ? extends MoleculeArchiveProperties> archive);
}
