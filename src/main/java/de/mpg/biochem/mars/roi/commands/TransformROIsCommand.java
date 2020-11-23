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

package de.mpg.biochem.mars.roi.commands;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.display.ImageDisplay;
import net.imagej.ops.Initializable;
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
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.NumberWidget;

import de.mpg.biochem.mars.image.MarsImageUtils;
import de.mpg.biochem.mars.image.Peak;
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
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 's'), @Menu(label = "ROI",
			weight = 30, mnemonic = 'r'), @Menu(label = "Transform ROIs", weight = 30,
				mnemonic = 't') })
public class TransformROIsCommand extends DynamicCommand
	implements Command, Initializable, Previewable
{

	/**
	 * SERVICES
	 */
	@Parameter
	private RoiManager roiManager;

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
	 * AFFINE2D Matrix
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String affineTitle = "Affine2D Transformation Matrix:";

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

	@Parameter(label = "Transformation Direction", choices = {
		"Long Wavelength to Short Wavelength",
		"Short Wavelength to Long Wavelength" })
	private String transformationDirection =
		"Long Wavelength to Short Wavelength";
	
	@Parameter(label = "Colocalize")
	private boolean colocalize = false;
	
	@Parameter(label = "Channel", choices = { "a", "b", "c" }, persist = false)
	private String channel = "0";
	
	@Parameter(label = "T", min = "0", style = NumberWidget.SCROLL_BAR_STYLE, persist = false)
	private int theT;

	@Parameter(label = "Use DoG filter")
	private boolean useDogFilter = true;

	@Parameter(label = "DoG filter radius")
	private double dogFilterRadius = 2;

	@Parameter(label = "Detection threshold")
	private double threshold = 50;

	@Parameter(label = "Filter Original ROIs")
	private boolean filterOriginalRois = true;

	@Parameter(label = "Colocalize search radius")
	private int colocalizeRadius = 0;

	@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false,
		callback = "previewChanged")
	private boolean preview = false;
	
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
		if (roiManager == null) {
			uiService.showDialog("No ROIs in manager to transform.");
			return;
		}
		
		if (image != null) {
			image.deleteRoi();
			image.setOverlay(null);
		}
		
		// Build log
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Transform ROIs");
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		logService.info(log);
		
		List<Roi> transformedROIs = new ArrayList<Roi>();
		List<Roi> originalROIs = new ArrayList<>();
		int roiNum = roiManager.getCount();
		for (int i = 0; i < roiNum; i++) {
			Roi roi = roiManager.getRoi(i);
			originalROIs.add((Roi) roi.clone());
		}

		transformROIs(transformedROIs, originalROIs);
		
		List<Integer> colocalizedIndex = new ArrayList<Integer>();
		
		roiManager.reset();
		if (colocalize) {
			colocalizedIndex = colocalize(transformedROIs, threshold, Integer.valueOf(channel), theT);	
			
			if (filterOriginalRois) {
				for (int i = 0; i < colocalizedIndex.size(); i++) {
					roiManager.addRoi(originalROIs.get(colocalizedIndex.get(i)));
					roiManager.addRoi(transformedROIs.get(colocalizedIndex.get(i)));
				}
			}
			else {
				for (int i = 0; i < transformedROIs.size(); i++) {
					roiManager.addRoi(originalROIs.get(i));
					if (colocalizedIndex.contains(i)) {
						roiManager.addRoi(transformedROIs.get(colocalizedIndex.get(
								colocalizedIndex.indexOf(i))));
					}
				}
			}
		}
		else {
			for (int i = 0; i < transformedROIs.size(); i++) {
				roiManager.addRoi(originalROIs.get(i));
				roiManager.addRoi(transformedROIs.get(i));
			}
		}

		logService.info(LogBuilder.endBlock(true));
	}
	
	private void transformROIs(List<Roi> transformedROIs, List<Roi> originalROIs) {

		AffineTransform2D transform = new AffineTransform2D();
		transform.set(m00, m01, m02, m10, m11, m12);

		for (Roi roi : originalROIs) {
			double[] source = new double[2];
			source[0] = roi.getFloatBounds().x;
			source[1] = roi.getFloatBounds().y;

			double[] target = new double[2];

			String baseRoiName = roi.getName();
			String currentPosition = "LONG";
			String newPosition = "SHORT";

			if (transformationDirection.equals(
				"Long Wavelength to Short Wavelength"))
			{
				currentPosition = "LONG";
				newPosition = "SHORT";
			}
			else if (transformationDirection.equals(
				"Short Wavelength to Long Wavelength"))
			{
				currentPosition = "SHORT";
				newPosition = "LONG";
			}

			roi.setName(baseRoiName + "_" + currentPosition);

			Roi newRoi = (Roi) roi.clone();
			transform.apply(source, target);
			
			newRoi.setLocation(target[0], target[1]);
			newRoi.setName(baseRoiName + "_" + newPosition);
			transformedROIs.add(newRoi);
		}
	}

	@SuppressWarnings("unchecked")
	public <T extends RealType<T> & NativeType<T>> List<Integer> colocalize(List<Roi> transformedROIs, double threshold, int channel, int t) {
		
		ArrayList<Integer> colocalizedIndex = new ArrayList<Integer>();
		
		RandomAccessibleInterval<T> img = (swapZandT) ? MarsImageUtils
				.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), t, -1, -1)
				: MarsImageUtils.get2DHyperSlice((ImgPlus<T>) dataset.getImgPlus(), 0,
					channel, t);
				
		Interval interval = Intervals.createMinMax(0, 0, dataset.dimension(0) - 1, dataset.dimension(1) - 1);
		
		RandomAccessibleInterval<FloatType> dog = (useDogFilter) ?
				MarsImageUtils.dogFilter(img, dogFilterRadius, opService) :
				opService.convert().float32(Views.iterable(img));
			
		RandomAccessible<FloatType> rae = Views.extendMirrorSingle(Views.interval(dog,
				interval));
		RandomAccess<FloatType> ra = rae.randomAccess();

		for (int i = 0; i < transformedROIs.size(); i++) {
			
			//The pixel origin for OvalRois is at the upper left corner !!
			//The pixel origin for PointRois is at the center !!
			//We always use pixel center as origin when integrating peaks !!
			double pixelOrginOffset = (transformedROIs.get(0) instanceof OvalRoi) ? -0.5 : 0;

			int x = (int) (transformedROIs.get(i).getFloatBounds().x + pixelOrginOffset + transformedROIs.get(i).getFloatBounds().width / 2);
			int y = (int) (transformedROIs.get(i).getFloatBounds().y + pixelOrginOffset + transformedROIs.get(i).getFloatBounds().height / 2);
			
			float max = 0;
			for (int Sy = y - colocalizeRadius; Sy <= y + colocalizeRadius; Sy++)
				for (int Sx = x - colocalizeRadius; Sx <= x + colocalizeRadius; Sx++)
					if (max < ra.setPositionAndGet(Sx, Sy).get())
						max = ra.get().get();
			
			if (max > threshold)
				colocalizedIndex.add(i);
		}
		return colocalizedIndex;
	}
	
	@Override
	public void preview() {
		if (preview) {
			image.deleteRoi();
			image.setOverlay(null);
			
			if (swapZandT) image.setSlice(theT + 1);
			else image.setPosition(Integer.valueOf(channel) + 1, 1, theT + 1);
			
			List<Roi> transformedROIs = new ArrayList<Roi>();
			List<Roi> originalROIs = new ArrayList<>();
			int roiNum = roiManager.getCount();
			for (int i = 0; i < roiNum; i++) {
				Roi roi = roiManager.getRoi(i);
				if (roi instanceof OvalRoi) {
					final OvalRoi ovalRoi = new OvalRoi( roi.getFloatBounds().x ,roi.getFloatBounds().y, roi.getFloatBounds().width, roi.getFloatBounds().height );
					ovalRoi.setStrokeColor( Color.CYAN.darker() );
					originalROIs.add(ovalRoi);
				} else {
					final PointRoi pointRoi = new PointRoi( roi.getFloatBounds().x ,roi.getFloatBounds().y );
					originalROIs.add(pointRoi);
				}
			}

			transformROIs(transformedROIs, originalROIs);
			
			List<Integer> colocalizedIndex = new ArrayList<Integer>();
			
			Overlay overlay = new Overlay();
			if (colocalize) {
				colocalizedIndex = colocalize(transformedROIs, threshold, Integer.valueOf(channel), theT);	
				
				if (filterOriginalRois) {
					for (int i = 0; i < colocalizedIndex.size(); i++) {
						overlay.add(originalROIs.get(colocalizedIndex.get(i)));
						overlay.add(transformedROIs.get(colocalizedIndex.get(i)));
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
			
			image.setOverlay(overlay);
		}
	}

	@Override
	public void cancel() {
		if (image != null)
			image.setOverlay(null);
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
		builder.addParameter("Transformation Direction", transformationDirection);
		builder.addParameter("Use DoG filter", String.valueOf(useDogFilter));
		builder.addParameter("DoG filter radius", String.valueOf(dogFilterRadius));
		builder.addParameter("Threshold", String.valueOf(threshold));
		builder.addParameter("filterOriginalRois", String.valueOf(
			filterOriginalRois));
		builder.addParameter("colocalizeRadius", String.valueOf(colocalizeRadius));
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
		return Integer.valueOf(channel);
	}
	
	public void setT(int theT) {
		this.theT = theT;
	}
	
	public int getT() {
		return theT;
	}

	public void setTransformationDirection(String transformationDirection) {
		this.transformationDirection = transformationDirection;
	}

	public String getTransformation() {
		return transformationDirection;
	}

	public void setColocalize(boolean colocalize) {
		this.colocalize = colocalize;
	}

	public boolean getColocalize() {
		return colocalize;
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

	public void setFilterOriginalRois(boolean filterOriginalRois) {
		this.filterOriginalRois = filterOriginalRois;
	}

	public boolean getFilterOriginalRois() {
		return filterOriginalRois;
	}

	public void setColocalizeSearchRadius(int colocalizeRadius) {
		this.colocalizeRadius = colocalizeRadius;
	}
}
