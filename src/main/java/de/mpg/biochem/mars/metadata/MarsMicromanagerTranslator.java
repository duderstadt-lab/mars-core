/*
 * #%L
 * SCIFIO support for the OME data model, including OME-XML and OME-TIFF.
 * %%
 * Copyright (C) 2013 - 2017 Board of Regents of the University of
 * Wisconsin-Madison
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

import io.scif.ome.translators.*;

import io.scif.FormatException;
import io.scif.common.DateTools;
import io.scif.io.Location;
import io.scif.ome.OMEMetadata;
import io.scif.ome.services.OMEMetadataService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import org.scijava.Priority;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.mpg.biochem.mars.metadata.MarsMicromanagerFormat.Metadata;
import de.mpg.biochem.mars.metadata.MarsMicromanagerFormat.Position;
import loci.formats.ome.OMEXMLMetadata;
import ome.units.UNITS;
import ome.units.quantity.ElectricPotential;
import ome.units.quantity.Length;
import ome.units.quantity.Temperature;
import ome.units.quantity.Time;
import ome.xml.model.MapPair;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.Timestamp;

/**
 * Container class for translators between OME and Micromanager formats.
 *
 * @author Mark Hiner hinerm at gmail.com
 * 
 * Small edits so that all fields per plane are translated into the OME record. Edits are Tagged with DROP-IN. 
 * 
 * @author Karl Duderstadt
 */
public class MarsMicromanagerTranslator {

	/**
	 * Translator class from {@link OMEMetadata} to
	 * {@link io.scif.formats.MicromanagerFormat.Metadata}.
	 * <p>
	 * NB: Plugin priority is set to high to be selected over the base
	 * {@link io.scif.Metadata} translator.
	 * </p>
	 * 
	 * @author Mark Hiner
	 */
	@Plugin(type = FromOMETranslator.class, priority = Priority.HIGH_PRIORITY)
	public static class MarsMicromanagerOMETranslator extends
		ToOMETranslator<MarsMicromanagerFormat.Metadata>
	{

		// -- Fields --

		@Parameter
		private OMEMetadataService omexmlMetadataService;

		// -- Translator API --

		@Override
		public Class<? extends io.scif.Metadata> source() {
			return MarsMicromanagerFormat.Metadata.class;
		}

		@Override
		public Class<? extends io.scif.Metadata> dest() {
			return OMEMetadata.class;
		}

		@Override
		protected void translateFormatMetadata(
			final MarsMicromanagerFormat.Metadata source, final OMEMetadata dest)
		{
			try {
				populateMetadata(source, dest.getRoot());
			}
			catch (final FormatException e) {
				log().error(
					"Error populating Metadata store with Micromanager metadata", e);
			}
		}

		private void populateMetadata(final Metadata meta,
			final OMEXMLMetadata store) throws FormatException
		{
			final String instrumentID = //
				omexmlMetadataService.createLSID("Instrument", 0);
			store.setInstrumentID(instrumentID, 0);
			final Vector<Position> positions = meta.getPositions();
			
			for (int i = 0; i < positions.size(); i++) {
				final Position p = positions.get(i);
				if (p.time != null) {
					final String date = //
						DateTools.formatDate(p.time, MarsMicromanagerFormat.Parser.DATE_FORMAT);
					if (date != null) {
						store.setImageAcquisitionDate(new Timestamp(date), i);
					}
				}

				if (positions.size() > 1) {
					final Location parent = //
						new Location(getContext(), p.metadataFile).getParentFile();
					store.setImageName(parent.getName(), i);
				}

				store.setImageDescription(p.comment, i);

				// link Instrument and Image
				store.setImageInstrumentRef(instrumentID, i);

				for (int c = 0; c < p.channels.length; c++) {
					store.setChannelName(p.channels[c], i, c);
				}

				if (p.pixelSize != null && p.pixelSize > 0) {
					store.setPixelsPhysicalSizeX(//
						new Length(p.pixelSize, UNITS.MICROMETER), i);
					store.setPixelsPhysicalSizeY(//
						new Length(p.pixelSize, UNITS.MICROMETER), i);
				}
				else {
					log().warn("Expected positive value for PhysicalSizeX; got " +
						p.pixelSize);
				}
				if (p.sliceThickness != null && p.sliceThickness > 0) {
					store.setPixelsPhysicalSizeZ(//
						new Length(p.sliceThickness, UNITS.MICROMETER), i);
				}
				else {
					log().warn("Expected positive value for PhysicalSizeZ; got " +
						p.sliceThickness);
				}

				int nextStamp = 0;
				for (int q = 0; q < meta.get(i).getPlaneCount(); q++) {
					
					store.setPlaneExposureTime(new Time(p.exposureTime, UNITS.SECOND), i,
						q);
					final String tiff = positions.get(i).getFile(meta, i, q);
					if (tiff != null && new Location(getContext(), tiff).exists() &&
						nextStamp < p.timestamps.length)
					{
						store.setPlaneDeltaT(new Time(p.timestamps[nextStamp++],
							UNITS.SECOND), i, q);
						
						store.setPlaneTheC(p.getTheC(meta, i, q), i, q);
						store.setPlaneTheZ(p.getTheZ(meta, i, q), i, q);
						store.setPlaneTheT(p.getTheT(meta, i, q), i, q);
						
						//DROP-IN
						HashMap<String, String> planeMetaTable = (HashMap<String, String>)meta.getTable().get(p.getPlaneMapKey(meta, i, q));
						
						ArrayList<MapPair> planeParameterList = new ArrayList<MapPair>();
						for (String planeParameterKey : planeMetaTable.keySet()) 
							planeParameterList.add(new MapPair(planeParameterKey, planeMetaTable.get(planeParameterKey))); 
						
						store.setMapAnnotationValue(planeParameterList, q);
						store.setMapAnnotationID("MMAllFileKey-" + i + "-" + q, q);
						store.setPlaneAnnotationRef("MMAllFileKey-" + i + "-" + q, i, q, 0);
						//
					}
				}

				final String serialNumber = p.detectorID;
				p.detectorID = omexmlMetadataService.createLSID("Detector", 0, i);

				for (int c = 0; c < p.channels.length; c++) {
					store.setDetectorSettingsBinning(//
						omexmlMetadataService.getBinning(p.binning), i, c);
					store.setDetectorSettingsGain(new Double(p.gain), i, c);
					if (c < p.voltage.size()) {
						store.setDetectorSettingsVoltage(new ElectricPotential(p.voltage
							.get(c), UNITS.VOLT), i, c);
					}
					store.setDetectorSettingsID(p.detectorID, i, c);
				}

				store.setDetectorID(p.detectorID, 0, i);
				if (p.detectorModel != null) {
					store.setDetectorModel(p.detectorModel, 0, i);
				}

				if (serialNumber != null) {
					store.setDetectorSerialNumber(serialNumber, 0, i);
				}

				if (p.detectorManufacturer != null) {
					store.setDetectorManufacturer(p.detectorManufacturer, 0, i);
				}

				if (p.cameraMode == null) p.cameraMode = "Other";
				store.setDetectorType(//
					omexmlMetadataService.getDetectorType(p.cameraMode), 0, i);
				store.setImagingEnvironmentTemperature(//
					new Temperature(p.temperature, UNITS.CELSIUS), i);
			}
		}

	}
}