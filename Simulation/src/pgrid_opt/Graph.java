package pgrid_opt;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;

public class Graph implements Cloneable {
	private int nnode; //Total Number of nodes in the graph
	private int ngenerators; //Number of conventional generators
	private int nconsumers; //Number of loads
	private int nrgenetarors; //Number of renewable generators
	private int loadmax; //Daily max load demand
	private int nstorage; //Number of storage systems in the grid
	private int efficency; //TODO: determine what this variable represents(capacity of real edges?), it's hardcoded to 75
	private float ccurt; //Renewable cut costs
	private float etac; //Duration of each time step
	private float etad; //Charge and discharge efficiency of storages
	private float delta; //Number of storage systems.
	private Node[] nodelist;
	private Edge[][] network;
	private Edge[] edges;

	public Graph(int nnode, int ngenerators, int nrgenerators, int nconsumers, int loadmax, int nstorage, float delta,
			float etac, float etad) {
		setNodeList(new Node[nnode]);
		this.network = new Edge[nnode][nnode];
		setLoadmax(loadmax);
		setNNode(nnode);
		setNGenerators(ngenerators);
		setNrgenetarors(nrgenerators);
		setNConsumers(nconsumers);
		setNstorage(nstorage);
		setCcurt(200.0F); //TODO: movie to configuration file.
		setEfficency(75);
		setDelta(delta);
		setEtac(etac);
		setEtad(etad);
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

	public int getNrgenetarors() {
		return this.nrgenetarors;
	}

	public void setNrgenetarors(int nrgenetarors) {
		this.nrgenetarors = nrgenetarors;
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
		Graph g = new Graph(this.nnode, this.ngenerators, this.nrgenetarors, this.nconsumers, this.loadmax,
				this.nstorage, this.delta, this.etac, this.etad);

		Node[] tempNodeList = new Node[g.getNodeList().length];
		int counter = 0;
		for (int i = 0; i < g.getNodeList().length; i++) {

			if(getNodeList()[i].getClass() == (ConventionalGenerator.class))
			{
				ConventionalGenerator conventionalGenerator = new ConventionalGenerator(((ConventionalGenerator) nodelist[i]).getMinP(),
																					((ConventionalGenerator) nodelist[i]).getMaxP(),
																					((ConventionalGenerator) nodelist[i]).getCoef(),
																					((ConventionalGenerator) nodelist[i]).getType(),
																					((ConventionalGenerator) nodelist[i]).getProduction(),
																					((ConventionalGenerator) nodelist[i]).getNodeId());

				conventionalGenerator.setMTTF(((ConventionalGenerator) nodelist[i]).getMTTF());
				conventionalGenerator.setMTTR(((ConventionalGenerator) nodelist[i]).getMTTR());
				tempNodeList[i] = conventionalGenerator;
			} else if(getNodeList()[i].getClass() == RewGenerator.class){

				RewGenerator renewableGenerator = new RewGenerator(((RewGenerator) nodelist[i]).getMaxP(),
																	((RewGenerator)nodelist[i]).getMinP(),
																	((RewGenerator)nodelist[i]).getCoef(),
																	((RewGenerator)nodelist[i]).getType(),
																	((RewGenerator)nodelist[i]).getNodeId());
				tempNodeList[i] = renewableGenerator;

			} else if (getNodeList()[i].getClass() == Storage.class){

				Storage storage = new Storage(((Storage)getNodeList()[i]).getCurrentCharge(),
											((Storage)getNodeList()[i]).getMaximumCharge(),
											((Storage)getNodeList()[i]).getMinimumCharge(),
											((Storage)getNodeList()[i]).getNodeId());

				tempNodeList[i] = storage;

			} else if (getNodeList()[i].getClass() == InnerNode.class){
				int nodeId = ((InnerNode)nodelist[i]).getNodeId();
				InnerNode innerNode = new InnerNode(nodeId);
				tempNodeList[i] = innerNode;

			}else if (getNodeList()[i].getClass() == Consumer.class){
				Consumer consumer = new Consumer(((Consumer)getNodeList()[i]).getLoad(), ((Consumer)getNodeList()[i]).getNodeId());
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
	 * @return number of loads in graph
	 */
	public int getNstorage() {
		return this.nstorage;
	}

	/**
	 * Sets the number of loads in the graph
	 * @param nstorage
	 */
	public void setNstorage(int nstorage) {
		this.nstorage = nstorage;
	}

	public int getEfficency() {
		return this.efficency;
	}

	public void setEfficency(int efficency) {
		this.efficency = efficency;
	}

	public float getCcurt() {
		return this.ccurt;
	}

	public void setCcurt(float ccurt) {
		this.ccurt = ccurt;
	}

	/**
	 *
	 * @return Number of storage systems
	 */
	public float getDelta() {
		return this.delta;
	}

	/**
	 * Set the Number of storage systems
	 * @param delta
	 */
	public void setDelta(float delta) {
		this.delta = delta;
	}

	/**
	 *
	 * @return Charge and discharge efficiency of storages
	 */
	public float getEtad() {
		return this.etad;
	}

	/**
	 * Set the Charge and discharge efficiency of storages
	 * @param etad
	 */

	public void setEtad(float etad) {
		this.etad = etad;
	}

	/**
	 *
	 * @return the duration of each time step.
	 */
	public float getEtac() {
		return this.etac;
	}

	/**
	 * Sets the duration of a time step.
	 * @param etac
	 */
	public void setEtac(float etac) {
		this.etac = etac;
	}

	public void printGraph(int graphNumber){
		String outputPath = "../graphstate/graph"+graphNumber+".dgs";
		try {
			File file = new File(outputPath);

			if(!file.exists())
				file.createNewFile();

			FileWriter fileWriter = new FileWriter(file, false);
			BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

			bufferedWriter.write("DGS004"); bufferedWriter.newLine();
			bufferedWriter.write("null 0 0 "); bufferedWriter.newLine();
			bufferedWriter.write("st 0"); bufferedWriter.newLine();
			bufferedWriter.write("cg  \"ui.quality\":true"); bufferedWriter.newLine();
			bufferedWriter.write("cg  \"ui.antialias\":true"); bufferedWriter.newLine();
			bufferedWriter.write("cg  \"ui.stylesheet\":\"url(mysheet.css)\""); bufferedWriter.newLine();

			for (int i = 0; i < nodelist.length; i++){
				Node node = nodelist[i];

				if(node.getClass() == ConventionalGenerator.class){
					ConventionalGenerator convGenerator = (ConventionalGenerator) nodelist[i];
					bufferedWriter.write("an " + "\"" + convGenerator.getNodeId() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + convGenerator.getNodeId() + "\" \"ui.label\":" + "\"" + convGenerator.getNodeId() +"\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + convGenerator.getNodeId() + "\" \"ui.class\":" + "\"" + convGenerator.getClass().getSimpleName() + "\""); bufferedWriter.newLine();
				} else if(node.getClass() == RewGenerator.class){
					RewGenerator rewGenerator = (RewGenerator) nodelist[i];
					bufferedWriter.write("an " + "\"" + rewGenerator.getNodeId() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + rewGenerator.getNodeId() + "\" \"ui.label\":" + "\"" + rewGenerator.getNodeId() +"\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + rewGenerator.getNodeId() + "\" \"ui.class\":" + "\"" + rewGenerator.getClass().getSimpleName() + "\""); bufferedWriter.newLine();
				} else if (node.getClass() == InnerNode.class){
					InnerNode innderNode = (InnerNode) nodelist[i];
					bufferedWriter.write("an " + "\"" + innderNode.getNodeId() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + innderNode.getNodeId() + "\" \"ui.label\":" + "\"" + innderNode.getNodeId() +"\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + innderNode.getNodeId() + "\" \"ui.class\":" + "\"" + innderNode.getClass().getSimpleName() + "\""); bufferedWriter.newLine();
				} else if (node.getClass() == Consumer.class){
					Consumer consumerNode = (Consumer) nodelist[i];
					bufferedWriter.write("an " + "\"" + consumerNode.getNodeId() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + consumerNode.getNodeId() + "\" \"ui.label\":" + "\"" + consumerNode.getNodeId() +"\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + consumerNode.getNodeId() + "\" \"ui.class\":" + "\"" + consumerNode.getClass().getSimpleName() + "\""); bufferedWriter.newLine();
				} else if (node.getClass() == Storage.class){
					Storage storageNode = (Storage ) nodelist[i];
					bufferedWriter.write("an " + "\"" + storageNode.getNodeId() + "\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + storageNode.getNodeId() + "\" \"ui.label\":" + "\"" + storageNode.getNodeId() +"\""); bufferedWriter.newLine();
					bufferedWriter.write("cn " + "\"" + storageNode.getNodeId() + "\" \"ui.class\":" + "\"" + storageNode.getClass().getSimpleName() + "\""); bufferedWriter.newLine();
				}
			}

			for(int i = 0; i < edges.length; i ++){
				bufferedWriter.write("ae \"edge" + i + "\" \"" + edges[i].getEndVertexes()[0] + "\" \"" + edges[i].getEndVertexes()[1] + "\""); bufferedWriter.newLine();
				bufferedWriter.write("ce \"" + edges[i].getEndVertexes()[0] + " \"flow\":" +"\"" + edges[i].getFlow() + "\""); bufferedWriter.newLine();
				bufferedWriter.write("ce \"" + edges[i].getEndVertexes()[0] + " \"flow\":" +"\"" + edges[i].getCapacity() + "\""); bufferedWriter.newLine();
			}

			bufferedWriter.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("rawr rawr rawr");
	}
}
