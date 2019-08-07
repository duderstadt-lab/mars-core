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
package de.mpg.biochem.mars.ImageProcessing.commands;

import net.imagej.ops.Initializable;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Previewable;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.mpg.biochem.mars.ImageProcessing.DiscoidalAveragingFilter;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

@Plugin(type = Command.class, label = "Discoidal Averaging Filter", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "MoleculeArchive Suite", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Image Processing", weight = 20,
			mnemonic = 'm'),
		@Menu(label = "Discoidal Averaging Filter", weight = 40, mnemonic = 'd')})
public class DiscoidalAveragingFilterCommand<T extends RealType< T >> extends DynamicCommand implements Command, Initializable {
	
	@Parameter
	private LogService logService;
	
	@Parameter(label = "Image to Filter")
	private ImagePlus input;
	
	//ROI SETTINGS
	@Parameter(label="use ROI", persist=false)
	private boolean useROI = true;
	
	@Parameter(label="ROI x0", persist=false)
	private int x0;
	
	@Parameter(label="ROI y0", persist=false)
	private int y0;
	
	@Parameter(label="ROI width", persist=false)
	private int w;
	
	@Parameter(label="ROI height", persist=false)
	private int h;
	
	@Parameter
	private int innerRadius;
	
	@Parameter
	private int outerRadius;
	
	//@Parameter
	//private boolean mirrorBoundaries = false;
	
	@Parameter(label = "Filtered Image", type = ItemIO.OUTPUT)
	private ImagePlus output;
	
	private Rectangle rect;
	private Roi startingRoi;

	@Override
	public void initialize() {
		if (input.getRoi() == null) {
			rect = new Rectangle(0,0,input.getWidth()-1,input.getHeight()-1);
			final MutableModuleItem<Boolean> useRoifield = getInfo().getMutableInput("useROI", Boolean.class);
			useRoifield.setValue(this, false);
		} else {
			rect = input.getRoi().getBounds();
			startingRoi = input.getRoi();
		}
		
		final MutableModuleItem<Integer> imgX0 = getInfo().getMutableInput("x0", Integer.class);
		imgX0.setValue(this, rect.x);
		
		final MutableModuleItem<Integer> imgY0 = getInfo().getMutableInput("y0", Integer.class);
		imgY0.setValue(this, rect.y);
		
		final MutableModuleItem<Integer> imgWidth = getInfo().getMutableInput("w", Integer.class);
		imgWidth.setValue(this, rect.width);
		
		final MutableModuleItem<Integer> imgHeight = getInfo().getMutableInput("h", Integer.class);
		imgHeight.setValue(this, rect.height);
	}
	
	@Override
	public void run() {
		
		//Rectangle roi = new Rectangle(x0, y0, w, h);
		
		//if (mirrorBoundaries) {
			output = new ImagePlus( input.getTitle() + "(Discoidal Averaged)" , DiscoidalAveragingFilter.calcDiscoidalAveragedImageInfiniteMirror(input.getProcessor(), innerRadius, outerRadius, rect));
		//} else {
		//	output = DiscoidalAveragingFilter.calcDiscoidalAveragedImage(input, innerRadius, outerRadius);
		//}
	}

}
