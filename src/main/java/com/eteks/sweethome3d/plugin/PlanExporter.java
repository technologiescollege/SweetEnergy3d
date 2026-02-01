package com.eteks.sweethome3d.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.Collection;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;

// Energy3D imports - chargement dynamique pour éviter les erreurs de compilation
// Ne pas importer directement pour permettre la gestion d'erreur
// import org.concord.energy3d.model.Foundation;
// import org.concord.energy3d.scene.Scene;
// import com.ardor3d.math.Vector3;
// import com.ardor3d.math.ColorRGBA;

/**
 * Classe utilitaire pour exporter un plan Sweet Home 3D vers un fichier .ng3 binaire Energy3D
 */
public class PlanExporter {
    
    /**
     * Exporte un plan complet vers un fichier .ng3 binaire compatible Energy3D
     * 
     * @param home Le Home à exporter
     * @param outputFile Le fichier de sortie .ng3
     * @return true si l'export a réussi, false sinon
     */
    public static boolean exportToEnergy3D(Home home, File outputFile) {
        // Créer un fichier de log pour le diagnostic
        File logFile = new File(outputFile.getParentFile(), outputFile.getName() + ".log");
        PrintWriter logWriter = null;
        
        try {
            // Créer le répertoire parent si nécessaire
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            if (logFile.getParentFile() != null) {
                logFile.getParentFile().mkdirs();
            }
            
            // Ouvrir le log
            try {
                logWriter = new PrintWriter(new java.io.FileWriter(logFile, true));
                logWriter.println("\n=== DEBUT EXPORT ENERGY3D (PlanExporter) ===");
                logWriter.println("Fichier de sortie: " + outputFile.getAbsolutePath());
                logWriter.println("Timestamp: " + new java.util.Date());
                logWriter.flush();
            } catch (Exception e) {
                System.err.println("ERREUR lors de la création du log: " + e.getMessage());
                e.printStackTrace();
                // Continuer sans log
            }
            
            // Vérifier les classes Energy3D
            logWriter.println("Vérification des classes Energy3D...");
            logWriter.flush();
            
            // Forcer l'écriture du log immédiatement
            System.out.println("DEBUG: Création du ClassLoader Energy3D...");
            
            ClassLoader energy3dLoader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
            if (energy3dLoader == null) {
                logWriter.println("✗ ERREUR: Impossible de créer le ClassLoader Energy3D!");
                logWriter.flush();
                System.err.println("ERREUR: ClassLoader Energy3D non créé!");
                return false;
            }
            
            System.out.println("DEBUG: ClassLoader créé: " + energy3dLoader.getClass().getName());
            
            // Précharger toutes les classes Ardor3D nécessaires AVANT de charger Foundation
            // HousePart a un champ statique offsetState = new OffsetState() qui doit être résolu
            // Foundation et HousePart utilisent RenderState.StateType comme import statique
            logWriter.println("Préchargement des classes Ardor3D nécessaires...");
            logWriter.flush();
            System.out.println("DEBUG: Tentative de chargement des classes Ardor3D...");
            
            // 1. RenderState (utilisé par RenderState.StateType)
            Class<?> renderStateClass = null;
            try {
                renderStateClass = energy3dLoader.loadClass("com.ardor3d.renderer.state.RenderState");
                logWriter.println("✓ RenderState préchargé: " + renderStateClass.getName());
                logWriter.flush();
            } catch (Exception e) {
                logWriter.println("✗ ERREUR: Impossible de précharger RenderState: " + e.getMessage());
                e.printStackTrace(logWriter);
                logWriter.flush();
                return false;
            }
            
            // 2. OffsetState (utilisé par HousePart.offsetState = new OffsetState())
            try {
                Class<?> offsetStateClass = energy3dLoader.loadClass("com.ardor3d.renderer.state.OffsetState");
                logWriter.println("✓ OffsetState préchargé: " + offsetStateClass.getName());
                logWriter.flush();
            } catch (Exception e) {
                logWriter.println("✗ ERREUR: Impossible de précharger OffsetState: " + e.getMessage());
                e.printStackTrace(logWriter);
                logWriter.flush();
                return false;
            }
            
            // 3. OffsetType (utilisé par HousePart static block: offsetState.setTypeEnabled(OffsetType.Fill, true))
            try {
                Class<?> offsetTypeClass = energy3dLoader.loadClass("com.ardor3d.renderer.state.OffsetState$OffsetType");
                logWriter.println("✓ OffsetType préchargé: " + offsetTypeClass.getName());
                logWriter.flush();
            } catch (Exception e) {
                logWriter.println("✗ ERREUR: Impossible de précharger OffsetType: " + e.getMessage());
                e.printStackTrace(logWriter);
                logWriter.flush();
                return false;
            }
            
            // 4. MaterialState (stub dans le plugin, utilisé par Window et peut-être Foundation)
            logWriter.println("MaterialState sera chargé à la demande si nécessaire");
            logWriter.flush();
            
            // 5. BlendState et TextureState (utilisés par Foundation)
            try {
                energy3dLoader.loadClass("com.ardor3d.renderer.state.BlendState");
                logWriter.println("✓ BlendState préchargé");
                logWriter.flush();
            } catch (Exception e) {
                logWriter.println("⚠ AVERTISSEMENT: BlendState non préchargé: " + e.getMessage());
                logWriter.flush();
            }
            
            try {
                energy3dLoader.loadClass("com.ardor3d.renderer.state.TextureState");
                logWriter.println("✓ TextureState préchargé");
                logWriter.flush();
            } catch (Exception e) {
                logWriter.println("⚠ AVERTISSEMENT: TextureState non préchargé: " + e.getMessage());
                logWriter.flush();
            }

            // 6. LightState (stub pour compatibilité)
            try {
                energy3dLoader.loadClass("com.ardor3d.renderer.state.LightState");
                logWriter.println("✓ LightState préchargé");
                logWriter.flush();
            } catch (Exception e) {
                logWriter.println("⚠ AVERTISSEMENT: LightState non préchargé: " + e.getMessage());
                logWriter.flush();
            }
            
            // Précharger BloomRenderPass (utilisé par Foundation, même si commenté dans le code)
            try {
                energy3dLoader.loadClass("com.ardor3d.extension.effect.bloom.BloomRenderPass");
                logWriter.println("✓ BloomRenderPass préchargé");
                logWriter.flush();
            } catch (Exception e) {
                logWriter.println("⚠ AVERTISSEMENT: BloomRenderPass non préchargé: " + e.getMessage());
                logWriter.flush();
            }

            // Précharger ImageLoader compat (utilisé indirectement par Scene.openNow)
            try {
                Class<?> imageLoaderClass = energy3dLoader.loadClass("com.ardor3d.image.util.ImageLoader");
                logWriter.println("✓ ImageLoader (compat) préchargé: " + imageLoaderClass.getName());
                logWriter.println("  ClassLoader: " + imageLoaderClass.getClassLoader().getClass().getName());
                logWriter.flush();
            } catch (Exception e) {
                logWriter.println("⚠ AVERTISSEMENT: ImageLoader (compat) non préchargé: " + e.getMessage());
                logWriter.println("  Type: " + e.getClass().getName());
                logWriter.flush();
            }

            // Vérifier la hiérarchie MaterialState -> RenderState (debug compatibilité)
            try {
                Class<?> materialStateClass = energy3dLoader.loadClass("com.ardor3d.renderer.state.MaterialState");
                Class<?> renderStateCheck = energy3dLoader.loadClass("com.ardor3d.renderer.state.RenderState");
                Class<?> superClass = materialStateClass.getSuperclass();
                String materialLoader = materialStateClass.getClassLoader() != null
                    ? materialStateClass.getClassLoader().getClass().getName()
                    : "bootstrap";
                String superLoader = (superClass != null && superClass.getClassLoader() != null)
                    ? superClass.getClassLoader().getClass().getName()
                    : "bootstrap";
                logWriter.println("✓ MaterialState chargé: " + materialStateClass.getName());
                logWriter.println("  ClassLoader: " + materialLoader);
                logWriter.println("  Superclass: " + (superClass != null ? superClass.getName() : "null"));
                logWriter.println("  SuperClassLoader: " + superLoader);
                logWriter.println("  RenderState == Superclass: " + (renderStateCheck == superClass));
                logWriter.flush();
            } catch (Exception e) {
                logWriter.println("⚠ AVERTISSEMENT: Impossible de vérifier MaterialState/RenderState: " + e.getMessage());
                logWriter.flush();
            }
            
            logWriter.println("✓ Toutes les classes Ardor3D critiques préchargées");
            logWriter.flush();
            System.out.println("DEBUG: Classes Ardor3D chargées avec succès");
            
            logWriter.println("Chargement des classes Energy3D (Scene, Foundation, Wall)...");
            logWriter.flush();
            
            // Charger les classes nécessaires
            Class<?> sceneClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.scene.Scene", logWriter);
            Class<?> foundationClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Foundation", logWriter);
            Class<?> wallClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Wall", logWriter);
            
            logWriter.println("✓ Classes Energy3D chargées");
            logWriter.println("  Foundation ClassLoader: " + foundationClass.getClassLoader().getClass().getName());
            logWriter.flush();
            
            // Obtenir les murs
            Collection<Wall> sh3dWalls = home.getWalls();
            if (sh3dWalls == null) {
                sh3dWalls = new java.util.ArrayList<Wall>();
            }
            
            logWriter.println("Nombre de murs à exporter: " + sh3dWalls.size());
            logWriter.flush();
            
            // Supprimer le fichier existant s'il existe
            if (outputFile.exists()) {
                outputFile.delete();
            }
            
            // Créer une nouvelle instance de Scene (comme Energy3D le fait)
            logWriter.println("Création d'une nouvelle instance de Scene...");
            logWriter.flush();
            
            java.lang.reflect.Constructor<?> sceneConstructor = sceneClass.getDeclaredConstructor();
            sceneConstructor.setAccessible(true);
            Object scene = sceneConstructor.newInstance();
            
            logWriter.println("✓ Scene créée");
            logWriter.flush();
           
            // En mode export sans UI, on évite Scene.openNow/SceneManager.
            // On force Scene.instance pour que HousePart.init n'appelle pas openNow().
            ensureSceneInstance(sceneClass, scene, logWriter);

            // Initialiser la Scene (optionnel en mode headless) : désactivé pour éviter SceneManager
            logWriter.println("INFO: Initialisation Scene ignorée (mode headless export)");
            logWriter.flush();
            
            // Origine du plan : coin minimal des murs (axe SH3D), pour éviter la marge/boussole du plan
            double originX = 0.0, originY = 0.0;
            if (!sh3dWalls.isEmpty()) {
                double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
                for (Wall w : sh3dWalls) {
                    minX = Math.min(minX, Math.min(w.getXStart(), w.getXEnd()));
                    minY = Math.min(minY, Math.min(w.getYStart(), w.getYEnd()));
                }
                originX = minX;
                originY = minY;
                if (logWriter != null) {
                    logWriter.println("Origine du plan (coin min murs): originX=" + originX + " cm, originY=" + originY + " cm");
                    logWriter.flush();
                }
            }
            
            // Créer la Foundation
            logWriter.println("Création de la Foundation...");
            logWriter.flush();
            
            Object foundation = null;
            if (sh3dWalls.isEmpty()) {
                // Créer une foundation par défaut 10x10 m via Foundation(largeur, longueur) en unités (évite addPoint qui lève "Drawing already completed")
                logWriter.println("Aucun mur trouvé, création d'une foundation par défaut (10x10 m)...");
                logWriter.flush();
                foundation = createSizedFoundation(10.0, 10.0, logWriter);
            } else {
                foundation = createFoundation(sh3dWalls, originX, originY, logWriter);
            }
            
            if (foundation == null) {
                logWriter.println("✗ ERREUR: Impossible de créer la Foundation");
                logWriter.flush();
                return false;
            }
            
            logWriter.println("✓ Foundation créée");
            logWriter.flush();
            
            // Convertir les murs
            logWriter.println("Conversion des murs...");
            logWriter.flush();
            
            int wallCount = 0;
            int wallIndex = 0;
            for (Wall sh3dWall : sh3dWalls) {
                wallIndex++;
                try {
                    if (logWriter != null) {
                        logWriter.println("  Conversion du mur " + wallIndex + "/" + sh3dWalls.size() + "...");
                        logWriter.flush();
                    }
                    Object energy3dWall = convertWallToEnergy3D(sh3dWall, foundation, originX, originY, logWriter);
                    if (energy3dWall != null) {
                        java.lang.reflect.Method getChildrenMethod = foundationClass.getMethod("getChildren");
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> children = (java.util.List<Object>) getChildrenMethod.invoke(foundation);
                        children.add(energy3dWall);
                        wallCount++;
                        if (logWriter != null) { logWriter.println("  ✓ Mur " + wallIndex + " converti"); logWriter.flush(); }
                    }
                } catch (Throwable t) {
                    logWriter.println("  ✗ ERREUR lors de la conversion du mur " + wallIndex + ": " + t.getClass().getName() + " - " + t.getMessage());
                    t.printStackTrace(logWriter);
                    logWriter.flush();
                }
            }
            // Si fichier vide (aucun mur), ajouter un mur par défaut 5 m × 2 m × 0,2 m
            if (sh3dWalls.isEmpty()) {
                if (logWriter != null) {
                    logWriter.println("  Ajout d'un mur par défaut (5 m × 2 m × 0,2 m)...");
                    logWriter.flush();
                }
                Object defaultWall = createWallAtOrigin(5.0, 2.0, 0.2, foundation, logWriter);
                if (defaultWall != null) {
                    java.lang.reflect.Method getChildrenMethod = foundationClass.getMethod("getChildren");
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> children = (java.util.List<Object>) getChildrenMethod.invoke(foundation);
                    children.add(defaultWall);
                    wallCount = 1;
                    if (logWriter != null) { logWriter.println("  ✓ Mur par défaut ajouté"); logWriter.flush(); }
                } else if (logWriter != null) {
                    logWriter.println("  ⚠ Impossible de créer le mur par défaut"); logWriter.flush();
                }
            }
            
            logWriter.println("✓ " + wallCount + " murs convertis");
            logWriter.flush();
            
            // Dessiner la foundation
            try {
                java.lang.reflect.Method drawMethod = foundationClass.getMethod("draw");
                drawMethod.invoke(foundation);
            } catch (Exception e) {
                logWriter.println("AVERTISSEMENT lors du dessin: " + e.getMessage());
            }
            
            // Ajouter la foundation à la Scene (comme Energy3D le fait)
            logWriter.println("Ajout de la Foundation à la Scene...");
            logWriter.flush();
            
            Class<?> housePartClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.HousePart", logWriter);
            java.lang.reflect.Method addMethod = sceneClass.getMethod("add", housePartClass, boolean.class);
            addMethod.invoke(scene, foundation, false);
            
            logWriter.println("✓ Foundation ajoutée à la Scene");
            logWriter.flush();
            
            ensureSceneAnnotationScale(sceneClass, scene, 0.2, logWriter);
            
            // Caméra (comme plan_energy.ng3) : définie juste avant sérialisation pour être bien persistée
            setExportedSceneCamera(sceneClass, scene, logWriter, 14.69, -139.37, 41.82);
            setSceneCameraFieldsByReflection(sceneClass, scene, 14.69, -139.37, 41.82, logWriter);
            
            // Sérialiser la Scene exactement comme Energy3D le fait
            logWriter.println("Sérialisation de la Scene vers: " + outputFile.getAbsolutePath());
            logWriter.println("Vérifications pré-sérialisation:");
            logWriter.println("  Fichier existe: " + outputFile.exists());
            logWriter.println("  Répertoire parent existe: " + (outputFile.getParentFile() != null ? outputFile.getParentFile().exists() : "N/A"));
            logWriter.println("  Répertoire parent peut écrire: " + (outputFile.getParentFile() != null ? outputFile.getParentFile().canWrite() : "N/A"));
            logWriter.flush();
            
            // Supprimer le fichier existant s'il existe
            if (outputFile.exists()) {
                logWriter.println("Suppression du fichier existant...");
                logWriter.flush();
                boolean deleted = outputFile.delete();
                logWriter.println("  Fichier supprimé: " + deleted);
                logWriter.flush();
            }
            
            // Même méthode d'enregistrement qu'Energy3D (Scene.realSave) : ObjectOutputStream.writeObject(instance).
            // On ne peut pas appeler Scene.save(url) depuis le plugin car nous sommes dans Sweet Home 3D, pas Energy3D :
            // realSave() utilise SceneManager.getCamera(), EnergyPanel.getInstance(), etc. qui n'existent pas ici.
            FileOutputStream fos = null;
            ObjectOutputStream out = null;
            try {
                logWriter.println("Création du FileOutputStream...");
                logWriter.flush();
                fos = new FileOutputStream(outputFile, false);
                logWriter.println("✓ FileOutputStream créé");
                logWriter.flush();
                
                logWriter.println("Création de l'ObjectOutputStream...");
                logWriter.flush();
                out = new ObjectOutputStream(fos);
                logWriter.println("✓ ObjectOutputStream créé");
                logWriter.flush();
                
                logWriter.println("Écriture de la Scene...");
                logWriter.flush();
                out.writeObject(scene);
                logWriter.println("✓ Scene écrite");
                logWriter.flush();
                
                logWriter.println("Flush des streams...");
                logWriter.flush();
                out.flush();
                fos.getFD().sync();
                logWriter.println("✓ Streams flushed");
                logWriter.flush();
                
                logWriter.println("Fermeture des streams...");
                logWriter.flush();
                out.close();
                fos.close();
                logWriter.println("✓ Streams fermés");
                logWriter.flush();
                
                logWriter.println("✓ Sérialisation terminée");
                logWriter.flush();
            } catch (Exception e) {
                logWriter.println("✗ ERREUR lors de la sérialisation: " + e.getMessage());
                logWriter.println("Type d'exception: " + e.getClass().getName());
                e.printStackTrace(logWriter);
                logWriter.flush();
                if (out != null) {
                    try { out.close(); } catch (Exception ex) {}
                }
                if (fos != null) {
                    try { fos.close(); } catch (Exception ex) {}
                }
                return false;
            }
            
            // Vérifier le fichier
            logWriter.println("Vérification du fichier créé...");
            logWriter.println("  Fichier existe: " + outputFile.exists());
            if (outputFile.exists()) {
                logWriter.println("  Taille: " + outputFile.length() + " bytes");
            }
            logWriter.flush();
            
            if (outputFile.exists() && outputFile.length() > 0) {
                // Vérifier le header binaire Java (AC ED = magic number)
                try (java.io.FileInputStream fis = new java.io.FileInputStream(outputFile)) {
                    byte[] header = new byte[4];
                    int read = fis.read(header);
                    if (read == 4 && header[0] == (byte)0xAC && header[1] == (byte)0xED) {
                        String headerStr = String.format("%02X %02X %02X %02X", header[0], header[1], header[2], header[3]);
                        logWriter.println("✓ Fichier .ng3 valide créé: " + outputFile.length() + " bytes");
                        logWriter.println("Header: " + headerStr);
                        logWriter.println("=== EXPORT REUSSI ===");
                        logWriter.flush();
                        return true;
                    } else {
                        String headerStr = read >= 1 ? String.format("%02X", header[0]) : "vide";
                        logWriter.println("✗ ERREUR: Fichier créé mais header invalide");
                        logWriter.println("  Bytes lus: " + read);
                        logWriter.println("  Premier byte: " + headerStr);
                        logWriter.println("=== EXPORT ECHOUE ===");
                        logWriter.flush();
                        return false;
                    }
                } catch (Exception e) {
                    logWriter.println("✗ ERREUR lors de la vérification du fichier: " + e.getMessage());
                    e.printStackTrace(logWriter);
                    logWriter.println("=== EXPORT ECHOUE ===");
                    logWriter.flush();
                    return false;
                }
            } else {
                logWriter.println("✗ ERREUR: Fichier non créé ou vide");
                logWriter.println("  Existe: " + outputFile.exists());
                if (outputFile.exists()) {
                    logWriter.println("  Taille: " + outputFile.length() + " bytes");
                }
                logWriter.println("=== EXPORT ECHOUE ===");
                logWriter.flush();
                return false;
            }
            
        } catch (Throwable t) {
            // Créer le log même en cas d'exception ou Error (LinkageError, NoSuchMethodError, etc.)
            if (logWriter == null) {
                try {
                    if (logFile.getParentFile() != null) {
                        logFile.getParentFile().mkdirs();
                    }
                    logWriter = new PrintWriter(new java.io.FileWriter(logFile, true));
                } catch (Exception logEx) {
                    t.printStackTrace();
                    return false;
                }
            }
            
            logWriter.println("\n=== EXCEPTION/ERROR LORS DE L'EXPORT ===");
            logWriter.println("Type: " + t.getClass().getName());
            logWriter.println("Message: " + t.getMessage());
            t.printStackTrace(logWriter);
            logWriter.flush();
            logWriter.close();
            
            t.printStackTrace();
            return false;
        } finally {
            if (logWriter != null) {
                try {
                    logWriter.close();
                } catch (Exception e) {
                    // Ignorer
                }
            }
        }
    }
    
