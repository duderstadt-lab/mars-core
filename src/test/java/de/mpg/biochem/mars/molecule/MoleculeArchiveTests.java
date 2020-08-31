package de.mpg.biochem.mars.molecule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.scijava.table.DoubleColumn;

import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableTests;
import de.mpg.biochem.mars.util.MarsMath;

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
		for (int i=0 ; i < 30; i++) {
			SingleMolecule molecule = new SingleMolecule(MarsMath.getUUID58());
			molecule.setTable(generateRandomTable());
			if (ran.nextDouble() < 0.3)
				molecule.addTag("below30");
			if (ran.nextDouble() < 0.1)
				molecule.addTag("below10");
			archive.put(molecule);
		}
		
		archive.putMetadata(generateMetadata());
		
		return archive;
	}
	
	public static MarsOMEMetadata generateMetadata() {
		MarsOMEMetadata metadata = new MarsOMEMetadata(MarsMath.getUUID58().substring(0, 10));
		
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
	
	public static MarsTable generateRandomTable() {
		MarsTable table = new MarsTable();
		DoubleColumn tCol = new DoubleColumn("T");
		DoubleColumn xCol = new DoubleColumn("x");
		DoubleColumn yCol = new DoubleColumn("y");
		DoubleColumn zCol = new DoubleColumn("z");
		
		Random ran = new Random();
		
		for (int t=0; t < MarsTableTests.XYNaNs.length; t++) {
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
