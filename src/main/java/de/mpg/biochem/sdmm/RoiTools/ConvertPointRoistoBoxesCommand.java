package de.mpg.biochem.sdmm.RoiTools;

import java.awt.Rectangle;
import java.util.ArrayList;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

@Plugin(type = Command.class, label = "Convert PointRois to Boxes", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "ROI Tools", weight = 30,
			mnemonic = 'r'),
		@Menu(label = "Convert PointRois to Boxes", weight = 50, mnemonic = 'c')})
public class ConvertPointRoistoBoxesCommand extends DynamicCommand implements Command {
	@Parameter
	private LogService logService;
	
	@Parameter
	private UIService uiService;
	
	@Parameter
	private RoiManager roiManager;
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String tableTypeMessage =
			"ROI dimensions - position (x0, y0) relative to peak x and y positions.";
	
	@Parameter(label="x0")
	private int x0 = -6;
	
	@Parameter(label="y0")
	private int y0 = -6;

	@Parameter(label="width")
	private int width = 12;
	
	@Parameter(label="height")
	private int height = 12;

	@Override
	public void run() {
		Roi[] rois = roiManager.getRoisAsArray();
		
		ArrayList<Roi> boxRois = new ArrayList<Roi>();

		for (int i = 0; i < rois.length; i++) {
			Rectangle r = new Rectangle((int)(rois[i].getFloatBounds().x + x0), (int)(rois[i].getFloatBounds().y + y0), width, height);
			Roi roi = new Roi(r);
			roi.setName(rois[i].getName());
			roi.setPosition(rois[i].getPosition());
			boxRois.add(roi);
		}		
		
		//reset RoiManger and add points.
		roiManager.reset();
		for (int i=0;i<boxRois.size();i++) {
			roiManager.addRoi(boxRois.get(i));
		}
		
		//Reset the image in case the Rois were displayed to reflect updates.
		//ImagePlus image = WindowManager.getCurrentImage();
		//image.updateAndRepaintWindow();
	}
}
