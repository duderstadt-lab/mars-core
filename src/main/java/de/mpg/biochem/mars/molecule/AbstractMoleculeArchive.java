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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.scijava.ui.DialogPrompt.MessageType;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.*;

import de.mpg.biochem.mars.ImageProcessing.MoleculeIntegrator;
import de.mpg.biochem.mars.ImageProcessing.PeakTracker;
import de.mpg.biochem.mars.kcp.commands.KCPCommand;
import de.mpg.biochem.mars.kcp.commands.SegmentDistributionBuilderCommand;
import de.mpg.biochem.mars.kcp.commands.SigmaCalculatorCommand;
import de.mpg.biochem.mars.molecule.commands.BuildArchiveFromTableCommand;
import de.mpg.biochem.mars.molecule.commands.DriftCalculatorCommand;
import de.mpg.biochem.mars.molecule.commands.DriftCorrectorCommand;
import de.mpg.biochem.mars.molecule.commands.ImportMoleculeArchiveCommand;
import de.mpg.biochem.mars.molecule.commands.MSDCalculatorCommand;
import de.mpg.biochem.mars.molecule.commands.RegionDifferenceCalculatorCommand;
import de.mpg.biochem.mars.table.GroupIndices;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.*;

import static java.util.stream.Collectors.toList;

import org.scijava.table.*;

/**
 * Primary storage structure for MARS datasets. The MoleculeArchive provides an optimal structure 
 * for storing single-molecule time-series data. Time-series data for each molecule in a dataset are 
 * stored in the form of {@link Molecule} records, which may also contain calculated parameters, tags, 
 * notes, and kinetic change point segments. These records are assigned a UID string at the time of creation.
 * This string provides univeral molecule uniqueness throughout all datasets. MoleculeArchives 
 * contain a collection of molecule records associated with a given experimental condition or analysis 
 * pipeline.
 * <p>
 * {@link MarsImageMetadata} records containing data collection information are also stored 
 * in MoleculeArchives. They are identified using metaUID strings. {@link Molecule} records 
 * associated with a given data collection have a metaUID string linking them
 * to the correct {@link MarsImageMetadata} record within the same MoleculeArchive. 
 * 
 * Global properties of the MoleculeArchive, including indexing, comments, etc.., are stored 
 * in a {@link MoleculeArchiveProperties} record also contained within the MoleculeArchive. 
 * <p>
 * Multiple MoleculeArchives can easily be merged with all {@link Molecule} records preserving uniqueness 
 * due to their UIDs and unique {@link MarsImageMetadata} information. After a merge the archive will 
 * then have additional {@link Molecule} records and {@link MarsImageMetadata} records.
 * </p>
 * <p>
 * Multithreaded processing is straightforward with this data structure because individual 
 * molecule records in a MoleculeArchive can be accessed simultaneously. Additionally, this 
 * structure allows for seamless virtual storage in which molecule records are stored as 
 * individual files within an archive folder. These records are loaded and saved as needed 
 * with only a few records residing in memory at any given time. 
 * </p>
 * <p>
 * MoleculeArchives are generated by several MARS commands, 
 * including {@link PeakTracker}, {@link MoleculeIntegrator} and {@link BuildArchiveFromTableCommand}. 
 * Required input for all commands in the molecule package, including {@link RegionDifferenceCalculatorCommand}, 
 * {@link MSDCalculatorCommand}, {@link DriftCorrectorCommand}, {@link DriftCalculatorCommand}
 * as well as commands in the kcp package, including 
 * {@link KCPCommand}, {@link SigmaCalculatorCommand}, and {@link SegmentDistributionBuilderCommand}.
 * </p>
 * <p>
 * MoleculeArchives can be saved in json (or smile) format as a single file or in a virtual store using the 
 * {@link #saveAs(File)} and {@link #saveAsVirtualStore(File)} methods, respectively. These files and folders
 * will have a .yama extension for Yet Another MoleculeArchive or .yama.store extension to indicate a virtual store. 
 * The files have a json or smile format with the .yama extension indicating they contain certain expected object 
 * and field names and value types.
 * </p>
 * <p>
 * MoleculeArchives can be loaded using the constructors {@link #AbstractMoleculeArchive(File)} or 
 * {@link #AbstractMoleculeArchive(String, File, MoleculeArchiveService)}. Otherwise, MoleculeArchives can be loaded
 * using the MARS command {@link ImportMoleculeArchiveCommand} through the 
 * GUI or in scripts.
 * </p>
 * @author Karl Duderstadt
 */
