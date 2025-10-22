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

package de.mpg.biochem.mars.image;

import de.mpg.biochem.mars.metadata.MarsOMEUtils;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.object.MartianObject;
import de.mpg.biochem.mars.object.ObjectArchive;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.MarsMath;
import net.imglib2.KDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import org.decimal4j.util.DoubleRounder;
import org.scijava.log.LogService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import static java.util.stream.Collectors.toList;

/**
 * PeakTracker tracks the relative position of peaks over time based on peak
 * feature differences, minimum distance change, and minimum total track length.
 * Parameters are maintained for multiple rounds of tracking. The main
 * processing method (track) accepts a map of all peaks for each position T, the
 * final SingleMoleculeArchive that will contain the molecule records resulting
 * from tracking and the channel to set in the molecule records.
 * 
 * @author Karl Duderstadt
 */
public class  PeakTracker {

	private final double[] maxDifference;
	private final boolean[] ckMaxDifference;
	private final int minTrajectoryLength;
	private final double searchRadius;
	private final boolean verbose;
	private final int minimumDistance;
	private final double pixelSize;

	//private String metaDataUID;

	// Stores the KDTree list for Peaks for each T.
	private ConcurrentMap<Integer, KDTree<Peak>> KDTreeStack;

	// Stores the list of possible links from each T as a list with the key of
	// that T.
	private ConcurrentMap<Integer, List<PeakLink>> possibleLinks;

	private final LogService logService;

	public PeakTracker(double maxDifferenceX, double maxDifferenceY,
		double maxDifferenceT, int minimumDistance, int minTrajectoryLength,
		boolean verbose, LogService logService, double pixelSize)
	{
		this.logService = logService;
		this.verbose = verbose;

		maxDifference = new double[6];
		maxDifference[0] = Double.NaN;
		maxDifference[1] = Double.NaN;
		maxDifference[2] = maxDifferenceX;
		maxDifference[3] = maxDifferenceY;
		maxDifference[4] = Double.NaN;
		maxDifference[5] = maxDifferenceT;

		ckMaxDifference = new boolean[3];

		this.minimumDistance = minimumDistance;
		this.minTrajectoryLength = minTrajectoryLength;
		this.pixelSize = pixelSize;

		searchRadius = Math.max(maxDifference[2], maxDifference[3]);
	}

	@SuppressWarnings("unused")
	public PeakTracker(double[] maxDifference, boolean[] ckMaxDifference,
					   int minimumDistance, int minTrajectoryLength, boolean writeIntegration,
					   boolean verbose, LogService logService, double pixelSize)
	{
		this.logService = logService;
		this.verbose = verbose;
		this.maxDifference = maxDifference;
		this.ckMaxDifference = ckMaxDifference;
		this.minimumDistance = minimumDistance;
		this.minTrajectoryLength = minTrajectoryLength;
		this.pixelSize = pixelSize;

		searchRadius = Math.max(maxDifference[2], maxDifference[3]);
	}

	public void track(ConcurrentMap<Integer, List<Peak>> peakStack,
		MoleculeArchive<?, ?, ?, ?> archive, final int channel, final int nThreads)
	{
		List<Integer> trackingTimePoints = peakStack.keySet()
			.stream().sorted().collect(toList());
		track(peakStack, archive, archive.getMetadataUIDs().get(0), channel, trackingTimePoints, nThreads);
	}

