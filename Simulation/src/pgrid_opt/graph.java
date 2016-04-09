package pgrid_opt;

public class graph implements Cloneable {
	private int nnode;
	private int ngenerators;
	private int nconsumers;
	private int nrgenetarors;
	private int loadmax;
	private int nstorage;
	private int efficency;
	private float ccurt;
	private float etac;
	private float etad;
	private float delta;
	private node[] nodelist;
	private edge[][] network;

	public graph(int nnode, int ngenerators, int nrgenerators, int nconsumers, int loadmax, int nstorage, float delta,
			float etac, float etad) {
		setNodeList(new node[nnode]);
		this.network = new edge[nnode][nnode];
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

	public edge[][] getNetwork() {
		return this.network;
	}

	public void setNetwork(edge[][] network) {
		this.network = network;
	}

	public node[] getNodeList() {
		return this.nodelist;
	}

	public void setNodeList(node[] nodelist) {
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

	public int getNNode() {
		return this.nnode;
	}

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

	public graph clone() {
		graph g = new graph(this.nnode, this.ngenerators, this.nrgenetarors, this.nconsumers, this.loadmax,
				this.nstorage, this.delta, this.etac, this.etad);
		for (int i = 0; i < g.ngenerators; i++) {
			g.nodelist[i] = new generator(((generator) this.nodelist[i]).getMinP(),
					((generator) this.nodelist[i]).getMaxP(), ((generator) this.nodelist[i]).getCoef(),
					((generator) this.nodelist[i]).getType());
		}
		for (int i = g.getNGenerators(); i < g.ngenerators + g.getNConsumers(); i++) {
			g.nodelist[i] = new consumer(((consumer) this.nodelist[i]).getLoad());
		}
		for (int i = g.getNNode() - g.getNrgenetarors() - g.getNstorage(); i < g.getNNode() - g.getNstorage(); i++) {
			g.nodelist[i] = new rewGenerator(((rewGenerator) this.nodelist[i]).getMinP(),
					((rewGenerator) this.nodelist[i]).getMaxP(), ((rewGenerator) this.nodelist[i]).getCoef(),
					((rewGenerator) this.nodelist[i]).getType());
		}
		for (int i = g.getNNode() - g.getNstorage(); i < g.getNNode(); i++) {
			g.nodelist[i] = new storage(((storage) this.nodelist[i]).getAvaliability(),
					((storage) this.nodelist[i]).getCapacity(), ((storage) this.nodelist[i]).getMincap());
		}
		for (int i = 0; i < g.getNNode(); i++)
			for (int j = 0; j < g.getNNode(); j++) {
				g.network[i][j] = new edge();
				g.network[i][j].setCapacity(this.network[i][j].getCapacity());
				g.network[i][j].setWeight(this.network[i][j].getWeight());
				g.network[i][j].setFlow(this.network[i][j].getFlow());
			}
		return g;
	}

	public int getNstorage() {
		return this.nstorage;
	}

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

	public float getDelta() {
		return this.delta;
	}

	public void setDelta(float delta) {
		this.delta = delta;
	}

	public float getEtad() {
		return this.etad;
	}

	public void setEtad(float etad) {
		this.etad = etad;
	}

	public float getEtac() {
		return this.etac;
	}

	public void setEtac(float etac) {
		this.etac = etac;
	}
}
