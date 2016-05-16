package pgrid_opt;

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
		setDelta(delta); //TODO: remove this because its not used.
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
}
