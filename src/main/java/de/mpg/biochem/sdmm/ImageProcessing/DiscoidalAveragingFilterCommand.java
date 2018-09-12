package de.mpg.biochem.sdmm.ImageProcessing;

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
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
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
