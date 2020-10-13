/*-
f * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2020 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
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
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.*;

import de.mpg.biochem.mars.image.MoleculeIntegrator;
import de.mpg.biochem.mars.image.PeakTracker;
import de.mpg.biochem.mars.kcp.commands.KCPCommand;
import de.mpg.biochem.mars.kcp.commands.SigmaCalculatorCommand;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.commands.BuildArchiveFromTableCommand;
import de.mpg.biochem.mars.molecule.commands.DriftCalculatorCommand;
import de.mpg.biochem.mars.molecule.commands.DriftCorrectorCommand;
import de.mpg.biochem.mars.molecule.commands.ImportVirtualStoreCommand;
import de.mpg.biochem.mars.molecule.commands.VarianceCalculatorCommand;
import de.mpg.biochem.mars.molecule.commands.RegionDifferenceCalculatorCommand;
import de.mpg.biochem.mars.table.GroupIndices;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.*;

import java.util.concurrent.locks.ReentrantLock;

import static java.util.stream.Collectors.toList;

import org.scijava.table.*;

/**
 * Abstract superclass for the primary storage structure of Mars datasets. MoleculeArchives provides an optimal structure 
 * for storing single-molecule time-series data. Time-series data for each molecule in a dataset are 
 * stored in the form of {@link Molecule} records, which may also contain calculated parameters, tags, 
 * notes, and kinetic change point segments. These records are assigned a UID string at the time of creation.
 * This string provides universal molecule uniqueness throughout all datasets. MoleculeArchives 
 * contain a collection of molecule records associated with a given experimental condition or analysis 
 * pipeline.
 * <p>
 * {@link MarsMetadata} records containing data collection information are also stored 
 * in MoleculeArchives. They are identified using metaUID strings. {@link Molecule} records 
 * associated with a given data collection have a metaUID string linking them
 * to the correct {@link MarsMetadata} record within the same MoleculeArchive. 
 * 
 * Global properties of the MoleculeArchive, including indexing, comments, etc.., are stored 
 * in a {@link MoleculeArchiveProperties} record also contained within the MoleculeArchive. 
 * <p>
 * Multiple MoleculeArchives can easily be merged with all {@link Molecule} records preserving uniqueness 
 * due to their UIDs and unique {@link MarsMetadata} information. After a merge the archive will 
 * then have additional {@link Molecule} records and {@link MarsMetadata} records.
 * </p>
 * <p>
 * Multithreaded processing is straightforward with this data structure because individual 
 * molecule records in a MoleculeArchive can be accessed simultaneously. Additionally, this 
 * structure allows for seamless virtual storage in which molecule records are stored as 
 * individual files within an archive folder. These records are loaded and saved as needed 
 * with only a few records residing in memory at any given time. 
 * </p>
 * <p>
 * MoleculeArchives are generated by several Mars commands, 
 * including {@link PeakTracker}, {@link MoleculeIntegrator} and {@link BuildArchiveFromTableCommand}. 
 * Required input for all commands in the molecule package, including {@link RegionDifferenceCalculatorCommand}, 
 * {@link VarianceCalculatorCommand}, {@link DriftCorrectorCommand}, {@link DriftCalculatorCommand}
 * as well as commands in the kcp package, including 
 * {@link KCPCommand}, and {@link SigmaCalculatorCommand}.
 * </p>
 * <p>
 * MoleculeArchives can be saved in json (or smile) format as a single file or in a virtual store using the 
 * {@link #saveAs(File)} and {@link #saveAsVirtualStore(File)} methods, respectively. These files and folders
 * will have a .yama extension for Yet Another MoleculeArchive or .yama.store extension to indicate a virtual store. 
 * The files have a json or smile format with the .yama extension indicating they contain certain expected field names 
 * and value types.
 * </p>
 * <p>
 * MoleculeArchive of different types can be opened using the {@link ImportVirtualStoreCommand}. This import command
 * will automatically detect the type and load the archive in accordingly.
 * </p>
 * <p>
 * MoleculeArchives can be loaded using the constructors {@link #AbstractMoleculeArchive(File)} or 
 * {@link #AbstractMoleculeArchive(String, File)}. Otherwise, MoleculeArchives can be loaded
 * using the Mars command {@link ImportVirtualStoreCommand} through the 
 * GUI or in scripts.
 * </p>
 * @author Karl Duderstadt
 * @param <M> Molecule type.
 * @param <I> MarsMetadata type.
 * @param <P> MoleculeArchiveProperties type.
 */
