package de.mpg.biochem.mars.molecule;

import java.util.HashMap;
import java.util.stream.Stream;

import org.decimal4j.util.DoubleRounder;
import org.scijava.log.LogService;
import org.scijava.table.DoubleColumn;

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEPlane;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;

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
		builder.addParameter("Background Tag", backgroundTag);
		builder.addParameter("mode", mode);
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
				xValuesColumns.put(t, new DoubleColumn("X " + t));
				yValuesColumns.put(t, new DoubleColumn("Y " + t));
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
			driftTable.appendColumn("x");
			driftTable.appendColumn("y");

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
					xTFinalValue = xTempTable.mean("X " + t);
					yTFinalValue = yTempTable.mean("Y " + t);
				}
				else {
					xTFinalValue = xTempTable.median("X " + t);
					yTFinalValue = yTempTable.median("Y " + t);
				}

				driftTable.appendRow();
				driftTable.setValue("T", gRow, t);
				driftTable.setValue("x", gRow, xTFinalValue);
				driftTable.setValue("y", gRow, yTFinalValue);
				gRow++;
			}

			if (driftTable.getRowCount() != sizeT) linearInterpolateGaps(driftTable,
				sizeT);

			// Build Maps
			HashMap<Integer, Double> TtoXMap = new HashMap<Integer, Double>();
			HashMap<Integer, Double> TtoYMap = new HashMap<Integer, Double>();

			driftTable.rows().forEach(row -> {
				TtoXMap.put((int) row.getValue("T"), row.getValue("x"));
				TtoYMap.put((int) row.getValue("T"), row.getValue("y"));
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
					table.setValue("x", table.getRowCount() - 1, table.getValue("x", i -
						1) + w * (table.getValue("x", i) - table.getValue("x", i - 1)) /
							(current_T - previous_T));
					table.setValue("y", table.getRowCount() - 1, table.getValue("y", i -
						1) + w * (table.getValue("y", i) - table.getValue("y", i - 1)) /
							(current_T - previous_T));
				}
			}
		}

		// fill ends if points are missing there...
		if (table.getValue("T", 0) > 1) {
			for (int t = 0; t < table.getValue("T", 0); t++) {
				table.appendRow();
				table.setValue("T", table.getRowCount() - 1, t);
				table.setValue("x", table.getRowCount() - 1, table.getValue("x", 0));
				table.setValue("y", table.getRowCount() - 1, table.getValue("y", 0));
			}
		}

		if (table.getValue("T", rows - 1) != sizeT) {
			for (int t = (int) table.getValue("T", rows - 1) +
				1; t < sizeT; sizeT++)
			{
				table.appendRow();
				table.setValue("T", table.getRowCount() - 1, t);
				table.setValue("x", table.getRowCount() - 1, table.getValue("x", rows -
					1));
				table.setValue("y", table.getRowCount() - 1, table.getValue("y", rows -
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
		HashMap<String, HashMap<Double, Double>> metaToMapX =
			new HashMap<String, HashMap<Double, Double>>();
		HashMap<String, HashMap<Double, Double>> metaToMapY =
			new HashMap<String, HashMap<Double, Double>>();

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

			HashMap<Double, Double> TtoXMap = metaToMapX.get(molecule
				.getMetadataUID());
			HashMap<Double, Double> TtoYMap = metaToMapY.get(molecule
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

	private static HashMap<Double, Double> getToXDriftMap(MarsMetadata meta, final int channel) {
		HashMap<Double, Double> TtoColumn = new HashMap<Double, Double>();

		for (int t = 0; t < meta.getImage(0).getSizeT(); t++)
			TtoColumn.put((double) t, meta.getPlane(0, 0, channel, t).getXDrift());
		
		return TtoColumn;
	}

	private static HashMap<Double, Double> getToYDriftMap(MarsMetadata meta, final int channel) {
		HashMap<Double, Double> TtoColumn = new HashMap<Double, Double>();

		for (int t = 0; t < meta.getImage(0).getSizeT(); t++)
			TtoColumn.put((double) t, meta.getPlane(0, 0, channel, t).getYDrift());

		return TtoColumn;
	}
}
