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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.mpg.biochem.mars.util.MarsUtil;

/**
 * Abstract superclass for MoleculeArchiveProperties objects that contain
 * global properties of the MoleculeArchive, including indexing, comments, and many
 * other global properties. Any information you would like to know without having to 
 * read the entire archive record-by-record.
 * 
 * @author Karl Duderstadt
 */
public abstract class AbstractMoleculeArchiveProperties extends AbstractJsonConvertibleRecord implements MoleculeArchiveProperties {
	protected int numberOfMolecules;
	protected int numMetadata;
	protected String comments;
	
	//Sets containing global indexes for various molecule properties.
	protected Set<String> tagSet;
	protected Set<String> parameterSet;
	protected Set<String> moleculeDataTableColumnSet;
	protected Set<ArrayList<String>> moleculeSegmentTableNames;
	
	protected MoleculeArchive<? extends Molecule, ? extends MarsMetadata, ? extends MoleculeArchiveProperties> parent;
	
	/**
	 * Creates an empty MoleculeArchiveProperties record. 
	 */
	public AbstractMoleculeArchiveProperties() {
		super();
		numberOfMolecules = 0;
		numMetadata = 0;
		comments = "";
		
		tagSet = ConcurrentHashMap.newKeySet();
		parameterSet = ConcurrentHashMap.newKeySet();
		moleculeDataTableColumnSet = ConcurrentHashMap.newKeySet();
		moleculeSegmentTableNames = ConcurrentHashMap.newKeySet();
	}
	
	/**
	 * Constructor for loading MoleculeArchiveProperties record from a JsonParser stream. 
	 * Used when archives are initially opened.
	 * 
	 * @param jParser A JsonParser at the start of the MoleculeArchiveProperties record.
	 * @throws IOException Thrown if unable to parse Json from the JsonParser stream.
	 */
	public AbstractMoleculeArchiveProperties(JsonParser jParser) throws IOException {
		this();
		fromJSON(jParser);
	}
	
	// JsonConveritableRecord methods...
	
