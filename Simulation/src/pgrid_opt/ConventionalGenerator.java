package pgrid_opt;

import java.util.Comparator;

public class ConventionalGenerator extends Generator implements Comparable<ConventionalGenerator>{


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
