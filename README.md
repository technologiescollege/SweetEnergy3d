# Plugin SweetEnergy3D pour Sweet Home 3D

Plugin pour exporter les plans Sweet Home 3D vers le format Energy3D.

## Fonctionnalité

Le plugin ajoute une action dans le menu **Outils** qui permet d'exporter le plan actuel vers un fichier JSON compatible Energy3D.

## Compilation

```bash
mvn clean package
```

Génère `target/sweetenergy3d.sh3p`

## Installation

### Méthode automatique

```powershell
.\install.ps1
```

### Méthode manuelle

Copier `target/sweetenergy3d.sh3p` dans :
- **Version portable** : `SweetHome3D-7.5-portable\data\plugins\`
- **Version installée** : `%APPDATA%\SweetHome3D\plugins\`

## Utilisation

1. **Ouvrir un plan** dans Sweet Home 3D
2. **Menu Outils** → **Exporter vers Energy3D**
3. **Choisir l'emplacement** du fichier JSON
4. Le fichier est créé avec tous les murs du plan convertis au format Energy3D

## Format d'export

Le fichier JSON contient :
- Les coordonnées 3D de chaque mur (4 points pour former un rectangle)
- L'épaisseur et la hauteur
- La couleur et le type de texture
- Les propriétés thermiques (uValue, volumetricHeatCapacity)
- Les informations originales SH3D pour référence

## Structure du projet

```
SweetEnergy3D/
├── pom.xml                                    (Configuration Maven)
├── ApplicationPlugin.properties               (Descripteur du plugin)
├── src/main/java/com/eteks/sweethome3d/plugin/
│   ├── Energy3DExportPlugin.java             (Classe principale du plugin)
│   ├── PlanExporter.java                      (Export du plan)
│   └── WallConverter.java                     (Conversion des murs)
└── src/main/resources/com/eteks/sweethome3d/plugin/
    ├── Energy3DExportPlugin.properties        (Propriétés - anglais)
    └── Energy3DExportPlugin_fr.properties     (Propriétés - français)
```
