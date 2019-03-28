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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import org.decimal4j.util.DoubleRounder;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.ui.DialogPrompt.MessageType;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import ij.ImagePlus;
import io.scif.Format;
import io.scif.FormatException;
import io.scif.Metadata;
import net.imagej.Dataset;
import org.scijava.table.DoubleColumn;
import org.scijava.table.GenericColumn;

import de.mpg.biochem.mars.ImageProcessing.MoleculeIntegrator;
import de.mpg.biochem.mars.ImageProcessing.PeakTracker;
import de.mpg.biochem.mars.table.*;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MARSMath;
import io.scif.services.FormatService;

/**
 * MARSImageMetaData records act as the storage location for all information
 * about specific data collections, including imaging settings, frame timing..
 * 
 * @author Karl Duderstadt
 */
public class MARSImageMetaData {
	//Unique ID used for universal identification and indexing.
	private String UID;
	
	//Reference to MoleculeArchive containing the MARSImageMetaData record
	private	MoleculeArchive parent;
	
	//Any comments specific to the data collection
	//a possible issue, the condition collected etc..
	private String log, Notes;
	
	private String Microscope;
	
	//Directory where the images are stored..
	private String SourceDirectory;
	
	//Date and time when the data was collected...
	private String CollectionDate;
	
	//tags added for filtering...
	private LinkedHashSet<String> Tags;
		
	//Hashmap that maps string parameters to doubles
	private LinkedHashMap<String, Double> Parameters;
	
	//Table that maps slices to times
	private MARSResultsTable DataTable;
    
    //Used for making JsonParser instances...
    //We make it static because we just need to it make parsers so we don't need multiple copies..
    private static JsonFactory jfactory = new JsonFactory();
    
    public MARSImageMetaData(String UID) {
    	initializeVariables();
    	this.UID = UID;

		//Create the table and add a slice column
		DataTable = new MARSResultsTable("MARSImageMetaData - " + UID);
		DoubleColumn sliceCol = new DoubleColumn("slice");
		sliceCol.add((double)1);
		DataTable.add(sliceCol);
    }
    
    public MARSImageMetaData(String UID, MARSResultsTable DataTable) {
    	initializeVariables();
    	this.UID = UID;		
		this.DataTable = DataTable;
    }
	
	public MARSImageMetaData(ImagePlus img, String Microscope, String imageFormat, ConcurrentMap<Integer, String> headerLabels) {
		initializeVariables();
		this.Microscope = Microscope;
		
		if (img.getOriginalFileInfo() != null) {
			this.SourceDirectory = img.getOriginalFileInfo().directory;
		}
		
		if (imageFormat.equals("NorPix")) {
			UID = generateUID(headerLabels);
			buildMetaDataNorPix(headerLabels);
		} else if (imageFormat.equals("MicroManager")) {
			UID = generateMMUID(headerLabels);
			buildMetaDataMicroManager(headerLabels);
			
			//Let's get custom Aquisition settings from the metadata.txt file
			getAquisitionProperties();
		} else {
			//For GenericData we just generate a random UID but truncate to 11 characters to match
			//output of FNV1a algorithm...
			UID = MARSMath.getUUID58().substring(0, 10);
			buildMetaDataGeneric(img);
		}
	}
	
	public MARSImageMetaData(JsonParser jParser) throws IOException {
		initializeVariables();
		fromJSON(jParser);
	}
	
	private void initializeVariables() {
		Parameters = new LinkedHashMap<>();
		Tags = new LinkedHashSet<String>();
		
    	this.Microscope = "unknown";
    	this.SourceDirectory = "unknown";
		log = "";
	}
	
	//Generate a unique ID using a hash of all headerlabel information...
	private String generateUID(ConcurrentMap<Integer, String> headerLabels) {
		String allLabels = "";
		for (int i=1;i<=headerLabels.size();i++)
			allLabels += headerLabels.get(i);
		
		return MARSMath.getFNV1aBase58(allLabels);
	}
	
