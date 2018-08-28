package de.mpg.biochem.sdmm.table;

import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

//import org.scijava.widget.FileWidget;

import ij.io.SaveDialog;

public class SDMMResultsTableWindow implements ActionListener {
	SDMMResultsTable results;
	
	ResultsTableService resultsTableService;
	
	private JFrame frame;
	JTable table;
	JScrollPane scrollPane;
	private AbstractTableModel tableModel;
	private JMenuItem saveAsMenuItem = new JMenuItem("Save As", KeyEvent.VK_S);
	private JMenuItem renameMenuItem = new JMenuItem("Rename", KeyEvent.VK_R);
	private JMenuItem duplicateMenuItem = new JMenuItem("Duplicate", KeyEvent.VK_D);
	private JMenuItem cutMenuItem = new JMenuItem("Cut");
	private JMenuItem copyMenuItem = new JMenuItem("Copy", KeyEvent.VK_C);
	private JMenuItem clearMenuItem = new JMenuItem("Clear");
	private JMenuItem selectAllMenuItem = new JMenuItem("Select All", KeyEvent.VK_A);
	//private JMenuItem sortMenuItem = new JMenuItem("Sort", KeyEvent.VK_S);
	//private JMenuItem plotMenuItem = new JMenuItem("Plot", KeyEvent.VK_P);
	//private JMenuItem filterMenuItem = new JMenuItem("Filter", KeyEvent.VK_F);
	//private JMenuItem toImageJResultsTableMenuItem = new JMenuItem("To ImageJ Results Table");
	
	//static so that table locations are offset...
	static int pos_x = 100;
	static int pos_y = 130;
	static int offsetX = 0;
	
	public SDMMResultsTableWindow(String name, SDMMResultsTable results, ResultsTableService resultsTableService) {
		this.results = results;
		this.resultsTableService = resultsTableService;
		results.setWindow(this);
		createFrame(name);
	}
	
	public void update() {
		for (int i = 0; i < table.getColumnCount(); i++)
			table.getColumnModel().getColumn(i).setPreferredWidth(75);
		
		tableModel.fireTableStructureChanged();
	}
	
	private void createFrame(String name) {
		tableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				if (columnIndex == 0)
					return rowIndex + 1;
				
				return results.getValue(columnIndex - 1, rowIndex);
			}
			
			@Override
			public String getColumnName(int columnIndex) {
				if (columnIndex == 0)
					return "Row";
				
				return results.getColumnHeader(columnIndex - 1);
			}

			@Override
			public int getRowCount() {
				return results.getRowCount();
			}
			
			@Override
			public int getColumnCount() {
				return results.getColumnCount() + 1;
			}
			
