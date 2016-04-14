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
	private Random random; //Random uses a uniform distribution.

	public MontoCarloHelper(double alpha, double beta, double mean, double  sigma){
		weibullDistribution = new WeibullDistribution(alpha, beta);
		normalDistribution = new NormalDistribution(mean, sigma);
		random = new Random();
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
		return random.nextInt();
	}

}
