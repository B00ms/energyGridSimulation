package pgrid_opt;

import com.typesafe.config.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Date;
import java.time.Instant;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Delayed;

import com.typesafe.config.ConfigFactory;

import net.e175.klaus.solarpositioning.AzimuthZenithAngle;
import net.e175.klaus.solarpositioning.DeltaT;
import net.e175.klaus.solarpositioning.Grena3;
import net.e175.klaus.solarpositioning.SPA;
public class Main {

	//Path to the summer load curve
	private static String OS = System.getProperty("os.name");
	private static Config conf;

	//From cheapest to most expensive.
	//private final static String[] priceIndex = {"nuclear", "oil", "coal"};
	//TODO: price index is currently not used.
	private final static Map<String, Double[]> PRICE_INDEX;
	static {
		//Production prices for 4 different levels of production 25%, 50%, 75%, 100%
		Double[] nuclearPrices = 	{1.0, 2.0, 3.0, 4.0};
		Double[] oilPrices = 		{1.0, 2.0, 3.0, 4.0};
		Double[] coalPrices = 		{1.0, 2.0, 3.0, 4.0};

		Map<String, Double[]> tempMap = new HashMap<>();
		tempMap.put("nuclear", nuclearPrices);
		tempMap.put("oil", oilPrices);
		tempMap.put("coal", coalPrices);
		PRICE_INDEX = Collections.unmodifiableMap(tempMap);
		}

	public static void main(String[] args) {

		String SUMMER_LOAD_CURVE;
		long starttime = System.nanoTime();
		float wcost = 0.0f;  //wind cost
		float scost = 0.0f; //solar cost
		Graph[] timestepsGraph = null;
		Parser parser = new Parser();
		Graph graph;

		if(OS.startsWith("Windows") || OS.startsWith("Linux")){
			conf = ConfigFactory.parseFile(new File("../config/application.conf"));
			graph = parser.parseData("../network.csv");
		}else{
			conf = ConfigFactory.parseFile(new File("config/application.conf"));
			graph = parser.parseData("./network.csv");
		}

		// load general config
		Config generalConf = conf.getConfig("general");
		String model = generalConf.getString("model-file"); //path to the model
		String dirpath = generalConf.getString("output-folder"); //path to the output
		String path = generalConf.getString("input-file"); // parse old input file

		// load glpsol config
		Config glpsolConf = conf.getConfig("glpsol-config");
		String outpath1 = glpsolConf.getString("outpath1");
		String outpath2 = glpsolConf.getString("outpath2");
		String solpath1 = glpsolConf.getString("solpath1");
		String solpath2 = glpsolConf.getString("solpath2");

		DataModelPrint mp = new DataModelPrint();
		Process proc = null;

		// load simulation limit
		int simLimit = generalConf.getInt("simulation-runs");
		for ( int numOfSim=0; numOfSim < simLimit; numOfSim++){
			System.out.println("Simulation: "+ numOfSim);
			SimulationStateInitializer simulationState = new SimulationStateInitializer();
			timestepsGraph = new Graph[generalConf.getInt(("numberOfTimeSteps"))];
			timestepsGraph = simulationState.creategraphs(graph, timestepsGraph);
			int i = 0;

			double load = 0;
			for (int q = 0; q < timestepsGraph[0].getNodeList().length-1; q++){
				if(timestepsGraph[0].getNodeList()[q] != null && timestepsGraph[0].getNodeList()[q].getClass() == Consumer.class){
					load += ((Consumer)timestepsGraph[0].getNodeList()[q]).getLoad();
				}
			}
			System.out.println("Consumer Load: " + load);

			String solutionPath = dirpath+"simRes"+numOfSim+"";
			try {
				Files.createDirectories(Paths.get(solutionPath)); //create a new directory to safe the output in
			} catch (IOException e1) {
				e1.printStackTrace();
			}

			timestepsGraph = setLoadError(timestepsGraph);
			while (i < timestepsGraph.length) {

				randomizeGridState(timestepsGraph[i], i);
				timestepsGraph[i] = checkGridEquilibrium(timestepsGraph[i], i);

				mp.printData(timestepsGraph[i], String.valueOf(dirpath) + outpath1 + i + outpath2, Integer.toString(i)); //This creates a new input file.
				timestepsGraph[i].printGraph(i);
				try {
					StringBuffer output = new StringBuffer();
					String command = String.valueOf(solpath1) + outpath1 + i + outpath2 + solpath2 + model;
					command = command + " --nopresol --output filename.out ";
					System.out.println(command);

					proc = Runtime.getRuntime().exec(command, null, new File(dirpath));
					proc.waitFor();

					if(new File(dirpath+"/sol"+i+".txt").exists())
						Files.move(Paths.get(dirpath+"/sol"+i+".txt"), Paths.get(solutionPath+"/sol"+i+".txt"), StandardCopyOption.REPLACE_EXISTING);

					if(new File(dirpath+"/filename.out").exists())
						Files.move(Paths.get(dirpath+"/filename.out"), Paths.get(solutionPath+"/filename.out"), StandardCopyOption.REPLACE_EXISTING);

					if(new File(dirpath+"/update.txt").exists())
						Files.copy(Paths.get(dirpath+"/update.txt"), Paths.get(solutionPath+"/update"+i+".txt"), StandardCopyOption.REPLACE_EXISTING);

					BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream())); //Using the new input file, we apply the model to solve the cost function given the new state of the grid.
					String line = "";
					while ((line = reader.readLine()) != null) {
						output.append(String.valueOf(line) + "\n");
					}
					System.out.println(output);
					if (graph.getNstorage() > 0) {
						timestepsGraph[i] = parser.parseUpdates(String.valueOf(dirpath) + "update.txt", timestepsGraph[i]); //Keeps track of the new state for storages.
						if (i < 23)
							timestepsGraph[i + 1] = simulationState.updateStorages(timestepsGraph[i], timestepsGraph[i + 1]); //Apply the new state of the storage for the next time step.
					}
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
				++i;
			}

			if (graph.getNstorage() > 0) {
				mp.printStorageData(timestepsGraph, String.valueOf(dirpath) + "storage.txt");

				if(new File(dirpath+"/storage.txt").exists())
					try {
						Files.copy(Paths.get(dirpath+"/storage.txt"), Paths.get(solutionPath+"/storage.txt"), StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException e) {
						e.printStackTrace();
					}
			}
		}

		long endtime = System.nanoTime();
		long duration = endtime - starttime;
		System.out.println("Time used:" + duration / 1000000 + " millisecond");
	}

