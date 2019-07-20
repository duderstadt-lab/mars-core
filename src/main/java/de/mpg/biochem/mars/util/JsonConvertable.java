package de.mpg.biochem.mars.util;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

/**
 * Interface for objects that are Json serialized and deserialized. 
 *
 * @author Karl Duderstadt
 */
public interface JsonConvertable {
	
	/** Serializes the implementing class to JSON using the JsonGenerator provided. */
	void toJSON(JsonGenerator jGenerator) throws IOException;
	
	/** Deserializes an instance of the implementing class from JSON using the JsonParser provided. */
	void fromJSON(JsonParser jParser) throws IOException;

}
