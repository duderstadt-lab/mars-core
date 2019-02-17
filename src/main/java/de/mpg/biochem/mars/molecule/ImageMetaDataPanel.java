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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import org.apache.commons.lang3.StringUtils;
import org.scijava.table.DoubleColumn;
import org.scijava.table.GenericColumn;

import de.mpg.biochem.mars.table.MARSResultsTable;

public class ImageMetaDataPanel extends JPanel {
	private ImageMetaData imageMetaData;
	private MoleculeArchive archive;
	
	private JTextField UIDLabel, DateLabel, SourcePath;
	private JTextField Microscope;
	private JTextArea Notes;
	
	//Log Tab Components
	private JScrollPane logTab;
	private JTextArea log;
	
	private JTabbedPane metaDataTabs;
	
	private JTable imageMetaDataIndex;
	private AbstractTableModel imageMetaDataTableModel;
	private TableRowSorter imageMetaDataSorter;
	
	private JTextField imageMetaDataSearchField;
	private JScrollPane imageMetaDataProperties;
	
	private JTable DataTable;
	private AbstractTableModel DataTableModel;
	
	private ImageMetaData DummyImageMetaData = new ImageMetaData("unknown", new MARSResultsTable());
	
	public ImageMetaDataPanel(MoleculeArchive archive) {
		this.archive = archive;
		
		if (archive.getNumberOfImageMetaDataItems() > 0) {
			this.imageMetaData = archive.getImageMetaData(0);
		} else {
			imageMetaData = DummyImageMetaData;
		}
		
		//METADATA INDEX LIST
		//Need to build the datamodel backed by the ImageMetaData...
		imageMetaDataTableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public String getValueAt(int rowIndex, int columnIndex) {
				if (columnIndex == 0) {
					return "" + rowIndex;
				} else if (columnIndex == 1) {
					return archive.getImageMetaData(rowIndex).getUID();
				} else if (columnIndex == 2) {
					return archive.getImageMetaData(rowIndex).getCollectionDate();
				}
				return null;
			}
			
			@Override
			public String getColumnName(int columnIndex) {
				if (columnIndex == 0) {
					return "Index";
				} else if (columnIndex == 1) {
					return "UID";
				} else if (columnIndex == 2) {
					return "Date";
				}	
				return null;
			}

			@Override
			public int getRowCount() {
				return archive.getNumberOfImageMetaDataItems();
			}
			
			@Override
			public int getColumnCount() {
				return 3;
			}
			
			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex)  {
				return false;
			}
		};
		
		imageMetaDataIndex = new JTable(imageMetaDataTableModel);
		//imageMetaDataIndex.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		imageMetaDataIndex.setFont(new Font("Menlo", Font.PLAIN, 12));
		imageMetaDataIndex.setRowSelectionAllowed(true);
		imageMetaDataIndex.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		resizeColumnWidth(imageMetaDataIndex);
		
		ListSelectionModel rowIMD = imageMetaDataIndex.getSelectionModel();
		rowIMD.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                //Ignore extra messages.
                if (e.getValueIsAdjusting()) return;

                ListSelectionModel lsm = (ListSelectionModel)e.getSource();
                if (!lsm.isSelectionEmpty()) {
                    int selectedRow = lsm.getMinSelectionIndex();
                    imageMetaData = archive.getImageMetaData((String)imageMetaDataIndex.getValueAt(selectedRow, 1));
                    updateAll();
                }
            }
        });

		//for (int i=0; i<imageMetaDataIndex.getColumnCount();i++)
		//	imageMetaDataIndex.getColumnModel().getColumn(i).sizeWidthToFit();
		
		//imageMetaDataIndex.getColumnModel().getColumn(0).setMinWidth(80);
		//imageMetaDataIndex.getColumnModel().getColumn(1).setMinWidth(150);
		
		JScrollPane imageMetaDataIndexScrollPane = new JScrollPane(imageMetaDataIndex);

		JPanel westPane = new JPanel();
		westPane.setLayout(new BorderLayout());
		
		imageMetaDataSorter = new TableRowSorter<AbstractTableModel>(imageMetaDataTableModel);
		for (int i=0;i<imageMetaDataTableModel.getColumnCount();i++)
			imageMetaDataSorter.setSortable(i, false);
		
		imageMetaDataIndex.setRowSorter(imageMetaDataSorter);
		
		imageMetaDataSearchField = new JTextField();
		
		imageMetaDataSearchField.getDocument().addDocumentListener(
	        new DocumentListener() {
	            public void changedUpdate(DocumentEvent e) {
	            	filterImageMetaDataIndex();
	            }
	            public void insertUpdate(DocumentEvent e) {
	            	filterImageMetaDataIndex();
	            }
	            public void removeUpdate(DocumentEvent e) {
	            	filterImageMetaDataIndex();
	            }
	        });
		
		westPane.add(imageMetaDataIndexScrollPane, BorderLayout.CENTER);
		westPane.add(imageMetaDataSearchField, BorderLayout.SOUTH);
		
		metaDataTabs = new JTabbedPane();
		buildMetaDataTabs();
		
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, westPane, metaDataTabs);
				
		splitPane.setDividerLocation(300);
		
		if (archive.getNumberOfImageMetaDataItems() != 0)
			imageMetaDataIndex.setRowSelectionInterval(0, 0);
		
		setLayout(new BorderLayout());
		add(splitPane, BorderLayout.CENTER);
	}
	
	public void buildMetaDataTabs() {
		metaDataTabs.removeAll();
		
		imageMetaDataProperties = buildMetaDataProperties();
		metaDataTabs.addTab("Properties", imageMetaDataProperties);
		
		JScrollPane tablePane = buildMetaDataTable();
		metaDataTabs.addTab("DataTable", tablePane);
		
		//Notes
		Notes = new JTextArea(imageMetaData.getNotes());
        JScrollPane commentScroll = new JScrollPane(Notes);
		        
        Notes.getDocument().addDocumentListener(
	        new DocumentListener() {
	            public void changedUpdate(DocumentEvent e) {
	            	if (archive.getNumberOfImageMetaDataItems() != 0) {
		            	imageMetaData.setNotes(Notes.getText());
		            	archive.setImageMetaData(imageMetaData);
	            	}
	            }
	            public void insertUpdate(DocumentEvent e) {
	            	if (archive.getNumberOfImageMetaDataItems() != 0) {
		            	imageMetaData.setNotes(Notes.getText());
		            	archive.setImageMetaData(imageMetaData);
	            	}
	            }
	            public void removeUpdate(DocumentEvent e) {
	            	if (archive.getNumberOfImageMetaDataItems() != 0) {
		            	imageMetaData.setNotes(Notes.getText());
		            	archive.setImageMetaData(imageMetaData);
	            	}
	            }
	        });
		metaDataTabs.addTab("Notes", commentScroll);
		
		logTab = makeLogTab();
		metaDataTabs.addTab("Log", logTab);	
	}
	
	public JScrollPane buildMetaDataProperties() {
		JPanel pane = new JPanel();
		
		pane.setLayout(new GridBagLayout());
		
		GridBagConstraints gbc = new GridBagConstraints();
		
		gbc.anchor = GridBagConstraints.NORTHWEST;
		
		gbc.weightx = 1;
		gbc.weighty = 1;
		
		gbc.gridx = 0;
		gbc.gridy = 0;
		
		gbc.insets = new Insets(10, 10, 10, 10);
		
		//UID and image UID for this molecule
		JLabel uidName = new JLabel("UID");
		uidName.setFont(new Font("Menlo", Font.BOLD, 12));
        pane.add(uidName, gbc);
		
        gbc.gridy += 1;
		UIDLabel = new JTextField("" + imageMetaData.getUID());
		UIDLabel.setFont(new Font("Menlo", Font.PLAIN, 12));
		UIDLabel.setEditable(false);
		UIDLabel.setBackground(null);
		pane.add(UIDLabel, gbc);
		
		gbc.gridy += 1;
		JLabel microscope = new JLabel("Microscope");
		microscope.setFont(new Font("Menlo", Font.BOLD, 12));
		pane.add(microscope, gbc);
		
		gbc.gridy += 1;
		Microscope = new JTextField("" + imageMetaData.getMicroscopeName());
		Microscope.setFont(new Font("Menlo", Font.PLAIN, 12));
		Microscope.setEditable(false);
		Microscope.setBackground(null);
		pane.add(Microscope, gbc);
		
		gbc.gridy += 1;
		JLabel CollectionDate = new JLabel("Collection Date");
		CollectionDate.setFont(new Font("Menlo", Font.BOLD, 12));
		pane.add(CollectionDate, gbc);
		
		gbc.gridy += 1;
		DateLabel = new JTextField("" + imageMetaData.getCollectionDate());
		DateLabel.setFont(new Font("Menlo", Font.PLAIN, 12));
		DateLabel.setEditable(false);
		DateLabel.setBackground(null);
		pane.add(DateLabel, gbc);
		
		gbc.gridy += 1;
		JLabel SourceFolder = new JLabel("Source Path");
		SourceFolder.setFont(new Font("Menlo", Font.BOLD, 12));
		pane.add(SourceFolder, gbc);
		
		gbc.gridy += 1;
		SourcePath = new JTextField("" + imageMetaData.getSourceDirectory());
		SourcePath.setFont(new Font("Menlo", Font.PLAIN, 12));
		SourcePath.setEditable(false);
		SourcePath.setBackground(null);
		pane.add(SourcePath, gbc);
		
		GridBagConstraints northGBC = new GridBagConstraints();
		northGBC.anchor = GridBagConstraints.NORTHWEST;
		
		northGBC.weightx = 1;
		northGBC.weighty = 1;
		
		northGBC.gridx = 0;
		northGBC.gridy = 0;
		
		JPanel pane2 = new JPanel();
		pane2.setLayout(new GridBagLayout());
		
		pane2.add(pane, northGBC);
		
		JScrollPane scrollpane = new JScrollPane(pane2);
		return scrollpane;
	}
	
	private JScrollPane buildMetaDataTable() {
		DataTableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				if (columnIndex == 0)
					return rowIndex + 1;
				
				return imageMetaData.getDataTable().get(columnIndex - 1, rowIndex);
			}
			
			@Override
			public String getColumnName(int columnIndex) {
				if (columnIndex == 0)
					return "Row";
				
				return imageMetaData.getDataTable().getColumnHeader(columnIndex - 1);
			}

			@Override
			public int getRowCount() {
				return imageMetaData.getDataTable().getRowCount();
			}
			
			@Override
			public int getColumnCount() {
				return imageMetaData.getDataTable().getColumnCount() + 1;
			}
			
			/*
			@Override
			public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
				if (imageMetaData.getDataTable().get(columnIndex - 1)  instanceof DoubleColumn) {
					imageMetaData.getDataTable().set(columnIndex - 1, rowIndex, Double.valueOf((String)aValue));
				} else {
					//Otherwise we just put a String
					imageMetaData.getDataTable().set(columnIndex - 1, rowIndex, (String)aValue);
				}
			}
			*/
			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex)  {
				return false;
			}
		};
		
		DataTable = new JTable(DataTableModel);		
		DataTable.setAutoCreateColumnsFromModel(true);
		DataTable.setRowSelectionAllowed(true);
		DataTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		resizeColumnWidth(DataTable);
		DataTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		
		for (int i = 0; i < DataTable.getColumnCount(); i++) {
			DataTable.getColumnModel().getColumn(i).sizeWidthToFit();
		}
		
		JScrollPane scrollPane = new JScrollPane(DataTable);
		
		//Dimension dim = new Dimension(DataTable.getColumnCount()*75 + 5, 500);
		Dimension dim = new Dimension(500, 500);
		
		scrollPane.setMinimumSize(dim);
		scrollPane.setMaximumSize(dim);
		scrollPane.setPreferredSize(dim);
		
		return scrollPane;
	}

	private JScrollPane makeLogTab() {
		log = new JTextArea(imageMetaData.getLog());
		log.setFont(new Font("Menlo", Font.PLAIN, 12));
		log.setEditable(false);
        JScrollPane pane = new JScrollPane(log);
		return pane;
	}
	
	public void updateAll() {
		if (archive.getNumberOfImageMetaDataItems() == 0) {
			imageMetaData = DummyImageMetaData;
			Notes.setEditable(false);
		} else if (archive.getImageMetaData(imageMetaData.getUID()) == null) {
			imageMetaData = archive.getImageMetaData(0);
			Notes.setEditable(true);
		} else {
			//Need to reload the current record if
			//working in virtual storage
			//This ensures if a command changed the values
			//The new values are loaded 
			//this prevents overwritting when switching records
			//in the window..
			imageMetaData = archive.getImageMetaData(imageMetaData.getUID());
			Notes.setEditable(true);
		}
		
		//Update Labels
		UIDLabel.setText(imageMetaData.getUID());
		Microscope.setText(imageMetaData.getMicroscopeName());
		DateLabel.setText(imageMetaData.getCollectionDate());
		SourcePath.setText(imageMetaData.getSourceDirectory());
		
		//Update Comments
		Notes.setText(imageMetaData.getNotes());
				
		//Update Log
		log.setText(imageMetaData.getLog());
				
		//Update DataTable
		DataTableModel.fireTableStructureChanged();
		for (int i = 0; i < DataTable.getColumnCount(); i++)
			DataTable.getColumnModel().getColumn(i).sizeWidthToFit();
		
	}
	
	private void filterImageMetaDataIndex() {
		RowFilter<AbstractTableModel, Object> rf = null;
        //If current expression doesn't parse, don't update.
        try {
            rf = RowFilter.regexFilter(imageMetaDataSearchField.getText(), 0, 1, 2);
        } catch (java.util.regex.PatternSyntaxException e) {
            return;
        }
        imageMetaDataSorter.setRowFilter(rf);
	}
	
	//getters and setters
	public void setImageMetaData(ImageMetaData imageMetaData) {
		this.imageMetaData = imageMetaData;
	}
	
	public ImageMetaData getImageMetaData() {
		return imageMetaData;
	}
	
	public void resizeColumnWidth(JTable table) {
	    final TableColumnModel columnModel = table.getColumnModel();
	    for (int column = 0; column < table.getColumnCount(); column++) {
	        int width = 15; // Min width
	        for (int row = 0; row < table.getRowCount(); row++) {
	            TableCellRenderer renderer = table.getCellRenderer(row, column);
	            Component comp = table.prepareRenderer(renderer, row, column);
	            width = Math.max(comp.getPreferredSize().width +1 , width);
	        }
	        if(width > 300)
	            width=300;
	        columnModel.getColumn(column).setPreferredWidth(width);
	    }
	}
}
