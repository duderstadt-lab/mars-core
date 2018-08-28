package de.mpg.biochem.sdmm.kcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.decimal4j.util.DoubleRounder;
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

import de.mpg.biochem.sdmm.molecule.Molecule;
import de.mpg.biochem.sdmm.molecule.MoleculeArchive;
import de.mpg.biochem.sdmm.molecule.MoleculeArchiveService;
import de.mpg.biochem.sdmm.table.*;
import de.mpg.biochem.sdmm.util.LogBuilder;
import net.imagej.ops.Initializable;
import net.imagej.table.DoubleColumn;

@Plugin(type = Command.class, headless = true, label = "Change Point Finder", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "SDMM Plugins", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "KCP", weight = 30,
			mnemonic = 'm'),
		@Menu(label = "Change Point Finder", weight = 1, mnemonic = 'f')})
public class KCPCommand extends DynamicCommand implements Command, Initializable {
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
    
    @Parameter(label="X Column", choices = {"a", "b", "c"})
	private String Xcolumn;
    
    @Parameter(label="Y Column", choices = {"a", "b", "c"})
	private String Ycolumn;
    
    @Parameter(label="Confidence value")
	private double confidenceLevel = 0.99;
    
    @Parameter(label="Global sigma")
	private double global_sigma = 1;
    
    @Parameter(label="Fit steps (zero slope)")
	private boolean step_analysis = false;
    
    //Global variables
    //For the progress thread
  	private final AtomicBoolean progressUpdating = new AtomicBoolean(true);
  	private final AtomicInteger numFinished = new AtomicInteger(0);
	
    @Override
	public void initialize() {
		final MutableModuleItem<String> XcolumnItems = getInfo().getMutableInput("Xcolumn", String.class);
		XcolumnItems.setChoices(moleculeArchiveService.getColumnNames());
		
		final MutableModuleItem<String> YcolumnItems = getInfo().getMutableInput("Ycolumn", String.class);
		YcolumnItems.setChoices(moleculeArchiveService.getColumnNames());
	}
	@Override
	public void run() {		
		//Lock the window so it can't be changed while processing
		if (!uiService.isHeadless())
			archive.getWindow().lockArchive();
		
		//Build log message
		LogBuilder builder = new LogBuilder();
		
		String log = builder.buildTitleBlock("Change Point Finder");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Let's build a thread pool and in a multithreaded manner perform changepoint analysis on all molecules
		//Need to determine the number of threads
		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
		
		//Output first part of log message...
		logService.info(log);
		
		double starttime = System.currentTimeMillis();
		logService.info("Finding Change Points...");
	    try {
	    	//Start a thread to keep track of the progress of the number of frames that have been processed.
	    	//Waiting call back to update the progress bar!!
	    	Thread progressThread = new Thread() {
	            public synchronized void run() {
                    try {
        		        while(progressUpdating.get()) {
        		        	Thread.sleep(100);
        		        	statusService.showStatus(numFinished.intValue(), archive.getNumberOfMolecules(), "Finding Change Points for " + archive.getName());
        		        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
	            }
	        };

	        progressThread.start();
	        
	        //This will spawn a bunch of threads that will analyze molecules individually in parallel 
	        //and put the changepoint tables back into the same molecule record
	        
	        forkJoinPool.submit(() -> IntStream.range(0, archive.getNumberOfMolecules()).parallel().forEach(i -> {
	        		Molecule molecule = archive.get(i);
	        		
	        		findChangePoints(molecule);
	        		
	        		archive.set(molecule);
	        })).get();
	        
	        progressUpdating.set(false);
	        
	        statusService.showProgress(100, 100);
	        statusService.showStatus("Change point search for archive " + archive.getName() + " - Done!");
	        
	    } catch (InterruptedException | ExecutionException e) {
	        // handle exceptions
	    	logService.error(e.getMessage());
	    	e.printStackTrace();
			logService.info(builder.endBlock(false));
			forkJoinPool.shutdown();
			return;
	    } finally {
	        forkJoinPool.shutdown();
	    }
		
	    logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	    logService.info(builder.endBlock(true));
	    
		//Unlock the window so it can be changed
	    if (!uiService.isHeadless()) {
	    	archive.getWindow().updateAll();
			archive.getWindow().unlockArchive();
		}
	}
	
	private void findChangePoints(Molecule molecule) {
		SDMMResultsTable datatable = molecule.getDataTable();
		int offset = 0;
		int length = datatable.getRowCount();
		
		int SigXstart = 0;
		int SigXend = datatable.getRowCount();
		
		double[] xData = datatable.getColumnAsDoubles(Xcolumn);
		double[] yData = datatable.getColumnAsDoubles(Ycolumn);
		
		for (int j=0;j<datatable.getRowCount();j++) {
			if (molecule.hasParameter("start") && xData[j] <= molecule.getParameter("start")) {
				offset = j;
			} else if (molecule.hasParameter("end") && xData[j] <= molecule.getParameter("end")) {
				length = j - offset;
			}
			
			if (molecule.hasParameter("bg_start") && xData[j] <= molecule.getParameter("bg_start")) {
				SigXstart = j;
			} else if (molecule.hasParameter("bg_end") && xData[j] <= molecule.getParameter("bg_end")) {
				SigXend = j;
			}
		}
		
		//Use global sigma or use local sigma or calculate sigma (in this order of priority)
		double sigma = global_sigma;
		if (molecule.hasParameter("sigma")) {
			sigma = molecule.getParameter("sigma");
		} else if (molecule.hasParameter("bg_start") || molecule.hasParameter("bg_end")) {
			sigma = KCP.calc_sigma(yData, SigXstart, SigXend);
		}
		
		double[] xRegion = Arrays.copyOfRange(xData, offset, offset + length);
		double[] yRegion = Arrays.copyOfRange(yData, offset, offset + length);
		KCP change = new KCP(sigma, confidenceLevel, xRegion, yRegion, step_analysis);
		molecule.setSegmentsTable(Ycolumn, Xcolumn, buildSegmentTable(change.generate_segments()));
		numFinished.incrementAndGet();
	}
	
	private SDMMResultsTable buildSegmentTable(ArrayList<Segment> segments) {
		SDMMResultsTable output = new SDMMResultsTable();
		
		//Do i need to add these columns first? I can't remember...
		output.add(new DoubleColumn("x1"));
		output.add(new DoubleColumn("y1"));
		output.add(new DoubleColumn("x2"));
		output.add(new DoubleColumn("y2"));
		output.add(new DoubleColumn("A"));
		output.add(new DoubleColumn("sigma_A"));
		output.add(new DoubleColumn("B"));
		output.add(new DoubleColumn("sigma_B"));
		
		int row = 0;
		for (Segment seg : segments) {
			output.appendRow();
			output.setValue("x1", row, seg.x1);
			output.setValue("y1", row, seg.y1);
			output.setValue("x2", row, seg.x2);
			output.setValue("y2", row, seg.y2);
			output.setValue("A", row, seg.A);
			output.setValue("sigma_A", row, seg.A_sigma);
			output.setValue("B", row, seg.B);
			output.setValue("sigma_B", row, seg.B_sigma);
			row++;
		}
		return output;
	}
	
	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("X Column", Xcolumn);
		builder.addParameter("Y Column", Ycolumn);
		builder.addParameter("Confidence value", String.valueOf(confidenceLevel));
		builder.addParameter("Global sigma", String.valueOf(global_sigma));
		builder.addParameter("Fit steps (zero slope)", String.valueOf(step_analysis));
	}
}
