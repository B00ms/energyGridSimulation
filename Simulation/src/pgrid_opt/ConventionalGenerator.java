package pgrid_opt;

import java.io.File;
import java.util.List;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ConventionalGenerator extends Generator implements Comparable<ConventionalGenerator>{


	private int mttf;//mean time to failure
	private int mttr;//mean time to repair
	private boolean generatorFailure = false; //Indicates if the generator is working normally or if it has failed
	private static Config conf;
	private Config convGeneratorConf;
	private static String OS = System.getProperty("os.name");

	private double maxProductionIncrease;
	private double dayAheadLimitMax;
	private double dayAheadLimitMin;

	private List<Offer> offerIncreaseProduction;
	private List<Offer> offerDecreaseProduction;

	public ConventionalGenerator(double minProduction, double maxProduction, double coef, String type, double production, int nodeId) {
		super(minProduction, maxProduction, coef, type, production, nodeId);

		// only used with conventional generator.
		this.mttf = 630;
		this.mttr = 60;

		if(OS.startsWith("Windows") || OS.startsWith("Linux")){
			conf = ConfigFactory.parseFile(new File("../config/application.conf"));
		}else{
			conf = ConfigFactory.parseFile(new File("config/application.conf"));
		}

		convGeneratorConf = conf.getConfig("conventionalGenerator");
		maxProductionIncrease = convGeneratorConf.getDouble("maxProductionIncrease");
		dayAheadLimitMax = convGeneratorConf.getDouble("dayAheadLimitMax");
		dayAheadLimitMin = convGeneratorConf.getDouble("dayAheadLimitMin");
	}

	public ConventionalGenerator(double minProduction, double maxProduction, double coef, String type, double production) {
		super(minProduction, maxProduction, coef, type, production);

		// only used with conventional generator.
		this.mttf = 630;
		this.mttr = 60;
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
		if((productionIncrease < 0 && (Math.abs(productionIncrease) <= maxp*maxProductionIncrease))){
			production = this.production + Math.abs(productionIncrease);
		} else if(productionIncrease < 0)
			production = this.production + maxp*maxProductionIncrease;

		if (maxp*dayAheadLimitMax < production){
			//New production is higher than maximum allowed production.
			this.production = maxp*dayAheadLimitMax;
			return this.production;
		} else if(minp*dayAheadLimitMin > production){
			//New production is lower than minimum allowed production.
			this.production = minp*dayAheadLimitMin;
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

	public void takeIncreaseOffer(int i){
		this.offerIncreaseProduction.get(i).setAvailable(false);
	}

	public void takeDecreaseOffer(int i){
		this.offerIncreaseProduction.get(i).setAvailable(false);
	}

	@Override
	public int compareTo(ConventionalGenerator o) {

		if(this.getType().equals(((ConventionalGenerator)o).getType())){
			return 0; //Generator type are equal so we return 0.
		}

		/*
		 * Nuclear followed by Oil followed by Coal, Hydro is dead last.
		 */
		switch (this.getType()){
		case "N":
			return -1; //Generators are not equal but this is a nuclear one so its always higher.
		case "H":
			if(o.getType().equals("C")){
				return -1;
			}
			return 1;
		case "O":
			if(o.getType().equals("C")){
				return -1;
			} else
				return 1;
			default:
				return 1;
			}
	}
}
