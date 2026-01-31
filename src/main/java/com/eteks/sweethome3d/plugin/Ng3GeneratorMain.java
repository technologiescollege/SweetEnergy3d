package com.eteks.sweethome3d.plugin;

import java.io.File;

public class Ng3GeneratorMain {
    public static void main(String[] args) {
        if (args != null && args.length >= 2 && args[0].toLowerCase().endsWith(".sh3d")) {
            File inputSh3d = new File(args[0]);
            File outputNg3 = new File(args[1]);
            boolean ok = PlanExporter.exportSh3dFileToNg3(inputSh3d, outputNg3);
            System.out.println("Converted: " + ok + " -> " + outputNg3.getAbsolutePath() + " (" + (outputNg3.exists() ? outputNg3.length() : 0) + " bytes) from " + inputSh3d.getAbsolutePath());
        } else if (args != null && args.length == 3 && args[2].toLowerCase().endsWith(".ng3")) {
            double w = Double.parseDouble(args[0]);
            double h = Double.parseDouble(args[1]);
            File outputFile = new File(args[2]);
            boolean ok = PlanExporter.exportEmptyNg3WithFloor(w, h, outputFile);
            System.out.println("Created floor " + w + "x" + h + "m: " + ok + " -> " + outputFile.getAbsolutePath() + " (" + (outputFile.exists() ? outputFile.length() : 0) + " bytes)");
        } else {
            String out = args != null && args.length > 0 ? args[0] : "target/empty_project.ng3";
            File outputFile = new File(out);
            boolean ok = PlanExporter.exportEmptyNg3(outputFile);
            System.out.println("Created: " + ok + " -> " + outputFile.getAbsolutePath() + " (" + (outputFile.exists() ? outputFile.length() : 0) + " bytes)");
        }
    }
}
