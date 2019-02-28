/*******************************************************************************
 * Copyright (C) 2019, Karl Duderstadt
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
import java.util.Set;
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
	private ConcurrentMap<String, MARSImageMetaData> imageMetaData;
	
	//This is a list of molecule keys that will define the index and be used for retrieval from the ChronicleMap in virtual memory
	//or retrieval from the molecules array in memory
	//This array defines the absolute set of molecules considered to be in the archive for purposes of saving and reading etc...
	//Need to make sure all write operations are placed within synchronized blocks. synchronized(moleculeIndex) { ... }
	//To avoid thread issues.
	//All read operations can be done in parallel no problem.
	private ArrayList<String> moleculeIndex;
	
	//This is a map index of tags for searching in molecule tables etc..
	private ConcurrentMap<String, LinkedHashSet<String>> tagIndex, imageMetaDataTagIndex;
	
	//this is a map of molecule UIDs to imageMetaDataUID for virtual storage indexing...
	private ConcurrentMap<String, String> moleculeImageMetaDataUIDIndex;
	
	//This is a map from keys to molecules if working in memory..
	//Otherwise if working in virtual memory it is left null..
	private ConcurrentMap<String, Molecule> molecules;
	
	//By default we work virtual
	private boolean virtual;
	
	private Set<String> virtualMoleculesSet, virtualImageMetaDataSet;
	
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
			imageMetaDataTagIndex = new ConcurrentHashMap<>();
			moleculeImageMetaDataUIDIndex = new ConcurrentHashMap<>();
			
			virtualMoleculesSet = ConcurrentHashMap.newKeySet();
			virtualImageMetaDataSet = ConcurrentHashMap.newKeySet();
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
		if (indexFile.exists()) {
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
			    		String metaUID = indexJParser.getText();
			    		//if (!virtualImageMetaDataSet.contains(metaUID)) {
			    			virtualImageMetaDataSet.add(metaUID);
			    			imageMetaDataIndex.add(metaUID);
			    		//}
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
			    				//if (!virtualMoleculesSet.contains(UID)) {
			    					virtualMoleculesSet.add(UID);
					    			moleculeIndex.add(UID);
					    		//}
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
		} else {
			System.out.println("No indexes.json file found. Rebuilding indexes... This might take a while...");
			rebuildIndexes();
		}
		
		updateProperties();
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
					putImageMetaData(new MARSImageMetaData(jParser));
				}
			}
			
			if ("Molecules".equals(fieldName)) {
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					put(new Molecule(jParser));
				}
			}
		}
		
		jParser.close();
		inputStream.close();
		
		//Once we are done reading we should update molecule archive properties
		updateProperties();		
	}
	
	private void buildFromTable(MARSResultsTable results) {
		//First we have to index the groups in the table to determine the number of Molecules and their average size...
		//Here we assume their is a molecule column that defines which data is related to which molecule.
		LinkedHashMap<Integer, GroupIndices> groups = ResultsTableService.find_group_indices(results, "molecule");
		
		//We need to generate and add an ImageMetaData entry for the molecules from the the table
		//This will basically be empty, but as further processing steps occurs the log will be filled in
		//Also, the DataTable can be updated manually.
		String metaUID = MARSMath.getUUID58().substring(0, 10);
		MARSImageMetaData meta = new MARSImageMetaData(metaUID);
		putImageMetaData(meta);

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
			put(molecule);
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
			for (int i=0;i<moleculeFileNameIndex.length;i++) {
				newMoleculeIndex.add(moleculeFileNameIndex[i].substring(0, moleculeFileNameIndex[i].length() - 5));
			}
			
			String[] imageMetaDataFileNameIndex = new File(file.getAbsolutePath() + "/ImageMetaData").list(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(".json");
				}
			});
			ArrayList<String> newImageMetaDataIndex = new ArrayList<String>();
			for (int i=0;i<imageMetaDataFileNameIndex.length;i++)
				newImageMetaDataIndex.add(imageMetaDataFileNameIndex[i].substring(0, imageMetaDataFileNameIndex[i].length() - 5));
			
			ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
			
			ConcurrentMap<String, LinkedHashSet<String>> newTagIndex = new ConcurrentHashMap<>();
			ConcurrentMap<String, String> newMoleculeImageMetaDataUIDIndex = new ConcurrentHashMap<>();
			
			//Let's also rebuild the parameter index stored in the archiveProperties
			//ConcurrentHashMap<String, Boolean> paramListMap = new ConcurrentHashMap<>();
			Set<String> newParameterSet = ConcurrentHashMap.newKeySet();
			
			//ConcurrentHashMap<String, Boolean> tagListMap = new ConcurrentHashMap<>();
			Set<String> newTagSet = ConcurrentHashMap.newKeySet();
			
			//ConcurrentHashMap<String, Boolean> molListMap = new ConcurrentHashMap<>();
			Set<String> newMoleculeDataTableColumnSet = ConcurrentHashMap.newKeySet();
			
			try {
		        forkJoinPool.submit(() -> moleculeIndex.parallelStream().forEach(UID -> { 
		        	Molecule molecule = get(UID);
		        	newTagIndex.put(UID, molecule.getTags());
		        	newMoleculeImageMetaDataUIDIndex.put(UID, molecule.getImageMetaDataUID());
		        	
		        	newParameterSet.addAll(molecule.getParameters().keySet());
		        	newTagSet.addAll(molecule.getTags());
		        	newMoleculeDataTableColumnSet.addAll(molecule.getDataTable().getColumnHeadingList());
		        	
		        	archiveProperties.addAllColumns(molecule.getDataTable().getColumnHeadingList());
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
		   
		   archiveProperties.setTagSet(newTagSet);
		   archiveProperties.setParameterSet(newParameterSet);
		   archiveProperties.setColumnSet(newMoleculeDataTableColumnSet);
			
		   saveIndexes();
		   updateProperties();
		}
	}
	
	private void saveIndexes() {
		saveIndexes(file, moleculeIndex, imageMetaDataIndex, moleculeImageMetaDataUIDIndex, tagIndex, jfactory);
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
				
				if (tagIndex.containsKey(UID)) {
					jGenerator.writeArrayFieldStart("Tags");
					for (String tag : tagIndex.get(UID)) {
						jGenerator.writeString(tag);
					}
					jGenerator.writeEndArray();
				}
				
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
			updateProperties();
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
			
			updateProperties();
			
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
			//Generate all MARSImageMetaData record files...
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
	
	/**
	 * Adds a molecule to the archive. If a molecule with the same UID 
	 * is already in the archive, the record is updated.
	 * <p>
	 * All indexes are updated using the new molecule record.
	 */
	public void put(Molecule molecule) {		
		if (virtual) {
			//Need to make sure all write operations to moleculeIndex 
			//are synchronized to avoid two threads working at the same time
			//during a write operation
			//We check with a set for speed, then add to index
			if (!virtualMoleculesSet.contains(molecule.getUID())) {
				synchronized(moleculeIndex) {
					moleculeIndex.add(molecule.getUID());
				}
				virtualMoleculesSet.add(molecule.getUID());
				archiveProperties.setNumberOfMolecules(moleculeIndex.size());
			}
			try {
				saveMoleculeToFile(new File(file.getAbsolutePath() + "/Molecules"), molecule, jfactory);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			updateTagIndex(molecule);
		} else if (!molecules.containsKey(molecule.getUID())) {
			//If working in memory and the key is already in the map
			//there is only one copy and all changes have already been saved
			//otherwise, we add it as a new record.
			synchronized(moleculeIndex) {
				moleculeIndex.add(molecule.getUID());
			}
			molecule.setParent(this);
			molecules.put(molecule.getUID(), molecule);
			archiveProperties.setNumberOfMolecules(moleculeIndex.size());
		}
		archiveProperties.addAllColumns(molecule.getDataTable().getColumnHeadingList());
	}
	
	public void putImageMetaData(MARSImageMetaData metaData) {
		if (virtual) {
				if (!virtualImageMetaDataSet.contains(metaData.getUID())) {
					synchronized(imageMetaDataIndex) {	
						imageMetaDataIndex.add(metaData.getUID());
					}
					virtualImageMetaDataSet.add(metaData.getUID());
					archiveProperties.setNumImageMetaData(imageMetaDataIndex.size());
				}
			try {
				saveImageMetaDataToFile(new File(file.getAbsolutePath() + "/ImageMetaData"), metaData, jfactory);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (!imageMetaData.containsKey(metaData.getUID())) {
			//If working in memory and the key is already in the map
			//there is only one copy and all changes have already been saved
			//otherwise, we add it as a new record.
			synchronized(imageMetaDataIndex) {	
				imageMetaDataIndex.add(metaData.getUID());
			}
			metaData.setParent(this);
			imageMetaData.put(metaData.getUID(), metaData);
			archiveProperties.setNumImageMetaData(imageMetaDataIndex.size());
		}
	}
	
	public void removeImageMetaData(String metaUID) {
		synchronized(imageMetaDataIndex) {
			imageMetaDataIndex.remove(metaUID);
		}
		if (virtual) {
			File metaDataFile = new File(file.getAbsolutePath() + "/ImageMetaData/" + metaUID + ".json");
			if (metaDataFile.exists())
				metaDataFile.delete();
			virtualImageMetaDataSet.remove(metaUID);
		} else {
			imageMetaData.remove(metaUID);
		}
	}

	public void removeImageMetaData(MARSImageMetaData meta) {
		removeImageMetaData(meta.getUID());
	}
	
	public MARSImageMetaData getImageMetaData(int index) {
		return getImageMetaData(imageMetaDataIndex.get(index));
	}
	
	public MARSImageMetaData getImageMetaData(String metaUID) {
		if (virtual) {
			MARSImageMetaData metaData = null;
			try {
				File metaDataFile = new File(file.getAbsolutePath() + "/ImageMetaData/" + metaUID + ".json");
				InputStream inputStream = new BufferedInputStream(new FileInputStream(metaDataFile));
		
				JsonParser jParser = jfactory.createParser(inputStream);

				metaData = new MARSImageMetaData(jParser);
				
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
	
	public int getNumberOfImageMetaDataRecords() {
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
		//if (virtual) {
		//	updateArchiveProperties();
		//}
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
	
	public String getImageMetaDataTagList(String UID) {
		LinkedHashSet<String> tags;
		if (UID == null)
			 return null;
		else if (virtual) {
			tags = imageMetaDataTagIndex.get(UID);
		} else {
			tags = getImageMetaData(UID).getTags();
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
	
	public void saveMoleculeToFile(File directory, Molecule molecule, JsonFactory jfactory) throws IOException {
		File moleculeFile = new File(directory.getAbsolutePath() + "/" + molecule.getUID() + ".json");
		OutputStream stream = new BufferedOutputStream(new FileOutputStream(moleculeFile));
		
		JsonGenerator jGenerator = jfactory.createGenerator(stream);
		molecule.toJSON(jGenerator);
		jGenerator.close();
		
		stream.flush();
		stream.close();
	}
	
	public void saveImageMetaDataToFile(File directory, MARSImageMetaData imageMetaData, JsonFactory jfactory) throws IOException {
		File imageMetaDataFile = new File(directory.getAbsolutePath() + "/" + imageMetaData.getUID() + ".json");
		OutputStream stream = new BufferedOutputStream(new FileOutputStream(imageMetaDataFile));
		
		JsonGenerator jGenerator = jfactory.createGenerator(stream);
		imageMetaData.toJSON(jGenerator);
		jGenerator.close();
		
		stream.flush();
		stream.close();
	}
	
	public void updateImageMetaDataTagIndex(String updateUID) {
		updateImageMetaDataTagIndex(getImageMetaData(updateUID));
	}
	
	public void updateImageMetaDataTagIndex(MARSImageMetaData metaData) {
		if (virtual) {
			if (metaData.getTags() == null) {
				//Shouldn't happen..do nothing...
			} else if (metaData.getTags().size() > 0) {
				imageMetaDataTagIndex.put(metaData.getUID(), metaData.getTags());
			} else {
				imageMetaDataTagIndex.remove(metaData.getUID());
			}
		}
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
	
	//This will check if a molecule has a tag in the index
	//by just checking in the index retrieving records
	//based on tags will be much faster for virtual
	//storage
	public boolean moleculeHasTag(String UID, String tag) {
		if (UID != null && tag != null) {
			if (virtual) {
				if (tagIndex.get(UID).contains(tag)) {
					return true;
				} else {
					return false;
				}
			} else {
				return get(UID).hasTag(tag);
			}
		}
		return false;
	}
	
	//This will check if an imageMetaData record has a tag in the index
	//by just checking in the index retreiving records
	//based on tags will be much faster for virtual
	//storage
	public boolean imageMetaDataHasTag(String UID, String tag) {
		if (UID != null && tag != null) {
			if (virtual) {
				if (imageMetaDataTagIndex.get(UID).contains(tag)) {
					return true;
				} else {
					return false;
				}
			} else {
				return getImageMetaData(UID).hasTag(tag);
			}
		}
		return false;
	}
	
	public void remove(String UID) {
		synchronized(moleculeIndex) {
			moleculeIndex.remove(UID);
		}
		if (virtual) {
			File moleculeFile = new File(file.getAbsolutePath() + "/Molecules/" + UID + ".json");
			if (moleculeFile.exists())
				moleculeFile.delete();
			virtualMoleculesSet.remove(UID);
		} else {
			molecules.remove(UID);
		}
	}
	
	public void remove(Molecule molecule) {
		remove(molecule.getUID());
	}
	
	//At the moment this method doesn't use the index for safely reasons
	//If we are sure the index is alway correct then this can be changed.
	public void deleteMoleculesWithTag(String tag) {
		ArrayList<String> newMoleculeIndex = new ArrayList<String>();
		
		for (String UID : moleculeIndex) {
			Molecule molecule = get(UID);
			
			if (molecule.hasTag(tag)) {
				if (virtual) {
					File moleculeFile = new File(file.getAbsolutePath() + "/Molecules/" + UID + ".json");
					if (moleculeFile.exists())
						moleculeFile.delete();
					virtualMoleculesSet.remove(UID);
				}
				tagIndex.remove(UID);
			} else {
				newMoleculeIndex.add(molecule.getUID());
			}
		}
		
		moleculeIndex = newMoleculeIndex;
		archiveProperties.setNumberOfMolecules(moleculeIndex.size());	
	}
	
	//At the moment this method doesn't use the index for safely reasons
	//If we are sure the index is always correct then this can be changed.
	public void deleteImageMetaDataRecordsWithTag(String tag) {
		//We should do this with streams but for the moment this is faster
		ArrayList<String> newImageMetaDataIndex = new ArrayList<String>();
		
		for (String UID : imageMetaDataIndex) {
			MARSImageMetaData metaData = getImageMetaData(UID);
			
			if (metaData.hasTag(tag)) {
				if (virtual) {
					File imageMetaDataFile = new File(file.getAbsolutePath() + "/ImageMetaData/" + UID + ".json");
					if (imageMetaDataFile.exists())
						imageMetaDataFile.delete();
					virtualImageMetaDataSet.remove(metaData.getUID());
				}
				imageMetaDataTagIndex.remove(UID);			
			} else {
				newImageMetaDataIndex.add(metaData.getUID());
			}
		}
		
		imageMetaDataIndex = newImageMetaDataIndex;
		archiveProperties.setNumImageMetaData(imageMetaDataIndex.size());
	}
	
	public boolean contains(String UID) {
		if (virtual) {
			return virtualMoleculesSet.contains(UID);
		} else {
			return molecules.containsKey(UID);
		}
	}
	
	public boolean containsMetaDataRecord(String UID) {
		if (virtual) {
			return virtualImageMetaDataSet.contains(UID);
		} else {
			return imageMetaData.containsKey(UID);
		}
	}

	//Retrieve molecule based on UID key
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
	
	public void addLogMessage(String message) {
		for (String metaUID : imageMetaDataIndex) {
			if (virtual) {
				MARSImageMetaData meta = getImageMetaData(metaUID);
				meta.addLogMessage(message);
				putImageMetaData(meta);
			} else {
				imageMetaData.get(metaUID).addLogMessage(message);
			}
		}
	}
	
	public MoleculeArchiveProperties getProperties() {
		return archiveProperties;
	}
	
	public void updateProperties() {
		archiveProperties.setNumberOfMolecules(moleculeIndex.size());
		archiveProperties.setNumImageMetaData(imageMetaDataIndex.size());
		
		LinkedHashSet<String> newTagSet = new LinkedHashSet<String>();
		if (virtual) {
			for (String UID : tagIndex.keySet())
				newTagSet.addAll(tagIndex.get(UID));
		} else {
			for (String UID : moleculeIndex)
				newTagSet.addAll(get(UID).getTags());
		}
		
		archiveProperties.setTagSet(newTagSet);
		
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