public abstract class AbstractMoleculeArchive<M extends Molecule, I extends MarsMetadata, P extends MoleculeArchiveProperties<M, I>, X extends MoleculeArchiveIndex<M, I>> 
	extends AbstractJsonConvertibleRecord implements MoleculeArchive<M, I, P, X> {
	
	protected String name;
	
	protected MoleculeArchiveWindow win;
	
	/*
	 * The archive file with .yama extension or virtual store with .yama.store extension 
	 */
	protected File file;
	
	/*
	 * JsonFactory instance used. Can be either smile or json.
	 */
	protected JsonFactory jfactory;
	
	/*
	 * Archive properties.
	 */
	protected P archiveProperties;
	
	/*
	 * Record index used for virtual archives.
	 */
	protected MoleculeArchiveIndex<M, I> archiveIndex;
	
	/*
	 * Map from metadata UID to MarsMetadata object. Keys should be synchronized with metadataList always. 
	 */
	protected ConcurrentMap<String, I> metadataMap;
	
	/*
	 * List of metadata UIDs. Items should match keys in metadataMap always. 
	 * All write operations must be placed in synchronized blocks. synchronized(metadataList) { ... }
	 */
	protected ArrayList<String> metadataList;
	
	/*
	 * Map from molecule UID to Molecule object. Keys should be synchronized with moleculeList always. Left null in virtual memory mode.
	 */
	protected ConcurrentMap<String, M> moleculeMap;
	
	/*
	 * List of molecule UIDs. Items should match keys in moleculeMap always. 
	 * All write operations must be placed in synchronized blocks. synchronized(moleculeList) { ... }
	 */
	protected ArrayList<String> moleculeList;
	
	/*
	 * Map from molecule UID to ReentrantLock to ensure thread blocking when accessing molecule files.
	 */
	protected ConcurrentMap<String, ReentrantLock> recordLocks;
	
	/*
	 * Set to true if working from a virtual store.
	 */
	protected boolean virtual;
	
	/*
	 * For virtual archives we must keep track of the encoding when it was loaded 
	 * so we always parse correctly even if the output format has been changed.
	 */
	protected boolean smileEncoding = true;
	protected String storeFileExtension = ".sml";
	
	/*
	 * Thread count. Should be derived from scijava or Fiji in the future.
	 */
	protected final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
	
	/**
	 * Constructor for creating an empty MoleculeArchive. 
	 * 
	 * @param name The name archive.
	 */
	public AbstractMoleculeArchive(String name) {
		super();
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
		super();
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
	 * 
	 * @throws JsonParseException if there is a parsing exception.
	 * @throws IOException if there is a problem with the file provided.
	 */
	public AbstractMoleculeArchive(String name, File file) throws JsonParseException, IOException {
		super();
		this.name = name;
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
	 * Constructor for building a molecule archive from a MarsTable.
	 * The table provided must contain a molecule column. The integer values
	 * in the molecule column determine the grouping for creation of 
	 * molecule records.
	 * 
	 * Status will be reported during processing by retrieving the StatusService
	 * from the MoleculeArchiveService instance.
	 * 
	 * @param name The name of the archive.
	 * @param table A MarsTable to build the archive from.
	 */
	public AbstractMoleculeArchive(String name, MarsTable table) {
		super();
		this.name = name;
		this.virtual = false;
		
		initializeVariables();
		
		buildFromTable(table);
	}
	
	private void initializeVariables() {
		moleculeList = new ArrayList<String>();  
		metadataList = new ArrayList<String>(); 
		metadataMap = new ConcurrentHashMap<>();
		
		archiveProperties = createProperties();
		archiveProperties.setParent(this);
		
		recordLocks = new ConcurrentHashMap<>();
		moleculeMap = new ConcurrentHashMap<>();
	}
	
	protected JsonParser detectEncoding(InputStream inputStream) throws IOException {
		JsonFactory jsonF = new JsonFactory();
		SmileFactory smileF = new SmileFactory(); 
		DataFormatDetector det = new DataFormatDetector(new JsonFactory[] { jsonF, smileF });
	    DataFormatMatcher match = det.findFormat(inputStream);
	    JsonParser jParser = match.createParserWithMatch();
	    
	    if (match.getMatchedFormatName().equals("Smile")) {
	    	smileEncoding = true;
	    	jfactory = smileF;
	    } else if (match.getMatchedFormatName().equals("JSON")) {
	    	smileEncoding = false;
	    	jfactory = jsonF;
	    } else {
	    	//We default to Smile
	    	smileEncoding = true;
	    	jfactory = smileF;
	    }
	    
	    return jParser;
	}
	
	protected void loadVirtualStore(File file) throws JsonParseException, IOException {
		File propertiesFile = new File(file.getAbsolutePath() + "/MoleculeArchiveProperties.json");
		if (propertiesFile.exists())
			storeFileExtension = ".json";
		else {
			storeFileExtension = ".sml";
			propertiesFile = new File(file.getAbsolutePath() + "/MoleculeArchiveProperties.sml");
		}
		InputStream propertiesInputStream = new BufferedInputStream(new FileInputStream(propertiesFile));
		JsonParser propertiesJParser = detectEncoding(propertiesInputStream);
		
		archiveProperties.fromJSON(propertiesJParser);
		propertiesJParser.close();
		propertiesInputStream.close();
		
		File indexFile = new File(file.getAbsolutePath() + "/indexes" + storeFileExtension);
		if (indexFile.exists()) {
			InputStream indexInputStream = new BufferedInputStream(new FileInputStream(indexFile));
		    JsonParser indexJParser = jfactory.createParser(indexInputStream);
		    
			index(indexJParser);
		    
			indexJParser.close();
			indexInputStream.close();
		} else {
			rebuildIndexes();
		}
	}
	
	protected void load(File file) throws JsonParseException, IOException {
		InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
	    JsonParser jParser = detectEncoding(inputStream);
		
	    fromJSON(jParser);
		
		jParser.close();
		inputStream.close();
		
		rebuildIndexes();	
	}
	
	private void buildFromTable(MarsTable results) {
		LinkedHashMap<Integer, GroupIndices> groups = MarsTableService.find_group_indices(results, "molecule");
		
		String metaUID = MarsMath.getUUID58().substring(0, 10);
		I meta = createMetadata(metaUID);
		putMetadata(meta);

		String[] headers = new String[results.getColumnCount() - 1];
		int col = 0;
		for (int i=0;i<results.getColumnCount();i++) {
			if (!results.getColumnHeader(i).equals("molecule")) {
				headers[col] = results.getColumnHeader(i);
				col++;
			}
		}
		
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
			molecule.setMetadataUID(metaUID);
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
		properties().clear();
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
		
		if (virtual) {
			MoleculeArchiveIndex<M,I> newIndex = createIndex();
			
			String[] moleculeFileNameIndex = new File(file.getAbsolutePath() + "/Molecules").list(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(storeFileExtension);
				}
			});
			
			for (int i=0;i<moleculeFileNameIndex.length;i++) {
				String UID = moleculeFileNameIndex[i].substring(0, moleculeFileNameIndex[i].length() - storeFileExtension.length());
				newIndex.getMoleculeUIDSet().add(UID);
			}
			
			String[] metadataFileNameIndex = new File(file.getAbsolutePath() + "/Metadata").list(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.endsWith(storeFileExtension);
				}
			});
			
			for (int i=0;i<metadataFileNameIndex.length;i++) {
				String UID = metadataFileNameIndex[i].substring(0, metadataFileNameIndex[i].length() - storeFileExtension.length());
				newIndex.getMetadataUIDSet().add(UID);
			}
			
		   try {
		        forkJoinPool.submit(() -> newIndex.getMoleculeUIDSet().parallelStream().forEach(UID -> { 
		        	M molecule = get(UID);
		        	newIndex.addMolecule(molecule);
		        	properties().addMoleculeProperties(molecule);
		        })).get();    
		        
		        forkJoinPool.submit(() -> newIndex.getMetadataUIDSet().parallelStream().forEach(metaUID -> { 
		        	I metadata = getMetadata(metaUID);
		        	newIndex.addMetadata(metadata);
		        	properties().addMetadataProperties(metadata);
		        })).get();
		   } catch (InterruptedException | ExecutionException e ) {
		    	e.printStackTrace();
		   } finally {
		      forkJoinPool.shutdown();
		   }
			
		   moleculeList = (ArrayList<String>)newIndex.getMoleculeUIDSet().stream().sorted().collect(toList());
		   metadataList = (ArrayList<String>)newIndex.getMetadataUIDSet().stream().sorted().collect(toList());
		   
		   this.archiveIndex = newIndex;
		   
		   properties().setNumberOfMolecules(moleculeList.size());
		   properties().setNumberOfMetadatas(metadataList.size());
		   
		   index().save(file, jfactory, storeFileExtension);
		   properties().save(file, jfactory, storeFileExtension);
		} else {
			try {
		        forkJoinPool.submit(() -> moleculeList.parallelStream().forEach(UID -> { 
		        	M molecule = get(UID);
		        	properties().addMoleculeProperties(molecule);
		        })).get();    
		        
		        forkJoinPool.submit(() -> metadataList.parallelStream().forEach(metaUID -> { 
		        	I metadata = getMetadata(metaUID);
		        	properties().addMetadataProperties(metadata);
		        })).get();
		        
		   } catch (InterruptedException | ExecutionException e ) {
		    	e.printStackTrace();
		   } finally {
		      forkJoinPool.shutdown();
		   }
			
			properties().setNumberOfMolecules(moleculeList.size());
			properties().setNumberOfMetadatas(metadataList.size());
		}
	}
	
	/**
	 * Saves the MoleculeAchive to the file from which it was opened.
	 * 
	 * @throws IOException if something goes wrong saving the data.
	 */
	public void save() throws IOException {
		if (virtual) {
			properties().save(file, jfactory, storeFileExtension);
			index().save(file, jfactory, storeFileExtension);
		} else if (smileEncoding) {
			this.file = saveAs(file);
		} else {
			this.file = saveAsJson(file);
		}
	}
	
	/**
	 * Saves MoleculeAchive to the given file destination. 
	 * 
	 * @param file a yama file destination. If the .yama is not present it will be added.
	 * @throws IOException if something goes wrong saving the data.
	 */
	public File saveAs(File file) throws IOException {
		String filePath = file.getAbsolutePath();
		if (!filePath.endsWith(".yama")) {
			file = new File(filePath + ".yama");
		}
		
		MarsUtil.writeJsonRecord(this, file, new SmileFactory());
		
		return file;
	}
	
	public File saveAsJson(File file) throws IOException {
		String filePath = file.getAbsolutePath();
		if (filePath.endsWith(".yama.json")) {
			//Great! Do nothing.
		} else if (filePath.endsWith(".yama")) {
			file = new File(filePath + ".json");
		} else if (filePath.endsWith(".json")) {
			file = new File(filePath.substring(0, filePath.length() - 5) + ".yama.json");
		} else {
			file = new File(filePath + ".yama.json");
		}
		
		MarsUtil.writeJsonRecord(this, file, new JsonFactory());
		
		return file;
	}
	
	@Override
	protected void createIOMaps() {

		setJsonField("properties", 
			jGenerator -> {
				jGenerator.writeFieldName("properties");
				archiveProperties.toJSON(jGenerator);
			}, 
			jParser -> archiveProperties.fromJSON(jParser));	
			 	
		setJsonField("metadata", 
			jGenerator -> {
				if (metadataList.size() > 0) {
					jGenerator.writeArrayFieldStart("metadata");
					Iterator<String> iter = metadataList.iterator();
					while (iter.hasNext())
						getMetadata(iter.next()).toJSON(jGenerator);
					jGenerator.writeEndArray();
				}
			 }, 
			jParser -> {
				while (jParser.nextToken() != JsonToken.END_ARRAY)
					putMetadata(createMetadata(jParser));
			});
		
		setJsonField("molecules", 
			jGenerator -> {
				if (moleculeList.size() > 0) {
					jGenerator.writeArrayFieldStart("molecules");
					Iterator<String> iterator = moleculeList.iterator();
					while (iterator.hasNext())
						get(iterator.next()).toJSON(jGenerator);
					jGenerator.writeEndArray();
				}
			 }, 
			jParser -> {
				while (jParser.nextToken() != JsonToken.END_ARRAY)
					put(createMolecule(jParser));
			});
		
		/*
		 * 
		 * The fields below are needed for backwards compatibility.
		 * 
		 * Please remove for a future release.
		 * 
		 */
		
		setJsonField("MoleculeArchiveProperties", null, 
			jParser -> archiveProperties.fromJSON(jParser));
		
		setJsonField("Metadata", null, 
			jParser -> {
				while (jParser.nextToken() != JsonToken.END_ARRAY)
					putMetadata(createMetadata(jParser));
			});
		
		setJsonField("ImageMetadata", null, 
			jParser -> {
				while (jParser.nextToken() != JsonToken.END_ARRAY)
					putMetadata(createMetadata(jParser));
			});
		
		setJsonField("ImageMetaData", null, 
			jParser -> {
				while (jParser.nextToken() != JsonToken.END_ARRAY)
					putMetadata(createMetadata(jParser));
			});
		
		setJsonField("Molecules", null, 
			jParser -> {
				while (jParser.nextToken() != JsonToken.END_ARRAY)
					put(createMolecule(jParser));
			});
	}
	
	/**
	 * Creates the directory given and a virtual store inside with all files
	 * in json format with .json file extension. Rebuilds indexes in the 
	 * process if the archive was loaded from a virtual store.
	 * 
	 * @param virtualDirectory a directory destination for the virtual store.
	 * @throws IOException if something goes wrong creating the virtual store.
	 */
	public void saveAsJsonVirtualStore(File virtualDirectory) throws IOException {
		saveAsVirtualStore(virtualDirectory, new JsonFactory(), ".json");
	}

	/**
	 * Creates the directory given and a virtual store inside with all files
	 * in smile format with .sml file extension. This is the default format. 
	 * Rebuilds indexes in the process if the archive was loaded
	 * from a virtual store.
	 * 
	 * @param virtualDirectory a directory destination for the virtual store.
	 * @throws IOException if something goes wrong creating the virtual store.
	 */
	public void saveAsVirtualStore(File virtualDirectory) throws IOException {
		saveAsVirtualStore(virtualDirectory, new SmileFactory(), ".sml");
	}
	
	private void saveAsVirtualStore(File virtualDirectory, JsonFactory jfactory, String fileExtension) throws IOException {
		virtualDirectory.mkdirs();
		File metadataDir = new File(virtualDirectory.getAbsolutePath() + "/Metadata");
		File moleculesDir = new File(virtualDirectory.getAbsolutePath() + "/Molecules");
		
		metadataDir.mkdirs();
		moleculesDir.mkdirs();

		MoleculeArchiveIndex<M, I> newIndex = createIndex();
		properties().clear();
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
		
		try {
			forkJoinPool.submit(() -> metadataList.parallelStream().forEach(metaUID -> { 
	        	try {
	        		I metadata = getMetadata(metaUID);
	        		newIndex.addMetadata(metadata);
	        		properties().addMetadataProperties(metadata);
					saveMetadataToFile(new File(virtualDirectory.getAbsolutePath() + "/Metadata"), metadata, jfactory, fileExtension);
	        	} catch (IOException e) {
	        		e.printStackTrace();
	        	}
	        })).get();
			
	        forkJoinPool.submit(() -> moleculeList.parallelStream().forEach(UID -> { 
	        	try {
	        		M molecule = get(UID);
		        	newIndex.addMolecule(molecule);
		        	properties().addMoleculeProperties(molecule);
					saveMoleculeToFile(new File(virtualDirectory.getAbsolutePath() + "/Molecules"), molecule, jfactory, fileExtension);
	        	} catch (IOException e) {
	        		e.printStackTrace();
	        	}
	        })).get();        
	   } catch (InterruptedException | ExecutionException e ) {
	    	e.printStackTrace();
	   } finally {
	      forkJoinPool.shutdown();
	   }
		   
	   properties().setNumberOfMolecules(moleculeList.size());
	   properties().setNumberOfMetadatas(metadataList.size());
	   
	   newIndex.save(virtualDirectory, jfactory, fileExtension);
	   properties().save(virtualDirectory, jfactory, fileExtension);
		
	   if (virtual)
			this.archiveIndex = newIndex;
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
			if (!index().getMoleculeUIDSet().contains(molecule.getUID()))
				synchronized(moleculeList) {
					moleculeList.add(molecule.getUID());
				}
			
			index().addMolecule(molecule);
			
			try {
				saveMoleculeToFile(new File(file.getAbsolutePath() + "/Molecules"), molecule, jfactory, storeFileExtension);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (!moleculeMap.containsKey(molecule.getUID())) {
			molecule.setParent(this);
			moleculeMap.put(molecule.getUID(), molecule);	
			synchronized(moleculeList) {
				moleculeList.add(molecule.getUID());
			}
		}
		
		properties().addMoleculeProperties(molecule);
		properties().setNumberOfMolecules(moleculeList.size());
	}
	
	/**
	 * Adds an metadata record to the archive. If a metadata record with 
	 * the same UID is already in the archive, the record is updated.
	 * 
	 * All indexes are updated with the properties of the metadata record added.
	 * 
	 * @param metadata an metadata record to add or update.
	 */
	public void putMetadata(I metadata) {		
		//If virtual we save the metadata to file
		if (virtual) {
			if (!index().getMetadataUIDSet().contains(metadata.getUID()))
				synchronized(metadataList) {	
					metadataList.add(metadata.getUID());
				}
			
			index().addMetadata(metadata);
			
			try {
				saveMetadataToFile(new File(file.getAbsolutePath() + "/Metadata"), metadata, jfactory, storeFileExtension);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		//Also, do this in virtual mode to lazy load records into memory.
		if (!metadataMap.containsKey(metadata.getUID())) {
			metadata.setParent(this);
			metadataMap.put(metadata.getUID(), metadata);
			synchronized(metadataList) {	
				metadataList.add(metadata.getUID());
			}
		}
		
		properties().addMetadataProperties(metadata);
		properties().setNumberOfMetadatas(metadataList.size());
	}
	
	/**
	 * The metadata record with the UID given is removed from the archive. 
	 * All indexes are updated to reflect the change.
	 * 
	 * @param metaUID the UID of the metadata record to remove.
	 */
	public void removeMetadata(String metaUID) {
		if (virtual) {
			index().removeMetadata(metaUID);
			File metadataFile = new File(file.getAbsolutePath() + "/Metadata/" + metaUID + storeFileExtension);
			if (metadataFile.exists())
				metadataFile.delete();
		}
		
		if (metadataMap.containsKey(metaUID))
			metadataMap.remove(metaUID);
		
		synchronized(metadataList) {
			metadataList.remove(metaUID);
		}
		
		properties().setNumberOfMetadatas(metadataList.size());
	}

	/**
	 * The Metadata record given is removed from the archive. 
	 * All indexes are updated to reflect the change.
	 * 
	 * @param meta Metadata record to remove.
	 */
	public void removeMetadata(I meta) {
		removeMetadata(meta.getUID());
	}
	
	/**
	 * Retrieves an MarsMetadata record.
	 * 
	 * @param index The index of the MarsMetadata record to retrieve.
	 * @return A MarsMetadata record.
	 */
	public I getMetadata(int index) {
		return getMetadata(metadataList.get(index));
	}
	
	/**
	 * Retrieves a MarsMetadata record.
	 * 
	 * @param metaUID The UID of the MarsMetadata record to retrieve.
	 * @return A MarsMetadata record.
	 */
	public I getMetadata(String metaUID) {
		if (virtual) {
			if (metadataMap.containsKey(metaUID))
				return metadataMap.get(metaUID);
			else {
				I metadata = null;
				
				if (!recordLocks.containsKey(metaUID))
					recordLocks.put(metaUID, new ReentrantLock());
				
				recordLocks.get(metaUID).lock();
				try {
					File metadataFile = new File(file.getAbsolutePath() + "/Metadata/" + metaUID + storeFileExtension);
					
					//Need to be read/write to ensure lock but the file is only read here.
					RandomAccessFile raf = new RandomAccessFile(metadataFile, "rw");
					FileLock fileLock = raf.getChannel().lock();
					
					JsonParser jParser = jfactory.createParser(Channels.newInputStream(raf.getChannel()));
	
					metadata = createMetadata(jParser);
	
					fileLock.release();
					raf.close();
					jParser.close();
				} catch (IOException e) {
					logln("MarsMetadata record " + metaUID + " has been corrupted.");
					recordLocks.get(metaUID).unlock();
					return null;
				} finally {
					recordLocks.get(metaUID).unlock();
				}
					
				if (metadata != null) {
					metadata.setParent(this);
					metadataMap.put(metadata.getUID(), metadata);
				}
				
				return metadata;
			}
		} else {
			return metadataMap.get(metaUID);
		}
	}
	
	/**
	 * Retrieves the list of UIDs of all MarsMetadata records.
	 * Useful for stream().forEach(...) operations.
	 * 
	 * @return The list of all MarsMetadata UIDs.
	 */
	public final ArrayList<String> getMetadataUIDs() {
		return metadataList;
	}
	
	/**
	 * Number of molecule records in the MoleculeArchive.
	 * 
	 * @return The integer number of molecule records.
	 */
	public int getNumberOfMolecules() {
		return moleculeList.size();
	}
	
	/**
	 * Number of MarsMetadata records in the MoleculeArchive.
	 * 
	 * @return The integer number of MarsMetadata records.
	 */
	public int getNumberOfMetadatas() {
		return metadataList.size();
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
		return properties().getComments();
	}
	
	/**
	 * Sets the global comments. This replaces all current 
	 * comments with those given.
	 * 
	 * @param comments A string of global comments to set.
	 */
	public void setComments(String comments) {
		properties().setComments(comments);
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
		return get(moleculeList.get(index));
	}
	
	/**
	 * Removes the molecule record with the given UID.
	 * 
	 * @param UID The UID of the molecule record to remove.
	 */
	public void remove(String UID) {
		if (virtual) {
			File moleculeFile = new File(file.getAbsolutePath() + "/Molecules/" + UID + storeFileExtension);
			if (moleculeFile.exists())
				moleculeFile.delete();
			index().removeMolecule(UID);
		} else {
			moleculeMap.remove(UID);
		}
		synchronized(moleculeList) {
			moleculeList.remove(UID);
		}
		properties().setNumberOfMolecules(moleculeList.size());
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
		return moleculeList;
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
			tags = index().getMoleculeUIDtoTagListMap().get(UID);
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
		if (!virtual)
			return moleculeMap.get(UID).getTags();
		else
			return index().getMoleculeUIDtoTagListMap().get(UID);
	}
	
	/**
	 * Channel for the molecule with the given UID.
	 * 
	 * @param UID The UID of the molecule to retrieve the channel of.
	 * @return The channel index of the molecule in question.
	 */
	public int getChannel(String UID) {
		if (!virtual) {
			return moleculeMap.get(UID).getChannel();
		} else if (index().getMoleculeUIDtoChannelMap().containsKey(UID))
			return index().getMoleculeUIDtoChannelMap().get(UID);
		else 
			return -1;
	}
	
	/**
	 * Image index for the molecule with the given UID.
	 * 
	 * @param UID The UID of the molecule to retrieve the image index of.
	 * @return The image index of the molecule in question.
	 */
	public int getImage(String UID) {
		if (!virtual) {
			return moleculeMap.get(UID).getImage();
		} else if (index().getMoleculeUIDtoImageMap().containsKey(UID))
			return index().getMoleculeUIDtoImageMap().get(UID);
		else 
			return -1;
	}
	
	/**
	 * Comma separated list of tags for the MarsMetadata record with the given UID.
	 * 
	 * @param UID The UID of the MarsMetadata record to retrieve the tag list for.
	 * @return A String containing a comma separated list of tags.
	 */
	public String getMetadataTagList(String UID) {
		LinkedHashSet<String> tags;
		if (UID == null)
			 return null;
		else if (virtual) {
			tags = index().getMetadataUIDtoTagListMap().get(UID);
		} else {
			tags = getMetadata(UID).getTags();
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
	 * Tags for the MarsMetadata record with the given UID.
	 * 
	 * @param UID The UID of the MarsMetadata record to retrieve the tag list for.
	 * @return The set of tags for the given MarsMetadata record.
	 */
	public LinkedHashSet<String> getMetadataTagSet(String UID) {
		if (!virtual)
			return metadataMap.get(UID).getTags();
		else 
			return index().getMetadataUIDtoTagListMap().get(UID);
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
	protected void saveMoleculeToFile(File directory, M molecule, JsonFactory jfactory, String fileExtension) throws IOException {
		if (!recordLocks.containsKey(molecule.getUID()))
			recordLocks.put(molecule.getUID(), new ReentrantLock());
		
		recordLocks.get(molecule.getUID()).lock();
		try {
			File moleculeFile = new File(directory.getAbsolutePath() + "/" + molecule.getUID() + fileExtension);
			
			FileOutputStream stream = new FileOutputStream(moleculeFile);
			FileChannel channel = stream.getChannel();
	
			// Use the file channel to create a lock on the file.
	        // This method blocks until it can retrieve the lock.
	        FileLock fileLock = channel.lock();
	
			JsonGenerator jGenerator = jfactory.createGenerator(stream);
			molecule.toJSON(jGenerator);
			
	        fileLock.release();
			jGenerator.close();
		} finally {
			recordLocks.get(molecule.getUID()).unlock();
		}
	}
	
	/**
	 * Saves a MarsMetadata record as a json file.
	 * 
	 * @param directory The directory to save the file in.
	 * @param metadata The MarsMetadata record to save.
	 * @param jfactory the JsonFactory to use when saving. 
	 * Determines if smile or text encoding is used.
	 * 
	 * @throws IOException if the MarsMetadata can't be saved to the file given.
	 */
	protected void saveMetadataToFile(File directory, I metadata, JsonFactory jfactory, String fileExtension) throws IOException {
		if (!recordLocks.containsKey(metadata.getUID()))
			recordLocks.put(metadata.getUID(), new ReentrantLock());
		
		recordLocks.get(metadata.getUID()).lock();
		try {
			File metadataFile = new File(directory.getAbsolutePath() + "/" + metadata.getUID() + fileExtension);
			FileOutputStream stream = new FileOutputStream(metadataFile);
			FileChannel channel = stream.getChannel();
			
			// Use the file channel to create a lock on the file.
	        // This method blocks until it can retrieve the lock.
	        FileLock fileLock = channel.lock();
	
			JsonGenerator jGenerator = jfactory.createGenerator(stream);
			metadata.toJSON(jGenerator);

			fileLock.release();
			jGenerator.close();
		} finally {
			recordLocks.get(metadata.getUID()).unlock();
		}
	}
	
	/**
	 * Utility function to generate batches of molecules 
	 * data in an optimal format for machine learning
	 * using keras. Region goes from rangeStart to 1 - rangeEnd.
	 * 
	 * @param UIDs The list of UIDs for the molecule to review data from.
	 * @param tColumn Name of the T column.
	 * @param signalColumn Name of the signal column.
	 * @param rangeStart Index of start of range in T column.
	 * @param rangeEnd Index of end of range in T column.
	 * @param tagsToLearn List of tags to use to build labels.
	 * @param threads Number of thread to use when building data.
	 * @return Returns batch of molecule data.
	 */
	public List<double[][]> getMoleculeBatch(List<String> UIDs, String tColumn, String signalColumn, int rangeStart, int rangeEnd, List<String> tagsToLearn, int threads) {
		ForkJoinPool forkJoinPool = new ForkJoinPool(threads);
		
		double[][] molData = new double[UIDs.size()][rangeEnd - rangeStart];
		int length = rangeEnd - rangeStart;
	
		try {
	        forkJoinPool.submit(() -> UIDs.parallelStream().forEach(UID -> { 
	        	M molecule = get(UID);
	        	
	        	MarsTable table = molecule.getTable();
	        	
	        	int molDataRow = UIDs.indexOf(UID);
	        	
	        	//could be moved into loop to improve performance.
	        	//pre-fill will NaNs
	        	for (int i=0; i<length; i++)
	        		molData[molDataRow][i] = Double.NaN;
	        	
	        	for (int row=0; row < table.getRowCount(); row++) {
	        		if (rangeStart <= table.getValue(tColumn, row) && table.getValue(tColumn, row) < rangeEnd) {
	        			int index = (int)table.getValue(tColumn, row) - rangeStart;
	        			molData[molDataRow][index] = table.getValue(signalColumn, row); 
	        		}
	        	}
	        })).get();    
		} catch (InterruptedException | ExecutionException e ) {
	    	e.printStackTrace();
		} finally {
	      forkJoinPool.shutdown();
		}
		
		List<double[][]> dataBatch = new ArrayList<double[][]>();
		dataBatch.add(molData);
		
		if (tagsToLearn != null && tagsToLearn.size() > 0) {
			//First build the labels using the index
			double[][] labels = new double[UIDs.size()][tagsToLearn.size()];
			for (int i=0; i < UIDs.size() ; i++) {
				for (String tag : tagsToLearn)
					if (moleculeHasTag(UIDs.get(i), tag)) {
						labels[i][tagsToLearn.indexOf(tag)] = 1;
						break;
					}
			}
			dataBatch.add(labels);
		}
				
		return dataBatch;
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
				if (index().getMoleculeUIDtoTagListMap().containsKey(UID) && index().getMoleculeUIDtoTagListMap().get(UID).contains(tag))
					return true;
				else
					return false;
			} else
				return get(UID).hasTag(tag);
		}
		return false;
	} 
	
	/**
	 * Check if a molecule record has a tag. This offers optimal
	 * performance for virtual mode because only the tag index
	 * is checked without retrieving all virtual records.
	 * 
	 * @param UID The UID of the molecule to check for the tag.
	 * @return Returns true if the molecule has the tag and false if not.
	 */
	public boolean moleculeHasNoTags(String UID) {
		if (UID != null) {
			if (virtual) {
				if (index().getMoleculeUIDtoTagListMap().containsKey(UID) && index().getMoleculeUIDtoTagListMap().get(UID).isEmpty())
					return true;
				else if (index().getMoleculeUIDtoTagListMap().containsKey(UID) && !index().getMoleculeUIDtoTagListMap().get(UID).isEmpty())
					return false;
				else
					return true;
			} else
				return get(UID).hasNoTags();
		}
		return false;
	}
	
	/**
	 * Retrieve the list of tags for a molecule. Will retrieve
	 * the list from the index if working in virtual memory.
	 * 
	 * @param UID The UID of the molecule to retrieve the tags of.
	 * @return Returns the set of for the molecule with UID.
	 */
	public LinkedHashSet<String> moleculeTags(String UID) {
		if (UID != null) {
			if (virtual) {
				if (index().getMoleculeUIDtoTagListMap().containsKey(UID))
					return index().getMoleculeUIDtoTagListMap().get(UID);
				else
					return new LinkedHashSet<String>();
			} else
				return get(UID).getTags();
		}
		return null;
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
				if (index().getMoleculeUIDtoTagListMap().containsKey(UID) && index().getMoleculeUIDtoTagListMap().get(UID).size() > 0)
					return true;
				else
					return false;
			} else
				return get(UID).getTags().size() > 0;
		}
		return false;
	}
	
	/**
	 * Check if a MarsMetadata record has a tag. This offers optimal
	 * performance for virtual mode because only the tag index
	 * is checked without retrieving all virtual records.
	 * 
	 * @param UID The UID of the MarsMetadata record to check for the tag.
	 * @param tag The tag to check for.
	 * @return Returns true if the MarsMetadata record has the tag and false if not.
	 */
	public boolean metadataHasTag(String UID, String tag) {
		if (UID != null && tag != null) {
			if (virtual) {
				if (index().getMetadataUIDtoTagListMap().containsKey(UID) && index().getMetadataUIDtoTagListMap().get(UID).contains(tag))
					return true;
				else
					return false;
			} else
				return getMetadata(UID).hasTag(tag);
		}
		return false;
	}
	
	/**
	 * Add tags to molecules using UID to tag map. This offers optimal
	 * performance by using multiple threads. Provides a way to add tags
	 * resulting from machine learning using python.
	 * 
	 * In virtual mode, this adds tags directly to the records as well as the index.
	 * 
	 * @param tagMap The UID to tag map for add to molecules.
	 */
	public void addMoleculeTags(HashMap<String, String> tagMap) {
		tagMap.keySet().parallelStream().forEach(UID -> {
			M molecule = get(UID);
			molecule.addTag(tagMap.get(UID));
			put(molecule);
		});
	}

	/**
	 * Removes all molecule records with the tag provided.
	 * 
	 * @param tag Molecule records with this tag will be removed.
	 */
	public void deleteMoleculesWithTag(String tag) {
		ArrayList<String> deleteUIDs = (ArrayList<String>)getMoleculeUIDs().parallelStream().filter(UID -> moleculeHasTag(UID, tag)).collect(toList());
		deleteUIDs.parallelStream().forEach(UID -> remove(UID));
	}
	
	/**
	 * Removes all MarsMetadata records with the tag provided.
	 * 
	 * @param tag MarsMetadata records with this tag will be removed.
	 */
	public void deleteMetadatasWithTag(String tag) {
		ArrayList<String> deleteMetadataUIDs = (ArrayList<String>)getMetadataUIDs().parallelStream().filter(UID -> metadataHasTag(UID, tag)).collect(toList());
		deleteMetadataUIDs.parallelStream().forEach(metadataUID -> removeMetadata(metadataUID));
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
			return index().getMoleculeUIDSet().contains(UID);
		} else {
			return moleculeMap.containsKey(UID);
		}
	}
	
	/**
	 * Used to check if there is a MarsMetadata record with the UID given.
	 * 
	 * @param UID Check for a MarsMetadata record with this UID.
	 * @return True if the archive contains a MarsMetadata record 
	 * with the provided UID and false if not.
	 */
	public boolean containsMetadata(String UID) {
		if (virtual) {
			return index().getMetadataUIDSet().contains(UID);
		} else {
			return metadataMap.containsKey(UID);
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
			
			if (!recordLocks.containsKey(UID))
				recordLocks.put(UID, new ReentrantLock());
			
			recordLocks.get(UID).lock();
			try {
				File moleculeFile = new File(file.getAbsolutePath() + "/Molecules/" + UID + storeFileExtension);
				
				if (!moleculeFile.exists()) {
					logln("Molecule record " + UID + " cannot be found.");
					return null;
				} else if (moleculeFile.length() == 0) {
					logln("Molecule record " + UID + " has been corrupted.");
					return null;
				}
				
				//Need to be read/write to ensure lock but the file is only read here.
				RandomAccessFile raf = new RandomAccessFile(moleculeFile, "rw");
				FileLock lock = raf.getChannel().lock();
				
				JsonParser jParser = jfactory.createParser(Channels.newInputStream(raf.getChannel()));

				molecule = createMolecule(jParser);

				lock.release();
				raf.close();
				jParser.close();
			} catch (IOException e) {
				logln("Molecule record " + UID + " has been corrupted.");
				recordLocks.get(UID).unlock();
				return null;
			} finally {
				recordLocks.get(UID).unlock();
			}
			
			return molecule;
		} else {
			return moleculeMap.get(UID);
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
		return moleculeList.indexOf(UID);
	}
	
	/**
	 * Convenience method to retrieve a Molecule stream. Can be used to 
	 * iterate over all molecules using forEach.
	 * 
	 * @return Molecule stream.
	 */
	public Stream<M> molecules() {
		return this.moleculeList.stream().map(UID -> get(UID));
	}
	
	/**
	 * Convenience method to retrieve a metadata stream. Can be used to 
	 * iterate over all metadata using forEach.
	 * 
	 * @return Molecule stream.
	 */
	public Stream<I> metadata() {
		return this.metadataList.stream().map(UID -> getMetadata(UID));
	}
	
	/**
	 * Convenience method to retrieve a metadata stream. Can be used to 
	 * iterate over all metadata using forEach.
	 * 
	 * @return Molecule stream.
	 */
	public Stream<I> parallelMetadata() {
		return this.metadataList.parallelStream().map(UID -> getMetadata(UID));
	}
	
	/**
	 * Convenience method to retrieve a multithreated Molecule stream. Can be used to 
	 * iterate over all molecules using forEach in a multithreaded manner.
	 * 
	 * @return Molecule stream.
	 */
	public Stream<M> parallelMolecules() {
		return this.moleculeList.parallelStream().map(UID -> get(UID));
	}
	
	/**
	 * Convenience method to execute an action on all molecules using a Consumer.
	 * 
	 * @param action Action to perform on all molecules.
	 */
	public void forEach(Consumer<? super Molecule> action) {
		this.moleculeList.stream().map(UID -> get(UID)).forEach(action);
	}
	
	/**
	 * Get the UID of the metadata for a molecule record. If 
	 * working from a virtual store, this will use an index providing
	 * optimal performance. If working in memory this is the same as
	 * retrieving the molecule record and the metadata UID from 
	 * it directly.
	 * 
	 * @param UID The UID of the molecule to get the metadata UID for.
	 * @return The UID string of the metadata record corresponding to the
	 * molecule record whose UID was provided.
	 */
	public String getMetadataUIDforMolecule(String UID) {
		if (virtual)
			return index().getMetadataUIDforMolecule(UID);
		else 
			return get(UID).getMetadataUID();
	}
	
	/**
	 * Get the molecule UID for the provided index location.
	 * 
	 * @param index Retrieve the UID at this index location.
	 * @return The UID at the index location provided.
	 */
	public String getUIDAtIndex(int index) {
		return moleculeList.get(index);
	}
	
	/**
	 * Get the metadata UID at the provided index location.
	 * 
	 * @param index Retrieve the metadata UID at this index location.
	 * @return The metadata UID at the index location provided.
	 */
	public String getMetadataUIDAtIndex(int index) {
		return metadataList.get(index);
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
	 * Set the file the archive should save to. Does
	 * nothing if called on a virtual archive.
	 * 
	 * @param file The File where the archive should be saved.
	 */
	public void setFile(File file) {
		if (!virtual)
			this.file = file;
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
		if (win != null)
			win.lock();
	}
	
	/**
	 * Unlock the archive window after processing is done, if one exists.
	 */
	public void unlock() {
		if (win != null)
			win.unlock();
	}
	
	/**
	 * Natural Order Sort all Molecule UIDs in the index. Run after adding new
	 * records or after recovery to ensure the molecule records preserve an order.
	 */
	public void naturalOrderSortMoleculeIndex() {
		synchronized (moleculeList) {
			moleculeList = (ArrayList<String>)moleculeList.stream().sorted().collect(toList());
		}
	}
	
	/**
	 * Natural Order Sort all Molecule UIDs in the index. Run after adding new
	 * records or after recovery to ensure the molecule records preserve an order.
	 */
	public void naturalOrderSortMetadataIndex() {
		synchronized (metadataList) {
			metadataList = (ArrayList<String>)metadataList.stream().sorted().collect(toList());
		}
	}
	
	/**
	 * Add a log message to all metadata records. Used by Mars commands 
	 * to keep a record of the sequence of processing steps during analysis. Start
	 * a new line after adding the message.
	 * 
	 * @param message The String message to add to all metadata logs.
	 */
	public void logln(String message) {
		for (String metaUID : metadataList) {
			if (virtual) {
				I meta = getMetadata(metaUID);
				meta.logln(message);
				putMetadata(meta);
			} else {
				metadataMap.get(metaUID).logln(message);
			}
		}
		if (getWindow() != null)
			getWindow().logln(message);
	}
	
	/**
	 * Add a log message to all metadata records. Used by Mars commands 
	 * to keep a record of the sequence of processing steps during analysis. Start
	 * a new line after adding the message. Do not start a new line after adding 
	 * the message.
	 * 
	 * @param message The String message to add to all metadata logs.
	 */
	public void log(String message) {
		for (String metaUID : metadataList) {
			if (virtual) {
				I meta = getMetadata(metaUID);
				meta.log(message);
				putMetadata(meta);
			} else {
				metadataMap.get(metaUID).log(message);
			}
		}
		if (getWindow() != null)
			getWindow().log(message);
	}
	
	/**
	 * Get the {@link MoleculeArchiveProperties} which contain general information about the archive.
	 * This includes numbers of records, comments, file locations, and global lists of table columns, 
	 * tags, and parameters. 
	 * 
	 * @return The {@link MoleculeArchiveProperties} for this {@link AbstractMoleculeArchive}.
	 */
	public P properties() {
		return archiveProperties;
	}
	
	private MoleculeArchiveIndex<M, I> index() {
		MoleculeArchiveIndex<M, I> idx = archiveIndex;
		if (idx == null) {
			idx = createIndex();
			archiveIndex = idx;
		}
		return idx;
	}
	
	private MoleculeArchiveIndex<M, I> index(JsonParser jParser) throws IOException {
		MoleculeArchiveIndex<M, I> idx = createIndex(jParser);
		if (idx != null) {
			archiveIndex = idx;
			moleculeList = (ArrayList<String>)archiveIndex.getMoleculeUIDSet().stream().sorted().collect(toList());
			metadataList = (ArrayList<String>)archiveIndex.getMetadataUIDSet().stream().sorted().collect(toList());
		}
		return idx;
	}
	
	/**
	 * Create empty MoleculeArchiveIndex.
	 */
	public abstract X createIndex();
	
	/**
	 * Create MoleculeArchiveIndex using JsonParser stream.
	 */
	public abstract X createIndex(JsonParser jParser) throws IOException;
	
	/**
	 * Create empty MoleculeArchiveProperties record.
	 */
	public abstract P createProperties();
	
	/**
	 * Create MoleculeArchiveProperties record using JsonParser stream.
	 */
	public abstract P createProperties(JsonParser jParser) throws IOException;
	
	/**
	 * Create MarsMetadata record using JsonParser stream.
	 */
	public abstract I createMetadata(JsonParser jParser) throws IOException;
	
	/**
	 * Create empty MarsMetadata record with the metaUID specified.
	 */
	public abstract I createMetadata(String metaUID);
	
	/**
	 * Create empty Molecule record.
	 */
	public abstract M createMolecule();
	
	/**
	 * Create Molecule record using the JsonParser stream given.
	 */
	public abstract M createMolecule(JsonParser jParser) throws IOException;
	
	/**
	 * Create empty Molecule record with the UID specified.
	 */
	public abstract M createMolecule(String UID);
	
	/**
	 * Create Molecule record using the UID and {@link MarsTable} specified.
	 */
	public abstract M createMolecule(String UID, MarsTable table);
	
	@Override
	public String toString() {
		return name;
	}
}
