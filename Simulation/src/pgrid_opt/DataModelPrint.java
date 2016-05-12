package pgrid_opt;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public class DataModelPrint {

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
	public void printData(Graph g, String filename, String outname) {
		float etac = g.getEtac();
		float etad = g.getEtad();
		float delta = g.getDelta();
		try {
			PrintWriter writer = new PrintWriter(filename, "UTF-8");
			writer.println("param n_tgen := " + (g.getNGenerators() - 1) + ";");
			writer.println("param n_rgen := " + g.getNrgenetarors() + ";");
			writer.println("param n_cons := " + g.getNConsumers() + ";");
			writer.println("param n_inner := " + (g.getNNode() - 1
					- (g.getNGenerators() - 1 + g.getNrgenetarors() + g.getNConsumers()) - g.getNstorage()) + ";");
			writer.println("param n_tot := " + (g.getNNode() - 1) + ";");
			writer.println("param m_factor := 100;");
			writer.println("param pi := 3.1415;");
			writer.println("param n_storage := " + g.getNstorage() + ";");
			writer.println("param totload :=" + g.getLoadmax() + ";");
			writer.println("param c_curt := " + g.getCcurt() + ";");
			writer.println("param outname := " + outname + ";");

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
						/*
							if (vertexOne == i && vertexTwo == j) {
								writer.print("[" + i + "," + j + "] " + (float)g.getEdges()[j].getWeight() + " ");
								written = true;
							} else {
								writer.print("[" + i + "," + j + "] " + 0 + " ");
							}
						*/
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
					edgesPrintArray[vertexOne][vertexTwo] = (float) g.getEdges()[e].getCapacity() / etac;
			}

			for (int i = 0; i < edgesPrintArray.length; i++) {
				for (int j = 0; j < edgesPrintArray.length; j++) {
					writer.print("[" + i + "," + j + "] " + edgesPrintArray[i][j] + " ");
					/*if ((i >= g.getNNode() - g.getNstorage()) && (g.getEdges()[j].getCapacity() > 0.0F)) {
						writer.print("[" + i + "," + j + "] " + (float)g.getEdges()[j].getCapacity() / etac + " ");
					} else
						writer.print("[" + i + "," + j + "] " + (float)g.getEdges()[j].getCapacity() + " ");

					*/
				}
				if (i == g.getNNode() - 1)
					writer.println(";");
				else
					writer.println();
			}
			/*
			writer.println("param costs :=");
			for (int i = 0; i < g.getNGenerators(); i++) {
				if(g.getNodeList()[i].getClass() == ConventionalGenerator.class){
					if (i == g.getNGenerators() - 1)
						writer.println(i + " " + ((Generator) g.getNodeList()[i]).getCoef() + ";");
					else
						writer.println(i + " " + ((Generator) g.getNodeList()[i]).getCoef());

					}
				*/
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
			if (g.getNrgenetarors() != 0) {
				writer.println("param rprodmax :=");
				for (int i = 0; i < g.getNodeList().length; i++) {
					if(g.getNodeList()[i].getClass() == RewGenerator.class){
						counter++;
						if( counter != g.getNrgenetarors())
							writer.println(i + " " + (float)((RewGenerator) g.getNodeList()[i]).getProduction());
						else
							writer.println(i + " " + (float)((RewGenerator) g.getNodeList()[i]).getProduction() + ";");
					}
				}

			}
			counter = 0;
			if (g.getNrgenetarors() != 0) {
				writer.println("param rprodmin :=");
				for (int i = 0; i < g.getNodeList().length; i++) {
						if(g.getNodeList()[i].getClass() == RewGenerator.class){
							counter++;
							if(counter != g.getNrgenetarors())
								writer.println(i + " " + (float)((RewGenerator) g.getNodeList()[i]).getMinP());
							else
								writer.println(i + " " + (float)((RewGenerator) g.getNodeList()[i]).getMinP() + ";");
					}
				}
			}
			counter = 0;
			if (g.getNrgenetarors() != 0) {
				writer.println("param rcost :=");
				for (int i = 0; i < g.getNodeList().length; i++) {
					if(g.getNodeList()[i].getClass() == RewGenerator.class){
						counter++;
						if(counter != g.getNrgenetarors())
							writer.println(i + " " + (float)((RewGenerator) g.getNodeList()[i]).getCoef());
						else
							writer.println(i + " " + (float)((RewGenerator) g.getNodeList()[i]).getCoef() + ";");
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
			if (g.getNstorage() != 0) {
				writer.println("param storagemax :=");
				for (int i = 0; i < g.getNodeList().length; i++) {
					if(g.getNodeList()[i].getClass() == Storage.class){
						double cap = 0;

						if (((Storage) g.getNodeList()[i]).getMinimumCharge() > ((Storage) g.getNodeList()[i]).getCurrentCharge()) {
							((Storage) g.getNodeList()[i]).setCurrentCharge(((Storage) g.getNodeList()[i]).getMinimumCharge());
						}
						double val = (((Storage) g.getNodeList()[i]).getCurrentCharge()
								- ((Storage) g.getNodeList()[i]).getMinimumCharge()) / delta * etad;
						for (int j = 0; j < g.getNNode(); j++) {
							if (g.getEdges()[j].getCapacity() != 0.0F) {
								cap = g.getEdges()[j].getCapacity();
								break;
							}
						}
						counter++;
						if (cap * etad < val) {
							if (counter != g.getNstorage())
								writer.println(i + " " + cap * etad);
							else
								writer.println(i + " " + cap * etad + ";");

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

						if (((Storage) g.getNodeList()[i]).getMaximumCharge() < ((Storage) g.getNodeList()[i])
								.getCurrentCharge()) {
							((Storage) g.getNodeList()[i]).setCurrentCharge(((Storage) g.getNodeList()[i]).getMaximumCharge());
						}
						double val = (((Storage) g.getNodeList()[i]).getMaximumCharge()
								- ((Storage) g.getNodeList()[i]).getCurrentCharge()) / delta / etac;
						for (int j = g.getNGenerators() + g.getNConsumers(); j < g.getNNode()
								- (g.getNstorage() + g.getNrgenetarors()); j++) {
							if (g.getEdges()[j].getCapacity() != 0.0F) {
								cap = g.getEdges()[j].getCapacity();
								break;
							}
						}

						counter++;
						if (cap / etac < val) {
							if (counter != g.getNstorage())
								writer.println(i + " -" + cap / etac);
							else
								writer.println(i + " -" + cap / etac + ";");

						} else if (counter != g.getNstorage()) {
							writer.println(i + " -" + val);
						} else
							writer.println(i + " -" + val + ";");
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
