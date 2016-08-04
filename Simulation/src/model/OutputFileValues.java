package model;

import java.util.ArrayList;
import java.util.List;

public class OutputFileValues {

    private List<Integer> listNode;
    private List<Integer> listInnerNode;

    private List<List<Float>> listFlow;
    private List<List<Float>> listCentrality;
    private List<List<Float>> listPercentage;

    public OutputFileValues(int nodecount){
        this.listNode = new ArrayList(nodecount);
        this.listInnerNode = new ArrayList(nodecount);

        this.listFlow = new ArrayList<List<Float>>(nodecount);
        this.listCentrality = new ArrayList<List<Float>>(nodecount);
        this.listPercentage = new ArrayList<List<Float>>(nodecount);

        for (int i = 0; i < nodecount; i++){
            listFlow.add(i, new ArrayList<>());
            listCentrality.add(i, new ArrayList<>());
            listPercentage.add(i, new ArrayList<>());
        }
    }

    public void addNodeToList(int line, int node){
        this.listNode.add(node);
    }

    public void addInnerNodeToList(int line, int innernode){
        this.listInnerNode.add(line, innernode);
    }

    public void addFlowToList(int line, float flow){
        this.listFlow.get(line).add(flow);
    }

    public void addCentralityToList(int line, float cent){
        this.listCentrality.get(line).add(cent);
    }

    public void addPercentageToList(int line, float perc){
        this.listPercentage.get(line).add(perc);
    }


    public List<Integer> getListNode(){
        return this.listNode;
    }

    public List<Integer> getListInnerNode(){
        return this.listInnerNode;
    }

    public List<List<Float>> getListFlow(){
        return this.listFlow;
    }

    public List<List<Float>> getListCentrality(){
        return this.listCentrality;
    }

    public List<List<Float>> getListPercentage(){
        return this.listPercentage;
    }



}
