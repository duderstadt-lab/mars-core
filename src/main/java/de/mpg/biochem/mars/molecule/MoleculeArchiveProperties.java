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
	private LinkedHashSet<String> tags;
	private LinkedHashSet<String> parameters;
	
	private MoleculeArchive parent;
	
	public MoleculeArchiveProperties() {
		numberOfMolecules = 0;
		numImageMetaData = 0;
		comments = "";
		tags = new LinkedHashSet<String>();
		parameters = new LinkedHashSet<String>();
	}
	
	public MoleculeArchiveProperties(MoleculeArchive parent) {
		numberOfMolecules = 0;
		numImageMetaData = 0;
		comments = "";
		tags = new LinkedHashSet<String>();
		parameters = new LinkedHashSet<String>();
		
		this.parent = parent;
	}
	
	public MoleculeArchiveProperties(JsonParser jParser, MoleculeArchive parent) {
		numberOfMolecules = 0;
		numImageMetaData = 0;
		comments = "";
		tags = new LinkedHashSet<String>();
		parameters = new LinkedHashSet<String>();
		
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
		
		//Write out arrays of tags if tags have been added.
		if (tags.size() > 0) {
			jGenerator.writeFieldName("tags");
			jGenerator.writeStartArray();
			Iterator<String> iterator = tags.iterator();
			while(iterator.hasNext())
				jGenerator.writeString(iterator.next());	
			jGenerator.writeEndArray();
		}
		
		//Write out arrays of parameters if parameters have been added.
		if (parameters.size() > 0) {
			jGenerator.writeFieldName("parameters");
			jGenerator.writeStartArray();
			Iterator<String> iterator = parameters.iterator();
			while(iterator.hasNext())
				jGenerator.writeString(iterator.next());	
			jGenerator.writeEndArray();
		}
		
		if (!comments.equals(""))
			jGenerator.writeStringField("Comments", comments);
		
		jGenerator.writeEndObject();
	}
	
	public void fromJSON(JsonParser jParser) throws IOException {
		while (jParser.nextToken() != JsonToken.END_OBJECT) {
		    String fieldname = jParser.getCurrentName();
		    
		    if ("numberOfMolecules".equals(fieldname)) {
		        jParser.nextToken();
		        numberOfMolecules = jParser.getValueAsInt();
		        //parent.getLogService().info("setting number of molecules " + numberOfMolecules);
		    }
		 
		    if ("numImageMetaData".equals(fieldname)) {
		        jParser.nextToken();
		        numImageMetaData = jParser.getIntValue();
		    }
		    
		    if("tags".equals(fieldname)) {
		    	jParser.nextToken();
		    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
		            tags.add(jParser.getText());
		        }
		    }
		    
		    if("parameters".equals(fieldname)) {
		    	jParser.nextToken();
		    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
		            tags.add(jParser.getText());
		        }
		    }
		    
		    if("Comments".equals(fieldname)) {
		    	jParser.nextToken();
		    	comments = jParser.getText();
		    }
		}
	}
	
	//Getters and Setters
	public void removeTag(String tag) {
		tags.remove(tag);
	}
	
	public void addTag(String tag) {
		tags.add(tag);
	}
	
	public LinkedHashSet<String> getTags() {
		return tags;
	}
	
	public void addParameter(String parameter) {
		parameters.add(parameter);
	}
	
	public void removeParameter(String parameter) {
		tags.remove(parameter);
	}
	
	public LinkedHashSet<String> getParameters() {
		return parameters;
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
