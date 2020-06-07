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
	
	public int getChannelIndex() {
		return channelIndex;
	}
	
	public String getName() {
		return name;
	}
	
	public String getID() {
		return id;
	}
	
	public Binning getBinning() {
		return binning;
	}
	
	public Double getGain() {
		return gain;
	}
	
	public ElectricPotential getVoltage() {
		return voltage;
	}
	
	public String getDetectorSettingID() {
		return detectorSettingsID;
	}
	
	@Override
	protected void createIOMaps() {
		
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
