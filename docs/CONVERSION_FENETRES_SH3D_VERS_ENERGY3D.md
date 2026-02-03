# Conversion des fenêtres (et portes) SH3D → Energy3D

## 1. État actuel

- **PlanExporter** convertit uniquement **les murs** et la **fondation**. Les fenêtres et portes ne sont pas exportées.
- Chaque mur SH3D est converti en `org.concord.energy3d.model.Wall` : position (u,v,z) sur la fondation, épaisseur, hauteur, U-value, etc. Le mur est ajouté à `foundation.getChildren()`.
- Aucun appel à une conversion de type `convertWindowToEnergy3D` ou traitement de `home.getFurniture()` pour les `HomeDoorOrWindow`.

---

## 2. Modèle SH3D (fenêtres / portes)

- **Classe** : `HomeDoorOrWindow` (dans `home.getFurniture()`), implémente `DoorOrWindow`.
- **Position / dimensions** (héritées de `HomePieceOfFurniture`) :
  - `getX()`, `getY()` : centre en cm dans le plan
  - `getAngle()` : orientation en degrés
  - `getWidth()`, `getDepth()`, `getHeight()` : dimensions en cm
- **Ouverture dans le mur** (pour le “trou” dans le mur) :
  - `getWallWidth()`, `getWallLeft()` : largeur et décalage gauche (en % 0–1 de la pièce)
  - `getWallHeight()`, `getWallTop()` : hauteur et décalage haut (en % 0–1)
- **Association au mur** : il n’y a pas de référence explicite “ce bloc est sur ce mur”. Le mur est déterminé par la position dans le plan :
  - soit le point (x, y) est **dans** un mur : `wall.containsPoint(x, y, includeBaseboards, margin)`
  - soit on prend le mur dont l’**intersection** (surface) avec le rectangle de la porte/fenêtre est maximale (voir `PlanController.adjustPieceOfFurnitureOnWallAt`).

Pour l’export, on peut donc :
1. Parcourir `home.getFurniture()` et ne garder que les `HomeDoorOrWindow`.
2. Pour chaque élément, déterminer le mur SH3D porteur avec `wall.containsPoint(door.getX(), door.getY(), false, margin)` (ou une marge adaptée), ou en comparant les surfaces d’intersection avec chaque mur du même niveau.

---

## 3. Modèle Energy3D (fenêtres)

- **Classe** : `org.concord.energy3d.model.Wall` pour le mur, `org.concord.energy3d.model.Window` pour la fenêtre.
- **Hiérarchie** :
  - La fenêtre est un **enfant du mur** : `window.setContainer(wall)` et `wall.getChildren().add(window)`.
  - Lors de l’ajout à la scène, on fait `scene.add(foundation)` ; la scène parcourt récursivement les enfants, donc si les fenêtres sont déjà dans `wall.getChildren()` **avant** d’ajouter le mur à la fondation, elles seront ajoutées automatiquement (voir `Scene.add(HousePart part)` qui appelle `add(child)` pour chaque enfant).
- **Window** :
  - Hérite de `HousePart(2, 4, 30.0)` : 4 points, rectangle.
  - **Points** (indices 0–3) : même convention que le mur conteneur, en coordonnées **relatives** (u, v, z) :
    - 0 = bas gauche, 1 = haut gauche, 2 = bas droite, 3 = haut droite.
  - Pour un mur (conteneur horizontal), `HousePart.toRelative` / `toAbsolute` utilisent :
    - **u** = projection sur l’axe (p0→p2) du mur (longueur),
    - **v** = projection sur (p0→p1) (souvent 0 pour une fenêtre sur un mur),
    - **z** = hauteur.
  - Propriétés thermiques : `setSolarHeatGainCoefficient(double)`, `setUValue(double)` (et éventuellement `setVolumetricHeatCapacity`).
- **Création minimale** d’une fenêtre Energy3D (équivalent réflexion) :
  1. `Window window = Window.class.getDeclaredConstructor().newInstance()`
  2. `window.setContainer(energy3dWall)`
  3. Remplir la liste `points` (4 `Vector3`) en (u, v, z) relatifs au mur.
  4. Marquer `firstPointInserted = true`, appeler `complete()` puis `draw()`.
  5. `energy3dWall.getChildren().add(window)`.
  Le tout **avant** d’ajouter le mur à `foundation.getChildren()`, pour que `scene.add(foundation)` inclue aussi les fenêtres.

---

## 4. Plan de conversion (fenêtres depuis SH3D)

### 4.1 Données à disposer

- **Carte** `Wall (SH3D) → Object (Energy3D Wall)` : construite pendant la conversion des murs (comme aujourd’hui on crée un mur Energy3D par mur SH3D, on peut stocker `sh3dWall → energy3dWall` dans une `Map`).
- **Niveau** : ne traiter que les portes/fenêtres du niveau exporté (ex. niveau terrain), ou tous les niveaux si on étend l’export multi-étages. Pour l’instant, filtrer par `piece.getLevel()` / niveau de la pièce terrain.

### 4.2 Algorithme

1. **Après** avoir converti tous les murs et rempli la map `sh3dWall → energy3dWall` :
   - Parcourir `home.getFurniture()`.
   - Pour chaque `piece` tel que `piece instanceof HomeDoorOrWindow` :
     - (Optionnel) Exclure les portes si on ne veut que les fenêtres : pas de méthode “isDoor” directe dans l’API SH3D ; on peut tout exporter en fenêtre Energy3D ou ajouter un filtre sur le catalogue/nom.
2. **Trouver le mur SH3D** pour cette porte/fenêtre :
   - Même niveau : `wall.isAtLevel(piece.getLevel())` (ou niveau terrain).
   - Mur contenant le centre : `wall.containsPoint((float)piece.getX(), (float)piece.getY(), false, margin)` avec une marge raisonnable (ex. 1–2 cm).
   - Si plusieurs murs contiennent le point, prendre par exemple le premier ou celui dont la distance au centre du segment est minimale.
