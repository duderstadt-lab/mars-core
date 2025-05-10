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

package de.mpg.biochem.mars.molecule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import de.mpg.biochem.mars.util.MarsDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.scijava.Context;
import org.scijava.io.location.LocationService;
import org.scijava.options.OptionsService;
import org.scijava.table.DoubleColumn;

import de.mpg.biochem.mars.metadata.MarsBdvSource;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEChannel;
import de.mpg.biochem.mars.metadata.MarsOMEImage;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEPlane;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.MarsMath;
import de.mpg.biochem.mars.util.MarsPosition;
import de.mpg.biochem.mars.util.MarsRegion;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;

public class MoleculeArchiveTests {

	@TempDir
	protected static File sharedTempDir;

	protected static Context context;

	protected static MoleculeArchive<?, ?, ?, ?> archive;

	protected static Context createContext() {
		return new Context(MoleculeArchiveService.class, OptionsService.class, LocationService.class);
	}

	@BeforeAll
	public static void setup() {
		context = createContext();
		archive = generateSingleMoleculeArchive();
	}

	@AfterAll
	public static synchronized void cleanUp() {
		if (context != null) {
			context.dispose();
			context = null;
		}
	}

	@Test
	@Order(1)
	void saveAsMoleculeArchive() throws IOException {
		archive.saveAs(new File(sharedTempDir.getAbsoluteFile() +
			"/singleMoleculeTestArchive.yama"));
	}

	@Test
	@Order(2)
	void loadMoleculeArchive() throws IOException {
		MoleculeArchiveIOPlugin ioPlugin = new MoleculeArchiveIOPlugin();
		context.inject(ioPlugin);

		MoleculeArchive<?, ?, ?, ?> reloadedArchive = ioPlugin.open(sharedTempDir
			.getAbsoluteFile() + "/singleMoleculeTestArchive.yama");
		isEqual(archive, reloadedArchive);
	}

	@Test
	@Order(3)
	void saveAsJsonMoleculeArchive() throws IOException {
		archive.saveAsJson(new File(sharedTempDir.getAbsoluteFile() +
			"/singleMoleculeJsonTestArchive.yama.json"));
	}

	@Test
	@Order(5)
	void loadJsonMoleculeArchive() throws IOException {
		MoleculeArchiveIOPlugin ioPlugin = new MoleculeArchiveIOPlugin();
		context.inject(ioPlugin);

		MoleculeArchive<?, ?, ?, ?> reloadedArchive = ioPlugin.open(sharedTempDir
			.getAbsoluteFile() + "/singleMoleculeJsonTestArchive.yama.json");
		isEqual(archive, reloadedArchive);
	}

	@Test
	@Order(6)
	void saveAsVirtualMoleculeArchive() throws IOException {
		archive.saveAsVirtualStore(new File(sharedTempDir.getAbsoluteFile() +
			"/singleMoleculeTestArchive.yama.store/"));
	}

	@Test
	@Order(7)
	void loadVirtualMoleculeArchive() throws IOException {
		MoleculeArchiveIOPlugin ioPlugin = new MoleculeArchiveIOPlugin();
		context.inject(ioPlugin);

		MoleculeArchive<?, ?, ?, ?> reloadedArchive = ioPlugin.open(sharedTempDir
			.getAbsoluteFile() + "/singleMoleculeTestArchive.yama.store/");
		isEqual(archive, reloadedArchive);
	}

	@Test
	@Order(8)
	void saveAsJsonVirtualMoleculeArchive() throws IOException {
		archive.saveAsJsonVirtualStore(new File(sharedTempDir.getAbsoluteFile() +
			"/jsonSingleMoleculeTestArchive.yama.store/"));
	}

