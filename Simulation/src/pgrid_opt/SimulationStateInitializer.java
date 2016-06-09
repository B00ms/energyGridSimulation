package pgrid_opt;

public class SimulationStateInitializer {
	private Graph g;
	private Graph[] gDay;

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
			this.gDay[i] = g.clone();
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
				float etac = oldg.getEtac();
				float etad = oldg.getEtad();
				float delta = oldg.getDelta();
				double av = ((Storage) oldg.getNodeList()[i]).getCurrentCharge();
				float flow = 0.0F;
				for (int j = 0; j < oldg.getNNode(); j++) {
					flow += oldg.getEdges()[j].getFlow();
				}
				if (flow >= 0.0F)
					((Storage) newg.getNodeList()[i]).charge(av - flow / etad * delta);
				else
					((Storage) newg.getNodeList()[i]).charge(av - flow * etac * delta);
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
		/*for (int i = 0; i < this.gDay.length - 1; i++) {

			if (this.g.getLoadmax() / 100 * 70 > this.loads[i]) {

				for (int j = 0; j < this.g.getNGenerators(); j++) {
					if (	("H".compareTo(((ConventionalGenerator) this.g.getNodeList()[j]).getType()) == 0)
						||  ("H".compareTo(((ConventionalGenerator) this.g.getNodeList()[j]).getType()) == 1))
					{
						((ConventionalGenerator) this.gDay[i].getNodeList()[j]).setMaxP(0.0F);
						((ConventionalGenerator) this.gDay[i].getNodeList()[j]).setMinP(0.0F);
					}
				}
			}


		}*/
		for (int i = 0; i < this.gDay.length; i++) {
			// todo check if loadmax is ever updated should probably be after first day ahead simulation
			if (this.g.getLoadmax() / 100 * 70 > ((ConventionalGenerator)g.getNodeList()[i]).getProduction()) {

				for (int j = 0; j < this.g.getNGenerators(); j++) {
					if(g.getNodeList()[j].getClass() == ConventionalGenerator.class){
						if ((((ConventionalGenerator) g.getNodeList()[j]).getType()).equals("H")){
							((ConventionalGenerator) gDay[i].getNodeList()[j]).setMaxP(0.0F);
							((ConventionalGenerator) gDay[i].getNodeList()[j]).setMinP(0.0F);
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
				if(gDay[i].getNodeList()[i].getClass() == RewGenerator.class){
					((RewGenerator)gDay[i].getNodeList()[j]).setProduction(((RewGenerator) gDay[i].getNodeList()[j]).getMaxP() * ((RewGenerator) gDay[i].getNodeList()[j]).getProduction());
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
