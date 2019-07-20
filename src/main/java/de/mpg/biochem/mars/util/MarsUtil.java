package de.mpg.biochem.mars.util;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class MarsUtil {
  	
	/**
	 * Used to bypass unknown Json Objects with JacksonJson streaming interface.
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
  	
  	public static <T, E extends Exception> Predicate<T> catchConsumerException(
  		ThrowingConsumer<T, E> throwingConsumer, Class<E> exceptionType) {
	  
	    return i -> {
	        try {
	            throwingConsumer.accept(i);
	            return true;
	        } catch (Exception e) {
	            try {
	                E ex = exceptionType.cast(e);
	                System.err.println("Exception: " + ex.getMessage());
	            } catch (ClassCastException cex) {
	                throw new RuntimeException(e);
	            }
	            return false;
	        }
	    };
	}
  	
  	@FunctionalInterface
  	public interface ThrowingConsumer<T, E extends Exception> {
  	    void accept(T t) throws E;
  	}
}