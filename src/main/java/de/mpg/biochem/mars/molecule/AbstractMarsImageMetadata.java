package de.mpg.biochem.mars.molecule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.table.MarsResultsTable;
import de.mpg.biochem.mars.util.MarsUtil;

public class AbstractMarsImageMetadata extends AbstractMarsRecord implements MarsImageMetadata {
	//Processing log for the record
	protected String log;
	
	protected String Microscope;
	
	//Directory where the images are stored..
	protected String SourceDirectory;
	
	//Date and time when the data was collected...
	protected String CollectionDate;
    
    //Used for making JsonParser instances...
    //We make it static because we just need to it make parsers so we don't need multiple copies..
	//Here json is always UTF encoded
    protected static JsonFactory jfactory = new JsonFactory();
    
    public AbstractMarsImageMetadata() {
    	super();
    }
    
    public AbstractMarsImageMetadata(String UID) {
    	super(UID);

    	this.Microscope = "unknown";
    	this.SourceDirectory = "unknown";
		this.log = "";
    }
    
    public AbstractMarsImageMetadata(String UID, MarsResultsTable dataTable) {
    	super(UID, dataTable);
    }
	
	public AbstractMarsImageMetadata(JsonParser jParser) throws IOException {
		super(jParser);
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
  		if (dataTable.size() > 0) {
  			jGenerator.writeFieldName("DataTable");
  			dataTable.toJSON(jGenerator);
  		}
  		jGenerator.writeEndObject();
  	}
  	
  	public String toJSONString() {
  		ByteArrayOutputStream stream = new ByteArrayOutputStream();

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
  		    	dataTable = new MarsResultsTable("MarsImageMetaData - " + UID);
  		    	dataTable.fromJSON(jParser);
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
  		    	MarsUtil.passThroughUnknownObjects(jParser);
  		    }
  		}
  	}
    
	//Getters and Setters
	public void setMicroscopeName(String Microscope) {
		this.Microscope = Microscope;
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
	
	public void addLogMessage(String str) {
		log += str + "\n";
	}
	
	public String getLog() {
		return log;
	}
	
	public void setParent(MoleculeArchive archive) {
		parent = archive;
	}
}
