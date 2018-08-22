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
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import de.mpg.biochem.sdmm.molecule.Molecule;

public class PlotPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	
	private RoiManager roiManager;
	private JComboBox xColumnComboBox;
	private JComboBox yColumnComboBox;
	private JComboBox typeComboBox = new JComboBox(new String[]{"Line", "Scatter"});
	public Plot plot = new Plot();
	private String caption = "";
	
	private int type = 0;
	private String xColumnName;
	private String yColumnName;
	
	private Molecule molecule;
	
	public PlotPanel(String xColumnName, Molecule molecule) {
		this.xColumnName = xColumnName;
		
		String[] columns = molecule.getDataTable().getColumnHeadings();
		yColumnComboBox = new JComboBox(columns);
		yColumnComboBox.setSelectedItem("bps");
		
		JPanel panel = new JPanel(new GridLayout(0, 2));
		panel.add(new JLabel("y_column"));
		panel.add(yColumnComboBox);
		
		panel.add(new JLabel("plot_type"));
		typeComboBox.setSelectedItem("Line");
		panel.add(typeComboBox);
		
		JPanel leftPanel = new JPanel();
		leftPanel.add(panel);
		
		setLayout(new BorderLayout());
		add(leftPanel, BorderLayout.WEST);
		add(plot);
	}

	public void showPlot() {
		plot.clear();
		
		//First let's read in all the information from the selectors
  		yColumnName = (String)yColumnComboBox.getSelectedItem();
  		if (xColumnName.equals(" ") || yColumnName.equals(" ")) 
  			return;
		
		caption = String.format("UID = %s", molecule.getUID());
		
  		type = typeComboBox.getSelectedIndex();
  		
      	PlotProperties curve = new PlotProperties("Curve", xColumnName, yColumnName, Color.black, type, Color.red, false);
		drawCurve(curve);
		
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
		
		if (props.drawSegments()) {
			//Not sure exactly why the end need a 2. The index of the end of group columns is correct.
			//Guess it is just because the index starts at 0 and the coordinate sets are put in a linear array.
			double[] seg_xs = molecule.getSegmentsTable(props.xColumnName(), props.yColumnName()).getColumnAsDoubles(props.xColumnName());
			double[] seg_ys = molecule.getSegmentsTable(props.xColumnName(), props.yColumnName()).getColumnAsDoubles(props.yColumnName());
			
			plot.addSegmentPlot(seg_xs, seg_ys, 0, seg_xs.length, props.getSegmentsColor(), 2.0f, props.getCurveName() + " Segments");
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
	
}

