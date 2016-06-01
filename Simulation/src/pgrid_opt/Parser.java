package pgrid_opt;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

import au.com.bytecode.opencsv.CSVReader;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class Parser {
	private static String OS = System.getProperty("os.name");
	private Config conf;
	private Config productionConf;

	public Parser(){
		if(OS.startsWith("Windows") || OS.startsWith("Linux")) {
			conf = ConfigFactory.parseFile(new File("../config/application.conf"));
		}else{
			conf = ConfigFactory.parseFile(new File("config/application.conf"));
		}
		productionConf = conf.getConfig("conventionalGenerator").getConfig("offers");
	}

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
			System.out.println("Error: Wrong path to input file");
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

		Config generalConf = conf.getConfig("general");
		int dailyMaxLoadDemand  = generalConf.getInt("dailyMaxLoadDemand");
		double timeStepDuration  = generalConf.getDouble("durationOfEachStep");
		double storageChargeEfficiency  = conf.getConfig("Storage").getDouble("chargeEfficiencyOfStorage");
		double storageDischargeEfficiency = conf.getConfig("Storage").getDouble("dischargEfficiencyOfStorage");
		//storageChargeEfficiency = generalConf.getDouble("chargeEfficiencyOfStorage");

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
				generatorList.add(convGenerator); //Using a different array for conventional generators because we want to sort it.
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
				maxProduction = scanner.nextDouble();
				double cost = scanner.nextDouble();

				RewGenerator renewableGenerator = new RewGenerator(maxProduction, 0, cost, type, nodeId);
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

		// add offers to generatorList for conventional generators
		List<List<Offer>> offerIncrease = this.parseOfferIncreaseProduction();
		List<List<Offer>> offerDecrease = this.parseOfferDecreaseProduction();

		for(int i=0; i< generatorList.size(); i++){
			ConventionalGenerator cgen = generatorList.get(i);
			// store offers to generators
			cgen.setOfferIncreaseProduction(offerIncrease.get(i));
			cgen.setOfferDecreaseProduction(offerDecrease.get(i));
			generatorList.set(i, cgen);
		}

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
				numberOfConsumers, dailyMaxLoadDemand, numberOfStorage, (float)timeStepDuration, (float)storageChargeEfficiency, (float)storageDischargeEfficiency);

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
			if(OS.startsWith("Windows") || OS.startsWith("Linux")) {
				scanner = new Scanner(Paths.get("../"+path));
			}else{
				scanner = new Scanner(Paths.get(path));
			}

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
	 * Parse expected production from config file
	 * @return List<Double[]> expected production for each node
	 */
	public List<double[]> parseExpectedProduction(){
		Config productionConf = conf.getConfig("conventionalGenerator").getConfig("production");
		String path = productionConf.getString("summer"); // TODO for each season

		List<double[]> expectedHourlyProduction = new ArrayList<>();
		try{
			CSVReader reader;
			if(OS.startsWith("Windows") || OS.startsWith("Linux")) {
				reader = new CSVReader(new FileReader("../"+path));
			}else{
				reader = new CSVReader(new FileReader(path));
			}

			String [] nextLine;
			while ((nextLine = reader.readNext()) != null) {
				double[] productionValues = new double[nextLine.length];
				for (int i = 0; i < nextLine.length; i++) {
					productionValues[i] = Double.parseDouble(nextLine[i]);
				}
				expectedHourlyProduction.add(productionValues);
			}

			return expectedHourlyProduction;
		} catch (FileNotFoundException e){
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * Parse and return the offers for increasing production for each conventional generator
	 * @return
	 */
	public List<List<Offer>> parseOfferIncreaseProduction(){
		String path = productionConf.getString("increaseProduction");
		List<List<Offer>> offerUp = parseOffer(path);
		return offerUp;
	}

	/**
	 * Parse and return the offers for decreasing production for each conventional generator
	 * @return
	 */
	public List<List<Offer>> parseOfferDecreaseProduction(){
		String path = productionConf.getString("decreaseProduction");
		List<List<Offer>> offerDown = parseOffer(path);
		return offerDown;
	}

	/**
	 * Parse offers from input csv files
	 % D1 and D2 are MWh; PD1 and PD2 are euro/MWh
	 * @param path
	 * @return
	 */
	public List<List<Offer>> parseOffer(String path){

		List<List<Offer>> generatorOffers = new ArrayList<>();
		try{
			CSVReader reader;
			if(OS.startsWith("Windows") || OS.startsWith("Linux")) {
				reader = new CSVReader(new FileReader("../"+path));
			}else{
				reader = new CSVReader(new FileReader(path));
			}

			String [] nextLine;
			int i = 0;
			while ((nextLine = reader.readNext()) != null) {
				List<Offer> offerList = new ArrayList<Offer>();

				// U1 MWh, PU1 euro/MWh
				offerList.add(new Offer(Integer.parseInt(nextLine[0]), Integer.parseInt(nextLine[1]), i, 0));
				// U2 MWh, PU2 euro/MWh
				offerList.add(new Offer(Integer.parseInt(nextLine[2]), Integer.parseInt(nextLine[3]), i, 1));
				generatorOffers.add(offerList);
				i++;
			}

			return generatorOffers;
		} catch (FileNotFoundException e){
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
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
	public Double[] parseLoadCSV(String fileLocation){

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

//	public Graph parseProductionCSV(String fileLocation, Graph graph){
//
//		try {
//			Scanner scanner = new Scanner(Paths.get(fileLocation));
////			scanner.useDelimiter(",|\\s");
//			scanner.useDelimiter("\r\n");
////			List<Double>< loadCurve = new ArrayList<>();
//			int i = 0;
//			while(scanner.hasNext()){
//
//				String lineValue = scanner.next();
//				if(!lineValue.isEmpty()){
//					scanner.useDelimiter(",");
////					loadCurve.add(Double.parseDouble(curveValue));
//				}
//				i++;
//			}
//			scanner.close();
//
////			return loadCurve.toArray(new Double[loadCurve.size()]);
//			return graph;
//		} catch (IOException e) {
//			e.printStackTrace();
//			return null;
//		}
//	}

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
