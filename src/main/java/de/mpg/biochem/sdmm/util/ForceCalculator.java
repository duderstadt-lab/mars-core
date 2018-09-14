package de.mpg.biochem.sdmm.util;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.AllowedSolution;
import org.apache.commons.math3.analysis.solvers.BracketingNthOrderBrentSolver;

//From http://commons.apache.org/proper/commons-math/userguide/analysis.html
public class ForceCalculator implements UnivariateFunction {
	  final double relativeAccuracy = 1.0e-12;
	  final double absoluteAccuracy = 1.0e-8;
	  final int    maxOrder         = 5;
	  final double kB = 1.380648528*Math.pow(10,-23);
	  final double Temperature = 296.15;
	  
	  BracketingNthOrderBrentSolver solver;
	  
	  double persistenceLength, L0, msd;
	
	  public ForceCalculator(double persistenceLength, double L0, double msd) {
		  this.persistenceLength = persistenceLength;
		  this.L0 = L0;
		  this.msd = msd;
		  solver = new BracketingNthOrderBrentSolver(relativeAccuracy, absoluteAccuracy, maxOrder);
	  }
	  
	  public double[] calculate() {
		    double length = solver.solve(100, this, 0.0, 1.0, AllowedSolution.RIGHT_SIDE);
		    
			double[] output = new double[2];
			output[0] = getWLCForce(length);
			output[1] = length;
			
			return output;
	  }
	
	   public double value(double length) {
	     return getEquipartitionForce(length) - getWLCForce(length);
	   }
	   
	   public double getWLCForce(double length) {
		   double a = kB*Temperature/persistenceLength;
		   return a*(0.25*(Math.pow(1-length/L0,-2)) - 0.25 + length/L0);
	   }
	   
	   public double getEquipartitionForce(double length) {
		   return (kB*Temperature*length)/msd;
	   }
}
