package de.mpg.biochem.sdmm.molecule;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Vector;

import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import de.mpg.biochem.sdmm.util.LogBuilder;
import net.imagej.ops.Initializable;

import javax.swing.JLabel;

@Plugin(type = Command.class, menuPath = "Plugins>SDMM Plugins>Molecule Utils>Generate bps and Time")
public class GenerateBPSCommand extends DynamicCommand implements Command, Initializable {
	
	@Parameter
	private LogService logService;
	
    @Parameter
    private StatusService statusService;
	
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
	@Parameter
    private UIService uiService;
    
    @Parameter(label="MoleculeArchive", choices = {"a", "b", "c"})
	private String archiveName;
    
    @Parameter(label="X Column", choices = {"a", "b", "c"})
	private String Xcolumn;
    
    @Parameter(label="Y Column", choices = {"a", "b", "c"})
	private String Ycolumn;
	
	@Parameter(label="um per pixel")
	private double um_per_pixel = 1.56;
	
	//@Parameter(label="seconds per slice")
	//private double seconds_per_slice = 0.5;
	
	@Parameter(label="global bps per um")
	private double global_bps_per_um = 3000;
	
	@Parameter(label = "Use", style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "Reversal", "Region"})
	private String conversionType = "Reversal";
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String ReversalRegionsMessage =
		"If using reversal regions:";
	
	@Parameter(label="Reverse flow start")
	private int rf_start = 150;
	
	@Parameter(label="Reverse flow end")
	private int rf_end = 250;
	
	@Parameter(label="Forward flow start")
	private int ff_start = 0;
	
	@Parameter(label="Forward flow end")
	private int ff_end = 100;

	@Parameter(label="FlowStart")
	private int f_start = 300;
	
	@Parameter(label="FlowEnd")
	private int f_end = 400;
	
	@Parameter(label="Bead radius (um)")
	private double bead_radius = 0.5;
	
	@Parameter(label = "Conversion type", style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "Global", "By molecule"})
	private String globalorMol = "global";
	
	@Parameter(label="DNA_length_in_bps")
	private double DNA_length_bps = 20666;
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String backgroundRegionsMessage = "If using background region:";

	@Parameter(label="Start")
	private int bg_start = 1;
	
	@Parameter(label="End")
	private int bg_end = 500;
	
	@Parameter(label="Output Column")
	private String distance_column_name = "bps";
	
	@Override
	public void initialize() {
    	final MutableModuleItem<String> tableItems = getInfo().getMutableInput("archiveName", String.class);
		tableItems.setChoices(moleculeArchiveService.getArchiveNames());
		
		final MutableModuleItem<String> XcolumnItems = getInfo().getMutableInput("Xcolumn", String.class);
		XcolumnItems.setChoices(moleculeArchiveService.getColumnNames());
		
		final MutableModuleItem<String> YcolumnItems = getInfo().getMutableInput("Ycolumn", String.class);
		YcolumnItems.setChoices(moleculeArchiveService.getColumnNames());
	}
	
	@Override
	public void run() {		
		MoleculeArchive archive = moleculeArchiveService.getArchive(archiveName);
		
		//Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Generate bps");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		archive.addLogMessage(log);
		
		if (conversionType.equals("Reversal")) {
			//Loop through each molecule and extract the start and end regions for reversal
			archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
				Molecule molecule = archive.get(UID);
				
				double ff_mean = molecule.getDataTable().mean(Ycolumn, Xcolumn, ff_start, ff_end);
				double rf_mean = molecule.getDataTable().mean(Ycolumn, Xcolumn, rf_start, rf_end);
				double f_mean = molecule.getDataTable().mean(Ycolumn, Xcolumn, f_start, f_end);
				
				//Let's switch rf and ff if the camera orientation is opposite to make sure the math still works out...
				boolean cameraFlipped = false;
				if (ff_mean > rf_mean) {
					cameraFlipped = true;
					double tmp_ff_mean = ff_mean;
					ff_mean = rf_mean;
					rf_mean = tmp_ff_mean;
				}
				//This is the attachment_Position in raw yColumn values
				double attachment_Position = ff_mean + (rf_mean - ff_mean)/2;
				double mol_bps_per_um;
				if (!globalorMol.equals("global"))
					mol_bps_per_um = DNA_length_bps / (Math.abs(attachment_Position - f_mean)*um_per_pixel - bead_radius);
				else 
					mol_bps_per_um = global_bps_per_um;
				
				//Add corrected distance to the table in a column with name distance_column_name
				molecule.getDataTable().appendColumn(distance_column_name);
				
				for (int j=0; j< molecule.getDataTable().getRowCount(); j++) {
					double output = (molecule.getDataTable().get(Ycolumn, j) - attachment_Position)*um_per_pixel;
					if (output > 0)
						output = (output - bead_radius)*mol_bps_per_um;
					else if (output < 0)
						output = (output + bead_radius)*mol_bps_per_um;
					else 
						output = output*mol_bps_per_um;
						
					if (!cameraFlipped)
						output *= -1;
					
					molecule.getDataTable().setValue(distance_column_name, j, output);
				}
				
				archive.set(molecule);
			});
		} else if (conversionType.equals("Region")) {
			
			//We loop through all molecules, find mean in background region, subtract it
			//and transform to correct units...
			archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
				Molecule molecule = archive.get(UID);
				
				//First we set to global start and end for the region
				//Then if the molecule has parameters those override the global values
				int tab_bg_start = bg_start;
				int tab_bg_end = bg_end;
				if (molecule.hasParameter("bg_start") && molecule.hasParameter("bg_end")) {
					tab_bg_start = (int)molecule.getParameter("bg_start");
					tab_bg_end = (int)molecule.getParameter("bg_end");
				}
				
				double mean_background = molecule.getDataTable().mean(Ycolumn, Xcolumn, tab_bg_start, tab_bg_end);
				
				for (int j = 0; j <= molecule.getDataTable().getRowCount(); j++) {
					double bps = (molecule.getDataTable().getValue(Ycolumn, j) - mean_background)*um_per_pixel*global_bps_per_um;
					molecule.getDataTable().setValue(distance_column_name, j, bps);
				}
				
				archive.set(molecule);
			});
		}
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(builder.endBlock(true));
	    archive.addLogMessage(builder.endBlock(true));
	}	
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archiveName);
		builder.addParameter("X Column", Xcolumn);
		builder.addParameter("Y Column", Ycolumn);
		builder.addParameter("um per pixel", String.valueOf(um_per_pixel));
		builder.addParameter("global bps per um", String.valueOf(global_bps_per_um));
		builder.addParameter("Use", conversionType);
		builder.addParameter("Reverse flow start", String.valueOf(rf_start));
		builder.addParameter("Reverse flow end", String.valueOf(rf_end));	
		builder.addParameter("Forward flow start", String.valueOf(ff_start));
		builder.addParameter("Forward flow end", String.valueOf(ff_end));
		builder.addParameter("FlowStart", String.valueOf(f_start));
		builder.addParameter("FlowEnd", String.valueOf(f_end));
		builder.addParameter("Bead radius (um)", String.valueOf(bead_radius));
		builder.addParameter("Conversion type", globalorMol);
		builder.addParameter("DNA_length_in_bps", String.valueOf(DNA_length_bps));
		builder.addParameter("Start", String.valueOf(bg_start));
		builder.addParameter("End", String.valueOf(bg_end));
		builder.addParameter("Output Column", distance_column_name);
	}
}