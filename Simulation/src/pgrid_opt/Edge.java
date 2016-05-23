package pgrid_opt;

public class Edge implements Comparable<Edge> {
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

	public void setCapacity(double capacity) {
		this.capacity = capacity;
	}

	public double getFlow() {
		return this.flow;
	}

	public void setFlow(double flow) {
		this.flow = flow;
	}

	public void setEndVertexes(int nodeOneId, int nodeTwoId){
		endVertexOne = nodeOneId;
		endVertexTwo = nodeTwoId;
	}

	public Integer[] getEndVertexes(){
		Integer[] endVertexes = new Integer[2];
		endVertexes[0] = endVertexOne;
		endVertexes[1] = endVertexTwo;
		return endVertexes;
	}

	@Override
	public int compareTo(Edge o) {
		if (o.getEndVertexes()[0] < endVertexOne)
			return 1;
		else if (o.getEndVertexes()[0] == endVertexOne)
			return 0;
		else
			return -1;
	}
}
