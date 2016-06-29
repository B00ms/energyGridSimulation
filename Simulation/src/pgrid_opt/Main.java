package pgrid_opt;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.rits.cloning.Cloner;
import config.ConfigCollection;
import config.ConfigCollection.CONFIGURATION_TYPE;
import filehandler.DataModelPrint;
import filehandler.OutputFileHandler;
import filehandler.Parser;
import graph.Graph;
import model.*;
import org.apache.commons.lang3.SerializationUtils;
import simulation.GridBalancer;
import simulation.ProductionLoadHandler;
import simulation.SimulationMonteCarloDraws;
import simulation.SimulationStateInitializer;
import simulation.StorageHandler;

public class Main {

	private static ConfigCollection config = new ConfigCollection();
	private static ProductionLoadHandler productionLoadHandler = new ProductionLoadHandler();
	private static StorageHandler storageHandler = new StorageHandler();
	private static SimulationMonteCarloDraws simulationMonteCarloDraws = new SimulationMonteCarloDraws();
	private static GridBalancer gridBalancer = new GridBalancer();
	private static OutputFileHandler outputFileHandler = new OutputFileHandler();

	public static void main(String[] args) {

		long starttime = System.nanoTime();
		Parser parser = new Parser();
		Graph graph;

		graph = parser.parseData(config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "input-file"));

