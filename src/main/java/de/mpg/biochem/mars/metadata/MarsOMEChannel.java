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
		
		ome.xml.model.Channel ch = ((OMEXMLMetadataRoot) md.getRoot()).getImage(0).getPixels().getChannel(channelIndex);
		
		if (ch.getName() != null)
			name = md.getChannelName(imageIndex, channelIndex);
		if (ch.getID() != null)
			id = md.getChannelID(imageIndex, channelIndex);
		
		if (ch.getDetectorSettings() != null) {
			binning = md.getDetectorSettingsBinning(imageIndex, channelIndex);
			gain = md.getDetectorSettingsGain(imageIndex, channelIndex);
			voltage = md.getDetectorSettingsVoltage(imageIndex, channelIndex);
			detectorSettingsID = md.getDetectorSettingsID(imageIndex, channelIndex);
		}
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
		
		setJsonField("ChannelIndex", 
				jGenerator -> jGenerator.writeNumberField("ChannelIndex", channelIndex),
				jParser -> channelIndex = jParser.getIntValue());
		
		setJsonField("Name", 
			jGenerator -> jGenerator.writeStringField("Name", name),
			jParser -> name = jParser.getText());
		
		setJsonField("ID", 
			jGenerator -> jGenerator.writeStringField("ID", id),
			jParser -> id = jParser.getText());
		
		setJsonField("Binning", 
			jGenerator -> {
					if (binning != null)
						jGenerator.writeStringField("Binning", binning.getValue());
				},
			jParser -> { 
				BinningEnumHandler handler = new BinningEnumHandler();
				try {
					binning = (Binning) handler.getEnumeration(jParser.getText());
				} catch (EnumerationException e) {
					e.printStackTrace();
				}
			});

		setJsonField("Gain",
			jGenerator -> { 
				if (gain != null)
					jGenerator.writeNumberField("Gain", gain.doubleValue());
			},
			jParser -> gain = jParser.getDoubleValue());
		
		//Should we keep track of the units here ???
		setJsonField("Voltage",
				jGenerator -> {
					if (voltage != null)
						jGenerator.writeNumberField("Voltage", voltage.value().doubleValue());
				},
				jParser -> voltage = new ElectricPotential(jParser.getNumberValue(), UNITS.VOLT));
		
		setJsonField("DetectorSettingsID",
				jGenerator -> jGenerator.writeStringField("DetectorSettingsID", detectorSettingsID),
				jParser -> detectorSettingsID = jParser.getText());
			
	}
}
