package pgrid_opt;

public class consumer extends node {
	private float load;

	public consumer(float load) {
		this.load = load;
	}

	public float getLoad() {
		return this.load;
	}

	public void setLoad(float load) {
		this.load = load;
	}

	public boolean isRenew() {
		return false;
	}
}
