package de.mpg.biochem.mars.util;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class MarsUtil {
  	
	/**
	 * User to parse uknown Json Objects with JacksonJson streaming interface.
	 * 
	 * @param jParser
	 * @throws IOException
	 */
  	public static void passThroughUnknownObjects(JsonParser jParser) throws IOException {
      	while (jParser.nextToken() != JsonToken.END_OBJECT) {
      		if (jParser.getCurrentToken() == JsonToken.START_OBJECT)
      			passThroughUnknownObjects(jParser);
      	}
  	}
}
