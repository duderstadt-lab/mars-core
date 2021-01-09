package de.mpg.biochem.mars.image;

import java.io.IOException;
import java.util.Arrays;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.mpg.biochem.mars.molecule.AbstractJsonConvertibleRecord;
import net.imagej.ImgPlus;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.roi.IterableRegion;
import net.imglib2.roi.Masks;
import net.imglib2.roi.Regions;
import net.imglib2.roi.geom.GeomMasks;
import net.imglib2.roi.geom.real.WritablePolygon2D;
import net.imglib2.type.logic.BoolType;
import net.imglib2.view.Views;

/**
 * Stores shape information for objects as 2D polygons. 
 * Extends AbstractJsonConvertibleRecord to allow for saving to 
 * file as JSON. Provides utility methods to sample inside the shape,
 * calculate the area and center position as well as Peak generation
 * method. Peaks contain references to PeakShape for recovery of shape
 * after tracking using the center position.
 * 
 * @author Karl Duderstadt
 */

public class PeakShape extends AbstractJsonConvertibleRecord {
	
	public double[] x, y;
	
	public PeakShape( final double[] x, final double[] y )
	{
		this.x = x;
		this.y = y;
	}
	
	public PeakShape(JsonParser jParser) throws IOException {
		super();
		fromJSON(jParser);
	}
	
	
	/*
	 * Utility methods from  SpotRoi from trackmate with small modifications for integration into Mars.
	 * 
	 * @author Jean-Yves Tinevez &lt;jeanyves.tinevez@gmail.com&gt;
	 * 
	 */
	
	/**
	 * Returns a new <code>int</code> array containing the X pixel coordinates
	 * to which to paint this polygon.
	 * 
	 * @param calibration
	 *            the pixel size in X, to convert physical coordinates to pixel
	 *            coordinates.
	 * @param xcorner
	 *            the top-left X corner of the view in the image to paint.
	 * @param magnification
	 *            the magnification of the view.
	 * @param magnification2
	 * @return a new <code>int</code> array.
	 */
	public double[] toPolygonX( final double calibration, final double xcorner, final double spotXCenter, final double magnification )
	{
		final double[] xp = new double[ x.length ];
		for ( int i = 0; i < xp.length; i++ )
		{
			final double xc = ( spotXCenter + x[ i ] ) / calibration;
			xp[ i ] = ( xc - xcorner ) * magnification;
		}
		return xp;
	}

	/**
	 * Returns a new <code>int</code> array containing the Y pixel coordinates
	 * to which to paint this polygon.
	 * 
	 * @param calibration
	 *            the pixel size in Y, to convert physical coordinates to pixel
	 *            coordinates.
	 * @param ycorner
	 *            the top-left Y corner of the view in the image to paint.
	 * @param magnification
	 *            the magnification of the view.
	 * @return a new <code>int</code> array.
	 */
	public double[] toPolygonY( final double calibration, final double ycorner, final double spotYCenter, final double magnification )
	{
		final double[] yp = new double[ y.length ];
		for ( int i = 0; i < yp.length; i++ )
		{
			final double yc = ( spotYCenter + y[ i ] ) / calibration;
			yp[ i ] = ( yc - ycorner ) * magnification;
		}
		return yp;
	}

	public < T > IterableInterval< T > sample( final Peak peak, final ImgPlus< T > img )
	{
		return sample( peak.getDoublePosition( 0 ), peak.getDoublePosition( 1 ), img, img.averageScale( 0 ), img.averageScale( 1 ) );
	}

	public < T > IterableInterval< T > sample( final double spotXCenter, final double spotYCenter, final RandomAccessibleInterval< T > img, final double xScale, final double yScale )
	{
		final double[] xp = toPolygonX( xScale, 0, spotXCenter, 1. );
		final double[] yp = toPolygonY( yScale, 0, spotYCenter, 1. );
		final WritablePolygon2D polygon = GeomMasks.closedPolygon2D( xp, yp );
		final IterableRegion< BoolType > region = Masks.toIterableRegion( polygon );
		return Regions.sample( region, Views.extendMirrorDouble( img ) );
	}

