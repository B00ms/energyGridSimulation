package pgrid_opt;

public class ConventionalGenerator extends Generator implements Comparable<ConventionalGenerator>{


	private int mttf;//mean time to failure
	private int mttr;//mean time to repair
	private boolean generatorFailure = false; //Indicates if the generator is working normally or if it has failed


	public ConventionalGenerator(double minProduction, double maxProduction, double coef, String type, double production, int nodeId) {
		super(minProduction, maxProduction, coef, type, production, nodeId);

		// only used with conventional generator.
		this.mttf = 630;
		this.mttr = 60;
	}

	public ConventionalGenerator(double minProduction, double maxProduction, double coef, String type, double production) {
		super(minProduction, maxProduction, coef, type, production);

		// only used with conventional generator.
		this.mttf = 630;
		this.mttr = 60;
	}

	public boolean getGeneratorFailure() {
		return generatorFailure;
	}

	public void setGeneratorFailure(boolean generatorFailure) {
		this.generatorFailure = generatorFailure;
	}

	/**
	 *
	 * @return the production of this generator
	 */
	public double getProduction() {
		if (generatorFailure == false)
			return production;
		else
			return 0;
	}

	public void setMTTF(int mttf){
		this.mttf = mttf;
	}

	public void setMTTR(int mttr){
		this.mttr = mttr;
	}

	public int getMTTF(){
		return this.mttf;
	}

	public int getMTTR(){
		return this.mttr;
	}

	@Override
	public int compareTo(ConventionalGenerator o) {

		if(this.getType().equals(o.getType())){
			return 0; //Generator type are equal so we return 0.
		}

		/*
		 * Nuclear followed by Oil followed by Coal, Hydro is dead last.
		 */
		switch (this.getType()){
		case "N":
			return -1; //Generators are not equal but this is a nuclear one so its always higher.
		case "H":
			if(o.getType().equals("C")){
				return -1;
			}
			return 1;
		case "O":
			if(o.getType().equals("C")){
				return -1;
			} else
				return 1;
			default:
				return 1;
			}
	}
}
