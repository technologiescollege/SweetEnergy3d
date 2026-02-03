package com.eteks.sweethome3d.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Compare deux fichiers .ng3 (ex: test_sh3d.ng3 exporté SH3D vs test_e3d.ng3 créé dans Energy3D)
 * pour aligner l'export sur ce qu'Energy3D écrit (scale, caméra, dimensions).
 */
public class Ng3CompareMain {

    static class CLObjectInputStream extends ObjectInputStream {
        private final ClassLoader cl;

        CLObjectInputStream(FileInputStream in, ClassLoader cl) throws java.io.IOException {
            super(in);
            this.cl = cl;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws java.io.IOException, ClassNotFoundException {
            try {
                return Class.forName(desc.getName(), false, cl);
            } catch (ClassNotFoundException e) {
                return super.resolveClass(desc);
            }
        }
    }

    static void dumpScene(Object scene, Class<?> sceneClass, String label) throws Exception {
        System.out.println("\n=== " + label + " ===");

        Field scaleField = sceneClass.getDeclaredField("annotationScale");
        scaleField.setAccessible(true);
        double scale = scaleField.getDouble(scene);
        System.out.println("annotationScale = " + scale);

        Method getCamLoc = sceneClass.getMethod("getCameraLocation");
        Method getCamDir = sceneClass.getMethod("getCameraDirection");
        Object loc = getCamLoc.invoke(scene);
        Object dir = getCamDir.invoke(scene);
        System.out.println("cameraLocation = " + (loc != null ? vec3Str(loc) : "null"));
        System.out.println("cameraDirection = " + (dir != null ? vec3Str(dir) : "null"));

        Method getParts = sceneClass.getMethod("getParts");
        @SuppressWarnings("unchecked")
        java.util.List<Object> parts = (java.util.List<Object>) getParts.invoke(scene);
        System.out.println("parts.size() = " + (parts != null ? parts.size() : 0));

        if (parts == null) return;

        ClassLoader loader = sceneClass.getClassLoader();
        Class<?> foundationClass = loader.loadClass("org.concord.energy3d.model.Foundation");
        Class<?> wallClass = loader.loadClass("org.concord.energy3d.model.Wall");

        for (int i = 0; i < parts.size(); i++) {
            Object p = parts.get(i);
            if (foundationClass.isInstance(p)) {
                System.out.println("  Part[" + i + "] Foundation:");
                dumpFoundation(p, foundationClass, loader);
            } else if (wallClass.isInstance(p)) {
                System.out.println("  Part[" + i + "] Wall (top-level):");
                dumpHousePartTextureType(p, wallClass, "    ");
                dumpWall(p, wallClass);
            }
        }
    }

    static String vec3Str(Object v) throws Exception {
        Class<?> c = v.getClass();
        Method getX = c.getMethod("getX");
        Method getY = c.getMethod("getY");
        Method getZ = c.getMethod("getZ");
        double x = ((Number) getX.invoke(v)).doubleValue();
        double y = ((Number) getY.invoke(v)).doubleValue();
        double z = ((Number) getZ.invoke(v)).doubleValue();
        return String.format("(%.4f, %.4f, %.4f)", x, y, z);
    }

    static void dumpFoundation(Object f, Class<?> foundationClass, ClassLoader loader) throws Exception {
        dumpHousePartTextureType(f, foundationClass, "    ");
        Method getHeight = foundationClass.getSuperclass().getMethod("getHeight");
        double h = ((Number) getHeight.invoke(f)).doubleValue();
        System.out.println("    height = " + h);

        Field pointsField = null;
        for (Class<?> c = foundationClass; c != null; c = c.getSuperclass()) {
            try {
                pointsField = c.getDeclaredField("points");
                break;
            } catch (NoSuchFieldException ignored) {}
        }
        if (pointsField != null) {
            pointsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Object> points = (java.util.List<Object>) pointsField.get(f);
            if (points != null && points.size() >= 4) {
                System.out.println("    points[0] = " + vec3Str(points.get(0)));
                System.out.println("    points[2] = " + vec3Str(points.get(2)));
                System.out.println("    points[1] = " + vec3Str(points.get(1)));
                System.out.println("    points[3] = " + vec3Str(points.get(3)));
            }
        }
        // Enfants (murs, etc.)
        Method getChildren = foundationClass.getMethod("getChildren");
        @SuppressWarnings("unchecked")
        java.util.List<Object> children = (java.util.List<Object>) getChildren.invoke(f);
        if (children != null && !children.isEmpty()) {
            Class<?> wallClass = loader.loadClass("org.concord.energy3d.model.Wall");
            System.out.println("    children = " + children.size());
            for (int i = 0; i < children.size(); i++) {
                Object ch = children.get(i);
                if (wallClass.isInstance(ch)) {
                    System.out.println("      child[" + i + "] Wall:");
                    dumpHousePartTextureType(ch, wallClass, "        ");
                    dumpWall(ch, wallClass);
                }
            }
        }
    }

