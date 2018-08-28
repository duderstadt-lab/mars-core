package de.mpg.biochem.sdmm.molecule;

import de.mpg.biochem.sdmm.table.*;
import de.mpg.biochem.sdmm.util.SDMMMath;
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
	//Unique ID used for Chronicle map storage and universal identification.
	private String UID;
	
	//Reference to MoleculeArchive containing the molecule
	private	MoleculeArchive parent;
	
	//UID of ImageMetaData associated wit this molecule.
	private String imageMetaDataUID;
	
	//For any notes to be made about the molecule
	private String notes;
	
	//tags added for filtering...
	private LinkedHashSet<String> tags;
	
	//Hashmap that maps string parameters to doubles
	private LinkedHashMap<String, Double> parameters;
	
	//DataTable with raw data and converted data etc.. 
	private SDMMResultsTable datatable;
	
	//Segments tables resulting from change point fitting
	//String is XColumn + YColumn
	private LinkedHashMap<String, SDMMResultsTable> segments;
	
	//This is a bit ugly, but we want to keep track of
	//both columns used for the changepoint plot
	//However, if you use a String[] array as a key 
	//it uses object ref and keys never match... so we just keep track here 
	//and add them tomorrow above. There are other ways but I think ultimately
	//this will be the most robust...
	private LinkedHashMap<String,String[]> segmentsColumns;
	
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
		    		if (jParser.currentToken().equals(JsonToken.VALUE_STRING)) {
	    				String str = jParser.getValueAsString();
	    				if (Objects.equals(str, new String("Infinity"))) {
	    					parameters.put(subfieldname, Double.POSITIVE_INFINITY);
	    				} else if (Objects.equals(str, new String("-Infinity"))) {
	    					parameters.put(subfieldname, Double.NEGATIVE_INFINITY);
	    				} else if (Objects.equals(str, new String("NaN"))) {
	    					parameters.put(subfieldname, Double.NaN);
	    				}
	    			} else {
	    				parameters.put(subfieldname, jParser.getDoubleValue());
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
				    	
				    	SDMMResultsTable segmenttable = new SDMMResultsTable(columnNames[0] + " vs " + columnNames[1]);
				    	
				    	//Move past Table
				    	jParser.nextToken();
				    	
				    	segmenttable.fromJSON(jParser);
				    	
				    	String str = columnNames[0] + " " + columnNames[1];
				    	segmentsColumns.put(str, columnNames);
				    	segments.put(str, segmenttable);
			    	}
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
}
