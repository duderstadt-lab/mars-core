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
package de.mpg.biochem.sdmm.molecule;

import de.mpg.biochem.sdmm.table.*;
import de.mpg.biochem.sdmm.util.SDMMMath;
import org.scijava.table.DoubleColumn;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

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
	private SDMMResultsTable datatable;
	
	//Segments tables resulting from change point fitting
	//String is XColumn + " vs " + YColumn
	private LinkedHashMap<String, SDMMResultsTable> segments;
	
	//This is a bit ugly, but we want to keep track of
	//both columns used for the changepoint plot
	//However, if you use a String[] array as a key 
	//it uses object ref and keys never match... so we just keep track here 
	//and add them above. There are other ways but I think ultimately
	//this will be the most robust...
	private LinkedHashMap<String,String[]> segmentsColumns;
	
	//Really ugly HACK to also store ImageMetaData and Archive Properties in the virtual store
	private ImageMetaData WrappedImageMetaData;
	private MoleculeArchiveProperties WrappedMoleculeArchiveProperties;
	
	public Molecule(JsonParser jParser) {
		datatable = new SDMMResultsTable();
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
		datatable = new SDMMResultsTable();
		initializeVariables();
	}

	public Molecule(String UID, SDMMResultsTable results) {
		this.UID = UID;
		datatable = results;
		initializeVariables();
	}
	
	//If we are just using this as a wrapper for ImageMetaData
	public Molecule(ImageMetaData meta) {
		WrappedImageMetaData = meta;
	}
	
	public ImageMetaData UnwrapImageMetaData() {
		return WrappedImageMetaData;
	}
	
	//If we are just using this as a wrapper for MoleculeArchiveProperties
	public Molecule(MoleculeArchiveProperties props) {
		WrappedMoleculeArchiveProperties = props;
	}
	
	public MoleculeArchiveProperties UnwrapMoleculeArchiveProperties() {
		return WrappedMoleculeArchiveProperties;
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
		
		//Check if this is a Wrapper molecule...
		//If so process accordingly..
		if (WrappedMoleculeArchiveProperties != null) {
			WrappedMoleculeArchiveProperties.toJSON(jGenerator);
			return;
		} else if (WrappedImageMetaData != null) {
			jGenerator.writeFieldName("ImageMetaDataItem");
			WrappedImageMetaData.toJSON(jGenerator);
			return;
		}
		
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
		    
		  //Check if this is a Wrapper molecule...
			if ("MoleculeArchiveProperties".equals(fieldname)) {
				//This must be a MoleculeArchive Properties Wrapper record
				WrappedMoleculeArchiveProperties = new MoleculeArchiveProperties(jParser, this.parent);
				return;
			} else if ("ImageMetaDataItem".equals(fieldname)) {
				WrappedImageMetaData = new ImageMetaData(jParser);
				return;
			}
		    
		    if ("UID".equals(fieldname)) {
		    	jParser.nextToken();
		        UID = jParser.getText();
		    }
		    
		    if ("ImageMetaDataUID".equals(fieldname)) {
		    	jParser.nextToken();
		    	imageMetaDataUID = jParser.getText();
		    }
		    
		    if ("Notes".equals(fieldname)) {
		    	jParser.nextToken();
		        Notes = jParser.getText();
		    }
		    
		    if("Tags".equals(fieldname)) {
		    	//First we move past object start ?
		    	jParser.nextToken();
		    	
		    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
		            Tags.add(jParser.getText());
		        }
		    }
			    
		    if("Parameters".equals(fieldname)) {
		    	//First we move past object start ?
		    	jParser.nextToken();
		    	
		    	//Then we move through fields
		    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
		    		String subfieldname = jParser.getCurrentName();
		    		jParser.nextToken();
		    		if (jParser.currentToken().equals(JsonToken.VALUE_STRING)) {
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
		    }
		    
		    if("DataTable".equals(fieldname)) {
			    datatable.fromJSON(jParser);
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
				    	
				    	SDMMResultsTable segmenttable = new SDMMResultsTable(tableKey);
				    	
				    	//Move past Table
				    	jParser.nextToken();
				    	
				    	segmenttable.fromJSON(jParser);
				    	
				    	segmentsColumns.put(tableKey, columnNames);
				    	segments.put(tableKey, segmenttable);
			    	}
		    	}
		    }
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
			parent.getArchiveProperties().addTag(tag);
			parent.updateTagIndex(UID);
		}
	}
	
	public void removeTag(String tag) {
		Tags.remove(tag);
		if (parent != null) {
			parent.getArchiveProperties().removeTag(tag);
			parent.updateTagIndex(UID);
		}
	}
	
	public void removeAllTags() {
		Tags.clear();
		if (parent != null) {
			parent.updateTagIndex(UID);
		}
	}
	
	public void setParameter(String parameter, double value) {
		Parameters.put(parameter, value);
		if (parent != null) {
			parent.getArchiveProperties().addParameter(parameter);
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
		
	public void setSegmentsTable(String yColumnName, String xColumnName, SDMMResultsTable segs) {
		String[] columnNames = new String[2];
		columnNames[0] = yColumnName;
		columnNames[1] = xColumnName;
		setSegmentsTable(columnNames, segs);
	}
	
	public void setSegmentsTable(String[] columnNames, SDMMResultsTable segs) {
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
	
	public SDMMResultsTable getSegmentsTable(String key) {
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
	
	public SDMMResultsTable getSegmentsTable(String yColumnName, String xColumnName) {
		String[] columnNames = new String[2];
		columnNames[0] = yColumnName;
		columnNames[1] = xColumnName;
		return getSegmentsTable(columnNames);
	}
	
	public ArrayList<String> getSegmentTableNames() {
		return new ArrayList<String>(segments.keySet());
	}
	
	
	public SDMMResultsTable getSegmentsTable(String[] columnNames) {
		String str = columnNames[0] + " vs " + columnNames[1];
		return segments.get(str);
	}
	
	public SDMMResultsTable getDataTable() {
		return datatable;
	}
	
	public void setDataTable(SDMMResultsTable table) {
		//This means we are resetting all the data...
		datatable.clear();
		
		//Now set to new table
		datatable = table;
	}
	
	public void setParentArchive(MoleculeArchive archive) {
		parent = archive;
	}
}
