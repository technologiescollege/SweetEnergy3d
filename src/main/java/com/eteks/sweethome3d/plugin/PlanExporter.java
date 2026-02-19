package com.eteks.sweethome3d.plugin;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.tools.URLContent;
import com.eteks.sweethome3d.tools.TemporaryURLContent;

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
    
    /** Clés de messages de validation (à résoudre via ResourceBundle du plugin). */
    public static final String VALIDATION_KEY_NO_PLAN = "msg.validation.no_plan";
    public static final String VALIDATION_KEY_NO_TERRAIN = "msg.validation.no_terrain";
    public static final String VALIDATION_KEY_NO_ROOM_ON_TERRAIN = "msg.validation.no_room_on_terrain";
    
    /**
     * Vérifie si le plan peut être exporté vers Energy3D (niveau fondation présent avec au moins une pièce).
     * 
     * @param home Le Home à vérifier
     * @return null si l'export est possible, sinon la clé du message d'erreur à afficher (msg.validation.*)
     */
    public static String getExportValidationError(Home home) {
        if (home == null) return VALIDATION_KEY_NO_PLAN;
        Level foundationLevel = findLevelByCategory(home, "foundation", null);
        if (foundationLevel == null) {
            return VALIDATION_KEY_NO_TERRAIN;
        }
        if (findRoomOnLevel(home, foundationLevel, null) == null) {
            return VALIDATION_KEY_NO_ROOM_ON_TERRAIN;
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
            
            // Fondation = pièce sur le niveau "foundation" (déjà validé : foundation + au moins une pièce)
            double originX = 0.0, originY = 0.0;
            Level foundationLevel = findLevelByCategory(home, "foundation", logWriter);
            Room foundationRoom = findRoomOnLevel(home, foundationLevel, logWriter);
            if (foundationRoom == null) {
                logWriter.println("✗ ERREUR: Aucune pièce sur le niveau 'foundation' (validation incohérente)");
                logWriter.flush();
                return false;
            }
            float[][] rpts = foundationRoom.getPoints();
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
            Object foundation = createFoundationFromRoom(foundationRoom, foundationClass, originX, originY, logWriter);
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
            List<List<Wall>> wallGroups = groupWallsBySegment(sh3dWalls, foundationLevel, logWriter);
            // Ne garder que les murs du périmètre de la pièce fondation si ce filtre laisse au moins un segment (sinon garder tous les murs)
            List<List<Wall>> boundaryFiltered = filterToRoomBoundaryOnly(wallGroups, foundationRoom, logWriter);
            boolean usedBoundaryFallback = boundaryFiltered.isEmpty();
            if (!boundaryFiltered.isEmpty()) {
                wallGroups = boundaryFiltered;
            } else if (logWriter != null) {
                logWriter.println("  Périmètre pièce fondation ne matche aucun segment (niveaux différents ?), export de tous les segments.");
                logWriter.flush();
            }
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
                    float foundationElev = foundationLevel != null ? foundationLevel.getElevation() : 0f;
                    float minElev = Float.MAX_VALUE;
                    double totalHeightCm = 0;
                    for (Wall w : group) {
                        Level l = w.getLevel();
                        if (l != null) minElev = Math.min(minElev, l.getElevation());
                        Float h = w.getHeight();
                        totalHeightCm += (h != null ? h.doubleValue() : 250.0);
                    }
                    if (minElev == Float.MAX_VALUE) minElev = foundationElev;
                    overrideBaseZ = foundationHeightUnits + (minElev - foundationElev) * SCALE_CM_TO_ENERGY3D;
                    overrideHeight = totalHeightCm * SCALE_CM_TO_ENERGY3D;
                }
                try {
                    // Déterminer si le mur est extérieur ou intérieur selon son niveau (config.json)
                    Level wallLevel = representativeWall.getLevel();
                    boolean isExterior = false;
                    if (wallLevel != null) {
                        String levelName = wallLevel.getName();
                        if (ConfigReader.matchesCategory(levelName, "external_wall")) {
                            isExterior = true;
                        } else if (ConfigReader.matchesCategory(levelName, "internal_wall")) {
                            isExterior = false;
                        } else {
                            // Fallback: utiliser l'enveloppe convexe si le niveau n'est pas reconnu
                            java.util.Set<String> convexHullKeys = usedBoundaryFallback
                                    ? convexHullSegmentKeysFromWallGroups(wallGroups)
                                    : convexHullSegmentKeys(foundationRoom);
                            isExterior = convexHullKeys.contains(segmentKey(representativeWall));
                        }
                    }
                    if (logWriter != null) {
                        logWriter.println("  Segment " + groupIndex + "/" + wallGroups.size() + (group.size() > 1 ? " (" + group.size() + " murs fusionnés)" : "") + " (" + (isExterior ? "extérieur" : "intérieur") + ")...");
                        logWriter.flush();
                    }
                    Object energy3dWall = convertWallToEnergy3D(representativeWall, foundation, originX, originY,
                            foundationLevel, overrideBaseZ, overrideHeight, isExterior, logWriter);
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

            // Export des arbres et buissons depuis les niveaux correspondants
            exportTreesAndBushes(home, foundation, foundationClass, originX, originY, scene, logWriter);
            
            if (ENABLE_TERRAIN3D_EXPORT) {
                export3DTerrainObjects(home, foundation, foundationClass, originX, originY, scene, logWriter);
            } else {
                logWriter.println("INFO: export terrain3d désactivé temporairement (TODO).");
                logWriter.flush();
            }
            
            logWriter.println("Ajout de la fondation à la Scene...");
            logWriter.flush();
            addMethod.invoke(scene, foundation, true);
            logWriter.println("✓ Fondation ajoutée à la Scene");
            logWriter.flush();

            // Connecter les murs (visitNeighbors)
            try {
                java.lang.reflect.Method connectWallsMethod = sceneClass.getDeclaredMethod("connectWalls");
                connectWallsMethod.setAccessible(true);
                connectWallsMethod.invoke(scene);
                if (logWriter != null) logWriter.println("  connectWalls() exécuté.");
            } catch (Throwable t) {
                if (logWriter != null) logWriter.println("  connectWalls(): " + t.getMessage());
            }

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
    
    @SuppressWarnings("unused")
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
    
    /**
     * Retourne le niveau (plan) SH3D dont le nom correspond à une catégorie du config.json (foundation, exterior, interior, etc.).
     * Utilise ConfigReader pour obtenir les mots-clés.
     */
    private static Level findLevelByCategory(Home home, String category, PrintWriter logWriter) {
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
            if (logWriter != null) {
                logWriter.println("    - \"" + (name != null ? name : "") + "\"");
                logWriter.flush();
            }
            if (ConfigReader.matchesCategory(name, category)) {
                    if (logWriter != null) {
                    logWriter.println("  Niveau " + category + " trouvé (\"" + name + "\").");
                        logWriter.flush();
                    }
                    return level;
            }
        }
        if (logWriter != null) {
            logWriter.println("  Aucun niveau correspondant à la catégorie \"" + category + "\".");
            logWriter.flush();
        }
        return null;
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
     * @deprecated Code orphelin supprimé.
     */
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

            // Texture Energy3D #2 (herbe) pour la fondation : modification directe du champ textureType (sérialisé dans .ng3)
            setHousePartTextureType(foundation, foundationClass, 2, logWriter, "fondation (herbe)");
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
    /** Code terrain3d conservé pour reprise ultérieure ; appel désactivé temporairement. */
    private static final boolean ENABLE_TERRAIN3D_EXPORT = false;

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
     * Retourne l'ensemble des clés de segments qui forment le périmètre de la pièce (bord extérieur).
     * Permet de ne garder que les murs extérieurs pour la fondation et le toit Energy3D.
     */
    private static java.util.Set<String> roomBoundarySegmentKeys(Room room) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (room == null) return keys;
        float[][] rpts = room.getPoints();
        if (rpts == null || rpts.length < 2) return keys;
        for (int i = 0; i < rpts.length; i++) {
            double x1 = Math.round(rpts[i][0] / SEGMENT_KEY_TOLERANCE_CM) * SEGMENT_KEY_TOLERANCE_CM;
            double y1 = Math.round(rpts[i][1] / SEGMENT_KEY_TOLERANCE_CM) * SEGMENT_KEY_TOLERANCE_CM;
            int next = (i + 1) % rpts.length;
            double x2 = Math.round(rpts[next][0] / SEGMENT_KEY_TOLERANCE_CM) * SEGMENT_KEY_TOLERANCE_CM;
            double y2 = Math.round(rpts[next][1] / SEGMENT_KEY_TOLERANCE_CM) * SEGMENT_KEY_TOLERANCE_CM;
            if (x1 > x2 || (x1 == x2 && y1 > y2)) {
                double t = x1; x1 = x2; x2 = t;
                t = y1; y1 = y2; y2 = t;
            }
            keys.add(x1 + "," + y1 + "," + x2 + "," + y2);
        }
        return keys;
    }

    /**
     * Enveloppe convexe 2D (Jarvis / gift wrapping). Retourne les indices des points du contour extérieur.
     * Permet de ne garder que les murs du contour extérieur pour une boucle fermée (toit Energy3D).
     */
    private static java.util.List<Integer> convexHullIndices(float[][] pts) {
        java.util.List<Integer> hull = new ArrayList<>();
        if (pts == null || pts.length < 3) return hull;
        int n = pts.length;
        int left = 0;
        for (int i = 1; i < n; i++) {
            if (pts[i][0] < pts[left][0] || (pts[i][0] == pts[left][0] && pts[i][1] < pts[left][1]))
                left = i;
        }
        int current = left;
        do {
            hull.add(current);
            int next = 0;
            for (int i = 1; i < n; i++) {
                if (next == current) {
                    next = i;
                    continue;
                }
                double cross = (pts[next][0] - pts[current][0]) * (pts[i][1] - pts[current][1])
                            - (pts[next][1] - pts[current][1]) * (pts[i][0] - pts[current][0]);
                if (cross < 0) next = i;
                else if (cross == 0) {
                    double dNext = (pts[next][0] - pts[current][0]) * (pts[next][0] - pts[current][0])
                                + (pts[next][1] - pts[current][1]) * (pts[next][1] - pts[current][1]);
                    double dI = (pts[i][0] - pts[current][0]) * (pts[i][0] - pts[current][0])
                              + (pts[i][1] - pts[current][1]) * (pts[i][1] - pts[current][1]);
                    if (dI > dNext) next = i;
                }
            }
            current = next;
        } while (current != left && hull.size() <= n);
        return hull;
    }

    /** Segments de l'enveloppe convexe de la pièce (clés normalisées), pour ne garder que le contour extérieur. */
    private static java.util.Set<String> convexHullSegmentKeys(Room room) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (room == null) return keys;
        float[][] rpts = room.getPoints();
        if (rpts == null || rpts.length < 3) return keys;
        java.util.List<Integer> hull = convexHullIndices(rpts);
        for (int i = 0; i < hull.size(); i++) {
            int i1 = hull.get(i);
            int i2 = hull.get((i + 1) % hull.size());
            double x1 = Math.round(rpts[i1][0] / SEGMENT_KEY_TOLERANCE_CM) * SEGMENT_KEY_TOLERANCE_CM;
            double y1 = Math.round(rpts[i1][1] / SEGMENT_KEY_TOLERANCE_CM) * SEGMENT_KEY_TOLERANCE_CM;
            double x2 = Math.round(rpts[i2][0] / SEGMENT_KEY_TOLERANCE_CM) * SEGMENT_KEY_TOLERANCE_CM;
            double y2 = Math.round(rpts[i2][1] / SEGMENT_KEY_TOLERANCE_CM) * SEGMENT_KEY_TOLERANCE_CM;
            if (x1 > x2 || (x1 == x2 && y1 > y2)) {
                double t = x1; x1 = x2; x2 = t;
                t = y1; y1 = y2; y2 = t;
            }
            keys.add(x1 + "," + y1 + "," + x2 + "," + y2);
        }
        return keys;
    }

    /**
     * Enveloppe convexe des segments de murs : clés des arêtes du convexe construit à partir des extrémités des murs.
     * Utilisé en fallback quand le périmètre de la pièce terrain ne matche aucun segment (niveaux différents) :
     * on tague extérieur/intérieur à partir du convexe des murs exportés pour que le toit puisse se créer.
     */
    private static java.util.Set<String> convexHullSegmentKeysFromWallGroups(List<List<Wall>> wallGroups) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (wallGroups == null || wallGroups.isEmpty()) return keys;
        java.util.Map<String, float[]> pointByKey = new java.util.LinkedHashMap<>();
        for (List<Wall> group : wallGroups) {
            if (group.isEmpty()) continue;
            Wall w = group.get(0);
            double xa = w.getXStart(); double ya = w.getYStart();
            double xb = w.getXEnd();   double yb = w.getYEnd();
            for (double[] p : new double[][] {{ xa, ya }, { xb, yb }}) {
                double rx = Math.round(p[0] / SEGMENT_KEY_TOLERANCE_CM) * SEGMENT_KEY_TOLERANCE_CM;
                double ry = Math.round(p[1] / SEGMENT_KEY_TOLERANCE_CM) * SEGMENT_KEY_TOLERANCE_CM;
                String pk = rx + "," + ry;
                if (!pointByKey.containsKey(pk)) pointByKey.put(pk, new float[] { (float) rx, (float) ry });
            }
        }
        float[][] pts = pointByKey.values().toArray(new float[0][]);
        if (pts.length < 3) return keys;
        java.util.List<Integer> hull = convexHullIndices(pts);
        for (int i = 0; i < hull.size(); i++) {
            int i1 = hull.get(i);
            int i2 = hull.get((i + 1) % hull.size());
            double x1 = pts[i1][0]; double y1 = pts[i1][1];
            double x2 = pts[i2][0]; double y2 = pts[i2][1];
            if (x1 > x2 || (x1 == x2 && y1 > y2)) {
                double t = x1; x1 = x2; x2 = t;
                t = y1; y1 = y2; y2 = t;
            }
            keys.add(x1 + "," + y1 + "," + x2 + "," + y2);
        }
        return keys;
    }

    /**
     * Filtre les groupes de murs pour ne garder que ceux dont le segment appartient au périmètre de la pièce (murs extérieurs).
     * Évite d'ajouter les murs intérieurs à la fondation Energy3D, ce qui casse la boucle fermée et empêche le toit de se créer.
     */
    private static List<List<Wall>> filterToRoomBoundaryOnly(List<List<Wall>> wallGroups, Room terrainRoom, PrintWriter logWriter) {
        if (terrainRoom == null) return wallGroups;
        java.util.Set<String> boundaryKeys = roomBoundarySegmentKeys(terrainRoom);
        List<List<Wall>> filtered = new ArrayList<>();
        for (List<Wall> group : wallGroups) {
            if (group.isEmpty()) continue;
            String key = segmentKey(group.get(0));
            if (boundaryKeys.contains(key)) {
                filtered.add(group);
            }
        }
        if (logWriter != null && filtered.size() != wallGroups.size()) {
            logWriter.println("  Murs périmètre uniquement (excl. intérieurs): " + filtered.size() + " / " + wallGroups.size() + " segments.");
            logWriter.flush();
        }
        return filtered;
    }

    /**
     * Ne garde que les murs dont le segment est sur l'enveloppe convexe de la pièce terrain.
     * Réduit à un contour extérieur unique (ex. 6 murs au lieu de 10 avec des murs intérieurs), ce qui permet
     * à connectWalls() de fermer la boucle et au toit Energy3D de s'afficher.
     * Si aucun mur ne matche (pièce non alignée avec le convexe), on garde tous les murs du périmètre.
     */
    @SuppressWarnings("unused")
    private static List<List<Wall>> filterToConvexHullOnly(List<List<Wall>> wallGroups, Room terrainRoom, PrintWriter logWriter) {
        if (terrainRoom == null) return wallGroups;
        java.util.Set<String> hullKeys = convexHullSegmentKeys(terrainRoom);
        if (hullKeys.isEmpty()) return wallGroups;
        List<List<Wall>> filtered = new ArrayList<>();
        for (List<Wall> group : wallGroups) {
            if (group.isEmpty()) continue;
            String key = segmentKey(group.get(0));
            if (hullKeys.contains(key)) {
                filtered.add(group);
            }
        }
        if (filtered.isEmpty()) {
            if (logWriter != null) {
                logWriter.println("  Enveloppe convexe ne matche aucun mur (conservation de tous les segments périmètre).");
                logWriter.flush();
            }
            return wallGroups;
        }
        if (logWriter != null && filtered.size() != wallGroups.size()) {
            logWriter.println("  Murs enveloppe convexe uniquement (contour extérieur pour toit): " + filtered.size() + " / " + wallGroups.size() + " segments.");
            logWriter.flush();
        }
        return filtered;
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
    /** Messages de log partagés pour homogénéiser les traces d'export. */
    private static final String LOG_TOTAL_FURNITURE = "  Nombre total de meubles dans le plan : ";
    private static final String LOG_TREE_CLASS_NOT_FOUND = "  ERREUR: Classe Tree non trouvée dans Energy3D";
    private static final String LOG_WALL_CLASS_NOT_FOUND = "  ERREUR: Classe Wall non trouvée dans Energy3D";

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
     * Cherche un mur qui contient le point (containsPoint avec marge).
     * Pas de fallback "mur le plus proche" pour éviter les ouvertures fantômes.
     */
    private static Wall findWallForDoorOrWindow(HomeDoorOrWindow piece, Collection<Wall> walls) {
        if (walls == null) return null;
        float x = (float) piece.getX();
        float y = (float) piece.getY();
        Level pieceLevel = piece.getLevel();
        for (Wall wall : walls) {
            if (pieceLevel != null && !wall.isAtLevel(pieceLevel)) continue;
            try {
                if (wall.containsPoint(x, y, false, DOOR_WINDOW_WALL_MARGIN_CM))
                    return wall;
            } catch (Exception ignored) { }
        }
        return null;
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

    /** Noms des plantes Energy3D (Tree.PLANTS) pour correspondance avec les meubles SH3D. */
    private static final String[] ENERGY3D_PLANT_NAMES = new String[]{
            "dogwood", "elm", "maple", "pine", "oak", "linden", "cottonwood"
    };

    /**
     * Initialise un stub Heliodon minimal pour permettre la création de Tree en mode headless.
     * Heliodon.getInstance() est requis par Tree.isShedded() lors de l'initialisation.
     * Crée le stub via sun.misc.Unsafe sans appeler le constructeur Heliodon.
     */
    private static void initializeHeliodonStub(PrintWriter logWriter) {
        try {
            Class<?> heliodonClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.shapes.Heliodon", logWriter);
            if (heliodonClass == null) {
                if (logWriter != null) {
                    logWriter.println("  AVERTISSEMENT: Classe Heliodon non trouvée, Tree.init() pourrait échouer");
                    logWriter.flush();
                }
                return;
            }
            // Vérifier si Heliodon.instance est déjà défini
            java.lang.reflect.Field instanceField = heliodonClass.getDeclaredField("instance");
            instanceField.setAccessible(true);
            Object existingInstance = instanceField.get(null);
            if (existingInstance != null) {
                if (logWriter != null) {
                    logWriter.println("  Heliodon.instance déjà défini");
                    logWriter.flush();
                }
                return;
            }
            
            // Le constructeur Heliodon appelle EnergyPanel.getInstance() qui n'existe pas en mode headless
            // Solution: utiliser sun.misc.Unsafe pour créer une instance Heliodon sans constructeur,
            // puis initialiser les champs nécessaires (calendar et latitude) via réflexion
            
            // Utiliser sun.misc.Unsafe pour créer une instance sans constructeur
            java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            
            // Allouer une instance Heliodon sans appeler le constructeur
            Object heliodonStub = unsafe.allocateInstance(heliodonClass);
            
            // Initialiser les champs nécessaires via réflexion
            // Le champ 'calendar' est privé final dans Heliodon, on doit utiliser Unsafe pour le modifier
            java.lang.reflect.Field calendarField = heliodonClass.getDeclaredField("calendar");
            long calendarOffset = unsafe.objectFieldOffset(calendarField);
            unsafe.putObject(heliodonStub, calendarOffset, java.util.Calendar.getInstance());
            
            // Le champ 'latitude' est aussi privé, utiliser Unsafe
            java.lang.reflect.Field latitudeField = heliodonClass.getDeclaredField("latitude");
            long latitudeOffset = unsafe.objectFieldOffset(latitudeField);
            unsafe.putDouble(heliodonStub, latitudeOffset, 42.34396 / 180.0 * Math.PI); // DEFAULT_LATITUDE en radians
            
            if (logWriter != null) {
                logWriter.println("  ✓ Heliodon stub créé avec Unsafe (sans constructeur)");
                logWriter.flush();
            }
            
            // Définir Heliodon.instance avec le stub
            instanceField.set(null, heliodonStub);
            
            if (logWriter != null) {
                logWriter.println("  ✓ Heliodon stub initialisé");
                logWriter.flush();
            }
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("  AVERTISSEMENT initializeHeliodonStub: " + e.getMessage());
                e.printStackTrace(new java.io.PrintWriter(logWriter));
                logWriter.flush();
            }
        }
    }
    
    private static boolean isLikelyTree(HomePieceOfFurniture piece) {
        if (piece == null || piece.isDoorOrWindow()) return false;
        String name = piece.getName();
        if (name == null) return false;
        String lower = name.toLowerCase().trim();
        if (lower.isEmpty()) return false;
        // Mots-clés spécifiques aux arbres uniquement
        if (lower.contains("tree") || lower.contains("arbre") || lower.contains("baum")
                || lower.contains("arbor") || lower.contains("albero")) {
            return true;
        }
        // Nom exact ou contenu correspondant à une plante Energy3D (qui sont toutes des arbres)
        for (String plantName : ENERGY3D_PLANT_NAMES) {
            if (lower.equals(plantName.toLowerCase()) || lower.contains(plantName.toLowerCase())) return true;
        }
        return false;
    }
    
    /**
     * Vérifie si un objet est probablement un buisson basé sur son nom.
     */
    private static boolean isLikelyBush(HomePieceOfFurniture piece) {
        if (piece == null || piece.isDoorOrWindow()) return false;
        String name = piece.getName();
        if (name == null) return false;
        String lower = name.toLowerCase().trim();
        if (lower.isEmpty()) return false;
        // Mots-clés pour les buissons depuis config.json (déjà en minuscules)
        List<String> bushKeywords = ConfigReader.getKeywords("bushes");
        for (String keyword : bushKeywords) {
            // Comparaison insensible à la casse
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retourne l'index du type de plante Energy3D (0–6) si le nom du meuble SH3D correspond à une plante, sinon -1.
     */
    private static int getEnergy3DPlantTypeFromName(HomePieceOfFurniture piece) {
        if (piece == null) return -1;
        String name = piece.getName();
        if (name == null) return -1;
        String lower = name.toLowerCase(java.util.Locale.ROOT).trim();
        for (int i = 0; i < ENERGY3D_PLANT_NAMES.length; i++) {
            if (lower.contains(ENERGY3D_PLANT_NAMES[i])) return i;
        }
        return -1;
    }

    /**
     * Exporte les arbres et buissons depuis les niveaux correspondants (config.json).
     * Les arbres sont exportés comme Tree Energy3D.
     * Les buissons sont exportés comme Wall avec texture buisson (TEXTURE_08).
     */
    private static void exportTreesAndBushes(Home home, Object foundation, Class<?> foundationClass,
            double originX, double originY, Object scene, PrintWriter logWriter) {
        if (home == null || foundation == null || scene == null) {
            if (logWriter != null) {
                logWriter.println("  exportTreesAndBushes ignoré : home=" + (home != null) + ", foundation=" + (foundation != null) + ", scene=" + (scene != null));
                logWriter.flush();
            }
            return;
        }
        
        if (logWriter != null) {
            logWriter.println("  Début exportTreesAndBushes...");
            logWriter.flush();
        }
        
        try {
            Class<?> sceneClass = scene.getClass();
            java.lang.reflect.Method addMethod = sceneClass.getMethod("add", 
                    Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.HousePart", logWriter), boolean.class);
            
            // Export des arbres : parcourir tous les niveaux et vérifier les mots-clés du JSON.
            if (logWriter != null) {
                logWriter.println("  Recherche des niveaux catégorie \"trees\" (mots-clés JSON)...");
                logWriter.flush();
            }
            List<String> treeKeywords = ConfigReader.getKeywords("trees");
            if (logWriter != null && !treeKeywords.isEmpty()) {
                logWriter.println("  Mots-clés arbres: " + String.join(", ", treeKeywords));
                logWriter.flush();
            }
            java.util.List<Level> allLevels = home.getLevels();
            Level foundationLevel = findLevelByCategory(home, "foundation", logWriter);
            int treesExported = 0;
            if (allLevels != null) {
                for (Level level : allLevels) {
                    if (level == null) continue;
                    String levelName = level.getName();
                    if (ConfigReader.matchesCategory(levelName, "trees")) {
                        if (logWriter != null) {
                            logWriter.println("  Export des arbres depuis le niveau \"" + levelName + "\"...");
                            logWriter.flush();
                        }
                        exportTreesFromLevel(home, level, scene, addMethod, originX, originY, logWriter);
                        treesExported++;
                    }
                }
            }
            // Scanner aussi le niveau fondation pour les objets arbres (tree-like).
            if (foundationLevel != null) {
                if (logWriter != null) {
                    logWriter.println("  Scan complémentaire du niveau fondation \"" + foundationLevel.getName() + "\" pour les objets arbres...");
                    logWriter.flush();
                }
                exportTreesFromFoundationLevel(home, foundationLevel, scene, addMethod, originX, originY, logWriter);
                treesExported++;
            }
            if (treesExported == 0 && logWriter != null) {
                logWriter.println("  Aucun niveau trouvé pour la catégorie \"trees\".");
                logWriter.flush();
            }
            
            // Export des buissons : parcourir tous les niveaux et vérifier les mots-clés du JSON.
            if (logWriter != null) {
                logWriter.println("  Recherche des niveaux catégorie \"bushes\" (mots-clés JSON)...");
                logWriter.flush();
            }
            List<String> bushKeywords = ConfigReader.getKeywords("bushes");
            if (logWriter != null && !bushKeywords.isEmpty()) {
                logWriter.println("  Mots-clés buissons: " + String.join(", ", bushKeywords));
                logWriter.flush();
            }
            int bushesExported = 0;
            if (allLevels != null) {
                for (Level level : allLevels) {
                    if (level == null) continue;
                    String levelName = level.getName();
                    if (ConfigReader.matchesCategory(levelName, "bushes")) {
                        if (logWriter != null) {
                            logWriter.println("  Export des buissons depuis le niveau \"" + levelName + "\"...");
                            logWriter.flush();
                        }
                        exportBushesFromLevel(home, level, foundation, foundationClass, originX, originY, logWriter);
                        bushesExported++;
                    }
                }
            }
            // Scanner aussi le niveau fondation pour les objets buissons.
            if (foundationLevel != null) {
                if (logWriter != null) {
                    logWriter.println("  Scan complémentaire du niveau fondation \"" + foundationLevel.getName() + "\" pour les objets buissons...");
                    logWriter.flush();
                }
                exportBushesFromFoundationLevel(home, foundationLevel, foundation, foundationClass, originX, originY, logWriter);
                bushesExported++;
            }
            if (bushesExported == 0 && logWriter != null) {
                logWriter.println("  Aucun niveau trouvé pour la catégorie \"bushes\".");
                logWriter.flush();
            }
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("  AVERTISSEMENT exportTreesAndBushes: " + e.getMessage());
                e.printStackTrace(new java.io.PrintWriter(logWriter));
                logWriter.flush();
            }
        }
    }
    
    /**
     * Exporte les meubles d'un niveau comme arbres Energy3D.
     */
    private static void exportTreesFromLevel(Home home, Level level, Object scene, 
            java.lang.reflect.Method addMethod, double originX, double originY, PrintWriter logWriter) {
        if (home == null || level == null || scene == null) {
            if (logWriter != null) {
                logWriter.println("  exportTreesFromLevel ignoré : home=" + (home != null) + ", level=" + (level != null) + ", scene=" + (scene != null));
                logWriter.flush();
            }
            return;
        }
        Class<?> sceneClass = scene.getClass();
        try {
            exportTreesFromLevelInternal(home, level, "niveau \"" + level.getName() + "\"", scene, sceneClass, addMethod, originX, originY, true, logWriter);
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("  AVERTISSEMENT exportTreesFromLevel: " + e.getMessage());
                e.printStackTrace(new java.io.PrintWriter(logWriter));
                logWriter.flush();
            }
        }
    }
    
    /**
     * Exporte les objets arbres du niveau fondation (filtre avec isLikelyTree).
     */
    private static void exportTreesFromFoundationLevel(Home home, Level foundationLevel, Object scene, 
            java.lang.reflect.Method addMethod, double originX, double originY, PrintWriter logWriter) {
        if (home == null || foundationLevel == null || scene == null) {
            if (logWriter != null) {
                logWriter.println("  exportTreesFromFoundationLevel ignoré : home=" + (home != null) + ", level=" + (foundationLevel != null) + ", scene=" + (scene != null));
                logWriter.flush();
            }
            return;
        }
        Class<?> sceneClass = scene.getClass();
        try {
            exportTreesFromLevelInternal(home, foundationLevel, "niveau fondation", scene, sceneClass, addMethod, originX, originY, false, logWriter);
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("  AVERTISSEMENT exportTreesFromFoundationLevel: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                e.printStackTrace(new java.io.PrintWriter(logWriter));
                logWriter.flush();
            }
        }
    }

    /**
     * Implémentation commune de l'export des arbres Energy3D.
     */
    private static void exportTreesFromLevelInternal(Home home, Level level, String levelLabel, Object scene,
            Class<?> sceneClass, java.lang.reflect.Method addMethod, double originX, double originY,
            boolean logFurnitureCount, PrintWriter logWriter) throws Exception {
        initializeHeliodonStub(logWriter);

        java.lang.reflect.Field instanceField = sceneClass.getDeclaredField("instance");
        instanceField.setAccessible(true);
        Object previousInstance = instanceField.get(null);
        try {
            instanceField.set(null, scene);

            Class<?> treeClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Tree", logWriter);
            if (treeClass == null) {
                if (logWriter != null) {
                    logWriter.println(LOG_TREE_CLASS_NOT_FOUND);
                    logWriter.flush();
                }
                return;
            }

            ClassLoader loader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
            Class<?> vector3Class = loader.loadClass("com.ardor3d.math.Vector3");
            java.lang.reflect.Method vector3Set = vector3Class.getMethod("set", double.class, double.class, double.class);
            java.lang.reflect.Method setLocationMethod = treeClass.getMethod("setLocation", vector3Class);
            java.lang.reflect.Method setPlantTypeMethod = null;
            try {
                setPlantTypeMethod = treeClass.getMethod("setPlantType", int.class);
            } catch (NoSuchMethodException ignored) { }

            List<HomePieceOfFurniture> furniture = getAllFurnitureIncludingGroups(home);
            if (logFurnitureCount && logWriter != null) {
                logWriter.println(LOG_TOTAL_FURNITURE + furniture.size());
                logWriter.flush();
            }

            int count = 0;
            int skipped = 0;
            for (HomePieceOfFurniture piece : furniture) {
                if (piece.getLevel() != level) {
                    skipped++;
                    continue;
                }
                if (piece.isDoorOrWindow()) {
                    skipped++;
                    continue;
                }
                if (isLikelyBush(piece)) {
                    skipped++;
                    continue;
                }
                if (!isLikelyTree(piece)) {
                    skipped++;
                    continue;
                }
                double xCm = piece.getX();
                double yCm = piece.getY();
                double zCm = level.getElevation() + piece.getElevation();
                double x = (xCm - originX) * SCALE_CM_TO_ENERGY3D;
                double y = (yCm - originY) * SCALE_CM_TO_ENERGY3D;
                double z = zCm * SCALE_CM_TO_ENERGY3D;
                if (MIRROR_FLIP_X) x = -x;
                if (ROTATE_180_Z) y = -y;

                Object tree = treeClass.getDeclaredConstructor().newInstance();
                int plantType = getEnergy3DPlantTypeFromName(piece);
                if (plantType >= 0 && setPlantTypeMethod != null) {
                    setPlantTypeMethod.invoke(tree, plantType);
                }
                Object pos = vector3Class.getDeclaredConstructor().newInstance();
                vector3Set.invoke(pos, x, y, z);
                setLocationMethod.invoke(tree, pos);
                try {
                    treeClass.getMethod("complete").invoke(tree);
                    treeClass.getMethod("draw").invoke(tree);
                    addMethod.invoke(scene, tree, true);
                    count++;
                    if (logWriter != null && count <= 5) {
                        logWriter.println("    Arbre #" + count + " créé depuis " + levelLabel + " à (" + xCm + ", " + yCm + ", " + zCm + ") cm");
                        logWriter.flush();
                    }
                } catch (Exception e) {
                    if (logWriter != null) {
                        logWriter.println("    ERREUR création arbre à (" + xCm + ", " + yCm + ", " + zCm + ") cm: " + e.getMessage());
                        e.printStackTrace(new java.io.PrintWriter(logWriter));
                        logWriter.flush();
                    }
                    skipped++;
                }
            }
            if (logWriter != null) {
                logWriter.println("  ✓ " + count + " arbre(s) exporté(s) depuis " + levelLabel + " (ignorés: " + skipped + ").");
                logWriter.flush();
            }
        } finally {
            instanceField.set(null, previousInstance);
        }
    }
    
    /**
     * Exporte les objets buissons du niveau fondation comme Wall Energy3D avec texture buisson.
     * La hauteur du mur correspond à la hauteur du meuble SH3D.
     */
    private static void exportBushesFromFoundationLevel(Home home, Level foundationLevel, Object foundation, Class<?> foundationClass,
            double originX, double originY, PrintWriter logWriter) {
        if (home == null || foundationLevel == null || foundation == null) {
            if (logWriter != null) {
                logWriter.println("  exportBushesFromFoundationLevel ignoré : home=" + (home != null) + ", level=" + (foundationLevel != null) + ", foundation=" + (foundation != null));
                logWriter.flush();
            }
            return;
        }
        try {
            exportBushesFromLevelInternal(home, foundationLevel, "niveau fondation", foundation, foundationClass, originX, originY, logWriter);
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("  AVERTISSEMENT exportBushesFromFoundationLevel: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                e.printStackTrace(new java.io.PrintWriter(logWriter));
                logWriter.flush();
            }
        }
    }
    
    /**
     * Exporte les objets buissons d'un niveau comme Wall Energy3D avec texture buisson.
     * La hauteur du mur correspond à la hauteur du meuble SH3D.
     */
    private static void exportBushesFromLevel(Home home, Level level, Object foundation, Class<?> foundationClass,
            double originX, double originY, PrintWriter logWriter) {
        if (home == null || level == null || foundation == null) {
            if (logWriter != null) {
                logWriter.println("  exportBushesFromLevel ignoré : home=" + (home != null) + ", level=" + (level != null) + ", foundation=" + (foundation != null));
                logWriter.flush();
            }
            return;
        }
        try {
            exportBushesFromLevelInternal(home, level, "niveau \"" + level.getName() + "\"", foundation, foundationClass, originX, originY, logWriter);
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("  AVERTISSEMENT exportBushesFromLevel: " + e.getMessage());
                e.printStackTrace(new java.io.PrintWriter(logWriter));
                logWriter.flush();
            }
        }
    }

    /**
     * Implémentation commune de l'export des buissons en murs Energy3D.
     */
    private static void exportBushesFromLevelInternal(Home home, Level level, String levelLabel, Object foundation,
            Class<?> foundationClass, double originX, double originY, PrintWriter logWriter) throws Exception {
        Class<?> wallClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Wall", logWriter);
        if (wallClass == null) {
            if (logWriter != null) {
                logWriter.println(LOG_WALL_CLASS_NOT_FOUND);
                logWriter.flush();
            }
            return;
        }
        Class<?> housePartClass = wallClass.getClassLoader().loadClass("org.concord.energy3d.model.HousePart");

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

        double edge0_1_dx = p1x - p0x;
        double edge0_1_dy = p1y - p0y;
        double edge0_2_dx = p2x - p0x;
        double edge0_2_dy = p2y - p0y;
        double edgeLength01 = Math.sqrt(edge0_1_dx * edge0_1_dx + edge0_1_dy * edge0_1_dy);
        double edgeLength02 = Math.sqrt(edge0_2_dx * edge0_2_dx + edge0_2_dy * edge0_2_dy);
        if (edgeLength01 <= 0) edgeLength01 = 1.0;
        if (edgeLength02 <= 0) edgeLength02 = 1.0;

        double foundationHeight = ((Number) getHeightMethod.invoke(foundation)).doubleValue();

        ClassLoader loader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
        Class<?> vector3Class = loader.loadClass("com.ardor3d.math.Vector3");
        java.lang.reflect.Method vector3Set = vector3Class.getMethod("set", double.class, double.class, double.class);

        List<HomePieceOfFurniture> furniture = getAllFurnitureIncludingGroups(home);
        if (logWriter != null) {
            logWriter.println(LOG_TOTAL_FURNITURE + furniture.size());
            List<String> bushKeywords = ConfigReader.getKeywords("bushes");
            if (!bushKeywords.isEmpty()) {
                logWriter.println("  Mots-clés buissons utilisés pour filtrage : " + String.join(", ", bushKeywords));
            }
            logWriter.flush();
        }

        int count = 0;
        int skipped = 0;
        for (HomePieceOfFurniture piece : furniture) {
            if (piece.getLevel() != level) {
                skipped++;
                continue;
            }
            if (piece.isDoorOrWindow()) {
                skipped++;
                continue;
            }
            String pieceName = piece.getName();
            boolean isBush = isLikelyBush(piece);
            if (logWriter != null && skipped < 10) {
                logWriter.println("    Vérification objet : \"" + (pieceName != null ? pieceName : "(sans nom)") + "\" → " + (isBush ? "BUISSON" : "ignoré"));
                logWriter.flush();
            }
            if (!isBush) {
                skipped++;
                continue;
            }

            Float pieceHeight = piece.getHeight();
            Float pieceWidth = piece.getWidth();
            Float pieceDepth = piece.getDepth();
            double heightCm = pieceHeight != null ? pieceHeight.doubleValue() : 100.0;
            double widthCm = pieceWidth != null ? pieceWidth.doubleValue() : 50.0;
            double depthCm = pieceDepth != null ? pieceDepth.doubleValue() : 50.0;

            double heightUnits = heightCm * SCALE_CM_TO_ENERGY3D;
            double lengthUnits = Math.max(widthCm, depthCm) * SCALE_CM_TO_ENERGY3D;
            double thicknessCm = Math.min(widthCm, depthCm);
            double thicknessM = thicknessCm / 100.0;
            double thicknessUnits = thicknessM * ENERGY3D_UNITS_PER_METER_EXPORT;

            if (heightUnits < 2.5) heightUnits = 2.5;
            if (lengthUnits < 2.5) lengthUnits = 2.5;
            if (thicknessUnits < 0.75) thicknessUnits = 0.75;
            if (heightUnits > 15.0) heightUnits = 15.0;
            if (lengthUnits > 25.0) lengthUnits = 25.0;
            if (thicknessUnits > 5.0) thicknessUnits = 5.0;

            double xCm = piece.getX();
            double yCm = piece.getY();
            double zCm = level.getElevation() + piece.getElevation();
            float pieceAngle = piece.getAngle();

            double xAbs = (xCm - originX) * SCALE_CM_TO_ENERGY3D;
            double yAbs = (yCm - originY) * SCALE_CM_TO_ENERGY3D;
            if (MIRROR_FLIP_X) xAbs = -xAbs;
            if (ROTATE_180_Z) yAbs = -yAbs;

            double uCenter = projectPointOnLineScale(xAbs, yAbs, p0x, p0y, p2x, p2y);
            double vCenter = projectPointOnLineScale(xAbs, yAbs, p0x, p0y, p1x, p1y);
            uCenter = Math.max(0, Math.min(1, uCenter));
            vCenter = Math.max(0, Math.min(1, vCenter));

            double normalizedAngle = ((pieceAngle % (2 * Math.PI)) + (2 * Math.PI)) % (2 * Math.PI);
            double angleFromU = normalizedAngle % (Math.PI / 2);
            boolean useUDirection = (angleFromU < Math.PI / 4) || (angleFromU > 3 * Math.PI / 4);

            double lengthInUV;
            double uStart;
            double vStart;
            double uEnd;
            double vEnd;
            if (useUDirection) {
                lengthInUV = lengthUnits / edgeLength02;
                uStart = Math.max(0, uCenter - lengthInUV / 2.0);
                vStart = vCenter;
                uEnd = Math.min(1, uCenter + lengthInUV / 2.0);
                vEnd = vCenter;
                if (uEnd - uStart < lengthInUV) {
                    if (uStart == 0) {
                        uEnd = Math.min(1, lengthInUV);
                    } else if (uEnd == 1) {
                        uStart = Math.max(0, 1 - lengthInUV);
                    }
                }
            } else {
                lengthInUV = lengthUnits / edgeLength01;
                uStart = uCenter;
                vStart = Math.max(0, vCenter - lengthInUV / 2.0);
                uEnd = uCenter;
                vEnd = Math.min(1, vCenter + lengthInUV / 2.0);
                if (vEnd - vStart < lengthInUV) {
                    if (vStart == 0) {
                        vEnd = Math.min(1, lengthInUV);
                    } else if (vEnd == 1) {
                        vStart = Math.max(0, 1 - lengthInUV);
                    }
                }
            }

            final double SNAP_GRID = 1e-5;
            uStart = Math.round(uStart / SNAP_GRID) * SNAP_GRID;
            vStart = Math.round(vStart / SNAP_GRID) * SNAP_GRID;
            uEnd = Math.round(uEnd / SNAP_GRID) * SNAP_GRID;
            vEnd = Math.round(vEnd / SNAP_GRID) * SNAP_GRID;

            double zBottom = foundationHeight;
            double zTop = foundationHeight + heightUnits;

            try {
                Object bushWall = wallClass.getDeclaredConstructor().newInstance();
                java.lang.reflect.Method setContainerMethod = wallClass.getMethod("setContainer", housePartClass);
                setContainerMethod.invoke(bushWall, foundation);
                java.lang.reflect.Method setThicknessMethod = wallClass.getMethod("setThickness", double.class);
                setThicknessMethod.invoke(bushWall, thicknessUnits);

                java.lang.reflect.Field pointsField = null;
                for (Class<?> c = wallClass; c != null; c = c.getSuperclass()) {
                    try {
                        pointsField = c.getDeclaredField("points");
                        break;
                    } catch (NoSuchFieldException ignored) { }
                }
                if (pointsField != null) {
                    pointsField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> pointsList = (java.util.List<Object>) pointsField.get(bushWall);
                    while (pointsList.size() < 4) {
                        pointsList.add(vector3Class.getConstructor(double.class, double.class, double.class).newInstance(0.0, 0.0, 0.0));
                    }
                    vector3Set.invoke(pointsList.get(0), uStart, vStart, zBottom);
                    vector3Set.invoke(pointsList.get(1), uStart, vStart, zTop);
                    vector3Set.invoke(pointsList.get(2), uEnd, vEnd, zBottom);
                    vector3Set.invoke(pointsList.get(3), uEnd, vEnd, zTop);

                    try {
                        java.lang.reflect.Field firstPointField = wallClass.getSuperclass().getDeclaredField("firstPointInserted");
                        firstPointField.setAccessible(true);
                        firstPointField.set(bushWall, true);
                    } catch (Exception ignored) { }
                }

                java.lang.reflect.Method setHeightMethod = wallClass.getMethod("setHeight", double.class, boolean.class);
                setHeightMethod.invoke(bushWall, heightUnits, true);
                wallClass.getMethod("complete").invoke(bushWall);
                setHousePartTextureType(bushWall, wallClass, 8, logWriter, "buisson (Wall)");
                wallClass.getMethod("draw").invoke(bushWall);

                java.lang.reflect.Method getChildrenMethod = foundationClass.getMethod("getChildren");
                @SuppressWarnings("unchecked")
                java.util.List<Object> children = (java.util.List<Object>) getChildrenMethod.invoke(foundation);
                children.add(bushWall);

                count++;
                if (logWriter != null && count <= 5) {
                    logWriter.println("    Buisson (Wall) #" + count + " créé depuis " + levelLabel + " à (" + xCm + ", " + yCm + ", " + zCm + ") cm, dimensions=" + heightCm + "x" + widthCm + "x" + depthCm + " cm → " + heightUnits + "x" + lengthUnits + "x" + thicknessUnits + " unités Energy3D");
                    logWriter.flush();
                }
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("    ERREUR création buisson (Wall) à (" + xCm + ", " + yCm + ", " + zCm + ") cm: " + e.getMessage());
                    e.printStackTrace(new java.io.PrintWriter(logWriter));
                    logWriter.flush();
                }
                skipped++;
            }
        }

        if (logWriter != null) {
            logWriter.println("  ✓ " + count + " buisson(s) (Wall) exporté(s) depuis " + levelLabel + " (ignorés: " + skipped + ").");
            logWriter.flush();
        }
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
                java.lang.reflect.Method setPlantTypeMethod = null;
                try {
                    setPlantTypeMethod = treeClass.getMethod("setPlantType", int.class);
                } catch (NoSuchMethodException ignored) { }
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
                    int plantType = getEnergy3DPlantTypeFromName(piece);
                    if (plantType >= 0 && setPlantTypeMethod != null) {
                        setPlantTypeMethod.invoke(tree, plantType);
                    }
                    Object pos = vector3Class.getDeclaredConstructor().newInstance();
                    vector3Set.invoke(pos, x, y, z);
                    setLocationMethod.invoke(tree, pos);
                    treeClass.getMethod("complete").invoke(tree);
                    treeClass.getMethod("draw").invoke(tree);
                    addMethod.invoke(scene, tree, true);
                    count++;
                }
                if (logWriter != null) {
                    logWriter.println("  ✓ " + count + " arbre(s) exporté(s) depuis SH3D.");
                    logWriter.flush();
                }
            } finally {
                instanceField.set(null, previousInstance);
            }
        } catch (Exception e) {
            if (logWriter != null) {
                String errorMsg = e.getMessage();
                if (errorMsg == null) {
                    errorMsg = e.getClass().getSimpleName();
                }
                logWriter.println("  AVERTISSEMENT: export des arbres: " + errorMsg);
                e.printStackTrace(new java.io.PrintWriter(logWriter));
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
     * @param isExterior true si le segment est sur l'enveloppe convexe (mur extérieur) : Energy3D ne connecte qu'exterior–exterior, le toit suit uniquement ces murs.
     * @param overrideBaseZUnits base Z en unités (au-dessus de la fondation). Si &lt;= 0 et overrideHeightUnits &lt;= 0, calculée depuis le niveau du mur.
     * @param overrideHeightUnits hauteur en unités. Si &gt; 0, utilisée (mur fusionné multi-niveaux) ; sinon hauteur du mur seul.
     */
    private static Object convertWallToEnergy3D(Wall sh3dWall, Object foundation, double originX, double originY,
            Level foundationLevel, double overrideBaseZUnits, double overrideHeightUnits, boolean isExterior, PrintWriter logWriter) {
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
            // Aligner les extrémités sur une grille pour que les sommets partagés entre segments soient identiques (connectWithOtherWalls)
            final double XY_SNAP = 1e-4; // unités Energy3D (~0.1 mm)
            xStart = Math.round(xStart / XY_SNAP) * XY_SNAP;
            yStart = Math.round(yStart / XY_SNAP) * XY_SNAP;
            xEnd   = Math.round(xEnd / XY_SNAP) * XY_SNAP;
            yEnd   = Math.round(yEnd / XY_SNAP) * XY_SNAP;
            // Épaisseur : SH3D stocke en cm, Energy3D en m. Convertir cm → m → unités Energy3D
            Float sh3dThickness = sh3dWall.getThickness();
            double thicknessCm = sh3dThickness != null ? sh3dThickness.doubleValue() : 20.0; // SH3D stocke en cm (défaut 20 cm)
            double thicknessM = thicknessCm / 100.0; // cm → m
            double thickness = thicknessM * ENERGY3D_UNITS_PER_METER_EXPORT; // m → unités Energy3D

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
                if (foundationLevel != null && level != null) {
                    float levelElev = level.getElevation();
                    float foundationElev = foundationLevel.getElevation();
                    zBottom = foundationHeight + (levelElev - foundationElev) * SCALE_CM_TO_ENERGY3D;
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
            // Grille pour que les sommets partagés entre murs soient égaux (connectWithOtherWalls utilise Util.isEqual ~ 1e-7)
            // 1e-5 absorbe les erreurs flottantes tout en restant précis pour un périmètre ~80u
            final double SNAP_GRID = 1e-5;
            uStart = Math.round(uStart / SNAP_GRID) * SNAP_GRID;
            vStart = Math.round(vStart / SNAP_GRID) * SNAP_GRID;
            uEnd   = Math.round(uEnd / SNAP_GRID) * SNAP_GRID;
            vEnd   = Math.round(vEnd / SNAP_GRID) * SNAP_GRID;

            Class<?> wallClass = Energy3DClassLoader.loadEnergy3DClass("org.concord.energy3d.model.Wall", logWriter);
            if (logWriter != null) { logWriter.println("  Instanciation du Wall..."); logWriter.flush(); }
            Object wall = wallClass.getDeclaredConstructor().newInstance();
            Class<?> housePartClass = wallClass.getClassLoader().loadClass("org.concord.energy3d.model.HousePart");
            java.lang.reflect.Method setContainerMethod = wallClass.getMethod("setContainer", housePartClass);
            setContainerMethod.invoke(wall, foundation);
            // Tagger intérieur/extérieur pour Energy3D : connectWithOtherWalls ne relie que murs de même type → le toit ne suit que les murs extérieurs
            try {
                java.lang.reflect.Method setInteriorMethod = wallClass.getMethod("setInterior", boolean.class);
                setInteriorMethod.invoke(wall, !isExterior);
            } catch (NoSuchMethodException e) {
                if (logWriter != null) logWriter.println("  Wall.setInterior non disponible (Energy3D ancienne version ?).");
            }

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

            // Texture du mur : appliquer automatiquement selon intérieur/extérieur
            // Murs extérieurs : texture #3, murs intérieurs : texture #0 (sans texture)
            int wallTextureType = isExterior ? 3 : 0;
            setHousePartTextureType(wall, wallClass, wallTextureType, logWriter, isExterior ? "mur extérieur" : "mur intérieur");

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
    
    /**
     * Convertit un modèle 3D de meuble en fichier Collada (.dae).
     * - Si le modèle est déjà en .dae: copie directe.
     * - Si le modèle est en .obj: conversion OBJ -> DAE (géométrie triangulée).
     */
    private static File convertModelToCollada(HomePieceOfFurniture piece, Content model, File tempDir, PrintWriter logWriter) {
        try {
            URLContent urlContent;
            if (model instanceof URLContent) {
                urlContent = (URLContent) model;
            } else {
                urlContent = TemporaryURLContent.copyToTemporaryURLContent(model);
            }

            URL modelURL = urlContent.getURL();
            String urlPath = modelURL.getPath().toLowerCase();
            String baseName = piece.getName() != null
                    ? piece.getName().replaceAll("[^a-zA-Z0-9._-]", "_")
                    : "object_" + System.currentTimeMillis();

            if (urlPath.endsWith(".dae") || urlPath.endsWith(".dae/")) {
                File colladaFile = new File(tempDir, baseName + ".dae");
                try (InputStream is = model.openStream()) {
                    Files.copy(is, colladaFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return colladaFile;
            }

            if (urlPath.endsWith(".obj") || urlPath.endsWith(".obj/")) {
                File objFile = new File(tempDir, baseName + ".obj");
                try (InputStream is = model.openStream()) {
                    Files.copy(is, objFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                File daeFile = new File(tempDir, baseName + ".dae");
                return convertObjToCollada(objFile, daeFile, logWriter) ? daeFile : null;
            }

            if (logWriter != null) {
                logWriter.println("    AVERTISSEMENT : format modèle non pris en charge pour conversion Collada: " + urlPath);
                logWriter.flush();
            }
            return null;

        } catch (Exception e) {
            if (logWriter != null) {
                String errorMsg = e.getMessage();
                if (errorMsg == null) errorMsg = e.getClass().getSimpleName();
                logWriter.println("    ERREUR conversion modèle pour \"" + (piece.getName() != null ? piece.getName() : "(sans nom)") + "\": " + errorMsg);
                e.printStackTrace(logWriter);
                logWriter.flush();
            }
            return null;
        }
    }

    /**
     * Conversion OBJ minimale vers Collada 1.4.1.
     * Supporte les lignes v / f (triangulation en éventail), génère des UV planaires.
     */
    private static boolean convertObjToCollada(File objFile, File daeFile, PrintWriter logWriter) {
        try {
            List<double[]> vertices = new ArrayList<>();
            List<int[]> triangles = new ArrayList<>();
            List<String> lines = Files.readAllLines(objFile.toPath(), StandardCharsets.UTF_8);

            for (String raw : lines) {
                if (raw == null) continue;
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("v ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 4) {
                        vertices.add(new double[] {
                                Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]),
                                Double.parseDouble(parts[3])
                        });
                    }
                    continue;
                }

                if (line.startsWith("f ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length < 4) continue;
                    int[] face = new int[parts.length - 1];
                    boolean valid = true;
                    for (int i = 1; i < parts.length; i++) {
                        String token = parts[i];
                        String idxToken = token.split("/")[0];
                        if (idxToken.isEmpty()) {
                            valid = false;
                            break;
                        }
                        int idx = Integer.parseInt(idxToken);
                        int resolved = idx > 0 ? idx - 1 : vertices.size() + idx;
                        if (resolved < 0 || resolved >= vertices.size()) {
                            valid = false;
                            break;
                        }
                        face[i - 1] = resolved;
                    }
                    if (!valid || face.length < 3) continue;
                    for (int i = 1; i < face.length - 1; i++) {
                        triangles.add(new int[] { face[0], face[i], face[i + 1] });
                    }
                }
            }

            if (vertices.isEmpty() || triangles.isEmpty()) {
                if (logWriter != null) {
                    logWriter.println("    AVERTISSEMENT : OBJ sans géométrie exploitable: " + objFile.getName());
                    logWriter.flush();
                }
                return false;
            }

            StringBuilder pos = new StringBuilder(vertices.size() * 32);
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (double[] v : vertices) {
                pos.append(v[0]).append(' ').append(v[1]).append(' ').append(v[2]).append(' ');
                minX = Math.min(minX, v[0]);
                minY = Math.min(minY, v[1]);
                maxX = Math.max(maxX, v[0]);
                maxY = Math.max(maxY, v[1]);
            }
            double dx = maxX - minX;
            double dy = maxY - minY;
            if (Math.abs(dx) < 1e-9) dx = 1.0;
            if (Math.abs(dy) < 1e-9) dy = 1.0;

            StringBuilder uv = new StringBuilder(vertices.size() * 24);
            for (double[] v : vertices) {
                double u = (v[0] - minX) / dx;
                double vv = (v[1] - minY) / dy;
                uv.append(u).append(' ').append(vv).append(' ');
            }

            StringBuilder p = new StringBuilder(triangles.size() * 24);
            for (int[] t : triangles) {
                // Interleave VERTEX index and TEXCOORD index
                p.append(t[0]).append(' ').append(t[0]).append(' ')
                 .append(t[1]).append(' ').append(t[1]).append(' ')
                 .append(t[2]).append(' ').append(t[2]).append(' ');
            }

            String xml =
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<COLLADA xmlns=\"http://www.collada.org/2005/11/COLLADASchema\" version=\"1.4.1\">\n" +
                    "  <asset><unit name=\"meter\" meter=\"1\"/><up_axis>Z_UP</up_axis></asset>\n" +
                    "  <library_geometries>\n" +
                    "    <geometry id=\"mesh\" name=\"mesh\">\n" +
                    "      <mesh>\n" +
                    "        <source id=\"mesh-positions\">\n" +
                    "          <float_array id=\"mesh-positions-array\" count=\"" + (vertices.size() * 3) + "\">" + pos + "</float_array>\n" +
                    "          <technique_common>\n" +
                    "            <accessor source=\"#mesh-positions-array\" count=\"" + vertices.size() + "\" stride=\"3\">\n" +
                    "              <param name=\"X\" type=\"float\"/>\n" +
                    "              <param name=\"Y\" type=\"float\"/>\n" +
                    "              <param name=\"Z\" type=\"float\"/>\n" +
                    "            </accessor>\n" +
                    "          </technique_common>\n" +
                    "        </source>\n" +
                    "        <source id=\"mesh-map-0\">\n" +
                    "          <float_array id=\"mesh-map-0-array\" count=\"" + (vertices.size() * 2) + "\">" + uv + "</float_array>\n" +
                    "          <technique_common>\n" +
                    "            <accessor source=\"#mesh-map-0-array\" count=\"" + vertices.size() + "\" stride=\"2\">\n" +
                    "              <param name=\"S\" type=\"float\"/>\n" +
                    "              <param name=\"T\" type=\"float\"/>\n" +
                    "            </accessor>\n" +
                    "          </technique_common>\n" +
                    "        </source>\n" +
                    "        <vertices id=\"mesh-vertices\">\n" +
                    "          <input semantic=\"POSITION\" source=\"#mesh-positions\"/>\n" +
                    "        </vertices>\n" +
                    "        <triangles count=\"" + triangles.size() + "\">\n" +
                    "          <input semantic=\"VERTEX\" source=\"#mesh-vertices\" offset=\"0\"/>\n" +
                    "          <input semantic=\"TEXCOORD\" source=\"#mesh-map-0\" offset=\"1\" set=\"0\"/>\n" +
                    "          <p>" + p + "</p>\n" +
                    "        </triangles>\n" +
                    "      </mesh>\n" +
                    "    </geometry>\n" +
                    "  </library_geometries>\n" +
                    "  <library_visual_scenes>\n" +
                    "    <visual_scene id=\"Scene\" name=\"Scene\">\n" +
                    "      <node id=\"mesh-node\" name=\"mesh-node\">\n" +
                    "        <instance_geometry url=\"#mesh\"/>\n" +
                    "      </node>\n" +
                    "    </visual_scene>\n" +
                    "  </library_visual_scenes>\n" +
                    "  <scene><instance_visual_scene url=\"#Scene\"/></scene>\n" +
                    "</COLLADA>\n";

            Files.write(daeFile.toPath(), xml.getBytes(StandardCharsets.UTF_8));
            if (logWriter != null) {
                logWriter.println("    ✓ Conversion OBJ -> Collada: " + objFile.getName() + " -> " + daeFile.getName());
                logWriter.flush();
            }
            return true;
        } catch (Exception e) {
            if (logWriter != null) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                logWriter.println("    ERREUR conversion OBJ -> Collada: " + msg);
                logWriter.flush();
            }
            return false;
        }
    }

    /**
     * Applique la texture herbe de la fondation sur les meshes importés Collada.
     */
    private static void applyGrassTextureToImportedNode(Object foundation, Class<?> foundationClass, Object importedNode, PrintWriter logWriter) {
        try {
            if (foundation == null || importedNode == null || foundationClass == null) return;

            // Récupère le mesh principal de la fondation (HousePart.mesh)
            Object foundationMesh = null;
            for (Class<?> c = foundationClass; c != null; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Field meshField = c.getDeclaredField("mesh");
                    meshField.setAccessible(true);
                    foundationMesh = meshField.get(foundation);
                    break;
                } catch (NoSuchFieldException ignored) {
                }
            }
            if (foundationMesh == null) return;

            ClassLoader loader = foundationClass.getClassLoader();
            Class<?> renderStateClass = loader.loadClass("com.ardor3d.renderer.state.RenderState");
            Class<?> stateTypeClass = loader.loadClass("com.ardor3d.renderer.state.RenderState$StateType");
            Class<?> meshClass = loader.loadClass("com.ardor3d.scenegraph.Mesh");

            Object textureStateType = null;
            Object[] enumConstants = stateTypeClass.getEnumConstants();
            if (enumConstants != null) {
                for (Object constant : enumConstants) {
                    if ("Texture".equals(String.valueOf(constant))) {
                        textureStateType = constant;
                        break;
                    }
                }
            }
            if (textureStateType == null) return;
            java.lang.reflect.Method getLocalRenderStateMethod = foundationMesh.getClass().getMethod("getLocalRenderState", stateTypeClass);
            Object grassTextureState = getLocalRenderStateMethod.invoke(foundationMesh, textureStateType);
            if (grassTextureState == null) return;

            java.lang.reflect.Method getChildrenMethod = importedNode.getClass().getMethod("getChildren");
            Object childrenObj = getChildrenMethod.invoke(importedNode);
            if (!(childrenObj instanceof java.util.List)) return;

            @SuppressWarnings("unchecked")
            java.util.List<Object> children = (java.util.List<Object>) childrenObj;
            for (Object child : children) {
                if (child == null) continue;
                if (!meshClass.isInstance(child)) continue;
                if (child.getClass().getName().endsWith(".Line")) continue;

                try {
                    java.lang.reflect.Method setRenderStateMethod = child.getClass().getMethod("setRenderState", renderStateClass);
                    setRenderStateMethod.invoke(child, grassTextureState);

                    java.lang.reflect.Method getUserDataMethod = child.getClass().getMethod("getUserData");
                    Object ud = getUserDataMethod.invoke(child);
                    if (ud != null) {
                        try {
                            java.lang.reflect.Method setRenderStateUd = ud.getClass().getMethod("setRenderState", renderStateClass);
                            setRenderStateUd.invoke(ud, grassTextureState);
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (logWriter != null) {
                logWriter.println("    ✓ Texture herbe appliquée à l'objet Collada importé");
                logWriter.flush();
            }
        } catch (Exception e) {
            if (logWriter != null) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                logWriter.println("    AVERTISSEMENT texture herbe objet Collada: " + msg);
                logWriter.flush();
            }
        }
    }

    /**
     * Exporte les objets 3D depuis le niveau terrain3d en Collada et les importe dans Energy3D.
     */
    private static void export3DTerrainObjects(Home home, Object foundation, Class<?> foundationClass,
            double originX, double originY, Object scene, PrintWriter logWriter) {
        if (home == null || foundation == null || scene == null) {
            if (logWriter != null) {
                logWriter.println("  export3DTerrainObjects ignoré : home=" + (home != null) + ", foundation=" + (foundation != null) + ", scene=" + (scene != null));
                logWriter.flush();
            }
            return;
        }
        
        if (logWriter != null) {
            logWriter.println("  Début export3DTerrainObjects...");
            logWriter.flush();
        }
        
        try {
            // Trouver le niveau terrain3d
            Level terrain3dLevel = findLevelByCategory(home, "terrain3d", logWriter);
            if (terrain3dLevel == null) {
                if (logWriter != null) {
                    logWriter.println("  Aucun niveau correspondant à la catégorie 'terrain3d'.");
                    logWriter.flush();
                }
                return;
            }
            
            if (logWriter != null) {
                logWriter.println("  Export des objets 3D depuis le niveau \"" + terrain3dLevel.getName() + "\"...");
                logWriter.flush();
            }
            
            // Charger les classes Energy3D nécessaires
            ClassLoader loader = Energy3DClassLoader.getEnergy3DClassLoader(logWriter);
            Class<?> vector3Class = loader.loadClass("com.ardor3d.math.Vector3");
            java.lang.reflect.Constructor<?> vector3Constructor = vector3Class.getConstructor(double.class, double.class, double.class);
            java.lang.reflect.Method importColladaMethod = foundationClass.getMethod("importCollada", URL.class, vector3Class);
            
            // Obtenir les meubles du niveau terrain3d
            List<HomePieceOfFurniture> furniture = getAllFurnitureIncludingGroups(home);
            int count = 0;
            int skipped = 0;
            
            // Créer un répertoire temporaire pour les fichiers Collada
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "sweetenergy3d_collada_" + System.currentTimeMillis());
            tempDir.mkdirs();
            
            for (HomePieceOfFurniture piece : furniture) {
                if (piece.getLevel() != terrain3dLevel) {
                    skipped++;
                    continue;
                }
                
                if (piece.isDoorOrWindow()) {
                    skipped++;
                    continue;
                }
                
                Content model = piece.getModel();
                if (model == null) {
                    skipped++;
                    continue;
                }
                
                try {
                    // Convertir le modèle en Collada
                    // Utiliser ModelManager pour charger le modèle 3D, puis OBJWriter pour l'exporter en OBJ,
                    // puis convertir l'OBJ en Collada
                    File colladaFile = convertModelToCollada(piece, model, tempDir, logWriter);
                    
                    if (colladaFile == null || !colladaFile.exists()) {
                        if (logWriter != null && skipped < 5) {
                            logWriter.println("    Objet \"" + (piece.getName() != null ? piece.getName() : "(sans nom)") + "\" ignoré : échec conversion en Collada");
                            logWriter.flush();
                        }
                        skipped++;
                        continue;
                    }
                    
                    File sourceFile = colladaFile;
                    
                    if (!sourceFile.exists()) {
                        if (logWriter != null) {
                            logWriter.println("    ERREUR : fichier Collada non trouvé : " + sourceFile.getAbsolutePath());
                            logWriter.flush();
                        }
                        skipped++;
                        continue;
                    }
                    
                    // Calculer la position en unités Energy3D
                    double xCm = piece.getX();
                    double yCm = piece.getY();
                    double zCm = terrain3dLevel.getElevation() + piece.getElevation();
                    
                    double xAbs = (xCm - originX) * SCALE_CM_TO_ENERGY3D;
                    double yAbs = (yCm - originY) * SCALE_CM_TO_ENERGY3D;
                    double zAbs = zCm * SCALE_CM_TO_ENERGY3D;
                    
                    if (MIRROR_FLIP_X) xAbs = -xAbs;
                    if (ROTATE_180_Z) yAbs = -yAbs;
                    
                    // Obtenir la hauteur de la fondation pour ajuster la position Z
                    java.lang.reflect.Method getHeightMethod = foundationClass.getMethod("getHeight");
                    double foundationHeight = ((Number) getHeightMethod.invoke(foundation)).doubleValue();
                    zAbs += foundationHeight;
                    
                    // Créer le Vector3 pour la position
                    Object position = vector3Constructor.newInstance(xAbs, yAbs, zAbs);
                    
                    // Importer le fichier Collada dans Energy3D
                    URL fileURL = sourceFile.toURI().toURL();
                    Object importedNode = importColladaMethod.invoke(foundation, fileURL, position);
                    
                    if (importedNode != null) {
                        applyGrassTextureToImportedNode(foundation, foundationClass, importedNode, logWriter);
                        count++;
                        if (logWriter != null && count <= 5) {
                            logWriter.println("    Objet 3D #" + count + " importé depuis \"" + (piece.getName() != null ? piece.getName() : "(sans nom)") + "\" à (" + xCm + ", " + yCm + ", " + zCm + ") cm");
                            logWriter.flush();
                        }
                    } else {
                        skipped++;
                        if (logWriter != null) {
                            logWriter.println("    AVERTISSEMENT : import Collada retourné null pour \"" + (piece.getName() != null ? piece.getName() : "(sans nom)") + "\"");
                            logWriter.flush();
                        }
                    }
                } catch (Exception e) {
                    skipped++;
                    if (logWriter != null) {
                        String errorMsg = e.getMessage();
                        if (errorMsg == null) errorMsg = e.getClass().getSimpleName();
                        logWriter.println("    ERREUR import Collada pour \"" + (piece.getName() != null ? piece.getName() : "(sans nom)") + "\": " + errorMsg);
                        if (count < 3) {
                            e.printStackTrace(logWriter);
                        }
                        logWriter.flush();
                    }
                }
            }
            
            if (logWriter != null) {
                logWriter.println("  ✓ " + count + " objet(s) 3D exporté(s) depuis le niveau terrain3d (ignorés: " + skipped + ").");
                logWriter.flush();
            }
            
            // Nettoyer le répertoire temporaire après un délai (optionnel)
            // tempDir.deleteOnExit();
            
        } catch (Exception e) {
            if (logWriter != null) {
                String errorMsg = e.getMessage();
                if (errorMsg == null) errorMsg = e.getClass().getSimpleName();
                logWriter.println("  ERREUR export3DTerrainObjects: " + errorMsg);
                e.printStackTrace(logWriter);
                logWriter.flush();
            }
        }
    }
}
