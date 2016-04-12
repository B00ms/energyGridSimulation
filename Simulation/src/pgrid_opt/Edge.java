package pgrid_opt;

public class Edge {
	private float weight;

	private float capacity;

	private float flow;

	public float getWeight() {
		return this.weight;
	}

	public void setWeight(float weight) {
		this.weight = weight;
	}

	public float getCapacity() {
		return this.capacity;
	}

	public void setCapacity(float capacity) {
		this.capacity = capacity;
	}

	public float getFlow() {
		return this.flow;
	}

	public void setFlow(float flow) {
		this.flow = flow;
	}
}
