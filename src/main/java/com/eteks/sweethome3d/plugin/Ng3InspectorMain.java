package com.eteks.sweethome3d.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Method;

public class Ng3InspectorMain {
    static class CLObjectInputStream extends ObjectInputStream {
        private final ClassLoader cl;

        CLObjectInputStream(FileInputStream in, ClassLoader cl) throws java.io.IOException {
            super(in);
            this.cl = cl;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            try {
                return Class.forName(desc.getName(), false, cl);
            } catch (ClassNotFoundException e) {
                return super.resolveClass(desc);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        final String inFile = args != null && args.length > 0 ? args[0] : "target/test_floor_10x12.ng3";
        final File file = new File(inFile);
        System.out.println("Inspecting: " + file.getAbsolutePath() + " (" + (file.exists() ? file.length() : 0) + " bytes)");
        if (!file.exists()) {
            System.out.println("File not found");
            return;
        }

        final ClassLoader energy3dCL = Energy3DClassLoader.getEnergy3DClassLoader(null);
        if (energy3dCL == null) {
            System.out.println("Energy3D ClassLoader unavailable");
            return;
        }

        try (final FileInputStream fis = new FileInputStream(file);
             final ObjectInputStream ois = new CLObjectInputStream(fis, energy3dCL)) {
            final Object scene = ois.readObject();
            final Class<?> sceneClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.scene.Scene", null);
            final Method getParts = sceneClass.getMethod("getParts");
            final java.util.List<?> parts = (java.util.List<?>) getParts.invoke(scene);
            System.out.println("Parts count: " + (parts != null ? parts.size() : -1));
            final Class<?> foundationClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Foundation", null);
            int foundations = 0;
            if (parts != null) {
                for (Object p : parts) {
                    if (foundationClass.isInstance(p)) {
                        foundations++;
                    }
                }
            }
            System.out.println("Foundations: " + foundations);
        }
    }
}
