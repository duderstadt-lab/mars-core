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

package de.mpg.biochem.mars.metadata;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;

import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import ome.units.UNITS;
import ome.units.quantity.ElectricPotential;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.meta.OMEXMLMetadataRoot;
import ome.xml.model.enums.Binning;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.handlers.BinningEnumHandler;

public class MarsOMEChannel extends AbstractJsonConvertibleRecord {

	private int channelIndex;
	private String name;
	private String id;
	private Binning binning;
	private Double gain;
	private ElectricPotential voltage;
	private String detectorSettingsID;

	public MarsOMEChannel(OMEXMLMetadata md, int imageIndex, int channelIndex) {
		super();

		this.channelIndex = channelIndex;

		ome.xml.model.Channel ch = ((OMEXMLMetadataRoot) md.getRoot()).getImage(0)
			.getPixels().getChannel(channelIndex);

		if (ch.getName() != null) name = md.getChannelName(imageIndex,
			channelIndex);
		if (ch.getID() != null) id = md.getChannelID(imageIndex, channelIndex);

		if (ch.getDetectorSettings() != null) {
			binning = md.getDetectorSettingsBinning(imageIndex, channelIndex);
			gain = md.getDetectorSettingsGain(imageIndex, channelIndex);
			voltage = md.getDetectorSettingsVoltage(imageIndex, channelIndex);
			detectorSettingsID = md.getDetectorSettingsID(imageIndex, channelIndex);
		}
	}

	public MarsOMEChannel() {
		super();
	}

	public MarsOMEChannel(JsonParser jParser) throws IOException {
		super();
		fromJSON(jParser);
	}

	public void setChannelIndex(int channelIndex) {
		this.channelIndex = channelIndex;
	}

	public int getChannelIndex() {
		return channelIndex;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setID(String id) {
		this.id = id;
	}

	public String getID() {
		return id;
	}

	public void setBinning(Binning binning) {
		this.binning = binning;
	}

	public Binning getBinning() {
		return binning;
	}

	public void setGain(Double gain) {
		this.gain = gain;
	}

	public Double getGain() {
		return gain;
	}

	public void setVoltage(ElectricPotential voltage) {
		this.voltage = voltage;
	}

	public ElectricPotential getVoltage() {
		return voltage;
	}

	public void setDetectorSettingsID(String detectorSettingsID) {
		this.detectorSettingsID = detectorSettingsID;
	}

	public String getDetectorSettingID() {
		return detectorSettingsID;
	}

	@Override
	protected void createIOMaps() {

		setJsonField("channelIndex", jGenerator -> jGenerator.writeNumberField(
			"channelIndex", channelIndex), jParser -> channelIndex = jParser
				.getIntValue());

		setJsonField("name", jGenerator -> jGenerator.writeStringField("name",
			name), jParser -> name = jParser.getText());

		setJsonField("id", jGenerator -> jGenerator.writeStringField("id", id),
			jParser -> id = jParser.getText());

		setJsonField("binning", jGenerator -> {
			if (binning != null) jGenerator.writeStringField("binning", binning
				.getValue());
		}, jParser -> {
			BinningEnumHandler handler = new BinningEnumHandler();
			try {
				binning = (Binning) handler.getEnumeration(jParser.getText());
			}
			catch (EnumerationException e) {
				e.printStackTrace();
			}
		});

		setJsonField("gain", jGenerator -> {
			if (gain != null) jGenerator.writeNumberField("gain", gain);
		}, jParser -> gain = jParser.getDoubleValue());

		// Should we keep track of the units here ???
		setJsonField("voltage", jGenerator -> {
			if (voltage != null) jGenerator.writeNumberField("voltage", voltage
				.value().doubleValue());
		}, jParser -> voltage = new ElectricPotential(jParser.getNumberValue(),
			UNITS.VOLT));

		setJsonField("detectorSettingsID", jGenerator -> jGenerator
			.writeStringField("detectorSettingsID", detectorSettingsID),
			jParser -> detectorSettingsID = jParser.getText());

		/*
		 * 
		 * The fields below are needed for backwards compatibility.
		 * 
		 * Please remove for a future release.
		 * 
		 */

		setJsonField("ChannelIndex", null, jParser -> channelIndex = jParser
			.getIntValue());

		setJsonField("Name", null, jParser -> name = jParser.getText());

		setJsonField("Binning", null, jParser -> {
			BinningEnumHandler handler = new BinningEnumHandler();
			try {
				binning = (Binning) handler.getEnumeration(jParser.getText());
			}
			catch (EnumerationException e) {
				e.printStackTrace();
			}
		});

		setJsonField("Gain", null, jParser -> gain = jParser.getDoubleValue());

		setJsonField("Voltage", null, jParser -> voltage = new ElectricPotential(
			jParser.getNumberValue(), UNITS.VOLT));

		setJsonField("DetectorSettingsID", null, jParser -> detectorSettingsID =
			jParser.getText());
	}
}
