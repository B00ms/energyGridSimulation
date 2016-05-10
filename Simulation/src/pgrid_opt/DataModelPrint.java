package pgrid_opt;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

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
					writer.print(((Storage) gdays[j].getNodeList()[i]).getAvaliability() + " ");
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
			for (int i = 0; i < g.getNNode(); i++) {
				for (int j = 0; j < g.getNNode(); j++) {
					writer.print("[" + i + "," + j + "] " + g.getNetwork()[i][j].getWeight() + " ");
				}
				if (i == g.getNNode() - 1)
					writer.println(";");
				else
					writer.println();
			}
			writer.println("param capacity :=");
			for (int i = 0; i < g.getNNode(); i++) {
				for (int j = 0; j < g.getNNode(); j++) {
					if ((i >= g.getNNode() - g.getNstorage()) && (g.getNetwork()[i][j].getCapacity() > 0.0F)) {
						writer.print("[" + i + "," + j + "] " + g.getNetwork()[i][j].getCapacity() / etac + " ");
					} else
						writer.print("[" + i + "," + j + "] " + g.getNetwork()[i][j].getCapacity() + " ");
				}
				if (i == g.getNNode() - 1)
					writer.println(";");
				else
					writer.println();
			}
			writer.println("param costs :=");
			for (int i = 0; i < g.getNGenerators(); i++) {
				if (i == g.getNGenerators() - 1)
					writer.println(i + " " + ((Generator) g.getNodeList()[i]).getCoef() + ";");
				else
					writer.println(i + " " + ((Generator) g.getNodeList()[i]).getCoef());
			}
			writer.println("param mintprod :=");
			for (int i = 0; i < g.getNGenerators(); i++) {
				if (i == g.getNGenerators() - 1)
					writer.println(i + " " + ((Generator) g.getNodeList()[i]).getMinP() + ";");
				else
					writer.println(i + " " + ((Generator) g.getNodeList()[i]).getMinP());
			}
			writer.println("param maxtprod :=");
			for (int i = 0; i < g.getNGenerators(); i++) {
				if (i == g.getNGenerators() - 1)
					writer.println(i + " " + ((Generator) g.getNodeList()[i]).getMaxP() + ";");
				else
					writer.println(i + " " + ((Generator) g.getNodeList()[i]).getMaxP());
			}
			if (g.getNrgenetarors() != 0) {
				writer.println("param rprodmax :=");
				for (int i = g.getNNode() - g.getNrgenetarors() - g.getNstorage(); i < g.getNNode()
						- g.getNstorage(); i++) {
					if (i == g.getNNode() - (g.getNstorage() + 1))
						writer.println(i + " " + ((RewGenerator) g.getNodeList()[i]).getProduction() + ";");
					else
						writer.println(i + " " + ((RewGenerator) g.getNodeList()[i]).getProduction());
				}
			}
			if (g.getNrgenetarors() != 0) {
				writer.println("param rprodmin :=");
				for (int i = g.getNNode() - g.getNrgenetarors() - g.getNstorage(); i < g.getNNode()
						- g.getNstorage(); i++) {
					if (i == g.getNNode() - (g.getNstorage() + 1))
						writer.println(i + " " + ((RewGenerator) g.getNodeList()[i]).getMinP() + ";");
					else
						writer.println(i + " " + ((RewGenerator) g.getNodeList()[i]).getMinP());
				}
			}
			if (g.getNrgenetarors() != 0) {
				writer.println("param rcost :=");
				for (int i = g.getNNode() - g.getNrgenetarors() - g.getNstorage(); i < g.getNNode()
						- g.getNstorage(); i++) {
					if (i == g.getNNode() - (g.getNstorage() + 1))
						writer.println(i + " " + ((RewGenerator) g.getNodeList()[i]).getCoef() + ";");
					else
						writer.println(i + " " + ((RewGenerator) g.getNodeList()[i]).getCoef());
				}
			}
			writer.println("param loads :=");
			for (int i = g.getNGenerators(); i < g.getNGenerators() + g.getNConsumers(); i++) {
				if (i == g.getNGenerators() + g.getNConsumers() - 1)
					writer.println(i + " " + ((Consumer) g.getNodeList()[i]).getLoad() + ";");
				else
					writer.println(i + " " + ((Consumer) g.getNodeList()[i]).getLoad());
			}
			if (g.getNstorage() != 0) {
				writer.println("param storagemax :=");
				for (int i = g.getNNode() - g.getNstorage(); i < g.getNNode(); i++) {
					double cap = 0;

					if (((Storage) g.getNodeList()[i]).getMincap() > ((Storage) g.getNodeList()[i]).getAvaliability()) {
						((Storage) g.getNodeList()[i]).setAvaliability(((Storage) g.getNodeList()[i]).getMincap());
					}
					double val = (((Storage) g.getNodeList()[i]).getAvaliability()
							- ((Storage) g.getNodeList()[i]).getMincap()) / delta * etad;
					for (int j = 0; j < g.getNNode(); j++) {
						if (g.getNetwork()[i][j].getCapacity() != 0.0F) {
							cap = g.getNetwork()[i][j].getCapacity();
							break;
						}
					}

					if (cap * etad < val) {
						if (i == g.getNNode() - 1) {
							writer.println(i + " " + cap * etad + ";");
						} else {
							writer.println(i + " " + cap * etad);
						}
					} else if (i == g.getNNode() - 1) {
						writer.println(i + " " + val + ";");
					} else
						writer.println(i + " " + val);
				}
			}
			if (g.getNstorage() != 0) {
				writer.println("param storagemin :=");
				for (int i = g.getNNode() - g.getNstorage(); i < g.getNNode(); i++) {
					double cap = 0;
					float eps = 0.001F;

					if (((Storage) g.getNodeList()[i]).getCapacity() < ((Storage) g.getNodeList()[i])
							.getAvaliability()) {
						((Storage) g.getNodeList()[i]).setAvaliability(((Storage) g.getNodeList()[i]).getCapacity());
					}
					double val = (((Storage) g.getNodeList()[i]).getCapacity()
							- ((Storage) g.getNodeList()[i]).getAvaliability()) / delta / etac;
					for (int j = g.getNGenerators() + g.getNConsumers(); j < g.getNNode()
							- (g.getNstorage() + g.getNrgenetarors()); j++) {
						if (g.getNetwork()[i][j].getCapacity() != 0.0F) {
							cap = g.getNetwork()[i][j].getCapacity();
							break;
						}
					}

					if (cap / etac < val) {
						if (i == g.getNNode() - 1) {
							writer.println(i + " -" + cap / etac + ";");
						} else {
							writer.println(i + " -" + cap / etac);
						}
					} else if (i == g.getNNode() - 1) {
						writer.println(i + " -" + val + ";");
					} else
						writer.println(i + " -" + val);
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
