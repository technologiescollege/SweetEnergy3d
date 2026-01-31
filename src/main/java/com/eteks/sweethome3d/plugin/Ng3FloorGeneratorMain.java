package com.eteks.sweethome3d.plugin;

import java.io.File;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;

public class Ng3FloorGeneratorMain {
    public static void main(String[] args) {
        final String out = args != null && args.length > 0 ? args[0] : "target/test_floor_10x12.ng3";
        final File outputFile = new File(out);
        final Home home = new Home(250);

        final float thickness = 20f;
        final float height = 250f;

        final Wall w1 = new Wall(0f, 0f, 1200f, 0f, thickness, height);
        final Wall w2 = new Wall(1200f, 0f, 1200f, 1000f, thickness, height);
        final Wall w3 = new Wall(1200f, 1000f, 0f, 1000f, thickness, height);
        final Wall w4 = new Wall(0f, 1000f, 0f, 0f, thickness, height);

        w1.setWallAtEnd(w2);
        w2.setWallAtEnd(w3);
        w3.setWallAtEnd(w4);
        w4.setWallAtEnd(w1);

        home.addWall(w1);
        home.addWall(w2);
        home.addWall(w3);
        home.addWall(w4);

        final boolean ok = PlanExporter.exportToEnergy3D(home, outputFile);
        System.out.println("Created: " + ok + " -> " + outputFile.getAbsolutePath() + " (" + (outputFile.exists() ? outputFile.length() : 0) + " bytes)");
    }
}
