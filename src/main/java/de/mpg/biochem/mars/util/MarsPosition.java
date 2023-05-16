/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2023 Karl Duderstadt
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

import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import de.mpg.biochem.mars.molecule.JsonConvertibleRecord;
import de.mpg.biochem.mars.molecule.Molecule;

/**
 * This class provides a simple position definition. Usually this is a position
 * in time or slice that is of interest for further analysis. {@link Molecule}s
 * and {@link MarsMetadata}s can contain a list of these positions.
 * <p>
 * Position definitions include the name, value, column (Time (s) or slice or
 * otherwise), and color (in hex). These values are used by mars-fx to draw
 * position on plots based value and color.
 * </p>
 * 
 * @author Karl Duderstadt
 */
public class MarsPosition extends AbstractJsonConvertibleRecord implements
	JsonConvertibleRecord
{

	private String name = "Position";
	private String column = "T";
	private String color = "#000000";
	private double stroke = 1;
	private double position = 0;

	/**
	 * Constructor for creating a plot position of a given name with default
	 * settings. This has a default black color and position value = 0. The
	 * position can then be changed in the gui.
	 * 
	 * @param name Name of the MarsPosition to create.
	 */
	public MarsPosition(String name) {
		this.name = name;
	}

	/**
	 * Constructor for creating a plot position given a json stream. Used when
	 * loading objects from file.
	 * 
	 * @param jParser Json stream used to build the object.
	 * @throws IOException Thrown if unable to parse Json from JsonParser.
	 */
	public MarsPosition(JsonParser jParser) throws IOException {
		fromJSON(jParser);
	}

	/**
	 * Constructor for creating a plot region using known parameters.
	 * 
	 * @param name Name of the region.
	 * @param column Name of column the region refers to.
	 * @param position Location of the position in unit of column.
	 * @param color Color string in hex format.
	 * @param stroke Line thickness.
	 */
	public MarsPosition(String name, String column, double position, String color,
		double stroke)
	{
		this.name = name;
		this.column = column;
		this.color = color;
		this.position = position;
		this.stroke = stroke;
	}

	@Override
	protected void createIOMaps() {

		setJsonField("name", jGenerator -> jGenerator.writeStringField("name",
			name), jParser -> name = jParser.getText());

		setJsonField("column", jGenerator -> jGenerator.writeStringField("column",
			column), jParser -> column = jParser.getText());

		setJsonField("position", jGenerator -> jGenerator.writeNumberField(
			"position", position), jParser -> position = jParser.getDoubleValue());

		setJsonField("color", jGenerator -> jGenerator.writeStringField("color",
			color), jParser -> color = jParser.getText());

		setJsonField("stroke", jGenerator -> jGenerator.writeNumberField("stroke",
			stroke), jParser -> stroke = jParser.getDoubleValue());

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

	/**
	 * Get the name of the column the position value refers to.
	 * 
	 * @return The column name.
	 */
	public String getColumn() {
		return column;
	}

	/**
	 * Set the name of the column the position value refers to.
	 * 
	 * @param column Name of the column that defines the position units.
	 */
	public void setColumn(String column) {
		this.column = column;
	}

	/**
	 * Get the string color of the region in hex.
	 * 
	 * @return Color string in hex.
	 */
	public String getColor() {
		return color;
	}

	/**
	 * Set the hex color string.
	 * 
	 * @param color The hex color string.
	 */
	public void setColor(String color) {
		this.color = color;
	}

	/**
	 * Get the position value.
	 * 
	 * @return Position value.
	 */
	public double getPosition() {
		return position;
	}

	/**
	 * Set the position value in units of column.
	 * 
	 * @param position Position value.
	 */
	public void setPosition(double position) {
		this.position = position;
	}

	/**
	 * Get the stroke value (line thickness).
	 * 
	 * @return Stroke (line thickness).
	 */
	public double getStroke() {
		return stroke;
	}

	/**
	 * Set the stroke value (line thickness). Default is 1.0.
	 * 
	 * @param stroke Line thickness.
	 */
	public void setStroke(double stroke) {
		this.stroke = stroke;
	}
}
