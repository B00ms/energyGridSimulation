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
}
