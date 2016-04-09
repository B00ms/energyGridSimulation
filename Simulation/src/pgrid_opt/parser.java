package pgrid_opt;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Scanner;

public class parser {
	private String gen = "generators";
	private String cons = "consumers";
	private String net = "network";
	private String rgen = "rgenerators";
	private String tload = "totloads";
	private String pload = "perloads";
	private String solar = "solar";
	private String wind = "wind";
	private String stor = "storage";
	private int ngraph;

	public Object[] parseData(String path) {
		Scanner scanner;
		try {
			scanner = new Scanner(Paths.get(path, new String[0]));
			scanner.useDelimiter(System.getProperty("line.separator"));
		} catch (IOException e1) {
			// Scanner scanner;
			System.out.println("ERROR: wrong raw data input file path");
			return null;
		}

		graph g = parseNetSize(scanner.next());
		float[] dsolar = new float[this.ngraph];
		float[] dwind = new float[this.ngraph];
		float[] dloads = new float[this.ngraph];
		float wcost = 0.0F;
		float scost = 0.0F;
		float ccurt = 200.0F;

		node[] n = new node[g.getNNode()];
		String token = scanner.next();
		if ((token.compareTo(this.gen) == 1) || (token.compareTo(this.gen) == 0)) {
			System.out.println("parsing gen");
			for (int i = 0; i < g.getNGenerators(); i++) {
				n[i] = parseGenerator(scanner.next());
			}
		}
		token = scanner.next();
		if ((token.compareTo(this.cons) == 1) || (token.compareTo(this.cons) == 0)) {
			token = scanner.next();
			if ((token.compareTo(this.tload) == 1) || (token.compareTo(this.tload) == 0)) {
				System.out.println("parsing tload");
				for (int i = 0; i < this.ngraph; i++) {
					dloads[i] = Float.parseFloat(scanner.next());
				}
			}
			token = scanner.next();
			if ((token.compareTo(this.pload) == 1) || (token.compareTo(this.pload) == 0)) {
				System.out.println("parsing pload");
				for (int i = 0; i < g.getNConsumers(); i++) {
					n[(g.getNGenerators() + i)] = parseConsumer(scanner.next());
				}
			}
		}
		token = scanner.next();
		if ((token.compareTo(this.rgen) == 1) || (token.compareTo(this.rgen) == 0)) {
			System.out.println("parsing rgen");
			ccurt = parsefloat(scanner.next());
			wcost = parsefloat(scanner.next());
			scost = parsefloat(scanner.next());
			for (int i = g.getNNode() - g.getNrgenetarors() - g.getNstorage(); i < g.getNNode()
					- g.getNstorage(); i++) {
				n[i] = parseRGenerators(scanner.next(), wcost, scost);
			}
		}
		g.setCcurt(ccurt);

		g.setNodeList(n);
		token = scanner.next();
		if (((token.compareTo(this.wind) == 1) || (token.compareTo(this.wind) == 0)) && (g.getNrgenetarors() > 0)) {
			for (int i = 0; i < this.ngraph; i++) {
				Scanner s = new Scanner(scanner.next());
				dwind[i] = s.nextFloat();
				s.close();
			}
		}
		token = scanner.next();
		if (((token.compareTo(this.solar) == 1) || (token.compareTo(this.solar) == 0)) && (g.getNrgenetarors() > 0)) {
			for (int i = 0; i < this.ngraph; i++) {
				Scanner s = new Scanner(scanner.next());
				dsolar[i] = s.nextFloat();
				s.close();
			}
		}
		token = scanner.next();
		if ((token.compareTo(this.stor) == 1) || (token.compareTo(this.stor) == 0)) {
			System.out.println("parsing stor");
			for (int i = g.getNNode() - g.getNstorage(); i < g.getNNode(); i++) {
				n[i] = parseStorage(scanner.next());
			}
		}
		edge[][] e = new edge[g.getNNode()][g.getNNode()];
		token = scanner.next();
		if ((token.compareTo(this.net) == 1) || (token.compareTo(this.net) == 0)) {
			System.out.println("parsing net");
			parseEdge(e, scanner);
		}
		g.setNetwork(e);

		scanner.close();
		Object[] o = new Object[6];
		o[0] = g;
		o[1] = dsolar;
		o[2] = dwind;
		o[3] = dloads;
		o[4] = Float.valueOf(wcost);
		o[5] = Float.valueOf(scost);
		return o;
	}