	public void track(ConcurrentMap<Integer, List<Peak>> peakStack,
		MoleculeArchive<?, ?, ?, ?> archive, String metaDataUID, int channel,
		List<Integer> trackingTimePoints, final int nThreads)
	{

		KDTreeStack = new ConcurrentHashMap<>();
		possibleLinks = new ConcurrentHashMap<>();

		ForkJoinPool forkJoinPool = new ForkJoinPool(nThreads);

		logService.info("building KDTrees and finding possible Peak links...");

		double startTime = System.currentTimeMillis();

		try {

			forkJoinPool.submit(() -> trackingTimePoints.parallelStream().forEach(
				t -> {
					// Remember this operation will change the order of the peaks in the
					// Arraylists but that should not be a problem here.

					// If you have a very small ROI and there are frames with no actual
					// peaks in them.
					// you need to skip that T.
					if (peakStack.containsKey(t))
					{
						KDTree<Peak> tree = new KDTree<>(peakStack.get(t), peakStack
								.get(t));
						KDTreeStack.put(trackingTimePoints.indexOf(t), tree);
					}
				})).get();

			forkJoinPool.submit(() -> trackingTimePoints.parallelStream().forEach(
				t -> findPossibleLinks(peakStack, trackingTimePoints.indexOf(t),
					trackingTimePoints))).get();
		}
		catch (InterruptedException | ExecutionException e) {
			// handle exceptions
			logService.error("Failed to finish building KDTrees.. " + e.getMessage());
			e.printStackTrace();
		}
		finally {
			forkJoinPool.shutdown();
		}

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			startTime) / 60000, 2) + " minutes.");

		HashMap<String, Integer> trackLengths = new HashMap<>();

		List<Peak> trackFirstT = new ArrayList<>();

		logService.info("Connecting most likely links...");

		startTime = System.currentTimeMillis();

		for (int indexT = 0; indexT < possibleLinks.size(); indexT++) {
			List<PeakLink> tPossibleLinks = possibleLinks.get(indexT);
			if (tPossibleLinks != null) {
				for (PeakLink tPossibleLink : tPossibleLinks) {
					Peak from = tPossibleLink.getFrom();
					Peak to = tPossibleLink.getTo();

					if (from.getForwardLink() != null || to.getBackwardLink() != null) {
						// already linked
						continue;
					}

					// We need to check if the to peak has any nearest neighbors that have
					// already been linked...
					boolean regionAlreadyLinked = false;

					for (int q = indexT + 1; q <= indexT + maxDifference[5]; q++) {
						if (!KDTreeStack.containsKey(q)) continue;

						RadiusNeighborSearchOnKDTree<Peak> radiusSearch =
								new RadiusNeighborSearchOnKDTree<>(KDTreeStack.get(q));
						radiusSearch.search(to, minimumDistance, false);

						for (int w = 0; w < radiusSearch.numNeighbors(); w++) {
							if (radiusSearch.getSampler(w).get().getTrackUID() != null)
								regionAlreadyLinked = true;
						}
					}

					if (from.getTrackUID() != null && !regionAlreadyLinked) {
						to.setTrackUID(from.getTrackUID());
						trackLengths.put(from.getTrackUID(), trackLengths.get(from
								.getTrackUID()) + 1);

						// Add references in each peak for forward and backward links...
						from.setForwardLink(to);
						to.setBackwardLink(from);

					} else if (!regionAlreadyLinked) {
						// Generate a new UID
						String UID = MarsMath.getUUID58();
						from.setTrackUID(UID);
						to.setTrackUID(UID);
						trackLengths.put(UID, 2);
						trackFirstT.add(from);

						// Add references in each peak for forward and backward links...
						from.setForwardLink(to);
						to.setBackwardLink(from);
					}
				}
			}
		}

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			startTime) / 60000, 2) + " minutes.");

		logService.info("Adding molecules to archive...");

		startTime = System.currentTimeMillis();

		// I think we need to reinitialize this pool since I shut it down above.
		forkJoinPool = new ForkJoinPool(nThreads);

		Map<Integer, Map<Integer, Double>> channelToTtoDtMap = MarsOMEUtils
			.buildChannelToTtoDtMap(archive.getMetadata(metaDataUID));

		// Now we build a MoleculeArchive in a multithreaded manner in which
		// each molecule is build by a different thread just following the
		// links until it hits a molecule with no UID, which signifies the end of
		// the track.
		try {
			forkJoinPool.submit(() -> trackFirstT.parallelStream().forEach(
				startingPeak -> buildMolecule(startingPeak, trackLengths, archive,
						metaDataUID, channel, channelToTtoDtMap))).get();
		}
		catch (InterruptedException | ExecutionException e) {
			// handle exceptions
			logService.error("Failed to add molecules to archive.. " + e
				.getMessage());
			e.printStackTrace();
		}
		finally {
			forkJoinPool.shutdown();
		}

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			startTime) / 60000, 2) + " minutes.");
	}

	private void findPossibleLinks(ConcurrentMap<Integer, List<Peak>> peakStack,
		int indexT, List<Integer> trackingTimePoints)
	{
		if (!peakStack.containsKey(trackingTimePoints.get(indexT))) return;

		List<PeakLink> tPossibleLinks = new ArrayList<>();

		int endT = indexT + (int) maxDifference[5];

		// Don't search past the last slice...
		if (endT >= trackingTimePoints.size()) endT = trackingTimePoints.size() - 1;

		// Here we only need to loop until maxDifference[5] slices into the
		// future.
		for (int j = indexT + 1; j <= endT; j++) {
			// can't search if there are no peaks in the given slice.
			if (!KDTreeStack.containsKey(j)) continue;

			RadiusNeighborSearchOnKDTree<Peak> radiusSearch =
					new RadiusNeighborSearchOnKDTree<>(KDTreeStack.get(j));
			for (Peak linkFrom : peakStack.get(trackingTimePoints.get(indexT))) {
				radiusSearch.search(linkFrom, searchRadius, false);

				for (int q = 0; q < radiusSearch.numNeighbors(); q++) {
					// Let's check that everything is below the max distance
					Peak linkTo = radiusSearch.getSampler(q).get();
					boolean valid = true;

					// We check distance again here because the KDTree search can only do
					// radius, and perhaps we want to look at dy bigger than dx.
					// This is why we take the larger difference above.
					if (ckMaxDifference[0] && Math.abs(linkFrom.getBaseline() - linkTo
						.getBaseline()) > maxDifference[0]) valid = false;
					else if (ckMaxDifference[1] && Math.abs(linkFrom.getHeight() - linkTo
						.getHeight()) > maxDifference[1]) valid = false;
					else if (Math.abs(linkFrom.getX() - linkTo.getX()) > maxDifference[2])
						valid = false;
					else if (Math.abs(linkFrom.getY() - linkTo.getY()) > maxDifference[3])
						valid = false;
					else if (ckMaxDifference[2] && Math.abs(linkFrom.getSigma() - linkTo
						.getSigma()) > maxDifference[4]) valid = false;

					if (valid) {
						PeakLink link = new PeakLink(linkFrom, linkTo, radiusSearch
							.getSquareDistance(q), indexT, j - indexT);
						tPossibleLinks.add(link);
					}
				}
			}
		}
		// Now we sort all the possible links for this T...
		tPossibleLinks.sort((o1, o2) -> {
			// Sort by T difference
			if (o1.getTDifference() != o2.getTDifference()) return Double.compare(o1
					.getTDifference(), o2.getTDifference());

			// next sort by distance - the shorter linking distance wins...
			return Double.compare(o1.getSquaredDistance(), o2.getSquaredDistance());
		});
		possibleLinks.put(indexT, tPossibleLinks);
	}

	private <M extends Molecule> void buildMolecule(Peak startingPeak,
		HashMap<String, Integer> trajectoryLengths,
		MoleculeArchive<M, ?, ?, ?> archive, String metaDataUID, int channel,
		Map<Integer, Map<Integer, Double>> channelToTtoDtMap)
	{
		// don't add the molecule if the trajectory length is below
		// minTrajectoryLength
		if (trajectoryLengths.get(startingPeak.getTrackUID()) < minTrajectoryLength) return;

		M mol = archive.createMolecule(startingPeak.getTrackUID());
		mol.setMetadataUID(metaDataUID);
		mol.setChannel(channel);
		if (archive.metadata().findFirst().isPresent() && archive.metadata().findFirst().get().images().findFirst().isPresent())
			mol.setImage(archive.metadata().findFirst().get().images().findFirst().get()
			.getImageID());

		MarsTable table = new MarsTable();

		// Now loop through all peaks connected to this starting peak and
		// add them to a DataTable as we go
		Peak peak = startingPeak;

		// fail-safe in case there are more peak links than sizeT
		int row = 0;
		int sizeT = archive.metadata().findFirst().get().getImage(0).getSizeT();
		do {
			table.appendRow();
			table.setValue(Peak.T, row, peak.getT());
			if (channelToTtoDtMap.get(channel).get(peak.getT()) != -1) table.setValue(
				"Time_(s)", row, channelToTtoDtMap.get(channel).get(peak.getT()));
			table.setValue(Peak.X, row, peak.getX());
			table.setValue(Peak.Y, row, peak.getY());
			if (verbose) {
				for (String name : peak.getProperties().keySet())
					table.setValue(name, row, peak.getProperties().get(name));
			}
			else {
				if (peak.getProperties().containsKey(Peak.INTENSITY)) table.setValue(
					Peak.INTENSITY, row, peak.getProperties().get(Peak.INTENSITY));
				if (archive instanceof ObjectArchive) {
					table.setValue(Peak.AREA, row, peak.getProperties().get(Peak.AREA));
					table.setValue(Peak.PERIMETER, row, peak.getProperties().get(
						Peak.PERIMETER));
					table.setValue(Peak.CIRCULARITY, row, peak.getProperties().get(
						Peak.CIRCULARITY));
				}
			}

			if (archive instanceof ObjectArchive) ((MartianObject) mol).putShape(peak
				.getT(), peak.getShape());

			row++;
			peak = peak.getForwardLink();
		}
		while (peak != null && row < sizeT);

		// Convert units
		if (pixelSize != 1) {
			table.rows().forEach(r -> {
				r.setValue(Peak.X, r.getValue(Peak.X) * pixelSize);
				r.setValue(Peak.Y, r.getValue(Peak.Y) * pixelSize);

				// What about objects? The polygons be multiplied also by pixelSize.
			});
		}

		mol.setTable(table);
		archive.put(mol);
	}
}
