/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2022 Karl Duderstadt
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

import java.util.stream.Collectors;

import net.imagej.ops.Initializable;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.menu.MenuConstants;
import org.scijava.module.MutableModuleItem;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.table.Table;
import org.scijava.table.TableDisplay;

import de.mpg.biochem.mars.table.MarsTable;

@Plugin(type = Command.class, label = "Import TableDisplay", menu = { @Menu(
		label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
		mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
			weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
				label = "Table", weight = 3, mnemonic = 't'), @Menu(
						label = "Import", weight = 7, mnemonic = 'i'), @Menu(
				label = "TableDisplay", weight = 6, mnemonic = 'i') })
public class ScijavaTableConversionCommand extends DynamicCommand implements
	Initializable
{

	@Parameter
	private ObjectService objectService;

	// For some reason this doesn't provide options when multiple tables are open
	// so a workaround is implemented below.
	// @Parameter(label="SciJava Table")
	// private TableDisplay display;

	@Parameter(label = "SciJava table", choices = { "a", "b", "c" })
	private String tableName;

	@Parameter(label = "MarsTable", type = ItemIO.OUTPUT)
	private MarsTable table;

	@Override
	public void initialize() {
		final MutableModuleItem<String> tableNames = getInfo().getMutableInput(
			"tableName", String.class);
		tableNames.setChoices(objectService.getObjects(
			org.scijava.table.TableDisplay.class).stream().map(display -> display
				.getName()).collect(Collectors.toList()));
	}

	@Override
	public void run() {
		TableDisplay display = objectService.getObjects(
			org.scijava.table.TableDisplay.class).stream().filter(obj -> obj.getName()
				.equals(tableName)).findFirst().get();
		table = new MarsTable((Table) display.get(0));
		table.setName(tableName);
		display.close();
	}
}
