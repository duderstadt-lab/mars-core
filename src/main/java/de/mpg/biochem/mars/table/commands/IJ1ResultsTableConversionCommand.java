/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2023 Karl Duderstadt
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

package de.mpg.biochem.mars.table.commands;

import java.awt.Frame;
import java.util.ArrayList;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableService;
import ij.WindowManager;
import ij.measure.ResultsTable;
import ij.text.TextPanel;
import ij.text.TextWindow;
import org.scijava.Initializable;

@Plugin(type = Command.class, label = "Import IJ1 ResultsTable", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
			label = "Table", weight = 3, mnemonic = 't'), @Menu(label = "Import",
				weight = 4, mnemonic = 'i'), @Menu(label = "IJ1 Table", weight = 5,
					mnemonic = 'i') })
public class IJ1ResultsTableConversionCommand extends DynamicCommand implements
	Initializable
{

	@Parameter
	private UIService uiService;

	@Parameter
	private MarsTableService marsTableService;

	@Parameter(label = "IJ1 Table", choices = { "a", "b", "c" })
	private String tableName;

	@Parameter(label = "MarsTable", type = ItemIO.OUTPUT)
	private MarsTable table;

	@Override
	public void initialize() {
		final MutableModuleItem<String> tableNames = getInfo().getMutableInput(
			"tableName", String.class);
		tableNames.setChoices(getResultsTableTitles());
	}

	@Override
	public void run() {
		Frame frame = WindowManager.getFrame(tableName);
		ResultsTable resultsTable = null;
		if (frame instanceof TextWindow) {
			TextWindow textWindow = (TextWindow) frame;
			TextPanel textPanel = textWindow.getTextPanel();
			resultsTable = textPanel.getResultsTable();
		}

		if (resultsTable == null) {
			uiService.showDialog("No IJ1 Tables found!");
			return;
		}

		table = marsTableService.createTable(resultsTable);
	}

	public static ArrayList<String> getResultsTableTitles() {

		Frame[] nonImageWindows = WindowManager.getNonImageWindows();
		ArrayList<String> openTables = new ArrayList<String>();

		for (Frame frame : nonImageWindows) {

			if (frame instanceof TextWindow) {

				TextWindow textWindow = (TextWindow) frame;
				TextPanel textPanel = textWindow.getTextPanel();
				ResultsTable table = textPanel.getResultsTable();

				if (table != null) openTables.add(frame.getTitle());
			}
		}

		return openTables;
	}
}
