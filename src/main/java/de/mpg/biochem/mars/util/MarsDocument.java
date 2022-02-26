/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2022 Karl Duderstadt
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
package de.mpg.biochem.mars.util;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import de.mpg.biochem.mars.molecule.JsonConvertibleRecord;

public class MarsDocument extends AbstractJsonConvertibleRecord implements
	JsonConvertibleRecord
{

	private String name;
	private String content = "";
	private Map<String, String> media = new LinkedHashMap<>();
	
	public MarsDocument(String name) {
		this.name = name;
	}
	
	public MarsDocument(String name, String content) {
		this.name = name;
		this.content = content;
	}
	
	public MarsDocument(JsonParser jParser) throws IOException {
		fromJSON(jParser);
	}
	
	@Override
	protected void createIOMaps() {
	
		setJsonField("name", jGenerator -> jGenerator.writeStringField("name", name),
				jParser -> name = jParser.getText());
		
		setJsonField("content", jGenerator -> jGenerator.writeStringField("content", content),
				jParser -> content = jParser.getText());
	
		setJsonField("media", jGenerator -> {
			if (media.size() > 0) {
				jGenerator.writeObjectFieldStart("media");
				for (String id : media.keySet())
					jGenerator.writeStringField(id, media.get(id));
				jGenerator.writeEndObject();
			}
		}, jParser -> {
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String id = jParser.getCurrentName();
				jParser.nextToken();
				media.put(id, jParser.getValueAsString());
			}
		});
	}
	
	// Getters and Setters
	/**
	 * Get position name.
	 * 
	 * @return Position name.
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Set position name.
	 * 
	 * @param name Position name.
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	public String getContent() {
		return content;
	}
	
	public void setContent(String content) {
		this.content = content;
	}
	
	public void putMedia(String id, String mediaData) {
		media.put(id, mediaData);
	}
	
	public String getMedia(String id) {
		return media.get(id);
	}
	
	public Set<String> getMediaIDs() {
		return media.keySet();
	}
	
	public void removeAllMedia() {
		media.clear();
	}
}

