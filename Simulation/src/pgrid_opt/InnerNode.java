package pgrid_opt;

public class InnerNode extends Node{

	public InnerNode(int nodeId) {
		super(nodeId);
	}

	@Override
	public boolean isRenew() {
		return false;
	}

}
