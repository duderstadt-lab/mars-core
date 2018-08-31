package de.mpg.biochem.sdmm.plot;

import ij.IJ;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import de.mpg.biochem.sdmm.molecule.Molecule;
import de.mpg.biochem.sdmm.table.SDMMResultsTable;

public class PlotPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	
	protected String[] colors = {"black", "blue", "cyan", "gray", "green", "dark green", "magenta", "orange", "pink", "red", "yellow"};
	
	protected String[] SegColors = {"none", "black", "blue", "cyan", "gray", "green", "dark green", "magenta", "orange", "pink", "red", "yellow"};
	
	//More Colors
	Color darkGreen = new Color(25, 123, 48);
	
	private JComboBox yColumnComboBox;
	private JComboBox yColor;
	private JComboBox SegmentColor;
	private JComboBox typeComboBox = new JComboBox(new String[]{"Line", "Scatter"});
	public Plot plot = new Plot();
	private String caption = "";
	
	private int type = 0;
	private String xColumnName;
	private String yColumnName;
	private String curveColor;
	private String segmentColor;
	
	private Molecule molecule;
	
	public PlotPanel(String xColumnName, Molecule molecule) {
		this.xColumnName = xColumnName;
		
		String[] columns = molecule.getDataTable().getColumnHeadings();
		yColumnComboBox = new JComboBox(columns);
		yColor = new JComboBox(colors);
		SegmentColor = new JComboBox(SegColors);
		
		
		JPanel panel = new JPanel(new GridLayout(0, 2));
		panel.add(new JLabel("y column"));
		yColumnComboBox.setSelectedItem("bps");
		panel.add(yColumnComboBox);
		
		panel.add(new JLabel("color"));
		yColor.setSelectedItem("black");
		panel.add(yColor);
		
		panel.add(new JLabel("plot type"));
		typeComboBox.setSelectedItem("Line");
		panel.add(typeComboBox);
		
		panel.add(new JLabel("segments"));
		SegmentColor.setSelectedItem("red");
		panel.add(SegmentColor);
		
		JPanel leftPanel = new JPanel();
		leftPanel.add(panel);
		
		setLayout(new BorderLayout());
		add(leftPanel, BorderLayout.WEST);
		
		JPanel whitePLOT = new JPanel();
		whitePLOT.setBackground(Color.WHITE);
		whitePLOT.setLayout(new BorderLayout());
		whitePLOT.add(plot, BorderLayout.CENTER);
		add(whitePLOT, BorderLayout.CENTER);
	}

	public void showPlot() {
		plot.clear();
		
		//First let's read in all the information from the selectors
  		yColumnName = (String)yColumnComboBox.getSelectedItem();
  		if (xColumnName.equals(" ") || yColumnName.equals(" ")) 
  			return;
  		segmentColor = (String)SegmentColor.getSelectedItem();
  		curveColor = (String)yColor.getSelectedItem();
		
		caption = String.format("UID = %s", molecule.getUID());
		
  		type = typeComboBox.getSelectedIndex();
  		
      	PlotProperties curve = new PlotProperties("Curve", xColumnName, yColumnName, getColorFromName(curveColor), type, getColorFromName(segmentColor));
		drawCurve(curve);
		
		plot.setBackground(Color.WHITE);
		plot.setPlotTitle(" ");
  		plot.setxAxisLabel(xColumnName);
  		plot.setyAxisLabel(yColumnName);
  		plot.setMolecule(molecule);
  		plot.resetBounds();
		plot.repaint();
	}
	
	public void drawCurve(PlotProperties props) {
		double[] xs = molecule.getDataTable().getColumnAsDoubles(props.xColumnName());
		double[] ys = molecule.getDataTable().getColumnAsDoubles(props.yColumnName());
		
		switch (props.getType()) {
		case 0:	// line plot
			plot.addLinePlot(xs, ys, 0, xs.length, props.getColor(), 1.0f, props.getCurveName());
			break;
		case 1:	// scatter plot
			plot.addScatterPlot(xs, ys, 0, xs.length, props.getColor(), 1.0f, props.getCurveName());
			break;
		case 2: //Bar graph
			plot.addBarGraph(xs, ys, 0, xs.length, props.getColor(), 1.0f, props.getCurveName());
			break;
		}
		
		SDMMResultsTable segmentsTable = molecule.getSegmentsTable(props.yColumnName(), props.xColumnName());
		
		if (props.drawSegments() && segmentsTable != null) {
			
			int numSegments = segmentsTable.getRowCount();
			double[] seg_xs = new double[numSegments*2];
			double[] seg_ys = new double[numSegments*2];
			
			for (int i = 0; i < numSegments*2 ; i+=2) {
				seg_xs[i] = segmentsTable.getValue("x1", i/2);
				seg_xs[i+1] = segmentsTable.getValue("x2", i/2);
				
				seg_ys[i] = segmentsTable.getValue("y1", i/2);
				seg_ys[i+1] = segmentsTable.getValue("y2", i/2);
			}
			plot.addSegmentPlot(seg_xs, seg_ys, props.getSegmentsColor(), 2.0f, props.getCurveName() + " Segments");
		}
	}
	
	public Plot getPlot() {
		return plot;
	}
	
	public void setMolecule(Molecule molecule) {
		this.molecule = molecule;
		showPlot();
	}
	
	public Molecule getMolecule() {
		return molecule;
	}
	private Color getColorFromName(String color_name) {
		if (color_name.equals("none"))
			return null;
		try {
			if (color_name.equals("dark green"))
				return darkGreen;
			Field field = Class.forName("java.awt.Color").getField(color_name);
			return (Color)field.get(null);
		} catch (Exception e) {
			//can't happen since only the colors specified above can be picked and those all exist...
		}
		return Color.black;
	}
}

