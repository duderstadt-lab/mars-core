package de.mpg.biochem.mars.ImageProcessing;

import java.util.concurrent.ConcurrentLinkedQueue;

public class PeakLinkFactory {

	private ConcurrentLinkedQueue<PeakLink> recycledPeakLinkList;
	
	public PeakLinkFactory() {
		recycledPeakLinkList = new ConcurrentLinkedQueue<>();
	}
	
	public PeakLink createPeakLink(Peak from, Peak to, double distanceSq, int slice, int sliceDifference) {
		PeakLink peakLink = recycledPeakLinkList.poll();
		
		if (peakLink == null)
			peakLink = new PeakLink(from, to, distanceSq, slice, sliceDifference);
		else 
			peakLink.reset(from, to, distanceSq, slice, sliceDifference);
		
		return peakLink;
	}
	
	public void recyclePeakLink(PeakLink peakLink) {
		recycledPeakLinkList.add(peakLink);
	}
}
