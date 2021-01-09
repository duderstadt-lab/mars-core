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

package de.mpg.biochem.mars.image;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import net.imglib2.KDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import org.decimal4j.util.DoubleRounder;
import org.scijava.log.LogService;
import org.scijava.table.DoubleColumn;

import de.mpg.biochem.mars.metadata.MarsMetadata;
import de.mpg.biochem.mars.molecule.Molecule;
import de.mpg.biochem.mars.molecule.MoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveIndex;
import de.mpg.biochem.mars.molecule.MoleculeArchiveProperties;
import de.mpg.biochem.mars.molecule.SingleMolecule;
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.MarsMath;

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
public class PeakTracker {

	private double[] maxDifference;
	private boolean[] ckMaxDifference;
	private int minTrajectoryLength;
	public static final String[] TABLE_HEADERS_VERBOSE = { "baseline", "height",
		"sigma", "R2" };
	private double searchRadius;
	private boolean verbose = false;
	private boolean writeIntegration = false;
	private int minimumDistance;
	private double pixelSize = 1;

	private String metaDataUID;

	// Stores the KDTree list for Peaks for each T.
	private ConcurrentMap<Integer, KDTree<Peak>> KDTreeStack;

	// Stores the list of possible links from each T as a list with the key of
	// that T.
	private ConcurrentMap<Integer, List<PeakLink>> possibleLinks;

	private LogService logService;

	public PeakTracker(double maxDifferenceX, double maxDifferenceY,
		double maxDifferenceT, int minimumDistance, int minTrajectoryLength,
		boolean writeIntegration, boolean verbose, LogService logService,
		double pixelSize)
	{
		this.logService = logService;
		this.verbose = verbose;
		this.writeIntegration = writeIntegration;

		maxDifference = new double[6];
		maxDifference[0] = Double.NaN;
		maxDifference[1] = Double.NaN;
		maxDifference[2] = maxDifferenceX;
		maxDifference[3] = maxDifferenceY;
		maxDifference[4] = Double.NaN;
		maxDifference[5] = maxDifferenceT;

		ckMaxDifference = new boolean[3];
		ckMaxDifference[0] = false;
		ckMaxDifference[1] = false;
		ckMaxDifference[2] = false;

		this.minimumDistance = minimumDistance;
		this.minTrajectoryLength = minTrajectoryLength;
		this.pixelSize = pixelSize;

		if (maxDifference[2] >= maxDifference[3]) searchRadius = maxDifference[2];
		else searchRadius = maxDifference[3];
	}

	public PeakTracker(double[] maxDifference, boolean[] ckMaxDifference,
		int minimumDistance, int minTrajectoryLength, boolean writeIntegration,
		boolean verbose, LogService logService, double pixelSize)
	{
		this.logService = logService;
		this.verbose = verbose;
		this.writeIntegration = writeIntegration;
		this.maxDifference = maxDifference;
		this.ckMaxDifference = ckMaxDifference;
		this.minimumDistance = minimumDistance;
		this.minTrajectoryLength = minTrajectoryLength;
		this.pixelSize = pixelSize;

		if (maxDifference[2] >= maxDifference[3]) searchRadius = maxDifference[2];
		else searchRadius = maxDifference[3];
	}
	
	public void track(ConcurrentMap<Integer, List<Peak>> peakStack,
			MoleculeArchive<?, ?, ?, ?> archive, int channel)
	{
		List<Integer> trackingTimePoints = (List<Integer>)peakStack.keySet().stream().sorted().collect(toList());
		track(peakStack, archive, channel, trackingTimePoints);
	}

	public void track(ConcurrentMap<Integer, List<Peak>> peakStack,
			MoleculeArchive<?, ?, ?, ?> archive, int channel, List<Integer> trackingTimePoints)
	{
		metaDataUID = archive.getMetadata(0).getUID();

		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();

		KDTreeStack = new ConcurrentHashMap<>();
		possibleLinks = new ConcurrentHashMap<>();

		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);

		logService.info("building KDTrees and finding possible Peak links...");

		double starttime = System.currentTimeMillis();

