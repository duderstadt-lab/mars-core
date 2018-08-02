package de.mpg.biochem.sdmm.ImageProcessing;

public class PeakLink {
	Peak from;
	Peak to;
	double distanceSq;
	int slice;
	int sliceDifference;
	public PeakLink(Peak from, Peak to, double distanceSq, int slice, int sliceDifference) {
		this.from = from;
		this.to = to;
		this.distanceSq = distanceSq;
		this.slice = slice;
		this.sliceDifference = sliceDifference;
	}
	
	public Peak getFrom() {
		return from;
	}
	
	public Peak getTo() {
		return to;
	}
}
