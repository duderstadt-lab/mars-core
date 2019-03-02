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

import de.mpg.biochem.mars.table.*;
import de.mpg.biochem.mars.util.MARSMath;

import org.scijava.table.DoubleColumn;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.decimal4j.util.DoubleRounder;
import org.scijava.log.LogService;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

public class Molecule {
	//Unique ID used for Chronicle map storage and universal identification.
	private String UID;
	
	//Reference to MoleculeArchive containing the molecule
	private	MoleculeArchive parent;
	
	//UID of ImageMetaData associated wit this molecule.
	private String imageMetaDataUID;
	
	//For any notes to be made about the molecule
	private String Notes;
	
	//tags added for filtering...
	private LinkedHashSet<String> Tags;
	
	//Hashmap that maps string parameters to doubles
	private LinkedHashMap<String, Double> Parameters;
	
	//DataTable with raw data and converted data etc.. 
	private MARSResultsTable datatable;
	
	//Segments tables resulting from change point fitting
	//String is XColumn + " vs " + YColumn
	private LinkedHashMap<String, MARSResultsTable> segments;
	
	//This is a bit ugly, but we want to keep track of
	//both columns used for the changepoint plot
	//However, if you use a String[] array as a key 
	//it uses object ref and keys never match... so we just keep track here 
	//and add them above. There are other ways but I think ultimately
	//this will be the most robust...
	private LinkedHashMap<String,String[]> segmentsColumns;
	
