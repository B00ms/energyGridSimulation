 package pgrid_opt;

public class generator
   extends node
 {
   private float maxp;
  
  private float minp;
   
 private float coef;
  private String type;
  public generator(float min, float max, float coef, String type)
   {
    this.maxp = max;
this.minp = min;
this.coef = coef;
setType(type);
}

public float getMaxP() { return this.maxp; }

public void setMaxP(float maxp) {
this.maxp = maxp;
}

public float getMinP() { return this.minp; }

public void setMinP(float minp) {
this.minp = minp;
}

public boolean isRenew() { return false; }

public float getCoef() {
return this.coef;
}

public void setCoef(float coef) { this.coef = coef; }

public String getType() {
return this.type;
}

public void setType(String type) { this.type = type; }
}
