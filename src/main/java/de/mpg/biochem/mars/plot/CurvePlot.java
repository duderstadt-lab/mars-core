/*******************************************************************************
 * Copyright (C) 2019, Duderstadt Lab
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import javax.swing.JPanel;

import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.plot.Plot.Type;
import de.mpg.biochem.mars.table.MARSResultsTable;

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
		PlotProperties curve = new PlotProperties("curve", "x", "y", Color.black, 0, Color.red);
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
		
		plot.resetPointPosition();
		
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
		
		MARSResultsTable segmentsTable = molecule.getSegmentsTable(props.yColumnName(), props.xColumnName());
		
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

