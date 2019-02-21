/*******************************************************************************
 * MARS - MoleculeArchive Suite - A collection of ImageJ2 commands for single-molecule analysis.
 * 
 * Copyright (C) 2018 - 2019 Karl Duderstadt
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.mpg.biochem.mars.molecule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
	private LinkedHashSet<String> tagList;
	private LinkedHashSet<String> parameterList;
	
	private MoleculeArchive parent;
	
	public MoleculeArchiveProperties() {
		numberOfMolecules = 0;
		numImageMetaData = 0;
		comments = "";
		tagList = new LinkedHashSet<String>();
		parameterList = new LinkedHashSet<String>();
	}
	
	public MoleculeArchiveProperties(MoleculeArchive parent) {
		numberOfMolecules = 0;
		numImageMetaData = 0;
		comments = "";
		tagList = new LinkedHashSet<String>();
		parameterList = new LinkedHashSet<String>();
		
		this.parent = parent;
	}
	
	public MoleculeArchiveProperties(JsonParser jParser, MoleculeArchive parent) {
		numberOfMolecules = 0;
		numImageMetaData = 0;
		comments = "";
		tagList = new LinkedHashSet<String>();
		parameterList = new LinkedHashSet<String>();
		
		this.parent = parent;
		
		try {
			fromJSON(jParser);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void toJSON(JsonGenerator jGenerator) throws IOException {
		jGenerator.writeObjectFieldStart("MoleculeArchiveProperties");

		//These fields should always exist...
		jGenerator.writeNumberField("numberOfMolecules", numberOfMolecules);
		jGenerator.writeNumberField("numImageMetaData", numImageMetaData);
		
		//Write tagList array if tags have been added.
		if (tagList.size() > 0) {
			jGenerator.writeFieldName("Tags");
			jGenerator.writeStartArray();
			Iterator<String> iterator = tagList.iterator();
			while(iterator.hasNext())
				jGenerator.writeString(iterator.next());	
			jGenerator.writeEndArray();
		}
		
		//Write out arrays of parameters if parameters have been added.
		if (parameterList.size() > 0) {
			jGenerator.writeFieldName("Parameters");
			jGenerator.writeStartArray();
			Iterator<String> iterator = parameterList.iterator();
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
		    
		    if("MoleculeTagList".equals(fieldname)) {
		    	jParser.nextToken();
		    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
		            tagList.add(jParser.getText());
		        }
		    	continue;
		    }
		    
		    if("MoleculeParameterList".equals(fieldname)) {
		    	jParser.nextToken();
		    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
		            tagList.add(jParser.getText());
		        }
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
		tagList.add(tag);
	}
	
	public LinkedHashSet<String> getTagList() {
		return tagList;
	}
	
	public void setTagList(LinkedHashSet<String> tagList) {
		this.tagList = tagList;
	}
	
	public void addParameter(String parameterName) {
		parameterList.add(parameterName);
	}
	
	public void removeParameter(String parameter) {
		tagList.remove(parameter);
	}
	
	public LinkedHashSet<String> getParameterList() {
		return parameterList;
	}
	
	public void setParameterList(LinkedHashSet<String> parameterList) {
		this.parameterList = parameterList;
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
	
	public void incrementNumImageMetaData() {
		numImageMetaData++;
	}
	
	public void decrementNumImageMetaData() {
		numImageMetaData--;
	}
	
	public int getNumImageMetaData() {
		return numImageMetaData;
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
