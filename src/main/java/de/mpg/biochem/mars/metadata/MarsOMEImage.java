package de.mpg.biochem.mars.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import de.mpg.biochem.mars.molecule.JsonConvertibleRecord;
import ome.units.UNITS;
import ome.units.quantity.ElectricPotential;
import ome.units.quantity.Length;
import ome.units.quantity.Temperature;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.MapPair;
import ome.xml.model.enums.Binning;
import ome.xml.model.enums.DetectorType;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.UnitsLength;
import ome.xml.model.enums.UnitsTemperature;
import ome.xml.model.enums.handlers.BinningEnumHandler;
import ome.xml.model.enums.handlers.UnitsLengthEnumHandler;
import ome.xml.model.enums.handlers.UnitsTemperatureEnumHandler;
import ome.xml.model.enums.handlers.DetectorTypeEnumHandler;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.Timestamp;
import ome.xml.meta.OMEXMLMetadataRoot;
import io.scif.util.FormatTools;

public class MarsOMEImage extends AbstractJsonConvertibleRecord implements GenericModel, JsonConvertibleRecord {
	private Map<Integer, MarsOMEPlane> marsOMEPlanes = new ConcurrentHashMap<Integer, MarsOMEPlane>();
	
	private String id;
	private String pixelID;
	
	private Timestamp imageAquisitionDate;
	private int imageIndex;
	private String imageName;
	private String imageDescription;
	private List<Channel> channels = new ArrayList<>();
	
	private Length pixelsPhysicalSizeX, pixelsPhysicalSizeY, pixelsPhysicalSizeZ;
	
	private String detectorSerialNumber, detectorModel, detectorManufacturer;
	private Temperature temperature;
	private DetectorType detectorType;
	
	private DimensionOrder dimensionOrder;
	
	private PositiveInteger sizeC;
	private PositiveInteger sizeT;
	private PositiveInteger sizeX;
	private PositiveInteger sizeY;
	private PositiveInteger sizeZ;
	
	public MarsOMEImage(JsonParser jParser) throws IOException {
		super();
		fromJSON(jParser);
	}
	
	public MarsOMEImage(int imageIndex, OMEXMLMetadata md) {
		super();
		
		this.imageIndex = imageIndex;
		id = md.getImageID(imageIndex);
		pixelID = md.getPixelsID(imageIndex);
		imageAquisitionDate = md.getImageAcquisitionDate(imageIndex);
		imageName = md.getImageName(imageIndex);
		imageDescription = md.getImageDescription(imageIndex);
		
		for (int channelIndex=0; channelIndex < md.getChannelCount(imageIndex); channelIndex++)
			channels.add(new Channel(md, imageIndex, channelIndex));

		pixelsPhysicalSizeX = md.getPixelsPhysicalSizeX(imageIndex);
		pixelsPhysicalSizeY = md.getPixelsPhysicalSizeY(imageIndex);
		pixelsPhysicalSizeZ = md.getPixelsPhysicalSizeZ(imageIndex);
		
		dimensionOrder = md.getPixelsDimensionOrder(imageIndex);
		
		this.sizeC = md.getPixelsSizeC(imageIndex);
		this.sizeT = md.getPixelsSizeT(imageIndex);
		this.sizeX = md.getPixelsSizeX(imageIndex);
		this.sizeY = md.getPixelsSizeY(imageIndex);
		this.sizeZ = md.getPixelsSizeZ(imageIndex);
		
		//Build Planes
		for (int planeIndex = 0; planeIndex < md.getPlaneCount(imageIndex); planeIndex++)
			marsOMEPlanes.put(planeIndex, new MarsOMEPlane(this, md, imageIndex, planeIndex));
		
		if (marsOMEPlanes.size() == 0)
			createPlanesFromDimensions();
		
		if (md.getInstrumentCount() > 0) {
			detectorSerialNumber = md.getDetectorSerialNumber(0, imageIndex);
			detectorModel = md.getDetectorModel(0, imageIndex);
			detectorManufacturer = md.getDetectorManufacturer(0, imageIndex);
			detectorType = md.getDetectorType(0, imageIndex);
		}
		
		if (((OMEXMLMetadataRoot) md.getRoot()).getImage(0).getImagingEnvironment() != null)
			temperature = md.getImagingEnvironmentTemperature(imageIndex);
		
		//Build look-up from image/plane to MapAnnotation if they exist.
		if (((OMEXMLMetadataRoot) md.getRoot()).getStructuredAnnotations() != null && md.getMapAnnotationCount() > 0) {
			for (int i=0; i<md.getMapAnnotationCount(); i++) {
				String[] strList = md.getMapAnnotationID(i).split("-");
				int iIndex = Integer.valueOf(strList[1]);
				int planeIndex = Integer.valueOf(strList[2]);
				
				if (iIndex == imageIndex) {
					List<MapPair> omeFieldsList = md.getMapAnnotationValue(i);
					
					Map<String, String> fieldsMap = new HashMap<String, String>(); 
					for (MapPair pair : omeFieldsList)
						fieldsMap.put(pair.getName(), pair.getValue());
					
					marsOMEPlanes.get(planeIndex).setCustomFields(fieldsMap);
				}
			}
		}
	}
	
