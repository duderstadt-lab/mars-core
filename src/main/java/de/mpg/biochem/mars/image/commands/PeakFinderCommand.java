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
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DoubleColumn;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.UIService;
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

	@Parameter(label = "Use ROI", persist = false)
	private boolean useROI = true;

	/**
	 * FINDER SETTINGS
	 */
	@Parameter(label = "Channel", choices = { "a", "b", "c" }, persist = false)
	private String channel = "0";

	@Parameter(label = "Use DoG filter")
	private boolean useDogFilter = true;

	@Parameter(label = "DoG filter radius")
	private double dogFilterRadius = 2;

	@Parameter(label = "Detection threshold")
	private double threshold = 50;

	@Parameter(label = "Minimum distance between peaks")
	private int minimumDistance = 4;
	
	@Parameter(label = "Preview timeout (s)")
	private int previewTimeout = 10;

	@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false,
		callback = "previewChanged")
	private boolean preview = false;

	@Parameter(label = "Preview:",
		style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "circle",
			"point" })
	private String roiType = "point";

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String tPeakCount = "count: 0";

	@Parameter(label = "T", min = "0", style = NumberWidget.SCROLL_BAR_STYLE,
		persist = false)
	private int theT;

	@Parameter(label = "Find negative peaks")
	private boolean findNegativePeaks = false;

	@Parameter(label = "Generate peak count table")
	private boolean generatePeakCountTable;

	@Parameter(label = "Generate peak table")
	private boolean generatePeakTable;

	@Parameter(label = "Add to ROIManager")
	private boolean addToRoiManager;

	//@Parameter(label = "Molecule names in RoiManager")
	//private boolean moleculeNames;

	@Parameter(label = "Process all frames")
	private boolean allFrames;

	/**
	 * FITTER SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String PeakFitterMessage = "Peak fitter settings:";

	@Parameter(label = "Fit peaks")
	private boolean fitPeaks = false;

	@Parameter(label = "Fit radius")
	private int fitRadius = 4;

	@Parameter(label = "Minimum R-squared", style = NumberWidget.SLIDER_STYLE,
		min = "0.00", max = "1.00", stepSize = "0.01")
	private double RsquaredMin = 0;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String integrationTitle = "Peak integration settings:";

	@Parameter(label = "Integrate")
	private boolean integrate = false;

	@Parameter(label = "Inner radius")
	private int integrationInnerRadius = 1;

	@Parameter(label = "Outer radius")
	private int integrationOuterRadius = 3;

	/**
	 * VERBOSE
	 */
	@Parameter(label = "Verbose output")
	private boolean verbose = true;
	
	@Parameter(label = "Thread count", required = false, min = "1", max = "120")
	private int nThreads = Runtime.getRuntime().availableProcessors();

	/**
	 * OUTPUTS
	 */
	@Parameter(label = "Peak Count", type = ItemIO.OUTPUT)
	private MarsTable peakCount;

	@Parameter(label = "Peaks", type = ItemIO.OUTPUT)
	private MarsTable peakTable;

	/**
	 * Map from T to peak list
	 */
	private ConcurrentMap<Integer, List<Peak>> peakStack;

	private boolean swapZandT = false;
	private Roi roi;

	//public static final String[] TABLE_HEADERS_VERBOSE = { "baseline", "height",
	//	"sigma", "R2" };
	private Dataset dataset;
	private ImagePlus image;

	@Override
	public void initialize() {
		if (imageDisplay != null) {
			dataset = (Dataset) imageDisplay.getActiveView().getData();
			image = convertService.convert(imageDisplay, ImagePlus.class);
		}
		else if (dataset == null) return;

		if (image.getRoi() == null) {
			final MutableModuleItem<Boolean> useRoifield = getInfo().getMutableInput(
				"useROI", Boolean.class);
			useRoifield.setValue(this, false);
		}
		else roi = image.getRoi();
		
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
		if (image != null) {
			image.deleteRoi();
			image.setOverlay(null);
		}

		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Peak Finder");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		logService.info(log);

		peakStack = new ConcurrentHashMap<>();

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
					List<Peak> tpeaks = findPeaksInT(Integer.valueOf(channel), theT, useDogFilter, fitPeaks, integrate);
					if (tpeaks.size() > 0) peakStack.put(theT, tpeaks);
				});
			}

			MarsUtil.threadPoolBuilder(statusService, logService,
				() -> statusService.showStatus(peakStack.size(), frameCount,
					"Finding Peaks for " + dataset.getName()), tasks, nThreads);
		}
		else peakStack.put(theT, findPeaksInT(Integer.valueOf(channel), theT,
			useDogFilter, fitPeaks, integrate));

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			starttime) / 60000, 2) + " minutes.");

		if (generatePeakCountTable) generatePeakCountTable();

		if (generatePeakTable) generatePeakTable();

		if (addToRoiManager) addToRoiManager();

		if (image != null) image.setRoi(roi);

		logService.info("Finished in " + DoubleRounder.round((System
			.currentTimeMillis() - starttime) / 60000, 2) + " minutes.");
		logService.info(LogBuilder.endBlock(true));
	}

	@SuppressWarnings("unchecked")
	private <T extends RealType<T> & NativeType<T>> List<Peak> findPeaksInT(
		int channel, int t, boolean useDogFilter, boolean fitPeaks,
		boolean integrate)
	{
		RandomAccessibleInterval<T> img = (swapZandT) ? MarsImageUtils
			.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), t, -1, -1)
			: MarsImageUtils.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), 0,
				channel, t);

		List<Peak> peaks = new ArrayList<Peak>();
		
		//Convert from Roi to IterableInterval
		Roi processingRoi = (useROI && roi != null) ? roi : new Roi(new Rectangle(0, 0, (int)dataset.dimension(0), (int)dataset.dimension(1)));
		
		RealMask roiMask = convertService.convert( processingRoi, RealMask.class );
		IterableRegion< BoolType > iterableROI = MarsImageUtils.toIterableRegion( roiMask, img );
		
		if (useDogFilter) {
			RandomAccessibleInterval<FloatType> filteredImg = MarsImageUtils
				.dogFilter(img, dogFilterRadius, opService);
			peaks = MarsImageUtils.findPeaks(filteredImg, Regions.sample( iterableROI, filteredImg ), t, threshold,
				minimumDistance, findNegativePeaks);
		}
		else peaks = MarsImageUtils.findPeaks(img, Regions.sample( iterableROI, img ), t, threshold,
			minimumDistance, findNegativePeaks);
		
		if (fitPeaks) {
			peaks = MarsImageUtils.fitPeaks(img, img, peaks, fitRadius,
				dogFilterRadius, findNegativePeaks, RsquaredMin);
			peaks = MarsImageUtils.removeNearestNeighbors(peaks, minimumDistance);
		}

		if (integrate) MarsImageUtils.integratePeaks(img, img, peaks,
			integrationInnerRadius, integrationOuterRadius);

		return peaks;
	}

	private void generatePeakCountTable() {
		logService.info("Generating peak count table..");
		peakCount = new MarsTable("Peak Count - " + dataset.getName());
		DoubleColumn frameColumn = new DoubleColumn("T");
		DoubleColumn countColumn = new DoubleColumn("peaks");

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
		for (int t : peakStack.keySet()) {
			List<Peak> framePeaks = peakStack.get(t);
			for (int j = 0; j < framePeaks.size(); j++) {
				peakTable.appendRow();
				peakTable.setValue("T", row, (double)framePeaks.get(j).getT());
				peakTable.setValue("x", row, framePeaks.get(j).getX());
				peakTable.setValue("y", row, framePeaks.get(j).getY());
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
			"Adding Peaks to the RoiManger. This might take a while...");

		int peakNumber = 1;
		for (int t : peakStack.keySet())
			peakNumber = addToRoiManager(peakStack.get(t), Integer.valueOf(channel),
				t, peakNumber);

		statusService.showStatus("Done adding ROIs to Manger");
	}

	private int addToRoiManager(List<Peak> peaks, int channel, int t,
		int startingPeakNum)
	{
		if (roiManager == null) roiManager = new RoiManager();
		int pCount = startingPeakNum;
		if (!peaks.isEmpty()) {
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
		}
		return pCount;
	}

	@Override
	public void preview() {
		if (preview) {
			ExecutorService es = Executors.newSingleThreadExecutor();
			try {
				es.submit(() -> {
						if (image != null) {
							image.deleteRoi();
							image.setOverlay(null);
						}
			
						if (swapZandT) image.setSlice(theT + 1);
						else image.setPosition(Integer.valueOf(channel) + 1, 1, theT + 1);
			
						List<Peak> peaks = findPeaksInT(Integer.valueOf(channel), theT,
							useDogFilter, fitPeaks, false);
			
						final MutableModuleItem<String> preFrameCount = getInfo().getMutableInput(
							"tPeakCount", String.class);
						
						if (!peaks.isEmpty()) {
			
							if (roiType.equals("point")) {
								Overlay overlay = new Overlay();
								FloatPolygon poly = new FloatPolygon();
								for (Peak p : peaks) {
									poly.addPoint(p.getDoublePosition(0), p.getDoublePosition(1));
								
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
								
								for (Peak p : peaks) {
									// The pixel origin for OvalRois is at the upper left corner !!!!
									// The pixel origin for PointRois is at the center !!!
									final OvalRoi roi = new OvalRoi(p.getDoublePosition(0) + 0.5 -
										fitRadius, p.getDoublePosition(1) + 0.5 - fitRadius, fitRadius *
											2, fitRadius * 2);
									roi.setStrokeColor(Color.CYAN.darker());
			
									overlay.add(roi);
									if (Thread.currentThread().isInterrupted())
										return;
								}
								if (Thread.currentThread().isInterrupted())
									return;
								
								image.setOverlay(overlay);
							}
			
							preFrameCount.setValue(this, "count: " + peaks.size());
							for (Window window : Window.getWindows())
								if (window instanceof JDialog && ((JDialog) window).getTitle().equals(getInfo().getLabel())) {
									MarsUtil.updateJLabelTextInContainer(((JDialog) window), "count: ", "count: " + peaks.size());
								}
						}
						else {
							preFrameCount.setValue(this, "count: 0");
						}
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

	@Override
	public void cancel() {
		if (image != null) {
			image.setOverlay(null);
			image.setRoi(roi);
		}
	}

	/** Called when the {@link #preview} parameter value changes. */
	protected void previewChanged() {
		if (!preview) cancel();
	}

	private void addInputParameterLog(LogBuilder builder) {
		if (image != null) {
			builder.addParameter("Image Title", image.getTitle());
			if (image.getOriginalFileInfo() != null && image
				.getOriginalFileInfo().directory != null)
			{
				builder.addParameter("Image Directory", image
					.getOriginalFileInfo().directory);
			}
		}
		else {
			builder.addParameter("Dataset Name", dataset.getName());
		}
		builder.addParameter("Use ROI", String.valueOf(useROI));
		if (useROI && roi != null) builder.addParameter("ROI", roi.toString());
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
		builder.addParameter("Verbose output", String.valueOf(verbose));
		builder.addParameter("Thread count", nThreads);
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

	public void setUseRoi(boolean useROI) {
		this.useROI = useROI;
	}

	public boolean getUseRoi() {
		return useROI;
	}
	
	public void setRoi(Roi roi) {
		this.roi = roi;
	}
	
	public Roi getROI() {
		return this.roi;
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

	public void setVerboseOutput(boolean verbose) {
		this.verbose = verbose;
	}

	public boolean getVerboseOutput() {
		return verbose;
	}
	
	public void setThreads(int nThreads) {
		this.nThreads = nThreads;
	}
	
	public int getThreads() {
		return this.nThreads;
	}
}
