/*-
 * #%L
 * Molecule Archive Suite (Mars) - core data storage and processing algorithms.
 * %%
 * Copyright (C) 2018 - 2023 Karl Duderstadt
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

package de.mpg.biochem.mars.kcp;

import de.mpg.biochem.mars.table.MarsTable;

public class KCPSegment {

	public final double x1;
	public final double y1;
	public final double x2;
	public final double y2;
	public final double a;
	public final double sigma_a;
	public final double b;
	public final double sigma_b;
	public String UID;

	public static final String X1 = "X1";
	public static final String X2 = "X2";
	public static final String Y1 = "Y1";
	public static final String Y2 = "Y2";
	public static final String A = "A";
	public static final String B = "B";
	public static final String SIGMA_A = "Sigma_A";
	public static final String SIGMA_B = "Sigma_B";

	public KCPSegment(MarsTable table, int index, String UID) {
		x1 = table.getValue(X1, index);
		y1 = table.getValue(Y1, index);
		x2 = table.getValue(X2, index);
		y2 = table.getValue(Y2, index);
		a = table.getValue(A, index);
		sigma_a = table.getValue(SIGMA_A, index);
		b = table.getValue(B, index);
		sigma_b = table.getValue(SIGMA_B, index);
		this.UID = UID;
	}

	public KCPSegment(MarsTable table, int index) {
		x1 = table.getValue(X1, index);
		y1 = table.getValue(Y1, index);
		x2 = table.getValue(X2, index);
		y2 = table.getValue(Y2, index);
		a = table.getValue(A, index);
		sigma_a = table.getValue(SIGMA_A, index);
		b = table.getValue(B, index);
		sigma_b = table.getValue(SIGMA_B, index);
	}

	public KCPSegment(double x1, double y1, double x2, double y2, double a,
		double sigma_a, double b, double sigma_b)
	{
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
		this.a = a;
		this.sigma_a = sigma_a;
		this.b = b;
		this.sigma_b = sigma_b;
	}

	public KCPSegment(double x1, double y1, double x2, double y2, double a,
		double sigma_a, double b, double sigma_b, String UID)
	{
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
		this.a = a;
		this.sigma_a = sigma_a;
		this.b = b;
		this.sigma_b = sigma_b;
		this.UID = UID;
	}

	public String getUID() {
		return UID;
	}
}
