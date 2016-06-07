package pgrid_opt;

import com.typesafe.config.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import com.typesafe.config.ConfigFactory;

import net.e175.klaus.solarpositioning.AzimuthZenithAngle;
import net.e175.klaus.solarpositioning.DeltaT;
import net.e175.klaus.solarpositioning.Grena3;
import net.e175.klaus.solarpositioning.SPA;
import pgrid_opt.ConfigCollection.CONFIGURATION_TYPE;

public class Main {

	// Path to the summer load curve
	private static String OS = System.getProperty("os.name");
	//private static Config conf;
	private static double totalCurrentProduction = 0;
	private static double sumLoads = 0;
	private static ConfigCollection config = new ConfigCollection();

	public static void main(String[] args) {

		long starttime = System.nanoTime();
		float wcost = 0.0f; // wind cost
		float scost = 0.0f; // solar cost
		Graph[] timestepsGraph = null;
		Parser parser = new Parser();
		Graph graph;

		/*if (OS.startsWith("Windows") || OS.startsWith("Linux")) {
			conf = ConfigFactory.parseFile(new File("../config/application.conf"));
			graph = parser.parseData("../network.csv");
		} else {
			conf = ConfigFactory.parseFile(new File("config/application.conf"));
			graph = parser.parseData("./network.csv");
		}*/

		graph = parser.parseData(config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "input-file"));

