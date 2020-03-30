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
package de.mpg.biochem.mars.molecule.commands;

import static java.util.stream.Collectors.toList;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.io.FileUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsUtil;
import de.mpg.biochem.mars.molecule.*;

@Plugin(type = Command.class, label = "Merge Virtual Stores", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule", weight = 1,
			mnemonic = 'm'),
		@Menu(label = "Merge Virtual Stores", weight = 100, mnemonic = 'm')})
public class MergeVirtualStoresCommand extends DynamicCommand {
	@Parameter
	private LogService logService;

    @Parameter
    private StatusService statusService;

	@Parameter
    private MoleculeArchiveService moleculeArchiveService;

	@Parameter
    private UIService uiService;

	@Parameter(label="Directory", style="directory")
    private File directory;

	@Parameter(label="IO encoding is Smile")
	private boolean smileEncoding = true;

	@Override
	public void run() {
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Merge Virtual Stores");
		builder.addParameter("Directory", directory.getAbsolutePath());
		builder.addParameter("IO encoding is Smile", String.valueOf(smileEncoding));
		log += builder.buildParameterList();
		logService.info(log);
	
		ArrayList<MoleculeArchive<?,?,?>> archives = new ArrayList<MoleculeArchive<?,?,?>>();
		
		FilenameFilter fileNameFilter = new FilenameFilter() {
           @Override
           public boolean accept(File dir, String name) {
              if (name.endsWith(".yama.store"))
            	  return true;
        	  else
        		  return false;
           }
        };

        //Get all virtual archive folders
		File[] archiveDirectoryList = directory.listFiles(fileNameFilter);
		
		//Create merged archive folder.
		File newVirtualDirectory = new File(directory.getAbsolutePath() + "/merged.yama.store");
		newVirtualDirectory.mkdirs();
		
		if (archiveDirectoryList.length > 0) {
			//retrieve the types of all archives.
			ArrayList<String> archiveTypes = new ArrayList<String>();
			for (File file: archiveDirectoryList) {
				try {
					archiveTypes.add(MarsUtil.getArchiveTypeFromStore(new File(file.getAbsolutePath() + "/MoleculeArchiveProperties.json")));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			//Check that all have the same type
			String archiveType = archiveTypes.get(0);
			for (String type : archiveTypes) {
				if (!archiveType.equals(type)) {
					logService.info("Not all archives are of the same type. Aborting merge.");
					for (int i=0;i< archiveTypes.size();i++)
						logService.info(archiveDirectoryList[i].getName() + " is type " + archiveTypes.get(i));
					return;
				}
			}
			
			for (File virtualDirectory: archiveDirectoryList) {
				MoleculeArchive<?,?,?> archive = MarsUtil.createMoleculeArchive(archiveType, virtualDirectory);
				
				if (archive.isSMILEInputEncoding() && !smileEncoding) {
					logService.error("IO encoding was set to JSON but " + virtualDirectory.getName() + " has Smile format. All virtual stores to be merged must have the format specified. Aborting...");
			    	logService.error(LogBuilder.endBlock(false));
			    	return;
				} else if (!archive.isSMILEInputEncoding() && smileEncoding) {
					logService.error("IO encoding was set to Smile but " + virtualDirectory.getName() + " has JSON format. All virtual stores to be merged must have the format specified. Aborting...");
			    	logService.error(LogBuilder.endBlock(false));
			    	return;
				}
				archives.add(archive);
			}
			
			//No conflicts found so we start building and writing the merged file
			MoleculeArchive<?,?,?> mergedArchiveType = MarsUtil.createMoleculeArchive(archiveType);
			MoleculeArchiveProperties mergedProperties = mergedArchiveType.createProperties();			
		
			//Build JSON factory with specified encoding.
			JsonFactory jfactory;
			if (smileEncoding) {
				mergedArchiveType.setSMILEOutputEncoding();
				jfactory = new SmileFactory();
			} else {
				mergedArchiveType.unsetSMILEOutputEncoding();
				jfactory = new JsonFactory();
			}
			
			int numMolecules = 0;
			int numMetadata = 0;
			String globalComments = "";
			for (MoleculeArchive<?,?,?> archive : archives) {
				MoleculeArchiveProperties properties = archive.properties();
				numMolecules += properties.getNumberOfMolecules();
				numMetadata += properties.getNumberOfMetadatas();
				globalComments += "Comments from Merged Archive " + archive.getName() + ":\n" + properties.getComments() + "\n";
				
				//update global indexes
				mergedProperties.addAllTags(properties.getTagSet());
				mergedProperties.addAllParameters(properties.getParameterSet());
				mergedProperties.addAllColumns(properties.getColumnSet());
			}
	
			mergedProperties.setNumberOfMolecules(numMolecules);
			mergedProperties.setNumberOfMetadatas(numMetadata);
			mergedProperties.setComments(globalComments);
			
			try {
				File propertiesFile = new File(newVirtualDirectory.getAbsolutePath() + "/MoleculeArchiveProperties.json");
				OutputStream stream = new BufferedOutputStream(new FileOutputStream(propertiesFile));
				
				JsonGenerator jGenerator = jfactory.createGenerator(stream);
				jGenerator.writeStartObject();
				mergedProperties.toJSON(jGenerator);
				jGenerator.writeEndObject();
				jGenerator.close();
				
				stream.flush();
				stream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			//Now we need to create the indexes file from all the archive records..
			ArrayList<String> moleculeIndex = new ArrayList<String>();
			ArrayList<String> metadataIndex = new ArrayList<String>();
			Set<String> virtualMoleculesSet = ConcurrentHashMap.newKeySet();
			Set<String> virtualMetadataSet = ConcurrentHashMap.newKeySet();
			
			ConcurrentMap<String, String> moleculeMetadataUIDIndex = new ConcurrentHashMap<>();
			ConcurrentMap<String, LinkedHashSet<String>> tagIndex = new ConcurrentHashMap<>();
			ConcurrentMap<String, LinkedHashSet<String>> metadataTagIndex = new ConcurrentHashMap<>();
			
			for (MoleculeArchive<?, ?, ?> archive : archives) {
				for (String UID : archive.getMoleculeUIDs()) {
					if (virtualMoleculesSet.contains(UID)) {
						logService.error("Duplicate molecule entry found in virtual store " + archive.getName() + ". Resolve conflict and try merge again. Aborting...");
						return;
					}
					moleculeIndex.add(UID);
					virtualMoleculesSet.add(UID);
					moleculeMetadataUIDIndex.put(UID, archive.getMetadataUIDforMolecule(UID));
					tagIndex.put(UID, archive.getTagSet(UID));
				}
				
				for (String metaUID : archive.getMetadataUIDs()) {
					if (virtualMetadataSet.contains(metaUID)) {
						logService.error("Duplicate metadata entry found in virtual store " + archive.getName() + ". Resolve conflict and try merge again. Aborting...");
						return;
					}
					metadataIndex.add(metaUID);
					virtualMetadataSet.add(metaUID);
					if (archive.getMetadataTagSet(metaUID) != null)
						metadataTagIndex.put(metaUID, archive.getMetadataTagSet(metaUID));
				}
			}
			
			//Let's release the memory used up by the archives..
			archives = null;
			
			//Before we write the new indexes file we should natural order sort the entries.
			moleculeIndex = (ArrayList<String>)moleculeIndex.stream().sorted().collect(toList());
			metadataIndex = (ArrayList<String>)metadataIndex.stream().sorted().collect(toList());
			
			File indexFile = new File(newVirtualDirectory.getAbsolutePath() + "/indexes.json");
			OutputStream stream;
			try {
				stream = new BufferedOutputStream(new FileOutputStream(indexFile));
				
				JsonGenerator jGenerator = jfactory.createGenerator(stream);
				
				jGenerator.writeStartObject();
	
				//Write MetadataIndex
				jGenerator.writeFieldName("MetadataIndex");
				jGenerator.writeStartArray();
				for (String metaUID : metadataIndex) {
					jGenerator.writeStartObject();
					jGenerator.writeStringField("UID", metaUID);
					
					if (metadataTagIndex.containsKey(metaUID)) {
						jGenerator.writeArrayFieldStart("Tags");
						for (String tag : metadataTagIndex.get(metaUID)) {
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
					jGenerator.writeStringField("MetadataUID", moleculeMetadataUIDIndex.get(UID));
					
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
			
			//Now that the MoleculeArchiveProperties is done and the indexes are done
			//We need to copy all the metadata records and molecule records.
			File newMetadataDirectory = new File(newVirtualDirectory.getAbsolutePath() + "/Metadata");
            newMetadataDirectory.mkdirs();
            
            File newMoleculeDirectory = new File(newVirtualDirectory.getAbsolutePath() + "/Molecules");
            newMoleculeDirectory.mkdirs();
			
			ArrayList<File> virtualStoreDirectoryList = new ArrayList<File>();
			for (File virtualDirectory: archiveDirectoryList) {
				virtualStoreDirectoryList.add(virtualDirectory);
			}
			
			FilenameFilter nameFilter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                   if (name.endsWith(".json"))
                 	  return true;
             	  else
             		  return false;
                }
             };
			
			final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
			ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
			try {
		        forkJoinPool.submit(() -> virtualStoreDirectoryList.parallelStream().forEach(directory -> { 
		        	try {
			        	File[] metaDataRecords = new File(directory.getAbsolutePath() + "/Metadata").listFiles(nameFilter);
			        	for (File metaDataRecord : metaDataRecords) {
			        		FileUtils.copyFileToDirectory(metaDataRecord, newMetadataDirectory);
			        	}
			        	
			        	File[] moleculeRecords = new File(directory.getAbsolutePath() + "/Molecules").listFiles(nameFilter);
			        	for (File moleculeRecord : moleculeRecords) {
			        		FileUtils.copyFileToDirectory(moleculeRecord, newMoleculeDirectory);
			        	}
		        	} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
		        })).get();        
		   } catch (InterruptedException | ExecutionException e ) {
		        // handle exceptions
		    	e.printStackTrace();
		   } finally {
		      forkJoinPool.shutdown();
		   }
			
			String storeList = "";
			for (int i=0; i<archiveDirectoryList.length; i++) 
				storeList += archiveDirectoryList[i].getName() + ", ";
			if (archiveDirectoryList.length > 0)
				storeList = storeList.substring(0, storeList.length() - 2);
			
			//Now we just need to update the metadata logs.
			log += "Merged " + archiveDirectoryList.length + " virtual stores into the output virtual store merged.yama.store\n";
			log += "Including: " + storeList + "\n";
			log += "In total " + mergedProperties.getNumberOfMetadatas() + " MarsMetadata records were merged.\n";
			log += "In total " + mergedProperties.getNumberOfMolecules() + " molecules were merged.\n";
			log += LogBuilder.endBlock(true) + "\n";
			try {
				SingleMoleculeArchive newArchive = new SingleMoleculeArchive(newVirtualDirectory);
				newArchive.logln(log);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			logService.info("Merged " + archiveDirectoryList.length + " virtual stores into the output virtual store merged.yama.store");
			log += "Including: " + storeList + "\n";
			logService.info("In total " + mergedProperties.getNumberOfMetadatas() + " MarsMetadata records were merged.");
			logService.info("In total " + mergedProperties.getNumberOfMolecules() + " molecules were merged.");
			logService.info(LogBuilder.endBlock(true));
		} else {
			logService.error("No .yama.store directories found in the directory given. Nothing to merge. Aborting");
			logService.error(LogBuilder.endBlock(false));
		}
	}

	//Getters and Setters
	public void setDirectory(String dir) {
		directory = new File(dir);
	}

	public void setDirectory(File directory) {
		this.directory = directory;
	}

	public File getDirectory() {
		return directory;
	}

	public void setSmileEncoding(boolean smileEncoding) {
		this.smileEncoding = smileEncoding;
	}

	public boolean getSmileEncoding() {
		return smileEncoding;
	}
}
