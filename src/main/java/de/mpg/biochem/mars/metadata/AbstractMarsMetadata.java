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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.AbstractMarsRecord;
import de.mpg.biochem.mars.molecule.MarsBdvSource;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;
import ome.xml.meta.OMEXMLMetadata;

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
	
	//Directory where the images are stored..
	protected String SourceDirectory = "unknown";
	
	protected Map<Integer, MarsOMEImage> images = new ConcurrentHashMap<Integer, MarsOMEImage>();
	
	//BDV views
	protected LinkedHashMap<String, MarsBdvSource> bdvSources = new LinkedHashMap<String, MarsBdvSource>();
    
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
		for (int imageIndex = 0; imageIndex < md.getImageCount(); imageIndex++)
			images.put(imageIndex, new MarsOMEImage(imageIndex, md));
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
				for (int imageIndex=0; imageIndex<images.size(); imageIndex++)
					images.get(imageIndex).toJSON(jGenerator);
				jGenerator.writeEndArray();
		 	},
			jParser -> {
				System.out.println("Parsing Images");
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					MarsOMEImage image = new MarsOMEImage(jParser);
					System.out.println("Done parsing image");
					
					if (images == null)
						System.out.println("images null");
					
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
	
	public void setImage(MarsOMEImage image, int imageIndex) {
		images.put(imageIndex, image);
	}
	
	public MarsOMEImage getImage(int imageIndex) {
		return images.get(imageIndex);
	}
	
	public int getImageCount() {
		return images.size();
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
	
	/**
	 * Get the MarsOMEPlane for a given image and ZCT position.
	 * If there are multiple planes for a given position, this
	 * will return the plane with the highest value in the plane
	 * index. Therefore, if there are multiple planes per position,
	 * the getPlane(int imageIndex, int planeIndex) should be used.
	 * 
	 * @param imageIndex Index of the image.
	 * @param Z Z (slice).
	 * @param C Channel.
	 * @param T Time point.
	 */
	public MarsOMEPlane getPlane(int imageIndex, int z, int c, int t) {
		return images.get(imageIndex).getPlane(z, c, t);
	}
	
	public boolean hasPlane(int imageIndex, int planeIndex) {
		return images.get(imageIndex).hasPlane(planeIndex);
	}
	
	public MarsOMEPlane getPlane(int imageIndex, int planeIndex) {
		return images.get(imageIndex).getPlane(planeIndex);
	}
	
	public String getCollectionDate() {
		return images.get(0).getAquisitionDate().getValue();
	}
}
