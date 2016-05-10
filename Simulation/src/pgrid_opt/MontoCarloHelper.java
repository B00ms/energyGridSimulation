package pgrid_opt;

import org.apache.commons.math3.random.RandomDataGenerator;

/**
 * Helper class to generate random numbers from a Weibell distribution, a Normal distribution and, a Uniform distribution.
 * @author julien
 *
 */
public class MontoCarloHelper {

	private RandomDataGenerator rand = new RandomDataGenerator();
	private double shape, scale, mean, sigma;

	public MontoCarloHelper(double shape, double scale, double mean, double  sigma){
		this.shape = shape;
		this.scale = scale;
		this.mean = mean;
		this.sigma = sigma;

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
		return rand.nextWeibull(shape, scale);
	}

	/**
	 *
	 * @return A random number from a normal distribution
	 */
	public double getRandomNormDist(){
		return rand.nextGaussian(mean, sigma);
	}

	/**
	 *
	 * @return A random number from a uniform distribution
	 */
	public double getRandomUniformDist(){
		//Comes for an approximate uniform distribution
		return rand.nextUniform(0, 1);
	}

}