	@Test
	@Order(9)
	void loadJsonVirtualMoleculeArchive() throws IOException {
		MoleculeArchiveIOPlugin ioPlugin = new MoleculeArchiveIOPlugin();
		context.inject(ioPlugin);

		MoleculeArchive<?, ?, ?, ?> reloadedArchive = ioPlugin.open(sharedTempDir
			.getAbsoluteFile() + "/jsonSingleMoleculeTestArchive.yama.store/");
		isEqual(archive, reloadedArchive);
	}

	void isEqual(MoleculeArchive<?, ?, ?, ?> archive1,
		MoleculeArchive<?, ?, ?, ?> archive2)
	{

		assertEquals(archive1.getClass(), archive2.getClass());

		assertEquals(archive1.getNumberOfMetadatas(), archive2
			.getNumberOfMetadatas());
		assertEquals(archive1.getNumberOfMolecules(), archive2
			.getNumberOfMolecules());
		isEqual(archive1.properties(), archive2.properties());

		for (int meta = 0; meta < archive1.getNumberOfMetadatas(); meta++)
			isEqual(archive1.getMetadata(archive1.getMetadataUIDs().get(meta)), archive2.getMetadata(archive2.getMetadataUIDs().get(meta)));

		for (int mol = 0; mol < archive1.getNumberOfMolecules(); mol++)
			isEqual(archive1.get(archive1.getMoleculeUIDs().get(0)), archive2.get(archive2.getMoleculeUIDs().get(0)));

		for (String name : archive1.properties().getDocumentNames())
			isEqual(archive1.properties().getDocument(name), archive2.properties().getDocument(name));
	}

	void isEqual(Molecule molecule1, Molecule molecule2) {
		assertEquals(molecule1.getUID(), molecule2.getUID());
		assertEquals(molecule1.getMetadataUID(), molecule2.getMetadataUID());
		assertEquals(molecule1.getImage(), molecule2.getImage());
		assertEquals(molecule1.getChannel(), molecule2.getChannel());
		assertEquals(molecule1.getTags(), molecule2.getTags());
		assertEquals(molecule1.getNotes(), molecule2.getNotes());
		assertEquals(molecule1.getParameters(), molecule2.getParameters());

		for (String name : molecule1.getRegionNames())
			isEqual(molecule1.getRegion(name), molecule2.getRegion(name));

		for (String name : molecule1.getPositionNames())
			isEqual(molecule1.getPosition(name), molecule2.getPosition(name));

		isEqual(molecule1.getTable(), molecule2.getTable());
	}

	void isEqual(MarsMetadata metadata1, MarsMetadata metadata2) {
		assertEquals(metadata1.getUID(), metadata2.getUID());
		assertEquals(metadata1.getTags(), metadata2.getTags());
		assertEquals(metadata1.getNotes(), metadata2.getNotes());
		assertEquals(metadata1.getParameters(), metadata2.getParameters());

		for (String name : metadata1.getRegionNames())
			isEqual(metadata1.getRegion(name), metadata2.getRegion(name));

		for (String name : metadata1.getPositionNames())
			isEqual(metadata1.getPosition(name), metadata2.getPosition(name));

		assertEquals(metadata1.getImageCount(), metadata2.getImageCount());
		assertEquals(metadata1.getLog(), metadata2.getLog());
		if (metadata1.getCollectionDate() != null) assertEquals(metadata1
			.getCollectionDate(), metadata2.getCollectionDate());
		if (metadata1.getMicroscopeName() != null) assertEquals(metadata1
			.getMicroscopeName(), metadata2.getMicroscopeName());

		for (String name : metadata1.getBdvSourceNames())
			isEqual(metadata1.getBdvSource(name), metadata2.getBdvSource(name));

		for (int imageIndex = 0; imageIndex < metadata1
			.getImageCount(); imageIndex++)
			isEqual(metadata1.getImage(imageIndex), metadata2.getImage(imageIndex));
	}

