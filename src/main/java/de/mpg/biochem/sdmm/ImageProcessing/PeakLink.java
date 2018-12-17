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
