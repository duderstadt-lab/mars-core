/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2022 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package de.mpg.biochem.mars.table;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.imagej.ImageJService;

import org.scijava.display.DisplayService;
import org.scijava.event.EventService;
import org.scijava.log.LogService;
import org.scijava.object.ObjectService;
import org.scijava.plugin.AbstractPTService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.script.ScriptService;
import org.scijava.service.Service;
import org.scijava.table.DoubleColumn;
import org.scijava.table.GenericColumn;
import org.scijava.ui.UIService;

import ij.measure.ResultsTable;

@SuppressWarnings({ "rawtypes", "unchecked" })
@Plugin(type = Service.class)
public class MarsTableService extends AbstractPTService<MarsTableService>
	implements ImageJService
{

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
		removeTable(getTable(title));
	}

	public void removeTable(MarsTable table) {
		if (table != null) objectService.removeObject(table);

		if (table != null && displayService.getDisplays(table).size() > 0)
			objectService.removeObject(displayService.getDisplays(table).get(0));
	}

	public boolean rename(String oldName, String newName) {
		List<MarsTable> tables = getTables();

		if (tables.stream().anyMatch(archive -> archive.getName().equals(
			oldName)))
		{
			logService.error("No MarsTables exists with the name " + oldName + ".");
			return false;
		}

		if (tables.stream().anyMatch(archive -> archive.getName().equals(
			newName)))
		{
			logService.error("A MarsTable is already open with the name " + newName +
				". Choose another name.");
			return false;
		}
		else {
			MarsTable table = tables.stream().filter(t -> t.getName().equals(oldName))
				.findFirst().get();
			table.setName(newName);
			displayService.getDisplay(oldName).setName(newName);
			return true;
		}
	}

	public ArrayList<String> getTableNames() {
		List<MarsTable> archives = getTables();

		return (ArrayList<String>) archives.stream().map(table -> table.getName())
			.collect(Collectors.toList());
	}

	public MarsTable createTable(ResultsTable resultsTable) {
		MarsTable table = new MarsTable("Imported IJ1 ResultsTable");

		String[] columnHeadings = resultsTable.getHeadings();

		// For now we assume it is entirely numbers
		for (int i = 0; i < columnHeadings.length; i++) {
			DoubleColumn col = new DoubleColumn(columnHeadings[i]);
			for (int row = 0; row < resultsTable.getCounter(); row++)
				col.add(resultsTable.getValue(columnHeadings[i], row));
			table.add(col);
		}

		return table;
	}

	public UIService getUIService() {
		return uiService;
	}

	// Utility method returning HashMap of molecule numbers and start and stop
	// index positions.
	// Here we are assuming the table is already sorted on the groupColumn..
	public static Map<String, GroupIndices> find_group_indices(
		MarsTable table, String groupColumn)
	{
		// Should we sort?

		Map<String, GroupIndices> map =
			new LinkedHashMap<>();

		int groupStartIndex = 0;

		if (table.get(groupColumn) instanceof GenericColumn) {
			String key = table.getStringValue(groupColumn, 0);
			for (int i = 1; i < table.getRowCount(); i++) {
				if (!key.equals(table.getStringValue(groupColumn, i))) {
					// Means we have encountered a new group so we add the key and start and
					// end values
					map.put(key, new GroupIndices(groupStartIndex, i - 1));
	
					groupStartIndex = i;
					key = table.getStringValue(groupColumn, i);
				}
			}
			map.put(key, new GroupIndices(groupStartIndex, table.getRowCount() - 1));
		} else if (table.get(groupColumn) instanceof DoubleColumn) {
			double key = table.getValue(groupColumn, 0);
			for (int i = 1; i < table.getRowCount(); i++) {
				if (key != table.getValue(groupColumn, i)) {
					// Means we have encountered a new group so we add the key and start and
					// end values
					map.put(String.valueOf(key), new GroupIndices(groupStartIndex, i - 1));
	
					groupStartIndex = i;
					key = table.getValue(groupColumn, i);
				}
			}
			map.put(String.valueOf(key), new GroupIndices(groupStartIndex, table.getRowCount() - 1));
		}
			
		return map;
	}

	public boolean contains(String key) {
		return getTables().stream().anyMatch(n -> n.getName().equals(key));
	}

	public MarsTable getTable(String name) {
		return getTables().stream().filter(a -> a.getName().equals(name))
			.findFirst().get();
	}

	public List<MarsTable> getTables() {
		return (List) objectService.getObjects(MarsTable.class);
	}

	@Override
	public Class<MarsTableService> getPluginType() {
		return MarsTableService.class;
	}
}
