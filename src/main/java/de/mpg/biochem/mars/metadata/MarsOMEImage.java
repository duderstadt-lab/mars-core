package de.mpg.biochem.mars.metadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import de.mpg.biochem.mars.molecule.JsonConvertibleRecord;
import ome.units.UNITS;
import ome.units.quantity.ElectricPotential;
import ome.units.quantity.Length;
import ome.units.quantity.Temperature;
import ome.units.quantity.Time;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.MapPair;
import ome.xml.model.enums.Binning;
import ome.xml.model.enums.DetectorType;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.UnitsLength;
import ome.xml.model.enums.UnitsTemperature;
import ome.xml.model.enums.UnitsTime;
import ome.xml.model.enums.handlers.BinningEnumHandler;
import ome.xml.model.enums.handlers.UnitsLengthEnumHandler;
import ome.xml.model.enums.handlers.UnitsTemperatureEnumHandler;
import ome.xml.model.enums.handlers.UnitsTimeEnumHandler;
import ome.xml.model.enums.handlers.DetectorTypeEnumHandler;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.Timestamp;
import ome.xml.meta.OMEXMLMetadataRoot;
import io.scif.util.FormatTools;

public class MarsOMEImage extends AbstractJsonConvertibleRecord implements GenericModel, JsonConvertibleRecord {
	private Map<Integer, MarsOMEPlane> marsOMEPlanes = new LinkedHashMap<Integer, MarsOMEPlane>();
	
	private String id;
	private String pixelID;
	
	private Time timeIncrement;
	private Timestamp imageAquisitionDate;
	private int imageIndex;
	private String imageName;
	private String imageDescription;
	private Map<Integer, MarsOMEChannel> channels = new LinkedHashMap<Integer, MarsOMEChannel>();
	
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
	
	private Map<String, String> stringFields = new LinkedHashMap<String, String>();
	private Map<String, Double> valueFields = new LinkedHashMap<String, Double>();
	
	public MarsOMEImage() {
		super();
	}
	
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
			channels.put(channelIndex, new MarsOMEChannel(md, imageIndex, channelIndex));
		
		pixelsPhysicalSizeX = md.getPixelsPhysicalSizeX(imageIndex);
		pixelsPhysicalSizeY = md.getPixelsPhysicalSizeY(imageIndex);
		pixelsPhysicalSizeZ = md.getPixelsPhysicalSizeZ(imageIndex);
		
		if (md.getPixelsTimeIncrement(imageIndex) != null)
			timeIncrement = md.getPixelsTimeIncrement(imageIndex);
		
		dimensionOrder = md.getPixelsDimensionOrder(imageIndex);
		
		this.sizeC = md.getPixelsSizeC(imageIndex);
		this.sizeT = md.getPixelsSizeT(imageIndex);
		this.sizeX = md.getPixelsSizeX(imageIndex);
		this.sizeY = md.getPixelsSizeY(imageIndex);
		this.sizeZ = md.getPixelsSizeZ(imageIndex);
		
		//Build Planes
		for (int planeIndex = 0; planeIndex < md.getPlaneCount(imageIndex); planeIndex++)
			if (md.getPlaneTheT(imageIndex, planeIndex) != null)
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
					
