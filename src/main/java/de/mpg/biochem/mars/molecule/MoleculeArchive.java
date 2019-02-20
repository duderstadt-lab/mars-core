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
import java.io.FilenameFilter;
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
	
	//Can be a .yama file or a directory containing a virtual store
	private File file;
	
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
	
	//this is a map of molecule UIDs to imageMetaDataUID for virtual storage indexing...
	private ConcurrentMap<String, String> moleculeImageMetaDataUIDIndex;
	
	//This is a map from keys to molecules if working in memory..
	//Otherwise if working in virtual memory it is left null..
	private ConcurrentMap<String, Molecule> molecules;
	
	//By default we work virtual
	private boolean virtual;
	
	//determines whether the file is encoded in binary smile format
	private boolean outputSmileEncoding = true;
	
	//For virtual archives we must keep track of the 
	//encoding when it was loaded so we always parse correctly
	//even if the user changed the format in the properties panel.
	private boolean inputSmileEncoding = true;
	
	//Need to determine the number of threads
	final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
	
	//Constructor for creating an empty molecule archive...	
	public MoleculeArchive(String name) {
		this.name = name;
		this.virtual = false;
		
		initializeVariables();
	}
	
	//Constructor for loading a moleculeArchive 
	//accepts files for in memory storage
	//and directories for virtual storage
	public MoleculeArchive(File file) throws JsonParseException, IOException {
		this.name = file.getName();
		this.file = file;
		
		if (file.isDirectory())
			this.virtual = true;
		else 
			this.virtual = false;
		
		initializeVariables();
		
		if (virtual)
			loadVirtualStore(file);
		else
			load(file);
	}
	
	//Constructor for loading a moleculeArchive 
	//accepts files for in memory storage
	//and directories for virtual storage
	public MoleculeArchive(String name, File file, MoleculeArchiveService moleculeArchiveService) throws JsonParseException, IOException {
		this.name = name;
		this.file = file;
		this.moleculeArchiveService = moleculeArchiveService;
		
		if (file.isDirectory())
			this.virtual = true;
		else 
			this.virtual = false;
		
		initializeVariables();
		
		if (virtual)
			loadVirtualStore(file);
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
		
		if (virtual) {
			tagIndex = new ConcurrentHashMap<>();
			moleculeImageMetaDataUIDIndex = new ConcurrentHashMap<>();
		} else {
			molecules = new ConcurrentHashMap<>();
			imageMetaData = new ConcurrentHashMap<>();
		}
		
		archiveProperties = new MoleculeArchiveProperties(this);
	}
	
	private void loadVirtualStore(File file) throws JsonParseException, IOException {
		this.file = file;		
		//Load in MoleculeArchive Properties.
		File propertiesFile = new File(file.getAbsolutePath() + "/MoleculeArchiveProperties.json");
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
	    	inputSmileEncoding = true;
	    	outputSmileEncoding = true;
	    	jfactory = smileF;
	    } else if (match.getMatchedFormatName().equals("JSON")) {
	    	//This is included just for completeness in case we want to
	    	//add a third format someday...
	    	inputSmileEncoding = false;
	    	outputSmileEncoding = false;
	    	jfactory = jsonF;
	    } else {
	    	//We default to Smile
	    	inputSmileEncoding = true;
	    	outputSmileEncoding = true;
	    	jfactory = smileF;
	    }
		
	    propertiesJParser.nextToken();
		if ("MoleculeArchiveProperties".equals(propertiesJParser.getCurrentName())) {
			archiveProperties = new MoleculeArchiveProperties(propertiesJParser, this);
		}
		propertiesJParser.close();
		propertiesInputStream.close();
		
		//Now load in moleculeIndex
		File indexFile = new File(file.getAbsolutePath() + "/indexes.json");
		InputStream indexInputStream = new BufferedInputStream(new FileInputStream(indexFile));
		
	    JsonParser indexJParser = jfactory.createParser(indexInputStream);
		
	    indexJParser.nextToken();
	    while (indexJParser.nextToken() != JsonToken.END_OBJECT) {
	    	String fieldname = indexJParser.getCurrentName();
		    
		    if (fieldname == null)
		    	continue;
	    	
		    if ("imageMetaDataIndex".equals(fieldname)) {
		    	indexJParser.nextToken();
		    	while (indexJParser.nextToken() != JsonToken.END_ARRAY) {
		    		imageMetaDataIndex.add(indexJParser.getText());
		        }
		    	continue;
		    }
		    
		    if("moleculeIndex".equals(fieldname)) {
		    	indexJParser.nextToken();
	    		while (indexJParser.nextToken() != JsonToken.END_ARRAY) {
	    			String UID = "NULL";
		    		while (indexJParser.nextToken() != JsonToken.END_OBJECT) {
		    			if("UID".equals(indexJParser.getCurrentName())) {
		    				indexJParser.nextToken();
		    				UID = indexJParser.getText();
		    				moleculeIndex.add(UID);
		    			}
		    			
		    			if ("ImageMetaDataUID".equals(indexJParser.getCurrentName())) {
		    				indexJParser.nextToken();
		    				moleculeImageMetaDataUIDIndex.put(UID, indexJParser.getText());
		    			}
		    			
		    			if ("Tags".equals(indexJParser.getCurrentName())) {
		    				indexJParser.nextToken();
		    				LinkedHashSet<String> tags = new LinkedHashSet<String>();
		    		    	while (indexJParser.nextToken() != JsonToken.END_ARRAY) {
		    		            tags.add(indexJParser.getText());
		    		        }
		    		    	tagIndex.put(UID, tags);
		    			}
		    		}
	    		}
	    		continue;
		    }
		    
		    //SHOULD BE UNREACHABLE
		    //This is only reached if there is an unexpected field added to the json record
		    //In that case we simply pass through it
		    //This ensure if extra fields are added in the future
		    //old versions will be able to open the new files
		    //However, the missing fields will not be saved properly
		    //In the case of a virtual archive new fields will be systematically removed as records are opened and saved...
		    if (indexJParser.getCurrentToken() == JsonToken.START_OBJECT) {
		    	System.out.println("unknown object encountered in indexes ... skipping");
		    	passThroughUnknownObjects(indexJParser);
		    }
	    }
	    
		indexJParser.close();
		indexInputStream.close();
		
		updateArchiveProperties();
	}
	
	private void passThroughUnknownObjects(JsonParser jParser) throws IOException {
    	while (jParser.nextToken() != JsonToken.END_OBJECT) {
    		if (jParser.getCurrentToken() == JsonToken.START_OBJECT)
    			passThroughUnknownObjects(jParser);
    	}
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
	    	inputSmileEncoding = true;
	    	outputSmileEncoding = true;
	    	jfactory = smileF;
	    } else if (match.getMatchedFormatName().equals("JSON")) {
	    	//This is included just for completeness in case we want to
	    	//add a third format someday...
	    	inputSmileEncoding = false;
	    	outputSmileEncoding = false;
	    	jfactory = jsonF;
	    } else {
	    	//We default to Smile
	    	inputSmileEncoding = true;
	    	outputSmileEncoding = true;
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
	
	//Rebuild all indexes by inspecting the contents of store directories...
	public void rebuildIndexes() {
		//We only do this if we have a virtual archive
		//otherwise some of the indexes were never initialized.
		if (virtual) {
			//First we get file lists from ImageMetaData and Molecule Directories
			//these are considered the new moleculeIndex and imageMetaDataIndex
			String[] moleculeFileNameIndex = new File(file.getAbsolutePath() + "/Molecules").list(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(".json");
				}
			});
			ArrayList<String> newMoleculeIndex = new ArrayList<String>();
			for (int i=0;i<moleculeFileNameIndex.length;i++)
				newMoleculeIndex.add(moleculeFileNameIndex[i].substring(0, moleculeFileNameIndex.length - 6));
			
			String[] imageMetaDataFileNameIndex = new File(file.getAbsolutePath() + "/ImageMetaData").list(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(".json");
				}
			});
			ArrayList<String> newImageMetaDataIndex = new ArrayList<String>();
			for (int i=0;i<imageMetaDataFileNameIndex.length;i++)
				newImageMetaDataIndex.add(imageMetaDataFileNameIndex[i].substring(0, imageMetaDataFileNameIndex.length - 6));
			
			
			ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
			
			ConcurrentMap<String, LinkedHashSet<String>> newTagIndex = new ConcurrentHashMap<>();
			ConcurrentMap<String, String> newMoleculeImageMetaDataUIDIndex = new ConcurrentHashMap<>();
			
			try {
		        forkJoinPool.submit(() -> moleculeIndex.parallelStream().forEach(UID -> { 
		        	Molecule molecule = get(UID);
		        	newTagIndex.put(UID, molecule.getTags());
		        	newMoleculeImageMetaDataUIDIndex.put(UID, molecule.getImageMetaDataUID());
		        })).get();        
		   } catch (InterruptedException | ExecutionException e ) {
		        // handle exceptions
		    	e.printStackTrace();
		   } finally {
		      forkJoinPool.shutdown();
		   }
			
		   this.moleculeIndex = (ArrayList<String>)newMoleculeIndex.stream().sorted().collect(toList());
		   this.imageMetaDataIndex = (ArrayList<String>)newImageMetaDataIndex.stream().sorted().collect(toList());
		   this.tagIndex = newTagIndex;
		   this.moleculeImageMetaDataUIDIndex = newMoleculeImageMetaDataUIDIndex;
			
		   saveIndexes();
		}
	}
	
	private void saveIndexes() {
		saveIndexes(file, moleculeIndex, imageMetaDataIndex, moleculeImageMetaDataUIDIndex,  tagIndex, jfactory);
	}
	
	private void saveIndexes(File directory, ArrayList<String> moleculeIndex, ArrayList<String> imageMetaDataIndex, ConcurrentMap<String, String> moleculeImageMetaDataUIDIndex, ConcurrentMap<String, LinkedHashSet<String>> tagIndex, JsonFactory jfactory) {
		try {
			File indexFile = new File(directory.getAbsolutePath() + "/indexes.json");
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
			jGenerator.writeArrayFieldStart("moleculeIndex");
			for (String UID : moleculeIndex) {
				jGenerator.writeStartObject();
				jGenerator.writeStringField("UID", UID);
				jGenerator.writeStringField("ImageMetaDataUID", moleculeImageMetaDataUIDIndex.get(UID));
				jGenerator.writeArrayFieldStart("Tags");
				for (String tag : tagIndex.get(UID)) {
					jGenerator.writeString(tag);
				}
				jGenerator.writeEndArray();
				jGenerator.writeEndObject();
			}
			jGenerator.writeEndArray();

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
			saveIndexes();
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
			
			//build a new factory just for this output run...
			JsonFactory jfactory;
			if (outputSmileEncoding) {
				jfactory = new SmileFactory();
			} else {
				jfactory = new JsonFactory();
			}
			
			JsonGenerator jGenerator = jfactory.createGenerator(stream);
			
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
	
	//Given a File directory this method will create the directory and a virtual store inside.
	public void saveAsVirtualStore(File virtualDirectory) {
		virtualDirectory.mkdirs();
		new File(virtualDirectory.getAbsolutePath() + "/ImageMetaData").mkdirs();
		new File(virtualDirectory.getAbsolutePath() + "/Molecules").mkdirs();

		//We will generate the index as we save records...
		ConcurrentMap<String, LinkedHashSet<String>> newTagIndex = new ConcurrentHashMap<>();
		ConcurrentMap<String, String> newMoleculeImageMetaDataUIDIndex = new ConcurrentHashMap<>();
		
		//build a new factory just for this output run...
		JsonFactory jfactory;
		if (outputSmileEncoding) {
			jfactory = new SmileFactory();
		} else {
			jfactory = new JsonFactory();
		}
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
		
		try {
			//Generate all imageMetaData record files...
			forkJoinPool.submit(() -> imageMetaDataIndex.parallelStream().forEach(metaUID -> { 
	        	try {
					saveImageMetaDataToFile(new File(virtualDirectory.getAbsolutePath() + "/ImageMetaData"), getImageMetaData(metaUID), jfactory);
	        	} catch (IOException e) {
	        		e.printStackTrace();
	        	}
	        })).get();
			
			//Generate all molecule record files and indexes as the same time...
	        forkJoinPool.submit(() -> moleculeIndex.parallelStream().forEach(UID -> { 
	        	Molecule molecule = get(UID);
	        	newTagIndex.put(UID, molecule.getTags());
	        	newMoleculeImageMetaDataUIDIndex.put(UID, molecule.getImageMetaDataUID());
	        	try {
					saveMoleculeToFile(new File(virtualDirectory.getAbsolutePath() + "/Molecules"), molecule, jfactory);
	        	} catch (IOException e) {
	        		e.printStackTrace();
	        	}
	        })).get();        
	   } catch (InterruptedException | ExecutionException e ) {
	        // handle exceptions
	    	e.printStackTrace();
	   } finally {
	      forkJoinPool.shutdown();
	   }

		//Save archive properties
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
			
		//Generate indexes file using moleculeIndex and imageMetaDataIndex of current archive
		//If the current archive is not virtual.. then tagIndex and moleculeImageMetaDataUIDIndex
		//were never created.. So here we create local copies as we save records
		//then we save the resulting indexes from the operation..
		//this way virtual or in memory archive can both be saved no problem..
		//In the case of saving virtual archives this method then re-indexes completely.
		saveIndexes(virtualDirectory, moleculeIndex, imageMetaDataIndex, newMoleculeImageMetaDataUIDIndex,  newTagIndex, jfactory);
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
				updateTagIndex(molecule);
			} else {
				molecules.put(molecule.getUID(), molecule);
				molecule.setParentArchive(this);
			}
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
				saveImageMetaDataToFile(new File(file.getAbsolutePath() + "/ImageMetaData"), metaData, jfactory);
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
			File metaDataFile = new File(file.getAbsolutePath() + "/ImageMetaData/" + metaUID + ".json");
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
				File metaDataFile = new File(file.getAbsolutePath() + "/ImageMetaData/" + metaUID + ".json");
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
		return file.getAbsolutePath();
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
		LinkedHashSet<String> tags;
		if (UID == null)
			 return null;
		else if (virtual) {
			tags = tagIndex.get(UID);
		} else {
			tags = get(UID).getTags();
		}
		
		if (tags == null)
			return "";
		
		String tagList = "";
		for (String tag: tags)
			tagList += tag + ", ";
		if (tags.size() > 0)
			tagList = tagList.substring(0, tagList.length() - 2);
		return tagList;
	}
	
	public void set(Molecule molecule) {
		if (virtual) {
			try {
				saveMoleculeToFile(new File(file.getAbsolutePath() + "/Molecules"), molecule, jfactory);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			updateTagIndex(molecule);
		} else {
			molecules.put(molecule.getUID(), molecule);
		}
	}
	
	public void saveMoleculeToFile(File directory, Molecule molecule, JsonFactory jfactory) throws IOException {
		File moleculeFile = new File(directory.getAbsolutePath() + "/" + molecule.getUID() + ".json");
		OutputStream stream = new BufferedOutputStream(new FileOutputStream(moleculeFile));
		
		JsonGenerator jGenerator = jfactory.createGenerator(stream);
		molecule.toJSON(jGenerator);
		jGenerator.close();
		
		stream.flush();
		stream.close();
	}
	
	public void saveImageMetaDataToFile(File directory, ImageMetaData imageMetaData, JsonFactory jfactory) throws IOException {
		File imageMetaDataFile = new File(directory.getAbsolutePath() + "/" + imageMetaData.getUID() + ".json");
		OutputStream stream = new BufferedOutputStream(new FileOutputStream(imageMetaDataFile));
		
		JsonGenerator jGenerator = jfactory.createGenerator(stream);
		imageMetaData.toJSON(jGenerator);
		jGenerator.close();
		
		stream.flush();
		stream.close();
	}

	public void updateTagIndex(String updateUID) {
		updateTagIndex(get(updateUID));
	}

	public void updateTagIndex(Molecule molecule) {
		if (virtual) {
			if (molecule.getTags() == null) {
				//Shouldn't happen..do nothing...
			} else if (molecule.getTags().size() > 0) {
				tagIndex.put(molecule.getUID(), molecule.getTags());
			} else {
				tagIndex.remove(molecule.getUID());
			}
		}
	}
	
	public void remove(String UID) {
		synchronized(moleculeIndex) {
			moleculeIndex.remove(UID);
		}
		if (virtual) {
			File moleculeFile = new File(file.getAbsolutePath() + "/Molecules/" + UID + ".json");
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
					File moleculeFile = new File(file.getAbsolutePath() + "/Molecules/" + UID + ".json");
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
				File moleculeFile = new File(file.getAbsolutePath() + "/Molecules/" + UID + ".json");
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
	
	public void setSMILEOutputEncoding() {
		outputSmileEncoding = true;
	}
	
	public void unsetSMILEOutputEncoding() {
		outputSmileEncoding = false;
	}
	
	public boolean isSMILEOutputEncoding() {
		return outputSmileEncoding;
	}
	
	public boolean isSMILEInputEncoding() {
		return inputSmileEncoding;
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
		archiveProperties.setNumImageMetaData(imageMetaDataIndex.size());
		
		if (virtual) {
			try {
				File propertiesFile = new File(file.getAbsolutePath() + "/MoleculeArchiveProperties.json");
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