			@Override
			public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
				double value = Double.parseDouble((String)aValue);
				results.set(columnIndex - 1, rowIndex, value);
			}
			
			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex)  {
				return columnIndex > 0;
			}
			
		};
		
		table = new JTable(tableModel);
		table.setRowSelectionAllowed(true);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		
		for (int i = 0; i < table.getColumnCount(); i++)
			table.getColumnModel().getColumn(i).setPreferredWidth(75);
		
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		scrollPane = new JScrollPane(table);
		JMenuBar mb = new JMenuBar();
		
		// file menu
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);
		
		fileMenu.add(saveAsMenuItem);
		fileMenu.add(renameMenuItem);
		fileMenu.add(duplicateMenuItem);
		
		mb.add(fileMenu);
		
		// edit menu
		JMenu editMenu = new JMenu("Edit");
		editMenu.setMnemonic(KeyEvent.VK_E);
		
		editMenu.add(cutMenuItem);
		editMenu.add(copyMenuItem);
		editMenu.add(clearMenuItem);
		editMenu.add(selectAllMenuItem);
		//editMenu.addSeparator();
		//editMenu.add(filterMenuItem);
		//editMenu.add(sortMenuItem);
		//editMenu.add(plotMenuItem);
		editMenu.addSeparator();
		//editMenu.add(toImageJResultsTableMenuItem);
		
		mb.add(editMenu);

		// set action listeners
		saveAsMenuItem.addActionListener(this);
		renameMenuItem.addActionListener(this);
		duplicateMenuItem.addActionListener(this);
		cutMenuItem.addActionListener(this);
		copyMenuItem.addActionListener(this);
		clearMenuItem.addActionListener(this);
		selectAllMenuItem.addActionListener(this);
		//filterMenuItem.addActionListener(this);
		//sortMenuItem.addActionListener(this);
		//plotMenuItem.addActionListener(this);
		//toImageJResultsTableMenuItem.addActionListener(this);
		
		frame = new JFrame(name);
		frame.setSize(400, 300);
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
		frame.add(scrollPane);
		frame.setJMenuBar(mb);
		frame.setVisible(true);
	}
	
	public SDMMResultsTable getResults() {
		return results;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == renameMenuItem) {
			String name = JOptionPane.showInputDialog("Table name", frame.getTitle());
			rename(name);
		} else if (e.getSource() == clearMenuItem) {
			clear();
		} else if (e.getSource() == selectAllMenuItem) {
			selectAll();
		} else if (e.getSource() == saveAsMenuItem) {
			saveAs();
		}
		
		/*
		//if (e.getSource() == saveAsMenuItem)
			//saveAs();
		if (e.getSource() == renameMenuItem) {
			String name = JOptionPane.showInputDialog("Table name", frame.getTitle());
			rename(name);
		} //else if (e.getSource() == duplicateMenuItem)
			//duplicate();
		else if (e.getSource() == cutMenuItem)
			cut();
		else if (e.getSource() == copyMenuItem)
			copy();
		else if (e.getSource() == clearMenuItem)
			clear();
		else if (e.getSource() == selectAllMenuItem)
			selectAll();
		//else if (e.getSource() == filterMenuItem)
		//	filter();
		//else if (e.getSource() == sortMenuItem)
		//	sort();
		//else if (e.getSource() == plotMenuItem)
			//plot();
		
		*/
	}
	protected boolean saveAs() {
		//String filename = frame.getTitle();
		//if (!filename.endsWith(".csv") && !filename.endsWith(".CSV")) {
		//	filename += ".csv";
		//}
		
		//This is not working properly in the current ImageJ API, I am not sure why
		//File file = new File(filename);
		//file = resultsTableService.getUIService().chooseFile(file, FileWidget.SAVE_STYLE);
		
		SaveDialog sd = new SaveDialog("Save Table", frame.getTitle(), ".csv");
        String file = sd.getFileName();
        if (file==null) return false;
        String path = sd.getDirectory() + file;
		try {
			results.saveAs(path);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return true;
	}
	
	public void rename(String name) {
		if (name != null) {
			resultsTableService.rename(frame.getTitle(), name);
			frame.setTitle(name);
		}
	}
	
	public void close() {
		frame.setVisible(false);
		frame.dispose();
		
		results.clear();
		resultsTableService.removeResultsTable(results.getName());
	}
	
	/*
	protected void duplicate() {
		new ResultsTableWindow((ResultsTable) results.clone(), WindowManager.getUniqueName(frame.getTitle()));
	}
	
	protected void cut() {
		copy();
		clear();
	}
	
	protected void copy() {
		
		final int[] selectedRows = table.getSelectedRows();
		
		Transferable transferable = new Transferable() {
			
			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor) {
				return DataFlavor.imageFlavor.equals(flavor);
			}
			
			@Override
			public DataFlavor[] getTransferDataFlavors() {
				return new DataFlavor[]{DataFlavor.stringFlavor};
			}
			
			@Override
			public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
				
				if (flavor.equals(DataFlavor.stringFlavor)) {
					String csv = "";
					
					for (int i = 0; i < selectedRows.length; i++)
						csv += results.getRowAsString(selectedRows[i]) + "\n";
					
					return csv;
				}
				else {
					throw new UnsupportedFlavorException(flavor);
				}
			}
		};
		
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
	}
	
	*/
	
	protected void clear() {
		resultsTableService.delete(results, table.getSelectedRows());
		
		table.clearSelection();
		tableModel.fireTableDataChanged();
	}
	
	protected void selectAll() {
		table.selectAll();
	}
}
