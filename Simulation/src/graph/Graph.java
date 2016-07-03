package graph;

import java.io.*;
import java.nio.file.Paths;
import java.util.Scanner;

import model.Consumer;
import model.ConventionalGenerator;
import model.RenewableGenerator;
import model.Storage;
import config.ConfigCollection;
import config.ConfigCollection.CONFIGURATION_TYPE;


public class Graph implements Cloneable {
	private int nnode; //Total Number of nodes in the graph
	private int ngenerators; //Number of conventional generators
	private int nconsumers; //Number of loads
	private int nrgenerators; //Number of renewable generators
	private int loadmax; //Daily max load demand
	private int nstorage; //Number of storage systems in the grid
	private int efficiency; //TODO: determine what this variable represents(capacity of real edges?), it's hardcoded to 75
	private float cost_curt; //Renewable cut costs
	private float cost_sl; // cost shedded load
	private float chargeEfficiency; //Duration of each time step
	private float dischargeEfficiency; //Charge and discharge efficiency of storages
	private float delta; //Length of the time step.
	private Node[] nodelist;
	private Edge[][] network;
	private Edge[] edges;
	private ConfigCollection config = new ConfigCollection();

	public Graph(int nnode, int ngenerators, int nrgenerators, int nconsumers, int loadmax, int nstorage, float delta,
			float chargeEfficiency, float dischargeEfficiency) {


		setNodeList(new Node[nnode]);
		this.network = new Edge[nnode][nnode];
		setLoadmax(loadmax);
		setNNode(nnode);
		setNGenerators(ngenerators);
		setNrGenerators(nrgenerators);
		setNConsumers(nconsumers);
		setNstorage(nstorage);

		int costLoadShedding = config.getConfigIntValue(CONFIGURATION_TYPE.GENERAL, "costLoadShedding");
		int costCurtailment = config.getConfigIntValue(CONFIGURATION_TYPE.GENERAL, "costCurtailment");
		int efficiency = config.getConfigIntValue(CONFIGURATION_TYPE.GENERAL, "efficiency");
		setCostSheddedLoad(costLoadShedding);
		setCostCurtailment(costCurtailment);
		setEfficiency(efficiency);
		setDelta(delta);
		setChargeEfficiency(chargeEfficiency);
		setDischargeEfficiency(dischargeEfficiency);
	}

	public Edge[][] getNetwork() {
		return this.network;
	}

	public void setNetwork(Edge[][] network) {
		this.network = network;
	}

	public void setEdges(Edge[] edges) {
		this.edges = edges;
	}

	public Edge[] getEdges(){
		return edges;
	}

	public Node[] getNodeList() {
		return this.nodelist;
	}

	public void setNodeList(Node[] nodelist) {
		this.nodelist = nodelist;
	}

	public int getNGenerators() {
		return this.ngenerators;
	}

	public void setNGenerators(int ngenerators) {
		this.ngenerators = ngenerators;
	}

	public int getNConsumers() {
		return this.nconsumers;
	}

	public void setNConsumers(int nconsumers) {
		this.nconsumers = nconsumers;
	}

	/**
	 * @return Total number of nodes in the graph
	 */
	public int getNNode() {
		return this.nnode;
	}

	/**
	 * Sets the total amount of nodes in the graph
	 * @param nnode
	 */
	public void setNNode(int nnode) {
		this.nnode = nnode;
	}

	public int getNrGenerators() {
		return this.nrgenerators;
	}

	public void setNrGenerators(int nrgenerators) {
		this.nrgenerators = nrgenerators;
	}

	public int getLoadmax() {
		return this.loadmax;
	}

	public void setLoadmax(int loadmax) {
		this.loadmax = loadmax;
	}

