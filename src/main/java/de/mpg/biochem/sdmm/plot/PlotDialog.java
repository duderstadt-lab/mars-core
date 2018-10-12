package de.mpg.biochem.sdmm.plot;

import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.Color;
import java.lang.reflect.Field;
import java.util.Vector;

import de.mpg.biochem.sdmm.table.SDMMResultsTable;

public class PlotDialog extends GenericDialog implements DialogListener {
	private static final long serialVersionUID = 1L;

	private String xColumn;
	private String groupColumn;
	
	//Can be one for single curve mode and more for multicurve mode...
	private String[] yColumns;
	private Color[] color_choices;
	private Color[] segment_color_choices;
	
	//Indexes for retrieving the values from the arrays above.
	private int yColumn_index = 0;
	private int color_choice_index = 0;
	private int segment_color_choice_index = 0;
	
	private int type = 0;
	
	protected String[] colors = {"black", "blue", "cyan", "gray", "green", "magenta", "orange", "pink", "red", "yellow"};
	
	protected String[] SegColors = {"none", "black", "blue", "cyan", "gray", "green", "magenta", "orange", "pink", "red", "yellow"};
	
	//More Colors
	Color darkGreen = new Color(44, 160, 44);
	
	private int curveNumber = 1;
	
	private boolean groupsOption = false;
	
	public PlotDialog(String dialogTitle, SDMMResultsTable table, int curveNumber, boolean groupsOption) {
		super(dialogTitle);
		
		this.groupsOption = groupsOption;
		
		this.curveNumber = curveNumber;
		
		String[] columns = table.getColumnHeadings();
		String[] types = {"line plot", "scatter plot", "bar graph"};
		yColumns = new String[curveNumber];
		color_choices = new Color[curveNumber];
		segment_color_choices = new Color[curveNumber];
		
		addChoice("x_column", columns, "slice");
		
		if (curveNumber == 1) {
			addChoice("y_column", columns, "x");
			addChoice("color", colors, "black");
			addChoice("segments", SegColors, "red");
		} else {
			for (int i=0;i<curveNumber;i++) {
				addChoice("y" + (i+1) + "_column", columns, "x");
				addChoice("y" + (i+1) + "_color", colors, "black");
				addChoice("y" + (i+1) + "_segments", SegColors, "red");
			}
		}

		addChoice("type", types, types[type]);
		
		//Need to add a none options for groups
		String[] gColumns = new String[columns.length+1];
		gColumns[0] = "none";
		for (int i=1;i < columns.length+1; i++) {
			gColumns[i] = columns[i-1];
		}
		
		if (groupsOption)
			addChoice("group", gColumns, "none");
		
		update(this);
	}
	
	public String getXColumnName() {
		return xColumn;
	}
	
	public String getNextYColumnName() {
		String output = yColumns[yColumn_index];
		if (yColumn_index == yColumns.length - 1) {
			yColumn_index = 0;
		} else {
			yColumn_index++;
		}
		return output;
	}
	
	public int getPlotType() {
		return type;
	}
	
	public Color getNextSegmentCurveColor() {
        Color curveColor = segment_color_choices[segment_color_choice_index];
		
		if (segment_color_choice_index == segment_color_choices.length - 1) {
			segment_color_choice_index = 0;
		} else {
			segment_color_choice_index++;
		}

		return curveColor;
	}
	
	public Color getNextCurveColor() {
        Color curveColor = color_choices[color_choice_index];
		
		if (color_choice_index == color_choices.length - 1) {
			color_choice_index = 0;
		} else {
			color_choice_index++;
		}

		return curveColor;
	}
	
	public int getCurveType() {
		return type;
	}
	
	public String getGroupColumn() {
		return groupColumn;
	}
	
	private Color getColorFromName(String color_name) {
		if (color_name.equals("none"))
			return null;
		try {
			if (color_name.equals("green"))
				return darkGreen;
			Field field = Class.forName("java.awt.Color").getField(color_name);
			return (Color)field.get(null);
		} catch (Exception e) {
			//can't happen since only the colors specified above can be picked and those all exist...
		}
		return Color.black;
	}
	@Override
	public boolean dialogItemChanged(GenericDialog dialog, AWTEvent arg1) {
		update(dialog);
		return true;
	}
	
	public void update(GenericDialog dialog) {
		xColumn = dialog.getNextChoice();
		for (int i=0;i<curveNumber;i++) {
			yColumns[i] = dialog.getNextChoice();
			color_choices[i] = getColorFromName(dialog.getNextChoice());
			segment_color_choices[i] = getColorFromName(dialog.getNextChoice());
		}
		type = dialog.getNextChoiceIndex(); 
		
		if (groupsOption)
			groupColumn = dialog.getNextChoice();
	}
}