	void isEqual(MarsDocument doc1, MarsDocument doc2) {
		// Compare basic fields
		assertEquals(doc1.getName(), doc2.getName(), "Document names should match");
		assertEquals(doc1.getContent(), doc2.getContent(), "Document content should match");

		// Compare media map
		assertEquals(doc1.getMediaIDs().size(), doc2.getMediaIDs().size(), "Number of media items should match");
		for (String mediaId : doc1.getMediaIDs()) {
			assertTrue(doc2.getMediaIDs().contains(mediaId), "Media ID '" + mediaId + "' should exist in both documents");
			assertEquals(doc1.getMedia(mediaId), doc2.getMedia(mediaId), "Media content for ID '" + mediaId + "' should match");
		}

		// Compare mediaArray map
		assertEquals(doc1.getMediaArrayIDs().size(), doc2.getMediaArrayIDs().size(), "Number of media arrays should match");
		for (String arrayId : doc1.getMediaArrayIDs()) {
			assertTrue(doc2.getMediaArrayIDs().contains(arrayId), "Media array ID '" + arrayId + "' should exist in both documents");

			String[] array1 = doc1.getMediaArray(arrayId);
			String[] array2 = doc2.getMediaArray(arrayId);

			assertNotNull(array1, "Media array '" + arrayId + "' should not be null in first document");
			assertNotNull(array2, "Media array '" + arrayId + "' should not be null in second document");
			assertEquals(array1.length, array2.length, "Length of media array '" + arrayId + "' should match");

			// Compare each element in the arrays
			for (int i = 0; i < array1.length; i++) {
				assertEquals(array1[i], array2[i], "Element " + i + " in media array '" + arrayId + "' should match");
			}
		}
	}

	void isEqual(MarsOMEImage image1, MarsOMEImage image2) {
		assertEquals(image1.getImageID(), image2.getImageID());
		if (image1.getDescription() != null) assertEquals(image1.getDescription(),
			image2.getDescription());
		if (image1.getDetectorManufacturer() != null) assertEquals(image1
			.getDetectorManufacturer(), image2.getDetectorManufacturer());
		if (image1.getDetectorModel() != null) assertEquals(image1
			.getDetectorModel(), image2.getDetectorModel());
		if (image1.getDetectorSerialNumber() != null) assertEquals(image1
			.getDetectorSerialNumber(), image2.getDetectorSerialNumber());
		if (image1.getDetectorType() != null) assertEquals(image1.getDetectorType()
			.getValue(), image2.getDetectorType().getValue());
		assertEquals(image1.getDimensionOrder().getValue(), image2
			.getDimensionOrder().getValue());
		if (image1.getID() != null) assertEquals(image1.getID(), image2.getID());
		assertEquals(image1.getName(), image2.getName());
		if (image1.getPixelID() != null) assertEquals(image1.getPixelID(), image2
			.getPixelID());
		assertEquals(image1.getPixelsPhysicalSizeX().unit().getSymbol(), image2
			.getPixelsPhysicalSizeX().unit().getSymbol());
		assertEquals(image1.getPixelsPhysicalSizeX().value().doubleValue(), image2
			.getPixelsPhysicalSizeX().value().doubleValue());
		assertEquals(image1.getPixelsPhysicalSizeY().unit().getSymbol(), image2
			.getPixelsPhysicalSizeY().unit().getSymbol());
		assertEquals(image1.getPixelsPhysicalSizeY().value().doubleValue(), image2
			.getPixelsPhysicalSizeY().value().doubleValue());
		assertEquals(image1.getPixelsPhysicalSizeZ().unit().getSymbol(), image2
			.getPixelsPhysicalSizeZ().unit().getSymbol());
		assertEquals(image1.getPixelsPhysicalSizeZ().value().doubleValue(), image2
			.getPixelsPhysicalSizeZ().value().doubleValue());
		assertEquals(image1.getPlaneCount(), image2.getPlaneCount());
		assertEquals(image1.getSizeC(), image2.getSizeC());
		assertEquals(image1.getSizeT(), image2.getSizeT());
		assertEquals(image1.getSizeX(), image2.getSizeX());
		assertEquals(image1.getSizeY(), image2.getSizeY());
		assertEquals(image1.getSizeZ(), image2.getSizeZ());
		if (image1.getTemperature() != null) {
			assertEquals(image1.getTemperature().unit().getSymbol(), image2
				.getTemperature().unit().getSymbol());
			assertEquals(image1.getTemperature().value().doubleValue(), image2
				.getTemperature().value().doubleValue());
		}
		assertEquals(image1.getTimeIncrementInSeconds(), image2
			.getTimeIncrementInSeconds());

		for (int channelIndex = 0; channelIndex < image1.getChannels()
			.size(); channelIndex++)
			isEqual(image1.getChannel(channelIndex), image2.getChannel(channelIndex));

		for (int planeIndex = 0; planeIndex < image1.getPlaneCount(); planeIndex++)
			isEqual(image1.getPlane(planeIndex), image2.getPlane(planeIndex));
	}

