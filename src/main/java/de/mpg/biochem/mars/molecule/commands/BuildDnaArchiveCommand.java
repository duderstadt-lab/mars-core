/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2023 Karl Duderstadt
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

import java.util.ArrayList;

import net.imagej.ops.Initializable;
import net.imagej.ops.OpService;
import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.DoubleColumn;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import de.mpg.biochem.mars.image.DNASegment;
import de.mpg.biochem.mars.image.Peak;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.molecule.DnaMolecule;
import de.mpg.biochem.mars.molecule.DnaMoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.molecule.SingleMolecule;
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableService;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import ij.gui.Line;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

@Plugin(type = Command.class, label = "Build DNA Archive", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
			label = "Molecule", weight = 2, mnemonic = 'm'), @Menu(
				label = "Build DNA Archive", weight = 4, mnemonic = 'b') })
public class BuildDnaArchiveCommand extends DynamicCommand implements Command,
	Initializable
{

	// GENERAL SERVICES NEEDED
	@Parameter
	private RoiManager roiManager;

	@Parameter
	private LogService logService;

	@Parameter
	private OpService ops;
	
	@Parameter
	private UIService uiService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private MarsTableService marsTableService;

	@Parameter
	private MoleculeArchiveService moleculeArchiveService;

	/**
	 * INPUT SETTINGS
	 */

	@Parameter(visibility = ItemVisibility.MESSAGE,
		style = "groupLabel, tabbedPaneWidth:450")
	private final String inputGroup = "Input";

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "image, group:Input")
	private final String inputFigure = "BuildDNAArchiveInput.png";

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "group:Input")
	private final String roiCount = "No ROIs in manager!";

	@Parameter(label = "SingleMoleculeArchive 1", choices = { "a", "b", "c" },
		style = "group:Input")
	private String archive1InputName;

	@Parameter(label = "SingleMoleculeArchive 1 Name", style = "group:Input")
	private String archive1Name = "mol1";

	@Parameter(label = "SingleMoleculeArchive 2", choices = { "a", "b", "c" },
		style = "group:Input")
	private String archive2InputName;

	@Parameter(label = "SingleMoleculeArchive 2 Name", style = "group:Input")
	private String archive2Name = "mol2";

	@Parameter(label = "SingleMoleculeArchive 3", choices = { "a", "b", "c" },
		style = "group:Input")
	private String archive3InputName;

	@Parameter(label = "SingleMoleculeArchive 3 Name", style = "group:Input")
	private String archive3Name = "mol3";

	/**
	 * OUTPUT SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private final String mergeGroup = "Search Parameters";

	@Parameter(label = "Search radius around DNA",
		style = "group:Search Parameters")
	private double radius;

	@Parameter(label = "DNA length in bps", style = "group:Search Parameters")
	private int DNALength = 21236;

	@Parameter(label = "Input Archive X column",
		style = "group:Search Parameters")
	private String xColumn = "X";

	@Parameter(label = "Input Archive Y column",
		style = "group:Search Parameters")
	private String yColumn = "Y";

	/**
	 * OUTPUT SETTINGS
	 */
	@Parameter(visibility = ItemVisibility.MESSAGE, style = "groupLabel")
	private final String outputGroup = "Output";

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "image, group:Output")
	private final String outputFigure = "DnaMoleculeArchive.png";

	@Parameter(visibility = ItemVisibility.MESSAGE,
		style = "group:Output, align:center")
	private final String outputArchiveType = "type: DnaMoleculeArchive";

	// OUTPUT PARAMETERS
	@Parameter(label = "DnaArchive.yama", type = ItemIO.OUTPUT)
	private DnaMoleculeArchive dnaMoleculeArchive;

	private SingleMoleculeArchive archive1, archive2, archive3;

	@Override
	public void initialize() {
		if (roiManager != null) {
			final MutableModuleItem<String> roiCountItem = getInfo().getMutableInput(
				"roiCount", String.class);
			roiCountItem.setValue(this, roiManager.getRoisAsArray().length +
				" molecules found in manager for integration.");
		}

		ArrayList<String> archiveNames = new ArrayList<String>();
		archiveNames.add("None");

		for (MoleculeArchive<?, ?, ?, ?> archive : moleculeArchiveService
			.getArchives())
			if (archive instanceof SingleMoleculeArchive) archiveNames.add(archive
				.getName());

		final MutableModuleItem<String> singleMoleculeArchive1Items = getInfo()
			.getMutableInput("archive1InputName", String.class);
		singleMoleculeArchive1Items.setChoices(archiveNames);

		final MutableModuleItem<String> singleMoleculeArchive2Items = getInfo()
			.getMutableInput("archive2InputName", String.class);
		singleMoleculeArchive2Items.setChoices(archiveNames);

		final MutableModuleItem<String> singleMoleculeArchive3Items = getInfo()
			.getMutableInput("archive3InputName", String.class);
		singleMoleculeArchive3Items.setChoices(archiveNames);
	}

	@Override
	public void run() {

		if (!archive1InputName.equals("None") && moleculeArchiveService.contains(
			archive1InputName)) archive1 =
				(SingleMoleculeArchive) moleculeArchiveService.getArchive(
					archive1InputName);
		if (!archive2InputName.equals("None") && moleculeArchiveService.contains(
			archive2InputName)) archive2 =
				(SingleMoleculeArchive) moleculeArchiveService.getArchive(
					archive2InputName);
		if (!archive3InputName.equals("None") && moleculeArchiveService.contains(
			archive3InputName)) archive3 =
				(SingleMoleculeArchive) moleculeArchiveService.getArchive(
					archive3InputName);

		// Build log
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Build DNA Archive");

		addInputParameterLog(builder);
		log += builder.buildParameterList();

		// Output first part of log message...
		logService.info(log);

		// Used to store list of DNAs which are the basis for the DNA records.
		ArrayList<DNASegment> DNASegments = new ArrayList<DNASegment>();

		Roi[] rois = roiManager.getRoisAsArray();
		if (rois.length == 0) {
			logService.info("Found 0 DNA records in the RoiManager.");
			return;
		}
		else {
			logService.info("Found " + rois.length +
				" DNA records in the RoiManager.");
		}

		// Let's build the DNASegment records
		for (Roi roi : rois) {
			Line line = (Line) roi;
			DNASegment dnaSegment = new DNASegment(line.x1d - 0.5, line.y1d - 0.5,
				line.x2d - 0.5, line.y2d - 0.5);
			DNASegments.add(dnaSegment);
		}

		if (archive1InputName.equals("None")) {
			logService.info("SingleMoleculeArchive 1 was not specified.");
			return;
		}
		else {

		}

		MarsOMEMetadata metadata1 = archive1.metadata().findFirst().get();
		if (archive2 != null) {
			metadata1.merge(archive2.metadata().findFirst().get());
		}

		if (archive3 != null) {
			metadata1.merge(archive3.metadata().findFirst().get());
		}

		metadata1.setParameter("DnaMoleculeCount", rois.length);

		// Build a new DnaMoleculeArchive
		dnaMoleculeArchive = new DnaMoleculeArchive("DnaArchive.yama");
		dnaMoleculeArchive.putMetadata(metadata1);

		// Build KDTrees for fast searching
		RadiusNeighborSearchOnKDTree<MoleculePosition> archive1PositionSearcher =
			null;
		RadiusNeighborSearchOnKDTree<MoleculePosition> archive2PositionSearcher =
			null;
		RadiusNeighborSearchOnKDTree<MoleculePosition> archive3PositionSearcher =
			null;

		archive1PositionSearcher = getMoleculeSearcher(archive1);

		if (archive2 != null) archive2PositionSearcher = getMoleculeSearcher(
			archive2);

		if (archive3 != null) archive3PositionSearcher = getMoleculeSearcher(
			archive3);

		for (DNASegment dnaSegment : DNASegments) {
			DnaMolecule dnaMolecule = new DnaMolecule(MarsMath.getUUID58());
			dnaMolecule.setMetadataUID(metadata1.getUID());
			dnaMolecule.setImage(metadata1.getImage(0).getImageID());
			dnaMolecule.setParameter("Dna_Top_X1", dnaSegment.getX1());
			dnaMolecule.setParameter("Dna_Top_Y1", dnaSegment.getY1());
			dnaMolecule.setParameter("Dna_Bottom_X2", dnaSegment.getX2());
			dnaMolecule.setParameter("Dna_Bottom_Y2", dnaSegment.getY2());

			MarsTable mergedTable = new MarsTable();

			ArrayList<SingleMolecule> moleculesOnDNA = findMoleculesOnDna(
				archive1PositionSearcher, archive1, dnaSegment);
			if (moleculesOnDNA.size() != 0) {
				addToMergedTable(mergedTable, moleculesOnDNA, archive1, archive1Name,
					dnaSegment);
			}
			dnaMolecule.setParameter("Number_" + archive1Name, moleculesOnDNA.size());

			if (archive2 != null) {
				moleculesOnDNA = findMoleculesOnDna(archive2PositionSearcher, archive2,
					dnaSegment);
				if (moleculesOnDNA.size() != 0) {
					addToMergedTable(mergedTable, moleculesOnDNA, archive2, archive2Name,
						dnaSegment);
				}
				dnaMolecule.setParameter("Number_" + archive2Name, moleculesOnDNA
					.size());
			}

			if (archive3 != null) {
				moleculesOnDNA = findMoleculesOnDna(archive3PositionSearcher, archive3,
					dnaSegment);
				if (moleculesOnDNA.size() != 0) {
					addToMergedTable(mergedTable, moleculesOnDNA, archive3, archive3Name,
						dnaSegment);
				}
				dnaMolecule.setParameter("Number_" + archive3Name, moleculesOnDNA
					.size());
			}

			if (mergedTable.isEmpty()) continue;

			dnaMolecule.setTable(mergedTable);
			dnaMoleculeArchive.put(dnaMolecule);
		}
		
		if (dnaMoleculeArchive.getNumberOfMolecules() == 0) {
			uiService.showDialog("No single molecules from the archives provided colocalize\n" +
			                     "with the DNA molecules discovered in the ROI manager.\n", DialogPrompt.MessageType.ERROR_MESSAGE);
			dnaMoleculeArchive = null;
			logService.info(LogBuilder.endBlock(false));
			return;
		}
		
		logService.info(LogBuilder.endBlock(true));

		log += "\n" + LogBuilder.endBlock(true);
		dnaMoleculeArchive.logln(log);
		dnaMoleculeArchive.logln("   ");
	}

	private void addToMergedTable(MarsTable mergedTable,
		ArrayList<SingleMolecule> moleculesOnDNA, SingleMoleculeArchive archive,
		String name, DNASegment dnaSegment)
	{
		int index = 1;
		for (SingleMolecule molecule : moleculesOnDNA) {
			MarsTable table = molecule.getTable().clone();

			// We need to make sure to fill the table with Double.NaN values
			// this will over write the scijava default value of 0.0.
			if (!mergedTable.isEmpty() && mergedTable.getRowCount() < table
				.getRowCount())
			{
				for (int row = mergedTable.getRowCount(); row < table
					.getRowCount(); row++)
				{
					mergedTable.appendRow();
					for (int col = 0; col < mergedTable.getColumnCount(); col++)
						mergedTable.setValue(col, row, Double.NaN);
				}
			}
			else if (!mergedTable.isEmpty() && mergedTable.getRowCount() > table
				.getRowCount())
			{
				for (int row = table.getRowCount(); row < mergedTable
					.getRowCount(); row++)
				{
					table.appendRow();
					for (int col = 0; col < table.getColumnCount(); col++)
						table.setValue(col, row, Double.NaN);
				}
			}

			// Distance from the DNA top END
			DoubleColumn dnaPositionColumn = new DoubleColumn(name + "_" + index +
				"_Position_on_DNA");
			for (int row = 0; row < table.getRowCount(); row++) {
				dnaPositionColumn.add(dnaSegment.getPositionOnDNA(table.getValue(
					xColumn, row), table.getValue(yColumn, row), DNALength));
			}

			for (int col = 0; col < table.getColumnCount(); col++) {
				table.get(col).setHeader(name + "_" + index + "_" + table.get(col)
					.getHeader());
				mergedTable.add(table.get(col));
			}

			mergedTable.add(dnaPositionColumn);

			index++;
		}
	}

	private RadiusNeighborSearchOnKDTree<MoleculePosition> getMoleculeSearcher(
		SingleMoleculeArchive archive)
	{
		ArrayList<MoleculePosition> moleculePositionList =
			new ArrayList<MoleculePosition>();

		archive.molecules().forEach(molecule -> moleculePositionList.add(
			new MoleculePosition(molecule.getUID(), molecule.getTable().median(
				Peak.X), molecule.getTable().median(Peak.Y))));

		KDTree<MoleculePosition> moleculesTree = new KDTree<MoleculePosition>(
			moleculePositionList, moleculePositionList);

		return new RadiusNeighborSearchOnKDTree<MoleculePosition>(moleculesTree);
	}

	private ArrayList<SingleMolecule> findMoleculesOnDna(
		RadiusNeighborSearchOnKDTree<MoleculePosition> archivePositionSearcher,
		SingleMoleculeArchive archive, DNASegment dnaSegment)
	{
		ArrayList<SingleMolecule> moleculesLocated =
			new ArrayList<SingleMolecule>();

		archivePositionSearcher.search(dnaSegment, radius + dnaSegment.getLength() / 2,
			false);

		// build DNA fit
		double x1 = dnaSegment.getX1();
		double y1 = dnaSegment.getY1();

		double x2 = dnaSegment.getX2();
		double y2 = dnaSegment.getY2();

		for (int j = 0; j < archivePositionSearcher.numNeighbors(); j++) {
			MoleculePosition moleculePosition = archivePositionSearcher.getSampler(j)
				.get();

			double distance;

			// Before we add the the molecules we need to constrain positions to just
			// within radius of DNA....
			if (moleculePosition.getY() < y1) {
				// the molecules is above the DNA
				distance = Math.sqrt((moleculePosition.getX() - x1) * (moleculePosition
					.getX() - x1) + (moleculePosition.getY() - y1) * (moleculePosition
						.getY() - y1));
			}
			else if (moleculePosition.getY() > y2) {
				distance = Math.sqrt((moleculePosition.getX() - x2) * (moleculePosition
					.getX() - x2) + (moleculePosition.getY() - y2) * (moleculePosition
						.getY() - y2));
			}
			else {
				// find the x center position of the DNA for the molecule y position.

				// If there is no intercept just take top x1.
				double DNAx;
				if (x1 == x2) DNAx = x1;
				else {
						SimpleRegression linearFit = new SimpleRegression(true);
						linearFit.addData(x1, y1);
						linearFit.addData(x2, y2);
						DNAx = (moleculePosition.getY() - linearFit.getIntercept()) / linearFit.getSlope();
				}

				distance = Math.abs(moleculePosition.getX() - DNAx);
			}

			// other conditions

			if (distance < radius) {
				moleculesLocated.add(archive.get(moleculePosition.getUID()));
			}
		}

		return moleculesLocated;
	}

	private void addInputParameterLog(LogBuilder builder) {
		builder.addParameter("Search radius around DNA", String.valueOf(radius));
		builder.addParameter("DNA length in bps", String.valueOf(DNALength));
		builder.addParameter("X column", String.valueOf(xColumn));
		builder.addParameter("Y column", String.valueOf(yColumn));
		builder.addParameter("SingleMoleculeArchive 1", archive1InputName);
		builder.addParameter("SingleMoleculeArchive 1 Name", archive1Name);
		builder.addParameter("SingleMoleculeArchive 2", archive2InputName);
		builder.addParameter("SingleMoleculeArchive 2 Name", archive2Name);
		builder.addParameter("SingleMoleculeArchive 3", archive3InputName);
		builder.addParameter("SingleMoleculeArchive 3 Name", archive3Name);
	}

	public void setDNASearchRadius(double radius) {
		this.radius = radius;
	}

	public double getDNASearchRadius() {
		return radius;
	}

	public void setDNALength(int DNALength) {
		this.DNALength = DNALength;
	}

	public int getDNALength() {
		return DNALength;
	}

	public void setXColumn(String xColumn) {
		this.xColumn = xColumn;
	}

	public String getXColumn() {
		return xColumn;
	}

	public void setYColumn(String yColumn) {
		this.yColumn = yColumn;
	}

	public String getYColumn() {
		return yColumn;
	}

	public void setArchive1(SingleMoleculeArchive archive1) {
		this.archive1 = archive1;
	}

	public SingleMoleculeArchive getArchive1() {
		return archive1;
	}

	public void setArchive1Name(String archive1Name) {
		this.archive1Name = archive1Name;
	}

	public String getArchive1Name() {
		return archive1Name;
	}

	public void setArchive2(SingleMoleculeArchive archive2) {
		this.archive2 = archive2;
	}

	public SingleMoleculeArchive getArchive2() {
		return archive2;
	}

	public void setArchive2Name(String archive2Name) {
		this.archive2Name = archive2Name;
	}

	public String getArchive2Name() {
		return archive2Name;
	}

	public void setArchive3(SingleMoleculeArchive archive3) {
		this.archive3 = archive3;
	}

	public SingleMoleculeArchive getArchive3() {
		return archive3;
	}

	public void setArchive3Name(String archive3Name) {
		this.archive3Name = archive3Name;
	}

	public String getArchive3Name() {
		return archive3Name;
	}

	class MoleculePosition implements RealLocalizable {

		private final String UID;

		private final double x;
		private final double y;

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

		// Override from RealLocalizable interface.. so peaks can be passed to
		// KDTree and other imglib2 functions.
		@Override
		public int numDimensions() {
			// We make no effort to think beyond 2 dimensions !
			return 2;
		}

		@Override
		public double getDoublePosition(int arg0) {
			if (arg0 == 0) {
				return x;
			}
			else if (arg0 == 1) {
				return y;
			}
			else {
				return -1;
			}
		}

		@Override
		public float getFloatPosition(int arg0) {
			if (arg0 == 0) {
				return (float) x;
			}
			else if (arg0 == 1) {
				return (float) y;
			}
			else {
				return -1;
			}
		}

		@Override
		public void localize(float[] arg0) {
			arg0[0] = (float) x;
			arg0[1] = (float) y;
		}

		@Override
		public void localize(double[] arg0) {
			arg0[0] = x;
			arg0[1] = y;
		}
	}

}
