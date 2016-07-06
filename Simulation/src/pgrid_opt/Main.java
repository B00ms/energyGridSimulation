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
import simulation.*;

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
		String modelNight = config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "modelnight-file");
		String modelDay = config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "modelday-file");
		String dirpath = config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "output-folder");
        int beginTime = config.getConfigIntValue(ConfigCollection.CONFIGURATION_TYPE.STORAGE, "beginChargeTime");
        int endTime = config.getConfigIntValue(ConfigCollection.CONFIGURATION_TYPE.STORAGE, "endChargeTime");

		// load glpsol config
		String outpath1 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "outpath1");
		String outpath2 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "outpath2");
		String solpath1 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "solpath1");
		String solpath2 = config.getConfigStringValue(CONFIGURATION_TYPE.GLPSOL, "solpath2");

		double EENSConvergenceThreshold = Main.config.getConfigDoubleValue((ConfigCollection.CONFIGURATION_TYPE.GENERAL), "EENSConvergenceThreshold");

		DataModelPrint mp = new DataModelPrint();
		Process proc = null;

		// EENS stuff
		List<Double> listEENS = new ArrayList<>();
		boolean EENSConvergence = false;

		Cloner cloner=new Cloner();

		int numOfSim = 0;

		while((!EENSConvergence && numOfSim <= 1000)){
			System.out.println("Simulation: " + numOfSim);
			SimulationStateInitializer simulationState = new SimulationStateInitializer();

			double dailyEENS = 0;
			Graph[] expectedSimulationGraph = new Graph[config.getConfigIntValue(CONFIGURATION_TYPE.GENERAL, "numberOfTimeSteps")];
			expectedSimulationGraph = simulationState.creategraphs(graph, expectedSimulationGraph);

			int currentTimeStep = 0;

			String solutionPath = dirpath + "simRes" + numOfSim + "";
			try {
				Files.createDirectories(Paths.get(solutionPath)); // create a new directory to safe the output in
			} catch (IOException e1) {
				e1.printStackTrace();
				System.exit(0);
			}

			//Plan production based on expected load and storage charging during the night period.
			//Graph[] expectedSimulationGraph = cloner.deepClone(initialTimestepsGraph);
			expectedSimulationGraph = productionLoadHandler.setExpectedLoadAndProduction(expectedSimulationGraph);
			//System.out.println("Expected production and load (from input file and expected renewable production) ");
			//System.out.println("Expected production: " + productionLoadHandler.calculateProduction(expectedSimulationGraph[currentTimeStep]) + " ");
			//System.out.println("Expected load: " + productionLoadHandler.calculateLoad(expectedSimulationGraph[currentTimeStep]));

			Graph[] realSimulationGraph = cloner.deepClone(expectedSimulationGraph);

			// set real load for consumers using Monte carlo draws for the entire day.
			realSimulationGraph = ProductionLoadHandler.setRealLoad(realSimulationGraph);

			while (currentTimeStep < realSimulationGraph .length) {
				System.out.println();
				System.out.println("TimeStep: "+ currentTimeStep);

				//Set failure state of conventional generators and calculates the real renewable production for a single hour.
				realSimulationGraph [currentTimeStep] = simulationMonteCarloDraws.randomizeGridState(realSimulationGraph[currentTimeStep], currentTimeStep);

				//Plan real storage charging using excess of renewable production to charge past 50% max SoC.x
				storageHandler.planStorageCharging(realSimulationGraph[currentTimeStep], currentTimeStep);

				//System.out.println("Real production and load (including storage charging during night, and failed generators and real renewable production)");
				//System.out.println("Real production: " + productionLoadHandler.calculateProduction(expectedSimulationGraph[currentTimeStep]));
				//System.out.println("Real load: " + productionLoadHandler.calculateLoad(expectedSimulationGraph[currentTimeStep]));

				//Attempt to balance production and load  for a single hour.
				realSimulationGraph[currentTimeStep] = gridBalancer.checkGridEquilibrium(realSimulationGraph[currentTimeStep], currentTimeStep);

				//System.out.println("Real production and load after checkGridEquilibrium");
				//System.out.println("Real production: " + productionLoadHandler.calculateProduction(expectedSimulationGraph[currentTimeStep]) + " ");
				//System.out.println("Real load: " + productionLoadHandler.calculateLoad(expectedSimulationGraph[currentTimeStep]));

				//Graph inputFileGraph = cloner.deepClone(realSimulationGraph[currentTimeStep]);
				Graph inputFileGraph = realSimulationGraph[currentTimeStep];
				mp.createModelInputFile(inputFileGraph, String.valueOf(dirpath) + outpath1 + currentTimeStep + outpath2, Integer.toString(currentTimeStep)); // This creates a new input file.

				try {
					String model = "";
					if(currentTimeStep >= beginTime || currentTimeStep<= endTime){
						model = modelNight;
					}else{
						model = modelDay;
					}

					String command = "" + String.valueOf(solpath1) + outpath1 + currentTimeStep + outpath2 + solpath2 + model;
					command = command + " --nopresol --output filename.out ";

					//System.out.println(command);
					File file = new File(dirpath);
					proc = Runtime.getRuntime().exec(command, null, file);
					proc.waitFor();

					StringBuffer output = new StringBuffer();
					BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream())); // Using the new input file, we apply the model to solve the cost function given the new state of the grid.
					String line = "";

					while ((line = reader.readLine()) != null) {
						output.append(String.valueOf(line) + "\n");
					}
					System.out.print(output);
					proc.getOutputStream().close();
					proc.getInputStream().close();
					proc.getErrorStream().close();
					proc.destroy();
					reader.close();

					realSimulationGraph[currentTimeStep] = realSimulationGraph[currentTimeStep].setFlowFromOutputFile(realSimulationGraph[currentTimeStep], currentTimeStep);

					//System.out.println("Actual production and load as defined by the flow simulation: ");
					//System.out.println("Actual Production " + productionLoadHandler.calculateSatisfiedLoad(realSimulationGraph[currentTimeStep]));
					//System.out.println("Actual Load "+ productionLoadHandler.calculateProduction(realSimulationGraph[currentTimeStep]));

					// write output to solution file
					outputFileHandler.writeModelOutputFiles(dirpath, solutionPath, currentTimeStep);

					if (graph.getNstorage() > 0) {
						realSimulationGraph[currentTimeStep] = parser.parseUpdates(String.valueOf(dirpath) + "update.txt", realSimulationGraph[currentTimeStep]); // Keeps track of the new state for storages.

						if (currentTimeStep < realSimulationGraph.length-1)
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
				double hourlyEENS = EENSHandler.calculateHourlyEENS(realSimulationGraph[currentTimeStep]);
				dailyEENS += hourlyEENS;

				realSimulationGraph[currentTimeStep].printGraph(currentTimeStep, numOfSim, hourlyEENS);

				currentTimeStep++;
			}

			// handle storage.txt output
			//outputFileHandler.writeStorageTxtFile(realSimulationGraph, dirpath, solutionPath);

			listEENS.add(dailyEENS);
			EENSConvergence = EENSHandler.checkEENSConvergence(listEENS, EENSConvergenceThreshold);
			if(numOfSim == 0)
				EENSConvergence = false;

			numOfSim++; // go to next day
		}


		long endtime = System.nanoTime();
		long duration = endtime - starttime;
		double realEENS = EENSHandler.calculateRealEENS(listEENS);

		System.out.println("Time used:" + duration / 1000000 + " millisecond");
		System.out.println("Real EENS: "+ realEENS);
	}

}