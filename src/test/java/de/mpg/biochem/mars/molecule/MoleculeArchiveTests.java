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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.scijava.table.DoubleColumn;

import de.mpg.biochem.mars.metadata.MarsOMEChannel;
import de.mpg.biochem.mars.metadata.MarsOMEImage;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEPlane;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableTests;
import de.mpg.biochem.mars.util.MarsMath;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.xml.meta.OMEXMLMetadataRoot;
import ome.xml.model.MapPair;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;

public class MoleculeArchiveTests {
	
	@TempDir
	static File sharedTempDir;
	
	@Test
	@Order(1)
	void generateMoleculeArchive() {
		generateSingleMoleculeArchive();
	}
	
	@Test
	@Order(2)
	void saveAsMoleculeArchive() throws IOException {
		generateSingleMoleculeArchive().saveAs(new File(sharedTempDir.getAbsoluteFile() + "/singleMoleculeTestArchive.yama"));
	}
	
	@Test
	@Order(3)
	void saveAsJsonMoleculeArchive() throws IOException {
		generateSingleMoleculeArchive().saveAsJson(new File(sharedTempDir.getAbsoluteFile() + "/singleMoleculeJsonTestArchive.yama.json"));
	}
	
	@Test
	@Order(4)
	void saveAsVirtualMoleculeArchive() throws IOException {
		generateSingleMoleculeArchive().saveAsVirtualStore(new File(sharedTempDir.getAbsoluteFile() + "/singleMoleculeTestArchive.yama.store/"));
	}
	
	@Test
	@Order(5)
	void saveAsJsonVirtualMoleculeArchive() throws IOException {
		generateSingleMoleculeArchive().saveAsJsonVirtualStore(new File(sharedTempDir.getAbsoluteFile() + "/jsonSingleMoleculeTestArchive.yama.store/"));
	}
	
	
	public static SingleMoleculeArchive generateSingleMoleculeArchive() {
		SingleMoleculeArchive archive = new SingleMoleculeArchive("testMoleculeArchive");
		
		MarsOMEMetadata metadata = generateMetadata(1, 1, 30);
		
		Random ran = new Random();
		for (int i=0 ; i < 100; i++) {
			SingleMolecule molecule = new SingleMolecule(MarsMath.getUUID58());
			molecule.setTable(generateRandomTable(30));
			molecule.setImage(0);
			molecule.setChannel(0);
			molecule.setMetadataUID(metadata.getUID());
			if (ran.nextDouble() < 0.3)
				molecule.addTag("below30");
			if (ran.nextDouble() < 0.1)
				molecule.addTag("below10");
			archive.put(molecule);
		}
		
		archive.putMetadata(metadata);
		
		return archive;
	}
	
	public static MarsOMEMetadata generateMetadata(int cNum, int zNum, int tNum) {
		MarsOMEMetadata metadata = new MarsOMEMetadata(MarsMath.getUUID58().substring(0, 10));
		
		MarsOMEImage image = new MarsOMEImage();
		image.setImageID(0);
		//image.setID(id);
		//image.setPixelID(pixelID);
		//image.setAquisitionDate(imageAquisitionDate);
		image.setName("simulated");
		//image.setDescription(imageDescription);
		
		//create channels
		for (int channelIndex=0; channelIndex < cNum; channelIndex++) {
			MarsOMEChannel channel = new MarsOMEChannel();
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
		
		//Build Planes
		for (int t = 0; t < tNum; t++) {
			MarsOMEPlane plane = new MarsOMEPlane(image, 0, t, new NonNegativeInteger(0), new NonNegativeInteger(0), new NonNegativeInteger(t));
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
