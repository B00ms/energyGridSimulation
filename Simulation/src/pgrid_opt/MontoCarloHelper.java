package pgrid_opt;

import java.io.File;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import pgrid_opt.ConfigCollection.CONFIGURATION_TYPE;

import org.apache.commons.math3.random.RandomDataGenerator;

/**
 * Helper class to generate random numbers from a Weibell distribution, a Normal distribution and, a Uniform distribution.
 * @author julien
 *
 */
public class MontoCarloHelper {

	private RandomDataGenerator rand = new RandomDataGenerator();
	private double shape, scale, mean, sigma;
	/*Config conf;
	private static String OS = System.getProperty("os.name");*/
	private ConfigCollection config = new ConfigCollection();

//	private static Config conf = ConfigFactory.parseFile(new File("../config/application.conf"));

	public MontoCarloHelper(){
		/*if(OS.startsWith("Windows") || OS.startsWith("Linux")) {
			conf = ConfigFactory.parseFile(new File("../config/application.conf"));
		}else{
			conf = ConfigFactory.parseFile(new File("config/application.conf"));
		}*/
		/*Config monteConfig = conf.getConfig("monte-carlo");
		this.shape = monteConfig.getDouble("shape");
		this.scale = monteConfig.getDouble("scale");
		this.mean = monteConfig.getDouble("mean");
		this.sigma = monteConfig.getDouble("sigma");*/
		
		this.shape = config.getConfigDoubleValue(CONFIGURATION_TYPE.MONTE_CARLO, "shape");
		this.scale = config.getConfigDoubleValue(CONFIGURATION_TYPE.MONTE_CARLO, "scale");
		this.mean = config.getConfigDoubleValue(CONFIGURATION_TYPE.MONTE_CARLO, "mean");
		this.sigma = config.getConfigDoubleValue(CONFIGURATION_TYPE.MONTE_CARLO, "sigma");
		
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

	public double getRandomGamma(){
		//Parameters as defined in carpinelli2014
		return rand.nextGamma(8.9166, 0.0311);
	}

}
