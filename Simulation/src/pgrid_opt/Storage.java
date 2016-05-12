package pgrid_opt;

public class Storage extends Node {
	private double avaliability;
	private double capacity;
	private double mincap;

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
		return this.avaliability;
	}

	public void setCurrentCharge(double avaliability) {
		this.avaliability = avaliability;
	}

	public double getMaximumCharge() {
		return this.capacity;
	}

	public void setMaximumCharge(double capacity) {
		this.capacity = capacity;
	}

	public boolean isRenew() {
		return false;
	}

	public double getMinimumCharge() {
		return this.mincap;
	}

	public void setMinimumCharge(double mincap) {
		this.mincap = mincap;
	}
}
