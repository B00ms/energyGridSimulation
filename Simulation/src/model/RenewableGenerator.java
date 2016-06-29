package model;

import java.io.Serializable;

/**
 * Renewable generator
 */
public class RenewableGenerator extends Generator implements Serializable {
	
	public RenewableGenerator(double max, double min, double cost, GENERATOR_TYPE type, int nodeId) {
		super(min, max, cost, type, (max / 2.0F), nodeId);
	}

	public boolean isRenew() {
		return true;
	}
}

