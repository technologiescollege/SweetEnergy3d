package com.eteks.sweethome3d.plugin;

import com.eteks.sweethome3d.model.Wall;
import com.eteks.sweethome3d.model.HomeTexture;

/**
 * Classe utilitaire pour convertir les murs entre Sweet Home 3D et Energy3D
 */
public class WallConverter {
    
    /**
     * Convertit un mur de Sweet Home 3D vers Energy3D
     * 
     * @param sh3dWall Le mur de Sweet Home 3D
     * @return Un objet contenant les données du mur pour Energy3D
     */
    public static Energy3DWallData convertToEnergy3D(Wall sh3dWall) {
        Energy3DWallData data = new Energy3DWallData();
        
        // Conversion des points (2D -> 3D)
        float xStart = sh3dWall.getXStart();
        float yStart = sh3dWall.getYStart();
        float xEnd = sh3dWall.getXEnd();
        float yEnd = sh3dWall.getYEnd();
        
        // Calculer les 4 coins du mur (rectangle)
        double angle = Math.atan2(yEnd - yStart, xEnd - xStart);
        double thickness = sh3dWall.getThickness();
        double halfThickness = thickness / 2.0;
        
        // Vecteur perpendiculaire au mur
        double perpX = -Math.sin(angle) * halfThickness;
        double perpY = Math.cos(angle) * halfThickness;
        
        // Les 4 points du rectangle (coordonnées 3D)
        data.points = new double[][] {
            {xStart + perpX, yStart + perpY, 0},  // Coin bas-gauche [x, y, z]
            {xEnd + perpX, yEnd + perpY, 0},      // Coin bas-droit
            {xEnd - perpX, yEnd - perpY, 0},      // Coin haut-droit
            {xStart - perpX, yStart - perpY, 0}   // Coin haut-gauche
        };
        
        // Conversion de l'épaisseur
        data.wallThickness = sh3dWall.getThickness();
        
        // Conversion de la hauteur
        Float height = sh3dWall.getHeight();
        data.height = height != null ? height.doubleValue() : 30.0; // Valeur par défaut Energy3D
        
        // Conversion de la couleur
        Integer color = sh3dWall.getLeftSideColor();
        if (color == null) {
            color = sh3dWall.getRightSideColor();
        }
        if (color != null) {
            // Convertir Integer (RGB) vers ColorRGBA
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            data.colorR = r / 255.0f;
            data.colorG = g / 255.0f;
            data.colorB = b / 255.0f;
            data.colorA = 1.0f;
        } else {
            // Couleur par défaut (gris clair)
            data.colorR = 0.8f;
            data.colorG = 0.8f;
            data.colorB = 0.8f;
            data.colorA = 1.0f;
        }
        
        // Conversion de la texture
        HomeTexture texture = sh3dWall.getLeftSideTexture();
        if (texture == null) {
            texture = sh3dWall.getRightSideTexture();
        }
        data.textureType = mapTextureToEnergy3D(texture);
        
        // Propriétés thermiques par défaut
        data.uValue = 0.28; // R20 par défaut
        data.volumetricHeatCapacity = 0.5; // kWh/m³/°C
        
        // Type de mur (toujours SOLID_WALL par défaut)
        data.type = 0; // SOLID_WALL
        
        return data;
    }
    
    /**
     * Mappe une texture de Sweet Home 3D vers un type de texture Energy3D
     */
    private static int mapTextureToEnergy3D(HomeTexture texture) {
        if (texture == null) {
            return 1; // TEXTURE_01 par défaut
        }
        
        // Essayer d'identifier le type de texture par son nom
        String name = texture.getName().toLowerCase();
        
        if (name.contains("brick") || name.contains("brique")) {
            return 2; // TEXTURE_02
        } else if (name.contains("concrete") || name.contains("beton")) {
            return 3; // TEXTURE_03
        } else if (name.contains("wood") || name.contains("bois")) {
            return 4; // TEXTURE_04
        } else if (name.contains("metal") || name.contains("metal")) {
            return 5; // TEXTURE_05
        } else if (name.contains("stone") || name.contains("pierre")) {
            return 6; // TEXTURE_06
        } else {
            return 1; // TEXTURE_01 par défaut
        }
    }
    
    /**
     * Classe de données pour stocker les informations d'un mur Energy3D
     */
    public static class Energy3DWallData {
        public double[][] points;  // Tableau de points [x, y, z]
        public double wallThickness;
        public double height;
        public float colorR, colorG, colorB, colorA;
        public int textureType;
        public double uValue;
        public double volumetricHeatCapacity;
        public int type;
    }
}
