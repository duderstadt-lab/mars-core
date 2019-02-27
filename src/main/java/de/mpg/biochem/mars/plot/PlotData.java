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
package de.mpg.biochem.mars.plot;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import de.mpg.biochem.mars.table.*;
import ij.text.TextWindow;

public class PlotData {
	public String xColumn;
	public String yColumn;
	public String tableTitle;
	public String groupColumn;
	public int type = 0;

	public MARSResultsTable table;
	public double[] xs, seg_xs;
	public double[] ys, seg_ys;
	public Map<Integer, GroupIndices> GroupIndex, SegmentGroupIndex;
	public ArrayList<Integer> groupNumbers;
	
	//Add color setting?
	public Color color = Color.black;
	public Color segments_color = Color.red;
	
	public boolean hasGroups;
	
	//This is way too long..... Need to either pass Plotdialog or do something else to simplify.
	public PlotData(MARSResultsTable table, String xColumn, String yColumn, Color color, String groupColumn, int type, String tableTitle) {
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
	
	public MARSResultsTable getTable() {
		return table;
	}
}

