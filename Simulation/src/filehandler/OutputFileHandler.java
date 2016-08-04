package filehandler;

import graph.Graph;
import model.OutputFileValues;
import model.SocValues;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

/**
 * Created by ejay on 25/06/16.
 */
public class OutputFileHandler {

    DataModelPrint mp = new DataModelPrint();

    /**
     * Move glpsol output to structured folders
     * @param dirpath current location of files
     * @param solutionPath new location for files
     * @param timeStep current timestep
     */
    public void writeModelOutputFiles(String dirpath, String solutionPath, int timeStep){
        try {
            if (new File(dirpath + "/sol" + timeStep + ".txt").exists())
                Files.move(Paths.get(dirpath + "/sol" + timeStep + ".txt"),
                        Paths.get(solutionPath + "/sol" + timeStep + ".txt"), StandardCopyOption.REPLACE_EXISTING);

            if (new File(dirpath + "/filename.out").exists())
                Files.move(Paths.get(dirpath + "/filename.out"), Paths.get(solutionPath + "/filename.out"),
                        StandardCopyOption.REPLACE_EXISTING);

            if (new File(dirpath + "/update.txt").exists())
                Files.copy(Paths.get(dirpath + "/update.txt"), Paths.get(solutionPath + "/update" + timeStep + ".txt"),
                        StandardCopyOption.REPLACE_EXISTING);
            
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    /**
     * Write and save storage.txt file for later analyzing
     * @param timestepsGraph
     * @param dirpath
     * @param solutionPath
     */
    public void writeStorageTxtFile(Graph[] timestepsGraph, String dirpath, String solutionPath){
        if (timestepsGraph[0].getNstorage() > 0) {
            mp.printStorageData(timestepsGraph, String.valueOf(dirpath) + "storage.txt");

            if (new File(dirpath + "/storage.txt").exists()) {
                try {
                    Files.copy(Paths.get(dirpath + "/storage.txt"), Paths.get(solutionPath + "/storage.txt"),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(0);
                }
            }
        }
    }

    public void writeOutputDescFile(String path){

        String filename = "grid_desc.txt";
        try {
            PrintWriter writer = new PrintWriter(path+"/"+filename, "UTF-8");
//            writer.print("#,#,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23");
            writer.print("#,#,hourly flow");
            writer.println();

            writer.print("#,#,hourly centrality");
            writer.println();

            writer.print("#,#,hourly %");
            writer.println();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

    }


    public void compressOutputFiles(String hourly_path, String daily_path, String filename, int edge_count){

        OutputFileValues outputValues = this.parseOutputFiles(hourly_path, edge_count);

        try {
            PrintWriter writer = new PrintWriter(daily_path+"/"+filename, "UTF-8");

            // write flows
            writer.println("## "+edge_count+": #,#,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23");
            for(int i=0; i < edge_count; i++){
                int node = outputValues.getListNode().get(i);
                int innernode = outputValues.getListInnerNode().get(i);
                writer.print(node+","+innernode);

                List<Float> flowList = outputValues.getListFlow().get(i);
                for(int j = 0; j < flowList.size(); j++){
                    float list_val = flowList.get(j);
                    writer.print(","+list_val);
                }
                writer.println();
                writer.flush();
            }

            writer.println();
            writer.println("## "+edge_count+": #,#,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23");
            // write centrality index
            for(int i=0; i < edge_count; i++){
                int node = outputValues.getListNode().get(i);
                int innernode = outputValues.getListInnerNode().get(i);
                writer.print(node+","+innernode);

                List<Float> flowList = outputValues.getListCentrality().get(i);
                for(int j = 0; j < flowList.size(); j++){
                    float list_val = flowList.get(j);
                    writer.print(","+list_val);
                }
                writer.println();
                writer.flush();
            }

            writer.println();
            writer.println("## "+edge_count+": #,#,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23");
            // write percentage
            for(int i=0; i < edge_count; i++){
                int node = outputValues.getListNode().get(i);
                int innernode = outputValues.getListInnerNode().get(i);
                writer.print(node+","+innernode);

                List<Float> flowList = outputValues.getListPercentage().get(i);
                for(int j = 0; j < flowList.size(); j++){
                    float list_val = flowList.get(j);
                    writer.print(","+list_val);
                }
                writer.println();
                writer.flush();
            }

            // write SoC

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public void compressSocFiles(String hourly_path, String daily_path, String filename, int nstorage){
        SocValues storageSoC = this.parseSocFiles(hourly_path, nstorage);

        try {
            PrintWriter writer = new PrintWriter(daily_path + "/" + filename, "UTF-8");

            writer.println("## "+nstorage+": #,#,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23");
            // write soc
            for(int i=0; i < nstorage; i++){
                int node = storageSoC.getListNode().get(i);
                int innernode = storageSoC.getListInnerNode().get(i);
                writer.print(node+","+innernode);

                List<Float> flowList = storageSoC.getListSoC().get(i);
                for(int j = 0; j < flowList.size(); j++){
                    float list_val = flowList.get(j);
                    writer.print(","+list_val);
                }
                writer.println();
                writer.flush();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    public OutputFileValues parseOutputFiles(String path, int edge_count){
        Scanner scanner;
        OutputFileValues outputValues = new OutputFileValues(edge_count);

        for(int i = 0; i <=23; i++) {
            if (new File(path + "/sol" + i + ".txt").exists()) {
                try {
                    int j = 0;
                    scanner = new Scanner(Paths.get(path, "/sol" + i + ".txt"));
                    scanner.useDelimiter(System.getProperty("line.separator"));
                    boolean has_next_line = scanner.hasNextLine();

                    while (has_next_line) {
                        String next_line = scanner.next();
                        Scanner linescanner = new Scanner(next_line);
                        linescanner.useDelimiter(",");

                        if(j == 252){
                            has_next_line = false;
                        }

                        int node = linescanner.nextInt();
                        int inner_node = linescanner.nextInt();

                        // these values are only needed once
                        if(i ==0) {
                            outputValues.addNodeToList(j, node);
                            outputValues.addInnerNodeToList(j, inner_node);
                        }

                        float flow = linescanner.nextFloat();
                        float cent_index = linescanner.nextFloat();
                        float percentage = linescanner.nextFloat();

                        outputValues.addFlowToList(j, flow);
                        outputValues.addCentralityToList(j, cent_index);
                        outputValues.addPercentageToList(j, percentage);

                        linescanner.close();
                        j++;
                    }
                    scanner.close();

                } catch (IOException e1) {
                    e1.printStackTrace();
                    System.exit(0);
                }
            }
        }

        return outputValues;
    }

    public SocValues parseSocFiles(String path, int nstorage){
        Scanner scanner;
        SocValues socValues = new SocValues(nstorage);

        for(int i = 0; i <=23; i++) {
            if (new File(path + "/update" + i + ".txt").exists()) {
                try {
                    int j = 0;
                    scanner = new Scanner(Paths.get(path, "/update" + i + ".txt"));
                    scanner.useDelimiter(System.getProperty("line.separator"));
                    String next_line = null;

                    // skip the last weird empty line
                    while (scanner.hasNextLine()) {
                        next_line = scanner.nextLine();
                        Scanner linescanner = new Scanner(next_line);
                        linescanner.useDelimiter(" ");

                        int node = linescanner.nextInt();
                        int inner_node = linescanner.nextInt();

                        // these values are only needed once
                        if(i ==0) {
                            socValues.addNodeToList(j, node);
                            socValues.addInnerNodeToList(j, inner_node);
                        }

                        float SoC = linescanner.nextFloat();
                        socValues.addSocToList(j, SoC);
                        linescanner.close();
                        j++;
                    }
                    scanner.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                    System.exit(0);
                }

            }
        }

        return socValues;
    }
}
