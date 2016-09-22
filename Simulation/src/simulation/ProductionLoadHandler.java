package simulation;

import config.ConfigCollection;
import graph.Graph;
import graph.Node;
import model.Consumer;
import model.ConventionalGenerator;
import model.RenewableGenerator;
import model.Storage;

/**
 * Created by ejay on 21/06/16.
 */
public class ProductionLoadHandler {

	//private static ConfigCollection config = new ConfigCollection();
    private static SimulationMonteCarloDraws simulationMonteCarloDraws = new SimulationMonteCarloDraws();

    /**
     * Calculates the total production on the grid from conventional generators, renewable generators and storage if it's discharing.
     * @param graph
     * @return
     */
    public double calculateProduction(Graph graph){

        double sumProduction = 0;
        for(int i = 0; i < graph.getNodeList().length; i++) {
            if (graph.getNodeList()[i].getClass() == ConventionalGenerator.class)
                sumProduction += ((ConventionalGenerator) graph.getNodeList()[i]).getProduction();
            else if (graph.getNodeList()[i].getClass() == RenewableGenerator.class)
                sumProduction += ((RenewableGenerator) graph.getNodeList()[i]).getProduction();
            else if (graph.getNodeList()[i].getClass() == Storage.class && ((Storage) graph.getNodeList()[i]).getStatus() == Storage.StorageStatus.DISCHARGING)
                sumProduction += ((Storage) graph.getNodeList()[i]).getFlow();
        }
        return sumProduction;
    }

    /**
     * Calculates the total load on the grid from consumers and storage if the latter is charging.
     * @param graph
     * @return
     */
    public double calculateLoad(Graph graph){
        double sumLoad = 0;

        for(int i = 0; i < graph.getNodeList().length; i++){
            if(graph.getNodeList()[i].getClass() == Storage.class && ((Storage)graph.getNodeList()[i]).getStatus() == Storage.StorageStatus.CHARGING )
                sumLoad += Math.abs(((Storage)graph.getNodeList()[i]).getFlow());
            else if (graph.getNodeList()[i].getClass() == Consumer.class)
                sumLoad += ((Consumer)graph.getNodeList()[i]).getLoad();
        }
        return sumLoad;
    }

    /**
     * Calculates the real satisfied load after the simulation run with the model
     * @param graph
     * @return
     */
    public double calculateSatisfiedLoad(Graph graph){
        double sumLoad = 0;

        for(int i = 0; i < graph.getNodeList().length; i++){
            if(graph.getNodeList()[i].getClass() == Storage.class && ((Storage)graph.getNodeList()[i]).getStatus() == Storage.StorageStatus.CHARGING )
                sumLoad += Math.abs(((Storage)graph.getNodeList()[i]).getFlow());
            else if (graph.getNodeList()[i].getClass() == Consumer.class)
                sumLoad += Math.abs(((Consumer)graph.getNodeList()[i]).getFlow());
        }
        return sumLoad;
    }

    public double calculateConventionalProduction(Graph graph){
        double production = 0;
        for(int i = 0; i < graph.getNodeList().length; i++){
            if (graph.getNodeList()[i].getClass() == ConventionalGenerator.class)
                production += ((ConventionalGenerator)graph.getNodeList()[i]).getProduction();
        }
        return production;
    }

    public double calculateRenewableProduction(Graph graph){
        double production = 0;
        for(int i = 0; i < graph.getNodeList().length; i++){
            if (graph.getNodeList()[i].getClass() == RenewableGenerator.class)
                production += ((RenewableGenerator)graph.getNodeList()[i]).getProduction();
        }
        return production;
    }

