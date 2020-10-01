/*-
 * #%L
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
package de.mpg.biochem.mars.molecule.commands;

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
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static java.util.stream.Collectors.toList;

import org.scijava.Context;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.ui.DialogPrompt.MessageType;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEImage;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsUtil;

@Plugin(type = Command.class, label = "Merge Archives", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Mars", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule", weight = 1,
			mnemonic = 'm'),
		@Menu(label = "Merge Archives", weight = 90, mnemonic = 'm')})
public class MergeCommand extends DynamicCommand {
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
	
	@Parameter(label="Use smile encoding")
	private boolean smileEncoding = true;
	
	@Override
	public void run() {				
		LogBuilder builder = new LogBuilder();
		
		String log = LogBuilder.buildTitleBlock("Merge Archives");
		builder.addParameter("Directory", directory.getAbsolutePath());
		builder.addParameter("Use smile encoding", String.valueOf(smileEncoding));
		log += builder.buildParameterList();
		logService.info(log);
		
		 // create new filename filter
        FilenameFilter fileNameFilter = new FilenameFilter() {
  
           @Override
           public boolean accept(File dir, String name) {
        	  if (name.startsWith("."))
        		  return false;
        	   
              if(name.lastIndexOf('.') > 0) {
              
                 // get last index for '.' char
                 int lastIndex = name.lastIndexOf('.');
                 
                 // get extension
                 String str = name.substring(lastIndex);
                 
                 // match path name extension
                 if(str.equals(".yama")) {
                    return true;
                 }
              }
              
              return false;
           }
        };
		
		File[] archiveFileList = directory.listFiles(fileNameFilter);
		if (archiveFileList.length > 0) {
			//retrieve the types of all archives.
			ArrayList<String> archiveTypes = new ArrayList<String>();
			for (File file: archiveFileList) {
				try {
					archiveTypes.add(moleculeArchiveService.getArchiveTypeFromYama(file));
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
						logService.info(archiveFileList[i].getName() + " is type " + archiveTypes.get(i));
					return;
				}
			}
			
			//No conflicts found so we start building and writing the merged file
			MoleculeArchive<?,?,?> mergedArchiveType = moleculeArchiveService.createArchive(archiveType);
			MoleculeArchiveProperties mergedProperties = mergedArchiveType.createProperties();
			
			mergedProperties.setParent(mergedArchiveType);
			
			//Initialize all file streams and parsers
			ArrayList<InputStream> fileInputStreams = new ArrayList<InputStream>();
			ArrayList<JsonParser> jParsers = new ArrayList<JsonParser>();
			
			try {
				for (File file : archiveFileList) {
					InputStream inputStream = new BufferedInputStream(new FileInputStream(file));

					JsonFactory jsonF = new JsonFactory();
					SmileFactory smileF = new SmileFactory(); 
					DataFormatDetector det = new DataFormatDetector(new JsonFactory[] { jsonF, smileF });
				    DataFormatMatcher match = det.findFormat(inputStream);
				    JsonParser jParser = match.createParserWithMatch();
				    
					jParser.nextToken();
					jParser.nextToken();
					if ("properties".equals(jParser.getCurrentName()) || "MoleculeArchiveProperties".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						mergedProperties.merge(mergedArchiveType.createProperties(jParser), file.getName());
					} else {
						logService.error("Can't find MoleculeArchiveProperties field in file " + file.getName() + ". Aborting.");
						return;
					}
					fileInputStreams.add(inputStream);
					jParsers.add(jParser);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			String archiveList = "";
			for (int i=0; i<archiveFileList.length; i++) 
				archiveList += archiveFileList[i].getName() + ", ";
			if (archiveFileList.length > 0)
				archiveList = archiveList.substring(0, archiveList.length() - 2);
			
			log += "Merged " + archiveFileList.length + " yama files into the output archive merged.yama\n";
			log += "Including: " + archiveList + "\n";
			log += "In total " + mergedProperties.getNumberOfMetadatas() + " datasets were merged.\n";
			log += "In total " + mergedProperties.getNumberOfMolecules() + " molecules were merged.\n";
			log += LogBuilder.endBlock(true);
			
			
			//read in all MarsMetadata items from all archives - I hope they fit in memory :)
			ArrayList<MarsMetadata> allMetadataItems = new ArrayList<MarsMetadata>();
			ArrayList<String> metaUIDs = new ArrayList<String>();
			
			for (JsonParser jParser :jParsers) {
				try {
					while (jParser.nextToken() != JsonToken.END_OBJECT) {
						String fieldName = jParser.getCurrentName();
						if ("metadata".equals(fieldName) || "ImageMetaData".equals(fieldName) || "ImageMetadata".equals(fieldName) || "Metadata".equals(fieldName)) {
							while (jParser.nextToken() != JsonToken.END_ARRAY) {
								//This line would be more generic but the translator from old formats is blocking that for now
								//This should be restored.
								//metadataList.add(mergedArchiveType.createMetadata(jParser));
								allMetadataItems.add(new MarsOMEMetadata(jParser));
							}
						}
						
						if ("molecules".equals(fieldName) || "Molecules".equals(fieldName))
							break;
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			Set<String> duplicateMetadataUIDs = new HashSet<String>();
			
			//First make a list of duplicates if there are duplicates
			for (MarsMetadata metaItem : allMetadataItems) {
				String metaUID = metaItem.getUID();
				if (metaUIDs.contains(metaUID)) {
					duplicateMetadataUIDs.add(metaUID);
				} else {
					metaUIDs.add(metaUID);
					metaItem.logln(log);
				}
			}
			
			Map<String, ArrayList<MarsMetadata>> duplicateMetadatas = new HashMap<String, ArrayList<MarsMetadata>>();
			
			for (String duplicateMetaUID : duplicateMetadataUIDs) {
				Set<Integer> imageIndexes = new HashSet<Integer>();
				ArrayList<MarsMetadata> listofDuplicates = new ArrayList<MarsMetadata>();
				for (MarsMetadata metaItem : allMetadataItems) {
					if (metaItem.getUID().equals(duplicateMetaUID)) {
						listofDuplicates.add(metaItem);
						for (int imageIndex = 0; imageIndex < metaItem.getImageCount(); imageIndex++) {
							if (imageIndexes.contains(metaItem.getImage(imageIndex).getImageID())) {
								logService.info("Duplicate metadata record " + duplicateMetaUID + " image " + metaItem.getImage(imageIndex).getImageID() + " found.");
								logService.info("Are you trying to merge copies of the same dataset?");
								logService.info("Please resolve the conflict and run the merge command again.");
								logService.info(LogBuilder.endBlock(false));
								uiService.showDialog("Merge failed due to duplicate metadata record " + duplicateMetaUID + " image " + metaItem.getImage(imageIndex).getImageID() + ".\n"
										+ "Please resolve the conflict before merging.", MessageType.ERROR_MESSAGE);
								return;
							} else {
								imageIndexes.add(metaItem.getImage(imageIndex).getImageID());
							}
						}
					}
				}
				duplicateMetadatas.put(duplicateMetaUID, listofDuplicates);
			}

			//Now we need to merge any duplicate Metadata records that contain different positions
			for (String duplicateMetaUID : duplicateMetadatas.keySet()) {
				List<MarsMetadata> duplicates = duplicateMetadatas.get(duplicateMetaUID);
				MarsMetadata mergedMetadata = duplicates.get(0);

				for (int i=1; i< duplicates.size(); i++) {
					mergedMetadata.merge(duplicates.get(i));
				}

				for (int i=0; i < allMetadataItems.size(); i++) {
					if (allMetadataItems.get(i).getUID().equals(duplicateMetaUID)) {
						allMetadataItems.remove(i);
						i--;
					}
				}
				allMetadataItems.add(mergedMetadata);
			}
			
			//Now we just need to write the file starting with the new MoleculeArchiveProperties
			File fileOUT = new File(directory.getAbsolutePath() + "/merged.yama");
			try {
				OutputStream stream = new BufferedOutputStream(new FileOutputStream(fileOUT));
				
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
				
				jGenerator.writeFieldName("properties");
				mergedProperties.toJSON(jGenerator);
				
				jGenerator.writeArrayFieldStart("metadata");
				for (MarsMetadata metaItem : allMetadataItems) {
					metaItem.toJSON(jGenerator);
				}
				jGenerator.writeEndArray();
				
				//Now we need to loop through all molecules in all archives and save them to the merged archive.
				jGenerator.writeArrayFieldStart("molecules");
				for (JsonParser jParser :jParsers) {
					while (jParser.nextToken() != JsonToken.END_ARRAY)
						mergedArchiveType.createMolecule(jParser).toJSON(jGenerator);
					
					jParser.close();
				}
				jGenerator.writeEndArray();
				
				//Now we need to add the corresponding global closing bracket } for the json format...
				jGenerator.writeEndObject();
				jGenerator.close();
				
				//flush and close streams...
				stream.flush();
				stream.close();
				
				for (InputStream inputStream : fileInputStreams) 
					inputStream.close();
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			logService.info("Merged " + archiveFileList.length + " yama files into the output archive merged.yama");
			logService.info("Including: " + archiveList);
			logService.info("In total " + mergedProperties.getNumberOfMetadatas() + " datasets were merged.");
			logService.info("In total " + mergedProperties.getNumberOfMolecules() + " molecules were merged.");
			logService.info(LogBuilder.endBlock(true));
		} else {
			logService.info("No .yama files in this directory.");
			logService.info(LogBuilder.endBlock(false));
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