		// load general config
		String model = config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "model-file");
		String dirpath = config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "output-folder");

		// load glpsol config
		String outpath1 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "outpath1");
		String outpath2 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "outpath2");
		String solpath1 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "solpath1");
		String solpath2 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "solpath2");

		DataModelPrint mp = new DataModelPrint();
		Process proc = null;

		// EENS stuff
		List<Double> listEENS = new ArrayList<Double>();
		boolean EENSConvergence = false;

		Cloner cloner=new Cloner();

		int numOfSim = 0;

		while((!EENSConvergence || numOfSim <= 50)){
			System.out.println("Simulation: " + numOfSim);
			SimulationStateInitializer simulationState = new SimulationStateInitializer();

			double dailyEENS = 0;
			Graph[] initialTimestepsGraph = new Graph[config.getConfigIntValue(CONFIGURATION_TYPE.GENERAL, "numberOfTimeSteps")];
			initialTimestepsGraph = simulationState.creategraphs(graph, initialTimestepsGraph);

			int currentTimeStep = 0;

			String solutionPath = dirpath + "simRes" + numOfSim + "";
			try {
				Files.createDirectories(Paths.get(solutionPath)); // create a new directory to safe the output in
			} catch (IOException e1) {
				e1.printStackTrace();
				System.exit(0);
			}

			//Plan production based on expected load and storage charging during the night period.
			Graph[] expectedSimulationGraph = cloner.deepClone(initialTimestepsGraph);
			expectedSimulationGraph = productionLoadHandler.setExpectedLoadAndProduction(expectedSimulationGraph);

			for(int i = 0; i < 24; i++){
				double testload = productionLoadHandler.calculateLoad(expectedSimulationGraph[i]);
				double testprod = productionLoadHandler.calculateProduction(expectedSimulationGraph[i]);
				System.out.println("expectedLoad: " + testload + " expectedProd: " + testprod);
			}

			Graph[] realSimulationGraph = cloner.deepClone(expectedSimulationGraph);

			// set real load for consumers using Monte carlo draws for the entire day.
			realSimulationGraph = ProductionLoadHandler.setRealLoad(realSimulationGraph);

			for(int i = 0; i < 24; i++){
				double testload = productionLoadHandler.calculateLoad(realSimulationGraph[i]);
				double testprod = productionLoadHandler.calculateProduction(realSimulationGraph[i]);
				System.out.println("realLoad: " + testload + " realProd: " + testprod);
			}
			while (currentTimeStep < realSimulationGraph .length) {
				System.out.println("TimeStep: "+ currentTimeStep);

				//Set failure state of conventional generators and calculates the real renewable production for a single hour.
				realSimulationGraph [currentTimeStep] = simulationMonteCarloDraws.randomizeGridState(realSimulationGraph[currentTimeStep], currentTimeStep);

				System.out.println("load "+productionLoadHandler.calculateLoad(realSimulationGraph[currentTimeStep]));
				System.out.println("prod "+productionLoadHandler.calculateProduction(realSimulationGraph[currentTimeStep]));

				//Plan real storage charging using excess of renewable production to charge past 50% max SoC.x
				storageHandler.planStorageCharging(realSimulationGraph[currentTimeStep], currentTimeStep);

				System.out.println("load "+productionLoadHandler.calculateLoad(realSimulationGraph[currentTimeStep]));
				System.out.println("prod "+productionLoadHandler.calculateProduction(realSimulationGraph[currentTimeStep]));

				//Attempt to balance production and load  for a single hour.
				realSimulationGraph[currentTimeStep] = gridBalancer.checkGridEquilibrium(realSimulationGraph[currentTimeStep], currentTimeStep);

				System.out.println("load "+productionLoadHandler.calculateLoad(realSimulationGraph[currentTimeStep]));
				System.out.println("prod "+productionLoadHandler.calculateProduction(realSimulationGraph[currentTimeStep]));

				// TODO: still have to check actual changes happening in createModelInputFile
				Graph inputFileGraph = cloner.deepClone(realSimulationGraph[currentTimeStep]);
				mp.createModelInputFile(inputFileGraph, String.valueOf(dirpath) + outpath1 + currentTimeStep + outpath2, Integer.toString(currentTimeStep)); // This creates a new input file.

				System.out.println("load "+productionLoadHandler.calculateLoad(realSimulationGraph[currentTimeStep]));
				System.out.println("prod "+productionLoadHandler.calculateProduction(realSimulationGraph[currentTimeStep]));
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

					realSimulationGraph[currentTimeStep] = realSimulationGraph[currentTimeStep].setFlowFromOutputFile(realSimulationGraph[currentTimeStep], currentTimeStep);
					realSimulationGraph[currentTimeStep].printGraph(currentTimeStep, numOfSim);

					// write output to solution file
					outputFileHandler.writeModelOutputFiles(dirpath, solutionPath, currentTimeStep);

					if (graph.getNstorage() > 0) {
						realSimulationGraph[currentTimeStep] = parser.parseUpdates(String.valueOf(dirpath) + "update.txt",
								realSimulationGraph[currentTimeStep]); // Keeps track of the new state for storages.

						if (currentTimeStep < 23)
							realSimulationGraph[currentTimeStep + 1] = simulationState.updateStorages(realSimulationGraph[currentTimeStep],
								realSimulationGraph[currentTimeStep + 1]); // Apply the new state of the storage for the next time step.
					}
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
					System.exit(0);
				}

				// set state of graph for the next hour to the state of the current graph (failure)
				if(currentTimeStep >0 && currentTimeStep < realSimulationGraph.length-1){
					for (int n = 0; n < realSimulationGraph[currentTimeStep].getNodeList().length; n++) {
						if (realSimulationGraph[currentTimeStep].getNodeList()[n].getClass() == ConventionalGenerator.class){
							// cascade generator failure to the next hour.
							ConventionalGenerator cgen = (ConventionalGenerator)realSimulationGraph[currentTimeStep].getNodeList()[n];
							((ConventionalGenerator) realSimulationGraph[currentTimeStep+1].getNodeList()[n]).setGeneratorFailure(cgen.getGeneratorFailure());
						}
					}
				}

				// calculate eens for hour
				double hourlyEENS = calculateEENS(realSimulationGraph[currentTimeStep]);
				dailyEENS += hourlyEENS;
				listEENS.add(dailyEENS);
				System.err.println("dailyEENS: "+ dailyEENS);

				currentTimeStep++;
			}

			// handle storage.txt output
			outputFileHandler.writeStorageTxtFile(realSimulationGraph, dirpath, solutionPath);

			EENSConvergence = checkEENSConvergence(listEENS);
			if(numOfSim == 0)
				EENSConvergence = false;

			numOfSim++; // go to next day
		}


		long endtime = System.nanoTime();
		long duration = endtime - starttime;

		System.out.println("Time used:" + duration / 1000000 + " millisecond");
	}

	public static Double calculateEENS(Graph realSimulationGraph){
		double hourlyEENS = 0;

		double realLoad 		= productionLoadHandler.calculateLoad(realSimulationGraph);
		double satisfiedLoad 	= productionLoadHandler.calculateSatisfiedLoad(realSimulationGraph);

		// if there is expected energy not supplied then count it as shedded load
		System.out.println("EENS: " + (realLoad-satisfiedLoad));
		if((realLoad - satisfiedLoad) > 0)
			hourlyEENS += (realLoad - satisfiedLoad);

		return hourlyEENS;
	}

	public static boolean checkEENSConvergence(List<Double> listEENS){
		double EENSConvergenceThreshold = config.getConfigDoubleValue((CONFIGURATION_TYPE.GENERAL), "EENSConvergenceThreshold");
		double sumEENS = 0;
		boolean EENSConvergence = false;

		if(listEENS.size() > 1){
			for (int i =0; i < listEENS.size()-1; i++){
				sumEENS += listEENS.get(i);
			}

			sumEENS = sumEENS / listEENS.size()-2;

			double convergence = Math.abs(sumEENS - listEENS.get(listEENS.size()-1));
			System.err.println("Convergence: "+ convergence +", Threshold: "+ EENSConvergenceThreshold);
			// stop the simulation when converged
			if(convergence <= EENSConvergenceThreshold){
				EENSConvergence = true;
			}
		}else{}

		return EENSConvergence;
	}
}