package com.eteks.sweethome3d.plugin;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Lit le fichier config.json pour obtenir les mots-clés de configuration du plugin.
 * Cherche config.json à côté de l'exécutable (ou dans le classpath comme fallback).
 */
public class ConfigReader {
    
    private static JsonObject config = null;
    
    /**
     * Charge config.json depuis le fichier à côté de l'exécutable ou depuis les ressources.
     */
    private static synchronized void loadConfig() {
        if (config != null) return;
        
        // Chercher config.json à côté de l'exécutable (dans le répertoire du plugin)
        File configFile = new File("config.json");
        if (!configFile.exists()) {
            // Fallback: chercher dans le classpath (ressources du plugin)
            InputStream is = ConfigReader.class.getClassLoader().getResourceAsStream("config.json");
            if (is != null) {
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    config = JsonParser.parseReader(reader).getAsJsonObject();
                    return;
                } catch (Exception e) {
                    System.err.println("Erreur lecture config.json depuis ressources: " + e.getMessage());
                }
            }
            // Si pas trouvé, utiliser valeurs par défaut
            config = getDefaultConfig();
            return;
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            config = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            System.err.println("Erreur lecture config.json: " + e.getMessage());
            config = getDefaultConfig();
        }
    }
    
    private static JsonObject getDefaultConfig() {
        JsonObject def = new JsonObject();
        JsonObject ext = new JsonObject();
        JsonArray extKeys = new JsonArray();
        extKeys.add("extérieur"); extKeys.add("exterieur"); extKeys.add("outside"); extKeys.add("outer"); extKeys.add("external"); extKeys.add("outdoor");
        ext.add("keywords", extKeys);
        def.add("external_wall", ext);
        
        JsonObject int_ = new JsonObject();
        JsonArray intKeys = new JsonArray();
        intKeys.add("intérieur"); intKeys.add("interieur"); intKeys.add("cloison"); intKeys.add("inside"); intKeys.add("interior"); intKeys.add("internal"); intKeys.add("indoor");
        int_.add("keywords", intKeys);
        def.add("internal_wall", int_);
        
        JsonObject roof = new JsonObject();
        JsonArray roofKeys = new JsonArray();
        roofKeys.add("toit"); roofKeys.add("roof"); roofKeys.add("toiture");
        roof.add("keywords", roofKeys);
        def.add("roof", roof);
        
        JsonObject foundation = new JsonObject();
        JsonArray foundKeys = new JsonArray();
        foundKeys.add("terrain"); foundKeys.add("fondation"); foundKeys.add("foundation"); foundKeys.add("ground");
        foundation.add("keywords", foundKeys);
        def.add("foundation", foundation);
        
        JsonObject trees = new JsonObject();
        JsonArray treeKeys = new JsonArray();
        treeKeys.add("arbre"); treeKeys.add("tree"); treeKeys.add("arbres"); treeKeys.add("trees");
        trees.add("keywords", treeKeys);
        def.add("trees", trees);
        
        JsonObject bushes = new JsonObject();
        JsonArray bushKeys = new JsonArray();
        bushKeys.add("buisson"); bushKeys.add("bush"); bushKeys.add("buissons"); bushKeys.add("bushes"); bushKeys.add("haie"); bushKeys.add("hedge");
        bushes.add("keywords", bushKeys);
        def.add("bushes", bushes);
        
        JsonObject terrain3d = new JsonObject();
        JsonArray terrain3dKeys = new JsonArray();
        terrain3dKeys.add("3dterrain"); terrain3dKeys.add("terrain3d"); terrain3dKeys.add("3d terrain"); terrain3dKeys.add("terrain 3d");
        terrain3d.add("keywords", terrain3dKeys);
        def.add("terrain3d", terrain3d);
        
        return def;
    }
    
    /**
     * Retourne les mots-clés pour une catégorie (exterior, interior, roof, foundation, trees, bushes).
     */
    public static List<String> getKeywords(String category) {
        loadConfig();
        List<String> keywords = new ArrayList<>();
        JsonElement cat = config.get(category);
        if (cat != null && cat.isJsonObject()) {
            JsonElement keys = cat.getAsJsonObject().get("keywords");
            if (keys != null && keys.isJsonArray()) {
                for (JsonElement key : keys.getAsJsonArray()) {
                    keywords.add(key.getAsString().toLowerCase());
                }
            }
        }
        return keywords;
    }
    
    /**
     * Vérifie si un nom de niveau correspond à une catégorie.
     */
    public static boolean matchesCategory(String levelName, String category) {
        if (levelName == null) return false;
        String nameLower = levelName.toLowerCase();
        for (String keyword : getKeywords(category)) {
            // Les mots-clés sont déjà en minuscules depuis getKeywords()
            if (nameLower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
