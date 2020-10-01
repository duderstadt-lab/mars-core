/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2020 Karl Duderstadt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package de.mpg.biochem.mars.molecule.commands;

import ij.ImagePlus;
import ij.Prefs;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.PointRoi;

import org.scijava.module.MutableModuleItem;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.command.Previewable;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.util.RealRect;
import org.scijava.widget.ChoiceWidget;

import de.mpg.biochem.mars.image.*;
import de.mpg.biochem.mars.molecule.*;
import de.mpg.biochem.mars.metadata.*;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import io.scif.config.SCIFIOConfig;
import io.scif.config.SCIFIOConfig.ImgMode;
import io.scif.img.ImgIOException;
import io.scif.img.ImgOpener;
import io.scif.img.SCIFIOImgPlus;
import net.imagej.display.ImageDisplay;
import net.imagej.display.OverlayService;
import net.imglib2.Cursor;
import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import net.imagej.ops.Initializable;
import net.imagej.ops.OpService;
import net.imagej.ops.Ops;

import org.scijava.table.DoubleColumn;
import io.scif.img.IO;
import io.scif.img.ImgIOException;

import java.awt.Color;
import java.awt.Image;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.imglib2.img.ImagePlusAdapter;

@Plugin(type = Command.class, label = "Build DNA Archive", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Mars", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 'm'),
		@Menu(label = "Molecule", weight = 20,
			mnemonic = 'm'),
		@Menu(label = "Build DNA Archive", weight = 1, mnemonic = 'b')})
public class BuildDnaArchiveCommand extends DynamicCommand implements Command {
	
	//GENERAL SERVICES NEEDED
	@Parameter
	private RoiManager roiManager;
	
	@Parameter
	private LogService logService;
	
	@Parameter
	private OpService ops;
	
    @Parameter
    private StatusService statusService;
    
	@Parameter
    private MarsTableService marsTableService;

	@Parameter(label="Search radius around DNA")
	private double radius;
	
	@Parameter(label="DNA length in bps")
	private int DNALength = 21236;

	@Parameter(label="x column")
	private String xColumn = "x_drift_corr";
	
	@Parameter(label="y column")
	private String yColumn = "y_drift_corr";
	
	@Parameter(label="SingleMoleculeArchive 1")
	private SingleMoleculeArchive archive1;
	
	@Parameter(label="SingleMoleculeArchive 1 Name")
	private String archive1Name = "mol1";
	
	@Parameter(label="Merge")
	private boolean merge2;
	
	@Parameter(label="SingleMoleculeArchive 2")
	private SingleMoleculeArchive archive2;
	
	@Parameter(label="SingleMoleculeArchive 2 Name")
	private String archive2Name = "mol2";
	
	@Parameter(label="Merge")
	private boolean merge3;
	
	@Parameter(label="SingleMoleculeArchive 3")
	private SingleMoleculeArchive archive3;
	
	@Parameter(label="SingleMoleculeArchive 3 Name")
	private String archive3Name = "mol3";
	
	//OUTPUT PARAMETERS
	@Parameter(label="DnaArchive.yama", type = ItemIO.OUTPUT)
	private DnaMoleculeArchive dnaMoleculeArchive;

