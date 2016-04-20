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
	private static double V_CUT_IN = 4;
	private static double V_CUT_OFF = 25;
	private static double V_RATED = 12;

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
		Graph g = (Graph) o[0];
		solar = (float[]) o[1];
		wind = (float[]) o[2];
		loads = (float[]) o[3];

		DataModelPrint mp = new DataModelPrint();
		Process proc = null;

		for ( int numOfSim=0; numOfSim < Integer.parseInt(s[3]); numOfSim++){
			InstanceRandomizer r = new InstanceRandomizer();
			timestepsGraph = new Graph[loads.length + 1];
			timestepsGraph = r.creategraphs(g, timestepsGraph, solar, wind, loads);
			int i = 0;

			String solutionPath = dirpath+"simRes"+numOfSim+"";
			try {
				Files.createDirectories(Paths.get(solutionPath)); //create a new directory to safe the output in
			} catch (IOException e1) {
				e1.printStackTrace();
			}

			while (i < timestepsGraph.length - 1) {

				//setGridState(timestepsGraph, i);

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
					if (g.getNstorage() > 0) {
						timestepsGraph[i] = parser.parseUpdates(String.valueOf(dirpath) + "update.txt", timestepsGraph[i]); //Keeps track of the new state for storages.
						timestepsGraph[i + 1] = r.updateStorages(timestepsGraph[i], timestepsGraph[i + 1]); //Apply the new state of the storage for the next time step.
					}
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
				++i;
			}
			if (g.getNstorage() > 0) {
				mp.printStorageData(timestepsGraph, String.valueOf(dirpath) + "storage.txt");
			}
		}
			long endtime = System.nanoTime();
			long duration = endtime - starttime;
			System.out.println("Time used:" + duration / 1000000 + " millisecond");
	}




	/**
	 * Set the state of generators and loads.
	 * @return Graphs of which the state has been changed using Monte Carlo draws
	 */
	private static void setGridState(Graph[] graphs, int currentTimeStep){
		//For weibull distribution: alpha = 1.6, beta = 8
		//For normal distribution: mean = 0, sigma = 0.05
		MontoCarloHelper monteCarloHelper = new MontoCarloHelper(1.6, 8, 0, 0.05);
		int[][][][][] convGeneratorStatus = new int[42][0][0][0][0]; //Generator[Id][status][min prod][max prod][timesteps since failure]

		//Get the number of generators in the system  and create an array to keep track of their status.
		//convGeneratorStatus = new int[graphs.getNGenerators()][0][0][0][0];

		double convGeneratorProb = 0.0; //Probability of failure for conventional generators
		double windSpeedVariance = 0.0; //Variance for wind speed
		double loadVariance = 0; //Variance for load

		//Loop through all nodes in the graph
		for(int i = 0; i < graphs.length-1; i ++){
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
					case "T": //Thermal generator
						mcDraw = monteCarloHelper.getRandomNormDist();
						if(((Generator) graphs[i].getNodeList()[j]).getReactiveteAtTimeStep() == 0){//0 means that the reactor can fail.
							if(mcDraw < convGeneratorProb){
								//Our draw is smaller meaning that the generator has failed.
								float lastminp = ((Generator) graphs[i].getNodeList()[j]).getMinP();
								float lastmaxp = ((Generator) graphs[i].getNodeList()[j]).getMaxP();

								((Generator) graphs[i].getNodeList()[j]).setLastminp(lastminp);
								((Generator) graphs[i].getNodeList()[j]).setLastmaxp(lastmaxp);

								((Generator) graphs[i].getNodeList()[j]).setMinP(0);
								((Generator) graphs[i].getNodeList()[j]).setMaxP(0);

								//Set the point at which the generator must be reactivated
								//since we have a 15 minute resolution we want to add 4 time steps for a period of 24
								((Generator) graphs[i].getNodeList()[j]).setReactiveteAtTimeStep(currentTimeStep+4);

								}
							}else if(((Generator) graphs[i].getNodeList()[j]).getReactiveteAtTimeStep() < currentTimeStep) {
								//We have to reactivate the generator because it's been offline for enough steps.
								float minp = ((Generator) graphs[i].getNodeList()[j]).getLastminp();
								float maxp = ((Generator) graphs[i].getNodeList()[j]).getLastmaxp();
								((Generator) graphs[i].getNodeList()[j]).setMinP(minp);
								((Generator) graphs[i].getNodeList()[j]).setMaxP(maxp);
							}
						break;
					case "W": //Wind park generator
						mcDraw = monteCarloHelper.getRandomWeibull();

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

				}
			}
		}
	}
}