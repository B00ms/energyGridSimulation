package pgrid_opt;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import config.ConfigCollection;
import config.ConfigCollection.CONFIGURATION_TYPE;
import filehandler.DataModelPrint;
import filehandler.OutputFileHandler;
import filehandler.Parser;
import graph.Graph;
import model.*;
import simulation.GridBalancer;
import simulation.ProductionLoadHandler;
import simulation.SimulationMonteCarloHelper;
import simulation.SimulationStateInitializer;
import simulation.StorageHandler;

public class Main {

	private static ConfigCollection config = new ConfigCollection();
	private static ProductionLoadHandler productionLoadHandler = new ProductionLoadHandler();
	private static StorageHandler storageHandler = new StorageHandler();
	private static SimulationMonteCarloHelper simulationMonteCarloHelper = new SimulationMonteCarloHelper();
	private static GridBalancer gridBalancer = new GridBalancer();
	private static OutputFileHandler outputFileHandler = new OutputFileHandler();

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
				realTimestepsGraph [currentTimeStep] = simulationMonteCarloHelper.randomizeGridState(plannedTimestepsGraph[currentTimeStep], currentTimeStep);

				System.out.println("load "+productionLoadHandler.calculateLoad(realTimestepsGraph[currentTimeStep]));
				System.out.println("prod "+productionLoadHandler.calculateProduction(realTimestepsGraph[currentTimeStep]));

				//Plan real storage charging using excess of renewable production to charge past 50% max SoC.x
				storageHandler.planStorageCharging(realTimestepsGraph[currentTimeStep], currentTimeStep);

				System.out.println("load "+productionLoadHandler.calculateLoad(realTimestepsGraph[currentTimeStep]));
				System.out.println("prod "+productionLoadHandler.calculateProduction(realTimestepsGraph[currentTimeStep]));

				//Attempt to balance production and load  for a single hour.
				realTimestepsGraph [currentTimeStep] = gridBalancer.checkGridEquilibrium(plannedTimestepsGraph[currentTimeStep], currentTimeStep);

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
					outputFileHandler.writeOutputFiles(dirpath, solutionPath, currentTimeStep);

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
				if(currentTimeStep >0 && currentTimeStep < timestepsGraph.length-1){
					for (int n = 0; n < timestepsGraph[currentTimeStep].getNodeList().length; n++) {
						if (timestepsGraph[currentTimeStep].getNodeList()[n].getClass() == ConventionalGenerator.class){
							// cascade generator failure to the next hour.
							ConventionalGenerator cgen = (ConventionalGenerator)timestepsGraph[currentTimeStep].getNodeList()[n];
							((ConventionalGenerator) timestepsGraph[currentTimeStep+1].getNodeList()[n]).setGeneratorFailure(cgen.getGeneratorFailure());
						}
					}
				}
				currentTimeStep++;
			}

			outputFileHandler.writeStorageTxtFile(timestepsGraph, dirpath, solutionPath, currentTimeStep);
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
}