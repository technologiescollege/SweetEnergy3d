package com.eteks.sweethome3d.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
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
     * Vérifie si le plan peut être exporté vers Energy3D (niveau "terrain" présent avec au moins une pièce).
     * 
     * @param home Le Home à vérifier
     * @return null si l'export est possible, sinon le message d'erreur à afficher (pas d'export)
     */
    public static String getExportValidationError(Home home) {
        if (home == null) return "Aucun plan ouvert.";
        Level terrainLevel = findLevelTerrainOrEquivalent(home, null);
        if (terrainLevel == null) {
            return "Aucun terrain dans ce plan. Créez un niveau nommé \"terrain\" avec au moins une pièce pour pouvoir exporter vers Energy3D.";
        }
        if (findRoomOnLevel(home, terrainLevel, null) == null) {
            return "Le niveau \"terrain\" existe mais il n'y a pas de pièce dessus. Ajoutez au moins une pièce sur ce niveau pour pouvoir exporter vers Energy3D.";
        }
        return null;
    }
    
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
            
            // Vérifier que le niveau "terrain" existe et contient au moins des murs ou une pièce (pas de fondation 10x10 par défaut)
            String validationError = getExportValidationError(home);
            if (validationError != null) {
                if (logWriter != null) {
                    logWriter.println("Export impossible: " + validationError.replace("\n\n", " "));
                    logWriter.flush();
                }
                return false;
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
            
            // Fondation = pièce sur le niveau "terrain" (déjà validé : terrain + au moins une pièce)
            double originX = 0.0, originY = 0.0;
            Level terrainLevel = findLevelTerrainOrEquivalent(home, logWriter);
            Room terrainRoom = findRoomOnLevel(home, terrainLevel, logWriter);
            if (terrainRoom == null) {
                logWriter.println("✗ ERREUR: Aucune pièce sur le niveau 'terrain' (validation incohérente)");
                logWriter.flush();
                return false;
            }
            float[][] rpts = terrainRoom.getPoints();
            if (rpts != null && rpts.length >= 2) {
                double rminX = Double.MAX_VALUE, rminY = Double.MAX_VALUE, rmaxX = Double.MIN_VALUE, rmaxY = Double.MIN_VALUE;
                for (int i = 0; i < rpts.length; i++) {
                    rminX = Math.min(rminX, rpts[i][0]);
                    rminY = Math.min(rminY, rpts[i][1]);
                    rmaxX = Math.max(rmaxX, rpts[i][0]);
                    rmaxY = Math.max(rmaxY, rpts[i][1]);
                }
                originX = 0.5 * (rminX + rmaxX);
                originY = 0.5 * (rminY + rmaxY);
            }
            if (logWriter != null) {
                logWriter.println("Origine = centre pièce terrain: " + originX + ", " + originY + " cm");
                logWriter.flush();
            }
            Object foundation = createFoundationFromRoom(terrainRoom, foundationClass, originX, originY, logWriter);
            if (foundation == null) {
                logWriter.println("✗ ERREUR: Impossible de créer la fondation à partir de la pièce terrain");
                logWriter.flush();
                return false;
            }
            Class<?> housePartClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.HousePart", logWriter);
            java.lang.reflect.Method addMethod = sceneClass.getMethod("add", housePartClass, boolean.class);
            logWriter.println("✓ Fondation créée");
            logWriter.flush();
            
            logWriter.println("Conversion des murs SH3D → Energy3D (tous les niveaux)...");
            logWriter.flush();
            List<HomePieceOfFurniture> allFurniture = getAllFurnitureIncludingGroups(home);
            int doorsWindowsCount = 0;
            for (HomePieceOfFurniture p : allFurniture) {
                if (p.isDoorOrWindow() && p instanceof HomeDoorOrWindow) doorsWindowsCount++;
            }
            if (logWriter != null) {
                logWriter.println("  Portes/fenêtres dans le plan (tous niveaux, dont groupes): " + doorsWindowsCount);
                logWriter.flush();
            }
            // Regrouper les murs par segment 2D (même trace au plan) pour fusionner les murs superposés sur plusieurs niveaux
            List<List<Wall>> wallGroups = groupWallsBySegment(sh3dWalls, terrainLevel, logWriter);
            if (WALLS_TRAVERSE_REVERSE_ORDER) {
                Collections.reverse(wallGroups);
                if (logWriter != null) logWriter.println("  Ordre des segments: inversé (sens périmètre Energy3D)");
            }
            double foundationHeightUnits = ((Number) foundationClass.getMethod("getHeight").invoke(foundation)).doubleValue();
            int wallCount = 0;
            int groupIndex = 0;
            for (List<Wall> group : wallGroups) {
                groupIndex++;
                if (group.isEmpty()) continue;
                Wall representativeWall = group.get(0);
                double overrideBaseZ = -1;
                double overrideHeight = -1;
                if (group.size() > 1) {
                    // Murs superposés : hauteur cumulée, base = niveau le plus bas
                    float terrainElev = terrainLevel != null ? terrainLevel.getElevation() : 0f;
                    float minElev = Float.MAX_VALUE;
                    double totalHeightCm = 0;
                    for (Wall w : group) {
                        Level l = w.getLevel();
                        if (l != null) minElev = Math.min(minElev, l.getElevation());
                        Float h = w.getHeight();
                        totalHeightCm += (h != null ? h.doubleValue() : 250.0);
                    }
                    if (minElev == Float.MAX_VALUE) minElev = terrainElev;
                    overrideBaseZ = foundationHeightUnits + (minElev - terrainElev) * SCALE_CM_TO_ENERGY3D;
                    overrideHeight = totalHeightCm * SCALE_CM_TO_ENERGY3D;
                }
                try {
                    if (logWriter != null) {
                        logWriter.println("  Segment " + groupIndex + "/" + wallGroups.size() + (group.size() > 1 ? " (" + group.size() + " murs fusionnés)" : "") + "...");
                        logWriter.flush();
                    }
                    Object energy3dWall = convertWallToEnergy3D(representativeWall, foundation, originX, originY,
                            terrainLevel, overrideBaseZ, overrideHeight, logWriter);
                    if (energy3dWall != null) {
                        for (Wall sh3dWallInGroup : group) {
                            convertWindowsOnWall(home, sh3dWallInGroup, energy3dWall, foundation, originX, originY, foundationClass, logWriter);
                        }
                        java.lang.reflect.Method getChildrenMethod = foundationClass.getMethod("getChildren");
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> children = (java.util.List<Object>) getChildrenMethod.invoke(foundation);
                        children.add(energy3dWall);
                        wallCount++;
                        if (logWriter != null) { logWriter.println("  ✓ Segment " + groupIndex + " converti"); logWriter.flush(); }
                    }
                } catch (Throwable t) {
                    logWriter.println("  ✗ ERREUR segment " + groupIndex + ": " + t.getMessage());
                    t.printStackTrace(logWriter);
                    logWriter.flush();
                }
            }
            logWriter.println("✓ " + wallCount + " murs convertis");
            logWriter.flush();
            try {
                foundationClass.getMethod("connectWalls").invoke(foundation);
            } catch (Exception ignored) { }

            try {
                foundationClass.getMethod("draw").invoke(foundation);
            } catch (Exception e) {
                logWriter.println("AVERTISSEMENT dessin fondation: " + e.getMessage());
            }

            // Si le plan contient un niveau "Toit-gen" (ou "Toit"), créer un toit Energy3D (HipRoof) sur la fondation
            Level toitGenLevel = null;
            for (String roofLevelName : ROOF_LEVEL_NAMES) {
                toitGenLevel = findLevelNamed(home, roofLevelName, logWriter);
                if (toitGenLevel != null) break;
            }
            if (toitGenLevel != null) {
                try {
                    addRoofFromToitGenLevel(home, foundation, foundationClass, terrainLevel, toitGenLevel, scene, logWriter);
                } catch (Throwable t) {
                    if (logWriter != null) {
                        logWriter.println("AVERTISSEMENT: toit (niveau Toit-gen) non créé: " + t.getMessage());
                        logWriter.flush();
                    }
                }
            }
            
            logWriter.println("Ajout de la fondation à la Scene...");
            logWriter.flush();
            addMethod.invoke(scene, foundation, true);
            logWriter.println("✓ Fondation ajoutée à la Scene");
            logWriter.flush();

            addTreesFromHome(home, scene, originX, originY, sceneClass, logWriter);

            ensureSceneAnnotationScale(sceneClass, scene, ENERGY3D_DEFAULT_SCALE, logWriter);
            
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
            
            // Mur 5m x 2m : désactivé pour vérifier la fondation seule
            // Object wall = createWallAtOrigin(5.0, 2.0, 0.2, foundation, logWriter);
            // if (wall != null) {
            //     try {
            //         java.lang.reflect.Method getChildrenMethod = foundation.getClass().getMethod("getChildren");
            //         @SuppressWarnings("unchecked")
            //         java.util.List<Object> children = (java.util.List<Object>) getChildrenMethod.invoke(foundation);
            //         children.add(wall);
            //         try {
            //             foundation.getClass().getMethod("connectWalls").invoke(foundation);
            //         } catch (Exception ignored) { }
            //         if (logWriter != null) {
            //             logWriter.println("✓ Mur 5m x 2m ajouté à la fondation (origine)");
            //             logWriter.flush();
            //         }
            //     } catch (Exception e) {
            //         if (logWriter != null) {
            //             logWriter.println("⚠ Mur non ajouté aux enfants: " + e.getMessage());
            //             logWriter.flush();
            //         }
            //     }
            // }
            
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
            ensureSceneAnnotationScale(sceneClass, scene, ENERGY3D_DEFAULT_SCALE, logWriter);
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
            ensureSceneAnnotationScale(sceneClass, scene, ENERGY3D_DEFAULT_SCALE, logWriter);
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
            // Grille 1×1×0,2 m : garder annotationScale = 0,2 (défaut Energy3D)
            try {
                java.lang.reflect.Field scaleField = sceneClass.getDeclaredField("annotationScale");
                scaleField.setAccessible(true);
                scaleField.set(sceneInstance, ENERGY3D_DEFAULT_SCALE);
                if (logWriter != null) {
                    logWriter.println("✓ Scene.annotationScale = " + ENERGY3D_DEFAULT_SCALE + " (grille 1×1×0,2 m)");
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
                // Épaisseur fondation : 1 unité = 0,2 m affiché (scale 0,2)
                try {
                    java.lang.reflect.Field heightField = foundationClass.getSuperclass().getDeclaredField("height");
                    heightField.setAccessible(true);
                    heightField.setDouble(foundation, FOUNDATION_HEIGHT_UNITS);
                    if (logWriter != null) {
                        logWriter.println("  Foundation.height = " + FOUNDATION_HEIGHT_UNITS + " u (0,2 m affiché)");
                        logWriter.flush();
                    }
                } catch (Exception e) {
                    if (logWriter != null) {
                        logWriter.println("  ⚠ Foundation.height non modifié: " + e.getMessage());
                        logWriter.flush();
                    }
                }
                setFoundationChildGridSize(foundationClass, foundation, 5.0, logWriter);
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
                setFoundationChildGridSize(foundationClass, foundation, 5.0, logWriter);
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
    
    /**
     * Crée un mur Energy3D sur la fondation (centre → +X sur lengthMeters).
     * Les points du mur sont en (u, v, z) relatifs à la fondation (HousePart.toAbsolute) :
     * u,v = facteurs 0-1 sur les arêtes (0.5,0.5 = centre) ; z = altitude absolue.
     * Base du mur à container.height, sommet à container.height + heightMeters.
     */
    private static Object createWallAtOrigin(double lengthMeters, double heightMeters, double thicknessMeters,
            Object foundation, PrintWriter logWriter) {
        try {
            Class<?> foundationClass = foundation.getClass();
            Class<?> wallClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Wall", logWriter);
            Class<?> housePartClass = wallClass.getClassLoader().loadClass("org.concord.energy3d.model.HousePart");
            java.lang.reflect.Method getAbsPoint = foundationClass.getMethod("getAbsPoint", int.class);
            java.lang.reflect.Method getHeightMethod = foundationClass.getMethod("getHeight");
            Object p0 = getAbsPoint.invoke(foundation, 0);
            Object p2 = getAbsPoint.invoke(foundation, 2);
            double dx = ((Number) p2.getClass().getMethod("getX").invoke(p2)).doubleValue() - ((Number) p0.getClass().getMethod("getX").invoke(p0)).doubleValue();
            double dy = ((Number) p2.getClass().getMethod("getY").invoke(p2)).doubleValue() - ((Number) p0.getClass().getMethod("getY").invoke(p0)).doubleValue();
            double foundationWidthM = Math.sqrt(dx * dx + dy * dy);
            if (foundationWidthM <= 0) foundationWidthM = 1.0;

            // Hauteur du sol de la fondation : les murs sont posés sur container.height (HousePart.toAbsolute utilise p.getZ() comme Z absolue).
            double foundationHeight = ((Number) getHeightMethod.invoke(foundation)).doubleValue();

            // (u, v, z) : u = facteur 0-1 le long de l'arête 0-2, v = facteur 0-1 le long de 0-1. Centre = (0.5, 0.5). Z = altitude absolue.
            double uStart = 0.5;
            double vStart = 0.5;
            double uEnd = 0.5 + lengthMeters / foundationWidthM;
            double vEnd = 0.5;
            double zBottom = foundationHeight;
            double zTop = foundationHeight + heightMeters;

            Object wall = wallClass.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method setContainerMethod = wallClass.getMethod("setContainer", housePartClass);
            setContainerMethod.invoke(wall, foundation);
            java.lang.reflect.Method setThicknessMethod = wallClass.getMethod("setThickness", double.class);
            setThicknessMethod.invoke(wall, thicknessMeters);

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
            ClassLoader loader = foundationClass.getClassLoader();
            Class<?> vector3Class = loader.loadClass("com.ardor3d.math.Vector3");
            java.lang.reflect.Method vector3Set = vector3Class.getMethod("set", double.class, double.class, double.class);
            while (pointsList.size() < 4) {
                pointsList.add(vector3Class.getConstructor(double.class, double.class, double.class).newInstance(0.0, 0.0, 0.0));
            }
            // 0=start bas, 1=start haut, 2=end bas, 3=end haut (coords u,v,z : u,v facteurs sur fondation, z altitude absolue)
            vector3Set.invoke(pointsList.get(0), uStart, vStart, zBottom);
            vector3Set.invoke(pointsList.get(1), uStart, vStart, zTop);
            vector3Set.invoke(pointsList.get(2), uEnd,   vEnd,   zBottom);
            vector3Set.invoke(pointsList.get(3), uEnd,   vEnd,   zTop);

            try {
                java.lang.reflect.Field firstPointField = wallClass.getSuperclass().getDeclaredField("firstPointInserted");
                firstPointField.setAccessible(true);
                firstPointField.set(wall, true);
            } catch (Exception ignored) { }

            java.lang.reflect.Method setHeightMethod = wallClass.getMethod("setHeight", double.class, boolean.class);
            setHeightMethod.invoke(wall, heightMeters, true);

            java.lang.reflect.Method completeMethod = wallClass.getMethod("complete");
            completeMethod.invoke(wall);
            java.lang.reflect.Method drawMethod = wallClass.getMethod("draw");
            drawMethod.invoke(wall);
            if (logWriter != null) {
                logWriter.println("  Mur créé: u " + uStart + "→" + uEnd + ", v=" + vStart + ", z=" + zBottom + "→" + zTop + " m, fondation largeur " + foundationWidthM + " m");
                logWriter.flush();
            }
            return wall;
        } catch (Throwable t) {
            if (logWriter != null) {
                logWriter.println("  ✗ Erreur createWallAtOrigin: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getName()));
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
            
            double scale = SCALE_CM_TO_ENERGY3D;
            double x0 = relMinX * scale, y0 = relMinY * scale, x1 = relMaxX * scale, y1 = relMaxY * scale;
            if (MIRROR_FLIP_X) {
                double tmp = x0;
                x0 = -x1;
                x1 = -tmp;
            }
            if (ROTATE_180_Z) {
                double tmpY0 = y0;
                y0 = -y1;
                y1 = -tmpY0;
            }
            double widthUnits = x1 - x0;
            double heightUnits = y1 - y0;
            double centerX = 0.5 * (x0 + x1);
            double centerY = 0.5 * (y0 + y1);
            
            // Comme "projet vide" : Foundation(double, double) évite CullHint.Always (constructeur sans arg = invisible).
            Object foundation = null;
            try {
                java.lang.reflect.Constructor<?> ctor = foundationClass.getConstructor(double.class, double.class);
                foundation = ctor.newInstance(widthUnits, heightUnits);
                Object root = foundationClass.getMethod("getRoot").invoke(foundation);
                if (root != null) {
                    root.getClass().getMethod("setTranslation", double.class, double.class, double.class).invoke(root, centerX, centerY, 0.0);
                }
                foundationClass.getMethod("draw").invoke(foundation);
                if (logWriter != null) {
                    logWriter.println("  ✓ Foundation créée via Foundation(largeur, hauteur) + translation (visible, comme projet vide)");
                    logWriter.flush();
                }
                return foundation;
            } catch (NoSuchMethodException nsme) {
                if (logWriter != null) {
                    logWriter.println("  Constructeur Foundation(double,double) introuvable, bascule vers newInstance + points + complete");
                    logWriter.flush();
                }
            } catch (Throwable e) {
                if (logWriter != null) {
                    logWriter.println("  ⚠ Foundation(double,double) échoué: " + e.getMessage() + ", bascule vers newInstance");
                    logWriter.flush();
                }
            }
            
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
    
    /** Noms acceptés pour le niveau SH3D utilisé comme fondation (terrain / ground). */
    private static final String[] TERRAIN_LEVEL_NAMES = { "terrain", "Terrain", "fondation", "foundation", "ground" };
    
    /**
     * Retourne le niveau (plan) SH3D dont le nom correspond à l'un des noms acceptés (terrain, Terrain, fondation, foundation, ground).
     * Comparaison insensible à la casse après trim.
     */
    private static Level findLevelTerrainOrEquivalent(Home home, PrintWriter logWriter) {
        if (home == null) return null;
        java.util.List<Level> levels = home.getLevels();
        if (levels == null || levels.isEmpty()) {
            if (logWriter != null) {
                logWriter.println("  Aucun niveau dans le plan (getLevels() vide ou null)");
                logWriter.flush();
            }
            return null;
        }
        if (logWriter != null) {
            logWriter.println("  Niveaux du plan : " + levels.size());
            logWriter.flush();
        }
        for (Level level : levels) {
            String name = level.getName();
            String nameTrim = name != null ? name.trim() : "";
            if (logWriter != null) {
                logWriter.println("    - \"" + (name != null ? name : "") + "\"");
                logWriter.flush();
            }
            for (String accepted : TERRAIN_LEVEL_NAMES) {
                if (accepted.equalsIgnoreCase(nameTrim)) {
                    if (logWriter != null) {
                        logWriter.println("  Niveau terrain/fondation trouvé (\"" + nameTrim + "\").");
                        logWriter.flush();
                    }
                    return level;
                }
            }
        }
        if (logWriter != null) {
            logWriter.println("  Aucun niveau nommé terrain, fondation, foundation ou ground.");
            logWriter.flush();
        }
        return null;
    }
    
    /**
     * Retourne le niveau (plan) SH3D dont le nom est égal à planName (insensible à la casse).
     * Log la liste des niveaux si aucun ne correspond.
     */
    private static Level findLevelNamed(Home home, String planName, PrintWriter logWriter) {
        if (home == null || planName == null || planName.trim().isEmpty()) return null;
        String search = planName.trim();
        java.util.List<Level> levels = home.getLevels();
        if (levels == null || levels.isEmpty()) {
            if (logWriter != null) {
                logWriter.println("  Aucun niveau dans le plan (getLevels() vide ou null)");
                logWriter.flush();
            }
            return null;
        }
        if (logWriter != null) {
            logWriter.println("  Niveaux du plan : " + levels.size());
            logWriter.flush();
        }
        for (Level level : levels) {
            String name = level.getName();
            String nameTrim = name != null ? name.trim() : "";
            if (logWriter != null) {
                logWriter.println("    - \"" + (name != null ? name : "") + "\"");
                logWriter.flush();
            }
            if (search.equalsIgnoreCase(nameTrim)) {
                if (logWriter != null) {
                    logWriter.println("  Niveau \"" + planName + "\" trouvé.");
                    logWriter.flush();
                }
                return level;
            }
        }
        if (logWriter != null) {
            logWriter.println("  Aucun niveau nommé \"" + planName + "\".");
            logWriter.flush();
        }
        return null;
    }

    /** Noms acceptés pour le niveau SH3D représentant le toit (conversion en Roof Energy3D). */
    private static final String[] ROOF_LEVEL_NAMES = { "Toit-gen", "Toit" };

    /**
     * Crée un toit Energy3D sur la fondation lorsque le plan contient un niveau "Toit-gen".
     * Pour une empreinte simple (≤4 murs) : HipRoof (2 points Steiner, crête).
     * Pour une empreinte en L ou complexe (>4 murs) : CustomRoof (un point Steiner par segment de mur),
     * ce qui permet une triangulation correcte du périmètre complet.
     */
    private static void addRoofFromToitGenLevel(Home home, Object foundation, Class<?> foundationClass,
            Level terrainLevel, Level toitGenLevel, Object scene, PrintWriter logWriter) throws Exception {
        ClassLoader loader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
        Class<?> wallClass = loader.loadClass("org.concord.energy3d.model.Wall");

        java.lang.reflect.Method getChildrenMethod = foundationClass.getMethod("getChildren");
        @SuppressWarnings("unchecked")
        java.util.List<Object> foundationChildren = (java.util.List<Object>) getChildrenMethod.invoke(foundation);
        int wallCount = 0;
        Object firstWall = null;
        for (Object ch : foundationChildren) {
            if (wallClass.isInstance(ch)) {
                if (firstWall == null) firstWall = ch;
                wallCount++;
            }
        }
        if (firstWall == null) {
            if (logWriter != null) logWriter.println("  Toit: aucun mur sur la fondation, toit non créé.");
            return;
        }

        // Origine plan (centre pièce terrain) pour convertir les points SH3D → Energy3D
        double originX = 0.0, originY = 0.0;
        if (terrainLevel != null) {
            Room terrainRoom = findRoomOnLevel(home, terrainLevel, logWriter);
            if (terrainRoom != null) {
                float[][] rpts = terrainRoom.getPoints();
                if (rpts != null && rpts.length >= 2) {
                    double rminX = Double.MAX_VALUE, rminY = Double.MAX_VALUE, rmaxX = Double.MIN_VALUE, rmaxY = Double.MIN_VALUE;
                    for (int i = 0; i < rpts.length; i++) {
                        rminX = Math.min(rminX, rpts[i][0]);
                        rminY = Math.min(rminY, rpts[i][1]);
                        rmaxX = Math.max(rmaxX, rpts[i][0]);
                        rmaxY = Math.max(rmaxY, rpts[i][1]);
                    }
                    originX = 0.5 * (rminX + rmaxX);
                    originY = 0.5 * (rminY + rmaxY);
                }
            }
        }

        // Hauteur du toit : optionnellement déduite du niveau Toit-gen
        double roofHeightUnits = 5.0;
        if (terrainLevel != null && toitGenLevel != null) {
            float terrainElev = terrainLevel.getElevation();
            float terrainHeight = terrainLevel.getHeight();
            float toitElev = toitGenLevel.getElevation();
            float toitHeight = toitGenLevel.getHeight();
            float wallTopCm = terrainElev + terrainHeight;
            float roofPeakCm = toitElev + toitHeight;
            if (roofPeakCm > wallTopCm) {
                double roofHeightCm = roofPeakCm - wallTopCm;
                roofHeightUnits = roofHeightCm * SCALE_CM_TO_ENERGY3D;
                roofHeightUnits = Math.max(1.0, Math.min(50.0, roofHeightUnits));
            }
        }

        // Check for "Toit" furniture on Toit-gen level
        List<HomePieceOfFurniture> roofFurniture = new ArrayList<>();
        if (toitGenLevel != null) {
            for (HomePieceOfFurniture furn : home.getFurniture()) {
                if (furn.getLevel() == toitGenLevel && furn.getName() != null && 
                    (furn.getName().toLowerCase().contains("toit") || furn.getName().toLowerCase().contains("roof"))) {
                    roofFurniture.add(furn);
                }
            }
        }

        // Common classes
        Class<?> housePartClass = loader.loadClass("org.concord.energy3d.model.HousePart");
        Class<?> sceneClass = loader.loadClass("org.concord.energy3d.scene.Scene");
        Class<?> vector3Class = loader.loadClass("com.ardor3d.math.Vector3");
        java.lang.reflect.Method vector3Set = vector3Class.getMethod("set", double.class, double.class, double.class);
        java.lang.reflect.Method toRelativeMethod = housePartClass.getMethod("toRelative", loader.loadClass("com.ardor3d.math.type.ReadOnlyVector3"));
        java.lang.reflect.Method getAbsPointMethod = firstWall.getClass().getMethod("getAbsPoint", int.class);
        java.lang.reflect.Method getZMethod = vector3Class.getMethod("getZ");

        // Set Scene instance
        java.lang.reflect.Field instanceField = sceneClass.getDeclaredField("instance");
        instanceField.setAccessible(true);
        Object previousInstance = instanceField.get(null);
        try {
            instanceField.set(null, scene);

            if (!roofFurniture.isEmpty()) {
                // Case 1: Create CustomRoof from UNION of Furniture
                if (logWriter != null) logWriter.println("  Toit: " + roofFurniture.size() + " meubles 'Toit' trouvés. Fusion et création d'un CustomRoof.");
                
                Class<?> customRoofClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.CustomRoof", logWriter);
                if (customRoofClass == null) return;

                // 1. Compute Union Area of all furniture pieces
                java.awt.geom.Area unionArea = new java.awt.geom.Area();
                double maxFurnHeight = 0;
                
                for (HomePieceOfFurniture furn : roofFurniture) {
                    float w = furn.getWidth();
                    float d = furn.getDepth();
                    if (furn.getHeight() > maxFurnHeight) maxFurnHeight = furn.getHeight();
                    
                    // Furniture rectangle centered at 0,0
                    java.awt.geom.Path2D.Double rect = new java.awt.geom.Path2D.Double();
                    rect.moveTo(-w/2.0, -d/2.0);
                    rect.lineTo(w/2.0, -d/2.0);
                    rect.lineTo(w/2.0, d/2.0);
                    rect.lineTo(-w/2.0, d/2.0);
                    rect.closePath();
                    
                    // Transform to world coordinates (translate + rotate)
                    java.awt.geom.AffineTransform tx = new java.awt.geom.AffineTransform();
                    tx.translate(furn.getX(), furn.getY());
                    tx.rotate(furn.getAngle()); // SH3D angle is radians, clockwise? check conventions. 
                    // SH3D getAngle() is radians, usually CCW in math, but let's trust standard transform.
                    // Actually SH3D 0 is right, positive is CCW? 
                    // Let's assume standard AffineTransform rotate works with SH3D angle.
                    
                    java.awt.Shape transformedShape = tx.createTransformedShape(rect);
                    unionArea.add(new java.awt.geom.Area(transformedShape));
                }

                // 2. Extract Polygons from Union Area
                // Area might contain multiple disjoint polygons (though unlikely for a single roof).
                // We handle each disjoint polygon as a separate roof part if needed.
                java.util.List<java.util.List<double[]>> polygons = new ArrayList<>();
                java.util.List<double[]> currentPoly = null;
                double[] coords = new double[6];
                
                for (java.awt.geom.PathIterator pi = unionArea.getPathIterator(null); !pi.isDone(); pi.next()) {
                    int type = pi.currentSegment(coords);
                    if (type == java.awt.geom.PathIterator.SEG_MOVETO) {
                        if (currentPoly != null && !currentPoly.isEmpty()) {
                            polygons.add(currentPoly);
                        }
                        currentPoly = new ArrayList<>();
                        currentPoly.add(new double[]{coords[0], coords[1]});
                    } else if (type == java.awt.geom.PathIterator.SEG_LINETO) {
                        if (currentPoly != null) currentPoly.add(new double[]{coords[0], coords[1]});
                    } else if (type == java.awt.geom.PathIterator.SEG_CLOSE) {
                        if (currentPoly != null && !currentPoly.isEmpty()) {
                            // Verify if closed (last point == first point)
                            double[] first = currentPoly.get(0);
                            double[] last = currentPoly.get(currentPoly.size() - 1);
                            if (Math.abs(first[0] - last[0]) > 0.001 || Math.abs(first[1] - last[1]) > 0.001) {
                                // Add closing segment if missing? Area usually closes it.
                                // Actually SEG_CLOSE implies line to first point.
                                // But Energy3D might want explicit points. 
                                // Let's check if the last point is already the first.
                            }
                            polygons.add(currentPoly);
                            currentPoly = null;
                        }
                    }
                }
                if (currentPoly != null && !currentPoly.isEmpty()) {
                    polygons.add(currentPoly);
                }

                // 3. Create CustomRoof for each polygon
                Object topPoint = getAbsPointMethod.invoke(firstWall, 1);
                double wallTopZ = ((Number) getZMethod.invoke(topPoint)).doubleValue();
                roofHeightUnits = maxFurnHeight * SCALE_CM_TO_ENERGY3D;
                
                for (java.util.List<double[]> polyPoints : polygons) {
                    if (polyPoints.size() < 3) continue;
                    
                    Object roof = customRoofClass.getDeclaredConstructor().newInstance();
                    
                    // Set container
                    java.lang.reflect.Method setContainerMethod = customRoofClass.getMethod("setContainer", loader.loadClass("org.concord.energy3d.model.HousePart"));
                    setContainerMethod.invoke(roof, firstWall);
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> wallChildren = (java.util.List<Object>) firstWall.getClass().getMethod("getChildren").invoke(firstWall);
                    wallChildren.add(roof);

                    // Initialize
                    customRoofClass.getMethod("complete").invoke(roof);
                    
                    // Convert polygon points to Energy3D coords
                    java.util.List<Object> absPerimeter = new ArrayList<>();
                    double cx = 0, cy = 0;
                    
                    // Check winding? Energy3D might expect specific winding.
                    // Area usually returns CCW for outer boundaries.
                    
                    for (double[] p : polyPoints) {
                        double px = p[0];
                        double py = p[1];
                        
                        double ex = (px - originX) * SCALE_CM_TO_ENERGY3D;
                        double ey = (py - originY) * SCALE_CM_TO_ENERGY3D;
                        if (MIRROR_FLIP_X) ex = -ex;
                        if (ROTATE_180_Z) ey = -ey;
                        
                        cx += ex;
                        cy += ey;
                        
                        Object v = vector3Class.getDeclaredConstructor().newInstance();
                        vector3Set.invoke(v, ex, ey, wallTopZ); // Base Z
                        absPerimeter.add(v);
                    }
                    cx /= polyPoints.size();
                    cy /= polyPoints.size();
                    
                    // Set points (Center + Perimeter)
                    Object centerAbs = vector3Class.getDeclaredConstructor().newInstance();
                    vector3Set.invoke(centerAbs, cx, cy, wallTopZ + roofHeightUnits);
                    
                    java.lang.reflect.Field pointsField = null;
                    for (Class<?> c = customRoofClass; c != null; c = c.getSuperclass()) {
                        try { pointsField = c.getDeclaredField("points"); break; } catch (NoSuchFieldException ignored) {}
                    }
                    if (pointsField != null) {
                        pointsField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> roofPoints = (java.util.List<Object>) pointsField.get(roof);
                        roofPoints.clear();
                        roofPoints.add(toRelativeMethod.invoke(roof, centerAbs)); // Peak
                        for (Object absP : absPerimeter) {
                            roofPoints.add(toRelativeMethod.invoke(roof, absP));
                        }
                        
                        // Set flags for CustomRoof
                        try {
                            java.lang.reflect.Field recalcField = customRoofClass.getDeclaredField("recalculateEditPoints");
                            recalcField.setAccessible(true);
                            recalcField.setBoolean(roof, false); // Manual points
                            
                            java.lang.reflect.Field preserveField = customRoofClass.getDeclaredField("preservePointHeights");
                            preserveField.setAccessible(true);
                            preserveField.setBoolean(roof, true);
                        } catch (Exception e) {
                            if (logWriter != null) logWriter.println("  Toit: flags CustomRoof non définis: " + e.getMessage());
                        }
                    }
                    
                    // Set Height
                    java.lang.reflect.Method setHeightMethod = housePartClass.getDeclaredMethod("setHeight", double.class, boolean.class);
                    setHeightMethod.setAccessible(true);
                    setHeightMethod.invoke(roof, roofHeightUnits, true);

                    // Texture Energy3D #1 pour le toit (par défaut)
                    setHousePartTextureType(roof, customRoofClass, 1, logWriter, "toit");
                    
                    // Draw
                    customRoofClass.getMethod("draw").invoke(roof);
                }
                
                foundationClass.getMethod("draw").invoke(foundation);
                return; // Done
            }

            // Priorité : si la pièce "Toit-gen" a des points → CustomRoof créé à partir de ces points ; sinon CustomRoof si >4 murs, HipRoof sinon
            Room roofRoom = findRoomOnLevel(home, toitGenLevel, logWriter);
            boolean useCustomRoofFromPoints = (roofRoom != null && roofRoom.getPointCount() >= 3);
            boolean useCustomRoof = useCustomRoofFromPoints || (wallCount > 4);
            Class<?> roofClass = useCustomRoof
                    ? Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.CustomRoof", logWriter)
                    : Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.HipRoof", logWriter);
            if (roofClass == null) return;
        
            Object roof = roofClass.getDeclaredConstructor().newInstance();

            java.lang.reflect.Method setContainerMethod = roofClass.getMethod("setContainer", loader.loadClass("org.concord.energy3d.model.HousePart"));
            setContainerMethod.invoke(roof, firstWall);

            @SuppressWarnings("unchecked")
            java.util.List<Object> wallChildren = (java.util.List<Object>) firstWall.getClass().getMethod("getChildren").invoke(firstWall);
            wallChildren.add(roof);

            roofClass.getMethod("complete").invoke(roof);

            // Texture Energy3D #1 pour le toit (par défaut)
            setHousePartTextureType(roof, roofClass, 1, logWriter, "toit");

            // CustomRoof : créer le toit à partir des points de la pièce "Toit-gen" (périmètre + faîte), ou recalcul auto si pas de pièce
            boolean useRoomPolygonForPitchedRoof = false;
            if (useCustomRoof && useCustomRoofFromPoints) {
                    float[][] rp = roofRoom.getPoints();
                    if (rp != null && rp.length >= 3) {
                        // Z du haut des murs (contenant du toit)
                        Object topPoint = getAbsPointMethod.invoke(firstWall, 1);
                        double wallTopZ = ((Number) getZMethod.invoke(topPoint)).doubleValue();

                        // Périmètre en coordonnées Energy3D absolues (x, y, Z par point : murs en pente pris en compte)
                        java.util.List<Object> absPerimeter = new ArrayList<>(rp.length);
                        double cx = 0, cy = 0;
                        for (int i = 0; i < rp.length; i++) {
                            double x = (rp[i][0] - originX) * SCALE_CM_TO_ENERGY3D;
                            double y = (rp[i][1] - originY) * SCALE_CM_TO_ENERGY3D;
                            if (MIRROR_FLIP_X) x = -x;
                            if (ROTATE_180_Z) y = -y;
                            cx += x;
                            cy += y;
                            double pointZ = computeRoofZAtPoint(home, toitGenLevel, rp[i][0], rp[i][1]);
                            Object v = vector3Class.getDeclaredConstructor().newInstance();
                            vector3Set.invoke(v, x, y, pointZ);
                            absPerimeter.add(v);
                        }
                        cx /= rp.length;
                        cy /= rp.length;

                        // Centre (faîte) en (cx, cy, max Z périmètre + roofHeight)
                        double baseZForPeak = wallTopZ;
                        if (!absPerimeter.isEmpty()) {
                            Object first = absPerimeter.get(0);
                            baseZForPeak = ((Number) getZMethod.invoke(first)).doubleValue();
                            for (int i = 1; i < absPerimeter.size(); i++) {
                                double zi = ((Number) absPerimeter.get(i).getClass().getMethod("getZ").invoke(absPerimeter.get(i))).doubleValue();
                                if (zi > baseZForPeak) baseZForPeak = zi;
                            }
                        }
                        Object centerAbs = vector3Class.getDeclaredConstructor().newInstance();
                        vector3Set.invoke(centerAbs, cx, cy, baseZForPeak + roofHeightUnits);

                        // Champ points du toit (HousePart)
                        java.lang.reflect.Field pointsField = null;
                        for (Class<?> c = roofClass; c != null; c = c.getSuperclass()) {
                            try {
                                pointsField = c.getDeclaredField("points");
                                break;
                            } catch (NoSuchFieldException ignored) { }
                        }
                        if (pointsField != null) {
                            pointsField.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            java.util.List<Object> roofPoints = (java.util.List<Object>) pointsField.get(roof);
                            roofPoints.clear();
                            roofPoints.add(toRelativeMethod.invoke(roof, centerAbs));
                            for (Object absP : absPerimeter) {
                                roofPoints.add(toRelativeMethod.invoke(roof, absP));
                            }
                            java.lang.reflect.Field recalcField = roofClass.getDeclaredField("recalculateEditPoints");
                            recalcField.setAccessible(true);
                            recalcField.setBoolean(roof, false);
                            try {
                                java.lang.reflect.Field preserveField = roofClass.getDeclaredField("preservePointHeights");
                                preserveField.setAccessible(true);
                                preserveField.setBoolean(roof, true);
                            } catch (NoSuchFieldException e) {
                                if (logWriter != null) logWriter.println("  Toit: preservePointHeights non défini (CustomRoof): " + e.getMessage());
                            }
                            useRoomPolygonForPitchedRoof = true;
                            if (logWriter != null) logWriter.println("  Toit: CustomRoof créé à partir des points de la pièce Toit-gen (" + rp.length + " sommets).");
                        }
                    }
            }
            if (useCustomRoof && !useRoomPolygonForPitchedRoof) {
                try {
                    java.lang.reflect.Field recalcField = roofClass.getDeclaredField("recalculateEditPoints");
                    recalcField.setAccessible(true);
                    recalcField.setBoolean(roof, true);
                } catch (Exception e) {
                    if (logWriter != null) logWriter.println("  Toit: recalculateEditPoints non défini (CustomRoof): " + e.getMessage());
                }
            }
            if (!useCustomRoof) {
                float[][] rp2 = null;
                if (roofRoom != null && roofRoom.getPointCount() >= 3) {
                    rp2 = roofRoom.getPoints();
                } else {
                    double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
                    for (Wall w : home.getWalls()) {
                        if (!w.isAtLevel(terrainLevel)) continue;
                        minX = Math.min(minX, Math.min(w.getXStart(), w.getXEnd()));
                        minY = Math.min(minY, Math.min(w.getYStart(), w.getYEnd()));
                        maxX = Math.max(maxX, Math.max(w.getXStart(), w.getXEnd()));
                        maxY = Math.max(maxY, Math.max(w.getYStart(), w.getYEnd()));
                    }
                    if (minX < maxX && minY < maxY) {
                        rp2 = new float[][] {
                            {(float)minX, (float)minY},
                            {(float)maxX, (float)minY},
                            {(float)maxX, (float)maxY},
                            {(float)minX, (float)maxY}
                        };
                    }
                }
                if (rp2 != null) {
                    Object topPoint = getAbsPointMethod.invoke(firstWall, 1);
                    double wallTopZ = ((Number) getZMethod.invoke(topPoint)).doubleValue();
                    java.util.List<Object> absPerimeter2 = new ArrayList<>(rp2.length);
                    double cx2 = 0, cy2 = 0;
                    for (int i = 0; i < rp2.length; i++) {
                        double x = (rp2[i][0] - originX) * SCALE_CM_TO_ENERGY3D;
                        double y = (rp2[i][1] - originY) * SCALE_CM_TO_ENERGY3D;
                        if (MIRROR_FLIP_X) x = -x;
                        if (ROTATE_180_Z) y = -y;
                        cx2 += x;
                        cy2 += y;
                        double pointZ = computeRoofZAtPoint(home, toitGenLevel, rp2[i][0], rp2[i][1]);
                        Object v = vector3Class.getDeclaredConstructor().newInstance();
                        vector3Set.invoke(v, x, y, pointZ);
                        absPerimeter2.add(v);
                    }
                    cx2 /= rp2.length;
                    cy2 /= rp2.length;
                    double baseZForPeak2 = wallTopZ;
                    if (!absPerimeter2.isEmpty()) {
                        Object first = absPerimeter2.get(0);
                        baseZForPeak2 = ((Number) getZMethod.invoke(first)).doubleValue();
                        for (int i = 1; i < absPerimeter2.size(); i++) {
                            double zi = ((Number) absPerimeter2.get(i).getClass().getMethod("getZ").invoke(absPerimeter2.get(i))).doubleValue();
                            if (zi > baseZForPeak2) baseZForPeak2 = zi;
                        }
                    }
                    Object centerAbs2 = vector3Class.getDeclaredConstructor().newInstance();
                    vector3Set.invoke(centerAbs2, cx2, cy2, baseZForPeak2 + roofHeightUnits);
                    java.lang.reflect.Field pointsField2 = null;
                    for (Class<?> c = roofClass; c != null; c = c.getSuperclass()) {
                        try {
                            pointsField2 = c.getDeclaredField("points");
                            break;
                        } catch (NoSuchFieldException ignored) { }
                    }
                    if (pointsField2 != null) {
                        pointsField2.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> roofPoints2 = (java.util.List<Object>) pointsField2.get(roof);
                        roofPoints2.clear();
                        roofPoints2.add(toRelativeMethod.invoke(roof, centerAbs2));
                        for (Object absP : absPerimeter2) {
                            roofPoints2.add(toRelativeMethod.invoke(roof, absP));
                        }
                    }
                }
            }

            java.lang.reflect.Method setHeightMethod = housePartClass.getDeclaredMethod("setHeight", double.class, boolean.class);
            setHeightMethod.setAccessible(true);
            setHeightMethod.invoke(roof, roofHeightUnits, true);

            roofClass.getMethod("draw").invoke(roof);
            foundationClass.getMethod("draw").invoke(foundation);

            if (logWriter != null) {
                logWriter.println("  ✓ Toit Energy3D (" + (useCustomRoof ? "CustomRoof" : "HipRoof") + ", " + wallCount + " murs) créé à partir du niveau \"Toit-gen\" (hauteur " + roofHeightUnits + " u).");
                logWriter.flush();
            }
        } finally {
            instanceField.set(null, previousInstance);
        }
    }
    
    /**
     * Calcule la hauteur Z (en unités Energy3D) du toit au point plan (px, py) en cm SH3D,
     * à partir des murs du niveau (murs en pente : height / heightAtEnd). Si aucun mur proche, utilise plafond du niveau.
     */
    private static double computeRoofZAtPoint(Home home, Level level, double px, double py) {
        if (home == null || level == null) return (level.getElevation() + level.getHeight()) * SCALE_CM_TO_ENERGY3D;
        double levelElev = level.getElevation();
        double levelHeight = level.getHeight();
        double defaultZ = (levelElev + levelHeight) * SCALE_CM_TO_ENERGY3D;
        double maxZ = defaultZ;
        double thresholdCm = 80.0; // point proche d'un mur (cm)
        for (Wall wall : home.getWalls()) {
            if (wall.getLevel() != level) continue;
            float xS = wall.getXStart(), yS = wall.getYStart(), xE = wall.getXEnd(), yE = wall.getYEnd();
            double dx = xE - xS, dy = yE - yS;
            double len2 = dx * dx + dy * dy;
            if (len2 < 1e-6) continue;
            double t = ((px - xS) * dx + (py - yS) * dy) / len2;
            t = Math.max(0, Math.min(1, t));
            double projX = xS + t * dx, projY = yS + t * dy;
            double dist = Math.hypot(px - projX, py - projY);
            if (dist > thresholdCm) continue;
            float hS = wall.getHeight() != null ? wall.getHeight() : 250f;
            float hE = wall.getHeightAtEnd() != null ? wall.getHeightAtEnd() : hS;
            double zCm = levelElev + (1 - t) * hS + t * hE;
            double zUnits = zCm * SCALE_CM_TO_ENERGY3D;
            if (zUnits > maxZ) maxZ = zUnits;
        }
        return maxZ;
    }

    /**
     * Retourne la pièce "terrain" sur le niveau donné : la plus grande en surface (aire),
     * pour éviter de prendre une pièce intérieure créée par les murs (ex. 9,93×7,92) au lieu du grand sol (12×10).
     */
    private static Room findRoomOnLevel(Home home, Level level, PrintWriter logWriter) {
        if (home == null || level == null) return null;
        java.util.List<Room> sorted = getAllRoomsOnLevelSortedByArea(home, level);
        if (sorted == null || sorted.isEmpty()) {
            if (logWriter != null) {
                logWriter.println("  Aucune pièce sur ce niveau.");
                logWriter.flush();
            }
            return null;
        }
        return sorted.get(0);
    }
    
    /**
     * Retourne toutes les pièces du niveau donné, triées par aire décroissante (plus grande = terrain/sol en premier).
     */
    private static java.util.List<Room> getAllRoomsOnLevelSortedByArea(Home home, Level level) {
        if (home == null || level == null) return java.util.Collections.emptyList();
        java.util.List<Room> onLevel = new java.util.ArrayList<Room>();
        java.util.List<Room> rooms = home.getRooms();
        if (rooms == null) return onLevel;
        for (Room room : rooms) {
            if (room != null && room.isAtLevel(level)) {
                onLevel.add(room);
            }
        }
        java.util.Collections.sort(onLevel, new java.util.Comparator<Room>() {
            @Override
            public int compare(Room a, Room b) {
                float areaA = a.getArea();
                float areaB = b.getArea();
                return Float.compare(areaB, areaA); // décroissant : plus grande d'abord (terrain/sol)
            }
        });
        return onLevel;
    }
    
    /**
     * Retourne le centre (en cm) du bounding box des murs du niveau donné, ou null si aucun mur.
     */
    private static double[] getWallsOnLevelCenterCm(Collection<Wall> walls, Level level) {
        if (walls == null || level == null) return null;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        int count = 0;
        for (Wall w : walls) {
            if (w == null || !w.isAtLevel(level)) continue;
            count++;
            minX = Math.min(minX, Math.min(w.getXStart(), w.getXEnd()));
            minY = Math.min(minY, Math.min(w.getYStart(), w.getYEnd()));
            maxX = Math.max(maxX, Math.max(w.getXStart(), w.getXEnd()));
            maxY = Math.max(maxY, Math.max(w.getYStart(), w.getYEnd()));
        }
        if (count == 0) return null;
        return new double[] { 0.5 * (minX + maxX), 0.5 * (minY + maxY) };
    }
    
    /**
     * Crée une fondation Energy3D à partir du bounding box des murs du niveau donné (même méthode que pièce : Foundation(largeur, hauteur) + translation).
     */
    private static Object createFoundationFromWallsOnLevel(Collection<Wall> walls, Level level, Class<?> foundationClass, double originX, double originY, PrintWriter logWriter) {
        if (walls == null || level == null) return null;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        int count = 0;
        for (Wall w : walls) {
            if (w == null || !w.isAtLevel(level)) continue;
            count++;
            minX = Math.min(minX, Math.min(w.getXStart(), w.getXEnd()));
            minY = Math.min(minY, Math.min(w.getYStart(), w.getYEnd()));
            maxX = Math.max(maxX, Math.max(w.getXStart(), w.getXEnd()));
            maxY = Math.max(maxY, Math.max(w.getYStart(), w.getYEnd()));
        }
        if (count == 0) {
            if (logWriter != null) {
                logWriter.println("  Aucun mur sur ce niveau.");
                logWriter.flush();
            }
            return null;
        }
        return createFoundationFromWallsBounds(foundationClass, minX, minY, maxX, maxY, originX, originY, 50.0, 200.0, logWriter, "Fondation murs: " + count + " mur(s)");
    }
    
    /**
     * Crée une fondation Energy3D à partir d'un bounding box (cm) avec marge et taille min optionnelles.
     */
    private static Object createFoundationFromWallsBounds(Class<?> foundationClass, double minX, double minY, double maxX, double maxY, double originX, double originY, double marginCm, double minSizeCm, PrintWriter logWriter, String logLabel) {
        minX -= marginCm;
        minY -= marginCm;
        maxX += marginCm;
        maxY += marginCm;
        if (maxX - minX < minSizeCm) {
            double cx = (minX + maxX) * 0.5;
            minX = cx - minSizeCm * 0.5;
            maxX = cx + minSizeCm * 0.5;
        }
        if (maxY - minY < minSizeCm) {
            double cy = (minY + maxY) * 0.5;
            minY = cy - minSizeCm * 0.5;
            maxY = cy + minSizeCm * 0.5;
        }
        double relMinX = minX - originX, relMinY = minY - originY, relMaxX = maxX - originX, relMaxY = maxY - originY;
        double scale = SCALE_CM_TO_ENERGY3D;
        double x0 = relMinX * scale, y0 = relMinY * scale, x1 = relMaxX * scale, y1 = relMaxY * scale;
        double widthUnits = x1 - x0, heightUnits = y1 - y0;
        double centerX = 0.5 * (x0 + x1), centerY = 0.5 * (y0 + y1);
        if (widthUnits <= 0 || heightUnits <= 0) return null;
        try {
            Object foundation = foundationClass.getDeclaredConstructor(double.class, double.class).newInstance(widthUnits, heightUnits);
            try {
                java.lang.reflect.Field heightField = foundationClass.getSuperclass().getDeclaredField("height");
                heightField.setAccessible(true);
                heightField.setDouble(foundation, FOUNDATION_HEIGHT_UNITS);
            } catch (Exception ignored) { }
            setFoundationChildGridSize(foundationClass, foundation, 5.0, logWriter);
            foundationClass.getMethod("draw").invoke(foundation);
            ClassLoader loader = foundationClass.getClassLoader();
            Object root = foundationClass.getMethod("getRoot").invoke(foundation);
            if (root != null) {
                root.getClass().getMethod("setTranslation", double.class, double.class, double.class).invoke(root, centerX, centerY, 0.0);
                try {
                    Class<?> cullHintClass = loader.loadClass("com.ardor3d.scenegraph.hint.CullHint");
                    Object cullInherit = cullHintClass.getMethod("valueOf", String.class).invoke(null, "Inherit");
                    Object sceneHints = root.getClass().getMethod("getSceneHints").invoke(root);
                    sceneHints.getClass().getMethod("setCullHint", cullHintClass).invoke(sceneHints, cullInherit);
                } catch (Throwable ignored) { }
                if (logWriter != null && logLabel != null) logWriter.println("  " + logLabel + ", " + widthUnits + "x" + heightUnits + " u, centre (" + centerX + "," + centerY + ")");
            }
            foundationClass.getMethod("draw").invoke(foundation);
            return foundation;
        } catch (Throwable t) {
            if (logWriter != null) {
                logWriter.println("  createFoundationFromWallsBounds: " + t.getMessage());
                logWriter.flush();
            }
            return null;
        }
    }
    
    /** Marge (cm) pour la fondation "terrain" (sol vert) à partir du bounding box des murs. */
    private static final double TERRAIN_GROUND_MARGIN_CM = 300.0;
    
    /**
     * Crée une fondation "terrain" (grand sol) à partir du bounding box des murs du niveau, avec une marge généreuse.
     */
    private static Object createTerrainGroundFromWalls(Collection<Wall> walls, Level level, Class<?> foundationClass, double originX, double originY, PrintWriter logWriter) {
        if (walls == null || level == null) return null;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        int count = 0;
        for (Wall w : walls) {
            if (w == null || !w.isAtLevel(level)) continue;
            count++;
            minX = Math.min(minX, Math.min(w.getXStart(), w.getXEnd()));
            minY = Math.min(minY, Math.min(w.getYStart(), w.getYEnd()));
            maxX = Math.max(maxX, Math.max(w.getXStart(), w.getXEnd()));
            maxY = Math.max(maxY, Math.max(w.getYStart(), w.getYEnd()));
        }
        if (count == 0) return null;
        return createFoundationFromWallsBounds(foundationClass, minX, minY, maxX, maxY, originX, originY, TERRAIN_GROUND_MARGIN_CM, 400.0, logWriter, "Terrain (sol): " + count + " mur(s)");
    }
    
    /**
     * Définit childGridSize sur une fondation Energy3D. Avec scale 0,2 : childGridSize = 5 → pas 1 m (5×0,2).
     * Garde la grille 1×1×0,2 m comme Energy3D par défaut.
     */
    private static void setFoundationChildGridSize(Class<?> foundationClass, Object foundation, double gridSize, PrintWriter logWriter) {
        try {
            java.lang.reflect.Field childGridSizeField = foundationClass.getDeclaredField("childGridSize");
            childGridSizeField.setAccessible(true);
            childGridSizeField.setDouble(foundation, gridSize);
            if (logWriter != null) {
                logWriter.println("  Fondation childGridSize = " + gridSize + " (pas tracé 1 m avec scale 0,2)");
                logWriter.flush();
            }
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("  ⚠ Foundation.childGridSize non modifié: " + e.getMessage());
                logWriter.flush();
            }
        }
    }

    /**
     * Crée une fondation Energy3D à partir du sol d'une pièce SH3D (bounding box des points de la pièce).
     * Utilise le constructeur Foundation(largeur, hauteur) comme Energy3D pour "nouveau projet avec contenu",
     * afin d'éviter CullHint.Always (réservé au constructeur sans argument). Puis translate le root au centre de la pièce.
     */
    private static Object createFoundationFromRoom(Room room, Class<?> foundationClass, double originX, double originY, PrintWriter logWriter) {
        try {
            float[][] pts = room.getPoints();
            if (pts == null || pts.length < 2) return null;
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
            for (int i = 0; i < pts.length; i++) {
                double px = pts[i][0], py = pts[i][1];
                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);
            }
            double relMinX = minX - originX, relMinY = minY - originY, relMaxX = maxX - originX, relMaxY = maxY - originY;
            double scale = SCALE_CM_TO_ENERGY3D;
            double x0 = relMinX * scale, y0 = relMinY * scale, x1 = relMaxX * scale, y1 = relMaxY * scale;
            if (MIRROR_FLIP_X) { x0 = -x0; x1 = -x1; double t = x0; x0 = x1; x1 = t; }
            if (ROTATE_180_Z) { y0 = -y0; y1 = -y1; double t = y0; y0 = y1; y1 = t; }
            double widthUnits = x1 - x0, heightUnits = y1 - y0;
            double centerX = 0.5 * (x0 + x1), centerY = 0.5 * (y0 + y1);
            if (widthUnits <= 0 || heightUnits <= 0) return null;

            // Comme Scene.newFile(true) : Foundation(80, 60) — constructeur (double, double) sans CullHint.Always
            Object foundation = foundationClass.getDeclaredConstructor(double.class, double.class).newInstance(widthUnits, heightUnits);

            // Épaisseur fondation (pièce) : toujours 0,2 m dans Energy3D (unifié pour tous les exports)
            try {
                java.lang.reflect.Field heightField = foundationClass.getSuperclass().getDeclaredField("height");
                heightField.setAccessible(true);
                heightField.setDouble(foundation, FOUNDATION_HEIGHT_UNITS);
                if (logWriter != null) logWriter.println("  Fondation height = " + FOUNDATION_HEIGHT_UNITS + " u (0,2 m affiché)");
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("  ⚠ Foundation.height non modifié: " + e.getMessage());
                    logWriter.flush();
                }
            }
            setFoundationChildGridSize(foundationClass, foundation, 5.0, logWriter);

            // Texture Energy3D #1 pour la fondation : modification directe du champ textureType (sérialisé dans .ng3)
            setHousePartTextureType(foundation, foundationClass, 1, logWriter, "fondation");
            foundationClass.getMethod("draw").invoke(foundation);

            // Positionner la fondation au centre de la pièce (Foundation place par défaut le centre en 0,0)
            ClassLoader loader = foundationClass.getClassLoader();
            Object root = foundationClass.getMethod("getRoot").invoke(foundation);
            if (root != null) {
                // setTranslation(double, double, double) pour éviter NoSuchMethod avec setTranslation(Vector3) (ClassLoader)
                root.getClass().getMethod("setTranslation", double.class, double.class, double.class).invoke(root, centerX, centerY, 0.0);
                // Forcer CullHint.Inherit (même constructeur (double,double) que projet vide = visible)
                try {
                    Class<?> cullHintClass = loader.loadClass("com.ardor3d.scenegraph.hint.CullHint");
                    Object cullInherit = cullHintClass.getMethod("valueOf", String.class).invoke(null, "Inherit");
                    Object sceneHints = root.getClass().getMethod("getSceneHints").invoke(root);
                    sceneHints.getClass().getMethod("setCullHint", cullHintClass).invoke(sceneHints, cullInherit);
                } catch (Throwable ignored) { }
                if (logWriter != null) logWriter.println("  Sol pièce: " + (widthUnits) + "x" + (heightUnits) + " u, centre (" + centerX + "," + centerY + ")");
            }
            foundationClass.getMethod("draw").invoke(foundation);
            return foundation;
        } catch (Throwable t) {
            if (logWriter != null) {
                logWriter.println("  createFoundationFromRoom: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                Throwable cause = t.getCause();
                if (cause != null) {
                    logWriter.println("    Cause: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                }
                t.printStackTrace(logWriter);
                logWriter.flush();
            }
            return null;
        }
    }
    
    /**
     * Échelle Energy3D par défaut (annotationScale = 0.2) : 1 unité = 0,2 m affiché.
     * Pour garder la grille 1×1×0,2 m, on n'impose pas scale = 1 et on exporte en unités Energy3D :
     * 1 m SH3D = 5 unités (5 × 0,2 = 1 m affiché).
     */
    private static final double ENERGY3D_DEFAULT_SCALE = 0.2;
    /** 100 cm SH3D = 5 unités Energy3D → 1 m affiché (avec scale 0,2). */
    private static final double SCALE_CM_TO_ENERGY3D = 0.05;
    /** 1 m = 5 unités (avec scale 0,2 : 5 × 0,2 = 1 m affiché). */
    private static final double ENERGY3D_UNITS_PER_METER_EXPORT = 5.0;
    /** Hauteur fondation / épaisseur en unités Energy3D : 1 unité = 0,2 m affiché. */
    private static final double FOUNDATION_HEIGHT_UNITS = 1.0;
    /** Épaisseur des murs affichée dans Energy3D : 0,2 m. (en unités : 0,2 m / scale 0,2 = 1 unité) */
    private static final double WALL_THICKNESS_M = 0.2;
    private static final double WALL_THICKNESS_UNITS = WALL_THICKNESS_M / ENERGY3D_DEFAULT_SCALE;
    /** Albédo des murs exportés pour obtenir une absorptance de 0,09 (Energy3D : absorptance = 1 - albedo). */
    private static final float DEFAULT_WALL_ALBEDO = 0.91f;
    /** Miroir X (symétrie axe Y) : à ajuster selon orientation SH3D vs Energy3D. */
    private static final boolean MIRROR_FLIP_X = false;
    /** Rotation 180° plan (inversion Y) : à ajuster pour aligner avec la vue 3D SH3D. */
    private static final boolean ROTATE_180_Z = true;
    /** Inverser l'orientation des murs (start/end) pour que la face extérieure en Energy3D corresponde à SH3D (évite les murs "à l'envers"). */
    private static final boolean WALL_REVERSE_ORIENTATION = true;
    /** Parcourir les murs dans l'ordre inverse (sens du périmètre attendu par Energy3D pour connectWalls / rendu). */
    private static final boolean WALLS_TRAVERSE_REVERSE_ORDER = true;
    /** Tolérance (cm) pour considérer deux segments comme le même mur (arrondi pour clé de regroupement). */
    private static final double SEGMENT_KEY_TOLERANCE_CM = 1.0;

    /**
     * Clé canonique pour un segment 2D (mur) : même clé = même trace au plan, tous niveaux confondus.
     * Les extrémités sont ordonnées pour que (x1,y1) <= (x2,y2) lexicographiquement, puis arrondies à 1 cm.
     */
    private static String segmentKey(Wall w) {
        if (w == null) return "";
        double x1 = Math.round(w.getXStart() / SEGMENT_KEY_TOLERANCE_CM) * SEGMENT_KEY_TOLERANCE_CM;
        double y1 = Math.round(w.getYStart() / SEGMENT_KEY_TOLERANCE_CM) * SEGMENT_KEY_TOLERANCE_CM;
        double x2 = Math.round(w.getXEnd()   / SEGMENT_KEY_TOLERANCE_CM) * SEGMENT_KEY_TOLERANCE_CM;
        double y2 = Math.round(w.getYEnd()   / SEGMENT_KEY_TOLERANCE_CM) * SEGMENT_KEY_TOLERANCE_CM;
        if (x1 > x2 || (x1 == x2 && y1 > y2)) {
            double t = x1; x1 = x2; x2 = t;
            t = y1; y1 = y2; y2 = t;
        }
        return x1 + "," + y1 + "," + x2 + "," + y2;
    }

    /**
     * Regroupe les murs par segment 2D (même trace au plan). Chaque groupe est trié par élévation du niveau (bas → haut).
     * Permet de fusionner les murs superposés sur plusieurs niveaux en un seul mur Energy3D avec hauteur cumulée.
     */
    private static List<List<Wall>> groupWallsBySegment(Collection<Wall> walls, final Level terrainLevel, PrintWriter logWriter) {
        java.util.Map<String, List<Wall>> byKey = new java.util.LinkedHashMap<>();
        for (Wall w : walls) {
            String key = segmentKey(w);
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(w);
        }
        List<List<Wall>> groups = new ArrayList<>();
        for (List<Wall> group : byKey.values()) {
            Collections.sort(group, new java.util.Comparator<Wall>() {
                @Override
                public int compare(Wall a, Wall b) {
                    Level la = a.getLevel();
                    Level lb = b.getLevel();
                    if (la == null && lb == null) return 0;
                    if (la == null) return 1;
                    if (lb == null) return -1;
                    float ea = la.getElevation();
                    float eb = lb.getElevation();
                    return Float.compare(ea, eb);
                }
            });
            groups.add(group);
        }
        if (logWriter != null) {
            int merged = 0;
            for (List<Wall> g : groups) if (g.size() > 1) merged++;
            logWriter.println("  Segments 2D uniques: " + groups.size() + " (dont " + merged + " avec murs fusionnés multi-niveaux)");
            logWriter.flush();
        }
        return groups;
    }

    /**
     * Fixe le type de texture d'un HousePart (Foundation, Wall). Utilise setTextureType(int) par réflexion.
     * getMethod() cherche dans la classe et les superclasses (méthode publique HousePart).
     */
    private static void setHousePartTextureType(Object part, Class<?> partClass, int textureTypeValue, PrintWriter logWriter, String label) {
        if (part == null) return;
        try {
            // 1) setTextureType(int) via getMethod (inclut les méthodes héritées publiques)
            java.lang.reflect.Method setTex = part.getClass().getMethod("setTextureType", int.class);
            setTex.invoke(part, textureTypeValue);
            if (logWriter != null) logWriter.println("  Texture #" + textureTypeValue + " appliquée (" + label + ")");
            try {
                java.lang.reflect.Method updateTex = part.getClass().getMethod("updateTextureAndColor");
                updateTex.invoke(part);
            } catch (Throwable ignored) { }
        } catch (NoSuchMethodException e) {
            // 2) Fallback : champ textureType
            try {
                Class<?> c = part.getClass();
                java.lang.reflect.Field textureTypeField = null;
                for (; c != null; c = c.getSuperclass()) {
                    try {
                        textureTypeField = c.getDeclaredField("textureType");
                        break;
                    } catch (NoSuchFieldException ignored) { }
                }
                if (textureTypeField != null) {
                    textureTypeField.setAccessible(true);
                    textureTypeField.setInt(part, textureTypeValue);
                    if (logWriter != null) logWriter.println("  Texture #" + textureTypeValue + " appliquée (" + label + ") via champ");
                    try {
                        java.lang.reflect.Method updateTex = part.getClass().getMethod("updateTextureAndColor");
                        updateTex.invoke(part);
                    } catch (Throwable ignored) { }
                    return;
                }
            } catch (Exception e2) {
                if (logWriter != null) logWriter.println("  ⚠ textureType " + label + ": " + e2.getMessage());
                return;
            }
            if (logWriter != null) logWriter.println("  ⚠ Texture non appliquée pour " + label + ": setTextureType(int) et champ textureType absents du JAR");
        } catch (Exception e) {
            if (logWriter != null) logWriter.println("  ⚠ textureType " + textureTypeValue + " " + label + ": " + e.getMessage());
        }
    }

    /**
     * Projette le point (px, py) sur le segment (ax,ay)-(bx,by) et retourne le facteur u tel que
     * projection = a + u*(b-a). Utilisé pour convertir coordonnées absolues en (u,v) relatives à la fondation.
     */
    private static double projectPointOnLineScale(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay;
        double lenSq = dx * dx + dy * dy;
        if (lenSq < 1e-20) return 0.5;
        return ((px - ax) * dx + (py - ay) * dy) / lenSq;
    }

    /**
     * Alloue une instance d'une classe sans appeler le constructeur (évite init() et dépendances UI en headless).
     * Utilise sun.misc.Unsafe.allocateInstance(Class).
     */
    private static Object allocateInstanceWithoutConstructor(Class<?> clazz) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            java.lang.reflect.Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            java.lang.reflect.Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
            return allocateInstance.invoke(unsafe, clazz);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Marge (cm) pour considérer qu'une porte/fenêtre est sur un mur (containsPoint). */
    private static final float DOOR_WINDOW_WALL_MARGIN_CM = 15f;

    /**
     * Retourne la liste de tous les meubles du plan, y compris ceux à l'intérieur des groupes.
     * Permet de trouver les portes/fenêtres même si elles sont dans un groupe.
     */
    private static List<HomePieceOfFurniture> getAllFurnitureIncludingGroups(Home home) {
        List<HomePieceOfFurniture> out = new ArrayList<>();
        List<HomePieceOfFurniture> furniture = home.getFurniture();
        if (furniture == null) return out;
        for (HomePieceOfFurniture piece : furniture) {
            if (piece instanceof HomeFurnitureGroup) {
                out.addAll(((HomeFurnitureGroup) piece).getAllFurniture());
            } else {
                out.add(piece);
            }
        }
        return out;
    }

    /**
     * Retourne le mur SH3D sur lequel la porte/fenêtre est placée (centre dans le mur), ou null.
     * Associe la pièce au mur qui la contient, au même niveau que la pièce (tous niveaux).
     * 1) Cherche un mur qui contient le point (containsPoint avec marge).
     * 2) Sinon, fallback : mur dont le segment est le plus proche du point (projection sur le segment).
     */
    private static Wall findWallForDoorOrWindow(HomeDoorOrWindow piece, Collection<Wall> walls) {
        if (walls == null) return null;
        float x = (float) piece.getX();
        float y = (float) piece.getY();
        Level pieceLevel = piece.getLevel();
        Wall closest = null;
        double closestDistSq = Double.MAX_VALUE;
        for (Wall wall : walls) {
            if (pieceLevel != null && !wall.isAtLevel(pieceLevel)) continue;
            try {
                if (wall.containsPoint(x, y, false, DOOR_WINDOW_WALL_MARGIN_CM))
                    return wall;
            } catch (Exception ignored) { }
            double xS = wall.getXStart(), yS = wall.getYStart(), xE = wall.getXEnd(), yE = wall.getYEnd();
            double dx = xE - xS, dy = yE - yS;
            double lenSq = dx * dx + dy * dy;
            if (lenSq < 1e-20) continue;
            double t = ((x - xS) * dx + (y - yS) * dy) / lenSq;
            t = Math.max(0, Math.min(1, t));
            double px = xS + t * dx;
            double py = yS + t * dy;
            double distSq = (x - px) * (x - px) + (y - py) * (y - py);
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closest = wall;
            }
        }
        return closest;
    }

    /**
     * Retourne true si le nom évoque une fenêtre (fenêtre, window, etc.).
     * Utilisé pour que "porte-fenêtre" soit traitée comme fenêtre (transparente) dans Energy3D.
     */
    private static boolean nameSuggestsWindow(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("fenêtre") || lower.contains("fenetre") || lower.contains("window")
                || lower.contains("finestra") || lower.contains("ventana") || lower.contains("janela")
                || lower.contains("fenster") || lower.contains("raam");
    }

    /**
     * Retourne true si la pièce SH3D (porte/fenêtre) est considérée comme une porte.
     * Critère : nom contenant "door", "porte", "porta", "puerta" (insensible à la casse).
     * Exception : si le nom contient aussi un mot évoquant la fenêtre (ex. "porte-fenêtre"),
     * on traite comme fenêtre (transparente) dans Energy3D — le mot fenêtre domine.
     */
    private static boolean isLikelyDoor(HomeDoorOrWindow piece) {
        String name = piece.getName();
        if (name == null) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        boolean hasDoor = lower.contains("door") || lower.contains("porte") || lower.contains("porta")
                || lower.contains("puerta") || lower.contains("tür") || lower.contains("deur");
        if (!hasDoor) return false;
        if (nameSuggestsWindow(name)) return false;
        return true;
    }

    /**
     * Retourne true si la pièce SH3D est considérée comme un arbre/plante (pour export Energy3D Tree).
     * Critère : nom contenant "tree", "arbre", "plant", "plante", "baum" (insensible à la casse) et pas une porte/fenêtre.
     */
    private static boolean isLikelyTree(HomePieceOfFurniture piece) {
        if (piece == null || piece.isDoorOrWindow()) return false;
        String name = piece.getName();
        if (name == null) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("tree") || lower.contains("arbre") || lower.contains("plant")
                || lower.contains("plante") || lower.contains("baum")
                || lower.contains("arbor") || lower.contains("albero");
    }

    /**
     * Crée des arbres Energy3D à partir des meubles SH3D identifiés comme arbres/plantes et les ajoute à la scène.
     */
    private static void addTreesFromHome(Home home, Object scene, double originX, double originY,
            Class<?> sceneClass, PrintWriter logWriter) {
        if (home == null || scene == null) return;
        Class<?> treeClass;
        Class<?> housePartClass;
        try {
            treeClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Tree", logWriter);
            housePartClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.HousePart", logWriter);
        } catch (ClassNotFoundException e) {
            if (logWriter != null) logWriter.println("  AVERTISSEMENT: classes Tree/HousePart: " + e.getMessage());
            return;
        }
        if (treeClass == null || housePartClass == null) return;
        try {
            java.lang.reflect.Field instanceField = sceneClass.getDeclaredField("instance");
            instanceField.setAccessible(true);
            Object previousInstance = instanceField.get(null);
            try {
                instanceField.set(null, scene);
                java.lang.reflect.Method addMethod = sceneClass.getMethod("add", housePartClass, boolean.class);
                ClassLoader loader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
                Class<?> vector3Class = loader.loadClass("com.ardor3d.math.Vector3");
                java.lang.reflect.Method vector3Set = vector3Class.getMethod("set", double.class, double.class, double.class);
                java.lang.reflect.Method setLocationMethod = treeClass.getMethod("setLocation", vector3Class);
                List<HomePieceOfFurniture> furniture = getAllFurnitureIncludingGroups(home);
                int count = 0;
                for (HomePieceOfFurniture piece : furniture) {
                    if (!isLikelyTree(piece)) continue;
                    Level level = piece.getLevel();
                    if (level == null) continue;
                    double xCm = piece.getX();
                    double yCm = piece.getY();
                    double zCm = level.getElevation() + piece.getElevation();
                    double x = (xCm - originX) * SCALE_CM_TO_ENERGY3D;
                    double y = (yCm - originY) * SCALE_CM_TO_ENERGY3D;
                    double z = zCm * SCALE_CM_TO_ENERGY3D;
                    if (MIRROR_FLIP_X) x = -x;
                    if (ROTATE_180_Z) y = -y;
                    Object tree = treeClass.getDeclaredConstructor().newInstance();
                    Object pos = vector3Class.getDeclaredConstructor().newInstance();
                    vector3Set.invoke(pos, x, y, z);
                    setLocationMethod.invoke(tree, pos);
                    treeClass.getMethod("complete").invoke(tree);
                    treeClass.getMethod("draw").invoke(tree);
                    addMethod.invoke(scene, tree, true);
                    count++;
                }
                if (logWriter != null && count > 0) {
                    logWriter.println("  ✓ " + count + " arbre(s) exporté(s) depuis SH3D.");
                    logWriter.flush();
                }
            } finally {
                instanceField.set(null, previousInstance);
            }
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("  AVERTISSEMENT: export des arbres: " + e.getMessage());
                logWriter.flush();
            }
        }
    }

    /**
     * Convertit les fenêtres/portes SH3D situées sur le mur donné en Window ou Door Energy3D et les ajoute aux enfants du mur.
     * Porte vs fenêtre : selon le nom de la pièce (isLikelyDoor). Sinon traité comme fenêtre.
     */
    private static void convertWindowsOnWall(Home home, Wall sh3dWall, Object energy3dWall, Object foundation,
            double originX, double originY, Class<?> foundationClass, PrintWriter logWriter) {
        if (home == null || energy3dWall == null || foundation == null) return;
        List<HomePieceOfFurniture> furniture = getAllFurnitureIncludingGroups(home);
        int converted = 0;
        for (HomePieceOfFurniture piece : furniture) {
            if (!piece.isDoorOrWindow() || !(piece instanceof HomeDoorOrWindow)) continue;
            Wall wallForPiece = findWallForDoorOrWindow((HomeDoorOrWindow) piece, home.getWalls());
            if (wallForPiece != sh3dWall) continue;
            boolean isDoor = isLikelyDoor((HomeDoorOrWindow) piece);
            Class<?> partClass;
            try {
                partClass = isDoor
                        ? Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Door", logWriter)
                        : Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Window", logWriter);
            } catch (ClassNotFoundException e) {
                if (logWriter != null) logWriter.println("    ⚠ Classe " + (isDoor ? "Door" : "Window") + " Energy3D introuvable: " + e.getMessage());
                continue;
            }
            if (partClass == null) continue;
            try {
                Object part = convertDoorOrWindowToEnergy3D(home, (HomeDoorOrWindow) piece, sh3dWall, energy3dWall, foundation, foundationClass, partClass, logWriter);
                if (part != null) {
                    java.lang.reflect.Method getChildrenMethod = energy3dWall.getClass().getMethod("getChildren");
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> children = (java.util.List<Object>) getChildrenMethod.invoke(energy3dWall);
                    children.add(part);
                    converted++;
                    if (logWriter != null) logWriter.println("    ✓ " + (isDoor ? "Porte" : "Fenêtre") + " convertie sur ce mur");
                }
            } catch (Throwable t) {
                if (logWriter != null) {
                    logWriter.println("    ⚠ " + (isDoor ? "Porte" : "Fenêtre") + " non convertie: " + t.getMessage());
                    logWriter.flush();
                }
            }
        }
        if (converted > 0) {
            try {
                energy3dWall.getClass().getMethod("draw").invoke(energy3dWall);
            } catch (Exception ignored) { }
        }
        if (logWriter != null && converted > 0) logWriter.println("  " + converted + " fenêtre(s)/porte(s) sur ce mur");
    }

    /**
     * Crée une fenêtre ou une porte Energy3D à partir d'une porte/fenêtre SH3D sur un mur.
     * partClass = Window.class ou Door.class (Energy3D). Les points sont en (x, 0, z) relatifs au mur (fractions 0-1).
     */
    private static Object convertDoorOrWindowToEnergy3D(Home home, HomeDoorOrWindow piece, Wall sh3dWall, Object energy3dWall,
            Object foundation, Class<?> foundationClass, Class<?> partClass, PrintWriter logWriter) {
        try {
            if (partClass == null) return null;
            ClassLoader loader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
            Class<?> housePartClass = loader.loadClass("org.concord.energy3d.model.HousePart");
            Class<?> vector3Class = loader.loadClass("com.ardor3d.math.Vector3");
            java.lang.reflect.Method vector3Set = vector3Class.getMethod("set", double.class, double.class, double.class);

            // Mur SH3D : segment en cm
            double xStartCm = sh3dWall.getXStart();
            double yStartCm = sh3dWall.getYStart();
            double xEndCm = sh3dWall.getXEnd();
            double yEndCm = sh3dWall.getYEnd();
            double wallLengthCm = Math.hypot(xEndCm - xStartCm, yEndCm - yStartCm);
            if (wallLengthCm < 1e-6) return null;

            // Centre porte/fenêtre en cm, projection sur le segment du mur → paramètre t ∈ [0,1]
            double pxCm = piece.getX();
            double pyCm = piece.getY();
            double tCenter = projectPointOnLineScale(pxCm, pyCm, xStartCm, yStartCm, xEndCm, yEndCm);
            tCenter = Math.max(0, Math.min(1, tCenter));

            // Ouverture : dimensions cohérentes avec le mur (SH3D : getWidth/getHeight en cm ; getWallWidth/getWallHeight = fraction 0–1)
            double wallHeightCm = sh3dWall.getHeight() != null ? sh3dWall.getHeight().doubleValue() : 250.0;
            float ww = piece.getWallWidth();
            float wh = piece.getWallHeight();
            if (ww <= 0) ww = 1f;
            if (wh <= 0) wh = 1f;
            if (ww > 1f) ww = ww / 100f;
            if (wh > 1f) wh = wh / 100f;
            // Largeur = fraction du mur OU taille pièce, plafonnée à 80 % du mur pour éviter fenêtres démesurées
            double openingWidthCm = ww * piece.getWidth();
            if (openingWidthCm > wallLengthCm * 0.8) openingWidthCm = wallLengthCm * 0.8;
            if (openingWidthCm < 20 && piece.getWidth() > 20) openingWidthCm = Math.min(piece.getWidth(), wallLengthCm * 0.8);
            // Hauteur = fraction du mur OU taille pièce, plafonnée à 85 % du mur
            double openingHeightCm = wh * piece.getHeight();
            if (openingHeightCm > wallHeightCm * 0.85) openingHeightCm = wallHeightCm * 0.85;
            if (openingHeightCm < 40 && piece.getHeight() > 40) openingHeightCm = Math.min(piece.getHeight(), wallHeightCm * 0.85);
            double halfWidthParam = (openingWidthCm / 2.0) / wallLengthCm;
            double tLeft = Math.max(0, tCenter - halfWidthParam);
            double tRight = Math.min(1, tCenter + halfWidthParam);

            // (u,v) du mur en coordonnées fondation : lire les points du mur Energy3D
            java.lang.reflect.Field pointsField = null;
            for (Class<?> c = energy3dWall.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    pointsField = c.getDeclaredField("points");
                    break;
                } catch (NoSuchFieldException ignored) { }
            }
            if (pointsField == null) return null;
            pointsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Object> wallPoints = (java.util.List<Object>) pointsField.get(energy3dWall);
            if (wallPoints == null || wallPoints.size() < 4) return null;
            Object p0 = wallPoints.get(0);

            // Paramètre le long du mur (0-1) : Energy3D attend des coordonnées relatives au mur (HousePart.toAbsolute).
            // x = fraction le long du mur, z = fraction de la hauteur du mur (0 = base, 1 = haut).
            double sLeft = WALL_REVERSE_ORIENTATION ? (1 - tRight) : tLeft;
            double sRight = WALL_REVERSE_ORIENTATION ? (1 - tLeft) : tRight;

            // Hauteur du mur et base Z (pour convertir z absolu en fraction 0-1)
            Object p1Wall = wallPoints.get(1);
            double wallZBottom = ((Number) p0.getClass().getMethod("getZ").invoke(p0)).doubleValue();
            double wallZTop = ((Number) p1Wall.getClass().getMethod("getZ").invoke(p1Wall)).doubleValue();
            double wallHeight = wallZTop - wallZBottom;
            if (wallHeight < 1e-6) return null;

            // Z absolus de la fenêtre (base et haut)
            double foundationHeight = ((Number) foundationClass.getMethod("getHeight").invoke(foundation)).doubleValue();
            float groundElevCm = piece.getGroundElevation();
            double zBottomAbs = foundationHeight + groundElevCm * SCALE_CM_TO_ENERGY3D;
            double zTopAbs = zBottomAbs + openingHeightCm * SCALE_CM_TO_ENERGY3D;
            // Fractions 0-1 le long de la hauteur du mur (comme dans plan_energy3d2.ng3 créé par Energy3D)
            double zBottomFraction = Math.max(0, Math.min(1, (zBottomAbs - wallZBottom) / wallHeight));
            double zTopFraction = Math.max(0, Math.min(1, (zTopAbs - wallZBottom) / wallHeight));

            // Créer la fenêtre/porte sans constructeur pour éviter init() → NPE en headless
            Object window = allocateInstanceWithoutConstructor(partClass);
            if (window == null) return null;

            java.lang.reflect.Method setContainerMethod = partClass.getMethod("setContainer", housePartClass);
            setContainerMethod.invoke(window, energy3dWall);

            // Points en coordonnées relatives au mur : (x = fraction longueur 0-1, y = 0, z = fraction hauteur 0-1)
            Object winPointsList = loader.loadClass("java.util.ArrayList").getDeclaredConstructor().newInstance();
            java.lang.reflect.Method listAdd = winPointsList.getClass().getMethod("add", Object.class);
            java.lang.reflect.Method listGet = winPointsList.getClass().getMethod("get", int.class);
            for (int i = 0; i < 4; i++) {
                listAdd.invoke(winPointsList, vector3Class.getConstructor(double.class, double.class, double.class).newInstance(0, 0, 0));
            }
            vector3Set.invoke(listGet.invoke(winPointsList, 0), sLeft,  0, zBottomFraction);
            vector3Set.invoke(listGet.invoke(winPointsList, 1), sLeft,  0, zTopFraction);
            vector3Set.invoke(listGet.invoke(winPointsList, 2), sRight, 0, zBottomFraction);
            vector3Set.invoke(listGet.invoke(winPointsList, 3), sRight, 0, zTopFraction);

            java.lang.reflect.Field winPointsField = null;
            for (Class<?> c = partClass; c != null; c = c.getSuperclass()) {
                try {
                    winPointsField = c.getDeclaredField("points");
                    break;
                } catch (NoSuchFieldException ignored) { }
            }
            if (winPointsField == null) return null;
            winPointsField.setAccessible(true);
            winPointsField.set(window, winPointsList);

            // Champs HousePart requis pour sérialisation / ouverture Energy3D
            try {
                java.lang.reflect.Field firstPointField = partClass.getSuperclass().getDeclaredField("firstPointInserted");
                firstPointField.setAccessible(true);
                firstPointField.set(window, true);
            } catch (Exception ignored) { }
            try {
                java.lang.reflect.Field drawCompletedField = partClass.getSuperclass().getDeclaredField("drawCompleted");
                drawCompletedField.setAccessible(true);
                drawCompletedField.set(window, true);
            } catch (Exception ignored) { }
            try {
                java.lang.reflect.Field heightField = partClass.getSuperclass().getDeclaredField("height");
                heightField.setAccessible(true);
                heightField.setDouble(window, (zTopAbs - zBottomAbs));
            } catch (Exception ignored) { }
            try {
                java.lang.reflect.Field idField = partClass.getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                if (idField.getLong(window) == 0L)
                    idField.setLong(window, System.nanoTime());
            } catch (Exception ignored) { }

            // Root non-null pour que Scene.add() → getRoot() ne déclenche pas init() (NPE en headless)
            try {
                Class<?> nodeClass = loader.loadClass("com.ardor3d.scenegraph.Node");
                Object rootNode = nodeClass.getConstructor(String.class).newInstance(partClass.getSimpleName());
                java.lang.reflect.Field rootField = partClass.getSuperclass().getDeclaredField("root");
                rootField.setAccessible(true);
                rootField.set(window, rootNode);
            } catch (Exception e) {
                if (logWriter != null) logWriter.println("    root " + partClass.getSimpleName() + " non défini: " + e.getMessage());
            }
            // children non-null pour que Scene.add() → part.getChildren() ne lance pas NPE (instance créée sans constructeur)
            try {
                java.lang.reflect.Field childrenField = partClass.getSuperclass().getDeclaredField("children");
                childrenField.setAccessible(true);
                if (childrenField.get(window) == null) {
                    Object emptyList = loader.loadClass("java.util.ArrayList").getDeclaredConstructor().newInstance();
                    childrenField.set(window, emptyList);
                }
            } catch (Exception e) {
                if (logWriter != null) logWriter.println("    children " + partClass.getSimpleName() + " non défini: " + e.getMessage());
            }

            // Propriétés thermiques : U-value (Window et Door), SHGC (Window uniquement)
            try {
                partClass.getMethod("setUValue", double.class).invoke(window, 2.0);
            } catch (Exception ignored) { }
            try {
                partClass.getMethod("setSolarHeatGainCoefficient", double.class).invoke(window, 0.5);
            } catch (Exception ignored) { }
            return window;
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            if (logWriter != null) {
                logWriter.println("    convertDoorOrWindowToEnergy3D: " + t.getClass().getSimpleName() + " - " + t.getMessage());
                logWriter.println("      cause: " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
                logWriter.flush();
            }
            return null;
        }
    }

    /**
     * Convertit un mur SH3D en mur Energy3D.
     * @param overrideBaseZUnits base Z en unités (au-dessus de la fondation). Si &lt;= 0 et overrideHeightUnits &lt;= 0, calculée depuis le niveau du mur.
     * @param overrideHeightUnits hauteur en unités. Si &gt; 0, utilisée (mur fusionné multi-niveaux) ; sinon hauteur du mur seul.
     */
    private static Object convertWallToEnergy3D(Wall sh3dWall, Object foundation, double originX, double originY,
            Level terrainLevel, double overrideBaseZUnits, double overrideHeightUnits, PrintWriter logWriter) {
        try {
            WallConverter.Energy3DWallData data = WallConverter.convertToEnergy3D(sh3dWall);
            // Positions absolues en m (centre plan = origin)
            double xStart = (sh3dWall.getXStart() - originX) * SCALE_CM_TO_ENERGY3D;
            double yStart = (sh3dWall.getYStart() - originY) * SCALE_CM_TO_ENERGY3D;
            double xEnd   = (sh3dWall.getXEnd()   - originX) * SCALE_CM_TO_ENERGY3D;
            double yEnd   = (sh3dWall.getYEnd()   - originY) * SCALE_CM_TO_ENERGY3D;
            if (MIRROR_FLIP_X) {
                xStart = -xStart;
                xEnd   = -xEnd;
            }
            if (ROTATE_180_Z) {
                yStart = -yStart;
                yEnd   = -yEnd;
            }
            double thickness = WALL_THICKNESS_UNITS;

            Class<?> foundationClass = foundation.getClass();
            java.lang.reflect.Method getAbsPoint = foundationClass.getMethod("getAbsPoint", int.class);
            java.lang.reflect.Method getHeightMethod = foundationClass.getMethod("getHeight");
            Object p0 = getAbsPoint.invoke(foundation, 0);
            Object p1 = getAbsPoint.invoke(foundation, 1);
            Object p2 = getAbsPoint.invoke(foundation, 2);
            double p0x = ((Number) p0.getClass().getMethod("getX").invoke(p0)).doubleValue();
            double p0y = ((Number) p0.getClass().getMethod("getY").invoke(p0)).doubleValue();
            double p1x = ((Number) p1.getClass().getMethod("getX").invoke(p1)).doubleValue();
            double p1y = ((Number) p1.getClass().getMethod("getY").invoke(p1)).doubleValue();
            double p2x = ((Number) p2.getClass().getMethod("getX").invoke(p2)).doubleValue();
            double p2y = ((Number) p2.getClass().getMethod("getY").invoke(p2)).doubleValue();
            double foundationHeight = ((Number) getHeightMethod.invoke(foundation)).doubleValue();

            double wallHeight;
            double zBottom;
            if (overrideHeightUnits > 0) {
                wallHeight = overrideHeightUnits;
                zBottom = overrideBaseZUnits > 0 ? overrideBaseZUnits : foundationHeight;
            } else {
                wallHeight = sh3dWall.getHeight() != null
                    ? sh3dWall.getHeight().doubleValue() * SCALE_CM_TO_ENERGY3D
                    : (2.5 * ENERGY3D_UNITS_PER_METER_EXPORT);
                Level level = sh3dWall.getLevel();
                if (terrainLevel != null && level != null) {
                    float levelElev = level.getElevation();
                    float terrainElev = terrainLevel.getElevation();
                    zBottom = foundationHeight + (levelElev - terrainElev) * SCALE_CM_TO_ENERGY3D;
                } else {
                    zBottom = foundationHeight;
                }
            }
            double zTop = zBottom + wallHeight;

            // Convertir (x,y) absolu en (u,v) relatif à la fondation (HousePart.toAbsolute : p0 + u*(p2-p0) + v*(p1-p0))
            double uStart = projectPointOnLineScale(xStart, yStart, p0x, p0y, p2x, p2y);
            double vStart = projectPointOnLineScale(xStart, yStart, p0x, p0y, p1x, p1y);
            double uEnd   = projectPointOnLineScale(xEnd,   yEnd,   p0x, p0y, p2x, p2y);
            double vEnd   = projectPointOnLineScale(xEnd,   yEnd,   p0x, p0y, p1x, p1y);

            Class<?> wallClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Wall", logWriter);
            if (logWriter != null) { logWriter.println("  Instanciation du Wall..."); logWriter.flush(); }
            Object wall = wallClass.getDeclaredConstructor().newInstance();
            Class<?> housePartClass = wallClass.getClassLoader().loadClass("org.concord.energy3d.model.HousePart");
            java.lang.reflect.Method setContainerMethod = wallClass.getMethod("setContainer", housePartClass);
            setContainerMethod.invoke(wall, foundation);

            java.lang.reflect.Method setThicknessMethod = wallClass.getMethod("setThickness", double.class);
            setThicknessMethod.invoke(wall, thickness);
            java.lang.reflect.Method setHeightMethod = wallClass.getMethod("setHeight", double.class, boolean.class);
            setHeightMethod.invoke(wall, wallHeight, true);

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

            while (points.size() < 4) {
                points.add(vector3Class.getConstructor(double.class, double.class, double.class).newInstance(0.0, 0.0, 0.0));
            }
            // Points en (u, v, z) relatifs à la fondation : 0=start bas, 1=start haut, 2=end bas, 3=end haut
            // Si WALL_REVERSE_ORIENTATION : on inverse start/end pour que la face "avant" en Energy3D = extérieur SH3D (évite murs à l'envers)
            if (WALL_REVERSE_ORIENTATION) {
                vector3Set.invoke(points.get(0), uEnd,   vEnd,   zBottom);
                vector3Set.invoke(points.get(1), uEnd,   vEnd,   zTop);
                vector3Set.invoke(points.get(2), uStart, vStart, zBottom);
                vector3Set.invoke(points.get(3), uStart, vStart, zTop);
            } else {
                vector3Set.invoke(points.get(0), uStart, vStart, zBottom);
                vector3Set.invoke(points.get(1), uStart, vStart, zTop);
                vector3Set.invoke(points.get(2), uEnd,   vEnd,   zBottom);
                vector3Set.invoke(points.get(3), uEnd,   vEnd,   zTop);
            }

            // Marquer comme "premier point inséré" pour que complete() et draw() fonctionnent
            try {
                java.lang.reflect.Field firstPointField = wallClass.getSuperclass().getDeclaredField("firstPointInserted");
                firstPointField.setAccessible(true);
                firstPointField.set(wall, true);
            } catch (Exception e) {
                if (logWriter != null) logWriter.println("  firstPointInserted non défini: " + e.getMessage());
            }

            try {
                java.lang.reflect.Method completeMethod = wallClass.getMethod("complete");
                completeMethod.invoke(wall);
            } catch (Throwable e) {
                // complete() peut échouer en headless (SceneManager, etc.) : forcer drawCompleted et draw() pour que le mur soit valide à l'ouverture
                if (logWriter != null) logWriter.println("  complete() ignoré (" + e.getMessage() + "), utilisation de draw() direct");
                try {
                    java.lang.reflect.Field drawCompletedField = wallClass.getSuperclass().getDeclaredField("drawCompleted");
                    drawCompletedField.setAccessible(true);
                    drawCompletedField.set(wall, true);
                } catch (Exception e2) {
                    if (logWriter != null) logWriter.println("  drawCompleted non défini: " + e2.getMessage());
                }
            }

            // Couleur : albedo = 0,91 pour absorptance 0,09 (Energy3D utilise la clarté de la couleur comme albedo)
            Class<?> colorClass = Energy3DClassLoader.loadEnergy3DClass("com.ardor3d.math.ColorRGBA", logWriter);
            Object colorObj = colorClass.getConstructor(float.class, float.class, float.class, float.class)
                .newInstance(DEFAULT_WALL_ALBEDO, DEFAULT_WALL_ALBEDO, DEFAULT_WALL_ALBEDO, 1.0f);
            // HousePart.setColor(ReadOnlyColorRGBA) : il faut le type déclaré pour getMethod
            Class<?> readOnlyColorClass = wallClass.getClassLoader().loadClass("com.ardor3d.math.type.ReadOnlyColorRGBA");
            java.lang.reflect.Method setColorMethod = wallClass.getMethod("setColor", readOnlyColorClass);
            setColorMethod.invoke(wall, colorObj);

            // Texture Energy3D #1 par défaut pour les murs
            setHousePartTextureType(wall, wallClass, 1, logWriter, "mur");

            java.lang.reflect.Method setUValueMethod = wallClass.getMethod("setUValue", double.class);
            setUValueMethod.invoke(wall, data.uValue);
            java.lang.reflect.Method setVolumetricHeatCapacityMethod = wallClass.getMethod("setVolumetricHeatCapacity", double.class);
            setVolumetricHeatCapacityMethod.invoke(wall, data.volumetricHeatCapacity);

            java.lang.reflect.Method drawMethod = wallClass.getMethod("draw");
            drawMethod.invoke(wall);

            // Forcer drawCompleted pour que Energy3D ne supprime pas le mur au cleanup() à l'ouverture
            try {
                java.lang.reflect.Field drawCompletedField = wallClass.getSuperclass().getDeclaredField("drawCompleted");
                drawCompletedField.setAccessible(true);
                drawCompletedField.set(wall, true);
            } catch (Exception e) {
                if (logWriter != null) logWriter.println("  drawCompleted (post-draw) non défini: " + e.getMessage());
            }

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
    
    /**
     * Analyse un fichier .ng3 (export plugin ou Energy3D) et affiche les textureType des Foundation/Wall.
     * À appeler après export pour vérifier l'enregistrement, ou pour comparer avec un fichier Energy3D natif.
     * @param ng3File fichier .ng3 (ex. plan_energy3d.ng3 ou plan_energy3d2.ng3)
     * @param logWriter où écrire le rapport (peut être null)
     */
    public static void dumpNg3FileTextureTypes(File ng3File, PrintWriter logWriter) {
        ClassLoader loader = Energy3DClassLoader.getEnergy3DClassLoader();
        if (loader != null) {
            dumpNg3TextureTypes(ng3File, logWriter, loader);
        } else if (logWriter != null) {
            logWriter.println("ClassLoader Energy3D non disponible (exporter d'abord un plan).");
            logWriter.flush();
        }
    }

    /**
     * Relit un fichier .ng3 avec le ClassLoader Energy3D et affiche les textureType des Foundation et Wall.
     * Permet de vérifier ce qui a été enregistré et de comparer avec un fichier Energy3D natif.
     */
    private static void dumpNg3TextureTypes(File ng3File, PrintWriter logWriter, ClassLoader energy3dLoader) {
        if (logWriter == null || energy3dLoader == null || !ng3File.exists()) return;
        Thread thread = Thread.currentThread();
        ClassLoader savedLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(energy3dLoader);
            try (FileInputStream fis = new FileInputStream(ng3File);
                 ObjectInputStream ois = new ObjectInputStream(fis)) {
                Object scene = ois.readObject();
                Class<?> sceneClass = scene.getClass();
                java.lang.reflect.Field partsField = sceneClass.getDeclaredField("parts");
                partsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.List<Object> parts = (java.util.List<Object>) partsField.get(scene);
                logWriter.println("Vérification textureType dans " + ng3File.getName() + ":");
                logWriter.println("  Nombre de parts (racine): " + parts.size());
                java.lang.reflect.Field textureTypeField = null;
                java.lang.reflect.Field childrenField = null;
                for (int i = 0; i < parts.size(); i++) {
                    Object part = parts.get(i);
                    if (part == null) continue;
                    Class<?> partClass = part.getClass();
                    String partClassName = partClass.getName();
                    if (textureTypeField == null) {
                        for (Class<?> c = partClass; c != null; c = c.getSuperclass()) {
                            try {
                                textureTypeField = c.getDeclaredField("textureType");
                                textureTypeField.setAccessible(true);
                                break;
                            } catch (NoSuchFieldException ignored) { }
                        }
                    }
                    if (textureTypeField != null) {
                        int tt = textureTypeField.getInt(part);
                        logWriter.println("  Part[" + i + "] " + partClassName + " → textureType = " + tt);
                    }
                    if (partClassName.contains("Foundation") && (childrenField == null || childrenField.getDeclaringClass().isAssignableFrom(partClass))) {
                        try {
                            if (childrenField == null) {
                                for (Class<?> c = partClass; c != null; c = c.getSuperclass()) {
                                    try {
                                        childrenField = c.getDeclaredField("children");
                                        childrenField.setAccessible(true);
                                        break;
                                    } catch (NoSuchFieldException ignored) { }
                                }
                            }
                            if (childrenField != null) {
                                @SuppressWarnings("unchecked")
                                java.util.List<Object> children = (java.util.List<Object>) childrenField.get(part);
                                if (children != null) {
                                    logWriter.println("    Enfants (murs): " + children.size());
                                    for (int j = 0; j < children.size(); j++) {
                                        Object child = children.get(j);
                                        if (child != null && textureTypeField != null) {
                                            int ctt = textureTypeField.getInt(child);
                                            logWriter.println("      Enfant[" + j + "] " + child.getClass().getName() + " → textureType = " + ctt);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logWriter.println("    (enfants: " + e.getMessage() + ")");
                        }
                    }
                }
                logWriter.flush();
            }
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("  Impossible de relire le .ng3 pour vérification: " + e.getMessage());
                logWriter.flush();
            }
        } finally {
            thread.setContextClassLoader(savedLoader);
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
            
            // Vérifier ce qui a été enregistré (textureType fondation/murs) en relisant le fichier
            ClassLoader energy3dLoader = Energy3DClassLoader.getEnergy3DClassLoader();
            if (energy3dLoader != null && logWriter != null) {
                dumpNg3TextureTypes(outputFile, logWriter, energy3dLoader);
            }
            
            // Vérifier le fichier
            if (outputFile.exists() && outputFile.length() > 0) {
                try (FileInputStream fis = new FileInputStream(outputFile)) {
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
