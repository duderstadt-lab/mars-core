package de.mpg.biochem.sdmm.ImageProcessing;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.Previewable;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.ImagePlus;

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
		@Menu(label = "Discoidal Averaging Filter", weight = 30, mnemonic = 'd')})
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
		/*
		ArrayList<Integer> lis = new ArrayList<Integer>();
		Random ran = new Random();
		
		
		for (int i=0;i<100000000;i++) {
			lis.add(ran.nextInt(1000000000));
		}
		
		logService.info(""+ lis.get(0));
		logService.info(""+ lis.get(1));
		logService.info(""+ lis.get(2));
		logService.info(""+ lis.get(3));
		
		
		
		ForkJoinPool customThreadPool = new ForkJoinPool(8);
		List<Integer> output2 = null;
		
		double time = System.currentTimeMillis();
		try {
			output2 = customThreadPool.submit(
			  () -> lis.parallelStream().sorted().collect(Collectors.toList())).get();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		logService.info("Time " + (System.currentTimeMillis() - time));
		logService.info(""+ output2.get(0));
		logService.info(""+ output2.get(1));
		logService.info(""+ output2.get(2));
		logService.info(""+ output2.get(3));
		*/
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
