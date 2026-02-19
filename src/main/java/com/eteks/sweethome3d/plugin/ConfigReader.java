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
    private static final String KEYWORDS = "keywords";
    
    /**
     * Charge config.json depuis le fichier à côté de l'exécutable ou depuis les ressources.
     */
    private static synchronized void loadConfig() {
        if (config != null) return;
        
        File configFile = new File("config.json");
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                config = JsonParser.parseReader(reader).getAsJsonObject();
                return;
            } catch (IOException e) {
                System.err.println("Erreur lecture config.json: " + e.getMessage());
            }
        }

        try (InputStream is = ConfigReader.class.getClassLoader().getResourceAsStream("config.json")) {
            if (is != null) {
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    config = JsonParser.parseReader(reader).getAsJsonObject();
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur lecture config.json depuis ressources: " + e.getMessage());
        }

        config = getDefaultConfig();
    }
    
    private static JsonObject getDefaultConfig() {
        JsonObject def = new JsonObject();
        addCategory(def, "external_wall", "extérieur", "exterieur", "outside", "outer", "external", "outdoor");
        addCategory(def, "internal_wall", "intérieur", "interieur", "cloison", "inside", "interior", "internal", "indoor");
        addCategory(def, "roof", "toit", "roof", "toiture");
        addCategory(def, "foundation", "terrain", "fondation", "foundation", "ground");
        addCategory(def, "trees", "arbre", "tree", "arbres", "trees");
        addCategory(def, "bushes", "buisson", "bush", "buissons", "bushes", "haie", "hedge");
        addCategory(def, "terrain3d", "3dterrain", "terrain3d", "3d terrain", "terrain 3d");
        return def;
    }

    private static void addCategory(JsonObject root, String category, String... keywords) {
        JsonObject obj = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String keyword : keywords) {
            arr.add(keyword);
        }
        obj.add(KEYWORDS, arr);
        root.add(category, obj);
    }
    
    /**
     * Retourne les mots-clés pour une catégorie (exterior, interior, roof, foundation, trees, bushes).
     */
    public static List<String> getKeywords(String category) {
        loadConfig();
        List<String> keywords = new ArrayList<>();
        JsonElement cat = config.get(category);
        if (cat == null || !cat.isJsonObject()) {
            return keywords;
        }
        JsonElement keys = cat.getAsJsonObject().get(KEYWORDS);
        if (keys != null && keys.isJsonArray()) {
            for (JsonElement key : keys.getAsJsonArray()) {
                keywords.add(key.getAsString().toLowerCase());
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
