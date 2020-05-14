/*******************************************************************************
 * Copyright (C) 2019, Duderstadt Lab
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package de.mpg.biochem.mars.metadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import org.scijava.Context;
import org.scijava.plugin.Parameter;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.metadata.MarsMicromanagerFormat.Position;
import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import de.mpg.biochem.mars.molecule.AbstractMarsRecord;
import de.mpg.biochem.mars.molecule.MarsBdvSource;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.MarsUtil;
import io.scif.common.DateTools;
import io.scif.io.Location;
import net.imagej.axis.Axes;
import net.imagej.axis.CalibratedAxis;
import net.imagej.axis.DefaultLinearAxis;
import ome.units.UNITS;
import ome.units.quantity.ElectricPotential;
import ome.units.quantity.Length;
import ome.units.quantity.Temperature;
import ome.units.unit.Unit;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.MapPair;
import ome.xml.model.enums.Binning;
import ome.xml.model.enums.DetectorType;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.handlers.BinningEnumHandler;
import ome.xml.model.enums.handlers.UnitsLengthEnumHandler;
import ome.xml.model.enums.handlers.UnitsTemperatureEnumHandler;
import ome.xml.model.primitives.Timestamp;

/**
 * Abstract superclass for storage of metadata, which includes all information
 * about specific data collections, imaging settings, frame timing. Mapping of frames/slices
 * to actual real time. These records can also include readouts from other instruments connected
 * to microscopes.
 * <p>
 * These records may also contain BigDataViewer registration coordinates, video locations, and channel names.
 * The log contained in these records will contain a history of commands run on this dataset and the molecule
 * records associated with it that are stored in the same {@link MoleculeArchive}. The {@link MarsTable} inherited
 * from {@link AbstractMarsRecord} will contain metadata for each frame in individual rows in order of collection.
 * </p>
 * <p>
 * This record format is designed for use with single-molecule time-series data collected on TIRF microscopes or bead
 * tracking microscopes. Therefore, these data are assumed to be 2D only. If individual colors are recorded in separate 
 * frames their information should be merged into a single row contained in the {@link MarsTable}.
 * </p>
 * @author Karl Duderstadt
 */
public abstract class AbstractMarsMetadata extends AbstractMarsRecord implements MarsMetadata {
	
	//Processing log for the record
	protected String log = "";

	protected String Microscope = "unknown";
	
	protected String instrumentID = "";
	
	//Directory where the images are stored..
	protected String SourceDirectory = "unknown";
	
	//Date and time when the data was collected...
	protected String CollectionDate = "unknown";
	
	protected Map<Integer, Image> images = new ConcurrentHashMap<Integer, Image>();
	
	//BDV views
	protected LinkedHashMap<String, MarsBdvSource> bdvSources = new LinkedHashMap<String, MarsBdvSource>();
    
    protected static JsonFactory jfactory = new JsonFactory();
    
    /**
	 * Constructor for creating an empty MarsMetadata record. 
	 */
    public AbstractMarsMetadata() {
    	super();
    }
    
    /**
	 * Constructor for creating an empty MarsMetadata record with the
	 * specified UID. 
	 * 
	 * @param UID The unique identifier for this record.
	 */
    public AbstractMarsMetadata(String UID) {
    	super(UID);
    }
    
    /**
	 * Constructor for creating a MarsMetadata record using OMEXMLMetadata.
	 * 
	 * @param OMEXMLMetadata The OMEXMLMetadata to use.
	 */
    public AbstractMarsMetadata(String UID, OMEXMLMetadata omexmlMetadata) {
    	super(UID);
    	populateMetadata(omexmlMetadata);
    }
	
    /**
	 * Constructor for loading a MarsMetadata record from a file. Typically,
	 * used when streaming records into memory when loading a {@link MoleculeArchive}
	 * or when a record is retrieved from the virtual store. 
	 * 
	 * @param jParser A JsonParser at the start of the record.
	 * @throws IOException Thrown if unable to parse Json from JsonParser stream.
	 */
	public AbstractMarsMetadata(JsonParser jParser) throws IOException {
		super();
		fromJSON(jParser);
	}
	
