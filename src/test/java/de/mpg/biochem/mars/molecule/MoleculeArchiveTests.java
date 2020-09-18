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
package de.mpg.biochem.mars.molecule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.scijava.table.DoubleColumn;

import de.mpg.biochem.mars.metadata.MarsOMEChannel;
import de.mpg.biochem.mars.metadata.MarsOMEImage;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEPlane;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableTests;
import de.mpg.biochem.mars.util.MarsMath;
import ome.xml.meta.OMEXMLMetadataRoot;
import ome.xml.model.MapPair;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;

public class MoleculeArchiveTests {

	private static SingleMoleculeArchive inMemoryArchive, virtualArchive;
	
	@BeforeAll
    public static void init() {
		inMemoryArchive = generateMoleculeArchive();
    }
	
	@Test
	void moleculeArchiveCoreFunctions() {
		//assertEquals(4869.277227, table.max("col1", "col0", 3, 3.5));
	}
	
	public static SingleMoleculeArchive generateMoleculeArchive() {
		SingleMoleculeArchive archive = new SingleMoleculeArchive("testMoleculeArchive");
		
		Random ran = new Random();
		for (int i=0 ; i < 100; i++) {
			SingleMolecule molecule = new SingleMolecule(MarsMath.getUUID58());
			molecule.setTable(generateRandomTable(30));
			if (ran.nextDouble() < 0.3)
				molecule.addTag("below30");
			if (ran.nextDouble() < 0.1)
				molecule.addTag("below10");
			archive.put(molecule);
		}
		
		archive.putMetadata(generateMetadata(1, 1, 30));
		
		return archive;
	}
	
	public static MarsOMEMetadata generateMetadata(int cNum, int zNum, int tNum) {
		MarsOMEMetadata metadata = new MarsOMEMetadata(MarsMath.getUUID58().substring(0, 10));
		
		MarsOMEImage image = new MarsOMEImage();
		image.setImageIndex(0);
		//image.setID(id);
		//image.setPixelID(pixelID);
		//image.setAquisitionDate(imageAquisitionDate);
		//image.setName(imageName);
		//image.setDescription(imageDescription);
		
		//create channels
		for (int channelIndex=0; channelIndex < cNum; channelIndex++) {
			MarsOMEChannel channel = new MarsOMEChannel();
			image.setChannel(channel, channelIndex);
		}
		
		//image.setPixelsPhysicalSizeX(pixelsPhysicalSizeX);
		//image.setPixelsPhysicalSizeX(pixelsPhysicalSizeY);
		//image.setPixelsPhysicalSizeX(pixelsPhysicalSizeZ);
		
		//image.setDimensionOrder(dimensionOrder);
		
		image.setSizeC(new PositiveInteger(cNum));
		image.setSizeT(new PositiveInteger(tNum));
		image.setSizeX(new PositiveInteger(300));
		image.setSizeY(new PositiveInteger(300));
		image.setSizeZ(new PositiveInteger(zNum));
		
		//Build Planes
		//For now we just have one channel and one z point so plane number is tNum
		/*
		for (int t = 0; t < tNum; t++) {
			MarsOMEPlane plane = new MarsOMEPlane();
			plane.setC(new NonNegativeInteger(0));
			plane.setT(new NonNegativeInteger(t));
			plane.setZ(new NonNegativeInteger(0));
			image.setPlane(plane, 0, 0, t);
		}
		
		
		if (md.getInstrumentCount() > 0) {
			detectorSerialNumber = md.getDetectorSerialNumber(0, imageIndex);
			detectorModel = md.getDetectorModel(0, imageIndex);
			detectorManufacturer = md.getDetectorManufacturer(0, imageIndex);
			detectorType = md.getDetectorType(0, imageIndex);
		}
		*/
		
		metadata.setImage(image, 0);
		
		return metadata;
	}
	
	public static MarsTable generateTable() {
		MarsTable table = new MarsTable();
		DoubleColumn tCol = new DoubleColumn("T");
		DoubleColumn xCol = new DoubleColumn("x");
		DoubleColumn yCol = new DoubleColumn("y");
		for (int t=0; t < MarsTableTests.XYNaNs.length; t++) {
			tCol.add((double)t);
			xCol.add(MarsTableTests.XYNaNs[t][0]);
			yCol.add(MarsTableTests.XYNaNs[t][1]);
		}
		
		table.add(tCol);
		table.add(xCol);
		table.add(yCol);
		
		return table;
	}
	
	public static MarsTable generateRandomTable(int tNum) {
		MarsTable table = new MarsTable();
		DoubleColumn tCol = new DoubleColumn("T");
		DoubleColumn xCol = new DoubleColumn("x");
		DoubleColumn yCol = new DoubleColumn("y");
		DoubleColumn zCol = new DoubleColumn("z");
		
		Random ran = new Random();
		
		for (int t=0; t < tNum; t++) {
			tCol.add((double)t);
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