	@Override
	public void run() {				
		
		//Build log
		LogBuilder builder = new LogBuilder();
		
		String log = LogBuilder.buildTitleBlock("Build DNA Archive");
		
		addInputParameterLog(builder);
		log += builder.buildParameterList();
		
		//Output first part of log message...
		logService.info(log);
		
		//Used to store list of DNAs which are the basis for the DNA records.
		ArrayList<DNASegment> DNASegments = new ArrayList<DNASegment>();
		
		
		Roi[] rois = roiManager.getRoisAsArray();
		if (rois.length == 0) {
			logService.info("Found 0 DNA records in the RoiManager. Aborting.");
			return;
		} else {
			logService.info("Found " + rois.length  + " DNA records in the RoiManager.");
		}
		
		//Let's build the DNASegment records
		for (Roi roi : rois) {
			Line line = (Line)roi;
			DNASegment dnaSegment = new DNASegment(line.x1d - 0.5, line.y1d - 0.5, line.x2d - 0.5, line.y2d - 0.5);
			DNASegments.add(dnaSegment);
		}
		
		MarsOMEMetadata metadata1 = archive1.getMetadata(0);
		if (merge2 && merge3) {
			metadata1.merge(archive2.getMetadata(0));
			metadata1.merge(archive3.getMetadata(0));
		} else if (merge2) {
			metadata1.merge(archive2.getMetadata(0));
		} else {
			return;
		}
		
		metadata1.setParameter("DnaMoleculeCount", rois.length);
		
		//Build a new DnaMoleculeArchive 
		dnaMoleculeArchive = new DnaMoleculeArchive("DnaArchive.yama");
		dnaMoleculeArchive.putMetadata(metadata1);
		
		//Build KDTrees for fast searching
		RadiusNeighborSearchOnKDTree< MoleculePosition > archive1PositionSearcher = null;
		RadiusNeighborSearchOnKDTree< MoleculePosition > archive2PositionSearcher = null;
		RadiusNeighborSearchOnKDTree< MoleculePosition > archive3PositionSearcher = null;
		

		archive1PositionSearcher = getMoleculeSearcher(archive1);
		
		if (merge2)
			archive2PositionSearcher = getMoleculeSearcher(archive2);
		
		if (merge3)
			archive3PositionSearcher = getMoleculeSearcher(archive3);
		
		for (DNASegment dnaSegment : DNASegments) {
			DnaMolecule dnaMolecule = new DnaMolecule(MarsMath.getUUID58());
			dnaMolecule.setImage(metadata1.getImage(0).getImageID());
			dnaMolecule.setParameter("Dna_Top_x1", dnaSegment.getX1());
			dnaMolecule.setParameter("Dna_Top_y1", dnaSegment.getY1());
			dnaMolecule.setParameter("Dna_Bottom_x2", dnaSegment.getX2());
			dnaMolecule.setParameter("Dna_Bottom_y2", dnaSegment.getY2());
			
			MarsTable mergedTable = new MarsTable();
			
			ArrayList<SingleMolecule> moleculesOnDNA = findMoleculesOnDna(archive1PositionSearcher, archive1, dnaSegment); 
			if (moleculesOnDNA.size() != 0) {
				addToMergedTable(mergedTable, moleculesOnDNA, archive1, archive1Name, dnaSegment);
			}
			dnaMolecule.setParameter("Number_" + archive1Name, moleculesOnDNA.size());
			
			if (merge2) {
				moleculesOnDNA = findMoleculesOnDna(archive2PositionSearcher, archive2, dnaSegment); 
				if (moleculesOnDNA.size() != 0) {
					addToMergedTable(mergedTable, moleculesOnDNA, archive2, archive2Name, dnaSegment);
				}
				dnaMolecule.setParameter("Number_" + archive2Name, moleculesOnDNA.size());
			}
			
			if (merge3) {
				moleculesOnDNA = findMoleculesOnDna(archive3PositionSearcher, archive3, dnaSegment); 
				if (moleculesOnDNA.size() != 0) {
					addToMergedTable(mergedTable, moleculesOnDNA, archive3, archive3Name, dnaSegment);
				}
				dnaMolecule.setParameter("Number_" + archive3Name, moleculesOnDNA.size());
			}
			
			if (mergedTable.isEmpty())
				continue;
			
			dnaMolecule.setTable(mergedTable);
			dnaMoleculeArchive.put(dnaMolecule);
		}
		
		logService.info(LogBuilder.endBlock(true));
		
		log += "\n" + LogBuilder.endBlock(true);
		dnaMoleculeArchive.logln(log);
		dnaMoleculeArchive.logln("   ");	
	}
	
	private void addToMergedTable(MarsTable mergedTable, ArrayList<SingleMolecule> moleculesOnDNA, SingleMoleculeArchive archive, String name, DNASegment dnaSegment) {
		int index = 1;
		for (SingleMolecule molecule : moleculesOnDNA) {
			MarsTable table = molecule.getTable().clone();
			
			//We need to make sure to fill the table with Double.NaN values
			//this will over write the scijava default value of 0.0.
			if (!mergedTable.isEmpty() && mergedTable.getRowCount() < table.getRowCount()) {
				for (int row=mergedTable.getRowCount(); row < table.getRowCount(); row++) {
					mergedTable.appendRow();
					for (int col=0; col<mergedTable.getColumnCount(); col++) 
						mergedTable.setValue(col, row, Double.NaN);
				}
			} else if (!mergedTable.isEmpty() && mergedTable.getRowCount() > table.getRowCount()) {
				for (int row=table.getRowCount(); row < mergedTable.getRowCount(); row++) {
					table.appendRow();
					for (int col=0; col<table.getColumnCount(); col++) 
						table.setValue(col, row, Double.NaN);
				}
			}
			
			//Distance from the DNA top END
			DoubleColumn dnaPositionColumn = new DoubleColumn(name + "_" + index + "_Position_on_DNA");
			for (int row=0; row<table.getRowCount(); row++) {
				dnaPositionColumn.add(dnaSegment.getPositionOnDNA(table.getValue(xColumn, row), table.getValue(yColumn, row)));
			}
			
			for (int col=0;col<table.getColumnCount();col++) {
				table.get(col).setHeader(name + "_" + index + "_" + table.get(col).getHeader());
				mergedTable.add(table.get(col));
			}
			
			mergedTable.add(dnaPositionColumn);
			
			index++;
		}
	}
	
