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

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.MarsUtil;
import de.mpg.biochem.mars.util.MarsPosition;
import de.mpg.biochem.mars.util.MarsRegion;

public abstract class AbstractMarsRecord extends AbstractJsonConvertibleRecord implements MarsRecord {
	//Unique ID for storage in maps and universal identification.
	protected String UID;
	
	//Reference to MoleculeArchive containing the record
	protected MoleculeArchive<? extends Molecule, ? extends MarsImageMetadata, ? extends MoleculeArchiveProperties> parent;
	
	//For any notes associated with the record
	protected String Notes;
	
	//tags for filtering and sorting records
	protected LinkedHashSet<String> Tags;
		
	//Parameter map for record properties
	protected LinkedHashMap<String, Double> Parameters;
	
	//Table housing main record data.
	protected MarsTable dataTable;
	
	//Regions of interest map
	protected LinkedHashMap<String, MarsRegion> regionsOfInterest;
	
	//Positions of interest map
	protected LinkedHashMap<String, MarsPosition> positionsOfInterest;
	
	public AbstractMarsRecord() {
		super();
		Parameters = new LinkedHashMap<>();
		Tags = new LinkedHashSet<String>();
		dataTable = new MarsTable();
		regionsOfInterest = new LinkedHashMap<>();
		positionsOfInterest = new LinkedHashMap<>();
	}
	
	public AbstractMarsRecord(String UID) {
		this();
		this.UID = UID;
	}
	
	public AbstractMarsRecord(JsonParser jParser) throws IOException {
		this();
		fromJSON(jParser);
	}
	
	/**
	 * Constructor for creating a new Molecule record with the
	 * specified UID and the {@link MarsTable} given
	 * as the DataTable. 
	 * 
	 * @param UID The unique identifier for this Molecule record.
	 * @param dataTable The {@link MarsTable} to use for 
	 * initialization.
	 */
	public AbstractMarsRecord(String UID, MarsTable dataTable) {
		super();
		Parameters = new LinkedHashMap<>();
		Tags = new LinkedHashSet<String>();
		regionsOfInterest = new LinkedHashMap<>();
		positionsOfInterest = new LinkedHashMap<>();
		this.UID = UID;
		this.dataTable = dataTable;
	}
	
