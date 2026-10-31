/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2026 Karl Duderstadt
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
import java.time.Instant;

import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import de.mpg.biochem.mars.molecule.JsonConvertibleRecord;

/**
 * A rendered figure, such as a kymograph or montage, stored in a molecule
 * record. The image is kept as an html encoded data string so it can be shown
 * again without regenerating it. The settings used to generate the figure are
 * kept as an opaque json string owned by the tool that created it, so the
 * figure can be regenerated or edited later.
 */
public class MarsFigure extends AbstractJsonConvertibleRecord implements
	JsonConvertibleRecord
{

	private String type = "";
	private String settings = "";
	private String image = "";
	private String generator = "";
	private Instant created;

	public MarsFigure() {
		this.created = Instant.now();
	}

	public MarsFigure(String type, String settings, String image) {
		this();
		this.type = type;
		this.settings = settings;
		this.image = image;
	}

	public MarsFigure(JsonParser jParser) throws IOException {
		fromJSON(jParser);
	}

	@Override
	protected void createIOMaps() {

		setJsonField("type", jGenerator -> jGenerator.writeStringField("type",
			type), jParser -> type = jParser.getText());

		setJsonField("created", jGenerator -> {
			if (created != null) jGenerator.writeStringField("created", created
				.toString());
		}, jParser -> created = Instant.parse(jParser.getText()));

		setJsonField("generator", jGenerator -> jGenerator.writeStringField(
			"generator", generator), jParser -> generator = jParser.getText());

		setJsonField("settings", jGenerator -> jGenerator.writeStringField(
			"settings", settings), jParser -> settings = jParser.getText());

		setJsonField("image", jGenerator -> jGenerator.writeStringField("image",
			image), jParser -> image = jParser.getText());
	}

	/**
	 * Get the figure type, for example kymograph or montage.
	 *
	 * @return The figure type.
	 */
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	/**
	 * Get the json settings used to generate the figure.
	 *
	 * @return The settings json string.
	 */
	public String getSettings() {
		return settings;
	}

	public void setSettings(String settings) {
		this.settings = settings;
	}

	/**
	 * Get the html encoded image, for example data:image/png;base64,...
	 *
	 * @return The html encoded image.
	 */
	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	/**
	 * Get the name and version of the tool that generated the figure.
	 *
	 * @return The generator.
	 */
	public String getGenerator() {
		return generator;
	}

	public void setGenerator(String generator) {
		this.generator = generator;
	}

	public Instant getCreated() {
		return created;
	}

	public void setCreated(Instant created) {
		this.created = created;
	}
}
