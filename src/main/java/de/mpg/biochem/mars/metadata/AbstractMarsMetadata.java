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
import ome.units.quantity.Time;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.MapPair;
import ome.xml.model.enums.DetectorType;
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
		
		//Add to output map
		outputMap.put("Microscope", MarsUtil.catchConsumerException(jGenerator -> {
			if(Microscope != null)
	  			jGenerator.writeStringField("Microscope", Microscope);
	 	}, IOException.class));
		outputMap.put("SourceDirectory", MarsUtil.catchConsumerException(jGenerator -> {
	  		if (SourceDirectory != null)
	  			jGenerator.writeStringField("SourceDirectory", SourceDirectory);
	 	}, IOException.class));
		outputMap.put("CollectionDate", MarsUtil.catchConsumerException(jGenerator -> {
	  		if (CollectionDate != null)
	  			jGenerator.writeStringField("CollectionDate", CollectionDate);
	 	}, IOException.class));
		outputMap.put("Log", MarsUtil.catchConsumerException(jGenerator -> {
	  		if (!log.equals("")) {
	  			jGenerator.writeStringField("Log", log);
	  		}
	 	}, IOException.class));
		outputMap.put("BdvSources", MarsUtil.catchConsumerException(jGenerator -> {
			if (bdvSources.size() > 0) {
				jGenerator.writeArrayFieldStart("BdvSources");
				for (MarsBdvSource source : bdvSources.values()) {
					source.toJSON(jGenerator);
				}
				jGenerator.writeEndArray();
			}
	 	}, IOException.class));
		
		
		//Add to input map
		inputMap.put("Microscope", MarsUtil.catchConsumerException(jParser -> {
	    	Microscope = jParser.getText();
		}, IOException.class));
		inputMap.put("SourceDirectory", MarsUtil.catchConsumerException(jParser -> {
	    	SourceDirectory = jParser.getText();
		}, IOException.class));
		inputMap.put("CollectionDate", MarsUtil.catchConsumerException(jParser -> {
	    	CollectionDate = jParser.getText();
		}, IOException.class));
		inputMap.put("Log", MarsUtil.catchConsumerException(jParser -> {
	    	log = jParser.getText();
		}, IOException.class));
		inputMap.put("BdvSources", MarsUtil.catchConsumerException(jParser -> {
			while (jParser.nextToken() != JsonToken.END_ARRAY) {
				MarsBdvSource source = new MarsBdvSource(jParser);
				bdvSources.put(source.getName(), source);
			}
		}, IOException.class));
		
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
		private String imageName;
		private String imageDescription;
		private List<String> channelNames = new ArrayList<String>();
		private List<String> channelBinning = new ArrayList<String>();
		private List<String> channelGain = new ArrayList<String>();
		private List<ElectricPotential> channelVoltage = new ArrayList<ElectricPotential>();
		
		private Length pixelsPhysicalSizeX, pixelsPhysicalSizeY, pixelsPhysicalSizeZ;
		
		private String detectorSerialNumber, detectorModel, detectorManufacturer;
		private Temperature temperature;
		private DetectorType detectorType;
		
		private int frames;
		
		Image(int imageIndex, OMEXMLMetadata md) {
			
			imageAquisitionDate = md.getImageAcquisitionDate(imageIndex);
			imageName = md.getImageName(imageIndex);
			imageDescription = md.getImageDescription(imageIndex);
			
			for (int channelIndex=0; channelIndex < md.getChannelCount(imageIndex); channelIndex++) {
				channelNames.add(md.getChannelName(imageIndex, channelIndex));
				md.getDetectorSettingsBinning(imageIndex, channelIndex);
				md.getDetectorSettingsGain(imageIndex, channelIndex);
				md.getDetectorSettingsVoltage(imageIndex, channelIndex);
				md.getDetectorSettingsID(imageIndex, channelIndex);
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
				Map<Integer, List<MapPair>> planeToMapAnnotation = new HashMap<Integer, List<MapPair>>();
				for (int i=0; i<md.getMapAnnotationCount(); i++) {
					String[] strList = md.getMapAnnotationID(i).split("-");
					int iIndex = Integer.valueOf(strList[1]);
					int pIndex = Integer.valueOf(strList[2]);
					
					if (iIndex == imageIndex) 
						planeToMapAnnotation.put(pIndex, md.getMapAnnotationValue(i));
				}
				
				for (int planeIndex = 0; planeIndex < md.getPlaneCount(imageIndex); planeIndex++)
					marsOMEPlanes.get(planeIndex).setCustomFields();
			}
		}
		
		MarsOMEPlane getPlane(int planeIndex) {
			marsOMEPlanes.get(planeIndex);
		}
		
		int getFrameCount() {
			return frames;
		}

		@Override
		protected void createIOMaps() {
			outputMap.put("ImageAcquisitionDate", MarsUtil.catchConsumerException(jGenerator -> {
				jGenerator.writeStringField("ImageAcquisitionDate", imageAcquisitionDate);
		 	}, IOException.class));
			
			outputMap.put("Planes", MarsUtil.catchConsumerException(jGenerator -> {
				if (marsOMEPlanes.size() > 0) {
					jGenerator.writeArrayFieldStart("Planes");
					for (MarsOMEPlane plane : marsOMEPlanes.values()) {
						plane.toJSON(jGenerator);
					}
					jGenerator.writeEndArray();
				}
		 	}, IOException.class));
			
			inputMap.put("Planes", MarsUtil.catchConsumerException(jParser -> {
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					MarsOMEPlane plane = new MarsOMEPlane(jParser);
					marsOMEPlanes.put(plane.getIndex(), plane);
				}
		 	}, IOException.class));
		}
	}
}
