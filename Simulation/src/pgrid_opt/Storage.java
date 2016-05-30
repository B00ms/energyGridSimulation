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
		setCurrentCharge(currentCharge);
		setMaximumCharge(maximumCharge);
		setMinimumCharge(minimumCharge);
	}

	public double discharge(){
		double tempCurrentCharge = currentCharge * dischargeEfficiency;
		currentCharge = tempCurrentCharge;
		return currentCharge;
	}

	public double getCurrentCharge() {
		return currentCharge;
	}

	public double setCurrentCharge(double charge) {
		if (charge >= maximumCharge)
			currentCharge = maximumCharge * chargeEfficiency;
		else
			currentCharge = charge * chargeEfficiency;

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