		// load general config
		/*
		Config generalConf = conf.getConfig("general");
		String model = generalConf.getString("model-file"); // path to the model
		String dirpath = generalConf.getString("output-folder"); // path to the output
		String path = generalConf.getString("input-file"); // parse old input file
		*/
		String model = config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "model-file");
		String dirpath = config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "output-folder");
		String path = config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "input-file");

		// load glpsol config
		/*
		Config glpsolConf = conf.getConfig("glpsol-config");
		String outpath1 = glpsolConf.getString("outpath1");
		String outpath2 = glpsolConf.getString("outpath2");
		String solpath1 = glpsolConf.getString("solpath1");
		String solpath2 = glpsolConf.getString("solpath2");
		*/
		String outpath1 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "outpath1");
		String outpath2 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "outpath2");
		String solpath1 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "solpath1");
		String solpath2 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "solpath2");

		DataModelPrint mp = new DataModelPrint();
		Process proc = null;

		// load simulation limit
		/*int simLimit = generalConf.getInt("simulation-runs");*/
		int simLimit = config.getConfigIntValue(CONFIGURATION_TYPE.GENERAL, "simulation-runs");
		for (int numOfSim = 0; numOfSim < simLimit; numOfSim++) {
			System.out.println("Simulation: " + numOfSim);
			SimulationStateInitializer simulationState = new SimulationStateInitializer();
			/*timestepsGraph = new Graph[generalConf.getInt(("numberOfTimeSteps"))];*/
			timestepsGraph = new Graph[config.getConfigIntValue(CONFIGURATION_TYPE.GENERAL, "numberOfTimeSteps")];
			timestepsGraph = simulationState.creategraphs(graph, timestepsGraph);
			int i = 0;

			double load = 0;
			for (int q = 0; q < timestepsGraph[0].getNodeList().length - 1; q++) {
				if (timestepsGraph[0].getNodeList()[q] != null
						&& timestepsGraph[0].getNodeList()[q].getClass() == Consumer.class) {
					load += ((Consumer) timestepsGraph[0].getNodeList()[q]).getLoad();
				}
			}

			String solutionPath = dirpath + "simRes" + numOfSim + "";
			try {
				Files.createDirectories(Paths.get(solutionPath)); // create a new directory to safe the output in
			} catch (IOException e1) {
				e1.printStackTrace();
				System.exit(0);
			}
			Double[] exptectedLoadAndProduction = expectedLoadAndProduction(timestepsGraph);
			timestepsGraph = setRealLoad(timestepsGraph);
			while (i < timestepsGraph.length) {
				System.out.println("TimeStep: "+ i);

				timestepsGraph[i] = randomizeGridState(timestepsGraph[i], i);
				timestepsGraph[i] = checkGridEquilibrium(timestepsGraph[i], i);

				mp.printData(timestepsGraph[i], String.valueOf(dirpath) + outpath1 + i + outpath2, Integer.toString(i)); // This creates a new input file.

				try {
					StringBuffer output = new StringBuffer();
					String command = "" + String.valueOf(solpath1) + outpath1 + i + outpath2 + solpath2 + model;
					command = command + " --nopresol --output filename.out ";
					System.out.println(command);

					proc = Runtime.getRuntime().exec(command, null, new File(dirpath));
					proc.waitFor();

					BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream())); // Using the new input file, we apply the model to solve the cost function given the new state of the grid.
					String line = "";
					while ((line = reader.readLine()) != null) {
						output.append(String.valueOf(line) + "\n");
					}
					System.out.println(output);

					timestepsGraph[i] = timestepsGraph[i].setFlowFromOutputFile(timestepsGraph[i], i);
					timestepsGraph[i].printGraph(i, numOfSim);

					if (new File(dirpath + "/sol" + i + ".txt").exists())
						Files.move(Paths.get(dirpath + "/sol" + i + ".txt"),
								Paths.get(solutionPath + "/sol" + i + ".txt"), StandardCopyOption.REPLACE_EXISTING);

					if (new File(dirpath + "/filename.out").exists())
						Files.move(Paths.get(dirpath + "/filename.out"), Paths.get(solutionPath + "/filename.out"),
								StandardCopyOption.REPLACE_EXISTING);

					if (new File(dirpath + "/update.txt").exists())
						Files.copy(Paths.get(dirpath + "/update.txt"), Paths.get(solutionPath + "/update" + i + ".txt"),
								StandardCopyOption.REPLACE_EXISTING);


					if (graph.getNstorage() > 0) {
						timestepsGraph[i] = parser.parseUpdates(String.valueOf(dirpath) + "update.txt",
								timestepsGraph[i]); // Keeps track of the new
													// state for storages.
						if (i < 23)
							timestepsGraph[i + 1] = simulationState.updateStorages(timestepsGraph[i],
									timestepsGraph[i + 1]); // Apply the new state of the storage for the next time step.
					}
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
					System.exit(0);
				}
				++i;
			}

			if (graph.getNstorage() > 0) {
				mp.printStorageData(timestepsGraph, String.valueOf(dirpath) + "storage.txt");

				if (new File(dirpath + "/storage.txt").exists())
					try {
						Files.copy(Paths.get(dirpath + "/storage.txt"), Paths.get(solutionPath + "/storage.txt"),
								StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						e.printStackTrace();
					}
			}
		}

		long endtime = System.nanoTime();
		long duration = endtime - starttime;
		System.out.println("Time used:" + duration / 1000000 + " millisecond");
	}

	/**
	 * Calculates the expected load and production of an entire day
	 * @param graphs
	 * @return Array where [0] = expectedLoad and [1] = expectedProduction
	 */
	private static Double[] expectedLoadAndProduction(Graph[] graphs) {
		double expectedLoad = 0;
		double expectedProduction = 0;

		for(int hour=0; hour < 24; hour++){
			graphs[hour] = randomizeRenewableGenerator(graphs[hour], hour); //set renewable production.

			for(int i = 0; i < graphs[hour].getNodeList().length; i++){
				if(graphs[hour].getNodeList()[i].getClass() == Consumer.class){
					expectedLoad += ((Consumer)graphs[hour].getNodeList()[i]).getLoad();
				} else if (graphs[hour].getNodeList()[i].getClass() == ConventionalGenerator.class){
					//handle conven generators
				} else if (graphs[hour].getNodeList()[i].getClass() == RewGenerator.class) {
					expectedLoad -=  ((RewGenerator)graphs[hour].getNodeList()[i]).getProduction();
				}
			}
			graphs[hour] = checkGridEquilibrium(graphs[hour], hour);

			for (int i = 0; i < graphs[hour].getNodeList().length; i ++){
				if (graphs[hour].getNodeList()[i].getClass() == ConventionalGenerator.class){
					expectedProduction +=  ((ConventionalGenerator)graphs[hour].getNodeList()[i]).getProduction();
				}
			}
		}
		Double[] result = new Double[]{expectedLoad, expectedProduction};
		return result;
	}

	/**
	 * Calculates and sets the real load by taking into account monte carlo draws.
	 * @param timestepsGraph
	 * @return The graph where the real load has been set for each Consumer.
	 */
	private static Graph[] setRealLoad(Graph[] timestepsGraph) {
		MontoCarloHelper mcHelper = new MontoCarloHelper();
		for (int i = 0; i < timestepsGraph.length; i++) {
			double totalLoad = 0;
			for (int n = 0; n < timestepsGraph[i].getNodeList().length; n++) {
				if (timestepsGraph[i].getNodeList()[n] != null
						&& timestepsGraph[i].getNodeList()[n].getClass() == Consumer.class) {
					double mcDraw = mcHelper.getRandomNormDist();
					double previousError = 0;

					// Calculate and set the load error of a single consumer.
					double error = (((Consumer) timestepsGraph[i].getNodeList()[n]).getLoad() * mcDraw);
					totalLoad += ((Consumer) timestepsGraph[i].getNodeList()[n]).getLoad();
					if (i > 0)
						previousError = ((Consumer) timestepsGraph[i - 1].getNodeList()[n]).getLoadError();

					((Consumer) timestepsGraph[i].getNodeList()[n]).setLoadError(error + previousError); // plus load error of i-1 makes it cumulative.

					// Calculate and set the real load of a single consumer
					double realLoad = ((Consumer) timestepsGraph[i].getNodeList()[n]).getLoad()
							+ ((Consumer) timestepsGraph[i].getNodeList()[n]).getLoadError();
					((Consumer) timestepsGraph[i].getNodeList()[n]).setLoad(realLoad);
				}
			}
		}
		return timestepsGraph;
	}

	/**
	 * Set the state of generators and loads.
	 * @return Graphs of which the state has been changed using Monte Carlo draws
	 */
	private static Graph randomizeGridState(Graph graph, int currentTimeStep) {
		// randomize conventional generator data
		graph = randomizeConventionalGenerator(graph);

		// randomize renewable generators
		graph = randomizeRenewableGenerator(graph, currentTimeStep);

		return graph;
	}

	/**
	 * Randomize conventional generator data
	 * @param graph
	 * @return
	 */
	private static Graph randomizeConventionalGenerator(Graph graph){
		MontoCarloHelper monteCarloHelper = new MontoCarloHelper();

		for (int j = 0; j < graph.getNodeList().length - 1; j++) {
			// Check the class of the current node and deal with it accordingly.
			if (graph.getNodeList()[j] != null && (graph.getNodeList()[j].getClass() == ConventionalGenerator.class
					|| graph.getNodeList()[j].getClass() == RewGenerator.class)) {
				String generatorType = ((Generator) graph.getNodeList()[j]).getType();
				double mcDraw = 0; // This will hold our Monte Carlo draw
				// (hahaha mac draw)
				switch (generatorType) {
					case "H": // Hydro-eletric generator
						// TODO Ignore this for now, might be added at a later stage
						break;
					case "O": // Oil Thermal generator
						mcDraw = monteCarloHelper.getRandomUniformDist();
						// System.out.println(mcDraw);
						graph = checkConventionalGeneratorFailure(graph, j, mcDraw);
						break;
					case "N": // Nuclear Thermal generator
						mcDraw = monteCarloHelper.getRandomUniformDist();
						// System.out.println(mcDraw);
						graph = checkConventionalGeneratorFailure(graph, j, mcDraw);
						break;
					case "C": // Coal Thermal generator
						mcDraw = monteCarloHelper.getRandomUniformDist();
						// System.out.println(mcDraw);
						graph = checkConventionalGeneratorFailure(graph, j, mcDraw);
						break;
				}
			}
		}
		return graph;
	}

	/**
	 * Does monte carlo draws for wind and solor generators and sets their production according to these draws.
	 * @param graph
	 * @param currentTimeStep used for solar data generation
	 * @return The graph in which renewable production has been set.
	 */
	private static Graph randomizeRenewableGenerator(Graph graph, int currentTimeStep) {
		MontoCarloHelper monteCarloHelper = new MontoCarloHelper();

		for (int j = 0; j < graph.getNodeList().length - 1; j++) {
			// Check the class of the current node and deal with it accordingly.
			if (graph.getNodeList()[j] != null && (graph.getNodeList()[j].getClass() == ConventionalGenerator.class
					|| graph.getNodeList()[j].getClass() == RewGenerator.class)) {
				String generatorType = ((Generator) graph.getNodeList()[j]).getType();
				double mcDraw = 0; // This will hold our Monte Carlo draw
				switch (generatorType) {
				case "W": // Wind park generator
					mcDraw = monteCarloHelper.getRandomWeibull();

					/*double vCutIn = conf.getConfig("windGenerator").getInt("vCutIn");
					double vCutOff = conf.getConfig("windGenerator").getInt("vCutOff");
					double vRated = conf.getConfig("windGenerator").getInt("vRated");
					double pRated = conf.getConfig("windGenerator").getInt("pRated");*/
					double vCutIn = config.getConfigDoubleValue(CONFIGURATION_TYPE.WIND_GENERATOR, "vCutIn");
					double vCutOff = config.getConfigDoubleValue(CONFIGURATION_TYPE.WIND_GENERATOR, "vCutOff");
					double vRated = config.getConfigDoubleValue(CONFIGURATION_TYPE.WIND_GENERATOR, "vRated");
					double pRated = config.getConfigDoubleValue(CONFIGURATION_TYPE.WIND_GENERATOR, "pRated");

					if (mcDraw <= vCutIn || mcDraw >= vCutOff) {
						// Wind speed is outside the margins
						((RewGenerator) graph.getNodeList()[j]).setProduction(0);
					} else if (mcDraw >= vCutIn && mcDraw <= vRated) {
						// In a sweet spot for max wind production
						double production = (pRated * ((Math.pow(mcDraw, 3) - Math.pow(vCutIn, 3))
								/ (Math.pow(vRated, 3) - Math.pow(vCutIn, 3))));// Should be the same as the matlab from Laura
						((RewGenerator) graph.getNodeList()[j]).setProduction(production);
					} else if (vRated <= mcDraw && mcDraw <= vCutOff) {
						((RewGenerator) graph.getNodeList()[j]).setProduction(pRated);
					}
					break;
				case "S": // Solar generator
					// Let's ignore the sun as well for now...
					mcDraw = monteCarloHelper.getRandomGamma();

					// TODO: move to configuration file, or make it a constant
					/*double irradianceConstant = conf.getConfig("solarGenerator").getDouble("irradianceConstant"); // Solar constant*/
					double irradianceConstant = config.getConfigDoubleValue(CONFIGURATION_TYPE.SOLAR_GENERATOR, "irradianceConstant");
					/*double eccentricityCorrFactor = 1 + 0.033;*/ // Eccentricity correction Factor
					double eccentricityCorrFactor = config.getConfigDoubleValue(CONFIGURATION_TYPE.SOLAR_GENERATOR, "eccentricity");
					/*double langitude = 53.218705;
					double longitude = 6.567793;*/
					double langitude = config.getConfigDoubleValue(CONFIGURATION_TYPE.SOLAR_GENERATOR, "langitude"); ;
					double longitude = config.getConfigDoubleValue(CONFIGURATION_TYPE.SOLAR_GENERATOR, "longitude"); ;

					int month = Calendar.DECEMBER;
					GregorianCalendar calendar = new GregorianCalendar(2016, month, 14, currentTimeStep, 0);
					double deltaT = DeltaT.estimate(calendar);
					// AzimuthZenithAngle azimuthZenithAgnle = Grena3.calculateSolarPosition(calendar, langitude, longitude, deltaT);
					//double zenithAngle = azimuthZenithAgnle.getZenithAngle();
					GregorianCalendar[] sunriseset = SPA.calculateSunriseTransitSet(calendar, langitude, longitude,
							deltaT);

					int sunrise = sunriseset[0].get(Calendar.HOUR_OF_DAY);
					int sunset = sunriseset[2].get(Calendar.HOUR_OF_DAY);

					// We want to find the maximum Extraterrestial irradiance of the day.
					double extratIrradianceMax = 0;
					for (int i = 0; i < 24; i++) {
						GregorianCalendar cal = new GregorianCalendar(2016, month, 14, i, 0);
						AzimuthZenithAngle azimuthZenithAgnle = Grena3.calculateSolarPosition(cal, langitude, longitude,
								deltaT);
						double zenithAngle = azimuthZenithAgnle.getZenithAngle();

						double extratIrradiance = irradianceConstant * eccentricityCorrFactor
								* Math.cos((2 * Math.PI * calendar.get(Calendar.DAY_OF_YEAR)) / 365)
								* Math.cos(zenithAngle);
						if (extratIrradiance > extratIrradianceMax)
							extratIrradianceMax = extratIrradiance;
					}
					double sMax = extratIrradianceMax * mcDraw;
					double irradiance;
					if ((currentTimeStep <= sunrise) || (currentTimeStep >= sunset))
						irradiance = 0;
					else
						irradiance = sMax * Math.sin(Math.PI * (currentTimeStep - sunrise) / (sunset - sunrise));

				/*	double efficiency = conf.getConfig("solarGenerator").getDouble("panelEfficiency");*/
					double efficiency = config.getConfigDoubleValue(CONFIGURATION_TYPE.SOLAR_GENERATOR, "panelEfficiency");
					// surface array of panels in m², efficiency, irradiance of
					// panels on the horizontal plane.
					double production = 45 * efficiency * irradiance;

					((RewGenerator) graph.getNodeList()[j]).setProduction(production);
					// System.out.println("sunRise:" + sunrise + " currentTime:" + currentTimeStep + " sunset:" + sunset + " production:" + production + " max irradiance:" + extratIrradianceMax + " MC draw:" + mcDraw + " nodeId:" + ((RewGenerator)graph.getNodeList()[j]).getNodeId());

					break;
				}
			}
		}

		return graph;
	}

	/**
	 * Sets the state of conventional generators to on or off.
	 * based on monte carlo draw
	 * @param graph
	 * @param node
	 * @param mcDraw
	 * @return
	 */
	private static Graph checkConventionalGeneratorFailure(Graph graph, int node, double mcDraw) {
		// double convGeneratorProb = 0.5; //Probability of failure for
		// conventional generators

		if (((ConventionalGenerator) graph.getNodeList()[node]).getGeneratorFailure() == false) {// 0  means that  the reactor can fail.
			int nodeMTTF = ((ConventionalGenerator) graph.getNodeList()[node]).getMTTF();
			float mttf = (float) 1 / nodeMTTF;
			if (mcDraw < mttf) {
				// Our draw is smaller meaning that the generator has failed.
				((ConventionalGenerator) graph.getNodeList()[node]).setGeneratorFailure(true);
			}
		}
		return graph;
	}

	/**
	 * Depending on the state of the grid this method will increase or decrease
	 * production in order to balance the system
	 */
	private static Graph checkGridEquilibrium(Graph grid, int timestep) {
		Node[] nodeList = grid.getNodeList();
		sumLoads = 0;
		double renewableProduction = 0;
		double conventionalProduction = 0;
		double sumCurrentStorage = 0;
		double maximumStorageCapacity = 0;
		double minimumStorageCapacity = 0;


		/*
		 * In this loop we calculate the total demand, the total ->current<-
		 * production and, total ->current<- production of renewable generators.
		 */
		for (int i = 0; i < nodeList.length; i++) {
			if (nodeList[i] != null && nodeList[i].getClass() == Consumer.class) {
				sumLoads += ((Consumer) nodeList[i]).getLoad();
			} else if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class) {
				conventionalProduction += ((ConventionalGenerator) nodeList[i]).getProduction();
			} else if (nodeList[i] != null && nodeList[i].getClass() == RewGenerator.class) {
				renewableProduction += ((RewGenerator) nodeList[i]).getProduction();
			} else if (nodeList[i] != null && nodeList[i].getClass() == Storage.class) {
				// todo fixen sumCurrent storage can be negative atm?
				sumCurrentStorage += ((Storage) nodeList[i]).getCurrentCharge();
				maximumStorageCapacity += ((Storage) nodeList[i]).getMaximumCharge();
				minimumStorageCapacity += ((Storage) nodeList[i]).getMinimumCharge();

			}
		}
		totalCurrentProduction = conventionalProduction + renewableProduction;

		/*Config convGeneratorConf = conf.getConfig("conventionalGenerator");
		Config renewableConfig = conf.getConfig("Storage");*/

		/*int beginTime = renewableConfig.getInt("beginChargeTime");
		int endTime = renewableConfig.getInt("endChargeTime");*/
		int beginTime = config.getConfigIntValue(CONFIGURATION_TYPE.STORAGE, "beginChargeTime" );
		int endTime = config.getConfigIntValue(CONFIGURATION_TYPE.STORAGE, "endChargeTime" );

		boolean dischargeAllowed = true;
		if(timestep <= beginTime && timestep <= endTime){
			System.out.print(" ");
			grid = chargeStorage(grid);
			dischargeAllowed = false;
		}


		double demand = (totalCurrentProduction - sumLoads);
		// Check if we need to increase current production
		if ((totalCurrentProduction - sumLoads) < 0) {
			System.out.print("Increasing production ");

			List<Offer> offers = new ArrayList<>();

			// find cheapest offers
			for (int i = 0; i < nodeList.length - 1; i++) {
				if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class) {

					List<Offer> offerList = ((ConventionalGenerator) nodeList[i]).getIncreaseProductionOffers();
					offers.addAll(offerList);
				}
			}

			// sort offers best value for money
			Collections.sort(offers);

			for (int i = 0; i < offers.size(); i++) {
				Offer offer = offers.get(i);
				double offeredProduction = offer.getProduction();
				if (demand < 0 && offer.getAvailable()) {
					((ConventionalGenerator) nodeList[offer.getNodeIndex()]).takeIncreaseOffer(offer.getOfferListId());
					double newProduction = ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).getProduction()
							+ offer.getProduction();

					if (Math.abs(demand) <= newProduction) {
						totalCurrentProduction += ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(Math.abs(demand));
					} else {
						totalCurrentProduction += ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(newProduction);
					}
					offers.remove(i); // remove offer from list
					demand = (totalCurrentProduction - sumLoads); // update demand
				}
			}

		} else if ((totalCurrentProduction - sumLoads) > 0) {
			// we need to decrease energy production
			System.out.print("Decreasing production ");

			// todo take offers from cheapest nodes to decrease production.
			List<Offer> offers = new ArrayList<>();

			// find cheapest offers
			for (int i = 0; i < nodeList.length - 1; i++) {
				if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class) {
					List<Offer> offerList = ((ConventionalGenerator) nodeList[i]).getDecreaseProductionOffers();
					offers.addAll(offerList);
				}
			}

			// sort offers best value for money
			Collections.sort(offers);

			// decrease production
			for (int i = 0; i < offers.size(); i++) {
				Offer offer = offers.get(i);
				double offeredProduction = offer.getProduction();
				if (demand > 0 && offer.getAvailable()) {
					((ConventionalGenerator) nodeList[offer.getNodeIndex()]).takeDecreaseOffer(offer.getOfferListId());
					double newProduction = ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).getProduction()- offer.getProduction();

					if (demand <= newProduction) {
						// only decrease production until demand is met
						totalCurrentProduction -= ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(demand);
					} else {
						totalCurrentProduction -= ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(newProduction);
					}

					offers.remove(i); // remove offer from list
					demand = (totalCurrentProduction - sumLoads); // update demand
				}
			}
		} else {
			System.out.print("Grid is balanced ");
			return null;// production and demand are balanced.
		}

		if(dischargeAllowed)
			grid = chargeOrDischargeStorage(grid);

		System.out.print("Total production: " + totalCurrentProduction + " ");
		System.out.println("total load: " + sumLoads);
		return grid;
	}


	/**
	 * Charges storage but only if the current charge is less than 50% of its capacity.
	 * @param graph
	 * @return graph in which the state of storages has been set.
	 */
	private static Graph chargeStorage(Graph graph){
		for(int i = 0; i < graph.getNodeList().length; i++){
			if(graph.getNodeList()[i].getClass() == Storage.class){
				if(((Storage)graph.getNodeList()[i]).getMaximumCharge() * 0.5 > ((Storage)graph.getNodeList()[i]).getCurrentCharge()){
					sumLoads += ((Storage)graph.getNodeList()[i]).setCurrentCharge(((Storage)graph.getNodeList()[i]).getMaximumCharge()*0.5);
				}
			}
		}
		return graph;
	}

	/**
	 * Charges or discharges storage depending depending on the state of production and load.
	 * @param graph
	 * @return
	 */
	private static Graph chargeOrDischargeStorage(Graph graph){
		Node[] nodeList = graph.getNodeList();
		if (totalCurrentProduction > sumLoads) {
			System.out.print("curtailment ");
			//Charge the batteries
			for (int i = 0; i < nodeList.length; i++) {
				if (nodeList[i] != null && nodeList[i].getClass() == Storage.class) {
					if (totalCurrentProduction - ((Storage) nodeList[i]).getMaximumCharge() > sumLoads){
						System.out.print("battery charging");
						totalCurrentProduction -= ((Storage) nodeList[i]).setCurrentCharge(((Storage) nodeList[i]).getMaximumCharge());
					} else
						totalCurrentProduction -= ((Storage) nodeList[i]).setCurrentCharge(((Storage) nodeList[i]).setCurrentCharge(sumLoads - totalCurrentProduction)); //charge the remainder to fully meet the demand.
				}
			}
		} else if (totalCurrentProduction < sumLoads) {
			System.out.print("battery discharge ");
			for (int i = 0; i < nodeList.length; i++) {
				if (nodeList[i] != null && nodeList[i].getClass() == Storage.class) {
					if (totalCurrentProduction + ((Storage) nodeList[i]).getMaximumCharge() > sumLoads) {
						totalCurrentProduction += ((Storage) nodeList[i]).discharge();
					} else
						totalCurrentProduction += ((Storage) nodeList[i]).discharge();
				}
			}
		} else {
			System.out.print("Balanced ");
			// Production and load are balanced.
		}

		return graph;
	}

	public static double handleIncreaseProductionOffers(Node[] nodeList, double demand) {
		List<Offer> offers = new ArrayList<>();

		// find cheapest offers
		for (int i = 0; i < nodeList.length - 1; i++) {
			if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class) {

				List<Offer> offerList = ((ConventionalGenerator) nodeList[i]).getDecreaseProductionOffers();
				offers.addAll(offerList);
			}
		}

		// sort offers best value for money
		Collections.sort(offers);

		for (int i = 0; i < offers.size(); i++) {
			Offer offer = offers.get(i);
			double offeredProduction = offer.getProduction();
			if (demand > 0) {
				((ConventionalGenerator) nodeList[offer.getNodeIndex()]).takeDecreaseOffer(0);
				demand -= ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(offeredProduction);
				offers.remove(i); // remove offer from list
			} else {
				return demand;
			}
		}

		return demand;
	}

	public static double handleDecreaseProductionOffers(Node[] nodeList, double demand) {
		List<Offer> offers = new ArrayList<>();

		// find cheapest offers
		for (int i = 0; i < nodeList.length - 1; i++) {
			if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class) {
				// Offer bestOffer = ((ConventionalGenerator)
				// nodeList[i]).getBestIncreaseOffer();
				// offers.add(bestOffer);
				List<Offer> offerList = ((ConventionalGenerator) nodeList[i]).getDecreaseProductionOffers();
				offers.addAll(offerList);
			}
		}

		// sort offers best value for money
		Collections.sort(offers);

		for (int i = 0; i < offers.size(); i++) {
			Offer offer = offers.get(i);
			double offeredProduction = offer.getProduction();
			if (demand > 0) {
				((ConventionalGenerator) nodeList[offer.getNodeIndex()]).takeDecreaseOffer(0);
				demand -= ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(offeredProduction);
				offers.remove(i); // remove offer from list
			} else {
				return demand;
			}
		}

		return demand;
	}

}