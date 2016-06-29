package graph;

import java.io.Serializable;

public class InnerNode extends Node implements Serializable{

	public InnerNode(int nodeId) {
		super(nodeId);
	}

	@Override
	public boolean isRenew() {
		return false;
	}

}