	private static Graph[] setLoadError(Graph[] timestepsGraph) {

		MontoCarloHelper mcHelper = new MontoCarloHelper();
		for (int i = 0; i < timestepsGraph.length; i++){
			double totalLoad = 0;
			for(int n = 0; n < timestepsGraph[i].getNodeList().length; n++){
				if(timestepsGraph[i].getNodeList()[n] != null && timestepsGraph[i].getNodeList()[n].getClass() == Consumer.class){
					double mcDraw = mcHelper.getRandomNormDist();
					double  previousError = 0;
					//if(i > 0){
						//Calculate and set the load error of a single consumer.
						double error = (((Consumer)timestepsGraph[i].getNodeList()[n]).getLoad() * mcDraw);
						totalLoad += ((Consumer)timestepsGraph[i].getNodeList()[n]).getLoad();
						if (i > 0)
							previousError = ((Consumer)timestepsGraph[i-1].getNodeList()[n]).getLoadError();
						//System.out.print("NodeID: " + n);
						//System.out.print(" time: " + i + " mcDraw: " +  + mcDraw + " error: " + error + " previousError: " + previousError);

						((Consumer)timestepsGraph[i].getNodeList()[n]).setLoadError(error+previousError); //plus load error of i-1 makes it cumulative.

						//Calculate and set the real load of a single consumer
						//System.out.print(" Load: " + ((Consumer)timestepsGraph[i].getNodeList()[n]).getLoad());
						double realLoad = ((Consumer)timestepsGraph[i].getNodeList()[n]).getLoad() + ((Consumer)timestepsGraph[i].getNodeList()[n]).getLoadError();
						((Consumer)timestepsGraph[i].getNodeList()[n]).setLoad(realLoad);

						//System.out.println(" realLoad: " + realLoad);
					//}
				}
			}
			//System.out.println("Total load: "+ totalLoad);
		}

		/*
		//System.out.println("next I: ");
		for (int i = 0; i < currentTimeStep; i++){
			for(int n = 0; n < timestepsGraph[i].getNodeList().length; n++){
				if(timestepsGraph[i].getNodeList()[n] != null && timestepsGraph[i].getNodeList()[n].getClass() == Consumer.class){

				}
			}
		}
		 */
		return timestepsGraph;

	}

