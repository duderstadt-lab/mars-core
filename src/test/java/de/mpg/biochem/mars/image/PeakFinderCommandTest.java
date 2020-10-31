
package de.mpg.biochem.mars.image;

import java.io.IOException;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.scijava.Context;
import org.scijava.options.OptionsService;
import org.scijava.plugin.Parameter;

import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imagej.axis.AxisType;
import net.imagej.axis.Axes;

import de.mpg.biochem.mars.util.Gaussian2D;

public class PeakFinderCommandTest {

	@Parameter
	protected Context context;

	@Parameter
	protected DatasetService datasetService;

	protected Context createContext() {
		return new Context(DatasetService.class);
	}

	@BeforeEach
	public void setUp() {
		createContext().inject(this);
	}

	@AfterEach
	public synchronized void cleanUp() {
		if (context != null) {
			context.dispose();
			context = null;
			datasetService = null;
		}
	}

	@Test
	@Order(1)
	void findPeaks() throws IOException {

	}

	public Dataset simulateDataset() {
		// Using the same seed each time ensure we always get
		// the same sequence of random numbers
		Random r = new Random(25);

		long[] dim = { 50, 50, 50 };
		AxisType[] axes = { Axes.X, Axes.Y, Axes.TIME };
		Dataset dataset = datasetService.create(dim, "simulated Image", axes, 16,
			false, false);

		for (int t = 0; t < dim[2]; t++)
			for (int x = 0; x < dim[0]; x++)
				for (int y = 0; y < dim[1]; y++) {
					Gaussian2D peak1 = new Gaussian2D(1000d, 3000d, 10d, 10d + t / 4,
						1.2d);
					Gaussian2D peak2 = new Gaussian2D(1000d, 3000d, 32.5d, 40d + t / 4,
						1.2d);
					Gaussian2D peak3 = new Gaussian2D(1000d, 3000d, 43.7d, 26.7d + t / 4,
						1.2d);
					Gaussian2D peak4 = new Gaussian2D(1000d, 1000d, 17.2d, 30d, 1.2d);
					Gaussian2D peak5 = new Gaussian2D(1000d, 1000d, 12.3d, 13.5d, 1.2d);
					Gaussian2D peak6 = new Gaussian2D(1000d, 2000d, 35.3d, 13.5d, 4d);

					dataset.getImgPlus().randomAccess().setPositionAndGet(x, y, t)
						.setReal(r.nextInt(1000) + 500 + peak1.getValue(x, y) + peak2
							.getValue(x, y) + peak3.getValue(x, y) + peak4.getValue(x, y) +
							peak5.getValue(x, y) + peak6.getValue(x, y));
				}
		return dataset;
	}
}
