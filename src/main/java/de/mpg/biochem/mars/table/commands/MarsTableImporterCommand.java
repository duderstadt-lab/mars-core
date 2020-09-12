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
package de.mpg.biochem.mars.table.commands;

import java.io.File;
import java.io.IOException;

import org.scijava.command.Command;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.table.MarsTableIOPlugin;

import org.scijava.Context;
import org.scijava.command.DynamicCommand;
import org.scijava.menu.MenuConstants;
import org.scijava.options.OptionsService;

@Plugin(type = Command.class, label = "Open table", menu = {
		@Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
				mnemonic = MenuConstants.PLUGINS_MNEMONIC),
		@Menu(label = "Mars", weight = MenuConstants.PLUGINS_WEIGHT,
			mnemonic = 's'),
		@Menu(label = "Table", weight = 10,
			mnemonic = 't'),
		@Menu(label = "Open table", weight = 1, mnemonic = 'o')})
public class MarsTableImporterCommand extends DynamicCommand {
	
    @Parameter(label="MarsTable (csv, tab or json) ")
    private File file;
    
    @Parameter
    private UIService uiService;
    
    @Parameter
    private OptionsService optionsService;
    
	@Override
	public void run() {				
		final MarsTableIOPlugin marsTableIOPlugin = new MarsTableIOPlugin();
		marsTableIOPlugin.setContext(getContext());
		
		try {
			MarsTable table = marsTableIOPlugin.open(file.getAbsolutePath());
			final boolean newStyleIO =
					optionsService.getOptions(net.imagej.legacy.ImageJ2Options.class).isSciJavaIO();
			
			if (!newStyleIO)
				uiService.show(table);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
