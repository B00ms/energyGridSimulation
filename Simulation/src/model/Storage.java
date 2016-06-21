package model;

import pgrid_opt.ConfigCollection;
import pgrid_opt.ConfigCollection.CONFIGURATION_TYPE;
import graph.Node;

public class Storage extends Node {
	private double currentCharge;
	private double maximumCharge;
	private double minimumCharge;
	private double chargeEfficiency;
	private double dischargeEfficiency;
	private double flowStorage = 0;
	private double flowLimit = 0;
	private ConfigCollection config = new ConfigCollection();
	public enum StorageStatus {CHARGING, DISCHARGING, NEUTRAL};
	private StorageStatus status;


	public Storage(double currentCharge, double maximumCharge, double minimumCharge, int nodeId) {
		super(nodeId);
		this.currentCharge = currentCharge;
		this.maximumCharge = maximumCharge;
		this.minimumCharge = minimumCharge;
		flowLimit = (maximumCharge - currentCharge) / config.getConfigDoubleValue(CONFIGURATION_TYPE.GENERAL, "durationOfEachStep");
		status = StorageStatus.NEUTRAL;

		chargeEfficiency = config.getConfigDoubleValue(CONFIGURATION_TYPE.STORAGE, "chargeEfficiencyOfStorage");
		dischargeEfficiency = config.getConfigDoubleValue(CONFIGURATION_TYPE.STORAGE, "dischargEfficiencyOfStorage");
	}

	public Storage(double currentCharge, double maximumCharge, double minimumCharge) {
		charge(currentCharge);
		setMaximumCharge(maximumCharge);
		setMinimumCharge(minimumCharge);
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

	public double getCurrentCharge() {
		return currentCharge;
	}

	/**
	 * Charge the storage using the given charge
	 * @param charge The amount of energy the grid wants to get rid of.
	 * @return
	 */
	public double charge(double charge) {
		status = StorageStatus.CHARGING;
		double newSoC = currentCharge + (charge * chargeEfficiency);
		double flowComingIn = charge;
		double tempCurrentcharge;

		if (newSoC > maximumCharge){
			newSoC = maximumCharge;
			flowComingIn = maximumCharge * chargeEfficiency;
			currentCharge = newSoC;
			flowStorage = flowComingIn;
		}

		if(flowComingIn > flowLimit){
			newSoC = currentCharge + (flowLimit * chargeEfficiency);
			currentCharge = newSoC;
			flowStorage = flowComingIn;
		}
		flowStorage = flowStorage * -1; //Make flow negative because the edge goes Storage->innnerNode.
		return flowComingIn;
	}

	/**
	 *
	 * @param charge the amount of energy the grid needs.
	 * @return
	 */
	public double discharge(double charge){
		status = StorageStatus.DISCHARGING;
		double dischargedEnergy = charge/dischargeEfficiency; //Amount of discharge to put 'charge' amount of energy back into the grid
		double outgoingFlow = dischargedEnergy * dischargeEfficiency; //Actual flow we put into the network, should equal charge
		double newSoC = currentCharge - dischargedEnergy;
		double tempCurrentCharge = newSoC;

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
		return outgoingFlow;
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