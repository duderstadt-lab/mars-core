/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2025 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package de.mpg.biochem.mars.image.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.IntStream;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsUtil;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import net.imagej.DatasetService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

@Plugin(type = Command.class, label = "Overlay Channels", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
			label = "Image", weight = 1, mnemonic = 'i'), @Menu(label = "Util",
				weight = 7, mnemonic = 'u'), @Menu(label = "Overlay Channels",
					weight = 10, mnemonic = 'o') })
public class OverlayChannelsCommand extends DynamicCommand implements Command {

	/**
	 * SERVICES
	 */
	@Parameter
	private LogService logService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private ConvertService convertService;

	@Parameter
	private DatasetService datasetService;

	/**
	 * IMAGES
	 */
	@Parameter(label = "Add to me", choices = { "a", "b", "c" })
	private String addToMeName;

	@Parameter(label = "Transform me", choices = { "a", "b", "c" })
	private String transformMeName;

	/**
	 * AFFINE 2D TRANSFORMATION MATRIX
	 */
	@Parameter(label = "Keep originals")
	private boolean keep = false;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String affineTitle = "Affine2D Transformation Matrix:";

	@Parameter(label = "m00")
	private double m00;

	@Parameter(label = "m01")
	private double m01;

	@Parameter(label = "m02")
	private double m02;

	@Parameter(label = "m10")
	private double m10;

	@Parameter(label = "m11")
	private double m11;

	@Parameter(label = "m12")
	private double m12;

	@Parameter(label = "Merged image", type = ItemIO.OUTPUT)
	private ImagePlus imgOut;

	@Parameter(label = "Threads", required = false, min = "1", max = "120")
	private int nThreads = Runtime.getRuntime().availableProcessors();

	// A map from slice to new transformed image
	private ConcurrentMap<Integer, ImagePlus> transformedImageMap;

	private ImagePlus addToMe, transformMe;

	@Override
	public void initialize() {
		final MutableModuleItem<String> addToMeItems = getInfo().getMutableInput(
			"addToMeName", String.class);
		final MutableModuleItem<String> transformMeItems = getInfo()
			.getMutableInput("transformMeName", String.class);

		// HACK: IJ1 workaround for issues in scijava/scifio related to
		// getting images.
		int numberOfImages = WindowManager.getImageCount();
		List<String> imageNames = new ArrayList<>();

		for (int i = 0; i < numberOfImages; i++)
			imageNames.add(WindowManager.getImage(i + 1).getTitle());

		addToMeItems.setChoices(imageNames);
		transformMeItems.setChoices(imageNames);
	}

