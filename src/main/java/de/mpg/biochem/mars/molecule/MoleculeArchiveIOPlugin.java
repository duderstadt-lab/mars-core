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
package de.mpg.biochem.mars.molecule;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;

import org.scijava.Priority;
import org.scijava.event.EventService;
import org.scijava.io.AbstractIOPlugin;
import org.scijava.io.IOPlugin;
import org.scijava.io.event.DataOpenedEvent;
import org.scijava.log.LogService;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.script.ScriptService;
import org.scijava.ui.UIService;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import de.mpg.biochem.mars.util.LogBuilder;
import de.mpg.biochem.mars.util.MarsUtil;

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
	
	@Override
	public Class<MoleculeArchive> getDataType() {
		return MoleculeArchive.class;
	}

	@Override
	public boolean supportsOpen(final String source) {
		return source.endsWith(".yama") || source.endsWith(".yama.store");
	}

	@Override
	public boolean supportsSave(final String source) {
		return supportsOpen(source);
	}
	
	//This needs cleaning up but lets see if it is working first...
	@Override
	public MoleculeArchive open(final String source) throws IOException {
		File file = new File(source);
		if (!file.exists())
			System.out.println("File not found.");
		String archiveType;
		
		if (file.isDirectory())
			archiveType = MarsUtil.getArchiveTypeFromStore(new File(file.getAbsolutePath() + "/MoleculeArchiveProperties.json"));
		else 
			archiveType = MarsUtil.getArchiveTypeFromYama(file);
		
		String name = file.getName();
		
		MoleculeArchive archive = MarsUtil.createMoleculeArchive(archiveType, file, moleculeArchiveService);
		
		if (moleculeArchiveService.contains(name)) {
			uiService.showDialog("The MoleculeArchive " + name + " is already open.", MessageType.ERROR_MESSAGE, OptionType.DEFAULT_OPTION);
			return null;
		}
		
		objectService.addObject(archive);
		scriptService.addAlias(archive.getClass());
		
		//Why doesn't this happen somewhere else. How if ij.io().open is used in a script. It will also open the archive window.
		if (!uiService.isHeadless())
			uiService.show(archive.getName(), archive);

		LogBuilder builder = new LogBuilder();
		
		String log = LogBuilder.buildTitleBlock("Opening MoleculeArchive");
		builder.addParameter("Loading File", file.getAbsolutePath());
		builder.addParameter("Archive Name", name);
		
		log += builder.buildParameterList();
		logService.info(log);
		logService.info(LogBuilder.endBlock(true));
		
		return archive;
	}
	
	@Override
	public void save(final MoleculeArchive archive, final String destination) throws IOException {
		File file = new File(destination);
		if (file.isDirectory())
			archive.saveAsVirtualStore(file);
		else
			archive.saveAs(file);
	}
}