	private node parseRGenerators(String next, float wcost, float scost) {
		Scanner scanner = new Scanner(next);
		float max = scanner.nextFloat();
		String type = scanner.next();
		scanner.close();
		if (("S".compareTo(type) == 0) && ("S".compareTo(type) == 1)) {
			return new rewGenerator(0.0F, max, scost, type);
		}
		return new rewGenerator(0.0F, max, wcost, type);
	}

	private void parseEdge(edge[][] e, Scanner scan) {
		for (int i = 0; i < e.length; i++) {
			for (int j = 0; j < e.length; j++) {
				e[i][j] = new edge();
				e[i][j].setCapacity(0.0F);
				e[i][j].setWeight(0.0F);
				e[i][j].setFlow(0.0F);
			}
		}
		while (scan.hasNext()) {
			Scanner scanner = new Scanner(scan.next());
			int i = scanner.nextInt();
			int j = scanner.nextInt();
			e[i][j].setWeight(scanner.nextFloat());
			e[i][j].setCapacity(scanner.nextFloat());
			scanner.close();
		}
	}

	private node parseConsumer(String next) {
		Scanner scanner = new Scanner(next);
		float load = scanner.nextFloat();
		scanner.close();
		return new consumer(load);
	}

	private float parsefloat(String next) {
		Scanner scanner = new Scanner(next);
		float num = scanner.nextFloat();
		scanner.close();
		return num;
	}

	private generator parseGenerator(String next) {
		Scanner scanner = new Scanner(next);
		float min = scanner.nextFloat();
		float max = scanner.nextFloat();
		float coef = scanner.nextFloat();
		String type = scanner.next();
		scanner.close();
		return new generator(min, max, coef, type);
	}

	private graph parseNetSize(String next) {
		Scanner s = new Scanner(next);
		s.useDelimiter("\\s");
		this.ngraph = Integer.parseInt(s.next());
		int loadmax = Integer.parseInt(s.next());
		int nNode = Integer.parseInt(s.next());
		int nGenerators = Integer.parseInt(s.next());
		int nRGenerators = Integer.parseInt(s.next());
		int nConsumers = Integer.parseInt(s.next());
		int nStorage = Integer.parseInt(s.next());
		float delta = (float) s.nextDouble();
		float etac = (float) s.nextDouble();
		float etad = (float) s.nextDouble();
		s.close();
		return new graph(nNode, nGenerators, nRGenerators, nConsumers, loadmax, nStorage, delta, etac, etad);
	}

	private storage parseStorage(String next) {
		Scanner s = new Scanner(next);
		float aval = s.nextFloat();
		float cap = s.nextFloat();
		float min = s.nextFloat();

		s.close();
		return new storage(aval, cap, min);
	}

	public graph parseUpdates(String path, graph g) {
		boolean goon = true;
		Scanner scanner;
		try {
			scanner = new Scanner(Paths.get(path, new String[0]));
		} catch (IOException e1) {
			System.out.println("ERROR: wrong sol data input file path");
			return null;
		}

		scanner.useDelimiter(System.getProperty("line.separator"));
		while (goon) {
			Scanner s = new Scanner(scanner.next());
			int i = s.nextInt();
			int j = s.nextInt();
			g.getNetwork()[i][j].setFlow(Float.parseFloat(s.next()));
			goon = scanner.hasNext();
			s.close();
		}
		scanner.close();
		return g;
	}

	public String[] parseArg(String[] s) {
		boolean m = false;
		boolean i = false;
		boolean o = false;
		String[] out = new String[3];
		for (int j = 0; j < s.length; j++) {
			if ((s[j].compareTo("-m") == 0) && (j + 1 < s.length)) {
				out[0] = s[(j + 1)];
				m = true;
			}
			if ((s[j].compareTo("-i") == 0) && (j + 1 < s.length)) {
				out[1] = s[(j + 1)];
				i = true;
			}
			if ((s[j].compareTo("-o") == 0) && (j + 1 < s.length)) {
				out[2] = s[(j + 1)];
				o = true;
			}
		}
		if ((m) && (i) && (o)) {
			return out;
		}
		if (!m) {
			System.out.println("model path missing");
		}
		if (!i) {
			System.out.println("input path missing");
		}
		if (!o) {
			System.out.println("output path missing");
		}
		System.exit(0);

		return out;
	}
}
