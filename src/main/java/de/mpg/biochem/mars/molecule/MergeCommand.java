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
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import de.mpg.biochem.mars.util.LogBuilder;

@Plugin(type = Command.class, label = "Merge Archives", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule Utils", weight = 1,
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
	
	ArrayList<MoleculeArchiveProperties> allArchiveProps;
	ArrayList<ArrayList<ImageMetaData>> allMetaDataItems;
	
	ArrayList<String> metaUIDs;
	
	@Override
	public void run() {				
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Merge Archives");
		builder.addParameter("Directory", directory.getAbsolutePath());
		builder.addParameter("Use smile encoding", String.valueOf(smileEncoding));
		log += builder.buildParameterList();
		logService.info(log);
		
		 // create new filename filter
        FilenameFilter fileNameFilter = new FilenameFilter() {
  
           @Override
           public boolean accept(File dir, String name) {
              if(name.lastIndexOf('.')>0) {
              
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
			allArchiveProps = new ArrayList<MoleculeArchiveProperties>();
			allMetaDataItems = new ArrayList<ArrayList<ImageMetaData>>();
			metaUIDs = new ArrayList<String>();
			
			for (File file: archiveFileList) {
				try {
					loadArchiveDetails(file);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			//Check for duplicate ImageMetaData items
			for (ArrayList<ImageMetaData> archiveMetaList : allMetaDataItems) {
				for (ImageMetaData metaItem : archiveMetaList) {
					String metaUID = metaItem.getUID();
					if (metaUIDs.contains(metaUID)) {
						logService.info("Duplicate ImageMetaData record " + metaUID + " found.");
						logService.info("Are you trying to merge copies of the same dataset?");
						logService.info("Please resolve the conflict and run the merge command again.");
						logService.info(builder.endBlock(false));
						return;
					} else {
						metaUIDs.add(metaUID);
					}
				}
			}
		
			//No conflicts found so we start building and writing the merged file
			//First we need to build the global MoleculeArchiveProperties
			int numMolecules = 0; 
			int numImageMetaData = 0;
			double totalSize = 0;
			String globalComments = "";
			int count = 0;
			for (MoleculeArchiveProperties archiveProperties : allArchiveProps) {
				numMolecules += archiveProperties.getNumberOfMolecules();
				numImageMetaData += archiveProperties.getNumImageMetaData();
				totalSize += archiveProperties.getAverageMoleculeSize()*archiveProperties.getNumberOfMolecules();
				globalComments += "Comments from Merged Archive " + archiveFileList[count].getName() + ":\n" + archiveProperties.getComments() + "\n";
				count++;
			}
			double averageMoleculeSize = totalSize/numMolecules;
				
			MoleculeArchiveProperties newArchiveProperties = new MoleculeArchiveProperties();
			newArchiveProperties.setNumberOfMolecules(numMolecules);
			newArchiveProperties.setAverageMoleculeSize(averageMoleculeSize);
			newArchiveProperties.setNumImageMetaData(numImageMetaData);
			newArchiveProperties.setComments(globalComments);
			
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
				
				newArchiveProperties.toJSON(jGenerator);
				
				jGenerator.writeArrayFieldStart("ImageMetaData");
				for (ArrayList<ImageMetaData> archiveMetaList : allMetaDataItems) {
					for (ImageMetaData metaItem : archiveMetaList) {
						metaItem.toJSON(jGenerator);
					}
				}	
				jGenerator.writeEndArray();
				
				//Now we need to loop through all molecules in all archives and save them to the merged archive.
				jGenerator.writeArrayFieldStart("Molecules");
				for (File file: archiveFileList) {
					try {
						mergeMolecules(file, jGenerator);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				jGenerator.writeEndArray();
				
				//Now we need to add the corresponding global closing bracket } for the json format...
				jGenerator.writeEndObject();
				jGenerator.close();
				
				//flush and close streams...
				stream.flush();
				stream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
			logService.info("Merged " + archiveFileList.length + " yama files into the output archive merged.yama");
			logService.info("In total " + newArchiveProperties.getNumImageMetaData() + " Datasets were merged.");
			logService.info("In total " + newArchiveProperties.getNumberOfMolecules() + " molecules were merged.");
			logService.info(builder.endBlock(true));
		} else {
			logService.info("No .yama files in this directory.");
			logService.info(builder.endBlock(false));
		}
	}
	
	public void loadArchiveDetails(File file) throws JsonParseException, IOException {
		//First load MoleculeArchiveProperties
		InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
		
		//Here we automatically detect the format of the JSON file
		//Can be JSON text or Smile encoded binary file...
		JsonFactory jsonF = new JsonFactory();
		SmileFactory smileF = new SmileFactory(); 
		DataFormatDetector det = new DataFormatDetector(new JsonFactory[] { jsonF, smileF });
	    DataFormatMatcher match = det.findFormat(inputStream);
	    JsonParser jParser = match.createParserWithMatch();
		
		jParser.nextToken();
		jParser.nextToken();
		if ("MoleculeArchiveProperties".equals(jParser.getCurrentName())) {
			allArchiveProps.add(new MoleculeArchiveProperties(jParser, null));
		} else {
			logService.info("The file " + file.getName() + " have to MoleculeArchiveProperties. Is this a proper yama file?");
			return;
		}
		
		ArrayList<ImageMetaData> metaArchiveList = new ArrayList<ImageMetaData>();
		
		//Next load ImageMetaData items
		while (jParser.nextToken() != JsonToken.END_OBJECT) {
			String fieldName = jParser.getCurrentName();
			if ("ImageMetaData".equals(fieldName)) {
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					metaArchiveList.add(new ImageMetaData(jParser));
				}
			}
			
			if ("Molecules".equals(fieldName)) {
				allMetaDataItems.add(metaArchiveList);
				//We first have to check all ImageMetaData items to ensure there are no duplicates...
				jParser.close();
				inputStream.close();
				return;
			}
		}
	}
	
	//Method which takes all molecule records from a single archive and adds them together by directly streaming 
	//them to the merged.yama file through jGenerator...
	public void mergeMolecules(File file, JsonGenerator jGenerator) throws JsonParseException, IOException {
		//First load MoleculeArchiveProperties
		InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
		
		//Here we automatically detect the format of the JSON file
		//Can be JSON text or Smile encoded binary file...
		JsonFactory jsonF = new JsonFactory();
		SmileFactory smileF = new SmileFactory(); 
		DataFormatDetector det = new DataFormatDetector(new JsonFactory[] { jsonF, smileF });
	    DataFormatMatcher match = det.findFormat(inputStream);
	    JsonParser jParser = match.createParserWithMatch();
		
	    //We need to parse all the way to the molecule records since we already added everything else...
	    //For the moment I just parse again all the first parts and do nothing with them
	    //I guess it would be better to save an index of all the open files..
		jParser.nextToken();
		jParser.nextToken();
		if ("MoleculeArchiveProperties".equals(jParser.getCurrentName())) {
			new MoleculeArchiveProperties(jParser, null);
		}
		
		//Next load ImageMetaData items
		while (jParser.nextToken() != JsonToken.END_OBJECT) {
			String fieldName = jParser.getCurrentName();
			if ("ImageMetaData".equals(fieldName)) {
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					new ImageMetaData(jParser);
				}
			}
			
			if ("Molecules".equals(fieldName)) {
				while (jParser.nextToken() != JsonToken.END_ARRAY) {
					//Read in molecule record
					Molecule molecule = new Molecule(jParser);
					
					//write out molecule record
					molecule.toJSON(jGenerator);
				}
			}
		}
		jParser.close();
		inputStream.close();
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