	void isEqual(MarsOMEChannel channel1, MarsOMEChannel channel2) {
		assertEquals(channel1.getName(), channel2.getName());
		assertEquals(channel1.getChannelIndex(), channel2.getChannelIndex());
		assertEquals(channel1.getBinning(), channel2.getBinning());
		assertEquals(channel1.getVoltage(), channel2.getVoltage());
		assertEquals(channel1.getGain(), channel2.getGain());
		assertEquals(channel1.getID(), channel2.getID());
		assertEquals(channel1.getDetectorSettingID(), channel2
			.getDetectorSettingID());
	}

	void isEqual(MarsOMEPlane plane1, MarsOMEPlane plane2) {
		assertEquals(plane1.getC(), plane2.getC());
		assertEquals(plane1.getT(), plane2.getT());
		assertEquals(plane1.getImageID(), plane2.getImageID());
		assertEquals(plane1.getZ(), plane2.getZ());
		assertEquals(plane1.getFilename(), plane2.getFilename());
		assertEquals(plane1.getDeltaTinSeconds(), plane2.getDeltaTinSeconds());
		assertEquals(plane1.getExposureTimeInSeconds(), plane2
			.getExposureTimeInSeconds());
		assertEquals(plane1.getFields(), plane2.getFields());
		// assertEquals(plane1.getIFD(), plane2.getIFD());
		assertEquals(plane1.getXDrift(), plane2.getXDrift());
		assertEquals(plane1.getYDrift(), plane2.getYDrift());
		assertEquals(plane1.getZDrift(), plane2.getZDrift());
		assertEquals(plane1.getPlaneIndex(), plane2.getPlaneIndex());
		assertEquals(plane1.getPosX(), plane2.getPosX());
		assertEquals(plane1.getPosY(), plane2.getPosY());
		assertEquals(plane1.getPosZ(), plane2.getPosZ());
		assertEquals(plane1.getStringFields(), plane2.getStringFields());
		assertEquals(plane1.getUUID(), plane2.getUUID());
	}

	void isEqual(MoleculeArchiveProperties<?, ?> properties1,
		MoleculeArchiveProperties<?, ?> properties2)
	{
		assertEquals(properties1.getNumberOfMolecules(), properties2
			.getNumberOfMolecules());
		assertEquals(properties1.getNumberOfMetadatas(), properties2
			.getNumberOfMetadatas());
		assertEquals(properties1.getComments(), properties2.getComments());
		assertEquals(properties1.getColumnSet(), properties2.getColumnSet());
		assertEquals(properties1.getTagSet(), properties2.getTagSet());
		assertEquals(properties1.getParameterSet(), properties2.getParameterSet());
		assertEquals(properties1.getPositionSet(), properties2.getPositionSet());
		assertEquals(properties1.getRegionSet(), properties2.getRegionSet());
		assertEquals(properties1.getSegmentsTableNames(), properties2
			.getSegmentsTableNames());
	}