	public double radius()
	{
		return Math.sqrt( area() / Math.PI );
	}

	public double area()
	{
		return Math.abs( signedArea( x, y ) );
	}

	public void scale( final double alpha )
	{
		for ( int i = 0; i < x.length; i++ )
		{
			final double x = this.x[ i ];
			final double y = this.y[ i ];
			final double r = Math.sqrt( x * x + y * y );
			final double costheta = x / r;
			final double sintheta = y / r;
			this.x[ i ] = costheta * r * alpha;
			this.y[ i ] = sintheta * r * alpha;
		}
	}

	public static Peak createPeak( final String UID, final double[] x, final double[] y )
	{
		// Put polygon coordinates with respect to centroid.
		final double[] centroid = centroid( x, y );
		final double xc = centroid[ 0 ];
		final double yc = centroid[ 1 ];
		final double[] xr = Arrays.stream( x ).map( x0 -> x0 - xc ).toArray();
		final double[] yr = Arrays.stream( y ).map( y0 -> y0 - yc ).toArray();
		
		// Create roi.
		final PeakShape peakShape = new PeakShape( xr, yr );
		final double r = peakShape.radius();
		final Peak peak = new Peak( UID, xc, yc );
		peak.setProperty("RADIUS", r);
		peak.setShape( peakShape );
		return peak;
	}

	private static final double[] centroid( final double[] x, final double[] y )
	{
		final double area = signedArea( x, y );
		double ax = 0.0;
		double ay = 0.0;
		final int n = x.length;
		for ( int i = 0; i < n - 1; i++ )
		{
			final double w = x[ i ] * y[ i + 1 ] - x[ i + 1 ] * y[ i ];
			ax += ( x[ i ] + x[ i + 1 ] ) * w;
			ay += ( y[ i ] + y[ i + 1 ] ) * w;
		}

		final double w0 = x[ n - 1 ] * y[ 0 ] - x[ 0 ] * y[ n - 1 ];
		ax += ( x[ n - 1 ] + x[ 0 ] ) * w0;
		ay += ( y[ n - 1 ] + y[ 0 ] ) * w0;
		return new double[] { ax / 6. / area, ay / 6. / area };
	}

	private static final double signedArea( final double[] x, final double[] y )
	{
		final int n = x.length;
		double a = 0.0;
		for ( int i = 0; i < n - 1; i++ )
			a += x[ i ] * y[ i + 1 ] - x[ i + 1 ] * y[ i ];

		return ( a + x[ n - 1 ] * y[ 0 ] - x[ 0 ] * y[ n - 1 ] ) / 2.0;
	}

	@Override
	protected void createIOMaps() {
		setJsonField("shape", jGenerator -> {
			jGenerator.writeNumberField("vertices", x.length);
			if (x.length > 0) {
				jGenerator.writeFieldName("x");
				jGenerator.writeArray(x, 0, x.length);
			}
			if (y.length > 0) {
				jGenerator.writeFieldName("y");
				jGenerator.writeArray(y, 0, y.length);
			}
		}, jParser -> {
			int vertices = 0;
			int xIndex = 0;
			int yIndex = 0;
			while (jParser.nextToken() != JsonToken.END_OBJECT) {
				if ("vertices".equals(jParser.getCurrentName())) {
					jParser.nextToken();
					vertices = jParser.getIntValue();
					x = new double[vertices];
					y = new double[vertices];
				}
				
				if ("x".equals(jParser.getCurrentName()))
					while (jParser.nextToken() != JsonToken.END_ARRAY) {
						x[xIndex] = jParser.getDoubleValue();
						xIndex++;
					}
					
				if ("y".equals(jParser.getCurrentName()))
					while (jParser.nextToken() != JsonToken.END_ARRAY) {
						y[yIndex] = jParser.getDoubleValue();
						yIndex++;
					}
			}
		});
	}
}
