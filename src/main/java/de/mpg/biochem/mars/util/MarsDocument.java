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
	private String content;
	private Map<String, String> media;
	
	public MarsDocument(String name) {
		this.name = name;
		this.content = "";
		
		media = new LinkedHashMap<String, String>();
	}
	
	public MarsDocument(String name, String content) {
		this.name = name;
		this.content = content;
		
		media = new LinkedHashMap<String, String>();
	}
	
	public MarsDocument(JsonParser jParser) throws IOException {
		fromJSON(jParser);
	}
	
	@Override
	protected void createIOMaps() {
	
		setJsonField("name", jGenerator -> jGenerator.writeStringField("name",
			name), jParser -> name = jParser.getText());
		
		setJsonField("content", jGenerator -> jGenerator.writeStringField("content",
				content), jParser -> content = jParser.getText());
	
		setJsonField("media", jGenerator -> {
			if (media.size() > 0) {
				jGenerator.writeObjectFieldStart("media");
				for (String id : media.keySet()) {
					jGenerator.writeStringField(id, media.get(name));
				}
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

