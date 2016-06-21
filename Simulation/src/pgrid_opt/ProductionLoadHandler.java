package pgrid_opt;

import graph.Graph;
import model.Consumer;
import model.ConventionalGenerator;
import model.RewGenerator;
import model.Storage;

/**
 * Created by ejay on 21/06/16.
 */
public class ProductionLoadHandler {

    /**
     * Calculates the total production on the grid from conventional generators, renewable generators and storage if it's discharing.
     * @param graph
     * @return
     */
    public static double calculateProduction(Graph graph){

        double sumProduction = 0;
        for(int i = 0; i < graph.getNodeList().length; i++){
            if(graph.getNodeList()[i].getClass() == ConventionalGenerator.class)
                sumProduction += ((ConventionalGenerator)graph.getNodeList()[i]).getProduction();
            //if(!((ConventionalGenerator)graph.getNodeList()[i]).getGeneratorFailure()){
            //sumProduction += ((ConventionalGenerator)graph.getNodeList()[i]).getProduction();
            if(graph.getNodeList()[i].getClass() == Storage.class && ((Storage)graph.getNodeList()[i]).getStatus() == Storage.StorageStatus.DISCHARGING)
                sumProduction += ((Storage)graph.getNodeList()[i]).getFlow();
            //} else if(graph.getNodeList()[i].getClass() == RewGenerator.class)//dont add production from renewable because we substract it from the load.
            //sumProduction += ((RewGenerator)graph.getNodeList()[i]).getProduction();


        }
        return sumProduction;
    }

    /**
     * Calculates the total load on the grid from consumers and storage if the latter is charging.
     * @param graph
     * @return
     */
    public static double calculateLoad(Graph graph){
        double sumLoad = 0;

        for(int i = 0; i < graph.getNodeList().length; i++){
            if(graph.getNodeList()[i].getClass() == Storage.class && ((Storage)graph.getNodeList()[i]).getStatus() == Storage.StorageStatus.CHARGING )
                sumLoad += ((Storage)graph.getNodeList()[i]).getFlow();
            else if (graph.getNodeList()[i].getClass() == Consumer.class)
                sumLoad += ((Consumer)graph.getNodeList()[i]).getLoad();
            else if(graph.getNodeList()[i].getClass() == RewGenerator.class)
                sumLoad -= ((RewGenerator)graph.getNodeList()[i]).getProduction();

        }
        return sumLoad;
    }

    public static double calculateRenewableProduction(Graph graph){
        double production = 0;
        for(int i = 0; i < graph.getNodeList().length; i++){
            if (graph.getNodeList()[i].getClass() == RewGenerator.class)
                production += ((RewGenerator)graph.getNodeList()[i]).getProduction();
        }
        return production;
    }

    /**
     * Calculates and sets the real load by taking into account monte carlo draws.
     * @param timestepsGraph
     * @return The graph where the real load has been set for each Consumer.
     */
    static Graph[] setRealLoad(Graph[] timestepsGraph) {
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
    static Graph[] setExpectedLoadAndProduction(Graph[] graphs) {

        Graph[] plannedProduction = graphs; // clone state of graphs

        for(int hour=0; hour < 24; hour++){
            double sumExpectedLoad = 0;
            double sumExpectedProduction = 0;
            plannedProduction[hour] = Main.randomizeRenewableGenerator(plannedProduction[hour], hour); //set renewable production.

            // calculate expected load
            for(int i = 0; i < plannedProduction[hour].getNodeList().length; i++){
                if(plannedProduction[hour].getNodeList()[i].getClass() == Consumer.class){
                    sumExpectedLoad += ((Consumer)plannedProduction[hour].getNodeList()[i]).getLoad();
                } else if (plannedProduction[hour].getNodeList()[i].getClass() == RewGenerator.class) {
                    sumExpectedLoad -=  ((RewGenerator)plannedProduction[hour].getNodeList()[i]).getProduction();
                }
            }

            // calculate expected conventional generator production
            plannedProduction[hour] = Main.planExpectedProductionConvGen(plannedProduction, hour, sumExpectedLoad);

            // get expected production
            for (int i = 0; i < plannedProduction[hour].getNodeList().length; i ++){
                if (plannedProduction[hour].getNodeList()[i].getClass() == ConventionalGenerator.class){
                    sumExpectedProduction +=  ((ConventionalGenerator)plannedProduction[hour].getNodeList()[i]).getProduction();
                }
            }
        }
        return plannedProduction;
    }
}
