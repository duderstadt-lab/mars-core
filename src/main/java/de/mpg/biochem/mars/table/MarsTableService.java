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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.scijava.command.Command;
import org.scijava.display.DisplayService;
import org.scijava.event.EventHandler;
import org.scijava.event.EventService;
import org.scijava.io.event.DataOpenedEvent;
import org.scijava.log.LogService;
import org.scijava.object.ObjectService;
//import org.scijava.object.ObjectService;
import org.scijava.object.event.ObjectCreatedEvent;
import org.scijava.service.*;
import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

import de.mpg.biochem.mars.molecule.MoleculeArchive;
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

@SuppressWarnings({ "rawtypes", "unchecked" })
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
    
    @Parameter
    private ObjectService objectService;
	
	@Override
	public void initialize() {
		scriptService.addAlias(MarsTable.class);
		scriptService.addAlias(MarsTableService.class);
	}
	
	public void addTable(MarsTable table) {
		objectService.addObject(table);
	}
	
	public void removeTable(String title) {
		objectService.removeObject(getTable(title));
	}
	
	public void removeTable(MarsTable table) {
		objectService.removeObject(table);
	}
	
	public boolean rename(String oldName, String newName) {
		List<MarsTable> tables = getTables();
		
		if (tables.stream().anyMatch(archive -> archive.getName().equals(oldName))) {
			logService.error("No MarsTables exists with the name " + oldName + ".");
			return false;
		}
		
		if (tables.stream().anyMatch(archive -> archive.getName().equals(newName))) {
			logService.error("A MarsTable is already open with the name " + newName + ". Choose another name.");
			return false;
		} else {
			MarsTable table = tables.stream().filter(t -> t.getName().equals(oldName)).findFirst().get();
			table.setName(newName);
			displayService.getDisplay(oldName).setName(newName);
			return true;
		}
	}
	
	public ArrayList<String> getTableNames() {
		List<MarsTable> archives = getTables();
		
		return (ArrayList<String>) archives.stream().map(table -> table.getName()).collect(Collectors.toList());
	}
	
	public ArrayList<String> getColumnNames() {
		Set<String> columnSet = new LinkedHashSet<String>();
		List<MarsTable> tables = getTables();
		
		tables.forEach(table -> columnSet.addAll(table.getColumnHeadingList()));
		
		ArrayList<String> columns = new ArrayList<String>();
		columns.addAll(columnSet);
		
		columns.sort(String::compareToIgnoreCase);
		
		return columns;
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
	
	public boolean contains(String key) {
		return getTables().stream().anyMatch(archive -> archive.getName().equals(key));
	}
	
	public MarsTable getTable(String name) {
		return getTables().stream().filter(a -> a.getName().equals(name)).findFirst().get();
	}
	
	public List<MarsTable> getTables() { 
		return (List) objectService.getObjects(MarsTable.class);
	}

	@Override
	public Class<MarsTableService> getPluginType() {
		return MarsTableService.class;
	}
}
