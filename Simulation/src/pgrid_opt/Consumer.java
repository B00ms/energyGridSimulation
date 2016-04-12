package pgrid_opt;

public class Consumer extends Node {
	private float load;

	public Consumer(float load) {
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
