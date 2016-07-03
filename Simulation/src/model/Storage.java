package model;

import config.ConfigCollection;
import config.ConfigCollection.CONFIGURATION_TYPE;
import graph.Node;

import java.io.Serializable;

public class Storage extends Node {
	private double currentCharge;
	private double maximumCharge;
	private double minimumCharge;
	private double chargeEfficiency;
	private double dischargeEfficiency;
	private double flowStorage = 0;
	private double flowLimit = 0;
	private int chMax;


	private ConfigCollection config;
	public enum StorageStatus {CHARGING, DISCHARGING, NEUTRAL};
	private StorageStatus status;


	public Storage(double currentCharge, double maximumCharge, double minimumCharge, int nodeId, int chMax) {
		super(nodeId);
		this.currentCharge = currentCharge;
		this.maximumCharge = maximumCharge;
		this.minimumCharge = minimumCharge;
		this.chMax = chMax;
		flowLimit = 0;
		status = StorageStatus.NEUTRAL;
		config = new ConfigCollection();
		chargeEfficiency = config.getConfigDoubleValue(CONFIGURATION_TYPE.STORAGE, "chargeEfficiencyOfStorage");
		dischargeEfficiency = config.getConfigDoubleValue(CONFIGURATION_TYPE.STORAGE, "dischargEfficiencyOfStorage");
	}

	public Storage(double currentCharge, double maximumCharge, double minimumCharge, int nodeId, int chMax, double chargeEfficiency, double dischargeEfficiency ) {
		super(nodeId);
		this.currentCharge = currentCharge;
		this.maximumCharge = maximumCharge;
		this.minimumCharge = minimumCharge;
		this.chMax = chMax;
		flowLimit = 0;
		status = StorageStatus.NEUTRAL;

		this.chargeEfficiency = chargeEfficiency;
		this.dischargeEfficiency = dischargeEfficiency;
	}

	public Storage(double currentCharge, double maximumCharge, double minimumCharge) {
		charge(currentCharge);
		setMaximumCharge(maximumCharge);
		setMinimumCharge(minimumCharge);
	}

	public void setStatus(StorageStatus status){
		this.status = status;
	}

	public StorageStatus getStatus(){
		return status;
	}

	public double getFlowLimit() {
		return flowLimit;
	}

	public void setFlowLimit(double flowLimit) {
		this.flowLimit = flowLimit;
	}

	public double getFlow(){
		return flowStorage;
	}

	public int getChMax() {
		return chMax;
	}

	/**
	 * Please don't use this unless you're setting flow to equal flow set by the flow simulation.
	 * @param flow
	 */
	public void setFlow(double flow){
		flowStorage = flow;
	}

	public double getCurrentCharge() {
		return currentCharge;
	}

	/**
	 * Charge the storage using the given charge
	 * @param charge The amount of energy the grid wants to get rid of.
	 * @return the amount of flow on the line when charging.
	 */
	public double charge(double flowComingIn) {
		status = StorageStatus.CHARGING;
		double newSoC = currentCharge + (flowComingIn * chargeEfficiency);
		flowStorage = flowComingIn;
		flowLimit = (chMax / chargeEfficiency);

		if (newSoC > maximumCharge){
			newSoC = maximumCharge;
			flowComingIn = maximumCharge * chargeEfficiency;
			currentCharge = newSoC;
			flowStorage = flowComingIn;
		}

		if(flowComingIn > flowLimit){
			newSoC = currentCharge + flowLimit;
			currentCharge = newSoC;
			flowStorage = flowLimit;
		}
		flowStorage = flowStorage * -1; //Make flow negative because the edge goes Storage->innnerNode.
		return flowStorage;
	}

	/**
	 *
	 * @param charge the amount of energy the grid needs.
	 * @return
	 */
	public double discharge(double charge){
		status = StorageStatus.DISCHARGING;
		flowLimit = (chMax * dischargeEfficiency);
		double dischargedEnergy = charge/dischargeEfficiency; //Amount of discharge to put 'charge' amount of energy back into the grid
		double outgoingFlow = dischargedEnergy * dischargeEfficiency; //Actual flow we put into the network, should equal charge
		double newSoC = currentCharge - dischargedEnergy;
		double tempCurrentCharge = newSoC;
		currentCharge = newSoC;
		flowStorage = charge;

		if(newSoC < minimumCharge){
			double minSoC = minimumCharge;
			outgoingFlow = minSoC * dischargeEfficiency;
			tempCurrentCharge  = minSoC;
			flowStorage = outgoingFlow;
		}

		if(outgoingFlow > flowLimit){
			double minSoC = currentCharge - flowLimit;
			outgoingFlow = flowLimit;
			tempCurrentCharge = minSoC;
			flowStorage = outgoingFlow;
		}

		currentCharge = tempCurrentCharge;
		return flowStorage;
	}

	public double getMaximumCharge() {
		return maximumCharge;
	}

	public void setMaximumCharge(double capacity) {
		this.maximumCharge = capacity;
	}

	public boolean isRenew() {
		return false;
	}

	public double getMinimumCharge() {
		return minimumCharge;
	}

	public void setMinimumCharge(double mincap) {
		minimumCharge = mincap;
	}

	public double getChargeEfficiency() {
		return chargeEfficiency;
	}

	public double getDischargeEfficiency() {
		return dischargeEfficiency;
	}
}
