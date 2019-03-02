/*******************************************************************************
 * Copyright (C) 2019, Karl Duderstadt
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

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

public class MAPropertiesPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	
	private HashMap<String, String> tagKeyMap = new HashMap<String, String>();
	
	private JTable hotKeyTable;
	private AbstractTableModel hotKeyTableModel;
	private String[] hotKeyList;
	
	public MAPropertiesPanel(HashMap<String, String> tagKeyMap) {
		this.tagKeyMap = tagKeyMap;
		
		setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.NORTHWEST;
		
		gbc.weightx = 1;
		gbc.weighty = 1;
		
		gbc.insets = new Insets(20, 20, 20, 20);
		
		add(buildHotKeyPanel(), gbc);
	}
	
	public JPanel buildHotKeyPanel() {
		updateHotKeyList();
		
		hotKeyTableModel = new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				if (columnIndex == 0) {
					return hotKeyList[rowIndex];
				}
				
				return tagKeyMap.get(hotKeyList[rowIndex]);
			}
			
			@Override
			public String getColumnName(int columnIndex) {
				if (columnIndex == 0)
					return "Keys";
				else
					return "Tag";
			}

			@Override
			public int getRowCount() {
				return hotKeyList.length;
			}
			
			@Override
			public int getColumnCount() {
				return 2;
			}
			
			@Override
			public void setValueAt(Object tag, int rowIndex, int columnIndex) {
				tagKeyMap.put(hotKeyList[rowIndex], (String)tag);
			}
			
			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex)  {
				return columnIndex > 0;
			}
		};
		
		JPanel hotKeyPanel = new JPanel();
		hotKeyPanel.setLayout(new GridBagLayout());
		GridBagConstraints hotKeyPanelGBC = new GridBagConstraints();
		hotKeyPanelGBC.anchor = GridBagConstraints.NORTHWEST;
		
		//Top, left, bottom, right
		hotKeyPanelGBC.insets = new Insets(5, 0, 5, 0);
		
		hotKeyPanelGBC.weightx = 1;
		hotKeyPanelGBC.weighty = 1;
		
		hotKeyPanelGBC.gridx = 0;
		hotKeyPanelGBC.gridy = 0;
		
		hotKeyPanel.add(new JLabel("Map Keys to Tags (e.g. 'control Z', 'alt B', "), hotKeyPanelGBC);
		hotKeyPanelGBC.gridy += 1;
		hotKeyPanel.add(new JLabel("'alt shift X'). Letter keys must be capitalized."), hotKeyPanelGBC);
		
		hotKeyTable = new JTable(hotKeyTableModel);
		hotKeyTable.setAutoCreateColumnsFromModel(true);
		hotKeyTable.setRowSelectionAllowed(true);
		hotKeyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		
		hotKeyTable.getColumnModel().getColumn(0).setMinWidth(100);
		
		//hotKeyTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		
		JScrollPane hotKeyScrollPane = new JScrollPane(hotKeyTable);
		
		Dimension dim2 = new Dimension(300, 250);
		
		//hotKeyScrollPane.setMinimumSize(dim2);
		hotKeyScrollPane.setMaximumSize(dim2);
		hotKeyScrollPane.setPreferredSize(dim2);
		
		hotKeyPanelGBC.gridy += 1;
		hotKeyPanel.add(hotKeyScrollPane, hotKeyPanelGBC);
		
		JPanel AddRemovePanel = new JPanel();
		JTextField newHotKey = new JTextField(12);
		Dimension dimParm = new Dimension(200, 20);
		newHotKey.setMinimumSize(dimParm);
		AddRemovePanel.add(newHotKey);
		JButton Add = new JButton("Add");
		Add.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!newHotKey.getText().equals("")) {
					tagKeyMap.put(newHotKey.getText().trim(), "");
					updateHotKeyList();
					hotKeyTableModel.fireTableDataChanged();
				}
			}
		});
		AddRemovePanel.add(Add);
		
		JButton Remove = new JButton("Remove");
		Remove.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (hotKeyTable.getSelectedRow() != -1) {
					String key = (String)hotKeyTable.getValueAt(hotKeyTable.getSelectedRow(), 0);
					tagKeyMap.remove(key);
					updateHotKeyList();
					hotKeyTableModel.fireTableDataChanged();
				}
			}
		});
		AddRemovePanel.add(Remove);
		
		hotKeyPanelGBC.gridy += 1;
		hotKeyPanel.add(AddRemovePanel, hotKeyPanelGBC);
		
		return hotKeyPanel;
	}
	
	public void updateHotKeyList() {		
		hotKeyList = new String[tagKeyMap.keySet().size()];
		tagKeyMap.keySet().toArray(hotKeyList);
	}
	
	public HashMap<String, String> getHotkeyList() {
		return tagKeyMap;
	}
	
	public JTable getHotKeyTable() {
		return hotKeyTable;
	}
}
