package de.mpg.biochem.sdmm.ImageProcessing;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.Previewable;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.ImagePlus;

import java.awt.geom.Rectangle2D;

@Plugin(type = Command.class,
menuPath = "Plugins>SDMM Plugins>Image Processing>Discoidal Averaging Filter")
public class DiscoidalAveragingFilterCommand<T extends RealType< T >> implements Command {
	
	@Parameter
	private int innerRadius;
	
	@Parameter
	private LogService logService;
	
	@Parameter
	private int outerRadius;

	@Parameter(label = "Image to Filter")
	private ImagePlus input;

	//@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false,
	//		callback = "previewChanged")
	//	private boolean preview;
	
	@Parameter(label = "Filtered Image", type = ItemIO.OUTPUT)
	private ImagePlus output;

	@Override
	public void run() {
		output = DiscoidalAveragingFilter.calcDiscoidalAveragedImage(input, innerRadius, outerRadius);
	}

	//@Override
	//public void cancel() {
		// TODO Auto-generated method stub
		
	//}
	
	///** Called when the {@link #preview} parameter value changes. */
	//protected void previewChanged() {
		// When preview box reset everything...
	//	if (!preview) cancel();
	//}

	//@Override
	//public void preview() {
	//	if (preview) run();
	//}

}