		try {

			forkJoinPool.submit(() -> trackingTimePoints.parallelStream()
				.forEach(t -> {
					// Remember this operation will change the order of the peaks in the
					// Arraylists but that should not be a problem here...

					// If you have a very small ROI and there are fames with no actual
					// peaks in them.
					// you need to skip that T.
					if (peakStack.containsKey(t))
					{
						KDTree<Peak> tree = new KDTree<Peak>(peakStack.get(t), peakStack
							.get(t));
						KDTreeStack.put(trackingTimePoints.indexOf(t), tree);
					}
				})).get();

			forkJoinPool.submit(() -> trackingTimePoints.parallelStream()
				.forEach(t -> findPossibleLinks(peakStack, trackingTimePoints.indexOf(t), trackingTimePoints))).get();
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
			starttime) / 60000, 2) + " minutes.");

		HashMap<String, Integer> trackLengths = new HashMap<>();

		List<Peak> trackFirstT = new ArrayList<Peak>();

		logService.info("Connecting most likely links...");

		starttime = System.currentTimeMillis();

		for (int indexT = 0; indexT < possibleLinks.size(); indexT++) {
			List<PeakLink> tPossibleLinks = possibleLinks.get(indexT);
			if (tPossibleLinks != null) {
				for (int i = 0; i < tPossibleLinks.size(); i++) {
					Peak from = tPossibleLinks.get(i).getFrom();
					Peak to = tPossibleLinks.get(i).getTo();

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
							new RadiusNeighborSearchOnKDTree<Peak>(KDTreeStack.get(q));
						radiusSearch.search(to, minimumDistance, false);

						for (int w = 0; w < radiusSearch.numNeighbors(); w++) {
							if (radiusSearch.getSampler(w).get().getUID() != null)
								regionAlreadyLinked = true;
						}
					}

					if (from.getUID() != null && !regionAlreadyLinked) {
						to.setUID(from.getUID());
						trackLengths.put(from.getUID(), trackLengths.get(from
							.getUID()) + 1);

						// Add references in each peak for forward and backward links...
						from.setForwardLink(to);
						to.setBackwardLink(from);

					}
					else if (!regionAlreadyLinked) {
						// Generate a new UID
						String UID = MarsMath.getUUID58();
						from.setUID(UID);
						to.setUID(UID);
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
			starttime) / 60000, 2) + " minutes.");

		logService.info("Building archive...");

		starttime = System.currentTimeMillis();

		// I think we need to reinitialize this pool since I shut it down above.
		forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);

		// Now we build a MoleculeArchive in a multithreaded manner in which
		// each molecule is build by a different thread just following the
		// links until it hits a molecule with no UID, which signifies the end of
		// the track.
		try {
			forkJoinPool.submit(() -> trackFirstT.parallelStream().forEach(
				startingPeak -> buildMolecule(startingPeak, trackLengths, archive,
					channel))).get();
		}
		catch (InterruptedException | ExecutionException e) {
			// handle exceptions
			logService.error("Failed to build MoleculeArchive.. " + e.getMessage());
			e.printStackTrace();
		}
		finally {
			forkJoinPool.shutdown();
		}

		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() -
			starttime) / 60000, 2) + " minutes.");
	}

	private void findPossibleLinks(ConcurrentMap<Integer, List<Peak>> peakStack,
		int indexT, List<Integer> trackingTimePoints)
	{
		if (!peakStack.containsKey(trackingTimePoints.get(indexT))) return;

		List<PeakLink> tPossibleLinks = new ArrayList<PeakLink>();

		int endT = indexT + (int) maxDifference[5];

		// Don't search past the last slice...
		if (endT >= trackingTimePoints.size()) endT = trackingTimePoints.size() - 1;

		// Here we only need to loop until maxDifference[5] slices into the
		// future...
		for (int j = indexT + 1; j <= endT; j++) {
			// can't search if there are not peaks in that given slice...
			if (!KDTreeStack.containsKey(j)) continue;

			RadiusNeighborSearchOnKDTree<Peak> radiusSearch =
				new RadiusNeighborSearchOnKDTree<Peak>(KDTreeStack.get(j));
			for (Peak linkFrom : peakStack.get(trackingTimePoints.get(indexT))) {
				radiusSearch.search(linkFrom, searchRadius, false);

				for (int q = 0; q < radiusSearch.numNeighbors(); q++) {
					// Let's check that everything is below the max distance
					Peak linkTo = radiusSearch.getSampler(q).get();
					boolean valid = true;

					// We check distance again here because the KDTree search can only do
					// radius
					// and perhaps we want to look at dy bigger than dx
					// this is why we take the larger difference above...
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
		Collections.sort(tPossibleLinks, new Comparator<PeakLink>() {

			@Override
			public int compare(PeakLink o1, PeakLink o2) {
				// Sort by T difference
				if (o1.getTDifference() != o2.getTDifference()) return Double.compare(o1
					.getTDifference(), o2.getTDifference());

				// next sort by distance - the shorter linking distance wins...
				return Double.compare(o1.getSquaredDistance(), o2.getSquaredDistance());
			}

		});
		possibleLinks.put(indexT, tPossibleLinks);
	}

	@SuppressWarnings("unchecked")
	private <M extends Molecule> void buildMolecule(Peak startingPeak,
		HashMap<String, Integer> trajectoryLengths, MoleculeArchive<M, ?, ?, ?> archive,
		int channel)
	{
		// don't add the molecule if the trajectory length is below
		// minTrajectoryLength
		if (trajectoryLengths.get(startingPeak.getUID())
			.intValue() < minTrajectoryLength) return;

		Map<String, DoubleColumn> columns =
			new LinkedHashMap<String, DoubleColumn>();

		columns.put("T", new DoubleColumn("T"));
		columns.put("x", new DoubleColumn("x"));
		columns.put("y", new DoubleColumn("y"));

		if (writeIntegration) columns.put("intensity", new DoubleColumn(
			"intensity"));

		if (verbose) for (int i = 0; i < TABLE_HEADERS_VERBOSE.length; i++)
			columns.put(TABLE_HEADERS_VERBOSE[i], new DoubleColumn(
				TABLE_HEADERS_VERBOSE[i]));

		// Now loop through all peaks connected to this starting peak and
		// add them to a DataTable as we go
		Peak peak = startingPeak;
		peak.addToColumns(columns);

		// fail-safe in case somehow a peak is linked to itself?
		// Fixes some kind of bug observed very very rarely that
		// prevents creation of an archive...
		int sizeT = archive.getMetadata(0).getImage(0).getSizeT();
		int count = 0;
		while (peak.getForwardLink() != null && count < sizeT) {
			peak = peak.getForwardLink();
			peak.addToColumns(columns);
			count++;
		}

		MarsTable table = new MarsTable();
		for (String key : columns.keySet())
			table.add(columns.get(key));

		// Convert units
		if (pixelSize != 1) {
			table.rows().forEach(row -> {
				row.setValue("x", row.getValue("x") * pixelSize);
				row.setValue("y", row.getValue("y") * pixelSize);
			});
		}

		Molecule mol = archive.createMolecule(startingPeak.getUID(), table);
		mol.setMetadataUID(metaDataUID);
		mol.setChannel(channel);
		mol.setImage(archive.getMetadata(0).images().findFirst().get()
			.getImageID());
		archive.put((M) mol);
	}
}
