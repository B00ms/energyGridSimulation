package pgrid_opt;

public class Generator extends Node {

	protected double maxp; //maximum production
	protected double minp; //minimum production
	private double lastmaxp,lastminp;
	private double coef; //multiplication coefficient, used to get real production
	private int reactivateAtTimeStep;
	protected double production;
	private GENERATOR_TYPE type; // Oil, coal or nuclear, wind, solar
	
	public enum GENERATOR_TYPE {OIL, COAL, NUCLEAR, HYDRO, WIND, SOLAR};

	public Generator(double minProduction, double maxProduction, double coef2, GENERATOR_TYPE type, double production, int nodeId) {
		super(nodeId);
		this.maxp = maxProduction;
		this.minp = minProduction;
		this.coef = coef2;
		this.type = type;
		this.production = production;
	}

	public Generator(double minProduction, double maxProduction, double coef2, GENERATOR_TYPE type, double production) {
		this.maxp = maxProduction;
		this.minp = minProduction;
		this.coef = coef2;
		this.type = type;
		this.production = production;
	}

	/**
	 *
	 * @return the production of this generator
	 */
	public double getProduction() {
		return production;
	}

	/**
	 * Set the production output of this generator, includes a check so production cannot be set higher than max production
	 * @param production
	 * @return returns the value at which the production was set
	 */
	public double setProduction(double production) {
		if (maxp < production){
			this.production = maxp;
			return maxp;
		} else if(minp > production){
			this.production = minp;
			return minp;
		} else {
			this.production = production;
			return production;
		}
	}

	/**
	 *
	 * @return Maximum power generation
	 */
	public double getMaxP() {
		return this.maxp;
	}

	/**
	 * Set the maximum power generation
	 * @param maxp
	 */
	public void setMaxP(double maxp) {
		this.maxp = maxp;
	}

	/**
	 *
	 * @return Minimum power generation
	 */
	public double getMinP() {
		return this.minp;
	}

	/**
	 * Set the minimum power generation
	 * @param minp
	 */
	public void setMinP(double minp) {
		this.minp = minp;
	}

	/**
	 * We don't use this method but we have to have it here because it's abstract in the super class.
	 */
	public boolean isRenew() {
		return false;
	}

	/**
	 *
	 * @return the multiplication coefficient, which is used to get the actual MWh of this generator
	 */
	public double getCoef() {
		return this.coef;
	}

	/**
	 * Set the multiplication coefficient.
	 * @param coef
	 */
	public void setCoef(double coef) {
		this.coef = coef;
	}

	/**
	 * @return the time step at which this generator must come back online
	 */
	public int getReactivateAtTimeStep() {
		return reactivateAtTimeStep;
	}

	/**
	 *
	 * @return The type of the generator (O = oil, C = Coal, N = Nuclear, H = Hydroeletric, W = Wind, S = Solar)
	 */
	public GENERATOR_TYPE getType() {
		return this.type;
	}

	/**
	 * Set the type of this generator.
	 * @param type
	 */
	public void setType(GENERATOR_TYPE type) {
		this.type = type;
	}

	/**
	 *
	 * @param currentTime sets the time step at which this generator must be back online
	 */
	public void setReactivateAtTimeStep(int currentTime) {
		this.reactivateAtTimeStep = reactivateAtTimeStep;
	}

	public double getLastmaxp() {
		return lastmaxp;
	}

	public void setLastmaxp(float lastmaxp) {
		this.lastmaxp = lastmaxp;
	}

	public double getLastminp() {
		return lastminp;
	}

	public void setLastminp(float lastminp) {
		this.lastminp = lastminp;
	}
}