	/**
	 * @return A copy of the graph.
	 */
	public Graph clone() {
		Graph g = new Graph(this.nnode, this.ngenerators, this.nrgenerators, this.nconsumers, this.loadmax,
				this.nstorage, this.delta, this.chargeEfficiency, this.dischargeEfficiency);

		Node[] tempNodeList = new Node[g.getNodeList().length];

		for (int i = 0; i < g.getNodeList().length; i++) {

			if(getNodeList()[i].getClass() == (ConventionalGenerator.class))
			{
				ConventionalGenerator conventionalGenerator = new ConventionalGenerator(((ConventionalGenerator) nodelist[i]).getMinP(),
																					((ConventionalGenerator) nodelist[i]).getMaxP(),
																					((ConventionalGenerator) nodelist[i]).getCoef(),
																					((ConventionalGenerator) nodelist[i]).getType(),
																					((ConventionalGenerator) nodelist[i]).getProduction(),
																					(nodelist[i]).getNodeId());

				conventionalGenerator.setMTTF(((ConventionalGenerator) nodelist[i]).getMTTF());
				conventionalGenerator.setMTTR(((ConventionalGenerator) nodelist[i]).getMTTR());
				conventionalGenerator.setOfferIncreaseProduction(((ConventionalGenerator) nodelist[i]).getIncreaseProductionOffers());
				conventionalGenerator.setOfferDecreaseProduction(((ConventionalGenerator) nodelist[i]).getDecreaseProductionOffers());
				tempNodeList[i] = conventionalGenerator;
			} else if(getNodeList()[i].getClass() == RenewableGenerator.class){

				RenewableGenerator renewableGenerator = new RenewableGenerator(((RenewableGenerator) nodelist[i]).getMaxP(),
																	((RenewableGenerator)nodelist[i]).getMinP(),
																	((RenewableGenerator)nodelist[i]).getCoef(),
																	((RenewableGenerator)nodelist[i]).getType(),
																	(nodelist[i]).getNodeId());
				tempNodeList[i] = renewableGenerator;

			} else if (getNodeList()[i].getClass() == Storage.class){

				Storage storage = new Storage(((Storage)getNodeList()[i]).getCurrentCharge(),
											((Storage)getNodeList()[i]).getMaximumCharge(),
											((Storage)getNodeList()[i]).getMinimumCharge(),
											(getNodeList()[i]).getNodeId(),
											((Storage)getNodeList()[i]).getChMax());

				tempNodeList[i] = storage;

			} else if (getNodeList()[i].getClass() == InnerNode.class){
				int nodeId = (nodelist[i]).getNodeId();
				InnerNode innerNode = new InnerNode(nodeId);
				tempNodeList[i] = innerNode;

			}else if (getNodeList()[i].getClass() == Consumer.class){
				Consumer consumer = new Consumer(((Consumer)getNodeList()[i]).getLoad(), (getNodeList()[i]).getNodeId());
				tempNodeList[i] = consumer;
			}
		}

		Edge[] tempEdges = new Edge[this.getEdges().length];
		for (int i = 0; i < getEdges().length; i++){
			Edge edge = new Edge();
			edge.setCapacity(getEdges()[i].getCapacity());
			edge.setWeight(getEdges()[i].getWeight());
			edge.setFlow(getEdges()[i].getFlow());
			edge.setEndVertexes(getEdges()[i].getEndVertexes()[0], getEdges()[i].getEndVertexes()[1]);
			tempEdges[i] = edge;
		}
		g.setEdges(tempEdges );
		g.setNodeList(tempNodeList);
		return g;
	}

	/**
	 *
	 * @return number of storage nodes in graph
	 */
	public int getNstorage() {
		return this.nstorage;
	}

	/**
	 * Sets the number of storage nodes in the graph
	 * @param nstorage
	 */
	public void setNstorage(int nstorage) {
		this.nstorage = nstorage;
	}

	public int getEfficiency() {
		return this.efficiency;
	}

	public void setEfficiency(int efficiency) {
		this.efficiency = efficiency;
	}

