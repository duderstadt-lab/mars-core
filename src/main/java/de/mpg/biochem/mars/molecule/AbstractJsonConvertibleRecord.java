/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2025 Karl Duderstadt
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.metadata.AbstractMarsMetadata;
import de.mpg.biochem.mars.util.MarsUtil;
import de.mpg.biochem.mars.util.MarsUtil.ThrowingConsumer;

/**
 * Abstract superclass for JsonConvertibleRecords. Contains basic conversion
 * methods to and from Json based on the Jackson streaming API. This abstract
 * class can be extended for any classes that needs to be serialized or
 * de-serialized from Json. The subclass must implement the
 * {@link #createIOMaps() createIOMaps} method to define the input and output
 * Predicate maps that determine how objects, fields, arrays should be stored
 * using the Jackson streaming API.
 * <p>
 * For examples, see {@link MarsRecord}, {@link AbstractMolecule},
 * {@link AbstractMarsMetadata}.
 * </p>
 * 
 * @author Karl Duderstadt
 */
public abstract class AbstractJsonConvertibleRecord implements
	JsonConvertibleRecord
{

	private final LinkedHashMap<String, Predicate<JsonGenerator>> outputMap =
			new LinkedHashMap<>();
	private final HashMap<String, Predicate<JsonParser>> inputMap =
			new HashMap<>();

	/**
	 * IOMaps are created during the first call to toJSON or fromJSON lazily This
	 * ensures subclasses overriding createIOMaps have been fully initialized
	 * before the first call. If false this field triggers initialization.
	 */
	private boolean IOMapsInitialized = false;

	private boolean showWarnings = true;

	/**
	 * Constructor for creating a JsonConvertibleRecord.
	 */
	public AbstractJsonConvertibleRecord() {}

	/**
	 * Stream a record to JSON. Stream a record from to a file using the
	 * JsonGenerator stream provided.
	 * 
	 * @param jGenerator A JsonGenerator for streaming a record to a file.
	 * @throws IOException if there is a problem reading from the file.
	 */
	@Override
	public void toJSON(JsonGenerator jGenerator) throws IOException {
		if (!IOMapsInitialized) {
			createIOMaps();
			IOMapsInitialized = true;
		}

		jGenerator.writeStartObject();
		for (String field : outputMap.keySet()) {
			if (!outputMap.get(field).test(jGenerator)) throw new IOException(
				"IOException: JsonGenerator encountered a problem writing to the output stream");
		}
		jGenerator.writeEndObject();
	}

	/**
	 * Read a record from JSON. Load a record from a file using the JsonParser
	 * stream provided.
	 * 
	 * @param jParser A JsonParser for loading the record from a file.
	 * @throws IOException if there is a problem reading from the file.
	 */
	@Override
	public void fromJSON(JsonParser jParser) throws IOException {
		if (!IOMapsInitialized) {
			createIOMaps();
			IOMapsInitialized = true;
		}

		JsonToken nextToken = JsonToken.NOT_AVAILABLE;
		String fieldBlockName;
		while (nextToken != JsonToken.END_OBJECT) {
			nextToken = jParser.nextToken();
			if (nextToken == null) {
				System.out.println("JsonParser encountered an incomplete record.");
				break;
			}

			String fieldName = jParser.getCurrentName();

			if (fieldName == null) continue;
			else fieldBlockName = fieldName;

			if (inputMap.containsKey(fieldName)) {
				jParser.nextToken();
				if (!inputMap.get(fieldName).test(jParser)) throw new IOException(
					"IOException: JsonParser encountered a problem reading from the input stream");
				continue;
			}

			// SHOULD BE UNREACHABLE
			// This is only reached if there is an unexpected field added to the json
			// record
			// In that case we simply pass through it and all substructures that
			// contain arrays or objects
			if (jParser.getCurrentToken() == JsonToken.START_OBJECT) {
				if (showWarnings) System.out.println("unknown object " +
					fieldBlockName + " encountered in the record ... skipping");
				MarsUtil.passThroughUnknownObjects(jParser);
			}
			else if (jParser.getCurrentToken() == JsonToken.START_ARRAY) {
				if (showWarnings) System.out.println("unknown array " + fieldBlockName +
					" encountered in the record ... skipping");
				MarsUtil.passThroughUnknownArrays(jParser);
			}
		}
	}

	@Override
	public void setShowWarnings(boolean showWarnings) {
		this.showWarnings = showWarnings;
	}

	@Override
	public void setJsonField(String field,
		ThrowingConsumer<JsonGenerator, IOException> output,
		ThrowingConsumer<JsonParser, IOException> input)
	{
		if (output != null) outputMap.put(field, MarsUtil.catchConsumerException(
			output, IOException.class));

		if (input != null) inputMap.put(field, MarsUtil.catchConsumerException(
			input, IOException.class));
	}

	/**
	 * Get the JsonGenerator for a field.
	 * 
	 * @param field Json field.
	 * @return JsonGenerator predicate for field.
	 */
	@Override
	public Predicate<JsonGenerator> getJsonGenerator(String field) {
		return outputMap.get(field);
	}

	/**
	 * Get the JsonParser for a field.
	 * 
	 * @param field Json field.
	 * @return JsonParser predicate for field.
	 */
	@Override
	public Predicate<JsonParser> getJsonParser(String field) {
		return inputMap.get(field);
	}

	/**
	 * Get the record in Json string format.
	 * 
	 * @return Json string representation of the record.
	 */
	@Override
	public String dumpJSON() {
		return MarsUtil.dumpJSON(this::toJSON);
	}

	/**
	 * Must be implemented in subclasses to define how fields, objects, arrays
	 * should be saved based on the Jackson streaming API.
	 */
	protected abstract void createIOMaps();
}
