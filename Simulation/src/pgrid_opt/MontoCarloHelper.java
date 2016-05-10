package pgrid_opt;

import java.io.File;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.math3.random.RandomDataGenerator;

/**
 * Helper class to generate random numbers from a Weibell distribution, a Normal distribution and, a Uniform distribution.
 * @author julien
 *
 */
public class MontoCarloHelper {

	private RandomDataGenerator rand = new RandomDataGenerator();
	private double shape, scale, mean, sigma;
	private static Config conf = ConfigFactory.parseFile(new File("config/application.conf"));

	public MontoCarloHelper(){
		Config monteConfig = conf.getConfig("monte-carlo");
		this.shape = monteConfig.getDouble("shape");
		this.scale = monteConfig.getDouble("scale");
		this.mean = monteConfig.getDouble("mean");
		this.sigma = monteConfig.getDouble("sigma");
	}

	//For weibull distribution: alpha = 1.6, beta = 8
	//For normal distribution: mean = 0, sigma = 0.05
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
