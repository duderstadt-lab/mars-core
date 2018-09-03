package de.mpg.biochem.sdmm.kcp;

import java.awt.AWTEvent;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import java.util.Random;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import net.imagej.ops.Initializable;

import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;

import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import de.mpg.biochem.sdmm.molecule.MoleculeArchive;
import de.mpg.biochem.sdmm.molecule.MoleculeArchiveService;
import de.mpg.biochem.sdmm.table.SDMMResultsTable;

import javax.swing.JCheckBox;

import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;

import static java.util.stream.Collectors.toList;

@Plugin(type = Command.class, headless = true, label = "Change Point Finder", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "KCP", weight = 30,
			mnemonic = 'k'),
		@Menu(label = "Segment Distribution Builder", weight = 10, mnemonic = 's')})
public class SegmentDistributionBuilderCommand extends DynamicCommand implements Command, Initializable {
	
	//GENERAL SERVICES NEEDED	
	@Parameter
	private LogService logService;
	
    @Parameter
    private StatusService statusService;
	
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
	@Parameter
    private UIService uiService;
	
    @Parameter(label="MoleculeArchive")
  	private MoleculeArchive archive;
    
    @Parameter(label="Segments", choices = {"a", "b", "c"})
	private String SegmentsTableName;
	
    @Parameter(label="Start")
	private double Dstart = 0;
    
    @Parameter(label="End")
	private double Dend = 0;
    
    @Parameter(label="Bins")
	private int bins;
	
	@Parameter(label = "Distribution type:",
			style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = { "Rate (Gaussian)",
					"Rate (Histogram)", "Duration", "Processivity (molecule)", "Processivity (region)" })
	private String distType;
	
	@Parameter(label="Only regions with rates")
	private boolean filter = false;
	
	@Parameter(label="from")
	private double filter_region_start = 0;
	
	@Parameter(label="to")
	private double filter_region_stop = 100;
	
	@Parameter(label="Bootstrapping:",
			style = ChoiceWidget.RADIO_BUTTON_VERTICAL_STYLE, choices = { "None",
					"Segments", "Molecules" })
	private String bootstrappingType;
	
	@Parameter(label="Number of cycles")
	private int bootstrap_cycles = 100;
	
	@Parameter(label = "Include:",
			style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "All",
					"Tagged with", "Untagged" })
	private String include;
	
	@Parameter(label="Tags (comma separated list)")
	private String tags = "";
	
    @Parameter(label="Distribution", type = ItemIO.OUTPUT)
    private SDMMResultsTable results;

	@Override
	public void initialize() {
		final MutableModuleItem<String> SegmentTableNames = getInfo().getMutableInput("SegmentsTableName", String.class);
		SegmentTableNames.setChoices(moleculeArchiveService.getSegmentTableNames());
	}
	
	@Override
	public void run() {
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.lockArchive();
		
		//Build Collection of UIDs based on tags if they exist...
        ArrayList<String> UIDs;
		if (include.equals("Tagged with")) {
			//First we parse tags to make a list...
	        String[] tagList = tags.split(",");
	        for (int i=0; i<tagList.length; i++) {
	        	tagList[i] = tagList[i].trim();
	        }
			
			UIDs = (ArrayList<String>)archive.getMoleculeUIDs().stream().filter(UID -> {
				boolean hasTag = false;
				for (int i=0; i<tagList.length; i++) {
		        	for (String tag : archive.get(UID).getTags()) {
		        		if (tagList[i].equals(tag))
		        			hasTag = true;
		        	}
		        }
				return hasTag;
			}).collect(toList());
		} else if (include.equals("Untagged")) {
			UIDs = (ArrayList<String>)archive.getMoleculeUIDs().stream().filter(UID -> archive.get(UID).hasNoTags()).collect(toList());
		} else {
			//  we include All molecules...
			UIDs = archive.getMoleculeUIDs();
		}
		
		SegmentDistributionBuilder distBuilder = new SegmentDistributionBuilder(archive, UIDs, SegmentsTableName, Dstart, Dend, bins, logService);
		
		//Configure segment builder
		if (filter) {
			distBuilder.setFilter(filter_region_start, filter_region_stop);
		}
		
		if (bootstrappingType.equals("Segments")) {
			distBuilder.bootstrapSegments(bootstrap_cycles);
		} else if (bootstrappingType.equals("Molecules")) {
			distBuilder.bootstrapMolecules(bootstrap_cycles);
		}
		
		//Build desired distribution
		if (distType.equals("Rate (Gaussian)")) {
			results = distBuilder.buildRateGaussian();
			results.setName("Rate (Gaussian)");
		} else if (distType.equals("Rate (Histogram)")) {
			results = distBuilder.buildRateHistogram();
			results.setName("Rate (Histogram)");
		} else if (distType.equals("Duration")) {
			results = distBuilder.buildDurationHistogram();
			results.setName("Duration Distribution");
		} else if (distType.equals("Processivity (molecule)")) {
			results = distBuilder.buildProcessivityByMoleculeHistogram();
			results.setName("Processivity Distribution (molecule)");
		} else if (distType.equals("Processivity (region)")) {
			results = distBuilder.buildProcessivityByRegionHistogram();
			results.setName("Processivity Distribution (region)");
		}
		
		getInfo().getOutput("results", SDMMResultsTable.class).setLabel(results.getName());
		
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless())
			archive.unlockArchive();
	}
}