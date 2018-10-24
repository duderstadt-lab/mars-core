package de.mpg.biochem.sdmm.RoiTools;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Vector;

import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Previewable;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import de.mpg.biochem.sdmm.ImageProcessing.DiscoidalAveragingFilter;
import de.mpg.biochem.sdmm.ImageProcessing.Peak;
import de.mpg.biochem.sdmm.table.ResultsTableService;
import de.mpg.biochem.sdmm.table.SDMMResultsTable;
import de.mpg.biochem.sdmm.util.LevenbergMarquardt;
import de.mpg.biochem.sdmm.util.LogBuilder;
import de.mpg.biochem.sdmm.util.SDMMMath;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import net.imagej.ops.Initializable;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import ij.plugin.PlugIn;

@Plugin(type = Command.class, label = "Transform ROIs", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "ROI Tools", weight = 30,
			mnemonic = 'r'),
		@Menu(label = "Transform ROIs", weight = 30, mnemonic = 't')})
public class TransformROIsCommand extends DynamicCommand implements Command, Previewable {
	//GENERAL SERVICES NEEDED
	@Parameter
	private RoiManager roiManager;
	
	@Parameter
	private UIService uiService;
	
	@Parameter
	private LogService logService;
	
    @Parameter
    private StatusService statusService;
    
	@Parameter
    private ResultsTableService resultsTableService;

	//INPUT IMAGE
	@Parameter(label = "Image")
	private ImagePlus image; 
	
	@Parameter(label = "Transformation Parameters")
	private SDMMResultsTable data_table;
	
	@Parameter(label = "Transformation Direction", choices = {"Long Wavelength to Short Wavelength", "Short Wavelength to Long Wavelength"})
	private String transformationDirection = "Long Wavelength to Short Wavelength";
	
	@Parameter(label = "Colocalize")
	private boolean colocalize = false;
	
	@Parameter(label="Use Discoidal Averaging Filter")
	private boolean useDiscoidalAveragingFilter;
	
	@Parameter(label="Inner radius")
	private int DS_innerRadius;
	
	@Parameter(label="Outer radius")
	private int DS_outerRadius;
	
	@Parameter(label="Detection threshold (mean + N * STD)")
	private int threshold = 6;
	
	@Parameter(label="Filter Original ROIs")
	private boolean filterOriginalRois = true;
	
	@Parameter(label="Colocalize search radius")
	private int colocalizeRadius = 0;
	
	@Parameter(visibility = ItemVisibility.INVISIBLE, persist = false, callback = "previewChanged")
	private boolean preview = false;

	private ArrayList<Roi> TransformedROIs = new ArrayList<Roi>();
    private ArrayList<Roi> OriginalROIs = new ArrayList<Roi>();
    
    private ArrayList<Integer> colocalizedPeakIndex = new ArrayList<Integer>();
    
	private Roi startingRoi;

	@Override
	public void run() {
		//Build log
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Transform ROIs");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		if (preview) {
			image.deleteRoi();
		}
		
		transformROIs();
		
		roiManager.reset();
		
		//If we are not colocalizing then we add all the peaks...
		if (colocalize) {
			if (filterOriginalRois) {
				for (int i=0;i<colocalizedPeakIndex.size();i++) {
					roiManager.addRoi(OriginalROIs.get(colocalizedPeakIndex.get(i)));
					roiManager.addRoi(TransformedROIs.get(colocalizedPeakIndex.get(i)));
				}
			} else {
				for (int i=0;i<TransformedROIs.size();i++) {
					roiManager.addRoi(OriginalROIs.get(i));
					if (colocalizedPeakIndex.contains(i)) {
						roiManager.addRoi(TransformedROIs.get(colocalizedPeakIndex.get(colocalizedPeakIndex.indexOf(i))));
					}
				}
			}
		} else {
			for (int i=0;i<TransformedROIs.size();i++) {
				roiManager.addRoi(OriginalROIs.get(i));
				roiManager.addRoi(TransformedROIs.get(i));
			}
		}
		
		logService.info(builder.endBlock(true));
		