	public void populateMetadata(OMEXMLMetadata md) {
		instrumentID = md.getInstrumentID(0);
		
		for (int imageIndex = 0; imageIndex < md.getImageCount(); imageIndex++)
			images.put(imageIndex, new Image(imageIndex, md));
	}
	
	@Override
	protected void createIOMaps() {
		super.createIOMaps();

		setJsonField("Microscope",
			jGenerator -> {
				if(Microscope != null)
		  			jGenerator.writeStringField("Microscope", Microscope);
		 	},
			jParser -> Microscope = jParser.getText());
		
		setJsonField("SourceDirectory", 
			jGenerator -> {
		  		if (SourceDirectory != null)
		  			jGenerator.writeStringField("SourceDirectory", SourceDirectory);
			},
			jParser -> SourceDirectory = jParser.getText());
				
		setJsonField("CollectionDate", 
			jGenerator -> {
		  		if (CollectionDate != null)
		  			jGenerator.writeStringField("CollectionDate", CollectionDate);
	 		},
			jParser -> CollectionDate = jParser.getText());
						
		setJsonField("Log", 
			jGenerator -> {
		  		if (!log.equals("")) {
		  			jGenerator.writeStringField("Log", log);
		  		}
		 	}, 
			jParser -> log = jParser.getText());
		
		setJsonField("BdvSources", 
			jGenerator -> {
				if (bdvSources.size() > 0) {
					jGenerator.writeArrayFieldStart("BdvSources");
					for (MarsBdvSource source : bdvSources.values()) {
						source.toJSON(jGenerator);
					}
					jGenerator.writeEndArray();
				}
		 	}, 
			jParser -> {
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					MarsBdvSource source = new MarsBdvSource(jParser);
					bdvSources.put(source.getName(), source);
				}
			});
		