    public static boolean exportSh3dFileToNg3(File sh3dFile, File outputFile) {
        java.io.InputStream in = null;
        try {
            in = new java.io.BufferedInputStream(new java.io.FileInputStream(sh3dFile));
            com.eteks.sweethome3d.io.DefaultHomeInputStream dhis = new com.eteks.sweethome3d.io.DefaultHomeInputStream(in);
            Home home = dhis.readHome();
            boolean ok = exportToEnergy3D(home, outputFile);
            if (!ok) {
                File logFile = new File(outputFile.getParentFile(), outputFile.getName() + ".log");
                try (java.io.PrintWriter logWriter = new java.io.PrintWriter(new java.io.FileWriter(logFile, true))) {
                    logWriter.println("INFO: exportToEnergy3D a échoué, création d'un .ng3 vide en repli");
                    logWriter.flush();
                } catch (Exception ignore) {}
                return exportEmptyNg3(outputFile);
            }
            return true;
        } catch (Throwable e) {
            try {
                File logFile = new File(outputFile.getParentFile(), outputFile.getName() + ".log");
                java.io.PrintWriter logWriter = new java.io.PrintWriter(new java.io.FileWriter(logFile, true));
                logWriter.println("✗ ERREUR export complet: " + e.getMessage());
                logWriter.println("Type: " + e.getClass().getName());
                e.printStackTrace(logWriter);
                logWriter.println("→ Bascule vers création d'un projet .ng3 vide");
                logWriter.flush();
                logWriter.close();
            } catch (Exception ignore) {}
            return exportEmptyNg3(outputFile);
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception e) {}
            }
        }
    }
    
    public static boolean exportEmptyNg3WithFloor(double widthMeters, double heightMeters, File outputFile) {
        File logFile = new File(outputFile.getParentFile(), outputFile.getName() + ".log");
        java.io.PrintWriter logWriter = null;
        try {
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            if (logFile.getParentFile() != null) {
                logFile.getParentFile().mkdirs();
            }
            try {
                logWriter = new PrintWriter(new java.io.FileWriter(logFile, true));
                logWriter.println("\n=== DEBUT EXPORT NG3 AVEC SOL ===");
                logWriter.println("Dimensions: " + widthMeters + "m x " + heightMeters + "m");
                logWriter.println("Fichier: " + outputFile.getAbsolutePath());
                logWriter.flush();
            } catch (Exception e) {
            }
            
            ClassLoader energy3dLoader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
            if (energy3dLoader == null) {
                if (logWriter != null) {
                    logWriter.println("✗ ERREUR: ClassLoader Energy3D non disponible");
                    logWriter.flush();
                }
                return false;
            }
            
            Class<?> sceneClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.scene.Scene", logWriter);
            Object scene = createNewSceneInstance(logWriter);
            if (scene == null) {
                if (logWriter != null) {
                    logWriter.println("✗ ERREUR: Scene non créée, repli vers ng3 vide");
                    logWriter.flush();
                }
                return exportEmptyNg3(outputFile);
            }
            ensureSceneInstance(sceneClass, scene, logWriter);
            
            Object foundation = createSizedFoundation(widthMeters, heightMeters, logWriter);
            if (foundation == null) {
                if (logWriter != null) {
                    logWriter.println("✗ ERREUR: Foundation non créée, repli vers ng3 vide");
                    logWriter.flush();
                }
                return exportEmptyNg3(outputFile);
            }
            
            // Mur 5m (longueur) x 2m (hauteur), même échelle 0.2 que l'export plan (zoom et pas cohérents)
            Object wall = createWallAtOrigin(5.0, 2.0, 0.2, foundation, logWriter);
            if (wall != null) {
                try {
                    java.lang.reflect.Method getChildrenMethod = foundation.getClass().getMethod("getChildren");
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> children = (java.util.List<Object>) getChildrenMethod.invoke(foundation);
                    children.add(wall);
                    if (logWriter != null) {
                        logWriter.println("✓ Mur 5m x 2m ajouté à la fondation (origine)");
                        logWriter.flush();
                    }
                } catch (Exception e) {
                    if (logWriter != null) {
                        logWriter.println("⚠ Mur non ajouté aux enfants: " + e.getMessage());
                        logWriter.flush();
                    }
                }
            }
            
            try {
                Class<?> housePartClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.HousePart", logWriter);
                java.lang.reflect.Method addMethod = sceneClass.getMethod("add", housePartClass, boolean.class);
                addMethod.invoke(scene, foundation, Boolean.FALSE);
                if (logWriter != null) {
                    logWriter.println("✓ Foundation ajoutée via Scene.add(..., false)");
                    logWriter.flush();
                }
                java.lang.reflect.Method getPartsMethod = sceneClass.getMethod("getParts");
                @SuppressWarnings("unchecked")
                java.util.List<Object> parts = (java.util.List<Object>) getPartsMethod.invoke(scene);
                if (logWriter != null) {
                    logWriter.println("✓ Vérification parts avant sérialisation: " + (parts != null ? parts.size() : -1));
                    logWriter.flush();
                }
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("✗ ERREUR: ajout Foundation à Scene: " + e.getMessage());
                    logWriter.flush();
                }
                return exportEmptyNg3(outputFile);
            }
            
            // Caméra pour fondation 10×10 m (50×50 unités scale 0.2)
            setExportedSceneCamera(sceneClass, scene, logWriter, 0, -40, 10);
            ensureSceneAnnotationScale(sceneClass, scene, 0.2, logWriter);
            setSceneCameraFieldsByReflection(sceneClass, scene, 0, -40, 10, logWriter);
            
            boolean ok = serializeSceneToNG3(scene, outputFile, logWriter);
            if (logWriter != null) {
                logWriter.println(ok ? "=== EXPORT SOL REUSSI ===" : "=== EXPORT SOL ECHOUE ===");
                logWriter.flush();
            }
            return ok;
        } catch (Throwable t) {
            if (logWriter != null) {
                logWriter.println("✗ ERREUR: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getName()));
                Throwable cause = t.getCause();
                if (cause != null) {
                    logWriter.println("  Cause: " + cause.getClass().getName() + ": " + cause.getMessage());
                    cause.printStackTrace(new java.io.PrintWriter(logWriter, true));
                }
                t.printStackTrace(new java.io.PrintWriter(logWriter, true));
                logWriter.flush();
            }
            return exportEmptyNg3(outputFile);
        } finally {
            if (logWriter != null) {
                try { logWriter.close(); } catch (Exception e) {}
            }
        }
    }
    
    /**
     * Recrée la scène de départ d'Energy3D (Scene.newFile(true)) : Human(0,1) + Foundation(80,60),
     * annotationScale 0.2, caméra adaptée. Permet d'exporter un .ng3 identique à la scène vide au lancement.
     */
    private static boolean populateSceneAsEnergy3DDefault(Class<?> sceneClass, Object scene, PrintWriter logWriter) {
        try {
            ClassLoader loader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
            Class<?> housePartClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.HousePart", logWriter);
            java.lang.reflect.Method addMethod = sceneClass.getMethod("add", housePartClass, boolean.class);

            // 1. Human(0, 1) comme Energy3D newFile(true)
            try {
                Class<?> humanClass = loader.loadClass("org.concord.energy3d.model.Human");
                java.lang.reflect.Constructor<?> humanCtor = humanClass.getConstructor(int.class, double.class);
                Object human = humanCtor.newInstance(0, 1.0);
                addMethod.invoke(scene, human, Boolean.FALSE);
                if (logWriter != null) {
                    logWriter.println("✓ Human(0, 1) ajouté (scène de départ Energy3D)");
                    logWriter.flush();
                }
            } catch (Throwable t) {
                if (logWriter != null) {
                    logWriter.println("⚠ Human non ajouté (optionnel): " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
                    logWriter.flush();
                }
            }

            // 2. Foundation(80, 60) en unités Energy3D = 16 m x 12 m avec scale 0.2
            Class<?> foundationClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Foundation", logWriter);
            java.lang.reflect.Constructor<?> foundCtor = foundationClass.getConstructor(double.class, double.class);
            Object foundation = foundCtor.newInstance(80.0, 60.0);
            // Couleur par défaut fondation (180, 180, 180) comme getDefaultFoundationColor()
            try {
                Class<?> colorClass = loader.loadClass("com.ardor3d.math.ColorRGBA");
                Object defaultColor = colorClass.getConstructor(float.class, float.class, float.class, float.class)
                    .newInstance(180f / 255f, 180f / 255f, 180f / 255f, 1f);
                foundationClass.getMethod("setColor", loader.loadClass("com.ardor3d.math.type.ReadOnlyColorRGBA")).invoke(foundation, defaultColor);
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("⚠ Foundation.setColor non appliqué: " + e.getMessage());
                    logWriter.flush();
                }
            }
            addMethod.invoke(scene, foundation, Boolean.FALSE);
            if (logWriter != null) {
                logWriter.println("✓ Foundation(80, 60) ajoutée (16 m x 12 m, scale 0.2)");
                logWriter.flush();
            }
            return true;
        } catch (Throwable t) {
            if (logWriter != null) {
                logWriter.println("✗ populateSceneAsEnergy3DDefault: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getName()));
                t.printStackTrace(logWriter);
                logWriter.flush();
            }
            return false;
        }
    }

    public static boolean exportEmptyNg3(File outputFile) {
        File logFile = new File(outputFile.getParentFile(), outputFile.getName() + ".log");
        PrintWriter logWriter = null;
        try {
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            if (logFile.getParentFile() != null) {
                logFile.getParentFile().mkdirs();
            }
            try {
                logWriter = new PrintWriter(new java.io.FileWriter(logFile, true));
                logWriter.println("\n=== DEBUT EXPORT NG3 VIDE (scène de départ Energy3D) ===");
                logWriter.println("Fichier de sortie: " + outputFile.getAbsolutePath());
                logWriter.println("Timestamp: " + new java.util.Date());
                logWriter.flush();
            } catch (Exception e) {
            }
            ClassLoader energy3dLoader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
            if (energy3dLoader == null) {
                if (logWriter != null) {
                    logWriter.println("✗ ERREUR: ClassLoader Energy3D non disponible");
                    logWriter.flush();
                }
                return false;
            }
            Class<?> sceneClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.scene.Scene", logWriter);
            if (sceneClass == null) {
                if (logWriter != null) {
                    logWriter.println("✗ ERREUR: Classe Scene non trouvée");
                    logWriter.flush();
                }
                return false;
            }
            java.lang.reflect.Constructor<?> ctor = sceneClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object scene = ctor.newInstance();
            try {
                java.lang.reflect.Field projectNameField = sceneClass.getDeclaredField("projectName");
                projectNameField.setAccessible(true);
                projectNameField.set(scene, "Projet vide");
            } catch (Exception ignore) {
            }
            ensureSceneInstance(sceneClass, scene, logWriter);
            // Scène de départ Energy3D : annotationScale 0.2 (défaut Scene), Human + Foundation(80,60)
            ensureSceneAnnotationScale(sceneClass, scene, 0.2, logWriter);
            populateSceneAsEnergy3DDefault(sceneClass, scene, logWriter);
            // Caméra pour 16 m x 12 m (scale 0.2) : camY=-40, camZ=10 unités ≈ 8 m derrière, 2 m hauteur
            setExportedSceneCamera(sceneClass, scene, logWriter, -40, 10);
            setSceneCameraFieldsByReflection(sceneClass, scene, 0, -40, 10, logWriter);
            if (outputFile.exists()) {
                outputFile.delete();
            }
            FileOutputStream fos = null;
            ObjectOutputStream out = null;
            try {
                fos = new FileOutputStream(outputFile, false);
                out = new ObjectOutputStream(fos);
                out.writeObject(scene);
                out.flush();
                fos.getFD().sync();
                out.close();
                fos.close();
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("✗ ERREUR sérialisation: " + e.getMessage());
                    logWriter.println("Type: " + e.getClass().getName());
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
                try { if (out != null) out.close(); } catch (Exception ex) {}
                try { if (fos != null) fos.close(); } catch (Exception ex) {}
                return false;
            }
            if (logWriter != null) {
                logWriter.println("Vérification du fichier créé...");
                logWriter.println("  Fichier existe: " + outputFile.exists());
                if (outputFile.exists()) {
                    logWriter.println("  Taille: " + outputFile.length() + " bytes");
                }
                try (java.io.FileInputStream fis = new java.io.FileInputStream(outputFile)) {
                    byte[] header = new byte[4];
                    int read = fis.read(header);
                    if (read == 4 && header[0] == (byte)0xAC && header[1] == (byte)0xED) {
                        String headerStr = String.format("%02X %02X %02X %02X", header[0], header[1], header[2], header[3]);
                        logWriter.println("✓ Fichier .ng3 vide valide");
                        logWriter.println("Header: " + headerStr);
                        logWriter.println("=== EXPORT VIDE REUSSI ===");
                        logWriter.flush();
                        return true;
                    } else {
                        logWriter.println("✗ Header invalide");
                        logWriter.flush();
                        return false;
                    }
                } catch (Exception e) {
                    logWriter.println("✗ ERREUR vérification: " + e.getMessage());
                    logWriter.flush();
                    return false;
                }
            }
            return outputFile.exists() && outputFile.length() > 0;
        } catch (Throwable t) {
            if (logWriter != null) {
                logWriter.println("✗ ERREUR: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getName()));
                Throwable cause = t.getCause();
                if (cause != null) {
                    logWriter.println("  Cause: " + cause.getClass().getName() + ": " + cause.getMessage());
                    cause.printStackTrace(new java.io.PrintWriter(logWriter, true));
                }
                t.printStackTrace(new java.io.PrintWriter(logWriter, true));
                logWriter.flush();
            }
            return false;
        } finally {
            if (logWriter != null) {
                try { logWriter.close(); } catch (Exception e) {}
            }
        }
    }
    
    private static void ensureSceneInstance(Class<?> sceneClass, Object sceneInstance, PrintWriter logWriter) {
        try {
            java.lang.reflect.Field instanceField = sceneClass.getDeclaredField("instance");
            instanceField.setAccessible(true);
            Object existing = instanceField.get(null);
            if (existing == null) {
                instanceField.set(null, sceneInstance);
                if (logWriter != null) {
                    logWriter.println("✓ Scene.instance initialisée (mode headless)");
                    logWriter.flush();
                }
            } else if (logWriter != null) {
                logWriter.println("INFO: Scene.instance déjà défini: " + existing.getClass().getName());
                logWriter.flush();
            }
            // Aligner sur plan_energy.ng3 (Energy3D) : annotationScale 0.2, 1 m = 5 unités (grille 1 m)
            try {
                java.lang.reflect.Field scaleField = sceneClass.getDeclaredField("annotationScale");
                scaleField.setAccessible(true);
                scaleField.set(sceneInstance, 0.2);
                if (logWriter != null) {
                    logWriter.println("✓ Scene.annotationScale = 0.2 (comme Energy3D, 1 m = 5 unités)");
                    logWriter.flush();
                }
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("⚠ annotationScale non défini: " + e.getMessage());
                    logWriter.flush();
                }
            }
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("⚠ AVERTISSEMENT: Impossible de forcer Scene.instance: " + e.getMessage());
                logWriter.flush();
            }
        }
    }
    
    /**
     * Réapplique annotationScale sur la Scene juste avant sérialisation
     * (au cas où une classe Energy3D l'aurait modifié lors de la création foundation/mur).
     */
    private static void ensureSceneAnnotationScale(Class<?> sceneClass, Object scene, double scale, PrintWriter logWriter) {
        try {
            java.lang.reflect.Field scaleField = sceneClass.getDeclaredField("annotationScale");
            scaleField.setAccessible(true);
            scaleField.set(scene, scale);
            if (logWriter != null) {
                logWriter.println("✓ Scene.annotationScale réappliqué = " + scale + " avant sérialisation");
                logWriter.flush();
            }
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("⚠ ensureSceneAnnotationScale: " + e.getMessage());
                logWriter.flush();
            }
        }
    }
    
    /**
     * Définit la position et la direction de la caméra sur la Scene exportée
     * pour que l'ouverture du .ng3 dans Energy3D affiche un zoom correct (vue rapprochée).
     * Sans cela, Energy3D utilise la valeur par défaut (0, -100, 25) et la scène paraît très loin.
     * @param camY distance caméra (négatif = derrière), camZ hauteur
     */
    private static void setExportedSceneCamera(Class<?> sceneClass, Object scene, PrintWriter logWriter, double camX, double camY, double camZ) {
        try {
            ClassLoader loader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
            Class<?> vector3Class = loader.loadClass("com.ardor3d.math.Vector3");
            Object loc = vector3Class.getConstructor(double.class, double.class, double.class).newInstance(camX, camY, camZ);
            // Direction = vecteur normalisé de la caméra vers le centre
            double dx = -camX, dy = -camY, dz = -camZ;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 1e-6) {
                dx /= len; dy /= len; dz /= len;
            }
            Object dir = vector3Class.getConstructor(double.class, double.class, double.class).newInstance(dx, dy, dz);
            sceneClass.getMethod("setCameraLocation", loader.loadClass("com.ardor3d.math.ReadOnlyVector3")).invoke(scene, loc);
            sceneClass.getMethod("setCameraDirection", loader.loadClass("com.ardor3d.math.ReadOnlyVector3")).invoke(scene, dir);
            if (logWriter != null) {
                logWriter.println("✓ Scene: caméra position (" + camX + ", " + camY + ", " + camZ + "), direction vers centre");
                logWriter.flush();
            }
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("⚠ Caméra non définie (zoom par défaut à l'ouverture): " + e.getMessage());
                logWriter.flush();
            }
        }
    }

    private static void setExportedSceneCamera(Class<?> sceneClass, Object scene, PrintWriter logWriter, double camY, double camZ) {
        setExportedSceneCamera(sceneClass, scene, logWriter, 0, camY, camZ);
    }
    
    /** Caméra par défaut (scale 0.2, comme plan_energy.ng3). */
    private static void setExportedSceneCamera(Class<?> sceneClass, Object scene, PrintWriter logWriter) {
        setExportedSceneCamera(sceneClass, scene, logWriter, 14.69, -139.37, 41.82);
    }
    
    /**
     * Définit cameraLocation et cameraDirection directement sur la Scene par réflexion
     * (champs sérialisés par defaultWriteObject), pour garantir leur persistance dans le .ng3.
     */
    private static void setSceneCameraFieldsByReflection(Class<?> sceneClass, Object scene,
            double camX, double camY, double camZ, PrintWriter logWriter) {
        try {
            ClassLoader loader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
            Class<?> vector3Class = loader.loadClass("com.ardor3d.math.Vector3");
            Object loc = vector3Class.getConstructor(double.class, double.class, double.class).newInstance(camX, camY, camZ);
            double dx = -camX, dy = -camY, dz = -camZ;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 1e-6) { dx /= len; dy /= len; dz /= len; }
            Object dir = vector3Class.getConstructor(double.class, double.class, double.class).newInstance(dx, dy, dz);
            java.lang.reflect.Field locField = sceneClass.getDeclaredField("cameraLocation");
            java.lang.reflect.Field dirField = sceneClass.getDeclaredField("cameraDirection");
            locField.setAccessible(true);
            dirField.setAccessible(true);
            locField.set(scene, loc);
            dirField.set(scene, dir);
            if (logWriter != null) {
                logWriter.println("✓ Scene.cameraLocation / cameraDirection fixés par réflexion (sérialisation)");
                logWriter.flush();
            }
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("⚠ setSceneCameraFieldsByReflection: " + e.getMessage());
                logWriter.flush();
            }
        }
    }

    private static Object createDefaultFoundation(PrintWriter logWriter) {
        try {
            // Précharger toutes les classes Ardor3D nécessaires AVANT de charger Foundation
            // HousePart a un champ statique offsetState = new OffsetState() qui doit être résolu
            ClassLoader energy3dLoader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
            
            // Précharger RenderState, OffsetState, OffsetType
            try {
                energy3dLoader.loadClass("com.ardor3d.renderer.state.RenderState");
                energy3dLoader.loadClass("com.ardor3d.renderer.state.OffsetState");
                energy3dLoader.loadClass("com.ardor3d.renderer.state.OffsetState$OffsetType");
                try {
                    energy3dLoader.loadClass("com.ardor3d.renderer.state.LightState");
                    if (logWriter != null) {
                        logWriter.println("  ✓ LightState préchargé");
                        logWriter.flush();
                    }
                } catch (Exception e) {
                    if (logWriter != null) {
                        logWriter.println("  ⚠ AVERTISSEMENT: LightState non préchargé: " + e.getMessage());
                        logWriter.flush();
                    }
                }
                // Précharger ImageLoader compat (appelé pendant Scene.openNow)
                try {
                    Class<?> imageLoaderClass = energy3dLoader.loadClass("com.ardor3d.image.util.ImageLoader");
                    if (logWriter != null) {
                        logWriter.println("  ✓ ImageLoader (compat) préchargé: " + imageLoaderClass.getName());
                        logWriter.println("    ClassLoader: " + imageLoaderClass.getClassLoader().getClass().getName());
                        logWriter.flush();
                    }
                } catch (Exception e) {
                    if (logWriter != null) {
                        logWriter.println("  ⚠ AVERTISSEMENT: ImageLoader (compat) non préchargé: " + e.getMessage());
                        logWriter.flush();
                    }
                }
                if (logWriter != null) {
                    logWriter.println("  ✓ Classes Ardor3D critiques préchargées (RenderState, OffsetState, OffsetType)");
                    logWriter.flush();
                }
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("  ✗ ERREUR: Classes Ardor3D non préchargées: " + e.getMessage());
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
                return null;
            }
            
            // PRÉCHARGER MaterialState AVANT de charger Foundation
            // Foundation peut avoir besoin de MaterialState lors de son chargement
            if (logWriter != null) {
                logWriter.println("  Préchargement de MaterialState avant Foundation...");
                logWriter.flush();
            }
            try {
                Class<?> materialStateClass = energy3dLoader.loadClass("com.ardor3d.renderer.state.MaterialState");
                if (logWriter != null) {
                    logWriter.println("  ✓ MaterialState préchargé avec succès");
                    Class<?> superClass = materialStateClass.getSuperclass();
                    String materialLoader = materialStateClass.getClassLoader() != null
                        ? materialStateClass.getClassLoader().getClass().getName()
                        : "bootstrap";
                    String superLoader = (superClass != null && superClass.getClassLoader() != null)
                        ? superClass.getClassLoader().getClass().getName()
                        : "bootstrap";
                    Class<?> renderStateCheck = energy3dLoader.loadClass("com.ardor3d.renderer.state.RenderState");
                    logWriter.println("    MaterialState ClassLoader: " + materialLoader);
                    logWriter.println("    Superclass: " + (superClass != null ? superClass.getName() : "null"));
                    logWriter.println("    SuperClassLoader: " + superLoader);
                    logWriter.println("    RenderState == Superclass: " + (renderStateCheck == superClass));
                    logWriter.flush();
                }
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("  ⚠ AVERTISSEMENT: MaterialState non préchargé: " + e.getMessage());
                    logWriter.println("    Type: " + e.getClass().getName());
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
            }
            
            // Charger Foundation avec le même ClassLoader
            Class<?> foundationClass = null;
            try {
                foundationClass = energy3dLoader.loadClass("org.concord.energy3d.model.Foundation");
                if (logWriter != null) {
                    logWriter.println("  ✓ Foundation chargée: " + foundationClass.getName());
                    logWriter.println("  ClassLoader de Foundation: " + foundationClass.getClassLoader().getClass().getName());
                    logWriter.flush();
                }
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("  ✗ ERREUR: Foundation non chargée: " + e.getMessage());
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
                return null;
            }
            
            // Forcer la résolution de toutes les classes nécessaires avant l'instanciation
            // HousePart importe RenderState.StateType, donc RenderState doit être résolu
            if (logWriter != null) {
                logWriter.println("  Résolution des dépendances avant instanciation...");
                logWriter.flush();
            }
            
            // Précharger toutes les classes Ardor3D utilisées par HousePart
            try {
                energy3dLoader.loadClass("com.ardor3d.renderer.state.OffsetState");
                energy3dLoader.loadClass("com.ardor3d.renderer.state.BlendState");
                energy3dLoader.loadClass("com.ardor3d.renderer.state.TextureState");
                // S'assurer que RenderState.StateType est accessible
                Class<?> renderStateClass2 = energy3dLoader.loadClass("com.ardor3d.renderer.state.RenderState");
                // Accéder à StateType pour forcer sa résolution
                Class<?>[] innerClasses = renderStateClass2.getDeclaredClasses();
                for (Class<?> innerClass : innerClasses) {
                    if (innerClass.getSimpleName().equals("StateType")) {
                        if (logWriter != null) {
                            logWriter.println("  ✓ StateType trouvé et résolu");
                            logWriter.flush();
                        }
                        break;
                    }
                }
                // Précharger BloomRenderPass (utilisé par Foundation ou HousePart)
                try {
                    energy3dLoader.loadClass("com.ardor3d.extension.effect.bloom.BloomRenderPass");
                    if (logWriter != null) {
                        logWriter.println("  ✓ BloomRenderPass préchargé");
                        logWriter.flush();
                    }
                } catch (Exception e2) {
                    if (logWriter != null) {
                        logWriter.println("  ⚠ AVERTISSEMENT: BloomRenderPass non préchargé: " + e2.getMessage());
                        logWriter.flush();
                    }
                }
                if (logWriter != null) {
                    logWriter.println("  ✓ Toutes les dépendances résolues");
                    logWriter.flush();
                }
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("  AVERTISSEMENT lors de la résolution des dépendances: " + e.getMessage());
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
            }
            
            if (logWriter != null) {
                logWriter.println("  Tentative d'instanciation de Foundation...");
                logWriter.println("  ClassLoader utilisé: " + foundationClass.getClassLoader().getClass().getName());
                logWriter.println("  Foundation class: " + foundationClass.getName());
                logWriter.flush();
            }
            System.out.println("DEBUG: Tentative d'instanciation de Foundation...");
            System.out.println("DEBUG: Foundation class: " + foundationClass.getName());
            
            Object foundation = null;
            try {
                if (logWriter != null) {
                    logWriter.println("  Appel de foundationClass.newInstance()...");
                    logWriter.flush();
                }
                System.out.println("DEBUG: Appel de foundationClass.newInstance()...");
                foundation = foundationClass.newInstance();
                if (logWriter != null) {
                    logWriter.println("  ✓ newInstance() retourné sans exception");
                    logWriter.flush();
                }
                System.out.println("DEBUG: newInstance() retourné sans exception");
                if (logWriter != null) {
                    logWriter.println("  ✓ Foundation instanciée avec succès");
                    logWriter.flush();
                }
                System.out.println("DEBUG: Foundation instanciée avec succès");
            } catch (NoClassDefFoundError e) {
                if (logWriter != null) {
                    logWriter.println("  ✗ ERREUR NoClassDefFoundError lors de l'instanciation!");
                    logWriter.println("  Message: " + e.getMessage());
                    logWriter.println("  Cause: " + (e.getCause() != null ? e.getCause().getMessage() : "null"));
                    logWriter.println("  Stack trace complète:");
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
                System.err.println("ERREUR NoClassDefFoundError: " + e.getMessage());
                e.printStackTrace();
                return null;
            } catch (LinkageError e) {
                if (logWriter != null) {
                    logWriter.println("  ✗ ERREUR LinkageError lors de l'instanciation!");
                    logWriter.println("  Message: " + e.getMessage());
                    logWriter.println("  Type: " + e.getClass().getName());
                    logWriter.println("  Stack trace complète:");
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
                System.err.println("ERREUR LinkageError: " + e.getMessage());
                e.printStackTrace();
                return null;
            } catch (Throwable e) {
                if (logWriter != null) {
                    logWriter.println("  ✗ ERREUR lors de l'instanciation!");
                    logWriter.println("  Type: " + e.getClass().getName());
                    logWriter.println("  Message: " + e.getMessage());
                    logWriter.println("  Stack trace complète:");
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
                System.err.println("ERREUR: " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
                return null;
            }
            
            // Créer une foundation rectangulaire par défaut de 10x10 mètres (1000x1000 cm)
            java.lang.reflect.Method addPointMethod = foundationClass.getMethod("addPoint", int.class, int.class);
            addPointMethod.invoke(foundation, -500, -500);  // -5m en cm
            addPointMethod.invoke(foundation, 500, -500);   // 5m en cm
            addPointMethod.invoke(foundation, 500, 500);    // 5m en cm
            addPointMethod.invoke(foundation, -500, 500);   // -5m en cm
            
            java.lang.reflect.Method completeMethod = foundationClass.getMethod("complete");
            completeMethod.invoke(foundation);
            
            return foundation;
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("Exception lors de la création de la Foundation par défaut: " + e.getMessage());
                e.printStackTrace(logWriter);
            }
            e.printStackTrace();
            return null;
        }
    }
    
    private static Object createSizedFoundation(double widthMeters, double heightMeters, PrintWriter logWriter) {
        try {
            ClassLoader energy3dLoader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
            
            // Précharger les classes Ardor3D critiques avant Foundation (évite NoClassDefFoundError dans les initialisations statiques)
            if (logWriter != null) {
                logWriter.println("  Préchargement des classes Ardor3D nécessaires pour Foundation (RenderState/OffsetState/OffsetType/LightState/ImageLoader/MaterialState)...");
                logWriter.flush();
            }
            try {
                energy3dLoader.loadClass("com.ardor3d.renderer.state.RenderState");
                energy3dLoader.loadClass("com.ardor3d.renderer.state.OffsetState");
                energy3dLoader.loadClass("com.ardor3d.renderer.state.OffsetState$OffsetType");
                try {
                    energy3dLoader.loadClass("com.ardor3d.renderer.state.LightState");
                    if (logWriter != null) {
                        logWriter.println("    ✓ LightState préchargé");
                        logWriter.flush();
                    }
                } catch (Exception e) {
                    if (logWriter != null) {
                        logWriter.println("    ⚠ AVERTISSEMENT: LightState non préchargé: " + e.getMessage());
                        logWriter.flush();
                    }
                }
                try {
                    Class<?> imageLoaderClass = energy3dLoader.loadClass("com.ardor3d.image.util.ImageLoader");
                    if (logWriter != null) {
                        logWriter.println("    ✓ ImageLoader (compat) préchargé: " + imageLoaderClass.getName());
                        logWriter.println("      ClassLoader: " + imageLoaderClass.getClassLoader().getClass().getName());
                        logWriter.flush();
                    }
                } catch (Exception e) {
                    if (logWriter != null) {
                        logWriter.println("    ⚠ AVERTISSEMENT: ImageLoader (compat) non préchargé: " + e.getMessage());
                        logWriter.flush();
                    }
                }
                try {
                    Class<?> materialStateClass = energy3dLoader.loadClass("com.ardor3d.renderer.state.MaterialState");
                    if (logWriter != null) {
                        Class<?> superClass = materialStateClass.getSuperclass();
                        String materialLoader = materialStateClass.getClassLoader() != null
                            ? materialStateClass.getClassLoader().getClass().getName()
                            : "bootstrap";
                        String superLoader = (superClass != null && superClass.getClassLoader() != null)
                            ? superClass.getClassLoader().getClass().getName()
                            : "bootstrap";
                        Class<?> renderStateCheck = energy3dLoader.loadClass("com.ardor3d.renderer.state.RenderState");
                        logWriter.println("    ✓ MaterialState préchargé");
                        logWriter.println("      MaterialState ClassLoader: " + materialLoader);
                        logWriter.println("      Superclass: " + (superClass != null ? superClass.getName() : "null"));
                        logWriter.println("      SuperClassLoader: " + superLoader);
                        logWriter.println("      RenderState == Superclass: " + (renderStateCheck == superClass));
                        logWriter.flush();
                    }
                } catch (Exception e) {
                    if (logWriter != null) {
                        logWriter.println("    ⚠ AVERTISSEMENT: MaterialState non préchargé: " + e.getMessage());
                        logWriter.flush();
                    }
                }
                if (logWriter != null) {
                    logWriter.println("  ✓ Préchargement Ardor3D terminé");
                    logWriter.flush();
                }
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("  ✗ ERREUR: Préchargement Ardor3D échoué: " + e.getMessage());
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
                // Continuer quand même; Foundation peut réussir si les dépendances ne sont pas strictes
            }
            
            // Tenter d'abord le constructeur Foundation(double, double) pour éviter l'appel à addPoint
            // Comme plan_energy.ng3 (scale 0.2) : 1 m = 5 unités → fondation 10×10 m = 50×50 u
            final double UNITS_PER_M = 5.0;
            double widthUnits = widthMeters * UNITS_PER_M;
            double heightUnits = heightMeters * UNITS_PER_M;
            Class<?> foundationClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Foundation", logWriter);
            try {
                java.lang.reflect.Constructor<?> ctor = foundationClass.getConstructor(double.class, double.class);
                Object foundation = ctor.newInstance(widthUnits, heightUnits);
                // Épaisseur fondation 0,2 m = 1 unité (scale 0.2, comme référence)
                final double DEFAULT_FOUNDATION_THICKNESS_M = 0.2;
                double foundationHeightUnits = DEFAULT_FOUNDATION_THICKNESS_M * UNITS_PER_M;
                try {
                    java.lang.reflect.Field heightField = foundationClass.getSuperclass().getDeclaredField("height");
                    heightField.setAccessible(true);
                    heightField.setDouble(foundation, foundationHeightUnits);
                    if (logWriter != null) {
                        logWriter.println("  Foundation.height = " + DEFAULT_FOUNDATION_THICKNESS_M + " m (épaisseur par défaut)");
                        logWriter.flush();
                    }
                } catch (Exception e) {
                    if (logWriter != null) {
                        logWriter.println("  ⚠ Foundation.height non modifié: " + e.getMessage());
                        logWriter.flush();
                    }
                }
                if (logWriter != null) {
                    logWriter.println("✓ Foundation créée via constructeur: " + widthMeters + "m x " + heightMeters + "m → " + widthUnits + " x " + heightUnits + " unités");
                    logWriter.flush();
                }
                return foundation;
            } catch (NoSuchMethodException nsme) {
                if (logWriter != null) {
                    logWriter.println("⚠ Constructeur Foundation(double,double) introuvable, bascule vers addPoint/complete");
                    logWriter.flush();
                }
                Object foundation = foundationClass.newInstance();
                int halfWidthCm = (int)Math.round((widthMeters * 100.0) / 2.0);
                int halfHeightCm = (int)Math.round((heightMeters * 100.0) / 2.0);
                java.lang.reflect.Method addPointMethod = foundationClass.getMethod("addPoint", int.class, int.class);
                addPointMethod.invoke(foundation, -halfWidthCm, -halfHeightCm);
                addPointMethod.invoke(foundation, halfWidthCm, -halfHeightCm);
                addPointMethod.invoke(foundation, halfWidthCm, halfHeightCm);
                addPointMethod.invoke(foundation, -halfWidthCm, halfHeightCm);
                java.lang.reflect.Method completeMethod = foundationClass.getMethod("complete");
                completeMethod.invoke(foundation);
                return foundation;
            }
        } catch (Throwable e) {
            if (logWriter != null) {
                logWriter.println("✗ ERREUR: createSizedFoundation " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace(logWriter);
                logWriter.flush();
            }
            return null;
        }
    }
    
    /** Comme plan_energy.ng3 (scale 0.2) : 1 m = 5 unités (fondation, hauteur mur). */
    private static final double ENERGY3D_UNITS_PER_METER = 5.0;
    /**
     * Crée un mur posé sur l'origine (départ en (0,0), longueur le long de X).
     * @param lengthMeters longueur du mur en mètres (axe X)
     * @param heightMeters hauteur du mur en mètres (axe Z)
     * @param thicknessMeters épaisseur du mur en mètres
     * @param foundation fondation Energy3D (conteneur du mur)
     * @param logWriter log (peut être null)
     * @return le Wall Energy3D ou null en cas d'erreur
     */
    private static Object createWallAtOrigin(double lengthMeters, double heightMeters, double thicknessMeters,
            Object foundation, PrintWriter logWriter) {
        try {
            // Longueur le long de X : même échelle que convertWallToEnergy3D (WALL_LENGTH_SCALE_X_CM)
            double lengthUnits = lengthMeters * 100.0 * WALL_LENGTH_SCALE_X_CM;
            double heightUnits = heightMeters * ENERGY3D_UNITS_PER_METER;
            double thicknessUnits = thicknessMeters * ENERGY3D_UNITS_PER_METER;
            // Mur le long de X : de (0,0,0) à (lengthUnits, 0, 0), hauteur heightUnits
            double xStart = 0, yStart = 0, xEnd = lengthUnits, yEnd = 0;
            
            Class<?> wallClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Wall", logWriter);
            if (logWriter != null) { logWriter.println("  Création mur à l'origine " + lengthMeters + "m x " + heightMeters + "m..."); logWriter.flush(); }
            Object wall = wallClass.newInstance();
            // setContainer(HousePart): use HousePart from same ClassLoader as Wall to avoid NoSuchMethodError
            Class<?> housePartClass = wallClass.getClassLoader().loadClass("org.concord.energy3d.model.HousePart");
            java.lang.reflect.Method setContainerMethod = wallClass.getMethod("setContainer", housePartClass);
            setContainerMethod.invoke(wall, foundation);
            java.lang.reflect.Method setThicknessMethod = wallClass.getMethod("setThickness", double.class);
            setThicknessMethod.invoke(wall, thicknessUnits);

            // Wall.setHeight() uses points.get(1) and points.get(3) -> fill points BEFORE setHeight
            ClassLoader loader = foundation.getClass().getClassLoader();
            Class<?> vector3Class = loader.loadClass("com.ardor3d.math.Vector3");
            java.lang.reflect.Method vector3Set = vector3Class.getMethod("set", double.class, double.class, double.class);
            double baseZ = 0.0;
            try {
                java.lang.reflect.Method getHeightMethod = foundation.getClass().getMethod("getHeight");
                baseZ = ((Number) getHeightMethod.invoke(foundation)).doubleValue();
            } catch (Exception ignored) { }
            java.lang.reflect.Field pointsField = null;
            for (Class<?> c = wallClass; c != null; c = c.getSuperclass()) {
                try {
                    pointsField = c.getDeclaredField("points");
                    break;
                } catch (NoSuchFieldException ignored) { }
            }
            if (pointsField == null) return null;
            pointsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Object> pointsList = (java.util.List<Object>) pointsField.get(wall);
            while (pointsList.size() < 4) {
                Object v = vector3Class.getConstructor(double.class, double.class, double.class)
                    .newInstance(0.0, 0.0, 0.0);
                pointsList.add(v);
            }
            vector3Set.invoke(pointsList.get(0), xStart, yStart, baseZ);
            vector3Set.invoke(pointsList.get(1), xStart, yStart, baseZ + heightUnits);
            vector3Set.invoke(pointsList.get(2), xEnd,   yEnd,   baseZ);
            vector3Set.invoke(pointsList.get(3), xEnd,   yEnd,   baseZ + heightUnits);
            try {
                java.lang.reflect.Field firstPointField = wallClass.getSuperclass().getDeclaredField("firstPointInserted");
                firstPointField.setAccessible(true);
                firstPointField.set(wall, true);
            } catch (Exception ignored) { }

            java.lang.reflect.Method setHeightMethod = wallClass.getMethod("setHeight", double.class, boolean.class);
            setHeightMethod.invoke(wall, heightUnits, true);

            java.lang.reflect.Method completeMethod = wallClass.getMethod("complete");
            completeMethod.invoke(wall);
            java.lang.reflect.Method drawMethod = wallClass.getMethod("draw");
            drawMethod.invoke(wall);
            
            return wall;
        } catch (Throwable t) {
            if (logWriter != null) {
                String msg = t.getMessage();
                logWriter.println("  ✗ Erreur createWallAtOrigin: " + (msg != null ? msg : t.getClass().getName()));
                t.printStackTrace(new java.io.PrintWriter(logWriter, true));
                logWriter.flush();
            }
            return null;
        }
    }
    
    private static Object createFoundation(Collection<Wall> sh3dWalls, double originX, double originY, PrintWriter logWriter) {
        try {
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
            
            for (Wall sh3dWall : sh3dWalls) {
                minX = Math.min(minX, Math.min(sh3dWall.getXStart(), sh3dWall.getXEnd()));
                minY = Math.min(minY, Math.min(sh3dWall.getYStart(), sh3dWall.getYEnd()));
                maxX = Math.max(maxX, Math.max(sh3dWall.getXStart(), sh3dWall.getXEnd()));
                maxY = Math.max(maxY, Math.max(sh3dWall.getYStart(), sh3dWall.getYEnd()));
            }
            // Marge en cm (SH3D) + taille minimale pour que la fondation soit visible dans Energy3D (au moins ~2 m)
            double margin = 50.0;
            double minSize = 200.0; // 200 cm = 2 m minimum
            minX -= margin;
            minY -= margin;
            maxX += margin;
            maxY += margin;
            if (maxX - minX < minSize) {
                double cx = (minX + maxX) * 0.5;
                minX = cx - minSize * 0.5;
                maxX = cx + minSize * 0.5;
            }
            if (maxY - minY < minSize) {
                double cy = (minY + maxY) * 0.5;
                minY = cy - minSize * 0.5;
                maxY = cy + minSize * 0.5;
            }
            // Coordonnées fondation relatives à l'origine du plan (0,0 dans Energy3D = originX, originY en SH3D)
            double relMinX = minX - originX;
            double relMinY = minY - originY;
            double relMaxX = maxX - originX;
            double relMaxY = maxY - originY;
            
            // Précharger toutes les classes Ardor3D nécessaires AVANT de charger Foundation
            // HousePart a un champ statique offsetState = new OffsetState() qui doit être résolu
            ClassLoader energy3dLoader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
            
            // Précharger RenderState, OffsetState, OffsetType
            try {
                energy3dLoader.loadClass("com.ardor3d.renderer.state.RenderState");
                energy3dLoader.loadClass("com.ardor3d.renderer.state.OffsetState");
                energy3dLoader.loadClass("com.ardor3d.renderer.state.OffsetState$OffsetType");
                try {
                    energy3dLoader.loadClass("com.ardor3d.renderer.state.LightState");
                    if (logWriter != null) {
                        logWriter.println("  ✓ LightState préchargé");
                        logWriter.flush();
                    }
                } catch (Exception e) {
                    if (logWriter != null) {
                        logWriter.println("  ⚠ AVERTISSEMENT: LightState non préchargé: " + e.getMessage());
                        logWriter.flush();
                    }
                }
                // Précharger ImageLoader compat (appelé pendant Scene.openNow)
                try {
                    Class<?> imageLoaderClass = energy3dLoader.loadClass("com.ardor3d.image.util.ImageLoader");
                    if (logWriter != null) {
                        logWriter.println("  ✓ ImageLoader (compat) préchargé: " + imageLoaderClass.getName());
                        logWriter.println("    ClassLoader: " + imageLoaderClass.getClassLoader().getClass().getName());
                        logWriter.flush();
                    }
                } catch (Exception e) {
                    if (logWriter != null) {
                        logWriter.println("  ⚠ AVERTISSEMENT: ImageLoader (compat) non préchargé: " + e.getMessage());
                        logWriter.flush();
                    }
                }
                if (logWriter != null) {
                    logWriter.println("  ✓ Classes Ardor3D critiques préchargées (RenderState, OffsetState, OffsetType)");
                    logWriter.flush();
                }
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("  ✗ ERREUR: Classes Ardor3D non préchargées: " + e.getMessage());
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
                return null;
            }
            
            // PRÉCHARGER MaterialState AVANT de charger Foundation
            // Foundation peut avoir besoin de MaterialState lors de son chargement
            if (logWriter != null) {
                logWriter.println("  Préchargement de MaterialState avant Foundation...");
                logWriter.flush();
            }
            try {
                Class<?> materialStateClass = energy3dLoader.loadClass("com.ardor3d.renderer.state.MaterialState");
                if (logWriter != null) {
                    logWriter.println("  ✓ MaterialState préchargé avec succès");
                    Class<?> superClass = materialStateClass.getSuperclass();
                    String materialLoader = materialStateClass.getClassLoader() != null
                        ? materialStateClass.getClassLoader().getClass().getName()
                        : "bootstrap";
                    String superLoader = (superClass != null && superClass.getClassLoader() != null)
                        ? superClass.getClassLoader().getClass().getName()
                        : "bootstrap";
                    Class<?> renderStateCheck = energy3dLoader.loadClass("com.ardor3d.renderer.state.RenderState");
                    logWriter.println("    MaterialState ClassLoader: " + materialLoader);
                    logWriter.println("    Superclass: " + (superClass != null ? superClass.getName() : "null"));
                    logWriter.println("    SuperClassLoader: " + superLoader);
                    logWriter.println("    RenderState == Superclass: " + (renderStateCheck == superClass));
                    logWriter.flush();
                }
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("  ⚠ AVERTISSEMENT: MaterialState non préchargé: " + e.getMessage());
                    logWriter.println("    Type: " + e.getClass().getName());
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
            }
            
            // PRÉCHARGER Mesh AVANT Foundation (Foundation.init() appelle Mesh.setUserData; notre ClassLoader injecte setUserData si absent)
            try {
                energy3dLoader.loadClass("com.ardor3d.scenegraph.Mesh");
                if (logWriter != null) {
                    logWriter.println("  ✓ Mesh préchargé (avec setUserData si nécessaire)");
                    logWriter.flush();
                }
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("  ⚠ AVERTISSEMENT: Mesh non préchargé: " + e.getMessage());
                    logWriter.flush();
                }
            }
            
            // Charger la classe Foundation dynamiquement via le ClassLoader Energy3D
            Class<?> foundationClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Foundation", logWriter);
            
            if (logWriter != null) {
                logWriter.println("  Tentative d'instanciation de Foundation...");
                logWriter.println("  ClassLoader utilisé: " + foundationClass.getClassLoader().getClass().getName());
                logWriter.flush();
            }
            System.out.println("DEBUG: Tentative d'instanciation de Foundation...");
            
            Object foundation = null;
            try {
                foundation = foundationClass.newInstance();
                if (logWriter != null) {
                    logWriter.println("  ✓ Foundation instanciée avec succès");
                    logWriter.flush();
                }
                System.out.println("DEBUG: Foundation instanciée avec succès");
            } catch (NoClassDefFoundError e) {
                if (logWriter != null) {
                    logWriter.println("  ✗ ERREUR NoClassDefFoundError lors de l'instanciation!");
                    logWriter.println("  Message: " + e.getMessage());
                    logWriter.println("  Cause: " + (e.getCause() != null ? e.getCause().getMessage() : "null"));
                    logWriter.println("  Stack trace complète:");
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
                System.err.println("ERREUR NoClassDefFoundError: " + e.getMessage());
                e.printStackTrace();
                return null;
            } catch (LinkageError e) {
                if (logWriter != null) {
                    logWriter.println("  ✗ ERREUR LinkageError lors de l'instanciation!");
                    logWriter.println("  Message: " + e.getMessage());
                    logWriter.println("  Type: " + e.getClass().getName());
                    logWriter.println("  Stack trace complète:");
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
                System.err.println("ERREUR LinkageError: " + e.getMessage());
                e.printStackTrace();
                return null;
            } catch (Throwable e) {
                if (logWriter != null) {
                    logWriter.println("  ✗ ERREUR lors de l'instanciation!");
                    logWriter.println("  Type: " + e.getClass().getName());
                    logWriter.println("  Message: " + e.getMessage());
                    logWriter.println("  Stack trace complète:");
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
                System.err.println("ERREUR: " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
                return null;
            }
            
            // Configurer la Foundation en définissant les 4 coins directement (comme Foundation(double, double)).
            // Coords relatives à l'origine du plan (0,0 dans Energy3D = coin min des murs en SH3D).
            try {
                double scale = SCALE_CM_TO_ENERGY3D;
                double x0 = relMinX * scale, y0 = relMinY * scale, x1 = relMaxX * scale, y1 = relMaxY * scale;
                if (MIRROR_FLIP_X) {
                    double tmp = x0;
                    x0 = -x1;
                    x1 = -tmp;
                }
                java.lang.reflect.Field pointsField = null;
                for (Class<?> c = foundationClass; c != null; c = c.getSuperclass()) {
                    try {
                        pointsField = c.getDeclaredField("points");
                        break;
                    } catch (NoSuchFieldException ignored) { }
                }
                if (pointsField == null) {
                    if (logWriter != null) { logWriter.println("  ✗ Champ 'points' introuvable sur Foundation"); logWriter.flush(); }
                    return null;
                }
                pointsField.setAccessible(true);
                java.util.List<?> points = (java.util.List<?>) pointsField.get(foundation);
                ClassLoader loader = foundationClass.getClassLoader();
                Class<?> vector3Class = loader.loadClass("com.ardor3d.math.Vector3");
                java.lang.reflect.Method vector3Set = vector3Class.getMethod("set", double.class, double.class, double.class);
                // Foundation(double, double) : get(0)=(-halfX,-halfY), get(2)=(halfX,-halfY), get(1)=(-halfX,halfY), get(3)=(halfX,halfY)
                if (points.size() >= 4) {
                    vector3Set.invoke(points.get(0), x0, y0, 0.0);
                    vector3Set.invoke(points.get(2), x1, y0, 0.0);
                    vector3Set.invoke(points.get(1), x0, y1, 0.0);
                    vector3Set.invoke(points.get(3), x1, y1, 0.0);
                }
                java.lang.reflect.Field firstPointField = null;
                for (Class<?> c = foundationClass; c != null; c = c.getSuperclass()) {
                    try {
                        firstPointField = c.getDeclaredField("firstPointInserted");
                        break;
                    } catch (NoSuchFieldException ignored) { }
                }
                if (firstPointField != null) {
                    firstPointField.setAccessible(true);
                    firstPointField.setBoolean(foundation, true);
                }
                java.lang.reflect.Method completeMethod = foundationClass.getMethod("complete");
                completeMethod.invoke(foundation);
                if (logWriter != null) {
                    logWriter.println("  ✓ Foundation configurée avec succès");
                    logWriter.flush();
                }
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("  ✗ ERREUR lors de la configuration de Foundation: " + e.getMessage());
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
                return null;
            }
            
            return foundation;
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("Exception lors de la création de la Foundation: " + e.getMessage());
                e.printStackTrace(logWriter);
            }
            e.printStackTrace();
            return null;
        }
    }
    
    /** Comme plan_energy.ng3 (scale 0.2) : 100 cm = 5 unités, 1 m = 5 u (fondation, hauteur). */
    private static final double SCALE_CM_TO_ENERGY3D = 0.05;
    /*
     * Échelles mur X/Y (source Energy3D) :
     * - Wall.getWallWidth() / getWallHeight() = distance(pts) * Scene.getScale() (Wall.java ~1855)
     * - SizeAnnotation affiche : to.subtract(from).length() * Scene.getScale() (SizeAnnotation.java L103)
     * - Convention : mètres_affichés = unités_modèle * annotationScale (Scene.annotationScale = 0.2, commenté "TODO: this is a mistake")
     * Le code Energy3D n'applique pas d'échelle différente pour X et Y ; en pratique on observe 8m→17.6 sur X et 8m→14.4 sur Y,
     * d'où les facteurs de correction empiriques ci‑dessous pour ramener à 1:1.
     */
    /** Échelle mur axe X : 8 m SH3D → 8 m Energy3D (correction 8/17.6 par rapport à 0.002). */
    private static final double WALL_LENGTH_SCALE_X_CM = 0.002 * (8.0 / 17.6);
    /** Échelle mur axe Y : 8 m SH3D → 8 m Energy3D (correction 8/14.4 par rapport à 0.002). */
    private static final double WALL_LENGTH_SCALE_Y_CM = 0.002 * (8.0 / 14.4);
    /** Inverser l'axe X pour corriger l'effet miroir entre vue intérieure et extérieure Energy3D. */
    private static final boolean MIRROR_FLIP_X = true;

    private static Object convertWallToEnergy3D(Wall sh3dWall, Object foundation, double originX, double originY, PrintWriter logWriter) {
        try {
            // Données dimensionnelles depuis Sweet Home 3D ; positions relatives à l'origine du plan (échelles X/Y corrigées)
            WallConverter.Energy3DWallData data = WallConverter.convertToEnergy3D(sh3dWall);
            double xStart = (sh3dWall.getXStart() - originX) * WALL_LENGTH_SCALE_X_CM;
            double yStart = (sh3dWall.getYStart() - originY) * WALL_LENGTH_SCALE_Y_CM;
            double xEnd   = (sh3dWall.getXEnd()   - originX) * WALL_LENGTH_SCALE_X_CM;
            double yEnd   = (sh3dWall.getYEnd()   - originY) * WALL_LENGTH_SCALE_Y_CM;
            if (MIRROR_FLIP_X) {
                xStart = -xStart;
                xEnd   = -xEnd;
            }
            // Épaisseur mur : SH3D en cm ; défaut 0,2 m = 20 cm si non définie
            double thicknessCm = data.wallThickness > 0 ? data.wallThickness : 20.0;
            double thickness = thicknessCm * SCALE_CM_TO_ENERGY3D;
            // Hauteur : SH3D en cm → Energy3D (1 m = 5 unités) ; si non définie, défaut 2,5 m
            double wallHeight = sh3dWall.getHeight() != null
                ? sh3dWall.getHeight().doubleValue() * SCALE_CM_TO_ENERGY3D
                : (2.5 * ENERGY3D_UNITS_PER_METER);

            Class<?> wallClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Wall", logWriter);
            if (logWriter != null) { logWriter.println("  Instanciation du Wall..."); logWriter.flush(); }
            Object wall = wallClass.newInstance();
            // setContainer(HousePart): use HousePart from same ClassLoader as Wall to avoid NoSuchMethodError
            Class<?> housePartClass = wallClass.getClassLoader().loadClass("org.concord.energy3d.model.HousePart");
            java.lang.reflect.Method setContainerMethod = wallClass.getMethod("setContainer", housePartClass);
            setContainerMethod.invoke(wall, foundation);

            // Définir épaisseur et hauteur avant les points (utilisées par draw/complete)
            java.lang.reflect.Method setThicknessMethod = wallClass.getMethod("setThickness", double.class);
            setThicknessMethod.invoke(wall, thickness);
            java.lang.reflect.Method setHeightMethod = wallClass.getMethod("setHeight", double.class, boolean.class);
            setHeightMethod.invoke(wall, wallHeight, true);

            // Energy3D Wall a 4 points : 0=start bas, 1=start haut, 2=end bas, 3=end haut (coords fondation)
            ClassLoader loader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
            Class<?> vector3Class = loader.loadClass("com.ardor3d.math.Vector3");
            java.lang.reflect.Method vector3Set = vector3Class.getMethod("set", double.class, double.class, double.class);

            java.lang.reflect.Field pointsField = null;
            for (Class<?> c = wallClass; c != null; c = c.getSuperclass()) {
                try {
                    pointsField = c.getDeclaredField("points");
                    break;
                } catch (NoSuchFieldException ignored) { }
            }
            if (pointsField == null) {
                if (logWriter != null) logWriter.println("  Champ 'points' introuvable sur Wall/HousePart");
                return null;
            }
            pointsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Object> points = (java.util.List<Object>) pointsField.get(wall);

            // Wall() crée 2 points ; on en a besoin de 4
            while (points.size() < 4) {
                Object v = vector3Class.getConstructor(double.class, double.class, double.class)
                    .newInstance(0.0, 0.0, 0.0);
                points.add(v);
            }
            // point 0 = start bas, 1 = start haut, 2 = end bas, 3 = end haut (z=0 au sol, z=height en haut)
            vector3Set.invoke(points.get(0), xStart, yStart, 0.0);
            vector3Set.invoke(points.get(1), xStart, yStart, wallHeight);
            vector3Set.invoke(points.get(2), xEnd,   yEnd,   0.0);
            vector3Set.invoke(points.get(3), xEnd,   yEnd,   wallHeight);

            // Marquer comme "premier point inséré" pour que complete() et draw() fonctionnent
            try {
                java.lang.reflect.Field firstPointField = wallClass.getSuperclass().getDeclaredField("firstPointInserted");
                firstPointField.setAccessible(true);
                firstPointField.set(wall, true);
            } catch (Exception e) {
                if (logWriter != null) logWriter.println("  firstPointInserted non défini: " + e.getMessage());
            }

            java.lang.reflect.Method completeMethod = wallClass.getMethod("complete");
            completeMethod.invoke(wall);

            // Couleur
            Integer color = sh3dWall.getLeftSideColor();
            if (color == null) color = sh3dWall.getRightSideColor();
            if (color != null) {
                int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
                Class<?> colorClass = Energy3DClassLoader.loadEnergy3DClass("com.ardor3d.math.ColorRGBA", logWriter);
                Object colorObj = colorClass.getConstructor(float.class, float.class, float.class, float.class)
                    .newInstance(r / 255.0f, g / 255.0f, b / 255.0f, 1.0f);
                java.lang.reflect.Method setColorMethod = wallClass.getMethod("setColor", colorClass);
                setColorMethod.invoke(wall, colorObj);
            }

            java.lang.reflect.Method setUValueMethod = wallClass.getMethod("setUValue", double.class);
            setUValueMethod.invoke(wall, data.uValue);
            java.lang.reflect.Method setVolumetricHeatCapacityMethod = wallClass.getMethod("setVolumetricHeatCapacity", double.class);
            setVolumetricHeatCapacityMethod.invoke(wall, data.volumetricHeatCapacity);

            java.lang.reflect.Method drawMethod = wallClass.getMethod("draw");
            drawMethod.invoke(wall);

            return wall;
        } catch (Throwable t) {
            if (logWriter != null) {
                logWriter.println("  Exception/Error lors de la conversion du mur: " + t.getClass().getName() + " - " + t.getMessage());
                t.printStackTrace(logWriter);
                logWriter.flush();
            }
            t.printStackTrace();
            return null;
        }
    }
    
    private static Object createNewSceneInstance(PrintWriter logWriter) {
        try {
            // Charger la classe Scene dynamiquement via le ClassLoader Energy3D
            Class<?> sceneClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.scene.Scene", logWriter);
            java.lang.reflect.Constructor<?> constructor = sceneClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object scene = constructor.newInstance();
            return scene;
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("Exception lors de la création de Scene: " + e.getMessage());
                e.printStackTrace(logWriter);
            }
            e.printStackTrace();
            return null;
        }
    }
    
    private static boolean serializeSceneToNG3(Object scene, File outputFile, PrintWriter logWriter) {
        ObjectOutputStream out = null;
        FileOutputStream fos = null;
        
        try {
            // Vérifications finales avant l'écriture
            logWriter.println("=== VERIFICATIONS PRE-ECRITURE ===");
            logWriter.println("Fichier: " + outputFile.getAbsolutePath());
            logWriter.println("  Existe: " + outputFile.exists());
            if (outputFile.exists()) {
                logWriter.println("  Taille: " + outputFile.length() + " bytes");
                logWriter.println("  Peut lire: " + outputFile.canRead());
                logWriter.println("  Peut écrire: " + outputFile.canWrite());
            }
            logWriter.println("  Répertoire parent existe: " + (outputFile.getParentFile() != null ? outputFile.getParentFile().exists() : "N/A"));
            logWriter.println("  Répertoire parent peut écrire: " + (outputFile.getParentFile() != null ? outputFile.getParentFile().canWrite() : "N/A"));
            logWriter.flush();
            
            // Vérifier les permissions une dernière fois
            if (outputFile.getParentFile() != null && !outputFile.getParentFile().canWrite()) {
                logWriter.println("✗ ERREUR: Pas de permission d'écriture dans le répertoire parent!");
                logWriter.flush();
                return false;
            }
            
            if (outputFile.exists() && !outputFile.canWrite()) {
                logWriter.println("✗ ERREUR: Le fichier existe et est en lecture seule!");
                logWriter.flush();
                return false;
            }
            
            logWriter.println("Création du FileOutputStream...");
            logWriter.flush();
            
            try {
                fos = new FileOutputStream(outputFile, false);
                logWriter.println("✓ FileOutputStream créé avec succès");
                logWriter.flush();
            } catch (java.io.FileNotFoundException e) {
                logWriter.println("✗ ERREUR FileNotFoundException: " + e.getMessage());
                logWriter.println("  Cela peut indiquer:");
                logWriter.println("  - Le répertoire parent n'existe pas");
                logWriter.println("  - Pas de permission d'écriture");
                logWriter.println("  - Le fichier est verrouillé par un autre programme");
                e.printStackTrace(logWriter);
                logWriter.flush();
                return false;
            } catch (java.io.IOException e) {
                logWriter.println("✗ ERREUR IOException lors de la création du FileOutputStream: " + e.getMessage());
                e.printStackTrace(logWriter);
                logWriter.flush();
                return false;
            }
            
            logWriter.println("Création de l'ObjectOutputStream...");
            logWriter.flush();
            
            out = new ObjectOutputStream(fos);
            
            logWriter.println("Écriture de la Scene...");
            logWriter.flush();
            
            out.writeObject(scene);
            
            logWriter.println("Flush...");
            logWriter.flush();
            
            out.flush();
            fos.getFD().sync();
            
            logWriter.println("Fermeture des flux...");
            logWriter.flush();
            
            out.close();
            fos.close();
            
            // Vérifier le fichier
            if (outputFile.exists() && outputFile.length() > 0) {
                try (java.io.FileInputStream fis = new java.io.FileInputStream(outputFile)) {
                    byte[] header = new byte[4];
                    int read = fis.read(header);
                    if (read == 4) {
                        String headerStr = String.format("%02X %02X %02X %02X", header[0], header[1], header[2], header[3]);
                        if (header[0] == (byte)0xAC && header[1] == (byte)0xED) {
                            logWriter.println("✓ Fichier binaire valide: " + outputFile.length() + " bytes");
                            logWriter.println("Header: " + headerStr);
                            logWriter.flush();
                            return true;
                        } else {
                            logWriter.println("✗ Fichier non binaire! Header: " + headerStr);
                            logWriter.flush();
                            return false;
                        }
                    }
                }
            }
            
            logWriter.println("✗ Fichier non créé ou vide");
            logWriter.flush();
            return false;
            
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("✗ ERREUR lors de la sérialisation: " + e.getMessage());
                logWriter.println("Type: " + e.getClass().getName());
                e.printStackTrace(logWriter);
                logWriter.flush();
            }
            e.printStackTrace();
            return false;
        } finally {
            if (out != null) {
                try { out.close(); } catch (IOException e) {}
            }
            if (fos != null) {
                try { fos.close(); } catch (IOException e) {}
            }
        }
    }
}
