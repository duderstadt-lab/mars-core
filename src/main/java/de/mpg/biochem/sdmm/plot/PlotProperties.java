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

