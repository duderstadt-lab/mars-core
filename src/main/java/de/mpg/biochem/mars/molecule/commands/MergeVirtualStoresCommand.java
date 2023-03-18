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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import org.apache.commons.io.FileUtils;
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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveIndex;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.util.LogBuilder;

@Plugin(type = Command.class, label = "Merge Virtual Stores", menu = { @Menu(
	label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT,
	mnemonic = MenuConstants.PLUGINS_MNEMONIC), @Menu(label = "Mars",
		weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = 's'), @Menu(
			label = "Molecule", weight = 2, mnemonic = 'm'), @Menu(
				label = "Merge Virtual Stores", weight = 6, mnemonic = 'm') })
public class MergeVirtualStoresCommand extends DynamicCommand {

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

	@Parameter(label = "Threads", required = false, min = "1", max = "120")
	private int nThreads = Runtime.getRuntime().availableProcessors();

	@Override
	public void run() {
		LogBuilder builder = new LogBuilder();

		String log = LogBuilder.buildTitleBlock("Merge Virtual Stores");
		builder.addParameter("Directory", directory.getAbsolutePath());
		log += builder.buildParameterList();
		logService.info(log);

		ArrayList<MoleculeArchive<?, ?, ?, ?>> archives =
			new ArrayList<MoleculeArchive<?, ?, ?, ?>>();

		FilenameFilter fileNameFilter = new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				if (name.startsWith(".")) return false;

				return name.endsWith(".yama.store");
			}
		};

		// Get all virtual archive folders
		File[] archiveDirectoryList = directory.listFiles(fileNameFilter);

		// Create merged archive folder.
		File newVirtualDirectory = new File(directory.getAbsolutePath() +
			"/merged.yama.store");
		newVirtualDirectory.mkdirs();

		if (archiveDirectoryList.length > 0) {
			// retrieve the types of all archives.
			ArrayList<String> archiveTypes = new ArrayList<String>();
			for (File file : archiveDirectoryList) {
				try {
					File propertiesFile = new File(file.getAbsolutePath() +
						"/MoleculeArchiveProperties.sml");
					if (propertiesFile.exists()) archiveTypes.add(MoleculeArchiveService
						.getArchiveTypeFromStore(propertiesFile));
					else {
						logService.info("Could not locate " + file.getAbsolutePath() +
							"/MoleculeArchiveProperties.sml.");
						logService.info(
							"All archives must be in smile format for merging. Please remove or fix this archive.");
						logService.error(LogBuilder.endBlock(false));
						return;
					}
				}
				catch (IOException e) {
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
						logService.info(archiveDirectoryList[i].getName() + " is type " +
							archiveTypes.get(i));
					logService.error(LogBuilder.endBlock(false));
					return;
				}
			}

			for (File virtualDirectory : archiveDirectoryList) {
				MoleculeArchive<?, ?, ?, ?> archive = moleculeArchiveService
					.createArchive(archiveType, virtualDirectory);
				archives.add(archive);
			}

			// No conflicts found so we start building and writing the merged file
			MoleculeArchive<?, ?, ?, ?> mergedArchiveType = moleculeArchiveService
				.createArchive(archiveType);
			MoleculeArchiveProperties<?, ?> mergedProperties = mergedArchiveType
				.createProperties();

			JsonFactory jfactory = new SmileFactory();

			int numMolecules = 0;
			int numMetadata = 0;
			String globalComments = "";
			for (MoleculeArchive<?, ?, ?, ?> archive : archives) {
				MoleculeArchiveProperties<?, ?> properties = archive.properties();
				numMolecules += properties.getNumberOfMolecules();
				numMetadata += properties.getNumberOfMetadatas();
				globalComments += "Comments from Merged Archive " + archive.getName() +
					":\n" + properties.getComments() + "\n";

				// update global indexes
				mergedProperties.addAllTags(properties.getTagSet());
				mergedProperties.addAllChannels(properties.getChannelSet());
				mergedProperties.addAllParameters(properties.getParameterSet());
				mergedProperties.addAllColumns(properties.getColumnSet());
			}

			mergedProperties.setNumberOfMolecules(numMolecules);
			mergedProperties.setNumberOfMetadatas(numMetadata);
			mergedProperties.setComments(globalComments);

			try {
				File propertiesFile = new File(newVirtualDirectory.getAbsolutePath() +
					"/MoleculeArchiveProperties.sml");
				OutputStream stream = new BufferedOutputStream(new FileOutputStream(
					propertiesFile));

				JsonGenerator jGenerator = jfactory.createGenerator(stream);
				mergedProperties.toJSON(jGenerator);
				jGenerator.close();

				stream.flush();
				stream.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}

			// read in all MarsMetadata items from all archives - I hope they fit in
			// memory :)
			ArrayList<MarsMetadata> allMetadataItems = new ArrayList<MarsMetadata>();
			ArrayList<String> metaUIDs = new ArrayList<String>();

			for (MoleculeArchive<?, ?, ?, ?> archive : archives)
				archive.metadata().forEach(metadata -> allMetadataItems.add(metadata));

			Set<String> duplicateMetadataUIDs = new HashSet<String>();

			// First make a list of duplicates if there are duplicates
			for (MarsMetadata metaItem : allMetadataItems) {
				String metaUID = metaItem.getUID();
				if (metaUIDs.contains(metaUID)) {
					duplicateMetadataUIDs.add(metaUID);
				}
				else {
					metaUIDs.add(metaUID);
					metaItem.logln(log);
				}
			}

			Map<String, ArrayList<MarsMetadata>> duplicateMetadatas =
				new HashMap<String, ArrayList<MarsMetadata>>();

			for (String duplicateMetaUID : duplicateMetadataUIDs) {
				Set<Integer> imageIndexes = new HashSet<Integer>();
				ArrayList<MarsMetadata> listofDuplicates =
					new ArrayList<MarsMetadata>();
				for (MarsMetadata metaItem : allMetadataItems) {
					if (metaItem.getUID().equals(duplicateMetaUID)) {
						listofDuplicates.add(metaItem);
						for (int imageIndex = 0; imageIndex < metaItem
							.getImageCount(); imageIndex++)
						{
							if (imageIndexes.contains(metaItem.getImage(imageIndex)
								.getImageID()))
							{
								logService.info("Duplicate metadata record " +
									duplicateMetaUID + " image " + metaItem.getImage(imageIndex)
										.getImageID() + " found.");
								logService.info(
									"Are you trying to merge copies of the same dataset?");
								logService.info(
									"Please resolve the conflict and run the merge virtual stores command again.");
								logService.info(LogBuilder.endBlock(false));
								uiService.showDialog(
									"Merge failed due to duplicate metadata record " +
										duplicateMetaUID + " image " + metaItem.getImage(imageIndex)
											.getImageID() + ".\n" +
										"Please resolve the conflict before merging.",
									MessageType.ERROR_MESSAGE);
								return;
							}
							else {
								imageIndexes.add(metaItem.getImage(imageIndex).getImageID());
							}
						}
					}
				}
				duplicateMetadatas.put(duplicateMetaUID, listofDuplicates);
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

			MoleculeArchiveIndex<?, ?> mergedIndex = mergedArchiveType.createIndex();

			for (MoleculeArchive<?, ?, ?, ?> archive : archives) {
				for (String UID : archive.getMoleculeUIDs()) {
					if (mergedIndex.getMoleculeUIDSet().contains(UID)) {
						logService.error(
							"Duplicate molecule entry found in virtual store " + archive
								.getName() +
								". Resolve conflict and try merge again. Aborting...");
						uiService.showDialog(
							"Merge failed due to duplicate molecule record " + UID + ".\n" +
								"Please resolve the conflict before merging.",
							MessageType.ERROR_MESSAGE);
						logService.error(LogBuilder.endBlock(false));
						return;
					}
					mergedIndex.getMoleculeUIDSet().add(UID);
					mergedIndex.getMoleculeUIDtoMetadataUIDMap().put(UID, archive
						.getMetadataUIDforMolecule(UID));
					if (archive.getTagSet(UID) != null) mergedIndex
						.getMoleculeUIDtoTagListMap().put(UID, archive.getTagSet(UID));
					if (archive.getChannel(UID) > -1) mergedIndex
						.getMoleculeUIDtoChannelMap().put(UID, archive.getChannel(UID));
					if (archive.getImage(UID) > -1) mergedIndex.getMoleculeUIDtoImageMap()
						.put(UID, archive.getImage(UID));
				}

				for (String metaUID : archive.getMetadataUIDs()) {
					mergedIndex.getMetadataUIDSet().add(metaUID);
					if (archive.getMetadataTagSet(metaUID) != null) if (mergedIndex
						.getMetadataUIDtoTagListMap().containsKey(metaUID))
						mergedIndex.getMetadataUIDtoTagListMap().get(metaUID).addAll(archive
							.getMetadataTagSet(metaUID));
					else mergedIndex.getMetadataUIDtoTagListMap().put(metaUID, archive
						.getMetadataTagSet(metaUID));
				}
			}

			// Let's release the memory used up by the archives..
			// archives = null;

			File indexFile = new File(newVirtualDirectory.getAbsolutePath() +
				"/indexes.sml");
			OutputStream stream;
			try {
				stream = new BufferedOutputStream(new FileOutputStream(indexFile));
				JsonGenerator jGenerator = jfactory.createGenerator(stream);

				mergedIndex.toJSON(jGenerator);

				jGenerator.close();
				stream.flush();
				stream.close();
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// Now that the MoleculeArchiveProperties is done and the indexes are done
			// We need to copy all the metadata records and molecule records.
			File newMetadataDirectory = new File(newVirtualDirectory
				.getAbsolutePath() + "/Metadata");
			newMetadataDirectory.mkdirs();

			File newMoleculeDirectory = new File(newVirtualDirectory
				.getAbsolutePath() + "/Molecules");
			newMoleculeDirectory.mkdirs();

			ArrayList<File> virtualStoreDirectoryList = new ArrayList<File>();
			Collections.addAll(virtualStoreDirectoryList, archiveDirectoryList);

			FilenameFilter nameFilter = new FilenameFilter() {

				@Override
				public boolean accept(File dir, String name) {
					if (name.startsWith(".")) return false;

					return name.endsWith(".sml");
				}
			};

			String storeList = "";
			for (int i = 0; i < archiveDirectoryList.length; i++)
				storeList += archiveDirectoryList[i].getName() + ", ";
			if (archiveDirectoryList.length > 0) storeList = storeList.substring(0,
				storeList.length() - 2);

			// Now we just need to update the metadata logs.
			log += "Merged " + archiveDirectoryList.length +
				" virtual stores into the output virtual store merged.yama.store\n";
			log += "Including: " + storeList + "\n";
			log += "In total " + mergedProperties.getNumberOfMetadatas() +
				" MarsMetadata records were merged.\n";
			log += "In total " + mergedProperties.getNumberOfMolecules() +
				" molecules were merged.\n";
			log += LogBuilder.endBlock(true) + "\n";

			for (MarsMetadata marsMetadata : allMetadataItems) {
				marsMetadata.logln(log);
				File metadataFile = new File(newMetadataDirectory.getAbsolutePath() +
					"/" + marsMetadata.getUID() + ".sml");
				try {
					OutputStream metaStream = new BufferedOutputStream(
						new FileOutputStream(metadataFile));
					JsonGenerator jGenerator = jfactory.createGenerator(metaStream);

					mergedIndex.toJSON(jGenerator);

					jGenerator.close();
					metaStream.flush();
					metaStream.close();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}

			ForkJoinPool forkJoinPool = new ForkJoinPool(nThreads);
			try {
				forkJoinPool.submit(() -> virtualStoreDirectoryList.parallelStream()
					.forEach(directory -> {
						try {
							File[] moleculeRecords = new File(directory.getAbsolutePath() +
								"/Molecules").listFiles(nameFilter);
							for (File moleculeRecord : moleculeRecords) {
								FileUtils.copyFileToDirectory(moleculeRecord,
									newMoleculeDirectory);
							}
						}
						catch (IOException e) {
							e.printStackTrace();
						}
					})).get();
			}
			catch (InterruptedException | ExecutionException e) {
				// handle exceptions
				e.printStackTrace();
			}
			finally {
				forkJoinPool.shutdown();
			}

			logService.info("Merged " + archiveDirectoryList.length +
				" virtual stores into the output virtual store merged.yama.store");
			log += "Including: " + storeList + "\n";
			logService.info("In total " + mergedProperties.getNumberOfMetadatas() +
				" MarsMetadata records were merged.");
			logService.info("In total " + mergedProperties.getNumberOfMolecules() +
				" molecules were merged.");
			logService.info(LogBuilder.endBlock(true));
		}
		else {
			logService.error(
				"No .yama.store directories found in the directory given. Nothing to merge. Aborting");
			logService.error(LogBuilder.endBlock(false));
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

	public void setThreads(int nThreads) {
		this.nThreads = nThreads;
	}

	public int getThreads() {
		return this.nThreads;
	}
}