	/**
	 * Set the state of generators and loads.
	 * @return Graphs of which the state has been changed using Monte Carlo draws
	 */
	private static void randomizeGridState(Graph graph, int currentTimeStep){
		MontoCarloHelper monteCarloHelper = new MontoCarloHelper();

		double sumLoadError = 0;
		//int i = currentTimeStep;
		//for(int i = 0; i < graphs.length-1; i ++){
		for (int j=0; j < graph.getNodeList().length-1; j++){
			//Check the class of the current node and deal with it accordingly.
			if(graph.getNodeList()[j] != null && (graph.getNodeList()[j].getClass() == ConventionalGenerator.class ||
					graph.getNodeList()[j].getClass() ==  RewGenerator.class)){
				String generatorType = ((Generator) graph.getNodeList()[j]).getType();
				double mcDraw = 0; //This will hold our Monte Carlo draw (hahaha mac draw)
				switch (generatorType) {
				case "H" : //Hydro-eletric generator
					//Ignore this for now, might be added at a later stage
					break;
				case "O": // Oil Thermal generator
					mcDraw = monteCarloHelper.getRandomUniformDist();
					//System.out.println(mcDraw);
					graph = handleConventionalGenerator(graph, j, currentTimeStep, mcDraw);
					break;
				case "N": // Nuclear Thermal generator
					mcDraw = monteCarloHelper.getRandomUniformDist();
					//System.out.println(mcDraw);
					graph = handleConventionalGenerator(graph, j, currentTimeStep, mcDraw);
					break;
				case "C": // Coal Thermal generator
					mcDraw = monteCarloHelper.getRandomUniformDist();
					//System.out.println(mcDraw);
					graph = handleConventionalGenerator(graph, j, currentTimeStep, mcDraw);
					break;
				case "W": //Wind park generator
					mcDraw = monteCarloHelper.getRandomWeibull();
					//System.out.println(mcDraw);

					double vCutIn = conf.getConfig("windGenerator").getInt("vCutIn");
					double vCutOff = conf.getConfig("windGenerator").getInt("vCutOff");
					double vRated = conf.getConfig("windGenerator").getInt("vRated");
					double pRated = conf.getConfig("windGenerator").getInt("pRated");

					if(mcDraw <= vCutIn || mcDraw >= vCutOff){
						//Wind speed is outside the margins
						((RewGenerator) graph.getNodeList()[j]).setProduction(0);
					} else if(mcDraw >= vCutIn && mcDraw <= vRated){
						//In a sweet spot for max wind production
						double production = (pRated*((Math.pow(mcDraw, 3)-Math.pow(vCutIn, 3))/(Math.pow(vRated, 3)-Math.pow(vCutIn, 3))));//Should be the same as the matlab from Laura
						((RewGenerator) graph.getNodeList()[j]).setProduction(production);
					} else if (vRated <= mcDraw && mcDraw <= vCutOff ) {
						((RewGenerator) graph.getNodeList()[j]).setProduction(pRated);
					}
					break;
				case "S": //Solar generator
					//Let's ignore the sun as well for now...
					mcDraw = monteCarloHelper.getRandomGamma();

					//TODO: move to configuration file, or make it a constant
					double irradianceConstant = conf.getConfig("solarGenerator").getDouble("irradianceConstant"); //Solar constant
					double eccentricityCorrFactor = 1+0.033; //Eccentricity correction Factor
					double langitude = 53.218705; //TODO: should maybe be placed within solar generator nodes so we can easily switch locations
					double longitude =  6.567793;

					int month = Calendar.DECEMBER;
					GregorianCalendar calendar = new GregorianCalendar(2016, month, 14, currentTimeStep, 0);
					double deltaT = DeltaT.estimate(calendar);
					//AzimuthZenithAngle azimuthZenithAgnle = Grena3.calculateSolarPosition(calendar, langitude, longitude, deltaT);
					//double zenithAngle = azimuthZenithAgnle.getZenithAngle();
					GregorianCalendar[] sunriseset = SPA.calculateSunriseTransitSet(calendar, langitude, longitude, deltaT);

					int sunrise = sunriseset[0].get(Calendar.HOUR_OF_DAY);
					int sunset = sunriseset[2].get(Calendar.HOUR_OF_DAY);

					// We want to find the maximum Extraterrestial irradiance of the day.
					double extratIrradianceMax = 0;
					for (int i = 0; i < 24; i ++){
						GregorianCalendar cal = new GregorianCalendar(2016, month, 14, i, 0);
						AzimuthZenithAngle azimuthZenithAgnle = Grena3.calculateSolarPosition(cal, langitude, longitude, deltaT);
						double zenithAngle = azimuthZenithAgnle.getZenithAngle();

						double extratIrradiance = irradianceConstant * eccentricityCorrFactor * Math.cos( (2*Math.PI*calendar.get(Calendar.DAY_OF_YEAR)) / 365) * Math.cos(zenithAngle);
						if (extratIrradiance > extratIrradianceMax)
							extratIrradianceMax = extratIrradiance;
					}
					double sMax = extratIrradianceMax * mcDraw;
					double irradiance;
					if((currentTimeStep <= sunrise) || (currentTimeStep >= sunset))
						irradiance = 0;
					else
						irradiance = sMax * Math.sin(Math.PI * (currentTimeStep - sunrise) / (sunset - sunrise));


					double efficiency = conf.getConfig("solarGenerator").getDouble("panelEfficiency");
					//surface array of panels in m², efficiency, irradiance of panels on the horizontal plane.
					double production = 45 *  efficiency * irradiance;

					((RewGenerator) graph.getNodeList()[j]).setProduction(production);
					//System.out.println("sunRise:" + sunrise + " currentTime:" + currentTimeStep + " sunset:" + sunset + " production:" + production +  " max irradiance:" + extratIrradianceMax + " MC draw:" + mcDraw + " nodeId:" + ((RewGenerator) graph.getNodeList()[j]).getNodeId());

					break;
				}
			}
		}
	}

