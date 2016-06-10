package pgrid_opt;

/**
 * Renewable generator
 */
public class RewGenerator extends Generator {
	
	public RewGenerator(double max, double min, double cost, String type, int nodeId) {
		super(min, max, cost, type, (max / 2.0F), nodeId);
	}

	public RewGenerator(double max, double min, double cost, String type) {
		super(min, max, cost, type, (max / 2.0F));
	}

	public boolean isRenew() {
		return true;
	}
}

