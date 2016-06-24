package pgrid_opt;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

import config.ConfigCollection;
import config.ConfigCollection.CONFIGURATION_TYPE;
import filehandler.DataModelPrint;
import filehandler.Parser;
import graph.Graph;
import graph.Node;
import model.*;
import net.e175.klaus.solarpositioning.AzimuthZenithAngle;
import net.e175.klaus.solarpositioning.DeltaT;
import net.e175.klaus.solarpositioning.Grena3;
import net.e175.klaus.solarpositioning.SPA;
import simulation.GridBalancer;
import simulation.MontoCarloHelper;
import simulation.ProductionLoadHandler;
import simulation.SimulationMonteCarloHelper;
import simulation.SimulationStateInitializer;
import simulation.StorageHandler;
import model.Generator.GENERATOR_TYPE;

public class Main {

	private static ConfigCollection config = new ConfigCollection();
	private static ProductionLoadHandler productionLoadHandler = new ProductionLoadHandler();
	private static StorageHandler storageHandler = new StorageHandler();
	private static SimulationMonteCarloHelper simulationMonteCarloHelper = new SimulationMonteCarloHelper();
	private static GridBalancer gridBalancer = new GridBalancer();

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
		int EENScum = 0;
		for (int numOfSim = 0; numOfSim < simLimit; numOfSim++) {
			System.out.println("Simulation: " + numOfSim);
			SimulationStateInitializer simulationState = new SimulationStateInitializer();

			timestepsGraph = new Graph[config.getConfigIntValue(CONFIGURATION_TYPE.GENERAL, "numberOfTimeSteps")];
			timestepsGraph = simulationState.creategraphs(graph, timestepsGraph);
			int currentTimeStep = 0;

			String solutionPath = dirpath + "simRes" + numOfSim + "";
			try {
				Files.createDirectories(Paths.get(solutionPath)); // create a new directory to safe the output in
			} catch (IOException e1) {
				e1.printStackTrace();
				System.exit(0);
			}

			//Plan production based on expected load and storage charging during the night period.
			Graph[] plannedTimestepsGraph = productionLoadHandler.setExpectedLoadAndProduction(timestepsGraph);

			for(int i = 0; i < 24; i++){
				double testload = productionLoadHandler.calculateLoad(plannedTimestepsGraph[i]);
				double testprod = productionLoadHandler.calculateProduction(plannedTimestepsGraph[i]);
				System.out.println("expectedLoad: " + testload + " expectedProd: " + testprod);
			}

			// set real load for consumers using Monte carlo draws for the entire day.
			Graph[] realTimestepsGraph = ProductionLoadHandler.setRealLoad(plannedTimestepsGraph);
			while (currentTimeStep < realTimestepsGraph .length) {
				System.out.println("TimeStep: "+ currentTimeStep);

				//Set failure state of conventional generators and calculates the real renewable production for a single hour.
				realTimestepsGraph [currentTimeStep] = randomizeGridState(plannedTimestepsGraph[currentTimeStep], currentTimeStep);


				System.out.println("load "+productionLoadHandler.calculateLoad(realTimestepsGraph[currentTimeStep]));
				System.out.println("prod "+productionLoadHandler.calculateProduction(realTimestepsGraph[currentTimeStep]));

				//Plan real storage charging using excess of renewable production to charge past 50% max SoC.
				storageHandler.planStorageCharging(realTimestepsGraph[currentTimeStep], currentTimeStep);

				System.out.println("load "+productionLoadHandler.calculateLoad(realTimestepsGraph[currentTimeStep]));
				System.out.println("prod "+productionLoadHandler.calculateProduction(realTimestepsGraph[currentTimeStep]));

				//Attempt to balance production and load  for a single hour.
				realTimestepsGraph [currentTimeStep] = checkGridEquilibrium(plannedTimestepsGraph[currentTimeStep], currentTimeStep);

				mp.printData(plannedTimestepsGraph[currentTimeStep], String.valueOf(dirpath) + outpath1 + currentTimeStep + outpath2, Integer.toString(currentTimeStep)); // This creates a new input file.

				try {

					String command = "" + String.valueOf(solpath1) + outpath1 + currentTimeStep + outpath2 + solpath2 + model;
					command = command + " --nopresol --output filename.out ";
					System.out.println(command);

					proc = Runtime.getRuntime().exec(command, null, new File(dirpath));
					proc.waitFor();

					StringBuffer output = new StringBuffer();
					BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream())); // Using the new input file, we apply the model to solve the cost function given the new state of the grid.
					String line = "";

					while ((line = reader.readLine()) != null) {
						output.append(String.valueOf(line) + "\n");
					}
					System.out.println(output);

					timestepsGraph[currentTimeStep] = timestepsGraph[currentTimeStep].setFlowFromOutputFile(timestepsGraph[currentTimeStep], currentTimeStep);
					timestepsGraph[currentTimeStep].printGraph(currentTimeStep, numOfSim);

					// write output to solution file
					writeOutputFiles(dirpath, solutionPath, currentTimeStep);

					if (graph.getNstorage() > 0) {
						timestepsGraph[currentTimeStep] = parser.parseUpdates(String.valueOf(dirpath) + "update.txt",
								timestepsGraph[currentTimeStep]); // Keeps track of the new state for storages.

					// TODO only last timestep doesn't need to update the storages, Julien?
					if (currentTimeStep < 23)
						timestepsGraph[currentTimeStep + 1] = simulationState.updateStorages(timestepsGraph[currentTimeStep],
								timestepsGraph[currentTimeStep + 1]); // Apply the new state of the storage for the next time step.
					}
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
					System.exit(0);
				}

