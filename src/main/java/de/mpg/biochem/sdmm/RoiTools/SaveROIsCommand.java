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
package de.mpg.biochem.sdmm.RoiTools;

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
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
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
