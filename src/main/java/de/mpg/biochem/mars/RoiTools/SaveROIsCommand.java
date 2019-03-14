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
package de.mpg.biochem.mars.RoiTools;

import java.io.File;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

//This needs to be updated to work fully with IJ2 in headless mode...

@Plugin(type = Command.class, label = "Save ROIs", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "ROI Tools", weight = 30,
			mnemonic = 'r'),
		@Menu(label = "Save ROIs", weight = 10, mnemonic = 's')})
public class SaveROIsCommand extends DynamicCommand implements Command {
		@Parameter
		private LogService logService;
		
		@Parameter
		private UIService uiService;
		
		@Parameter
		private RoiManager roiManager;
		
		@Parameter
		private ImagePlus image;
		
		@Parameter(label="Choose output directory", style = "directory")
		private File directory;
	
		@Override
		public void run() {
			Roi[] rois = roiManager.getRoisAsArray();
			
			ImageStack stack = image.getStack();
			
			ImagePlus[] roiImages = new ImagePlus[rois.length];
			for (int i=0; i< rois.length; i++) {
				roiImages[i] = IJ.createImage(roiManager.getName(i), rois[i].getBounds().width, rois[i].getBounds().height, stack.getSize(), image.getBitDepth());		
			}
			
			long elapsedTime = System.currentTimeMillis();	
	        for (int i=1; i<=stack.getSize(); i++) {
	        	ImageProcessor ip2 = stack.getProcessor(i);
	        	for (int w=0; w<rois.length ; w++) {
		            ip2.setRoi(rois[w].getBounds());
		            ImageProcessor curFrame = roiImages[w].getStack().getProcessor(i);
		            curFrame.insert(ip2.crop(), 0, 0);
	        	}
	        	
	        	//The progress bar can only be updated every 90 ms.  
	        	//If you try to do it more rapidly it never updates.
	        	if (System.currentTimeMillis() - elapsedTime > 200) {
	        		IJ.showProgress(i, stack.getSize());
	        		elapsedTime = 0L;
	        	}
	        }
	        
	        //Now we save them to the HD
	        for (int i=0;i<roiImages.length;i++) {
	        	IJ.save(roiImages[i], directory + "/" + roiManager.getName(i) + ".tif");
	        }
	        
	        //uiService showStatus
		}
}
