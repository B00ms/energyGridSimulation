package pgrid_opt;

public class Storage extends Node {
	private float avaliability;
	private float capacity;
	private float mincap;

	public Storage(float aval, float cap, float min) {
		setAvaliability(aval);
		setCapacity(cap);
		setMincap(min);
	}

	public float getAvaliability() {
		return this.avaliability;
	}

	public void setAvaliability(float avaliability) {
		this.avaliability = avaliability;
	}

	public float getCapacity() {
		return this.capacity;
	}

	public void setCapacity(float capacity) {
		this.capacity = capacity;
	}

	public boolean isRenew() {
		return false;
	}

	public float getMincap() {
		return this.mincap;
	}

	public void setMincap(float mincap) {
		this.mincap = mincap;
	}
}
