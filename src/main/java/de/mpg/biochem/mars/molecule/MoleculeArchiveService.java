/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2025 Karl Duderstadt
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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import de.mpg.biochem.mars.io.MoleculeArchiveSource;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import net.imagej.ImageJService;
import org.scijava.display.DisplayService;
import org.scijava.log.LogService;
import org.scijava.object.ObjectService;
import org.scijava.plugin.AbstractPTService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.script.ScriptService;
import org.scijava.service.Service;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.DialogPrompt.OptionType;
import org.scijava.ui.UIService;

import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@SuppressWarnings({ "rawtypes", "unchecked" })
@Plugin(type = Service.class)
public class MoleculeArchiveService extends
	AbstractPTService<MoleculeArchiveService> implements ImageJService
{

	@Parameter
	private LogService logService;

	@Parameter
	private UIService uiService;

	@Parameter
	private ScriptService scriptService;

	@Parameter
	private DisplayService displayService;

	@Parameter
	private ObjectService objectService;

	@Override
	public void initialize() {
		scriptService.addAlias(Molecule.class);
		scriptService.addAlias(MarsMetadata.class);
		scriptService.addAlias(MoleculeArchive.class);
		scriptService.addAlias(MoleculeArchiveService.class);
	}

	public String getArchiveTypeFromYama(File file) throws
			IOException
	{
		InputStream inputStream = new BufferedInputStream(Files.newInputStream(file.toPath()));

		// Here we automatically detect the format of the JSON file
		// Can be JSON text or Smile encoded binary file...
		JsonFactory jsonF = new JsonFactory();
		SmileFactory smileF = new SmileFactory();
		DataFormatDetector det = new DataFormatDetector(jsonF,
				smileF);
		DataFormatMatcher match = det.findFormat(inputStream);
		JsonParser jParser = match.createParserWithMatch();

		String archiveType = "de.mpg.biochem.mars.molecule.SingleMoleculeArchive";

		jParser.nextToken();
		jParser.nextToken();
		if ("properties".equals(jParser.getCurrentName()) ||
			"MoleculeArchiveProperties".equals(jParser.getCurrentName()))
		{
			jParser.nextToken();
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				String fieldName = jParser.getCurrentName();

				if ("archiveType".equals(fieldName) || "ArchiveType".equals(
					fieldName))
				{
					jParser.nextToken();
					archiveType = jParser.getText();
					break;
				}
			}
		}
		else {
			logService.warn("The file " + file.getName() +
				" doesn't have a MoleculeArchiveProperties field. Is this a proper yama file?");
			return null;
		}

		jParser.close();
		inputStream.close();

		return archiveType;
	}

	public static String getArchiveTypeFromStore(File file)
		throws IOException
	{
		InputStream inputStream = new BufferedInputStream(Files.newInputStream(file.toPath()));

		// Here we automatically detect the format of the JSON file
		// Can be JSON text or Smile encoded binary file...
		JsonFactory jsonF = new JsonFactory();
		SmileFactory smileF = new SmileFactory();
		DataFormatDetector det = new DataFormatDetector(jsonF,
				smileF);
		DataFormatMatcher match = det.findFormat(inputStream);
		JsonParser jParser = match.createParserWithMatch();

		String archiveType = "de.mpg.biochem.mars.molecule.SingleMoleculeArchive";

		while (jParser.nextToken() != JsonToken.END_OBJECT) {
			String fieldName = jParser.getCurrentName();

			if ("archiveType".equals(fieldName) || "ArchiveType".equals(fieldName)) {
				jParser.nextToken();
				archiveType = jParser.getText();
				break;
			}
		}

		jParser.close();
		inputStream.close();

		return archiveType;
	}

	public MoleculeArchive<?, ?, ?, ?> createArchive(String archiveType) {
		try {
			Class<?> clazz = Class.forName(archiveType);
			Constructor<?> constructor = clazz.getConstructor(String.class);
			return (MoleculeArchive<?, ?, ?, ?>) constructor.newInstance("archive");
		}
		catch (ClassNotFoundException e) {
			uiService.showDialog(archiveType +
				" type not found. Is the class in the classpath?",
				MessageType.ERROR_MESSAGE, OptionType.DEFAULT_OPTION);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public MoleculeArchive<?, ?, ?, ?> createArchive(String archiveType,
		File file)
	{
		try {
			Class<?> clazz = Class.forName(archiveType);
			Constructor<?> constructor = clazz.getConstructor(File.class);
			return (MoleculeArchive<?, ?, ?, ?>) constructor.newInstance(file);
		}
		catch (ClassNotFoundException e) {
			uiService.showDialog(archiveType +
				" type not found. Is the class in the classpath?",
				MessageType.ERROR_MESSAGE, OptionType.DEFAULT_OPTION);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public MoleculeArchive<?, ?, ?, ?> createArchive(String archiveType,
													 MoleculeArchiveSource source)
	{
		try {
			Class<?> clazz = Class.forName(archiveType);
			Constructor<?> constructor = clazz.getConstructor(MoleculeArchiveSource.class);
			return (MoleculeArchive<?, ?, ?, ?>) constructor.newInstance(source);
		}
		catch (ClassNotFoundException e) {
			uiService.showDialog(archiveType +
							" type not found. Is the class in the classpath?",
					MessageType.ERROR_MESSAGE, OptionType.DEFAULT_OPTION);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public void addArchive(MoleculeArchive archive) {
		objectService.addObject(archive);
	}

	public void removeArchive(String title) {
		removeArchive(getArchive(title));
	}

	public void removeArchive(MoleculeArchive archive) {
		if (archive != null) objectService.removeObject(archive);

		if (archive != null && displayService.getDisplays(archive).size() > 0)
			objectService.removeObject(displayService.getDisplays(archive).get(0));
	}

	public boolean rename(String oldName, String newName) {
		List<MoleculeArchive<?, ?, ?, ?>> archives = getArchives();

		if (archives.stream().anyMatch(archive -> archive.getName().equals(
			oldName)))
		{
			logService.error("No MoleculeArchive exists with the name " + oldName +
				".");
			return false;
		}

		if (archives.stream().anyMatch(archive -> archive.getName().equals(
			newName)))
		{
			logService.error("A MoleculeArchive is already open with the name " +
				newName + ". Choose another name.");
			return false;
		}
		else {
			Optional<MoleculeArchive<?, ?, ?, ?>> archiveOptional = archives.stream().filter(a -> a
					.getName().equals(oldName)).findFirst();
			if (archiveOptional.isPresent()) {
				archiveOptional.get().setName(newName);
				displayService.getDisplay(oldName).setName(newName);
				return true;
			} else return false;
		}
	}

	public ArrayList<String> getArchiveNames() {
		List<MoleculeArchive<?, ?, ?, ?>> archives = getArchives();

		return (ArrayList<String>) archives.stream().map(MoleculeArchive::getName).collect(Collectors.toList());
	}

	public boolean contains(String name) {
		return getArchives().stream().anyMatch(archive -> archive.getName().equals(
			name));
	}

	public boolean contains(MoleculeArchive archive) {
		return getArchives().stream().anyMatch(a -> a.equals(archive));
	}

	public MoleculeArchive<?, ?, ?, ?> getArchive(String name) {
		List<MoleculeArchive<?, ?, ?, ?>> archives = getArchives();
		for (MoleculeArchive<?, ?, ?, ?> archive : archives)
			if (archive.getName().equals(name)) return archive;

		return null;
	}

	public List<MoleculeArchive<?, ?, ?, ?>> getArchives() {
		return (List) objectService.getObjects(MoleculeArchive.class);
	}

	@Override
	public Class<MoleculeArchiveService> getPluginType() {
		return MoleculeArchiveService.class;
	}
}
