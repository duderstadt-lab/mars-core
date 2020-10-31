/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2020 Karl Duderstadt
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

import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.gui.PointRoi;

import org.scijava.module.MutableModuleItem;
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
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.mpg.biochem.mars.image.MarsImageUtils;
import de.mpg.biochem.mars.image.Peak;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import de.mpg.biochem.mars.util.MarsUtil;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.display.ImageDisplay;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;

import net.imagej.ImgPlus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.IntStream;

import net.imagej.ops.Initializable;
import org.scijava.table.DoubleColumn;

import org.scijava.widget.NumberWidget;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;

import net.imagej.ops.OpService;

@Plugin(type = Command.class, label = "Peak Finder", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Mars", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 'm'),
		@Menu(label = "Image", weight = 20,
			mnemonic = 'i'),
		@Menu(label = "Peak Finder", weight = 1, mnemonic = 'p')})
public class PeakFinderCommand< T extends RealType< T > & NativeType< T >> extends DynamicCommand implements Command, Initializable, Previewable {
	
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
	
	/**
	 * IMAGE
	 */
    @Parameter(label = "Image to search for Peaks")
	private ImageDisplay imageDisplay;
	
	/**
	 * ROI
	 */
	@Parameter(required=false)
	private RoiManager roiManager;
	
	@Parameter(label="Use ROI", persist=false)
	private boolean useROI = true;
	
	@Parameter(label="ROI x0", persist=false)
	private int x0;
	
	@Parameter(label="ROI y0", persist=false)
	private int y0;
	
	@Parameter(label="ROI width", persist=false)
	private int width;
	
	@Parameter(label="ROI height", persist=false)
	private int height;
	
	/**
	 * FINDER SETTINGS
	 */
    @Parameter(label="Channel", choices = {"a", "b", "c"})
	private String channel = "0";
    
	@Parameter(label="Use DoG filter")
	private boolean useDogFilter = true;
	
	@Parameter(label="DoG filter radius")
	private double dogFilterRadius = 2;
	
	@Parameter(label="Detection threshold")
	private double threshold = 50;
	
	@Parameter(label="Minimum distance between peaks")
	private int minimumDistance = 4;
	
