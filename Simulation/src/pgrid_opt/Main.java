package pgrid_opt;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.math3.distribution.WeibullDistribution;

import pgrid_opt.DataModelPrint;
import pgrid_opt.Graph;
import pgrid_opt.InstanceRandomizer;
import pgrid_opt.Parser;

public class Main {
	public static void main(String[] args) {
		long starttime = System.nanoTime();
		float[] wind = null;
		float[] solar = null;
		float[] loads = null;
		float wcost = 0.0f;  //wind cost
		float scost = 0.0f; //solar cost
		Graph[] gdays = null;
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



		InstanceRandomizer r = new InstanceRandomizer();
		gdays = new Graph[loads.length + 1];
		gdays = r.creategraphs(g, gdays, solar, wind, loads);
		DataModelPrint mp = new DataModelPrint();
		Process proc = null;
		int i = 0;
		while (i < gdays.length - 1) {
			
			//gdays = setGridState(gdays, i);
			
			mp.printData(gdays[i], String.valueOf(dirpath) + outpath1 + i + outpath2, Integer.toString(i)); //This creates a new input file.
			try {
				StringBuffer output = new StringBuffer();
				String command = String.valueOf(solpath1) + outpath1 + i + outpath2 + solpath2 + model;
				System.out.println(command);
				//TODO: Do a Monte Carlo draw for conventional and renewable generators and set their state.
				//TODO: Determine the probability of failure for conventional generators
				//TODO: Do a Monto Carlo draw for the load.
				proc = Runtime.getRuntime().exec(command, null, new File(dirpath));
				proc.waitFor();
				BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream())); //Using the new input file, we apply the model to solve the cost function given the new state of the grid.
				String line = "";
				while ((line = reader.readLine()) != null) {
					output.append(String.valueOf(line) + "\n");
				}
				System.out.println(output);
				if (g.getNstorage() > 0) {
					gdays[i] = parser.parseUpdates(String.valueOf(dirpath) + "update.txt", gdays[i]); //Keeps track of the new state for storages.
					gdays[i + 1] = r.updateStorages(gdays[i], gdays[i + 1]); //Apply the new state of the storage for the next time step.
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
			++i;
		}
		if (g.getNstorage() > 0) {
			mp.printStorageData(gdays, String.valueOf(dirpath) + "storage.txt");
		}
		long endtime = System.nanoTime();
		long duration = endtime - starttime;
		System.out.println("Time used:" + duration / 1000000 + " millisecond");
	}

	/**
	 * Set the state of generators and loads.
	 * @return
	 */
	private static Graph[] setGridState(Graph graph, int currentTimeStep){
		// TODO Auto-generated method stub
		
		//For weibull distribution: alpha = 1.6, beta = 8
		//For normal distribution: mean = 0, sigma = 0.05
		MontoCarloHelper monteCarloHelper = new MontoCarloHelper(1.6, 8, 0, 0.05);
		int[][][][][] convGeneratorStatus = new int[42][0][0][0][0]; //Generator[Id][status][min prod][max prod][timesteps since failure]
		
		//Get the number of generators in the system  and create an array to keep track of their status.
		convGeneratorStatus = new int[graph.getNGenerators()][0][0][0][0];

		double convGeneratorProb = 0.0; //Probability of failure for conventional generators
		double windSpeedVariance = 0.0; //Variance for wind speed
		double loadVariance = 0; //Variance for load
		
		//Loop through all nodes in the graph
		for(int i = 0; i < graph.getNNode(); i ++){
			
			//Check the class of the current node and deal with it accordingly. 
			if(graph.getNodeList()[i].getClass() == Generator.class){ 
				String generatorType = ((Generator) graph.getNodeList()[i]).getType();
				double mcDraw = 0; //This will hold our Monte Carlo draw (hahaha mac draw)
				switch (generatorType) {
				case "H" : //Hydro-eletric generator
					//Ignore this for now, might be added at a later stage
					break;
				case "T": //Thermal generator
					mcDraw = monteCarloHelper.getRandomNormDist();
					if(((Generator) graph.getNodeList()[i]).getReactiveteAtTimeStep() == 0){//0 means that the reactor can fail.
						if(mcDraw < convGeneratorProb){
							//Our draw is smaller meaning that the generator has failed.
								((Generator) graph.getNodeList()[i]).setMaxP(0);
								((Generator) graph.getNodeList()[i]).setMinP(0);

								//Set the point at which the generator must be reactivated
								//since we have a 15 minute resolution we want to add 4 time steps for a period of 24
								((Generator) graph.getNodeList()[i]).setReactiveteAtTimeStep(currentTimeStep+4);
							}
						}else if(((Generator) graph.getNodeList()[i]).getReactiveteAtTimeStep() < currentTimeStep) {
							//We have to reactivate the generator because it's been offline for enough steps.
							//TODO: setmaxP to previous reported value
							//TODO: setminP to previous reported value
						}
					break;
				case "W": //Wind park generator
					mcDraw = monteCarloHelper.getRandomWeibull();						
					break;
				case "S": //Solar generator
					//Let's ignore the sun as well for now...
					break;
				}
			}
			else if(graph.getNodeList()[i].getClass() == Consumer.class){
				loadVariance = monteCarloHelper.getRandomUniformDist();
				
			}
			else if(graph.getNodeList()[i].getClass() == Storage.class){
				
			}
			
		}
		
		return null;
	}
}