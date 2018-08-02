package de.mpg.biochem.sdmm.molecule;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.ScrollPane;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;

import javax.swing.AbstractListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;

import de.mpg.biochem.sdmm.plot.CurvePlot;
import de.mpg.biochem.sdmm.plot.PlotDialog;
import de.mpg.biochem.sdmm.plot.PlotProperties;
import de.mpg.biochem.sdmm.table.ResultsTableService;
import de.mpg.biochem.sdmm.table.SDMMResultsTable;
import ij.gui.GenericDialog;
import ij.plugin.frame.Recorder;

public class MoleculeArchiveWindow {
	
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
    @Parameter
    private UIService uiService;

    private MoleculeArchive archive;
	
	private JFrame frame;
	
	private JTabbedPane tabbedPane = new JTabbedPane();
	private JPanel propertiesTab;
	
	private ImageMetaDataPanel imageMetaDataPanel;
	
	private MoleculePanel moleculePanel;
	
	//Comments Tab Components
	private JScrollPane commentsTab;
	private JTextArea comments;
	
	//Log Tab Components
	private JScrollPane logTab;
	private JTextArea log;
	
	private JMenuItem saveMenuItem = new JMenuItem("Save");
	private JMenuItem saveAsMenuItem = new JMenuItem("Save As");
	
	//private JMenuItem renameMenuItem = new JMenuItem("Rename");
	
	private JMenuItem addMetaDataMenuItem = new JMenuItem("Add ImageMetaData");
	
	private JMenuItem singleCurveMenuItem = new JMenuItem("Single Curve");
	private JMenuItem multiCurveMenuItem = new JMenuItem("Multiple Curves");
	private JMenuItem multiPlotMenuItem = new JMenuItem("Multiple Plots");
	
	//static so that window locations are offset...
	static int pos_x = 100;
	static int pos_y = 130;
	static int offsetX = 0;

	public MoleculeArchiveWindow(MoleculeArchiveService moleculeArchiveService) {
		this.moleculeArchiveService = moleculeArchiveService;
		this.uiService = moleculeArchiveService.getUIService();
	}
	
	public MoleculeArchiveWindow(String name, MoleculeArchive archive, MoleculeArchiveService moleculeArchiveService) {
		this.archive = archive;
		this.moleculeArchiveService = moleculeArchiveService;
		this.uiService = moleculeArchiveService.getUIService();

	    UIManager.put("Label.font", new Font("Menlo", Font.PLAIN, 12));
		
		createFrame(name);
	}
	
	public void update() {
		comments.setText(archive.getComments());
		log.setText(archive.getLog());
	}
	
