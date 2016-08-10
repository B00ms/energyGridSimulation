package simulation;

import config.ConfigCollection;
import graph.Graph;
import graph.Node;
import model.Storage;
import config.ConfigCollection.CONFIGURATION_TYPE;

/**
 * Created by ejay on 21/06/16.
 */
public class StorageHandler {
	ConfigCollection config = new ConfigCollection();

    private static ProductionLoadHandler hpl = new ProductionLoadHandler();

   /**
    * Sets the state of storage to charge to 50% of max SoC between a begin time and end time.
    * @param plannedTimestepsGraph
    * @return
    */
	public Graph[] planStorageCharging(Graph[] plannedTimestepsGraph) {

	    int beginChargeTime = config.getConfigIntValue(CONFIGURATION_TYPE.STORAGE, "beginChargeTime");
	    int endChargeTime = config.getConfigIntValue(CONFIGURATION_TYPE.STORAGE, "endChargeTime");
		int hour = 0;

		while(hour < 24){
			if(hour  >= beginChargeTime || hour  <= endChargeTime){
				Graph graph = plannedTimestepsGraph[hour];
				for (int i = 0; i < graph.getNodeList().length; i++){
					if(graph.getNodeList()[i].getClass() == Storage.class){
						Storage storage = (Storage) graph.getNodeList()[i];
						//We calculate 50% of max charge then divide by efficiency which will give us the amount we need to charge to 50% of max charge
						double chargeTarget = (storage.getMaximumCharge() * 0.5 / storage.getChargeEfficiency()) - storage.getCurrentSoC() / storage.getChargeEfficiency();
						double oldSoC = storage.getCurrentSoC();
						storage.charge(chargeTarget);
						//Set the  SoC to what it was before because when planning we don't actually charge the storage.
						storage.setCurrentSoC(oldSoC);
						graph.getNodeList()[i] = storage;
					}
				}
				plannedTimestepsGraph[hour] = graph;

                // TODO: check SOC for the next hour at start is same as hour just calculated
			}
			hour++;
		}
	return plannedTimestepsGraph;
	}

	public Graph planStorageCharging(Graph graph, int timestep, boolean executePlan){
	    int beginChargeTime = config.getConfigIntValue(CONFIGURATION_TYPE.STORAGE, "beginChargeTime");
	    int endChargeTime = config.getConfigIntValue(CONFIGURATION_TYPE.STORAGE, "endChargeTime");

		if(timestep >= beginChargeTime || timestep <= endChargeTime){

		    ProductionLoadHandler plh = new ProductionLoadHandler();
		    double renewableProduction = plh.calculateRenewableProduction(graph);
		    double conventionalProduction = plh.calculateProduction(graph);
		    double realLoad = plh.calculateLoad(graph);

		    double remainRenewableProd = renewableProduction - (realLoad - conventionalProduction);
		    System.out.println(remainRenewableProd);


	    	for(int i=0; i < graph.getNodeList().length; i++){
	    		if(graph.getNodeList()[i].getClass() == Storage.class){
	    			if(remainRenewableProd > 0){
	    			Storage storage = (Storage)graph.getNodeList()[i];
	    			//Set storage to charge and substract the flow from charging from renewable production.
	    			double oldSoC = storage.getCurrentSoC();
    				remainRenewableProd -= Math.abs(storage.charge(remainRenewableProd));
    				if (executePlan == false)
    					storage.setCurrentSoC(oldSoC);
    				graph.getNodeList()[i] = storage;
	    			} else
	    				break;
	    		}
	    	}
		}
		return graph;
	}


    /**
     * Charges storage but only if the current charge is less than 50% of its capacity.
     * @param graph
     * @return graph in which the state of storages has been set.
     */
    public Graph chargeStorage(Graph graph){

        //Double[] sumProdAndLoad = calcSumProductionSumLoad(graph);
        double sumLoads = hpl.calculateLoad(graph);

        for(int i = 0; i < graph.getNodeList().length; i++){
            if(graph.getNodeList()[i].getClass() == Storage.class){
            	Storage storage = (Storage)graph.getNodeList()[i];
                if(((Storage)graph.getNodeList()[i]).getMaximumCharge() * 0.5 > ((Storage)graph.getNodeList()[i]).getCurrentSoC()){
                	double test = ((Storage) graph.getNodeList()[i]).getFlow();
                    sumLoads += ((Storage)graph.getNodeList()[i]).charge(Math.abs(((Storage) graph.getNodeList()[i]).getFlow()));
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
    public Graph chargeOrDischargeStorage(Graph graph){

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
