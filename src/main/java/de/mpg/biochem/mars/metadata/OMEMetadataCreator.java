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
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import java.util.HashMap;

import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import ij.ImageJ;

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
import net.imagej.ImgPlus;
import ome.xml.meta.OMEXMLMetadata;

import ij.ImagePlus;
import io.scif.Metadata;
import io.scif.filters.AbstractMetadataWrapper;
import io.scif.img.SCIFIOImgPlus;
import io.scif.ome.formats.OMETIFFFormat;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.display.ImageDisplay;

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
		
		// we need the file path to determine the file format
        //final String filePath = dataset.getSource();
        	
    	ImgPlus<?> imp = dataset.getImgPlus();
    	
    	if (!(imp instanceof SCIFIOImgPlus)) {
			uiService.showDialog("This image has not been opened with SCIFIO.", DialogPrompt.MessageType.ERROR_MESSAGE);
			return;
		}
    	
    	SCIFIOImgPlus<?> sciImp = (SCIFIOImgPlus<?>) imp;
		Metadata metadata = sciImp.getMetadata();
    	
        //for (Format format : formatService.getAllFormats())
        //	if (format.getFormatName().equals("MarsMicromanager")) {
        //		metadata = format.createParser().parse(filePath);
        //		break;
        //	}
        
        OMEMetadata omeMeta = new OMEMetadata(getContext());
        translatorService.translate(metadata, omeMeta, true);
        
        mString = omeMeta.getRoot().dumpXML();
	}
}
