package de.mpg.biochem.sdmm.ImageProcessing;

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

import de.mpg.biochem.sdmm.molecule.Molecule;
import de.mpg.biochem.sdmm.molecule.MoleculeArchive;
import de.mpg.biochem.sdmm.molecule.MoleculeArchiveService;
import de.mpg.biochem.sdmm.table.SDMMResultsTable;
import ij.IJ;
import net.imagej.table.DoubleColumn;
import net.imglib2.KDTree;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;

public class PeakTracker {
	double[] maxDifference;
	boolean[] ckMaxDifference;
	int minTrajectoryLength;
	String[] headers;
	double searchRadius;
	
	String metaDataUID;
	
	private ConcurrentMap<Integer, KDTree<Peak>> KDTreeStack;
	private Set<PeakLink> possibleLinks; 
	
	
	public PeakTracker(double[] maxDifference, boolean[] ckMaxDifference, int minTrajectoryLength) {
		this.maxDifference = maxDifference;
		this.ckMaxDifference = ckMaxDifference;
		this.minTrajectoryLength = minTrajectoryLength;

		if (maxDifference[2] >= maxDifference[3])
			searchRadius = maxDifference[2];
		else
			searchRadius = maxDifference[3];
		
		headers = new String[3];
		headers[0] = "x";
		headers[1] = "y";
		headers[2] = "slice";
	}
	
	public void track(ConcurrentMap<Integer, ArrayList<Peak>> PeakStack, MoleculeArchive archive) {
		//The metadata information should have been added already
		//this should always be a new archive so there can only be one metadata item added
		//at index 0.
		metaDataUID = archive.getImageMetaData(0).getUID();
		
		//Need to determine the number of threads
		final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors();
		
		//First we will build a KDTree for each peak list to allow for fast 2D searching..
		//For this purpose we will make a second ConcurrentMap with slice as the key and KDTrees for each slice
		KDTreeStack = new ConcurrentHashMap<>();
		
		//Size required for two possible links between each frame for each peak.
		int initialCapacity = PeakStack.size() * PeakStack.get(1).size() * (int)maxDifference[5] * 2;
		
		possibleLinks = ConcurrentHashMap.newKeySet(initialCapacity);

		ForkJoinPool forkJoinPool = new ForkJoinPool(PARALLELISM_LEVEL);
		
		try {		
			
			forkJoinPool.submit(() -> IntStream.rangeClosed(1, PeakStack.size()).parallel().forEach(i -> {
					//Remember this operation will change the order of the peaks in the Arraylists but that should not be a problem here...
					KDTree<Peak> tree = new KDTree<Peak>(PeakStack.get(i), PeakStack.get(i));
					KDTreeStack.put(i, tree);
				})).get();
			
	        //This will spawn a bunch of threads that will find all possible links for peaks 
			//within each slice individually in parallel and put the results into 
	        forkJoinPool.submit(() -> IntStream.rangeClosed(1, PeakStack.size()).parallel().forEach(i -> findPossibleLinks(PeakStack, i))).get();
	        
	    } catch (InterruptedException | ExecutionException e) {
	        // handle exceptions
	    } finally {
	        forkJoinPool.shutdown();
	    }
		
		//Now possibleLinks should be huge and have all possible linking options for all slices
		//We need to sort all those links to find the most likely ones.
		ArrayList<PeakLink> sortedLinkList = new ArrayList<PeakLink>(possibleLinks);
			
		//I think this has to be done with a single thread. 
		//I don't really see away around this.
		//Hopefully this will not hurt the performance.
		Collections.sort(sortedLinkList, new Comparator<PeakLink>(){
			@Override
			public int compare(PeakLink o1, PeakLink o2) {
				//First we sort by slice
				if (o1.slice != o2.slice)
					return Double.compare(o1.slice, o2.slice);
				
				//Then we sort by slice difference
				if (o1.sliceDifference != o2.sliceDifference)
					return Double.compare(o1.sliceDifference, o2.sliceDifference);
				
				//finally we sort by distance - the shorter linking distance wins...
				return Double.compare(o1.distanceSq, o2.distanceSq);
			}
			
		});
		
		//Now we have a sorted list of all possible links from most to least likely
		//Now we just need to run through the list making the links until we run out of peaks to link
		//This will ensure the most likely links are made first and other possible links involving the
		//same peaks will not be possible because we only link each peak once...
		
		//This is all single threaded at the moment, I am not sure if it would be easy to multithread this process since the order really matters
			
		HashMap<String, Integer> trajectoryLengths = new HashMap<>();
		
		for (int i=0; i<sortedLinkList.size();i++) {
			Peak from = sortedLinkList.get(i).getFrom();
			Peak to = sortedLinkList.get(i).getTo();
			
			if (to.getUID() != null) {
				//already linked
				continue;
			} else if (from.getUID() != null) {
				to.setUID(from.getUID());
				trajectoryLengths.put(from.getUID(), trajectoryLengths.get(from.getUID()) + 1);
			} else {
				//Generate a new UID
				String UID = MoleculeArchiveService.getUUID58();
				from.setUID(UID);
				to.setUID(UID);
				trajectoryLengths.put(UID, 2);
			}
		}
		
		//This next part could be multithreaded but I am not sure it will make that much difference.
	
		//Now we build the molecule archive based on the UIDs assigned above.
		for (int slice=1;slice<=PeakStack.size();slice++) {
			for (Peak peak: PeakStack.get(slice)) {
				if (peak.getUID() != null) {
					if (trajectoryLengths.get(peak.getUID()).intValue() < minTrajectoryLength)
						continue;
					
					if (archive.get(peak.getUID()) != null) {
						addPeakToTable(archive.get(peak.getUID()).getDataTable(), peak, slice);
					} else {
						SDMMResultsTable table = buildResultsTable();
						addPeakToTable(table, peak, slice);
						Molecule mol = new Molecule(peak.getUID(), table);
						mol.setImageMetaDataUID(metaDataUID);
						archive.add(mol);
					}
				}
			}
		}
	}
	
	private void findPossibleLinks(ConcurrentMap<Integer, ArrayList<Peak>> PeakStack, int slice) {
		//Here we only need to loop until maxDifference[5] slices into the future...
		for (int j = slice + 1;j <= slice + (int)maxDifference[5]; j++) {
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
						possibleLinks.add(link);
					}
				}
			}
		}
	}
	private SDMMResultsTable buildResultsTable() {
		SDMMResultsTable molTable = new SDMMResultsTable();
		for (String header: headers) {
			molTable.add(new DoubleColumn(header));
		}
		return molTable;
	}
	private void addPeakToTable(SDMMResultsTable table, Peak peak, int slice) {
		table.appendRow();
		int row = table.getRowCount() - 1;
		table.set(0, row, peak.getX());
		table.set(1, row, peak.getY());
		table.set(2, row, (double)slice);
	}
}
