package pgrid_opt;

/**
 * Renewable generator
 */
public class RewGenerator extends Generator {
	private float production;

	public RewGenerator(float max, float min, float cost, String type) {
		super(min, max, cost, type);
		this.production = (max / 2.0F);
	}

	public boolean isRenew() {
		return true;
	}

	public float getProduction() {
		return this.production;
	}

	public void setProduction(float production) {
		this.production = production;
	}
}
