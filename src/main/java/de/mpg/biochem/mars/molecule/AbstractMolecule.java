package de.mpg.biochem.mars.molecule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
	
	@Override
	protected void createIOMaps() {
		super.createIOMaps();
		
		//Add to output map
		outputMap.put("metaUID", MarsUtil.catchConsumerException(jGenerator -> {
			if (imageMetaDataUID != null)
				jGenerator.writeStringField("ImageMetaDataUID", imageMetaDataUID);
	 	}, IOException.class));
		outputMap.put("SegmentTables", MarsUtil.catchConsumerException(jGenerator -> {
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
	 	}, IOException.class));
		
		//Add to input map
		inputMap.put("ImageMetaDataUID", MarsUtil.catchConsumerException(jParser -> {
			jParser.nextToken();
	    	imageMetaDataUID = jParser.getText();
		}, IOException.class));
		inputMap.put("SegmentTables", MarsUtil.catchConsumerException(jParser -> {
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
		}, IOException.class));
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
