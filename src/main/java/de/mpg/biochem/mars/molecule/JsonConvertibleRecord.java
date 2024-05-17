/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2024 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package de.mpg.biochem.mars.molecule;

import java.io.IOException;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.util.MarsUtil.ThrowingConsumer;

/**
 * Interface for objects that are Json serialized and deserialized.
 *
 * @author Karl Duderstadt
 */
public interface JsonConvertibleRecord {

	/**
	 * Serializes the implementing class to JSON using the JsonGenerator provided.
	 * 
	 * @param jGenerator JsonGenerator stream to read Json from.
	 * @throws IOException Thrown if unable to write to JsonGenerator.
	 */
	void toJSON(JsonGenerator jGenerator) throws IOException;

	/**
	 * Deserializes an instance of the implementing class from JSON using the
	 * JsonParser provided.
	 * 
	 * @param jParser JsonParser stream to write Json to.
	 * @throws IOException Thrown if unable to read from JsonParser.
	 */
	void fromJSON(JsonParser jParser) throws IOException;

	void setShowWarnings(boolean showWarnings);

	void setJsonField(String field,
		ThrowingConsumer<JsonGenerator, IOException> output,
		ThrowingConsumer<JsonParser, IOException> input);

	/**
	 * Get the JsonGenerator for a field.
	 * 
	 * @param field Json field.
	 * @return JsonGenerator predicate for field.
	 */
    Predicate<JsonGenerator> getJsonGenerator(String field);

	/**
	 * Get the JsonParser for a field.
	 * 
	 * @param field Json field.
	 * @return JsonParser predicate for field.
	 */
    Predicate<JsonParser> getJsonParser(String field);

	/**
	 * Get the record in Json string format.
	 * 
	 * @return Json string representation of the MarsMetadata record.
	 */
	String dumpJSON();

}
