package model;

import config.ConfigCollection;
import config.ConfigCollection.CONFIGURATION_TYPE;
import graph.Node;

import java.io.Serializable;

public class Storage extends Node {
	private double currentSoC;
	private double maximumSoC;
	private double minimumSoC;
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
		this.currentSoC = currentCharge;
		this.maximumSoC = maximumCharge;
		this.minimumSoC = minimumCharge;
		this.chMax = chMax;
		flowLimit = 0;
		status = StorageStatus.NEUTRAL;
		config = new ConfigCollection();
		chargeEfficiency = config.getConfigDoubleValue(CONFIGURATION_TYPE.STORAGE, "chargeEfficiencyOfStorage");
		dischargeEfficiency = config.getConfigDoubleValue(CONFIGURATION_TYPE.STORAGE, "dischargEfficiencyOfStorage");
	}

	public Storage(double currentCharge, double maximumCharge, double minimumCharge, int nodeId, int chMax, double chargeEfficiency, double dischargeEfficiency ) {
		super(nodeId);
		this.currentSoC = currentCharge;
		this.maximumSoC = maximumCharge;
		this.minimumSoC = minimumCharge;
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

	public double getCurrentSoC() {
		return currentSoC;
	}

	public void setCurrentSoC(double SoC){
		this.currentSoC = SoC;
	}

	/**
	 * Charge the storage using the given charge
	 * @param charge The amount of energy the grid wants to get rid of.
	 * @return the amount of flow on the line when charging.
	 */
	public double charge(double flowComingIn) {
		status = StorageStatus.CHARGING;
		double newSoC = currentSoC + (flowComingIn * chargeEfficiency);
		flowLimit = (chMax / chargeEfficiency);

		if (newSoC > maximumSoC){
			newSoC = maximumSoC;
			flowComingIn = maximumSoC * chargeEfficiency;
		}

		if(flowComingIn > flowLimit){
			newSoC = currentSoC + flowLimit;
			flowComingIn = flowLimit;
		}
		if(newSoC <= maximumSoC && flowComingIn <= flowLimit){
			currentSoC = newSoC;
			flowStorage = flowComingIn * -1; //Make flow negative because the edge goes Storage->innnerNode.
		}
		System.out.print("flow storage: " + flowStorage);
		System.out.println("SoC storage: " + currentSoC);
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
		double newSoC = currentSoC - dischargedEnergy;
		double tempCurrentCharge = newSoC;
		currentSoC = newSoC;
		flowStorage = charge;

		if(newSoC < minimumSoC){
			double minSoC = minimumSoC;
			outgoingFlow = minSoC * dischargeEfficiency;
			tempCurrentCharge  = minSoC;
			flowStorage = outgoingFlow;
		}

		if(outgoingFlow > flowLimit){
			double minSoC = currentSoC - flowLimit;
			outgoingFlow = flowLimit;
			tempCurrentCharge = minSoC;
			flowStorage = outgoingFlow;
		}

		currentSoC = tempCurrentCharge;
		return flowStorage;
	}

	public double getMaximumCharge() {
		return maximumSoC;
	}

	public void setMaximumCharge(double capacity) {
		this.maximumSoC = capacity;
	}

	public boolean isRenew() {
		return false;
	}

	public double getMinimumCharge() {
		return minimumSoC;
	}

	public void setMinimumCharge(double mincap) {
		minimumSoC = mincap;
	}

	public double getChargeEfficiency() {
		return chargeEfficiency;
	}

	public double getDischargeEfficiency() {
		return dischargeEfficiency;
	}
}
