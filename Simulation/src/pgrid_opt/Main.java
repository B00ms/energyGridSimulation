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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import com.typesafe.config.ConfigFactory;
public class Main {

	/*
	 * Cut in and cut out margins for wind energy
	 * V_rated is the optimal value for wind... I think.
	 */
	private final static double V_CUT_IN = 3;
	private final static double V_CUT_OFF = 25;
	private final static double V_RATED = 12;
	private final static double P_RATED = 220;

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

		if(OS.startsWith("Windows") || OS.startsWith("Linux")){
			SUMMER_LOAD_CURVE = "../Expected Load summer.csv";
			conf = ConfigFactory.parseFile(new File("../config/application.conf"));
		}else{
			SUMMER_LOAD_CURVE = "..q/Expected Load summer.csv";
			conf = ConfigFactory.parseFile(new File("config/application.conf"));
		}

		long starttime = System.nanoTime();

		Float[] loads = null;
		float wcost = 0.0f;  //wind cost
		float scost = 0.0f; //solar cost
		Graph[] timestepsGraph = null;
		Parser parser = new Parser();
		String outpath1 = "input";
		String outpath2 = ".mod";
		String solpath1 = "glpsol -d ";
		String solpath2 = " -m ";
		//String solpath3 = "sol";

		Config generalConf = conf.getConfig("general");
		String model = generalConf.getString("model-file"); //path to the model
		String dirpath = generalConf.getString("output-folder"); //path to the output
		String path = generalConf.getString("input-file"); // parse old input file

		loads = parser.parseExpectedHourlyLoad();

		Graph graph = parser.parseData("../network.csv");

		DataModelPrint mp = new DataModelPrint();
		Process proc = null;

		//Hashmap for the 4 different seasonal curves
		HashMap<String, Double[]> seasonalLoadCurve = new HashMap<>();
		HashMap<String, Double[][]> expectedProduction = new HashMap<>();
		seasonalLoadCurve.put("summer", parser.parseCSV(SUMMER_LOAD_CURVE));

		seasonalLoadCurve = setSeasonalVariety(seasonalLoadCurve);

