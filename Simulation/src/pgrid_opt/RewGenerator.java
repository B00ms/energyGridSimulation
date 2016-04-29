package pgrid_opt;

/**
 * Renewable generator
 */
public class RewGenerator extends Generator {
	private double production;

	public RewGenerator(float max, float min, float cost, String type) {
		super(min, max, cost, type);
		this.production = (max / 2.0F);
	}

	public boolean isRenew() {
		return true;
	}

	public double getProduction() {
		return this.production;
	}

	public void setProduction(double production) {
		this.production = production;
	}
}
