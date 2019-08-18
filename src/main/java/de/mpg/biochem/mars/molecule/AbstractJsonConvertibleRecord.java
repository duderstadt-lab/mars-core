package de.mpg.biochem.mars.molecule;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.util.MarsUtil;

public abstract class AbstractJsonConvertibleRecord implements JsonConvertibleRecord {
	
	protected LinkedHashMap<String, Predicate<JsonGenerator>> outputMap;
	
	protected HashMap<String, Predicate<JsonParser>> inputMap;
	
	public AbstractJsonConvertibleRecord() {
		outputMap = new LinkedHashMap<String, Predicate<JsonGenerator>>();
		inputMap = new HashMap<String, Predicate<JsonParser>>();
		createIOMaps();
	}
	
	public void toJSON(JsonGenerator jGenerator) throws IOException {
		jGenerator.writeStartObject();
		for (String field : outputMap.keySet()) {
			if (!outputMap.get(field).test(jGenerator))
				throw new IOException("IOExcpetion: JsonGenerator encountered a problem writing to the output stream");
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
		JsonToken nextToken = JsonToken.NOT_AVAILABLE;
		String fieldBlockName = "";
		while (nextToken != JsonToken.END_OBJECT) {
			nextToken = jParser.nextToken(); 
			if (nextToken == null) {
				System.out.println("JsonParser encountered an incomplete record.");
				break;
			}
			
		    String fieldname = jParser.getCurrentName();

		    if (fieldname == null)
		    	continue;
		    else 
		    	fieldBlockName = fieldname;
		    	
		    if (inputMap.containsKey(fieldname)) {
		    	jParser.nextToken();
			    if (!inputMap.get(fieldname).test(jParser))
			    	throw new IOException("IOExcpetion: JsonParser encountered a problem reading from the input stream");
			    continue;
		    }
		    
		    //SHOULD BE UNREACHABLE
		    //This is only reached if there is an unexpected field added to the json record
		    //In that case we simply pass through it and all substructures that contain arrays or objects
		    if (jParser.getCurrentToken() == JsonToken.START_OBJECT) {
		    	System.out.println("unknown object " + fieldBlockName + " encountered in the record ... skipping");
		    	MarsUtil.passThroughUnknownObjects(jParser);
		    } else if (jParser.getCurrentToken() == JsonToken.START_ARRAY) {
		    	System.out.println("unknown array " + fieldBlockName + " encountered in the record ... skipping");
		    	MarsUtil.passThroughUnknownArrays(jParser);
		    } else {
		    	//Must just be a normal field... so it won't escape the loop prematurely.
		    }
		}
	}
	
	protected abstract void createIOMaps();
}