	private void createFrame(String title) {
		
		propertiesTab = archiveProperties();
		tabbedPane.addTab("Properties", propertiesTab);
		
		imageMetaDataPanel = new ImageMetaDataPanel(archive);
		tabbedPane.addTab("ImageMetaData", imageMetaDataPanel);
		
		moleculePanel = new MoleculePanel(archive);
		tabbedPane.addTab("Molecules", moleculePanel);
		
		commentsTab = makeCommentsTab();
		tabbedPane.addTab("Comments", commentsTab);
		
		logTab = makeLogTab();
		tabbedPane.addTab("Log", logTab);
		
		tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		
		JMenu fileMenu = new JMenu("File");
		JMenuBar mb = new JMenuBar();
		mb.add(fileMenu);
		fileMenu.add(saveMenuItem);
		fileMenu.add(saveAsMenuItem);
		saveMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 archive.set(moleculePanel.getMolecule());
	 			 if (archive.getFile() != null) {
	 				archive.save();
	 			 } else {
	 				File file = uiService.chooseFile(archive.getFile(), FileWidget.SAVE_STYLE);
	 				archive.saveAs(file);
	 			 }
	          }
	       });
		saveAsMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 //Add yama ending if not there ??
	        	 File file = uiService.chooseFile(archive.getFile(), FileWidget.SAVE_STYLE);
	        	 if (file != null) {
		 			 archive.set(moleculePanel.getMolecule());
		 			 archive.saveAs(file);
	        	 }
	          }
	       });
		
		
		JMenu toolsMenu = new JMenu("Tools");
		mb.add(toolsMenu);
		toolsMenu.add(addMetaDataMenuItem);
		addMetaDataMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 //File imageFolder = uiService.chooseFile(archive.getFile(), FileWidget.DIRECTORY_STYLE);
	 			 //archive.addImageMetaData(new ImageMetaData(imageFolder, moleculeArchiveService, "Odin"));
	          }
	       });
		toolsMenu.add(singleCurveMenuItem);
		singleCurveMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	PlotDialog dialog = new PlotDialog("Curve Plot", archive.get(0).getDataTable(), 1);
	        	dialog.showDialog();
	        	if (dialog.wasCanceled())
	     			return;
	        	
	        	//Need to put this so the final values and properly
	        	dialog.update(dialog);
	        	ArrayList<PlotProperties> props = new ArrayList<PlotProperties>();
	        	PlotProperties curve1 = new PlotProperties("Curve", dialog.getXColumnName(), dialog.getNextYColumnName(), dialog.getNextCurveColor(), dialog.getCurveType(), dialog.getNextSegmentCurveColor(), false);
	        	props.add(curve1);
	        	 
	        	moleculePanel.addPlot(props);
	        	 
	          }
	       });
		
		toolsMenu.add(multiCurveMenuItem);
		multiCurveMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 //First we ask how many curves will be added
	        	 GenericDialog Numdialog = new GenericDialog("Multicurve Plot");
	     		 Numdialog.addNumericField("Number_of_curves", 2, 0);
	     		 Numdialog.showDialog();
	     		
	     		 if (Numdialog.wasCanceled())
	     			return;
	     		
	     		 int curveNum = (int)Numdialog.getNextNumber(); 
	        	 
	        	 PlotDialog dialog = new PlotDialog("Curve Plot", archive.get(0).getDataTable(), curveNum);
	        	 dialog.showDialog();
	        	 //Need to put this so the final values and properly
	        	 dialog.update(dialog);
	        	 ArrayList<PlotProperties> props = new ArrayList<PlotProperties>();
	        	 //Need to add None options for segments curve and then use inputs below and above
	        	 
	        	 for (int i=0;i<curveNum;i++) {
	        		 String yName = dialog.getNextYColumnName();
	        		 props.add(new PlotProperties(yName + " " + dialog.getXColumnName(), dialog.getXColumnName(), yName, dialog.getNextCurveColor(), dialog.getCurveType(), dialog.getNextSegmentCurveColor(), false));
	        	 }
	        	 moleculePanel.addPlot(props);
	          }
	       });
		
		toolsMenu.add(multiPlotMenuItem);
		multiPlotMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 //plotPanel =
	          }
	       });


		frame = new JFrame(title);
		frame.setLocation(pos_x, pos_y);
		pos_x += 10;
 		pos_y += 30;
 		if (pos_y > 600) {
 			offsetX += 200;
 			pos_x = offsetX;
 			pos_y = 130;
 		} else if (pos_x > 1000) {
 			offsetX = 0;
 			pos_x = 100;
 			pos_y = 130;
 		}
 		frame.addWindowListener(new WindowAdapter()
 	      {
 	         public void windowClosing(WindowEvent e)
 	         {
 	           close();
 	         }
 	      });
		frame.setLayout(new BorderLayout());
		frame.add(tabbedPane, BorderLayout.CENTER);
		frame.setJMenuBar(mb);
		frame.pack();
		frame.setSize(800, 500);
		frame.setVisible(true);	
	}
	
	protected JPanel archiveProperties() {
		JPanel panel = new JPanel(false);
		panel.setLayout(new GridBagLayout());
		
		GridBagConstraints gbc = new GridBagConstraints();
		
		gbc.anchor = GridBagConstraints.NORTHWEST;
		
		gbc.insets = new Insets(10, 100, 10, 100);
		
		gbc.weightx = 1;
		gbc.weighty = 0;
		
		gbc.gridx = 0;
		gbc.gridy = 0;	
		panel.add(new JLabel("                                   "), gbc);
		
		gbc.gridy += 1;
		panel.add(new JLabel("                                   "), gbc);
		
		gbc.gridy += 1;
		panel.add(new JLabel("Archive Name                       " + archive.getName()), gbc);
		
		gbc.gridy += 1;
		panel.add(new JLabel("Number of Molecules                " + archive.getNumberOfMolecules()), gbc);
		
		gbc.gridy += 1;
		panel.add(new JLabel("Average Molecule Size (in bytes)   " + archive.getAverageMoleculeSize()), gbc);
		
		gbc.gridy += 1;
		panel.add(new JLabel("Number of Image MetaData Items     " + archive.getNumberOfImageMetaDataItems()), gbc);
		
		gbc.gridy += 1;
		panel.add(new JLabel("                                   "), gbc);
		
		if (archive.isVirtual()) {
			gbc.gridy += 1;
			panel.add(new JLabel("Working from the virtual memory store: "), gbc);
			
			gbc.gridy += 1;
			JTextField archiveStorePathName = new JTextField(archive.getStoreLocation());
			archiveStorePathName.setFont(new Font("Menlo", Font.PLAIN, 12));
			archiveStorePathName.setEditable(false);
			archiveStorePathName.setBackground(null);
			panel.add(archiveStorePathName, gbc);
		} else {
			gbc.gridy += 1;
			panel.add(new JLabel("This archive is stored in normal memory."), gbc);
		}
		
		gbc.gridy += 1;
		panel.add(new JLabel("                                   "), gbc);
		
		gbc.gridy += 1;
		panel.add(new JLabel("                                   "), gbc);
		
		JPanel northPanel = new JPanel();
		northPanel.setLayout(new GridBagLayout());	
		GridBagConstraints northGBC = new GridBagConstraints();
		northGBC.anchor = GridBagConstraints.NORTHWEST;
		
		northGBC.weightx = 1;
		northGBC.weighty = 1;
		
		gbc.gridx = 0;
		gbc.gridy = 0;
		
		northPanel.add(panel, northGBC);
		
		return northPanel;
	}
	
	protected JComponent makeTextPanel(String text) {
        JPanel panel = new JPanel(false);
        JLabel filler = new JLabel(text);
        filler.setHorizontalAlignment(JLabel.CENTER);
        panel.setLayout(new GridLayout(1, 1));
        panel.add(filler);
        return panel;
    }
 	
	private JScrollPane makeCommentsTab() {
		comments = new JTextArea(archive.getComments());
		comments.getDocument().addDocumentListener(
		        new DocumentListener() {
		            public void changedUpdate(DocumentEvent e) {
		                archive.setComments(comments.getText());
		            }
		            public void insertUpdate(DocumentEvent e) {
		            	archive.setComments(comments.getText());
		            }
		            public void removeUpdate(DocumentEvent e) {
		            	archive.setComments(comments.getText());
		            }
		        });
		
		comments.setFont(new Font("Menlo", Font.PLAIN, 12));
        JScrollPane pane = new JScrollPane(comments);
		return pane;
	}

	private JScrollPane makeLogTab() {
		log = new JTextArea(archive.getLog());
		log.setFont(new Font("Menlo", Font.PLAIN, 12));
		log.setEditable(false);
        JScrollPane pane = new JScrollPane(log);
		return pane;
	}
	
	public MoleculeArchive getArchive() {
		return archive;
	}
	
	public void rename(String name) {
		if (name != null) {

			moleculeArchiveService.rename(frame.getTitle(), name);
			frame.setTitle(name);
		}
	}
	
	public void close() {
		frame.setVisible(false);
		frame.dispose();
		
		moleculeArchiveService.removeArchive(frame.getTitle());
	}
	
	public void setArchiveService(MoleculeArchiveService moleculeArchiveService) {
		this.moleculeArchiveService = moleculeArchiveService;
	}
	
	public void setUIService(UIService uiService) {
		this.uiService = uiService;
	}
}
