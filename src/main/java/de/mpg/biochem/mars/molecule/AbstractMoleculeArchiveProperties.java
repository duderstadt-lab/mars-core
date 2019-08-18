/*******************************************************************************
 * Copyright (C) 2019, Karl Duderstadt
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
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.scijava.table.DoubleColumn;
import org.scijava.table.GenericColumn;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.MarsMath;
import de.mpg.biochem.mars.util.MarsUtil;

public abstract class AbstractMoleculeArchiveProperties extends AbstractJsonConvertibleRecord implements MoleculeArchiveProperties {
	//Contains general information about the molecule archive
	//Number of molecules, ImageMetadata, tags, average size in bytes.
	//Any information we about to know without having to read the entire archive in directly.
	
	protected int numberOfMolecules;
	protected int numImageMetadata;
	protected String comments;
	
	//Not really used for anything at the moment...
	protected Set<String> tagSet;
	protected Set<String> parameterSet;
	protected Set<String> moleculeDataTableColumnSet;
	
	protected Set<ArrayList<String>> moleculeSegmentTableNames;
	
	//Reference to MoleculeArchive containing the record
	protected MoleculeArchive<? extends Molecule, ? extends MarsImageMetadata, ? extends MoleculeArchiveProperties> parent;
	
	public AbstractMoleculeArchiveProperties() {
		super();
		numberOfMolecules = 0;
		numImageMetadata = 0;
		comments = "";
		
		tagSet = ConcurrentHashMap.newKeySet();
		parameterSet = ConcurrentHashMap.newKeySet();
		moleculeDataTableColumnSet = ConcurrentHashMap.newKeySet();
		moleculeSegmentTableNames = ConcurrentHashMap.newKeySet();
	}
	
	public AbstractMoleculeArchiveProperties(JsonParser jParser) throws IOException {
		this();
		fromJSON(jParser);
	}
	
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
		outputMap.put("numImageMetadata", MarsUtil.catchConsumerException(jGenerator ->
			jGenerator.writeNumberField("numImageMetadata", numImageMetadata), IOException.class));
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
	        numImageMetadata = jParser.getIntValue();
		}, IOException.class));
		inputMap.put("numImageMetadata", MarsUtil.catchConsumerException(jParser -> {
	        numImageMetadata = jParser.getIntValue();
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
	
	public void merge(MoleculeArchiveProperties properties, String archiveName) {
		this.numberOfMolecules += properties.getNumberOfMolecules();
		this.numImageMetadata += properties.getNumImageMetadata();
		this.addComment("Comments from Merged Archive " + archiveName + ":\n" + properties.getComments() + "\n");
		
		addAllTags(properties.getTagSet());
		addAllParameters(properties.getParameterSet());
		addAllColumns(properties.getColumnSet());
		addAllSegmentTableNames(properties.getSegmentTableNames());
	}
	
	//Getters and Setters	
	public void addTag(String tag) {
		tagSet.add(tag);
	}
	
	public void addAllTags(Set<String> tags) {
		tagSet.addAll(tags);
	}
	
	public Set<String> getTagSet() {
		return tagSet;
	}
	
	public void setTagSet(Set<String> tagSet) {
		this.tagSet = tagSet;
	}
	
	public void addParameter(String parameterName) {
		parameterSet.add(parameterName);
	}
	
	public void addAllParameters(Set<String> parameters) {
		parameterSet.addAll(parameterSet);
	}
	
	public void removeParameter(String parameter) {
		tagSet.remove(parameter);
	}
	
	public Set<String> getParameterSet() {
		return parameterSet;
	}
	
	public void setParameterSet(Set<String> parameterSet) {
		this.parameterSet = parameterSet;
	}
	
	public void setNumberOfMolecules(int numMolecules) {
		this.numberOfMolecules = numMolecules;
	}
	
	public int getNumberOfMolecules() {
		return numberOfMolecules;
	}
	
	public void setNumImageMetadata(int numImageMetadata) {
		this.numImageMetadata = numImageMetadata;
	}
	
	public int getNumImageMetadata() {
		return numImageMetadata;
	}
	
	public void addColumn(String column) {
		this.moleculeDataTableColumnSet.add(column);
	}
	
	public void addAllColumns(Set<String> columns) {
		this.moleculeDataTableColumnSet.addAll(columns);
	}
	
	public void addAllColumns(ArrayList<String> columns) {
		this.moleculeDataTableColumnSet.addAll(columns);
	}
	
	public void setColumnSet(Set<String> moleculeDataTableColumnSet) {
		this.moleculeDataTableColumnSet = moleculeDataTableColumnSet;
	}
	
	public Set<String> getColumnSet() {
		return moleculeDataTableColumnSet;
	}
	
	public void addSegmentTableName(ArrayList<String> segmentTableName) {
		this.moleculeSegmentTableNames.add(segmentTableName);
	}
	
	public void addAllSegmentTableNames(Set<ArrayList<String>> segmentTableNames) {
		this.moleculeSegmentTableNames.addAll(segmentTableNames);
	}
	
	public void setSegmentTableNames(Set<ArrayList<String>> moleculeSegmentTableNames) {
		this.moleculeSegmentTableNames = moleculeSegmentTableNames;
	}
	
	public Set<ArrayList<String>> getSegmentTableNames() {
		return moleculeSegmentTableNames;
	}
	
	public String getComments() {
		return comments;
	}
	
	public void addComment(String comment) {
		this.comments += comment;
	}
	
	public void setComments(String comments) {
		this.comments = comments;
	}
	
	public void setParent(MoleculeArchive<? extends Molecule, ? extends MarsImageMetadata, ? extends MoleculeArchiveProperties> archive) {
		this.parent = archive;
	}
}
