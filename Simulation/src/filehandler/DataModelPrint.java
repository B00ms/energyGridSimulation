package filehandler;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import org.apache.commons.lang3.text.StrBuilder;

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
					writer.print(((Storage) gdays[j].getNodeList()[i]).getCurrentSoC() + " ");
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

		StringBuilder strBuilder = new StringBuilder();

		try {
			PrintWriter writer = new PrintWriter(filename, "UTF-8");
			strBuilder.append("param n_tgen := " + (g.getNGenerators() - 1) + ";\n");
			strBuilder.append("param n_rgen := " + g.getNrGenerators() + ";\n");
			strBuilder.append("param n_cons := " + g.getNConsumers() + ";\n");
			strBuilder.append("param n_inner := " + (g.getNNode() - 1
					- (g.getNGenerators() - 1 + g.getNrGenerators() + g.getNConsumers()) - g.getNstorage()) + ";\n");
			strBuilder.append("param n_tot := " + (g.getNNode() - 1) + ";\n");
			strBuilder.append("param m_factor := 100;\n");
			strBuilder.append("param pi := 3.1415;\n");
			strBuilder.append("param n_storage := " + g.getNstorage() + ";\n");
			strBuilder.append("param totload :=" + g.getLoadmax() + ";\n");
			strBuilder.append("param cost_curt := " + g.getCostCurtailment() + ";\n");
			strBuilder.append("param cost_sl := " + g.getCostSheddedLoad() + ";\n");
			strBuilder.append("param outname := " + outname + ";\n");
			strBuilder.append("param current_hour := " + currentHour + ";\n");
			strBuilder.append("param start_charge_time := " + beginChargeTime + ";\n");
			strBuilder.append("param end_charge_time := " + endChargeTime + ";\n");
			strBuilder.append("param n := " + n + ";\n");

			strBuilder.append("param weight :=\n");
			//writer.print(strBuilder.toString());

			float[][] edgesPrintArray = new float[g.getNNode()][g.getNNode()];
			for (int e = 0; e < g.getEdges().length; e++){
				int vertexOne = g.getEdges()[e].getEndVertexes()[0];
				int vertexTwo = g.getEdges()[e].getEndVertexes()[1];
				edgesPrintArray[vertexOne][vertexTwo] = (float) g.getEdges()[e].getWeight();
			}

				for (int i = 0; i < edgesPrintArray.length; i++) {
					for (int j = 0; j < edgesPrintArray.length; j++) {
						strBuilder.append("[" + i + "," + j + "] " + edgesPrintArray[i][j] + " ");
					}

				if (i == g.getNNode() - 1)
					strBuilder.append(";\n");

				else
					strBuilder.append("\n");
				}

				strBuilder.append("param capacity :=\n");

			edgesPrintArray = new float[g.getNNode()][g.getNNode()];
			for (int e = 0; e < g.getEdges().length; e++){
				int vertexOne = g.getEdges()[e].getEndVertexes()[0];
				int vertexTwo = g.getEdges()[e].getEndVertexes()[1];
				if (g.getEdges()[e].getCapacity() > 0)
					edgesPrintArray[vertexOne][vertexTwo] = (float) g.getEdges()[e].getCapacity() / chargeEfficiency;
			}

			for (int i = 0; i < edgesPrintArray.length; i++) {
				for (int j = 0; j < edgesPrintArray.length; j++) {
					strBuilder.append("[" + i + "," + j + "] " + edgesPrintArray[i][j] + " ");
				}
				if (i == g.getNNode() - 1)
					strBuilder.append(";\n");
				else
					strBuilder.append("\n");
			}

			int counter = 0;
			strBuilder.append("param costs :=\n");
			for (int i = 0; i < g.getNodeList().length; i++) {
				if(g.getNodeList()[i].getClass() == ConventionalGenerator.class){
					counter++;
					if (counter == g.getNGenerators())
						strBuilder.append(i + " " + (float)((Generator) g.getNodeList()[i]).getCoef() + ";\n");
					else
						strBuilder.append(i + " " + (float)((Generator) g.getNodeList()[i]).getCoef()+"\n");

					}
			}

			counter = 0;
			strBuilder.append("param mintprod :=\n");
			for (int i = 0; i < g.getNodeList().length; i++) {
				if(g.getNodeList()[i].getClass() == ConventionalGenerator.class){
					counter++;
					if (counter != g.getNGenerators())
						strBuilder.append(i + " " + (float)((Generator) g.getNodeList()[i]).getMinP()+"\n");
					else
						strBuilder.append(i + " " + (float)((Generator) g.getNodeList()[i]).getMinP() + ";\n");
					}
			}

			counter = 0;
			strBuilder.append("param maxtprod :=\n");
			for (int i = 0; i < g.getNodeList().length; i++) {
				if(g.getNodeList()[i].getClass() == ConventionalGenerator.class){
					counter++;
					if (counter != g.getNGenerators())
						strBuilder.append(i + " " + (float)((Generator) g.getNodeList()[i]).getMaxP()+"\n");
					else
						strBuilder.append(i + " " + (float)((Generator) g.getNodeList()[i]).getMaxP() + ";\n");
				}
			}
			counter = 0;
			if (g.getNrGenerators() != 0) {
				strBuilder.append("param rprodmax :=\n");
				for (int i = 0; i < g.getNodeList().length; i++) {
					if(g.getNodeList()[i].getClass() == RenewableGenerator.class){
						counter++;
						if( counter != g.getNrGenerators())
							strBuilder.append(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getMaxP()+"\n");
						else
							strBuilder.append(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getMaxP() + ";\n");
					}
				}

			}
			counter = 0;
			if (g.getNrGenerators() != 0) {
				strBuilder.append("param rprodmin :=\n");
				for (int i = 0; i < g.getNodeList().length; i++) {
						if(g.getNodeList()[i].getClass() == RenewableGenerator.class){
							counter++;
							if(counter != g.getNrGenerators())
								strBuilder.append(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getMinP()+"\n");
							else
								strBuilder.append(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getMinP() + ";\n");
					}
				}
			}
			counter = 0;
			if (g.getNrGenerators() != 0) {
				strBuilder.append("param rcost :=\n");
				for (int i = 0; i < g.getNodeList().length; i++) {
					if(g.getNodeList()[i].getClass() == RenewableGenerator.class){
						counter++;
						if(counter != g.getNrGenerators())
							strBuilder.append(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getCoef()+"\n");
						else
							strBuilder.append(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getCoef() + ";\n");
					}
				}
			}
			counter = 0;
			strBuilder.append("param loads :=\n");
			for (int i = 0; i < g.getNodeList().length; i++) {
				if(g.getNodeList()[i].getClass() == Consumer.class){
					counter++;
					if (counter != g.getNConsumers())
						strBuilder.append(i + " " + (float)((Consumer) g.getNodeList()[i]).getLoad()+"\n");
					else
						strBuilder.append(i + " " + (float)((Consumer) g.getNodeList()[i]).getLoad() + ";\n");
				}
			}
			counter = 0;
			strBuilder.append("param production :=\n");
			for (int i = 0; i < g.getNodeList().length; i++) {
				if(g.getNodeList()[i].getClass() == ConventionalGenerator.class){
					counter++;
					if (counter != g.getNGenerators())
						strBuilder.append(((ConventionalGenerator) g.getNodeList()[i]).getNodeId() + " " + (float)((ConventionalGenerator) g.getNodeList()[i]).getProduction()+"\n");
					else
						strBuilder.append(((ConventionalGenerator) g.getNodeList()[i]).getNodeId() + " " + (float)((ConventionalGenerator) g.getNodeList()[i]).getProduction() + ";\n");
				}
			}
			counter = 0;
			strBuilder.append("param rewproduction :=\n");
			for (int i = 0; i < g.getNodeList().length; i++) {
				if(g.getNodeList()[i].getClass() == RenewableGenerator.class){
					counter++;
					if (counter != g.getNrGenerators())
						strBuilder.append(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getProduction()+"\n");
					else
						strBuilder.append(i + " " + (float)((RenewableGenerator) g.getNodeList()[i]).getProduction() + ";\n");
				}
			}
			printStorageFlow(currentHour, beginChargeTime, endChargeTime, strBuilder, g);

			/*counter = 0;
			if (g.getNstorage() != 0) {
				strBuilder.append("param storagemax :=\n");
				for (int i = 0; i < g.getNodeList().length; i++) {
					if(g.getNodeList()[i].getClass() == Storage.class){
						double cap = 0;
						double val = (((Storage) g.getNodeList()[i]).getCurrentCharge()	- ((Storage) g.getNodeList()[i]).getMinimumCharge()) / delta * dischargeEfficiency;

						for (int j = 0; j < g.getNNode(); j++) {
							if (g.getEdges()[j].getCapacity() != 0.0F) {
								cap = g.getEdges()[j].getCapacity();
								break;
							}
						}
						counter++;
						if (cap * dischargeEfficiency < val) {
							if (counter != g.getNstorage())
								strBuilder.append(i + " " + cap * dischargeEfficiency+"\n");
							else
								strBuilder.append(i + " " + cap * dischargeEfficiency + ";\n");

						} else if (counter != g.getNstorage()) {
							strBuilder.append(i + " " + val+"\n");
						} else
							strBuilder.append(i + " " + val + ";\n");

					}
				}
			}
			counter = 0;
			if (g.getNstorage() != 0) {
				strBuilder.append("param storagemin :=\n");
				for (int i = 0; i < g.getNodeList().length; i++) {
					if(g.getNodeList()[i].getClass() == Storage.class){
						double cap = 0;
						float eps = 0.001F;
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
								strBuilder.append(i + " -" + cap / chargeEfficiency+"\n");
							else
								strBuilder.append(i + " -" + cap / chargeEfficiency + ";\n");

						} else if (counter != g.getNstorage()) {
							//writer.println(i + " -" + val);
							strBuilder.append(i +  val+"\n");
						} else
							strBuilder.append(i + val + ";\n");
							//writer.println(i + " -" + val + ";");
					}
				}
			}*/
			strBuilder.append("end;\n");
			writer.print(strBuilder.toString());
			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	private void printStorageFlow(int currentHour, int beginChargeTime, int endChargeTime, StringBuilder strBuilder, Graph g) {
		// TODO Auto-generated method stub
		int counter = 0;
		if(currentHour >= beginChargeTime || currentHour <= endChargeTime){
			strBuilder.append("param flowfromstorage :=\n");
			for (int i = 0; i < g.getNodeList().length; i++) {
				if(g.getNodeList()[i].getClass() == Storage.class){
					counter++;
					if (counter != g.getNstorage()) {
						strBuilder.append(i + " " + (float)((Storage) g.getNodeList()[i]).getFlow()+"\n");
					} else {
						strBuilder.append(i + " " + (float)((Storage) g.getNodeList()[i]).getFlow() + ";\n");
					}
				}
			}
		}else {
			printChargeLimit(strBuilder, g);
			printDischargeLimit(strBuilder, g);
		}

	}

	private void printChargeLimit(StringBuilder strBuilder, Graph g) {
		int counter = 0;
		strBuilder.append("param flowmaxcharge :=\n");
		for (int i = 0; i < g.getNodeList().length; i++) {
			if(g.getNodeList()[i].getClass() == Storage.class){
				counter++;
				Storage storage = (Storage) g.getNodeList()[i];
				double flowLimit = storage.getFlowLimit();
				double currentCharge = storage.getCurrentSoC();
				double maxSoC = storage.getMaximumCharge();
				if(currentCharge + (flowLimit * storage.getChargeEfficiency()) <= maxSoC) {
					if (counter != g.getNstorage()) {
						strBuilder.append(i + " " + (float)((Storage) g.getNodeList()[i]).getFlowLimit()*-1+"\n");
					} else {
						strBuilder.append(i + " " + (float)((Storage) g.getNodeList()[i]).getFlowLimit()*-1 + ";\n");
					}
				} else {
					flowLimit = maxSoC - currentCharge;
					if (counter != g.getNstorage()) {
						strBuilder.append(i + " " + (float)flowLimit*-1 +"\n");
					}else {
						strBuilder.append(i + " " + (float)flowLimit*-1 + ";\n");
					}
				}
			}
		}
	}

	private void printDischargeLimit(StringBuilder strBuilder, Graph g) {
		int counter = 0;
		strBuilder.append("param flowmaxdischarge :=\n");
		for (int i = 0; i < g.getNodeList().length; i++) {
			if(g.getNodeList()[i].getClass() == Storage.class){
				counter++;
				Storage storage = (Storage) g.getNodeList()[i];
				double flowLimit = storage.getFlowLimit();
				double currentCharge = storage.getCurrentSoC();
				double minSoC = storage.getMinimumCharge();
				if(currentCharge - (flowLimit * storage.getChargeEfficiency()) >= minSoC) {
					if (counter != g.getNstorage()) {
						strBuilder.append(i + " " + (float)((Storage) g.getNodeList()[i]).getFlowLimit()+"\n");
					} else {
						strBuilder.append(i + " " + (float)((Storage) g.getNodeList()[i]).getFlowLimit() + ";\n");
					}
				} else{
					flowLimit = minSoC - currentCharge;
					if (counter != g.getNstorage()) {
						strBuilder.append(i + " " + (float)flowLimit+"\n");
					}else {
						strBuilder.append(i + " " + (float)flowLimit + ";\n");
					}
				}
			}
		}
	}
}