	public float getCostCurtailment() {
		return this.cost_curt;
	}

	public void setCostCurtailment(float cost_curt) {
		this.cost_curt = cost_curt;
	}

	public float getCostSheddedLoad() {
		return this.cost_sl;
	}

	public void setCostSheddedLoad(float cost_sl){this.cost_sl = cost_sl;}

	/**
	 * @return duriation of time
	 */
	public float getDelta() {
		return this.delta;
	}

	/**
	 * Set the duration of time (e.g. 1 hour, 0.25 = 1/4th hour)
	 * @param delta current duration of time
	 */
	public void setDelta(float delta) {
		this.delta = delta;
	}

	/**
	 * @return discharge efficiency of storage nodes
	 */
	public float getDischargeEfficiency() {
		return this.dischargeEfficiency;
	}

	/**
	 * Set the discharge efficiency of storage nodes
	 * @param dischargeEfficiency discharge efficiency
	 */
	public void setDischargeEfficiency(float dischargeEfficiency) {
		this.dischargeEfficiency = dischargeEfficiency;
	}

	/**
	 * Charge efficiency of storage nodes
	 * @return charge efficiency of storage nodes.
	 */
	public float getChargeEfficiency() {
		return this.chargeEfficiency;
	}

	/**
	 * charge efficiency of storage nodes
	 * @param chargeEfficiency charge efficiency of storage nodes
	 */
	public void setChargeEfficiency(float chargeEfficiency) {
		this.chargeEfficiency = chargeEfficiency;
	}

