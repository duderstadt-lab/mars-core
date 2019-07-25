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
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.io.event.DataOpenedEvent;
import org.scijava.log.LogService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.UIService;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive;
import de.mpg.biochem.mars.util.LogBuilder;

import org.scijava.menu.MenuConstants;
import org.scijava.object.ObjectService;

import net.imagej.ImageJ;

@SuppressWarnings("rawtypes")
@Plugin(type = Command.class, label = "Import archive", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule", weight = 1,
			mnemonic = 'm'),
		@Menu(label = "Import archive", weight = 1, mnemonic = 'o')})
public class ImportMoleculeArchiveCommand extends DynamicCommand {
    @Parameter
    private ImageJ ij;
    
    @Parameter
    private MoleculeArchiveService moleculeArchiveService;
    
    @Parameter
    private LogService logService;
    
    @Parameter
    private ObjectService objectService;
    
    @Parameter
    private UIService uiService;
    
    @Parameter(label="MoleculeArchive (.yama file or .yama.store folder)", style="both")
    private File file;
    
    @Parameter(label="archive.yama", type = ItemIO.OUTPUT)
	private MoleculeArchive archive;
    
	@Override
	public void run() {				
		if (!file.exists())
			System.out.println("File not found.");
		
		String name = file.getName();
		String archiveType = "";
		
		try {
			if (file.isDirectory())
				archiveType = getArchiveType(new File(file.getAbsolutePath() + "/MoleculeArchiveProperties.json"));
			else 
				archiveType = getArchiveType(file);
		} catch (IOException e1) {
			System.out.println("IOException:");
			e1.printStackTrace();
		}
		
		try {
			Class<?> clazz = Class.forName(archiveType);
			Constructor<?> constructor = clazz.getConstructor(File.class);
			archive = (MoleculeArchive)constructor.newInstance(file);
			getInfo().getOutput("archive", MoleculeArchive.class).setLabel(archive.getName());
		} catch (ClassNotFoundException e) {
			System.err.println("MoleculeArchive class not found.");
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (moleculeArchiveService.contains(name)) {
			uiService.showDialog("The MoleculeArchive " + name + " is already open.", MessageType.ERROR_MESSAGE, OptionType.DEFAULT_OPTION);
			return;
		}
		
		objectService.addObject(archive);

		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Importing MoleculeArchive");
		builder.addParameter("Loading File", file.getAbsolutePath());
		builder.addParameter("Archive Name", name);
		
		log += builder.buildParameterList();
		logService.info(log);
		logService.info(LogBuilder.endBlock(true));
	}
    
	private String getArchiveType(File file) throws JsonParseException, IOException {
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
	    
	    String archiveType = "de.mpg.biochem.mars.molecule.SingleMoleculeArchive";
	    
		jParser.nextToken();
		jParser.nextToken();
		if ("MoleculeArchiveProperties".equals(jParser.getCurrentName())) {
			jParser.nextToken();
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
			    String fieldname = jParser.getCurrentName();
			    	
			    if ("ArchiveType".equals(fieldname)) {
			    	jParser.nextToken();
			    	archiveType = jParser.getText();
			    	break;
			    }
			}
		}
		
		jParser.close();
		inputStream.close();
		
		return archiveType;
	}
}
