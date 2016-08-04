package model;

import java.util.ArrayList;
import java.util.List;

public class SocValues {

    private List<Integer> listNode;
    private List<Integer> listInnerNode;

    private List<List<Float>> listSoC;


    public SocValues(int nstorage){

        this.listNode = new ArrayList<>(nstorage);
        this.listInnerNode = new ArrayList<>(nstorage);
        this.listSoC = new ArrayList<List<Float>>(nstorage);

        for (int i = 0; i < nstorage; i++) {
            listSoC.add(i, new ArrayList<>());
        }

    }

    public void addNodeToList(int line, int node){
        this.listNode.add(node);
    }

    public void addInnerNodeToList(int line, int innernode){
        this.listInnerNode.add(line, innernode);
    }

    public void addSocToList(int line, float soc){
        this.listSoC.get(line).add(soc);
    }

    public List<Integer> getListNode(){
        return this.listNode;
    }

    public List<Integer> getListInnerNode(){
        return this.listInnerNode;
    }

    public List<List<Float>> getListSoC(){
        return this.listSoC;
    }


}
