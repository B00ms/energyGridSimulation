package pgrid_opt;

public class Generator extends Node {
	private float maxp;

	private float minp;

	private float coef;
	private String type;

	public Generator(float min, float max, float coef, String type) {
		this.maxp = max;
		this.minp = min;
		this.coef = coef;
		setType(type);
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
	 * @return The type of the generator (T = thermal, H = Hydroeletric, W = Wind, S = Solar)
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
}
