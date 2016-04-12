package pgrid_opt;

public class InstanceRandomizer {
	private Graph g;

	private Graph[] gDay;

	private float[] solar;

	private float[] wind;

	private float[] loads;

	public Graph[] creategraphs(Graph g, Graph[] gDay, float[] solar, float[] wind, float[] loads) {
		this.g = g;
		this.gDay = gDay;
		this.solar = solar;
		this.wind = wind;
		this.loads = loads;
		for (int i = 0; i < this.gDay.length; i++) {
			this.gDay[i] = g.clone();
		}
		calculateLoads();
		calculateRewProd();
		checkGen();
		return gDay;
	}

	public Graph updateStorages(Graph oldg, Graph newg) {
		for (int i = oldg.getNNode() - oldg.getNstorage(); i < oldg.getNNode(); i++) {
			float etac = oldg.getEtac();
			float etad = oldg.getEtad();
			float delta = oldg.getDelta();
			float av = ((Storage) oldg.getNodeList()[i]).getAvaliability();
			float flow = 0.0F;
			for (int j = 0; j < oldg.getNNode(); j++) {
				flow += oldg.getNetwork()[i][j].getFlow();
			}
			if (flow >= 0.0F)
				((Storage) newg.getNodeList()[i]).setAvaliability(av - flow / etad * delta);
			else
				((Storage) newg.getNodeList()[i]).setAvaliability(av - flow * etac * delta);
		}
		return newg;
	}

	private void checkGen() {
		for (int i = 0; i < this.gDay.length - 1; i++) {
			if (this.g.getLoadmax() / 100 * 70 > this.loads[i]) {
				for (int j = 0; j < this.g.getNGenerators(); j++) {
					if (("H".compareTo(((Generator) this.g.getNodeList()[j]).getType()) == 0)
							|| ("H".compareTo(((Generator) this.g.getNodeList()[j]).getType()) == 1)) {
						((Generator) this.gDay[i].getNodeList()[j]).setMaxP(0.0F);
						((Generator) this.gDay[i].getNodeList()[j]).setMinP(0.0F);
					}
				}
			}
		}
	}

	private void calculateRewProd() {
		for (int i = 0; i < this.gDay.length - 1; i++) {
			for (int j = this.g.getNNode() - this.g.getNrgenetarors() - this.g.getNstorage(); j < this.g.getNNode()
					- this.g.getNstorage(); j++) {
				if (("S".compareTo(((Generator) this.g.getNodeList()[j]).getType()) == 1)
						|| ("S".compareTo(((Generator) this.g.getNodeList()[j]).getType()) == 0)) {
					((RewGenerator) this.gDay[i].getNodeList()[j])
							.setProduction(((RewGenerator) this.gDay[i].getNodeList()[j]).getMaxP() * this.solar[i]);
				} else {
					((RewGenerator) this.gDay[i].getNodeList()[j])
							.setProduction(((RewGenerator) this.gDay[i].getNodeList()[j]).getMaxP() * this.wind[i]);
				}
			}
		}
	}

	private void calculateLoads() {
		for (int i = 0; i < this.gDay.length - 1; i++) {
			for (int j = this.g.getNGenerators(); j < this.g.getNGenerators() + this.g.getNConsumers(); j++) {
				((Consumer) this.gDay[i].getNodeList()[j])
						.setLoad(this.loads[i] / 100.0F * ((Consumer) this.g.getNodeList()[j]).getLoad());
			}
		}
	}
}
