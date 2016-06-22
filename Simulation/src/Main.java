import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import config.ConfigCollection;
import filehandler.DataModelPrint;
import filehandler.Parser;
import graph.Graph;
import graph.Node;
import model.*;
import config.ConfigCollection.CONFIGURATION_TYPE;
import simulation.*;

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

			//Plan production for the day.
			Graph[] plannedTimestepsGraph = ProductionLoadHandler.setExpectedLoadAndProduction(timestepsGraph);

			//Plan storage for the entire day.
			// todo i don't think that the plannedTimestepsGraph currently is being used, in the sense of applying the planning to the timestepsGraph
			plannedTimestepsGraph = storageHandler.PlanStorageCharging(plannedTimestepsGraph);

			// set real load from consumers using Monte carlo draws
			timestepsGraph = ProductionLoadHandler.setRealLoad(timestepsGraph);
			while (currentTimeStep < timestepsGraph.length) {
				System.out.println("TimeStep: "+ currentTimeStep);

				timestepsGraph[currentTimeStep] = simulationMonteCarloHelper.randomizeGridState(timestepsGraph[currentTimeStep], currentTimeStep);
				timestepsGraph[currentTimeStep] = gridBalancer.checkGridEquilibrium(timestepsGraph[currentTimeStep], currentTimeStep);

				mp.printData(timestepsGraph[currentTimeStep], String.valueOf(dirpath) + outpath1 + currentTimeStep + outpath2, Integer.toString(currentTimeStep)); // This creates a new input file.

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

	// currenlty unused
	public static Graph planExpectedProductionConvGen(Graph[] grid, int timestep, double sumExpectedLoad) {
		Node[] nodeList = grid[timestep].getNodeList();

		double sumExpectedProduction = 0;

		for ( int i = 0; i < nodeList.length; i++){
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
}