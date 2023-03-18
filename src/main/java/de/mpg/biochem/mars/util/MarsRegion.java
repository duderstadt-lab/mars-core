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

import de.mpg.biochem.mars.kcp.commands.KCPCommand;
import de.mpg.biochem.mars.kcp.commands.SigmaCalculatorCommand;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import de.mpg.biochem.mars.molecule.JsonConvertibleRecord;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.commands.RegionDifferenceCalculatorCommand;
import de.mpg.biochem.mars.molecule.commands.VarianceCalculatorCommand;

/**
 * This class provides a simple region definition. Usually this a region in time
 * or slice that is of interest for further analysis. {@link Molecule}s and
 * {@link MarsMetadata}s can contain a list of these regions, which are used
 * when running several commands including {@link KCPCommand},
 * {@link SigmaCalculatorCommand}, {@link KCPCommand},
 * {@link RegionDifferenceCalculatorCommand}, {@link VarianceCalculatorCommand}.
 * <p>
 * Region definitions include name, start value, end value, column (Time (s) or
 * slice or otherwise), color (in hex), and opacity (range 0 to 1). These values
 * are used by mars-fx to draw regions on plots based on the start, end color
 * and opacity.
 * <p>
 * Opacity specified in the hex code is ignored. The opacity setting overrides
 * the value in the hex string.
 * 
 * @author Karl Duderstadt
 */
public class MarsRegion extends AbstractJsonConvertibleRecord implements
	JsonConvertibleRecord
{

	private String name = "Region";
	private String column = "T";
	private String color = "#416ef468";
	private double start = 0;
	private double end = 0;
	private double opacity = 0.2;

	/**
	 * Constructor for creating a plot region of a given name with default
	 * settings. This has a default light blue color and start = end = 0. The
	 * bounds can then be changed in the gui.
	 * 
	 * @param name Name of the region.
	 */
	public MarsRegion(String name) {
		this.name = name;
	}

	/**
	 * Constructor for creating a plot region given a json stream. Used when
	 * loading objects from file.
	 * 
	 * @param jParser Json stream used to build the object.
	 * @throws IOException Thrown if unable to parse Json from the JsonParser
	 *           stream.
	 */
	public MarsRegion(JsonParser jParser) throws IOException {
		fromJSON(jParser);
	}

	/**
	 * Constructor for creating a plot region using known parameters.
	 * 
	 * @param name Name of the region.
	 * @param column Name of column the region refers to.
	 * @param start Starting value of the region.
	 * @param end Ending value of the region.
	 * @param color Color string in hex format.
	 * @param opacity Opacity of the region between 0 and 1.
	 */
	public MarsRegion(String name, String column, double start, double end,
		String color, double opacity)
	{
		this.name = name;
		this.column = column;
		this.color = color;
		this.start = start;
		this.end = end;
		this.opacity = opacity;
	}

	@Override
	protected void createIOMaps() {

		setJsonField("name", jGenerator -> jGenerator.writeStringField("name",
			name), jParser -> name = jParser.getText());

		setJsonField("column", jGenerator -> jGenerator.writeStringField("column",
			column), jParser -> column = jParser.getText());

		setJsonField("start", jGenerator -> jGenerator.writeNumberField("start",
			start), jParser -> start = jParser.getDoubleValue());

		setJsonField("end", jGenerator -> jGenerator.writeNumberField("end", end),
			jParser -> end = jParser.getDoubleValue());

		setJsonField("color", jGenerator -> jGenerator.writeStringField("color",
			color), jParser -> color = jParser.getText());

		setJsonField("opacity", jGenerator -> jGenerator.writeNumberField("opacity",
			opacity), jParser -> opacity = jParser.getDoubleValue());

	}

	/**
	 * Get the name of the region.
	 * 
	 * @return The region name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Set the region name.
	 * 
	 * @param name The region name.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Get the name of the column the start and end values refer to.
	 * 
	 * @return The column name.
	 */
	public String getColumn() {
		return column;
	}

	/**
	 * Set the name of the column the start and end values refer to.
	 * 
	 * @param column The column name.
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
	 * Get lower bound of the region.
	 * 
	 * @return Lower bound of the region.
	 */
	public double getStart() {
		return start;
	}

	/**
	 * Set the lower bound of the region.
	 * 
	 * @param start The lower bound of the region.
	 */
	public void setStart(double start) {
		this.start = start;
	}

	/**
	 * Get the upper bound of the region.
	 * 
	 * @return The upper bound of the region.
	 */
	public double getEnd() {
		return end;
	}

	/**
	 * Set the upper bound of the region.
	 * 
	 * @param end The upper bound of the region.
	 */
	public void setEnd(double end) {
		this.end = end;
	}

	/**
	 * Set the opacity of the region. Range is 0 to 1. Used by the javafx gui when
	 * drawing regions on plots.
	 * 
	 * @param opacity The opacity of the region.
	 */
	public void setOpacity(double opacity) {
		this.opacity = opacity;
	}

	/**
	 * Get the opacity of the region. Range is 0 to 1. Used by the javafx gui when
	 * drawing regions on plots.
	 * 
	 * @return The opacity of the region.
	 */
	public double getOpacity() {
		return opacity;
	}
}
