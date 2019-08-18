package de.mpg.biochem.mars.molecule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.MarsUtil;

public class AbstractMarsImageMetadata extends AbstractMarsRecord implements MarsImageMetadata {
	//Processing log for the record
	protected String log;
	
	protected String Microscope;
	
	//Directory where the images are stored..
	protected String SourceDirectory;
	
	//Date and time when the data was collected...
	protected String CollectionDate;
	
	//BDV views
	protected LinkedHashMap<String, BdvSource> bdvSources;
    
    //Used for making JsonParser instances...
    //We make it static because we just need to it make parsers so we don't need multiple copies..
	//Here json is always UTF encoded
    protected static JsonFactory jfactory = new JsonFactory();
    
    public AbstractMarsImageMetadata() {
    	super();
    }
    
    public AbstractMarsImageMetadata(String UID) {
    	super(UID);
    }
    
    public AbstractMarsImageMetadata(String UID, MarsTable dataTable) {
    	super(UID, dataTable);
    }
	
	public AbstractMarsImageMetadata(JsonParser jParser) throws IOException {
		super(jParser);
	}
	
	@Override
	protected void createIOMaps() {
		super.createIOMaps();
		
		bdvSources = new LinkedHashMap<String, BdvSource>();
		
		//Set defaults in case these don't exist.
		log = "";
		Microscope = "unknown";
		SourceDirectory = "unknown";
		CollectionDate = "unknown";

		//Add to output map
		outputMap.put("Microscope", MarsUtil.catchConsumerException(jGenerator -> {
			if(Microscope != null)
	  			jGenerator.writeStringField("Microscope", Microscope);
	 	}, IOException.class));
		outputMap.put("SourceDirectory", MarsUtil.catchConsumerException(jGenerator -> {
	  		if (SourceDirectory != null)
	  			jGenerator.writeStringField("SourceDirectory", SourceDirectory);
	 	}, IOException.class));
		outputMap.put("CollectionDate", MarsUtil.catchConsumerException(jGenerator -> {
	  		if (CollectionDate != null)
	  			jGenerator.writeStringField("CollectionDate", CollectionDate);
	 	}, IOException.class));
		outputMap.put("Log", MarsUtil.catchConsumerException(jGenerator -> {
	  		if (!log.equals("")) {
	  			jGenerator.writeStringField("Log", log);
	  		}
	 	}, IOException.class));
		outputMap.put("BdvSources", MarsUtil.catchConsumerException(jGenerator -> {
			if (bdvSources.size() > 0) {
				jGenerator.writeArrayFieldStart("BdvSources");
				for (BdvSource source : bdvSources.values()) {
					source.toJSON(jGenerator);
				}
				jGenerator.writeEndArray();
			}
	 	}, IOException.class));
		
		//Add to input map
		inputMap.put("Microscope", MarsUtil.catchConsumerException(jParser -> {
	    	Microscope = jParser.getText();
		}, IOException.class));
		inputMap.put("SourceDirectory", MarsUtil.catchConsumerException(jParser -> {
	    	SourceDirectory = jParser.getText();
		}, IOException.class));
		inputMap.put("CollectionDate", MarsUtil.catchConsumerException(jParser -> {
	    	CollectionDate = jParser.getText();
		}, IOException.class));
		inputMap.put("Log", MarsUtil.catchConsumerException(jParser -> {
	    	log = jParser.getText();
		}, IOException.class));
		inputMap.put("BdvSources", MarsUtil.catchConsumerException(jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				BdvSource source = new BdvSource(jParser);
				bdvSources.put(source.getName(), source);
			}
		}, IOException.class));
	}
	
	public void putBdvSource(BdvSource source) {
		bdvSources.put(source.getName(), source);
	}
	
	public BdvSource getBdvSource(String name) {
		return bdvSources.get(name);
	}
	
	public void removeBdvSource(String name) {
		bdvSources.remove(name);
	}
	
	public Collection<BdvSource> getBdvSources() {
		return bdvSources.values();
	}
	
	public Set<String> getBdvSourceNames() {
		return bdvSources.keySet();
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
}
