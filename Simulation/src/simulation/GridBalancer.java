package simulation;

import config.ConfigCollection;
import graph.Graph;
import graph.Node;
import model.ConventionalGenerator;
import model.Generator;
import model.Offer;
import model.RenewableGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by ejay on 23/06/16.
 * Contains all the functionality to balance the Grid
 */
public class GridBalancer {
    private static ConfigCollection config = new ConfigCollection();
    private static ProductionLoadHandler productionLoadHandler = new ProductionLoadHandler();
    private static StorageHandler storageHandler = new StorageHandler();

    /**
     * Depending on the state of the grid this method will increase or decrease
     * production in order to balance the system
     */
    public Graph checkGridEquilibrium(Graph grid, int timestep) {
        Node[] nodeList = grid.getNodeList();
        double totalCurrentProduction = 0;
        double sumLoads = 0;
        double realLoad = 0;
        double renewableProduction = productionLoadHandler.calculateRenewableProduction(grid);
        double realProduction = 0; //TODO: check that real production is correctly adjusted when production changes.

        int beginTime = config.getConfigIntValue(ConfigCollection.CONFIGURATION_TYPE.STORAGE, "beginChargeTime");
        int endTime = config.getConfigIntValue(ConfigCollection.CONFIGURATION_TYPE.STORAGE, "endChargeTime");

        boolean dischargeAllowed = true;
        // timestep >= 23 && timestep <= 4
        if(timestep >= beginTime || timestep <= endTime){
            grid = storageHandler.chargeStorage(grid);
            dischargeAllowed = false;
        }
        realLoad = productionLoadHandler.calculateLoad(grid);

        // deltaP = 0 needs to be satisfied
        realProduction = productionLoadHandler.calculateProduction(grid);
        // real load fix when real load is negative
        double deltaP = (realProduction - realLoad);
        //System.out.println("RealProduction: " + realProduction + " " + "realLoad: "+ realLoad);

        // Check if we need to increase current production
        if (deltaP < 0) {
            System.out.println("Increasing production ");

            List<Offer> offers = new ArrayList<Offer>();

            // find cheapest offers
            for (int i = 0; i < nodeList.length; i++) {
                if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class) {

                    Offer[] offerList = ((ConventionalGenerator) nodeList[i]).getIncreaseProductionOffers();
                    offers.add(offerList[0]);
                    offers.add(offerList[1]);
                }
            }

            // sort offers best value for money
            Collections.sort(offers);

            for (int i = 0; i < offers.size(); i++) {
                Offer offer = offers.get(i);
                double offeredProduction = offer.getProduction();
                if (deltaP < 0 && offer.getAvailable()) {
                    ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).takeIncreaseOffer(offer.getOfferListId());
                    double newProduction = ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).getProduction()
                            + offer.getProduction();


                    if (Math.abs(deltaP) <= newProduction) {
                        totalCurrentProduction += ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(Math.abs(deltaP));
                    } else {
                        totalCurrentProduction += ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).setProduction(newProduction);
                    }
                    offers.remove(i); // remove offer from list
                    deltaP = (totalCurrentProduction - sumLoads); // update demand
                }
            }

            for (int i = 0; i < offers.size(); i++) {
                Offer offer = offers.get(i);
                double offeredProduction = offer.getProduction();

                // check if deltaP isn't satisfied, and if offer is available
                if (deltaP < 0 && offer.getAvailable()) {
                    ConventionalGenerator generator = (ConventionalGenerator) nodeList[offer.getNodeIndex()];
                    if((deltaP+offeredProduction) <= 0){ // take offer
                        double oldProduction = generator.getProduction();
                        double newProduction = generator.setProduction(generator.getProduction() + offer.getProduction());
                        if(oldProduction != newProduction)
                            realProduction += offer.getProduction();
                    }else if((deltaP+offeredProduction)>0){ // only take difference between deltaP and offeredProduction
                        double oldProduction = generator.getProduction();
                        double remainingProduction = offeredProduction-(offeredProduction+deltaP);
                        double newProduction = generator.setProduction(generator.getProduction() + remainingProduction);
                        if(oldProduction != newProduction)
                            realProduction += remainingProduction;
                    }

                    nodeList[offer.getNodeIndex()] = generator;
                    // disable offer from generator
                    ((ConventionalGenerator) nodeList[offer.getNodeIndex()]).takeDecreaseOffer(offer.getOfferListId());
                    deltaP = (realProduction - realLoad); // update deltaP
                }else{
                    break; // load is satisfied
                }
            }

        } else if (deltaP > 0) {
            // we need to decrease energy production
            System.out.println("Decreasing production ");

            //TODO: take offers from cheapest nodes to decrease production.
            //TODO: generate real offers not from input file
            List<Offer> offers = new ArrayList<>();

            // find cheapest offers
            for (int i = 0; i < nodeList.length - 1; i++) {
                if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class) {
                    Offer[] offerList = ((ConventionalGenerator) nodeList[i]).getDecreaseProductionOffers();
                    offers.add(offerList[0]);
                    offers.add(offerList[1]);
                }
            }

            // sort offers by cheapest offer
            Collections.sort(offers);

            // decrease production by taking offers
            for (int i = 0; i < offers.size(); i++) {
                Offer offer = offers.get(i);
                double offeredProduction = offer.getProduction();
                ConventionalGenerator convGenerator = null;

                for(int j = 0; j < nodeList.length; j++){
                    if(nodeList[j].getClass() == ConventionalGenerator.class)
                        convGenerator = ((ConventionalGenerator) nodeList[j]);

                    if(convGenerator != null && convGenerator.getNodeId() == offer.getNodeIndex()){
                        // check if deltaP isn't satisfied, and if offer is available
                        if (deltaP > 0 && offer.getAvailable()) {
                            if((deltaP-offeredProduction) >= 0){ // take offer
                                double oldProduction = convGenerator.getProduction();
                                double newProduction = convGenerator.setProduction(convGenerator.getProduction() - offer.getProduction());
                                if(oldProduction != newProduction)
                                    realProduction -= offer.getProduction();
                            }else if(offer.getProduction() > deltaP){ // only take difference between deltaP and offeredProduction
                                double oldProduction = convGenerator.getProduction();
                                double newProduction = convGenerator.setProduction( convGenerator.getProduction() - (deltaP));
                                if(oldProduction != newProduction)
                                    realProduction -= (deltaP);
                            }
                            // disable offer from generator
                            convGenerator.takeDecreaseOffer(offer.getOfferListId());
                            nodeList[j] = convGenerator;
                            grid.setNodeList(nodeList);
                            deltaP = (realProduction - realLoad); // update deltaP
                        }else{
                            break; // load is satisfied
                        }
                    }
                }
            }

            if(deltaP > 0){
                // turn off OIL generators when Production is still to high.
                // loop over generators and when this.maxP < 60 disable generator
                for (int i = 0; i < nodeList.length; i++) {
                    if (nodeList[i] != null && nodeList[i].getClass() == ConventionalGenerator.class && ((ConventionalGenerator) nodeList[i]).getType() == Generator.GENERATOR_TYPE.OIL) {
                        double maxP = ((ConventionalGenerator) nodeList[i]).getMaxP();

                        int disableCheapGeneratorThresholdMaxP = config.
                                getConfigIntValue(ConfigCollection.CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "disableCheapGeneratorThresholdMaxP");

                        if(maxP < disableCheapGeneratorThresholdMaxP && deltaP >0){
                            realProduction -= ((ConventionalGenerator) nodeList[i]).getProduction();
                            ((ConventionalGenerator) nodeList[i]).disableProduction();

                            deltaP = (realProduction - realLoad); // update deltaP
                        }
                    }
                }
            }
        } else {
            System.out.print("Grid is balanced ");
            return grid;
        }
        //System.out.println("Production after turning conventional generators off: " + realProduction);

        // update nodeList
        grid.setNodeList(nodeList);

        if(dischargeAllowed){
            grid = storageHandler.chargeOrDischargeStorage(grid);
            realProduction = productionLoadHandler.calculateProduction(grid);
            realLoad = productionLoadHandler.calculateLoad(grid);
        }
        //System.out.println("Prod after charging storage:" + realProduction);
        //System.out.println("Load after charging storage:" + realLoad);
        //System.out.println("Renewable prod: " + productionLoadHandler.calculateRenewableProduction(grid));

        if(realProduction - realLoad > 0){
            //grid = curtailRenewables(grid, realProduction, realLoad);
            realProduction = productionLoadHandler.calculateProduction(grid);
            realLoad = productionLoadHandler.calculateLoad(grid);
           // System.out.println("After cuirtailment Real production: " + productionLoadHandler.calculateProduction(grid) + " Total load: " + productionLoadHandler.calculateLoad(grid));
            //System.out.println("Renewable prod: " + productionLoadHandler.calculateRenewableProduction(grid));
        }

        realProduction = productionLoadHandler.calculateProduction(grid);
        realLoad = productionLoadHandler.calculateLoad(grid);
        //System.out.print("After balancing - ");
        //System.out.println("Real production: " + realProduction + " Total load: " + realLoad);
        return grid;
    }

    private static Graph curtailRenewables(Graph grid, double realProduction, double realLoad) {

        double productionTarget = realProduction - realLoad;
        System.out.println("RenewProd before curtailment " + productionLoadHandler.calculateRenewableProduction(grid));
        for( int i = 0; i < grid.getNodeList().length; i++){
            if(grid.getNodeList()[i].getClass() == RenewableGenerator.class){
                RenewableGenerator renew = ((RenewableGenerator)grid.getNodeList()[i]);
                if(productionTarget - renew .getProduction() > 0 && productionTarget != 0){
                    productionTarget -= renew .getProduction();
                    renew.setProduction(0);
                }else if(productionTarget > 0){
                    renew .setProduction(renew .getProduction()-productionTarget);
                    productionTarget -= productionTarget;
                    break;
                }
                grid.getNodeList()[i] = renew;
            }
        }
        System.out.print("Load: " + productionLoadHandler.calculateLoad(grid));
        System.out.println(" renewProd after curtailment " + productionLoadHandler.calculateRenewableProduction(grid));

        return grid;
    }
}
