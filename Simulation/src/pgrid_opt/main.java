package pgrid_opt;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import pgrid_opt.dataModelPrint;
import pgrid_opt.graph;
import pgrid_opt.instanceRandomizer;
import pgrid_opt.parser;

public class main {
	public static void main(String[] args) {
		long starttime = System.nanoTime();
		float[] wind = null;
		float[] solar = null;
		float[] loads = null;
		float wcost = 0.0f;
		float scost = 0.0f;
		graph[] gdays = null;
		parser p = new parser();
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
		graph g = (graph) o[0];
		solar = (float[]) o[1];
		wind = (float[]) o[2];
		loads = (float[]) o[3];
		instanceRandomizer r = new instanceRandomizer();
		gdays = new graph[loads.length + 1];
		gdays = r.creategraphs(g, gdays, solar, wind, loads);
		dataModelPrint mp = new dataModelPrint();
		Process proc = null;
		int i = 0;
		while (i < gdays.length - 1) {
			mp.printData(gdays[i], String.valueOf(dirpath) + outpath1 + i + outpath2, Integer.toString(i)); //This creates a new input file.
			try {
				StringBuffer output = new StringBuffer();
				String command = String.valueOf(solpath1) + outpath1 + i + outpath2 + solpath2 + model;
				System.out.println(command);
				proc = Runtime.getRuntime().exec(command, null, new File(dirpath));
				proc.waitFor();
				BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
				String line = "";
				while ((line = reader.readLine()) != null) {
					output.append(String.valueOf(line) + "\n");
				}
				System.out.println(output);
				if (g.getNstorage() > 0) {
					gdays[i] = p.parseUpdates(String.valueOf(dirpath) + "update.txt", gdays[i]);
					gdays[i + 1] = r.updateStorages(gdays[i], gdays[i + 1]);
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