    /**
     * Calculates and sets the real load by taking into account monte carlo draws.
     * @param timestepsGraph
     * @return The graph where the real load has been set for each Consumer.
     */
    public static Graph[] setRealLoad(Graph[] timestepsGraph) {
        MontoCarloHelper mcHelper = new MontoCarloHelper();
        for (int i = 0; i < timestepsGraph.length; i++) {
            double totalLoad = 0;
            for (int n = 0; n < timestepsGraph[i].getNodeList().length; n++) {
                if (timestepsGraph[i].getNodeList()[n] != null
                        && timestepsGraph[i].getNodeList()[n].getClass() == Consumer.class) {
                    double mcDraw = mcHelper.getRandomNormDist();
                    double previousError = 0;

                    // Calculate and set the load error of a single consumer.
                    double error = (((Consumer) timestepsGraph[i].getNodeList()[n]).getLoad() * mcDraw);
                    totalLoad += ((Consumer) timestepsGraph[i].getNodeList()[n]).getLoad();
                    if (i > 0)
                        previousError = ((Consumer) timestepsGraph[i - 1].getNodeList()[n]).getLoadError();

                    ((Consumer) timestepsGraph[i].getNodeList()[n]).setLoadError(error + previousError); // plus load error of i-1 makes it cumulative.

                    // Calculate and set the real load of a single consumer
                    double realLoad = ((Consumer) timestepsGraph[i].getNodeList()[n]).getLoad()
                            + ((Consumer) timestepsGraph[i].getNodeList()[n]).getLoadError();
                    ((Consumer) timestepsGraph[i].getNodeList()[n]).setLoad(realLoad);
                }
            }
        }
        return timestepsGraph;
    }

    /**
     * Plans charging of storage during night period, sets expected renewable production, sets expected conventional production.
     * This function sets the above mention for the entire day.
     * @param graphs
     * @return Array of graphs for each hour where production and load has been set.
     */
    public Graph[] setExpectedLoadAndProduction(Graph[] graphs, int month) {

    	StorageHandler storageHandler = new StorageHandler();

    	// clone state of graphs
        Graph[] plannedProduction = graphs;

        //Plan charging storage for during the night period of the day..
        plannedProduction = storageHandler.planStorageCharging(plannedProduction);

        for(int hour=0; hour < graphs.length; hour++){
        	//Set expected renewable production
            plannedProduction[hour] = simulationMonteCarloDraws.randomizeRenewableGenerator(plannedProduction[hour], hour, month); //set renewable production.

            // calculate and set expected conventional generator production
            plannedProduction[hour] = planExpectedProductionConvGen(plannedProduction, hour);
        }
        return plannedProduction;
    }

    public Graph planExpectedProductionConvGen(Graph[] grid, int timestep) {
        Node[] nodeList = grid[timestep].getNodeList();

        double sumExpectedProduction = calculateProduction(grid[timestep]);
        double sumExpectedLoad = calculateLoad(grid[timestep]);
        for ( int i = 0; i < nodeList.length; i++){
            if(nodeList[i].getClass() == ConventionalGenerator.class && sumExpectedLoad != sumExpectedProduction){
                ConventionalGenerator generator =  ((ConventionalGenerator) nodeList[i]);
                if (timestep > 0){ //We take into account spinup after hour 0, maximum increase with spinup is 50% of max generation.
                    double previousProduction =  ((ConventionalGenerator)grid[timestep - 1].getNodeList()[i]).getProduction();
                    double production = previousProduction + generator.getMaxP() * 0.5;

                    //We're going to change the production of a generator so we remove the current production of that generator first.
                    sumExpectedProduction -= generator.getProduction();

                    if(sumExpectedProduction+production < sumExpectedLoad){
                        sumExpectedProduction += generator.setScheduledProduction(production, previousProduction);
                    } else{
                        //We don't need to use maximum production to meet the load, so we set production to remainder.
                        production = sumExpectedLoad - sumExpectedProduction;

                        //Check if production isn't to low, if it is set generator to min production.
                        if (production < generator.getDayAheadMinProduction() || production < generator.getDayAheadMaxProduction())
                            sumExpectedProduction += generator.setScheduledProduction(production, previousProduction);
                        else
                            sumExpectedProduction += generator.setScheduledProduction(generator.getDayAheadMinProduction(), previousProduction);
                    }
                }else{
                    double production = generator.getDayAheadMaxProduction();

                    //We're going to change the production of a generator so we remove the current production of that generator first.
                    sumExpectedProduction -= generator.getProduction();

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
