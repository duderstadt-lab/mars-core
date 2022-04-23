/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2022 Karl Duderstadt
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

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Window;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.JDialog;
import javax.swing.SwingUtilities;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Previewable;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.platform.PlatformService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DoubleColumn;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.UIService;
import org.scijava.widget.Button;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import de.mpg.biochem.mars.image.DNAFinder;
import de.mpg.biochem.mars.image.DNASegment;
import de.mpg.biochem.mars.image.MarsImageUtils;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import de.mpg.biochem.mars.util.MarsUtil;
import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.display.ImageDisplay;
import net.imagej.ops.Initializable;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.RealMask;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;

/**
 * Finds the location of vertically aligned DNA molecules within the specified
 * length range. Calculates the vertical gradient of the image and applies a DoG
 * filter. Then a search for pairs of positive and negative peaks is conducted.
 * Vertically aligned pairs with the range provided considered DNA molecules.
 * The ends can be fit with subpixel accuracy. Output can be the number of
 * molecules or a list of positions provided as a table. Alternatively, line
 * Rois can be added to the RoiManager, which can be used to create
 * DnaMoleculeArchives. Thresholds for the intensity and variance in intensity
 * can be applied to further filter the DNA molecule located.
 *
 * @author Karl Duderstadt
 */
@Plugin(type = Command.class, label = "DNA Finder", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
			label = "Image", weight = 1, mnemonic = 'i'), @Menu(label = "DNA Finder",
				weight = 2, mnemonic = 'd') })
