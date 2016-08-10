package filehandler;

import java.awt.FlowLayout;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

import au.com.bytecode.opencsv.CSVReader;
import graph.Edge;
import graph.Graph;
import graph.InnerNode;
import graph.Node;
import model.*;
import config.ConfigCollection;
import config.ConfigCollection.CONFIGURATION_TYPE;
import model.Generator.GENERATOR_TYPE;

public class Parser {
	private ConfigCollection config = new ConfigCollection();

	public Parser(){
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
			scanner.useLocale(Locale.US);
			scanner.useDelimiter(",|\\n");
		}catch (IOException e){
			e.printStackTrace();
			System.out.println("Error: Wrong path to input file");
			System.exit(0);
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

		int dailyMaxLoadDemand  = config.getConfigIntValue(CONFIGURATION_TYPE.GENERAL, "dailyMaxLoadDemand");
		double timeStepDuration  = config.getConfigDoubleValue(CONFIGURATION_TYPE.GENERAL, "durationOfEachStep");
		double storageChargeEfficiency = config.getConfigDoubleValue(CONFIGURATION_TYPE.STORAGE, "chargeEfficiencyOfStorage");
		double storageDischargeEfficiency = config.getConfigDoubleValue(CONFIGURATION_TYPE.STORAGE, "dischargEfficiencyOfStorage");

		int hydromttf = config.getConfigIntValue(CONFIGURATION_TYPE.HYDROELECTRIC_GENERATOR, "mttf");
		int hydromttr = config.getConfigIntValue(CONFIGURATION_TYPE.HYDROELECTRIC_GENERATOR, "mttr");


		int mttf = config.getConfigIntValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "mttf");
		int mttr = config.getConfigIntValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "mttr");
		double maxProductionIncrease = config.getConfigDoubleValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "maxProductionIncrease");
		double dayAheadLimitMax = config.getConfigDoubleValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "dayAheadLimitMax");
		double dayAheadLimitMin =  config.getConfigDoubleValue(CONFIGURATION_TYPE.CONVENTIONAL_GENERATOR, "dayAheadLimitMin");


		double chargeEfficiency = config.getConfigDoubleValue(CONFIGURATION_TYPE.STORAGE, "chargeEfficiencyOfStorage");
		double dischargeEfficiency = config.getConfigDoubleValue(CONFIGURATION_TYPE.STORAGE, "dischargEfficiencyOfStorage");

		while(scanner.hasNext()){

			String data = scanner.next();
			int nodeId;
			double minProduction;
			double maxProduction;
			double production;
			String type;

			if(data.contains("#"))
				scanner.nextLine();

			GENERATOR_TYPE realType = null;
			switch (data){
			case "CG":
				type = scanner.next();
				switch (type){
				case "O":
					realType = GENERATOR_TYPE.OIL;
					break;
				case "C":
					realType = GENERATOR_TYPE.COAL;
					break;
				case "N":
					realType = GENERATOR_TYPE.NUCLEAR;
					break;
				case "H":
					realType = GENERATOR_TYPE.HYDRO;
					break;
				}
				nodeId = scanner.nextInt();
				minProduction = scanner.nextDouble();
				maxProduction = scanner.nextDouble();
				double coef = scanner.nextDouble();
				production = minProduction;

				ConventionalGenerator convGenerator = null;
				if(realType != GENERATOR_TYPE.HYDRO){
					convGenerator = new ConventionalGenerator(minProduction, maxProduction, coef, realType, production, nodeId, mttf, mttr, maxProductionIncrease, dayAheadLimitMax, dayAheadLimitMin);
				} else {
					convGenerator = new ConventionalGenerator(minProduction, maxProduction, coef, realType, production, nodeId, mttf, mttr, maxProductionIncrease, dayAheadLimitMax, dayAheadLimitMin);
				}

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

				realType = null;
				switch (type){
					case "W":
						realType = GENERATOR_TYPE.WIND;
						break;
					case "S":
						realType = GENERATOR_TYPE.SOLAR;
						break;
				}

				RenewableGenerator renewableGenerator = new RenewableGenerator(maxProduction, 0, cost, realType, nodeId);
				nodeList.add(renewableGenerator);
				numberOfRenewableGenerators++;
				break;
			case "Storage":
				nodeId = scanner.nextInt();
				double currentCharge = scanner.nextDouble();
				double maximumCharge = scanner.nextDouble();
				double minimumCharge = scanner.nextDouble();
				int chMax = scanner.nextInt();
				Storage storage =  new Storage(currentCharge, maximumCharge, minimumCharge, nodeId,  chMax, chargeEfficiency, dischargeEfficiency);
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
		//List<List<Offer>> offerIncrease = this.parseOfferIncreaseProduction();
		//List<List<Offer>> offerDecrease = this.parseOfferDecreaseProduction();

		for(int i=0; i< generatorList.size(); i++){
			ConventionalGenerator cgen = generatorList.get(i);
			// store offers to generators

			cgen = setOffers(cgen);
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

					else if(o1.getClass() != RenewableGenerator.class)
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

	/**
	 * Reads a expected hourly load CSV file and returns its contents in a Float[]
	 * @param path
	 * @return
	 */
	public Float[] parseExpectedHourlyLoad(String path){

		List<Float> expectedHourlyLoad = new ArrayList<>();

		Scanner scanner;
		try {
			if(config.getOS().startsWith("Windows") || config.getOS().startsWith("Linux")) {
				scanner = new Scanner(Paths.get("../"+path));
			}else{
				scanner = new Scanner(Paths.get(path));
			}
			scanner.useLocale(Locale.US);
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
		/*Config productionConf = conf.getConfig("conventionalGenerator").getConfig("production");
		String path = productionConf.getString("summer"); //TODO: for each season
		 */
		String path = config.getConfigStringValue(CONFIGURATION_TYPE.PRODUCTION, "summer");
		List<double[]> expectedHourlyProduction = new ArrayList<>();
		try{
			CSVReader reader;
			if(config.getOS().startsWith("Windows") || config.getOS().startsWith("Linux")) {
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
			reader.close();
			return expectedHourlyProduction;
		} catch (FileNotFoundException e){
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * Parse offers from input csv files
	 % D1 and D2 are MWh; PD1 and PD2 are euro/MWh
	 * @param generator
	 * @return
	 */
	public ConventionalGenerator setOffers(ConventionalGenerator generator){

		GENERATOR_TYPE generatorType = generator.getType();

		double percentFirstIncr = 0;
		double percentFirstDecrease = 0;

		double priceIncreaseStepOne = 0;
		double priceIncreaseStepTwo = 0;

		double priceDecreaseStepOne = 0;
		double priceDecreaseStepTwo = 0;

		int nodeIndex = generator.getNodeId();
		int offerId = 0;

		switch (generatorType){
		case OIL:
			percentFirstIncr = config.getConfigDoubleValue(CONFIGURATION_TYPE.OIL_OFFER, "percentageFirstIncrease");
			percentFirstDecrease = config.getConfigDoubleValue(CONFIGURATION_TYPE.OIL_OFFER, "percentageFirstDecrease");

			priceIncreaseStepOne = config.getConfigDoubleValue(CONFIGURATION_TYPE.OIL_OFFER, "priceIncreaseOne");
			priceIncreaseStepTwo = config.getConfigDoubleValue(CONFIGURATION_TYPE.OIL_OFFER, "priceIncreaseTwo");

			priceDecreaseStepOne = config.getConfigDoubleValue(CONFIGURATION_TYPE.OIL_OFFER, "priceDecreaseOne");
			priceDecreaseStepTwo = config.getConfigDoubleValue(CONFIGURATION_TYPE.OIL_OFFER, "priceDecreaseTwo");
			break;
		case COAL:
			percentFirstIncr = config.getConfigDoubleValue(CONFIGURATION_TYPE.COAL_OFFER, "percentageFirstIncrease");
			percentFirstDecrease = config.getConfigDoubleValue(CONFIGURATION_TYPE.COAL_OFFER, "percentageFirstDecrease");

			priceIncreaseStepOne = config.getConfigDoubleValue(CONFIGURATION_TYPE.COAL_OFFER, "priceIncreaseOne");
			priceIncreaseStepTwo = config.getConfigDoubleValue(CONFIGURATION_TYPE.COAL_OFFER, "priceIncreaseTwo");

			priceDecreaseStepOne = config.getConfigDoubleValue(CONFIGURATION_TYPE.COAL_OFFER, "priceDecreaseOne");
			priceDecreaseStepTwo = config.getConfigDoubleValue(CONFIGURATION_TYPE.COAL_OFFER, "priceDecreaseTwo");
			break;
		case NUCLEAR:
			percentFirstIncr = config.getConfigDoubleValue(CONFIGURATION_TYPE.NUCLEAR_OFFER, "percentageFirstIncrease");
			percentFirstDecrease = config.getConfigDoubleValue(CONFIGURATION_TYPE.NUCLEAR_OFFER, "percentageFirstDecrease");

			priceIncreaseStepOne = config.getConfigDoubleValue(CONFIGURATION_TYPE.NUCLEAR_OFFER, "priceIncreaseOne");
			priceIncreaseStepTwo = config.getConfigDoubleValue(CONFIGURATION_TYPE.NUCLEAR_OFFER, "priceIncreaseTwo");

			priceDecreaseStepOne = config.getConfigDoubleValue(CONFIGURATION_TYPE.NUCLEAR_OFFER, "priceDecreaseOne");
			priceDecreaseStepTwo = config.getConfigDoubleValue(CONFIGURATION_TYPE.NUCLEAR_OFFER, "priceDecreaseTwo");
			break;
		case HYDRO:
			percentFirstIncr = config.getConfigDoubleValue(CONFIGURATION_TYPE.HYRDO_OFFER, "percentageFirstIncrease");
			percentFirstDecrease = config.getConfigDoubleValue(CONFIGURATION_TYPE.HYRDO_OFFER, "percentageFirstDecrease");

			priceIncreaseStepOne = config.getConfigDoubleValue(CONFIGURATION_TYPE.HYRDO_OFFER, "priceIncreaseOne");
			priceIncreaseStepTwo = config.getConfigDoubleValue(CONFIGURATION_TYPE.HYRDO_OFFER, "priceIncreaseTwo");

			priceDecreaseStepOne = config.getConfigDoubleValue(CONFIGURATION_TYPE.HYRDO_OFFER, "priceDecreaseOne");
			priceDecreaseStepTwo = config.getConfigDoubleValue(CONFIGURATION_TYPE.HYRDO_OFFER, "priceDecreaseTwo");
			break;
		default:
			//Ignore wind and solar because they dont do offers.
			break;
		}

		/*
		 * Calculate production values for offers to increase production
		 */
		double changeValue = (generator.getMaxP() * 0.075) * percentFirstIncr;
		Offer increaseOneOffer = new Offer(changeValue, priceIncreaseStepOne, nodeIndex, 0);

		changeValue= (generator.getMaxP() * 0.075) * (1-percentFirstIncr);
		Offer increaseTwoOffer = new Offer(changeValue, priceIncreaseStepTwo, nodeIndex, 1);

		/*
		 * Calculate production values for offers to decrease production
		 */
		changeValue = (generator.getMinP() * 0.075) * percentFirstDecrease;
		Offer decreaseOneOffer = new Offer(changeValue, priceDecreaseStepOne, nodeIndex,0);

		changeValue= (generator.getMinP() * 0.075) * (1-percentFirstDecrease);
		Offer decreaseTwoOffer = new Offer(changeValue, priceDecreaseStepTwo, nodeIndex, 1);

		Offer[] increaseOffers = new Offer[2];
		increaseOffers[0] = increaseOneOffer;
		increaseOffers[1] = increaseTwoOffer;


		Offer[] decreaseOffer = new Offer[2];
		decreaseOffer[0] = decreaseOneOffer;
		decreaseOffer[1] = decreaseTwoOffer;

		if(!(generator.getProduction() > 0)){
			decreaseOffer[0].setAvailable(false);
			decreaseOffer[1].setAvailable(false);
		}
		generator.setOfferIncreaseProduction(increaseOffers);
		generator.setOfferDecreaseProduction(decreaseOffer);

		return generator;
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
			scanner.useLocale(Locale.US);
		} catch (IOException e1) {
			System.out.println("ERROR: wrong sol data input file path");
			return null;
		}

		scanner.useDelimiter(System.getProperty("line.separator"));
		while (goon) {
			Scanner linescanner = new Scanner(scanner.next());
			scanner.useLocale(Locale.US);
			int i = linescanner.nextInt();
			int j = linescanner.nextInt();
			double flow = Float.parseFloat(linescanner.next());
			g.getEdges()[j].setFlow(flow);
			for(int y = 0; y < g.getNodeList().length; y++){
				if(g.getNodeList()[y].getClass() == Storage.class){
					Storage storage = (Storage) g.getNodeList()[y];
					if(flow > 0)
						storage.discharge(Math.abs(flow));
					 else if (storage.getCurrentSoC() < storage.getMaximumCharge())
						storage.charge(Math.abs(flow));

					g.getNodeList()[y] = storage;
				}
			}

			goon = scanner.hasNext();
			linescanner.close();
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
}
