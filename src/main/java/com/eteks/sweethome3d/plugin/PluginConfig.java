package com.eteks.sweethome3d.plugin;

import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Charge et parse le fichier config.json pour les mots-clés du plugin SweetEnergy3D.
 * Le fichier doit être à côté de l'exécutable une fois compilé.
 */
public class PluginConfig {
    
    private static PluginConfig instance;
    private List<String> exteriorWallKeywords = new ArrayList<>();
    private List<String> interiorWallKeywords = new ArrayList<>();
    private List<String> roofKeywords = new ArrayList<>();
    private List<String> foundationKeywords = new ArrayList<>();
    private List<String> treeKeywords = new ArrayList<>();
    private List<String> bushKeywords = new ArrayList<>();
    
    private PluginConfig() {
        // Valeurs par défaut si le fichier n'est pas trouvé
        exteriorWallKeywords = Arrays.asList("extérieur", "exterieur", "outside", "outer", "external", "outdoor");
        interiorWallKeywords = Arrays.asList("intérieur", "interieur", "cloison", "inside", "interior", "internal", "indoor");
        roofKeywords = Arrays.asList("toit", "roof");
        foundationKeywords = Arrays.asList("terrain", "foundation", "sol");
        treeKeywords = Arrays.asList("arbre", "tree");
        bushKeywords = Arrays.asList("buisson", "bush", "shrub");
    }
    
    /**
     * Charge le fichier config.json depuis le répertoire de l'exécutable.
     * @param logWriter Pour écrire les erreurs de chargement
     * @return L'instance singleton de PluginConfig
     */
    public static PluginConfig getInstance(PrintWriter logWriter) {
        if (instance == null) {
            instance = new PluginConfig();
            instance.loadConfig(logWriter);
        }
        return instance;
    }
    
    private void loadConfig(PrintWriter logWriter) {
        // Chercher config.json à côté de l'exécutable (dans le répertoire du JAR)
        File configFile = findConfigFile();
        if (configFile == null || !configFile.exists()) {
            if (logWriter != null) {
                logWriter.println("  Config: config.json non trouvé, utilisation des valeurs par défaut.");
                logWriter.flush();
            }
            return;
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            
            if (json.has("exterior_wall_keywords")) {
                exteriorWallKeywords = parseStringArray(json.getAsJsonArray("exterior_wall_keywords"));
            }
            if (json.has("interior_wall_keywords")) {
                interiorWallKeywords = parseStringArray(json.getAsJsonArray("interior_wall_keywords"));
            }
            if (json.has("roof_keywords")) {
                roofKeywords = parseStringArray(json.getAsJsonArray("roof_keywords"));
            }
            if (json.has("foundation_keywords")) {
                foundationKeywords = parseStringArray(json.getAsJsonArray("foundation_keywords"));
            }
            if (json.has("tree_keywords")) {
                treeKeywords = parseStringArray(json.getAsJsonArray("tree_keywords"));
            }
            if (json.has("bush_keywords")) {
                bushKeywords = parseStringArray(json.getAsJsonArray("bush_keywords"));
            }
            
            if (logWriter != null) {
                logWriter.println("  Config: config.json chargé depuis " + configFile.getAbsolutePath());
                logWriter.flush();
            }
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("  Config: Erreur lors du chargement de config.json: " + e.getMessage());
                logWriter.println("  Config: Utilisation des valeurs par défaut.");
                logWriter.flush();
            }
        }
    }
    
    private List<String> parseStringArray(com.google.gson.JsonArray array) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            result.add(array.get(i).getAsString().toLowerCase());
        }
        return result;
    }
    
    private File findConfigFile() {
        // Chercher config.json dans plusieurs emplacements possibles
        String[] paths = {
            "config.json",
            "../config.json",
            "../../config.json",
            System.getProperty("user.dir") + File.separator + "config.json"
        };
        
        for (String path : paths) {
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                return f;
            }
        }
        
        // Chercher à côté du JAR du plugin
        try {
            String pluginPath = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            if (pluginPath.endsWith(".jar")) {
                File pluginJar = new File(pluginPath);
                File configInJarDir = new File(pluginJar.getParent(), "config.json");
                if (configInJarDir.exists()) {
                    return configInJarDir;
                }
            }
        } catch (Exception e) {
            // Ignorer
        }
        
        return null;
    }
    
    /**
     * Vérifie si le nom de niveau correspond aux mots-clés extérieurs.
     */
    public boolean isExteriorLevel(String levelName) {
        if (levelName == null) return false;
        String lower = levelName.toLowerCase();
        for (String keyword : exteriorWallKeywords) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }
    
    /**
     * Vérifie si le nom de niveau correspond aux mots-clés intérieurs.
     */
    public boolean isInteriorLevel(String levelName) {
        if (levelName == null) return false;
        String lower = levelName.toLowerCase();
        for (String keyword : interiorWallKeywords) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }
    
    /**
     * Vérifie si le nom de niveau correspond aux mots-clés toit.
     */
    public boolean isRoofLevel(String levelName) {
        if (levelName == null) return false;
        String lower = levelName.toLowerCase();
        for (String keyword : roofKeywords) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }
    
    /**
     * Vérifie si le nom de niveau correspond aux mots-clés fondation.
     */
    public boolean isFoundationLevel(String levelName) {
        if (levelName == null) return false;
        String lower = levelName.toLowerCase();
        for (String keyword : foundationKeywords) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }
    
    /**
     * Vérifie si le nom de niveau correspond aux mots-clés arbre.
     */
    public boolean isTreeLevel(String levelName) {
        if (levelName == null) return false;
        String lower = levelName.toLowerCase();
        for (String keyword : treeKeywords) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }
    
    /**
     * Vérifie si le nom de niveau correspond aux mots-clés buisson.
     */
    public boolean isBushLevel(String levelName) {
        if (levelName == null) return false;
        String lower = levelName.toLowerCase();
        for (String keyword : bushKeywords) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }
    
    public List<String> getExteriorWallKeywords() { return exteriorWallKeywords; }
    public List<String> getInteriorWallKeywords() { return interiorWallKeywords; }
    public List<String> getRoofKeywords() { return roofKeywords; }
    public List<String> getFoundationKeywords() { return foundationKeywords; }
    public List<String> getTreeKeywords() { return treeKeywords; }
    public List<String> getBushKeywords() { return bushKeywords; }
}
