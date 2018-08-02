package de.mpg.biochem.sdmm.plot;

import java.awt.geom.Rectangle2D;

public interface BoundsChangedListener {
	public void boundsChanged(Rectangle2D.Double bounds, int bleftMargin);
}