	private RadiusNeighborSearchOnKDTree< MoleculePosition > getMoleculeSearcher(SingleMoleculeArchive archive) {
		ArrayList<MoleculePosition> moleculePositionList = new ArrayList<MoleculePosition>();
		
		archive.molecules().forEach(molecule ->  moleculePositionList.add(new MoleculePosition(molecule.getUID(), molecule.getTable().median("x"), molecule.getTable().median("y"))));

		KDTree<MoleculePosition> moleculesTree = new KDTree<MoleculePosition>(moleculePositionList, moleculePositionList);
		
		return new RadiusNeighborSearchOnKDTree< MoleculePosition >( moleculesTree );
	}
	
	private ArrayList<SingleMolecule> findMoleculesOnDna(RadiusNeighborSearchOnKDTree< MoleculePosition > archivePositionSearcher, SingleMoleculeArchive archive, DNASegment dnaSegment) {
		ArrayList<SingleMolecule> moleculesLocated = new ArrayList<SingleMolecule>();
		
		archivePositionSearcher.search(dnaSegment, dnaSegment.getSearchRadius(), false);
		
		//build DNA fit
		double x1 = dnaSegment.getX1();
		double y1 = dnaSegment.getY1();
		
		double x2 = dnaSegment.getX2();
		double y2 = dnaSegment.getY2();
		
		double A = dnaSegment.getA();
		double B = dnaSegment.getB();
		
		for (int j = 0 ; j < archivePositionSearcher.numNeighbors() ; j++ ) {
			MoleculePosition moleculePosition = archivePositionSearcher.getSampler(j).get();
			
			double distance;
			
			//Before we add the the molecules we need to constrain positions to just within radius of DNA....
			if (moleculePosition.getY() < y1) {
				//the molecules is above the DNA
				distance = Math.sqrt((moleculePosition.getX()-x1)*(moleculePosition.getX()-x1) + (moleculePosition.getY()-y1)*(moleculePosition.getY()-y1));
			} else if (moleculePosition.getY() > y2) {
				distance = Math.sqrt((moleculePosition.getX()-x2)*(moleculePosition.getX()-x2) + (moleculePosition.getY()-y2)*(moleculePosition.getY()-y2));
			} else {
				//find the x center position of the DNA for the molecule y position.
				
				//If there is no intercept just take top x1.
				double DNAx;
				if (x1 == x2)
					DNAx = x1;
				else
					DNAx = (moleculePosition.getY() - A)/B;
				
				distance = Math.abs(moleculePosition.getX() - DNAx);
			}
			
			//other conditions
			
			if (distance < radius) {
				moleculesLocated.add(archive.get(moleculePosition.getUID()));
			}
		}
		
		return moleculesLocated;
	}

	private void addInputParameterLog(LogBuilder builder) {
		//builder.addParameter("useROI", String.valueOf(useROI));
		builder.addParameter("Search radius around DNA", String.valueOf(radius));
		builder.addParameter("DNA length in bps", String.valueOf(DNALength));
		builder.addParameter("xColumn", String.valueOf(xColumn));
		builder.addParameter("yColumn", String.valueOf(yColumn));
		builder.addParameter("SingleMoleculeArchive 1", archive1.getName());
		builder.addParameter("SingleMoleculeArchive 1 Name", archive1Name);
		builder.addParameter("Merge 2", String.valueOf(merge2));
		builder.addParameter("SingleMoleculeArchive 2", archive2.getName());
		builder.addParameter("SingleMoleculeArchive 2 Name", archive2Name);
		builder.addParameter("Merge 3", String.valueOf(merge3));
		builder.addParameter("SingleMoleculeArchive 3", archive3.getName());
		builder.addParameter("SingleMoleculeArchive 3 Name", archive3Name);
	}
	
