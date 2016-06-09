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
		Graph[] timestepsGraph = null;
		Parser parser = new Parser();
		Graph graph;


		graph = parser.parseData(config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "input-file"));

		// load general config
		String model = config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "model-file");
		String dirpath = config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "output-folder");
		String path = config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "input-file");

		// load glpsol config
		String outpath1 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "outpath1");
		String outpath2 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "outpath2");
		String solpath1 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "solpath1");
		String solpath2 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "solpath2");

		DataModelPrint mp = new DataModelPrint();
		Process proc = null;

		// load simulation limit
		int simLimit = config.getConfigIntValue(CONFIGURATION_TYPE.GENERAL, "simulation-runs");
		for (int numOfSim = 0; numOfSim < simLimit; numOfSim++) {
			System.out.println("Simulation: " + numOfSim);
			SimulationStateInitializer simulationState = new SimulationStateInitializer();

			timestepsGraph = new Graph[config.getConfigIntValue(CONFIGURATION_TYPE.GENERAL, "numberOfTimeSteps")];
			timestepsGraph = simulationState.creategraphs(graph, timestepsGraph);
			int i = 0;

			String solutionPath = dirpath + "simRes" + numOfSim + "";
			try {
				Files.createDirectories(Paths.get(solutionPath)); // create a new directory to safe the output in
			} catch (IOException e1) {
				e1.printStackTrace();
				System.exit(0);
			}

			//todo use expectedloadprod for current grid
			Graph[] plannedTimestepsGraph = expectedLoadAndProduction(timestepsGraph);
			//TODO: Plan offers based on remaining 1/3,2/3 of buffer

			// set real load from consumers using Monte carlo draws
			timestepsGraph = setRealLoad(timestepsGraph);
			while (i < plannedTimestepsGraph.length) {
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

					// write output to solution file
					writeOutputFiles(dirpath, solutionPath, i);

					if (graph.getNstorage() > 0) {
						timestepsGraph[i] = parser.parseUpdates(String.valueOf(dirpath) + "update.txt",
								timestepsGraph[i]); // Keeps track of the new state for storages.
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

		//TODO: compare expected load/prod versus actual load/prod
		long endtime = System.nanoTime();
		long duration = endtime - starttime;
		System.out.println("Time used:" + duration / 1000000 + " millisecond");
	}

	/**
	 * Move glpsol output to structured folders
	 * @param dirpath current location of files
	 * @param solutionPath new location for files
	 * @param timeStep current timestep
	 */
	public static void writeOutputFiles(String dirpath, String solutionPath, int timeStep){
		try {
			if (new File(dirpath + "/sol" + timeStep + ".txt").exists())
				Files.move(Paths.get(dirpath + "/sol" + timeStep + ".txt"),
						Paths.get(solutionPath + "/sol" + timeStep + ".txt"), StandardCopyOption.REPLACE_EXISTING);

			if (new File(dirpath + "/filename.out").exists())
				Files.move(Paths.get(dirpath + "/filename.out"), Paths.get(solutionPath + "/filename.out"),
						StandardCopyOption.REPLACE_EXISTING);

			if (new File(dirpath + "/update.txt").exists())
				Files.copy(Paths.get(dirpath + "/update.txt"), Paths.get(solutionPath + "/update" + timeStep + ".txt"),
						StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	/**
	 * Calculates the expected load and production of an entire day
	 * @param graphs
	 * @return Array where [0] = expectedLoad and [1] = expectedProduction
	 */
	private static Graph[] expectedLoadAndProduction(Graph[] graphs) {
		double sumExpectedLoad = 0;
		double sumExpectedProduction = 0;
		Graph[] plannedProduction = graphs; // clone state of graphs
//		List<Double[double,double]> result = new Double[23];

		for(int hour=0; hour < 24; hour++){
			double expectedLoad = 0, expectedProduction = 0; // initialize expected loads,production
			plannedProduction[hour] = randomizeRenewableGenerator(plannedProduction[hour], hour); //set renewable production.

			// calculate expected load
			for(int i = 0; i < plannedProduction[hour].getNodeList().length; i++){
				if(plannedProduction[hour].getNodeList()[i].getClass() == Consumer.class){
					expectedLoad += ((Consumer)plannedProduction[hour].getNodeList()[i]).getLoad();
				} else if (plannedProduction[hour].getNodeList()[i].getClass() == RewGenerator.class) {
					expectedLoad -=  ((RewGenerator)plannedProduction[hour].getNodeList()[i]).getProduction();
				}
			}

			// calculate expected conventional generator production
			plannedProduction[hour] = planExpectedProductionConvGen(plannedProduction[hour], hour, expectedLoad);

			// get expected production
			for (int i = 0; i < plannedProduction[hour].getNodeList().length; i ++){
				if (plannedProduction[hour].getNodeList()[i].getClass() == ConventionalGenerator.class){
					expectedProduction +=  ((ConventionalGenerator)plannedProduction[hour].getNodeList()[i]).getProduction();
				}
			}
			sumExpectedLoad += expectedLoad;
			sumExpectedProduction += expectedProduction;

//			result[hour] = new Double[]{expectedLoad, expectedProduction};
		}
//		Double[] result = new Double[]{sumExpectedLoad, sumExpectedProduction};
		return plannedProduction;
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
					mcDraw = monteCarloHelper.getRandomGamma();

					double irradianceConstant = config.getConfigDoubleValue(CONFIGURATION_TYPE.SOLAR_GENERATOR, "irradianceConstant");
					double eccentricityCorrFactor = config.getConfigDoubleValue(CONFIGURATION_TYPE.SOLAR_GENERATOR, "eccentricity");
					double langitude = config.getConfigDoubleValue(CONFIGURATION_TYPE.SOLAR_GENERATOR, "langitude"); ;
					double longitude = config.getConfigDoubleValue(CONFIGURATION_TYPE.SOLAR_GENERATOR, "longitude"); ;

					int month = Calendar.DECEMBER;
					GregorianCalendar calendar = new GregorianCalendar(2016, month, 14, currentTimeStep, 0);
					double deltaT = DeltaT.estimate(calendar);
					GregorianCalendar[] sunriseset = SPA.calculateSunriseTransitSet(calendar, langitude, longitude, deltaT);

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


	private static Graph planExpectedProductionConvGen(Graph grid, int timestep, double expectedLoad) {
		Node[] nodeList = grid.getNodeList();
		double totalMaxProductionAvailableConv = 0;
		double totalMaxProductionAvailableRenew = 0;
		double sumLoads = 0;

		for(int i = 0; i < nodeList.length; i++)
		{
			if(nodeList[i] != null && nodeList[i].getClass() == Consumer.class){
				sumLoads += ((Consumer)nodeList[i]).getLoad();
			} else if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class){
				totalMaxProductionAvailableConv += ((ConventionalGenerator)nodeList[i]).getDayAheadMaxP();
			} else if (nodeList[i] != null && nodeList[i].getClass() == RewGenerator.class){
				totalMaxProductionAvailableRenew += ((RewGenerator)nodeList[i]).getProduction();
			} else if (nodeList[i] != null && nodeList[i].getClass() == Storage.class){
			}
		}

		double expectedProduction = 0;
		double dayAheadLimitMax = config.getConfigDoubleValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "dayAheadLimitMax");
		double dayAheadLimitMin = config.getConfigDoubleValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "dayAheadLimitMin");
		double maxProductionIncrease = config.getConfigDoubleValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "maxProductionIncrease");

		if (expectedLoad > 0) {
			for ( int i = nodeList.length-1; i >= 0; i--){

				double remainingLoad =(expectedLoad - expectedProduction);
				if( remainingLoad > 0 ){
					if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class){
						double minP = ((ConventionalGenerator) nodeList[i]).getDayAheadMinP();
						double maxP = ((ConventionalGenerator) nodeList[i]).getDayAheadMaxP();

						if(remainingLoad<maxP && remainingLoad > minP) {
							expectedProduction += ((ConventionalGenerator) nodeList[i]).setScheduledProduction(remainingLoad);
						} else if(remainingLoad < minP){
							expectedProduction += ((ConventionalGenerator) nodeList[i]).setScheduledProduction(minP);
						}else{
							expectedProduction += ((ConventionalGenerator) nodeList[i]).setScheduledProduction(maxP);
						}
					}
				}else{
					break; // remaining load fulfilled
				}
			}

		}else{
			// current expected load is already met.
		}


		return grid;
	}

	/**
	 * Depending on the state of the grid this method will increase or decrease
	 * production in order to balance the system
	 */
	private static Graph checkGridEquilibrium(Graph grid, int timestep) {
		Node[] nodeList = grid.getNodeList();
		double realLoad = 0;
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
				realLoad += ((Consumer) nodeList[i]).getLoad();
			} else if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class) {
				conventionalProduction += ((ConventionalGenerator) nodeList[i]).getProduction();
			} else if (nodeList[i] != null && nodeList[i].getClass() == RewGenerator.class) {
				renewableProduction += ((RewGenerator) nodeList[i]).getProduction();
			} else if (nodeList[i] != null && nodeList[i].getClass() == Storage.class) {
				//TODO: fixen sumCurrent storage can be negative atm?
				sumCurrentStorage += ((Storage) nodeList[i]).getCurrentCharge();
				maximumStorageCapacity += ((Storage) nodeList[i]).getMaximumCharge();
				minimumStorageCapacity += ((Storage) nodeList[i]).getMinimumCharge();

			}
		}

		int beginTime = config.getConfigIntValue(CONFIGURATION_TYPE.STORAGE, "beginChargeTime");
		int endTime = config.getConfigIntValue(CONFIGURATION_TYPE.STORAGE, "endChargeTime");

		boolean dischargeAllowed = true;
		if(timestep <= beginTime && timestep <= endTime){
			grid = chargeStorage(grid);
			dischargeAllowed = false;
		}

		// overProduction = 0 needs to be satisfied
		double realProduction = conventionalProduction + renewableProduction;
		double overProduction = (realProduction - realLoad);
		// Check if we need to increase current production
		System.out.println("RealProduction: " + realProduction + " " + "realLoad: "+ realLoad);
		if (overProduction < 0) {
			System.out.println("Increasing production ");

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
				if (overProduction < 0 && offer.getAvailable()) {
					((ConventionalGenerator) nodeList[offer.getNodeIndex()]).takeIncreaseOffer(offer.getOfferListId());
					double newProduction = ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).getProduction()
							+ offer.getProduction();


					if (Math.abs(overProduction) <= newProduction) {
						totalCurrentProduction += ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(Math.abs(overProduction));
					} else {
						totalCurrentProduction += ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(newProduction);
					}
					offers.remove(i); // remove offer from list
					overProduction = (totalCurrentProduction - sumLoads); // update demand
				}
			}

			for (int i = 0; i < offers.size(); i++) {
				Offer offer = offers.get(i);
				double offeredProduction = offer.getProduction();

				// check if deltaP isn't satisfied, and if offer is available
				if (overProduction < 0 && offer.getAvailable()) {
					if((overProduction+offeredProduction) <= 0){ // take offer
						double newProduction = ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(offer.getProduction());
						realProduction += newProduction;
					}else if((overProduction+offeredProduction)>0){ // only take difference between deltaP and offeredProduction
						double remainingProduction = offeredProduction-(offeredProduction+overProduction);
						double newProduction = ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(remainingProduction);
						realProduction += newProduction;
					}else{
						double newProduction = ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(offer.getProduction());
						realProduction += newProduction;
					}

					// disable offer from generator
					((ConventionalGenerator) nodeList[offer.getNodeIndex()]).takeDecreaseOffer(offer.getOfferListId());
					overProduction = (realProduction - realLoad); // update deltaP
				}else{
					break; // load is satisfied
				}
			}

		} else if (overProduction > 0) {
			// we need to decrease energy production
			System.out.println("Decreasing production ");

			//TODO: take offers from cheapest nodes to decrease production.
			//TODO: generate real offers not from input file
			List<Offer> offers = new ArrayList<>();

			// find cheapest offers
			for (int i = 0; i < nodeList.length - 1; i++) {
				if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class) {
					List<Offer> offerList = ((ConventionalGenerator) nodeList[i]).getDecreaseProductionOffers();
					offers.addAll(offerList);
				}
			}

			// sort offers by cheapest offer
			Collections.sort(offers);

			// decrease production by taking offers
			for (int i = 0; i < offers.size(); i++) {
				Offer offer = offers.get(i);
				double offeredProduction = offer.getProduction();

				// check if deltaP isn't satisfied, and if offer is available
				if (overProduction > 0 && offer.getAvailable()) {
					if((overProduction-offeredProduction) >= 0){ // take offer
						double newProduction = ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(offer.getProduction());
						realProduction -= newProduction;
					}else if((overProduction-offeredProduction)<0){ // only take difference between deltaP and offeredProduction
						double newProduction = ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction((offeredProduction-overProduction));
						realProduction -= newProduction;
					}else{
						double newProduction = ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(offer.getProduction());
						realProduction -= newProduction;
					}

					// disable offer from generator
					((ConventionalGenerator) nodeList[offer.getNodeIndex()]).takeDecreaseOffer(offer.getOfferListId());
					overProduction = (realProduction - realLoad); // update deltaP
				}else{
					break; // load is satisfied
				}
			}



			//TODO: turn off generator
			if(overProduction >0){
				// turn off generators when Production is still to high.

				// loop over generators and when this.maxP < 60 disable generator

			}


			//TODO: last resort cut renewable

		} else {
			System.out.print("Grid is balanced ");
			return grid;
		}

		// update nodeList
		grid.setNodeList(nodeList);


		if(dischargeAllowed)
			grid = chargeOrDischargeStorage(grid);


		//TODO: curtailment decrease production
		// curtail the renewable


		//TODO: shedd load increase prod

		System.out.print("After balancing - ");
		System.out.println("Real production: " + realProduction + " Total load: " + realLoad);
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
					sumLoads += ((Storage)graph.getNodeList()[i]).charge(((Storage) graph.getNodeList()[i]).getMaximumCharge() * 0.5);
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
						totalCurrentProduction -= ((Storage) nodeList[i]).charge(((Storage) nodeList[i]).getMaximumCharge());
					} else
						totalCurrentProduction -= ((Storage) nodeList[i]).charge(((Storage) nodeList[i]).charge(sumLoads - totalCurrentProduction)); //charge the remainder to fully meet the demand.
				}
			}
		} else if (totalCurrentProduction < sumLoads) {
			System.out.print("battery discharge ");
			for (int i = 0; i < nodeList.length; i++) {
				if (nodeList[i] != null && nodeList[i].getClass() == Storage.class) {
					if (totalCurrentProduction + ((Storage) nodeList[i]).getMaximumCharge() > sumLoads) {
						totalCurrentProduction += ((Storage) nodeList[i]).discharge(((Storage) nodeList[i]).getMaximumCharge());
					} else
						totalCurrentProduction += ((Storage) nodeList[i]).discharge(sumLoads - totalCurrentProduction);
				}
			}
		} else {
			System.out.print("Balanced ");
			// Production and load are balanced.
		}

		return graph;
	}

