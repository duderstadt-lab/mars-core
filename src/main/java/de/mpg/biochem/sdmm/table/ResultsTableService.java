package de.mpg.biochem.sdmm.table;

import java.awt.Frame;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.File;
import java.nio.file.*;
import java.nio.charset.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.scijava.command.Command;
import org.scijava.event.EventHandler;
import org.scijava.log.LogService;
//import org.scijava.object.ObjectService;
import org.scijava.object.event.ObjectCreatedEvent;
import org.scijava.service.*;
import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

import net.imagej.table.DoubleColumn;
import net.imglib2.type.numeric.RealType;
import net.imagej.ImageJService;

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
	
    //@Parameter
    //private ObjectService objectService;
    
	private Map<String, SDMMResultsTable> tables;
	
	@Override
	public void initialize() {
		// This Service method is called when the service is first created.
		tables = new LinkedHashMap<>();
		
		//This allow for just the class name as an input 
		//in scripts. Otherwise the whole path would have to be given..
		scriptService.addAlias(SDMMResultsTable.class);
		scriptService.addAlias(SDMMGenericTable.class);
	}
	
	public void addTable(SDMMResultsTable table) {
		tables.put(table.getName(), table);
		//objectService.addObject(table);
		
	}
	
	public void removeResultsTable(String name) {
		if (tables.containsKey(name)) {
			//objectService.removeObject(tables.get(name));
			tables.remove(name);		
		}
	}
	
	public void removeResultsTable(SDMMResultsTable table) {
		if (tables.containsKey(table.getName())) {
			//objectService.removeObject(table);
			tables.remove(table.getName());
		}
	}
	
	public void rename(String oldName, String newName) {
		tables.get(oldName).setName(newName);
		SDMMResultsTable tab = tables.remove(oldName);
		tables.put(newName, tab);
	}
	
	public ArrayList<String> getTableNames() {
		return new ArrayList<String>(tables.keySet());
	}
	
	public ArrayList<String> getColumnNames() {
		ArrayList<String> columns = new ArrayList<String>();
	
		for (SDMMResultsTable table: tables.values()) {
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
	public LinkedHashMap<Integer, GroupIndices> find_group_indices(SDMMResultsTable table, String groupColumn) {
		// make sure we sort on groupColumn
		//ResultsTableSorter.sort(table, true, groupColumn);
		
		LinkedHashMap<Integer, GroupIndices> map = new LinkedHashMap<Integer, GroupIndices>();
		
		List<Integer> indices = new ArrayList<Integer>();
		indices.add(0);
		
		int key = table.get(groupColumn, 0).intValue();
		
		for (int i = 1; i < table.getRowCount(); i++) {
			if (key != table.get(groupColumn,i)) {
				//Means we have encountered a new group so we add the key and start and end values
				indices.add(i-1);
				map.put(key, new GroupIndices(indices.get(0), indices.get(1)));
				
				//Then we clean the indices and add the start of the next group
				indices.clear();
				indices.add(i);
				key = table.get(groupColumn, i).intValue();
			}
		}

		indices.add(table.getRowCount() - 1);
		map.put(key, new GroupIndices(indices.get(0), indices.get(1)));
		
		return map;
	}
	
	public SDMMResultsTable getResultsTable(String name) {
		return tables.get(name);
	}
	
	public void delete(SDMMResultsTable table, int[] rows) {
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
				for (int j=1;j<table.getColumnCount();j++)
					table.set(j, pos, table.get(j, i));
			}
			pos++;
		}
		
		// delete last rows
		for (int row = table.getRowCount() - 1; row > pos-1; row--)
			table.removeRow(row);
	}

	@Override
	public Class<ResultsTableService> getPluginType() {
		return ResultsTableService.class;
	}
	
	// Event Handlers
	/*
	@EventHandler
	protected void onEvent(final ObjectCreatedEvent event) {
		logService.info("event " + event.hashCode());
		if (((Class) SDMMResultsTable.class).isAssignableFrom(event.getClass())) {
			logService.info("A new table was created with the name " + ((SDMMResultsTable)event.getObject()).getName());
		}
	}
	*/
	//(List) objectService.getObjects(Display.class);
}
