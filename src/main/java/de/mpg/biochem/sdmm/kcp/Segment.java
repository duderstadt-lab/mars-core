package de.mpg.biochem.sdmm.kcp;

import de.mpg.biochem.sdmm.table.SDMMResultsTable;

public class Segment {
	public double x1, y1, x2, y2, A, A_sigma, B, B_sigma;
	public Segment(SDMMResultsTable table, int index) {
		x1 = table.getValue("x1", index);
		y1 = table.getValue("y1", index);
		x2 = table.getValue("x2", index);
		y2 = table.getValue("y2", index);
		A = table.getValue("A", index);
		A_sigma = table.getValue("sigma_A", index);
		B = table.getValue("B", index);
		B_sigma = table.getValue("sigma_B", index);
	}
	public Segment(double X1, double Y1, double X2, double Y2, double iA, double iA_sigma, double iB, double iB_sigma) {
		x1 = X1;
		y1 = Y1;
		x2 = X2;
		y2 = Y2;
		A = iA;
		A_sigma = iA_sigma;
		B = iB;
		B_sigma = iB_sigma;
	}
}