	//the one way Hashfunction actually takes a while to run so we need to keep the String size low
	//Therefore, we randomly take some different unique properties here to make the UID...
	private String generateMMUID(ConcurrentMap<Integer, String> headerLabels) {
		String allLabels = "";
		for (int i=1;i<=headerLabels.size();i++) {
			HashMap<String, String> params = getMetaDataParamMap(headerLabels.get(i));
			allLabels += headerLabels.get(i).substring(0, headerLabels.get(i).indexOf("{"));
			if (params.containsKey("Time"))
				allLabels += params.get("Time");
			if (params.containsKey("ElapsedTime-ms"))
				allLabels += params.get("ElapsedTime-ms");
			if (params.containsKey("DigitalIO-State"))
				allLabels += params.get("DigitalIO-State");
			if (params.containsKey("XPositionUm"))
				allLabels += params.get("XPositionUm");
			if (params.containsKey("YPositionUm"))
				allLabels += params.get("YPositionUm");
			if (params.containsKey("ZPositionUm"))
				allLabels += params.get("ZPositionUm");
			if (params.containsKey("MCL TIRF-Lock-Y"))
				allLabels += params.get("MCL TIRF-Lock-Y");
			if (params.containsKey("MCL TIRF-Lock-X"))
				allLabels += params.get("MCL TIRF-Lock-X");
		}
		
		return MARSMath.getFNV1aBase58(allLabels);
	}
	
	private void buildMetaDataGeneric(ImagePlus img) {
        DoubleColumn sliceCol = new DoubleColumn("slice");
		int slices = img.getImageStackSize();
		for (int i=1; i<=slices ; i++) {
			sliceCol.add((double)i);
		}
		
		//Create the table and add all the columns...
		DataTable = new MARSResultsTable("MARSImageMetaData - " + UID);
		DataTable.add(sliceCol);
	}
	
	private void buildMetaDataNorPix(ConcurrentMap<Integer, String> headerLabels) {        
        //Now we will build the DataTable using the headerLabel
        DoubleColumn sliceCol = new DoubleColumn("slice");
        DoubleColumn timeCol = new DoubleColumn("Time (s)");
        GenericColumn labelCol = new GenericColumn("Label (Raw)");
        
		try {
			//Set Global Collection Date for the dataset
			CollectionDate = getNorPixDate(headerLabels.get(1).substring(10));
			
			//Extract the exact time of collection of all frames..
			long t0 = getNorPixMillisecondTime(headerLabels.get(1).substring(10));
			for (int i=1;i<=headerLabels.size();i++) {
				labelCol.add(headerLabels.get(i));
				sliceCol.add((double)i);
				timeCol.add((double)(getNorPixMillisecondTime(headerLabels.get(i).substring(10)) - t0)/1000);
			}
		} catch (ParseException e) {
			//moleculeArchiveService.getLogService().error("There seems to be a problem with the Image header Labels. Are you sure they are the correction Norpix format?");
			//e.printStackTrace();
		}
		
		//Create the table and add all the columns...
		DataTable = new MARSResultsTable("MARSImageMetaData - " + UID);
		DataTable.add(sliceCol);
		DataTable.add(timeCol);
		DataTable.add(labelCol);
	}
	