					if (marsOMEPlanes.containsKey(planeIndex))
						marsOMEPlanes.get(planeIndex).setStringFields(fieldsMap);
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
	public long getPlaneIndex(int z, int c, int t) {
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
	
	public void setID(String id) {
		this.id = id;
	}

	public String getID() {
		return id;
	}
	
	public void setPixelID(String pixelID) {
		this.pixelID = pixelID;
	}
	
	public String getPixelID() {
		return pixelID;
	}
	
	public void setAquisitionDate(Timestamp imageAquisitionDate) {
		this.imageAquisitionDate = imageAquisitionDate;
	}
	
	public Timestamp getAquisitionDate() {
		return imageAquisitionDate;
	}
	
	public void setName(String imageName) {
		this.imageName = imageName;
	}
	
	public String getName() {
		return imageName;
	}
	
	public void setDescription(String imageDescription) {
		this.imageDescription = imageDescription;
	}
	
	public String getDescription() {
		return imageDescription;
	}
	
	public void setChannel(MarsOMEChannel channel, int channelIndex) {
		channels.put(channelIndex, channel);
	}
	
	public MarsOMEChannel getChannel(int channelIndex) {
		return channels.get(channelIndex);
	}
	
	public  Map<Integer, MarsOMEChannel> getChannels() {
		return channels;
	}
	
	public Stream<MarsOMEChannel> channels() {
		return channels.values().stream();
	}
	
	public void setPixelsPhysicalSizeX(Length pixelsPhysicalSizeX) {
		this.pixelsPhysicalSizeX = pixelsPhysicalSizeX;
	}
	
	public Length getPixelsPhysicalSizeX() {
		return pixelsPhysicalSizeX;
	}
	
	public Length getPixelsPhysicalSizeY() {
		return pixelsPhysicalSizeY;
	}
	
	public void setPixelsPhysicalSizeY(Length pixelsPhysicalSizeY) {
		this.pixelsPhysicalSizeY = pixelsPhysicalSizeY;
	}
	
	public Length getPixelsPhysicalSizeZ() {
		return pixelsPhysicalSizeZ;
	}
	
	public void setPixelsPhysicalSizeZ(Length pixelsPhysicalSizeZ) {
		this.pixelsPhysicalSizeZ = pixelsPhysicalSizeZ;
	}
	
	public void setTimeIncrementInSeconds(double timeIncrementSeconds) {
		this.timeIncrement = new Time(timeIncrementSeconds, UNITS.SECOND);
	}
	
	public double getTimeIncrementInSeconds() {
		if (timeIncrement != null)
			return timeIncrement.value(UNITS.SECOND).doubleValue();
		else
			return -1;
	}
	
	public void setDetectorSerialNumber(String detectorSerialNumber) {
		this.detectorSerialNumber = detectorSerialNumber;
	}
	
	public String getDetectorSerialNumber() {
		return detectorSerialNumber;
	}
	
	public void setDetectorModel(String detectorModel) {
		this.detectorModel = detectorModel;
	}
	
	public String getDetectorModel() {
		return detectorModel;
	}
	
	public void setDetectorManufacturer(String detectorManufacturer) {
		this.detectorManufacturer = detectorManufacturer;
	}
	
	public String getDetectorManufacturer() {
		return detectorManufacturer;
	}
	
	public void setTemperature(Temperature temperature) {
		this.temperature = temperature;
	}
	
	public Temperature getTemperature() {
		return temperature;
	}
	
	public void setDetectorType(DetectorType detectorType) {
		this.detectorType = detectorType;
	}
	
	public DetectorType getDetectorType() {
		return detectorType;
	}
	
	public int getPlaneCount() {
		return marsOMEPlanes.size();
	}
	
	public void setPlane(MarsOMEPlane plane, int planeIndex) {
		marsOMEPlanes.put(planeIndex, plane);
	}
	
	public void setPlane(MarsOMEPlane plane, int z, int c, int t) {
		marsOMEPlanes.put((int) getPlaneIndex(z, c, t), plane);
	}
	
	public MarsOMEPlane getPlane(int planeIndex) {
		return marsOMEPlanes.get(planeIndex);
	}
	
	public MarsOMEPlane getPlane(int z, int c, int t) {
		return getPlane((int) getPlaneIndex(z, c, t));
	}
	
	public  Map<Integer, MarsOMEPlane> getPlanes() {
		return marsOMEPlanes;
	}
	
	public boolean hasPlane(int planeIndex) {
		return marsOMEPlanes.containsKey(planeIndex);
	}
	
	public boolean hasPlane(int z, int c, int t) {
		return marsOMEPlanes.containsKey((int) getPlaneIndex(z, c, t));
	}
	
	public Stream<MarsOMEPlane> planes() {
		return marsOMEPlanes.values().stream();
	}
	
	public void setDimensionOrder(DimensionOrder dimensionOrder) {
		this.dimensionOrder = dimensionOrder;
	}
	
	public DimensionOrder getDimensionOrder() {
		return dimensionOrder;
	}
	
	public void setSizeC(PositiveInteger sizeC) {
		this.sizeC = sizeC;
	}
	
	public int getSizeC() {
		return sizeC.getValue();
	}
	
	public void setSizeT(PositiveInteger sizeT) {
		this.sizeT = sizeT;
	}
	
	public int getSizeT() {
		return sizeT.getValue();
	}
	
	public void setSizeX(PositiveInteger sizeX) {
		this.sizeX = sizeX;
	}
	
	public int getSizeX() {
		return sizeX.getValue();
	}
	
	public void setSizeY(PositiveInteger sizeY) {
		this.sizeY = sizeY;
	}
	
	public int getSizeY() {
		return sizeY.getValue();
	}
	
	public void setSizeZ(PositiveInteger sizeZ) {
		this.sizeZ = sizeZ;
	}
	
	public int getSizeZ() {
		return sizeZ.getValue();
	}
	
	public void setImageIndex(int imageIndex) {
		this.imageIndex = imageIndex;
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
						for (MarsOMEChannel channel : channels.values())
							channel.toJSON(jGenerator);
						jGenerator.writeEndArray();
					}
			 	},
				jParser -> {
					while (jParser.nextToken() != JsonToken.END_ARRAY) {
						MarsOMEChannel channel = new MarsOMEChannel(jParser);
						channels.put(channel.getChannelIndex(), channel);
					}
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
		
		setJsonField("TimeIncrement",
				jGenerator -> {
					if (timeIncrement != null) {
						jGenerator.writeObjectFieldStart("TimeIncrement");
						jGenerator.writeNumberField("value", timeIncrement.value().doubleValue());
						jGenerator.writeStringField("units", timeIncrement.unit().getSymbol());
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
						UnitsTimeEnumHandler timehandler = new UnitsTimeEnumHandler();
						timeIncrement = new Time(value, UnitsTimeEnumHandler.getBaseUnit((UnitsTime) timehandler.getEnumeration(units)));
					} catch (EnumerationException e) {
						// TODO Auto-generated catch block
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
		
		setJsonField("StringFields", 
		 		jGenerator -> {
					if (stringFields.size() > 0) {
						jGenerator.writeObjectFieldStart("StringFields");
						for (String name : stringFields.keySet())
							jGenerator.writeStringField(name, stringFields.get(name));
						jGenerator.writeEndObject();
					}
			 	}, 
		 		jParser -> {
					while (jParser.nextToken() != JsonToken.END_OBJECT) {
			    		String fieldname = jParser.getCurrentName();
			    		jParser.nextToken();
			    		stringFields.put(fieldname, jParser.getText());
					}
		 		});
		 	
	 	setJsonField("ValueFields", 
		 		jGenerator -> {
					if (stringFields.size() > 0) {
						jGenerator.writeObjectFieldStart("ValueFields");
						for (String name : valueFields.keySet())
							jGenerator.writeNumberField(name, valueFields.get(name));
						jGenerator.writeEndObject();
					}
			 	}, 
		 		jParser -> {
					while (jParser.nextToken() != JsonToken.END_OBJECT) {
			    		String fieldname = jParser.getCurrentName();
			    		jParser.nextToken();
			    		valueFields.put(fieldname, jParser.getDoubleValue());
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

		if (this.pixelsPhysicalSizeX != null) {
			rows.add(Arrays.asList("Physical Size X", this.pixelsPhysicalSizeX.value().doubleValue() + " " + pixelsPhysicalSizeX.unit().getSymbol()));
		} else {
			rows.add(Arrays.asList("Physical Size X", ""));
		}

		if (this.pixelsPhysicalSizeY != null) {
			rows.add(Arrays.asList("Physical Size Y", this.pixelsPhysicalSizeY.value().doubleValue() + " " + pixelsPhysicalSizeY.unit().getSymbol()));
		} else {
			rows.add(Arrays.asList("Physical Size Y", ""));
		}

		if (this.pixelsPhysicalSizeZ != null) {
			rows.add(Arrays.asList("Physical Size Z", this.pixelsPhysicalSizeZ.value().doubleValue() + " " + pixelsPhysicalSizeZ.unit().getSymbol()));
		} else {
			rows.add(Arrays.asList("Physical Size Z", ""));
		}
		
		if (this.timeIncrement != null) {
			rows.add(Arrays.asList("Time Increment", this.timeIncrement.value().doubleValue() + " " + timeIncrement.unit().getSymbol()));
		} else {
			rows.add(Arrays.asList("Time Increment", ""));
		}

		if (sizeX != null)
			rows.add(Arrays.asList("Size X", this.sizeX.toString()));
		if (sizeY != null)
			rows.add(Arrays.asList("Size Y", this.sizeY.toString()));
		if (sizeZ != null)
			rows.add(Arrays.asList("Size Z", this.sizeZ.toString()));
		if (sizeC != null)
			rows.add(Arrays.asList("Size C", this.sizeC.toString()));
		if (sizeT != null)
			rows.add(Arrays.asList("Size T", this.sizeT.toString()));

		
		for (MarsOMEChannel channel : channels.values()) {
			int channelIndex = channel.getChannelIndex();
			
			if (channel.getName() != null)
				rows.add(Arrays.asList("Channel " + channelIndex + " - name ", channel.getName()));
			if (channel.getID() != null)	
				rows.add(Arrays.asList("Channel " + channelIndex + " - id ", channel.getID()));
			if (channel.getBinning() != null)
				rows.add(Arrays.asList("Channel " + channelIndex + " - Binning ", channel.getBinning().getValue()));
			if (channel.getGain() != null)
				rows.add(Arrays.asList("Channel " + channelIndex + " - Gain ", String.valueOf(channel.getGain())));
			if (channel.getVoltage() != null)
				rows.add(Arrays.asList("Channel " + channelIndex + " - Voltage ", channel.getVoltage().value(UNITS.VOLT).doubleValue() + " V"));
			rows.add(Arrays.asList("Channel " + channelIndex + " - DetectorSettingID ", channel.getDetectorSettingID()));
		}
		
		for (String field : stringFields.keySet())
			rows.add(Arrays.asList(field, stringFields.get(field)));
		
		for (String field : valueFields.keySet())
			rows.add(Arrays.asList(field, String.valueOf(valueFields.get(field))));

		return rows;
	}
}

