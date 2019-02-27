/*******************************************************************************
 * Copyright (C) 2019, Karl Duderstadt
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
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
package de.mpg.biochem.mars.RoiTools;

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

