package de.mpg.biochem.sdmm.molecule;

import de.mpg.biochem.sdmm.table.*;
import net.imagej.table.DoubleColumn;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesMarshallable;
import net.openhft.chronicle.bytes.BytesOut;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.io.IOException;
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class Molecule {
	//Precision in number of decimal places for output arrays
	final static int DECIMAL_PLACE_PRECISION = 7;
	
	//Unique ID used for Chronicle map storage and universal identification.
	String UID;
	
	//Reference to MoleculeArchive containing the molecule
	MoleculeArchive parent;
	
	//UID of ImageMetaData associated wit this molecule.
	String imageMetaDataUID;
	
	//For any notes to be made about the molecule
	String notes;
	
	//tags added for filtering...
	LinkedHashSet<String> tags;
	
	//Hashmap that maps string parameters to doubles
	LinkedHashMap<String, Double> parameters;
	
	//DataTable with raw data and converted data etc.. 
	SDMMResultsTable datatable;
	
	//Segments tables resulting from change point fitting
	//String is XColumn + YColumn
	LinkedHashMap<String, SDMMResultsTable> segments;
	
	//This is a bit ugly, but we want to keep track of
	//both columns used for the changepoint plot
	//However, if you use a String[] array as a key 
	//it uses object ref and keys never match... so we just keep track here 
	//and add them tomorrow above. There are other ways but I think ultimately
	//this will be the most robust...
	LinkedHashMap<String,String[]> segmentsColumns;
	
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
	
	//Added for debugging..
	private LogService logService;
	
	public Molecule(JsonParser jParser, LogService logService) {
		this.logService = logService;
		datatable = new SDMMResultsTable();
		initializeVariables();
		try {
			fromJSON(jParser);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	//end Addition
	
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
	
	private void initializeVariables() {
		segments = new LinkedHashMap<>();
		segmentsColumns = new LinkedHashMap<>();
		parameters = new LinkedHashMap<>();
		tags = new LinkedHashSet<String>();
	}
	
	//jackson custom JSON serialization 
	public void toJSON(JsonGenerator jGenerator) throws IOException {
		jGenerator.writeStartObject();
		
		//write out UID - all molecules must have this field.
		jGenerator.writeStringField("UID", UID);
		
		if (imageMetaDataUID != null)
			jGenerator.writeStringField("ImageMetaDataUID", imageMetaDataUID);
		
		//Write out notes if there are any
		if (notes != null)
			jGenerator.writeStringField("notes", notes);
		
		//Write out arrays of tags if tags have been added.
		if (tags.size() > 0) {
			jGenerator.writeFieldName("tags");
			jGenerator.writeStartArray();
			Iterator<String> iterator = tags.iterator();
			while(iterator.hasNext())
				jGenerator.writeString(iterator.next());
			jGenerator.writeEndArray();
		}
		
		//Write out parameters, which are number fields used to filter and process the molecule..
		if (parameters.size() > 0) {
			jGenerator.writeObjectFieldStart("Parameters");
			for (String name:parameters.keySet())
				jGenerator.writeNumberField(name, parameters.get(name));
			jGenerator.writeEndObject();
		}
 		
		//Write out raw data table if there are columns
		if (datatable.size() > 0) {
			jGenerator.writeObjectFieldStart("DataTable");
			for (int i=0;i<datatable.getColumnCount();i++) {
				jGenerator.writeFieldName(datatable.getColumnHeader(i));
		 		jGenerator.writeArray(roundArray(datatable.get(i).getArray()), 0, datatable.getRowCount());
			}
			jGenerator.writeEndObject();
		}
		
		//Write out segment tables generated from KCP as object that have two fields that store the x column and y column names used during KCP
		if (segments.size() > 0) {
			jGenerator.writeArrayFieldStart("SegmentTables");
			for (String tableName:segments.keySet()) {
				jGenerator.writeStartObject();
				String[] YX = segmentsColumns.get(tableName);
				jGenerator.writeStringField("yColumnName", YX[0]);
				jGenerator.writeStringField("xColumnName", YX[1]);
				if (segments.get(tableName).size() > 0) {
					for (int i=0;i<segments.get(tableName).getColumnCount();i++) {
						jGenerator.writeFieldName(segments.get(tableName).getColumnHeader(i));
				 		jGenerator.writeArray(roundArray(segments.get(tableName).get(i).getArray()), 0, segments.get(tableName).getRowCount());
					}
				}
				jGenerator.writeEndObject();
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
		    
		    if ("UID".equals(fieldname)) {
		    	jParser.nextToken();
		        UID = jParser.getText();
		    }
		    
		    if ("ImageMetaDataUID".equals(fieldname)) {
		    	jParser.nextToken();
		    	imageMetaDataUID = jParser.getText();
		    }
		    
		    if ("notes".equals(fieldname)) {
		    	jParser.nextToken();
		        notes = jParser.getText();
		    }
		    
		    if("tags".equals(fieldname)) {
		    	//First we move past object start ?
		    	jParser.nextToken();
		    	
		    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
		            tags.add(jParser.getText());
		        }
		    }
			    
		    if("Parameters".equals(fieldname)) {
		    	//First we move past object start ?
		    	jParser.nextToken();
		    	
		    	//Then we move through fields
		    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
		    		String subfieldname = jParser.getCurrentName();
		    		jParser.nextToken();
		    		parameters.put(subfieldname, jParser.getDoubleValue());
		    	}
		    }
		    
		    if("DataTable".equals(fieldname)) {
		    	//First we move past object start
		    	jParser.nextToken();
		    	
		    	//Then we move through fields
		    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
		    		String ColumnName = jParser.getCurrentName();
		    		
		    		DoubleColumn column = new DoubleColumn(ColumnName);
		    		
		    		//Have to move past array start
		    		jParser.nextToken();
					
		    		while (jParser.nextToken() != JsonToken.END_ARRAY) {
		    			column.add(jParser.getDoubleValue());
		    		}
		    		datatable.add(column);
		    	}
		    }
		    
		    if("SegmentTables".equals(fieldname)) {
		    	jParser.nextToken();
		    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
			    	//First we move past object start
			    	jParser.nextToken();
			    	
			    	//Then move past field Name - xColumnName...
			    	jParser.nextToken();
			    	
			    	//Should we create a special kind of table or just parse with a space?
			    	String[] columnNames = new String[2];
			    	columnNames[0] = jParser.getText();
			    	
			    	//Then move past the field and next field Name - yColumnName...
			    	jParser.nextToken();
			    	jParser.nextToken();
			    	columnNames[1] = jParser.getText();
			    	
			    	SDMMResultsTable segmenttable = new SDMMResultsTable(columnNames[0] + " vs " + columnNames[1]);
			    	
			    	//Then we move through fields
			    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
			    		String ColumnName = jParser.getCurrentName();
			    		
			    		DoubleColumn column = new DoubleColumn(ColumnName);
	
			    		//Have to move past array start
			    		jParser.nextToken();
			    		
			    		while (jParser.nextToken() != JsonToken.END_ARRAY) {
			    			if (jParser.currentToken().equals(JsonToken.VALUE_STRING)) {
			    				String str = jParser.getValueAsString();
			    				if (Objects.equals(str, new String("Infinity"))) {
			    					column.add(Double.POSITIVE_INFINITY);
			    				} else if (Objects.equals(str, new String("-Infinity"))) {
			    					column.add(Double.NEGATIVE_INFINITY);
			    				} else if (Objects.equals(str, new String("NaN"))) {
			    					column.add(Double.NaN);
			    				}
			    			} else {
			    				column.add(jParser.getDoubleValue());
			    			}
			    		}
			    		segmenttable.add(column);
			    	}
			    	String str = columnNames[0] + " " + columnNames[1];
			    	segmentsColumns.put(str, columnNames);
			    	segments.put(str, segmenttable);
		    	}
		    }
		}
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
		return notes;
	}
	
	public void setNotes(String notes) {
		this.notes = notes;
	}
	
	public void addNote(String note) {
		notes += note;
	}
	
	public void addTag(String tag) {
		tags.add(tag);
		if (parent != null) {
			parent.getArchiveProperties().addTag(tag);
		}
	}
	
	public void removeTag(String tag) {
		tags.remove(tag);
	}
	
	public void setParameter(String parameter, double value) {
		parameters.put(parameter, value);
		if (parent != null) {
			parent.getArchiveProperties().addParameter(parameter);
		}
	}
	
	public void removeParameter(String parameter) {
		parameters.remove(parameter);
	}
	
	public double getParameter(String parameter) {
		return parameters.get(parameter);
	}
	
	public boolean hasParameter(String parameter) {
		return parameters.containsKey(parameter);
	}
	
	public boolean hasTag(String tag) {
		return tags.contains(tag);
	}
	
	public LinkedHashMap<String, Double> getParameters() {
		return parameters;
	}
	
	public LinkedHashSet<String> getTags() {
		return tags;
	}
		
	public void setSegmentsTable(String yColumnName, String xColumnName, SDMMResultsTable segs) {
		String[] columnNames = new String[2];
		columnNames[0] = yColumnName;
		columnNames[1] = xColumnName;
		setSegmentsTable(columnNames, segs);
	}
	
	public void setSegmentsTable(String[] columnNames, SDMMResultsTable segs) {
		String str = columnNames[0] + " " + columnNames[1];
		segmentsColumns.put(str, columnNames);
		segments.put(str, segs);
		
		logService.info(UID + " segmentsColumns size " + segmentsColumns.size());
		for (String key: segmentsColumns.keySet()) 
			logService.info(UID + " " + key + " " + segmentsColumns.get(key)[0] + " " + segmentsColumns.get(key)[1]);
		
		logService.info(UID + " segments size " + segments.size());
		for (String key: segments.keySet())
			logService.info(UID + " " + key);
	}
	
	public String[] getSegmentTableColumns(String key) {
		return segmentsColumns.get(key);
	}
	
	public SDMMResultsTable getSegmentsTable(String key) {
		return segments.get(key);
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
	
	public LinkedHashMap<String, SDMMResultsTable> getSegmentTables() {
		return segments;
	}
	
	public SDMMResultsTable getSegmentsTable(String[] columnNames) {
		String str = columnNames[0] + " " + columnNames[1];
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
	
	public double[] roundArray(double[] input) {
		double[] output = new double[input.length];
		for (int i=0;i<input.length;i++) {
			output[i] = DoubleRounder.round(input[i], DECIMAL_PLACE_PRECISION);
		}
		return output;
	}
}