	private void createPlanesFromDimensions() {
		//Loop through all dimensions in nested loops and generate planes for all.
		for (int z=0; z < sizeZ.getValue(); z++)
			for (int c=0; c < sizeC.getValue(); c++)
				for (int t=0; t < sizeT.getValue(); t++) {
					int planeIndex = (int) getPlaneIndex(z, c, t);
					marsOMEPlanes.put(planeIndex, new MarsOMEPlane(this, imageIndex, planeIndex, 
							new NonNegativeInteger(z), 
							new NonNegativeInteger(c),
							new NonNegativeInteger(t)));
				}
	}
	
	//This must exist somewhere but I can't find it...
	private long getPlaneIndex(int z, int c, int t) {
		long[] length = new long[3];
		long[] position = new long[3];
		if (dimensionOrder.getValue().equals("XYCTZ")) {
			length[0] = sizeC.getValue();
			length[1] = sizeT.getValue();
			length[2] = sizeZ.getValue();
			
			position[0] = c;
			position[1] = t;
			position[2] = z;
		} else if (dimensionOrder.getValue().equals("XYCZT")) {
			length[0] = sizeC.getValue();
			length[1] = sizeZ.getValue();
			length[2] = sizeT.getValue();
			
			position[0] = c;
			position[1] = z;
			position[2] = t;
		} else if (dimensionOrder.getValue().equals("XYTCZ")) {
			length[0] = sizeT.getValue();
			length[1] = sizeC.getValue();
			length[2] = sizeZ.getValue();
			
			position[0] = t;
			position[1] = c;
			position[2] = z;
		} else if (dimensionOrder.getValue().equals("XYTZC")) {
			length[0] = sizeT.getValue();
			length[1] = sizeZ.getValue();
			length[2] = sizeC.getValue();
			
			position[0] = t;
			position[1] = z;
			position[2] = c;
		} else if (dimensionOrder.getValue().equals("XYZCT")) {
			length[0] = sizeZ.getValue();
			length[1] = sizeC.getValue();
			length[2] = sizeT.getValue();
			
			position[0] = z;
			position[1] = c;
			position[2] = t;
		} else if (dimensionOrder.getValue().equals("XYZTC")) {
			length[0] = sizeZ.getValue();
			length[1] = sizeT.getValue();
			length[2] = sizeC.getValue();
			
			position[0] = z;
			position[1] = t;
			position[2] = c;
		}
		return FormatTools.positionToRaster(length, position);
	}

	public String getID() {
		return id;
	}
	
	public String getPixelID() {
		return pixelID;
	}
	
	public Timestamp getAquisitionDate() {
		return imageAquisitionDate;
	}
	
	public String getName() {
		return imageName;
	}
	
	public String getDescription() {
		return imageDescription;
	}
	
	public Channel getChannel(int channelIndex) {
		return channels.get(channelIndex);
	}
	
	public Length getPixelsPhysicalSizeX() {
		return pixelsPhysicalSizeX;
	}
	
	public Length getPixelsPhysicalSizeY() {
		return pixelsPhysicalSizeY;
	}
	
	public Length getPixelsPhysicalSizeZ() {
		return pixelsPhysicalSizeZ;
	}
	
	public String getDetectorSerialNumber() {
		return detectorSerialNumber;
	}
	
	public String getDetectorModel() {
		return detectorModel;
	}
	
	public String getDetectorManufacturer() {
		return detectorManufacturer;
	}
	
	public Temperature getTemperature() {
		return temperature;
	}
	
	public DetectorType getDetectorType() {
		return detectorType;
	}
	
	public int getPlaneCount() {
		return marsOMEPlanes.size();
	}
	
	MarsOMEPlane getPlane(int planeIndex) {
		return marsOMEPlanes.get(planeIndex);
	}
	
	DimensionOrder getDimensionOrder() {
		return dimensionOrder;
	}
	
