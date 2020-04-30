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

import org.decimal4j.util.DoubleRounder;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.util.HashMap;

import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;

import java.io.IOException;

import io.scif.services.FormatService;
import io.scif.services.TranslatorService;
import io.scif.Format;
import io.scif.FormatException;
import io.scif.ImageMetadata;
import io.scif.Metadata;
import io.scif.FieldPrinter;
import io.scif.ome.OMEMetadata;

import net.imagej.Dataset;
import ome.xml.meta.OMEXMLMetadata;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import org.scijava.table.DoubleColumn;

@Plugin(type = Command.class, label = "OMEMetadata Creator", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Molecule", weight = 1,
			mnemonic = 'm'),
		@Menu(label = "OMEMetadata Creator", weight = 40, mnemonic = 'o')})
public class OMEMetadataCreator extends DynamicCommand implements Command {
	
    // for determining the Format of the input image
    @Parameter
    private FormatService formatService;
    
    @Parameter
    private TranslatorService translatorService;

    // for logging errors
    @Parameter
    private LogService log;
	
    @Parameter
    private StatusService statusService;
	
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
	@Parameter
    private UIService uiService;
	
	// -- Inputs and outputs to the command --

    // input image
    @Parameter
    private Dataset dataset;

    // output metadata string
    @Parameter(label = "Metadata", type = ItemIO.OUTPUT)
    private String mString;
	
	@Override
	public void run() {	
		
		//final Dataset img = ij.scifio().datasetIO().open("http://imagej.net/images/FluorescentCells.jpg");
		
		// we need the file path to determine the file format
        final String filePath = dataset.getSource();

        // catch any Format or IO exceptions
        try {
        	
        	Metadata metadata = null;
        	
            for (Format format : formatService.getAllFormats())
            	if (format.getFormatName().equals("Micro-Manager-All")) {
            		metadata = format.createParser().parse(filePath);
            		break;
            	}
            
            //for (String key : metadata.getTable().keySet())
            //	System.out.println(key);
            
            //((MicromanagerAllFormat.Metadata) metadata).getPositions().get(0).time = "0";
            
            //for (ImageMetadata position : metadata.get(0))
            //	position

            OMEMetadata omeMeta = new OMEMetadata(getContext());
            translatorService.translate(metadata, omeMeta, true);
            
            // Access some metadata from the OME trove. The below
    		// is just a small example of what the OME API provides.
    		OMEXMLMetadata omexml = omeMeta.getRoot();
    		int iCount = omexml.getInstrumentCount();
    		for (int iIndex = 0; iIndex < iCount; iIndex++) {
    			System.out.println("Instrument #" + iIndex + ":");
    			System.out.println("\tID = " + omexml.getInstrumentID(iIndex));
    			int oCount = omexml.getObjectiveCount(iIndex);
    			for (int oIndex = 0; oIndex < oCount; oIndex++) {
    				System.out.println("\tObjective #" + oIndex + ":");
    				System.out.println("\t\tID = " + omexml.getObjectiveID(iIndex, oIndex));
    				System.out.println("\t\tLensNA = " + omexml.getObjectiveLensNA(iIndex, oIndex));
    				System.out.println("\t\tModel = " + omexml.getObjectiveModel(iIndex, oIndex));
    				System.out.println("\t\tManufacturer = " + omexml.getObjectiveManufacturer(iIndex, oIndex));
    			}
    		}
            
            // use FieldPrinter to traverse metadata tree and return as a String
            String metadataTree = new FieldPrinter(metadata).toString();

            // (optional) remove some of the tree formatting to make the metadata easier to read
            mString = formatMetadata(metadataTree);
        }
        catch (final FormatException | IOException e) {
            log.error(e);
        }
	}
	
	/**
     * This function makes the metadata easier to read by removing some of the tree formatting from FieldPrinter.
     * @param metadataTree raw metadata string returned by FieldPrinter().toString()
     * @return formatted version of metadataTree
     */
    private String formatMetadata(String metadataTree) {

        // remove ending braces | replace ", " between OME fields
        String tmp = metadataTree.replaceAll("(\\t+}\\n)|(,\\s)", "\n");

        // remove beginning braces | remove indenting
        return tmp.replaceAll("(\\t+\\{\\n)|(\\t+)", "");
    }
}
