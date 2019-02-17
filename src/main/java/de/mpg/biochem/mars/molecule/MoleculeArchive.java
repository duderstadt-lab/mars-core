/*******************************************************************************
 * MARS - MoleculeArchive Suite - A collection of ImageJ2 commands for single-molecule analysis.
 * 
 * Copyright (C) 2018 - 2019 Karl Duderstadt
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.mpg.biochem.mars.molecule;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import org.scijava.ui.DialogPrompt.MessageType;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.*;

import de.mpg.biochem.mars.table.GroupIndices;
import de.mpg.biochem.mars.table.ResultsTableService;
import de.mpg.biochem.mars.table.MARSResultsTable;
import de.mpg.biochem.mars.util.*;

import static java.util.stream.Collectors.toList;

import org.scijava.table.*;

public class MoleculeArchive {
	private String name;
	
	//Services that the archive will need access to but that are not initialized..
	private MoleculeArchiveWindow win;
	private MoleculeArchiveService moleculeArchiveService;
	private File file;
	private File virtualDirectory;
	
	//This is the global factory used for parsing
	//will be set for either json or smile
	//and all uses will follow...
	private JsonFactory jfactory;
	
	private MoleculeArchiveProperties archiveProperties;
	
	//This will maintain a list of the metaDatasets as an index with UID keys for each..
	//Need to make sure all write operations are placed within synchronized blocks. synchronized(imageMetaDataIndex) { ... }
	//To avoid thread issues.
	//All read operations can be done in parallel no problem.
	private ArrayList<String> imageMetaDataIndex;
	
	//This will store all the ImageMetaData sets associated with the molecules
	//molecules have a metadataUID that maps to these keys so it is clear which dataset they were from.
	private ConcurrentMap<String, ImageMetaData> imageMetaData;
	
	//This is a list of molecule keys that will define the index and be used for retrieval from the ChronicleMap in virtual memory
	//or retrieval from the molecules array in memory
	//This array defines the absolute set of molecules considered to be in the archive for purposes of saving and reading etc...
	//Need to make sure all write operations are placed within synchronized blocks. synchronized(moleculeIndex) { ... }
	//To avoid thread issues.
	//All read operations can be done in parallel no problem.
	private ArrayList<String> moleculeIndex;
	
	//This is a map index of tags for searching in molecule tables etc..
	private ConcurrentMap<String, LinkedHashSet<String>> tagIndex;
	
	//This is a map from keys to molecules if working in memory..
	//Otherwise if working in virtual memory it is left null..
	private ConcurrentMap<String, Molecule> molecules;
	
	//By default we work virtual
	private boolean virtual = true;
	
	//determines whether the file is encoded in binary smile format
	private boolean smileEncoding = true;
	
	//Constructor for creating an empty molecule archive...	
	public MoleculeArchive(String name) {
		this.name = name;
		this.virtual = false;
		
		initializeVariables();
		
		//We will load the archive into normal memory for faster processing...
		molecules = new ConcurrentHashMap<>();
	}
	
	//Constructor for loading a moleculeArchive from file in memory without service initialization...
	public MoleculeArchive(File file) throws JsonParseException, IOException {
		this.name = file.getName();
		this.file = file;
		this.virtual = false;
		
		initializeVariables();
		
		if (virtual && file.isDirectory())
			loadStore(file);
		else if (virtual && !file.isDirectory())
			convertToVirtual(file);
		else
			load(file);
	}
	
	//Constructor for loading a moleculeArchive from file in memory without service initialization...
	public MoleculeArchive(File file, boolean virtual) throws JsonParseException, IOException {
		this.name = file.getName();
		this.file = file;
		this.virtual = virtual;
		
		initializeVariables();
		
		if (virtual && file.isDirectory())
			loadStore(file);
		else if (virtual && !file.isDirectory())
			convertToVirtual(file);
		else
			load(file);
	}
	
	//Constructor for loading a moleculeArchive from file...
	public MoleculeArchive(String name, File file, MoleculeArchiveService moleculeArchiveService, boolean virtual) throws JsonParseException, IOException {
		this.name = name;
		this.file = file;
		this.virtual = virtual;
		this.moleculeArchiveService = moleculeArchiveService;
		
		initializeVariables();
		
		if (virtual && file.isDirectory())
			loadStore(file);
		else if (virtual && !file.isDirectory())
			convertToVirtual(file);
		else
			load(file);
	}
	
	//Constructor for building a molecule archive from table...
	public MoleculeArchive(String name, MARSResultsTable table, MoleculeArchiveService moleculeArchiveService, boolean virtual) {
		this.name = name;
		this.virtual = virtual;
		this.moleculeArchiveService = moleculeArchiveService;
		
		initializeVariables();
		
		buildFromTable(table);
	}
	
	//Constructor for building a molecule archive from table without status updates...
	public MoleculeArchive(String name, MARSResultsTable table, boolean virtual) {
		this.name = name;
		this.virtual = virtual;
		
		initializeVariables();
		
		buildFromTable(table);
	}
	
	private void initializeVariables() {
		moleculeIndex = new ArrayList<String>();  
		imageMetaDataIndex = new ArrayList<String>(); 
		
		tagIndex = new ConcurrentHashMap<>();
		imageMetaData = new ConcurrentHashMap<>();
		
		archiveProperties = new MoleculeArchiveProperties(this);
	}
	
	private void convertToVirtual(File file) throws JsonParseException, IOException {
		//First lets build the directory structure
		virtualDirectory = new File(file.getParent() + "/" + file.getName() + ".store");
		virtualDirectory.mkdirs();
		new File(virtualDirectory.getAbsolutePath() + "/ImageMetaData").mkdirs();
		new File(virtualDirectory.getAbsolutePath() + "/Molecules").mkdirs();
		
		load(file);		
		
		saveIndex();
	}
	
	private void loadStore(File directory) throws JsonParseException, IOException {
		this.virtualDirectory = directory;		
		//Load in MoleculeArchive Properties.
		File propertiesFile = new File(virtualDirectory.getAbsolutePath() + "/MoleculeArchiveProperties.json");
		InputStream propertiesInputStream = new BufferedInputStream(new FileInputStream(propertiesFile));
		
		//Here we automatically detect the format of the MoleculeArchiveProperties 
		//Can be JSON text or Smile encoded binary file. 
		//We assume the entire virtual archive has the same format
		JsonFactory jsonF = new JsonFactory();
		SmileFactory smileF = new SmileFactory(); 
		DataFormatDetector det = new DataFormatDetector(new JsonFactory[] { jsonF, smileF });
	    DataFormatMatcher match = det.findFormat(propertiesInputStream);
	    JsonParser propertiesJParser = match.createParserWithMatch();
	    
	    if (match.getMatchedFormatName().equals("Smile")) {
	    	smileEncoding = true;
	    	jfactory = smileF;
	    } else if (match.getMatchedFormatName().equals("JSON")) {
	    	//This is included just for completeness in case we want to
	    	//add a third format someday...
	    	smileEncoding = false;
	    	jfactory = jsonF;
	    } else {
	    	//We default to Smile
	    	smileEncoding = true;
	    	jfactory = smileF;
	    }
		
	    propertiesJParser.nextToken();
		if ("MoleculeArchiveProperties".equals(propertiesJParser.getCurrentName())) {
			archiveProperties = new MoleculeArchiveProperties(propertiesJParser, this);
		}
		propertiesJParser.close();
		propertiesInputStream.close();
		
		//Now load in moleculeIndex
		File indexFile = new File(virtualDirectory.getAbsolutePath() + "/indexes.json");
		InputStream indexInputStream = new BufferedInputStream(new FileInputStream(indexFile));
		
	    JsonParser indexJParser = jfactory.createParser(indexInputStream);
		
	    indexJParser.nextToken();
	    while (indexJParser.nextToken() != JsonToken.END_OBJECT) {
		    if ("imageMetaDataIndex".equals(indexJParser.getCurrentName())) {
		    	indexJParser.nextToken();
		    	while (indexJParser.nextToken() != JsonToken.END_ARRAY) {
		    		imageMetaDataIndex.add(indexJParser.getText());
		        }
		    }
		    
		    if("moleculeIndex".equals(indexJParser.getCurrentName())) {
		    	indexJParser.nextToken();
		    	while (indexJParser.nextToken() != JsonToken.END_ARRAY) {
		    		moleculeIndex.add(indexJParser.getText());
		        }
		    }
		    
		    if("tagIndex".equals(indexJParser.getCurrentName())) {
		    	indexJParser.nextToken();
	    		while (indexJParser.nextToken() != JsonToken.END_ARRAY) {
	    			String UID = "NULL";
		    		while (indexJParser.nextToken() != JsonToken.END_OBJECT) {
		    			if("UID".equals(indexJParser.getCurrentName())) {
		    				indexJParser.nextToken();
		    				UID = indexJParser.getText();
		    			}
		    			
		    			if ("tags".equals(indexJParser.getCurrentName())) {
		    				indexJParser.nextToken();
		    				LinkedHashSet<String> tags = new LinkedHashSet<String>();
		    		    	while (indexJParser.nextToken() != JsonToken.END_ARRAY) {
		    		            tags.add(indexJParser.getText());
		    		        }
		    		    	tagIndex.put(UID, tags);
		    			}
		    		}
	    		}
		    }
	    }
	    
		indexJParser.close();
		indexInputStream.close();
		
		updateArchiveProperties();
	}
	
	private void load(File file) throws JsonParseException, IOException {
		//The first object in the yama file has general information about the archive including
		//number of Molecules and their averageSize, which we can use to initialize the ChronicleMap
		//if we are working virtual. So we load that information first
		InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
		
		//Here we automatically detect the format of the JSON file
		//Can be JSON text or Smile encoded binary file...
		JsonFactory jsonF = new JsonFactory();
		SmileFactory smileF = new SmileFactory(); 
		DataFormatDetector det = new DataFormatDetector(new JsonFactory[] { jsonF, smileF });
	    DataFormatMatcher match = det.findFormat(inputStream);
	    JsonParser jParser = match.createParserWithMatch();
	    
	    if (match.getMatchedFormatName().equals("Smile")) {
	    	smileEncoding = true;
	    	jfactory = smileF;
	    } else if (match.getMatchedFormatName().equals("JSON")) {
	    	//This is included just for completeness in case we want to
	    	//add a third format someday...
	    	smileEncoding = false;
	    	jfactory = jsonF;
	    } else {
	    	//We default to Smile
	    	smileEncoding = true;
	    	jfactory = smileF;
	    }
		
		jParser.nextToken();
		jParser.nextToken();
		if ("MoleculeArchiveProperties".equals(jParser.getCurrentName())) {
			archiveProperties = new MoleculeArchiveProperties(jParser, this);
		} else {
			moleculeArchiveService.getUIService().showDialog("No MoleculeArchiveProperties found. Are you sure this is a yama file?", MessageType.ERROR_MESSAGE);
			return;
		}
		
		if (!virtual)
			molecules = new ConcurrentHashMap<>();

		while (jParser.nextToken() != JsonToken.END_OBJECT) {
			String fieldName = jParser.getCurrentName();
			if ("ImageMetaData".equals(fieldName)) {
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					addImageMetaData(new ImageMetaData(jParser));
				}
			}
			
			if ("Molecules".equals(fieldName)) {
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					add(new Molecule(jParser));
				}
			}
		}
		
		jParser.close();
		inputStream.close();
		
		//Once we are done reading we should update molecule archive properties
		updateArchiveProperties();		
	}
	
	private void buildFromTable(MARSResultsTable results) {
		//First we have to index the groups in the table to determine the number of Molecules and their average size...
		//Here we assume their is a molecule column that defines which data is related to which molecule.
		LinkedHashMap<Integer, GroupIndices> groups = ResultsTableService.find_group_indices(results, "molecule");
		
		//We will load the archive into normal memory for faster processing...
		molecules = new ConcurrentHashMap<>();
		
		//We need to generate and add an ImageMetaData entry for the molecules from the the table
		//This will basically be empty, but as further processing steps occurs the log will be filled in
		//Also, the DataTable can be updated manually.
		String metaUID = MARSMath.getUUID58().substring(0, 10);
		ImageMetaData meta = new ImageMetaData(metaUID);
		addImageMetaData(meta);

		String[] headers = new String[results.getColumnCount() - 1];
		int col = 0;
		for (int i=0;i<results.getColumnCount();i++) {
			if (!results.getColumnHeader(i).equals("molecule")) {
				headers[col] = results.getColumnHeader(i);
				col++;
			}
		}
		
		//Now we need to build the archive from the table, molecule by molecule
		for (int mol: groups.keySet()) {
			MARSResultsTable molTable = new MARSResultsTable();
			for (String header: headers) {
				molTable.add(new DoubleColumn(header));
			}
			int row = 0;
			for (int j=groups.get(mol).start;j<=groups.get(mol).end;j++) {
				molTable.appendRow();
				col = 0;
				for (int i=0;i<results.getColumnCount();i++) {
					if (!results.getColumnHeader(i).equals("molecule")) {
						molTable.set(col, row, results.get(i, j));
						col++;
					}
				}
				row++;
			}
			Molecule molecule = new Molecule(MARSMath.getUUID58(), molTable);
			molecule.setImageMetaDataUID(metaUID);
			add(molecule);
		}
	}
	
	//Saving moleculeIndex
	private void saveIndex() {
		//Write out moleculeIndex
		try {
			File indexFile = new File(virtualDirectory.getAbsolutePath() + "/indexes.json");
			OutputStream stream = new BufferedOutputStream(new FileOutputStream(indexFile));
			
			JsonGenerator jGenerator = jfactory.createGenerator(stream);
			
			jGenerator.writeStartObject();

			//Write imageMetaDataIndex
			jGenerator.writeFieldName("imageMetaDataIndex");
			jGenerator.writeStartArray();
			for (String metaUID : imageMetaDataIndex) {
				jGenerator.writeString(metaUID);
			}
			jGenerator.writeEndArray();
			
			//Write moleculeIndex
			jGenerator.writeFieldName("moleculeIndex");
			jGenerator.writeStartArray();
			for (String UID : moleculeIndex) {
				jGenerator.writeString(UID);
			}
			jGenerator.writeEndArray();

			jGenerator.writeFieldName("tagIndex");
			jGenerator.writeStartArray();
			for (String UID : tagIndex.keySet()) {
				jGenerator.writeStartObject();
				jGenerator.writeStringField("UID", UID);
				jGenerator.writeFieldName("tags");
				jGenerator.writeStartArray();
				for (String tag : tagIndex.get(UID)) {
					jGenerator.writeString(tag);
				}
				jGenerator.writeEndArray();
				jGenerator.writeEndObject();
			}
			jGenerator.writeEndArray();
			
			jGenerator.writeEndObject();
			
			jGenerator.close();
			
			stream.flush();
			stream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void save() {
		if (virtual) {
			updateArchiveProperties();
			saveIndex();
		} else
			saveAs(file);
	}
	
	public void saveAs(File file) {
		try {
			String filePath = file.getAbsolutePath();
			if (!filePath.endsWith(".yama")) {
				file = new File(filePath + ".yama");
			}
			
			OutputStream stream = new BufferedOutputStream(new FileOutputStream(file));
			
			JsonGenerator jGenerator;
			if (smileEncoding) {
				SmileFactory jfactory = new SmileFactory();
				jGenerator = jfactory.createGenerator(stream);
			} else {
				JsonFactory jfactory = new JsonFactory();
				jGenerator = jfactory.createGenerator(stream, JsonEncoding.UTF8);
			}
			
			//We have to have a starting { for the json...
			jGenerator.writeStartObject();
			
			updateArchiveProperties();
			
			archiveProperties.toJSON(jGenerator);
			
			if (imageMetaDataIndex.size() > 0) {
				jGenerator.writeArrayFieldStart("ImageMetaData");
				Iterator<String> iter = imageMetaDataIndex.iterator();
				while (iter.hasNext()) {
					getImageMetaData(iter.next()).toJSON(jGenerator);
				}
				jGenerator.writeEndArray();
			}
			
			jGenerator.writeArrayFieldStart("Molecules");
			
			//loop through all molecules in ChronicleMap and save the data...
			Iterator<String> iterator = moleculeIndex.iterator();
			while (iterator.hasNext()) {
				get(iterator.next()).toJSON(jGenerator);
			}
			
			jGenerator.writeEndArray();
			
			//Now we need to add the corresponding global closing bracket } for the json format...
			jGenerator.writeEndObject();
			jGenerator.close();
			
			//flush and close streams...
			stream.flush();
			stream.close();
			
			//Reset the file and name
			this.file = file;
			setName(file.getName());
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	//Method for adding molecules to the archive
	//A key assumption here is that we never try to add two molecules that have the same key
	//So the idea is that we would only ever call this method once for a molecule with a given UID.
	public void add(Molecule molecule) {
		//We should increment the numberOfMolecules and set the correct index for molecule
		if (moleculeIndex.contains(molecule.getUID())) {
			addLogMessage("The archive already contains the molecule " + molecule.getUID() + ".");
		} else {
			//Need to make sure all write operations to moleculeIndex 
			//are synchronized to avoid two threads working at the same time
			//during a write operation
			synchronized(moleculeIndex) {
				moleculeIndex.add(molecule.getUID());
			}
			archiveProperties.setNumberOfMolecules(moleculeIndex.size());
			if (virtual) {
				set(molecule);
			} else {
				molecules.put(molecule.getUID(), molecule);
				molecule.setParentArchive(this);
			}
			updateTagIndex(molecule);
		}
	}
	
	public void addImageMetaData(ImageMetaData metaData) {
		setImageMetaData(metaData);
	}
	
	public void setImageMetaData(ImageMetaData metaData) {
		synchronized(imageMetaDataIndex) {
			if (!imageMetaDataIndex.contains(metaData.getUID()))
				imageMetaDataIndex.add(metaData.getUID());
		}
		if (virtual) {
			try {
				File metaDataFile = new File(virtualDirectory.getAbsolutePath() + "/ImageMetaData/" + metaData.getUID() + ".json");
				OutputStream stream = new BufferedOutputStream(new FileOutputStream(metaDataFile));
				
				JsonGenerator jGenerator = jfactory.createGenerator(stream);
				metaData.toJSON(jGenerator);
				jGenerator.close();
				
				stream.flush();
				stream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			imageMetaData.put(metaData.getUID(), metaData);
		}
	}
	
	public void removeImageMetaData(String metaUID) {
		synchronized(imageMetaDataIndex) {
			if (!imageMetaDataIndex.contains(metaUID))
				imageMetaDataIndex.remove(metaUID);
		}
		if (virtual) {
			File metaDataFile = new File(virtualDirectory.getAbsolutePath() + "/ImageMetaData/" + metaUID + ".json");
			if (metaDataFile.exists())
				metaDataFile.delete();
		} else {
			imageMetaData.remove(metaUID);
		}
	}

	public void removeImageMetaData(ImageMetaData meta) {
		removeImageMetaData(meta.getUID());
	}
	
	public ImageMetaData getImageMetaData(int index) {
		return getImageMetaData(imageMetaDataIndex.get(index));
	}
	
	public ImageMetaData getImageMetaData(String metaUID) {
		if (virtual) {
			ImageMetaData metaData = null;
			try {
				File metaDataFile = new File(virtualDirectory.getAbsolutePath() + "/ImageMetaData/" + metaUID + ".json");
				InputStream inputStream = new BufferedInputStream(new FileInputStream(metaDataFile));
		
				JsonParser jParser = jfactory.createParser(inputStream);

				metaData = new ImageMetaData(jParser);
				
				jParser.close();
				inputStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return metaData;
		} else {
			return imageMetaData.get(metaUID);
		}
	}
	
	public ArrayList<String> getImageMetaDataUIDs() {
		return imageMetaDataIndex;
	}
	
	public int getNumberOfMolecules() {
		return moleculeIndex.size();
	}
	
	public int getNumberOfImageMetaDataItems() {
		return imageMetaDataIndex.size();
	}
	
	public String getStoreLocation() {
		return virtualDirectory.getAbsolutePath();
	}
	
	public String getComments() {
		return archiveProperties.getComments();
	}
	
	public void setComments(String comments) {
		archiveProperties.setComments(comments);
		if (virtual) {
			updateArchiveProperties();
		}
	}
	
	public boolean isVirtual() {
		return virtual;
	}

	//Retrieve molecule based on index
	public Molecule get(int index) {
		return get(moleculeIndex.get(index));
	}
	
	public ArrayList<String> getMoleculeUIDs() {
		return moleculeIndex;
	}
	
	public String getTagList(String UID) {
		if (UID == null)
			return null;
		else {
			String tagList = "";
			for (String tag: tagIndex.get(UID))
				tagList += tag + ", ";
			tagList = tagList.substring(0, tagList.length() - 2);
			return tagList;
		}
	}
	
	public void set(Molecule molecule) {
		if (virtual) {
			try {
				File moleculeFile = new File(virtualDirectory.getAbsolutePath() + "/Molecules/" + molecule.getUID() + ".json");
				OutputStream stream = new BufferedOutputStream(new FileOutputStream(moleculeFile));
				
				JsonGenerator jGenerator = jfactory.createGenerator(stream);
				molecule.toJSON(jGenerator);
				jGenerator.close();
				
				stream.flush();
				stream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			//We do nothing because we always work with the actual archive copy if working in memory.
			//We would do the following, but doesn't make sense.
			//molecules.put(molecule.getUID(), molecule);
		}
		
		//In either case we should update the tag index for fast searching...
		updateTagIndex(molecule);
	}
	
	public void generateTagIndex() {
		//Need to determine the number of threads
		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
		
		try {
	        forkJoinPool.submit(() -> moleculeIndex.parallelStream().forEach(UID -> { 
	        	updateTagIndex(get(UID));
	        })).get();
	   } catch (InterruptedException | ExecutionException e) {
	        // handle exceptions
	    	e.printStackTrace();
	   } finally {
	      forkJoinPool.shutdown();
	   }
	}
	
	public void updateTagIndex(String updateUID) {
		updateTagIndex(get(updateUID));
	}

	private void updateTagIndex(Molecule molecule) {
		if (molecule.getTags() == null) {
			//Shouldn't happen..do nothing...
		} else if (molecule.getTags().size() > 0) {
			tagIndex.put(molecule.getUID(), molecule.getTags());
		} else {
			tagIndex.remove(molecule.getUID());
		}
	}
	
	public void remove(String UID) {
		synchronized(moleculeIndex) {
			moleculeIndex.remove(UID);
		}
		if (virtual) {
			File moleculeFile = new File(virtualDirectory.getAbsolutePath() + "/Molecules/" + UID + ".json");
			if (moleculeFile.exists())
				moleculeFile.delete();
		} else {
			molecules.remove(UID);
		}
	}
	
	public void remove(Molecule molecule) {
		remove(molecule.getUID());
	}
	
	//Should update to use tagIndex in virtual mode
	//So all records are not opened...
	public void deleteMoleculesWithTag(String tag) {
		//We should do this with streams but for the moment this is faster
		ArrayList<String> newMoleculeIndex = new ArrayList<String>();
		
		for (String UID : moleculeIndex) {
			Molecule molecule = get(UID);
			
			if (molecule.hasTag(tag)) {
				if (virtual) {
					File moleculeFile = new File(virtualDirectory.getAbsolutePath() + "/Molecules/" + UID + ".json");
					if (moleculeFile.exists())
						moleculeFile.delete();
				} else {
					molecules.remove(UID);
				}
			} else {
				newMoleculeIndex.add(molecule.getUID());
			}
		}
		
		moleculeIndex = newMoleculeIndex;
		archiveProperties.setNumberOfMolecules(moleculeIndex.size());	
	}
	
	public boolean contains(String UID) {
		if (virtual) {
			return moleculeIndex.contains(UID);
		} else {
			return molecules.containsKey(UID);
		}
	}

	//Retrieve molecule based on UUID58 key
	public Molecule get(String UID) {
		if (virtual) {
			Molecule molecule = null;
			try {
				File moleculeFile = new File(virtualDirectory.getAbsolutePath() + "/Molecules/" + UID + ".json");
				InputStream inputStream = new BufferedInputStream(new FileInputStream(moleculeFile));
		
				JsonParser jParser = jfactory.createParser(inputStream);
	
				molecule = new Molecule(jParser);
				
				jParser.close();
				inputStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return molecule;
		} else {
			return molecules.get(UID);
		}
	}
	
	public int getIndex(String UID) {
		return moleculeIndex.indexOf(UID);
	}
	
	public String getUIDAtIndex(int index) {
		return moleculeIndex.get(index);
	}
	
	public void destroy() {
		if (virtual) {
			//archive.close();
		}
	}
	
	public File getFile() {
		return file;
	}
	
	public void setFile(File file) {
		this.file = file;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public MoleculeArchiveWindow getWindow() {
		return win;
	}
	
	public void setWindow(MoleculeArchiveWindow win) {
		this.win = win;
	}
	
	public void lock() {
		if (win != null) {
			win.lockArchive();
		}
	}
	
	public void unlock() {
		if (win != null) {
			win.unlockArchive();
		}
	}
	
	public void setSMILEencoding() {
		smileEncoding = true;
	}
	
	public void unsetSMILEencoding() {
		smileEncoding = false;
	}
	
	public boolean isSMILEencoding() {
		return smileEncoding;
	}
	
	public void naturalOrderSortMoleculeIndex() {
		moleculeIndex = (ArrayList<String>)moleculeIndex.stream().sorted().collect(toList());
	}
	
	public MoleculeArchiveProperties getArchiveProperties() {
		return archiveProperties;
	}
	
	public void addLogMessage(String message) {
		for (String metaUID : imageMetaDataIndex) {
			if (virtual) {
				ImageMetaData meta = getImageMetaData(metaUID);
				meta.addLogMessage(message);
				setImageMetaData(meta);
			} else {
				imageMetaData.get(metaUID).addLogMessage(message);
			}
		}
	}
	
	//Utility functions
	public void updateArchiveProperties() {
		archiveProperties.setNumberOfMolecules(moleculeIndex.size());
		archiveProperties.setNumImageMetaData(imageMetaData.size());
		
		if (virtual) {
			try {
				File propertiesFile = new File(virtualDirectory.getAbsolutePath() + "/MoleculeArchiveProperties.json");
				OutputStream stream = new BufferedOutputStream(new FileOutputStream(propertiesFile));
				
				JsonGenerator jGenerator = jfactory.createGenerator(stream);
				jGenerator.writeStartObject();
				archiveProperties.toJSON(jGenerator);
				jGenerator.writeEndObject();
				jGenerator.close();
				
				stream.flush();
				stream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public String toString() {
		return name;
	}
}
