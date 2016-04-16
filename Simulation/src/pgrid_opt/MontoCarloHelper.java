package pgrid_opt;

import java.util.Random;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.WeibullDistribution;

/**
 * Helper class to generate random numbers from a Weibell distribution, a Normal distribution and, a Uniform distribution.
 * @author julien
 *
 */
public class MontoCarloHelper {

	private WeibullDistribution weibullDistribution;
	private NormalDistribution normalDistribution;

	public MontoCarloHelper(double shape, double scale, double mean, double  sigma){
		weibullDistribution = new WeibullDistribution(shape, scale);
		normalDistribution = new NormalDistribution(mean, sigma);
	}

	/**
	 * TODO: maybe implement this (Generate x amount of random numbers before generating the actual random numbers)
	 */
	private void burnin(){

	}

	/**
	 *
	 * @return A random double from a Weibull distribution
	 */
	public double getRandomWeibull(){
		return weibullDistribution.sample();
	}

	/**
	 *
	 * @return A random number from a normal distribution
	 */
	public double getRandomNormDist(){
		return normalDistribution.sample();
	}

	/**
	 *
	 * @return A random number from a uniform distribution
	 */
	public double getRandomUniformDist(){
		//Comes for an approximate uniform distribution
		return Math.random();
	}

}
