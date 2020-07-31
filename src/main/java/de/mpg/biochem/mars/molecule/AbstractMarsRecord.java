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

import de.mpg.biochem.mars.kcp.commands.KCPCommand;
import de.mpg.biochem.mars.metadata.AbstractMarsMetadata;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.MarsUtil;
import de.mpg.biochem.mars.util.MarsPosition;
import de.mpg.biochem.mars.util.MarsRegion;

/**
 * Abstract superclass for all {@link MarsRecord} types: {@link Molecule} and {@link MarsMetadata}. 
 * All {@link MarsRecord}s have a basic set of properties including a UID, Notes, 
 * Tags, Parameters, a {@link MarsTable}, {@link MarsRegion}s, and {@link MarsPosition}s. {@link MarsRecord}
 * can also be serialized to and from Json.
 * <p>
 * This basic set of properties is extended for storage of molecule information and metadata information in
 * {@link Molecule}, {@link AbstractMolecule}, {@link MarsMetadata}, {@link AbstractMarsMetadata}.
 * </p>
 * @author Karl Duderstadt
 */
public abstract class AbstractMarsRecord extends AbstractJsonConvertibleRecord implements MarsRecord {
	//Unique ID for storage in maps and universal identification.
	protected String UID;
	
	//Reference to MoleculeArchive containing the record
	protected MoleculeArchive<? extends Molecule, ? extends MarsMetadata, ? extends MoleculeArchiveProperties> parent;
	
	//For any notes associated with the record
	protected String Notes;
	
	//tags for filtering and sorting records
	protected LinkedHashSet<String> Tags;
		
	//Parameter map for record properties
	protected LinkedHashMap<String, Double> Parameters;
	
	//Regions of interest map
	protected LinkedHashMap<String, MarsRegion> regionsOfInterest;
	
	//Positions of interest map
	protected LinkedHashMap<String, MarsPosition> positionsOfInterest;
	
	/**
	 * Constructor for creating an empty MarsRecord. 
	 */
	public AbstractMarsRecord() {
		super();
		Parameters = new LinkedHashMap<>();
		Tags = new LinkedHashSet<String>();
		regionsOfInterest = new LinkedHashMap<>();
		positionsOfInterest = new LinkedHashMap<>();
	}
	
	/**
	 * Constructor for creating an empty MarsRecord with the
	 * specified UID. 
	 * 
	 * @param UID The unique identifier for this MarsRecord.
	 */
	public AbstractMarsRecord(String UID) {
		super();
		Parameters = new LinkedHashMap<>();
		Tags = new LinkedHashSet<String>();
		regionsOfInterest = new LinkedHashMap<>();
		positionsOfInterest = new LinkedHashMap<>();
		this.UID = UID;
		
	}
	
	@Override
	protected void createIOMaps() {

		setJsonField("UID",
			jGenerator -> jGenerator.writeStringField("UID", UID),
			jParser -> UID = jParser.getText());
		
		setJsonField("Type", 
			jGenerator -> jGenerator.writeStringField("Type", this.getClass().getName()),
			null);
		
		setJsonField("Notes",
			jGenerator -> {
				if (Notes != null)
					jGenerator.writeStringField("Notes", Notes);
				}, 
			jParser -> Notes = jParser.getText());
		
		setJsonField("Tags", 
			jGenerator -> {
					if (Tags.size() > 0) {
						jGenerator.writeFieldName("Tags");
						jGenerator.writeStartArray();
						Iterator<String> iterator = Tags.iterator();
						while(iterator.hasNext())
							jGenerator.writeString(iterator.next());
						jGenerator.writeEndArray();
					}
				}, 
			jParser -> {
		    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
		            Tags.add(jParser.getText());
		        }
			});
				
		setJsonField("Parameters", 
			jGenerator -> {
					if (Parameters.size() > 0) {
						jGenerator.writeObjectFieldStart("Parameters");
						for (String name:Parameters.keySet())
							jGenerator.writeNumberField(name, Parameters.get(name));
						jGenerator.writeEndObject();
					}
				},
			jParser -> {
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
			});
				
		setJsonField("RegionsOfInterest", 
			jGenerator -> {
					if (regionsOfInterest.size() > 0) {
						jGenerator.writeArrayFieldStart("RegionsOfInterest");
						for (String region : regionsOfInterest.keySet()) 
							regionsOfInterest.get(region).toJSON(jGenerator);
						jGenerator.writeEndArray();
					}
			 	}, 
			jParser -> {
					while (jParser.nextToken() != JsonToken.END_ARRAY) {
						MarsRegion regionOfInterest = new MarsRegion(jParser);
				    	regionsOfInterest.put(regionOfInterest.getName(), regionOfInterest);
			    	}
			 });
				 	
