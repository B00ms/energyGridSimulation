package simulation;


import com.rits.cloning.Cloner;

import config.ConfigCollection;
import config.ConfigCollection.CONFIGURATION_TYPE;
import filehandler.Parser;
import graph.Edge;
import graph.Graph;
import graph.Node;
import model.Consumer;
import model.ConventionalGenerator;
import model.RenewableGenerator;
import model.Storage;

/**
 * Initializes graphs for each timestep
 */
public class SimulationStateInitializer {
	private Graph g;
	private Graph[] gDay;

	private Cloner cloner = new Cloner();
	private static ConfigCollection config = new ConfigCollection();

	private Float[] loads;
	Parser parser = new Parser();

	/**
	 * Create graphs for every timestep and apply load initialization
	 * @param g original graph
	 * @param gDay	timestep
	 * @return	list of graph for each timestep
	 */
	public Graph[] creategraphs(Graph g, Graph[] gDay) {
		this.g = g;
		this.gDay = gDay;

		// parse expected hourly load from input file
		this.loads = parser.parseExpectedHourlyLoad();

		for (int i = 0; i < this.gDay.length; i++) {
			//this.gDay[i] = g.clone();
			//this.gDay[i] = cloner.deepClone(g);
			this.gDay[i] = parser.parseData(config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "input-file"));
		}

		calculateLoads();
		calculateRewProd();
		checkGen();
		return gDay;
	}

	/**
	 * Update storage nodes
	 * @param oldg
	 * @param newg
	 * @return
	 */
	public Graph updateStorages(Graph oldg, Graph newg) {
		for (int i = 0; i < oldg.getNodeList().length; i++) {
			if(oldg.getNodeList()[i].getClass() == Storage.class) {
				Storage storage = (Storage) oldg.getNodeList()[i];
				newg.getNodeList()[i] = cloner.deepClone(storage);

				/*float chargeEfficiency = oldg.getChargeEfficiency();
				float dischargeEfficiency = oldg.getDischargeEfficiency();
				float delta = oldg.getDelta();
				double av = ((Storage) oldg.getNodeList()[i]).getCurrentCharge();
				double flow = ((Storage) oldg.getNodeList()[i]).getFlow();
				for (int j = 0; j < oldg.getNNode(); j++) {
					flow += oldg.getEdges()[j].getFlow();
				}


				if (flow >= 0.0F)
					((Storage) newg.getNodeList()[i]).charge(av - flow / dischargeEfficiency * delta);
				else
					((Storage) newg.getNodeList()[i]).charge(av - flow * chargeEfficiency * delta);*/
			}
		}
		return newg;
	}

	/**
	 * For each of the timestep checks if the current load is higher than 70% of the maximum load
	 * 70% of maximum load > load demand = disable hydro plant;
	 * TODO this is old and this check should possibly be done later on
	 */
	private void checkGen() {
		// todo ASK laura if this is still valid if so, when to disable
		// todo this should probably happen during planning phase or something
		for (int i = 0; i < this.gDay.length; i++) {
			if (this.g.getLoadmax() / 100 * 10 > this.loads[i]) {
				for (int j = 0; j < this.g.getNGenerators(); j++) {
					if(g.getNodeList()[j].getClass() == ConventionalGenerator.class){
						if ((((ConventionalGenerator) g.getNodeList()[j]).getType()).equals("H")){
							((ConventionalGenerator) gDay[i].getNodeList()[j]).setGeneratorDisabled(true);

						}
					}
				}
			}
		}
	}

	/**
	 * Calculates the max/min production of renewable generators
	 * "S" solar
	 * "W" wind
	 */
	private void calculateRewProd() {
		for (int i = 0; i < gDay.length; i++){
			for (int j = 0; j < gDay[i].getNodeList().length; j++){
				if(gDay[i].getNodeList()[i].getClass() == RenewableGenerator.class){
					((RenewableGenerator)gDay[i].getNodeList()[j]).setProduction(((RenewableGenerator) gDay[i].getNodeList()[j]).getMaxP() * ((RenewableGenerator) gDay[i].getNodeList()[j]).getProduction());
				}
			}
		}
	}

	/**
	 * Calculates the load on current time step for each consumer in the grid
	 * Uses total load for timestep i (totloads)
	 * Consumer has percentage of total load predefined (perloads)
	 */
	private void calculateLoads() {
		for (int i = 0; i < gDay.length; i++) {
			for (int j = 0; j < g.getNodeList().length; j++){
				if(gDay[i].getNodeList()[j].getClass() == Consumer.class){
					double load = loads[i] / 100 * ((Consumer)g.getNodeList()[j]).getLoad();
					((Consumer)gDay[i].getNodeList()[j]).setLoad(loads[i] / 100 * ((Consumer)g.getNodeList()[j]).getLoad());
				}
			}
		}
	}
}
