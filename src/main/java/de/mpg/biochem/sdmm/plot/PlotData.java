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
package de.mpg.biochem.sdmm.plot;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import de.mpg.biochem.sdmm.table.*;
import ij.text.TextWindow;

public class PlotData {
	public String xColumn;
	public String yColumn;
	public String tableTitle;
	public String groupColumn;
	public int type = 0;

	public SDMMResultsTable table;
	public double[] xs, seg_xs;
	public double[] ys, seg_ys;
	public Map<Integer, GroupIndices> GroupIndex, SegmentGroupIndex;
	public ArrayList<Integer> groupNumbers;
	
	//Add color setting?
	public Color color = Color.black;
	public Color segments_color = Color.red;
	
	public boolean hasGroups;
	
	//This is way too long..... Need to either pass Plotdialog or do something else to simplify.
	public PlotData(SDMMResultsTable table, String xColumn, String yColumn, Color color, String groupColumn, int type, String tableTitle) {
		this.table = table;
		this.xColumn = xColumn;
		this.yColumn = yColumn;
		this.color = color;
		this.groupColumn = groupColumn;
		this.type = type;
		this.tableTitle = tableTitle;
		
		import_data();
	}
	
	public void import_data() {
		// get values
		xs = table.getColumnAsDoubles(xColumn);
		ys = table.getColumnAsDoubles(yColumn);
		
		if (!groupColumn.equals("none")) {
			hasGroups = true;
			GroupIndex = ResultsTableService.find_group_indices(table, groupColumn);
			groupNumbers = new ArrayList<Integer>();
			
			//Let's get a list of all GroupNumbers (usually molecule numbers)
			Object[] Allkeys = GroupIndex.keySet().toArray();
			for (int i=0;i < Allkeys.length;i++) {
				groupNumbers.add((int)Allkeys[i]);
			}
		} else {
			hasGroups = false;
		}
	}
	
	public void drawCurve(Plot plot, int group) {
		String curveName = tableTitle + " - " + yColumn + " vs " + xColumn; 
		
		if (hasGroups) {
			switch (type) {
			case 0:	// line plot
				plot.addLinePlot(xs, ys, GroupIndex.get(groupNumbers.get(group)).getStart(), GroupIndex.get(groupNumbers.get(group)).getEnd() + 1, getColor(), 1.0f, curveName);
				break;
			case 1:	// scatter plot
				plot.addScatterPlot(xs, ys, GroupIndex.get(groupNumbers.get(group)).getStart(), GroupIndex.get(groupNumbers.get(group)).getEnd() + 1, getColor(), 1.0f, curveName);
				break;
			case 2: //Bar graph
				plot.addBarGraph(xs, ys, GroupIndex.get(groupNumbers.get(group)).getStart(), GroupIndex.get(groupNumbers.get(group)).getEnd() + 1, getColor(), 1.0f, curveName);
				break;
			}
		} else {
			switch (type) {
			case 0:	// line plot
				plot.addLinePlot(xs, ys, 0, xs.length, getColor(), 1.0f, curveName);
				break;
			case 1:	// scatter plot
				plot.addScatterPlot(xs, ys, 0, xs.length, getColor(), 1.0f, curveName);
				break;
			case 2: //Bar graph
				plot.addBarGraph(xs, ys, 0, xs.length, getColor(), 1.0f, curveName);
				break;
			}
		}
	}
	  
	public int getGroupStartRow(int group) {
		return GroupIndex.get(groupNumbers.get(group)).getStart();
	}
	
	public int getGroupEndRow(int group) {
		return GroupIndex.get(groupNumbers.get(group)).getEnd();
	}
	
	public Color getColor() {
		return color;
	}
	
	public Color getSegmentsColor() {
		return segments_color;
	}
	
	public boolean hasGroups() {
		return hasGroups;
	}
	
	public int numberGroups() {
		return GroupIndex.size();
	}
	
	public int getGroupNumber(int groupNumIndex) {
		return groupNumbers.get(groupNumIndex);
	}
	
	public int getGroupIndex(int molecule) {
		return groupNumbers.indexOf(molecule);
	}
	
	public String xColumnName() {
		return xColumn;
	}
	
	public String yColumnName() {
		return yColumn;
	}
	
	public SDMMResultsTable getTable() {
		return table;
	}
}

