package graph;

import java.io.Serializable;

public abstract class Node implements Serializable {
	public abstract boolean isRenew();

	private int nodeId;

	public Node(int nodeId){
		this.nodeId = nodeId;
	}

	public Node(){}

	public int getNodeId() {
		return nodeId;
	}
}
