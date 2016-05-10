package pgrid_opt;

public class Edge {
	private double weight;
	private double capacity;
	private double flow;
	private int endVertexOne, endVertexTwo;

	public double getWeight() {
		return this.weight;
	}

	public void setWeight(double weight) {
		this.weight = weight;
	}

	public double getCapacity() {
		return this.capacity;
	}

	public void setCapacity(double capacity2) {
		this.capacity = capacity2;
	}

	public double getFlow() {
		return this.flow;
	}

	public void setFlow(double flow) {
		this.flow = flow;
	}

	public void setEndVertexes(int nodeOneId, int nodeTwoId){
		endVertexOne = nodeOneId;
		endVertexOne = nodeTwoId;
	}

	public Integer[] getEndVertexes(){
		Integer[] endVertexes = new Integer[2];
		endVertexes[0] = endVertexOne;
		endVertexes[1] = endVertexTwo;
		return endVertexes;
	}
}
