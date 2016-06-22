package simulation;

import config.ConfigCollection;
import graph.Graph;
import graph.Node;
import model.Consumer;
import model.ConventionalGenerator;
import model.RenewableGenerator;
import model.Storage;
import config.ConfigCollection.CONFIGURATION_TYPE;

/**
 * Created by ejay on 21/06/16.
 */
public class ProductionLoadHandler {

	private static ConfigCollection config = new ConfigCollection();
    private static SimulationMonteCarloHelper simulationMonteCarloHelper = new SimulationMonteCarloHelper();

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
                sumLoad += ((Storage)graph.getNodeList()[i]).getFlow();
            else if (graph.getNodeList()[i].getClass() == Consumer.class)
                sumLoad += ((Consumer)graph.getNodeList()[i]).getLoad();
        }
        return sumLoad;
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
     * Sets the expected load and production of an entire day
     * @param graphs
     * @return Array where [0] = expectedLoad and [1] = expectedProduction
     */
    public static Graph[] setExpectedLoadAndProduction(Graph[] graphs) {

        Graph[] plannedProduction = graphs; // clone state of graphs

        for(int hour=0; hour < 24; hour++){
            double sumExpectedLoad = 0;
            double sumExpectedProduction = 0;
            plannedProduction[hour] = simulationMonteCarloHelper.randomizeRenewableGenerator(plannedProduction[hour], hour); //set renewable production.

            int beginChargeTime = config.getConfigIntValue(CONFIGURATION_TYPE.STORAGE, "beginChargeTime");
            int endChargeTime = config.getConfigIntValue(CONFIGURATION_TYPE.STORAGE, "beginChargeTime");

            // calculate expected load
            for(int i = 0; i < plannedProduction[hour].getNodeList().length; i++){
                if(plannedProduction[hour].getNodeList()[i].getClass() == Consumer.class){
                    sumExpectedLoad += ((Consumer)plannedProduction[hour].getNodeList()[i]).getLoad();
                } else if (plannedProduction[hour].getNodeList()[i].getClass() == RenewableGenerator.class) {
                    sumExpectedLoad -=  ((RenewableGenerator)plannedProduction[hour].getNodeList()[i]).getProduction();
                } else if (plannedProduction[hour].getNodeList()[i].getClass() == Storage.class) {
                	sumExpectedLoad += ((Storage)plannedProduction[hour].getNodeList()[i]).getFlow();
                }
            }

            //Make sure sumExpectedLoad cannot be negative.
            sumExpectedLoad = Math.abs(sumExpectedLoad);

            // calculate expected conventional generator production
            plannedProduction[hour] = planExpectedProductionConvGen(plannedProduction, hour, sumExpectedLoad);

            // get expected production
            for (int i = 0; i < plannedProduction[hour].getNodeList().length; i ++){
                if (plannedProduction[hour].getNodeList()[i].getClass() == ConventionalGenerator.class){
                    sumExpectedProduction +=  ((ConventionalGenerator)plannedProduction[hour].getNodeList()[i]).getProduction();
                }
            }
        }
        return plannedProduction;
    }

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
