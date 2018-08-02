package de.mpg.biochem.sdmm.plot;

import java.awt.Color;

public class PlotProperties {
		public String xColumn;
		public String yColumn;
		public int type = 0;
		
		public String CurveName;
		
		public boolean drawSegments = false;
		
		//Add color setting?
		public Color color = Color.black;
		public Color segments_color = Color.red;
		
		public PlotProperties(String CurveName, String xColumn, String yColumn, Color color, int type, Color segments_color, boolean drawSegments) {
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
			return drawSegments;
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

