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
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import ij.IJ;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import ij.text.TextWindow;

public class ResultsPlotter implements ActionListener {
	//this plot object will actually draw the plot and manage all the graphics rendering
	//caption will be displayed on the plot at the top
	private Plot plot = new Plot();
	private String caption = "";
	private String groupColumn;
	
	//This is the title of the input table
	private String tableTitle;
	
	//This is the index that hold the current group rendered on the plot
	//This runs from 0 to the number of molecules
	//This is distinct from the molecule numbers
	private int group = 0;
	
	//List of groups to delete stored as group numbers (not their index)
	//private ArrayList<Integer> deleteList = new ArrayList<Integer>();
	
	private JButton previousButton = new JButton("previous");
	private JButton nextButton = new JButton("next");
	private JButton saveButton = new JButton("save");
	//private JButton Delete = new JButton("Delete");
	//private JButton Process = new JButton("Update Table");
	private JButton setPath = new JButton("path");
	//private JButton showROI = new JButton("show");
	private JLabel label = new JLabel();
	private JCheckBox fixBoundsCheckBox = new JCheckBox("fix bounds", false);
	
	private JButton goTo = new JButton("Go To");
	private TextField trajSelection = new TextField(IJ.d2s(1, 0), 8);
	
	private File default_path;
	//private RoiManager roiManager;
	
	//TextWindow log_window = new TextWindow("Log", "", 400, 600);
	
	//This holds all the data to be plotted
	//each PlotData object will hold data for a given x y pair as well as group information.
	//PlotData is defined in util.
	ArrayList<PlotData> plot_data = new ArrayList<PlotData>();
	
	public ResultsPlotter(ArrayList<PlotData> plot_data_in, String tableTitle_in, String groupColumn_in) {
		plot_data = plot_data_in;
		tableTitle = tableTitle_in;
		groupColumn = groupColumn_in;
		
		JPanel panel = new JPanel();
		
		//Either all plot_data sets have groups or none d0, so we only need to check one curve set.
		if (plot_data.get(0).hasGroups()) {
			panel.add(fixBoundsCheckBox);
			panel.add(previousButton);
			panel.add(nextButton);
			//panel.add(Delete);
			//panel.add(Process);
			panel.add(setPath);
			//panel.add(showROI);
			panel.add(saveButton);
			panel.add(goTo);
			panel.add(new JLabel(groupColumn));
			panel.add(trajSelection);
			panel.add(label);
			
			previousButton.addActionListener(this);
			nextButton.addActionListener(this);
			//Delete.addActionListener(this);
			//Process.addActionListener(this);
			setPath.addActionListener(this);
			//showROI.addActionListener(this);
			saveButton.addActionListener(this);
			goTo.addActionListener(this);
		} else {
			panel.add(saveButton);
			panel.add(label);
			
			saveButton.addActionListener(this);
		}
		
		JFrame plotFrame = new JFrame("Plot - " + tableTitle);
		plotFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		plotFrame.setSize(800, 800);
		
		//plotFrame.setBackground(Color.WHITE);
		//panel.setBackground(Color.WHITE);
		
		Container buttonPane = new Container();
		buttonPane.setLayout(new BorderLayout());
		buttonPane.add(panel, BorderLayout.CENTER);
			
		Container contentPane = plotFrame.getContentPane();
		contentPane.setLayout(new BorderLayout());
		contentPane.add(buttonPane, BorderLayout.SOUTH);
		
		JPanel whitePLOT = new JPanel();
		whitePLOT.setBackground(Color.WHITE);
		whitePLOT.setLayout(new BorderLayout());
		whitePLOT.add(plot, BorderLayout.CENTER);
		contentPane.add(whitePLOT, BorderLayout.CENTER);
		
		showPlot();
		plotFrame.setVisible(true);
	}
	
	private void showPlot() {
		plot.clear();
		
		if (plot_data.get(0).hasGroups()) {
			caption = String.format("%s = %d", groupColumn, plot_data.get(0).getGroupNumber(group));
			label.setText(String.format("#%d of %d", group + 1, plot_data.get(0).numberGroups()));
			
			plot.setGroup(groupColumn, plot_data.get(0).getGroupNumber(group));
		}
		
		for (int i = 0; i < plot_data.size() ; i++) {
			plot_data.get(i).drawCurve(plot, group);
		}
		
		if (!fixBoundsCheckBox.isSelected())
			plot.resetBounds();
		
		//For the moment we assume the labels from the first curve are fine for all curves.
		plot.setxAxisLabel(plot_data.get(0).xColumnName());
		plot.setyAxisLabel(plot_data.get(0).yColumnName());
		
		if (plot_data.get(0).hasGroups) {
			//if (deleteList.contains(plot_data.get(0).getGroupNumber(group))) {
			//	plot.setCaption("Marked for Deletion");
			//} else {
				plot.setCaption(caption);
			//}
		}
		
		plot.repaint();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == previousButton && group > 0) {
			group--;
			showPlot();
		}
		else if (e.getSource() == nextButton && group < plot_data.get(0).numberGroups() - 1) {
			group++;
			showPlot();
		} else if (e.getSource() == setPath) {
			JFileChooser fileChooser = new JFileChooser(default_path);
			
			if (fileChooser.showSaveDialog(plot) == JFileChooser.APPROVE_OPTION) {
				default_path = fileChooser.getSelectedFile();
			}
		}
		else if (e.getSource() == saveButton) {
		    
			if (default_path == null) {
				JFileChooser fileChooser = new JFileChooser(default_path);
				
				if (fileChooser.showSaveDialog(plot) == JFileChooser.APPROVE_OPTION) {
					default_path = fileChooser.getSelectedFile();
				} else {
					return;
				}
			}
			
			String gNum = "";
			if (plot_data.get(0).hasGroups)
				gNum = plot_data.get(0).getGroupNumber(group) + "";
 		   
 		    File imageName = new File(default_path.getPath() + gNum + ".png");
 		    plot.savePlot(imageName);
		} else if (e.getSource() == goTo) {
			String theText = trajSelection.getText();
			int molecule = Integer.valueOf(theText);
			int group_pos = plot_data.get(0).getGroupIndex(molecule);
			if (group_pos != -1) {
				group = group_pos;
				showPlot();
			}
		}
	}
}

