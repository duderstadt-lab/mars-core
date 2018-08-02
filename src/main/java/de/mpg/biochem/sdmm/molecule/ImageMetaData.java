package de.mpg.biochem.sdmm.molecule;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentMap;

import org.scijava.plugin.Parameter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.sdmm.table.SDMMResultsTable;
import ij.ImagePlus;
import io.scif.Format;
import io.scif.FormatException;
import io.scif.Metadata;
import net.imagej.Dataset;
import net.imagej.table.DefaultGenericTable;
import net.imagej.table.DoubleColumn;
import net.imagej.table.GenericColumn;
import net.imagej.table.GenericTable;
import net.imagej.table.IntColumn;
import io.scif.services.FormatService;

public class ImageMetaData {
	
	//Unique ID used for universal identification and indexing.
	String UID;
	
	//Any comments specific to the data collection
	//a possible issue, the condition collected etc..
	String Comments;
	
	String Microscope;
	
	//Directory where the images are stored..
	String SourceDirectory;
	
	//Date and time when the data was collected...
	String CollectionDate;
	
	//Table that maps slices to times
	GenericTable DataTable;
	
	// Required Services
    private MoleculeArchiveService moleculeArchiveService;
    
    
    //Used for making JsonParser isntances..
    //We make a static becasue we just need to it make parsers so we don't multiple copies..
    private static JsonFactory jfactory = new JsonFactory();
    
    //Fall back if no frameRate is given...
    //double timeBetweenFrames = 1;
	
	public ImageMetaData(ImagePlus img, MoleculeArchiveService moleculeArchiveService, String Microscope, String imageFormat, ConcurrentMap<Integer, String> headerLabels) {
		this.moleculeArchiveService = moleculeArchiveService;
		this.Microscope = Microscope;
		this.SourceDirectory = img.getOriginalFileInfo().directory;
		
		if (imageFormat.equals("NorPix")) {
			UID = generateUID(headerLabels);
			buildMetaDataNorPix(headerLabels);
		} else if (imageFormat.equals("MicroManager")) {
			UID = generateUID(headerLabels);
			buildMetaDataMicroManager(headerLabels);
		} else {
			//For GenericData we just generate a random UID but truncate to 11 characters to match
			//output of FNV1a algorithm...
			UID = moleculeArchiveService.getUUID58().substring(0, 10);
			buildMetaDataGeneric(img);
		}
	}
	
	//Generate a unique ID using a hash of all headerlabel information...
	private String generateUID(ConcurrentMap<Integer, String> headerLabels) {
		String allLabels = "";
		for (int i=1;i<=headerLabels.size();i++)
			allLabels += headerLabels.get(i);
		
		return moleculeArchiveService.getFNV1aBase58(allLabels);
	}
	
	public ImageMetaData(JsonParser jParser) throws IOException {
		fromJSON(jParser);
	}
	
	private void buildMetaDataGeneric(ImagePlus img) {
        DoubleColumn sliceCol = new DoubleColumn("slice");
		int slices = img.getImageStackSize();
		for (int i=1; i<=slices ; i++) {
			sliceCol.add((double)i);
		}
		
		//Create the table and add all the columns...
		DataTable = new DefaultGenericTable();
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
			e.printStackTrace();
		}
		
		//Create the table and add all the columns...
		DataTable = new DefaultGenericTable();
		DataTable.add(sliceCol);
		DataTable.add(timeCol);
		DataTable.add(labelCol);
	}
	
	private void buildMetaDataMicroManager(ConcurrentMap<Integer, String> headerLabels) {        
        //Now we will build the DataTable using the headerLabel
		try {
			JsonParser jParser = jfactory.createParser(headerLabels.get(1).substring(headerLabels.get(1).indexOf("{")));
			
			//Just to skip to the first field
			jParser.nextToken();
			
			HashMap<String, GenericColumn> columns = new HashMap<>();
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String fieldname = jParser.getCurrentName();
				jParser.nextToken();
		
				GenericColumn col = new GenericColumn(fieldname);
				columns.put(fieldname, col);
			}
			
			//Now we loop through all frame headerLabels and build the table
			for (int i=1;i<=headerLabels.size();i++) {
				jParser = jfactory.createParser(headerLabels.get(i).substring(headerLabels.get(i).indexOf("{")));
				
				//Just to skip to the first field
				jParser.nextToken();
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
					String fieldname = jParser.getCurrentName();
					jParser.nextToken();

					columns.get(fieldname).add(jParser.getValueAsString());
				}
			}
			
			DataTable = new DefaultGenericTable();
			for(String str: columns.keySet()) {
				DataTable.add(columns.get(str));
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
		
		if (Comments != null)
			jGenerator.writeStringField("Comments", Comments);
 		
		//Write out raw data table if there are columns
		if (DataTable.size() > 0) {
			jGenerator.writeObjectFieldStart("DataTable");
			for (int i=0;i<DataTable.getColumnCount();i++) {
				jGenerator.writeArrayFieldStart(DataTable.getColumnHeader(i));
				for (int j=0;j<DataTable.getRowCount();j++) { 
					
					//If generic we assume it is a String column
					if (DataTable.get(i) instanceof GenericColumn)
						jGenerator.writeString((String)DataTable.get(i).get(j));
					
					if (DataTable.get(i) instanceof DoubleColumn)
						jGenerator.writeNumber((Double)DataTable.get(i).get(j));

				}
				jGenerator.writeEndArray();
			}
			jGenerator.writeEndObject();
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
		    
		    if("Microscope".equals(fieldname)) {
		    	jParser.nextToken();
		    	Microscope = jParser.getText();
		    }
		    
		    if ("SourceDirectory".equals(fieldname)) {
		    	jParser.nextToken();
		    	SourceDirectory = jParser.getText();
		    }
		    
		    if ("CollectionDate".equals(fieldname)) {
		    	jParser.nextToken();
		    	CollectionDate = jParser.getText();
		    }
		    
		    if("Comments".equals(fieldname)) {
		    	jParser.nextToken();
		    	Comments = jParser.getText();
		    }
		    
		    if("DataTable".equals(fieldname)) {
		    	//First we move past object start
		    	jParser.nextToken();
		    	
		    	DataTable = new DefaultGenericTable();
		    	
		    	//Then we move through fields
		    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
		    		String ColumnName = jParser.getCurrentName();
		    		
		    		//Have to move past array start
		    		jParser.nextToken();

		    		GenericColumn gCol = new GenericColumn(ColumnName);
		    		DoubleColumn dCol =  new DoubleColumn(ColumnName);
					
		    		while (jParser.nextToken() != JsonToken.END_ARRAY) {
		    			if (jParser.getCurrentToken().isNumeric()) {
		    				dCol.add(jParser.getValueAsDouble());
		    			} else {
		    				gCol.add(jParser.getValueAsString());
		    			}
		    		}
		    		if (dCol.size() > 1) {
		    			DataTable.add(dCol);
		    		} else {
		    			DataTable.add(gCol);
		    		}
		    	}
		    }
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
	public void setComments(String comments) {
		this.Comments = comments;
	}
	
	public void setMicroscopeName(String Microscope) {
		this.Microscope = Microscope;
	}
	
	public GenericTable getDataTable() {
		return DataTable;
	}
	
	public String getUID() {
		return UID;
	}
	
	public String getMicroscopeName() {
		return Microscope;
	}
	
	public String getCollectionDate() {
		return CollectionDate;
	}
	
	public String getSourceDirectory() {
		return SourceDirectory;
	}
	
	public String getComments() {
		return Comments;
	}
}
