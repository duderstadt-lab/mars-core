package de.mpg.biochem.sdmm.table;

import java.io.File;
import java.io.IOException;

import ij.IJ;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;

public class ResultsTableSaver implements PlugIn {

	@Override
	public void run(String arg0) {
		String path = IJ.getFilePath("Save ResultsTable");
		
		if (path == null)
			return;
		
		String title = new File(path).getName();
		
		//ResultsTable results = ResultsTableUtil.open(path);
		
		//new ResultsTableWindow(results, title);
	}
}
