package pgrid_opt;

public class Graph implements Cloneable {
	private int nnode; //Total Number of nodes in the graph
	private int ngenerators; //Number of conventional generators
	private int nconsumers; //Number of loads
	private int nrgenetarors; //Number of renewable generators
	private int loadmax; //Daily max load demand
	private int nstorage; //Number of loads
	private int efficency; //TODO: determine what this variable represents(capacity of real edges?), it's hardcoded to 75
	private float ccurt; //Renewable cut costs
	private float etac; //Duration of each time step
	private float etad; //Charge and discharge efficiency of storages
	private float delta; //Number of storage systems.
	private Node[] nodelist;
	private Edge[][] network;

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
		setCcurt(200.0F);
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
		for (int i = 0; i < g.ngenerators; i++) {
			g.nodelist[i] = new Generator(((Generator) this.nodelist[i]).getMinP(),
					((Generator) this.nodelist[i]).getMaxP(), ((Generator) this.nodelist[i]).getCoef(),
					((Generator) this.nodelist[i]).getType(), ((Generator) this.nodelist[i]).getProduction());
		}
		for (int i = g.getNGenerators(); i < g.ngenerators + g.getNConsumers(); i++) {
			g.nodelist[i] = new Consumer(((Consumer) this.nodelist[i]).getLoad());
		}
		for (int i = g.getNNode() - g.getNrgenetarors() - g.getNstorage(); i < g.getNNode() - g.getNstorage(); i++) {
			g.nodelist[i] = new RewGenerator(((RewGenerator) this.nodelist[i]).getMinP(),
					((RewGenerator) this.nodelist[i]).getMaxP(), ((RewGenerator) this.nodelist[i]).getCoef(),
					((RewGenerator) this.nodelist[i]).getType());
		}
		for (int i = g.getNNode() - g.getNstorage(); i < g.getNNode(); i++) {
			g.nodelist[i] = new Storage(((Storage) this.nodelist[i]).getAvaliability(),
					((Storage) this.nodelist[i]).getCapacity(), ((Storage) this.nodelist[i]).getMincap());
		}
		for (int i = 0; i < g.getNNode(); i++)
			for (int j = 0; j < g.getNNode(); j++) {
				g.network[i][j] = new Edge();
				g.network[i][j].setCapacity(this.network[i][j].getCapacity());
				g.network[i][j].setWeight(this.network[i][j].getWeight());
				g.network[i][j].setFlow(this.network[i][j].getFlow());
			}
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