	void isEqual(MarsBdvSource source1, MarsBdvSource source2) {

		assertEquals(source1.getName(), source2.getName());
		assertEquals(source1.getPath(), source2.getPath());
		assertEquals(source1.getCorrectDrift(), source2.getCorrectDrift());
		double[][] data1 = null;
		double[][] data2 = null;
		source1.getAffineTransform3D().toMatrix(data1);
		source2.getAffineTransform3D().toMatrix(data2);
		assertEquals(data1, data2);
	}

	void isEqual(MoleculeArchiveIndex<?, ?> index1,
		MoleculeArchiveIndex<?, ?> index2)
	{
		assertEquals(index1.getMetadataUIDSet(), index2.getMetadataUIDSet());
		assertEquals(index1.getMoleculeUIDSet(), index2.getMoleculeUIDSet());
		assertEquals(index1.getMetadataUIDtoTagListMap(), index2
			.getMetadataUIDtoTagListMap());
		assertEquals(index1.getMoleculeUIDtoTagListMap(), index2
			.getMoleculeUIDtoTagListMap());
		assertEquals(index1.getMoleculeUIDtoMetadataUIDMap(), index2
			.getMoleculeUIDtoMetadataUIDMap());
		assertEquals(index1.getMoleculeUIDtoImageMap(), index2
			.getMoleculeUIDtoImageMap());
		assertEquals(index1.getMoleculeUIDtoChannelMap(), index2
			.getMoleculeUIDtoChannelMap());
	}

	void isEqual(MarsTable table1, MarsTable table2) {
		// Assumes all columns contain only doubles

		assertEquals(table1.size(), table2.size());
		assertEquals(table1.getColumnCount(), table2.getColumnCount());
		assertEquals(table1.getRowCount(), table2.getRowCount());

		// check column headers and data
		for (int col = 0; col < table1.getColumnCount(); col++) {
			assertEquals(table1.getColumnHeader(col), table2.getColumnHeader(col));
			for (int row = 0; row < table1.getColumnCount(); row++)
				assertEquals(table1.getValue(col, row), table2.getValue(col, row));
		}
	}

	void isEqual(MarsRegion region1, MarsRegion region2) {
		assertEquals(region1.getColor(), region2.getColor());
		assertEquals(region1.getColumn(), region2.getColumn());
		assertEquals(region1.getStart(), region2.getStart());
		assertEquals(region1.getEnd(), region2.getEnd());
		assertEquals(region1.getName(), region2.getName());
		assertEquals(region1.getOpacity(), region2.getOpacity());
	}

	void isEqual(MarsPosition position1, MarsPosition position2) {
		assertEquals(position1.getName(), position2.getName());
		assertEquals(position1.getColor(), position2.getColor());
		assertEquals(position1.getColumn(), position2.getColumn());
		assertEquals(position1.getStroke(), position2.getStroke());
		assertEquals(position1.getPosition(), position2.getPosition());
	}

