package de.mpg.biochem.sdmm.plot;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import javax.swing.JPanel;

import de.mpg.biochem.sdmm.molecule.Molecule;

//This will hold single and multicurve plots that go in the Plot panel of the
//Molecule Archive window...

public class CurvePlot extends JPanel {
	//this plot object will actually draw the plot and manage all the graphics rendering
	//caption will be displayed on the plot at the top
	private Plot plot = new Plot();
	private String caption = "";
	private Molecule molecule;
	
	//This holds the settings for all the curves to be added to a plot...
	ArrayList<PlotProperties> plot_properties = new ArrayList<PlotProperties>();
	
	public CurvePlot(Molecule molecule) {
		plot_properties = new ArrayList<PlotProperties>();
		PlotProperties curve = new PlotProperties("curve", "x", "y", Color.black, 0, Color.red, false);
		plot_properties.add(curve);
		this.molecule = molecule;
		
		setBackground(Color.WHITE);
		setLayout(new BorderLayout());
		add(plot, BorderLayout.CENTER);
		
		showPlot();
	}
	
	public CurvePlot(ArrayList<PlotProperties> plot_properties, Molecule molecule) {
		this.plot_properties = plot_properties;
		this.molecule = molecule;
		
		setBackground(Color.WHITE);
		setLayout(new BorderLayout());
		add(plot, BorderLayout.CENTER);
		
		showPlot();
	}
	
	public void showPlot() {
		plot.clear();
		
		caption = String.format("UID = %s", molecule.getUID());
		
		//plot.setPlotTitle(String.format("UID = %s", molecule.getUID()));
		
		plot.setPlotTitle(" ");
		plot.setMolecule(molecule);
		
		for (PlotProperties props: plot_properties) {
			drawCurve(props);
		}
		
		//For the moment we assume the labels from the first curve are fine for all curves.
		plot.setxAxisLabel(plot_properties.get(0).xColumnName());
		plot.setyAxisLabel(plot_properties.get(0).yColumnName());
		
		plot.setCaption(caption);

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
	
	public void updatePlotProperties(ArrayList<PlotProperties> plot_properties) {
		this.plot_properties = plot_properties;
		showPlot();
	}
	
	public void setMolecule(Molecule molecule) {
		this.molecule = molecule;
		showPlot();
	}
	
	public Plot getPlot() {
		return plot;
	}
}

