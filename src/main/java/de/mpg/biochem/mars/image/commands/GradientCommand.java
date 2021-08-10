/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2021 Karl Duderstadt
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

package de.mpg.biochem.mars.image.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.ChoiceWidget;

import de.mpg.biochem.mars.image.MarsImageUtils;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsUtil;

/**
 * This is a convenience command that calculates the gradient of an image.
 * Assumes Z = 0. Processes all TIME and CHANNEL positions.
 *
 * @author Karl Duderstadt
 */
@Plugin(type = Command.class, label = "Gradient Calculator", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 's'), @Menu(
			label = "Image", weight = 20, mnemonic = 'i'), @Menu(
				label = "Gradient Calculator", weight = 50, mnemonic = 'g') })
public class GradientCommand extends DynamicCommand implements Command {

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
	private OpService opService;

	@Parameter
	private DatasetService datasetService;

	/**
	 * IMAGE
	 */
	@Parameter(label = "Image")
	private Dataset dataset;

	@Parameter(label = "Gaussian smoothing sigma")
	private double gaussSigma = 2;

	@Parameter(label = "Direction:",
		style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = {
			"Top to bottom", "Left to right" })
	private String gradientDirection = "Top to bottom";
	
	@Parameter(label = "Threads", required = false, min = "1", max = "120")
	private int nThreads = Runtime.getRuntime().availableProcessors();

	@Parameter(label = "Gradient image", type = ItemIO.OUTPUT)
	private Dataset output;

	// For the progress thread
	private final AtomicInteger slicesDone = new AtomicInteger(0);

	@Override
	public void run() {
		// Build log
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Calculate Gradient");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		logService.info(log);

		Img<DoubleType> gradImage = opService.create().img(dataset,
			new DoubleType());
		final int[] derivatives = (gradientDirection.equals("Top to bottom"))
			? new int[] { 0, 1 } : new int[] { 1, 0 };
		final double[] sigma = { gaussSigma, gaussSigma };

		int cDim = dataset.getImgPlus().dimensionIndex(Axes.CHANNEL);
		long cSize = dataset.getImgPlus().dimension(cDim);

		int tDim = dataset.getImgPlus().dimensionIndex(Axes.TIME);
		long tSize = dataset.getImgPlus().dimension(tDim);

		final List<int[]> CTs = new ArrayList<>();
		for (int c = 0; c < cSize; c++)
			for (int t = 0; t < tSize; t++)
				CTs.add(new int[] { c, t });
		
		List<Runnable> tasks = new ArrayList<Runnable>();
		IntStream.range(0, CTs.size()).forEach(i -> {
			tasks.add(() -> {
				int[] ct = CTs.get(i);
				Img<DoubleType> in = getInput2DSlice(ct);
				RandomAccessibleInterval<DoubleType> out = getOutput2DSlice(
					gradImage, ct);
				opService.filter().derivativeGauss(out, in, derivatives, sigma);
				slicesDone.incrementAndGet();
			});
		});

		MarsUtil.threadPoolBuilder(statusService, logService, () -> statusService
			.showStatus(slicesDone.get(), CTs.size(), "Calculating gradient for " +
				dataset.getName()), tasks , nThreads);

		output = datasetService.create(gradImage);

		logService.info(LogBuilder.endBlock(true));
	}

	@SuppressWarnings("unchecked")
	private <T extends RealType<T> & NativeType<T>> Img<DoubleType>
		getInput2DSlice(int[] ct)
	{
		return opService.convert().float64(Views.iterable(
			(RandomAccessibleInterval<T>) MarsImageUtils.get2DHyperSlice(
				(ImgPlus<T>) dataset.getImgPlus(), 0, ct[0], ct[1])));
	}

	public static RandomAccessibleInterval<DoubleType> getOutput2DSlice(
		final Img<DoubleType> img, final int[] ct)
	{
		RandomAccessible<DoubleType> frameImg;
		frameImg = Views.hyperSlice(img, 3, ct[0]);
		frameImg = Views.hyperSlice(frameImg, 3, ct[1]);
		frameImg = Views.hyperSlice(frameImg, 2, 0);

		return Views.interval(frameImg, Intervals.createMinSize(0, 0, img.dimension(
			0), img.dimension(1)));
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Dataset", dataset.getName());
		builder.addParameter("Gaussian smoothing sigma", gaussSigma);
		builder.addParameter("Direction", gradientDirection);
		builder.addParameter("Thread count", nThreads);
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}

	public Dataset getDataset() {
		return dataset;
	}

	public void setGaussianSigma(double gaussSigma) {
		this.gaussSigma = gaussSigma;
	}

	public double getGaussianSigma() {
		return gaussSigma;
	}

	public void setDirection(String gradientDirection) {
		this.gradientDirection = gradientDirection;
	}

	public String getDirection() {
		return gradientDirection;
	}
	
	public void setThreads(int nThreads) {
		this.nThreads = nThreads;
	}
	
	public int getThreads() {
		return this.nThreads;
	}
}