	public static SingleMoleculeArchive generateSingleMoleculeArchive() {
		SingleMoleculeArchive archive = new SingleMoleculeArchive(
			"testMoleculeArchive");

		MarsOMEMetadata metadata = generateMetadata(1, 1, 30);

		Random ran = new Random();
		for (int i = 0; i < 100; i++) {
			SingleMolecule molecule = new SingleMolecule(MarsMath.getUUID58());
			molecule.setTable(generateRandomTable(30));
			molecule.setImage(0);
			molecule.setChannel(0);
			molecule.setMetadataUID(metadata.getUID());
			if (ran.nextDouble() < 0.3) {
				molecule.addTag("below30");
				molecule.setParameter("below30", ran.nextDouble());
				molecule.setParameter("below30", true);
				molecule.setParameter("below30", "below30");
			}
			if (ran.nextDouble() < 0.1) {
				molecule.addTag("below10");
				molecule.putRegion(new MarsRegion("Gyrase Reaction", "T", 4600, 6990,
					"#42A5F5", 0.2));
				molecule.putPosition(new MarsPosition("Position 1", "T", 300.342,
					"#42A5F5", 2.0));
			}
			archive.put(molecule);
		}

		archive.putMetadata(metadata);

		// Create a new MarsDocument with sample data
		MarsDocument document = new MarsDocument("TestMediaArrayDoc", "Test content for media array document");

		// Add regular media items
		document.putMedia("thumbnail", "base64encodedimage...");
		document.putMedia("note", "Some text note content");

		// Add string arrays to mediaArray
		String[] imageFiles = new String[]{"image1.tif", "image2.tif", "image3.tif"};
		document.putMediaArray("imageFiles", imageFiles);

		String[] tags = new String[]{"microscopy", "timelapse", "fluorescence", "tracking"};
		document.putMediaArray("tags", tags);

		String[] coordinates = new String[]{"10.5,20.3", "15.7,22.1", "18.2,25.9"};
		document.putMediaArray("coordinates", coordinates);

		archive.properties().putDocument(document);

		return archive;
	}

	public static MarsOMEMetadata generateMetadata(int cNum, int zNum, int tNum) {
		MarsOMEMetadata metadata = new MarsOMEMetadata(MarsMath.getUUID58()
			.substring(0, 10));

		MarsOMEImage image = new MarsOMEImage();
		image.setImageID(0);
		image.setName("simulated");

		// create channels
		for (int channelIndex = 0; channelIndex < cNum; channelIndex++) {
			MarsOMEChannel channel = new MarsOMEChannel();
			channel.setName("blue");
			channel.setID("id");
			channel.setDetectorSettingsID("DetectorSettingID");
			channel.setChannelIndex(channelIndex);
			image.setChannel(channel, channelIndex);
		}

		image.setPixelsPhysicalSizeX(new Length(1.0d, UNITS.PIXEL));
		image.setPixelsPhysicalSizeY(new Length(1.0d, UNITS.PIXEL));
		image.setPixelsPhysicalSizeZ(new Length(1.0d, UNITS.PIXEL));

		image.setDimensionOrder(DimensionOrder.valueOf("XYZCT"));

		image.setSizeC(new PositiveInteger(cNum));
		image.setSizeT(new PositiveInteger(tNum));
		image.setSizeX(new PositiveInteger(300));
		image.setSizeY(new PositiveInteger(300));
		image.setSizeZ(new PositiveInteger(zNum));

		// Build Planes
		for (int t = 0; t < tNum; t++) {
			MarsOMEPlane plane = new MarsOMEPlane(image, 0, t, new NonNegativeInteger(
				0), new NonNegativeInteger(0), new NonNegativeInteger(t));
			plane.setC(new NonNegativeInteger(0));
			plane.setT(new NonNegativeInteger(t));
			plane.setZ(new NonNegativeInteger(0));
			image.setPlane(plane, 0, 0, t);
		}

		metadata.setImage(image, 0);

		return metadata;
	}

	public static MarsTable generateRandomTable(int tNum) {
		MarsTable table = new MarsTable();
		DoubleColumn tCol = new DoubleColumn("T");
		DoubleColumn xCol = new DoubleColumn("x");
		DoubleColumn yCol = new DoubleColumn("y");
		DoubleColumn zCol = new DoubleColumn("z");

		Random ran = new Random();

		for (int t = 0; t < tNum; t++) {
			tCol.add((double) t);
			xCol.add(t + ran.nextGaussian() - 0.5);
			yCol.add(t + ran.nextGaussian() - 0.5);
			zCol.add(t + ran.nextGaussian() - 0.5);
		}

		table.add(tCol);
		table.add(xCol);
		table.add(yCol);
		return table;
	}
}
