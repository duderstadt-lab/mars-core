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

package de.mpg.biochem.mars.molecule;

import java.io.File;
import java.io.IOException;

import de.mpg.biochem.mars.io.MoleculeArchiveIOFactory;
import de.mpg.biochem.mars.io.MoleculeArchiveSource;
import de.mpg.biochem.mars.io.MoleculeArchiveVirtualSource;
import org.scijava.Priority;
import org.scijava.event.EventService;
import org.scijava.io.AbstractIOPlugin;
import org.scijava.io.IOPlugin;
import org.scijava.io.location.Location;
import org.scijava.log.LogService;
import org.scijava.object.ObjectService;
import org.scijava.options.OptionsService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.script.ScriptService;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.UIService;

import de.mpg.biochem.mars.util.LogBuilder;

@SuppressWarnings("rawtypes")
@Plugin(type = IOPlugin.class, priority = Priority.LOW)
public class MoleculeArchiveIOPlugin extends AbstractIOPlugin<MoleculeArchive> {

	@Parameter
	private LogService logService;

	@Parameter
	private MoleculeArchiveService moleculeArchiveService;

	@Parameter
	private EventService eventService;

	@Parameter
	private ScriptService scriptService;

	@Parameter
	private ObjectService objectService;

	@Parameter
	private UIService uiService;

	@Parameter
	private OptionsService optionsService;

	@Override
	public Class<MoleculeArchive> getDataType() {
		return MoleculeArchive.class;
	}

	@Override
	public boolean supportsOpen(final String source) {
		return source.endsWith(".yama") || source.endsWith(".yama.json") || source
			.endsWith(".yama.store/");
	}

	@Override
	public boolean supportsOpen(final Location source) {
		return supportsOpen(source.getURI().getPath());
	}

	@Override
	public boolean supportsSave(final String source) {
		return supportsOpen(source);
	}

	@Override
	public boolean supportsSave(final Location destination) {
		return supportsOpen(destination);
	}

	@Override
	public MoleculeArchive open(final String source) throws IOException {
		MoleculeArchive archive;
		String name = "archive";

		//Now we need to determine what kind of source it is virtual or regular...
		//Make sure this method removes the trailing slash?
		boolean virtual = ArchiveUtils.isVirtualArchive(source);
		if (virtual) {
			MoleculeArchiveVirtualSource virtualSource = new MoleculeArchiveIOFactory().openVirtualSource(source);
			String archiveType = virtualSource.getArchiveType();
			name = virtualSource.getName();
			archive = moleculeArchiveService.createArchive(archiveType, virtualSource);
		} else {
			MoleculeArchiveSource maSource = new MoleculeArchiveIOFactory().openSource(source);
			String archiveType = maSource.getArchiveType();
			name = maSource.getName();
			archive = moleculeArchiveService.createArchive(archiveType, maSource);
		}

		if (moleculeArchiveService.contains(name)) {
			uiService.showDialog("The MoleculeArchive " + name + " is already open.",
				MessageType.ERROR_MESSAGE, OptionType.DEFAULT_OPTION);
			return null;
		}

		objectService.addObject(archive);
		scriptService.addAlias(archive.getClass());

		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Opening MoleculeArchive");
		builder.addParameter("Loading source", source);
		builder.addParameter("Archive Name", name);

		log += builder.buildParameterList();
		logService.info(log);
		logService.info(LogBuilder.endBlock(true));

		final boolean newStyleIO = optionsService.getOptions(
			net.imagej.legacy.ImageJ2Options.class).isSciJavaIO();

		if (newStyleIO) uiService.show(archive);

		return archive;
	}

	@Override
	public MoleculeArchive open(final Location source) throws IOException {
		return open(source.getURI().toString());
	}

	@Override
	public void save(final MoleculeArchive archive, final String destination)
		throws IOException
	{
		File file = new File(destination);
		if (file.isDirectory()) archive.saveAsVirtualStore(file);
		else archive.saveAs(file);
	}

	@Override
	public void save(final MoleculeArchive archive, final Location destination)
		throws IOException
	{
		save(archive, destination.getURI().getPath());
	}
}