	public Molecule(JsonParser jParser) {
		datatable = new MARSResultsTable();
		initializeVariables();
		try {
			fromJSON(jParser);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Molecule(String UID) {
		this.UID = UID;
		datatable = new MARSResultsTable();
		initializeVariables();
	}

	public Molecule(String UID, MARSResultsTable results) {
		this.UID = UID;
		datatable = results;
		initializeVariables();
	}
	
	private void initializeVariables() {
		segments = new LinkedHashMap<>();
		segmentsColumns = new LinkedHashMap<>();
		Parameters = new LinkedHashMap<>();
		Tags = new LinkedHashSet<String>();
	}
	
	//jackson custom JSON serialization 
	public void toJSON(JsonGenerator jGenerator) throws IOException {
		jGenerator.writeStartObject();
		
		//write out UID - all molecules must have this field.
		jGenerator.writeStringField("UID", UID);
		
		if (imageMetaDataUID != null)
			jGenerator.writeStringField("ImageMetaDataUID", imageMetaDataUID);
		
		//Write out notes if there are any
		if (Notes != null)
			jGenerator.writeStringField("Notes", Notes);
		
		//Write out arrays of tags if tags have been added.
		if (Tags.size() > 0) {
			jGenerator.writeFieldName("Tags");
			jGenerator.writeStartArray();
			Iterator<String> iterator = Tags.iterator();
			while(iterator.hasNext())
				jGenerator.writeString(iterator.next());
			jGenerator.writeEndArray();
		}
		
		//Write out parameters, which are number fields used to filter and process the molecule..
		if (Parameters.size() > 0) {
			jGenerator.writeObjectFieldStart("Parameters");
			for (String name:Parameters.keySet())
				jGenerator.writeNumberField(name, Parameters.get(name));
			jGenerator.writeEndObject();
		}
 		
		//Write out data table (will do nothing if there are no columns
		if (datatable.getColumnCount() > 0) {
			jGenerator.writeFieldName("DataTable");
			datatable.toJSON(jGenerator);
		}
		
		//Write out segment tables generated from KCP as object that have two fields that store the x column and y column names used during KCP
		if (segments.size() > 0) {
			jGenerator.writeArrayFieldStart("SegmentTables");
			for (String tableName:segments.keySet()) {
				if (segments.get(tableName).size() > 0) {
					jGenerator.writeStartObject();
					
					String[] YX = segmentsColumns.get(tableName);
					jGenerator.writeStringField("yColumnName", YX[0]);
					jGenerator.writeStringField("xColumnName", YX[1]);
					
					jGenerator.writeFieldName("Table");
					segments.get(tableName).toJSON(jGenerator);
					
					jGenerator.writeEndObject();
				}
			}
			jGenerator.writeEndArray();
		}
		jGenerator.writeEndObject();
	}
	
	//jackson custom JSON deserialization
	public void fromJSON(JsonParser jParser) throws IOException {
		//We assume a molecule object and just been detected and now we want to parse all the values into this molecule entry.
		while (jParser.nextToken() != JsonToken.END_OBJECT) {
		    String fieldname = jParser.getCurrentName();

		    if (fieldname == null)
		    	continue;
		    
		    if ("UID".equals(fieldname)) {
		    	jParser.nextToken();
		        UID = jParser.getText();
		        continue;
		    }
		    
		    if ("ImageMetaDataUID".equals(fieldname)) {
		    	jParser.nextToken();
		    	imageMetaDataUID = jParser.getText();
		    	continue;
		    }
		    
		    if ("Notes".equals(fieldname)) {
		    	jParser.nextToken();
		        Notes = jParser.getText();
		        continue;
		    }
		    
		    if("Tags".equals(fieldname)) {
		    	//First we move past object start ?
		    	jParser.nextToken();
		    	
		    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
		            Tags.add(jParser.getText());
		        }
		    	continue;
		    }
			    
		    if("Parameters".equals(fieldname)) {
		    	//First we move past object start ?
		    	jParser.nextToken();
		    	
		    	//Then we move through fields
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
		    	continue;
		    }
		    
		    if("DataTable".equals(fieldname)) {
			    datatable.fromJSON(jParser);
			    continue;
		    }
		    
		    if("SegmentTables".equals(fieldname)) {
		    	jParser.nextToken();
		    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
			    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
				    	//Then move past field Name - xColumnName...
				    	jParser.nextToken();
				    	
				    	//Should we create a special kind of table or just parse with a space?
				    	String[] columnNames = new String[2];
				    	columnNames[0] = jParser.getText();
				    	
				    	//Then move past the field and next field Name - yColumnName...
				    	jParser.nextToken();
				    	jParser.nextToken();
				    	columnNames[1] = jParser.getText();
				    	
				    	String tableKey = columnNames[0] + " vs " + columnNames[1];
				    	
				    	MARSResultsTable segmenttable = new MARSResultsTable(tableKey);
				    	
				    	//Move past Table
				    	jParser.nextToken();
				    	
				    	segmenttable.fromJSON(jParser);
				    	
				    	segmentsColumns.put(tableKey, columnNames);
				    	segments.put(tableKey, segmenttable);
			    	}
		    	}
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
		    	System.out.println("unknown object encountered in molecule record ... skipping");
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
	
	public String toJSONString() {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();

		JsonFactory jfactory = new JsonFactory();
		JsonGenerator jGenerator;
		try {
			jGenerator = jfactory.createGenerator(stream, JsonEncoding.UTF8);
			toJSON(jGenerator);
			jGenerator.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return stream.toString();
	}
	
	//Getters and Setters
	public String getUID() {
		return UID;
	}
	
	//We don't provide a method to set the UID... It should never be changed and only set on creation and loading...
	
	public void setImageMetaDataUID(String uid) {
		imageMetaDataUID = uid;
	}
	
	public String getImageMetaDataUID() {
		return imageMetaDataUID;
	}

	public String getNotes() {
		return Notes;
	}
	
	public void setNotes(String Notes) {
		this.Notes = Notes;
	}
	
	public void addNote(String Note) {
		Notes += Note;
	}
	
	public void addTag(String tag) {
		Tags.add(tag);
		if (parent != null) {
			parent.getProperties().addTag(tag);
		}
	}
	
	public void removeTag(String tag) {
		Tags.remove(tag);
	}
	
	public void removeAllTags() {
		Tags.clear();
	}
	
	public void setParameter(String parameter, double value) {
		Parameters.put(parameter, value);
		if (parent != null) {
			parent.getProperties().addParameter(parameter);
		}
	}
	
	public void removeAllParameters() {
		Parameters.clear();
	}
	
	public void removeParameter(String parameter) {
		if (Parameters.containsKey(parameter)) {
			Parameters.remove(parameter);
		}
	}
	
	public double getParameter(String parameter) {
		if (Parameters.containsKey(parameter)) {
			return Parameters.get(parameter);
		} else {
			return Double.NaN;
		}
	}
	
	public boolean hasParameter(String parameter) {
		return Parameters.containsKey(parameter);
	}
	
	public boolean hasTag(String tag) {
		return Tags.contains(tag);
	}
	
	public boolean hasNoTags() {
		return Tags.size() == 0;
	}
	
	public LinkedHashMap<String, Double> getParameters() {
		return Parameters;
	}
	
	public LinkedHashSet<String> getTags() {
		return Tags;
	}
		
	public void setSegmentsTable(String yColumnName, String xColumnName, MARSResultsTable segs) {
		String[] columnNames = new String[2];
		columnNames[0] = yColumnName;
		columnNames[1] = xColumnName;
		setSegmentsTable(columnNames, segs);
	}
	
	public void setSegmentsTable(String[] columnNames, MARSResultsTable segs) {
		String str = columnNames[0] + " vs " + columnNames[1];
		segmentsColumns.put(str, columnNames);
		segments.put(str, segs);
	}
	
	public String[] getSegmentTableColumns(String key) {
		if (segmentsColumns.containsKey(key))
			return segmentsColumns.get(key);
		else
			return null;
	}
	
	public MARSResultsTable getSegmentsTable(String key) {
		if (segments.containsKey(key))
			return segments.get(key);
		else 
			return null;
	}
	
	public void removeSegmentsTable(String yColumnName, String xColumnName) {
		String[] columnNames = new String[2];
		columnNames[0] = yColumnName;
		columnNames[1] = xColumnName;
		removeSegmentsTable(columnNames);
	}
	
	public void removeSegmentsTable(String[] columnNames) {
		String str = columnNames[0] + " " + columnNames[1];
		segmentsColumns.remove(str);
		segments.remove(str);
	}
	
	public MARSResultsTable getSegmentsTable(String yColumnName, String xColumnName) {
		String[] columnNames = new String[2];
		columnNames[0] = yColumnName;
		columnNames[1] = xColumnName;
		return getSegmentsTable(columnNames);
	}
	
	public ArrayList<String> getSegmentTableNames() {
		return new ArrayList<String>(segments.keySet());
	}
	
	
	public MARSResultsTable getSegmentsTable(String[] columnNames) {
		String str = columnNames[0] + " vs " + columnNames[1];
		return segments.get(str);
	}
	
	public MARSResultsTable getDataTable() {
		return datatable;
	}
	
	public void setDataTable(MARSResultsTable table) {
		//This means we are resetting all the data...
		datatable.clear();
		
		//Now set to new table
		datatable = table;
	}
	
	public void setParent(MoleculeArchive archive) {
		parent = archive;
	}
}