	public int getSizeC() {
		return sizeC.getValue();
	}
	
	public int getSizeT() {
		return sizeT.getValue();
	}
	
	public int getSizeX() {
		return sizeX.getValue();
	}
	
	public int getSizeY() {
		return sizeY.getValue();
	}
	
	public int getSizeZ() {
		return sizeZ.getValue();
	}
	
	public int getImageIndex() {
		return imageIndex;
	}

	@Override
	protected void createIOMaps() {

		UnitsLengthEnumHandler unitshandler = new UnitsLengthEnumHandler();

		setJsonField("ImageAcquisitionDate", 
			jGenerator -> {
				if (imageAquisitionDate != null)
					jGenerator.writeStringField("ImageAcquisitionDate", imageAquisitionDate.getValue());
			},
			jParser -> {
				imageAquisitionDate = new Timestamp(jParser.getText());
			});
		
		setJsonField("ImageIndex",
			jGenerator -> jGenerator.writeNumberField("ImageIndex", imageIndex),
			jParser -> imageIndex = jParser.getIntValue());
		
		setJsonField("ImageName", 
			jGenerator -> jGenerator.writeStringField("ImageName", imageName),
			jParser -> imageName = jParser.getText());
		
		setJsonField("ImageDescription", 
			jGenerator -> jGenerator.writeStringField("ImageDescription", imageDescription),
			jParser -> imageDescription = jParser.getText());
		
		setJsonField("ID", 
			jGenerator -> jGenerator.writeStringField("ID", id),
			jParser -> id = jParser.getText());
		
		setJsonField("PixelID", 
			jGenerator -> jGenerator.writeStringField("PixelID", pixelID),
			jParser -> pixelID = jParser.getText());
		
		setJsonField("Channels", 
			jGenerator -> {
				if (channels.size() > 0) {
					jGenerator.writeArrayFieldStart("Channels");
					for (Channel channel : channels)
						channel.toJSON(jGenerator);
					jGenerator.writeEndArray();
				}
		 	},
			jParser -> {
				while (jParser.nextToken() != JsonToken.END_ARRAY)
					channels.add(new Channel(jParser));
		 	});	
		
		setJsonField("DimensionOrder",
			jGenerator -> { 
				if (dimensionOrder != null)
					jGenerator.writeStringField("DimensionOrder", dimensionOrder.getValue());
			},
			jParser -> dimensionOrder = DimensionOrder.valueOf(jParser.getText()));
		
		setJsonField("SizeC",
				jGenerator -> { 
					if (sizeC != null)
						jGenerator.writeNumberField("SizeC", sizeC.getValue());
				},
				jParser -> sizeC = new PositiveInteger(jParser.getIntValue()));
		
		setJsonField("SizeT", 
				jGenerator -> {
					if (sizeT != null)
						jGenerator.writeNumberField("SizeT", sizeT.getValue());
				},
				jParser -> sizeT = new PositiveInteger(jParser.getIntValue()));
		
		setJsonField("SizeX",
				jGenerator -> {
					if (sizeX != null)
						jGenerator.writeNumberField("SizeX", sizeX.getValue());
				},
				jParser -> sizeX = new PositiveInteger(jParser.getIntValue()));
		
		setJsonField("SizeY",
				jGenerator -> {
					if (sizeY != null)
						jGenerator.writeNumberField("SizeY", sizeY.getValue());
				},
				jParser -> sizeY = new PositiveInteger(jParser.getIntValue()));
		
		setJsonField("SizeZ",
				jGenerator -> {
					if (sizeZ != null)
						jGenerator.writeNumberField("SizeZ", sizeZ.getValue());
				},
				jParser -> sizeZ = new PositiveInteger(jParser.getIntValue()));
		
		setJsonField("PixelsPhysicalSizeX",
			jGenerator -> {
				if (pixelsPhysicalSizeX != null) {
					jGenerator.writeObjectFieldStart("PixelsPhysicalSizeX");
					jGenerator.writeNumberField("value", pixelsPhysicalSizeX.value().doubleValue());
					jGenerator.writeStringField("units", pixelsPhysicalSizeX.unit().getSymbol());
					jGenerator.writeEndObject();
				}
			},
			jParser -> { 
				double value = Double.NaN;
				String units = "";
				while (jParser.nextToken() != JsonToken.END_OBJECT) {
		    		String subfieldname = jParser.getCurrentName();
		    		jParser.nextToken();
		    		if (subfieldname.equals("value"))
		    			value = jParser.getDoubleValue();
		    		
		    		if (subfieldname.equals("units"))
		    			units = jParser.getText();
		    	}
				try {
					pixelsPhysicalSizeX = new Length(value, UnitsLengthEnumHandler.getBaseUnit((UnitsLength) unitshandler.getEnumeration(units)));
				} catch (EnumerationException e) {
					e.printStackTrace();
				}
			});
		
		setJsonField("PixelsPhysicalSizeY",
				jGenerator -> {
					if (pixelsPhysicalSizeY != null) {
						jGenerator.writeObjectFieldStart("PixelsPhysicalSizeY");
						jGenerator.writeNumberField("value", pixelsPhysicalSizeY.value().doubleValue());
						jGenerator.writeStringField("units", pixelsPhysicalSizeY.unit().getSymbol());
						jGenerator.writeEndObject();
					}
				},
				jParser -> { 
					double value = Double.NaN;
					String units = "";
					while (jParser.nextToken() != JsonToken.END_OBJECT) {
			    		String subfieldname = jParser.getCurrentName();
			    		jParser.nextToken();
			    		if (subfieldname.equals("value"))
			    			value = jParser.getDoubleValue();
			    		
			    		if (subfieldname.equals("units"))
			    			units = jParser.getText();
			    	}
					try {
						pixelsPhysicalSizeY = new Length(value, UnitsLengthEnumHandler.getBaseUnit((UnitsLength) unitshandler.getEnumeration(units)));
					} catch (EnumerationException e) {
						e.printStackTrace();
					}
				});
		
		setJsonField("PixelsPhysicalSizeZ",
				jGenerator -> {
					if (pixelsPhysicalSizeZ != null) {
						jGenerator.writeObjectFieldStart("PixelsPhysicalSizeZ");
						jGenerator.writeNumberField("value", pixelsPhysicalSizeZ.value().doubleValue());
						jGenerator.writeStringField("units", pixelsPhysicalSizeZ.unit().getSymbol());
						jGenerator.writeEndObject();
					}
				},
				jParser -> { 
					double value = Double.NaN;
					String units = "";
					while (jParser.nextToken() != JsonToken.END_OBJECT) {
			    		String subfieldname = jParser.getCurrentName();
			    		jParser.nextToken();
			    		if (subfieldname.equals("value"))
			    			value = jParser.getDoubleValue();
			    		
			    		if (subfieldname.equals("units"))
			    			units = jParser.getText();
			    	}
					try {
						pixelsPhysicalSizeZ = new Length(value, UnitsLengthEnumHandler.getBaseUnit((UnitsLength) unitshandler.getEnumeration(units)));
					} catch (EnumerationException e) {
						e.printStackTrace();
					}
				});
		
		setJsonField("DetectorSerialNumber", 
				jGenerator -> jGenerator.writeStringField("DetectorSerialNumber", detectorSerialNumber),
				jParser -> detectorSerialNumber = jParser.getText());
		
		setJsonField("DetectorModel", 
				jGenerator -> jGenerator.writeStringField("DetectorModel", detectorModel),
				jParser -> detectorModel = jParser.getText());
		
		setJsonField("DetectorManufacturer", 
				jGenerator -> jGenerator.writeStringField("DetectorManufacturer", detectorManufacturer),
				jParser -> detectorManufacturer = jParser.getText());
		
		setJsonField("DetectorType", 
				jGenerator -> {
					if (detectorType != null)
						jGenerator.writeStringField("DetectorType", detectorType.getValue());
				},
				jParser -> {
					DetectorTypeEnumHandler handler = new DetectorTypeEnumHandler();
					try {
						detectorType = (DetectorType) handler.getEnumeration(jParser.getText());
					} catch (EnumerationException e) {
						e.printStackTrace();
					}
				});

		setJsonField("Temperature",
				jGenerator -> {
					if (temperature != null) {
						jGenerator.writeObjectFieldStart("Temperature");
						jGenerator.writeNumberField("value", temperature.value().doubleValue());
						jGenerator.writeStringField("units", temperature.unit().getSymbol());
						jGenerator.writeEndObject();
					}
				},
				jParser -> { 
					double value = Double.NaN;
					String units = "";
					while (jParser.nextToken() != JsonToken.END_OBJECT) {
			    		String subfieldname = jParser.getCurrentName();
			    		jParser.nextToken();
			    		if (subfieldname.equals("value"))
			    			value = jParser.getDoubleValue();
			    		
			    		if (subfieldname.equals("units"))
			    			units = jParser.getText();
			    	}
					UnitsTemperatureEnumHandler handler = new UnitsTemperatureEnumHandler();
					try {
						temperature = new Temperature(value, UnitsTemperatureEnumHandler.getBaseUnit((UnitsTemperature) handler.getEnumeration(units)));
					} catch (EnumerationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				});			
		
		setJsonField("Planes", 
			jGenerator -> {
				if (marsOMEPlanes.size() > 0) {
					jGenerator.writeArrayFieldStart("Planes");
					for (MarsOMEPlane plane : marsOMEPlanes.values())
						plane.toJSON(jGenerator);
					jGenerator.writeEndArray();
				}
		 	},
			jParser -> {
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					MarsOMEPlane plane = new MarsOMEPlane(jParser, this);
					marsOMEPlanes.put(plane.getPlaneIndex(), plane);
				}
		 	});	
		
	}
	
