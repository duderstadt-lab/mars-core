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

package de.mpg.biochem.mars.molecule.commands;

import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveIOPlugin;
import de.mpg.biochem.mars.swingUI.MoleculeArchiveSelector.MoleculeArchiveSelection;
import de.mpg.biochem.mars.swingUI.MoleculeArchiveSelector.MoleculeArchiveSelectorDialog;
import de.mpg.biochem.mars.swingUI.MoleculeArchiveSelector.MoleculeArchiveTreeCellRenderer;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.menu.MenuConstants;
import org.scijava.options.OptionsService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.ui.UIService;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

@Plugin(type = Command.class, label = "Open Virtual Store", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
			label = "Molecule", weight = 2, mnemonic = 'm'), @Menu(
				label = "Open archive (cloud storage)", weight = 2, mnemonic = 'c') })
public class ImportCloudArchiveCommand extends DynamicCommand {

	@Parameter
	private UIService uiService;

	@Parameter
	private OptionsService optionsService;

	@Parameter
	private PrefService prefService;

	@Override
	public void run() {
		MoleculeArchiveSelectorDialog selectionDialog = new MoleculeArchiveSelectorDialog("", getContext());
		selectionDialog.setTreeRenderer(new MoleculeArchiveTreeCellRenderer(true));
		// Prevents NullPointerException
		selectionDialog.setContainerPathUpdateCallback(x -> {});

		final Consumer<MoleculeArchiveSelection> callback = (MoleculeArchiveSelection dataSelection) -> {
			final MoleculeArchiveIOPlugin moleculeArchiveIOPlugin =
					new MoleculeArchiveIOPlugin();
			moleculeArchiveIOPlugin.setContext(getContext());

			try {
				MoleculeArchive<?, ?, ?, ?> archive = moleculeArchiveIOPlugin.open(dataSelection.url);

				final boolean newStyleIO = optionsService.getOptions(
						net.imagej.legacy.ImageJ2Options.class).isSciJavaIO();

				if (!newStyleIO) uiService.show(archive);

			}
			catch (IOException e) {
				e.printStackTrace();
			}
		};

		selectionDialog.run(callback);
	}
}
