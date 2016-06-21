package graph;

public abstract class Node {
	public abstract boolean isRenew();

	private int nodeId;

	public Node(int nodeId){
		this.nodeId = nodeId;
	}

	public Node(){
	}

	public int getNodeId() {
		return nodeId;
	}
}
