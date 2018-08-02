/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package de.mpg.biochem.sdmm.testing;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.scijava.widget.FileWidget;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import de.mpg.biochem.sdmm.molecule.*;
import de.mpg.biochem.sdmm.table.ResultsTableService;
import de.mpg.biochem.sdmm.table.SDMMResultsTable;
import de.mpg.biochem.sdmm.table.SDMMResultsTableDisplay;
import net.imagej.table.*;

@Plugin(type = Command.class, menuPath = "Plugins>Testing")
public class TableTests implements Command {

    @Parameter
    private UIService uiService;
    
    @Parameter
    private LogService logService;
    
    @Parameter
    private MoleculeArchiveService moleculeArchiveService;
    
    @Parameter
    private ResultsTableService resultsTableService;
    
    @Parameter(label="GenericTableTests", type = ItemIO.OUTPUT)
    private DefaultGenericTable table;

    @Override
    public void run() {
    	
    	/*
    	DoubleColumn Col1 = new DoubleColumn("Col1");
 		DoubleColumn Col2 = new DoubleColumn("Col2");
 		IntColumn Col3 = new IntColumn("Col3");
 		GenericColumn Col4 = new GenericColumn("Col4");
 		
 		for (int i=0;i<5;i++) {
 			Col1.add((double)i);
 			Col2.add((double)i + 10);
 			Col3.add(i);
 			Col4.add("a string " + i);
 		}

 		table = new DefaultGenericTable();

 		// and add the columns to that table
 		table.add(Col1);
 		table.add(Col2);
 		table.add(Col3);
 		table.add(Col4);

 		
 		
 		//table.set(1, 1, 10);
 		//table.set(3, 1, "string");
 		//table.set(8, 1, 3452.2342562354);
 		
 		for (int i=0; i< table.getColumnCount();i++) {
 			if (table.get(i) instanceof GenericColumn) {
 				logService.info("col " + i + " is a GenericColumn");
 			
 			} else if (table.get(i) instanceof IntColumn) {
 				logService.info("col " + i + " is a IntColumn");
 			
 			} else if (table.get(i) instanceof DoubleColumn) {
 				logService.info("col " + i + " is a DoubleColumn");
 			
 			} else {
 				logService.info("col " + i + " is an OtherColumn");
 			}
 		}
    	*/
    	/*
    	MoleculeArchive arch = new MoleculeArchive("testArchive.yama", moleculeArchiveService);
    	
    	DoubleColumn Col1 = new DoubleColumn("Col1");
 		DoubleColumn Col2 = new DoubleColumn("Col2");
 		
 		for (int i=0;i<5;i++) {
 			Col1.add((double)i);
 			Col2.add((double)i + 10);
 		}

 		SDMMResultsTable table = new SDMMResultsTable();

 		// and add the columns to that table
 		table.add(Col1);
 		table.add(Col2);
    	
    	Molecule mol1 = new Molecule(moleculeArchiveService.getUUID58(), table);
    	
    	mol1.addTag("Tagged YOU");
    	
    	mol1.setParameter("bg_ff_value", 32342.52342352);
    	
    	mol1.setParameter("bg_ff_value2", 32.552);
    	
    	mol1.setSegmentsTable("SomeX", "SomeY", table);
    	
    	mol1.setSegmentsTable("SomeOtherX", "SomeOtherY", table);
    	
    	mol1.setImageMetaDataUID(moleculeArchiveService.getUUID58());
    	
    	arch.add(mol1);
    	
    	Molecule mol2 = new Molecule(moleculeArchiveService.getUUID58(), table);
    	
    	mol2.addTag("Tagged ME");
    	
    	mol2.setParameter("bg_lue", 9342.52342352);
    	
    	mol2.setParameter("bg_flue2", 932.552);
    	
    	mol2.setSegmentsTable("SX", "SY", table);
    	
    	mol2.setSegmentsTable("X3", "Y3", table);
    	
    	mol2.setImageMetaDataUID(moleculeArchiveService.getUUID58());
    	
    	arch.add(mol2);
    	
        final File file = uiService.chooseFile(null, FileWidget.SAVE_STYLE);

        if (file.getAbsolutePath() != null) {
        	logService.info(file.getAbsolutePath());
        	
        	arch.saveAs(file);
        }
        */
    }
}
