package simulation;

import config.ConfigCollection;
import graph.Graph;
import model.ConventionalGenerator;
import model.Generator;
import model.RenewableGenerator;
import net.e175.klaus.solarpositioning.AzimuthZenithAngle;
import net.e175.klaus.solarpositioning.DeltaT;
import net.e175.klaus.solarpositioning.Grena3;
import net.e175.klaus.solarpositioning.SPA;
import model.Generator.GENERATOR_TYPE;

import java.util.*;

/**
 * Created by ejay on 23/06/16.
 * Contains all of the functionality that applies the monte carlo draws to the grid
 */
public class SimulationMonteCarloDraws {

    private static ConfigCollection config = new ConfigCollection();

    /**
     * Set the failure state of generators and calculates the production of renewable production.
     * @return Graphs of which the state has been changed using Monte Carlo draws
     */
    public Graph randomizeGridState(Graph graph, int currentTimeStep, int month) {
        // randomize conventional generator data
        graph = randomizeConventionalGenerator(graph);

        // randomize renewable generators
        graph = randomizeRenewableGenerator(graph, currentTimeStep, month);

        return graph;
    }

    /**
     * Randomize the failure status of conventional generator data
     * @param graph
     * @return
     */
    private Graph randomizeConventionalGenerator(Graph graph){
        MontoCarloHelper monteCarloHelper = new MontoCarloHelper();

        for (int j = 0; j < graph.getNodeList().length - 1; j++) {
            // Check the class of the current node and deal with it accordingly.
            if (graph.getNodeList()[j] != null && (graph.getNodeList()[j].getClass() == ConventionalGenerator.class)) {
                GENERATOR_TYPE generatorType = ((Generator) graph.getNodeList()[j]).getType();
                double mcDraw = 0; // This will hold our Monte Carlo draw
                // (hahaha mac draw)
                switch (generatorType) {
                    case HYDRO: // Hydro-eletric generator
                        // TODO Ignore this for now, might be added at a later stage
                        break;
                    case OIL: // Oil Thermal generator
                        mcDraw = monteCarloHelper.getRandomUniformDist();
                        // System.out.println(mcDraw);
                        graph = checkConventionalGeneratorFailure(graph, j, mcDraw);
                        break;
                    case NUCLEAR: // Nuclear Thermal generator
                        mcDraw = monteCarloHelper.getRandomUniformDist();
                        // System.out.println(mcDraw);
                        graph = checkConventionalGeneratorFailure(graph, j, mcDraw);
                        break;
                    case COAL: // Coal Thermal generator
                        mcDraw = monteCarloHelper.getRandomUniformDist();
                        // System.out.println(mcDraw);
                        graph = checkConventionalGeneratorFailure(graph, j, mcDraw);
                        break;
                    default:
                        //We Don't care about renewables at this point.
                        break;
                }
            }
        }
        return graph;
    }

    /**
     * Sets the state of conventional generators to on or off.
     * based on monte carlo draw
     * @param graph
     * @param node
     * @param mcDraw
     * @return
     */
    private Graph checkConventionalGeneratorFailure(Graph graph, int node, double mcDraw) {
        if (((ConventionalGenerator) graph.getNodeList()[node]).getGeneratorFailure() == false) {// 0  means that  the reactor can fail.
            int nodeMTTF = ((ConventionalGenerator) graph.getNodeList()[node]).getMTTF();
            float mttf = (float) 1 / nodeMTTF;
            if (mcDraw < mttf) {
                // Our draw is smaller meaning that the generator has failed.
                ((ConventionalGenerator) graph.getNodeList()[node]).setGeneratorFailure(true);
            }
        }
        return graph;
    }


