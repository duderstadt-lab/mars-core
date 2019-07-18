package de.mpg.biochem.mars.molecule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.kcp.commands.KCPCommand;
import de.mpg.biochem.mars.table.MarsResultsTable;
import de.mpg.biochem.mars.util.MarsUtil;

public abstract class AbstractMolecule extends AbstractMarsRecord implements Molecule {
	//UID of ImageMetaData associated wit this molecule.
	protected String imageMetaDataUID;
	
	//Segments tables resulting from change point fitting
	//ArrayList has two items:
	//XColumn is at index 0
	//YColumn is at index 1
	protected LinkedHashMap<ArrayList<String>, MarsResultsTable> segmentTables;
	
	/**
	 * Constructor for creating an empty Molecule record. 
	 */
	public AbstractMolecule() {
		super();
		segmentTables = new LinkedHashMap<>();
	}
	
	/**
	 * Constructor for loading a Molecule record from a file. Typically,
	 * used when streaming records into memory when loading a {@link AbstractMoleculeArchive}
	 * or when a record is retrieved from the virtual store. 
	 * 
	 * @param jParser A JsonParser at the start of the molecule record json
	 * for loading the molecule record from a file.
	 */
	public AbstractMolecule(JsonParser jParser) throws IOException {
		super();
		segmentTables = new LinkedHashMap<>();
		fromJSON(jParser);
	}
	
	/**
	 * Constructor for creating an empty Molecule record with the
	 * specified UID. 
	 * 
	 * @param UID The unique identifier for this Molecule record.
	 */
	public AbstractMolecule(String UID) {
		super(UID);
		segmentTables = new LinkedHashMap<>();
	}

	/**
	 * Constructor for creating a new Molecule record with the
	 * specified UID and the {@link MarsResultsTable} given
	 * as the DataTable. 
	 * 
	 * @param UID The unique identifier for this Molecule record.
	 * @param datatable The {@link MarsResultsTable} to use for 
	 * initialization.
	 */
	public AbstractMolecule(String UID, MarsResultsTable dataTable) {
		super(UID, dataTable);
		segmentTables = new LinkedHashMap<>();
	}
	
	/**
	 * Write the molecule record to JSON. Uses the provided
	 * JsonGenerator created elsewhere to stream the molecule 
	 * record to file.
	 * 
	 * @param jGenerator A JsonGenerator for stream the molecule
	 * record to a file.
	 * 
	 * @throws IOException if there is a problem writing to the file.
	 */
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
		if (dataTable.getColumnCount() > 0) {
			jGenerator.writeFieldName("DataTable");
			dataTable.toJSON(jGenerator);
		}
		
