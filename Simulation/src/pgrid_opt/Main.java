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
	private final static double V_CUT_IN = 4;
	private final static double V_CUT_OFF = 25;
	private final static double V_RATED = 12;

	//Path to the summer load curve
	private final static String SUMMER_LOAD_CURVE = "./Expected Load summer.csv";

	//From cheapest to most expensive.
	//private final static String[] priceIndex = {"nuclear", "oil", "coal"};
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
		Graph graph = (Graph) o[0];
		solar = (float[]) o[1];
		wind = (float[]) o[2];
		loads = (float[]) o[3];

		DataModelPrint mp = new DataModelPrint();
		Process proc = null;

		//Hashmap for the 4 different seasonal curves
		HashMap<String, Double[]> seasonalLoadCurve = new HashMap<>();
		seasonalLoadCurve.put("summer", parser.parseDayLoadCurve(SUMMER_LOAD_CURVE));
		//Set the seasonal curve
		seasonalLoadCurve = setSeasonalVariety(seasonalLoadCurve);

		for ( int numOfSim=0; numOfSim < Integer.parseInt(s[3]); numOfSim++){
			InstanceRandomizer instanceRandomizer = new InstanceRandomizer();
			timestepsGraph = new Graph[loads.length + 1];
			timestepsGraph = instanceRandomizer.creategraphs(graph, timestepsGraph, solar, wind, loads);
			int i = 0;

			String solutionPath = dirpath+"simRes"+numOfSim+"";
			try {
				Files.createDirectories(Paths.get(solutionPath)); //create a new directory to safe the output in
			} catch (IOException e1) {
				e1.printStackTrace();
			}

			while (i < timestepsGraph.length - 1) {

				setGridState(timestepsGraph, i);

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
	private static void setGridState(Graph[] graphs, int currentTimeStep){
		//For weibull distribution: alpha = 1.6, beta = 8
		//For normal distribution: mean = 0, sigma = 0.05
		MontoCarloHelper monteCarloHelper = new MontoCarloHelper(1.6, 8, 0, 0.05);

		//Loop through all nodes in the graph
		//for(int i = 0; i < graphs.length-1; i ++){
		int i = currentTimeStep;
			for (int j=0; j < graphs[i].getNodeList().length-1; j++){
				//Check the class of the current node and deal with it accordingly.
				if(graphs[i].getNodeList()[j] != null && (graphs[i].getNodeList()[j].getClass() == Generator.class ||
						graphs[i].getNodeList()[j].getClass() ==  RewGenerator.class)){
					String generatorType = ((Generator) graphs[i].getNodeList()[j]).getType();
					double mcDraw = 0; //This will hold our Monte Carlo draw (hahaha mac draw)
					switch (generatorType) {
					case "H" : //Hydro-eletric generator
						//Ignore this for now, might be added at a later stage
						break;
					case "O": // Oil Thermal generator
						mcDraw = monteCarloHelper.getRandomNormDist();
						//System.out.println(mcDraw);
						graphs = handleThermalGenerator(graphs, i, j, currentTimeStep, mcDraw);
						break;
					case "N": // Nuclear Thermal generator
						mcDraw = monteCarloHelper.getRandomNormDist();
						//System.out.println(mcDraw);
						graphs = handleThermalGenerator(graphs, i, j, currentTimeStep, mcDraw);
						break;
					case "C": // Coal Thermal generator
						mcDraw = monteCarloHelper.getRandomUniformDist();
						//System.out.println(mcDraw);
						graphs = handleThermalGenerator(graphs, i, j, currentTimeStep, mcDraw);
						break;
					case "W": //Wind park generator
						mcDraw = monteCarloHelper.getRandomWeibull();
						//System.out.println(mcDraw);

						if(mcDraw <= V_CUT_IN || mcDraw >= V_CUT_OFF){
							//Wind speed is outside the margins
							((RewGenerator) graphs[i].getNodeList()[j]).setProduction(0);
						} else if(mcDraw >= V_CUT_IN && mcDraw <= V_RATED){
							//In a sweet spot for max wind production?
							float production = ((RewGenerator) graphs[i].getNodeList()[j]).getProduction();
							production = (float) (production*(Math.pow(mcDraw, 3)-Math.pow(V_CUT_IN, 3)/Math.pow(V_RATED, 3)-Math.pow(V_CUT_IN, 3)));//Should be the same as the matlab from Laura
							((RewGenerator) graphs[i].getNodeList()[j]).setProduction(production);
						} else if(mcDraw <= V_CUT_IN && mcDraw <= V_CUT_OFF){
							float production = ((RewGenerator) graphs[i].getNodeList()[j]).getMinP();
							((RewGenerator) graphs[i].getNodeList()[j]).setProduction(production);
						}
						break;
					case "S": //Solar generator
						//Let's ignore the sun as well for now...
						break;
					}
				}
				else if(graphs[i].getNodeList()[j] != null && graphs[i].getNodeList()[j].getClass() == Consumer.class){
					//Consumer so we want to calculate and set the real demand using the load error.
					double mcDraw = monteCarloHelper.getRandomUniformDist();
					float realLoad = (float) (((Consumer) graphs[i].getNodeList()[j]).getLoad() * (1+mcDraw));

					if (realLoad >= 0)
						((Consumer) graphs[i].getNodeList()[j]).setLoad(realLoad);
					else
						((Consumer) graphs[i].getNodeList()[j]).setLoad(0);
				}
				else if(graphs[i].getNodeList()[j] != null && graphs[i].getNodeList()[j].getClass() == Storage.class){
					// storage node currenlty not being adapted
				}
			}
		//}
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
	private static Graph[] handleThermalGenerator(Graph[] graphs, int timestep, int node, int currentTimeStep, double mcDraw){
		double convGeneratorProb = 0.5; //Probability of failure for conventional generators

		if(((Generator) graphs[timestep].getNodeList()[node]).getReactivateAtTimeStep() == 0){//0 means that the reactor can fail.
			if(mcDraw < convGeneratorProb){
				//System.out.println(mcDraw);
				//Our draw is smaller meaning that the generator has failed.
				float lastminp = ((Generator) graphs[timestep].getNodeList()[node]).getMinP();
				float lastmaxp = ((Generator) graphs[timestep].getNodeList()[node]).getMaxP();

				((Generator) graphs[timestep].getNodeList()[node]).setLastminp(lastminp);
				((Generator) graphs[timestep].getNodeList()[node]).setLastmaxp(lastmaxp);

				((Generator) graphs[timestep].getNodeList()[node]).setMinP(0);
				((Generator) graphs[timestep].getNodeList()[node]).setMaxP(0);

				//Set the point at which the generator must be reactivated
				// time resultion has changed to hourly, still have to determine the proper rate fore reactivation
				((Generator) graphs[timestep].getNodeList()[node]).setReactivateAtTimeStep(currentTimeStep + 1);
			}
		}else if(((Generator) graphs[timestep].getNodeList()[node]).getReactivateAtTimeStep() < currentTimeStep) {
			//We have to reactivate the generator because it's been offline for enough steps.
			float minp = ((Generator) graphs[timestep].getNodeList()[node]).getLastminp();
			float maxp = ((Generator) graphs[timestep].getNodeList()[node]).getLastmaxp();
			((Generator) graphs[timestep].getNodeList()[node]).setMinP(minp);
			((Generator) graphs[timestep].getNodeList()[node]).setMaxP(maxp);
		}

		return graphs;
	}

	/**
	 * check if emergency procedure is needed for current timestep
	 *
	 *
	 * @param graphs
	 * @param currentTimeStep
	 */
	private static void checkEmergencyProcedure(Graph[] graphs, int currentTimeStep){

		float deltaP =0, reserveInStorage = 0;

		Graph g = graphs[currentTimeStep];
		Node[] graphNodes = graphs[currentTimeStep].getNodeList();

		// get total current reserve in storage nodes
		for (int j=0; j < graphNodes.length-1; j++){
			if(graphNodes[j] != null && graphNodes[j].getClass() == Storage.class) {
				reserveInStorage += ((Storage) graphNodes[j]).getCapacity();
			}
		}


		// is the system balanced or smaller than the available reserve
		if(Math.abs(deltaP) < reserveInStorage){


		// current load is higher than production
		}else if(deltaP>reserveInStorage){

		// load lower than production
		}else if(deltaP<=reserveInStorage){

		}
	}

	/**
	 * Depending on the state of the grid this method will increase or decrease production in order to balance the system
	 */
	private static void checkGridEquilibrium(Graph grid){

		Node[] nodeList = grid.getNodeList();
		double sumLoads = 0;
		double productionOutput = 0;

		for(int i = 0; i > nodeList.length-1; i++){
			if(nodeList[i] != null && nodeList[i].getClass() == Consumer.class){
				sumLoads += ((Consumer)nodeList[i]).getLoad();
			}
			else if (nodeList[i] != null && nodeList[i].getClass() == Generator.class){
				//How do we get the current power that is being produced? Maybe we can look at the edge connected to generators and get its load.
				//productionOutput += ((Generator)nodeList[i]).getProduction();
			}
		}

		if(productionOutput - sumLoads < 0){
			//We need to increase the energy production!
		} else if (productionOutput - sumLoads > 0) {
			//we need to decrease energy production
		}
	}
}