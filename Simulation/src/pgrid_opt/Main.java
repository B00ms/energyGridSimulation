package pgrid_opt;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.math3.distribution.WeibullDistribution;

import pgrid_opt.DataModelPrint;
import pgrid_opt.Graph;
import pgrid_opt.InstanceRandomizer;
import pgrid_opt.Parser;

public class Main {
	public static void main(String[] args) {

		//For weibull distribution: alpha = 1.6, beta = 8
		//For normal distribution: mean = 0, sigma = 0.05
		MontoCarloHelper monteCarloHelper = new MontoCarloHelper(1.6, 8, 0, 0.05);

		long starttime = System.nanoTime();
		float[] wind = null;
		float[] solar = null;
		float[] loads = null;
		float wcost = 0.0f;  //wind cost
		float scost = 0.0f; //solar cost
		Graph[] gdays = null;
		Parser p = new Parser();
		String[] s = p.parseArg(args);
		String path = s[1]; //path to the input
		String outpath1 = "input";
		String outpath2 = ".mod";
		String solpath1 = "glpsol -d ";
		String solpath2 = " -m ";
		String solpath3 = "sol";
		String model = s[0]; //path to the model
		String dirpath = s[2]; //path to the output
		Object[] o = p.parseData(path);
		Graph g = (Graph) o[0];
		solar = (float[]) o[1];
		wind = (float[]) o[2];
		loads = (float[]) o[3];
		InstanceRandomizer r = new InstanceRandomizer();
		gdays = new Graph[loads.length + 1];
		gdays = r.creategraphs(g, gdays, solar, wind, loads);
		DataModelPrint mp = new DataModelPrint();
		Process proc = null;
		int i = 0;
		while (i < gdays.length - 1) {
			mp.printData(gdays[i], String.valueOf(dirpath) + outpath1 + i + outpath2, Integer.toString(i)); //This creates a new input file.
			try {
				StringBuffer output = new StringBuffer();
				String command = String.valueOf(solpath1) + outpath1 + i + outpath2 + solpath2 + model;
				System.out.println(command);
				//TODO: Do a Monte Carlo draw for conventional and renewable generators and set their state.
				//TODO: Do a Monto Carlo draw for the load.
				proc = Runtime.getRuntime().exec(command, null, new File(dirpath));
				proc.waitFor();
				BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream())); //Using the new input file, we apply the model to solve the cost function given the new state of the grid.
				String line = "";
				while ((line = reader.readLine()) != null) {
					output.append(String.valueOf(line) + "\n");
				}
				System.out.println(output);
				if (g.getNstorage() > 0) {
					gdays[i] = p.parseUpdates(String.valueOf(dirpath) + "update.txt", gdays[i]); //Keeps track of the new state for storages.
					gdays[i + 1] = r.updateStorages(gdays[i], gdays[i + 1]); //Apply the new state of the storage for the next time step.
				}
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
			++i;
		}
		if (g.getNstorage() > 0) {
			mp.printStorageData(gdays, String.valueOf(dirpath) + "storage.txt");
		}
		long endtime = System.nanoTime();
		long duration = endtime - starttime;
		System.out.println("Time used:" + duration / 1000000 + " millisecond");
	}
}