/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2023 Karl Duderstadt
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

package de.mpg.biochem.mars.roi.commands;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.SwingUtilities;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.display.ImageDisplay;
import org.scijava.Initializable;
import net.imagej.ops.OpService;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Previewable;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.DefaultMutableModuleItem;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.UIService;
import org.scijava.widget.NumberWidget;

import de.mpg.biochem.mars.image.MarsImageUtils;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.LogBuilder;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

@Plugin(type = Command.class, label = "Transform ROIs", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(label = "ROI",
			weight = 4, mnemonic = 'r'), @Menu(label = "Transform ROIs", weight = 1,
				mnemonic = 't') })
public class TransformROIsCommand extends DynamicCommand implements Command,
	Initializable, Previewable
{

	/**
	 * SERVICES
	 */

	@Parameter
	private UIService uiService;

	@Parameter
	private LogService logService;

	@Parameter
	private OpService opService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private MarsTableService resultsTableService;

	@Parameter
	private ConvertService convertService;

	/**
	 * IMAGE
	 */
	@Parameter(label = "Image to search for Peaks")
	private ImageDisplay imageDisplay;

	/**
	 * ROI
	 */
	@Parameter
	private RoiManager roiManager;

	/**
	 * INPUT SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String inputGroup = "Input";

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "image, group:Input",
		persist = false)
	private String inputFigure = "TransformROIsInput.png";

	@Parameter(visibility = ItemVisibility.MESSAGE,
		style = "group:Input, align:center", persist = false)
	private String imageName = "name";

	@Parameter(visibility = ItemVisibility.MESSAGE,
		style = "group:Input, align:left", persist = false)
	private String affineTitle = "Affine2D Transformation Matrix";
	
	@Parameter(label = "m00", style = "format:#####.#####, group:Input")
	private double m00;

	@Parameter(label = "m01", style = "format:#####.#####, group:Input")
	private double m01;

	@Parameter(label = "m02", style = "format:#####.#####, group:Input")
	private double m02;

	@Parameter(label = "m10", style = "format:#####.#####, group:Input")
	private double m10;

	@Parameter(label = "m11", style = "format:#####.#####, group:Input")
	private double m11;

	@Parameter(label = "m12", style = "format:#####.#####, group:Input")
	private double m12;

	@Parameter(label = "Apply inverse transformation", style = "group:Input")
	private boolean inverseTransform = false;

	/**
	 * COLOCALIZE SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String colocalizeGroup = "Colocalize";

	@Parameter(label = "Colocalize", style = "group:Colocalize")
	private boolean colocalize = false;

	@Parameter(label = "Channel", choices = { "a", "b", "c" },
		style = "group:Colocalize", persist = false)
	private String channel = "0";

	@Parameter(label = "DoG filter", style = "group:Colocalize")
	private boolean useDogFilter = true;

	@Parameter(label = "DoG radius", style = "group:Colocalize")
	private double dogFilterRadius = 2;

	@Parameter(label = "Threshold", style = "group:Colocalize")
	private double threshold = 50;

	@Parameter(label = "Search radius", style = "group:Colocalize")
	private int colocalizeRadius = 0;

	@Parameter(label = "Filter original ROIs", style = "group:Colocalize")
	private boolean filterOriginalRois = true;

	@Parameter(label = "Remove colocalizing ROIs", style = "group:Colocalize")
	private boolean filterColocalizingRois = false;

	/**
	 * OUTPUT SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String outputGroup = "Output";

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "image, group:Output",
		persist = false)
	private String outputFigure = "TransformROIsOutput.png";

	/**
	 * PREVIEW SETTINGS
	 */

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private String previewGroup = "Preview";

	@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false,
		callback = "previewChanged", style = "group:Preview")
	private boolean preview = false;

	@Parameter(label = "T", min = "0", style = NumberWidget.SCROLL_BAR_STYLE +
		", group:Preview", persist = false)
	private int theT;

	@Parameter(label = "Preview timeout (s)", style = "group:Preview")
	private int previewTimeout = 10;

	private Dataset dataset;
	private ImagePlus image;
	private boolean swapZandT = false;

	// True when two regions already exist in the Roi manager based on _ suffixes
	// existing.
	private boolean subRegionMode = false;

	MutableModuleItem<String> fromRegionName, toRegionName;

	private String transformFrom, transformTo;

	@Override
	public void initialize() {
		if (imageDisplay != null) {
			dataset = (Dataset) imageDisplay.getActiveView().getData();
			image = convertService.convert(imageDisplay, ImagePlus.class);
		}
		else if (dataset == null) return;

		if (dataset != null) {
			final MutableModuleItem<String> imageNameItem = getInfo().getMutableInput(
				"imageName", String.class);
			imageNameItem.setValue(this, dataset.getName());
		}

		final MutableModuleItem<String> channelItems = getInfo().getMutableInput(
			"channel", String.class);
		long channelCount = dataset.getChannels();
		ArrayList<String> channels = new ArrayList<>();
		for (int ch = 1; ch <= channelCount; ch++)
			channels.add(String.valueOf(ch - 1));
		channelItems.setChoices(channels);
		channelItems.setValue(this, String.valueOf(image.getChannel() - 1));

		fromRegionName = new DefaultMutableModuleItem<>(this,
				"Transform from region", String.class);
		fromRegionName.setWidgetStyle("group:Output");

		subRegionMode = roisHaveSuffixes();
		if (subRegionMode) {
			fromRegionName.setChoices(getSubRegionNames());
		}
		else {
			fromRegionName.setDefaultValue("LONG");
		}
		getInfo().addInput(fromRegionName);

		toRegionName = new DefaultMutableModuleItem<>(this,
				"Transform to region", String.class);
		toRegionName.setWidgetStyle("group:Output");
		toRegionName.setDefaultValue("SHORT");
		getInfo().addInput(toRegionName);

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
		if (roiManager == null) {
			uiService.showDialog("No ROIs in manager to transform.");
			return;
		}

		if (image != null) {
			image.deleteRoi();
			image.setOverlay(null);
		}

		if (transformFrom == null && fromRegionName != null)
			transformFrom = fromRegionName.getValue(this);
		
		if (transformTo == null && toRegionName != null)
			transformTo = toRegionName.getValue(this);

		// Build log
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Transform ROIs");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		logService.info(log);

		List<Roi> originalROIs = new ArrayList<>();
		List<Roi> remainingROIs = new ArrayList<>();

		int roiNum = roiManager.getCount();
		for (int i = 0; i < roiNum; i++) {
			Roi roi = roiManager.getRoi(i);
			if (subRegionMode && !roi.getName().endsWith(transformFrom)) {
				remainingROIs.add((Roi) roi.clone());
			}
			else originalROIs.add((Roi) roi.clone());
		}

		List<Roi> transformedROIs = new ArrayList<>();
		transformROIs(transformedROIs, originalROIs);

		List<Integer> colocalizedIndex;

		List<Roi> combinedRoiList = new ArrayList<>();
		if (colocalize) {
			colocalizedIndex = colocalize(transformedROIs, threshold, Integer.parseInt(
				channel), theT);

			if (filterOriginalRois) {
				for (Integer index : colocalizedIndex) {
					combinedRoiList.add(originalROIs.get(index));
					combinedRoiList.add(transformedROIs.get(index));
				}
			}
			else {
				for (int i = 0; i < transformedROIs.size(); i++) {
					combinedRoiList.add(originalROIs.get(i));
					if (colocalizedIndex.contains(i)) {
						combinedRoiList.add(transformedROIs.get(colocalizedIndex.get(
							colocalizedIndex.indexOf(i))));
					}
				}
			}
		}
		else {
			for (int i = 0; i < transformedROIs.size(); i++) {
				combinedRoiList.add(originalROIs.get(i));
				combinedRoiList.add(transformedROIs.get(i));
			}
		}

		combinedRoiList.addAll(remainingROIs);
		combinedRoiList.sort(Comparator.comparing(Roi::getName));

		roiManager.reset();
		for (Roi points : combinedRoiList) roiManager.addRoi(points);

		if (!uiService.isHeadless()) roiManager.repaint();

		logService.info(LogBuilder.endBlock(true));
	}

	private boolean roisHaveSuffixes() {
		int roiNum = roiManager.getCount();
		for (int i = 0; i < roiNum; i++) {
			if (roiManager.getRoi(i).getName().contains("_")) return true;
		}
		return false;
	}

	private List<String> getSubRegionNames() {
		Set<String> uniqueSuffixes = new HashSet<>();

		int roiNum = roiManager.getCount();
		for (int i = 0; i < roiNum; i++) {
			String name = roiManager.getRoi(i).getName();
			if (name.contains("_")) uniqueSuffixes.add(name.substring(name.indexOf(
				"_") + 1));
		}
		return new ArrayList<>(uniqueSuffixes);
	}

	private void transformROIs(List<Roi> transformedROIs,
		List<Roi> originalROIs)
	{

		AffineTransform2D transform = new AffineTransform2D();
		transform.set(m00, m01, m02, m10, m11, m12);

		for (Roi roi : originalROIs) {
			double[] source = new double[2];
			source[0] = roi.getFloatBounds().x;
			source[1] = roi.getFloatBounds().y;

			double[] target = new double[2];

			String originalRoiName = roi.getName();

			if (!subRegionMode) roi.setName(originalRoiName + "_" + transformFrom);

			Roi newRoi = (Roi) roi.clone();
			if (this.inverseTransform) {
				// This one transforms target to source...
				// same input order as apply but operates on second input and result is
				// in first...
				transform.applyInverse(target, source);
			}
			else transform.apply(source, target);

			String baseRoiName = (subRegionMode) ? originalRoiName.substring(0,
				originalRoiName.indexOf("_")) : originalRoiName;

			newRoi.setLocation(target[0], target[1]);
			newRoi.setName(baseRoiName + "_" + transformTo);
			newRoi.setStrokeColor(Color.CYAN.darker());
			transformedROIs.add(newRoi);
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends RealType<T> & NativeType<T>> List<Integer> colocalize(
		List<Roi> transformedROIs, double threshold, int channel, int t)
	{

		ArrayList<Integer> colocalizedIndex = new ArrayList<>();

		RandomAccessibleInterval<T> img = (swapZandT) ? MarsImageUtils
			.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), t, -1, -1)
			: MarsImageUtils.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), 0,
				channel, t);

		Interval interval = Intervals.createMinMax(0, 0, dataset.dimension(0) - 1,
			dataset.dimension(1) - 1);

		RandomAccessibleInterval<FloatType> dog = (useDogFilter) ? MarsImageUtils
			.dogFilter(img, dogFilterRadius, Runtime.getRuntime().availableProcessors()) : opService.convert().float32(
				Views.iterable(img));

		RandomAccessible<FloatType> rae = Views.extendMirrorSingle(Views.interval(
			dog, interval));
		RandomAccess<FloatType> ra = rae.randomAccess();

		for (int i = 0; i < transformedROIs.size(); i++) {

			// The pixel origin for OvalRois is in the upper left corner.
			// The pixel origin for PointRois is in the center.
			// We always use pixel center as origin when integrating peaks.
			double pixelOriginOffset = (transformedROIs.get(0) instanceof OvalRoi)
				? -0.5 : 0;

			int x = (int) (transformedROIs.get(i).getFloatBounds().x +
				pixelOriginOffset + transformedROIs.get(i).getFloatBounds().width / 2);
			int y = (int) (transformedROIs.get(i).getFloatBounds().y +
				pixelOriginOffset + transformedROIs.get(i).getFloatBounds().height / 2);

			float max = 0;
			for (int Sy = y - colocalizeRadius; Sy <= y + colocalizeRadius; Sy++)
				for (int Sx = x - colocalizeRadius; Sx <= x + colocalizeRadius; Sx++)
					if (max < ra.setPositionAndGet(Sx, Sy).get()) max = ra.get().get();

			if (!filterColocalizingRois && max > threshold) colocalizedIndex.add(i);
			else if (filterColocalizingRois && max < threshold) colocalizedIndex.add(
				i);
		}
		return colocalizedIndex;
	}

	@Override
	public void preview() {
		if (preview) {
			ExecutorService es = Executors.newSingleThreadExecutor();
			try {
				es.submit(() -> {
					if (swapZandT) image.setSlice(theT + 1);
					else image.setPosition(Integer.parseInt(channel) + 1, 1, theT + 1);

					transformFrom = fromRegionName.getValue(this);
					transformTo = toRegionName.getValue(this);

					List<Roi> transformedROIs = new ArrayList<>();
					List<Roi> originalROIs = new ArrayList<>();
					int roiNum = roiManager.getCount();
					for (int i = 0; i < roiNum; i++) {
						Roi roi = roiManager.getRoi(i);
						if (subRegionMode && !roi.getName().endsWith(transformFrom))
							continue;

						if (roi instanceof OvalRoi) {
							final OvalRoi ovalRoi = new OvalRoi(roi.getFloatBounds().x, roi
								.getFloatBounds().y, roi.getFloatBounds().width, roi
									.getFloatBounds().height);
							ovalRoi.setName(roi.getName());
							originalROIs.add(ovalRoi);
						}
						else {
							final PointRoi pointRoi = new PointRoi(roi.getFloatBounds().x, roi
								.getFloatBounds().y);
							pointRoi.setName(roi.getName());
							originalROIs.add(pointRoi);
						}
					}

					transformROIs(transformedROIs, originalROIs);

					List<Integer> colocalizedIndex;

					Overlay overlay = new Overlay();
					if (colocalize) {
						colocalizedIndex = colocalize(transformedROIs, threshold, Integer
							.parseInt(channel), theT);

						if (filterOriginalRois) {
							for (Integer index : colocalizedIndex) {
								overlay.add(originalROIs.get(index));
								overlay.add(transformedROIs.get(index));
							}
						}
						else {
							for (int i = 0; i < transformedROIs.size(); i++) {
								overlay.add(originalROIs.get(i));
								if (colocalizedIndex.contains(i)) {
									overlay.add(transformedROIs.get(colocalizedIndex.get(
										colocalizedIndex.indexOf(i))));
								}
							}
						}
					}
					else {
						for (int i = 0; i < transformedROIs.size(); i++) {
							overlay.add(originalROIs.get(i));
							overlay.add(transformedROIs.get(i));
						}
					}

					SwingUtilities.invokeLater(() -> {
						if (image != null) {
							image.deleteRoi();
							image.setOverlay(overlay);
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

	@Override
	public void cancel() {
		if (image != null) image.setOverlay(null);
	}

	/** Called when the {@link #preview} parameter value changes. */
	protected void previewChanged() {
		if (!preview) cancel();
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Affine2D m00", String.valueOf(m00));
		builder.addParameter("Affine2D m01", String.valueOf(m01));
		builder.addParameter("Affine2D m02", String.valueOf(m02));
		builder.addParameter("Affine2D m10", String.valueOf(m10));
		builder.addParameter("Affine2D m11", String.valueOf(m11));
		builder.addParameter("Affine2D m12", String.valueOf(m12));
		builder.addParameter("Apply inverse transformation", String.valueOf(
			inverseTransform));
		builder.addParameter("Transform from region", transformFrom);
		builder.addParameter("Transform to region", transformTo);
		builder.addParameter("Use DoG filter", String.valueOf(useDogFilter));
		builder.addParameter("DoG filter radius", String.valueOf(dogFilterRadius));
		builder.addParameter("Threshold", String.valueOf(threshold));
		builder.addParameter("colocalizeRadius", String.valueOf(colocalizeRadius));
		builder.addParameter("filterOriginalRois", String.valueOf(
			filterOriginalRois));
		builder.addParameter("filterColocalizingRois", String.valueOf(
			filterColocalizingRois));
	}

	public void setRoiManager(RoiManager roiManager) {
		this.roiManager = roiManager;
	}

	public RoiManager getRoiManager() {
		return roiManager;
	}

	public void setM00(double m00) {
		this.m00 = m00;
	}

	public void setM01(double m01) {
		this.m01 = m01;
	}

	public void setM02(double m02) {
		this.m02 = m02;
	}

	public void setM10(double m10) {
		this.m10 = m10;
	}

	public void setM11(double m11) {
		this.m11 = m11;
	}

	public void setM12(double m12) {
		this.m12 = m12;
	}

	public void setApplyInverseTransformation(boolean inverseTransform) {
		this.inverseTransform = inverseTransform;
	}

	public boolean getApplyInverseTransformation() {
		return inverseTransform;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}

	public Dataset getDataset() {
		return dataset;
	}

	public void setChannel(int channel) {
		this.channel = String.valueOf(channel);
	}

	public int getChannel() {
		return Integer.parseInt(channel);
	}

	public void setT(int theT) {
		this.theT = theT;
	}

	public int getT() {
		return theT;
	}

	public void setTransformFromRegion(String transformFrom) {
		this.transformFrom = transformFrom;
	}

	public String getTransformFromRegion() {
		return transformFrom;
	}

	public void setTransformToRegion(String transformTo) {
		this.transformTo = transformTo;
	}

	public String getTransformToRegion() {
		return transformTo;
	}

	public void setColocalize(boolean colocalize) {
		this.colocalize = colocalize;
	}

	public boolean getColocalize() {
		return colocalize;
	}

	public void setUseDogFilter(boolean useDogFilter) {
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

	public void setFilterOriginalRois(boolean filterOriginalRois) {
		this.filterOriginalRois = filterOriginalRois;
	}

	public boolean getFilterOriginalRois() {
		return filterOriginalRois;
	}

	public void setFilterColocalizingRois(boolean filterColocalizingRois) {
		this.filterColocalizingRois = filterColocalizingRois;
	}

	public boolean getFilterColocalizingRois() {
		return filterColocalizingRois;
	}

	public void setColocalizeSearchRadius(int colocalizeRadius) {
		this.colocalizeRadius = colocalizeRadius;
	}
}