	/**
	 * Outputs the graph in .DGS format.
	 * @param graphNumber
	 */
	public void printGraph(int graphNumber, int numOfSim, double hourlyEENS){
		String outputPath = "../graphstate/simulation"+numOfSim+"/";
		String fileName = "graphstate"+graphNumber+".dgs";
		try {
			File file = new File(outputPath);
			file.mkdir();

			file = new File(outputPath+fileName);

			if(!file.exists())
				file.createNewFile();

			FileWriter fileWriter = new FileWriter(file, false);
			BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

			bufferedWriter.write("DGS004"); bufferedWriter.newLine();
			bufferedWriter.write("null 0 0 "); bufferedWriter.newLine();
			bufferedWriter.write("st 0"); bufferedWriter.newLine();
			bufferedWriter.write("cg  \"ui.quality\":true"); bufferedWriter.newLine();
			bufferedWriter.write("cg  \"ui.antialias\":true"); bufferedWriter.newLine();
			bufferedWriter.write("cg  \"ui.stylesheet\":\"url(mySheet.css)\""); bufferedWriter.newLine();
			bufferedWriter.write("cg  \"EENS\":\"" + hourlyEENS +"\""); bufferedWriter.newLine();

			for (int i = 0; i < nodelist.length; i++){
				Node node = nodelist[i];

				if(node.getClass() == ConventionalGenerator.class){
					ConventionalGenerator convGenerator = (ConventionalGenerator) nodelist[i];
					bufferedWriter.write("an " + "\"" + convGenerator.getNodeId() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + convGenerator.getNodeId() + "\" \"ui.label\":" + "\"" + convGenerator.getNodeId() +"\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + convGenerator.getNodeId() + "\" \"ui.class\":" + "\"" + convGenerator.getClass().getSimpleName() + "\""); bufferedWriter.newLine();

					bufferedWriter.write("cn " + "\"" + convGenerator.getNodeId() + "\" \"failure\":" + convGenerator.getGeneratorFailure()); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + convGenerator.getNodeId() + "\" \"subType\":" + "\"" + convGenerator.getType() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + convGenerator.getNodeId() + "\" \"production\":" + "\"" +convGenerator.getProduction() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + convGenerator.getNodeId() + "\" \"maxProduction\":" + "\"" + convGenerator.getMaxP() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + convGenerator.getNodeId() + "\" \"minProduction\":" + "\"" + convGenerator.getMinP() + "\""); bufferedWriter.newLine();

				}else if (node.getClass() == RenewableGenerator.class){
					RenewableGenerator renewableGenerator = (RenewableGenerator) nodelist[i];
					bufferedWriter.write("an " + "\"" + renewableGenerator.getNodeId() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + renewableGenerator.getNodeId() + "\" \"ui.label\":" + "\"" + renewableGenerator.getNodeId() +"\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + renewableGenerator.getNodeId() + "\" \"ui.class\":" + "\"" + renewableGenerator.getClass().getSimpleName() + "\""); bufferedWriter.newLine();

					bufferedWriter.write("cn " + "\"" + renewableGenerator.getNodeId() + "\" \"subType\":" + "\"" + renewableGenerator.getType() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + renewableGenerator.getNodeId() + "\" \"production\":" + "\"" + renewableGenerator.getProduction()+ "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + renewableGenerator.getNodeId() + "\" \"maxProduction\":" + "\"" + renewableGenerator.getMaxP()+ "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + renewableGenerator.getNodeId() + "\" \"minProduction\":" + "\"" + renewableGenerator.getMinP()+ "\""); bufferedWriter.newLine();

				}else if (node.getClass() == InnerNode.class){
					InnerNode innderNode = (InnerNode) nodelist[i];
					bufferedWriter.write("an " + "\"" + innderNode.getNodeId() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + innderNode.getNodeId() + "\" \"ui.label\":" + "\"" + innderNode.getNodeId() +"\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + innderNode.getNodeId() + "\" \"ui.class\":" + "\"" + innderNode.getClass().getSimpleName() + "\""); bufferedWriter.newLine();
				} else if (node.getClass() == Consumer.class){
					Consumer consumerNode = (Consumer) nodelist[i];
					bufferedWriter.write("an " + "\"" + consumerNode.getNodeId() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + consumerNode.getNodeId() + "\" \"ui.label\":" + "\"" + consumerNode.getNodeId() +"\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + consumerNode.getNodeId() + "\" \"ui.class\":" + "\"" + consumerNode.getClass().getSimpleName() + "\""); bufferedWriter.newLine();

					bufferedWriter.write("cn " + "\"" + consumerNode.getNodeId() + "\" \"load\":" + "\"" + consumerNode.getLoad() + "\""); bufferedWriter.newLine();
					for(int j = 0; j < edges.length; j++){
						if( edges[j].getEndVertexes()[0] == consumerNode.getNodeId() || edges[j].getEndVertexes()[1] == consumerNode.getNodeId()){
							double flow = edges[j].getFlow();
							bufferedWriter.write("cn " + "\"" + consumerNode.getNodeId() + "\" \"flow\":" + "\"" + flow + "\""); bufferedWriter.newLine();
							break;
						}
					}
				} else if (node.getClass() == Storage.class){
					Storage storageNode = (Storage ) nodelist[i];
					bufferedWriter.write("an " + "\"" + storageNode.getNodeId() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + storageNode.getNodeId() + "\" \"ui.label\":" + "\"" + storageNode.getNodeId() +"\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + storageNode.getNodeId() + "\" \"ui.class\":" + "\"" + storageNode.getClass().getSimpleName() + "\""); bufferedWriter.newLine();

					bufferedWriter.write("cn " + "\"" + storageNode.getNodeId() + "\" \"currentCharge\":" + "\"" + storageNode.getCurrentCharge() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + storageNode.getNodeId() + "\" \"maxCharge\":" + "\"" + storageNode.getMaximumCharge() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + storageNode.getNodeId() + "\" \"minCharge\":" + "\"" + storageNode.getMinimumCharge() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + storageNode.getNodeId() + "\" \"chargeEfficiency\":" + "\"" + storageNode.getChargeEfficiency() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + storageNode.getNodeId() + "\" \"dischargeEfficiency\":" + "\"" + storageNode.getDischargeEfficiency() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + storageNode.getNodeId() + "\" \"status\":" + "\"" + storageNode.getStatus() + "\""); bufferedWriter.newLine();
				}
			}

			for(int i = 0; i < edges.length; i ++){
				bufferedWriter.write("ae \"edge" + i + "\" \"" + edges[i].getEndVertexes()[0] + "\" > \"" + edges[i].getEndVertexes()[1] + "\"" + " \"flow\":" +"\"" + edges[i].getFlow() + "\"" + " \"capacity\":" +"\"" + edges[i].getCapacity() + "\""
			+ " \"reactance\":\"" + edges[i].getWeight() + "\"");
				bufferedWriter.newLine();
			}

			bufferedWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	/**
	 * For a single graph: copy the flow of the output file to the graph in the simulation
	 * @return updated graph with adjusted flow after minimizing objective function in simulation
	 */
	public Graph setFlowFromOutputFile(Graph graph, int timestep){
		ConfigCollection config = new ConfigCollection();
		String path = config.getConfigStringValue(CONFIGURATION_TYPE.GENERAL, "output-folder");

		path += "sol"+timestep+".txt";

		Scanner scanner;
		try {
			scanner = new Scanner(Paths.get(path));
			scanner.useDelimiter(",");

			Scanner lineScanner;

			while (scanner.hasNext()){
				String line = scanner.nextLine();
				lineScanner = new Scanner(line);
				lineScanner.useDelimiter(",|\n");
				if (line.isEmpty()){
					lineScanner.close();
					break;
				}

				int nodeOneId = lineScanner.nextInt();
				int nodeTwoId = lineScanner.nextInt();
				double flow = Double.parseDouble(lineScanner.next());

				graph = setFlowState(graph, nodeOneId, flow);
				graph = setFlowState(graph, nodeTwoId, flow);

				for (int i = 0; i < graph.getEdges().length; i++){
					if (graph.getEdges()[i].getEndVertexes()[0] == nodeOneId && graph.getEdges()[i].getEndVertexes()[1] == nodeTwoId
						|| graph.getEdges()[i].getEndVertexes()[0] == nodeTwoId && graph.getEdges()[i].getEndVertexes()[1] == nodeOneId){
						graph.getEdges()[i].setFlow(flow);
						break;
					}
				}
				lineScanner.close();
			}
			scanner.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(0);
		}
		return graph;
	}

	/**
	 * Because renewable and storage production is decided during the flow simulation we have to set their states.
	 * @return Graph where production of renewable and storage set to values computed during flow simulation
	 */
	private Graph setFlowState(Graph graph, int nodeId, double flow){

		for(int i = 0; i < graph.getNodeList().length; i++){
			if(graph.getNodeList()[i].getClass() == RenewableGenerator.class && ((Node)graph.getNodeList()[i]).getNodeId() == nodeId){
				((RenewableGenerator)graph.getNodeList()[i]).setProduction(flow);
			} else if(graph.getNodeList()[i].getClass() == Storage.class && ((Node)graph.getNodeList()[i]).getNodeId() == nodeId){
				((Storage)graph.getNodeList()[i]).setFlow(flow);
				// update the status of the storage node
				if(flow<0){
					((Storage)graph.getNodeList()[i]).setStatus(Storage.StorageStatus.CHARGING);
				}else if(flow>0){
					((Storage)graph.getNodeList()[i]).setStatus(Storage.StorageStatus.DISCHARGING);
				}else{
					((Storage)graph.getNodeList()[i]).setStatus(Storage.StorageStatus.NEUTRAL);
				}
			}
			else if(graph.getNodeList()[i].getClass() == Consumer.class && ((Node)graph.getNodeList()[i]).getNodeId() == nodeId){
				((Consumer)graph.getNodeList()[i]).setFlow(flow);
			}
		}
		return graph;
	}
}