				// set state of graph for the next hour to the state of the current graph (failure)
				if(currentTimeStep >0 && currentTimeStep < 23){
					for (int n = 0; n < timestepsGraph[currentTimeStep].getNodeList().length; n++) {
						if (timestepsGraph[currentTimeStep].getNodeList()[n].getClass() == ConventionalGenerator.class){
							// cascade generator failure to the next hour.
							ConventionalGenerator cgen = (ConventionalGenerator)timestepsGraph[currentTimeStep].getNodeList()[n];
							((ConventionalGenerator) timestepsGraph[currentTimeStep+1].getNodeList()[n]).setGeneratorFailure(cgen.getGeneratorFailure());
						}
					}
				}
				++currentTimeStep;
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

//			EENScum += calculateEENS(plannedTimestepsGraph, timestepsGraph);
		}

		//TODO: compare expected load/prod versus actual load/prod
		long endtime = System.nanoTime();
		long duration = endtime - starttime;
		System.out.println("Time used:" + duration / 1000000 + " millisecond");
	}

	public double calculateEENS(Graph[] plannedTimestepsGraph, Graph[] realTimestepsGraph){
		int currentTimeStep =0;

		double sheddedLoad = 0;
		while (currentTimeStep < realTimestepsGraph.length) {
			double plannedLoad = productionLoadHandler.calculateLoad(plannedTimestepsGraph[currentTimeStep]);
			double realLoad = productionLoadHandler.calculateLoad(realTimestepsGraph[currentTimeStep]);

			// if there is expected energy not supplied then count it as shedded load
			if((realLoad - plannedLoad) >0)
				sheddedLoad += (realLoad - plannedLoad);
		}

		// calculate EENS
		return sheddedLoad;
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
	 * Set the failure state of generators and calculates the production of renewable production.
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
	 * Randomize the failure status of conventional generator data
	 * @param graph
	 * @return
	 */
	private static Graph randomizeConventionalGenerator(Graph graph){
		MontoCarloHelper monteCarloHelper = new MontoCarloHelper();

		for (int j = 0; j < graph.getNodeList().length - 1; j++) {
			// Check the class of the current node and deal with it accordingly.
			if (graph.getNodeList()[j] != null && (graph.getNodeList()[j].getClass() == ConventionalGenerator.class
					|| graph.getNodeList()[j].getClass() == RenewableGenerator.class)) {
				GENERATOR_TYPE generatorType = ((Generator) graph.getNodeList()[j]).getType();
				double mcDraw = 0; // This will hold our Monte Carlo draw
				// (hahaha mac draw)
				switch (generatorType) {
					case HYDRO: // Hydro-eletric generator
						// TODO Ignore this for now, might be added at a later stage
						break;
					case OIL: // Oil Thermal generator
						mcDraw = monteCarloHelper.getRandomUniformDist();
						// System.out.println(mcDraw);
						graph = checkConventionalGeneratorFailure(graph, j, mcDraw);
						break;
					case NUCLEAR: // Nuclear Thermal generator
						mcDraw = monteCarloHelper.getRandomUniformDist();
						// System.out.println(mcDraw);
						graph = checkConventionalGeneratorFailure(graph, j, mcDraw);
						break;
					case COAL: // Coal Thermal generator
						mcDraw = monteCarloHelper.getRandomUniformDist();
						// System.out.println(mcDraw);
						graph = checkConventionalGeneratorFailure(graph, j, mcDraw);
						break;
				default:
					//We Don't care about renewables at this point.
					break;
				}
			}
		}
		return graph;
	}

	/**
	 * Does monte carlo draws for wind and solar generators and sets their production according to these draws.
	 * @param graph
	 * @param currentTimeStep used for solar data generation
	 * @return The graph in which renewable production has been set.
	 */
	public static Graph randomizeRenewableGenerator(Graph graph, int currentTimeStep) {
		MontoCarloHelper monteCarloHelper = new MontoCarloHelper();

		for (int j = 0; j < graph.getNodeList().length - 1; j++) {
			// Check the class of the current node and deal with it accordingly.
			if (graph.getNodeList()[j] != null && (graph.getNodeList()[j].getClass() == ConventionalGenerator.class
					|| graph.getNodeList()[j].getClass() == RenewableGenerator.class)) {
				GENERATOR_TYPE generatorType = ((Generator) graph.getNodeList()[j]).getType();
				double mcDraw = 0; // This will hold our Monte Carlo draw
				switch (generatorType) {
				case WIND: // Wind park generator
					mcDraw = monteCarloHelper.getRandomWeibull();
					double vCutIn = config.getConfigDoubleValue(CONFIGURATION_TYPE.WIND_GENERATOR, "vCutIn");
					double vCutOff = config.getConfigDoubleValue(CONFIGURATION_TYPE.WIND_GENERATOR, "vCutOff");
					double vRated = config.getConfigDoubleValue(CONFIGURATION_TYPE.WIND_GENERATOR, "vRated");
					double pRated = config.getConfigDoubleValue(CONFIGURATION_TYPE.WIND_GENERATOR, "pRated");

					if (mcDraw <= vCutIn || mcDraw >= vCutOff) {
						// Wind speed is outside the margins
						((RenewableGenerator) graph.getNodeList()[j]).setProduction(0);
					} else if (mcDraw >= vCutIn && mcDraw <= vRated) {
						// In a sweet spot for max wind production
						double production = (pRated * ((Math.pow(mcDraw, 3) - Math.pow(vCutIn, 3))
								/ (Math.pow(vRated, 3) - Math.pow(vCutIn, 3))));// Should be the same as the matlab from Laura
						((RenewableGenerator) graph.getNodeList()[j]).setProduction(production);
					} else if (vRated <= mcDraw && mcDraw <= vCutOff) {
						((RenewableGenerator) graph.getNodeList()[j]).setProduction(pRated);
					}
					break;
				case SOLAR: // Solar generator
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

					((RenewableGenerator) graph.getNodeList()[j]).setProduction(production);
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

	public static Graph planExpectedProductionConvGen(Graph[] grid, int timestep) {
		Node[] nodeList = grid[timestep].getNodeList();
		double sumExpectedProduction = 0;
		for ( int i = 0; i < nodeList.length-1; i++){
			double sumExpectedLoad = productionLoadHandler.calculateLoad(grid[timestep]);
			if(nodeList[i].getClass() == ConventionalGenerator.class){
				ConventionalGenerator generator =  ((ConventionalGenerator) nodeList[i]);
				if (timestep > 0){ //We take into account spinup after hour 0, maximum increase with spinup is 50% of max generation.
					double previousProduction =  ((ConventionalGenerator)grid[timestep - 1].getNodeList()[i]).getProduction();
					double production = previousProduction + generator.getMaxP() * 0.5;
					if(sumExpectedProduction+production < sumExpectedLoad){
						sumExpectedProduction += generator.setScheduledProduction(production, previousProduction);
					} else{
						//We don't need to use maximum production to meet the load, so we set production to remainder.
						production = sumExpectedLoad - sumExpectedProduction;

						//Check if production isn't to low, if it is set generator to min production.
						if (production < generator.getDayAheadMinProduction())
							sumExpectedProduction += generator.setScheduledProduction(production, previousProduction);
						else
							sumExpectedProduction += generator.setScheduledProduction(generator.getDayAheadMinProduction(), previousProduction);
					}
				}else{
					double production = generator.getDayAheadMaxProduction();
					if(sumExpectedProduction+production < sumExpectedLoad){
						sumExpectedProduction += generator.setProduction(production);
					} else{
						production = sumExpectedLoad - sumExpectedProduction;

						if (production < generator.getDayAheadMinProduction())
							sumExpectedProduction += generator.setProduction(production);
						else
							sumExpectedProduction += generator.setProduction(generator.getDayAheadMinProduction());
					}
				}

				if(generator.getProduction() == 0){ //Turn off offers for decreasing production if we're not producing anything.
					generator.getDecreaseProductionOffers()[0].setAvailable(false);
					generator.getDecreaseProductionOffers()[1].setAvailable(false);
				}

				nodeList[i] = generator;
			}
		}
		grid[timestep].setNodeList(nodeList);
		return grid[timestep];
	}

	/**
	 * Depending on the state of the grid this method will increase or decrease
	 * production in order to balance the system
	 */
	private static Graph checkGridEquilibrium(Graph grid, int timestep) {
		Node[] nodeList = grid.getNodeList();
		double totalCurrentProduction = 0;
		double sumLoads = 0;
		double realLoad = 0;
		double renewableProduction = productionLoadHandler.calculateRenewableProduction(grid);
		double realProduction = 0; //TODO: check that real production is correctly adjusted when production changes.

		int beginTime = config.getConfigIntValue(CONFIGURATION_TYPE.STORAGE, "beginChargeTime");
		int endTime = config.getConfigIntValue(CONFIGURATION_TYPE.STORAGE, "endChargeTime");

		boolean dischargeAllowed = true;
		// timestep >= 23 && timestep <= 4
		if(timestep >= beginTime && timestep <= endTime){
			grid = storageHandler.chargeStorage(grid);
			dischargeAllowed = false;
		}
		realLoad = productionLoadHandler.calculateLoad(grid);

		// deltaP = 0 needs to be satisfied
		realProduction = productionLoadHandler.calculateProduction(grid);
		// real load fix when real load is negative
		double deltaP = (realProduction - realLoad);
		System.out.println("RealProduction: " + realProduction + " " + "realLoad: "+ realLoad);

		// Check if we need to increase current production
		if (deltaP < 0) {
			System.out.println("Increasing production ");

			List<Offer> offers = new ArrayList<Offer>();

			// find cheapest offers
			for (int i = 0; i < nodeList.length; i++) {
				if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class) {

					Offer[] offerList = ((ConventionalGenerator) nodeList[i]).getIncreaseProductionOffers();
					offers.add(offerList[0]);
					offers.add(offerList[1]);
				}
			}

			// sort offers best value for money
			Collections.sort(offers);

			for (int i = 0; i < offers.size(); i++) {
				Offer offer = offers.get(i);
				double offeredProduction = offer.getProduction();
				if (deltaP < 0 && offer.getAvailable()) {
					((ConventionalGenerator) nodeList[offer.getNodeIndex()]).takeIncreaseOffer(offer.getOfferListId());
					double newProduction = ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).getProduction()
							+ offer.getProduction();


					if (Math.abs(deltaP) <= newProduction) {
						totalCurrentProduction += ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(Math.abs(deltaP));
					} else {
						totalCurrentProduction += ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(newProduction);
					}
					offers.remove(i); // remove offer from list
					deltaP = (totalCurrentProduction - sumLoads); // update demand
				}
			}

			for (int i = 0; i < offers.size(); i++) {
				Offer offer = offers.get(i);
				double offeredProduction = offer.getProduction();

				// check if deltaP isn't satisfied, and if offer is available
				if (deltaP < 0 && offer.getAvailable()) {
					ConventionalGenerator generator = (ConventionalGenerator) nodeList[offer.getNodeIndex()];
					if((deltaP+offeredProduction) <= 0){ // take offer
						double oldProduction = generator.getProduction();
						double newProduction = generator.setProduction(generator.getProduction() + offer.getProduction());
						if(oldProduction != newProduction)
							realProduction += offer.getProduction();
					}else if((deltaP+offeredProduction)>0){ // only take difference between deltaP and offeredProduction
						double oldProduction = generator.getProduction();
						double remainingProduction = offeredProduction-(offeredProduction+deltaP);
						double newProduction = generator.setProduction(generator.getProduction() + remainingProduction);
						if(oldProduction != newProduction)
							realProduction += remainingProduction;
					}

					nodeList[offer.getNodeIndex()] = generator;
					// disable offer from generator
					((ConventionalGenerator) nodeList[offer.getNodeIndex()]).takeDecreaseOffer(offer.getOfferListId());
					deltaP = (realProduction - realLoad); // update deltaP
				}else{
					break; // load is satisfied
				}
			}

		} else if (deltaP > 0) {
			// we need to decrease energy production
			System.out.println("Decreasing production ");

			//TODO: take offers from cheapest nodes to decrease production.
			//TODO: generate real offers not from input file
			List<Offer> offers = new ArrayList<>();

			// find cheapest offers
			for (int i = 0; i < nodeList.length - 1; i++) {
				if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class) {
					Offer[] offerList = ((ConventionalGenerator) nodeList[i]).getDecreaseProductionOffers();
					offers.add(offerList[0]);
					offers.add(offerList[1]);
				}
			}

			// sort offers by cheapest offer
			Collections.sort(offers);

			// decrease production by taking offers
			for (int i = 0; i < offers.size(); i++) {
				Offer offer = offers.get(i);
				double offeredProduction = offer.getProduction();
				ConventionalGenerator convGenerator = null;

				for(int j = 0; j < nodeList.length; j++){
					if(nodeList[j].getClass() == ConventionalGenerator.class)
						convGenerator = ((ConventionalGenerator) nodeList[j]);

					if(convGenerator != null && convGenerator.getNodeId() == offer.getNodeIndex()){
						// check if deltaP isn't satisfied, and if offer is available
						if (deltaP > 0 && offer.getAvailable()) {
							if((deltaP-offeredProduction) >= 0){ // take offer
								double oldProduction = convGenerator.getProduction();
								double newProduction = convGenerator.setProduction(convGenerator.getProduction() - offer.getProduction());
								if(oldProduction != newProduction)
									realProduction -= offer.getProduction();
							}else if(offer.getProduction() > deltaP){ // only take difference between deltaP and offeredProduction
								double oldProduction = convGenerator.getProduction();
								double newProduction = convGenerator.setProduction( convGenerator.getProduction() - (deltaP));
								if(oldProduction != newProduction)
									realProduction -= (deltaP);
							}
							// disable offer from generator
							convGenerator.takeDecreaseOffer(offer.getOfferListId());
							nodeList[j] = convGenerator;
							grid.setNodeList(nodeList);
							deltaP = (realProduction - realLoad); // update deltaP
						}else{
							break; // load is satisfied
						}
					}
				}
			}

			if(deltaP > 0){
				// turn off OIL generators when Production is still to high.
				// loop over generators and when this.maxP < 60 disable generator
				for (int i = 0; i < nodeList.length; i++) {
					if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class && ((ConventionalGenerator) nodeList[i]).getType() == GENERATOR_TYPE.OIL) {
						double maxP = ((ConventionalGenerator) nodeList[i]).getMaxP();

						int disableCheapGeneratorThresholdMaxP = config.
						getConfigIntValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "disableCheapGeneratorThresholdMaxP");

						if(maxP < disableCheapGeneratorThresholdMaxP && deltaP >0){
							realProduction -= ((ConventionalGenerator) nodeList[i]).getProduction();
							((ConventionalGenerator) nodeList[i]).disableProduction();

							deltaP = (realProduction - realLoad); // update deltaP
						}
					}
				}
			}
		} else {
			System.out.print("Grid is balanced ");
			return grid;
		}
		System.out.println("Production after turning conventional generators off: " + realProduction);

		// update nodeList
		grid.setNodeList(nodeList);

		if(dischargeAllowed){
			grid = storageHandler.chargeOrDischargeStorage(grid);
			realProduction = productionLoadHandler.calculateProduction(grid);
			realLoad = productionLoadHandler.calculateLoad(grid);
		}
		System.out.println("Prod after charging storage:" + realProduction);
		System.out.println("Load after charging storage:" + realLoad);
		System.out.println("Renewable prod: " + productionLoadHandler.calculateRenewableProduction(grid));

		//TODO: curtailment decrease production
		if(realProduction - realLoad > 0){
			grid = curtailRenewables(grid, realProduction, realLoad);
			realProduction = productionLoadHandler.calculateProduction(grid);
			realLoad = productionLoadHandler.calculateLoad(grid);
			System.out.println("After cuirtailment Real production: " + productionLoadHandler.calculateProduction(grid) + " Total load: " + productionLoadHandler.calculateLoad(grid));
			System.out.println("Renewable prod: " + productionLoadHandler.calculateRenewableProduction(grid));
		}

		System.out.print("After balancing - ");
		System.out.println("Real production: " + realProduction + " Total load: " + realLoad);
		return grid;
	}

	private static Graph curtailRenewables(Graph grid, double realProduction, double realLoad) {

		double productionTarget = realProduction - realLoad;
		System.out.println("RenewProd before curtailment " + productionLoadHandler.calculateRenewableProduction(grid));
		for( int i = 0; i < grid.getNodeList().length; i++){
			if(grid.getNodeList()[i].getClass() == RenewableGenerator.class){
				RenewableGenerator renew = ((RenewableGenerator)grid.getNodeList()[i]);
				if(productionTarget - renew .getProduction() > 0 && productionTarget != 0){
					productionTarget -= renew .getProduction();
					renew.setProduction(0);
				}else if(productionTarget > 0){
					renew .setProduction(renew .getProduction()-productionTarget);
					productionTarget -= productionTarget;
					break;
				}
				grid.getNodeList()[i] = renew;
			}
		}
		System.out.print("Load: " + productionLoadHandler.calculateLoad(grid));
		System.out.println(" renewProd after curtailment " + productionLoadHandler.calculateRenewableProduction(grid));

		return grid;
	}
}