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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.format.DataFormatDetector;
import com.fasterxml.jackson.core.format.DataFormatMatcher;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.metadata.MarsOMEMetadata;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.util.LogBuilder;
import org.scijava.ItemVisibility;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt.MessageType;
import org.scijava.ui.UIService;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

@Plugin(type = Command.class, label = "Merge Archives", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 'm'), @Menu(
			label = "Molecule", weight = 2, mnemonic = 'm'), @Menu(
				label = "Merge Archives", weight = 5, mnemonic = 'm') })
public class MergeCommand extends DynamicCommand {

	@Parameter
	private LogService logService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private MoleculeArchiveService moleculeArchiveService;

	@Parameter
	private UIService uiService;

	@Parameter(visibility = ItemVisibility.MESSAGE, style = "image")
	private final String inputFigure = "MergeArchives.png";

	@Parameter(label = "Directory", style = "directory")
	private File directory;

	@Override
	public void run() {
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Merge Archives");
		builder.addParameter("Directory", directory.getAbsolutePath());
		log += builder.buildParameterList();
		logService.info(log);

		// create new filename filter
		FilenameFilter fileNameFilter = (dir, name) -> {
			if (name.startsWith(".")) return false;

			return name.endsWith(".yama");
		};

		File[] archiveFileList = directory.listFiles(fileNameFilter);
		if (archiveFileList != null && archiveFileList.length > 0) {
			// retrieve the types of all archives.
			ArrayList<String> archiveTypes = new ArrayList<>();
			for (File file : archiveFileList) {
				try {
					archiveTypes.add(moleculeArchiveService.getArchiveTypeFromYama(file));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			// Check that all have the same type
			String archiveType = archiveTypes.get(0);
			for (String type : archiveTypes) {
				if (!archiveType.equals(type)) {
					logService.info(
							"Not all archives are of the same type. Aborting merge.");
					for (int i = 0; i < archiveTypes.size(); i++)
						logService.info(archiveFileList[i].getName() + " is type " +
								archiveTypes.get(i));
					return;
				}
			}

			// No conflicts found, so we start building and writing the merged file
			MoleculeArchive<?, ?, ?, ?> mergedArchiveType = moleculeArchiveService
					.createArchive(archiveType);
			MoleculeArchiveProperties<?, ?> mergedProperties = mergedArchiveType
					.createProperties();

			mergedProperties.setParent(mergedArchiveType);

			// Initialize all file streams and parsers
			ArrayList<InputStream> fileInputStreams = new ArrayList<>();
			ArrayList<JsonParser> jParsers = new ArrayList<>();

			try {
				for (File file : archiveFileList) {
					InputStream inputStream = new BufferedInputStream(Files.newInputStream(file.toPath()));

					JsonFactory jsonF = new JsonFactory();
					SmileFactory smileF = new SmileFactory();
					DataFormatDetector det = new DataFormatDetector(jsonF, smileF);
					DataFormatMatcher match = det.findFormat(inputStream);
					JsonParser jParser = match.createParserWithMatch();

					jParser.nextToken();
					jParser.nextToken();
					if ("properties".equals(jParser.getCurrentName()) ||
							"MoleculeArchiveProperties".equals(jParser.getCurrentName())) {
						jParser.nextToken();
						mergedProperties.merge(mergedArchiveType.createProperties(jParser),
								file.getName());
					} else {
						logService.error(
								"Can't find MoleculeArchiveProperties field in file " + file
										.getName() + ". Aborting.");
						return;
					}
					fileInputStreams.add(inputStream);
					jParsers.add(jParser);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			StringBuilder archiveList = new StringBuilder();
			for (File file : archiveFileList) archiveList.append(file.getName()).append(", ");
			archiveList = new StringBuilder(archiveList.substring(0,
					archiveList.length() - 2));

			log += "Merged " + archiveFileList.length +
					" yama files into the output archive merged.yama\n";
			log += "Including: " + archiveList + "\n";
			log += "In total " + mergedProperties.getNumberOfMetadatas() +
					" datasets were merged.\n";
			log += "In total " + mergedProperties.getNumberOfMolecules() +
					" molecules were merged.\n";
			log += LogBuilder.endBlock(true);

			// read in all MarsMetadata items from all archives - I hope they fit in
			// memory :)
			ArrayList<MarsMetadata> allMetadataItems = new ArrayList<>();
			ArrayList<String> metaUIDs = new ArrayList<>();

			for (JsonParser jParser : jParsers) {
				try {
					while (jParser.nextToken() != JsonToken.END_OBJECT) {
						String fieldName = jParser.getCurrentName();
						if ("metadata".equals(fieldName) || "ImageMetaData".equals(
								fieldName) || "ImageMetadata".equals(fieldName) || "Metadata"
								.equals(fieldName)) {
							while (jParser.nextToken() != JsonToken.END_ARRAY) {
								// This line would be more generic but the translator from old
								// formats is blocking that for now
								// This should be restored.
								// metadataList.add(mergedArchiveType.createMetadata(jParser));
								allMetadataItems.add(new MarsOMEMetadata(jParser));
							}
						}

						if ("molecules".equals(fieldName) || "Molecules".equals(fieldName))
							break;
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			Set<String> duplicateMetadataUIDs = new HashSet<>();

			// First make a list of duplicates if there are duplicates
			for (MarsMetadata metaItem : allMetadataItems) {
				String metaUID = metaItem.getUID();
				if (metaUIDs.contains(metaUID)) {
					duplicateMetadataUIDs.add(metaUID);
				} else {
					metaUIDs.add(metaUID);
					metaItem.logln(log);
				}
			}

			Map<String, ArrayList<MarsMetadata>> duplicateMetadatas =
					new HashMap<>();

			for (String duplicateMetaUID : duplicateMetadataUIDs) {
				Set<Integer> imageIndexes = new HashSet<>();
				ArrayList<MarsMetadata> listOfDuplicates =
						new ArrayList<>();
				for (MarsMetadata metaItem : allMetadataItems) {
					if (metaItem.getUID().equals(duplicateMetaUID)) {
						listOfDuplicates.add(metaItem);
						for (int imageIndex = 0; imageIndex < metaItem
								.getImageCount(); imageIndex++) {
							if (imageIndexes.contains(metaItem.getImage(imageIndex)
									.getImageID())) {
								logService.info("Duplicate metadata record " +
										duplicateMetaUID + " image " + metaItem.getImage(imageIndex)
										.getImageID() + " found.");
								logService.info(
										"Are you trying to merge copies of the same dataset?");
								logService.info(
										"Please resolve the conflict and run the merge command again.");
								logService.info(LogBuilder.endBlock(false));
								uiService.showDialog(
										"Merge failed due to duplicate metadata record " +
												duplicateMetaUID + " image " + metaItem.getImage(imageIndex)
												.getImageID() + ".\n" +
												"Please resolve the conflict before merging.",
										MessageType.ERROR_MESSAGE);
								return;
							} else {
								imageIndexes.add(metaItem.getImage(imageIndex).getImageID());
							}
						}
					}
				}
				duplicateMetadatas.put(duplicateMetaUID, listOfDuplicates);
			}

			// Now we need to merge any duplicate Metadata records that contain
			// different positions
			for (String duplicateMetaUID : duplicateMetadatas.keySet()) {
				List<MarsMetadata> duplicates = duplicateMetadatas.get(
						duplicateMetaUID);
				MarsMetadata mergedMetadata = duplicates.get(0);

				for (int i = 1; i < duplicates.size(); i++) {
					mergedMetadata.merge(duplicates.get(i));
				}

				for (int i = 0; i < allMetadataItems.size(); i++) {
					if (allMetadataItems.get(i).getUID().equals(duplicateMetaUID)) {
						allMetadataItems.remove(i);
						i--;
					}
				}
				allMetadataItems.add(mergedMetadata);
			}

			// Now we just need to write the file starting with the new
			// MoleculeArchiveProperties
			File fileOUT = new File(directory.getAbsolutePath() + "/merged.yama");
			try {
				OutputStream stream = new BufferedOutputStream(Files.newOutputStream(fileOUT.toPath()));

				SmileFactory jFactory = new SmileFactory();
				JsonGenerator jGenerator = jFactory.createGenerator(stream);

				// We have to have a starting { for the json...
				jGenerator.writeStartObject();

				jGenerator.writeFieldName("properties");
				mergedProperties.toJSON(jGenerator);

				jGenerator.writeArrayFieldStart("metadata");
				for (MarsMetadata metaItem : allMetadataItems) {
					metaItem.toJSON(jGenerator);
				}
				jGenerator.writeEndArray();

				// Now we need to loop through all molecules in all archives and save
				// them to the merged archive.
				jGenerator.writeArrayFieldStart("molecules");
				for (JsonParser jParser : jParsers) {
					while (jParser.nextToken() != JsonToken.END_ARRAY)
						mergedArchiveType.createMolecule(jParser).toJSON(jGenerator);

					jParser.close();
				}
				jGenerator.writeEndArray();

				// Add closing bracket.
				jGenerator.writeEndObject();
				jGenerator.close();

				// flush and close streams.
				stream.flush();
				stream.close();

				for (InputStream inputStream : fileInputStreams)
					inputStream.close();

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			logService.info("Merged " + archiveFileList.length +
					" yama files into the output archive merged.yama");
			logService.info("Including: " + archiveList);
			logService.info("In total " + mergedProperties.getNumberOfMetadatas() +
					" datasets were merged.");
			logService.info("In total " + mergedProperties.getNumberOfMolecules() +
					" molecules were merged.");
			logService.info(LogBuilder.endBlock(true));
		}
	}

	// Getters and Setters
	public void setDirectory(String dir) {
		directory = new File(dir);
	}

	public void setDirectory(File directory) {
		this.directory = directory;
	}

	public File getDirectory() {
		return directory;
	}
}
