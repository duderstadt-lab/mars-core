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
package de.mpg.biochem.sdmm.plot;

import java.awt.Color;

public class PlotProperties {
		private String xColumn;
		private String yColumn;
		private int type = 0;
		
		private String CurveName;
		
		//Add color setting?
		private Color color = Color.black;
		private Color segments_color = Color.red;
		
		public PlotProperties(String xColumn, String yColumn, Color color, int type, Color segments_color) {
			this.CurveName = yColumn + " vs " + xColumn;
			this.xColumn = xColumn;
			this.yColumn = yColumn;
			this.color = color;
			this.type = type;
			this.segments_color = segments_color;
		}
		
		public PlotProperties(String CurveName, String xColumn, String yColumn, Color color, int type, Color segments_color) {
			this.CurveName = CurveName;
			this.xColumn = xColumn;
			this.yColumn = yColumn;
			this.color = color;
			this.type = type;
			this.segments_color = segments_color;
		}
		
		public String getCurveName() {
			return CurveName;
		}
		
		public int getType() {
			return type;
		}
		
		public boolean drawSegments() {
			if (segments_color == null)
				return false;
			else
				return true;
		}
		
		public Color getColor() {
			return color;
		}
		
		public Color getSegmentsColor() {
			return segments_color;
		}
		
		public String xColumnName() {
			return xColumn;
		}
		
		public String yColumnName() {
			return yColumn;
		}
	}

