/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2025 Karl Duderstadt
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

package de.mpg.biochem.mars.metadata;

import io.scif.ome.services.OMEXMLService;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.imagej.Dataset;
import net.imagej.axis.Axes;

import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsMath;
import loci.common.services.ServiceException;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.meta.OMEXMLMetadata;
import ome.xml.model.enums.Binning;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.UnitsTime;
import ome.xml.model.enums.handlers.BinningEnumHandler;
import ome.xml.model.enums.handlers.UnitsTimeEnumHandler;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.Timestamp;

public class MarsOMEUtils {

	public static OMEXMLMetadata createOMEXMLMetadata(OMEXMLService omexmlService,
		Dataset dataset) throws ServiceException
	{
		OMEXMLMetadata meta = omexmlService.createOMEXMLMetadata();

		StringBuilder dimensionOrderString = new StringBuilder();

		for (int d = 0; d < dataset.numDimensions(); d++) {
			if (dataset.axis(d).type().equals(Axes.X)) {
				meta.setPixelsSizeX(new PositiveInteger((int) dataset.dimension(d)), 0);
				dimensionOrderString.append("X");
			}
			else if (dataset.axis(d).type().equals(Axes.Y)) {
				meta.setPixelsSizeY(new PositiveInteger((int) dataset.dimension(d)), 0);
				dimensionOrderString.append("Y");
			}
			else if (dataset.axis(d).type().equals(Axes.Z)) {
				meta.setPixelsSizeZ(new PositiveInteger((int) dataset.dimension(d)), 0);
				dimensionOrderString.append("Z");
			}
			else if (dataset.axis(d).type().equals(Axes.CHANNEL)) {
				meta.setPixelsSizeC(new PositiveInteger((int) dataset.dimension(d)), 0);
				dimensionOrderString.append("C");
			}
			else if (dataset.axis(d).type().equals(Axes.TIME)) {
				meta.setPixelsSizeT(new PositiveInteger((int) dataset.dimension(d)), 0);
				dimensionOrderString.append("T");
			}
		}

		if (meta.getPixelsSizeX(0) == null) {
			meta.setPixelsSizeX(new PositiveInteger(1), 0);
			dimensionOrderString.append("X");
		}

		if (meta.getPixelsSizeY(0) == null) {
			meta.setPixelsSizeY(new PositiveInteger(1), 0);
			dimensionOrderString.append("Y");
		}

		if (meta.getPixelsSizeZ(0) == null) {
			meta.setPixelsSizeZ(new PositiveInteger(1), 0);
			dimensionOrderString.append("Z");
		}

		if (meta.getPixelsSizeC(0) == null) {
			meta.setPixelsSizeC(new PositiveInteger(1), 0);
			dimensionOrderString.append("C");
		}

		if (meta.getPixelsSizeT(0) == null) {
			meta.setPixelsSizeT(new PositiveInteger(1), 0);
			dimensionOrderString.append("T");
		}

		meta.setPixelsDimensionOrder(DimensionOrder.valueOf(dimensionOrderString.toString()),
			0);

		for (int c = 0; c < meta.getPixelsSizeC(0).getNumberValue()
			.intValue(); c++)
		{
			meta.setChannelName(String.valueOf(c), 0, c);
		}

		return meta;
	}

	public static String generateMetadataUIDfromDataset(
		OMEXMLMetadata omexmlMetadata)
	{
		if (omexmlMetadata.getUUID() != null) return MarsMath.getUUID58(
			omexmlMetadata.getUUID()).substring(0, 10);

		// We should retrieve any Date or time information and use that to try to
		// generate a unique UID using a hash...
		StringBuilder uniqueDateTimeInfo = new StringBuilder();
		for (int i = 0; i < omexmlMetadata.getImageCount(); i++)
			if (omexmlMetadata.getImageAcquisitionDate(i) != null)
				uniqueDateTimeInfo.append(omexmlMetadata.getImageAcquisitionDate(i));

		if (!uniqueDateTimeInfo.toString().equals("")) return MarsMath.getFNV1aBase58(
				uniqueDateTimeInfo.toString());
		return null;
	}

