package de.mpg.biochem.mars.molecule;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import de.mpg.biochem.mars.table.MarsTable;

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
	 * such as the slope or MSD. Storing parameters with the record data
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
	
	/**
	 * Set the parent {@link MoleculeArchive} that this record is stored in.
	 * 
	 * @param archive The {@link MoleculeArchive} holding this record.
	 */
	void setParent(MoleculeArchive<? extends Molecule, ? extends MarsImageMetadata, ? extends MoleculeArchiveProperties> archive);
}
