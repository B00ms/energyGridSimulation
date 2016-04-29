package pgrid_opt;

public class ConventionalGenerator extends Generator{


	private int mttf;//mean time to failure
	private int mttr;//mean time to repair


	public ConventionalGenerator(float min, float max, float coef, String type, double production) {
		// TODO Auto-generated constructor stub
		super(min, max, coef, type, production);

		// only used with conventional generator.
		this.mttf = 630;
		this.mttr = 60;
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


}