	@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false, callback = "previewChanged")
	private boolean preview = false;
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String tPeakCount = "count: 0";
	
	@Parameter(label = "T", min = "0", style = NumberWidget.SCROLL_BAR_STYLE)
	private int previewT;
	
	@Parameter(label="Find negative peaks")
	private boolean findNegativePeaks = false;
	
	@Parameter(label="Generate peak count table")
	private boolean generatePeakCountTable;
	
	@Parameter(label="Generate peak table")
	private boolean generatePeakTable;
	
	@Parameter(label="Add to ROIManager")
	private boolean addToRoiManager;
	
	@Parameter(label="Molecule names in ROIManager")
	private boolean moleculeNames;
	
	@Parameter(label="Process all frames")
	private boolean allFrames;
	
	/**
	 * FITTER SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String PeakFitterMessage =
		"Peak fitter settings:";
	
	@Parameter(label="Fit peaks")
	private boolean fitPeaks = false;
	
	@Parameter(label="Fit radius")
	private int fitRadius = 4;
	
	@Parameter(label = "Minimum R-squared",
			style = NumberWidget.SLIDER_STYLE, min = "0.00", max = "1.00",
			stepSize = "0.01")
	private double RsquaredMin = 0;
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String integrationTitle =
		"Peak integration settings:";
	
	@Parameter(label="Integrate")
	private boolean integrate = false;
	
	@Parameter(label="Inner radius")
	private int integrationInnerRadius = 1;
	
	@Parameter(label="Outer radius")
	private int integrationOuterRadius = 3;
	
	/**
	 * VERBOSE
	 */
	@Parameter(label="Verbose output")
	private boolean verbose = true;
	
	/**
	 * OUTPUTS
	 */
	@Parameter(label="Peak Count", type = ItemIO.OUTPUT)
	private MarsTable peakCount;
	
	@Parameter(label="Peaks", type = ItemIO.OUTPUT)
	private MarsTable peakTable;
	
	/**
	 * Map from t to peak list
	 */
	private ConcurrentMap<Integer, List<Peak>> peakStack;
	
	private boolean swapZandT = false;
	private Interval interval;
	private Roi startingRoi;

	public static final String[] TABLE_HEADERS_VERBOSE = {"baseline", "height", "sigma", "R2"};
	private List<int[]> innerOffsets;
	private List<int[]> outerOffsets;
	private Dataset dataset;
	private ImagePlus image;
	
	@Override
	public void initialize() {
		if (imageDisplay != null) {
			dataset = (Dataset) imageDisplay.getActiveView().getData();
			image = convertService.convert(imageDisplay, ImagePlus.class);
		} else if (dataset != null)
			image = convertService.convert(dataset, ImagePlus.class);
		else
			return;
		
		Rectangle rect;
		if (image.getRoi() == null) {
			rect = new Rectangle(0,0,image.getWidth()-1,image.getHeight()-1);
			final MutableModuleItem<Boolean> useRoifield = getInfo().getMutableInput("useROI", Boolean.class);
			useRoifield.setValue(this, false);
		} else {
			rect = image.getRoi().getBounds();
			startingRoi = image.getRoi();
		}
		
		final MutableModuleItem<String> channelItems = getInfo().getMutableInput("channel", String.class);
		long channelCount = dataset.getChannels();
		ArrayList<String> channels = new ArrayList<String>();
		for (int ch=1; ch<=channelCount; ch++)
			channels.add(String.valueOf(ch - 1));
		channelItems.setChoices(channels);
		channelItems.setValue(this, String.valueOf(image.getChannel() - 1));
		
		final MutableModuleItem<Integer> imgX0 = getInfo().getMutableInput("x0", Integer.class);
		imgX0.setValue(this, rect.x);
		
		final MutableModuleItem<Integer> imgY0 = getInfo().getMutableInput("y0", Integer.class);
		imgY0.setValue(this, rect.y);
		
		final MutableModuleItem<Integer> imgWidth = getInfo().getMutableInput("width", Integer.class);
		imgWidth.setValue(this, rect.width);
		
		final MutableModuleItem<Integer> imgHeight = getInfo().getMutableInput("height", Integer.class);
		imgHeight.setValue(this, rect.height);
		
		final MutableModuleItem<Integer> preFrame = getInfo().getMutableInput("previewT", Integer.class);
		if (image.getNFrames() < 2) {
			preFrame.setValue(this, image.getSlice() - 1);
			preFrame.setMaximumValue(image.getStackSize() - 1);
			swapZandT = true;
		} else {
			preFrame.setValue(this, image.getFrame() - 1);
			preFrame.setMaximumValue(image.getNFrames() - 1);
		}

		//Doesn't update, probably because of a bug in scijava.
		final MutableModuleItem<Integer> preT = getInfo().getMutableInput("previewT", Integer.class);
		preT.setValue(this, image.convertIndexToPosition(image.getCurrentSlice())[2] - 1);
	}
	
	@Override
	public void run() {	
		updateInterval();
		
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Peak Finder");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		logService.info(log);

		List<Peak> peaks = new ArrayList<>();
		
		innerOffsets = MarsImageUtils.innerIntegrationOffsets(integrationInnerRadius);
		outerOffsets = MarsImageUtils.outerIntegrationOffsets(integrationInnerRadius, integrationOuterRadius);
		
		double starttime = System.currentTimeMillis();
		logService.info("Finding Peaks...");
		if (allFrames) {
			peakStack = new ConcurrentHashMap<>();
			
			final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
			
			int frameCount = (swapZandT) ? image.getStackSize() : image.getNFrames();
			
			MarsUtil.forkJoinPoolBuilder(statusService, logService, 
					() -> statusService.showStatus(peakStack.size(), frameCount, "Finding Peaks for " + image.getTitle()), 
					() -> IntStream.range(0, frameCount).parallel().forEach(t -> { 
			        	List<Peak> tpeaks = findPeaksInT(Integer.valueOf(channel), t, useDogFilter, fitPeaks, integrate);
			        	
			        	if (tpeaks.size() > 0)
			        		peakStack.put(t, tpeaks);
			        }), PARALLELISM_LEVEL);
			
		} else {
			int t = image.getFrame() - 1;
			if (swapZandT) {
				image.setSlice(previewT + 1);
				t = image.getCurrentSlice() - 1;
			} else
				image.setPosition(Integer.valueOf(channel) + 1, 1, previewT + 1);

			peaks = findPeaksInT(Integer.valueOf(channel), t, useDogFilter, fitPeaks, integrate);
		}
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		
		if (generatePeakCountTable)
			generatePeakCountTable(peaks);
		
		if (generatePeakTable)
			generatePeakTable(peaks);
		
		if (addToRoiManager)
			addToRoiManager(peaks);
		
		image.setRoi(startingRoi);
		
		logService.info("Finished in " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		logService.info(LogBuilder.endBlock(true));
	}
	
	private void updateInterval() {
		interval = (useROI) ? Intervals.createMinMax(x0, y0, x0 + width - 1, y0 + height - 1) :
			  				  Intervals.createMinMax(0, 0, image.getWidth()-1,image.getHeight()-1);

		image.deleteRoi();
		image.setOverlay(null);
	}
	
	@SuppressWarnings("unchecked")
	private List<Peak> findPeaksInT(int channel, int t, boolean useDogFilter, boolean fitPeaks, boolean integrate) {
		RandomAccessibleInterval< T > img = (swapZandT) ? MarsImageUtils.get2DHyperSlice((ImgPlus< T >) dataset.getImgPlus(), t, -1, -1) :
														  MarsImageUtils.get2DHyperSlice((ImgPlus< T >) dataset.getImgPlus(), 0, channel, t);
		
		List<Peak> peaks = new ArrayList<Peak>();
		
		if (useDogFilter) {
			RandomAccessibleInterval< FloatType > filteredImg = MarsImageUtils.dogFilter(img, dogFilterRadius, opService);
			peaks = MarsImageUtils.findPeaks(filteredImg, interval, t, threshold, minimumDistance, findNegativePeaks);
		} else
			peaks = MarsImageUtils.findPeaks(img, interval, t, threshold, minimumDistance, findNegativePeaks);
		
		if (fitPeaks) {
			peaks = MarsImageUtils.fitPeaks(img, peaks, fitRadius, dogFilterRadius, findNegativePeaks, RsquaredMin, interval);
			peaks = MarsImageUtils.removeNearestNeighbors(peaks, minimumDistance);
		}
		
		if (integrate)
			MarsImageUtils.integratePeaks(img, interval, peaks, innerOffsets, outerOffsets);
		
		return peaks;
	}
	
	private void generatePeakCountTable(List<Peak> peaks) {
		logService.info("Generating peak count table..");
		peakCount = new MarsTable("Peak Count - " + image.getTitle());
		DoubleColumn frameColumn = new DoubleColumn("T");
		DoubleColumn countColumn = new DoubleColumn("peaks");
		
		if (allFrames) {
			for (int t : peakStack.keySet()) {
				frameColumn.addValue(t);
				countColumn.addValue(peakStack.get(t).size());
			}
		} else {
			frameColumn.addValue(previewT);
			countColumn.addValue(peaks.size());
		}
		peakCount.add(frameColumn);
		peakCount.add(countColumn);
		peakCount.sort("T");
		
		getInfo().getMutableOutput("peakCount", MarsTable.class).setLabel(peakCount.getName());
	}
	
	private void generatePeakTable(List<Peak> peaks) {
		logService.info("Generating peak table..");
		peakTable = new MarsTable("Peaks - " + image.getTitle());
		
		Map<String, DoubleColumn> columns = new LinkedHashMap<String, DoubleColumn>();
		
		columns.put("T", new DoubleColumn("T"));
		columns.put("x", new DoubleColumn("x"));
		columns.put("y", new DoubleColumn("y"));
		
		if (integrate) 
			columns.put("Intensity", new DoubleColumn("Intensity"));
		
		if (verbose)
			for (int i=0;i<TABLE_HEADERS_VERBOSE.length;i++)
				columns.put(TABLE_HEADERS_VERBOSE[i], new DoubleColumn(TABLE_HEADERS_VERBOSE[i]));
		
		if (allFrames)
			for (int i=0;i<peakStack.size() ; i++) {
				List<Peak> framePeaks = peakStack.get(i);
				for (int j=0;j<framePeaks.size();j++)
					framePeaks.get(j).addToColumns(columns);
			}
		else
			for (int j=0;j<peaks.size();j++)
				peaks.get(j).addToColumns(columns);
		
		for(String key : columns.keySet())
			peakTable.add(columns.get(key));	
		
		getInfo().getMutableOutput("peakTable", MarsTable.class).setLabel(peakTable.getName());
	}
	
	private void addToRoiManager(List<Peak> peaks) {
		logService.info("Adding Peaks to the RoiManger. This might take a while...");
		
		if (allFrames) {
			int peakNumber = 1;
			for (int i=0;i < peakStack.size() ; i++)
				peakNumber = addToRoiManager(peakStack.get(i), Integer.valueOf(channel), i, peakNumber);
		} else
			addToRoiManager(peaks, Integer.valueOf(channel), 0, 0);

		statusService.showStatus("Done adding ROIs to Manger");
	}

	private int addToRoiManager(List<Peak> peaks, int channel, int t, int startingPeakNum) {
		if (roiManager == null)
			roiManager = new RoiManager();
		int pCount = startingPeakNum;
		if (!peaks.isEmpty()) {
			for (Peak peak : peaks) {
				PointRoi peakRoi = new PointRoi(peak.getDoublePosition(0) + 0.5, peak.getDoublePosition(1) + 0.5);
				if (moleculeNames)
					peakRoi.setName("Molecule"+pCount);
				else
					peakRoi.setName(MarsMath.getUUID58());
				
				if (swapZandT)
					peakRoi.setPosition(channel, t + 1, 1);
				else
					peakRoi.setPosition(channel, 1, t + 1);
				roiManager.addRoi(peakRoi);
				pCount++;
			}
		}
		return pCount;
	}
	
	@Override
	public void preview() {	
		if (preview) {
			updateInterval();
			
			if (swapZandT)
				image.setSlice(previewT + 1);
			else
				image.setPosition(Integer.valueOf(channel) + 1, 1, previewT + 1);
			
			List<Peak> peaks = findPeaksInT(Integer.valueOf(channel), previewT, useDogFilter, false, false);
			
			final MutableModuleItem<String> preFrameCount = getInfo().getMutableInput("tPeakCount", String.class);
			if (!peaks.isEmpty()) {
				Polygon poly = new Polygon();
				
				for (Peak p: peaks) {
					int x = (int)p.getDoublePosition(0);
					int y = (int)p.getDoublePosition(1);
					poly.addPoint(x, y);
				}
				
				PointRoi peakRoi = new PointRoi(poly);
				image.setRoi(peakRoi);

				preFrameCount.setValue(this, "count: " + peaks.size());
			} else {
				preFrameCount.setValue(this, "count: 0");
			}
		}
	}
	
	@Override
	public void cancel() {
		if (image !=  null) {
			image.setOverlay(null);
			image.setRoi(startingRoi);
		}
	}
	
	/** Called when the {@link #preview} parameter value changes. */
	protected void previewChanged() {
		if (!preview) cancel();
	}
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Image Title", image.getTitle());
		if (image.getOriginalFileInfo() != null && image.getOriginalFileInfo().directory != null) {
			builder.addParameter("Image Directory", image.getOriginalFileInfo().directory);
		}
		builder.addParameter("Use ROI", String.valueOf(useROI));
		if (useROI) {
			builder.addParameter("ROI x0", String.valueOf(x0));
			builder.addParameter("ROI y0", String.valueOf(y0));
			builder.addParameter("ROI width", String.valueOf(width));
			builder.addParameter("ROI height", String.valueOf(height));
		}
		builder.addParameter("Use DoG filter", String.valueOf(useDogFilter));
		builder.addParameter("DoG filter radius", String.valueOf(dogFilterRadius));
		builder.addParameter("Threshold", String.valueOf(threshold));
		builder.addParameter("Minimum distance", String.valueOf(minimumDistance));
		builder.addParameter("Find negative peaks", String.valueOf(findNegativePeaks));
		builder.addParameter("Generate peak count table", String.valueOf(generatePeakCountTable));
		builder.addParameter("Generate peak table", String.valueOf(generatePeakTable));
		builder.addParameter("Add to ROIManager", String.valueOf(addToRoiManager));
		builder.addParameter("Process all frames", String.valueOf(allFrames));
		builder.addParameter("Fit peaks", String.valueOf(fitPeaks));
		builder.addParameter("Fit Radius", String.valueOf(fitRadius));
		builder.addParameter("Minimum R-squared", String.valueOf(RsquaredMin));
		builder.addParameter("Integrate", String.valueOf(integrate));
		builder.addParameter("Integration inner radius", String.valueOf(integrationInnerRadius));
		builder.addParameter("Integration outer radius", String.valueOf(integrationOuterRadius));
		builder.addParameter("Swap Z and T", swapZandT);
		builder.addParameter("Verbose output", String.valueOf(verbose));
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
	
	public void setUseROI(boolean useROI) {
		this.useROI = useROI;
	}
	
	public boolean getUseROI() {
		return useROI;
	}
	
	public void setX0(int x0) {
		this.x0 = x0;
	}
	
	public int getX0() {
		return x0;
	}
	
	public void setY0(int y0) {
		this.y0 = y0;
	}
	
	public int getY0() {
		return y0;
	}
	
	public void setWidth(int width) {
		this.width = width;
	}
	
	public int getWidth() {
		return width;
	}
	
	public void setHeight(int height) {
		this.height = height;
	}
	
	public int getHeight() {
		return height;
	}
	
	public void setChannel(int channel) {
		this.channel = String.valueOf(channel);
	}
	
	public int getChannel() {
		return Integer.valueOf(channel);
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
}