	@Override
	public void run() {
		addToMe = WindowManager.getImage(addToMeName);
		transformMe = WindowManager.getImage(transformMeName);

		// Build log
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Overlay Channels");

		addInputParameterLog(builder);
		log += builder.buildParameterList();

		logService.info(log);

		transformedImageMap = new ConcurrentHashMap<>();

		AffineTransform2D transform = new AffineTransform2D();
		transform.set(m00, m01, m02, m10, m11, m12);

		ImageStack oldStack = transformMe.getImageStack();

		double startTime = System.currentTimeMillis();
		logService.info("Transforming and Overlaying channels...");

		List<Runnable> tasks = new ArrayList<>();
		IntStream.rangeClosed(1, transformMe.getStackSize()).forEach(t -> tasks.add(
			() -> transformT(t, new ImagePlus("T " + t, oldStack.getProcessor(t)),
				transform)));

		MarsUtil.threadPoolBuilder(statusService, logService, () -> statusService
			.showStatus(transformedImageMap.size(), transformMe.getStackSize(),
				"Transforming " + transformMe.getTitle()), tasks, nThreads);

		// Now we have a map with all the transformed images. We just need to add
		// them to a new stack
		// and then merge with the untransformed image.
		ImageStack newStack = new ImageStack(transformMe.getWidth(), transformMe
			.getHeight());

		// I think this just works with references, so it should be very fast.
		// otherwise the stack could be made in the parallel stream but might need
		// to be placed in a synchronized block.
		for (int slice = 1; slice <= transformedImageMap.size(); slice++)
			newStack.addSlice(transformedImageMap.get(slice).getProcessor());

		ImagePlus[] images = new ImagePlus[2];
		images[0] = addToMe;
		images[1] = new ImagePlus("transformed", newStack);

		imgOut = ij.plugin.RGBStackMerge.mergeChannels(images, keep);

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			startTime) / 60000, 2) + " minutes.");

		logService.info(LogBuilder.endBlock(true));

		if (!keep) {
			transformMe.changes = false;
			transformMe.close();

			addToMe.changes = false;
			addToMe.close();
		}
	}

	private <T extends RealType<T> & NativeType<T>> void transformT(int t,
		ImagePlus tImage, AffineTransform2D transform)
	{
		Img<T> img = ImagePlusAdapter.wrap(tImage);

		@SuppressWarnings({"unchecked", "rawtypes"})
		RandomAccessibleInterval<T> ra = Views.interval(Views.raster(RealViews
			.affine(Views.interpolate(Views.extendZero(img),
				new NLinearInterpolatorFactory()), transform)), img);

		ImagePlus transImg = ImageJFunctions.wrap(ra, "transformed");

		transformedImageMap.put(t, transImg);
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Image 1", addToMe.getTitle());
		if (addToMe.getOriginalFileInfo() != null && addToMe
			.getOriginalFileInfo().directory != null)
		{
			builder.addParameter("Image 1 Directory", addToMe
				.getOriginalFileInfo().directory);
		}
		builder.addParameter("Image 2", transformMe.getTitle());
		if (transformMe.getOriginalFileInfo() != null && transformMe
			.getOriginalFileInfo().directory != null)
		{
			builder.addParameter("Image 2 Directory", transformMe
				.getOriginalFileInfo().directory);
		}
		// builder.addParameter("keep originals", String.valueOf(keep));
		builder.addParameter("Affine2D m00", String.valueOf(m00));
		builder.addParameter("Affine2D m01", String.valueOf(m01));
		builder.addParameter("Affine2D m02", String.valueOf(m02));
		builder.addParameter("Affine2D m10", String.valueOf(m10));
		builder.addParameter("Affine2D m11", String.valueOf(m11));
		builder.addParameter("Affine2D m12", String.valueOf(m12));
		builder.addParameter("Thread count", nThreads);
	}

	public void setAddToMe(ImagePlus addToMe) {
		this.addToMe = addToMe;
	}

	public ImagePlus getAddToMe() {
		return addToMe;
	}

	public void setTransformMe(ImagePlus transformMe) {
		this.transformMe = transformMe;
	}

	public ImagePlus getTransformMe() {
		return transformMe;
	}

	public void setKeepOriginals(boolean keep) {
		this.keep = keep;
	}

	public boolean getKeepOriginals() {
		return keep;
	}

	public void setM00(double m00) {
		this.m00 = m00;
	}

	public double getM00() {
		return m00;
	}

	public void setM01(double m01) {
		this.m01 = m01;
	}

	public double getM01() {
		return m01;
	}

	public void setM02(double m02) {
		this.m02 = m02;
	}

	public double getM02() {
		return m02;
	}

	public void setM10(double m10) {
		this.m10 = m10;
	}

	public double getM10() {
		return m10;
	}

	public void setM11(double m11) {
		this.m11 = m11;
	}

	public double getM11() {
		return m11;
	}

	public void setM12(double m12) {
		this.m12 = m12;
	}

	public double getM12() {
		return m12;
	}

	public void setThreads(int nThreads) {
		this.nThreads = nThreads;
	}

	public int getThreads() {
		return this.nThreads;
	}
}
