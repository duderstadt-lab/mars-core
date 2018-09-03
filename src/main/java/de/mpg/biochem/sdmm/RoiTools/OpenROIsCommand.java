package de.mpg.biochem.sdmm.RoiTools;

import java.io.File;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

@Plugin(type = Command.class, label = "Open ROIs", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "ROI Tools", weight = 30,
			mnemonic = 'r'),
		@Menu(label = "Open ROIs", weight = 1, mnemonic = 'o')})
public class OpenROIsCommand extends DynamicCommand implements Command {
	@Parameter
	private LogService logService;
	
	@Parameter
	private UIService uiService;
	
	@Parameter(label="Choose input directory", style = "directory")
	private File directory;
	
	@Parameter(label="Vertical Tiles")
	private int vertical_tiles = 2;
	
	@Parameter(label="Vertical Tiles")
	private int horizontal_tiles = 2;

	@Override
	public void run() {
		ROITilesWindow tilewindow = new ROITilesWindow(directory.getAbsolutePath(), vertical_tiles, horizontal_tiles);
	}
}
