package de.mpg.biochem.sdmm.RoiTools;

import java.awt.Rectangle;
import java.util.ArrayList;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

@Plugin(type = Command.class, label = "Convert PointRois to Box Trajectory", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "ROI Tools", weight = 30,
			mnemonic = 'r'),
		@Menu(label = "Convert PointRois to Box Trajectory", weight = 60, mnemonic = 'c')})
public class ConvertPointRoistoBoxTrajectoryCommand extends DynamicCommand implements Command {
	@Parameter
	private LogService logService;
	
	@Parameter
	private UIService uiService;
	
	@Parameter
	private RoiManager roiManager;
	
	@Parameter(label="Radius")
	private int radius = 5;

	@Override
	public void run() {
		Roi[] InputRois = roiManager.getRoisAsArray();
		
		ArrayList<Roi> CompleteTrack = new ArrayList<Roi>();
		
		//First we build the track that follows the center of the spot...
		int startSlice = InputRois[0].getPosition();
		int endSlice;
		
		CompleteTrack.add(InputRois[0]);
		for (int i=1;i<InputRois.length;i++) {
				endSlice = InputRois[i].getPosition();
				int sliceDiff = endSlice - startSlice;
				if (sliceDiff > 0) {
					double startX = InputRois[i-1].getBounds().x;
					double startY = InputRois[i-1].getBounds().y;
					double xDiff = InputRois[i].getBounds().x - startX;
					double yDiff = InputRois[i].getBounds().y - startY;
					for (int j=0; j <= sliceDiff; j++) {
						PointRoi pos = new PointRoi(InputRois[i-1].getBounds().x + j*xDiff/sliceDiff , InputRois[i-1].getBounds().y + j*yDiff/sliceDiff);
						pos.setPosition(startSlice + j);
						CompleteTrack.add(pos);
					}
				} else {
					CompleteTrack.add(InputRois[i]);
				}
				startSlice = InputRois[i].getPosition();
		}
		
		//Now we build bounding boxes for integration...
		roiManager.reset();
		
		int selectionWidth = radius * 2 + 1;
		
		for(int i=0;i<CompleteTrack.size();i++) {
			int boxCornerX = CompleteTrack.get(i).getBounds().x - radius;
			int boxCornerY = CompleteTrack.get(i).getBounds().y - radius;
			
			Rectangle r = new Rectangle(boxCornerX, boxCornerY, selectionWidth, selectionWidth);
			Roi roi = new Roi(r);
			roi.setPosition(CompleteTrack.get(i).getPosition());
				
			roi.setName("trajectory-" + CompleteTrack.get(i).getPosition());
			roiManager.addRoi(roi);
		}
	}
}
