package filehandler;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import graph.Graph;
import model.*;
import config.ConfigCollection;
import config.ConfigCollection.CONFIGURATION_TYPE;

public class DataModelPrint {
	private static ConfigCollection config = new ConfigCollection();

	/**
	 * Write storage nodes to txt file
	 * @param gdays	full graph contained for each timestep
	 * @param filename output filename
	 */
	public void printStorageData(Graph[] gdays, String filename) {
		try {
			PrintWriter writer = new PrintWriter(filename, "UTF-8");

			for (int i = gdays[0].getNNode() - gdays[0].getNstorage(); i < gdays[0].getNNode(); i++) {
				writer.print(i + " ");
				for (int j = 0; j < gdays.length; j++) {
					writer.print(((Storage) gdays[j].getNodeList()[i]).getCurrentCharge() + " ");
				}
				writer.println();
				writer.flush();
			}
			writer.close();
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Write graph information to file for usage with glpsol
	 * @param g input graph
	 * @param filename output filename
	 * @param outname current timestep
	 */
	public void createModelInputFile(Graph g, String filename, String outname) {
		float chargeEfficiency = g.getChargeEfficiency();
		float dischargeEfficiency = g.getDischargeEfficiency();
		float delta = g.getDelta();
//		int current_hour = Integer.getInteger(outname);

		int beginChargeTime = config.getConfigIntValue(CONFIGURATION_TYPE.STORAGE, "beginChargeTime");
		int endChargeTime = config.getConfigIntValue(CONFIGURATION_TYPE.STORAGE, "endChargeTime");

		// outname is always equal to current hour
		int n = 0;
		int currentHour = Integer.valueOf(outname);
		if(currentHour>= beginChargeTime || currentHour <= endChargeTime ){
			n = 1;
		}else{
			n = 0;
		}


		try {
			PrintWriter writer = new PrintWriter(filename, "UTF-8");
			writer.println("param n_tgen := " + (g.getNGenerators() - 1) + ";");
			writer.println("param n_rgen := " + g.getNrGenerators() + ";");
			writer.println("param n_cons := " + g.getNConsumers() + ";");
			writer.println("param n_inner := " + (g.getNNode() - 1
					- (g.getNGenerators() - 1 + g.getNrGenerators() + g.getNConsumers()) - g.getNstorage()) + ";");
			writer.println("param n_tot := " + (g.getNNode() - 1) + ";");
			writer.println("param m_factor := 100;");
			writer.println("param pi := 3.1415;");
			writer.println("param n_storage := " + g.getNstorage() + ";");
			writer.println("param totload :=" + g.getLoadmax() + ";");
			writer.println("param cost_curt := " + g.getCostCurtailment() + ";");
			writer.println("param cost_sl := " + g.getCostSheddedLoad() + ";");
			writer.println("param outname := " + outname + ";");
			writer.println("param current_hour := " + currentHour + ";");
			writer.println("param start_charge_time := " + beginChargeTime + ";");
			writer.println("param end_charge_time := " + endChargeTime + ";");
			writer.println("param n := " + n + ";");

			writer.println("param weight :=");

			float[][] edgesPrintArray = new float[g.getNNode()][g.getNNode()];
			for (int e = 0; e < g.getEdges().length; e++){
				int vertexOne = g.getEdges()[e].getEndVertexes()[0];
				int vertexTwo = g.getEdges()[e].getEndVertexes()[1];
				edgesPrintArray[vertexOne][vertexTwo] = (float) g.getEdges()[e].getWeight();
			}

				for (int i = 0; i < edgesPrintArray.length; i++) {
					for (int j = 0; j < edgesPrintArray.length; j++) {
						writer.print("[" + i + "," + j + "] " + edgesPrintArray[i][j] + " ");
					}

				if (i == g.getNNode() - 1)
					writer.println(";");

				else
					writer.println();
				}

			writer.println("param capacity :=");

			edgesPrintArray = new float[g.getNNode()][g.getNNode()];
			for (int e = 0; e < g.getEdges().length; e++){
				int vertexOne = g.getEdges()[e].getEndVertexes()[0];
				int vertexTwo = g.getEdges()[e].getEndVertexes()[1];
				if (g.getEdges()[e].getCapacity() > 0)
					edgesPrintArray[vertexOne][vertexTwo] = (float) g.getEdges()[e].getCapacity() / chargeEfficiency;
			}

			for (int i = 0; i < edgesPrintArray.length; i++) {
				for (int j = 0; j < edgesPrintArray.length; j++) {
					writer.print("[" + i + "," + j + "] " + edgesPrintArray[i][j] + " ");
				}
				if (i == g.getNNode() - 1)
					writer.println(";");
				else
					writer.println();
			}

			int counter = 0;
			writer.println("param costs :=");
			for (int i = 0; i < g.getNodeList().length; i++) {
				if(g.getNodeList()[i].getClass() == ConventionalGenerator.class){
					counter++;
					if (counter == g.getNGenerators())
						writer.println(i + " " + (float)((Generator) g.getNodeList()[i]).getCoef() + ";");
					else
						writer.println(i + " " + (float)((Generator) g.getNodeList()[i]).getCoef());

					}
			}

			counter = 0;
			writer.println("param mintprod :=");
			for (int i = 0; i < g.getNodeList().length; i++) {
				if(g.getNodeList()[i].getClass() == ConventionalGenerator.class){
					counter++;
					if (counter != g.getNGenerators())
						writer.println(i + " " + (float)((Generator) g.getNodeList()[i]).getMinP());
					else
						writer.println(i + " " + (float)((Generator) g.getNodeList()[i]).getMinP() + ";");
					}
			}

			counter = 0;
			writer.println("param maxtprod :=");
			for (int i = 0; i < g.getNodeList().length; i++) {
				if(g.getNodeList()[i].getClass() == ConventionalGenerator.class){
					counter++;
					if (counter != g.getNGenerators())
						writer.println(i + " " + (float)((Generator) g.getNodeList()[i]).getMaxP());
					else
						writer.println(i + " " + (float)((Generator) g.getNodeList()[i]).getMaxP() + ";");
				}
			}
			counter = 0;
			if (g.getNrGenerators() != 0) {
				writer.println("param rprodmax :=");
				for (int i = 0; i < g.getNodeList().length; i++) {
					if(g.getNodeList()[i].getClass() == RenewableGenerator.class){
						counter++;
						if( counter != g.getNrGenerators())
							writer.println(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getMaxP());
						else
							writer.println(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getMaxP() + ";");
					}
				}

			}
			counter = 0;
			if (g.getNrGenerators() != 0) {
				writer.println("param rprodmin :=");
				for (int i = 0; i < g.getNodeList().length; i++) {
						if(g.getNodeList()[i].getClass() == RenewableGenerator.class){
							counter++;
							if(counter != g.getNrGenerators())
								writer.println(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getMinP());
							else
								writer.println(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getMinP() + ";");
					}
				}
			}
			counter = 0;
			if (g.getNrGenerators() != 0) {
				writer.println("param rcost :=");
				for (int i = 0; i < g.getNodeList().length; i++) {
					if(g.getNodeList()[i].getClass() == RenewableGenerator.class){
						counter++;
						if(counter != g.getNrGenerators())
							writer.println(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getCoef());
						else
							writer.println(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getCoef() + ";");
					}
				}
			}
			counter = 0;
			writer.println("param loads :=");
			for (int i = 0; i < g.getNodeList().length; i++) {
				if(g.getNodeList()[i].getClass() == Consumer.class){
					counter++;
					if (counter != g.getNConsumers())
						writer.println(i + " " + (float)((Consumer) g.getNodeList()[i]).getLoad());
					else
						writer.println(i + " " + (float)((Consumer) g.getNodeList()[i]).getLoad() + ";");
				}
			}
			counter = 0;
			writer.println("param production :=");
			for (int i = 0; i < g.getNodeList().length; i++) {
				if(g.getNodeList()[i].getClass() == ConventionalGenerator.class){
					counter++;
					if (counter != g.getNGenerators())
						writer.println(((ConventionalGenerator) g.getNodeList()[i]).getNodeId() + " " + (float)((ConventionalGenerator) g.getNodeList()[i]).getProduction());
					else
						writer.println(((ConventionalGenerator) g.getNodeList()[i]).getNodeId() + " " + (float)((ConventionalGenerator) g.getNodeList()[i]).getProduction() + ";");
				}
			}
			counter = 0;
			writer.println("param rewproduction :=");
			for (int i = 0; i < g.getNodeList().length; i++) {
				if(g.getNodeList()[i].getClass() == RenewableGenerator.class){
					counter++;
					if (counter != g.getNrGenerators())
						writer.println(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getProduction());
					else
						writer.println(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getProduction() + ";");
				}
			}
			counter = 0;
			writer.println("param flowfromstorage :=");
			for (int i = 0; i < g.getNodeList().length; i++) {
				if(g.getNodeList()[i].getClass() == Storage.class){
					counter++;

					if (counter != g.getNstorage()) {
						writer.println(i + " " + (float)((Storage) g.getNodeList()[i]).getFlow());
					} else {
						writer.println(i + " " + (float)((Storage) g.getNodeList()[i]).getFlow() + ";");
					}
				}
			}
			counter = 0;
			if (g.getNstorage() != 0) {
				writer.println("param storagemax :=");
				for (int i = 0; i < g.getNodeList().length; i++) {
					if(g.getNodeList()[i].getClass() == Storage.class){
						double cap = 0;

						/*if (((Storage) g.getNodeList()[i]).getMinimumCharge() > ((Storage) g.getNodeList()[i]).getCurrentCharge()) {
							((Storage) g.getNodeList()[i]).charge(((Storage) g.getNodeList()[i]).getMinimumCharge());
						}*/
						Storage stor = (Storage) g.getNodeList()[i];
						double val = (((Storage) g.getNodeList()[i]).getCurrentCharge()	- ((Storage) g.getNodeList()[i]).getMinimumCharge()) / delta * dischargeEfficiency;
						//System.out.print(stor.getCurrentCharge() + " ");
						//System.out.println(val);
						//TODO: val is negative sometimes.
						for (int j = 0; j < g.getNNode(); j++) {
							if (g.getEdges()[j].getCapacity() != 0.0F) {
								cap = g.getEdges()[j].getCapacity();
								break;
							}
						}
						counter++;
						if (cap * dischargeEfficiency < val) {
							if (counter != g.getNstorage())
								writer.println(i + " " + cap * dischargeEfficiency);
							else
								writer.println(i + " " + cap * dischargeEfficiency + ";");

						} else if (counter != g.getNstorage()) {
							writer.println(i + " " + val);
						} else
							writer.println(i + " " + val + ";");

					}
				}
			}
			counter = 0;
			if (g.getNstorage() != 0) {
				writer.println("param storagemin :=");
				for (int i = 0; i < g.getNodeList().length; i++) {
					if(g.getNodeList()[i].getClass() == Storage.class){
						double cap = 0;
						float eps = 0.001F;


						/*if (((Storage) g.getNodeList()[i]).getMaximumCharge() < ((Storage) g.getNodeList()[i]).getCurrentCharge()) {
							((Storage) g.getNodeList()[i]).charge(((Storage) g.getNodeList()[i]).getMaximumCharge());
						}*/

//						double max = ((Storage) g.getNodeList()[i]).getMaximumCharge();
//						double current = ((Storage) g.getNodeList()[i]).getCurrentCharge();
						// chargeEfficiency is (dis)charge efficiency
						double val = (((Storage) g.getNodeList()[i]).getMaximumCharge() - ((Storage) g.getNodeList()[i]).getCurrentCharge()) / delta / chargeEfficiency;

						for (int j = g.getNGenerators() + g.getNConsumers(); j < g.getNNode() - (g.getNstorage() + g.getNrGenerators()); j++) {
							if (g.getEdges()[j].getCapacity() != 0.0F) {
								cap = g.getEdges()[j].getCapacity();
								break;
							}
						}

						counter++;
						if (cap / chargeEfficiency < val) {
							if (counter != g.getNstorage())
								writer.println(i + " -" + cap / chargeEfficiency);
							else
								writer.println(i + " -" + cap / chargeEfficiency + ";");

						} else if (counter != g.getNstorage()) {
							//writer.println(i + " -" + val);
							writer.println(i +  val);
						} else
							writer.println(i + val + ";");
							//writer.println(i + " -" + val + ";");
					}
				}
			}
			writer.println("end;");
			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
}