	@Override
	protected void createIOMaps() {
		//Output Map
		outputMap.put("UID", MarsUtil.catchConsumerException(jGenerator ->
			jGenerator.writeStringField("UID", UID), IOException.class));
		outputMap.put("Type", MarsUtil.catchConsumerException(jGenerator ->
			jGenerator.writeStringField("Type", this.getClass().getName()), IOException.class));
		outputMap.put("Notes", MarsUtil.catchConsumerException(jGenerator -> {
				if (Notes != null)
					jGenerator.writeStringField("Notes", Notes);
			}, IOException.class));
		outputMap.put("Tags", MarsUtil.catchConsumerException(jGenerator -> {
			if (Tags.size() > 0) {
				jGenerator.writeFieldName("Tags");
				jGenerator.writeStartArray();
				Iterator<String> iterator = Tags.iterator();
				while(iterator.hasNext())
					jGenerator.writeString(iterator.next());
				jGenerator.writeEndArray();
			}
		}, IOException.class));
		outputMap.put("Parameters", MarsUtil.catchConsumerException(jGenerator -> {
			if (Parameters.size() > 0) {
				jGenerator.writeObjectFieldStart("Parameters");
				for (String name:Parameters.keySet())
					jGenerator.writeNumberField(name, Parameters.get(name));
				jGenerator.writeEndObject();
			}
		}, IOException.class));
		outputMap.put("DataTable", MarsUtil.catchConsumerException(jGenerator -> {
			if (dataTable.getColumnCount() > 0) {
				jGenerator.writeFieldName("DataTable");
				dataTable.toJSON(jGenerator);
			}
		}, IOException.class));
		outputMap.put("RegionsOfInterest", MarsUtil.catchConsumerException(jGenerator -> {
			if (regionsOfInterest.size() > 0) {
				jGenerator.writeArrayFieldStart("RegionsOfInterest");
				for (String region :regionsOfInterest.keySet()) 
					regionsOfInterest.get(region).toJSON(jGenerator);
				jGenerator.writeEndArray();
			}
	 	}, IOException.class));
		outputMap.put("PositionsOfInterest", MarsUtil.catchConsumerException(jGenerator -> {
			if (positionsOfInterest.size() > 0) {
				jGenerator.writeArrayFieldStart("PositionsOfInterest");
				for (String position :positionsOfInterest.keySet()) 
					positionsOfInterest.get(position).toJSON(jGenerator);
				jGenerator.writeEndArray();
			}
	 	}, IOException.class));
		
		//Input Map
		inputMap.put("UID", MarsUtil.catchConsumerException(jParser -> {
	        UID = jParser.getText();
		}, IOException.class));
		inputMap.put("Notes", MarsUtil.catchConsumerException(jParser -> {
	        Notes = jParser.getText();
		}, IOException.class));
		inputMap.put("Tags", MarsUtil.catchConsumerException(jParser -> {
	    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
	            Tags.add(jParser.getText());
	        }
		}, IOException.class));
		inputMap.put("Parameters", MarsUtil.catchConsumerException(jParser -> {
	    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
	    		String subfieldname = jParser.getCurrentName();
	    		jParser.nextToken();
	    		if (jParser.getCurrentToken().equals(JsonToken.VALUE_STRING)) {
    				String str = jParser.getValueAsString();
    				if (Objects.equals(str, new String("Infinity"))) {
    					Parameters.put(subfieldname, Double.POSITIVE_INFINITY);
    				} else if (Objects.equals(str, new String("-Infinity"))) {
    					Parameters.put(subfieldname, Double.NEGATIVE_INFINITY);
    				} else if (Objects.equals(str, new String("NaN"))) {
    					Parameters.put(subfieldname, Double.NaN);
    				}
    			} else {
    				Parameters.put(subfieldname, jParser.getDoubleValue());
    			}
	    	}
		}, IOException.class));
		inputMap.put("DataTable", MarsUtil.catchConsumerException(jParser -> {
			dataTable.fromJSON(jParser);
		}, IOException.class));		
		inputMap.put("RegionsOfInterest", MarsUtil.catchConsumerException(jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				MarsRegion regionOfInterest = new MarsRegion(jParser);
		    	regionsOfInterest.put(regionOfInterest.getName(), regionOfInterest);
	    	}
	 	}, IOException.class));
		inputMap.put("PositionsOfInterest", MarsUtil.catchConsumerException(jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				MarsPosition positionOfInterest = new MarsPosition(jParser);
		    	positionsOfInterest.put(positionOfInterest.getName(), positionOfInterest);
	    	}
	 	}, IOException.class));
	}
	
	/**
	 * Get the UID for this molecule record.
	 * 
	 * @return Returns the UID.
	 */
	public String getUID() {
		return UID;
	}
	
	/**
	 * Get notes for this record. Notes can be added during manual sorting
	 * to point out a feature or important detail about the current record.
	 * 
	 * @return Returns a string containing any notes associated with this molecule record.
	 */
	public String getNotes() {
		return Notes;
	}
	
	/**
	 * Sets the notes for this record. Notes can be added during manual sorting
	 * to point out a feature or important detail about the current record.
	 * 
	 * @param Notes Any notes about this molecule.
	 */
	public void setNotes(String Notes) {
		this.Notes = Notes;
	}
	
	/**
	 * Add to any notes already in the record.
	 *  
	 * @param Note String with the note to add to the molecule record.
	 */
	public void addNote(String Note) {
		Notes += Note;
	}
	
	/**
	 * Add a string tag to the record. Tags are used for marking individual
	 * record to sorting and processing with subsets of molecules.
	 *  
	 * @param tag The string tag to be added.
	 */
	public void addTag(String tag) {
		Tags.add(tag);
		if (parent != null) {
			parent.getProperties().addTag(tag);
		}
	}
	
	/**
	 * Check if the molecule record has a tag.
	 *  
	 * @param tag The string tag to check for.
	 * @return Returns true if the molecule has the tag
	 * and false if the molecule doesn't.
	 */
	public boolean hasTag(String tag) {
		return Tags.contains(tag);
	}
	
	/**
	 * Check if the molecule has to tags.
	 *  
	 * @return Returns true if the molecule has no tags.
	 */
	public boolean hasNoTags() {
		return Tags.size() == 0;
	}
	
	/**
	 * Get the set of all tags.
	 *  
	 * @return Returns the set of tags for this molecule record.
	 */
	public LinkedHashSet<String> getTags() {
		return Tags;
	}
	
	/**
	 * Get an array list of all tags.
	 *  
	 * @return Returns the set of tags for this molecule record as an array.
	 */
	public String[] getTagsArray() {
		String tagArray[] = new String[Tags.size()];
		return Tags.toArray(tagArray);
	}
	
	/**
	 * Remove a string tag from the record.
	 *  
	 * @param tag The string tag to remove.
	 */
	public void removeTag(String tag) {
		Tags.remove(tag);
	}
	
	/**
	 * Remove all tags from the molecule record.
	 */
	public void removeAllTags() {
		Tags.clear();
	}
	
	/**
	 * Add or update a parameter value. Parameters are used to store single 
	 * values associated with the molecule. For example, this can be the 
	 * start and stop times for a region of interest. Or calculated features
	 * such as the slope or MSD. Storing parameters with the molecule data
	 * allows for easier and more efficient processing and data extraction.
	 *  
	 * @param parameter The string parameter name.
	 * @param value The double value to set for the parameter name.
	 */
	public void setParameter(String parameter, double value) {
		Parameters.put(parameter, value);
		if (parent != null) {
			parent.getProperties().addParameter(parameter);
		}
	}
	
	/**
	 * Remove all parameter values from the molecule record.
	 */
	public void removeAllParameters() {
		Parameters.clear();
	}
	
	/**
	 * Remove parameter. Removes the name and value pair.
	 * 
	 * @param parameter The parameter name to remove.
	 */
	public void removeParameter(String parameter) {
		if (Parameters.containsKey(parameter)) {
			Parameters.remove(parameter);
		}
	}
	
	/**
	 * Get the value of a parameter.
	 * 
	 * @param parameter The string parameter name to retrieve the value for.
	 * @return Returns the double value for the parameter name given.
	 */
	public double getParameter(String parameter) {
		if (Parameters.containsKey(parameter)) {
			return Parameters.get(parameter);
		} else {
			return Double.NaN;
		}
	}
	
	/**
	 * Get the value of a parameter.
	 * 
	 * @param parameter The string parameter name to retrieve the value for.
	 * @return Returns the double value for the parameter name given.
	 */
	public boolean hasParameter(String parameter) {
		return Parameters.containsKey(parameter);
	}
	
	/**
	 * Get the map for all parameters.
	 * 
	 * @return Returns the map of parameter names to values.
	 */
	public LinkedHashMap<String, Double> getParameters() {
		return Parameters;
	}
	
	/**
	 * Get the {@link MarsTable} DataTable holding the primary data for
	 * this molecule record.
	 * 
	 * @return The primary DataTable for this molecule record.
	 */
	public MarsTable getDataTable() {
		return dataTable;
	}
	
	/**
	 * Set the {@link MarsTable} holding the primary data for
	 * this molecule record. Usually this is tracking or intensity 
	 * as a function of time.
	 * 
	 * @param table The {@link MarsTable} to add or update in the 
	 * molecule record.
	 */
	public void setDataTable(MarsTable table) {
		//This means we are resetting all the data...
		dataTable.clear();
		
		//Now set to new table
		dataTable = table;
	}
	
	
	public void putRegion(MarsRegion regionOfInterest) {
		regionsOfInterest.put(regionOfInterest.getName(), regionOfInterest);
	}
	
	public MarsRegion getRegion(String name) {
		return regionsOfInterest.get(name);
	}
	
	public boolean hasRegion(String name) {
		return regionsOfInterest.containsKey(name);
	}
	
	public void removeRegion(String name) {
		regionsOfInterest.remove(name);
	}
	
	public Set<String> getRegionNames() {
		return regionsOfInterest.keySet();
	}
	
	public void putPosition(MarsPosition positionOfInterest) {
		positionsOfInterest.put(positionOfInterest.getName(), positionOfInterest);
	}
	
	public MarsPosition getPosition(String name) {
		return positionsOfInterest.get(name);
	}
	
	public boolean hasPosition(String name) {
		return positionsOfInterest.containsKey(name);
	}
	
	public void removePosition(String name) {
		positionsOfInterest.remove(name);
	}
	
	public Set<String> getPositionNames() {
		return positionsOfInterest.keySet();
	}
	
	/**
	 * Set the parent {@link AbstractMoleculeArchive} that this molecule
	 * record is stored in.
	 * 
	 * @param archive The {@link AbstractMoleculeArchive} holding this record.
	 */
	public void setParent(MoleculeArchive<? extends Molecule, ? extends MarsImageMetadata, ? extends MoleculeArchiveProperties> archive) {
		parent = archive;
	}
}