		setJsonField("Images", 
			jGenerator -> {
				jGenerator.writeArrayFieldStart("Images");
				for (int imageIndex=0; imageIndex<images.size(); imageIndex++) {
					images.get(imageIndex).toJSON(jGenerator);
				}
				jGenerator.writeEndArray();
		 	},
			jParser -> {
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					Image image = new Image(jParser);
					images.put(image.getImageIndex(), image);
				}
		 	});
		
	}
	
	/**
	 * Add or update the {@link MarsBdvSource} with the 
	 * name provided. All {@link MarsBdvSource} are unique
	 * so record will be overwritten if they have the same
	 * name.
	 */
	public void putBdvSource(MarsBdvSource source) {
		bdvSources.put(source.getName(), source);
	}
	
	/**
	 * Get the {@link MarsBdvSource} with the 
	 * name provided.
	 */
	public MarsBdvSource getBdvSource(String name) {
		return bdvSources.get(name);
	}
	
	/**
	 * Remove the {@link MarsBdvSource} with the 
	 * name provided.
	 */
	public void removeBdvSource(String name) {
		bdvSources.remove(name);
	}
	
	/**
	 * Get the Collection of BigDataViewer sources with 
	 * each in {@link MarsBdvSource} format.
	 */
	public Collection<MarsBdvSource> getBdvSources() {
		return bdvSources.values();
	}
	
	/**
	 * Get the set of BigDataViewer source names.
	 */
	public Set<String> getBdvSourceNames() {
		return bdvSources.keySet();
	}
	
	/**
	 * Check if this record contains the BigDataViewer
	 * with the name provided.
	 */
	public boolean hasBdvSource(String name) {
		return bdvSources.containsKey(name);
	}
  	
	/**
	 * Get the record in Json string format.
	 * 
	 * @return Json string representation of the MarsMetadata record.
	 */
  	public String toJSONString() {
  		ByteArrayOutputStream stream = new ByteArrayOutputStream();

  		JsonGenerator jGenerator;
  		try {
  			jGenerator = jfactory.createGenerator(stream, JsonEncoding.UTF8);
  			toJSON(jGenerator);
  			jGenerator.close();
  		} catch (IOException e) {
  			// TODO Auto-generated catch block
  			e.printStackTrace();
  		}
  		
  		return stream.toString();
  	}
    
  	/**
	 * Set the name of the microscope used for data collection.
	 * This is just for record keeping. There are no predefined
	 * setting based on microscope names.
	 */
	public void setMicroscopeName(String Microscope) {
		this.Microscope = Microscope;
	}
	
	/**
	 * Get the name of the microscope used for data collection.
	 * This is just for record keeping. There are no predefined
	 * setting based on microscope names.
	 */
	public String getMicroscopeName() {
		return Microscope;
	}
	
	/**
	 * Set the Date when these data were collected.
	 */
	public void setCollectionDate(String str) {
		CollectionDate = str;
	}
	
	/**
	 * Get the Date when these data were collected.
	 */
	public String getCollectionDate() {
		return CollectionDate;
	}
	
	/**
	 * Get the Source Directory where the images are stored.
	 */
	public String getSourceDirectory() {
		return SourceDirectory;
	}
	
	/**
	 * Set the Source Directory where the images are stored.
	 */
	public void setSourceDirectory(String path) {
		this.SourceDirectory = path;
	}
	
	/**
	 * Add to the log that contains the history of processing steps
	 * conducted on this dataset and the associated molecule records
	 * contained in the same {@link MoleculeArchive}. Start a new line
	 * after adding the message.
	 */
	public void logln(String str) {
		log += str + "\n";
	}
	
	/**
	 * Add to the log that contains the history of processing steps
	 * conducted on this dataset and the associated molecule records
	 * contained in the same {@link MoleculeArchive}. Do not start a 
	 * new line after adding the message.
	 */
	public void log(String str) {
		log += str;
	}
	
	/**
	 * Get the log that contains the history of processing steps
	 * conducted on this dataset and the associated molecule records
	 * contained in the same {@link MoleculeArchive}.
	 */
	public String getLog() {
		return log;
	}
	
	public MarsOMEPlane getPlane(int imageIndex, int planeIndex) {
		return images.get(imageIndex).getPlane(planeIndex);
	}
	
	public int getFrameCount(int imageIndex) {
		return images.get(imageIndex).getFrameCount();
	}
	
	private static class Image extends AbstractJsonConvertibleRecord {
		private Map<Integer, MarsOMEPlane> marsOMEPlanes = new ConcurrentHashMap<Integer, MarsOMEPlane>();
		
		private Timestamp imageAquisitionDate;
		private int imageIndex;
		private String imageName;
		private String imageDescription;
		private List<String> channelNames = new ArrayList<String>();
		private List<Binning> channelBinning = new ArrayList<Binning>();
		private List<Double> channelGain = new ArrayList<Double>();
		private List<ElectricPotential> channelVoltage = new ArrayList<ElectricPotential>();
		private List<String> channelDetectorSettingsID = new ArrayList<String>();
		
		private Length pixelsPhysicalSizeX, pixelsPhysicalSizeY, pixelsPhysicalSizeZ;
		
		private String detectorSerialNumber, detectorModel, detectorManufacturer;
		private Temperature temperature;
		private DetectorType detectorType;
		
		private int frames;
		
		Image(JsonParser jParser) throws IOException {
			super();
			fromJSON(jParser);
		}
		
		Image(int imageIndex, OMEXMLMetadata md) {
			this.imageIndex = imageIndex;
			imageAquisitionDate = md.getImageAcquisitionDate(imageIndex);
			imageName = md.getImageName(imageIndex);
			imageDescription = md.getImageDescription(imageIndex);
			
			for (int channelIndex=0; channelIndex < md.getChannelCount(imageIndex); channelIndex++) {
				channelNames.add(md.getChannelName(imageIndex, channelIndex));
				channelBinning.add(md.getDetectorSettingsBinning(imageIndex, channelIndex));
				channelGain.add(md.getDetectorSettingsGain(imageIndex, channelIndex));
				channelVoltage.add(md.getDetectorSettingsVoltage(imageIndex, channelIndex));
				channelDetectorSettingsID.add(md.getDetectorSettingsID(imageIndex, channelIndex));
			}
			
			pixelsPhysicalSizeX = md.getPixelsPhysicalSizeX(imageIndex);
			pixelsPhysicalSizeY = md.getPixelsPhysicalSizeY(imageIndex);
			pixelsPhysicalSizeZ = md.getPixelsPhysicalSizeZ(imageIndex);
			
			//Build Planes
			for (int planeIndex = 0; planeIndex < md.getPlaneCount(imageIndex); planeIndex++)
				marsOMEPlanes.put(planeIndex, new MarsOMEPlane(imageIndex, planeIndex, md));
			
			detectorSerialNumber = md.getDetectorSerialNumber(0, imageIndex);
			detectorModel = md.getDetectorModel(0, imageIndex);
			detectorManufacturer = md.getDetectorManufacturer(0, imageIndex);
			detectorType = md.getDetectorType(0, imageIndex);
			temperature = md.getImagingEnvironmentTemperature(imageIndex);
			
			//Build look-up from image/plane to MapAnnotation
			if (md.getMapAnnotationCount() > 0) {
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
		
		MarsOMEPlane getPlane(int planeIndex) {
			return marsOMEPlanes.get(planeIndex);
		}
		
		int getFrameCount() {
			return frames;
		}
		
		int getImageIndex() {
			return imageIndex;
		}

		@Override
		protected void createIOMaps() {
			
			UnitsLengthEnumHandler unitshandler = new UnitsLengthEnumHandler();
			
			setJsonField("ImageAcquisitionDate", 
				jGenerator -> jGenerator.writeStringField("ImageAcquisitionDate", imageAquisitionDate.toString()),
				jParser -> imageAquisitionDate = new Timestamp(jParser.getText()));
			
			setJsonField("ImageIndex",
				jGenerator -> jGenerator.writeNumberField("ImageIndex", imageIndex),
				jParser -> imageIndex = jParser.getIntValue());
			
			setJsonField("ImageName", 
				jGenerator -> jGenerator.writeStringField("ImageName", imageName),
				jParser -> imageName = jParser.getText());
			
			setJsonField("ImageDescription", 
				jGenerator -> jGenerator.writeStringField("ImageDescription", imageDescription),
				jParser -> imageDescription = jParser.getText());
			
			//Dimensions !!!!!!!!!!!!!!!!!!!!!!!
			
			
			
			setJsonField("T", 
				jGenerator -> jGenerator.writeStringField("T", String.valueOf(frames)),
				jParser -> frames = jParser.getIntValue());
			
			
			//!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			
			setJsonField("ChannelNames", 
				jGenerator -> {
				if (channelNames.size() > 0) {
					jGenerator.writeFieldName("ChannelNames");
					jGenerator.writeStartArray();
					Iterator<String> iterator = channelNames.iterator();
					while(iterator.hasNext())
						jGenerator.writeString(iterator.next());
					jGenerator.writeEndArray();
				}},
				jParser -> {
					while (jParser.nextToken() != JsonToken.END_ARRAY) {
						channelNames.add(jParser.getText());
					}
			 	});
				
			setJsonField("ChannelBinning", 
				jGenerator -> {
					if (channelBinning.size() > 0) {
						jGenerator.writeFieldName("ChannelBinning");
						jGenerator.writeStartArray();
						Iterator<Binning> iterator = channelBinning.iterator();
						while(iterator.hasNext())
							jGenerator.writeString(iterator.next().getValue());
						jGenerator.writeEndArray();
					}
				},
				jParser -> {
					while (jParser.nextToken() != JsonToken.END_ARRAY) {
						BinningEnumHandler handler = new BinningEnumHandler();
						try {
							channelBinning.add((Binning) handler.getEnumeration(jParser.getText()));
						} catch (EnumerationException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
			 	});
			
			setJsonField("ChannelGain", 
					jGenerator -> {
						if (channelGain.size() > 0) {
							jGenerator.writeFieldName("ChannelGain");
							jGenerator.writeStartArray();
							Iterator<Double> iterator = channelGain.iterator();
							while(iterator.hasNext())
								jGenerator.writeString(iterator.next().toString());
							jGenerator.writeEndArray();
						}
					},
					jParser -> {
						while (jParser.nextToken() != JsonToken.END_ARRAY) {
							channelGain.add(jParser.getDoubleValue());
						}
				 	});
			
			setJsonField("ChannelVoltage", 
					jGenerator -> {
						if (channelVoltage.size() > 0) {
							jGenerator.writeFieldName("ChannelVoltage");
							jGenerator.writeStartArray();
							Iterator<ElectricPotential> iterator = channelVoltage.iterator();
							while(iterator.hasNext())
								jGenerator.writeString(iterator.next().value().toString());
							jGenerator.writeEndArray();
						}
					},
					jParser -> {
						while (jParser.nextToken() != JsonToken.END_ARRAY) {
							channelVoltage.add(new ElectricPotential(jParser.getNumberValue(), UNITS.VOLT));
						}
				 	});
			
			setJsonField("ChannelDetectorSettingsID", 
					jGenerator -> {
						if (channelDetectorSettingsID.size() > 0) {
							jGenerator.writeFieldName("ChannelDetectorSettingsID");
							jGenerator.writeStartArray();
							Iterator<String> iterator = channelDetectorSettingsID.iterator();
							while(iterator.hasNext())
								jGenerator.writeString(iterator.next());
							jGenerator.writeEndArray();
						}
					},
					jParser -> {
						while (jParser.nextToken() != JsonToken.END_ARRAY) {
							channelDetectorSettingsID.add(jParser.getText());
						}
				 	});
			
			setJsonField("PixelsPhysicalSizeX",
				jGenerator -> {
					jGenerator.writeObjectFieldStart("PixelsPhysicalSizeX");
					jGenerator.writeNumberField("value", pixelsPhysicalSizeX.value().doubleValue());
					jGenerator.writeStringField("units", pixelsPhysicalSizeX.unit().toString());
					jGenerator.writeEndObject();
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
						pixelsPhysicalSizeX = new Length(value, (Unit<Length>) unitshandler.getEnumeration(units));
					} catch (EnumerationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				});
			
			setJsonField("PixelsPhysicalSizeY",
					jGenerator -> {
						jGenerator.writeObjectFieldStart("PixelsPhysicalSizeY");
						jGenerator.writeNumberField("value", pixelsPhysicalSizeY.value().doubleValue());
						jGenerator.writeStringField("units", pixelsPhysicalSizeY.unit().toString());
						jGenerator.writeEndObject();
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
							pixelsPhysicalSizeY = new Length(value, (Unit<Length>) unitshandler.getEnumeration(units));
						} catch (EnumerationException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					});
			
			setJsonField("PixelsPhysicalSizeZ",
					jGenerator -> {
						jGenerator.writeObjectFieldStart("PixelsPhysicalSizeZ");
						jGenerator.writeNumberField("value", pixelsPhysicalSizeZ.value().doubleValue());
						jGenerator.writeStringField("units", pixelsPhysicalSizeZ.unit().toString());
						jGenerator.writeEndObject();
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
							pixelsPhysicalSizeZ = new Length(value, (Unit<Length>) unitshandler.getEnumeration(units));
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
					jGenerator -> jGenerator.writeStringField("DetectorType", detectorType.getValue()),
					jParser -> detectorType = DetectorType.valueOf(jParser.getText()));

			setJsonField("Temperature",
					jGenerator -> {
						jGenerator.writeObjectFieldStart("Temperature");
						jGenerator.writeNumberField("value", temperature.value().doubleValue());
						jGenerator.writeStringField("units", temperature.unit().toString());
						jGenerator.writeEndObject();
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
							temperature = new Temperature(value, (Unit<Temperature>) handler.getEnumeration(units));
						} catch (EnumerationException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					});			
			
			setJsonField("Planes", 
				jGenerator -> {
					if (marsOMEPlanes.size() > 0) {
						jGenerator.writeArrayFieldStart("Planes");
						for (MarsOMEPlane plane : marsOMEPlanes.values()) {
							plane.toJSON(jGenerator);
						}
						jGenerator.writeEndArray();
					}
			 	},
				jParser -> {
					while (jParser.nextToken() != JsonToken.END_ARRAY) {
						MarsOMEPlane plane = new MarsOMEPlane(jParser);
						marsOMEPlanes.put(plane.getPlaneIndex(), plane);
					}
			 	});	
			
		}
	}
}
