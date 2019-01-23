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
package de.mpg.biochem.mars.table;

import java.awt.Frame;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.File;
import java.nio.file.*;
import java.nio.charset.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.scijava.command.Command;
import org.scijava.display.DisplayService;
import org.scijava.event.EventHandler;
import org.scijava.log.LogService;
//import org.scijava.object.ObjectService;
import org.scijava.object.event.ObjectCreatedEvent;
import org.scijava.service.*;
import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

import de.mpg.biochem.mars.molecule.MoleculeArchiveService;

import org.scijava.table.*;
import net.imglib2.type.numeric.RealType;
import net.imagej.ImageJService;
import net.imagej.display.WindowService;

import org.scijava.plugin.AbstractPTService;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.PluginInfo;
import org.scijava.script.ScriptService;
import org.scijava.service.Service;

@Plugin(type = Service.class)
public class ResultsTableService extends AbstractPTService<ResultsTableService> implements ImageJService {
	
    @Parameter
    private UIService uiService;
    
    @Parameter
    private LogService logService;
    
    @Parameter
    private ScriptService scriptService;
    
    @Parameter
    private DisplayService displayService;
    
	private Map<String, MARSResultsTable> tables;
	
	@Override
	public void initialize() {
		// This Service method is called when the service is first created.
		tables = new LinkedHashMap<>();
		
		//This allow for just the class name as an input 
		//in scripts. Otherwise the whole path would have to be given..
		scriptService.addAlias(MARSResultsTable.class);
		scriptService.addAlias(ResultsTableService.class);
	}
	
	public void addResultsTable(MARSResultsTable table) {
		String name = table.getName();
		int num = 1;	    
	    while (tables.containsKey(name)) {
	    	if (num == 1) {
	    		name = name + num;
	    	} else  {
	    		name = name.substring(0, name.length() - String.valueOf(num-1).length()) + num;
	    	}
	    	num++;
	    }
	    
	    table.setName(name);
		tables.put(table.getName(), table);
	}
	
	public void removeResultsTable(String name) {
		if (tables.containsKey(name)) {
			tables.remove(name);
			displayService.getDisplay(name).close();
		}
	}
	
	public void removeResultsTable(MARSResultsTable table) {
		if (tables.containsKey(table.getName())) {
			tables.remove(table.getName());
			displayService.getDisplay(table.getName()).close();
		}
	}
	
	public boolean rename(String oldName, String newName) {
		if (tables.containsKey(newName)) {
			logService.error("A Table is already open with that name. Choose another name.");
			return false;
		} else {
			tables.get(oldName).setName(newName);
			MARSResultsTable tab = tables.remove(oldName);
			tables.put(newName, tab);
			displayService.getDisplay(oldName).setName(newName);
			return true;
		}
	}
	
	public ArrayList<String> getTableNames() {
		return new ArrayList<String>(tables.keySet());
	}
	
	public ArrayList<String> getColumnNames() {
		ArrayList<String> columns = new ArrayList<String>();
	
		for (MARSResultsTable table: tables.values()) {
			for (int i=0;i<table.getColumnCount();i++) {
				if(!columns.contains(table.getColumnHeader(i)))
					columns.add(table.getColumnHeader(i));
			}
		}
		
		return columns;
	}
	
	public boolean isUniqueName(final String name) {
		for (final String tableName : tables.keySet()) {
			if (name.equalsIgnoreCase(tableName)) {
				return false;
			}
		}
		return true;
	}
	
	public UIService getUIService() {
		return uiService;
	}
	
	//Utility method returning HashMap of molecule numbers and start and stop index positions.
	//Here we are assuming the table is already sorted on the groupColumn..
	public static LinkedHashMap<Integer, GroupIndices> find_group_indices(MARSResultsTable table, String groupColumn) {
		// make sure we sort on groupColumn
		//ResultsTableSorter.sort(table, true, groupColumn);
		
		LinkedHashMap<Integer, GroupIndices> map = new LinkedHashMap<Integer, GroupIndices>();
		
		List<Integer> indices = new ArrayList<Integer>();
		indices.add(0);
		
		int key = (int)table.getValue(groupColumn, 0);
		
		for (int i = 1; i < table.getRowCount(); i++) {
			if (key != (int)table.getValue(groupColumn,i)) {
				//Means we have encountered a new group so we add the key and start and end values
				indices.add(i-1);
				map.put(key, new GroupIndices(indices.get(0), indices.get(1)));
				
				//Then we clean the indices and add the start of the next group
				indices.clear();
				indices.add(i);
				key = (int)table.getValue(groupColumn, i);
			}
		}

		indices.add(table.getRowCount() - 1);
		map.put(key, new GroupIndices(indices.get(0), indices.get(1)));
		
		return map;
	}
	
	public static boolean sort(MARSResultsTable table, final boolean ascending, String... columns) {
		
		for (int index=0; index<columns.length; index++) {	
			if (!table.hasColumn(columns[index])) {
				System.out.println("MARSResultsTable sort failed because one of the columns given was not found.");
				System.out.println("Are you sure the column name is a match to a column in the table?");
				return false;
			}
		}
		
		ResultsTableList list = new ResultsTableList(table);
		
		final int[] columnIndexes = new int[columns.length];
		
		for (int i = 0; i < columns.length; i++)
			columnIndexes[i] = table.getColumnIndex(columns[i]);
		
		Collections.sort(list, new Comparator<double[]>() {
			
			@Override
			public int compare(double[] o1, double[] o2) {				
				for (int columnIndex: columnIndexes) {
					int groupDifference = Double.compare(o1[columnIndex], o2[columnIndex]); 
				
					if (groupDifference != 0)
						return ascending ? groupDifference : -groupDifference;
				}
				return 0;
			}
			
		});
		
		return true;
	}
	
	public MARSResultsTable getResultsTable(String name) {
		return tables.get(name);
	}
	
	public void deleteRows(MARSResultsTable table, int[] rows) {
		if (rows.length == 0)
			return;
		
		int pos = 0;
		int rowsIndex = 0;
		for (int i = 0; i < table.getRowCount(); i++) {
			if (rowsIndex < rows.length) {
				if (rows[rowsIndex] == i) {
					rowsIndex++;
					continue;
				}
			}
			
			if (pos != i) {
				//means we need to move row i to position row.
				for (int j=0;j<table.getColumnCount();j++)
					table.set(j, pos, table.get(j, i));
			}
			pos++;
		}
		
		// delete last rows
		for (int row = table.getRowCount() - 1; row > pos-1; row--)
			table.removeRow(row);
	}
	
	public boolean contains(String key) {
		return tables.containsKey(key);
	}

	@Override
	public Class<ResultsTableService> getPluginType() {
		return ResultsTableService.class;
	}
}