	private HashMap<String, String> getMetaDataParamMap(String headerLabel) {
		HashMap<String, String> parameters = new HashMap<String, String>();
		
        //Let's get the lasers and filters...
		try {
			JsonParser jParser = jfactory.createParser(headerLabel.substring(headerLabel.indexOf("{")));
			
			//Just to skip to the first field
			jParser.nextToken();
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String fieldname = jParser.getCurrentName();
				
				//Summary is an object, so that will break the loop
				//Therefore, we need to skip over it...
				if ("Summary".equals(fieldname)) {
					while (jParser.nextToken() != JsonToken.END_OBJECT) {
						// We just want to skip over the summary
					}
					//once we have skipped over we just want to continue
					continue;
				}
				
				jParser.nextToken();

				parameters.put(fieldname, jParser.getValueAsString());
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return parameters;
	}
	
	private void getAquisitionProperties() {
		if (SourceDirectory.equals("unknown"))
			return;
			
		File metadatafile = new File(SourceDirectory + "/metadata.txt");
		if (metadatafile.exists()) {
			LinkedHashMap<String, String> properties = new LinkedHashMap<>();
			
			FileInputStream inputStream;
			try {
				inputStream = new FileInputStream(metadatafile);
				JsonFactory jsonF = new JsonFactory();

			    JsonParser jParser = jsonF.createParser(inputStream);
			    
			    jParser.nextToken();

			    while (jParser.nextToken() != JsonToken.END_OBJECT) {
					String fieldname = jParser.getCurrentName();
					
					if ("Summary".equals(fieldname)) {
						jParser.nextToken();
						while (jParser.nextToken() != JsonToken.END_OBJECT) {
							String subfieldname = jParser.getCurrentName();
							jParser.nextToken();
							String propValue;
							
							if (jParser.getCurrentToken() == JsonToken.START_ARRAY) {
								propValue = "[";
								while (jParser.nextToken() != JsonToken.END_ARRAY)
									propValue +=  jParser.getValueAsString() + ", ";
								if (propValue.length() > 1)
									propValue = propValue.substring(0, propValue.length() - 2);
								propValue += "]";
							} else {
								propValue = jParser.getValueAsString();
							}

							properties.put(subfieldname, propValue);
						}
						break;
					}
				}
			    
			    LogBuilder builder = new LogBuilder();
			    builder.clearParameterList();
				
				String log = builder.buildTitleBlock("Aquisition Summary");
				for (String key: properties.keySet())
					builder.addParameter(key, properties.get(key));
				
				log += builder.buildParameterList();
				log += builder.endBlock(true);
				
				log += "\n";
				
				addLogMessage(log);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private void buildMetaDataMicroManager(ConcurrentMap<Integer, String> headerLabels) {       
		HashMap<Integer, HashMap<String, String>> propertiesStack = new HashMap<>();
		try {
			JsonParser jParser;
			
			//Now we loop through all frame headerLabels and build the table
			for (int slice=1;slice<=headerLabels.size();slice++) {
				HashMap<String, String> properties = new HashMap<>();
				
				jParser = jfactory.createParser(headerLabels.get(slice).substring(headerLabels.get(slice).indexOf("{")));
				
				//Just to skip to the first field
				jParser.nextToken();
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					String fieldname = jParser.getCurrentName();
					
					if (fieldname == null)
						continue;
					
					//At the moment, we skip all object fields
					//We can add above if statements for specific
					//known fields we want to parse...
					if (jParser.nextToken() == JsonToken.START_OBJECT) {
				    	passThroughUnknownObjects(jParser);
				    	continue;
				    }
					
					properties.put(fieldname, jParser.getValueAsString());
				}
				propertiesStack.put(slice, properties);
			}
			
			//Let's generate the Time column using the micromanager ElapsedTime-ms
			//For Dobby and the Andor camera this is alwasy in the frame hearders
			//but it might be the case that for Winky this is not in the header
			//or not an output...
			
			//Get t0 in seconds...
			double t0 = Double.valueOf(propertiesStack.get(1).get("ElapsedTime-ms"))/1000;
			
			DataTable = new MARSResultsTable("MARSImageMetaData - " + UID);
			DataTable.add(new DoubleColumn("slice"));
			DataTable.add(new DoubleColumn("Time (s)"));
			
			int row = 0;
			for(int slice=1;slice<=headerLabels.size();slice++) {
				DataTable.appendRow();
				
				DataTable.setValue("slice", row, slice);
				if (propertiesStack.get(slice).containsKey("ElapsedTime-ms")) {
					//Get tn in seconds...
					double tn = Double.valueOf(propertiesStack.get(slice).get("ElapsedTime-ms"))/1000;
					DataTable.setValue("Time (s)", row, DoubleRounder.round(tn - t0, 3));
				} else {
					DataTable.setValue("Time (s)", row, Double.NaN);
				}
				
				HashMap<String, String> properties = propertiesStack.get(slice);
				for (String field: properties.keySet()) {
					boolean ExcludedColumn = false;
		    		
		    		for (int i=0;i<column_exclude_list.length;i++) {
		    			if (field.equals(column_exclude_list[i]))
		    				ExcludedColumn = true;
		    		}
		    		if (!ExcludedColumn) {
						if (!DataTable.hasColumn(field)) {
							GenericColumn newCol = new GenericColumn(field);
							for (int i=0;i<slice;i++)
								newCol.add("");
							DataTable.add(newCol);
						}
						
						DataTable.setValue(field, row, properties.get(field));
		    		}
 				}
				row++;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (DataTable.get("Time") != null)
			CollectionDate = (String)DataTable.get("Time").get(0);
		
	}
	
	//jackson custom JSON serialization 
	public void toJSON(JsonGenerator jGenerator) throws IOException {
		jGenerator.writeStartObject();
		
		//write out UID
		jGenerator.writeStringField("UID", UID);
		
		if(Microscope != null)
			jGenerator.writeStringField("Microscope", Microscope);
		
		if (SourceDirectory != null)
			jGenerator.writeStringField("SourceDirectory", SourceDirectory);
		
		if (CollectionDate != null)
			jGenerator.writeStringField("CollectionDate", CollectionDate);
		
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
		
		if (Notes != null)
			jGenerator.writeStringField("Notes", Notes);
		
		if (!log.equals(""))
			jGenerator.writeStringField("Log", log);
 		
		//Write out raw data table if there are columns
		if (DataTable.size() > 0) {
			jGenerator.writeFieldName("DataTable");
			DataTable.toJSON(jGenerator);
		}
		jGenerator.writeEndObject();
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
		    
		    if("Microscope".equals(fieldname)) {
		    	jParser.nextToken();
		    	Microscope = jParser.getText();
		    	continue;
		    }
		    
		    if ("SourceDirectory".equals(fieldname)) {
		    	jParser.nextToken();
		    	SourceDirectory = jParser.getText();
		    	continue;
		    }
		    
		    if ("CollectionDate".equals(fieldname)) {
		    	jParser.nextToken();
		    	CollectionDate = jParser.getText();
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
		    
		    if("Notes".equals(fieldname)) {
		    	jParser.nextToken();
		    	Notes = jParser.getText();
		    	continue;
		    }
		    
		    if("Log".equals(fieldname)) {
		    	jParser.nextToken();
		    	log = jParser.getText();
		    	continue;
		    }
		    
		    if("DataTable".equals(fieldname)) {		    	
		    	DataTable = new MARSResultsTable("MARSImageMetaData - " + UID);
		    	DataTable.fromJSON(jParser);
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
		    	System.out.println("unknown object encountered in MARSImageMetaData record ... skipping");
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
	
	//Utility method
	//Returns the time when the frame was collected in milliseconds since 1970
	//Makes sure to properly round microsecond information.
	private long getNorPixMillisecondTime(String strTime) throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd HHmmssSSS");
		//For the moment we throw-out the microsecond information.
		//String microSecs = strTime.substring(strTime.length() - 3, strTime.length());
		Date convertedDate = formatter.parse(strTime.substring(0, strTime.length() - 4));
		return convertedDate.getTime();// + Double.parseDouble(microSecs)/1000;
	}
	
	//Utility method
	//Returns the Date as a string
	private String getNorPixDate(String strTime) throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd HHmmssSSS");
		Date convertedDate = formatter.parse(strTime.substring(0, strTime.length() - 4));
		return convertedDate.toString();
	}
	
	//Getters and Setters
	public void setNotes(String Notes) {
		this.Notes = Notes;
	}
	
	public void setMicroscopeName(String Microscope) {
		this.Microscope = Microscope;
	}
	
	public MARSResultsTable getDataTable() {
		return DataTable;
	}
	
	public String getUID() {
		return UID;
	}
	
	public String getMicroscopeName() {
		return Microscope;
	}
	
	public void setCollectionDate(String str) {
		CollectionDate = str;
	}
	
	public String getCollectionDate() {
		return CollectionDate;
	}
	
	public String getSourceDirectory() {
		return SourceDirectory;
	}
	
	public void addTag(String tag) {
		Tags.add(tag);
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
	
	public String getNotes() {
		return Notes;
	}
	
	public void addLogMessage(String str) {
		log += str + "\n";
	}
	
	public String getLog() {
		return log;
	}
	
	public void setParent(MoleculeArchive archive) {
		parent = archive;
	}
	
	//DataTable column exclusion list
	private String[] column_exclude_list = new String[]{
			"MCL NanoDrive XY Stage-Name", 
			 "Core-Initialize",
			 "Andor-FanMode",
			 "Andor-SpuriousNoiseFilterDescription",
			 "Filter wheel 1-Label",
			 "Optospin Controller-Positions",
			 "Andor-BaselineClamp",
			 "Intensity637-TriggerInputLine",
			 "Intensity561-Block voltage",
			 "MCL NanoDrive XY Stage-Lower x limit",
			 "Andor-CountConvert",
			 "Filter wheel 2-Label",
			 "Intensity637-SupportsTriggering",
			 "Andor-SpuriousNoiseFilterThreshold",
			 "MCL NanoDrive Z Stage-Serial Number",
			 "Andor-Region of Interest",
			 "MCL NanoDrive XY Stage-Description",
			 "Andor-Advanced | Snap Image Timing Mode",
			 "Andor-CoolerMode",
			 "Intensity561-MaxVolts",
			 "Andor-Shutter (Internal)",
			 "Intensity488-Sequenceable",
			 "MCL NanoDrive XY Stage-Settling Time X axis (ms)",
			 "Filter wheel 1-HubID",
			 "Andor-Pre-Amp-Gain",
			 "Filter wheel 2-Wheel Mode",
			 "Andor-EMSwitch",
			 "Andor-Shutter Closing Time",
			 "Intensity637-Demo",
			 "Andor-1. Camera Information : | Type | Model | Serial No. |",
			 "Intensity561-SupportsTriggering",
			 "Intensity561-Sequenceable",
			 "Intensity488-IOChannel",
			 "Intensity561-Demo",
			 "MCL NanoDrive Z Stage-Description",
			 "DigitalIO-IOChannel",
			 "Intensity637-MinVolts",
			 "Filter wheel 2-HubID",
			 "Filter wheel 1-Mode",
			 "Andor-KeepCleanTime",
			 "Andor-ReadMode",
			 "TimeFirst",
			 "Andor-Camera",
			 "Intensity532-Description",
			 "Core-Camera",
			 "Core-SLM",
			 "Filter wheel 2- Spins",
			 "Filter wheel 1- Position Locked",
			 "Andor-CountConvertWavelength",
			 "Intensity488-TriggerSequenceLength",
			 "Filter wheel 1- Spins",
			 "DigitalIO-TriggerSequenceLength",
			 "Core-ChannelGroup",
			 "DigitalIO-Sequenceable",
			 "DigitalIO-TriggerInputLine",
			 "DigitalIO-ClosedPosition",
			 "Intensity637-Sequenceable",
			 "PixelSizeUm",
			 "MCL NanoDrive Z Stage-Calibration",
			 "Intensity637-TriggerSequenceLength",
			 "Andor-Shutter Opening Time",
			 "Intensity561-MinVolts",
			 "Intensity488-MaxVolts",
			 "Intensity637-Name",
			 "MCL NanoDrive XY Stage-Upper y limit",
			 "MCL NanoDrive XY Stage-Set origin here",
			 "Filter wheel 1-Wheel Mode",
			 "Andor-FrameTransfer Help",
			 "Intensity488-Name",
			 "Optospin Controller-serial number",
			 "MCL TIRF-Lock-TransposeMirrorX",
			 "Andor-SpuriousNoiseFilter",
			 "MCL TIRF-Lock-TransposeMirrorY",
			 "Andor-OptAcquireMode",
			 "Intensity488-MinVolts",
			 "Filter wheel 2-Description",
			 "Andor-Description",
			 "DigitalIO-OutputChannel",
			 "MCL TIRF-Lock-Name",
			 "MCL TIRF-Lock-Serial number",
			 "Andor-Advanced | Snap Image Additional Delay (ms)",
			 "Intensity532-Block voltage",
			 "Intensity488-SupportsTriggering",
			 "Intensity532-MaxVolts",
			 "Intensity488-TriggerInputLine",
			 "Intensity637-Description",
			 "Andor-TransposeCorrection",
			 "MCL TIRF-Lock-Description",
			 "MCL NanoDrive XY Stage-Settling Time Y axis (ms)",
			 "Intensity532-MinVolts",
			 "MCL NanoDrive Z Stage-Set origin here",
			 "DigitalIO-SupportsTriggering",
			 "MCL NanoDrive XY Stage-Handle",
			 "Intensity637-Block voltage",
			 "MCL NanoDrive Z Stage-Upper Limit",
			 "MCL NanoDrive XY Stage-TransposeMirrorY",
			 "Filter wheel 2-Mode",
			 "MCL NanoDrive XY Stage-TransposeMirrorX",
			 "Intensity488-Block voltage",
			 "Filter wheel 2- Position Locked",
			 "Core-Galvo",
			 "MCL NanoDrive XY Stage-Serial number",
			 "CameraChannelIndex",
			 "Intensity488-Description",
			 "Core-Focus",
			 "MCL NanoDrive XY Stage-Upper x limit",
			 "Intensity532-IOChannel",
			 "Optospin Controller-Filter Wheel Spin",
			 "Core-ImageProcessor",
			 "Intensity532-Demo",
			 "Andor-Output_Amplifier",
			 "Intensity561-IOChannel",
			 "Intensity532-Name",
			 "MCL NanoDrive Z Stage-Lower Limit",
			 "MCL NanoDrive Z Stage-Settling time Z axis (ms)",
			 "Core-AutoShutter",
			 "Core-XYStage",
			 "Intensity637-MaxVolts",
			 "DigitalIO-Description",
			 "Filter wheel 2-Name",
			 "Andor-Isolated Crop Mode",
			 "Core-AutoFocus",
			 "Intensity561-Name",
			 "Intensity532-Sequenceable",
			 "BitDepth",
			 "Slice",
			 "Intensity561-TriggerSequenceLength",
			 "MCL NanoDrive Z Stage-Device Handle",
			 "DigitalIO-Name",
			 "Intensity488-Demo",
			 "Core-Shutter",
			 "Intensity561-TriggerInputLine",
			 "Intensity561-Description",
			 "MCL NanoDrive Z Stage-Axis being used as Z axis",
			 "Core-TimeoutMs",
			 "MCL NanoDrive Z Stage-Name",
			 "Optospin Controller-Optospin serial number",
			 "Filter wheel 1-Name",
			 "Intensity532-TriggerSequenceLength",
			 "MCL NanoDrive XY Stage-Lower y limit",
			 "Andor-TransposeMirrorX",
			 "Andor-TransposeMirrorY",
			 "Intensity532-TriggerInputLine",
			 "Optospin Controller-Name",
			 "Andor-TransposeXY",
			 "Intensity532-SupportsTriggering",
			 "Andor-Shutter (External)",
			 "SlicesFirst",
			 "Optospin Controller-Description",
			 "Andor-CCDTemperature Help",
			 "Intensity637-IOChannel",
			 "MCL NanoDrive XY Stage-Set position Y (um)",
			 "Filter wheel 1-Description",
			 "Andor-TimeOut",
			 "Andor-Trigger",
			 "MCL NanoDrive XY Stage-Set position X (um)",
			 "MCL NanoDrive Z Stage-Set position Z (um)",
			 "Andor-OptAcquireMode Description",
			 "SliceIndex"
	};
}
