/*******************************************************************************
 * Copyright (C) 2019, Duderstadt Lab
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package de.mpg.biochem.mars.molecule;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
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

import de.mpg.biochem.mars.molecule.MoleculePanel.DecimalFormatRenderer;
import de.mpg.biochem.mars.table.MARSResultsTable;

public class ImageMetaDataPanel extends JPanel {
	private MARSImageMetaData imageMetaData;
	private MoleculeArchive archive;
	
	private JTextField UIDLabel, DateLabel, SourcePath;
	private JTextField Microscope;
	private JTextArea Notes;
	
	private int imageMetaDataCount;
	
	//Log Tab Components
	private JScrollPane logTab;
	private JTextArea log;
	
	private JTabbedPane metaDataTabs;
	
	private JTable imageMetaDataIndex;
	private AbstractTableModel imageMetaDataIndexTableModel;
	private TableRowSorter imageMetaDataSorter;
	
	private JTextField imageMetaDataSearchField;
	private JScrollPane imageMetaDataProperties;
	
	private JTable DataTable;
	private AbstractTableModel DataTableModel;
	
	private JTable ParameterTable;
	private AbstractTableModel ParameterTableModel;
	private String[] ParameterList;
	
	private JTable TagTable;
	private AbstractTableModel TagTableModel;
	private String[] TagList;
	
	private boolean imageMetaDataRecordChanged = false;
	
	private MARSImageMetaData DummyImageMetaData = new MARSImageMetaData("unknown", new MARSResultsTable());
	
	public ImageMetaDataPanel(MoleculeArchive archive) {
		this.archive = archive;
		
		if (archive.getNumberOfImageMetaDataRecords() > 0) {
			this.imageMetaData = archive.getImageMetaData(0);
		} else {
			imageMetaData = DummyImageMetaData;
		}
		
		imageMetaDataCount = archive.getNumberOfImageMetaDataRecords();
		buildPanel();
	}
	
	public void buildPanel() {
		//METADATA INDEX LIST
		//Need to build the index datamodel backed by the ImageMetaData...
		imageMetaDataIndexTableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public String getValueAt(int rowIndex, int columnIndex) {
				if (columnIndex == 0) {
					return "" + rowIndex;
				} else if (columnIndex == 1) {
					return archive.getImageMetaDataUIDAtIndex(rowIndex);
				}  else if (columnIndex == 2) {
					return archive.getImageMetaDataTagList(archive.getImageMetaDataUIDAtIndex(rowIndex));
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
					return "Tags";
				}	
				return null;
			}

			@Override
			public int getRowCount() {
				return archive.getNumberOfImageMetaDataRecords();
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
		
		imageMetaDataIndex = new JTable(imageMetaDataIndexTableModel);
		imageMetaDataIndex.setFont(new Font("Menlo", Font.PLAIN, 12));
		imageMetaDataIndex.setRowSelectionAllowed(true);
		imageMetaDataIndex.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		resizeColumnWidth(imageMetaDataIndex);
		
		ListSelectionModel rowIMD = imageMetaDataIndex.getSelectionModel();
		rowIMD.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
            	if (ParameterTable != null && ParameterTable.isEditing())
        			ParameterTable.getCellEditor().stopCellEditing();
                //Ignore extra messages.
                if (e.getValueIsAdjusting()) return;

                ListSelectionModel lsm = (ListSelectionModel)e.getSource();
                if (!lsm.isSelectionEmpty()) {
                    int selectedRow = lsm.getMinSelectionIndex();
                    if (imageMetaDataRecordChanged)
                    	archive.putImageMetaData(imageMetaData);
                    imageMetaData = archive.getImageMetaData((String)imageMetaDataIndex.getValueAt(selectedRow, 1));
                    updateAll();
                }
            }
        });

		//for (int i=0; i<imageMetaDataIndex.getColumnCount();i++)
		//	imageMetaDataIndex.getColumnModel().getColumn(i).sizeWidthToFit();
		
		imageMetaDataIndex.getColumnModel().getColumn(0).setMinWidth(40);
		imageMetaDataIndex.getColumnModel().getColumn(1).setMinWidth(70);
		
		JScrollPane imageMetaDataIndexScrollPane = new JScrollPane(imageMetaDataIndex);

		JPanel westPane = new JPanel();
		westPane.setLayout(new BorderLayout());
		
		imageMetaDataSorter = new TableRowSorter<AbstractTableModel>(imageMetaDataIndexTableModel);
		for (int i=0;i<imageMetaDataIndexTableModel.getColumnCount();i++)
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
		
		updateParameterList();
		updateTagList();
		
		//PROPERTIES OF IMAGE META DATA AT INDEX
		//This properties panel will be a JSplitPane on the right side.
		//That contains UID, Metadata, etc...
		JPanel propsPanel = buildPropertiesPanel();
		
		JPanel rightPane = new JPanel();
		rightPane.setLayout(new BorderLayout());
		rightPane.add(metaDataTabs, BorderLayout.CENTER);
		rightPane.add(propsPanel, BorderLayout.EAST);
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, westPane, rightPane);
				
		splitPane.setDividerLocation(300);
		
		if (archive.getNumberOfImageMetaDataRecords() > 0)
			imageMetaDataIndex.setRowSelectionInterval(0, 0);
		
		setLayout(new BorderLayout());
		add(splitPane, BorderLayout.CENTER);
		
		updateAll();
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
	            	if (archive.getNumberOfImageMetaDataRecords() != 0) {
		            	imageMetaData.setNotes(Notes.getText());
		            	archive.putImageMetaData(imageMetaData);
	            	}
	            }
	            public void insertUpdate(DocumentEvent e) {
	            	if (archive.getNumberOfImageMetaDataRecords() != 0) {
		            	imageMetaData.setNotes(Notes.getText());
		            	archive.putImageMetaData(imageMetaData);
	            	}
	            }
	            public void removeUpdate(DocumentEvent e) {
	            	if (archive.getNumberOfImageMetaDataRecords() != 0) {
		            	imageMetaData.setNotes(Notes.getText());
		            	archive.putImageMetaData(imageMetaData);
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
	
	public JPanel buildPropertiesPanel() {
		JPanel globalPan = new JPanel();
		globalPan.setLayout(new BorderLayout());
		
		JPanel northPan = new JPanel();
		northPan.setLayout(new GridBagLayout());
		GridBagConstraints gbcNorth = new GridBagConstraints();
		gbcNorth.anchor = GridBagConstraints.NORTH;
		
		gbcNorth.weightx = 1;
		gbcNorth.weighty = 1;
		
		gbcNorth.gridx = 0;
		gbcNorth.gridy = 0;
		
		gbcNorth.insets = new Insets(5, 0, 5, 0);
		
		//This will be placed in the center...
		JPanel paramPanel = buildParameterPanel();
		globalPan.add(paramPanel, BorderLayout.CENTER);
		
		JPanel southPan = new JPanel();
		southPan.setLayout(new GridBagLayout());
		GridBagConstraints gbcSouth = new GridBagConstraints();
		gbcSouth.anchor = GridBagConstraints.NORTH;
		
		gbcSouth.weightx = 1;
		gbcSouth.weighty = 1;
		
		gbcSouth.gridx = 0;
		gbcSouth.gridy = 0;
		
		JPanel tagsPanel = buildTagsPanel();
		southPan.add(tagsPanel, gbcSouth);
        
        globalPan.add(southPan, BorderLayout.SOUTH);
		
		return globalPan;
	}

	public void updateParameterList() {
		ParameterList = new String[imageMetaData.getParameters().keySet().size()];
		imageMetaData.getParameters().keySet().toArray(ParameterList);
	}
	
	public void updateTagList() {
		TagList = new String[imageMetaData.getTags().size()];
		imageMetaData.getTags().toArray(TagList);
	}

	public JPanel buildParameterPanel() {
		updateParameterList();
		
		ParameterTableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;
	
			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				if (columnIndex == 0) {
					return ParameterList[rowIndex];
				}
				
				return imageMetaData.getParameters().get(ParameterList[rowIndex]);
			}
			
			@Override
			public String getColumnName(int columnIndex) {
				if (columnIndex == 0)
					return "Parameter";
				else
					return "Value";
			}
	
			@Override
			public int getRowCount() {
				return ParameterList.length;
			}
			
			@Override
			public int getColumnCount() {
				return 2;
			}
			
			@Override
			public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
				imageMetaDataRecordChanged = true;
				double value = Double.parseDouble((String)aValue);
				imageMetaData.setParameter(ParameterList[rowIndex], value);
			}
			
			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex)  {
				return columnIndex > 0;
			}
		};
		
		JPanel parameterPanel = new JPanel();
		parameterPanel.setLayout(new BorderLayout());
		
		ParameterTable = new JTable(ParameterTableModel);
		ParameterTable.setAutoCreateColumnsFromModel(true);
		ParameterTable.setRowSelectionAllowed(true);
		ParameterTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		resizeColumnWidth(ParameterTable);
		
		ParameterTable.getColumnModel().getColumn(0).setMinWidth(125);
		ParameterTable.getColumnModel().getColumn(1).setCellRenderer( new DecimalFormatRenderer() );
		
		//ParameterTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		
		JScrollPane ParameterScrollPane = new JScrollPane(ParameterTable);
		
		Dimension dim2 = new Dimension(225, 10000);
		
		//ParameterScrollPane.setMinimumSize(dim2);
		ParameterScrollPane.setMaximumSize(dim2);
		ParameterScrollPane.setPreferredSize(dim2);
		
		parameterPanel.add(ParameterScrollPane, BorderLayout.CENTER);
		
		JPanel southPanel = new JPanel();
		southPanel.setLayout(new GridBagLayout());
		GridBagConstraints parameterPanelGBC = new GridBagConstraints();
		parameterPanelGBC.anchor = GridBagConstraints.NORTH;
		
		//Top, left, bottom, right
		parameterPanelGBC.insets = new Insets(5, 0, 5, 0);
		
		parameterPanelGBC.weightx = 1;
		parameterPanelGBC.weighty = 1;
		
		parameterPanelGBC.gridx = 0;
		parameterPanelGBC.gridy = 0;
		
		JPanel AddPanel = new JPanel();
		AddPanel.setLayout(new BorderLayout());
		JTextField newParameter = new JTextField(12);
		Dimension dimParm = new Dimension(200, 20);
		newParameter.setMinimumSize(dimParm);
		AddPanel.add(newParameter, BorderLayout.CENTER);
		JButton Add = new JButton("Add");
		Add.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!newParameter.getText().equals("") && archive.getNumberOfMolecules() != 0) {
					imageMetaDataRecordChanged = true;
					imageMetaData.setParameter(newParameter.getText().trim(), 0);
					updateParameterList();
					ParameterTableModel.fireTableDataChanged();
				}
			}
		});
		AddPanel.add(Add, BorderLayout.EAST);
		
		southPanel.add(AddPanel, parameterPanelGBC);
		
		JButton Remove = new JButton("Remove");
		Remove.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (ParameterTable.getSelectedRow() != -1 && archive.getNumberOfMolecules() != 0) {
					imageMetaDataRecordChanged = true;
					String param = (String)ParameterTable.getValueAt(ParameterTable.getSelectedRow(), 0);
					imageMetaData.removeParameter(param);
					updateParameterList();
					ParameterTableModel.fireTableDataChanged();
				}
			}
		});
		
		parameterPanelGBC.gridy += 1;
		parameterPanelGBC.anchor = GridBagConstraints.NORTHEAST;
		southPanel.add(Remove, parameterPanelGBC);
		
		parameterPanel.add(southPanel, BorderLayout.SOUTH);
		
		return parameterPanel;
	}
	
	public JPanel buildTagsPanel() {
		TagTableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;
	
			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				return TagList[rowIndex];
			}
			
			@Override
			public String getColumnName(int columnIndex) {
				return "Tag";
			}
	
			@Override
			public int getRowCount() {
				return TagList.length;
			}
			
			@Override
			public int getColumnCount() {
				return 1;
			}
		};
		
		JPanel tagPanel = new JPanel();
		tagPanel.setLayout(new GridBagLayout());
		
		GridBagConstraints tagPanelGBC = new GridBagConstraints();
		tagPanelGBC.anchor = GridBagConstraints.NORTH;
		//tagPanelGBC.insets = new Insets(5, 5, 5, 5);
		
		tagPanelGBC.weightx = 1;
		tagPanelGBC.weighty = 1;
		
		tagPanelGBC.gridx = 0;
		tagPanelGBC.gridy = 0;
		
		//JLabel tagsName = new JLabel("Tags");
		//tagsName.setFont(new Font("Menlo", Font.BOLD, 12));
		//tagPanel.add(tagsName, tagPanelGBC);
		
		TagTable = new JTable(TagTableModel);
		TagTable.setAutoCreateColumnsFromModel(true);
		TagTable.setRowSelectionAllowed(true);
		TagTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		TagTable.getColumnModel().getColumn(0).sizeWidthToFit();
		
		//TagTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		
		JScrollPane TagScrollPane = new JScrollPane(TagTable);
		
		Dimension dim3 = new Dimension(225, 100);
		
		TagScrollPane.setMinimumSize(dim3);
		TagScrollPane.setMaximumSize(dim3);
		TagScrollPane.setPreferredSize(dim3);
		
		tagPanelGBC.gridx = 0;
		tagPanelGBC.gridy = 0;
		tagPanelGBC.insets = new Insets(0, 0, 0, 0);
		tagPanel.add(TagScrollPane, tagPanelGBC);
		
		JPanel AddPanel = new JPanel();
		AddPanel.setLayout(new BorderLayout());
		JTextField newTag = new JTextField(12);
		Dimension dimTag = new Dimension(200, 20);
		newTag.setMinimumSize(dimTag);
		AddPanel.add(newTag, BorderLayout.CENTER);
		JButton Add = new JButton("Add");
		Add.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!newTag.getText().equals("") && archive.getNumberOfMolecules() != 0) {
					imageMetaDataRecordChanged = true;
					imageMetaData.addTag(newTag.getText().trim());
					updateTagList();
					TagTableModel.fireTableDataChanged();
				}
			}
		});
		AddPanel.add(Add, BorderLayout.EAST);
		
		tagPanelGBC.gridx = 0;
		tagPanelGBC.gridy = 2;
		tagPanelGBC.insets = new Insets(5, 0, 5, 0);
		
		tagPanel.add(AddPanel, tagPanelGBC);
		
		JButton Remove = new JButton("Remove");
		Remove.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (TagTable.getSelectedRow() != -1 && archive.getNumberOfMolecules() != 0) {
					String tag = (String)TagTable.getValueAt(TagTable.getSelectedRow(), 0);
					imageMetaDataRecordChanged = true;
					imageMetaData.removeTag(tag);
					updateTagList();
					TagTableModel.fireTableDataChanged();
				}
			}
		});
		tagPanelGBC.gridx = 0;
		tagPanelGBC.gridy = 3;
		tagPanelGBC.anchor = GridBagConstraints.NORTHEAST;
		tagPanel.add(Remove, tagPanelGBC);
		
		return tagPanel;
	}
	
	public void saveCurrentRecord() {
		archive.putImageMetaData(imageMetaData);
	}
	
	public void updateAll() {
		if (archive.getNumberOfImageMetaDataRecords() == 0) {
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
			//this prevents overwriting when switching records
			//in the window..
			imageMetaData = archive.getImageMetaData(imageMetaData.getUID());
			Notes.setEditable(true);
		}
		imageMetaDataRecordChanged = false;
		
		//Update index table in case tags were changed
		if (imageMetaDataCount < archive.getNumberOfImageMetaDataRecords()) {
			imageMetaDataIndexTableModel.fireTableRowsInserted(imageMetaDataCount - 1, archive.getNumberOfImageMetaDataRecords() - 1);
			imageMetaDataCount = archive.getNumberOfImageMetaDataRecords();
		} else if (imageMetaDataCount > archive.getNumberOfImageMetaDataRecords()) {
			imageMetaDataIndexTableModel.fireTableRowsDeleted(archive.getNumberOfImageMetaDataRecords() - 1, imageMetaDataCount - 1);
			imageMetaDataCount = archive.getNumberOfImageMetaDataRecords();
		}
			
		//Update all entries...
		if (imageMetaDataCount != 0) {
			imageMetaDataIndexTableModel.fireTableRowsUpdated(0, imageMetaDataCount - 1);
		}
		
		updateParameterList();
		updateTagList();
		
		//Update Labels
		UIDLabel.setText(imageMetaData.getUID());
		Microscope.setText(imageMetaData.getMicroscopeName());
		DateLabel.setText(imageMetaData.getCollectionDate());
		SourcePath.setText(imageMetaData.getSourceDirectory());
		
		//Update DataTable
		DataTableModel.fireTableStructureChanged();
		resizeColumnWidth(DataTable);
		for (int i = 0; i < DataTable.getColumnCount(); i++)
			DataTable.getColumnModel().getColumn(i).sizeWidthToFit();
		
		//Update Parameter list
		ParameterTableModel.fireTableDataChanged();
		for (int i = 0; i < ParameterTable.getColumnCount(); i++)
			ParameterTable.getColumnModel().getColumn(i).sizeWidthToFit();
		
		//Update TagList
		TagTableModel.fireTableDataChanged();
		for (int i = 0; i < TagTable.getColumnCount(); i++)
			TagTable.getColumnModel().getColumn(i).sizeWidthToFit();
		
		//Update Comments
		Notes.setText(imageMetaData.getNotes());
				
		//Update Log
		log.setText(imageMetaData.getLog());
		
	}
	
	private void filterImageMetaDataIndex() {
        RowFilter<AbstractTableModel, Object> rf = null;
        //If current expression doesn't parse, don't update.
        try {
        	String searchString = imageMetaDataSearchField.getText();
        	
        	if (searchString.contains(",")) {
	        	String[] searchlist = searchString.split(",");
	            for (int i=0; i<searchlist.length; i++) {
	            	searchlist[i] = searchlist[i].trim();
	            }
	        
	            searchString = "";
	            for (int i=0; i<searchlist.length; i++) {
	            	searchString += "(?=.*?(" + searchlist[i] + "))";
	            }
        	}
        	
            rf = RowFilter.regexFilter(searchString, 0, 1, 2);
        } catch (java.util.regex.PatternSyntaxException e) {
            return;
        }
        imageMetaDataSorter.setRowFilter(rf);
        imageMetaDataIndex.updateUI();
    }
	
	//getters and setters
	public void setImageMetaData(MARSImageMetaData imageMetaData) {
		this.imageMetaData = imageMetaData;
	}
	
	public MARSImageMetaData getImageMetaData() {
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
