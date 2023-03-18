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

package de.mpg.biochem.mars.table;

import java.io.File;
import java.io.IOException;

import org.scijava.Priority;
import org.scijava.event.EventService;
import org.scijava.io.AbstractIOPlugin;
import org.scijava.io.IOPlugin;
import org.scijava.io.location.Location;
import org.scijava.object.ObjectService;
import org.scijava.options.OptionsService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.util.FileUtils;

@SuppressWarnings("rawtypes")
@Plugin(type = IOPlugin.class, priority = Priority.LOW)
public class MarsTableIOPlugin extends AbstractIOPlugin<MarsTable> {

	@Parameter
	private EventService eventService;

	@Parameter
	private UIService uiService;

	@Parameter
	private ObjectService objectService;

	@Parameter
	private OptionsService optionsService;

	@Override
	public Class<MarsTable> getDataType() {
		return MarsTable.class;
	}

	@Override
	public boolean supportsOpen(final String source) {
		final String ext = FileUtils.getExtension(source).toLowerCase();
		return ext.equals("yamt");
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
	public MarsTable open(final String source) throws IOException {

		MarsTable table = new MarsTable(new File(source));

		objectService.addObject(table);

		final boolean newStyleIO = optionsService.getOptions(
			net.imagej.legacy.ImageJ2Options.class).isSciJavaIO();

		if (newStyleIO) uiService.show(table);

		return table;
	}

	@Override
	public MarsTable open(final Location source) throws IOException {
		return open(source.getURI().getPath());
	}

	@Override
	public void save(final MarsTable table, final String destination)
		throws IOException
	{
		table.saveAsYAMT(destination);
	}

	@Override
	public void save(final MarsTable table, final Location destination)
		throws IOException
	{
		save(table, destination.getURI().getPath());
	}
}
