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
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;

import org.scijava.Context;
import org.scijava.plugin.Parameter;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.AbstractMarsRecord;
import de.mpg.biochem.mars.molecule.MarsBdvSource;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.MarsUtil;
import io.scif.ome.services.OMEMetadataService;
import io.scif.ome.services.OMEXMLService;
import loci.common.services.ServiceException;
import ome.units.UNITS;
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
	
	@Parameter
	private OMEXMLService omexmlService;
	
	//Processing log for the record
	protected String log = "";
	
	protected OMEXMLMetadata store;

	protected String Microscope = "unknown";
	
	//Directory where the images are stored..
	protected String SourceDirectory = "unknown";
	
	//Date and time when the data was collected...
	protected String CollectionDate = "unknown";
	
	//BDV views
	protected LinkedHashMap<String, MarsBdvSource> bdvSources = new LinkedHashMap<String, MarsBdvSource>();
    
    protected static JsonFactory jfactory = new JsonFactory();
    
    /**
	 * Constructor for creating an empty MarsMetadata record. 
	 */
    public AbstractMarsMetadata(final Context context) {
    	super();
    	context.inject(this);
    }
    
    /**
	 * Constructor for creating an empty MarsMetadata record with the
	 * specified UID. 
	 * 
	 * @param UID The unique identifier for this record.
	 */
    public AbstractMarsMetadata(final Context context, String UID) {
    	super(UID);
    	context.inject(this);
    }
    
    public AbstractMarsMetadata(final Context context, OMEXMLMetadata store) {
    	super();
    	context.inject(this);
    	this.store = store;
    }
	
    /**
	 * Constructor for loading a MarsMetadata record from a file. Typically,
	 * used when streaming records into memory when loading a {@link MoleculeArchive}
	 * or when a record is retrieved from the virtual store. 
	 * 
	 * @param jParser A JsonParser at the start of the record.
	 * @throws IOException Thrown if unable to parse Json from JsonParser stream.
	 */
	public AbstractMarsMetadata(final Context context, JsonParser jParser) throws IOException {
		super();
		context.inject(this);
		fromJSON(jParser);
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
		outputMap.put("OMEXMLMetadataDump", MarsUtil.catchConsumerException(jGenerator -> {
			if(Microscope != null)
	  			jGenerator.writeStringField("OMEXMLMetadataDump", store.dumpXML());
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
		inputMap.put("OMEXMLMetadataDump", MarsUtil.catchConsumerException(jParser -> {
	        try {
	    	   store = omexmlService.createOMEXMLMetadata(jParser.getText());
			} catch (ServiceException e) {
				e.printStackTrace();
			}
		}, IOException.class));
	}
	
	public int getFrameCount() {
		return store.getTiffDataCount(0);
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
	 * contained in the same {@link MoleculeArchive}.
	 */
	@Deprecated
	public void addLogMessage(String str) {
		log += str + "\n";
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

	@Override
	public OMEXMLMetadata getOMEXMLMetadata() {
		return store;
	}
	
	public double getDeltaT(int z, int c, int t) {
		return getDeltaT(1, t);
	}
	
	public double getDeltaT(int imageIndex, int planeIndex) {
		return store.getPlaneDeltaT(imageIndex, planeIndex).value(UNITS.SECOND).doubleValue();
	}
}
