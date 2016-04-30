package pgrid_opt;

public class Generator extends Node {

	private float maxp; //maximum production
	private float minp; //minimum production
	private float lastmaxp,lastminp;
	private float coef; //multiplication coefficient, used to get real production
	private String type; // Oil, coal or nuclear, wind, solar
	private int reactivateAtTimeStep;
	private double production;

	public Generator(float min, float max, float coef, String type, double production) {
		this.maxp = max;
		this.minp = min;
		this.coef = coef;
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
			return production - maxp;
		} else {
			this.production = production;
			return production;
		}
	}

	/**
	 *
	 * @return Maximum power generation
	 */
	public float getMaxP() {
		return this.maxp;
	}

	/**
	 * Set the maximum power generation
	 * @param maxp
	 */
	public void setMaxP(float maxp) {
		this.maxp = maxp;
	}

	/**
	 *
	 * @return Minimum power generation
	 */
	public float getMinP() {
		return this.minp;
	}

	/**
	 * Set the minimum power generation
	 * @param minp
	 */
	public void setMinP(float minp) {
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
	public float getCoef() {
		return this.coef;
	}

	/**
	 * Set the multiplication coefficient.
	 * @param coef
	 */
	public void setCoef(float coef) {
		this.coef = coef;
	}

	/**
	 *
	 * @return The type of the generator (O = oil, C = Coal, N = Nuclear, H = Hydroeletric, W = Wind, S = Solar)
	 */
	public String getType() {
		return this.type;
	}

	/**
	 * Set the type of this generator.
	 * @param type
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * @return the time step at which this generator must come back online
	 */
	public int getReactivateAtTimeStep() {
		return reactivateAtTimeStep;
	}

	/**
	 *
	 * @param currentTime sets the time step at which this generator must be back online
	 */
	public void setReactivateAtTimeStep(int currentTime) {
		this.reactivateAtTimeStep = reactivateAtTimeStep;
	}

	public float getLastmaxp() {
		return lastmaxp;
	}

	public void setLastmaxp(float lastmaxp) {
		this.lastmaxp = lastmaxp;
	}

	public float getLastminp() {
		return lastminp;
	}

	public void setLastminp(float lastminp) {
		this.lastminp = lastminp;
	}
}
