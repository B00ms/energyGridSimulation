package pgrid_opt;

public class Storage extends Node {
	private double currentCharge;
	private double maximumCharge;
	private double minimumCharge;

	public Storage(double currentCharge, double maximumCharge, double minimumCharge, int nodeId) {
		super(nodeId);
		this.currentCharge = currentCharge;
		this.maximumCharge = maximumCharge;
		this.minimumCharge = minimumCharge;
	}

	public Storage(double currentCharge, double maximumCharge, double minimumCharge) {
		setCurrentCharge(currentCharge);
		setMaximumCharge(maximumCharge);
		setMinimumCharge(minimumCharge);
	}

	public double getCurrentCharge() {
		return currentCharge;
	}

	public double setCurrentCharge(double charge) {
		if (charge >= maximumCharge)
			currentCharge = maximumCharge;
		else
			currentCharge = charge;

		return currentCharge;
	}

	public double getMaximumCharge() {
		return maximumCharge;
	}

	public void setMaximumCharge(double capacity) {
		this.maximumCharge = capacity;
	}

	public boolean isRenew() {
		return false;
	}

	public double getMinimumCharge() {
		return minimumCharge;
	}

	public void setMinimumCharge(double mincap) {
		minimumCharge = mincap;
	}
}
