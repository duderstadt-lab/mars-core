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
package de.mpg.biochem.mars.kcp;

import de.mpg.biochem.mars.table.MARSResultsTable;

public class Segment {
	public double x1, y1, x2, y2, A, A_sigma, B, B_sigma;
	public String UID;
	public Segment(MARSResultsTable table, int index, String UID) {
		x1 = table.getValue("x1", index);
		y1 = table.getValue("y1", index);
		x2 = table.getValue("x2", index);
		y2 = table.getValue("y2", index);
		A = table.getValue("A", index);
		A_sigma = table.getValue("sigma_A", index);
		B = table.getValue("B", index);
		B_sigma = table.getValue("sigma_B", index);
		this.UID = UID;
	}
	public Segment(MARSResultsTable table, int index) {
		x1 = table.getValue("x1", index);
		y1 = table.getValue("y1", index);
		x2 = table.getValue("x2", index);
		y2 = table.getValue("y2", index);
		A = table.getValue("A", index);
		A_sigma = table.getValue("sigma_A", index);
		B = table.getValue("B", index);
		B_sigma = table.getValue("sigma_B", index);
	}
	public Segment(double X1, double Y1, double X2, double Y2, double A, double A_sigma, double B, double B_sigma) {
		x1 = X1;
		y1 = Y1;
		x2 = X2;
		y2 = Y2;
		this.A = A;
		this.A_sigma = A_sigma;
		this.B = B;
		this.B_sigma = B_sigma;
	}
	public Segment(double X1, double Y1, double X2, double Y2, double A, double A_sigma, double B, double B_sigma, String UID) {
		x1 = X1;
		y1 = Y1;
		x2 = X2;
		y2 = Y2;
		this.A = A;
		this.A_sigma = A_sigma;
		this.B = B;
		this.B_sigma = B_sigma;
		this.UID = UID;
	}
	public String getUID() {
		return UID;
	}
}
