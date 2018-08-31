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
	
	//TextWindow log_window = new TextWindow("PlotData Log", "", 400, 600);
	
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
		if (hasGroups) {
			switch (type) {
			case 0:	// line plot
				plot.addLinePlot(xs, ys, GroupIndex.get(groupNumbers.get(group)).getStart(), GroupIndex.get(groupNumbers.get(group)).getEnd() + 1, getColor(), 1.0f, tableTitle);
				break;
			case 1:	// scatter plot
				plot.addScatterPlot(xs, ys, GroupIndex.get(groupNumbers.get(group)).getStart(), GroupIndex.get(groupNumbers.get(group)).getEnd() + 1, getColor(), 1.0f, tableTitle);
				break;
			case 2: //Bar graph
				plot.addBarGraph(xs, ys, GroupIndex.get(groupNumbers.get(group)).getStart(), GroupIndex.get(groupNumbers.get(group)).getEnd() + 1, getColor(), 1.0f, tableTitle);
				break;
			}
		} else {
			switch (type) {
			case 0:	// line plot
				plot.addLinePlot(xs, ys, 0, xs.length, getColor(), 1.0f, tableTitle);
				break;
			case 1:	// scatter plot
				plot.addScatterPlot(xs, ys, 0, xs.length, getColor(), 1.0f, tableTitle);
				break;
			case 2: //Bar graph
				plot.addBarGraph(xs, ys, 0, xs.length, getColor(), 1.0f, tableTitle);
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

