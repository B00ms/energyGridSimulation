package model;

/**
 * Renewable generator
 */
public class RewGenerator extends Generator {
	
	public RewGenerator(double max, double min, double cost, GENERATOR_TYPE type, int nodeId) {
		super(min, max, cost, type, (max / 2.0F), nodeId);
	}

	public boolean isRenew() {
		return true;
	}
}