3. **Récupérer le mur Energy3D** : `energy3dWall = map.get(sh3dWall)`. Si `null`, ignorer cette porte/fenêtre (mur non exporté).
4. **Calculer le rectangle de la fenêtre** sur le mur en coordonnées Energy3D :
   - **Longueur du mur** (SH3D) en cm : `wallLengthCm = distance((xStart,yStart), (xEnd,yEnd))`.
   - **Hauteur du mur** (SH3D) : `wallHeightCm = wall.getHeight() != null ? wall.getHeight() : home.getWallHeight()`.
   - En SH3D, l’ouverture est définie par des **ratios** (wallLeft, wallTop, wallWidth, wallHeight) par rapport aux dimensions de la porte/fenêtre. Les méthodes `getWallLeft()`, `getWallTop()`, `getWallWidth()`, `getWallHeight()` renvoient des valeurs entre 0 et 1 (pourcentage). L’ouverture sur le mur peut être interprétée comme :
     - largeur ouverture = `wallWidth * piece.getWidth()` (cm),
     - hauteur ouverture = `wallHeight * piece.getHeight()` (cm),
     - position le long du mur : projeter le centre (x,y) sur le segment (xStart,yStart)–(xEnd,yEnd) → paramètre `t` ∈ [0,1]. Position “bas gauche” de l’ouverture le long du mur : `t * wallLengthCm - (largeur ouverture/2)` (en cm), à convertir en **u** Energy3D (u ∈ [0,1] si le mur va de 0 à 1).
   - Position verticale : `wallTop` donne le décalage du haut de l’ouverture par rapport au mur ; en déduire la cote z du bas de la fenêtre en m (puis en unités Energy3D avec `SCALE_CM_TO_ENERGY3D`).
   - Convertir ces grandeurs (cm, m) en **unités Energy3D** (même facteur que pour les murs) et en (u, v, z) relatifs au mur :
     - u_start, u_end (début/fin le long du mur),
     - z_bottom, z_top (bas/haut de la fenêtre).
   - Les 4 points de la fenêtre en relatif : (u_start, 0, z_bottom), (u_start, 0, z_top), (u_end, 0, z_bottom), (u_end, 0, z_top) pour 0,1,2,3 (avec v=0 car fenêtre dans le plan du mur).
5. **Créer la fenêtre Energy3D** (via réflexion dans le plugin) :
   - Charger la classe `org.concord.energy3d.model.Window`, instancier.
   - `setContainer(energy3dWall)`.
   - Remplir `points` (liste de 4 Vector3) avec les (u, v, z) calculés.
   - Mettre `firstPointInserted = true`, appeler `complete()`, `draw()`.
   - Propriétés thermiques : `setSolarHeatGainCoefficient(0.5)` (ou valeur par défaut), `setUValue(2.0)` (ou déduit du catalogue SH3D si disponible).
   - `energy3dWall.getChildren().add(window)`.
6. Ne pas appeler `scene.add(window)` séparément : l’ajout du mur (déjà dans `foundation.getChildren()`) suffit, car `scene.add(foundation)` parcourt les enfants récursivement.

### 4.3 Détails à ajuster dans le code

- **Réflexion** : le plugin n’a pas accès direct aux classes Energy3D ; il faut donc utiliser le même `Energy3DClassLoader` et des `getMethod` / `getField` pour `Window`, `setContainer`, `points`, `firstPointInserted`, `complete()`, `draw()`, `getChildren().add(...)`, etc.
- **Ordre d’exécution** : créer et attacher les fenêtres **après** `convertWallToEnergy3D` pour chaque mur, mais **avant** d’ajouter ce mur à `foundation.getChildren()`. Ainsi, au moment de `foundation.getChildren().add(energy3dWall)`, le mur a déjà ses fenêtres dans `getChildren()`.
- **Portes** : Energy3D a une classe `Door`. On peut soit convertir toutes les `HomeDoorOrWindow` en `Window`, soit distinguer portes et fenêtres (par nom/catalogue) et utiliser `Door` pour les portes (à implémenter de la même façon côté position sur le mur).

---

## 5. Résumé des fichiers à modifier

| Fichier | Modification |
|--------|---------------|
| `PlanExporter.java` | 1) Map `Wall (SH3D) → Object (Energy3D Wall)` pendant la conversion des murs. 2) Après conversion d’un mur, récupérer les `HomeDoorOrWindow` dont le mur porteur est ce mur SH3D ; pour chacune, calculer (u,v,z) et appeler une nouvelle méthode `convertWindowToEnergy3D(...)` qui crée la fenêtre Energy3D et l’ajoute à `energy3dWall.getChildren()`. 3) Gérer le niveau (filtrer par niveau terrain ou niveau du mur). |

---

## 6. Références dans le code

- **PlanExporter** : boucle des murs ~l.318–341 ; `convertWallToEnergy3D` ~l.2146.
- **Energy3D** : `Window` (points, setContainer, complete, draw), `Wall.getChildren()`, `HousePart.toRelative` / `toAbsolute` (coordonnées u,v,z).
- **SH3D** : `Home.getWalls()`, `Home.getFurniture()`, `HomeDoorOrWindow`, `Wall.containsPoint`, `PlanController.adjustPieceOfFurnitureOnWallAt` (recherche du mur par intersection).

Une fois cette logique en place, les fenêtres (et éventuellement les portes) exportées depuis SH3D apparaîtront sur les murs Energy3D avec la bonne position et taille, et seront prises en compte dans les calculs thermiques (SHGC, U-value).
