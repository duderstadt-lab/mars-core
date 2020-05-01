package de.mpg.biochem.mars.image;

import java.util.concurrent.ConcurrentLinkedQueue;

public class PeakFactory {

	private ConcurrentLinkedQueue<Peak> recycledPeakList;
	
	public PeakFactory() {
		recycledPeakList = new ConcurrentLinkedQueue<>();
	}
	
	public Peak createPeak(double x, double y, double pixelValue, int slice) {
		Peak peak = recycledPeakList.poll();
		
		if (peak == null)
			peak = new Peak(x, y, pixelValue, slice);
		else 
			peak.reset(x, y, pixelValue, slice);
		
		return peak;
	}
	
	public void recyclePeak(Peak peak) {
		recycledPeakList.add(peak);
	}
}
