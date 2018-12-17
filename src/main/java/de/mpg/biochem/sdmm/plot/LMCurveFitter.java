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

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.TextField;

import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.macro.Interpreter;

import java.util.ArrayList;
import java.util.Vector;

public class LMCurveFitter implements LMFunction, DialogListener {
	
	private String[] functions = {"linear","gaussian","exponential decay","other"};
	private String fitFunction = "a+b*x";
	private String initialParameters = "1,1";
	private GenericDialog dialog;
	private double xfrom, xto;
	private String previousChoice = "linear";
	private boolean noFitting = false;
	private int maxIterations = 10000;
	private double precision = 0.00001;
	
	private String[] plotNames;
	private int plotIndex = 0;
	private int previousPlotIndex = plotIndex;
	
	private double[] x, y;
	private Plot plot;

	public double getValue(double[] x, double[] parameters) {
		String macro = "";
		
		for (int i = 0; i < parameters.length; i++)
			macro += String.format("%c=%f;", 'a' + i, parameters[i]);
		
		macro += String.format("x=%f;y=%s", x[0], fitFunction);
		
		Interpreter interpreter = new Interpreter();
		interpreter.run(macro);
		
		return interpreter.getVariable("y");
	}

	public void fit(Plot plot) {
		this.plot = plot;
		dialog = new GenericDialog("Curve Fitter");
		plotNames = new String[plot.plotNames.size()];
		plotNames = plot.plotNames.toArray(plotNames);
		dialog.addChoice("Plot", plotNames, plotNames[0]);
		
		importData();
		
		dialog.addChoice("Functions", functions, "linear");
		dialog.addStringField("Function", fitFunction, 25);
		dialog.addStringField("initial_parameters", initialParameters, 25);
		dialog.addNumericField("From", x[0], 3, 10, "");
		dialog.addNumericField("To", x[x.length - 1], 3, 10, "");
		dialog.addNumericField("Fitting precision",precision, 8, 10, "");
		dialog.addNumericField("Max iterations", maxIterations,0, 10, "");
		dialog.addCheckbox("Just plot", noFitting);
		dialog.addDialogListener(this);
		dialog.showDialog();
		
		if (dialog.wasCanceled())
			return;
		
		//find the index of the start and end points -- is there a better way??
		int from = 0;
		int to = x.length;
		for (int i=0; i < x.length ; i++) {
			if (x[i] <= xfrom)
				from = i;
			
			if (x[i] <= xto)
				to = i;
		}
		int n = to - from + 1;
		
		double[][] xValues = new double[n][1];
		double[] yValues = new double[n];
		double[] xPlotValues = new double[n];
		
		for (int i = 0; i < n; i++) {
			xValues[i][0] = x[from + i];
			yValues[i] = y[from + i];
			xPlotValues[i] = x[from + i];
		}
		
		// parse initial parameter values
		String[] values = initialParameters.split(",");
		double[] initial = new double[values.length];
		
		for (int i = 0; i < values.length; i++)
			initial[i] = Double.parseDouble(values[i]);
		
		// do fit
		LMplotfit lm = new LMplotfit(this, initial.length, precision, maxIterations);
		double[] parameters = initial.clone();
		double[] error = new double[parameters.length];
		
		lm.solve(parameters, null, xValues, yValues, null, 0.001, error);
		
		
		// create fit plot
		double[] yFit = new double[xValues.length];
		
		for (int i = 0; i < xValues.length; i++) {
			if (noFitting) {
				yFit[i] = getValue(xValues[i], initial);
			} else {
				yFit[i] = getValue(xValues[i], parameters);
			}
		}
		
		plot.addFitPlot(xPlotValues, yFit, Color.RED, 1, plot.plotNames.get(plotIndex));
		
		ArrayList<String> fitParameters = new ArrayList<String>();
		
		String fitFunc = "y=" + fitFunction;
		
		for (int i = 0; i < parameters.length; i++)
			fitFunc = fitFunc.replace(Character.toString((char)('a' + i)), String.format("%.4g", parameters[i]));
		
		fitParameters.add(fitFunc);
		
		// calculating R^2
		double mean = 0;
		for (int i = 0; i < yValues.length; i++)
			mean += yValues[i];
		mean /= yValues.length;
		
		double sst = 0;
		for (int i = 0; i < yValues.length; i++) {
			double deviation = yValues[i] - mean;
			sst += deviation * deviation;
		}
		
		double sse = lm.chiSquared;
		
		fitParameters.add(String.format("R^2=%.4g", (1.0 - (sse / (yValues.length - parameters.length)) / (sst / (yValues.length - 1)))));
		fitParameters.add(String.format("chi^2=%.4g ", lm.chiSquared));
		
		for (int i = 0; i < parameters.length; i++)
			fitParameters.add(String.format("sd_%c=%.4g", 'a' + i, error[i]));
		
		plot.setLegend(fitParameters);

	}
	
	private void importData() {
		x = new double[plot.plotCoordinates.get(plotIndex).length/2];
		y = new double[plot.plotCoordinates.get(plotIndex).length/2];
		for (int i=0; i<plot.plotCoordinates.get(plotIndex).length ; i+=2) {
			x[i/2] = plot.plotCoordinates.get(plotIndex)[i];
			y[i/2] = plot.plotCoordinates.get(plotIndex)[i + 1];
		}
	}
	
	@Override
	public boolean dialogItemChanged(GenericDialog dialog, AWTEvent e) {
		plotIndex = dialog.getNextChoiceIndex();
		
		importData();
		
		if (plotIndex != previousPlotIndex) {
			previousPlotIndex = plotIndex;
			
			@SuppressWarnings("rawtypes")
			Vector numerics = dialog.getNumericFields();
			
			((TextField)numerics.get(0)).setText(x[0] + "");
			((TextField)numerics.get(1)).setText(x[x.length - 1] + ""); 
		}
		
		String fun = dialog.getNextChoice();
		
		if (!fun.equals(previousChoice)) {
			@SuppressWarnings("rawtypes")
			Vector textboxes = dialog.getStringFields();
			
			if (fun.equals("linear")) {
				((TextField)textboxes.get(0)).setText("a+b*x");
				((TextField)textboxes.get(1)).setText("1,1");
			} else if (fun.equals("gaussian")) {
				((TextField)textboxes.get(0)).setText("a*exp(-pow(x-b,2)/(2*pow(c,2)))");
				((TextField)textboxes.get(1)).setText("1,1,1");
			} else if (fun.equals("exponential decay")) {
				((TextField)textboxes.get(0)).setText("a*exp(-x/b)");
				((TextField)textboxes.get(1)).setText("1,1");
			}
		}
		
		previousChoice = fun;
			
		fitFunction = dialog.getNextString();
		initialParameters = dialog.getNextString();
		
		xfrom = dialog.getNextNumber();
		xto = dialog.getNextNumber();
		
		precision = dialog.getNextNumber();
		maxIterations = (int)dialog.getNextNumber();
		
		noFitting = dialog.getNextBoolean();
		
		return true;
	}
}

