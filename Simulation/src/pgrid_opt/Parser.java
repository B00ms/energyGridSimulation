package pgrid_opt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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


	/**
	 * Parsers the initial input file and returns its contents as a java object that contains:
	 * The graph representing the grid, production values of solar and wind, the consumption value of the loads
	 * @param path to the input file.
	 * @return
	 */
	public Graph parseData(String path) {
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
				double maximumCharge = scanner.nextDouble();
				double minimumCharge = scanner.nextDouble();
				Storage storage =  new Storage(currentCharge, maximumCharge, minimumCharge, nodeId);
				nodeList.add(storage);
				numberOfStorage++;
				break;
			case "AE":
				int nodeOneId = scanner.nextInt();
				int nodeTwoId = scanner.nextInt();
				double reactance = scanner.nextDouble();
				double capacity = scanner.nextDouble();
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

		/*
		 * So we have to create this comparator because we need to sort the nodes according to a order so they can be used by GLPK
		 */
		Comparator<Node> comparator = new Comparator<Node>() {
			@Override
			public int compare(Node o1, Node o2) {
					if (o1.getClass() == o2.getClass())
						return 0;

					if(o1.getClass() != ConventionalGenerator.class)
						return 1;
					else if (o1.getClass() != Consumer.class)
						if (o2.getClass() != ConventionalGenerator.class)
							return -1;
						else
							return 1;
					else if(o1.getClass() != InnerNode.class)
						if(o2.getClass() != ConventionalGenerator.class || o2.getClass() != Consumer.class)
							return -1;
						else
							return 1;

					else if(o1.getClass() != RewGenerator.class)
						if (o2.getClass() != ConventionalGenerator.class || o2.getClass() != Consumer.class || o2.getClass() != InnerNode.class)
							return -1;
						else
							return 1;
					else if(o1.getClass() != Storage.class)
							return -1;

					return 0;
			}
		};
		Arrays.sort(nodeArray, comparator); //Get it sorted son.
		Collections.sort(edges);
		Edge[] edgesArray = edges.toArray(new Edge[0]);

		totalNumberOfNodes = nodeArray.length;

		Graph graph = new Graph(totalNumberOfNodes, numberOfConventionalGenerators, numberOfRenewableGenerators,
				numberOfConsumers, dailyMaxLoadDemand, numberOfStorage, (float)timeStepDuration, (float)storageChargeEfficiency, (float)storageChargeEfficiency);

		Node[] sortedArray = new Node[totalNumberOfNodes];

		graph.setNodeList(nodeArray);
		graph.setEdges(edgesArray);
		scanner.close();

		return graph;
	}

	public Float[] parseExpectedHourlyLoad(){

		Config generalConf = conf.getConfig("conventionalGenerator");
		Config loadConfig = generalConf.getConfig("load-curves");

		//TODO: later on we have to change this to be dynamic because we want to run for 4 different seasons.
		String path = loadConfig.getString("summer");
		List<Float> expectedHourlyLoad = new ArrayList<>();

		Scanner scanner;
		try {
			scanner = new Scanner(Paths.get("../"+path));
			scanner.useDelimiter(",|\\n");

			while(scanner.hasNext()){
				float expectedLoad = scanner.nextFloat();
				expectedHourlyLoad.add(expectedLoad);
				scanner.nextLine();
			}
			scanner.close();

			return expectedHourlyLoad.toArray(new Float[expectedHourlyLoad.size()]);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
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
		float currentCharge = s.nextFloat();
		float maximumCharge= s.nextFloat();
		float minimumCharge = s.nextFloat();

		s.close();
		return new Storage(currentCharge, maximumCharge, minimumCharge);
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
			g.getEdges()[j].setFlow(Float.parseFloat(s.next()));
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
