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

package de.mpg.biochem.mars.image.commands;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Window;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JRootPane;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.display.ImageDisplay;
import net.imagej.ops.Initializable;
import net.imagej.ops.OpService;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.RealMask;
import net.imglib2.roi.Regions;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BoolType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;

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
import org.scijava.object.ObjectService;
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

import de.mpg.biochem.mars.image.MarsImageUtils;
import de.mpg.biochem.mars.image.Peak;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import de.mpg.biochem.mars.util.MarsUtil;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;

@Plugin(type = Command.class, label = "Peak Finder", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
			label = "Image", weight = 20, mnemonic = 'i'), @Menu(
				label = "Peak Finder", weight = 1, mnemonic = 'p') })
public class PeakFinderCommand extends DynamicCommand implements Command,
	Initializable, Previewable
{

	/**
	 * SERVICES
	 */
	@Parameter
	private LogService logService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private MarsTableService resultsTableService;

	@Parameter
	private OpService opService;

	@Parameter
	private ConvertService convertService;

	@Parameter
	private DatasetService datasetService;
	
	@Parameter
	private ObjectService objectService;
	
	@Parameter
	private UIService uiService;
	
	@Parameter
	private PlatformService platformService;

	/**
	 * IMAGE
	 */
	@Parameter(label = "Image to search for Peaks")
	private ImageDisplay imageDisplay;
	
	/**
	 * ROI
	 */
	@Parameter(required = false)
	private RoiManager roiManager;
	
	/**
	 * INPUT SETTINGS
	 */
	
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String inputGroup = "Input";
	
	@Parameter(label = "Region",
		style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE + ", group:Input", choices = { "whole image",
			"ROI from image", "ROIs from manager" })
	private String region = "whole image";
	
	@Parameter(label = "Channel", choices = { "a", "b", "c" }, style = "group:Input", persist = false)
	private String channel = "0";
	
	/**
	 * FINDER SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String findGroup = "Find";
	
	@Parameter(label = "DoG filter", style = "group:Find")
	private boolean useDogFilter = true;

	@Parameter(label = "DoG radius", style = "group:Find")
	private double dogFilterRadius = 2;

	@Parameter(label = "Threshold", style = "group:Find")
	private double threshold = 50;

	@Parameter(label = "Peak separation", style = "group:Find")
	private int minimumDistance = 4;
	
	@Parameter(label = "Negative peaks", style = "group:Find")
	private boolean findNegativePeaks = false;

	/**
	 * FITTER SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String fitGroup = "Fit";
	
	@Parameter(label = "Fit", style = "group:Fit")
	private boolean fitPeaks = false;

	@Parameter(label = "Radius", style = "group:Fit")
	private int fitRadius = 4;

	@Parameter(label = "R-squared", style = NumberWidget.SLIDER_STYLE + ", group:Fit",
		min = "0.00", max = "1.00", stepSize = "0.01")
	private double RsquaredMin = 0;
	
	/**
	 * INTEGRATION SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String integrateGroup = "Integrate";
	
	@Parameter(label = "Integrate", style = "group:Integrate")
	private boolean integrate = false;

	@Parameter(label = "Inner radius", style = "group:Integrate")
	private int integrationInnerRadius = 2;

	@Parameter(label = "Outer radius", style = "group:Integrate")
	private int integrationOuterRadius = 4;
	
	/**
	 * OUTPUT SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String outputGroup = "Output";

	@Parameter(label = "Generate peak count table", style = "group:Output")
	private boolean generatePeakCountTable;

	@Parameter(label = "Generate peak table", style = "group:Output")
	private boolean generatePeakTable;

	@Parameter(label = "Add to RoiManager", style = "group:Output")
	private boolean addToRoiManager;

	@Parameter(label = "Process all frames", style = "group:Output")
	private boolean allFrames;

	@Parameter(label = "Verbose", style = "group:Output")
	private boolean verbose = false;
	
	@Parameter(label = "Threads", required = false, min = "1", max = "120", style = "group:Output")
	private int nThreads = Runtime.getRuntime().availableProcessors();
	
	/**
	 * PREVIEW SETTINGS
	 */
	
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String previewGroup = "Preview";
	
	@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false,
			callback = "previewChanged", style = "group:Preview")
	private boolean preview = false;

	@Parameter(label = "Roi",
		style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE + ", group:Preview", choices = { "circle",
			"point" })
	private String roiType;

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "group:Preview")
	private String tPeakCount = "count: 0";

	@Parameter(label = "T", min = "0", style = NumberWidget.SCROLL_BAR_STYLE + ", group:Preview",
		persist = false)
	private int theT;
	
	@Parameter(label = "Timeout (s)", style = "group:Preview")
	private int previewTimeout = 10;
	
	@Parameter(label = "Help",
			description="View a web page detailing Peak Finder options",
			callback="openWebPage", persist = false)
	private Button openWebPage;

	/**
	 * OUTPUTS
	 */
	@Parameter(label = "Peak Count", type = ItemIO.OUTPUT)
	private MarsTable peakCount;

	@Parameter(label = "Peaks", type = ItemIO.OUTPUT)
	private MarsTable peakTable;

	/**
	 * Map from T to label peak lists
	 */
	private List<ConcurrentMap<Integer, List<Peak>>> peakLabelsStack;
	
	private Roi[] rois;
	private Roi imageRoi;
	
	private Dataset dataset;
	private ImagePlus image;
	private boolean swapZandT = false;

	@Override
	public void initialize() {
		if (imageDisplay != null) {
			dataset = (Dataset) imageDisplay.getActiveView().getData();
			image = convertService.convert(imageDisplay, ImagePlus.class);
		}
		else if (dataset == null) return;

		if (image.getRoi() != null)
			imageRoi = image.getRoi();
		
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
		if (image.getNFrames() < 2) {
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
		if (image != null && imageRoi == null && image.getRoi() != null)
			imageRoi = image.getRoi();
		
		if (region.equals("ROI from image")) {
			rois = new Roi[1];
			rois[0] = imageRoi;
		} else if (region.equals("ROIs from manager")) {
			rois = roiManager.getRoisAsArray();
		} else {
			rois = new Roi[1];
			rois[0] = new Roi(new Rectangle(0, 0, (int)dataset.dimension(0), (int)dataset.dimension(1)));
		}
		
		if (image != null) {
			image.deleteRoi();
			image.setOverlay(null);
		}

		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Peak Finder");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		logService.info(log);

		peakLabelsStack = new ArrayList<>();
		for (int i = 0; i < rois.length; i++)
			peakLabelsStack.add(new ConcurrentHashMap<Integer, List<Peak>>());

		double starttime = System.currentTimeMillis();
		logService.info("Finding Peaks...");
		if (allFrames) {
			int zDim = dataset.getImgPlus().dimensionIndex(Axes.Z);
			int zSize = (int) dataset.getImgPlus().dimension(zDim);

			int tDim = dataset.getImgPlus().dimensionIndex(Axes.TIME);
			int tSize = (int) dataset.getImgPlus().dimension(tDim);

			final int frameCount = (swapZandT) ? zSize : tSize;
			
			List<Runnable> tasks = new ArrayList<Runnable>();
			for (int t=0; t<frameCount; t++) {
				final int theT = t;
				tasks.add(() -> {
					List<List<Peak>> labelPeaks = findPeaksInT(Integer.valueOf(channel), theT, useDogFilter, fitPeaks, integrate, rois);
					for (int i = 0; i < rois.length; i++)
						if (labelPeaks.get(i).size() > 0) peakLabelsStack.get(i).put(theT, labelPeaks.get(i));
				});
			}

			MarsUtil.threadPoolBuilder(statusService, logService, () -> statusService
					.showStatus(peakLabelsStack.get(0).size(), frameCount, "Finding Peaks for " + dataset
						.getName()), tasks, nThreads);
		}
		else peakLabelsStack.get(0).put(theT, findPeaksInT(Integer.valueOf(channel), theT,
			useDogFilter, fitPeaks, integrate, rois).get(0));

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			starttime) / 60000, 2) + " minutes.");

		if (generatePeakCountTable) generatePeakCountTable();

		if (generatePeakTable) generatePeakTable();

		if (addToRoiManager) addToRoiManager();

		if (image != null) image.setRoi(imageRoi);

		logService.info("Finished in " + DoubleRounder.round((System
			.currentTimeMillis() - starttime) / 60000, 2) + " minutes.");
		logService.info(LogBuilder.endBlock(true));
	}

	@SuppressWarnings("unchecked")
	private <T extends RealType<T> & NativeType<T>> List<List<Peak>> findPeaksInT(
		int channel, int t, boolean useDogFilter, boolean fitPeaks,
		boolean integrate, Roi[] processingRois)
	{
		RandomAccessibleInterval<T> img = (swapZandT) ? MarsImageUtils
			.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), t, -1, -1)
			: MarsImageUtils.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), 0,
				channel, t);
		
		RandomAccessibleInterval<FloatType> filteredImg = null; 
		if (useDogFilter)
			filteredImg = MarsImageUtils.dogFilter(img, dogFilterRadius, opService);

		List<List<Peak>> labelPeakLists = new ArrayList<List<Peak>>();
		for (int i = 0; i < processingRois.length; i++) {
			List<Peak> peaks = new ArrayList<Peak>();
			
			RealMask roiMask = convertService.convert( processingRois[i], RealMask.class );
			IterableRegion< BoolType > iterableROI = MarsImageUtils.toIterableRegion( roiMask, img );
	
			if (useDogFilter)
				 peaks = MarsImageUtils.findPeaks(filteredImg, Regions.sample( iterableROI, filteredImg ), t, threshold,
					minimumDistance, findNegativePeaks);
			else peaks = MarsImageUtils.findPeaks(img, Regions.sample( iterableROI, img ), t, threshold,
				minimumDistance, findNegativePeaks);
	
			if (fitPeaks) {
				peaks = MarsImageUtils.fitPeaks(img, img, peaks, fitRadius,
					dogFilterRadius, findNegativePeaks, RsquaredMin);
				peaks = MarsImageUtils.removeNearestNeighbors(peaks, minimumDistance);
			}
	
			if (integrate) MarsImageUtils.integratePeaks(img, img, peaks,
				integrationInnerRadius, integrationOuterRadius);
			
			labelPeakLists.add(peaks);
		}
		
		return labelPeakLists;
	}

	private void generatePeakCountTable() {
		logService.info("Generating peak count table..");
		peakCount = new MarsTable("Peak Count - " + dataset.getName());
		DoubleColumn frameColumn = new DoubleColumn(Peak.T);
		DoubleColumn countColumn = new DoubleColumn("Peaks");

		for (Map<Integer, List<Peak>> peakStack : peakLabelsStack)
			for (int t : peakStack.keySet()) {
				frameColumn.addValue(t);
				countColumn.addValue(peakStack.get(t).size());
			}
		peakCount.add(frameColumn);
		peakCount.add(countColumn);
		peakCount.sort("T");

		getInfo().getMutableOutput("peakCount", MarsTable.class).setLabel(peakCount
			.getName());
	}

	private void generatePeakTable() {
		logService.info("Generating peak table..");
		peakTable = new MarsTable("Peaks - " + dataset.getName());

		int row = 0;
		for (Map<Integer, List<Peak>> peakStack : peakLabelsStack)
			for (int t : peakStack.keySet()) {
				List<Peak> framePeaks = peakStack.get(t);
				for (int j = 0; j < framePeaks.size(); j++) {
					peakTable.appendRow();
					peakTable.setValue(Peak.T, row, (double)framePeaks.get(j).getT());
					peakTable.setValue(Peak.X, row, framePeaks.get(j).getX());
					peakTable.setValue(Peak.Y, row, framePeaks.get(j).getY());
					if (verbose) {
						for (String name : framePeaks.get(j).getProperties().keySet())
							peakTable.setValue(name, row, framePeaks.get(j).getProperties().get(name));
					} else if (framePeaks.get(j).getProperties().containsKey(Peak.INTENSITY))
						peakTable.setValue(Peak.INTENSITY, row, framePeaks.get(j).getProperties().get(Peak.INTENSITY));
					row++;
				}
			}

		getInfo().getMutableOutput("peakTable", MarsTable.class).setLabel(peakTable
			.getName());
	}

	private void addToRoiManager() {
		logService.info(
			"Adding Peaks to the RoiManager. This might take a while...");

		int peakNumber = 1;
		for (Map<Integer, List<Peak>> peakStack : peakLabelsStack)
			for (int t : peakStack.keySet())
				peakNumber = addToRoiManager(peakStack.get(t), Integer.valueOf(channel),
					t, peakNumber);

		statusService.showStatus("Done adding ROIs to Manager");
	}

	private int addToRoiManager(List<Peak> peaks, int channel, int t,
		int startingPeakNum)
	{
		if (roiManager == null) roiManager = new RoiManager();
		int pCount = startingPeakNum;
		for (Peak peak : peaks) {
			// The pixel origin for OvalRois is at the upper left corner !!!!
			// The pixel origin for PointRois is at the center !!!
			Roi peakRoi = (roiType.equals("point")) ? new PointRoi(peak
				.getDoublePosition(0), peak.getDoublePosition(1)) : new OvalRoi(peak
					.getDoublePosition(0) + 0.5 - fitRadius, peak.getDoublePosition(1) +
						0.5 - fitRadius, fitRadius * 2, fitRadius * 2);

			//if (moleculeNames) peakRoi.setName("Molecule" + pCount);
			//else 
			peakRoi.setName(MarsMath.getUUID58());

			if (swapZandT) peakRoi.setPosition(channel + 1, t + 1, 1);
			else peakRoi.setPosition(channel + 1, 1, t + 1);
			roiManager.addRoi(peakRoi);
			pCount++;
		}
		return pCount;
	}

	@Override
	public void preview() {
		if (preview) {
			ExecutorService es = Executors.newSingleThreadExecutor();
			try {
				es.submit(() -> {
					if (imageRoi == null && image.getRoi() != null)
						imageRoi = image.getRoi();
					
					if (region.equals("ROI from image")) {
						rois = new Roi[1];
						rois[0] = imageRoi;
					} else if (region.equals("ROIs from manager")) {
						rois = roiManager.getRoisAsArray();
					} else {
						rois = new Roi[1];
						rois[0] = new Roi(new Rectangle(0, 0, (int)dataset.dimension(0), (int)dataset.dimension(1)));
					}
					
					if (image != null) {
						image.deleteRoi();
						image.setOverlay(null);
					}
			
					if (swapZandT) image.setSlice(theT + 1);
					else image.setPosition(Integer.valueOf(channel) + 1, 1, theT + 1);
					
					List<List<Peak>> labelPeakLists = findPeaksInT(Integer.valueOf(channel), theT,
							useDogFilter, fitPeaks, false, rois);
		
					final MutableModuleItem<String> preFrameCount = getInfo().getMutableInput(
						"tPeakCount", String.class);
					
					int peakCount = 0;
					if (roiType.equals("point")) {
						Overlay overlay = new Overlay();
						FloatPolygon poly = new FloatPolygon();
						for (List<Peak> labelPeaks : labelPeakLists)
							for (Peak p : labelPeaks) {
								poly.addPoint(p.getDoublePosition(0), p.getDoublePosition(1));
								peakCount++;
								
								if (Thread.currentThread().isInterrupted())
									return;
							}
	
						PointRoi peakRoi = new PointRoi(poly);
	
						overlay.add(peakRoi);
						
						if (Thread.currentThread().isInterrupted())
							return;
						
						image.setOverlay(overlay);
					}
					else {
						Overlay overlay = new Overlay();
						if (Thread.currentThread().isInterrupted())
							return;
						for (List<Peak> labelPeaks : labelPeakLists)
							for (Peak p : labelPeaks) {
								// The pixel origin for OvalRois is at the upper left corner !!!!
								// The pixel origin for PointRois is at the center !!!
								final OvalRoi ovalRoi = new OvalRoi(p.getDoublePosition(0) + 0.5 -
										integrationInnerRadius, p.getDoublePosition(1) + 0.5 - integrationInnerRadius, integrationInnerRadius *
										2, integrationInnerRadius * 2);
								//ovalRoi.setStrokeColor(Color.CYAN.darker());
								overlay.add(ovalRoi);
								peakCount++;
								if (Thread.currentThread().isInterrupted())
									return;
							}
						if (Thread.currentThread().isInterrupted())
							return;
						
						image.setOverlay(overlay);
					}
	
					preFrameCount.setValue(this, "count: " + peakCount);
					
					for (Window window : Window.getWindows())
						if (window instanceof JDialog && ((JDialog) window).getTitle().equals(getInfo().getLabel()))
							MarsUtil.updateJLabelTextInContainer(((JDialog) window), "count: ", "count: " + peakCount);
						
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
					"https://duderstadt-lab.github.io/mars-docs/docs/image/PeakFinder/";
			URL url = new URL(urlString);
			platformService.open(url);
		} catch (Exception e) {
			// do nothing
		}
	}

	@Override
	public void cancel() {
		if (image != null) {
			image.setOverlay(null);
			image.setRoi(imageRoi);
		}
	}

	/** Called when the {@link #preview} parameter value changes. */
	protected void previewChanged() {
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
		if (region.equals("ROI from image") && imageRoi != null) builder.addParameter("ROI from image", imageRoi.toString());
		builder.addParameter("Use DoG filter", String.valueOf(useDogFilter));
		builder.addParameter("DoG filter radius", String.valueOf(dogFilterRadius));
		builder.addParameter("Threshold", String.valueOf(threshold));
		builder.addParameter("Minimum distance", String.valueOf(minimumDistance));
		builder.addParameter("Find negative peaks", String.valueOf(
			findNegativePeaks));
		builder.addParameter("Generate peak count table", String.valueOf(
			generatePeakCountTable));
		builder.addParameter("Generate peak table", String.valueOf(
			generatePeakTable));
		builder.addParameter("Add to ROIManager", String.valueOf(addToRoiManager));
		builder.addParameter("Roi Type", roiType);
		builder.addParameter("Process all time points", String.valueOf(allFrames));
		builder.addParameter("Fit peaks", String.valueOf(fitPeaks));
		builder.addParameter("Fit Radius", String.valueOf(fitRadius));
		builder.addParameter("Minimum R-squared", String.valueOf(RsquaredMin));
		builder.addParameter("Integrate", String.valueOf(integrate));
		builder.addParameter("Integration inner radius", String.valueOf(
			integrationInnerRadius));
		builder.addParameter("Integration outer radius", String.valueOf(
			integrationOuterRadius));
		builder.addParameter("Verbose", String.valueOf(verbose));
		builder.addParameter("Thread count", nThreads);
	}
	
	public void setRoiManager(RoiManager roiManager) {
		this.roiManager = roiManager;
	}
	
	public RoiManager getRoiManager() {
		return roiManager;
	}

	public MarsTable getPeakCountTable() {
		return peakCount;
	}

	public MarsTable getPeakTable() {
		return peakTable;
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
	
	public void setRois(Roi[] rois) {
		this.rois = rois;
		this.region = "ROIs from manager";
	}
	
	public Roi[] getROIs() {
		return this.rois;
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

	public void setFindNegativePeaks(boolean findNegativePeaks) {
		this.findNegativePeaks = findNegativePeaks;
	}

	public boolean getFindNegativePeaks() {
		return findNegativePeaks;
	}

	public void setGeneratePeakCountTable(boolean generatePeakCountTable) {
		this.generatePeakCountTable = generatePeakCountTable;
	}

	public boolean getGeneratePeakCountTable() {
		return generatePeakCountTable;
	}

	public void setGeneratePeakTable(boolean generatePeakTable) {
		this.generatePeakTable = generatePeakTable;
	}

	public boolean getGeneratePeakTable() {
		return generatePeakTable;
	}

	public void setMinimumRsquared(double RsquaredMin) {
		this.RsquaredMin = RsquaredMin;
	}

	public double getMinimumRsquared() {
		return RsquaredMin;
	}

	public void setAddToRoiManager(boolean addToRoiManager) {
		this.addToRoiManager = addToRoiManager;
	}

	public boolean getAddToRoiManager() {
		return addToRoiManager;
	}
	
	/**
	 * This method specifies the type of Roi to add to the
	 * RoiManager. The currently available types are "point" 
	 * or "circle" with non-available string defaulting to
	 * points.
	 * 
	 * @param roiType String specifying the roi type.
	 */
	public void setRoiType(String roiType) {
		this.roiType = roiType;
	}

	public String getRoiType() {
		return roiType;
	}

	public void setProcessAllFrames(boolean allFrames) {
		this.allFrames = allFrames;
	}

	public boolean getProcessAllFrames() {
		return allFrames;
	}

	public void setFitPeaks(boolean fitPeaks) {
		this.fitPeaks = fitPeaks;
	}

	public boolean getFitPeaks() {
		return fitPeaks;
	}

	public void setFitRadius(int fitRadius) {
		this.fitRadius = fitRadius;
	}

	public int getFitRadius() {
		return fitRadius;
	}

	public void setIntegrate(boolean integrate) {
		this.integrate = integrate;
	}

	public boolean getIntegrate() {
		return integrate;
	}

	public void setIntegrationInnerRadius(int integrationInnerRadius) {
		this.integrationInnerRadius = integrationInnerRadius;
	}

	public int getIntegrationInnerRadius() {
		return integrationInnerRadius;
	}

	public void setIntegrationOuterRadius(int integrationOuterRadius) {
		this.integrationOuterRadius = integrationOuterRadius;
	}

	public int getIntegrationOuterRadius() {
		return integrationOuterRadius;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public boolean getVerbose() {
		return verbose;
	}
	
	public void setThreads(int nThreads) {
		this.nThreads = nThreads;
	}
	
	public int getThreads() {
		return this.nThreads;
	}
}
