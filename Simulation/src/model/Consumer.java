package model;

import graph.Node;

public class Consumer extends Node {
	private double load;
	private double loadError;

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
}