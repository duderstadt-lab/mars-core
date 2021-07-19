/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2021 Karl Duderstadt
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
package de.mpg.biochem.mars.molecule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.decimal4j.util.DoubleRounder;
import org.scijava.log.LogService;
import org.scijava.table.DoubleColumn;

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEPlane;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsPosition;
import de.mpg.biochem.mars.util.MarsRegion;

public class ArchiveUtils {
	
	public static void calculateDrift(
		MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive,
		String backgroundTag, String input_x, String input_y, boolean use_incomplete_traces, String mode,
		String zeroPoint)
	{
		calculateDrift(archive, backgroundTag, input_x, input_y, use_incomplete_traces, mode,
				zeroPoint, false, 0, null);
	}
	
	public static void calculateDrift(
			MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive,
			String backgroundTag, String input_x, String input_y, boolean use_incomplete_traces, String mode,
			String zeroPoint, LogService logService)
		{
		calculateDrift(archive, backgroundTag, input_x, input_y, use_incomplete_traces, mode,
				zeroPoint, false, 0, null);
		}
	
	public static void calculateDrift(
		MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive,
		String backgroundTag, String input_x, String input_y, boolean use_incomplete_traces, String mode,
		String zeroPoint, final boolean singleChannel, final int theC, LogService logService)
	{
		double starttime = System.currentTimeMillis();
		
		final int channel = (singleChannel) ? theC : 0;

		// Build log message
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Drift Calculator");

		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("Input X", input_x);
		builder.addParameter("Input Y", input_y);
		builder.addParameter("Use incomplete traces", String.valueOf(
			use_incomplete_traces));
		builder.addParameter("Zero point", zeroPoint);
		builder.addParameter("Background tag", backgroundTag);
		builder.addParameter("Mode", mode);
		log += builder.buildParameterList();

		// Output first part of log message...
		if (logService != null) logService.info(log);

		archive.logln(log);

		// We will want to calculate the background for each dataset
		// in the archive separately
		for (String metaUID : archive.getMetadataUIDs()) {
			MarsMetadata meta = archive.getMetadata(metaUID);
			// Let's find the last T
			// MarsTable metaDataTable = meta.getDataTable();

			int sizeT = meta.getImage(0).getSizeT();

			HashMap<Integer, DoubleColumn> xValuesColumns =
				new HashMap<Integer, DoubleColumn>();
			HashMap<Integer, DoubleColumn> yValuesColumns =
				new HashMap<Integer, DoubleColumn>();

			for (int t = 0; t <= sizeT; t++) {
				xValuesColumns.put(t, new DoubleColumn("X_" + t));
				yValuesColumns.put(t, new DoubleColumn("Y_" + t));
			}

			if (use_incomplete_traces) {
				// For all molecules in this dataset that are marked with the background
				// tag and have all Ts
				Stream<String> UIDs = archive.getMoleculeUIDs().stream().filter(UID -> archive
					.getMetadataUIDforMolecule(UID).equals(meta.getUID())).filter(
						UID -> archive.moleculeHasTag(UID, backgroundTag));
				
				if (singleChannel)
					UIDs = UIDs.filter(UID -> archive.getChannel(UID) == channel);
				
				UIDs.forEach(UID -> {
							MarsTable datatable = archive.get(UID).getTable();
							double x_mean = datatable.mean(input_x);
							double y_mean = datatable.mean(input_y);

							for (int row = 0; row < datatable.getRowCount(); row++) {
								int t = (int) datatable.getValue("T", row);
								xValuesColumns.get(t).add(datatable.getValue(input_x, row) -
									x_mean);
								yValuesColumns.get(t).add(datatable.getValue(input_y, row) -
									y_mean);
							}
						});
			}
			else {
				// For all molecules in this dataset that are marked with the background
				// tag and have all Ts
				long[] num_full_traj = new long[1];
				num_full_traj[0] = 0;
				Stream<String> UIDs = archive.getMoleculeUIDs().stream().filter(UID -> archive
					.getMetadataUIDforMolecule(UID).equals(meta.getUID())).filter(
						UID -> archive.moleculeHasTag(UID, backgroundTag));
				
				if (singleChannel)
					UIDs = UIDs.filter(UID -> archive.getChannel(UID) == channel);
				
				UIDs.forEach(UID -> {
							MarsTable datatable = archive.get(UID).getTable();
							if (archive.get(UID).getTable().getRowCount() == sizeT) {
								double x_mean = datatable.mean(input_x);
								double y_mean = datatable.mean(input_y);

								for (int row = 0; row < datatable.getRowCount(); row++) {
									int t = (int) datatable.getValue("T", row);
									xValuesColumns.get(t).add(datatable.getValue(input_x, row) -
										x_mean);
									yValuesColumns.get(t).add(datatable.getValue(input_y, row) -
										y_mean);
								}
								num_full_traj[0]++;
							}
						});

				if (num_full_traj[0] == 0) {
					archive.logln(
						"Aborting. No complete molecules with all Ts found for dataset " +
							meta.getUID() + "!");
					if (logService != null) logService.info(
						"Aborting. No complete molecules with all Ts found for dataset " +
							meta.getUID() + "!");
					continue;
				}
			}

			MarsTable driftTable = new MarsTable();
			driftTable.appendColumn("T");
			driftTable.appendColumn("X");
			driftTable.appendColumn("Y");

			int gRow = 0;
			for (int t = 0; t <= sizeT; t++) {
				if (xValuesColumns.get(t).size() == 0 || yValuesColumns.get(t)
					.size() == 0) continue;

				double xTFinalValue = Double.NaN;
				double yTFinalValue = Double.NaN;

				MarsTable xTempTable = new MarsTable();
				xTempTable.add(xValuesColumns.get(t));

				MarsTable yTempTable = new MarsTable();
				yTempTable.add(yValuesColumns.get(t));

				if (mode.equals("mean")) {
					xTFinalValue = xTempTable.mean("X_" + t);
					yTFinalValue = yTempTable.mean("Y_" + t);
				}
				else {
					xTFinalValue = xTempTable.median("X_" + t);
					yTFinalValue = yTempTable.median("Y_" + t);
				}

				driftTable.appendRow();
				driftTable.setValue("T", gRow, t);
				driftTable.setValue("X", gRow, xTFinalValue);
				driftTable.setValue("Y", gRow, yTFinalValue);
				gRow++;
			}

			if (driftTable.getRowCount() != sizeT) linearInterpolateGaps(driftTable,
				sizeT);

			// Build Maps
			Map<Integer, Double> TtoXMap = new HashMap<Integer, Double>();
			Map<Integer, Double> TtoYMap = new HashMap<Integer, Double>();

			driftTable.rows().forEach(row -> {
				TtoXMap.put((int) row.getValue("T"), row.getValue("X"));
				TtoYMap.put((int) row.getValue("T"), row.getValue("Y"));
			});

			Stream<MarsOMEPlane> planes = meta.getImage(0).planes();
			
			if (singleChannel)
				planes = planes.filter(plane -> plane.getC() == channel);
			
			planes.forEach(plane -> {
				plane.setXDrift(TtoXMap.get(plane.getT()));
				plane.setYDrift(TtoYMap.get(plane.getT()));
			});

			double xZeroPoint = 0;
			double yZeroPoint = 0;

			
			
			if (zeroPoint.equals("beginning")) {
				xZeroPoint = meta.getPlane(0, 0, channel, 0).getXDrift();
				yZeroPoint = meta.getPlane(0, 0, channel, 0).getYDrift();
			}
			else if (zeroPoint.equals("end")) {
				xZeroPoint = meta.getPlane(0, 0, channel, meta.getImage(0).getSizeT() - 1)
					.getXDrift();
				yZeroPoint = meta.getPlane(0, 0, channel, meta.getImage(0).getSizeT() - 1)
					.getYDrift();
			}

			final double xZeroPointFinal = xZeroPoint;
			final double yZeroPointFinal = yZeroPoint;
			
			planes = meta.getImage(0).planes();
			
			if (singleChannel)
				planes = planes.filter(plane -> plane.getC() == channel);

			planes.forEach(plane -> {
				plane.setXDrift(plane.getXDrift() - xZeroPointFinal);
				plane.setYDrift(plane.getYDrift() - yZeroPointFinal);
			});

			archive.putMetadata(meta);
		}
		if (logService != null)  {
			logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
				starttime) / 60000, 2) + " minutes.");
			logService.info(LogBuilder.endBlock(true));
		}
		archive.logln("\n" + LogBuilder.endBlock(true));
		archive.logln("  ");
	}
	
	private static void linearInterpolateGaps(MarsTable table, int sizeT) {
		int rows = table.getRowCount();

		for (int i = 1; i < rows; i++) {
			// Check whether there is a gap in the T index...
			int previous_T = (int) table.getValue("T", i - 1);
			int current_T = (int) table.getValue("T", i);
			if (previous_T != current_T - 1) {
				for (int w = 1; w < current_T - previous_T; w++) {
					table.appendRow();
					table.setValue("T", table.getRowCount() - 1, previous_T + w);
					table.setValue("X", table.getRowCount() - 1, table.getValue("X", i -
						1) + w * (table.getValue("X", i) - table.getValue("X", i - 1)) /
							(current_T - previous_T));
					table.setValue("Y", table.getRowCount() - 1, table.getValue("Y", i -
						1) + w * (table.getValue("Y", i) - table.getValue("Y", i - 1)) /
							(current_T - previous_T));
				}
			}
		}

		// fill ends if points are missing there...
		if (table.getValue("T", 0) > 1) {
			for (int t = 0; t < table.getValue("T", 0); t++) {
				table.appendRow();
				table.setValue("T", table.getRowCount() - 1, t);
				table.setValue("X", table.getRowCount() - 1, table.getValue("X", 0));
				table.setValue("Y", table.getRowCount() - 1, table.getValue("Y", 0));
			}
		}

		if (table.getValue("T", rows - 1) != sizeT) {
			for (int t = (int) table.getValue("T", rows - 1) +
				1; t < sizeT; sizeT++)
			{
				table.appendRow();
				table.setValue("T", table.getRowCount() - 1, t);
				table.setValue("X", table.getRowCount() - 1, table.getValue("X", rows -
					1));
				table.setValue("Y", table.getRowCount() - 1, table.getValue("Y", rows -
					1));
			}
		}

		// now that we have added all the new rows we need to resort the table by T.
		table.sort(true, "T");
	}
	
	public static void correctDrift(
			MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive,
			String input_x, String input_y, String output_x,
			String output_y)
		{
			correctDrift(archive, input_x, input_y, output_x, output_y, false, 0, null);
		}
	
	public static void correctDrift(
			MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive,
			String input_x, String input_y, String output_x,
			String output_y, LogService logService)
		{
			correctDrift(archive, input_x, input_y, output_x, output_y, false, 0, logService);
		}
	
	public static void updateTableHeaders(MoleculeArchive<Molecule,MarsMetadata,?,?> archive) {
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Updating table headers");

		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("Outdated schema from", archive.properties().getInputSchema());
		log += builder.buildParameterList();
		
		//Map of header name changes to use for updating parameters and segments tables
		Map<String, String> oldHeaderToNewHeader = new ConcurrentHashMap<String, String>();
		
		//Check and update molecule table headers
		archive.parallelMolecules().forEach(molecule -> {
			MarsTable table = molecule.getTable();
			String[] headers = table.getColumnHeadings();
			
			if (molecule instanceof DnaMolecule) {
				for (int col=0; col<headers.length; col++) {
					String header = headers[col];
					if (header.endsWith("_x"))
				    	table.setColumnHeader(col, header.substring(0, header.length() - 1) + "X");
					else if (header.endsWith("_y"))
						table.setColumnHeader(col, header.substring(0, header.length() - 1) + "Y");
					else if (header.endsWith("_height"))
						table.setColumnHeader(col, header.substring(0, header.length() - 6) + "Height");
					else if (header.endsWith("_baseline"))
						table.setColumnHeader(col, header.substring(0, header.length() - 8) + "Baseline");
					else if (header.endsWith("_sigma"))
						table.setColumnHeader(col, header.substring(0, header.length() - 5) + "Sigma");
					else if (header.endsWith("_intensity"))
						table.setColumnHeader(col, header.substring(0, header.length() - 9) + "Intensity");
					else if (header.endsWith("_medianBackground"))
						table.setColumnHeader(col, header.substring(0, header.length() - 16) + "Median_background");
					else if (header.endsWith("_Time (s)"))
						table.setColumnHeader(col, header.substring(0, header.length() - 8) + "Time_(s)");
					else if (header.endsWith("_x_drift_corr"))
						table.setColumnHeader(col, header.substring(0, header.length() - 12) + "X_drift_corr");
					else if (header.endsWith("_y_drift_corr"))
						table.setColumnHeader(col, header.substring(0, header.length() - 12) + "Y_drift_corr");
					
					if (!header.equals(table.getColumnHeader(col)))
						oldHeaderToNewHeader.put(header, table.getColumnHeader(col));
				}
				
				double x1 = molecule.getParameter("Dna_Top_x1");
				molecule.setParameter("Dna_Top_X1", x1);
				molecule.removeParameter("Dna_Top_x1");
				
				double y1 = molecule.getParameter("Dna_Top_y1");
				molecule.setParameter("Dna_Top_Y1", y1);
				molecule.removeParameter("Dna_Top_y1");
				
				double x2 = molecule.getParameter("Dna_Bottom_x2");
				molecule.setParameter("Dna_Bottom_X2", x2);
				molecule.removeParameter("Dna_Bottom_x2");
				
				double y2 = molecule.getParameter("Dna_Bottom_y2");
				molecule.setParameter("Dna_Bottom_Y2", y2);
				molecule.removeParameter("Dna_Bottom_y2");
			} else {
				for (int col=0; col<headers.length; col++) {
					String header = headers[col];
					switch (header) {
				    	case "x":
				    		table.setColumnHeader(col, "X");
				    		break;
				    	case "y":
				    		table.setColumnHeader(col, "Y");
				    		break;
				    	case "height":
				    		table.setColumnHeader(col, "Height");
				    		break;
				    	case "baseline":
				    		table.setColumnHeader(col, "Baseline");
				    		break;
				    	case "sigma":
				    		table.setColumnHeader(col, "Sigma");
				    		break;
				    	case "intensity":
				    		table.setColumnHeader(col, "Intensity");
				    		break;
				    	case "medianBackground":
				    		table.setColumnHeader(col, "Median_background");
				    		break;
				    	case "Time (s)":
				    		table.setColumnHeader(col, "Time_(s)");
				    		break;
				    	case "area":
				    		table.setColumnHeader(col, "Area");
				    		break;
				    	case "length":
				    		table.setColumnHeader(col, "Length");
				    		break;
				    	case "x_drift_corr":
				    		table.setColumnHeader(col, "X_drift_corr");
				    		break;
				    	case "y_drift_corr":
				    		table.setColumnHeader(col, "Y_drift_corr");
				    		break;
					}
					
					if (!header.equals(table.getColumnHeader(col)))
						oldHeaderToNewHeader.put(header, table.getColumnHeader(col));
				}
			}
			
			//Check and update molecule segment table headers and table title
			for (ArrayList<String> tableColumnNames : molecule.getSegmentsTableNames()) {
				MarsTable segmentTable = molecule.getSegmentsTable(tableColumnNames);
				String[] segmentHeaders = segmentTable.getColumnHeadings();
				
				for (int col=0; col<segmentHeaders.length; col++) {
					String header = segmentHeaders[col];
					switch (header) {
				    	case "x1":
				    		table.setColumnHeader(col, "X1");
				    		break;
				    	case "y1":
				    		table.setColumnHeader(col, "Y1");
				    		break;
				    	case "x2":
				    		table.setColumnHeader(col, "X2");
				    		break;
				    	case "y2":
				    		table.setColumnHeader(col, "Y2");
				    		break;
				    	case "sigma_A":
				    		table.setColumnHeader(col, "Sigma_A");
				    		break;
				    	case "sigma_B":
				    		table.setColumnHeader(col, "Sigma_B");
				    		break;
					}
				}
			}
			
			//Now we need to use oldHeaderToNewHeader to update segment table titles
			//regions and positions... what am I forgetting??
			
			for (ArrayList<String> tableColumnNames : molecule.getSegmentsTableNames()) {
				MarsTable segmentsTable = molecule.getSegmentsTable(tableColumnNames);
				
				if (oldHeaderToNewHeader.containsKey(tableColumnNames.get(0)) || 
						oldHeaderToNewHeader.containsKey(tableColumnNames.get(1))) {
					String xColumn = (oldHeaderToNewHeader.containsKey(tableColumnNames.get(0))) ? 
						oldHeaderToNewHeader.get(tableColumnNames.get(0)) : tableColumnNames.get(0);
						
					String yColumn = (oldHeaderToNewHeader.containsKey(tableColumnNames.get(1))) ? 
						oldHeaderToNewHeader.get(tableColumnNames.get(1)) : tableColumnNames.get(1);
						
					String region = tableColumnNames.get(2);
						
					String tableTitle = (region.equals("")) ? yColumn + "_vs_" + xColumn : yColumn + "_vs_" + xColumn + "_" + region;
					segmentsTable.setName(tableTitle);
						
					molecule.removeSegmentsTable(tableColumnNames);
					molecule.putSegmentsTable(xColumn, yColumn, region, segmentsTable);
				}
			}
			
			for (String regionName : molecule.getRegionNames()) {
				MarsRegion region = molecule.getRegion(regionName);
				if (oldHeaderToNewHeader.containsKey(region.getColumn()))
					region.setColumn(oldHeaderToNewHeader.get(region.getColumn()));
			}
			
			for (String positionName : molecule.getPositionNames()) {
				MarsPosition position = molecule.getPosition(positionName);
				if (oldHeaderToNewHeader.containsKey(position.getColumn()))
					position.setColumn(oldHeaderToNewHeader.get(position.getColumn()));
			}
			
			//Have to put it back in to ensure indexing, also for virtual archives..
			archive.put(molecule);
		});
		
		archive.parallelMetadata().forEach(metadata -> {
			for (String regionName : metadata.getRegionNames()) {
				MarsRegion region = metadata.getRegion(regionName);
				if (oldHeaderToNewHeader.containsKey(region.getColumn()))
					region.setColumn(oldHeaderToNewHeader.get(region.getColumn()));
			}
			
			for (String positionName : metadata.getPositionNames()) {
				MarsPosition position = metadata.getPosition(positionName);
				if (oldHeaderToNewHeader.containsKey(position.getColumn()))
					position.setColumn(oldHeaderToNewHeader.get(position.getColumn()));
			}
			
			archive.putMetadata(metadata);
		});
		
		try {
			archive.rebuildIndexes();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		log += "\n" + LogBuilder.endBlock(true);
		archive.logln(log);
		archive.logln("   ");
	}
	
	public static void correctDrift(
		MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive,
		String input_x, String input_y, String output_x,
		String output_y, final boolean singleChannel, final int theC, LogService logService)
	{
		// Let's keep track of the time it takes
		double starttime = System.currentTimeMillis();
		
		final int channel = (singleChannel) ? theC : 0;

		// Build log message
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Drift Corrector");

		builder.addParameter("MoleculeArchive", archive.getName());
		builder.addParameter("Input X", input_x);
		builder.addParameter("Input Y", input_y);
		builder.addParameter("Output X", output_x);
		builder.addParameter("Output Y", output_y);
		log += builder.buildParameterList();

		// Output first part of log message...
		if (logService != null) logService.info(log);
		archive.logln(log);

		// Build maps from slice to x and slice to y for each metadataset
		HashMap<String, Map<Double, Double>> metaToMapX =
			new HashMap<String, Map<Double, Double>>();
		HashMap<String, Map<Double, Double>> metaToMapY =
			new HashMap<String, Map<Double, Double>>();

		for (String metaUID : archive.getMetadataUIDs()) {
			MarsMetadata meta = archive.getMetadata(metaUID);
			metaToMapX.put(meta.getUID(), getToXDriftMap(meta, channel));
			metaToMapY.put(meta.getUID(), getToYDriftMap(meta, channel));
		}

		// Loop through each molecule and calculate drift corrected traces...
		Stream<String> UIDs = archive.getMoleculeUIDs().parallelStream();
		
		if (singleChannel)
			UIDs.filter(UID -> archive.getChannel(UID) == channel);
		
		UIDs.forEach(UID -> {
			Molecule molecule = archive.get(UID);

			if (molecule == null) {
				if (logService != null) logService.error("No record found for molecule with UID " + UID +
					". Could be due to data corruption. Continuing with the rest.");
				archive.logln("No record found for molecule with UID " + UID +
					". Could be due to data corruption. Continuing with the rest.");
				return;
			}

			Map<Double, Double> TtoXMap = metaToMapX.get(molecule
				.getMetadataUID());
			Map<Double, Double> TtoYMap = metaToMapY.get(molecule
				.getMetadataUID());

			MarsTable datatable = molecule.getTable();

			// If the column already exists we don't need to add it
			// instead we will just be overwriting the values below..
			if (!datatable.hasColumn(output_x)) molecule.getTable().appendColumn(
				output_x);

			if (!datatable.hasColumn(output_y)) molecule.getTable().appendColumn(
				output_y);

			// If we want to retain the original coordinates then
			// we don't subtract anything except the drift.
			double meanX = 0;
			double meanY = 0;

			final double meanXFinal = meanX;
			final double meanYFinal = meanY;
			datatable.rows().forEach(row -> {
				double T = row.getValue("T");

				double molX = row.getValue(input_x) - meanXFinal;
				double backgroundX = Double.NaN;

				if (TtoXMap.containsKey(T)) backgroundX = TtoXMap.get(T);

				double x_drift_corr_value = molX - backgroundX;
				row.setValue(output_x, x_drift_corr_value);

				double molY = row.getValue(input_y) - meanYFinal;
				double backgroundY = Double.NaN;

				if (TtoYMap.containsKey(T)) backgroundY = TtoYMap.get(T);

				double y_drift_corr_value = molY - backgroundY;
				row.setValue(output_y, y_drift_corr_value);
			});

			archive.put(molecule);
		});

		if (logService != null) {
			logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
				starttime) / 60000, 2) + " minutes.");
			logService.info(LogBuilder.endBlock(true));
		}
		archive.logln("\n" + LogBuilder.endBlock(true));
		archive.logln("  ");
	}

	private static Map<Double, Double> getToXDriftMap(MarsMetadata meta, final int channel) {
		HashMap<Double, Double> TtoColumn = new HashMap<Double, Double>();

		for (int t = 0; t < meta.getImage(0).getSizeT(); t++)
			TtoColumn.put((double) t, meta.getPlane(0, 0, channel, t).getXDrift());
		
		return TtoColumn;
	}

	private static Map<Double, Double> getToYDriftMap(MarsMetadata meta, final int channel) {
		HashMap<Double, Double> TtoColumn = new HashMap<Double, Double>();

		for (int t = 0; t < meta.getImage(0).getSizeT(); t++)
			TtoColumn.put((double) t, meta.getPlane(0, 0, channel, t).getYDrift());

		return TtoColumn;
	}
}
