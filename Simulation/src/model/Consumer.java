package model;

import graph.Node;


public class Consumer extends Node {
	private double load;
	private double loadError;
	private double flow = 0;

	public Consumer(double load, int nodeId) {
		super(nodeId);
		this.load = load;
		loadError = 0;
	}

	public Consumer(double load) {
		this.load = load;
	}

	public double getLoad() {
		return this.load;
	}

	public void setLoad(double d) {
		this.load = d;
	}

	/**
	 * Add the parameter loadError to the load error of the Consumer
	 * @param loadError
	 */
	public void setLoadError(double loadError)
	{
		this.loadError = loadError;
	}

	public double getLoadError(){
		return loadError;
	}

	public boolean isRenew() {
		return false;
	}

	public double getFlow(){
		return flow;
	}

	/**
	 * Please don't use this unless you're setting flow equal to the flow set by the linear program.
	 * This is so stupid.
	 * @param flow
	 */
	public void setFlow(double flow){
		this.flow = flow;
	}
}