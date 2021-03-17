/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2021 Karl Duderstadt
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

import java.util.HashMap;

import org.decimal4j.util.DoubleRounder;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.ChoiceWidget;

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.ArchiveUtils;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveIndex;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.table.*;
import de.mpg.biochem.mars.util.LogBuilder;

@Plugin(type = Command.class, label = "Drift Corrector", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 's'), @Menu(
			label = "Molecule", weight = 1, mnemonic = 'm'), @Menu(
				label = "Drift Corrector", weight = 60, mnemonic = 'd') })
public class DriftCorrectorCommand extends DynamicCommand implements Command {

	@Parameter
	private LogService logService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private MoleculeArchiveService moleculeArchiveService;

	@Parameter
	private UIService uiService;

	@Parameter(label = "MoleculeArchive")
	private MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive;
	
	@Parameter(label = "Single channel")
	private boolean singleChannel = false;
	
	@Parameter(label = "Channel")
	private int channel = 0;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String calcDriftMessage = "Drift calculator:";
	
	@Parameter(label = "Calculate drift")
	private boolean calculateDrift = true;
	
	@Parameter(label = "Background Tag")
	private String backgroundTag = "background";

	@Parameter(label = "Input X (x)")
	private String input_x = "x";

	@Parameter(label = "Input Y (y)")
	private String input_y = "y";

	@Parameter(label = "Use incomplete traces")
	private boolean use_incomplete_traces = false;
	
	@Parameter(label = "mode", choices = { "mean", "median" })
	private String mode = "mean";

	@Parameter(label = "Zero:",
		style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "beginning",
			"end" })
	private String zeroPoint = "end";
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String corrDriftMessage = "Drift corrector:";
	
	@Parameter(label = "Correct drift")
	private boolean correctDrift = true;
	
	@Parameter(label = "Output X (x_drift_corr)")
	private String output_x = "x_drift_corr";

	@Parameter(label = "Output Y (y_drift_corr)")
	private String output_y = "y_drift_corr";
	
	@Parameter(label = "set output zero to mean of region")
	private boolean zeroToRegion = false;
	
	@Parameter(visibility = ItemVisibility.MESSAGE)
	private final String header = "Region:";

	@Parameter(label = "start T")
	private int start = 0;

	@Parameter(label = "end T")
	private int end = 100;

	@Override
	public void run() {
		
		final int theC = (singleChannel) ? channel : -1;
		
		if (calculateDrift) ArchiveUtils.calculateDrift(archive,
			backgroundTag, input_x, input_y, output_x,
			output_y, use_incomplete_traces, mode,
			zeroPoint, theC, logService);
		
		if (correctDrift) ArchiveUtils.correctDrift(archive, input_x, input_y, 
			output_x, output_y, start, end, zeroToRegion, theC, logService);
		
	}

	public void setArchive(
		MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>> archive)
	{
		this.archive = archive;
	}

	public
		MoleculeArchive<Molecule, MarsMetadata, MoleculeArchiveProperties<Molecule, MarsMetadata>, MoleculeArchiveIndex<Molecule, MarsMetadata>>
		getArchive()
	{
		return archive;
	}
	
	public void setSingleChannel(boolean singleChannel) {
		this.singleChannel = singleChannel;
	}
	
	public boolean getSingleChannel() {
		return this.singleChannel;
	}
	
	public void setChannel(int channel) {
		this.channel = channel;
	}
	
	public int getChannel() {
		return this.channel;
	}
	
	public void setCalculateDrift(boolean calculateDrift) {
		this.calculateDrift = calculateDrift;
	}
	
	public boolean getCalculateDrift() {
		return this.calculateDrift;
	}

	public void setStartT(int start) {
		this.start = start;
	}

	public int getStartT() {
		return start;
	}

	public void setEndT(int end) {
		this.end = end;
	}

	public int getEndT() {
		return end;
	}

	public void setInputX(String input_x) {
		this.input_x = input_x;
	}

	public String getInputX() {
		return input_x;
	}

	public void setInputY(String input_y) {
		this.input_y = input_y;
	}

	public String getInputY() {
		return input_y;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}
	
	public String getMode() {
		return mode;
	}

	public void setZeroPoint(String zeroPoint) {
		this.zeroPoint = zeroPoint;
	}
	
	public String getZeroPoint() {
		return zeroPoint;
	}
	
	public void setUseIncompleteTraces(boolean use_incomplete_traces) {
		this.use_incomplete_traces = use_incomplete_traces;
	}
	
	public boolean getUseIncompleteTraces() {
		return use_incomplete_traces;
	}

	public String getBackgroundTag() {
		return backgroundTag;
	}

	public void setBackgroundTag(String backgroundTag) {
		this.backgroundTag = backgroundTag;
	}
	
	public void setCorrectDrift(boolean correctDrift) {
		this.correctDrift = correctDrift;
	}
	
	public boolean getCorrectDrift() {
		return this.correctDrift;
	}

	public void setOutputX(String output_x) {
		this.output_x = output_x;
	}

	public String getOutputX() {
		return output_x;
	}

	public void setOutputY(String output_y) {
		this.output_y = output_y;
	}

	public String getOutputY() {
		return output_y;
	}

	public void setZeroToRegion(boolean zeroToRegion) {
		this.zeroToRegion = zeroToRegion;
	}
}
