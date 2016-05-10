package pgrid_opt;

public class Consumer extends Node {
	private double load;

	public Consumer(double load, int nodeId) {
		super(nodeId);
		this.load = load;
	}

	public Consumer(double load) {
		this.load = load;
	}

	public double getLoad() {
		return this.load;
	}

	public void setLoad(double d) {
		this.load = d;
	}

	public boolean isRenew() {
		return false;
	}
}