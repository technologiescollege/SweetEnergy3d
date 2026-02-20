# SweetEnergy3D Plugin (FR / EN)

## Francais

### A quoi sert ce plugin ?

`SweetEnergy3D` est un plugin pour SweetHome3D qui exporte un plan SH3D vers un fichier `Energy3D` (`.ng3`).

L'export se base sur les niveaux SH3D et sur des mots-cles configurables dans `config.json`.

### Ce que fait l'export

- créé la fondation Energy3D à partir de la pièce du niveau `fondation`,
- exporte les murs SH3D en murs Energy3D (`intérieur` / `extérieur` selon mots-clés),
- applique des textures automatiquement (fondation, murs interieurs, murs exterieurs),
- exporte les arbres comme `Energy3D.Tree`,
- exporte les buissons comme `Energy3D.Wall` avec une texture buisson,
- ignore les objets hors fondation.

Note: la création automatique du toit est désactivée (toit à créer manuellement dans Energy3D).

### `config.json` : catégories et mots-clés

Toutes les comparaisons de mots-clés sont insensibles à la casse et des alias sont déjà dans le fichier (on peut en rajouter d'autres, puis recompiler).

- `external_wall`: niveaux identifiés comme murs extérieurs
- `internal_wall`: niveaux identifiés comme murs intérieurs
- `roof`: mots-clés reservés pour le niveau du toit généré avec le plugin correspondant
- `foundation`: niveau servant de base/fondation
- `trees`: niveaux à scanner pour export d'arbres
- `bushes`: niveaux à scanner pour export de buissons
- `terrain3d`: mots-clés reservés pour objets que forme le terrain 3D généré avec le plugin correspondant

Extrait actuel de `config.json`:

```json
{
  "external_wall": { "keywords": ["extérieur", "exterieur", "outside", "outer", "external", "outdoor"] },
  "internal_wall": { "keywords": ["intérieur", "interieur", "cloison", "inside", "interior", "internal", "indoor"] },
  "roof":         { "keywords": ["toit", "roof", "toiture"] },
  "foundation":   { "keywords": ["terrain", "fondation", "foundation", "ground"] },
  "trees":        { "keywords": ["arbre", "tree", "arbres", "trees"] },
  "bushes":       { "keywords": ["buisson", "bush", "buissons", "bushes", "haie", "hedge"] },
  "terrain3d":    { "keywords": ["3dterrain", "terrain3d", "3d terrain", "terrain 3d"] }
}
```

### Build et installation

```bash
mvn clean package
```

Génère `target/sweetenergy3d.sh3p`.

Copier ce fichier dans le dossier plugins Sweet Home 3D (portable ou `%APPDATA%\SweetHome3D\plugins\`).

---

## English

### What is this plugin for?

`SweetEnergy3D` is a SweetHome3D plugin that exports a SH3D plan to an `Energy3D` file (`.ng3`).

The export relies on SH3D level names and configurable keywords defined in `config.json`.

### What the exporter does (summary)

- creates the Energy3D foundation from the room on the `foundation` level,
- exports SH3D walls to Energy3D walls (`interior`/`exterior` based on keywords),
- auto-applies textures (foundation, interior walls, exterior walls),
- exports trees as `Energy3D.Tree`,
- exports bushes as `Energy3D.Wall` with bush texture,
- ignores objects outside the foundation area.

Note: automatic roof generation is disabled (roof mus be created manually in Energy3D).

### `config.json`: categories and keywords

All keyword matching is case-insensitive.

- `external_wall`: levels treated as exterior walls
- `internal_wall`: levels treated as interior walls
- `roof`: reserved keywords for roof levels
- `foundation`: level used as base/foundation
- `trees`: levels scanned for tree export
- `bushes`: levels scanned for bush export
- `terrain3d`: reserved keywords for 3D terrain objects

Current `config.json` excerpt:

```json
{
  "external_wall": { "keywords": ["extérieur", "exterieur", "outside", "outer", "external", "outdoor"] },
  "internal_wall": { "keywords": ["intérieur", "interieur", "cloison", "inside", "interior", "internal", "indoor"] },
  "roof":         { "keywords": ["toit", "roof", "toiture"] },
  "foundation":   { "keywords": ["terrain", "fondation", "foundation", "ground"] },
  "trees":        { "keywords": ["arbre", "tree", "arbres", "trees"] },
  "bushes":       { "keywords": ["buisson", "bush", "buissons", "bushes", "haie", "hedge"] },
  "terrain3d":    { "keywords": ["3dterrain", "terrain3d", "3d terrain", "terrain 3d"] }
}
```

### Build and installation

```bash
mvn clean package
```

This generates `target/sweetenergy3d.sh3p`.

Copy it into your Sweet Home 3D plugins folder (portable version or `%APPDATA%\SweetHome3D\plugins\`).