	class MoleculePosition implements RealLocalizable {
		private String UID;
		
		private double x, y;
		
		public MoleculePosition(String UID, double x, double y) {
			this.UID = UID;
			this.x = x;
			this.y = y;
		}
		
		public String getUID() {
			return UID;
		}
		
		public double getX() {
			return x;
		}
		
		public double getY() {
			return y;
		}
		
		//Override from RealLocalizable interface.. so peaks can be passed to KDTree and other imglib2 functions.
		@Override
		public int numDimensions() {
			// We make no effort to think beyond 2 dimensions !
			return 2;
		}
		@Override
		public double getDoublePosition(int arg0) {
			if (arg0 == 0) {
				return x;
			} else if (arg0 == 1) {
				return y;
			} else {
				return -1;
			}
		}
		@Override
		public float getFloatPosition(int arg0) {
			if (arg0 == 0) {
				return (float)x;
			} else if (arg0 == 1) {
				return (float)y;
			} else {
				return -1;
			}
		}
		@Override
		public void localize(float[] arg0) {
			arg0[0] = (float)x;
			arg0[1] = (float)y;
		}
		@Override
		public void localize(double[] arg0) {
			arg0[0] = x;
			arg0[1] = y;
		}
	}
	
	//Not sure which ROI library to use at the moment
	//for now we just use this...
	class DNASegment implements RealLocalizable {
		private double x1, y1, x2, y2, A, B;
		
		private double centerX, centerY;
		
		private double bpsPerPixels;
		
		private int medianIntensity;
		
		private double msd;
		
		DNASegment(double x1, double y1, double x2, double y2) {
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			
			centerX = x1 + (x2 - x1)/2;
			centerY = y1 + (y2 - y1)/2;
			
			SimpleRegression linearFit = new SimpleRegression(true);
			linearFit.addData(x1, y1);
			linearFit.addData(x2, y2);
			
			//y = A + Bx
			A = linearFit.getIntercept();
			B = linearFit.getSlope();
			
			bpsPerPixels = DNALength/getLength();
		}
		
		double getA() {
			return A;
		}
		
		double getB() {
			return B;
		}
		
		double getX1() {
			return x1;
		}
		
		double getY1() {
			return y1;
		}
		
		double getX2() {
			return x2;
		}
		
		double getY2() {
			return y2;
		}
		
		void setX1(double x1) {
			this.x1 = x1;
		}
		
		void setY1(double y1) {
			this.y1 = y1;
		}
		
		void setX2(double x2) {
			this.x2 = x2;
		}
		
		void setY2(double y2) {
			this.y2 = y2;
		}
		
		void setMedianIntensity(int medianIntensity) {
			this.medianIntensity = medianIntensity;
		}
		
		int getMedianIntensity() {
			return medianIntensity;
		}
		
		void setMSD(double msd) {
			this.msd = msd;
		}
		
		double getMSD() {
			return msd;
		}
		
		double getLength() {
			return Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1));
		}
		
		double getPositionOnDNA(double x, double y) {
			return Math.sqrt((x-x1)*(x-x1) + (y-y1)*(y-y1))*bpsPerPixels;
		}
		
		double getSearchRadius() {
			return radius + getLength()/2;
		}
		
		//Override from RealLocalizable interface.. so peaks can be passed to KDTree and other imglib2 functions.
		@Override
		public int numDimensions() {
			// We make no effort to think beyond 2 dimensions !
			return 2;
		}
		@Override
		public double getDoublePosition(int arg0) {
			if (arg0 == 0) {
				return centerX;
			} else if (arg0 == 1) {
				return centerY;
			} else {
				return -1;
			}
		}
		@Override
		public float getFloatPosition(int arg0) {
			if (arg0 == 0) {
				return (float)centerX;
			} else if (arg0 == 1) {
				return (float)centerY;
			} else {
				return -1;
			}
		}
		@Override
		public void localize(float[] arg0) {
			arg0[0] = (float)centerX;
			arg0[1] = (float)centerY;
		}
		@Override
		public void localize(double[] arg0) {
			arg0[0] = centerX;
			arg0[1] = centerY;
		}
	}
}