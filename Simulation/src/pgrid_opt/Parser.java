package pgrid_opt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class Parser {
	private Config conf = ConfigFactory.parseFile(new File("../config/application.conf"));
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


	public Graph parseData(String path, int overload) {
		Scanner scanner = null;

		try {
			scanner = new Scanner(Paths.get(path));
			scanner.useDelimiter(",|\\n");
		}catch (IOException e){
			System.err.println("Error: Wrong path to input file");
			return null;
		}

		List<ConventionalGenerator> generatorList = new ArrayList<ConventionalGenerator>();
		List<Node> nodeList = new ArrayList<Node>();
		List<Edge> edges = new ArrayList<Edge>();

		int totalNumberOfNodes = 0;
		int numberOfConventionalGenerators = 0;
		int numberOfRenewableGenerators = 0;
		int numberOfConsumers = 0;
		int numberOfStorage = 0;

		int dailyMaxLoadDemand = 0;
		int numberOfTimeSteps = 0;
		double timeStepDuration = 0;
		double storageChargeEfficiency = 0;

		Config generalConf = conf.getConfig("general");
		dailyMaxLoadDemand  = generalConf.getInt("dailyMaxLoadDemand");
		timeStepDuration  = generalConf.getDouble("durationOfEachStep");
		storageChargeEfficiency = generalConf.getDouble("chargeEfficiencyOfStorage");

		while(scanner.hasNext()){

			String data = scanner.next();
			int nodeId;
			double minProduction;
			double maxProduction;
			double production;
			String type;

			if(data.contains("#"))
				scanner.nextLine();

			switch (data){
			case "CG":
				type = scanner.next();
				nodeId = scanner.nextInt();
				//String test =  scanner.next();
				minProduction = scanner.nextDouble();
				maxProduction = scanner.nextDouble();
				double coef = scanner.nextDouble();
				production = minProduction;

				ConventionalGenerator convGenerator = new ConventionalGenerator(minProduction, maxProduction, coef, type, production, nodeId);
				generatorList.add(convGenerator); //Using a different array for convetional generators because we want to sort it.
				numberOfConventionalGenerators++;
				break;
			case "C":
				nodeId = scanner.nextInt();
				double load = scanner.nextDouble();

				Consumer consumer = new Consumer(load, nodeId);
				nodeList.add(consumer);
				numberOfConsumers++;

				break;
			case "IN":
				nodeId = scanner.nextInt();
				Node node = new InnerNode(nodeId);
				nodeList.add(node);

				break;
			case "RG":
				type = scanner.next();
				nodeId = scanner.nextInt();
				minProduction = scanner.nextDouble();
				maxProduction = scanner.nextDouble();
				double cost = scanner.nextDouble();

				RewGenerator renewableGenerator = new RewGenerator(maxProduction, minProduction, cost, type, nodeId);
				nodeList.add(renewableGenerator);
				numberOfRenewableGenerators++;
				break;
			case "Storage":
				nodeId = scanner.nextInt();
				double currentCharge = scanner.nextDouble();
				double minimumCharge = scanner.nextDouble();
				double maximumCharge = scanner.nextDouble();
				Storage storage =  new Storage(currentCharge, maximumCharge, minimumCharge, nodeId);
				nodeList.add(storage);
				numberOfStorage++;
				break;
			case "AE":
				int nodeOneId = scanner.nextInt();
				int nodeTwoId = scanner.nextInt();
				System.out.println(nodeOneId + " " + nodeTwoId);
				double capacity = scanner.nextDouble();
				double reactance = scanner.nextDouble();
				double flow = 0;

				Edge edge = new Edge();
				edge.setCapacity(capacity);
				edge.setFlow(flow);
				edge.setWeight(reactance);
				edge.setEndVertexes(nodeOneId, nodeTwoId);
				edges.add(edge);
				break;
			default:
				//Ignores comments
			}
		}
		Collections.sort(generatorList);
		nodeList.addAll(generatorList); //Merge conventional generator nodes with the rest.
		Node[] nodeArray = nodeList.toArray(new Node[0]);
		Edge[] edgesArray = edges.toArray(new Edge[0]);

		totalNumberOfNodes = nodeArray.length-1;

		Graph graph = new Graph(totalNumberOfNodes, numberOfConventionalGenerators, numberOfRenewableGenerators,
				numberOfConsumers, dailyMaxLoadDemand, numberOfStorage, (float)timeStepDuration, (float)storageChargeEfficiency, (float)storageChargeEfficiency);
		graph.setNodeList(nodeArray);
		graph.setEdges(edgesArray);
		scanner.close();

		return graph;
	}

	/**
	 * Parsers the initial input file and returns its contents as a java object that contains:
	 * The graph representing the grid, production values of solar and wind, the consumption value of the loads
	 * @param path to the input file.
	 * @return
	 */
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
		Graph g = parseNetSize(scanner.next());
		float[] dsolar = new float[this.ngraph]; //Production values for solar for each time step
		float[] dwind = new float[this.ngraph]; //Production values for wind for each time step
		float[] dloads = new float[this.ngraph]; //Load demand for each time step
		float wcost = 0.0F;
		float scost = 0.0F;
		float ccurt = 200.0F;

		Node[] n = new Node[g.getNNode()];
		String token = scanner.next();
		if ((token.compareTo(this.gen) == 1) || (token.compareTo(this.gen) == 0)) {
			System.out.println("parsing gen");
			List<ConventionalGenerator> generatorList = new ArrayList<>();
			for (int i = 0; i < g.getNGenerators(); i++) {
				//n[i] = parseGenerator(scanner.next());
				generatorList.add(parseGenerator(scanner.next()));
			}
			Collections.sort(generatorList);
			generatorList.toArray(n);
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
		Edge[][] e = new Edge[g.getNNode()][g.getNNode()];
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

	/**
	 * Parse renewable generators from input file
	 * @param next
	 * @param wcost
	 * @param scost
	 * @return
	 */
	private Node parseRGenerators(String next, float wcost, float scost) {
		Scanner scanner = new Scanner(next);
		float max = scanner.nextFloat();
		String type = scanner.next();
		scanner.close();

		// doesn't seem to do anything same as normal renewable generator
		if (("S".compareTo(type) == 0) && ("S".compareTo(type) == 1)) {
			return new RewGenerator(0.0F, max, scost, type);
		}
		return new RewGenerator(0.0F, max, wcost, type);
	}

	/**
	 * Parse an edge
	 * @param e
	 * @param scan
	 */
	private void parseEdge(Edge[][] e, Scanner scan) {
		for (int i = 0; i < e.length; i++) {
			for (int j = 0; j < e.length; j++) {
				e[i][j] = new Edge();
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

	/**
	 * Parse consumer node
	 * @param next input String
	 * @return Consumer node
	 */
	private Node parseConsumer(String next) {
		Scanner scanner = new Scanner(next);
		float load = scanner.nextFloat();
		scanner.close();
		return new Consumer(load);
	}

	private float parsefloat(String next) {
		Scanner scanner = new Scanner(next);
		float num = scanner.nextFloat();
		scanner.close();
		return num;
	}

	/**
	 * Parse initial values for Generator node
	 * @param next input String
	 * @return
	 */
	private ConventionalGenerator parseGenerator(String next) {
		Scanner scanner = new Scanner(next);
		float min = scanner.nextFloat();
		float max = scanner.nextFloat();
		float coef = scanner.nextFloat();

		// returns type from input file
		String type = scanner.next();
		scanner.close();

		// set type of traditional plant based on coef.
		if(coef == 10){
			type = "H"; // Hydro
		}else if(coef == 35) {
			type = "N";//nuclear
		}else if(coef == 40 && max == 350){
			type = "C";// coal
		}else if(coef == 45 && min == 155 || max == 155){
			type = "C";// coal
		}else if(coef == 55 && min == 76){
			type = "C";// coal
		}else if(coef == 55 && min > 100) {
			type = "O";// oil, not sure minp 236 maxp 591 not in thesis
		}else if(coef > 55 && coef <=80){
			type = "O";
		}else{
			type = "unknown";
		}

		return new ConventionalGenerator(min, max, coef, type, 0);
	}

	/**
	 * Parse initial parameters for construction of energy grid
	 * @param next input line
	 * @return Graph of energy grid
	 */
	private Graph parseNetSize(String next) {
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
		return new Graph(nNode, nGenerators, nRGenerators, nConsumers, loadmax, nStorage, delta, etac, etad);
	}

	/**
	 * Parse and create new Storage node
	 * @param next
	 * @return Storage node
	 */
	private Storage parseStorage(String next) {
		Scanner s = new Scanner(next);
		float aval = s.nextFloat();
		float cap = s.nextFloat();
		float min = s.nextFloat();

		s.close();
		return new Storage(aval, cap, min);
	}

	/**
	 * Parses update.txt file for current timestep with current flow of storage node
	 * @param path update.txt file
	 * @param g graph of timestep i
	 * @return updated graph at timestep i
	 */
	public Graph parseUpdates(String path, Graph g) {
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

	/**
	 * Iterate through program arguments to split the arguments into separate pieces
	 * in order to set the model path, input path and output path.
	 * @param s
	 * @return array with the paths to the model, input file and, output file, the amount of simulations to run.
	 */
	public String[] parseArg(String[] s) {
		boolean m = false;
		boolean i = false;
		boolean o = false;
		boolean simNumber = false;
		String[] out = new String[4];
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
			if((s[j].compareTo("-s") == 0) && (j + 1 < s.length)){
				out[3] = s[(j + 1)];
				simNumber = true;

			}
		}
		if (m && i && o && simNumber) {
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
		if(!simNumber){
			System.out.println("Number of simulations not specified.");
		}
		System.exit(0);

		return out;
	}

	/**
	 * Reads
	 * @param fileLocation
	 * @return
	 */
	public Double[] parseCSV(String fileLocation){

		try {
			Scanner scanner = new Scanner(Paths.get(fileLocation));
			scanner.useDelimiter(",|\\s");
			List<Double> loadCurve = new ArrayList<>();

			while(scanner.hasNext()){
				String curveValue = scanner.next();
				if(!curveValue.isEmpty())
					loadCurve.add(Double.parseDouble(curveValue));
			}
			scanner.close();

			return loadCurve.toArray(new Double[loadCurve.size()]);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public Double[][] parseCSV2D(String fileLocation){

		try {
			Scanner scanner = new Scanner(Paths.get(fileLocation));
			scanner.useDelimiter(",|\\s");
			List<ArrayList<Double>> data = new ArrayList<ArrayList<Double>>();
			List<Double> tempList = new ArrayList<>();

			int i = 0;
			int j = 0;
			while(scanner.hasNext()){

				String curveValue = scanner.next();
				if(!curveValue.isEmpty()){
					tempList.add(Double.parseDouble(curveValue));
					j++;
				}
				if(j >= 24){
					data.add(i, (ArrayList<Double>) tempList);
					tempList = new ArrayList<Double>();
					j = 0;
					i++;
				}
			}

			scanner.close();

			return data.toArray(new Double[data.size()][24]);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

}
