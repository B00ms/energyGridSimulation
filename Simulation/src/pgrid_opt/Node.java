package pgrid_opt;

public abstract class Node {
	public abstract boolean isRenew();
	protected int nodeId;

	public Node(int nodeId){
		this.nodeId = nodeId;
	}

	public Node(){
	}
}
