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
package de.mpg.biochem.mars.plot;

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