public abstract class AbstractMoleculeArchive<M extends Molecule, I extends MarsImageMetadata, P extends MoleculeArchiveProperties> 
	implements MoleculeArchive<M, I, P> {
	
	private String name;
	
	private MoleculeArchiveWindow win;
	
	//Services that the archive will need access to but that are not initialized..
	private MoleculeArchiveService moleculeArchiveService;
	
	//Can be a .yama file or a directory containing a virtual store
	private File file;
	
	//This is the global factory used for parsing
	//will be set for either json or smile
	//and all uses will follow...
	private JsonFactory jfactory;
	
	private P archiveProperties;
	
	//This will maintain a list of the metaDatasets as an index with UID keys for each..
	//Need to make sure all write operations are placed within synchronized blocks. synchronized(imageMetadataIndex) { ... }
	//To avoid thread issues.
	//All read operations can be done in parallel no problem.
	private ArrayList<String> imageMetadataIndex;
	
	//This will store all the ImageMetadata sets associated with the molecules
	//molecules have a metadataUID that maps to these keys so it is clear which dataset they were from.
	private ConcurrentMap<String, I> imageMetadata;
	
	//This is a list of molecule keys that will define the index and be used for retrieval from the ChronicleMap in virtual memory
	//or retrieval from the molecules array in memory
	//This array defines the absolute set of molecules considered to be in the archive for purposes of saving and reading etc...
	//Need to make sure all write operations are placed within synchronized blocks. synchronized(moleculeIndex) { ... }
	//To avoid thread issues.
	//All read operations can be done in parallel no problem.
	private ArrayList<String> moleculeIndex;
	
	//This is a map index of tags for searching in molecule tables etc..
	private ConcurrentMap<String, LinkedHashSet<String>> tagIndex, imageMetadataTagIndex;
	
	//this is a map of molecule UIDs to imageMetadataUID for virtual storage indexing...
	private ConcurrentMap<String, String> moleculeImageMetadataUIDIndex;
	
	//This is a map from keys to molecules if working in memory..
	//Otherwise if working in virtual memory it is left null..
	private ConcurrentMap<String, M> molecules;
	
	//If true we work in virtual memory
	private boolean virtual;
	
	private Set<String> virtualMoleculesSet, virtualImageMetadataSet;
	
	//determines whether the file is encoded in binary smile format
	private boolean outputSmileEncoding = true;
	
	//For virtual archives we must keep track of the 
	//encoding when it was loaded so we always parse correctly
	//even if the user changed the format in the properties panel.
	private boolean inputSmileEncoding = true;
	
	//Need to determine the number of threads
	private final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
	
	/**
	 * Constructor for creating an empty MoleculeArchive. 
	 * 
	 * @param name The name archive.
	 */
	public AbstractMoleculeArchive(String name) {
		this.name = name;
		this.virtual = false;
		
		initializeVariables();
	}
	
	/**
	 * Constructor for loading a MoleculeArchive. A
	 * yama file can be given or a yama virtual 
	 * store directory. Virtual mode will automatically
	 * be activated if a directory is provided.
	 * 
	 * @param file The file or directory to load the archive from.
	 * 
	 * @throws JsonParseException if there is a problem parsing the file provided.
	 * @throws IOException if there is a problem with the file location.
	 */
	public AbstractMoleculeArchive(File file) throws JsonParseException, IOException {
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
	
	/**
	 * Constructor for loading a MoleculeArchive. A
	 * yama file can be given or a yama virtual 
	 * store directory. Virtual mode will automatically
	 * be activated if a directory is provided.
	 * 
	 * If the MoleculeArchiveService is provided the statusService
	 * will be retrieved and when working in Fiji the progress
	 * shows up in the bar as molecule records are loaded.
	 * 
	 * @param name The name of the archive.
	 * @param file The file or directory to load the archive from.
	 * @param moleculeArchiveService The MoleculeArchiveService from
	 * the current context.
	 * 
	 * @throws JsonParseException if there is a parsing exception.
	 * @throws IOException if there is a problem with the file provided.
	 */
	public AbstractMoleculeArchive(String name, File file, MoleculeArchiveService moleculeArchiveService) throws JsonParseException, IOException {
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
	
	/**
	 * Constructor for building a molecule archive from a MARSResultsTable.
	 * The table provided must contain a molecule column. The integer values
	 * in the molecule column determine the grouping for creation of 
	 * molecule records.
	 * 
	 * Status will be reported during processing by retrieving the StatusService
	 * from the MoleculeArchiveService instance.
	 * 
	 * @param name The name of the archive.
	 * @param table A MARSResultsTable to build the archive from.
	 * @param moleculeArchiveService The MoleculeArchiveService from
	 * the current context.
	 */
	public AbstractMoleculeArchive(String name, MarsTable table, MoleculeArchiveService moleculeArchiveService) {
		this.name = name;
		this.virtual = false;
		this.moleculeArchiveService = moleculeArchiveService;
		
		initializeVariables();
		
		buildFromTable(table);
	}
	
	/**
	 * Constructor for building a molecule archive from a MARSResultsTable.
	 * The table provided must contain a molecule column. The integer values
	 * in the molecule column determine the grouping for creation of 
	 * molecule records.
	 * 
	 * No status update are provided during processing.
	 * 
	 * @param name The name of the archive.
	 * @param table A MARSResultsTable to build the archive from.
	 */
	public AbstractMoleculeArchive(String name, MarsTable table) {
		this.name = name;
		this.virtual = false;
		
		initializeVariables();
		
		buildFromTable(table);
	}
	
	private void initializeVariables() {
		moleculeIndex = new ArrayList<String>();  
		imageMetadataIndex = new ArrayList<String>(); 
		
		if (virtual) {
			tagIndex = new ConcurrentHashMap<>();
			imageMetadataTagIndex = new ConcurrentHashMap<>();
			moleculeImageMetadataUIDIndex = new ConcurrentHashMap<>();
			
			virtualMoleculesSet = ConcurrentHashMap.newKeySet();
			virtualImageMetadataSet = ConcurrentHashMap.newKeySet();
		} else {
			molecules = new ConcurrentHashMap<>();
			imageMetadata = new ConcurrentHashMap<>();
		}
		
		archiveProperties = createProperties();
		archiveProperties.setParent(this);
	}
	
	protected void loadVirtualStore(File file) throws JsonParseException, IOException {
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
		
		archiveProperties.fromJSON(propertiesJParser);
		propertiesJParser.close();
		propertiesInputStream.close();
		
		//Now load in moleculeIndex
		File indexFile = new File(file.getAbsolutePath() + "/indexes.json");
		if (indexFile.exists()) {
			InputStream indexInputStream = new BufferedInputStream(new FileInputStream(indexFile));
			
		    JsonParser indexJParser = jfactory.createParser(indexInputStream);
			
		    indexJParser.nextToken();
		    String fieldBlockName = "";
		    while (indexJParser.nextToken() != JsonToken.END_OBJECT) {
		    	String fieldname = indexJParser.getCurrentName();
			    
		    	if (fieldname == null)
			    	continue;
			    else 
			    	fieldBlockName = fieldname;
		    	
			    if ("imageMetaDataIndex".equals(fieldname) || "ImageMetadataIndex".equals(fieldname)) {
			    	indexJParser.nextToken();
			    	while (indexJParser.nextToken() != JsonToken.END_ARRAY) {
		    			String metaUID = "NULL";
			    		while (indexJParser.nextToken() != JsonToken.END_OBJECT) {
			    			if("UID".equals(indexJParser.getCurrentName())) {
			    				indexJParser.nextToken();
			    				metaUID = indexJParser.getText();
				    			virtualImageMetadataSet.add(metaUID);
				    			imageMetadataIndex.add(metaUID);
			    			}
			    			
			    			if ("Tags".equals(indexJParser.getCurrentName())) {
			    				indexJParser.nextToken();
			    				LinkedHashSet<String> tags = new LinkedHashSet<String>();
			    		    	while (indexJParser.nextToken() != JsonToken.END_ARRAY) {
			    		            tags.add(indexJParser.getText());
			    		        }
			    		    	if (!metaUID.equals("NULL"))
			    		    		imageMetadataTagIndex.put(metaUID, tags);
			    			}
			    		}
			    		
			        }
			    	continue;
			    }
			    
			    if("moleculeIndex".equals(fieldname) || "MoleculeIndex".equals(fieldname)) {
			    	indexJParser.nextToken();
		    		while (indexJParser.nextToken() != JsonToken.END_ARRAY) {
		    			String UID = "NULL";
			    		while (indexJParser.nextToken() != JsonToken.END_OBJECT) {
			    			if("UID".equals(indexJParser.getCurrentName())) {
			    				indexJParser.nextToken();
			    				UID = indexJParser.getText();
			    					virtualMoleculesSet.add(UID);
					    			moleculeIndex.add(UID);
			    			}
			    			
			    			if ("ImageMetaDataUID".equals(indexJParser.getCurrentName()) || "ImageMetadataUID".equals(indexJParser.getCurrentName())) {
			    				indexJParser.nextToken();
			    				moleculeImageMetadataUIDIndex.put(UID, indexJParser.getText());
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
			    //In that case we simply pass through it and all substructures that contain arrays or objects
			    if (indexJParser.getCurrentToken() == JsonToken.START_OBJECT) {
			    	System.out.println("unknown object " + fieldBlockName + " encountered in the record ... skipping");
			    	MarsUtil.passThroughUnknownObjects(indexJParser);
			    } else if (indexJParser.getCurrentToken() == JsonToken.START_ARRAY) {
			    	System.out.println("unknown array " + fieldBlockName + " encountered in the record ... skipping");
			    	MarsUtil.passThroughUnknownArrays(indexJParser);
			    } else {
			    	//Must just be a normal field... so it won't escape the loop prematurely.
			    }
		    }
		    
			indexJParser.close();
			indexInputStream.close();
		} else {
			System.out.println("No indexes.json file found. Rebuilding indexes... This might take a while...");
			rebuildIndexes();
		}
		//updateProperties();
	}
	
	protected void load(File file) throws JsonParseException, IOException {
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
		
	    fromJSON(jParser);
		
		jParser.close();
		inputStream.close();
		
		//Once we are done reading we should update the indexes
		rebuildIndexes();	
	}
	
	private void buildFromTable(MarsTable results) {
		//First we have to index the groups in the table to determine the number of Molecules and their average size...
		//Here we assume their is a molecule column that defines which data is related to which molecule.
		LinkedHashMap<Integer, GroupIndices> groups = MarsTableService.find_group_indices(results, "molecule");
		
		//We need to generate and add an ImageMetadata entry for the molecules from the the table
		//This will basically be empty, but as further processing steps occurs the log will be filled in
		//Also, the DataTable can be updated manually.
		String metaUID = MarsMath.getUUID58().substring(0, 10);
		I meta = createImageMetadata(metaUID);
		putImageMetadata(meta);

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
			MarsTable molTable = new MarsTable();
			for (String header: headers) {
				molTable.add(new DoubleColumn(header));
			}
			int row = 0;
			for (int j=groups.get(mol).getStart();j<=groups.get(mol).getEnd();j++) {
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
			M molecule = createMolecule(MarsMath.getUUID58(), molTable);
			molecule.setImageMetadataUID(metaUID);
			put(molecule);
		}
	}
	
	/**
	 * Rebuild all indexes by inspecting the contents of store directories. 
	 * Then save the new indexes to the indexes.json file in the store. 
	 * 
	 * @throws IOException if something goes wrong saving the indexes.
	 */
	public void rebuildIndexes() throws IOException {
		lock();
		
		//Global sets stored in MoleculeArchiveProperties
		Set<String> newParameterSet = ConcurrentHashMap.newKeySet();
		Set<String> newTagSet = ConcurrentHashMap.newKeySet();
		Set<String> newMoleculeDataTableColumnSet = ConcurrentHashMap.newKeySet();
		Set<ArrayList<String>> newMoleculeSegmentTableNames = ConcurrentHashMap.newKeySet();
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
		
		if (virtual) {
			//First we get file lists from ImageMetadata and Molecule Directories
			//these are considered the new moleculeIndex and imageMetadataIndex
			String[] moleculeFileNameIndex = new File(file.getAbsolutePath() + "/Molecules").list(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(".json");
				}
			});
			
			Set<String> newVirtualMoleculesSet = ConcurrentHashMap.newKeySet();
			ArrayList<String> newMoleculeIndex = new ArrayList<String>();
			for (int i=0;i<moleculeFileNameIndex.length;i++) {
				String UID = moleculeFileNameIndex[i].substring(0, moleculeFileNameIndex[i].length() - 5);
				newMoleculeIndex.add(UID);
				newVirtualMoleculesSet.add(UID);
			}
			
			String[] imageMetadataFileNameIndex = new File(file.getAbsolutePath() + "/ImageMetadata").list(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(".json");
				}
			});
			
			Set<String> newVirtualImageMetadataSet = ConcurrentHashMap.newKeySet();
			ArrayList<String> newImageMetadataIndex = new ArrayList<String>();
			for (int i=0;i<imageMetadataFileNameIndex.length;i++) {
				String UID = imageMetadataFileNameIndex[i].substring(0, imageMetadataFileNameIndex[i].length() - 5);
				newImageMetadataIndex.add(UID);
				newVirtualImageMetadataSet.add(UID);
			}
			
			ConcurrentMap<String, LinkedHashSet<String>> newTagIndex = new ConcurrentHashMap<>();
			ConcurrentMap<String, LinkedHashSet<String>> newTmageMetadataTagIndex = new ConcurrentHashMap<>();
			
			ConcurrentMap<String, String> newMoleculeImageMetadataUIDIndex = new ConcurrentHashMap<>();
			
		   try {
		        forkJoinPool.submit(() -> newMoleculeIndex.parallelStream().forEach(UID -> { 
		        	M molecule = get(UID);
		        	newTagIndex.put(UID, molecule.getTags());
		        	newMoleculeImageMetadataUIDIndex.put(UID, molecule.getImageMetadataUID());
		        	
		        	newParameterSet.addAll(molecule.getParameters().keySet());
		        	newTagSet.addAll(molecule.getTags());
		        	newMoleculeDataTableColumnSet.addAll(molecule.getDataTable().getColumnHeadingList());
		        	newMoleculeSegmentTableNames.addAll(molecule.getSegmentTableNames());
		        })).get();    
		        
		        forkJoinPool.submit(() -> newImageMetadataIndex.parallelStream().forEach(metaUID -> { 
		        	I metaData = getImageMetadata(metaUID);
		        	newTmageMetadataTagIndex.put(metaUID, metaData.getTags());
		        })).get();
		   } catch (InterruptedException | ExecutionException e ) {
		        // handle exceptions
		    	e.printStackTrace();
		   } finally {
		      forkJoinPool.shutdown();
		   }
			
		   this.moleculeIndex = (ArrayList<String>)newMoleculeIndex.stream().sorted().collect(toList());
		   this.imageMetadataIndex = (ArrayList<String>)newImageMetadataIndex.stream().sorted().collect(toList());
		   this.tagIndex = newTagIndex;
		   this.moleculeImageMetadataUIDIndex = newMoleculeImageMetadataUIDIndex;
		   this.imageMetadataTagIndex = newTmageMetadataTagIndex;
		   
		   this.virtualMoleculesSet = newVirtualMoleculesSet;
		   this.virtualImageMetadataSet = newVirtualImageMetadataSet;
		   
		   archiveProperties.setTagSet(newTagSet);
		   archiveProperties.setParameterSet(newParameterSet);
		   archiveProperties.setColumnSet(newMoleculeDataTableColumnSet);
		   archiveProperties.setSegmentTableNames(newMoleculeSegmentTableNames);
			
		   updateProperties();
		   saveIndexes();
		} else {
			//If working in memory we just need to update the global sets..
			
			try {
		        forkJoinPool.submit(() -> moleculeIndex.parallelStream().forEach(UID -> { 
		        	M molecule = get(UID);
		        	
		        	newParameterSet.addAll(molecule.getParameters().keySet());
		        	newTagSet.addAll(molecule.getTags());
		        	newMoleculeDataTableColumnSet.addAll(molecule.getDataTable().getColumnHeadingList());
		        	newMoleculeSegmentTableNames.addAll(molecule.getSegmentTableNames());
		        })).get();    
		   } catch (InterruptedException | ExecutionException e ) {
		        // handle exceptions
		    	e.printStackTrace();
		   } finally {
		      forkJoinPool.shutdown();
		   }
			
			archiveProperties.setTagSet(newTagSet);
			archiveProperties.setParameterSet(newParameterSet);
			archiveProperties.setColumnSet(newMoleculeDataTableColumnSet);
			archiveProperties.setSegmentTableNames(newMoleculeSegmentTableNames);	
			
			updateProperties();
		}
		
		unlock();
	}
	
	private void saveIndexes() throws IOException {
		saveIndexes(file, moleculeIndex, imageMetadataIndex, moleculeImageMetadataUIDIndex, imageMetadataTagIndex, tagIndex, jfactory);
	}
	
	private void saveIndexes(File directory, ArrayList<String> moleculeIndex, ArrayList<String> imageMetadataIndex, ConcurrentMap<String, String> moleculeImageMetadataUIDIndex, ConcurrentMap<String, LinkedHashSet<String>> imageMetadataTagIndex, ConcurrentMap<String, LinkedHashSet<String>> tagIndex, JsonFactory jfactory) throws IOException {
		File indexFile = new File(directory.getAbsolutePath() + "/indexes.json");
		OutputStream stream = new BufferedOutputStream(new FileOutputStream(indexFile));
		
		JsonGenerator jGenerator = jfactory.createGenerator(stream);
		
		jGenerator.writeStartObject();

		//Write imageMetadataIndex
		jGenerator.writeFieldName("ImageMetadataIndex");
		jGenerator.writeStartArray();
		for (String metaUID : imageMetadataIndex) {
			jGenerator.writeStartObject();
			jGenerator.writeStringField("UID", metaUID);
			
			if (imageMetadataTagIndex.containsKey(metaUID)) {
				jGenerator.writeArrayFieldStart("Tags");
				for (String tag : imageMetadataTagIndex.get(metaUID)) {
					jGenerator.writeString(tag);
				}
				jGenerator.writeEndArray();
			}
			
			jGenerator.writeEndObject();
		}
		jGenerator.writeEndArray();
		
		//Write moleculeIndex
		jGenerator.writeArrayFieldStart("MoleculeIndex");
		for (String UID : moleculeIndex) {
			jGenerator.writeStartObject();
			jGenerator.writeStringField("UID", UID);
			jGenerator.writeStringField("ImageMetadataUID", moleculeImageMetadataUIDIndex.get(UID));
			
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
		
        Files.setPosixFilePermissions(indexFile.toPath(), MarsUtil.ownerGroupPermissions);
	}
	
	/**
	 * Saves the MoleculeAchive to the file from which it was opened.
	 * 
	 * @throws IOException if something goes wrong saving the data.
	 */
	public void save() throws IOException {
		if (virtual) {
			updateProperties();
			saveIndexes();
		} else
			saveAs(file);
	}
	
	/**
	 * Saves MoleculeAchive to the given file destination. 
	 * 
	 * @param file a yama file destination. If the .yama is not present it will be added.
	 * @throws IOException if something goes wrong saving the data.
	 */
	public void saveAs(File file) throws IOException {
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
		
		toJSON(jGenerator);
		
		jGenerator.close();
		
		//flush and close streams...
		stream.flush();
		stream.close();
		
        Files.setPosixFilePermissions(file.toPath(), MarsUtil.ownerGroupPermissions);
	}
	
	/**
	 * Write the MoleculeArchive record to JSON. Uses the provided
	 * JsonGenerator created elsewhere to stream the MoleculeArchive
	 * record to JSON.
	 * 
	 * @param jGenerator A JsonGenerator for stream the MoleculeArchive
	 * record to JSON.
	 * 
	 * @throws IOException if there is a problem writing to the file.
	 */
	public void toJSON(JsonGenerator jGenerator) throws IOException {
		jGenerator.writeStartObject();
		
		updateProperties();
		
		jGenerator.writeFieldName("MoleculeArchiveProperties");
		archiveProperties.toJSON(jGenerator);
		
		if (imageMetadataIndex.size() > 0) {
			jGenerator.writeArrayFieldStart("ImageMetadata");
			Iterator<String> iter = imageMetadataIndex.iterator();
			while (iter.hasNext()) {
				getImageMetadata(iter.next()).toJSON(jGenerator);
			}
			jGenerator.writeEndArray();
		}
		
		jGenerator.writeArrayFieldStart("Molecules");
		Iterator<String> iterator = moleculeIndex.iterator();
		while (iterator.hasNext()) {
			get(iterator.next()).toJSON(jGenerator);
		}
		jGenerator.writeEndArray();
		
		jGenerator.writeEndObject();
	}
	
	/**
	 * Read a MoleculeArchive from JSON. Load a MoleculeArchive record
	 * using the JsonParser stream provided. Only for non-virtual archives.
	 * If the archive is virtual, different archive objects are stored in 
	 * individual files whereas this excepts a JsonParser streaming from 
	 * a single complete MoleculeArchive yama file.
	 * 
	 * @param jParser A JsonParser for loading MoleculeArchives.
	 * 
     * @throws IOException if there is a problem reading from the stream.
	 */
	public void fromJSON(JsonParser jParser) throws IOException {
		jParser.nextToken();
		jParser.nextToken();
		if ("MoleculeArchiveProperties".equals(jParser.getCurrentName())) {
			jParser.nextToken();
			archiveProperties.fromJSON(jParser);
		} else {
			if (moleculeArchiveService != null)
				moleculeArchiveService.getUIService().showDialog("No MoleculeArchiveProperties found. Are you sure this is a yama file?", MessageType.ERROR_MESSAGE);
			return;
		}

		int numMolecules = archiveProperties.getNumberOfMolecules();
		
		String fieldBlockName = "";
		while (jParser.nextToken() != JsonToken.END_OBJECT) {
			String fieldName = jParser.getCurrentName();
			
			if (fieldName == null)
		    	continue;
		    else 
		    	fieldBlockName = fieldName;
			
			if ("ImageMetaData".equals(fieldName) || "ImageMetadata".equals(fieldName)) {
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					putImageMetadata(createImageMetadata(jParser));
				}
			}
			
			if ("Molecules".equals(fieldName)) {
				int molNum = 0;
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					put(createMolecule(jParser));
					molNum++;
					if (moleculeArchiveService != null)
						moleculeArchiveService.getStatusService().showStatus(molNum, numMolecules, "Loading molecules from " + file.getName());
				}
			}
			
			//SHOULD BE UNREACHABLE
		    //This is only reached if there is an unexpected field added to the json record
		    //In that case we simply pass through it and all substructures that contain arrays or objects
		    if (jParser.getCurrentToken() == JsonToken.START_OBJECT) {
		    	System.out.println("unknown object " + fieldBlockName + " encountered in the record ... skipping");
		    	MarsUtil.passThroughUnknownObjects(jParser);
		    } else if (jParser.getCurrentToken() == JsonToken.START_ARRAY) {
		    	System.out.println("unknown array " + fieldBlockName + " encountered in the record ... skipping");
		    	MarsUtil.passThroughUnknownArrays(jParser);
		    } else {
		    	//Must just be a normal field... so it won't escape the loop prematurely.
		    }
		}
	}
	
	/**
	 * Creates the directory given and a virtual store inside. 
	 * Rebuilds indexes in the process if the archive was loaded
	 * from a virtual store.
	 * 
	 * @param virtualDirectory a directory destination for the virtual store.
	 * @throws IOException if something goes wrong creating the virtual store.
	 */
	public void saveAsVirtualStore(File virtualDirectory) throws IOException {
		virtualDirectory.mkdirs();
		File imageMetaDataDir = new File(virtualDirectory.getAbsolutePath() + "/ImageMetadata");
		File moleculesDir = new File(virtualDirectory.getAbsolutePath() + "/Molecules");
		
		imageMetaDataDir.mkdirs();
		moleculesDir.mkdirs();
		
		//Set directory permissions
		Files.setPosixFilePermissions(virtualDirectory.toPath(), MarsUtil.ownerGroupPermissions);
		Files.setPosixFilePermissions(imageMetaDataDir.toPath(), MarsUtil.ownerGroupPermissions);
		Files.setPosixFilePermissions(moleculesDir.toPath(), MarsUtil.ownerGroupPermissions);

		//We will generate the index as we save records...
		ConcurrentMap<String, LinkedHashSet<String>> newTagIndex = new ConcurrentHashMap<>();
		ConcurrentMap<String, LinkedHashSet<String>> newImageMetadataTagIndex = new ConcurrentHashMap<>();
		ConcurrentMap<String, String> newMoleculeImageMetadataUIDIndex = new ConcurrentHashMap<>();
		
		//Let's also rebuild the parameter index stored in the archiveProperties
		Set<String> newParameterSet = ConcurrentHashMap.newKeySet();
		Set<String> newTagSet = ConcurrentHashMap.newKeySet();
		Set<String> newMoleculeDataTableColumnSet = ConcurrentHashMap.newKeySet();
		
		Set<ArrayList<String>> newMoleculeSegmentTableNames = ConcurrentHashMap.newKeySet();
		
		//build a new factory just for this output run...
		JsonFactory jfactory;
		if (outputSmileEncoding) {
			jfactory = new SmileFactory();
		} else {
			jfactory = new JsonFactory();
		}
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
		
		try {
			forkJoinPool.submit(() -> imageMetadataIndex.parallelStream().forEach(metaUID -> { 
	        	try {
	        		I metaData = getImageMetadata(metaUID);
	        		newImageMetadataTagIndex.put(metaUID, metaData.getTags());
					saveImageMetadataToFile(new File(virtualDirectory.getAbsolutePath() + "/ImageMetadata"), metaData, jfactory);
	        	} catch (IOException e) {
	        		e.printStackTrace();
	        	}
	        })).get();
			
			//Generate all molecule record files and indexes as the same time...
	        forkJoinPool.submit(() -> moleculeIndex.parallelStream().forEach(UID -> { 
	        	M molecule = get(UID);
	        	newTagIndex.put(UID, molecule.getTags());
	        	newMoleculeImageMetadataUIDIndex.put(UID, molecule.getImageMetadataUID());
	        	
	        	newParameterSet.addAll(molecule.getParameters().keySet());
	        	newTagSet.addAll(molecule.getTags());
	        	newMoleculeDataTableColumnSet.addAll(molecule.getDataTable().getColumnHeadingList());
	        	
	        	newMoleculeSegmentTableNames.addAll(molecule.getSegmentTableNames());
	        	
	        	archiveProperties.addAllColumns(molecule.getDataTable().getColumnHeadingList());
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
		
		if (virtual) {
			this.tagIndex = newTagIndex;
	    	this.moleculeImageMetadataUIDIndex = newMoleculeImageMetadataUIDIndex;
		}
		
		archiveProperties.setTagSet(newTagSet);
		archiveProperties.setParameterSet(newParameterSet);
		archiveProperties.setColumnSet(newMoleculeDataTableColumnSet);
		archiveProperties.setSegmentTableNames(newMoleculeSegmentTableNames);

		//Save archive properties
		File propertiesFile = new File(virtualDirectory.getAbsolutePath() + "/MoleculeArchiveProperties.json");
		OutputStream stream = new BufferedOutputStream(new FileOutputStream(propertiesFile));
		
		JsonGenerator jGenerator = jfactory.createGenerator(stream);
		archiveProperties.toJSON(jGenerator);
		jGenerator.close();
		
		stream.flush();
		stream.close();
		
        Files.setPosixFilePermissions(propertiesFile.toPath(), MarsUtil.ownerGroupPermissions);
			
		//Generate indexes file using moleculeIndex and imageMetadataIndex of current archive
		//If the current archive is not virtual.. then tagIndex and moleculeImageMetadataUIDIndex
		//were never created.. So here we create local copies as we save records
		//then we save the resulting indexes from the operation..
		//this way virtual or in memory archive can both be saved no problem..
		//In the case of saving virtual archives this method then re-indexes completely.
		saveIndexes(virtualDirectory, moleculeIndex, imageMetadataIndex, newMoleculeImageMetadataUIDIndex, newImageMetadataTagIndex, newTagIndex, jfactory);
	}
	
	/**
	 * Adds a molecule to the archive. If a molecule with the same UID 
	 * is already in the archive, the record is updated.
	 * 
	 * All indexes are updated with the properties of the molecule added.
	 * 
	 * @param molecule a record to add or update.
	 */
	public void put(M molecule) {		
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
			//For the moment we don't through IOException here...
			//Would only occur in virtual store ...
			//Hmm maybe we should throw IOException all the time ?
			try {
				saveMoleculeToFile(new File(file.getAbsolutePath() + "/Molecules"), molecule, jfactory);
			} catch (IOException e) {
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
		archiveProperties.addAllSegmentTableNames(molecule.getSegmentTableNames());
	}
	
	/**
	 * Adds an ImageMetadata record to the archive. If an ImageMetadata record with 
	 * the same UID is already in the archive, the record is updated.
	 * 
	 * All indexes are updated with the properties of the ImageMetadata record added.
	 * 
	 * @param metaData an ImageMetadata record to add or update.
	 */
	public void putImageMetadata(I metaData) {
		if (virtual) {
				if (!virtualImageMetadataSet.contains(metaData.getUID())) {
					synchronized(imageMetadataIndex) {	
						imageMetadataIndex.add(metaData.getUID());
					}
					virtualImageMetadataSet.add(metaData.getUID());
					archiveProperties.setNumImageMetadata(imageMetadataIndex.size());
				}
			try {
				saveImageMetadataToFile(new File(file.getAbsolutePath() + "/ImageMetadata"), metaData, jfactory);
			} catch (IOException e) {
				e.printStackTrace();
			}
			updateImageMetadataTagIndex(metaData);
		} else if (!imageMetadata.containsKey(metaData.getUID())) {
			//If working in memory and the key is already in the map
			//there is only one copy and all changes have already been saved
			//otherwise, we add it as a new record.
			synchronized(imageMetadataIndex) {	
				imageMetadataIndex.add(metaData.getUID());
			}
			metaData.setParent(this);
			imageMetadata.put(metaData.getUID(), metaData);
			archiveProperties.setNumImageMetadata(imageMetadataIndex.size());
		}
	}
	
	/**
	 * The ImageMetadata record with the UID given is removed from the archive. 
	 * All indexes are updated to reflect the change.
	 * 
	 * @param metaUID the UID of the ImageMetadata record to remove.
	 */
	public void removeImageMetadata(String metaUID) {
		synchronized(imageMetadataIndex) {
			imageMetadataIndex.remove(metaUID);
		}
		if (virtual) {
			File metaDataFile = new File(file.getAbsolutePath() + "/ImageMetadata/" + metaUID + ".json");
			if (metaDataFile.exists())
				metaDataFile.delete();
			virtualImageMetadataSet.remove(metaUID);
		} else {
			imageMetadata.remove(metaUID);
		}
	}

	/**
	 * The ImageMetadata record given is removed from the archive. 
	 * All indexes are updated to reflect the change.
	 * 
	 * @param meta ImageMetadata record to remove.
	 */
	public void removeImageMetadata(I meta) {
		removeImageMetadata(meta.getUID());
	}
	
	/**
	 * Retrieves an MARSImageMetadata record.
	 * 
	 * @param index The index of the MARSImageMetadata record to retrieve.
	 * @return A MARSImageMetadata record.
	 */
	public I getImageMetadata(int index) {
		return getImageMetadata(imageMetadataIndex.get(index));
	}
	
	/**
	 * Retrieves a MARSImageMetadata record.
	 * 
	 * @param metaUID The UID of the MARSImageMetadata record to retrieve.
	 * @return A MARSImageMetadata record.
	 */
	public I getImageMetadata(String metaUID) {
		if (virtual) {
			I metaData = null;
			try {
				File metaDataFile = new File(file.getAbsolutePath() + "/ImageMetadata/" + metaUID + ".json");
				InputStream inputStream = new BufferedInputStream(new FileInputStream(metaDataFile));
		
				JsonParser jParser = jfactory.createParser(inputStream);

				metaData = createImageMetadata(jParser);
				
				jParser.close();
				inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return metaData;
		} else {
			return imageMetadata.get(metaUID);
		}
	}
	
	/**
	 * Retrieves the list of UIDs of all MARSImageMetadata records.
	 * Useful for stream().forEach(...) operations.
	 * 
	 * @return The list of all MARImageMetadata UIDs.
	 */
	public final ArrayList<String> getImageMetadataUIDs() {
		return imageMetadataIndex;
	}
	
	/**
	 * Number of molecule records in the MoleculeArchive.
	 * 
	 * @return The integer number of molecule records.
	 */
	public int getNumberOfMolecules() {
		return moleculeIndex.size();
	}
	
	/**
	 * Number of MARSImageMetadata records in the MoleculeArchive.
	 * 
	 * @return The integer number of MARSImageMetadata records.
	 */
	public int getNumberOfImageMetadataRecords() {
		return imageMetadataIndex.size();
	}
	
	/**
	 * Location of the virtual store.
	 * 
	 * @return The String absolute path of the open virtual store.
	 */
	public String getStoreLocation() {
		return file.getAbsolutePath();
	}
	
	/**
	 * Global comments.
	 * 
	 * @return The global comments String.
	 */
	public String getComments() {
		return archiveProperties.getComments();
	}
	
	/**
	 * Sets the global comments. This replaces all current 
	 * comments with those given.
	 * 
	 * @param comments A string of global comments to set.
	 */
	public void setComments(String comments) {
		archiveProperties.setComments(comments);
	}
	
	/**
	 * True if the archive is virtual, false if not.
	 * 
	 * @return A boolean which is true if working from a virtual store.
	 */
	public boolean isVirtual() {
		return virtual;
	}

	/**
	 * Retrieves the molecule record at the provided index.
	 * 
	 * @param index The integer index position of the molecule record.
	 * @return A Molecule record.
	 */
	public M get(int index) {
		return get(moleculeIndex.get(index));
	}
	
	/**
	 * Removes the molecule record with the given UID.
	 * 
	 * @param UID The UID of the molecule record to remove.
	 */
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
	
	/**
	 * Removes the molecule record provided.
	 * 
	 * @param molecule The molecule record to remove.
	 */
	public void remove(M molecule) {
		remove(molecule.getUID());
	}
	
	/**
	 * Retrieves the list of UIDs for all Molecule records. 
	 * Useful for stream().forEach(...) operations.
	 * 
	 * @return The list with all Molecule UIDs.
	 */
	public final ArrayList<String> getMoleculeUIDs() {
		return moleculeIndex;
	}
	
	/**
	 * Comma separated list of tags for the molecule with the given UID.
	 * 
	 * @param UID The UID of the molecule to retrieve the tag list for.
	 * @return A String containing a comma separated list of tags.
	 */
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
	
	/**
	 * Tags for the molecule with the given UID.
	 * 
	 * @param UID The UID of the molecule to retrieve the tag set for.
	 * @return A set containing all tags for the given molecule.
	 */
	public LinkedHashSet<String> getTagSet(String UID) {
		return tagIndex.get(UID);
	}
	
	/**
	 * Comma separated list of tags for the MARSImageMetadata record with the given UID.
	 * 
	 * @param UID The UID of the MARSImageMetadata record to retrieve the tag list for.
	 * @return A String containing a comma separated list of tags.
	 */
	public String getImageMetadataTagList(String UID) {
		LinkedHashSet<String> tags;
		if (UID == null)
			 return null;
		else if (virtual) {
			tags = imageMetadataTagIndex.get(UID);
		} else {
			tags = getImageMetadata(UID).getTags();
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
	
	/**
	 * Tags for the MARSImageMetadata record with the given UID.
	 * 
	 * @param UID The UID of the MARSImageMetadata record to retrieve the tag list for.
	 * @return The set of tags for the given MARSImageMetadata record.
	 */
	public LinkedHashSet<String> getImageMetadataTagSet(String UID) {
		return imageMetadataTagIndex.get(UID);
	}
	
	/**
	 * Saves a molecule record as a json file.
	 * 
	 * @param directory The directory to save the file in.
	 * @param molecule The molecule record to save.
	 * @param jfactory the JsonFactory to use when saving. 
	 * Determines if smile or text encoding is used.
	 * 
	 * @throws IOException if the molecule can't be saved to the file given.
	 */
	public void saveMoleculeToFile(File directory, M molecule, JsonFactory jfactory) throws IOException {
		File moleculeFile = new File(directory.getAbsolutePath() + "/" + molecule.getUID() + ".json");
		OutputStream stream = new BufferedOutputStream(new FileOutputStream(moleculeFile));
		
		JsonGenerator jGenerator = jfactory.createGenerator(stream);
		molecule.toJSON(jGenerator);
		jGenerator.close();
		
		stream.flush();
		stream.close();
		
		Files.setPosixFilePermissions(moleculeFile.toPath(), MarsUtil.ownerGroupPermissions);
	}
	
	/**
	 * Saves a MARSImageMetadata record as a json file.
	 * 
	 * @param directory The directory to save the file in.
	 * @param imageMetadata The MARSImageMetadata record to save.
	 * @param jfactory the JsonFactory to use when saving. 
	 * Determines if smile or text encoding is used.
	 * 
	 * @throws IOException if the MARSImageMetadata can't be saved to the file given.
	 */
	public void saveImageMetadataToFile(File directory, I imageMetadata, JsonFactory jfactory) throws IOException {
		File imageMetadataFile = new File(directory.getAbsolutePath() + "/" + imageMetadata.getUID() + ".json");
		OutputStream stream = new BufferedOutputStream(new FileOutputStream(imageMetadataFile));
		
		JsonGenerator jGenerator = jfactory.createGenerator(stream);
		imageMetadata.toJSON(jGenerator);
		jGenerator.close();
		
		stream.flush();
		stream.close();
		
		Files.setPosixFilePermissions(imageMetadataFile.toPath(), MarsUtil.ownerGroupPermissions);
	}
	
	private void updateImageMetadataTagIndex(I metaData) {
		if (virtual) {
			if (metaData.getTags().size() > 0) {
				imageMetadataTagIndex.put(metaData.getUID(), metaData.getTags());
			} else {
				imageMetadataTagIndex.remove(metaData.getUID());
			}
		}
	}

	private void updateTagIndex(M molecule) {
		if (virtual) {
			if (molecule.getTags().size() > 0) {
				tagIndex.put(molecule.getUID(), molecule.getTags());
				archiveProperties.addAllTags(molecule.getTags());
			} else {
				tagIndex.remove(molecule.getUID());
			}
		}
	}
	
	/**
	 * Check if a molecule record has a tag. This offers optimal
	 * performance for virtual mode because only the tag index
	 * is checked without retrieving all virtual records.
	 * 
	 * @param UID The UID of the molecule to check for the tag.
	 * @param tag The tag to check for.
	 * @return Returns true if the molecule has the tag and false if not.
	 */
	public boolean moleculeHasTag(String UID, String tag) {
		if (UID != null && tag != null) {
			if (virtual) {
				if (tagIndex.containsKey(UID) && tagIndex.get(UID).contains(tag))
					return true;
				else
					return false;
			} else
				return get(UID).hasTag(tag);
		}
		return false;
	}
	
	/**
	 * Check if a molecule record has tags. This offers optimal
	 * performance for virtual mode because only the tag index
	 * is checked without retrieving all virtual records.
	 * 
	 * @param UID The UID of the molecule to check.
	 * @return Returns true if the molecule has tags and false if not.
	 */
	public boolean moleculeHasTags(String UID) {
		if (UID != null) {
			if (virtual) {
				if (tagIndex.containsKey(UID) && tagIndex.get(UID).size() > 0)
					return true;
				else
					return false;
			} else
				return get(UID).getTags().size() > 0;
		}
		return false;
	}
	
	/**
	 * Check if a MARSImageMetadata record has a tag. This offers optimal
	 * performance for virtual mode because only the tag index
	 * is checked without retrieving all virtual records.
	 * 
	 * @param UID The UID of the MARSImageMetadata record to check for the tag.
	 * @param tag The tag to check for.
	 * @return Returns true if the MARSImageMetadata record has the tag and false if not.
	 */
	public boolean imageMetadataHasTag(String UID, String tag) {
		if (UID != null && tag != null) {
			if (virtual) {
				if (imageMetadataTagIndex.containsKey(UID) && imageMetadataTagIndex.get(UID).contains(tag))
					return true;
				else
					return false;
			} else
				return getImageMetadata(UID).hasTag(tag);
		}
		return false;
	}

	/**
	 * Removes all molecule records with the tag provided.
	 * 
	 * @param tag Molecule records with this tag will be removed.
	 */
	public void deleteMoleculesWithTag(String tag) {
		ArrayList<String> newMoleculeIndex = new ArrayList<String>();
		
		for (String UID : moleculeIndex) {
			M molecule = get(UID);
			
			if (moleculeHasTag(UID, tag)) {
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
	
	/**
	 * Removes all MARSImageMetadata records with the tag provided.
	 * 
	 * @param tag MARSImageMetadata records with this tag will be removed.
	 */
	public void deleteImageMetadataRecordsWithTag(String tag) {
		//We should do this with streams but for the moment this is faster
		ArrayList<String> newImageMetadataIndex = new ArrayList<String>();
		
		for (String UID : imageMetadataIndex) {
			I metaData = getImageMetadata(UID);
			
			if (imageMetadataHasTag(UID,tag)) {
				if (virtual) {
					File imageMetadataFile = new File(file.getAbsolutePath() + "/ImageMetadata/" + UID + ".json");
					if (imageMetadataFile.exists())
						imageMetadataFile.delete();
					virtualImageMetadataSet.remove(metaData.getUID());
				}
				imageMetadataTagIndex.remove(UID);			
			} else {
				newImageMetadataIndex.add(metaData.getUID());
			}
		}
		
		imageMetadataIndex = newImageMetadataIndex;
		archiveProperties.setNumImageMetadata(imageMetadataIndex.size());
	}
	
	/**
	 * Used to check if there is a molecule record with the UID given.
	 * 
	 * @param UID Check for a molecule record with this UID.
	 * @return True if the archive contains the molecule record 
	 * with the provided UID and false if not.
	 */
	public boolean contains(String UID) {
		if (virtual) {
			return virtualMoleculesSet.contains(UID);
		} else {
			return molecules.containsKey(UID);
		}
	}
	
	/**
	 * Used to check if there is a MARSImageMetadata record with the UID given.
	 * 
	 * @param UID Check for a MARSImageMetadata record with this UID.
	 * @return True if the archive contains a MARSImageMetadata record 
	 * with the provided UID and false if not.
	 */
	public boolean containsImageMetadataRecord(String UID) {
		if (virtual) {
			return virtualImageMetadataSet.contains(UID);
		} else {
			return imageMetadata.containsKey(UID);
		}
	}

	/**
	 * Get the molecule record with the given UID.
	 * 
	 * @param UID The UID of the record to retrieve.
	 * @return The Molecule record with the UID given 
	 * or null if none is located.
	 */
	public M get(String UID) {
		if (virtual) {
			M molecule = null;
			try {
				File moleculeFile = new File(file.getAbsolutePath() + "/Molecules/" + UID + ".json");
				
				if (!moleculeFile.exists()) {
					addLogMessage("Molecule record " + UID + " cannot be found.");
					//molecule = createMolecule(UID);
					//molecule.setNotes("This record cannot be found.");
					return null;
				} else if (moleculeFile.length() == 0) {
					addLogMessage("Molecule record " + UID + " has been corrupted.");
					//molecule = createMolecule(UID);
					//molecule.setNotes("There is no data available for this record.");
					return null;
				}
				
				InputStream inputStream = new BufferedInputStream(new FileInputStream(moleculeFile));
		
				JsonParser jParser = jfactory.createParser(inputStream);

				molecule = createMolecule(jParser);
				
				jParser.close();
				inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return molecule;
		} else {
			return molecules.get(UID);
		}
	}
	
	/**
	 * Get the index position of the UID given.
	 * 
	 * @param UID The UID to find the index location for.
	 * @return The Integer location in the index of
	 * the UID provided.
	 */
	public int getIndex(String UID) {
		return moleculeIndex.indexOf(UID);
	}
	
	public Stream<M> stream() {
		return this.moleculeIndex.stream().map(UID -> get(UID));
	}
	
	public Stream<M> parallelStream() {
		return this.moleculeIndex.parallelStream().map(UID -> get(UID));
	}
	
	//Should we add a put statement at the end to enforce proper saving even in the case of a virtual archive.
	public void forEach(Consumer<? super Molecule> action) {
		this.moleculeIndex.stream().map(UID -> get(UID)).forEach(action);
	}
	
	/**
	 * Get the UID of the MARSImageMetadata for a molecule record. If 
	 * working from a virtual store, this will use an index providing
	 * optimal performance. If working in memory this is the same as
	 * retrieving the molecule record and the ImageMetadata UID from 
	 * it directly.
	 * 
	 * @param UID The UID of the molecule to get the MARSImageMetadata UID for.
	 * @return The UID string of the MARSImageMetadata record corresponding to the
	 * molecule record whose UID was provided.
	 */
	public String getImageMetadataUIDforMolecule(String UID) {
		if (virtual)
			return moleculeImageMetadataUIDIndex.get(UID);
		else 
			return get(UID).getImageMetadataUID();
	}
	
	/**
	 * Get the UID at the provided index location.
	 * 
	 * @param index Retrieve the UID at this index location.
	 * @return The UID at the index location provided.
	 */
	public String getUIDAtIndex(int index) {
		return moleculeIndex.get(index);
	}
	
	/**
	 * Get the ImageMetadata UID at the provided index location.
	 * 
	 * @param index Retrieve the ImageMetadata UID at this index location.
	 * @return The ImageMetadata UID at the index location provided.
	 */
	public String getImageMetadataUIDAtIndex(int index) {
		return imageMetadataIndex.get(index);
	}
	
	/**
	 * Returns the File from which the archive was opened.
	 * 
	 * @return The File the archive was opened from.
	 */
	public File getFile() {
		return file;
	}

	/**
	 * Set the name of the archive.
	 * 
	 * @param name The new name of the archive.
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * Get the name of the archive.
	 * 
	 * @return The String name of the archive.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the MoleculeArchiveWindow holding this archive, if one exists.
	 * Otherwise, null is returned.
	 * 
	 * @return The MoleculeArchiveWindow containing this archive.
	 */
	public MoleculeArchiveWindow getWindow() {
		return win;
	}
	
	/**
	 * Set the window containing this archive.
	 * 
	 * @param win Set the MoleculeArchiveWindow that contains this archive.
	 */
	public void setWindow(MoleculeArchiveWindow win) {
		this.win = win;
	}
	
	/**
	 * Lock the archive window during processing, if one exists.
	 */
	public void lock() {
		if (win != null) {
			win.lock();
		}
	}
	
	/**
	 * Unlock the archive window after processing is done, if one exists.
	 */
	public void unlock() {
		if (win != null) {
			win.unlock();
		}
	}
	
	/**
	 * Set json output format to SMILE. See Jackson JSON for further details.
	 */
	public void setSMILEOutputEncoding() {
		outputSmileEncoding = true;
	}
	
	/**
	 * Set json output format to text.
	 */
	public void unsetSMILEOutputEncoding() {
		outputSmileEncoding = false;
	}
	
	/**
	 * Check if SMILE is the output encoding.
	 * 
	 * @return True if SMILE is the output encoding, false if not.
	 */
	public boolean isSMILEOutputEncoding() {
		return outputSmileEncoding;
	}
	
	/**
	 * Check if SMILE is the input encoding when the archive was opened.
	 * 
	 * @return True if SMILE was the input encoding, false if not.
	 */
	public boolean isSMILEInputEncoding() {
		return inputSmileEncoding;
	}
	
	/**
	 * Natural Order Sort all Molecule UIDs in the index. Run after adding new
	 * records or after recovery to ensure the molecule records preserve an order.
	 */
	public void naturalOrderSortMoleculeIndex() {
		moleculeIndex = (ArrayList<String>)moleculeIndex.stream().sorted().collect(toList());
	}
	
	/**
	 * Add a Log message to all MARSImageMetadata records. Used by all processing plugins 
	 * so there is a record of the sequence of processing steps during analysis.
	 * 
	 * @param message The String message to add to all MARSImageMetadata logs.
	 */
	public void addLogMessage(String message) {
		for (String metaUID : imageMetadataIndex) {
			if (virtual) {
				I meta = getImageMetadata(metaUID);
				meta.addLogMessage(message);
				putImageMetadata(meta);
			} else {
				imageMetadata.get(metaUID).addLogMessage(message);
			}
		}
	}
	
	/**
	 * Get the {@link MoleculeArchiveProperties} which contain general information about the archive.
	 * This includes numbers of records, comments, file locations, and global lists of table columns, 
	 * tags, and parameters. 
	 * 
	 * @return The {@link MoleculeArchiveProperties} for this {@link AbstractMoleculeArchive}.
	 */
	public P getProperties() {
		return archiveProperties;
	}
	
	/**
	 * Update the {@link MoleculeArchiveProperties}. Updates the global tag 
	 * list using the tagIndex and updates the record numbers. 
	 * If in virtual mode, this saves the properties to the virtual store.
	 * 
	 * The parameter list and MARSResultsTable column names are not updated 
	 * because in virtual mode this would require reading all records in the
	 * archive, since indexes for these items are not maintained. Therefore,
	 * the accuracy of these elements relay entirely on updates when adding
	 * and changing records.
	 * 
	 * If a complete update is required then use the {@link #rebuildIndexes()} method 
	 * or corresponding menu item in the MoleculeArchiveWindow.
	 */
	public void updateProperties() {
		archiveProperties.setNumberOfMolecules(moleculeIndex.size());
		archiveProperties.setNumImageMetadata(imageMetadataIndex.size());
		
		if (virtual) {
			try {
				File propertiesFile = new File(file.getAbsolutePath() + "/MoleculeArchiveProperties.json");
				OutputStream stream = new BufferedOutputStream(new FileOutputStream(propertiesFile));
				
				JsonGenerator jGenerator = jfactory.createGenerator(stream);
				archiveProperties.toJSON(jGenerator);
				jGenerator.close();
				
				stream.flush();
				stream.close();
				
				Files.setPosixFilePermissions(propertiesFile.toPath(), MarsUtil.ownerGroupPermissions);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public MoleculeArchiveService getMoleculeArchiveService() {
		return moleculeArchiveService;
	}
	
	public abstract P createProperties();
	
	public abstract P createProperties(JsonParser jParser) throws IOException;
	
	public abstract I createImageMetadata(JsonParser jParser) throws IOException;
	
	public abstract I createImageMetadata(String metaUID);
	
	public abstract M createMolecule();
	
	public abstract M createMolecule(JsonParser jParser) throws IOException;
	
	public abstract M createMolecule(String UID);
	
	public abstract M createMolecule(String UID, MarsTable table);
	
	@Override
	public String toString() {
		return name;
	}
}
