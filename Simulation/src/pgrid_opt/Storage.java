package pgrid_opt;

public class Storage extends Node {
	private double avaliability;
	private double capacity;
	private double mincap;

	public Storage(double currentCharge, double maximumCharge, double minimumCharge, int nodeId) {
		super(nodeId);
		setAvaliability(currentCharge);
		setCapacity(maximumCharge);
		setMincap(minimumCharge);
	}

	public Storage(double currentCharge, double maximumCharge, double minimumCharge) {
		setAvaliability(currentCharge);
		setCapacity(maximumCharge);
		setMincap(minimumCharge);
	}

	public double getAvaliability() {
		return this.avaliability;
	}

	public void setAvaliability(double avaliability) {
		this.avaliability = avaliability;
	}

	public double getCapacity() {
		return this.capacity;
	}

	public void setCapacity(double capacity) {
		this.capacity = capacity;
	}

	public boolean isRenew() {
		return false;
	}

	public double getMincap() {
		return this.mincap;
	}

	public void setMincap(double mincap) {
		this.mincap = mincap;
	}
}
