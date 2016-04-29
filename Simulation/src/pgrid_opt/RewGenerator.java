package pgrid_opt;

/**
 * Renewable generator
 */
public class RewGenerator extends Generator {

	public RewGenerator(float max, float min, float cost, String type) {
		super(min, max, cost, type, (max / 2.0F));
	}

	public boolean isRenew() {
		return true;
	}
}

