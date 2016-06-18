package pgrid_opt;

import java.io.File;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ConfigCollection {

	private static final String OS = System.getProperty("os.name");
	private Config conf;
	private Config generalConfig;
	private Config monteCarlo;
	private Config conventionalGenerator;
	private Config loadCurves;
	private Config production;
	private Config windGenerator;
	private Config solarGenerator;
	private Config storage;
	private Config glpsol;

	private Config oilOffer;
	private Config coalOffer;
	private Config nuclearOffer;
	private Config hydroOilOffer;

	//Do we want ../directory or ./directory?
	private boolean doubleDot;

	public enum CONFIGURATION_TYPE { GENERAL, MONTE_CARLO, CONVENTIONAL_GENERATOR,
			LOAD_CURVES, OIL_OFFER, COAL_OFFER, NUCLEAR_OFFER, HYRDO_OFFER, PRODUCTION, WIND_GENERATOR, SOLAR_GENERATOR, STORAGE, GLPSOL};


	public ConfigCollection(){
		if (OS.startsWith("Windows") || OS.startsWith("Linux")) {
			conf = ConfigFactory.parseFile(new File("../config/application.conf"));
			doubleDot = true;
		} else {
			conf = ConfigFactory.parseFile(new File("config/application.conf"));
			doubleDot = false;
		}

		generalConfig 			= conf.getConfig("general");
		monteCarlo 				= conf.getConfig("monte-carlo");
		conventionalGenerator 	= conf.getConfig("conventionalGenerator");
		loadCurves 				= conf.getConfig("conventionalGenerator").getConfig("load-curves");
		
		oilOffer 				= conf.getConfig("conventionalGenerator").getConfig("oilOffer");
		coalOffer   			= conf.getConfig("conventionalGenerator").getConfig("coalOffer");
		nuclearOffer			= conf.getConfig("conventionalGenerator").getConfig("nuclearOffer");
		hydroOilOffer			= conf.getConfig("conventionalGenerator").getConfig("hydroOffer");

		production				= conf.getConfig("conventionalGenerator").getConfig("production");
		windGenerator 			= conf.getConfig("windGenerator");
		solarGenerator 			= conf.getConfig("solarGenerator");
		storage 				= conf.getConfig("Storage");
		glpsol 					= conf.getConfig("glpsol-config");
		
	}

	public String getOS(){
		return OS;
	}

	public String getConfigStringValue(CONFIGURATION_TYPE configType, String configurationKeyWord ){
		String confValue = null;
		switch (configType){
		case GENERAL:
			if(doubleDot == false && (
					configurationKeyWord.equalsIgnoreCase("model-file") ||
					configurationKeyWord.equalsIgnoreCase("input-file") ||
//					configurationKeyWord.equalsIgnoreCase("output-folder")||
					configurationKeyWord.equalsIgnoreCase("graphstate-folder"))){
				confValue = generalConfig.getString(configurationKeyWord);
				confValue = confValue.substring(1, confValue.length());
			}else{
				confValue = generalConfig.getString(configurationKeyWord);
			}
			break;
		case MONTE_CARLO:
			confValue = monteCarlo.getString(configurationKeyWord);
			break;
		case CONVENTIONAL_GENERATOR:
			confValue = conventionalGenerator.getString(configurationKeyWord);
			break;
		case LOAD_CURVES:
			confValue = loadCurves.getString(configurationKeyWord);
			break;
		case OIL_OFFER:
			confValue = oilOffer.getString(configurationKeyWord);
			break;
		case COAL_OFFER:
			confValue = coalOffer.getString(configurationKeyWord);
			break;
		case NUCLEAR_OFFER:
			confValue = nuclearOffer.getString(configurationKeyWord);
			break;
		case HYRDO_OFFER:
			confValue = hydroOilOffer.getString(configurationKeyWord);
			break;						
		case PRODUCTION:
			confValue = production.getString(configurationKeyWord);
			break;
		case WIND_GENERATOR:
			confValue = windGenerator.getString(configurationKeyWord);
			break;
		case SOLAR_GENERATOR:
			confValue = solarGenerator.getString(configurationKeyWord);
			break;
		case STORAGE:
			confValue = storage.getString(configurationKeyWord);
			break;
		case GLPSOL:
			confValue = glpsol.getString(configurationKeyWord);
			break;
		}

		return confValue;

	}

	public Integer getConfigIntValue(CONFIGURATION_TYPE configType, String configurationKeyWord ){
		Integer confValue = null;
		switch (configType){
		case GENERAL:
			confValue = generalConfig.getInt(configurationKeyWord);
			break;
		case MONTE_CARLO:
			confValue = monteCarlo.getInt(configurationKeyWord);
			break;
		case CONVENTIONAL_GENERATOR:
			confValue = conventionalGenerator.getInt(configurationKeyWord);
			break;
		case LOAD_CURVES:
			confValue = loadCurves.getInt(configurationKeyWord);
			break;
		case OIL_OFFER:
			confValue = oilOffer.getInt(configurationKeyWord);
			break;
		case COAL_OFFER:
			confValue = coalOffer.getInt(configurationKeyWord);
			break;
		case NUCLEAR_OFFER:
			confValue = nuclearOffer.getInt(configurationKeyWord);
			break;
		case HYRDO_OFFER:
			confValue = hydroOilOffer.getInt(configurationKeyWord);
			break;		
		case PRODUCTION:
			confValue = production.getInt(configurationKeyWord);
			break;
		case WIND_GENERATOR:
			confValue = windGenerator.getInt(configurationKeyWord);
			break;
		case SOLAR_GENERATOR:
			confValue = solarGenerator.getInt(configurationKeyWord);
			break;
		case STORAGE:
			confValue = storage.getInt(configurationKeyWord);
			break;
		case GLPSOL:
			confValue = glpsol.getInt(configurationKeyWord);
			break;
		}

		return confValue;

	}

	public Double getConfigDoubleValue(CONFIGURATION_TYPE configType, String configurationKeyWord ){
		Double confValue = null;
		switch (configType){
		case GENERAL:
			confValue = generalConfig.getDouble(configurationKeyWord);
			break;
		case MONTE_CARLO:
			confValue = monteCarlo.getDouble(configurationKeyWord);
			break;
		case CONVENTIONAL_GENERATOR:
			confValue = conventionalGenerator.getDouble(configurationKeyWord);
			break;
		case LOAD_CURVES:
			confValue = loadCurves.getDouble(configurationKeyWord);
			break;
		case OIL_OFFER:
			confValue = oilOffer.getDouble(configurationKeyWord);
			break;
		case COAL_OFFER:
			confValue = coalOffer.getDouble(configurationKeyWord);
			break;
		case NUCLEAR_OFFER:
			confValue = nuclearOffer.getDouble(configurationKeyWord);
			break;
		case HYRDO_OFFER:
			confValue = hydroOilOffer.getDouble(configurationKeyWord);
			break;	
		case PRODUCTION:
			confValue = production.getDouble(configurationKeyWord);
			break;
		case WIND_GENERATOR:
			confValue = windGenerator.getDouble(configurationKeyWord);
			break;
		case SOLAR_GENERATOR:
			confValue = solarGenerator.getDouble(configurationKeyWord);
			break;
		case STORAGE:
			confValue = storage.getDouble(configurationKeyWord);
			break;
		case GLPSOL:
			confValue = glpsol.getDouble(configurationKeyWord);
			break;
		}

		return confValue;

	}
}