	/**
	 * Sets the state of conventional generators to on or off.
	 * @param graph
	 * @param node
	 * @param currentTimeStep
	 * @param mcDraw
	 * @return
	 */
	private static Graph handleConventionalGenerator(Graph graph, int node, int currentTimeStep, double mcDraw){
		//double convGeneratorProb = 0.5; //Probability of failure for conventional generators

		if(((ConventionalGenerator) graph.getNodeList()[node]).getGeneratorFailure() == false){//0 means that the reactor can fail.
			if(mcDraw < 1/((ConventionalGenerator) graph.getNodeList()[node]).getMTTF()){
				//Our draw is smaller meaning that the generator has failed.
				((ConventionalGenerator) graph.getNodeList()[node]).setGeneratorFailure(true);
			}
		}
		return graph;
	}

	/**
	 * Depending on the state of the grid this method will increase or decrease production in order to balance the system
	 */
	private static Graph checkGridEquilibrium(Graph grid, int timestep){
		Node[] nodeList = grid.getNodeList();
		double sumLoads = 0;
		double renewableProduction = 0;
		double conventionalProduction= 0;
		double sumCurrentStorage  = 0;
		double maximumStorageCapacity = 0;
		double minumumStorageCapacity = 0;

		/*
		 * In this loop we calculate the total demand, the total ->current<- production and, total ->current<- production of renewable generators.
		 */
		for(int i = 0; i < nodeList.length; i++)
		{
			if(nodeList[i] != null && nodeList[i].getClass() == Consumer.class){
				sumLoads += ((Consumer)nodeList[i]).getLoad();
			} else if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class){
				conventionalProduction += ((ConventionalGenerator)nodeList[i]).getProduction();
			} else if (nodeList[i] != null && nodeList[i].getClass() == RewGenerator.class){
				renewableProduction += ((RewGenerator)nodeList[i]).getProduction();
			} else if (nodeList[i] != null && nodeList[i].getClass() == Storage.class){
				sumCurrentStorage += ((Storage) nodeList[i]).getCurrentCharge();
				maximumStorageCapacity += ((Storage) nodeList[i]).getMaximumCharge();
				minumumStorageCapacity += ((Storage) nodeList[i]).getMinimumCharge();

			}
		}
		double totalCurrentProduction = conventionalProduction + renewableProduction;