		//Write out segment tables generated from KCP as object that have two fields that store the x column and y column names used during KCP
		if (segmentTables.size() > 0) {
			jGenerator.writeArrayFieldStart("SegmentTables");
			for (ArrayList<String> tableColumnNames :segmentTables.keySet()) {
				if (segmentTables.get(tableColumnNames).size() > 0) {
					jGenerator.writeStartObject();
					
					jGenerator.writeStringField("xColumnName", tableColumnNames.get(0));
					jGenerator.writeStringField("yColumnName", tableColumnNames.get(1));
					
					jGenerator.writeFieldName("Table");
					segmentTables.get(tableColumnNames).toJSON(jGenerator);
					
					jGenerator.writeEndObject();
				}
			}
			jGenerator.writeEndArray();
		}
		jGenerator.writeEndObject();
	}
	
	/**
	 * Read a molecule record from JSON. Load a molecule record
	 * from a file using the JsonParser stream provided.
	 * 
	 * @param jParser A JsonParser for loading the molecule
	 * record from a file.
	 * 
     * @throws IOException if there is a problem reading from the file.
	 */
	public void fromJSON(JsonParser jParser) throws IOException {
		//We assume a molecule object and just been detected and now we want to parse all the values into this molecule entry.
		JsonToken nextToken = JsonToken.NOT_AVAILABLE;
		while (nextToken != JsonToken.END_OBJECT) {
			nextToken = jParser.nextToken(); 
			if (nextToken == null) {
				System.out.println("JsonParser encountered an incomplete molecule record.");
				this.addNote("JsonParser encountered a problem. This record is incomplete.");
				break;
			}
			
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
			    dataTable.fromJSON(jParser);
			    continue;
		    }
		    
		    if("SegmentTables".equals(fieldname)) {
		    	jParser.nextToken();
		    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
			    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
			    		String xColumnName = "";
			    		String yColumnName = "";
			    	
			    		//Needed for backwards compatibility when reverse order was used...
					    if ("xColumnName".equals(jParser.getCurrentName())) {
					    	jParser.nextToken();
					    	xColumnName = jParser.getText();
					    	
					    	//Then move past the field and next name
						    jParser.nextToken();
						    jParser.nextToken();
					    	yColumnName = jParser.getText();
					    } else if ("yColumnName".equals(jParser.getCurrentName())) {
					    	jParser.nextToken();
					    	yColumnName = jParser.getText();
					    	
					    	//Then move past the field and next name
						    jParser.nextToken();
						    jParser.nextToken();
					    	xColumnName = jParser.getText();
					    }
					    
					    ArrayList<String> tableColumnNames = new ArrayList<String>();

				    	tableColumnNames.add(xColumnName);
				    	tableColumnNames.add(yColumnName);
				    	
				    	MarsResultsTable segmenttable = new MarsResultsTable(yColumnName + " vs " + xColumnName);
				    	
				    	//Move past Table
				    	jParser.nextToken();
				    	
				    	segmenttable.fromJSON(jParser);
				    	
				    	segmentTables.put(tableColumnNames, segmenttable);
			    	}
		    	}
		    	continue;
		    }
		    
		    //SHOULD BE UNREACHABLE
		    //This is only reached if there is an unexpected field added to the json record
		    //In that case we simply pass through it
		    //This ensures if extra fields are added in the future
		    //old versions will be able to open the new files
		    //However, the missing fields will not be saved properly
		    //In the case of a virtual archive new fields will be systematically removed as records are opened and saved...
		    if (jParser.getCurrentToken() == JsonToken.START_OBJECT) {
		    	System.out.println("unknown object encountered in molecule record ... skipping");
		    	MarsUtil.passThroughUnknownObjects(jParser);
		    }
		}
	}
	
	/**
	 * Generate a JSON String representation of the molecule record.
	 * 
	 * @return Return a JSON string representation of the molecule.
	 */
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
	
	/**
	 * Set the UID of the {@link SDMMImageMetadata} record associated with
	 * this molecule. The {@link SDMMImageMetadata} contains information about
	 * the data collection (Timing of frames, colors, collection date, etc...)
	 * 
	 * @param imageMetaDataUID The new ImageMetaData UID to set.
	 */
	public void setImageMetaDataUID(String imageMetaDataUID) {
		this.imageMetaDataUID = imageMetaDataUID;
	}
	
	/**
	 * Get the UID of the {@link SDMMImageMetadata} record associated with
	 * this molecule. The {@link SDMMImageMetadata} contains information about
	 * the data collection (Timing of frames, colors, collection date, etc...)
	 * 
	 * @return Return a JSON string representation of the molecule.
	 */
	public String getImageMetaDataUID() {
		return imageMetaDataUID;
	}
		
	/**
	 * Add or update a Segments table ({@link MarsResultsTable}) generated 
	 * using the yColumnName and xColumnName. The {@link KCPCommand} performs
	 * kinetic change point analysis generating segments to fit regions
	 * of a trace. The information about these segments is added using
	 * this method.
	 * 
	 * @param xColumnName The name of the column used for x for KCP analysis.
	 * @param yColumnName The name of the column used for y for KCP analysis.
	 * @param segs The {@link MarsResultsTable} to add that contains the 
	 * segments.
	 */
	public void putSegmentsTable(String xColumnName, String yColumnName, MarsResultsTable segs) {
		ArrayList<String> tableColumnNames = new ArrayList<String>();
		tableColumnNames.add(xColumnName);
		tableColumnNames.add(yColumnName);
		
		//Let's also make sure the MARSResultsTable contains
		//the x and y column names...
		//Should always be set but just in case....
		//segs.setXYColumnNames(xColumnName, yColumnName);
		segmentTables.put(tableColumnNames, segs);
	}
	
	/**
	 * Retrieve a Segments table ({@link MarsResultsTable}) generated 
	 * using xColumnName and yColumnName.
	 * 
	 * @param xColumnName The name of the x column used for analysis.
	 * @param yColumnName The name of the y column used for analysis.
	 * @return The MARSResultsTable generated using the columns specified.
	 */	
	public MarsResultsTable getSegmentsTable(String xColumnName, String yColumnName) {
		ArrayList<String> tableColumnNames = new ArrayList<String>();
		tableColumnNames.add(xColumnName);
		tableColumnNames.add(yColumnName);
		return segmentTables.get(tableColumnNames);
	}
	
	/**
	 * Check if record has a Segments table ({@link MarsResultsTable}) generated 
	 * using xColumnName and yColumnName.
	 * 
	 * @param xColumnName The name of the x column used for analysis.
	 * @param yColumnName The name of the y column used for analysis.
	 * @return Boolean whether the segment table exists.
	 */	
	public boolean hasSegmentsTable(String xColumnName, String yColumnName) {
		ArrayList<String> tableColumnNames = new ArrayList<String>();
		tableColumnNames.add(xColumnName);
		tableColumnNames.add(yColumnName);
		return segmentTables.containsKey(tableColumnNames);
	}
	
	/**
	 * Retrieve a Segments table ({@link MarsResultsTable}) generated 
	 * using yColumnName and xColumnName provided in index positions 0
	 * and 1 of an ArrayList, respectively.
	 * 
	 * @param tableColumnNames The xColumnName and yColumnName used when
	 * generating the table, provided in index positions 0 and 1 of an 
	 * ArrayList, respectively.
	 * @return The MARSResultsTable generated using the columns specified.
	 */	
	public MarsResultsTable getSegmentsTable(ArrayList<String> tableColumnNames) {
		return segmentTables.get(tableColumnNames);
	}
	
	/**
	 * Remove the Segments table ({@link MarsResultsTable}) generated 
	 * using yColumnName and xColumnName.
	 * 
	 * @param xColumnName The name of the x column used for analysis.
	 * @param yColumnName The name of the y column used for analysis.
	 */
	public void removeSegmentsTable(String xColumnName, String yColumnName) {
		ArrayList<String> tableColumnNames = new ArrayList<String>();
		tableColumnNames.add(xColumnName);
		tableColumnNames.add(yColumnName);
		segmentTables.remove(tableColumnNames);
	}
	
	/**
	 * Retrieve a Segments table ({@link MarsResultsTable}) generated 
	 * using yColumnName and xColumnName.
	 * 
	 * @return The Set of ArrayLists holding the x and y column names at
	 * index positions 0 and 1, respectively.
	 */
	public Set<ArrayList<String>> getSegmentTableNames() {
		return segmentTables.keySet();
	}
}
