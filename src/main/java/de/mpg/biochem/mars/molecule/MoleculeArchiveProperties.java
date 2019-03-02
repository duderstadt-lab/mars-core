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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.scijava.table.DoubleColumn;
import org.scijava.table.GenericColumn;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.util.MARSMath;

public class MoleculeArchiveProperties {
	//Contains general information about the molecule archive
	//Number of molecules, ImageMetaData, tags, average size in bytes.
	//Any information we about to know without having to read the entire archive in directly.
	
	private int numberOfMolecules;
	private int numImageMetaData;
	private String comments;
	
	//Not really used for anything at the moment...
	private Set<String> tagSet;
	private Set<String> parameterSet;
	private Set<String> moleculeDataTableColumnSet;
	
	public MoleculeArchiveProperties() {
		initializeVariables();
	}
	
	public MoleculeArchiveProperties(JsonParser jParser, MoleculeArchive parent) throws IOException {
		initializeVariables();
		
		fromJSON(jParser);
	}
	
	private void initializeVariables() {
		numberOfMolecules = 0;
		numImageMetaData = 0;
		comments = "";
		
		tagSet = ConcurrentHashMap.newKeySet();
		parameterSet = ConcurrentHashMap.newKeySet();
		moleculeDataTableColumnSet = ConcurrentHashMap.newKeySet();
	}
	
	public void toJSON(JsonGenerator jGenerator) throws IOException {
		jGenerator.writeObjectFieldStart("MoleculeArchiveProperties");

		//These fields should always exist...
		jGenerator.writeNumberField("numberOfMolecules", numberOfMolecules);
		jGenerator.writeNumberField("numImageMetaData", numImageMetaData);
		
		//Write moleculeDataTableColumnSet array if there are column it in.
		if (moleculeDataTableColumnSet.size() > 0) {
			jGenerator.writeFieldName("MoleculeDataTableColumnSet");
			jGenerator.writeStartArray();
			Iterator<String> iterator = moleculeDataTableColumnSet.iterator();
			while(iterator.hasNext())
				jGenerator.writeString(iterator.next());	
			jGenerator.writeEndArray();
		}
		
		//Write tagSet array if tags have been added.
		if (tagSet.size() > 0) {
			jGenerator.writeFieldName("MoleculeTagSet");
			jGenerator.writeStartArray();
			Iterator<String> iterator = tagSet.iterator();
			while(iterator.hasNext())
				jGenerator.writeString(iterator.next());	
			jGenerator.writeEndArray();
		}
		
		//Write out arrays of parameters if parameters have been added.
		if (parameterSet.size() > 0) {
			jGenerator.writeFieldName("MoleculeParameterSet");
			jGenerator.writeStartArray();
			Iterator<String> iterator = parameterSet.iterator();
			while(iterator.hasNext())
				jGenerator.writeString(iterator.next());	
			jGenerator.writeEndArray();
		}
		
		if (!comments.equals(""))
			jGenerator.writeStringField("Comments", comments);
		
		jGenerator.writeEndObject();
	}
	
	public void fromJSON(JsonParser jParser) throws IOException {
		jParser.nextToken();
		while (jParser.nextToken() != JsonToken.END_OBJECT) {
		    String fieldname = jParser.getCurrentName();

		    if (fieldname == null)
		    	continue;
		    	
		    if ("numberOfMolecules".equals(fieldname)) {
		        jParser.nextToken();
		        numberOfMolecules = jParser.getValueAsInt();
		        continue;
		    }
		 
		    if ("numImageMetaData".equals(fieldname)) {
		        jParser.nextToken();
		        numImageMetaData = jParser.getIntValue();
		        continue;
		    }
		    
		    if("MoleculeDataTableColumnSet".equals(fieldname)) {
		    	jParser.nextToken();
		    	while (jParser.nextToken() != JsonToken.END_ARRAY)
		    		moleculeDataTableColumnSet.add(jParser.getText());
		    	continue;
		    }
		    
		    if("MoleculeTagSet".equals(fieldname)) {
		    	jParser.nextToken();
		    	while (jParser.nextToken() != JsonToken.END_ARRAY)
		            tagSet.add(jParser.getText());
		    	continue;
		    }
		    
		    if("MoleculeParameterSet".equals(fieldname)) {
		    	jParser.nextToken();
		    	while (jParser.nextToken() != JsonToken.END_ARRAY)
		            tagSet.add(jParser.getText());
		    	continue;
		    }
		    
		    if("Comments".equals(fieldname)) {
		    	jParser.nextToken();
		    	comments = jParser.getText();
		    	continue;
		    }
		    
		    //SHOULD BE UNREACHABLE
		    //This is only reached if there is an unexpected field added to the json record
		    //In that case we simply pass through it
		    //This ensure if extra fields are added in the future
		    //old versions will be able to open the new files
		    //However, the missing fields will not be saved properly
		    //In the case of a virtual archive new fields will be systematically removed as records are opened and saved...
		    if (jParser.getCurrentToken() == JsonToken.START_OBJECT) {
		    	System.out.println("unknown object encountered in MoleculeArchiveProperties record ... skipping");
		    	passThroughUnknownObjects(jParser);
		    }
		}
	}
	
	private void passThroughUnknownObjects(JsonParser jParser) throws IOException {
    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
    		if (jParser.getCurrentToken() == JsonToken.START_OBJECT)
    			passThroughUnknownObjects(jParser);
    	}
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
	
	public void setNumImageMetaData(int numImageMetaData) {
		this.numImageMetaData = numImageMetaData;
	}
	
	public int getNumImageMetaData() {
		return numImageMetaData;
	}
	
	public void addColumn(String column) {
		this.moleculeDataTableColumnSet.add(column);
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
	
	public String getComments() {
		return comments;
	}
	
	public void addComment(String comment) {
		this.comments += comment;
	}
	
	public void setComments(String comments) {
		this.comments = comments;
	}
}
