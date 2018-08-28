package de.mpg.biochem.sdmm.table;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.UIService;

//Can we just make this a module so the services are automatically populated???
public class TableDropRunner implements DropTargetListener, Runnable {
	
	@Parameter
	private ResultsTableService resultsTableService;
	
	@Parameter
    private UIService uiService;
    
	@Parameter
    private StatusService statusService;
    
	@Parameter
    private LogService logService;
	
	boolean done = false;
	
	private ArrayList<File> tables = new ArrayList<File>();
	
	private JFrame frame;
	private JLabel label = new JLabel("Drop ResultsTable Files Here", JLabel.CENTER);
	JPanel pane = new JPanel(new GridLayout(1,1), false);
	
	public TableDropRunner() {}
	
	public void run() {
		frame = new JFrame("Drop & Drop Window");
		frame.setSize(300, 150);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setLayout(new GridLayout(1,0));
		label.setFont(new Font("Arial", Font.PLAIN, 16));
		pane.add(label);
		frame.add(pane);
		frame.setVisible(true);	
		frame.setBackground(Color.lightGray);
		new DropTarget(frame, this);
	    frame.setVisible(true);
		//use a thread so that different indicators are updating during table opening operation.
	    	synchronized(this) {
		        while (!done) {
	                try {wait(1000);}
	                catch(InterruptedException e) {}
		        	if (tables.size()>0)
	                	openTables();
		        }
	    	}
    }

	public void close() {
        done = true;
        synchronized(this) {
            notify();
        }
    }
	
	public synchronized void openTables() {
		ArrayList<String> tableNames = resultsTableService.getTableNames();
		boolean open = true;
		for (int j=0;j<tables.size();j++) {
			File file = tables.get(j);
			String title = file.getName();
         	for (String tableName: tableNames) {
         		if (tableName.equals(title)) {
         			uiService.showDialog("A file with the name " + title + " is already open.", MessageType.ERROR_MESSAGE);
         			pane.setBorder(BorderFactory.createEmptyBorder());
         			open = false;
         		}
         	}
         	if (open) { 	
         		ResultsTableOpenerCommand opener = new ResultsTableOpenerCommand();
         		opener.setStatusService(statusService);
         		opener.setTableService(resultsTableService);
         		opener.setUIService(uiService);
         		opener.setLogService(logService);
         		SDMMResultsTable results = opener.open(file.getAbsolutePath());
         		uiService.show(title, results);
         	} else {
         		open = true;
         	}
        }
		tables.clear();
	}
	
	public void drop(DropTargetDropEvent evt) {
        try {
            evt.acceptDrop(DnDConstants.ACTION_COPY);
            @SuppressWarnings("unchecked")
			List<File> droppedFiles = (List<File>) evt
                    .getTransferable().getTransferData(
                            DataFlavor.javaFileListFlavor);
            for (File file : droppedFiles) {
    			tables.add(file);
    		}
            //notify();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        pane.setBorder(BorderFactory.createEmptyBorder());
        evt.dropComplete(true);
    } 
	
	public void dragEnter(DropTargetDragEvent evt) {
		pane.setBorder(BorderFactory.createMatteBorder(5, 5, 5, 5, Color.GREEN));
	}
	
	public void dragOver(DropTargetDragEvent evt) {
	
	}
	
	public void dragExit(DropTargetEvent evt) {
		pane.setBorder(BorderFactory.createEmptyBorder());
	}
	
	public void dropActionChanged(DropTargetDragEvent evt) {
	
	}
	
	public void dragDropEnd(DragSourceDropEvent evt) {
	
	}
	
	//Utility methods to set Parameters not initialized...
	public void setTableService(ResultsTableService resultsTableService) {
		this.resultsTableService = resultsTableService;
	}
	
	public void setUIService(UIService uiService) {
		this.uiService = uiService;
	}
	
	public void setStatusService(StatusService statusService) {
		this.statusService = statusService;
	}
	
	public void setLogService(LogService logService) {
		this.logService = logService;
	}
}