		//ImagePlus image = WindowManager.getCurrentImage();
		//image.updateAndRepaintWindow();
	}
	
	public ArrayList<Point> findPeaks(ImageProcessor ip) {
		ArrayList<Point> peaks = new ArrayList<Point>();
		ImageProcessor duplicate; 
		
		if (useDiscoidalAveragingFilter) {
			duplicate = DiscoidalAveragingFilter.calcDiscoidalAveragedImageInfiniteMirror(ip.duplicate(), DS_innerRadius, DS_outerRadius);
		} else {
			duplicate = ip.duplicate();
		}
		
		Rectangle roi = ip.getRoi();
			
		// determine mean and standard deviation
		double mean = 0;
		double stdDev = 0;
		
		for (int y = roi.y; y < roi.y + roi.height; y++) {
			for (int x = roi.x; x < roi.x + roi.width; x++) {
				
				mean += duplicate.getf(x, y);
			}
		}
		
		mean /= roi.width * roi.height;
		
		for (int y = roi.y; y < roi.y + roi.height; y++) {
			for (int x = roi.x; x < roi.x + roi.width; x++) {
				
				double d = duplicate.getf(x, y) - mean;
				
				stdDev += d * d;
			}
		}
		
		stdDev /= roi.width * roi.height;
		stdDev = Math.sqrt(stdDev);
		
		double t = mean + threshold * stdDev;

		
		colocalizedPeakIndex.clear();
		
		for (int i = 0; i < TransformedROIs.size(); i++) {
			
				int x = (int)TransformedROIs.get(i).getFloatBounds().x;
		        int y = (int)TransformedROIs.get(i).getFloatBounds().y;
				
				//Let's check a radius of pixels around the transformed peaks if anyone is above the threshold we keep the colocalizedPeak
				ArrayList<Point> col_search_pixels = new ArrayList<Point>();
				
				for (int Sy = y - colocalizeRadius; Sy <= y + colocalizeRadius; Sy++) {
					for (int Sx = x - colocalizeRadius; Sx <= x + colocalizeRadius; Sx++) {
						if (Sx < 0 || Sx > duplicate.getWidth() || Sy < 0 || Sy > duplicate.getHeight())
							continue;
						
						col_search_pixels.add(new Point(Sx, Sy));
					}
				}
				
				boolean passed = false;
				for (int w=0;w<col_search_pixels.size();w++) {
					if (duplicate.getf(col_search_pixels.get(w).x, col_search_pixels.get(w).y) >= t) {
						passed = true;
						break;
					}
				}
				
				if (passed) {
					peaks.add(new Point(x, y));
					colocalizedPeakIndex.add(i);	
				}
				
		}
		return peaks;
	}
	
	@Override
	public void preview() {
		if (preview) {
			image.deleteRoi();
			transformROIs();
		}
	}
	
	public void transformROIs() {
		String[] columns = {
				"x_translation",
				"y_translation",
				"x_scaling",
				"y_scaling", 
				"rotation_angle"
		};	
		
		double[] trans = new double[columns.length];
		
		for (int i=0; i < columns.length;i++) {
			if (data_table.get(columns[i]) == null) {
				uiService.showDialog("The table provided does not have the correct format. It must have the columns: x_translation, y_translation, x_scaling, y_scaling, rotation_angle. The transformation parameters will be taken from the first row of each of these columns", "Wrong table format");
				return;
			} else {
				trans[i] = data_table.getValue(columns[i],0);
			}	
		}
		
		OriginalROIs.clear();
		TransformedROIs.clear();
		
		if (roiManager == null) {
			uiService.showDialog("No ROIs in Manager to transform");
			return;
		}
		
		int roiNum = roiManager.getCount();
		
		for (int i=0; i<roiNum; i++) {
			Roi roi = roiManager.getRoi(i);
			//Using getFloatBounds should work well with either points or boxes.
			//Here I remove the 0.5 offset, then transform and add it back below since the transformation matrix was calculated without the offset.
			double x = roi.getFloatBounds().x - 0.5;
			double y = roi.getFloatBounds().y - 0.5;
	        
			
			//Here we generate a new UID... This is in preparation for the Molecule Integrator
	        String baseRoiName = SDMMMath.getUUID58();
	        
	        String currentPosition = "LONG";
	        String newPosition = "SHORT";
	        
	        if (transformationDirection.equals("Long Wavelength to Short Wavelength")) {
	        	currentPosition = "LONG";
	        	newPosition = "SHORT";
	        } else if (transformationDirection.equals("Short Wavelength to Long Wavelength")) {
	        	currentPosition = "SHORT";
	        	newPosition = "LONG";
	        }
	        
	        Roi oldRoi = (Roi)roi.clone();
	        oldRoi.setName(baseRoiName + "_" + currentPosition);
	        OriginalROIs.add(oldRoi);
	     
	        Roi newRoi = (Roi)roi.clone();
	        newRoi.setLocation(trans[2]*Math.cos(trans[4])*x - trans[3]*Math.sin(trans[4])*y + trans[0] + 0.5, 
					trans[2]*Math.sin(trans[4])*x + trans[3]*Math.cos(trans[4])*y + trans[1] + 0.5);
	        newRoi.setName(baseRoiName + "_" + newPosition);
	        TransformedROIs.add(newRoi);
		}
		
		if (colocalize) {
			ArrayList<Point> peaks = findPeaks(image.getProcessor());
		
			if (preview) {
				Polygon poly = new Polygon();
				
				for (Point p: peaks)
					poly.addPoint(p.x, p.y);
				
				PointRoi peakRoi = new PointRoi(poly);
				image.setRoi(peakRoi);
			}
		} else {
			if (preview) {
				Polygon poly = new Polygon();
				
				for (int i=0;i<TransformedROIs.size();i++) {
					int x = (int)TransformedROIs.get(i).getFloatBounds().x;
			        int y = (int)TransformedROIs.get(i).getFloatBounds().y;
					poly.addPoint(x, y);
				}
				
				PointRoi peakRoi = new PointRoi(poly);
				image.setRoi(peakRoi);
			}
		}
	}
	
	/** Called when the {@link #preview} parameter value changes. */
	protected void previewChanged() {
		// When preview box is unchecked, reset the Roi back to how it was before...
		if (!preview) cancel();
	}
	
	@Override
	public void cancel() {
		image.deleteRoi();
	}
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Transformation Parameters", data_table.getName());
		builder.addParameter("Transformation Direction", transformationDirection);
		builder.addParameter("useDiscoidalAveragingFilter", String.valueOf(useDiscoidalAveragingFilter));
		builder.addParameter("DS_innerRadius", String.valueOf(DS_innerRadius));
		builder.addParameter("DS_innerRadius", String.valueOf(DS_innerRadius));
		builder.addParameter("Threshold", String.valueOf(threshold));
		builder.addParameter("filterOriginalRois", String.valueOf(filterOriginalRois));
		builder.addParameter("colocalizeRadius", String.valueOf(colocalizeRadius));
	}
	
	public void setROIManager(RoiManager roiManager) {
		this.roiManager = roiManager;
	}
	
	public RoiManager getROIManager() {
		return roiManager;
	}
	
	public void setImage(ImagePlus image) {
		this.image = image;
	}
	
	public ImagePlus getImage() {
		return image;
	}
	
	public void setTransformationParameters(SDMMResultsTable data_table) {
		this.data_table = data_table;
	}
	
	public SDMMResultsTable getTransformationParameters() {
		return data_table;
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
	
	public void setUseDiscoidalAveragingFilter(boolean useDiscoidalAveragingFilter) {
		this.useDiscoidalAveragingFilter = useDiscoidalAveragingFilter;
	}
	
	public boolean getUseDiscoidalAveragingFilter() {
		return useDiscoidalAveragingFilter;
	}
	
	public void setInnerRadius(int DS_innerRadius) {
		this.DS_innerRadius = DS_innerRadius;
	}
	
	public int getInnerRadius() {
		return DS_innerRadius;
	}
	
	public void setOuterRadius(int DS_outerRadius) {
		this.DS_outerRadius = DS_outerRadius;
	}
	
	public int getOuterRadius() {
		return DS_outerRadius;
	}
	
	public void setDetectionThreshold(int threshold) {
		this.threshold = threshold;
	}
	
	public int getDetectionThreshold() {
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
