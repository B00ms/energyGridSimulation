package pgrid_opt;

public class Offer implements Comparable<Offer>{

    private double production; //MWh
    private double price; //euro/MWh
    private boolean available;

    private int nodeIndex; // id of current generator in nodeList
    private int offerId; // id in offer list

    public Offer(double production, double price, int nodeIndex, int offerId){
        this.production = production;
        this.price = price;
        this.available = true;
        this.nodeIndex = nodeIndex;
        this.offerId = offerId;
    }

    public void setProduction(int production){
        this.production = production;
    }

    public double getProduction(){
        return this.production;
    }

    public void setPrice(int price){
        this.price = price;
    }

    public double getPrice(){
        return this.price;
    }

    public void setAvailable(boolean available){
        this.available = available;
    }

    public boolean getAvailable(){
        return this.available;
    }

    public int getNodeIndex(){
        return this.nodeIndex;
    }

    public int getOfferListId(){
        return this.offerId;
    }

    @Override
    public int compareTo(Offer o) {
        if(this.getPrice() < o.getPrice()){
            return -1;
        }else if(this.getPrice() > o.getPrice()){
            return 1;
        }else{
            return 0;
        }
    }
}
