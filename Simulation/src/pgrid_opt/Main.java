package pgrid_opt;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.math3.stat.descriptive.summary.Product;

import pgrid_opt.DataModelPrint;
import pgrid_opt.Graph;
import pgrid_opt.InstanceRandomizer;
import pgrid_opt.Parser;

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

//	private final static String SUMMER_LOAD_CURVE = "./Expected Load summer.csv";

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

		if(OS.startsWith("Windows")){
			SUMMER_LOAD_CURVE = "../Expected Load summer.csv";
		}else{
			SUMMER_LOAD_CURVE = "./Expected Load summer.csv";
		}


		long starttime = System.nanoTime();
		float[] wind = null;
		float[] solar = null;
		float[] loads = null;
		float wcost = 0.0f;  //wind cost
		float scost = 0.0f; //solar cost
		Graph[] timestepsGraph = null;
		Parser parser = new Parser();
		String[] s = parser.parseArg(args);
		String path = s[1]; //path to the input
		String outpath1 = "input";
		String outpath2 = ".mod";
		String solpath1 = "glpsol -d ";
		String solpath2 = " -m ";
		String solpath3 = "sol";
		String model = s[0]; //path to the model
		String dirpath = s[2]; //path to the output
		Object[] o = parser.parseData(path);
		Graph graph = (Graph) o[0]; //Initial graph created from the input file
		solar = (float[]) o[1]; //Hourly production values for solar
		wind = (float[]) o[2]; //hourly production values for wind
		loads = (float[]) o[3]; //Total hourly load of all sinks

		DataModelPrint mp = new DataModelPrint();
		Process proc = null;

		//Hashmap for the 4 different seasonal curves
		HashMap<String, Double[]> seasonalLoadCurve = new HashMap<>();
		seasonalLoadCurve.put("summer", parser.parseDayLoadCurve(SUMMER_LOAD_CURVE));
		//Set the seasonal curve
		seasonalLoadCurve = setSeasonalVariety(seasonalLoadCurve);

		for ( int numOfSim=0; numOfSim < Integer.parseInt(s[3]); numOfSim++){
			System.out.println("Simulation: "+ numOfSim);
			InstanceRandomizer instanceRandomizer = new InstanceRandomizer();
			timestepsGraph = new Graph[loads.length + 1];
			timestepsGraph = instanceRandomizer.creategraphs(graph, timestepsGraph, solar, wind, loads);
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

				setGridState(timestepsGraph[i], i);
				checkGridEquilibrium(timestepsGraph[i], i);

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
						// TODO Auto-generated catch block
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
		//For weibull distribution: alpha = 1.6, beta = 8
		//For normal distribution: mean = 0, sigma = 0.05
		MontoCarloHelper monteCarloHelper = new MontoCarloHelper(1.6, 8, 0, 0.04);

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
					} else if(mcDraw <= V_CUT_IN && mcDraw <= V_CUT_OFF){
						float production = ((RewGenerator) graph.getNodeList()[j]).getMinP();
						((RewGenerator) graph.getNodeList()[j]).setProduction(production);
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

				/*
				if (realLoad >= 0)
					((Consumer) graph.getNodeList()[j]).setLoad(realLoad);
				else
					((Consumer) graph.getNodeList()[j]).setLoad(0);
					*/
			}
			else if(graph.getNodeList()[j] != null && graph.getNodeList()[j].getClass() == Storage.class){
				// storage node currenlty not being adapted
			}
		}

		//Set the load of a consumer using the previously calculated cumulative load error.
		for (int i = 0; i < graph.getNodeList().length-1; i++){
			if(graph.getNodeList()[i] != null && graph.getNodeList()[i].getClass() == Consumer.class){
				((Consumer) graph.getNodeList()[i]).setLoad((float) (((Consumer) graph.getNodeList()[i]).getLoad() + sumLoadError));
			}
		}
	}


	/**
	 * Sets the state of conventional generators to on or off.
	 * @param graphs
	 * @param timestep
	 * @param node
	 * @param currentTimeStep
	 * @param mcDraw
	 * @return
	 */
	private static Graph handleConventionalGenerator(Graph graph, int node, int currentTimeStep, double mcDraw){
		double convGeneratorProb = 0.5; //Probability of failure for conventional generators

		if(((ConventionalGenerator) graph.getNodeList()[node]).getReactivateAtTimeStep() == 0){//0 means that the reactor can fail.
			if(mcDraw < convGeneratorProb){
				//System.out.println(mcDraw);
				//Our draw is smaller meaning that the generator has failed.
				float lastminp = ((ConventionalGenerator) graph.getNodeList()[node]).getMinP();
				float lastmaxp = ((ConventionalGenerator) graph.getNodeList()[node]).getMaxP();

				((ConventionalGenerator) graph.getNodeList()[node]).setLastminp(lastminp);
				((ConventionalGenerator) graph.getNodeList()[node]).setLastmaxp(lastmaxp);

				((ConventionalGenerator) graph.getNodeList()[node]).setMinP(0);
				((ConventionalGenerator) graph.getNodeList()[node]).setMaxP(0);

				//Set the point at which the generator must be reactivated
				// time resultion has changed to hourly, still have to determine the proper rate fore reactivation
				((ConventionalGenerator) graph.getNodeList()[node]).setReactivateAtTimeStep(currentTimeStep + 1);
			}
		}else if(((ConventionalGenerator) graph.getNodeList()[node]).getReactivateAtTimeStep() < currentTimeStep) {
			//We have to reactivate the generator because it's been offline for enough steps.
			float minp = ((ConventionalGenerator) graph.getNodeList()[node]).getLastminp();
			float maxp = ((ConventionalGenerator) graph.getNodeList()[node]).getLastmaxp();
			((ConventionalGenerator) graph.getNodeList()[node]).setMinP(minp);
			((ConventionalGenerator) graph.getNodeList()[node]).setMaxP(maxp);
		}

		return graph;
	}

	/**
	 * Depending on the state of the grid this method will increase or decrease production in order to balance the system
	 */
	private static void checkGridEquilibrium(Graph grid, int timestep){

		Node[] nodeList = grid.getNodeList();
		double sumLoads = 0;
		double renewableProduction = 0;
		double conventionalProduction= 0;

		/*
		 * In this loop we calculate the total demand, the total ->current<- production and, total ->current<- production of renewable generators.
		 */
		for(int i = 0; i < nodeList.length-1; i++)
		{
			if(nodeList[i] != null && nodeList[i].getClass() == Consumer.class){
				sumLoads += ((Consumer)nodeList[i]).getLoad();
			} else if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class){
				conventionalProduction += ((ConventionalGenerator)nodeList[i]).getProduction();
			} else if (nodeList[i] != null && nodeList[i].getClass() == RewGenerator.class){
				renewableProduction += ((RewGenerator)nodeList[i]).getProduction();
			}
		}
		double totalCurrentProduction = conventionalProduction + renewableProduction;

		//Check if we need to increase current production
		if((totalCurrentProduction  - sumLoads) > 0){
			System.out.println("Increasing production");

			//We need to increase production until it meets demand.
			for(int i = 0; i < grid.getNodeList().length-1; i++){
				if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class){
					if (totalCurrentProduction+((ConventionalGenerator)nodeList[i]).getMaxP() > sumLoads){
						totalCurrentProduction += ((ConventionalGenerator)nodeList[i]).setProduction(sumLoads-totalCurrentProduction); //Set production to the remainder so we can meet the demand exactly
					}else
						totalCurrentProduction += ((ConventionalGenerator)nodeList[i]).setProduction( ((ConventionalGenerator)nodeList[i]).getProduction() + ((ConventionalGenerator)nodeList[i]).getMaxP()/4);
				}
			}

		} else if ((totalCurrentProduction  - sumLoads) < 0) {
			//we need to decrease energy production
			System.out.println("Decreasing production");

			for ( int i = grid.getNodeList().length-1; i >= 0; i--){
				if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class){
					if (totalCurrentProduction-((ConventionalGenerator)nodeList[i]).getMinP() > sumLoads){
						totalCurrentProduction += ((ConventionalGenerator)nodeList[i]).setProduction( ((ConventionalGenerator)nodeList[i]).getProduction() - ((ConventionalGenerator)nodeList[i]).getMaxP()/4);
					} else {
						totalCurrentProduction += ((ConventionalGenerator)nodeList[i]).setProduction(sumLoads-totalCurrentProduction);
					}
				}
			}
		} else {
			System.err.println("Grid is balanced");
			return;//production and demand are balanced.
		}
	}
}