		// load simulation limit
		int simLimit = generalConf.getInt("simulation-runs");
		for ( int numOfSim=0; numOfSim < simLimit; numOfSim++){
			System.out.println("Simulation: "+ numOfSim);
			InstanceRandomizer instanceRandomizer = new InstanceRandomizer();
			timestepsGraph = new Graph[generalConf.getInt(("numberOfTimeSteps"))];
			timestepsGraph = instanceRandomizer.creategraphs(graph, timestepsGraph, loads);
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

			while (i < timestepsGraph.length - 1) {

				//timestepsGraph[i] = setExpectedProduction(timestepsGraph[i], i, expectedProduction.get("summer"));
				setGridState(timestepsGraph[i], i);
				timestepsGraph[i] = checkGridEquilibrium(timestepsGraph[i], i);

				mp.printData(timestepsGraph[i], String.valueOf(dirpath) + outpath1 + i + outpath2, Integer.toString(i)); //This creates a new input file.
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
						timestepsGraph[i + 1] = instanceRandomizer.updateStorages(timestepsGraph[i], timestepsGraph[i + 1]); //Apply the new state of the storage for the next time step.
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


	/**
	 * Increases or decreases the high of the seasonal curve according to some random double.
	 *@param seasonalLoadCurve
	 * @return the seasonal curve adjust up or down.
	 */
	private static HashMap<String, Double[]> setSeasonalVariety(HashMap<String, Double[]> seasonalLoadCurve) {

		Iterator<String> it = seasonalLoadCurve.keySet().iterator();

		while(it.hasNext()){
			String key = it.next();
			Double[] seasoncurve  = seasonalLoadCurve.get(key);

			double multiplicationFactor = ThreadLocalRandom.current().nextDouble(3);
			for(int i = 0; i < seasoncurve.length-1; i++){
				seasoncurve[i] = seasoncurve[i] * (multiplicationFactor);
			}
			seasonalLoadCurve.put(key, seasoncurve);
		}
		return seasonalLoadCurve;
	}

	/**
	 * Set the state of generators and loads.
	 * @return Graphs of which the state has been changed using Monte Carlo draws
	 */
	private static void setGridState(Graph graph, int currentTimeStep){
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

					if(mcDraw <= V_CUT_IN || mcDraw >= V_CUT_OFF){
						//Wind speed is outside the margins
						((RewGenerator) graph.getNodeList()[j]).setProduction(0);
					} else if(mcDraw >= V_CUT_IN && mcDraw <= V_RATED){
						//In a sweet spot for max wind production
						double production = (P_RATED*((Math.pow(mcDraw, 3)-Math.pow(V_CUT_IN, 3))/(Math.pow(V_RATED, 3)-Math.pow(V_CUT_IN, 3))));//Should be the same as the matlab from Laura
						((RewGenerator) graph.getNodeList()[j]).setProduction(production);
					} else if (V_RATED <= mcDraw && mcDraw <= V_CUT_OFF ) {
						((RewGenerator) graph.getNodeList()[j]).setProduction(P_RATED);
					}
					break;
				case "S": //Solar generator
					//Let's ignore the sun as well for now...
					break;
				}
			}
			else if(graph.getNodeList()[j] != null && graph.getNodeList()[j].getClass() == Consumer.class){
				//Consumer so we want to calculate and set the real demand using the load error.
				double mcDraw = monteCarloHelper.getRandomNormDist();
				//System.out.println(mcDraw);
				sumLoadError += (((Consumer) graph.getNodeList()[j]).getLoad() * mcDraw);
			}
			else if(graph.getNodeList()[j] != null && graph.getNodeList()[j].getClass() == Storage.class){
				// storage node currenlty not being adapted
			}
		}
		System.out.println("Load error: " + sumLoadError);
		//Set the load of a consumer using the previously calculated cumulative load error.
		for (int i = 0; i < graph.getNodeList().length-1; i++){
			if(graph.getNodeList()[i] != null && graph.getNodeList()[i].getClass() == Consumer.class){
				((Consumer) graph.getNodeList()[i]).setLoad((float) (((Consumer) graph.getNodeList()[i]).getLoad() + sumLoadError));
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

	private static Graph setExpectedProduction(Graph graph, int timeStep, Double[][] expectedProduction){

		for(int i = 0; i < graph.getNodeList().length-1; i++){
			if(graph.getNodeList()[i].getClass() == ConventionalGenerator.class){
				//TODO: this is a problem because we have 42 generators but we dont know the correct order of said generators.
				//TODO: so lets use json to define the generator, then use the IDs from that definition to set the expected production.
				((ConventionalGenerator) graph.getNodeList()[i]).setProduction(expectedProduction[i][i]);
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
				System.out.println(((Storage) nodeList[i]).getNodeId());
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
			System.err.println("Increasing production");

			//We need to increase production until it meets demand.
			for(int i = 0; i < grid.getNodeList().length-1; i++){
				if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class){
					if (totalCurrentProduction+((ConventionalGenerator)nodeList[i]).getMaxP()*dayAheadLimitMax > sumLoads){
						totalCurrentProduction += ((ConventionalGenerator)nodeList[i]).setProduction(sumLoads-totalCurrentProduction); //Set production to the remainder so we can meet the demand exactly
					}else {
						totalCurrentProduction += ((ConventionalGenerator) nodeList[i]).setProduction(((ConventionalGenerator) nodeList[i]).getProduction() + ((ConventionalGenerator) nodeList[i]).getMaxP());
					}
				}
			}
		} else if ((totalCurrentProduction  - sumLoads) > 0) {
			//we need to decrease energy production
			System.out.println("Decreasing production");

			for ( int i = grid.getNodeList().length-1; i >= 0; i--){
				if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class){
					if (totalCurrentProduction-((ConventionalGenerator)nodeList[i]).getMinP()*dayAheadLimitMin > sumLoads){
						totalCurrentProduction += ((ConventionalGenerator)nodeList[i]).setProduction( ((ConventionalGenerator)nodeList[i]).getProduction() - ((ConventionalGenerator)nodeList[i]).getMaxP());
					} else {
						totalCurrentProduction += ((ConventionalGenerator)nodeList[i]).setProduction(sumLoads-totalCurrentProduction);
					}
				}
			}
		} else {
			System.err.println("Grid is balanced");
			return null;//production and demand are balanced.
		}

		if(totalCurrentProduction > sumLoads){
			//TODO: Load curtailment, we're producing more then the demand.
			//TODO: We could try and charge the batteries if they are not at capacity and if the demand is low.
			System.out.println("curtailment");
			sumCurrentStorage = 0;
			for ( int i = 0; i < nodeList.length; i++){
				if (nodeList[i] != null && nodeList[i].getClass() == Storage.class){

					if(totalCurrentProduction - ((Storage)nodeList[i]).getMaximumCharge() > sumLoads){
						totalCurrentProduction -= ((Storage)nodeList[i]).setCurrentCharge(((Storage)nodeList[i]).getMaximumCharge());
						sumCurrentStorage += ((Storage)nodeList[i]).getCurrentCharge();
					}else
						totalCurrentProduction -= ((Storage)nodeList[i]).setCurrentCharge(((Storage)nodeList[i]).setCurrentCharge(sumLoads-totalCurrentProduction));
				}
			}
		} else if(totalCurrentProduction < sumLoads){
			//TODO: Load shedding, we cannot meet the demand hence we have to load shedding.
			//TODO: Or we can use the batteries to try and meet the demand
			System.out.println("Shedding");
		} else{
			//Production and load are balanced.
		}
		return grid;
	}

}