    /** Affiche le textureType d'un HousePart (Foundation ou Wall). Utilise le ClassLoader de part. */
    static void dumpHousePartTextureType(Object part, Class<?> partClass, String indent) throws Exception {
        int tt = -999;
        if (part != null) {
            ClassLoader loader = part.getClass().getClassLoader();
            try {
                Class<?> housePart = loader.loadClass("org.concord.energy3d.model.HousePart");
                Field ttField = housePart.getDeclaredField("textureType");
                ttField.setAccessible(true);
                tt = ttField.getInt(part);
            } catch (Exception e) {
                System.out.println(indent + "textureType = (erreur: " + e.getMessage() + ")");
                return;
            }
        }
        System.out.println(indent + "textureType = " + tt + " (0=aucune, 1=#1 fondation, 3=#3 mur, etc.)");
    }

    static void dumpWall(Object w, Class<?> wallClass) throws Exception {
        Method getHeight = wallClass.getSuperclass().getMethod("getHeight");
        double h = ((Number) getHeight.invoke(w)).doubleValue();
        System.out.println("    height = " + h);

        Field pointsField = null;
        for (Class<?> c = wallClass; c != null; c = c.getSuperclass()) {
            try {
                pointsField = c.getDeclaredField("points");
                break;
            } catch (NoSuchFieldException ignored) {}
        }
        if (pointsField != null) {
            pointsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Object> points = (java.util.List<Object>) pointsField.get(w);
            if (points != null && points.size() >= 4) {
                System.out.println("    points[0] = " + vec3Str(points.get(0)));
                System.out.println("    points[2] = " + vec3Str(points.get(2)));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String sh3dPath = args != null && args.length > 0 ? args[0] : "C:\\Users\\babas\\Documents\\plan_energy3d.ng3";
        String e3dPath  = args != null && args.length > 1 ? args[1] : "C:\\Users\\babas\\Documents\\plan_energy3d2.ng3";

        File fSh3d = new File(sh3dPath);
        File fE3d  = new File(e3dPath);
        System.out.println("Compare:");
        System.out.println("  SH3D export: " + fSh3d.getAbsolutePath() + " (" + (fSh3d.exists() ? fSh3d.length() : 0) + " bytes)");
        System.out.println("  Energy3D:   " + fE3d.getAbsolutePath()  + " (" + (fE3d.exists() ? fE3d.length() : 0) + " bytes)");

        if (!fSh3d.exists() || !fE3d.exists()) {
            System.out.println("Fichier(s) manquant(s).");
            return;
        }

        ClassLoader energy3dCL = Energy3DClassLoader.getEnergy3DClassLoader(null);
        if (energy3dCL == null) {
            System.out.println("Energy3D ClassLoader indisponible.");
            return;
        }

        Class<?> sceneClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.scene.Scene", null);
        if (sceneClass == null) {
            System.out.println("Classe Scene indisponible.");
            return;
        }

        try (FileInputStream fis = new FileInputStream(fSh3d);
             ObjectInputStream ois = new CLObjectInputStream(fis, energy3dCL)) {
            Object sceneSh3d = ois.readObject();
            dumpScene(sceneSh3d, sceneClass, "test_sh3d.ng3 (export SH3D)");
        }

        try (FileInputStream fis = new FileInputStream(fE3d);
             ObjectInputStream ois = new CLObjectInputStream(fis, energy3dCL)) {
            Object sceneE3d = ois.readObject();
            dumpScene(sceneE3d, sceneClass, "test_e3d.ng3 (créé Energy3D)");
        }

        System.out.println("\n--- Fin comparaison ---");
    }
}