	@Override
	public String toString() {
		return "Image : " + this.imageName;
	}

	@Override
	public Iterable<List<String>> getInformationsRow() {
		List<List<String>> rows = new ArrayList<>();

		rows.add(Arrays.asList("Name", this.imageName));
		rows.add(Arrays.asList("ID", this.id));
		rows.add(Arrays.asList("Pixel ID", this.pixelID));

		if (this.pixelsPhysicalSizeX.value(UNITS.MICROMETER).doubleValue() > -1) {
			rows.add(Arrays.asList("Physical Size X", this.pixelsPhysicalSizeX.value(UNITS.MICROMETER).doubleValue() + " µm"));
		} else {
			rows.add(Arrays.asList("Physical Size X", ""));
		}

		if (this.pixelsPhysicalSizeY.value(UNITS.MICROMETER).doubleValue() > -1) {
			rows.add(Arrays.asList("Physical Size Y", this.pixelsPhysicalSizeY.value(UNITS.MICROMETER).doubleValue() + " µm"));
		} else {
			rows.add(Arrays.asList("Physical Size Y", ""));
		}

		if (this.pixelsPhysicalSizeZ.value(UNITS.MICROMETER).doubleValue() > -1) {
			rows.add(Arrays.asList("Physical Size Z", this.pixelsPhysicalSizeZ.value(UNITS.MICROMETER).doubleValue() + " µm"));
		} else {
			rows.add(Arrays.asList("Physical Size Z", ""));
		}

		/*
		if (this.timeIncrement > -1) {
			rows.add(Arrays.asList("Time Increment", this.timeIncrement + " s"));
		} else {
			rows.add(Arrays.asList("Time Increment", ""));
		}
*/
		rows.add(Arrays.asList("Size X", this.sizeX.toString()));
		rows.add(Arrays.asList("Size Y", this.sizeY.toString()));
		rows.add(Arrays.asList("Size Z", this.sizeZ.toString()));
		rows.add(Arrays.asList("Size Channel", this.sizeC.toString()));
		rows.add(Arrays.asList("Size Time", this.sizeT.toString()));

		for (int i = 0; i < channels.size(); i++) {
			Channel channel = channels.get(i);
			
			if (channel.getName() != null)
				rows.add(Arrays.asList("Channel " + Integer.toString(i) + " - name ", channel.getName()));
			if (channel.getID() != null)	
				rows.add(Arrays.asList("Channel " + Integer.toString(i) + " - id ", channel.getID()));
			if (channel.getBinning() != null)
				rows.add(Arrays.asList("Channel " + Integer.toString(i) + " - Binning ", channel.getBinning().getValue()));
			if (channel.getGain() != null)
				rows.add(Arrays.asList("Channel " + Integer.toString(i) + " - Gain ", String.valueOf(channel.getGain())));
			if (channel.getVoltage() != null)
				rows.add(Arrays.asList("Channel " + Integer.toString(i) + " - Voltage ", channel.getVoltage().value(UNITS.VOLT).doubleValue() + " V"));
			rows.add(Arrays.asList("Channel " + Integer.toString(i) + " - DetectorSettingID ", channel.getDetectorSettingID()));
		}
		
		//We should add customfields for images as well here.
		//for (String field : customFields.keySet())
		//	rows.add(Arrays.asList(field, customFields.get(field)));

		return rows;
	}
	
	public static class Channel extends AbstractJsonConvertibleRecord {
		private String name;
		private String id;
		private Binning binning;
		private Double gain;
		private ElectricPotential voltage;
		private String detectorSettingsID;
		
		Channel(OMEXMLMetadata md, int imageIndex, int channelIndex) {
			super();
			
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
		
		Channel(JsonParser jParser) throws IOException {
			super();
			fromJSON(jParser);
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
}

