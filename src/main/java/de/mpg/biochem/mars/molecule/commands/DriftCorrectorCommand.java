/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2024 Karl Duderstadt
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

@Plugin(type = Command.class, label = "Drift Corrector", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
			label = "Molecule", weight = 2, mnemonic = 'm'), @Menu(label = "Util",
				weight = 7, mnemonic = 'u'), @Menu(label = "Drift Corrector",
					weight = 9, mnemonic = 'd') })
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
	private int theC = 0;

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String calcDriftMessage = "Drift calculator:";

	@Parameter(label = "Calculate drift")
	private boolean calculateDrift = true;

	@Parameter(label = "Background tag")
	private String backgroundTag = "background";

	@Parameter(label = "Input X")
	private String input_x = "X";

	@Parameter(label = "Input Y")
	private String input_y = "Y";

	@Parameter(label = "Use incomplete traces")
	private boolean use_incomplete_traces = false;

	@Parameter(label = "Mode", choices = { "mean", "median" })
	private String mode = "mean";

	@Parameter(label = "Zero:",
		style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, choices = { "beginning",
			"end" })
	private String zeroPoint = "end";

	@Parameter(visibility = ItemVisibility.MESSAGE)
	private String corrDriftMessage = "Drift corrector:";

	@Parameter(label = "Correct drift")
	private boolean correctDrift = true;

	@Parameter(label = "Output X (X_drift_corr)")
	private String output_x = "X_drift_corr";

	@Parameter(label = "Output Y (Y_drift_corr)")
	private String output_y = "Y_drift_corr";

	@Override
	public void run() {

		final int channel = (singleChannel) ? theC : 0;

		if (calculateDrift) ArchiveUtils.calculateDrift(archive, backgroundTag,
			input_x, input_y, use_incomplete_traces, mode, zeroPoint, singleChannel,
			channel, logService);

		if (correctDrift) ArchiveUtils.correctDrift(archive, input_x, input_y,
			output_x, output_y, singleChannel, channel, logService);

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

	public void setChannel(int theC) {
		this.theC = theC;
	}

	public int getChannel() {
		return this.theC;
	}

	public void setCalculateDrift(boolean calculateDrift) {
		this.calculateDrift = calculateDrift;
	}

	public boolean getCalculateDrift() {
		return this.calculateDrift;
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
}
