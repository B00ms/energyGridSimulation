package pgrid_opt;

import graph.Graph;
import graph.Node;
import model.Storage;

/**
 * Created by ejay on 21/06/16.
 */
public class StorageHandler {

    private static ProductionLoadHandler hpl = new ProductionLoadHandler();


    /**
     * Charges storage but only if the current charge is less than 50% of its capacity.
     * @param graph
     * @return graph in which the state of storages has been set.
     */
    public static Graph chargeStorage(Graph graph){

        //Double[] sumProdAndLoad = calcSumProductionSumLoad(graph);
        double sumLoads = hpl.calculateLoad(graph);

        for(int i = 0; i < graph.getNodeList().length; i++){
            if(graph.getNodeList()[i].getClass() == Storage.class){
                if(((Storage)graph.getNodeList()[i]).getMaximumCharge() * 0.5 > ((Storage)graph.getNodeList()[i]).getCurrentCharge()){
                    sumLoads += ((Storage)graph.getNodeList()[i]).charge(((Storage) graph.getNodeList()[i]).getMaximumCharge() * 0.5);
                }
            }
        }
        return graph;
    }

    /**
     * Charges or discharges storage depending depending on the state of production and load.
     * @param graph
     * @return
     */
    public static Graph chargeOrDischargeStorage(Graph graph){

        //Double[] sumProdAndLoad = calcSumProductionSumLoad(graph);
        double totalCurrentProduction = hpl.calculateProduction(graph);
        double sumLoad = hpl.calculateLoad(graph);
        double rewProd = hpl.calculateRenewableProduction(graph);

        Node[] nodeList = graph.getNodeList();
        if (totalCurrentProduction > sumLoad) {
            //Charge the batteries
            System.out.println("Storage charging ");
            for (int i = 0; i < nodeList.length; i++) {
                if (nodeList[i] != null && nodeList[i].getClass() == Storage.class) {
                    if (sumLoad + ((Storage) nodeList[i]).getMaximumCharge() <= totalCurrentProduction){
                        sumLoad += ((Storage) nodeList[i]).charge(((Storage) nodeList[i]).getMaximumCharge());
                    } else
                        sumLoad += ((Storage) nodeList[i]).charge(((Storage) nodeList[i]).charge(Math.abs(sumLoad - totalCurrentProduction))); //charge the remainder to fully meet the demand.
                }
            }
        } else if (totalCurrentProduction < sumLoad) {
            System.out.println("battery discharge ");
            for (int i = 0; i < nodeList.length; i++) {
                if (nodeList[i] != null && nodeList[i].getClass() == Storage.class) {
                    if (totalCurrentProduction + ((Storage) nodeList[i]).getMaximumCharge() <= sumLoad) {
                        totalCurrentProduction += ((Storage) nodeList[i]).discharge(((Storage) nodeList[i]).getMaximumCharge());
                    } else
                        totalCurrentProduction += ((Storage) nodeList[i]).discharge(sumLoad - totalCurrentProduction);
                }
            }
        } else {
            System.out.print("Balanced ");
            // Production and load are balanced.
        }
        graph.setNodeList(nodeList);
        return graph;
    }


}