		setJsonField("PositionsOfInterest", 
			jGenerator -> {
					if (positionsOfInterest.size() > 0) {
						jGenerator.writeArrayFieldStart("PositionsOfInterest");
						for (String position :positionsOfInterest.keySet()) 
							positionsOfInterest.get(position).toJSON(jGenerator);
						jGenerator.writeEndArray();
					}
			 	}, 
			jParser -> {
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					MarsPosition positionOfInterest = new MarsPosition(jParser);
			    	positionsOfInterest.put(positionOfInterest.getName(), positionOfInterest);
		    	}
		 	});
	}
	
	/**
	 * Get the UID for this record.
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
	 * @return Returns a string containing any notes associated with this record.
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
	 * @param Note String with the note to add to the record.
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
			parent.properties().addTag(tag);
		}
	}
	
	/**
	 * Check if the record has a tag.
	 *  
	 * @param tag The string tag to check for.
	 * @return Returns true if the record has the tag
	 * and false if not.
	 */
	public boolean hasTag(String tag) {
		return Tags.contains(tag);
	}
	
	/**
	 * Check if the record has no tags.
	 *  
	 * @return Returns true if the record has no tags.
	 */
	public boolean hasNoTags() {
		return Tags.size() == 0;
	}
	
	/**
	 * Get the set of all tags.
	 *  
	 * @return Returns the set of tags for this record.
	 */
	public LinkedHashSet<String> getTags() {
		return Tags;
	}
	
	/**
	 * Get an array list of all tags.
	 *  
	 * @return Returns the set of tags for this record as an array.
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
	 * Remove all tags from the record.
	 */
	public void removeAllTags() {
		Tags.clear();
	}
	
	/**
	 * Add or update a parameter value. Parameters are used to store single 
	 * values associated with the record. For example, this can be the 
	 * start and stop times for a region of interest. Or calculated features
	 * such as a slope. Storing parameters with the record data
	 * allows for easier and more efficient processing and data extraction.
	 *  
	 * @param parameter The string parameter name.
	 * @param value The double value to set for the parameter name.
	 */
	public void setParameter(String parameter, double value) {
		Parameters.put(parameter, value);
		if (parent != null) {
			parent.properties().addParameter(parameter);
		}
	}
	
	/**
	 * Remove all parameter values from the record.
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
	 * Add or update a {@link MarsRegion}. This can be a region of
	 * interest for further analysis steps: slope calculations or
	 * KCP calculations {@link KCPCommand}. Region names are unique. If a region that
	 * has this name already exists in the record it will be
	 * overwritten by this method.
	 *  
	 * @param regionOfInterest The region to add to the record.
	 */
	public void putRegion(MarsRegion regionOfInterest) {
		regionsOfInterest.put(regionOfInterest.getName(), regionOfInterest);
	}
	
	/**
	 * Get a {@link MarsRegion}. Region names are
	 * unique. Only one copy of each region can be 
	 * stored in the record.
	 *  
	 * @param name The name of the region to retrieve.
	 */
	public MarsRegion getRegion(String name) {
		return regionsOfInterest.get(name);
	}
	
	/**
	 * Check if the record contains a {@link MarsRegion}
	 * using the name.
	 *  
	 * @param name The name of the region to check for.
	 */
	public boolean hasRegion(String name) {
		return regionsOfInterest.containsKey(name);
	}
	
	/**
	 * Remove a {@link MarsRegion} from the record using the name.
	 *  
	 * @param name The name of the region to remove.
	 */
	public void removeRegion(String name) {
		regionsOfInterest.remove(name);
	}
	
	/**
	 * Get the set of region names contained in this record.
	 */
	public Set<String> getRegionNames() {
		return regionsOfInterest.keySet();
	}
	
	/**
	 * Add or update a {@link MarsPosition}. This can be a position of
	 * interest for further analysis steps. Position names are unique. 
	 * If a position with has this name already exists in the record it 
	 * will be overwritten by this method.
	 *  
	 * @param positionOfInterest The position to add to the record.
	 */
	public void putPosition(MarsPosition positionOfInterest) {
		positionsOfInterest.put(positionOfInterest.getName(), positionOfInterest);
	}
	
	/**
	 * Get a {@link MarsPosition}. Position names are
	 * unique. Only one copy of each region can be 
	 * stored in the record.
	 *  
	 * @param name The name of the position to retrieve.
	 */
	public MarsPosition getPosition(String name) {
		return positionsOfInterest.get(name);
	}
	
	/**
	 * Check if the record contains a {@link MarsPosition}
	 * using the name.
	 *  
	 * @param name The name of the position to check for.
	 */
	public boolean hasPosition(String name) {
		return positionsOfInterest.containsKey(name);
	}
	
	/**
	 * Remove a {@link MarsPosition} from the record using the name.
	 *  
	 * @param name The name of the position to remove.
	 */
	public void removePosition(String name) {
		positionsOfInterest.remove(name);
	}
	
	/**
	 * Get the set of position names contained in this record.
	 */
	public Set<String> getPositionNames() {
		return positionsOfInterest.keySet();
	}
	
	/**
	 * 
	 * @param archive The {@link MoleculeArchive} holding this record.
	 */
	public void setParent(MoleculeArchive<? extends Molecule, ? extends MarsMetadata, ? extends MoleculeArchiveProperties> archive) {
		parent = archive;
	}
}
