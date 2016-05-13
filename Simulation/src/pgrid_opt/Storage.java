package pgrid_opt;

public class Storage extends Node {
	private double currentCharge;
	private double maximumCharge;
	private double minimumCharge;

	public Storage(double currentCharge, double maximumCharge, double minimumCharge, int nodeId) {
		super(nodeId);
		setCurrentCharge(currentCharge);
		setMaximumCharge(maximumCharge);
		setMinimumCharge(minimumCharge);
	}

	public Storage(double currentCharge, double maximumCharge, double minimumCharge) {
		setCurrentCharge(currentCharge);
		setMaximumCharge(maximumCharge);
		setMinimumCharge(minimumCharge);
	}

	public double getCurrentCharge() {
		return this.currentCharge;
	}

	public void setCurrentCharge(double avaliability) {
		this.currentCharge = avaliability;
	}

	public double getMaximumCharge() {
		return this.maximumCharge;
	}

	public void setMaximumCharge(double capacity) {
		this.maximumCharge = capacity;
	}

	public boolean isRenew() {
		return false;
	}

	public double getMinimumCharge() {
		return this.minimumCharge;
	}

	public void setMinimumCharge(double mincap) {
		this.minimumCharge = mincap;
	}
}