		Config convGeneratorConf = conf.getConfig("conventionalGenerator");
		Double maxProductionIncrease = convGeneratorConf.getDouble("maxProductionIncrease");
		Double dayAheadLimitMax = convGeneratorConf.getDouble("dayAheadLimitMax");
		Double dayAheadLimitMin = convGeneratorConf.getDouble("dayAheadLimitMin");

		//Check if we need to increase current production
		if((totalCurrentProduction  - sumLoads) < 0){
			System.out.println("Increasing production");

			//We need to increase production until it meets demand.
			for(int i = 0; i < grid.getNodeList().length-1; i++){
				if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class){
					if (totalCurrentProduction+((ConventionalGenerator)nodeList[i]).getMaxP()*dayAheadLimitMax > sumLoads){
						totalCurrentProduction += ((ConventionalGenerator)nodeList[i]).setProduction(sumLoads-totalCurrentProduction); //Set production to the remainder so we can meet the demand exactly
					}else {
						//totalCurrentProduction += ((ConventionalGenerator) nodeList[i]).setProduction(((ConventionalGenerator) nodeList[i]).getProduction() + ((ConventionalGenerator) nodeList[i]).getMaxP());
						totalCurrentProduction += ((ConventionalGenerator) nodeList[i]).setProduction(((ConventionalGenerator) nodeList[i]).getMaxP());
					}
				}
			}
		} else if ((totalCurrentProduction  - sumLoads) > 0) {
			//we need to decrease energy production
			System.out.println("Decreasing production");

			for ( int i = grid.getNodeList().length-1; i >= 0; i--){
				if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class){
					if (totalCurrentProduction-((ConventionalGenerator)nodeList[i]).getMinP()*dayAheadLimitMin > sumLoads){
						//totalCurrentProduction += ((ConventionalGenerator)nodeList[i]).setProduction( ((ConventionalGenerator)nodeList[i]).getProduction() - ((ConventionalGenerator)nodeList[i]).getMaxP());
						totalCurrentProduction += ((ConventionalGenerator)nodeList[i]).setProduction( ((ConventionalGenerator)nodeList[i]).getMinP());

					} else {
						totalCurrentProduction += ((ConventionalGenerator)nodeList[i]).setProduction(sumLoads-totalCurrentProduction);
					}
				}
			}
		} else {
			System.out.println("Grid is balanced");
			return null;//production and demand are balanced.
		}

		/*
		 * Deal with curtailment and shedding
		 */
		if(totalCurrentProduction > sumLoads){
			System.out.println("curtailment");
			for ( int i = 0; i < nodeList.length; i++){
				if (nodeList[i] != null && nodeList[i].getClass() == Storage.class){
					if(totalCurrentProduction - ((Storage)nodeList[i]).getMaximumCharge() > sumLoads){
						totalCurrentProduction -= ((Storage)nodeList[i]).setCurrentCharge(((Storage)nodeList[i]).getMaximumCharge());
					}else
						totalCurrentProduction -= ((Storage)nodeList[i]).setCurrentCharge(((Storage)nodeList[i]).setCurrentCharge(sumLoads-totalCurrentProduction));
				}
			}
		} else if(totalCurrentProduction < sumLoads){
			for ( int i = 0; i < nodeList.length; i++){
				if (nodeList[i] != null && nodeList[i].getClass() == Storage.class){
					if(totalCurrentProduction + ((Storage)nodeList[i]).getMaximumCharge() > sumLoads){
						totalCurrentProduction += ((Storage)nodeList[i]).discharge();
					}else
						totalCurrentProduction += ((Storage)nodeList[i]).discharge();
				}
			}
			System.out.println("Shedding");
		} else{
			//Production and load are balanced.
		}
		return grid;
	}

}