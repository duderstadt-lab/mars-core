/*******************************************************************************
 * Copyright (C) 2019, Duderstadt Lab
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package de.mpg.biochem.mars.ImageProcessing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.decimal4j.util.DoubleRounder;
import org.scijava.log.LogService;

import de.mpg.biochem.mars.molecule.SingleMolecule;
import de.mpg.biochem.mars.molecule.SingleMoleculeArchive;
import de.mpg.biochem.mars.molecule.AbstractMoleculeArchive;
import de.mpg.biochem.mars.molecule.MoleculeArchiveService;
import de.mpg.biochem.mars.table.MarsTable;
import de.mpg.biochem.mars.util.MarsMath;
import ij.IJ;
import org.scijava.table.DoubleColumn;
import net.imglib2.KDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;

public class PeakTracker {
	double[] maxDifference;
	boolean[] ckMaxDifference;
	int minTrajectoryLength;
	public static final String[] TABLE_HEADERS_VERBOSE = {"baseline", "error_baseline", "height", "error_height", "x", "error_x", "y", "error_y", "sigma", "error_sigma"};
	double searchRadius;
	boolean PeakFitter_writeEverything = false;
	boolean writeIntegration = false;
	int minimumDistance;
	
	String metaDataUID;
	
	//Stores the KDTree list for Peaks for each slice.
	private ConcurrentMap<Integer, KDTree<Peak>> KDTreeStack;
	//private Set<PeakLink> possibleLinks; 
	
	//Stores the list of possible links from each slice as a list with the key of that slice.
	private ConcurrentMap<Integer, ArrayList<PeakLink>> possibleLinks;
	
	LogService logService;
	
	public PeakTracker(double[] maxDifference, boolean[] ckMaxDifference, int minimumDistance, int minTrajectoryLength, boolean writeIntegration, boolean PeakFitter_writeEverything, LogService logService) {
		this.logService = logService;
		this.PeakFitter_writeEverything = PeakFitter_writeEverything;
		this.writeIntegration = writeIntegration;
		this.maxDifference = maxDifference;
		this.ckMaxDifference = ckMaxDifference;
		this.minimumDistance = minimumDistance;
		this.minTrajectoryLength = minTrajectoryLength;

		if (maxDifference[2] >= maxDifference[3])
			searchRadius = maxDifference[2];
		else
			searchRadius = maxDifference[3];
	}
	
	public void track(ConcurrentMap<Integer, ArrayList<Peak>> PeakStack, SingleMoleculeArchive archive) {
		//The metadata information should have been added already
		//this should always be a new archive so there can only be one metadata item added
		//at index 0.
		metaDataUID = archive.getImageMetadata(0).getUID();
		
		//Need to determine the number of threads
		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
		
		//First we will build a KDTree for each peak list to allow for fast 2D searching..
		//For this purpose we will make a second ConcurrentMap with slice as the key and KDTrees for each slice
		KDTreeStack = new ConcurrentHashMap<>();
		
		//Size required for two possible links between each frame for each peak.
		//int initialCapacity = PeakStack.size();
		//if (PeakStack.containsKey(1)) {
		//	initialCapacity = PeakStack.size();// * PeakStack.get(1).size() * (int)maxDifference[5] * 2;
		//}
		//possibleLinks = new ConcurrentHashMap<>(initialCapacity);
		
		possibleLinks = new ConcurrentHashMap<>();
		
		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
		
		logService.info("building KDTrees and finding possible Peak links...");
		
		double starttime = System.currentTimeMillis();
		
		try {		
			
			forkJoinPool.submit(() -> IntStream.rangeClosed(1, PeakStack.size()).parallel().forEach(i -> {
					//Remember this operation will change the order of the peaks in the Arraylists but that should not be a problem here...
				
					//If you have a very small ROI and there are fames with no actual peaks in them.
					//you need to skip that slice.
					if (PeakStack.containsKey(i)) {
						KDTree<Peak> tree = new KDTree<Peak>(PeakStack.get(i), PeakStack.get(i));
						KDTreeStack.put(i, tree);
					}
				})).get();
			
	        //This will spawn a bunch of threads that will find all possible links for peaks 
			//within each slice individually in parallel and put the results into the global map possibleLinks
	        forkJoinPool.submit(() -> IntStream.rangeClosed(1, PeakStack.size()).parallel().forEach(i -> findPossibleLinks(PeakStack, i))).get();
	        
	    } catch (InterruptedException | ExecutionException e) {
	        // handle exceptions
	    	logService.error("Failed to finish building KDTrees.. " + e.getMessage());
	    	e.printStackTrace();
	    } finally {
	        forkJoinPool.shutdown();
	    }
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		
		//Now we have sorted lists of all possible links from most to least likely for each slice
		//Now we just need to run through the lists making the links until we run out of peaks to link
		//This will ensure the most likely links are made first and other possible links involving the
		//same peaks will not be possible because we only link each peak once...
		
		//This is all single threaded at the moment, I am not sure if it would be easy to multithread this process since the order really matters
			
		//This will keep track of the length of the trajectories so we can remove short ones later
		HashMap<String, Integer> trajectoryLengths = new HashMap<>();
		
		//This will keep track of the first peak for each trajectory
		//I think since the loop below goes from slice 1 forward we should always get the first slice with the peak.
		ArrayList<Peak> trajectoryFirstSlice = new ArrayList<Peak>();
		
		
		//We also need to check if a link has already been made within the local region
		//So I think the best idea is to add the Peaks we have linked to to a KDTree
		//Then always reject further links into the same region....
		
		logService.info("Connecting most likely links...");
		
		starttime = System.currentTimeMillis();
		
		for (int slice=1;slice<=possibleLinks.size();slice++) {
			ArrayList<PeakLink> slicePossibleLinks = possibleLinks.get(slice);
			if (slicePossibleLinks != null) { 
				for (int i=0; i<slicePossibleLinks.size();i++) {
					Peak from = slicePossibleLinks.get(i).getFrom();
					Peak to = slicePossibleLinks.get(i).getTo();
					
					if (from.getForwardLink() != null || to.getBackwardLink() != null) {
						//already linked
						continue;
					} 
					
					//We need to check if the to peak has any nearest neighbors that have already been linked...
					boolean regionAlreadyLinked = false;
					for (int q=slice+1;q<=slice + maxDifference[5];q++) {
						if (!KDTreeStack.containsKey(q))
							continue;
						
						RadiusNeighborSearchOnKDTree< Peak > radiusSearch = new RadiusNeighborSearchOnKDTree< Peak >( KDTreeStack.get(q) );
							radiusSearch.search(to, minimumDistance, false);
							
							for (int w = 0 ; w < radiusSearch.numNeighbors() ; w++ ) {
								if (radiusSearch.getSampler(w).get().getUID() != null)
									regionAlreadyLinked = true;
							}	
					}
					
					if (from.getUID() != null && !regionAlreadyLinked) {
						to.setUID(from.getUID());
						trajectoryLengths.put(from.getUID(), trajectoryLengths.get(from.getUID()) + 1);
						
						//Add references in each peak for forward and backward links...
						from.setForwardLink(to);
						to.setBackwardLink(from);
						
					} else if (!regionAlreadyLinked) { 
						//Generate a new UID
						String UID = MarsMath.getUUID58();
						from.setUID(UID);
						to.setUID(UID);
						trajectoryLengths.put(UID, 2);
						trajectoryFirstSlice.add(from);
						
						//Add references in each peak for forward and backward links...
						from.setForwardLink(to);
						to.setBackwardLink(from);
					}
				}
			}
		}
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
		
		logService.info("Building molecule archive...");
		
		logService.info("trajectoryFirstSlice size" + trajectoryFirstSlice.size());
		
		starttime = System.currentTimeMillis();
		
		//I think I need to reinitialize this pool since I shut it down above.
		forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
	
		//Now we build a MoleculeArchive in a multithreaded manner in which 
		//each molecule is build by a different thread just following the 
		//links until it hits a molecule with no UID, which signifies the end of the trajectory.
		try {		
			forkJoinPool.submit(() -> trajectoryFirstSlice.parallelStream().forEach(startingPeak -> buildMolecule(startingPeak, trajectoryLengths, archive))).get();  
	    } catch (InterruptedException | ExecutionException e) {
	        // handle exceptions
	    	logService.error("Failed to build MoleculeArchive.. " + e.getMessage());
	    	e.printStackTrace();
	    } finally {
	        forkJoinPool.shutdown();
	    }
		
		logService.info("Time: " + DoubleRounder.round((System.currentTimeMillis() - starttime)/60000, 2) + " minutes.");
	}
	
	private void findPossibleLinks(ConcurrentMap<Integer, ArrayList<Peak>> PeakStack, int slice) {
		if (!PeakStack.containsKey(slice))
			return;
		
		ArrayList<PeakLink> slicePossibleLinks = new ArrayList<PeakLink>();
		
		int endslice = slice + (int)maxDifference[5];
		
		//Don't search past the last slice...
		if (endslice > PeakStack.size())
			endslice = PeakStack.size();
		
		//Here we only need to loop until maxDifference[5] slices into the future...
		for (int j = slice + 1;j <= endslice; j++) {
			//can't search if there are not peaks in that given slice...
			if (!KDTreeStack.containsKey(j))
				continue;
			
			RadiusNeighborSearchOnKDTree< Peak > radiusSearch = new RadiusNeighborSearchOnKDTree< Peak >( KDTreeStack.get(j) );
			for (Peak linkFrom: PeakStack.get(slice)) {
				radiusSearch.search(linkFrom, searchRadius, false);
				
				for (int q = 0 ; q < radiusSearch.numNeighbors() ; q++ ) {
					//Let's check that everything is below the max distance
					Peak linkTo = radiusSearch.getSampler(q).get();
					boolean valid = true;
					
					//We check distance again here because the KDTree search can only do radius
					//and perhaps we want to look at dy bigger than dx 
					//this is why we take the larger difference above...
					if (ckMaxDifference[0] && Math.abs(linkFrom.baseline - linkTo.baseline) > maxDifference[0])
						valid = false;
					else if (ckMaxDifference[1] && Math.abs(linkFrom.height - linkTo.height) > maxDifference[1])
						valid = false;
					else if (Math.abs(linkFrom.x - linkTo.x) > maxDifference[2])
						valid = false;
					else if (Math.abs(linkFrom.y - linkTo.y) > maxDifference[3])
						valid = false;
					else if (ckMaxDifference[2] && Math.abs(linkFrom.sigma - linkTo.sigma) > maxDifference[4])
						valid = false;
					
					if (valid) {
						PeakLink link = new PeakLink(linkFrom, linkTo, radiusSearch.getSquareDistance(q), slice, j - slice);
						slicePossibleLinks.add(link);
					}
				}
			}
		}
		//Now we sort all the possible links for this slice...
		Collections.sort(slicePossibleLinks, new Comparator<PeakLink>(){
			@Override
			public int compare(PeakLink o1, PeakLink o2) {
				//Sort by slice difference
				if (o1.sliceDifference != o2.sliceDifference)
					return Double.compare(o1.sliceDifference, o2.sliceDifference);
				
				//next sort by distance - the shorter linking distance wins...
				return Double.compare(o1.distanceSq, o2.distanceSq);
			}
			
		});
		possibleLinks.put(slice, slicePossibleLinks);
	}
	
	private void buildMolecule(Peak startingPeak, HashMap<String, Integer> trajectoryLengths, SingleMoleculeArchive archive) {
		//don't add the molecule if the trajectory length is below minTrajectoryLength
		if (trajectoryLengths.get(startingPeak.getUID()).intValue() < minTrajectoryLength)
			return;
		
		//Now loop through all peaks connected to this starting peak and
		//add them to a DataTable as we go
		MarsTable table = buildResultsTable();
		Peak peak = startingPeak;
		addPeakToTable(table, peak, peak.getSlice());
		
		//fail-safe in case somehow a peak is linked to itself?
		//Fixes some kind of bug observed very very rarely that 
		//prevents creation of an archive...
		int slices = archive.getImageMetadata(0).getDataTable().getRowCount();
		int count = 0;
		while (peak.getForwardLink() != null && count < slices) {
			peak = peak.getForwardLink();
			addPeakToTable(table, peak, peak.getSlice());
			count++;
		}

		SingleMolecule mol = new SingleMolecule(startingPeak.getUID(), table);
		mol.setImageMetadataUID(metaDataUID);
		archive.put(mol);
	}
	
	private MarsTable buildResultsTable() {
		MarsTable molTable = new MarsTable();
		
		ArrayList<DoubleColumn> columns = new ArrayList<DoubleColumn>();
		
		if (PeakFitter_writeEverything) {
			for (int i=0;i<TABLE_HEADERS_VERBOSE.length;i++)
				columns.add(new DoubleColumn(TABLE_HEADERS_VERBOSE[i]));
		} else {
			columns.add(new DoubleColumn("x"));
			columns.add(new DoubleColumn("y"));
		}
		if (writeIntegration) 
			columns.add(new DoubleColumn("Intensity"));
			
		columns.add(new DoubleColumn("slice"));
		
		for(DoubleColumn column : columns)
			molTable.add(column);	
		
		return molTable;
	}
	private void addPeakToTable(MarsTable table, Peak peak, int slice) {
		table.appendRow();
		int row = table.getRowCount() - 1;
		if (PeakFitter_writeEverything) {
			table.set(0, row, peak.getBaseline());
			table.set(1, row, peak.getBaselineError());
			table.set(2, row, peak.getHeight());
			table.set(3, row, peak.getHeightError());
			table.set(4, row, peak.getX());
			table.set(5, row, peak.getXError());
			table.set(6, row, peak.getY());
			table.set(7, row, peak.getYError());
			table.set(8, row, peak.getSigma());
			table.set(9, row, peak.getSigmaError());
			if (writeIntegration) {
				table.set(10, row, peak.getIntensity());
				table.set(11, row, (double)slice);
			} else {
				table.set(10, row, (double)slice);
			}
		} else {
			table.set(0, row, peak.getX());
			table.set(1, row, peak.getY());
			if (writeIntegration) {
				table.set(2, row, peak.getIntensity());
				table.set(3, row, (double)slice);
			} else {
				table.set(2, row, (double)slice);
			}
		}
	}
}
