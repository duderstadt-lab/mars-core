/*******************************************************************************
 * MARS - MoleculeArchive Suite - A collection of ImageJ2 commands for single-molecule analysis.
 * 
 * Copyright (C) 2018 - 2019 Karl Duderstadt
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.mpg.biochem.sdmm.RoiTools;

import ij.*;
import ij.gui.*;
import ij.measure.Calibration;

public class FrameSkipAnimator implements Runnable {
	private static double animationRate = Prefs.getDouble(Prefs.FPS, 7.0);
    private static int firstFrame=0, lastFrame=0;
    private ImagePlus imp;
    private StackWindow swin;
    private int slice;
    private int nSlices; 
    private int sliceIncrement;
    public boolean kill = false;
	
    public FrameSkipAnimator(int sliceIncrement) {
    	imp = WindowManager.getCurrentImage();
        nSlices = imp.getStackSize();
        if (nSlices<2)
            {IJ.error("Stack required."); return;}
        ImageWindow win = imp.getWindow();
        swin = (StackWindow)win;
        slice = imp.getCurrentSlice();
        this.sliceIncrement = sliceIncrement;
    }
    
	public void run() {
		int first=firstFrame, last=lastFrame;
        if (first<1 || first>nSlices || last<1 || last>nSlices)
            {first=1; last=nSlices;}
        //if (swin.getAnimate())
         //   {stopAnimation(); return;}
        imp.unlock(); // so users can adjust brightness/contrast/threshold
        //swin.setAnimate(true);
        long time, nextTime=System.currentTimeMillis();
        //Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        Calibration cal = imp.getCalibration();
        if (cal.fps!=0.0)
            animationRate = cal.fps;
        if (animationRate<0.1)
            animationRate = 1.0;

		while (!kill) {
            time = System.currentTimeMillis();

            if (time<nextTime)
                IJ.wait((int)(nextTime-time));
            else
                Thread.yield();
            nextTime += (long)((1000.0/animationRate)*sliceIncrement);
            slice += sliceIncrement;
            if (slice<first) {
                slice = first+1;
            }
            if (slice>last) {
                if (cal.loop) {
                    slice = last-1;
                    sliceIncrement*=-1;
                } else {
                    slice = first;
                }
            }
            swin.showSlice(slice);
        }
    }
}

