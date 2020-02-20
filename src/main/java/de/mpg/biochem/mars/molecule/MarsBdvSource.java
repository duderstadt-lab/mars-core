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
package de.mpg.biochem.mars.molecule;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.util.MarsUtil;
import net.imglib2.realtransform.AffineTransform3D;

public class MarsBdvSource extends AbstractJsonConvertibleRecord implements JsonConvertibleRecord {
	private String name, xDriftColumn, yDriftColumn, pathToXml;
	private AffineTransform3D affine3D;
	
	public MarsBdvSource(String name) {
		super();
		this.name = name;
		xDriftColumn = "";
		yDriftColumn = "";
		pathToXml = "";
		setAffineTransform2D(1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
	}
	
	public MarsBdvSource(JsonParser jParser) throws IOException {
		super();
		name = "";
		xDriftColumn = "";
		yDriftColumn = "";
		pathToXml = "";
		setAffineTransform2D(1.0, 0.0, 0.0, 0.0, 1.0, 0.0);
		fromJSON(jParser);
	}
	
	@Override
	protected void createIOMaps() {
		//Add to output map
		outputMap.put("Name", MarsUtil.catchConsumerException(jGenerator -> {
			jGenerator.writeStringField("Name", name);
	 	}, IOException.class));
		outputMap.put("xDriftColumn", MarsUtil.catchConsumerException(jGenerator -> {
			jGenerator.writeStringField("xDriftColumn", xDriftColumn);
	 	}, IOException.class));
		outputMap.put("yDriftColumn", MarsUtil.catchConsumerException(jGenerator -> {
			jGenerator.writeStringField("yDriftColumn", yDriftColumn);
	 	}, IOException.class));
		outputMap.put("pathToXml", MarsUtil.catchConsumerException(jGenerator -> {
			jGenerator.writeStringField("pathToXml", pathToXml);
	 	}, IOException.class));
		outputMap.put("AffineTransform3D", MarsUtil.catchConsumerException(jGenerator -> {
			//Jackson 2.9.9 compatible stuff
			//jGenerator.writeFieldName("AffineTransform3D");
			//jGenerator.writeArray(getTransformAsArray(), 0, 12);
			
			//Jackson 2.6.5 version of above
			jGenerator.writeFieldName("AffineTransform3D");
			jGenerator.writeStartArray();
			for (double num : getTransformAsArray())
				jGenerator.writeNumber(num);
			jGenerator.writeEndArray();
	 	}, IOException.class));
		
		//Add to input map
		inputMap.put("Name", MarsUtil.catchConsumerException(jParser -> {
	    	name = jParser.getText();
		}, IOException.class));
		inputMap.put("xDriftColumn", MarsUtil.catchConsumerException(jParser -> {
	    	xDriftColumn = jParser.getText();
		}, IOException.class));
		inputMap.put("yDriftColumn", MarsUtil.catchConsumerException(jParser -> {
	    	yDriftColumn = jParser.getText();
		}, IOException.class));
		inputMap.put("pathToXml", MarsUtil.catchConsumerException(jParser -> {
	    	pathToXml = jParser.getText();
		}, IOException.class));
		inputMap.put("AffineTransform3D", MarsUtil.catchConsumerException(jParser -> {
			double[] trans = new double[12];
			int index = 0;
	    	while (jParser.nextToken() != JsonToken.END_ARRAY) {
	    		trans[index] = jParser.getDoubleValue();
	    		index++;
	    	}
	    	affine3D.set(trans[0], trans[1], trans[2], trans[3], trans[4], trans[5], trans[6], trans[7], trans[8], trans[9], trans[10], trans[11]);
		}, IOException.class));
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getPathToXml() {
		return pathToXml;
	}
	
	public void setPathToXml(String pathToXml) {
		this.pathToXml = pathToXml;
	}
	
	public String getXDriftColumn() {
		return xDriftColumn;
	}
	
	public void setXDriftColumn(String xDriftColumn) {
		this.xDriftColumn = xDriftColumn;
	}
	
	public String getYDriftColumn() {
		return yDriftColumn;
	}
	
	public void setYDriftColumn(String yDriftColumn) {
		this.yDriftColumn = yDriftColumn;
	}
	
	//See https://forum.image.sc/t/applying-affine-matrix-result-from-2d-3d-registration-to-images/22298/8 for mapping info
	public void setAffineTransform2D(double m00, double m01, double m02, double m10, double m11, double m12) {
		AffineTransform3D affine = new AffineTransform3D();
		affine.set(m00, m01, 0, m02, m10, m11, 0, m12, 0, 0, 1, 0, 0, 0, 0, 1);
		affine3D = affine;
	}
	
	public AffineTransform3D getAffineTransform3D() {
		return affine3D;
	}
	
	public AffineTransform3D getAffineTransform3D(double dX, double dY) {
		AffineTransform3D affine = affine3D.copy();
		affine.set( affine.get( 0, 3 ) - dX, 0, 3 );
		affine.set( affine.get( 1, 3 ) - dY, 1, 3 );
		return affine;
	}
	
	private double[] getTransformAsArray() {
		double[] trans = new double[12];
		for (int row = 0; row < 3; row++)
			for (int column = 0; column < 4; column++)
				trans[row*4 + column] = affine3D.get(row, column);
				
		return trans;
	}
}
