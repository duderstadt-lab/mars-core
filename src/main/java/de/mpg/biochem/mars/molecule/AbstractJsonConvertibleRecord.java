/*******************************************************************************
 * Copyright (C) 2019, Duderstadt Lab
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

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.util.MarsUtil;

/**
 * Abstract superclass for JsonConvertibleRecords. Contains basic conversion 
 * methods to and from Json based on the Jackson streaming API. This abstract
 * class can be extended for any classes that need to be serialized or de-serialized
 * from Json. The subclass must then define an input and output Predicate maps that define
 * how objects, fields, arrays should be stored using the jackson streaming API. 
 * <p>
 * For examples, see {@link MarsRecord}, {@link AbstractMolecule}, {@link AbstractMarsMetadata}.
 * </p>
 * @author Karl Duderstadt
 */
public abstract class AbstractJsonConvertibleRecord implements JsonConvertibleRecord {
	
	protected LinkedHashMap<String, Predicate<JsonGenerator>> outputMap;
	
	protected HashMap<String, Predicate<JsonParser>> inputMap;
	
	//IOMaps are created during the first call to toJSON or fromJSON lazily
	//This ensures subclasses overriding createIOMaps have been fully 
	//initialized before the first call. If false this field triggers initialization.
	private boolean IOMapsInitialized;
	
	/**
	 * Constructor for creating a JsonConvertiableRecord. 
	 */
	public AbstractJsonConvertibleRecord() {
		outputMap = new LinkedHashMap<String, Predicate<JsonGenerator>>();
		inputMap = new HashMap<String, Predicate<JsonParser>>();
		IOMapsInitialized = false;
	}
	
	/**
	 * Stream a record to JSON. Stream a record
	 * from to a file using the JsonGenerator stream provided.
	 * 
	 * @param jGenerator A JsonGenerator for streaming a
	 * record to a file.
	 * 
     * @throws IOException if there is a problem reading from the file.
	 */
	public void toJSON(JsonGenerator jGenerator) throws IOException {
		if (!IOMapsInitialized) {
			createIOMaps();
			IOMapsInitialized = true;
		}
		
		jGenerator.writeStartObject();
		for (String field : outputMap.keySet()) {
			if (!outputMap.get(field).test(jGenerator))
				throw new IOException("IOExcpetion: JsonGenerator encountered a problem writing to the output stream");
		}
		jGenerator.writeEndObject();
	}
	
	/**
	 * Read a record from JSON. Load a record
	 * from a file using the JsonParser stream provided.
	 * 
	 * @param jParser A JsonParser for loading the 
	 * record from a file.
	 * 
     * @throws IOException if there is a problem reading from the file.
	 */
	public void fromJSON(JsonParser jParser) throws IOException {
		if (!IOMapsInitialized) {
			createIOMaps();
			IOMapsInitialized = true;
		}
		
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
	
	//Must be implemented in subclasses to define how fields, objects, arrays should be saved
	//based on the Jackson streaming API.
	protected abstract void createIOMaps();
}
