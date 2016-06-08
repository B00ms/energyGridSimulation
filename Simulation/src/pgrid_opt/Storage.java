package pgrid_opt;

import java.io.File;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class Storage extends Node {
	private double currentCharge;
	private double maximumCharge;
	private double minimumCharge;
	private Config conf;
	private double chargeEfficiency;
	private double dischargeEfficiency;
	private double flowFromStorage = 0;

	public Storage(double currentCharge, double maximumCharge, double minimumCharge, int nodeId) {
		super(nodeId);
		this.currentCharge = currentCharge;
		this.maximumCharge = maximumCharge;
		this.minimumCharge = minimumCharge;

		if(System.getProperty("os.name").startsWith("Windows") || System.getProperty("os.name").startsWith("Linux")){
			conf = ConfigFactory.parseFile(new File("../config/application.conf"));
		}else{
			conf = ConfigFactory.parseFile(new File("config/application.conf"));
		}

		chargeEfficiency = conf.getConfig("Storage").getDouble("chargeEfficiencyOfStorage");
		dischargeEfficiency = conf.getConfig("Storage").getDouble("dischargEfficiencyOfStorage");
	}

	public Storage(double currentCharge, double maximumCharge, double minimumCharge) {
		charge(currentCharge);
		setMaximumCharge(maximumCharge);
		setMinimumCharge(minimumCharge);
	}

	public double getFlowFromStorage(){
		return flowFromStorage;
	}

	public double getCurrentCharge() {
		return currentCharge;
	}

	/**
	 * Charge the storage using the given charge
	 * @param charge
	 * @return
	 */
	public double charge(double charge) {
		double tempCurrentCharge;
		if (charge >= maximumCharge)
			tempCurrentCharge = maximumCharge * chargeEfficiency;
		else
			tempCurrentCharge = charge * chargeEfficiency;

		flowFromStorage = charge*-1;
		//flowFromStorage = 0;
		currentCharge = tempCurrentCharge;
		return currentCharge;
	}

	public double discharge(){
		double tempCurrentCharge = currentCharge * dischargeEfficiency;
		flowFromStorage = currentCharge - tempCurrentCharge;
		//flowFromStorage = 0;
		currentCharge = tempCurrentCharge;
		return currentCharge;
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