	// Translator from Normal Archive (SingleMoleculeArchive, ArchMoleculeArchive)
	public static MarsOMEMetadata translateToMarsOMEMetadata(
		OLDMarsMetadata oldMetadata)
	{
		MarsOMEMetadata marsOME = new MarsOMEMetadata(oldMetadata.getUID());

		marsOME.setMicroscopeName(oldMetadata.getMicroscopeName());
		marsOME.setNotes(oldMetadata.getNotes());
		marsOME.log(oldMetadata.getLog());
		marsOME.setSourceDirectory(oldMetadata.getSourceDirectory());
		oldMetadata.getParameters().keySet().forEach(name -> marsOME.getParameters()
			.put(name, oldMetadata.getParameters().get(name)));
		oldMetadata.getTags().forEach(marsOME::addTag);
		oldMetadata.getBdvSources().forEach(marsOME::putBdvSource);
		oldMetadata.getRegionNames().forEach(name -> marsOME.putRegion(oldMetadata
			.getRegion(name)));
		oldMetadata.getPositionNames().forEach(name -> marsOME.putPosition(
			oldMetadata.getPosition(name)));

		// Create MarsOMEImage and fill in all the planes and then add it to the
		// MarsOMEMetadata...
		MarsOMEImage image = new MarsOMEImage();
		image.setImageID(0);
		image.setPixelsPhysicalSizeX(new Length(1.0d, UNITS.PIXEL));
		image.setPixelsPhysicalSizeY(new Length(1.0d, UNITS.PIXEL));
		image.setSizeZ(new PositiveInteger(1));
		// image.setAcquisitionDate(new Timestamp(oldMetadata.getCollectionDate()));

		image.setName(oldMetadata.getSourceDirectory());
		image.setDimensionOrder(DimensionOrder.valueOf("XYZCT"));

		MarsTable table = oldMetadata.getDataTable();

		String xDriftColumnName = "";
		String yDriftColumnName = "";

		// Check for drift Columns
		for (String heading : table.getColumnHeadingList()) {
			String lower = heading.toLowerCase();
			if (lower.contains("x") && lower.contains("drift")) xDriftColumnName =
				heading;
			if (lower.contains("y") && lower.contains("drift")) yDriftColumnName =
				heading;
		}

		String format = "Unknown";

		// Check format
		if (table.hasColumn("DateTime")) {
			format = "Norpix";
			// Must be Norpix data...
			image.setSizeC(new PositiveInteger(1));
			image.setSizeT(new PositiveInteger((int) table.getValue("slice", table
				.getRowCount() - 1)));

			MarsOMEChannel channel = new MarsOMEChannel();
			channel.setChannelIndex(0);
			image.setChannel(channel, 0);

			for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
				int slice = (int) table.getValue("slice", rowIndex);
				int t = slice - 1;
				MarsOMEPlane plane = new MarsOMEPlane(image, 0, rowIndex,
					new NonNegativeInteger(0), new NonNegativeInteger(0),
					new NonNegativeInteger(t));

				if (!xDriftColumnName.equals("")) plane.setXDrift(table.getValue(
					xDriftColumnName, rowIndex));
				if (!yDriftColumnName.equals("")) plane.setYDrift(table.getValue(
					yDriftColumnName, rowIndex));
				if (table.hasColumn("Time (s)")) plane.setDeltaT(new Time(table
					.getValue("Time (s)", rowIndex), UNITS.SECOND));
				if (table.hasColumn("DateTime")) plane.setStringField("DateTime", table
					.getStringValue("DateTime", rowIndex));

				image.setPlane(plane, 0, 0, t);
			}
		}
		else if (table.hasColumn("ChannelIndex")) {
			format = "Micromanager";
			// Must be Micromanager data.
			Map<Integer, String> channelNames = new LinkedHashMap<>();
			Map<Integer, String> channelBinning =
					new LinkedHashMap<>();

			table.rows().forEach(row -> {
				int channelIndex = Integer.parseInt(row.getStringValue("ChannelIndex"));
				channelBinning.put(channelIndex, row.getStringValue("Binning"));
				channelNames.put(channelIndex, row.getStringValue("Channel"));
			});
			image.setSizeC(new PositiveInteger(channelNames.size()));
			image.setSizeT(new PositiveInteger((int) table.getValue("Frame", table
				.getRowCount() - 1)));
			if (table.hasColumn("Width")) image.setSizeX(new PositiveInteger(Integer
				.valueOf(table.getStringValue("Width", 0))));
			if (table.hasColumn("Height")) image.setSizeY(new PositiveInteger(Integer
				.valueOf(table.getStringValue("Height", 0))));

			BinningEnumHandler handler = new BinningEnumHandler();

			for (int channelIndex : channelNames.keySet()) {
				MarsOMEChannel channel = new MarsOMEChannel();
				channel.setChannelIndex(channelIndex);
				channel.setName(channelNames.get(channelIndex));
				try {
					String binKey = channelBinning.get(channelIndex) + "x" +
						channelBinning.get(channelIndex);
					channel.setBinning((Binning) handler.getEnumeration(binKey));
				}
				catch (EnumerationException e) {
					e.printStackTrace();
				}
				image.setChannel(channel, channelIndex);
			}

			for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
				MarsOMEPlane plane = new MarsOMEPlane();
				plane.setImage(image);
				plane.setImageID(0);

				int c = Integer.parseInt(table.getStringValue("ChannelIndex", rowIndex));

				plane.setPlaneIndex((int) image.getPlaneIndex(0, c, rowIndex));
				plane.setZ(new NonNegativeInteger(0));
				plane.setC(new NonNegativeInteger(c));
				plane.setT(new NonNegativeInteger(rowIndex));

				for (String heading : table.getColumnHeadingList()) {
					if (xDriftColumnName.equals(heading) || yDriftColumnName.equals(
						heading)) continue;
					else if (heading.equals("FileName")) {
						plane.setFilename(table.getStringValue(heading, rowIndex));
						continue;
					}
					else if (heading.equals("Time (s)")) {
						plane.setDeltaT(new Time(table.getValue("Time (s)", rowIndex),
							UNITS.SECOND));
						continue;
					}

					// Add all unknown columns as StringFields
					plane.setStringField(heading, table.getStringValue(heading,
						rowIndex));
				}

				if (!xDriftColumnName.equals("")) plane.setXDrift(table.getValue(
					xDriftColumnName, rowIndex));
				if (!yDriftColumnName.equals("")) plane.setYDrift(table.getValue(
					yDriftColumnName, rowIndex));

				image.setPlane(plane, 0, c, rowIndex);
			}
		}
		else {
			// Unknown. We should at least have a slice column.
			// Have to guess the rest.
			image.setSizeC(new PositiveInteger(1));
			image.setSizeT(new PositiveInteger((int) table.getValue("slice", table
				.getRowCount() - 1)));

			MarsOMEChannel channel = new MarsOMEChannel();
			channel.setChannelIndex(0);
			image.setChannel(channel, 0);

			for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
				int slice = (int) table.getValue("slice", rowIndex);
				int t = slice - 1;
				MarsOMEPlane plane = new MarsOMEPlane(image, 0, rowIndex,
					new NonNegativeInteger(0), new NonNegativeInteger(0),
					new NonNegativeInteger(t));

				if (!xDriftColumnName.equals("")) plane.setXDrift(table.getValue(
					xDriftColumnName, rowIndex));
				if (!yDriftColumnName.equals("")) plane.setYDrift(table.getValue(
					yDriftColumnName, rowIndex));
				if (table.hasColumn("Time (s)")) plane.setDeltaT(new Time(table
					.getValue("Time (s)", rowIndex), UNITS.SECOND));

				image.setPlane(plane, 0, 0, t);
			}
		}

		marsOME.setImage(image, 0);

		// Build log message
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Migrated to OME format");
		builder.addParameter("From format", format);
		log += builder.buildParameterList() + "\n";
		log += LogBuilder.endBlock();

		marsOME.logln(log);

		return marsOME;
	}

	// Translator from DNA Archive
	public static MarsOMEMetadata translateDNAMetadataToMarsOMEMetadata(
		OLDMarsMetadata oldMetadata)
	{
		MarsOMEMetadata marsOME = new MarsOMEMetadata(oldMetadata.getUID());

		// First, lets merge in easy stuff...
		marsOME.setMicroscopeName(oldMetadata.getMicroscopeName());
		marsOME.setNotes(oldMetadata.getNotes());
		marsOME.log(oldMetadata.getLog());
		marsOME.setSourceDirectory(oldMetadata.getSourceDirectory());
		oldMetadata.getParameters().keySet().forEach(name -> marsOME.getParameters()
			.put(name, oldMetadata.getParameters().get(name)));
		oldMetadata.getTags().forEach(marsOME::addTag);
		oldMetadata.getBdvSources().forEach(marsOME::putBdvSource);
		oldMetadata.getRegionNames().forEach(name -> marsOME.putRegion(oldMetadata
			.getRegion(name)));
		oldMetadata.getPositionNames().forEach(name -> marsOME.putPosition(
			oldMetadata.getPosition(name)));

		// Create MarsOMEImage and fill in all the planes and then add it to the
		// MarsOMEMetadata...
		MarsOMEImage image = new MarsOMEImage();
		image.setImageID(0);
		image.setPixelsPhysicalSizeX(new Length(1.0d, UNITS.PIXEL));
		image.setPixelsPhysicalSizeY(new Length(1.0d, UNITS.PIXEL));
		image.setSizeZ(new PositiveInteger(1));
		// image.setAcquisitionDate(new Timestamp(oldMetadata.getCollectionDate()));

		image.setName(oldMetadata.getSourceDirectory());
		image.setDimensionOrder(DimensionOrder.valueOf("XYZCT"));

		MarsTable table = oldMetadata.getDataTable();

		String xDriftColumnName = "";
		String yDriftColumnName = "";

		// Check for drift Columns
		for (String heading : table.getColumnHeadingList()) {
			String lower = heading.toLowerCase();
			if (lower.contains("x") && lower.contains("drift")) xDriftColumnName =
				heading;
			if (lower.contains("y") && lower.contains("drift")) yDriftColumnName =
				heading;
		}

		// Discover channel names...
		Map<String, String> channelToColumnSuffix = new HashMap<>();
		for (int colIndex = 0; colIndex < table.getColumnCount(); colIndex++) {
			if (table.get(colIndex).getHeader().startsWith("Channel "))
				channelToColumnSuffix.put((String) table.get(colIndex).get(0), table
					.get(colIndex).getHeader().substring(8));
		}

		Map<Integer, String> channelIndexToChannel = new HashMap<>();
		for (String ch : channelToColumnSuffix.keySet())
			channelIndexToChannel.put(Integer.valueOf(table.getStringValue(
				"ChannelIndex " + channelToColumnSuffix.get(ch), 0)), ch);

		int imageID = Integer.parseInt(table.getStringValue("PositionIndex " +
			channelToColumnSuffix.get(channelIndexToChannel.get(0)), 0));
		image.setImageID(imageID);

		image.setSizeC(new PositiveInteger(channelIndexToChannel.size()));
		image.setSizeT(new PositiveInteger(table.getRowCount()));
		if (table.hasColumn("Width " + channelIndexToChannel.get(0))) image
			.setSizeX(new PositiveInteger(Integer.valueOf(table.getStringValue(
				"Width", 0))));
		if (table.hasColumn("Height " + channelIndexToChannel.get(0))) image
			.setSizeY(new PositiveInteger(Integer.valueOf(table.getStringValue(
				"Height", 0))));

		BinningEnumHandler handler = new BinningEnumHandler();

		for (int channelIndex : channelIndexToChannel.keySet()) {
			MarsOMEChannel channel = new MarsOMEChannel();
			channel.setChannelIndex(channelIndex);
			channel.setName(channelIndexToChannel.get(channelIndex));
			try {
				String binKey = "1x1";
				channel.setBinning((Binning) handler.getEnumeration(binKey));
			}
			catch (EnumerationException e) {
				e.printStackTrace();
			}
			image.setChannel(channel, channelIndex);
		}

		for (int c = 0; c < channelIndexToChannel.size(); c++)
			for (int t = 0; t < table.getRowCount(); t++) {
				MarsOMEPlane plane = new MarsOMEPlane();
				plane.setImage(image);
				plane.setImageID(imageID);

				plane.setPlaneIndex((int) image.getPlaneIndex(0, c, t));
				plane.setZ(new NonNegativeInteger(0));
				plane.setC(new NonNegativeInteger(c));
				plane.setT(new NonNegativeInteger(t));

				for (String heading : table.getColumnHeadingList()) {
					if (xDriftColumnName.equals(heading) || yDriftColumnName.equals(
						heading)) continue;
					else if (heading.equals("FileName " + channelToColumnSuffix.get(
						channelIndexToChannel.get(c))))
					{
						plane.setFilename(table.getStringValue(heading, t));
						continue;
					}
					else if (heading.equals("Time (s) " + channelToColumnSuffix.get(
						channelIndexToChannel.get(c))))
					{
						plane.setDeltaT(new Time(table.getValue("Time (s) " +
							channelToColumnSuffix.get(channelIndexToChannel.get(c)), t),
							UNITS.SECOND));
						continue;
					}

					// Add all unknown columns as StringFields
					plane.setStringField(heading, table.getStringValue(heading, t));
				}

				if (!xDriftColumnName.equals("")) plane.setXDrift(table.getValue(
					xDriftColumnName, t));
				if (!yDriftColumnName.equals("")) plane.setYDrift(table.getValue(
					yDriftColumnName, t));

				image.setPlane(plane, 0, c, t);
			}

		marsOME.setImage(image, 0);

		// Build log message
		LogBuilder builder = new LogBuilder();
		String log = LogBuilder.buildTitleBlock("Migrated to OME format");
		log += builder.buildParameterList() + "\n";
		log += LogBuilder.endBlock();

		marsOME.logln(log);

		return marsOME;
	}

	public static Map<Integer, Map<Integer, Double>> buildChannelToTtoDtMap(
		MarsMetadata marsOMEMetadata)
	{
		Map<Integer, Map<Integer, Double>> channelToTtoDtMap =
				new HashMap<>();

		// Let's build some maps from t to dt for each color...
		for (int channelIndex = 0; channelIndex < marsOMEMetadata.getImage(0)
			.getSizeC(); channelIndex++)
		{
			HashMap<Integer, Double> tToDtMap = new HashMap<>();

			final int finalChannelIndex = channelIndex;
			marsOMEMetadata.getImage(0).planes().filter(plane -> plane
				.getC() == finalChannelIndex).forEach(plane -> tToDtMap.put(plane
					.getT(), plane.getDeltaTinSeconds()));

			channelToTtoDtMap.put(channelIndex, tToDtMap);
		}

		return channelToTtoDtMap;
	}
	
	public static boolean checkOMEMetadataForDt(MarsMetadata marsOMEMetadata) {
		return marsOMEMetadata.getImage(0).planes().anyMatch(plane -> plane.getDeltaTinSeconds() != -1);
	}

	public static void getTimeFromNoprixSliceLabels(MarsMetadata marsMetadata,
		Map<Integer, String> metaDataStack)
	{
		try {
			// Set Global Collection Date for the dataset
			int DateTimeIndex1 = metaDataStack.get(0).indexOf("DateTime: ");
			String DateTimeString1 = metaDataStack.get(0).substring(DateTimeIndex1 +
				10);
			marsMetadata.getImage(0).setAcquisitionDate(getNorPixDate(
				DateTimeString1));

			final UnitsTimeEnumHandler timeHandler = new UnitsTimeEnumHandler();

			// Extract the exact time of collection of all frames.
			final long t0 = getNorPixMillisecondTime(DateTimeString1);

			marsMetadata.getImage(0).planes().forEach(plane -> {
				if (metaDataStack.containsKey(plane.getT())) {
					int dateTimeIndex2 = metaDataStack.get(plane.getT()).indexOf(
						"DateTime: ");
					String DateTimeString2 = metaDataStack.get(plane.getT()).substring(
						dateTimeIndex2 + 10);
					Time dt = null;
					try {
						double millisecondsDt = ((double) getNorPixMillisecondTime(
							DateTimeString2) - t0) / 1000;
						dt = new Time(millisecondsDt, UnitsTimeEnumHandler.getBaseUnit(
							(UnitsTime) timeHandler.getEnumeration("s")));
					}
					catch (ParseException | EnumerationException e) {
						e.printStackTrace();
					}
					plane.setDeltaT(dt);
				}
			});
		}
		catch (ParseException e1) {
			// e1.printStackTrace();
		}
	}

	private static long getNorPixMillisecondTime(String strTime)
		throws ParseException
	{
		SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd HHmmssSSS");
		Date convertedDate = formatter.parse(strTime.substring(0, strTime.length() -
			4));
		return convertedDate.getTime();
	}

	private static Timestamp getNorPixDate(String strTime) throws ParseException {
		SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd HHmmssSSS");
		Date convertedDate = formatter.parse(strTime.substring(0, strTime.length() -
			4));
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
		String nowAsISO = df.format(convertedDate);
		return new Timestamp(nowAsISO);
	}
}