	@Override
	protected void createIOMaps() {
		//Output Map
		outputMap.put("ArchiveType", MarsUtil.catchConsumerException(jGenerator -> {
			if (parent != null)
				jGenerator.writeStringField("ArchiveType", parent.getClass().getName());
		}, IOException.class));
		outputMap.put("Type", MarsUtil.catchConsumerException(jGenerator ->
			jGenerator.writeStringField("Type", this.getClass().getName()), IOException.class));
		outputMap.put("numberOfMolecules", MarsUtil.catchConsumerException(jGenerator ->
			jGenerator.writeNumberField("numberOfMolecules", numberOfMolecules), IOException.class));
		outputMap.put("numberOfMetadata", MarsUtil.catchConsumerException(jGenerator ->
			jGenerator.writeNumberField("numberOfMetadata", numMetadata), IOException.class));
		outputMap.put("MoleculeDataTableColumnSet", MarsUtil.catchConsumerException(jGenerator -> {
				if (moleculeDataTableColumnSet.size() > 0) {
					jGenerator.writeFieldName("MoleculeDataTableColumnSet");
					jGenerator.writeStartArray();
					Iterator<String> iterator = moleculeDataTableColumnSet.iterator();
					while(iterator.hasNext())
						jGenerator.writeString(iterator.next());	
					jGenerator.writeEndArray();
				}
			}, IOException.class));
		outputMap.put("MoleculeSegmentTableNames", MarsUtil.catchConsumerException(jGenerator -> {
				if (moleculeSegmentTableNames.size() > 0) {
					jGenerator.writeFieldName("MoleculeSegmentTableNames");
					jGenerator.writeStartArray();
					Iterator<ArrayList<String>> iterator = moleculeSegmentTableNames.iterator();
					while(iterator.hasNext()) {
						jGenerator.writeStartObject();
						
						ArrayList<String> SegmentTableName = iterator.next();
						
						jGenerator.writeStringField("yColumnName", SegmentTableName.get(0));
						jGenerator.writeStringField("xColumnName", SegmentTableName.get(1));
						
						jGenerator.writeEndObject();
					}
					jGenerator.writeEndArray();
				}
			}, IOException.class));
		outputMap.put("MoleculeTagSet", MarsUtil.catchConsumerException(jGenerator -> {
				if (tagSet.size() > 0) {
					jGenerator.writeFieldName("MoleculeTagSet");
					jGenerator.writeStartArray();
					Iterator<String> iterator = tagSet.iterator();
					while(iterator.hasNext())
						jGenerator.writeString(iterator.next());	
					jGenerator.writeEndArray();
				}
			}, IOException.class));
		outputMap.put("MoleculeParameterSet", MarsUtil.catchConsumerException(jGenerator -> {
				if (parameterSet.size() > 0) {
					jGenerator.writeFieldName("MoleculeParameterSet");
					jGenerator.writeStartArray();
					Iterator<String> iterator = parameterSet.iterator();
					while(iterator.hasNext())
						jGenerator.writeString(iterator.next());	
					jGenerator.writeEndArray();
				}
			}, IOException.class));
		outputMap.put("Comments", MarsUtil.catchConsumerException(jGenerator -> {
				if (!comments.equals(""))
					jGenerator.writeStringField("Comments", comments);
			}, IOException.class));

		
		//Input Map
		inputMap.put("numberOfMolecules", MarsUtil.catchConsumerException(jParser -> {
	        numberOfMolecules = jParser.getValueAsInt();
		}, IOException.class));
		inputMap.put("numImageMetaData", MarsUtil.catchConsumerException(jParser -> {
	        numMetadata = jParser.getIntValue();
		}, IOException.class));
		inputMap.put("numImageMetadata", MarsUtil.catchConsumerException(jParser -> {
	        numMetadata = jParser.getIntValue();
		}, IOException.class));
		inputMap.put("numberOfMetadata", MarsUtil.catchConsumerException(jParser -> {
	        numMetadata = jParser.getIntValue();
		}, IOException.class));
		inputMap.put("MoleculeDataTableColumnSet", MarsUtil.catchConsumerException(jParser -> {
	    	while (jParser.nextToken() != JsonToken.END_ARRAY)
	    		moleculeDataTableColumnSet.add(jParser.getText());
		}, IOException.class));
		
		//Added for backward compatibility...
		inputMap.put("moleculeSegmentTableNames", MarsUtil.catchConsumerException(jParser -> {
	    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
		    	ArrayList<String> segemntTableName = new ArrayList<String>();
		    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
			    	//Then move past field Name - yColumnName...
			    	jParser.nextToken();
			    	
			    	segemntTableName.add(jParser.getText());
			    	
			    	//Then move past the field and next field Name - xColumnName...
			    	jParser.nextToken();
			    	jParser.nextToken();
			    	segemntTableName.add(jParser.getText());
		    	}
		    	moleculeSegmentTableNames.add(segemntTableName);
	    	}
		}, IOException.class));
		inputMap.put("MoleculeSegmentTableNames", MarsUtil.catchConsumerException(jParser -> {
	    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
		    	ArrayList<String> segemntTableName = new ArrayList<String>();
		    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
			    	//Then move past field Name - yColumnName...
			    	jParser.nextToken();
			    	
			    	segemntTableName.add(jParser.getText());
			    	
			    	//Then move past the field and next field Name - xColumnName...
			    	jParser.nextToken();
			    	jParser.nextToken();
			    	segemntTableName.add(jParser.getText());
		    	}
		    	moleculeSegmentTableNames.add(segemntTableName);
	    	}
		}, IOException.class));
		inputMap.put("MoleculeTagSet", MarsUtil.catchConsumerException(jParser -> {
	    	while (jParser.nextToken() != JsonToken.END_ARRAY)
	            tagSet.add(jParser.getText());
		}, IOException.class));
		inputMap.put("MoleculeParameterSet", MarsUtil.catchConsumerException(jParser -> {
	    	while (jParser.nextToken() != JsonToken.END_ARRAY)
	            parameterSet.add(jParser.getText());
		}, IOException.class));
		inputMap.put("Comments", MarsUtil.catchConsumerException(jParser -> {
	    	comments = jParser.getText();
		}, IOException.class));
	}
	
	/**
	 * Used to during merge MoleculeArchive merge events to merge the properties
	 * of another archive into this one.
	 * 
	 * @param properties MoleculeArchiveProperties record to merge into this one.
	 * @param archiveName Name of the archive that is being merged with this one.
	 */
	public void merge(MoleculeArchiveProperties properties, String archiveName) {
		this.numberOfMolecules += properties.getNumberOfMolecules();
		this.numMetadata += properties.getNumberOfMetadata();
		this.addComment("Comments from Merged Archive " + archiveName + ":\n" + properties.getComments() + "\n");
		
		addAllTags(properties.getTagSet());
		addAllParameters(properties.getParameterSet());
		addAllColumns(properties.getColumnSet());
		addAllSegmentTableNames(properties.getSegmentTableNames());
	}
	
	/**
	 * Add a molecule tag to the global set that contains a record of all unique tag 
	 * names that are being used.
	 */	
	public void addTag(String tag) {
		tagSet.add(tag);
	}
	
	/**
	 * Add molecule tags to the global set that contains a record of all unique tag 
	 * names that are being used.
	 */
	public void addAllTags(Set<String> tags) {
		tagSet.addAll(tags);
	}
	
	/**
	 * Get the set of molecule tag names in use.
	 */
	public Set<String> getTagSet() {
		return tagSet;
	}
	
	/**
	 * Redefine the set of molecule tags in use. 
	 */
	public void setTagSet(Set<String> tagSet) {
		this.tagSet = tagSet;
	}
	
	/**
	 * Add a molecule parameter name to the global set that contains a record of all
	 * unique parameter names that are being used.
	 */
	public void addParameter(String parameterName) {
		parameterSet.add(parameterName);
	}
	
	/**
	 * Add molecule parameter names to the global set that contains a record of all unique parameter 
	 * names that are being used.
	 */
	public void addAllParameters(Set<String> parameters) {
		parameterSet.addAll(parameterSet);
	}
	
	/**
	 * Remove a parameter name from the set of unique molecule parameter names being used.
	 */
	public void removeParameter(String parameter) {
		tagSet.remove(parameter);
	}
	
	/**
	 * Get the set of parameter names in use.
	 */
	public Set<String> getParameterSet() {
		return parameterSet;
	}
	
	/**
	 * Redefine the set of parameter names in use.
	 */
	public void setParameterSet(Set<String> parameterSet) {
		this.parameterSet = parameterSet;
	}
	
	/**
	 * Set the number of molecule in the archive.
	 */
	public void setNumberOfMolecules(int numMolecules) {
		this.numberOfMolecules = numMolecules;
	}
	
	/**
	 * Get the number of molecule in the archive.
	 */
	public int getNumberOfMolecules() {
		return numberOfMolecules;
	}
	
	/**
	 * Set the number of MarsMetadata records in the archive.
	 */
	public void setNumberOfMetadata(int numMetadata) {
		this.numMetadata = numMetadata;
	}
	
	/**
	 * Get the number of MarsMetadata records in the archive.
	 */
	public int getNumberOfMetadata() {
		return numMetadata;
	}
	
	/**
	 * Add a column name to the unique set of column names 
	 * in use in molecule DataTables.
	 */
	public void addColumn(String column) {
		this.moleculeDataTableColumnSet.add(column);
	}
	
	/**
	 * Add column names to the unique set of column names 
	 * in use in molecule DataTables.
	 */
	public void addAllColumns(Set<String> columns) {
		this.moleculeDataTableColumnSet.addAll(columns);
	}
	
	/**
	 * Add column names to the unique set of column names 
	 * in use in molecule DataTables.
	 */
	public void addAllColumns(ArrayList<String> columns) {
		this.moleculeDataTableColumnSet.addAll(columns);
	}
	
	/**
	 * Redefine the unique set of column names 
	 * in use in molecule DataTables.
	 */
	public void setColumnSet(Set<String> moleculeDataTableColumnSet) {
		this.moleculeDataTableColumnSet = moleculeDataTableColumnSet;
	}
	
	/**
	 * Get the unique set of column names 
	 * in use in molecule DataTables.
	 */
	public Set<String> getColumnSet() {
		return moleculeDataTableColumnSet;
	}
	
	/**
	 * Add a segment table name to the unique set of segment table 
	 * names found in molecule records.
	 */
	public void addSegmentTableName(ArrayList<String> segmentTableName) {
		this.moleculeSegmentTableNames.add(segmentTableName);
	}
	
	/**
	 * Add segment table names to the unique set of segment table names found in 
	 * molecule records.
	 */
	public void addAllSegmentTableNames(Set<ArrayList<String>> segmentTableNames) {
		this.moleculeSegmentTableNames.addAll(segmentTableNames);
	}
	
	/**
	 * Redefine the unique set of segment table names found in molecule records.
	 */
	public void setSegmentTableNames(Set<ArrayList<String>> moleculeSegmentTableNames) {
		this.moleculeSegmentTableNames = moleculeSegmentTableNames;
	}
	
	/**
	 * Get the unique set of segment table names found in molecule records.
	 */
	public Set<ArrayList<String>> getSegmentTableNames() {
		return moleculeSegmentTableNames;
	}
	
	/**
	 * Get archive comments.
	 */
	public String getComments() {
		return comments;
	}
	
	/**
	 * Add to archive comments.
	 */
	public void addComment(String comment) {
		this.comments += comment;
	}
	
	/**
	 * Overwrite archive comments with new set of comments.
	 */
	public void setComments(String comments) {
		this.comments = comments;
	}
	
	/**
	 * Set the parent MoleculeArchive that these MoleculeArchiveProperties belong to.
	 */
	public void setParent(MoleculeArchive<? extends Molecule, ? extends MarsMetadata, ? extends MoleculeArchiveProperties> archive) {
		this.parent = archive;
	}
}
