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
