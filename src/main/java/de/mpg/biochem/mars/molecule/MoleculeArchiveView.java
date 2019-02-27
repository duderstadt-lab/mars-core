/*******************************************************************************
 * Copyright (C) 2019, Karl Duderstadt
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package de.mpg.biochem.mars.molecule;

import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

import org.scijava.display.Display;
import org.scijava.display.DisplayService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.ui.UserInterface;
import org.scijava.ui.viewer.AbstractDisplayViewer;
import org.scijava.ui.viewer.DisplayViewer;

import net.imagej.display.WindowService;

@Plugin(type = DisplayViewer.class)
public class MoleculeArchiveView extends AbstractDisplayViewer<MoleculeArchive> implements DisplayViewer<MoleculeArchive> {
	
	@Parameter
    private MoleculeArchiveService moleculeArchiveService;
	
	//This method is called to create and display a window
	//here we override it to make sure that calls like uiService.show( .. for MoleculeArchive 
	//will use this method automatically..
	@Override
	public void view(final UserInterface ui, final Display<?> d) {	
		MoleculeArchive archive = (MoleculeArchive)d.get(0);
		archive.setName(d.getName());

		moleculeArchiveService.addArchive(archive);
		d.setName(archive.getName());
		
		new MoleculeArchiveWindow(archive, moleculeArchiveService);
	}

	@Override
	public boolean canView(final Display<?> d) {
		if (d instanceof MoleculeArchiveDisplay) {
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public MoleculeArchiveDisplay getDisplay() {
		return (MoleculeArchiveDisplay) super.getDisplay();
	}

	@Override
	public boolean isCompatible(UserInterface arg0) {
		//Needs to be updated if all contexts are to be enabled beyond ImageJ
		return true;
	}
}
