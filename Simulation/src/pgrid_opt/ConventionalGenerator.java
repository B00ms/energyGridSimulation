package pgrid_opt;

import java.io.File;
import java.util.List;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import pgrid_opt.ConfigCollection.CONFIGURATION_TYPE;

public class ConventionalGenerator extends Generator implements Comparable<ConventionalGenerator>{


	private int mttf;//mean time to failure
	private int mttr;//mean time to repair
	private boolean generatorFailure = false; //Indicates if the generator is working normally or if it has failed
	/*private static Config conf;
	private Config convGeneratorConf;*/
	private ConfigCollection config = new ConfigCollection();
	private static String OS = System.getProperty("os.name");

	private double maxProductionIncrease;
	private double dayAheadLimitMax;
	private double dayAheadLimitMin;

	private List<Offer> offerIncreaseProduction;
	private List<Offer> offerDecreaseProduction;

	public ConventionalGenerator(double minProduction, double maxProduction, double coef, String type, double production, int nodeId) {
		super(minProduction, maxProduction, coef, type, production, nodeId);

		// only used with conventional generator.
		this.mttf = config.getConfigIntValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "mttf");
		this.mttr = config.getConfigIntValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "mttr");

		/*if(OS.startsWith("Windows") || OS.startsWith("Linux")){
			conf = ConfigFactory.parseFile(new File("../config/application.conf"));
		}else{
			conf = ConfigFactory.parseFile(new File("config/application.conf"));
		}*/

		/*convGeneratorConf = conf.getConfig("conventionalGenerator");
		maxProductionIncrease = convGeneratorConf.getDouble("maxProductionIncrease");
		dayAheadLimitMax = convGeneratorConf.getDouble("dayAheadLimitMax");
		dayAheadLimitMin = convGeneratorConf.getDouble("dayAheadLimitMin");*/
		
		maxProductionIncrease = config.getConfigDoubleValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "maxProductionIncrease");
		dayAheadLimitMax = config.getConfigDoubleValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "dayAheadLimitMax");
		dayAheadLimitMin =  config.getConfigDoubleValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "dayAheadLimitMin");
	}

	public ConventionalGenerator(double minProduction, double maxProduction, double coef, String type, double production) {
		super(minProduction, maxProduction, coef, type, production);

		// only used with conventional generator.
		this.mttf = config.getConfigIntValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "mttf");
		this.mttr = config.getConfigIntValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "mttr");
	}

	public boolean getGeneratorFailure() {
		return generatorFailure;
	}

	public void setGeneratorFailure(boolean generatorFailure) {
		this.generatorFailure = generatorFailure;
	}

	public double initializeProduction(double production){

		// check edge cases for initialization
		if(production>this.maxp){
			this.production = this.maxp*dayAheadLimitMax;
		}else if(production<this.minp){
			this.production = this.minp*dayAheadLimitMin;
		}else{
			this.production = production;
		}

		return this.production;
	}

	public double setProduction(double production) {

		//For the edge case where we dont want to change production:
		double productionIncrease = 0;
		if (production-production != production)
			productionIncrease = this.production-production;
		else
			productionIncrease = 0;

		/* We check if not the production increase is <= the maximum power increase (xx% of maximum power generation)
		 * If the increase is smaller than xx% of maxp we go inside the if and set the production
		 * If not the increase of production to the highest possible value.
		 */
		if((productionIncrease < 0 && (Math.abs(productionIncrease) <= maxp))){
			production = this.production + Math.abs(productionIncrease);
		} else if(productionIncrease < 0)
			production = this.production + maxp;

		if (maxp < production){
			//New production is higher than maximum allowed production.
			this.production = maxp;
			return this.production;
		} else if(minp > production){
			//New production is lower than minimum allowed production.
			this.production = minp;
			return this.production;
		} else {
			//New production falls within the margins.
			this.production = production;
			return this.production;
		}
	}

	/**
	 * Scheduled production lies between minp+7,5% and maxp-7,5% to leave room for buffer
	 * @param production new production
	 * @return production
	 */
	public double setScheduledProduction(double production, double previousProduction) {
		double productionIncrease = 0;

		if (production-production != production)
			productionIncrease = this.production-production;
		else
			productionIncrease = 0;

		// only use when previous production is higher than 0
		// |Ph - Ph+1| <0,5% Pmax
		if(previousProduction > 0 && Math.abs((previousProduction - production)) > this.getMaxP()*maxProductionIncrease){
			if(productionIncrease > 0){
				productionIncrease = this.getMaxP()*maxProductionIncrease; // limit production
			}else{
				productionIncrease = -this.getMaxP()*maxProductionIncrease; // limit production
			}
		}

		/* We check if not the production increase is <= the maximum power increase (xx% of maximum power generation)
		 * If the increase is smaller than xx% of maxp we go inside the if and set the production
		 * If not the increase of production to the highest possible value.
		 */
		if((productionIncrease < 0 && (Math.abs(productionIncrease) <= maxp*maxProductionIncrease))){
			production = this.production + Math.abs(productionIncrease);
		} else if(productionIncrease < 0)
			production = this.production + maxp*maxProductionIncrease;

		if (this.getDayAheadMaxP() < production){
			//New production is higher than maximum allowed production.
			this.production = this.getDayAheadMaxP();
			return this.production;
		} else if(this.getDayAheadMinP() > production){
			//New production is lower than minimum allowed production.
			this.production = this.getDayAheadMinP();
			return this.production;
		} else {
			//New production falls within the margins.
			this.production = production;
			return this.production;
		}
	}

	/**
	 *
	 * @return the production of this generator
	 */
	public double getProduction() {
		if (generatorFailure == false)
			return production;
		else
			return 0;
	}

	public void setMTTF(int mttf){
		this.mttf = mttf;
	}

	public void setMTTR(int mttr){
		this.mttr = mttr;
	}

	public int getMTTF(){
		return this.mttf;
	}

	public int getMTTR(){
		return this.mttr;
	}

	public void setOfferIncreaseProduction(List<Offer> offerIncreaseProduction){
		this.offerIncreaseProduction = offerIncreaseProduction;
	}

	public void setOfferDecreaseProduction(List<Offer> offerDecreaseProduction){
		this.offerDecreaseProduction = offerDecreaseProduction;
	}

	public List<Offer> getIncreaseProductionOffers(){
		return this.offerIncreaseProduction;
	}

	public List<Offer> getDecreaseProductionOffers(){
		return this.offerDecreaseProduction;
	}

	public Offer getBestIncreaseOffer(){
		Offer bestOffer = null;
		bestOffer = this.getBestOffer(this.offerIncreaseProduction);
		return bestOffer;
	}

	public Offer getBestDecreaseOffer(){
		Offer bestOffer = null;
		bestOffer = this.getBestOffer(this.offerDecreaseProduction);
		return bestOffer;
	}

	public Offer getBestOffer(List<Offer> offerList){
		Offer bestOffer = null;

		for(Offer offer : offerList){
			if(offer.getAvailable()){
				if(bestOffer == null){
					bestOffer = offer;
				}

				if(offer.getPrice() < bestOffer.getPrice()){
					bestOffer = offer;
				}
			}
		}

		return bestOffer;
	}

	public void disableProduction(){
		this.production = 0;
	}

	public void takeIncreaseOffer(int i){
		this.offerIncreaseProduction.get(i).setAvailable(false);
	}
	public void takeDecreaseOffer(int i) { this.offerDecreaseProduction.get(i).setAvailable(false); }
	public double getDayAheadMaxP(){
		return this.maxp*dayAheadLimitMax;
	}
	public double getDayAheadMinP(){
		return this.minp*dayAheadLimitMin;
	}

	@Override
	public int compareTo(ConventionalGenerator o) {

		if(this.getCoef() < o.getCoef()){
			return -1;
		}else if(this.getCoef() > o.getCoef()){
			return 1;
		}else{
			return 0;
		}
	}
}