    /**
     * Does monte carlo draws for wind and solar generators and sets their production according to these draws.
     * @param graph
     * @param currentTimeStep used for solar data generation
     * @return The graph in which renewable production has been set.
     */
    public Graph randomizeRenewableGenerator(Graph graph, int currentTimeStep, int month) {
        MontoCarloHelper monteCarloHelper = new MontoCarloHelper();

        for (int j = 0; j < graph.getNodeList().length - 1; j++) {
            // Check the class of the current node and deal with it accordingly.
            if (graph.getNodeList()[j] != null && (graph.getNodeList()[j].getClass() == ConventionalGenerator.class
                    || graph.getNodeList()[j].getClass() == RenewableGenerator.class)) {
                GENERATOR_TYPE generatorType = ((Generator) graph.getNodeList()[j]).getType();
                double mcDraw = 0; // This will hold our Monte Carlo draw
                switch (generatorType) {
                    case WIND: // Wind park generator
                        mcDraw = monteCarloHelper.getRandomWeibull();
                        double vCutIn = config.getConfigDoubleValue(ConfigCollection.CONFIGURATION_TYPE.WIND_GENERATOR, "vCutIn");
                        double vCutOff = config.getConfigDoubleValue(ConfigCollection.CONFIGURATION_TYPE.WIND_GENERATOR, "vCutOff");
                        double vRated = config.getConfigDoubleValue(ConfigCollection.CONFIGURATION_TYPE.WIND_GENERATOR, "vRated");
                        double pRated = config.getConfigDoubleValue(ConfigCollection.CONFIGURATION_TYPE.WIND_GENERATOR, "pRated");

                        if (mcDraw <= vCutIn || mcDraw >= vCutOff) {
                            // Wind speed is outside the margins
                            ((RenewableGenerator) graph.getNodeList()[j]).setProduction(0);
                        } else if (mcDraw >= vCutIn && mcDraw <= vRated) {
                            // In a sweet spot for max wind production
                            double production = (pRated * ((Math.pow(mcDraw, 3) - Math.pow(vCutIn, 3))
                                    / (Math.pow(vRated, 3) - Math.pow(vCutIn, 3))));// Should be the same as the matlab from Laura
                            ((RenewableGenerator) graph.getNodeList()[j]).setProduction(production);
                        } else if (vRated <= mcDraw && mcDraw <= vCutOff) {
                            ((RenewableGenerator) graph.getNodeList()[j]).setProduction(pRated);
                        }
                        break;
                    case SOLAR: // Solar generator
                        mcDraw = monteCarloHelper.getRandomGamma();

                        double irradianceConstant = config.getConfigDoubleValue(ConfigCollection.CONFIGURATION_TYPE.SOLAR_GENERATOR, "irradianceConstant");
                        double eccentricityCorrFactor = config.getConfigDoubleValue(ConfigCollection.CONFIGURATION_TYPE.SOLAR_GENERATOR, "eccentricity");
                        double langitude = config.getConfigDoubleValue(ConfigCollection.CONFIGURATION_TYPE.SOLAR_GENERATOR, "langitude"); ;
                        double longitude = config.getConfigDoubleValue(ConfigCollection.CONFIGURATION_TYPE.SOLAR_GENERATOR, "longitude"); ;
                        //int month = config.getConfigIntValue(ConfigCollection.CONFIGURATION_TYPE.SOLAR_GENERATOR, "month"); ;
                        int year = config.getConfigIntValue(ConfigCollection.CONFIGURATION_TYPE.SOLAR_GENERATOR, "year"); ;

                        GregorianCalendar calendar = new GregorianCalendar(year, month, 14, currentTimeStep, 0);
                        double deltaT = DeltaT.estimate(calendar);
                        GregorianCalendar[] sunriseset = SPA.calculateSunriseTransitSet(calendar, langitude, longitude, deltaT);

                        int sunrise = sunriseset[0].get(Calendar.HOUR_OF_DAY);
                        int sunset = sunriseset[2].get(Calendar.HOUR_OF_DAY);

                        // We want to find the maximum Extraterrestial irradiance of the day.
                        double extratIrradianceMax = 0;
                        for (int i = 0; i < 24; i++) {
                            GregorianCalendar cal = new GregorianCalendar(year, month, 14, i, 0);
                            AzimuthZenithAngle azimuthZenithAgnle = Grena3.calculateSolarPosition(cal, langitude, longitude, deltaT);
                            double zenithAngle = azimuthZenithAgnle.getZenithAngle();

                            double extratIrradiance = irradianceConstant * ( 1 + eccentricityCorrFactor * Math.cos((2 * Math.PI * calendar.get(Calendar.DAY_OF_YEAR)) / 365)) * Math.cos(zenithAngle);
                            if (extratIrradiance > extratIrradianceMax)
                                extratIrradianceMax = extratIrradiance;
                        }
                        double sMax = extratIrradianceMax * mcDraw;
                        double irradiance;
                        if ((currentTimeStep <= sunrise) || (currentTimeStep >= sunset))
                            irradiance = 0;
                        else
                            irradiance = sMax * Math.sin(Math.PI * (currentTimeStep - sunrise) / (sunset - sunrise));

                        double efficiency = config.getConfigDoubleValue(ConfigCollection.CONFIGURATION_TYPE.SOLAR_GENERATOR, "panelEfficiency");
                        // surface array of panels in m², efficiency, irradiance of panels on the horizontal plane.
                        double production = 550000 * efficiency * irradiance;
                        production = production /1000000;
                        ((RenewableGenerator) graph.getNodeList()[j]).setProduction(production);
                        // System.out.println("sunRise:" + sunrise + " currentTime:" + currentTimeStep + " sunset:" + sunset + " production:" + production + " max irradiance:" + extratIrradianceMax + " MC draw:" + mcDraw + " nodeId:" + ((RewGenerator)graph.getNodeList()[j]).getNodeId());

                        break;
				default:
					break;
                }
            }
        }

        return graph;
    }

}
