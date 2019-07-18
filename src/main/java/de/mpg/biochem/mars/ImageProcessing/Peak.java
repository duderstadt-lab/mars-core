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
package de.mpg.biochem.mars.ImageProcessing;

import java.util.ArrayList;

import org.scijava.table.DoubleColumn;
import net.imglib2.RealLocalizable;

//This is a class that contains all the information about peaks
//here we implement RealLocalizable so that we can pass lists of peaks to
//other imglib2 libraries that do cool things.
//for example we pass Peaks to KDTree to make searching a list of peak positions in 2D much faster !
//For the implementation to work all we have to do is provide the four methods at the bottom that provide the positions in x and y...

public class Peak implements RealLocalizable {
	//Used during peak linking to assign UID molecule numbers
	String UID;

	int slice;
	
	//Used for multithreaded Peak linking..
	Peak forwardLink, backwardLink;
	
	double x,y, height, baseline, sigma;
	double xError,yError, heightError, baselineError, sigmaError;
	double pixelValue;
	boolean valid = true;
	Peak(double[] values, double[] errors) {
		baseline = values[0];
		height = values[1];
		x = values[2];
		y = values[3];
		sigma = values[4];

		baselineError = errors[0];
		heightError = errors[1];
		xError = errors[2];
		yError = errors[3];
		sigmaError = errors[4];
	}
	Peak(double x, double y, double height, double baseline, double sigma) {
		this.x = x;
		this.y = y;
		this.height = height;
		this.baseline = baseline;
		this.sigma = sigma;
	}
	Peak(double x, double y, double pixelValue) {
		this.x = x;
		this.y = y;
		this.pixelValue = pixelValue;
	}
	Peak(double x, double y, double pixelValue, int slice) {
		this.x = x;
		this.y = y;
		this.pixelValue = pixelValue;
		this.slice = slice;
	}
	Peak(Peak peak) {
		this.x = peak.x;
		this.y = peak.y;
		this.baseline = peak.baseline;
		this.height = peak.height;
		this.sigma = peak.sigma;
		this.xError = peak.xError;
		this.yError = peak.yError;
		this.baselineError = peak.baselineError;
		this.heightError = peak.heightError;
		this.sigmaError = peak.sigmaError;
		this.pixelValue = peak.pixelValue;
		this.UID = peak.UID;
		this.slice = peak.slice;
	}
	
	//Getters
	public double getX() {
		return x;
	}
	public double getXError() {
		return xError;
	}
	public double getY() {
		return y;
	}
	public double getYError() {
		return yError;
	}
	public double getHeight() {
		return height;
	}
	public double getHeightError() {
		return heightError;
	}
	public double getBaseline() {
		return baseline;
	}
	public double getBaselineError() {
		return baselineError;
	}
	public double getSigma() {
		return sigma;
	}
	public double getSigmaError() {
		return sigmaError;
	}
	public double getPixelValue() {
		return pixelValue;
	}
	public boolean isValid() {
		return valid;
	}
	public String getUID() {
		return UID;
	}
	public int getSlice() {
		return slice;
	}
	public void setSlice(int slice) {
		this.slice = slice;
	}
	
	//Setters
	public void setValues(double[] values) {
		this.baseline = values[0];
		this.height = values[1];
		this.x = values[2];
		this.y = values[3];
		this.sigma = values[4];
	}
	
	public void setErrorValues(double[] errors) {
		this.baselineError = errors[0];
		this.heightError = errors[1];
		this.xError = errors[2];
		this.yError = errors[3];
		this.sigmaError = errors[4];
	}
	
	public void addToColumnsXY(ArrayList<DoubleColumn> columns) {
		columns.get(0).add(x);
		columns.get(1).add(y);
	}
	
	public void addToColumnsVerbose(ArrayList<DoubleColumn> columns) {
		columns.get(0).add(baseline);
		columns.get(1).add(baselineError);
		columns.get(2).add(height);
		columns.get(3).add(heightError);
		columns.get(4).add(x);
		columns.get(5).add(xError);
		columns.get(6).add(y);
		columns.get(7).add(yError);
		columns.get(8).add(sigma);
		columns.get(9).add(sigmaError);
	}
	
	//used for pixel sort in the peak finder
	//and for rejection of bad fits.
	public void setValid() {
		valid = true;
	}
	public void setNotValid() {
		valid = false;
	}	
	public void setUID(String UID) {
		this.UID = UID;
	}
	
	//Sets the reference to the next peak in the trajectory
	public void setForwardLink(Peak link) {
		this.forwardLink = link;
	}
	
	//Gets the reference to the next peak in the trajectory
	public Peak getForwardLink() {
		return forwardLink;
	}
	
	//Sets the reference to the previous peak in the trajectory
	public void setBackwardLink(Peak link) {
		this.backwardLink = link;
	}
	
	//Gets the reference to the previous peak in the trajectory
	public Peak getBackwardLink() {
		return backwardLink;
	}
	
	//Override from RealLocalizable interface.. so peaks can be passed to KDTree and other imglib2 functions.
	@Override
	public int numDimensions() {
		// We make no effort to think beyond 2 dimensions !
		return 2;
	}
	@Override
	public double getDoublePosition(int arg0) {
		if (arg0 == 0) {
			return x;
		} else if (arg0 == 1) {
			return y;
		} else {
			return -1;
		}
	}
	@Override
	public float getFloatPosition(int arg0) {
		if (arg0 == 0) {
			return (float)x;
		} else if (arg0 == 1) {
			return (float)y;
		} else {
			return -1;
		}
	}
	@Override
	public void localize(float[] arg0) {
		arg0[0] = (float)x;
		arg0[1] = (float)y;
	}
	@Override
	public void localize(double[] arg0) {
		arg0[0] = x;
		arg0[1] = y;
	}
}
