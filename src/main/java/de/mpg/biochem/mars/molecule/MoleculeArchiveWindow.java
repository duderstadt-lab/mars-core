/*******************************************************************************
 * MARS - MoleculeArchive Suite - A collection of ImageJ2 commands for single-molecule analysis.
 * 
 * Copyright (C) 2018 - 2019 Karl Duderstadt
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.mpg.biochem.mars.molecule;

import static java.util.stream.Collectors.toList;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
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
import java.util.HashMap;
import java.util.HashSet;

import javax.swing.AbstractListModel;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.PrefService;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;

import de.mpg.biochem.mars.plot.CurvePlot;
import de.mpg.biochem.mars.plot.PlotDialog;
import de.mpg.biochem.mars.plot.PlotProperties;
import de.mpg.biochem.mars.table.ResultsTableService;
import de.mpg.biochem.mars.table.MARSResultsTable;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.frame.Recorder;

public class MoleculeArchiveWindow {
	
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
    @Parameter
    private UIService uiService;
    
    @Parameter
    private PrefService prefService;

    private MoleculeArchive archive;
	
    private boolean lockArchive = false;
	private JFrame frame;
	
	private JTabbedPane tabbedPane = new JTabbedPane();
	private JPanel propertiesTab;
	
	private ImageMetaDataPanel imageMetaDataPanel;
	
	private MoleculePanel moleculePanel;
	
	private HashMap<String, String> tagHotKeyList;
	
	//Comments Tab Components
	private JScrollPane commentsTab;
	private JTextArea comments;
	
	private JMenuItem propertiesMenuItem = new JMenuItem("Properties");
	private JMenuItem saveMenuItem = new JMenuItem("Save");
	private JMenuItem saveAsMenuItem = new JMenuItem("Save As");
	
	private JRadioButton JSONencodingButton, SMILEencodingButton;
	
	//private JMenuItem renameMenuItem = new JMenuItem("Rename");
	
	//private JMenuItem addMetaDataMenuItem = new JMenuItem("Add ImageMetaData");
	
	private JMenuItem singleCurveMenuItem = new JMenuItem("Single Curve");
	private JMenuItem multiCurveMenuItem = new JMenuItem("Multiple Curves");
	private JMenuItem multiPlotMenuItem = new JMenuItem("Multiple Plots");
	
	private JMenuItem deleteMenuItem = new JMenuItem("Delete Molecules");
	private JMenuItem deleteTagsMenuItem = new JMenuItem("Delete Tags");
	private JMenuItem deleteParametersMenuItem = new JMenuItem("Delete Parameters");
	//private JMenuItem generateNewMenuItem = new JMenuItem("Generate new archive");
	//private JMenuItem addVideosMenuItem = new JMenuItem("Add Videos");
	private JMenuItem mergeMenuItem = new JMenuItem("Merge Molecules");
	private JMenuItem updateMenuItem = new JMenuItem("Update");
	//private JMenuItem unlockMenuItem = new JMenuItem("Unlock");
	
	//static so that window locations are offset...
	static int pos_x = 100;
	static int pos_y = 130;
	static int offsetX = 0;

	public MoleculeArchiveWindow(MoleculeArchiveService moleculeArchiveService) {
		this.moleculeArchiveService = moleculeArchiveService;
		this.prefService = 	moleculeArchiveService.getPrefService();
		this.uiService = moleculeArchiveService.getUIService();
		
		// add window to window manager
		// IJ1 style IJ2 doesn't seem to work...
		if (!uiService.isHeadless())
			WindowManager.addWindow(frame);
	}
	
	public MoleculeArchiveWindow(MoleculeArchive archive, MoleculeArchiveService moleculeArchiveService) {
		this.archive = archive;
		archive.setWindow(this);
		this.moleculeArchiveService = moleculeArchiveService;
		this.prefService = 	moleculeArchiveService.getPrefService();
		this.uiService = moleculeArchiveService.getUIService();

	    UIManager.put("Label.font", new Font("Menlo", Font.PLAIN, 12));
		
		createFrame(archive.getName());
		
		// add window to window manager
		// IJ1 style IJ2 doesn't seem to work...
		if (!uiService.isHeadless())
			WindowManager.addWindow(frame);
	}
	
	private void importHotKeys() {
		tagHotKeyList = new HashMap<String, String>();
		
		//Somehow import hotkeys
		if (prefService.getMap(MoleculeArchiveWindow.class, "tagHotKeyList") != null) {
			tagHotKeyList = (HashMap<String, String>)prefService.getMap(MoleculeArchiveWindow.class, "tagHotKeyList");
		}
	}
	
	public void updateAll() {
		//We just update everything when the tab is changed.
    	//We could just update the selected tab but it probably doesn't matter much.
		propertiesTab = archiveProperties();
		tabbedPane.setComponentAt(0, propertiesTab);
        imageMetaDataPanel.updateAll();
        moleculePanel.updateAll();
        comments.setText(archive.getComments());
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
		
		tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		
		tabbedPane.addChangeListener(new ChangeListener() {
	        public void stateChanged(ChangeEvent e) {
	        	//Prevent change if archive is locked
	        	if (lockArchive)
	        		tabbedPane.setSelectedIndex(0);
	        	else
	        		updateAll();
	        }
	    });
		
		JMenu fileMenu = new JMenu("File");
		JMenuBar mb = new JMenuBar();
		mb.add(fileMenu);
		fileMenu.add(saveMenuItem);
		fileMenu.add(saveAsMenuItem);
		fileMenu.addSeparator();
		fileMenu.add(propertiesMenuItem);
		
		importHotKeys();
		propertiesMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 MAPropertiesPanel propPanel = new MAPropertiesPanel(tagHotKeyList);
	        	 
	        	 JScrollPane dialogScrollPane = new JScrollPane(propPanel);		
	 			
	    	     JOptionPane pane = new JOptionPane(dialogScrollPane, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
	    	     JDialog dialog = pane.createDialog(frame, "Properties");
	    	     dialog.setResizable(true);
	    	     dialog.setVisible(true);
	    	     
	    	     if (pane.getValue() != null) {
	    	    	 if (pane.getValue().equals(JOptionPane.OK_OPTION)) {
	    	    		 if (propPanel.getHotKeyTable().isEditing())
	    	    			 propPanel.getHotKeyTable().getCellEditor().stopCellEditing();
	    	    		 
	    	    		 moleculePanel.updateTagHotKeyList(propPanel.getHotkeyList());
	    	    		 
	    	    		 //Remove old settings
	    	    		 prefService.remove(MoleculeArchiveWindow.class, "tagHotKeyList");
	    	    		 
	    	    		 //Save the tagHotKeyList to Preferences...
	    	    		 prefService.put(MoleculeArchiveWindow.class, "tagHotKeyList", tagHotKeyList);
	    	    	 } else if (pane.getValue().equals(JOptionPane.CANCEL_OPTION)) {
	   		    	  //Do nothing...
	    	    	 }
	    	     }
	          }
	       });
		
		saveMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 if (!lockArchive) {
		        	 archive.set(moleculePanel.getMolecule());
		        	 
		 			 if (archive.getFile() != null) {
		 				 if(archive.getFile().getName().equals(archive.getName())) {
		 				 	archive.save();
		 				 } else {
		 					 //the archive name has changed... so let's check with the user about the new name...
			 				saveAs(archive.getFile());
		 				 }
		 			 } else {
		 				saveAs(new File(archive.getName()));
		 			 }
		 			updateAll();
	        	 }
	          }
	       });
		saveAsMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 if (!lockArchive) {
	        		 	archive.set(moleculePanel.getMolecule());
	        		 
		 				if (archive.getFile() != null) {
			 				saveAs(new File(archive.getFile(), archive.getName()));
		 				} else {
		 					saveAs(new File(archive.getName()));
		 				}
		 				updateAll();
	        	 }
	          }
	       });
		
		
		JMenu plotMenu = new JMenu("Plot");
		mb.add(plotMenu);
		//toolsMenu.add(addMetaDataMenuItem);
		/*
		addMetaDataMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 //File imageFolder = uiService.chooseFile(archive.getFile(), FileWidget.DIRECTORY_STYLE);
	 			 //archive.addImageMetaData(new ImageMetaData(imageFolder, moleculeArchiveService, "Odin"));
	          }
	       });
	       */
		plotMenu.add(singleCurveMenuItem);
		singleCurveMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 if (!lockArchive) {
		        	PlotDialog dialog = new PlotDialog("Curve Plot", archive.get(0).getDataTable(), 1, false);
		        	dialog.showDialog();
		        	if (dialog.wasCanceled())
		     			return;
		        	
		        	dialog.update(dialog);
		        	ArrayList<PlotProperties> props = new ArrayList<PlotProperties>();
		        	PlotProperties curve1 = new PlotProperties(dialog.getXColumnName(), dialog.getNextYColumnName(), dialog.getNextCurveColor(), dialog.getCurveType(), dialog.getNextSegmentCurveColor());
		        	props.add(curve1);
		        	 
		        	moleculePanel.addCurvePlot(props);
	        	 }
	          }
	       });
		
		plotMenu.add(multiCurveMenuItem);
		multiCurveMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 if (!lockArchive) {
		        	//First we ask how many curves will be added
		        	 GenericDialog Numdialog = new GenericDialog("MultiPlot");
		     		 Numdialog.addNumericField("Number_of_curves", 2, 0);
		     		 Numdialog.showDialog();
		     		
		     		 if (Numdialog.wasCanceled())
		     			return;
		     		
		     		 int curveNum = (int)Numdialog.getNextNumber(); 
		        	 
		        	 PlotDialog dialog = new PlotDialog("Curve Plot", archive.get(0).getDataTable(), curveNum, false);
		        	 dialog.showDialog();
		        	 //Need to put this so the final values and properly
		        	 dialog.update(dialog);
		        	 ArrayList<PlotProperties> props = new ArrayList<PlotProperties>();
		        	 //Need to add None options for segments curve and then use inputs below and above
		        	 
		        	 for (int i=0;i<curveNum;i++) {
		        		 props.add(new PlotProperties(dialog.getXColumnName(), dialog.getNextYColumnName(), dialog.getNextCurveColor(), dialog.getCurveType(), dialog.getNextSegmentCurveColor()));
		        	 }
		        	 moleculePanel.addCurvePlot(props);
	        	 }
	          }
	       });
		
		plotMenu.add(multiPlotMenuItem);
		multiPlotMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 if (!lockArchive) {
	        	    //First we ask how many plots will be added
		        	GenericDialog dialog = new GenericDialog("Multiple Plots");
		        	String[] columnNames = archive.get(0).getDataTable().getColumnHeadings();
		     		dialog.addChoice("x_column", columnNames, "Time (s)");
		     		dialog.addNumericField("Number_of_plots", 2, 0);
		     		dialog.showDialog();
		     		
		     		if (dialog.wasCanceled())
		     			return;
		     		
		     		 String xColumnName = dialog.getNextChoice();
		     		 int plotNum = (int)dialog.getNextNumber(); 
			     		 
			         moleculePanel.addMulitplePlots(plotNum, xColumnName);
	        	 }
	          }
	       });
		
		JMenu toolsMenu = new JMenu("Tools");
		mb.add(toolsMenu);
		
		toolsMenu.add(deleteMenuItem);
		deleteMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 if (!lockArchive) {
		        	GenericDialog dialog = new GenericDialog("Delete Molecules");
		     		dialog.addStringField("Tags (comma separated list)", "", 30);
		     		dialog.addCheckbox("remove molecules with no tags", false);
		     		dialog.showDialog();
		     		
		     		if (dialog.wasCanceled())
		     			return;
		     		
		     		String tagsToDelete = dialog.getNextString();
		     		
		     		boolean removeWithNoTags = dialog.getNextBoolean();
		     		
		     		String[] tagList = tagsToDelete.split(",");
		            for (int i=0; i<tagList.length; i++) {
		            	tagList[i] = tagList[i].trim();
		            }
		     		 
		            ArrayList<String> deleteUIDs = (ArrayList<String>)archive.getMoleculeUIDs().stream().filter(UID -> {
		            	 	if (removeWithNoTags && archive.get(UID).getTags().size() == 0) {
		            	 		return true;
		            	 	}
		            	 
		     				boolean hasTag = false;
		     				for (int i=0; i<tagList.length; i++) {
		     		        	for (String tag : archive.get(UID).getTags()) {
		     		        		if (tagList[i].equals(tag)) {
		     		        			hasTag = true;
		     		        		}
		     		        	}
		     		        }
		     				return hasTag;
		     			}).collect(toList());
		             
		             for (String UID : deleteUIDs) {
		            	 archive.remove(UID);
		             }
		             
		     		 moleculePanel.updateAll();
	        	 }
	          }
	       });
		
		toolsMenu.add(deleteTagsMenuItem);
		deleteTagsMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 if (!lockArchive) {
			        	GenericDialog dialog = new GenericDialog("Delete Tags");
			     		dialog.addStringField("Tags (comma separated list)", "", 30);
			     		dialog.addCheckbox("remove all tags", false);
			     		dialog.showDialog();
			     		
			     		if (dialog.wasCanceled())
			     			return;
			     		
			     		String tagsToDelete = dialog.getNextString();
			     		
			     		boolean removeAllTags = dialog.getNextBoolean();
			     		
			     		String[] tagList = tagsToDelete.split(",");
			            for (int i=0; i<tagList.length; i++) {
			            	tagList[i] = tagList[i].trim();
			            }
			     		 
			            archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			            		Molecule molecule = archive.get(UID);
			            	 	if (removeAllTags) {
			            	 		molecule.removeAllTags();
			            	 	} else {
			     		        	for (String tag : tagList) {
			     		        		molecule.removeTag(tag);
			     		        	}
			            	 	}
			            	 	archive.set(molecule);
			     			});
			             
			     		 moleculePanel.updateAll();
		        	 }
	          }
	       });
		
		toolsMenu.add(deleteParametersMenuItem);
		deleteParametersMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 if (!lockArchive) {
			        	GenericDialog dialog = new GenericDialog("Delete Parameters");
			     		dialog.addStringField("Parameters (comma separated list)", "", 30);
			     		dialog.addCheckbox("remove all parameters", false);
			     		dialog.showDialog();
			     		
			     		if (dialog.wasCanceled())
			     			return;
			     		
			     		String parametersToDelete = dialog.getNextString();
			     		
			     		boolean removeAllParameters = dialog.getNextBoolean();
			     		
			     		String[] parameterList = parametersToDelete.split(",");
			            for (int i=0; i<parameterList.length; i++) {
			            	parameterList[i] = parameterList[i].trim();
			            }
			     		 
			            archive.getMoleculeUIDs().parallelStream().forEach(UID -> {
			            		Molecule molecule = archive.get(UID);
			            	 	if (removeAllParameters) {
			            	 		molecule.removeAllParameters();
			            	 	} else {
			     		        	for (String parameter : parameterList) {
			     		        		molecule.removeParameter(parameter);
			     		        	}
			            	 	}
			            	 	archive.set(molecule);
			     			});
			             
			     		 moleculePanel.updateAll();
		        	 }
	          }
	       });
		
		toolsMenu.add(mergeMenuItem);
		mergeMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 if (!lockArchive) {
	        		GenericDialog dialog = new GenericDialog("Merge molecules");
		     		dialog.addStringField("Merge Molecules with Tag", "", 20);
		     		dialog.showDialog();
		     		
		     		if (dialog.wasCanceled())
		     			return;
		     		
		     		String tag = dialog.getNextString().trim();
			     		 
		     		ArrayList<String> mergeUIDs = (ArrayList<String>)archive.getMoleculeUIDs().stream().filter(UID -> archive.get(UID).hasTag(tag)).collect(toList());
	             
		     		if (mergeUIDs.size() < 2) 
		     			return;
		     		
		     		String mergeNote = "Merged " + mergeUIDs.size() + " molecules \n";
		     		
		     		MARSResultsTable mergedDataTable = archive.get(mergeUIDs.get(0)).getDataTable();
		     		
		     		HashSet<Double> sliceNumbers = new HashSet<Double>();
		     		
		     		//First add all current slices to set
		     		for (int row=0;row<mergedDataTable.getRowCount();row++) {
	            		sliceNumbers.add(mergedDataTable.getValue("slice", row));
	            	}
		     		
		     		mergeNote += mergeUIDs.get(0).substring(0, 5) + " : slices " + mergedDataTable.getValue("slice", 0) + " " + mergedDataTable.getValue("slice", mergedDataTable.getRowCount()-1) + "\n";
		     		
		            for (int i = 1; i < mergeUIDs.size() ; i++) {
		            	MARSResultsTable nextDataTable = archive.get(mergeUIDs.get(i)).getDataTable();
		            	
		            	for (int row=0;row<nextDataTable.getRowCount();row++) {
		            		if (!sliceNumbers.contains(nextDataTable.getValue("slice", row))) {
		            			mergedDataTable.appendRow();
		            			int mergeLastRow = mergedDataTable.getRowCount() - 1;
		            			
		            			for (int col=0;col<mergedDataTable.getColumnCount();col++) {
		            				String column = mergedDataTable.getColumnHeader(col);
		    	            		mergedDataTable.setValue(column, mergeLastRow, nextDataTable.getValue(column, row));
		    	            	}
		            			
		            			sliceNumbers.add(nextDataTable.getValue("slice", row));
		            		}
		            	}
		            	mergeNote += mergeUIDs.get(i).substring(0, 5) + " : slices " + nextDataTable.getValue("slice", 0) + " " + nextDataTable.getValue("slice", nextDataTable.getRowCount()-1) + "\n";
		            	
		            	archive.remove(mergeUIDs.get(i));
		            }
		            
		            //sort by slice
		            mergedDataTable.sort(true, "slice");
		            
		            archive.get(mergeUIDs.get(0)).setNotes(archive.get(mergeUIDs.get(0)).getNotes() + "\n" + mergeNote);
		            
		            
		     		moleculePanel.updateAll();
	        	 }
	          }
	       });
		
		toolsMenu.add(updateMenuItem);
		updateMenuItem.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	 updateAll();
	        	 lockArchive = false;
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
 		frame.addWindowListener(new WindowAdapter() {
 	         public void windowClosing(WindowEvent e) {
 	        	if (!lockArchive)
 	        		close();
 	         }
 	      });
		frame.setLayout(new BorderLayout());
		frame.add(tabbedPane, BorderLayout.CENTER);
		frame.setJMenuBar(mb);
		frame.pack();
		frame.setSize(900, 700);
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
		
		//gbc.gridy += 1;
		//panel.add(new JLabel("                                   "), gbc);
		
		gbc.gridy += 1;
		panel.add(new JLabel("Encoding:                                 "), gbc);
		
		JSONencodingButton = new JRadioButton("JSON");
		JSONencodingButton.setMnemonic(KeyEvent.VK_J);
		JSONencodingButton.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	archive.unsetSMILEencoding();
	         }
		});
		gbc.gridy += 1;
		panel.add(JSONencodingButton, gbc);
		
		SMILEencodingButton = new JRadioButton("Smile");
	    SMILEencodingButton.setMnemonic(KeyEvent.VK_S);
		SMILEencodingButton.addActionListener(new ActionListener() {
	         public void actionPerformed(ActionEvent e) {
	        	archive.setSMILEencoding();
	         }
		});
		gbc.gridy += 1;
		panel.add(SMILEencodingButton, gbc);
		
		//Group the radio buttons.
	    ButtonGroup group = new ButtonGroup();
	    group.add(JSONencodingButton);
	    group.add(SMILEencodingButton);
	    
	    if (archive.isSMILEencoding()) {
	    	JSONencodingButton.setSelected(false);
		    SMILEencodingButton.setSelected(true);
	    } else {
	    	JSONencodingButton.setSelected(true);
		    SMILEencodingButton.setSelected(false);
	    }
		
		JPanel northPanel = new JPanel();
		northPanel.setLayout(new GridBagLayout());	
		GridBagConstraints northGBC = new GridBagConstraints();
		northGBC.anchor = GridBagConstraints.NORTHWEST;
		
		northGBC.weightx = 1;
		northGBC.weighty = 1;
		
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
	
	private boolean saveAs(File saveAsFile) {
		File file = uiService.chooseFile(saveAsFile, FileWidget.SAVE_STYLE);
		if (file != null) {
			if (moleculeArchiveService.rename(archive.getName(), file.getName())) {
				if (!uiService.isHeadless())
					WindowManager.removeWindow(frame);
				
				archive.setName(file.getName());
				frame.setTitle(file.getName());
				
				archive.saveAs(file);
				
				if (!uiService.isHeadless())
					WindowManager.addWindow(frame);
				
				return true;
			} else {
				archive.saveAs(file);
				return true;
			}
		}
		return false;
	}
	
	//We add an lockArchive for duing processing steps
	public void lockArchive() {
		lockArchive = true;
		//We move to the general properties pane
		tabbedPane.setSelectedIndex(0);
	}
	
	public void unlockArchive() {
		updateAll();
		lockArchive = false;
	}
	
	public MoleculeArchive getArchive() {
		return archive;
	}
	
	public void rename(String name) {
		if (name != null) {
			if (moleculeArchiveService.rename(archive.getName(), name)) {
				if (!uiService.isHeadless())
					WindowManager.removeWindow(frame);
				
				archive.setName(name);
				frame.setTitle(name);
				
				if (!uiService.isHeadless())
					WindowManager.addWindow(frame);
			}
		}
	}
	
	public void close() {
		moleculeArchiveService.removeArchive(archive.getName());
		frame.setVisible(false);
		frame.dispose();
		
		if (!uiService.isHeadless())
			WindowManager.removeWindow(frame);
	}
	
	public void setArchiveService(MoleculeArchiveService moleculeArchiveService) {
		this.moleculeArchiveService = moleculeArchiveService;
	}
	
	public void setUIService(UIService uiService) {
		this.uiService = uiService;
	}
}
