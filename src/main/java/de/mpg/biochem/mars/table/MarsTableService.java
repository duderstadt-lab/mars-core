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
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
import org.scijava.event.EventService;
import org.scijava.io.event.DataOpenedEvent;
import org.scijava.log.LogService;
//import org.scijava.object.ObjectService;
import org.scijava.object.event.ObjectCreatedEvent;
import org.scijava.service.*;
import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.table.event.MarsTableDeletedEvent;

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
public class MarsTableService extends AbstractPTService<MarsTableService> implements ImageJService {
	
    @Parameter
    private UIService uiService;
    
    @Parameter
    private LogService logService;
    
    @Parameter
    private ScriptService scriptService;
    
    @Parameter
    private EventService eventService;
    
    @Parameter
    private DisplayService displayService;
    
	private Map<String, MarsTable> tables;
	
	@Override
	public void initialize() {
		// This Service method is called when the service is first created.
		tables = new LinkedHashMap<>();
		
		//This allow for just the class name as an input 
		//in scripts. Otherwise the whole path would have to be given..
		scriptService.addAlias(MarsTable.class);
		scriptService.addAlias(MarsTableService.class);
	}
	
	public void addTable(MarsTable table) {
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
	
	public void removeTable(String name) {
		if (tables.containsKey(name)) {
			//eventService.publish(new MarsTableDeletedEvent(tables.get(name)));
			tables.remove(name);
			displayService.getDisplay(name).close();
		}
	}
	
	public void removeTable(MarsTable table) {
		if (tables.containsKey(table.getName())) {
			//eventService.publish(new MarsTableDeletedEvent(tables));
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
			MarsTable tab = tables.remove(oldName);
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
	
		for (MarsTable table: tables.values()) {
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
	public static LinkedHashMap<Integer, GroupIndices> find_group_indices(MarsTable table, String groupColumn) {
		// make sure we sort on groupColumn
		//MarsTableSorter.sort(table, true, groupColumn);
		
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
	
	public MarsTable getTable(String name) {
		return tables.get(name);
	}
	
	public boolean contains(String key) {
		return tables.containsKey(key);
	}

	@Override
	public Class<MarsTableService> getPluginType() {
		return MarsTableService.class;
	}
}
