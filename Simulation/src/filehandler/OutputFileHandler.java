package filehandler;

import graph.Graph;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

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
    public static void writeModelOutputFiles(String dirpath, String solutionPath, int timeStep){
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
}