public class DNAFinderCommand extends DynamicCommand implements Command,
	Initializable, Previewable
{

	/**
	 * SERVICES
	 */
	@Parameter
	private LogService logService;

	@Parameter
	private OpService opService;

	@Parameter
	private UIService uiService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private MarsTableService marsTableService;

	@Parameter
	private ConvertService convertService;

	@Parameter
	private PlatformService platformService;

	/**
	 * IMAGE
	 */
	@Parameter(label = "Image to search for DNAs")
	private ImageDisplay imageDisplay;

	/**
	 * ROI
	 */
	@Parameter(required = false)
	private RoiManager roiManager;

	/**
	 * INPUT SETTINGS
	 */

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel",
		persist = false)
	private String inputGroup = "Input";

	@Parameter(visibility = ItemVisibility.MESSAGE,
		style = "group:Input, align:center", persist = false)
	private String inputDetails = "Images with Y-axis aligned DNA molecules";

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "image, group:Input",
		persist = false)
	private String inputFigure = "DNAImageInput.png";

	@Parameter(visibility = ItemVisibility.MESSAGE,
		style = "group:Input, align:center", persist = false)
	private String imageName = "name";

	@Parameter(label = "Region",
		style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE + ", group:Input",
		choices = { "whole image", "ROI from image", "ROIs from manager" })
	private String region = "whole image";

	@Parameter(label = "Channel", choices = { "a", "b", "c" },
		style = "group:Input", persist = false)
	private String channel = "0";

	/**
	 * FINDER SETTINGS
	 */

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String findGroup = "Find";

	@Parameter(label = "Gaussian Smoothing Sigma", style = "group:Find")
	private double gaussSigma = 2;

	@Parameter(label = "DoG filter", style = "group:Find")
	private boolean useDogFilter = true;

	@Parameter(label = "DoG radius", style = "group:Find")
	private double dogFilterRadius = 2;

	@Parameter(label = "Threshold", style = "group:Find")
	private double threshold = 50;

	@Parameter(label = "DNA separation (pixels)", style = "group:Find")
	private int minimumDistance = 6;

	@Parameter(label = "DNA length (pixels)", style = "group:Find")
	private int optimalDNALength = 38;

	@Parameter(label = "DNA end search Y (pixels)", style = "group:Find")
	private int yDNAEndSearchRadius = 6;

	@Parameter(label = "DNA end search X (pixels)", style = "group:Find")
	private int xDNAEndSearchRadius = 5;

	@Parameter(label = "Filter by median intensity", style = "group:Find")
	private boolean medianIntensityFilter = false;

	@Parameter(label = "Median DNA intensity lower bound", style = "group:Find")
	private int medianIntensityLowerBound = 0;

	@Parameter(label = "Filter by intensity variance", style = "group:Find")
	private boolean varianceFilter = false;

	@Parameter(label = "DNA intensity variance upper bound", style = "group:Find")
	private int varianceUpperBound = 1_000_000;

	/**
	 * FITTER SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String fitGroup = "Fit";

	@Parameter(label = "Fit ends", style = "group:Fit")
	private boolean fit = false;

	@Parameter(label = "2nd order", style = "group:Fit")
	private boolean fitSecondOrder = false;

	@Parameter(label = "Radius", style = "group:Fit")
	private int fitRadius = 4;

	/**
	 * OUTPUT SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String outputGroup = "Output";

	@Parameter(label = "Generate DNA count table", style = "group:Output")
	private boolean generateDNACountTable;

	@Parameter(label = "Generate DNA table", style = "group:Output")
	private boolean generateDNATable;

	@Parameter(label = "Add to ROI Manager", style = "group:Output")
	private boolean addToRoiManger;

	@Parameter(label = "Process all frames", style = "group:Output")
	private boolean allFrames;

	@Parameter(label = "Threads", required = false, min = "1", max = "120",
		style = "group:Output")
	private int nThreads = Runtime.getRuntime().availableProcessors();

	/**
	 * PREVIEW SETTINGS
	 */

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String previewGroup = "Preview";

	@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false,
		callback = "previewChanged", style = "group:Preview")
	private boolean preview = false;

	@Parameter(label = "Label", style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE +
		", group:Preview", choices = { "Median intensity", "Variance intensity" })
	private String previewLabelType;

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "group:Preview")
	private String tDNACount = "count: 0";

	@Parameter(label = "T", min = "0", style = NumberWidget.SCROLL_BAR_STYLE +
		", group:Preview", persist = false)
	private int theT;

	@Parameter(label = "Timeout (s)", style = "group:Preview")
	private int previewTimeout = 10;

	@Parameter(label = "Help",
		description = "View a web page detailing DNA Finder options",
		callback = "openWebPage", persist = false)
	private Button openWebPage;

	/**
	 * OUTPUTS
	 */
	@Parameter(label = "DNA Count", type = ItemIO.OUTPUT)
	private MarsTable dnaCount;

	@Parameter(label = "DNA Table", type = ItemIO.OUTPUT)
	private MarsTable dnaTable;

	// A map with peak lists for each slice for an image stack
	private ConcurrentMap<Integer, List<DNASegment>> dnaStack;

	private boolean swapZandT = false;

	private Roi[] rois;
	private Roi imageRoi;

	private Dataset dataset;
	private ImagePlus image;

	@Override
	public void initialize() {
		if (imageDisplay != null) {
			dataset = (Dataset) imageDisplay.getActiveView().getData();
			image = convertService.convert(imageDisplay, ImagePlus.class);
		}
		else if (dataset == null) return;

		if (image.getRoi() != null) imageRoi = image.getRoi();

		if (dataset != null) {
			final MutableModuleItem<String> imageNameItem = getInfo().getMutableInput(
				"imageName", String.class);
			imageNameItem.setValue(this, dataset.getName());
		}

		final MutableModuleItem<String> channelItems = getInfo().getMutableInput(
			"channel", String.class);
		long channelCount = dataset.getChannels();
		ArrayList<String> channels = new ArrayList<String>();
		for (int ch = 1; ch <= channelCount; ch++)
			channels.add(String.valueOf(ch - 1));
		channelItems.setChoices(channels);
		channelItems.setValue(this, String.valueOf(image.getChannel() - 1));

		final MutableModuleItem<Integer> preFrame = getInfo().getMutableInput(
			"theT", Integer.class);
		if (image.getNFrames() < 2 && image.getZ() > 1) {
			preFrame.setValue(this, image.getSlice() - 1);
			preFrame.setMaximumValue(image.getStackSize() - 1);
			swapZandT = true;
		}
		else {
			preFrame.setValue(this, image.getFrame() - 1);
			preFrame.setMaximumValue(image.getNFrames() - 1);
		}
	}

	@Override
	public void run() {
		if (dataset == null && image != null) dataset = convertService.convert(
			image, Dataset.class);

		if (dataset.dimension(dataset.dimensionIndex(Axes.TIME)) < 2) swapZandT =
			true;

		if (image != null && imageRoi == null && image.getRoi() != null) imageRoi =
			image.getRoi();

		if (region.equals("ROI from image")) {
			rois = new Roi[1];
			rois[0] = imageRoi;
		}
		else if (region.equals("ROIs from manager")) {
			rois = roiManager.getRoisAsArray();
		}
		else {
			rois = new Roi[1];
			rois[0] = new Roi(new Rectangle(0, 0, (int) dataset.dimension(0),
				(int) dataset.dimension(1)));
		}

		if (image != null) {
			image.deleteRoi();
			image.setOverlay(null);
		}

		// Build log
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("DNA Finder");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		logService.info(log);

		// Used to store dna list for multiframe search
		dnaStack = new ConcurrentHashMap<>();

		double starttime = System.currentTimeMillis();
		logService.info("Finding DNAs...");
		if (allFrames) {

			int zDim = dataset.getImgPlus().dimensionIndex(Axes.Z);
			int zSize = (int) dataset.getImgPlus().dimension(zDim);

			int tDim = dataset.getImgPlus().dimensionIndex(Axes.TIME);
			int tSize = (int) dataset.getImgPlus().dimension(tDim);

			final int frameCount = (swapZandT) ? zSize : tSize;

			List<Runnable> tasks = new ArrayList<Runnable>();
			for (int t = 0; t < frameCount; t++) {
				final int theT = t;
				tasks.add(() -> dnaStack.put(theT, findDNAsInT(Integer.valueOf(channel),
					theT, rois)));
			}

			MarsUtil.threadPoolBuilder(statusService, logService, () -> statusService
				.showStatus(dnaStack.size(), frameCount, "Finding DNAs for " + dataset
					.getName()), tasks, nThreads);

		}
		else dnaStack.put(theT, findDNAsInT(Integer.valueOf(channel), theT, rois));

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			starttime) / 60000, 2) + " minutes.");

		if (generateDNACountTable) generateDNACountTable();

		if (generateDNATable) generateDNATable();

		if (addToRoiManger) addToRoiManager();

		if (image != null && imageRoi != null) image.setRoi(imageRoi);

		logService.info("Finished in " + DoubleRounder.round((System
			.currentTimeMillis() - starttime) / 60000, 2) + " minutes.");
		logService.info(LogBuilder.endBlock(true));
	}

	@SuppressWarnings("unchecked")
	private <T extends RealType<T> & NativeType<T>> List<DNASegment> findDNAsInT(
		int channel, int t, Roi[] processingRois)
	{

		RandomAccessibleInterval<T> img = (swapZandT) ? MarsImageUtils
			.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), t, -1, -1)
			: MarsImageUtils.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), 0,
				channel, t);

		DNAFinder<T> dnaFinder = new DNAFinder<>(opService);
		dnaFinder.setGaussianSigma(gaussSigma);
		dnaFinder.setOptimalDNALength(optimalDNALength);
		dnaFinder.setMinimumDistance(minimumDistance);
		dnaFinder.setXDNAEndSearchRadius(xDNAEndSearchRadius);
		dnaFinder.setYDNAEndSearchRadius(yDNAEndSearchRadius);
		dnaFinder.setUseDogFiler(useDogFilter);
		dnaFinder.setDogFilterRadius(dogFilterRadius);
		dnaFinder.setThreshold(threshold);
		dnaFinder.setFilterByMedianIntensity(medianIntensityFilter);
		dnaFinder.setMedianIntensityLowerBound(medianIntensityLowerBound);
		dnaFinder.setFilterByVariance(varianceFilter);
		dnaFinder.setVarianceUpperBound(varianceUpperBound);
		dnaFinder.setFit(fit);
		dnaFinder.setFitSecondOrder(fitSecondOrder);
		dnaFinder.setFitRadius(fitRadius);

		List<IterableRegion<BoolType>> regionList =
			new ArrayList<IterableRegion<BoolType>>();
		for (int i = 0; i < processingRois.length; i++) {
			// Convert from Roi to IterableInterval
			RealMask roiMask = convertService.convert(processingRois[i],
				RealMask.class);
			IterableRegion<BoolType> iterableROI = MarsImageUtils.toIterableRegion(
				roiMask, img);
			regionList.add(iterableROI);
		}

		return dnaFinder.findDNAs(img, regionList, t);
	}

	private void generateDNACountTable() {
		logService.info("Generating DNA count table..");
		dnaCount = new MarsTable("DNA_Count_" + image.getTitle());
		DoubleColumn frameColumn = new DoubleColumn("T");
		DoubleColumn countColumn = new DoubleColumn("DNAs");

		for (int t : dnaStack.keySet()) {
			frameColumn.addValue(t);
			countColumn.addValue(dnaStack.get(t).size());
		}
		dnaCount.add(frameColumn);
		dnaCount.add(countColumn);
		dnaCount.sort("T");

		// Make sure the output table has the correct name
		getInfo().getMutableOutput("dnaCount", MarsTable.class).setLabel(dnaCount
			.getName());
	}

	private void generateDNATable() {
		logService.info("Generating peak table..");
		// build a table with all peaks
		String title = "DNAs Table - " + dataset.getName();
		dnaTable = new MarsTable(title, "T", "X1", "Y1", "X2", "Y2", "Length",
			"Median_intensity", "Intensity_variance");

		int row = 0;
		for (int t : dnaStack.keySet()) {
			List<DNASegment> tDNAs = dnaStack.get(t);
			for (int j = 0; j < tDNAs.size(); j++) {
				dnaTable.appendRow();
				dnaTable.setValue("T", row, t);
				dnaTable.setValue("X1", row, tDNAs.get(j).getX1());
				dnaTable.setValue("Y1", row, tDNAs.get(j).getY1());
				dnaTable.setValue("X2", row, tDNAs.get(j).getX2());
				dnaTable.setValue("Y2", row, tDNAs.get(j).getY2());
				dnaTable.setValue("Length", row, tDNAs.get(j).getLength());
				dnaTable.setValue("Median_intensity", row, tDNAs.get(j)
					.getMedianIntensity());
				dnaTable.setValue("Intensity_variance", row, tDNAs.get(j)
					.getVariance());
				row++;
			}
		}

		dnaTable.sort("T");

		// Make sure the output table has the correct name
		getInfo().getMutableOutput("dnaTable", MarsTable.class).setLabel(dnaTable
			.getName());
	}

	private void addToRoiManager() {
		logService.info(
			"Adding Peaks to the RoiManager. This might take a while...");
		int dnaNumber = 1;
		for (int t : dnaStack.keySet()) {
			dnaNumber = AddToManager(dnaStack.get(t), Integer.valueOf(channel), t,
				dnaNumber);
		}
		statusService.showStatus("Done adding ROIs to Manager");
	}

	private int AddToManager(List<DNASegment> segments, int channel, int t,
		int startingPeakNum)
	{
		if (roiManager == null) roiManager = new RoiManager();
		int dnaCount = startingPeakNum;
		if (!segments.isEmpty()) {
			for (DNASegment segment : segments) {
				Line line = new Line(segment.getX1(), segment.getY1(), segment.getX2(),
					segment.getY2());
				// if (moleculeNames) line.setName("Molecule" + dnaCount);
				// else

				line.setName(MarsMath.getUUID58());

				if (swapZandT) line.setPosition(channel, t + 1, 1);
				else line.setPosition(channel, 1, t + 1);

				roiManager.addRoi(line);
				dnaCount++;
			}
		}
		return dnaCount;
	}

	@Override
	public void preview() {
		if (preview) {
			if (swapZandT) image.setSlice(theT + 1);
			else image.setPosition(Integer.valueOf(channel) + 1, 1, theT + 1);

			ExecutorService es = Executors.newSingleThreadExecutor();
			try {
				es.submit(() -> {
					if (imageRoi == null && image.getRoi() != null) imageRoi = image
						.getRoi();

					if (region.equals("ROI from image")) {
						rois = new Roi[1];
						rois[0] = imageRoi;
					}
					else if (region.equals("ROIs from manager")) {
						rois = roiManager.getRoisAsArray();
					}
					else {
						rois = new Roi[1];
						rois[0] = new Roi(new Rectangle(0, 0, (int) dataset.dimension(0),
							(int) dataset.dimension(1)));
					}

					List<DNASegment> segments = findDNAsInT(Integer.valueOf(channel),
						theT, rois);

					if (Thread.currentThread().isInterrupted()) return;

					Overlay overlay = new Overlay();
					if (segments.size() > 0) {
						for (DNASegment segment : segments) {
							Line line = new Line(segment.getX1(), segment.getY1(), segment
								.getX2(), segment.getY2());

							double value = Double.NaN;
							if (previewLabelType.equals("Variance intensity")) value = segment
								.getVariance();
							else if (previewLabelType.equals("Median intensity")) value =
								segment.getMedianIntensity();

							if (Double.isNaN(value)) line.setName("");
							if (value > 1_000_000) line.setName(DoubleRounder.round(value /
								1_000_000, 2) + " m");
							else if (value > 1000) line.setName(DoubleRounder.round(value /
								1000, 2) + " k");
							else line.setName((int) value + "");

							overlay.add(line);
							if (Thread.currentThread().isInterrupted()) return;
						}
						overlay.drawLabels(true);
						overlay.drawNames(true);
						overlay.setLabelColor(new Color(255, 255, 255));
					}

					final String countString = "count: " + segments.size();
					final MutableModuleItem<String> preFrameCount = getInfo()
						.getMutableInput("tDNACount", String.class);
					preFrameCount.setValue(this, countString);

					SwingUtilities.invokeLater(() -> {
						if (image != null) {
							image.deleteRoi();
							image.setOverlay(overlay);

							for (Window window : Window.getWindows())
								if (window instanceof JDialog && ((JDialog) window).getTitle()
									.equals(getInfo().getLabel())) MarsUtil
										.updateJLabelTextInContainer(((JDialog) window), "count: ",
											countString);
						}
					});
				}).get(previewTimeout, TimeUnit.SECONDS);
			}
			catch (TimeoutException e1) {
				es.shutdownNow();
				uiService.showDialog(
					"Preview took too long. Try a smaller region, a higher threshold, or try again with a longer delay before preview timeout.",
					MessageType.ERROR_MESSAGE, OptionType.DEFAULT_OPTION);
				cancel();
			}
			catch (InterruptedException | ExecutionException e2) {
				es.shutdownNow();
				cancel();
			}
			es.shutdownNow();
		}
	}

	protected void openWebPage() {
		try {
			String urlString =
				"https://duderstadt-lab.github.io/mars-docs/docs/image/DNA_finder/";
			URL url = new URL(urlString);
			platformService.open(url);
		}
		catch (Exception e) {
			// do nothing
		}
	}

	@Override
	public void cancel() {
		if (image != null) {
			image.setOverlay(null);
			if (imageRoi != null) image.setRoi(imageRoi);
		}
	}

	/** Called when the {@link #preview} parameter value changes. */
	protected void previewChanged() {
		// When preview box is unchecked, reset the Roi back to how it was before...
		if (!preview) cancel();
	}

	private void addInputParameterLog(LogBuilder builder) {
		if (image != null) {
			builder.addParameter("Image title", image.getTitle());
			if (image.getOriginalFileInfo() != null && image
				.getOriginalFileInfo().directory != null)
			{
				builder.addParameter("Image directory", image
					.getOriginalFileInfo().directory);
			}
		}
		else {
			builder.addParameter("Dataset name", dataset.getName());
		}
		builder.addParameter("Region", region);
		if (region.equals("ROI from image") && imageRoi != null) builder
			.addParameter("ROI from image", imageRoi.toString());
		builder.addParameter("Channel", channel);
		builder.addParameter("Gaussian smoothing sigma", String.valueOf(
			this.gaussSigma));
		builder.addParameter("DoG filter", String.valueOf(useDogFilter));
		builder.addParameter("DoG radius", String.valueOf(dogFilterRadius));
		builder.addParameter("Threshold", String.valueOf(threshold));
		builder.addParameter("Minimum distance", String.valueOf(minimumDistance));
		builder.addParameter("Optimal DNA length", String.valueOf(
			optimalDNALength));
		builder.addParameter("DNA end search radius Y", String.valueOf(
			yDNAEndSearchRadius));
		builder.addParameter("DNA end search radius X", String.valueOf(
			xDNAEndSearchRadius));
		builder.addParameter("Filter by median intensity", String.valueOf(
			medianIntensityFilter));
		builder.addParameter("Median intensity lower bound", String.valueOf(
			medianIntensityLowerBound));
		builder.addParameter("Filter by variance", String.valueOf(varianceFilter));
		builder.addParameter("Intensity variance upper bound", String.valueOf(
			varianceUpperBound));
		builder.addParameter("Generate peak table", String.valueOf(
			generateDNATable));
		builder.addParameter("Add to ROI Manager", String.valueOf(addToRoiManger));
		builder.addParameter("Process all frames", String.valueOf(allFrames));
		builder.addParameter("Fit peaks", String.valueOf(fit));
		builder.addParameter("Fit radius", String.valueOf(fitRadius));
		builder.addParameter("Fit 2nd order", String.valueOf(fitSecondOrder));
		builder.addParameter("Thread count", nThreads);
	}

	public MarsTable getDNACountTable() {
		return dnaCount;
	}

	public MarsTable getDNATable() {
		return dnaTable;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}

	public Dataset getDataset() {
		return dataset;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getRegion() {
		return region;
	}

	public void setROIs(Roi[] rois) {
		this.rois = rois;
		this.region = "ROIs from manager";
	}

	public Roi[] getROIs() {
		return rois;
	}

	public void setChannel(int channel) {
		this.channel = String.valueOf(channel);
	}

	public int getChannel() {
		return Integer.valueOf(channel);
	}

	public void setT(int theT) {
		this.theT = theT;
	}

	public int getT() {
		return theT;
	}

	public void setGaussianSigma(double gaussSigma) {
		this.gaussSigma = gaussSigma;
	}

	public double getGaussianSigma() {
		return gaussSigma;
	}

	public void setUseDogFiler(boolean useDogFilter) {
		this.useDogFilter = useDogFilter;
	}

	public void setDogFilterRadius(double dogFilterRadius) {
		this.dogFilterRadius = dogFilterRadius;
	}

	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}

	public double getThreshold() {
		return threshold;
	}

	public void setMinimumDistance(int minimumDistance) {
		this.minimumDistance = minimumDistance;
	}

	public int getMinimumDistance() {
		return minimumDistance;
	}

	public void setOptimalDNALength(int optimalDNALength) {
		this.optimalDNALength = optimalDNALength;
	}

	public int getOptimalDNALength() {
		return optimalDNALength;
	}

	public void setYDNAEndSearchRadius(int yDNAEndSearchRadius) {
		this.yDNAEndSearchRadius = yDNAEndSearchRadius;
	}

	public int getYDNAEndSearchRadius() {
		return yDNAEndSearchRadius;
	}

	public void setXDNAEndSearchRadius(int xDNAEndSearchRadius) {
		this.xDNAEndSearchRadius = xDNAEndSearchRadius;
	}

	public int getXDNAEndSearchRadius() {
		return xDNAEndSearchRadius;
	}

	public void setFilterByVariance(boolean varianceFilter) {
		this.varianceFilter = varianceFilter;
	}

	public boolean getFilterByVariance() {
		return this.varianceFilter;
	}

	public double getVarianceUpperBound() {
		return varianceUpperBound;
	}

	public void setVarianceUpperBound(int varianceUpperBound) {
		this.varianceUpperBound = varianceUpperBound;
	}

	public void setFilterByMedianIntensity(boolean medianIntensityFilter) {
		this.medianIntensityFilter = medianIntensityFilter;
	}

	public boolean getFilterByMedianIntensity() {
		return medianIntensityFilter;
	}

	public void setMedianIntensityLowerBound(int medianIntensityLowerBound) {
		this.medianIntensityLowerBound = medianIntensityLowerBound;
	}

	public int getMedianIntensityLowerBound() {
		return medianIntensityLowerBound;
	}

	public void setGenerateDNACountTable(boolean generateDNACountTable) {
		this.generateDNACountTable = generateDNACountTable;
	}

	public boolean getGenerateDNACountTable() {
		return generateDNACountTable;
	}

	public void setGenerateDNATable(boolean generatePeakTable) {
		this.generateDNATable = generatePeakTable;
	}

	public boolean getGenerateDNATable() {
		return generateDNATable;
	}

	public void setAddToRoiManager(boolean addToRoiManger) {
		this.addToRoiManger = addToRoiManger;
	}

	public boolean getAddToRoiManager() {
		return addToRoiManger;
	}

	public void setProcessAllFrames(boolean allFrames) {
		this.allFrames = allFrames;
	}

	public boolean getProcessAllFrames() {
		return allFrames;
	}

	public void setFit(boolean fit) {
		this.fit = fit;
	}

	public boolean getFit() {
		return fit;
	}

	public void setFitSecondOrder(boolean fitSecondOrder) {
		this.fitSecondOrder = fitSecondOrder;
	}

	public boolean getFitSecondOrder() {
		return fitSecondOrder;
	}

	public void setFitRadius(int fitRadius) {
		this.fitRadius = fitRadius;
	}

	public int getFitRadius() {
		return fitRadius;
	}

	public void setThreads(int nThreads) {
		this.nThreads = nThreads;
	}

	public int getThreads() {
		return this.nThreads;
	}
}