//	public static double handleIncreaseProductionOffers(Node[] nodeList, double demand) {
//		List<Offer> offers = new ArrayList<>();
//
//		// find cheapest offers
//		for (int i = 0; i < nodeList.length - 1; i++) {
//			if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class) {
//
//				List<Offer> offerList = ((ConventionalGenerator) nodeList[i]).getDecreaseProductionOffers();
//				offers.addAll(offerList);
//			}
//		}
//
//		// sort offers best value for money
//		Collections.sort(offers);
//
//		for (int i = 0; i < offers.size(); i++) {
//			Offer offer = offers.get(i);
//			double offeredProduction = offer.getProduction();
//			if (demand > 0) {
//				((ConventionalGenerator) nodeList[offer.getNodeIndex()]).takeDecreaseOffer(0);
//				demand -= ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(offeredProduction);
//				offers.remove(i); // remove offer from list
//			} else {
//				return demand;
//			}
//		}
//
//		return demand;
//	}
//
//	public static double handleDecreaseProductionOffers(Node[] nodeList, double demand) {
//		List<Offer> offers = new ArrayList<>();
//
//		// find cheapest offers
//		for (int i = 0; i < nodeList.length - 1; i++) {
//			if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class) {
//				// Offer bestOffer = ((ConventionalGenerator)
//				// nodeList[i]).getBestIncreaseOffer();
//				// offers.add(bestOffer);
//				List<Offer> offerList = ((ConventionalGenerator) nodeList[i]).getDecreaseProductionOffers();
//				offers.addAll(offerList);
//			}
//		}
//
//		// sort offers best value for money
//		Collections.sort(offers);
//
//		for (int i = 0; i < offers.size(); i++) {
//			Offer offer = offers.get(i);
//			double offeredProduction = offer.getProduction();
//			if (demand > 0) {
//				((ConventionalGenerator) nodeList[offer.getNodeIndex()]).takeDecreaseOffer(0);
//				demand -= ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(offeredProduction);
//				offers.remove(i); // remove offer from list
//			} else {
//				return demand;
//			}
//		}
//
//		return demand;
//	}

}