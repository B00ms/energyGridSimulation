package simulation;

import graph.Graph;
import java.util.List;

/**
 * Created by ejay on 06/07/16.
 */
public class EENSHandler {

    private ProductionLoadHandler productionLoadHandler = new ProductionLoadHandler();

    public double calculateHourlyEENS(Graph realSimulationGraph){
        double hourlyEENS = 0;

        double realLoad 		= productionLoadHandler.calculateLoad(realSimulationGraph);
        double satisfiedLoad 	= productionLoadHandler.calculateSatisfiedLoad(realSimulationGraph);

        if((realLoad - satisfiedLoad) > 0)
            hourlyEENS += (realLoad - satisfiedLoad);

        if(hourlyEENS < 0)
            hourlyEENS = 0;

        //if there is expected energy not supplied then count it as shedded load
        System.out.println("hourly EENS: " + (hourlyEENS));

        return hourlyEENS;
    }

    public Double calculateHourlySheddedLoad(Graph realSimulationGraph){
        double hourlySL = 0;

        double realLoad 		= productionLoadHandler.calculateLoad(realSimulationGraph);
        double satisfiedLoad 	= productionLoadHandler.calculateSatisfiedLoad(realSimulationGraph);

        // if there is expected energy not supplied then count it as shedded load
        System.out.println("EENS: " + (realLoad-satisfiedLoad));

        if((realLoad - satisfiedLoad) < 0)
            hourlySL += Math.abs((realLoad - satisfiedLoad));

        return hourlySL;
    }

    public static Double calculateRealEENS(List<Double> listEENS){
        double sumEENS = 0;
        double realEENS = 0;

        if(listEENS.size() > 0) {

            for (int i = 0; i < listEENS.size(); i++) {
                sumEENS += listEENS.get(i);
            }

            realEENS = sumEENS / listEENS.size();
        }

        return realEENS;
    }

    public boolean checkEENSConvergence(List<Double> listEENS, double EENSConvergenceThreshold){
        double sumEENS = 0;
        double avgEENS = 0;
        boolean EENSConvergence = false;

        if(listEENS.size() > 1){
            for (int i =0; i < listEENS.size()-1; i++){
                sumEENS += listEENS.get(i);
            }

            avgEENS = sumEENS / (listEENS.size()-1);
            System.out.println("sum EENS: " + sumEENS + " " + "average EENS: " + avgEENS + "Amount of days: " + String.valueOf(listEENS.size()-1));
            System.out.println("average - amount of days" + (avgEENS - listEENS.get(listEENS.size()-1)));

            double convergence = Math.abs(avgEENS - listEENS.get(listEENS.size()-1));
            System.out.println("Convergence: "+ convergence +", Threshold: "+ EENSConvergenceThreshold);
            // stop the simulation when converged
            if(convergence <= EENSConvergenceThreshold){
                EENSConvergence = true;
            }
        }

        return EENSConvergence;
    }
}
