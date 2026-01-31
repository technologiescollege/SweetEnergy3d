package com.eteks.sweethome3d.plugin;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ClassLoader personnalisé pour charger les classes Energy3D depuis les JARs externes
 */
public class Energy3DClassLoader {
    
    private static ClassLoader energy3dClassLoader = null;
    
    /**
     * Crée et retourne un ClassLoader pour Energy3D
     * @param logWriter PrintWriter pour écrire les logs (peut être null)
     * @return Le ClassLoader ou null si les JARs ne peuvent pas être chargés
     */
    public static ClassLoader getEnergy3DClassLoader(java.io.PrintWriter logWriter) {
        if (energy3dClassLoader != null) {
            if (logWriter != null) {
                logWriter.println("ClassLoader Energy3D déjà créé, réutilisation");
                logWriter.flush();
            }
            return energy3dClassLoader;
        }
        
        try {
            List<URL> jarUrls = new ArrayList<URL>();
            
            File energy3dJar = null;
            
            if (logWriter != null) {
                logWriter.println("Recherche du JAR Energy3D...");
                logWriter.println("Répertoire de travail: " + System.getProperty("user.dir"));
                logWriter.flush();
            }
            
            // D'abord, essayer de trouver depuis l'emplacement du plugin
            try {
                URL pluginUrl = Energy3DClassLoader.class.getProtectionDomain()
                    .getCodeSource().getLocation();
                File pluginFile = new File(pluginUrl.toURI());
                File pluginDir = pluginFile.getParentFile();
                
                if (logWriter != null) {
                    logWriter.println("Emplacement du plugin: " + pluginFile.getAbsolutePath());
                    logWriter.println("Répertoire du plugin: " + (pluginDir != null ? pluginDir.getAbsolutePath() : "null"));
                    logWriter.flush();
                }
                
                // Chercher energy3d.jar dans plusieurs emplacements relatifs au plugin
                // Le plugin est dans data/plugins/, donc on cherche depuis le répertoire Sweet Home 3D
                // Structure: SweetHome3D-7.5-portable/data/plugins/sweetenergy3d.sh3p
                // On cherche: ../../../energy3d_src/exe/energy3d.jar (depuis plugins/)
                File[] searchDirs = {
                    pluginDir != null ? pluginDir.getParentFile() : null,  // ../ depuis plugins/ -> data/
                    pluginDir != null && pluginDir.getParentFile() != null ? pluginDir.getParentFile().getParentFile() : null,  // ../../ depuis plugins/ -> SweetHome3D-7.5-portable/
                    pluginDir != null && pluginDir.getParentFile() != null && pluginDir.getParentFile().getParentFile() != null ? 
                        pluginDir.getParentFile().getParentFile().getParentFile() : null,  // ../../../ depuis plugins/ -> energy3d-master/
                    new File(System.getProperty("user.dir")),  // Répertoire de travail
                };
                
                for (File searchDir : searchDirs) {
                    if (searchDir == null) continue;
                    
                    if (logWriter != null) {
                        logWriter.println("Recherche dans: " + searchDir.getAbsolutePath());
                        logWriter.flush();
                    }
                    
                    // Essayer energy3d_src/exe/energy3d.jar
                    File testJar = new File(searchDir, "energy3d_src/exe/energy3d.jar");
                    if (logWriter != null) {
                        logWriter.println("  Test: " + testJar.getAbsolutePath() + " (existe: " + testJar.exists() + ")");
                        logWriter.flush();
                    }
                    if (testJar.exists() && testJar.isFile()) {
                        energy3dJar = testJar;
                        if (logWriter != null) {
                            logWriter.println("✓ JAR Energy3D trouvé: " + energy3dJar.getAbsolutePath());
                            logWriter.flush();
                        }
                        break;
                    }
                    
                    // Essayer ../energy3d_src/exe/energy3d.jar
                    if (searchDir.getParentFile() != null) {
                        testJar = new File(searchDir.getParentFile(), "energy3d_src/exe/energy3d.jar");
                        if (logWriter != null) {
                            logWriter.println("  Test: " + testJar.getAbsolutePath() + " (existe: " + testJar.exists() + ")");
                            logWriter.flush();
                        }
                        if (testJar.exists() && testJar.isFile()) {
                            energy3dJar = testJar;
                            if (logWriter != null) {
                                logWriter.println("✓ JAR Energy3D trouvé: " + energy3dJar.getAbsolutePath());
                                logWriter.flush();
                            }
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                if (logWriter != null) {
                    logWriter.println("Exception lors de la recherche depuis le plugin: " + e.getMessage());
                    e.printStackTrace(logWriter);
                    logWriter.flush();
                }
            }
            
            // Si toujours pas trouvé, essayer des chemins absolus
            if (energy3dJar == null) {
                if (logWriter != null) {
                    logWriter.println("Recherche dans les chemins absolus...");
                    logWriter.flush();
                }
                String[] possiblePaths = {
                    System.getProperty("user.dir") + "/energy3d_src/exe/energy3d.jar",
                    System.getProperty("user.dir") + "/../energy3d_src/exe/energy3d.jar",
                    "../energy3d_src/exe/energy3d.jar",
                    "../../energy3d_src/exe/energy3d.jar",
                };
                
                for (String path : possiblePaths) {
                    File testFile = new File(path);
                    if (logWriter != null) {
                        logWriter.println("  Test: " + testFile.getAbsolutePath() + " (existe: " + testFile.exists() + ")");
                        logWriter.flush();
                    }
                    if (testFile.exists() && testFile.isFile()) {
                        energy3dJar = testFile;
                        if (logWriter != null) {
                            logWriter.println("✓ JAR Energy3D trouvé: " + energy3dJar.getAbsolutePath());
                            logWriter.flush();
                        }
                        break;
                    }
                }
            }
            
            if (energy3dJar == null || !energy3dJar.exists()) {
                if (logWriter != null) {
                    logWriter.println("✗ ERREUR: JAR Energy3D non trouvé!");
                    logWriter.println("Vérifiez que energy3d.jar existe dans energy3d_src/exe/");
                    logWriter.flush();
                }
                return null;
            }
            
            jarUrls.add(energy3dJar.toURI().toURL());
            
            if (logWriter != null) {
                logWriter.println("Ajout des JARs Ardor3D (sans sources)...");
                logWriter.flush();
            }
            
            // Ajouter les JARs Ardor3D (sans les sources)
            // Sélection des JARs Ardor3D: préférer lib/ardor3d (legacy, avec méthodes comme setCastsShadows),
            // sinon basculer vers lib/ardor3d1 (patch Energy3D)
            File ardor3d1Dir = new File(energy3dJar.getParentFile(), "lib/ardor3d1");
            File ardor3dDir = new File(energy3dJar.getParentFile(), "lib/ardor3d");
            File chosenArdorDir = ardor3dDir.exists() && ardor3dDir.isDirectory() ? ardor3dDir : ardor3d1Dir;
            if (logWriter != null) {
                logWriter.println("Répertoire Ardor3D choisi: " + chosenArdorDir.getAbsolutePath() + " (existe: " + chosenArdorDir.exists() + ")");
                logWriter.println("  Politique: priorité à 'lib/ardor3d' pour compatibilité API; fallback 'lib/ardor3d1'");
                logWriter.flush();
            }
            if (chosenArdorDir.exists() && chosenArdorDir.isDirectory()) {
                File[] ardorJars = chosenArdorDir.listFiles((dir, name) -> name.endsWith(".jar") && !name.contains("-sources"));
                if (ardorJars != null) {
                    if (logWriter != null) {
                        logWriter.println("Nombre de JARs Ardor3D trouvés (sans sources): " + ardorJars.length);
                        logWriter.flush();
                    }
                    java.util.Arrays.sort(ardorJars, (a, b) -> a.getName().compareTo(b.getName()));
                    for (File ardorJar : ardorJars) {
                        jarUrls.add(ardorJar.toURI().toURL());
                        if (logWriter != null) {
                            logWriter.println("  Ajouté: " + ardorJar.getName());
                            logWriter.flush();
                        }
                    }
                }
            } else {
                if (logWriter != null) {
                    logWriter.println("AVERTISSEMENT: Répertoire Ardor3D non trouvé!");
                    logWriter.flush();
                }
            }
            
            // Préférer éventuellement les JARs Ardor3D de la distribution WebStart (souvent plus récents)
            try {
                File projectRoot = new File(System.getProperty("user.dir")).getParentFile();
                File webstartArdorDir = new File(projectRoot, "energy3d-master\\exe\\webstart\\resources\\ardor3d");
                if (webstartArdorDir.exists() && webstartArdorDir.isDirectory()) {
                    if (logWriter != null) {
                        logWriter.println("Répertoire Ardor3D (WebStart): " + webstartArdorDir.getAbsolutePath());
                        logWriter.flush();
                    }
                    File[] wsJars = webstartArdorDir.listFiles((dir, name) -> name.endsWith(".jar"));
                    if (wsJars != null && wsJars.length > 0) {
                        java.util.Arrays.sort(wsJars, (a, b) -> a.getName().compareTo(b.getName()));
                        java.util.List<URL> wsUrls = new java.util.ArrayList<>();
                        for (File f : wsJars) {
                            wsUrls.add(f.toURI().toURL());
                            if (logWriter != null) {
                                logWriter.println("  Préférence WebStart ajoutée: " + f.getName());
                                logWriter.flush();
                            }
                        }
                        // Insérer en tête pour priorité de chargement
                        java.util.List<URL> merged = new java.util.ArrayList<>(wsUrls);
                        merged.addAll(jarUrls);
                        jarUrls = merged;
                    }
                }
            } catch (Exception ignore) {}
            
            // Ajouter d'autres JARs nécessaires
            // energy3dJar est dans exe/, donc libDir doit être exe/lib/
            File libDir = new File(energy3dJar.getParentFile(), "lib");
            if (logWriter != null) {
                logWriter.println("Ajout des autres JARs de dépendances...");
                logWriter.println("Répertoire lib: " + libDir.getAbsolutePath() + " (existe: " + libDir.exists() + ")");
                logWriter.flush();
            }
            
            // JARs nécessaires - chercher dans lib/ directement
            String[] requiredJars = {
                "getdown.jar",
                "samskivert.jar",
                "jdom2.jar",
                "google-collections.jar",
                "orange-extensions.jar",
                "slf4j-api.jar"
            };
            
            for (String jarName : requiredJars) {
                File jarFile = new File(libDir, jarName);
                if (jarFile.exists() && jarFile.isFile()) {
                    jarUrls.add(jarFile.toURI().toURL());
                    if (logWriter != null) {
                        logWriter.println("  ✓ Ajouté: " + jarName);
                        logWriter.flush();
                    }
                } else {
                    if (logWriter != null) {
                        logWriter.println("  ✗ Non trouvé: " + jarName + " (cherché dans: " + jarFile.getAbsolutePath() + ")");
                        logWriter.flush();
                    }
                }
            }
            
            // Ajouter les JARs JOGL
            File joglDir = new File(libDir, "jogl");
            if (joglDir.exists() && joglDir.isDirectory()) {
                File[] joglJars = joglDir.listFiles((dir, name) -> name.endsWith(".jar"));
                if (joglJars != null) {
                    if (logWriter != null) {
                        logWriter.println("Ajout des JARs JOGL (" + joglJars.length + " fichiers)...");
                        logWriter.flush();
                    }
                    for (File joglJar : joglJars) {
                        jarUrls.add(joglJar.toURI().toURL());
                        if (logWriter != null) {
                            logWriter.println("  Ajouté: " + joglJar.getName());
                            logWriter.flush();
                        }
                    }
                }
            }
            
            if (logWriter != null) {
                logWriter.println("Création du URLClassLoader avec " + jarUrls.size() + " JAR(s)...");
                logWriter.flush();
            }
            
            // Créer un ClassLoader personnalisé qui étend URLClassLoader et peut définir des classes
            final ClassLoader pluginClassLoader = Energy3DClassLoader.class.getClassLoader();
            final java.util.List<URL> jarUrlsFinal = new java.util.ArrayList<>(jarUrls);
            
            // Créer un ClassLoader personnalisé qui peut définir des stubs directement
            final URLClassLoader jarOnlyClassLoader = new URLClassLoader(
                jarUrls.toArray(new URL[jarUrls.size()]),
                null  // Pas de parent pour forcer le chargement depuis les JARs Energy3D
            ) {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    // Pour SceneHints, forcer l'utilisation du stub avec la méthode setCastsShadows(boolean)
                    if (name.equals("com.ardor3d.scenegraph.hint.SceneHints")) {
                        try {
                            String resourcePath = "com/ardor3d/scenegraph/hint/SceneHints.class";
                            java.io.InputStream is = pluginClassLoader.getResourceAsStream(resourcePath);
                            if (is == null) {
                                throw new ClassNotFoundException("Stub SceneHints introuvable: " + resourcePath);
                            }
                            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                            byte[] data = new byte[4096];
                            int nRead;
                            while ((nRead = is.read(data, 0, data.length)) != -1) {
                                buffer.write(data, 0, nRead);
                            }
                            byte[] classBytes = buffer.toByteArray();
                            is.close();
                            Class<?> c = defineClass(name, classBytes, 0, classBytes.length);
                            resolveClass(c);
                            if (logWriter != null) {
                                logWriter.println("  SceneHints (compat) défini directement dans jarOnlyClassLoader");
                                logWriter.flush();
                            }
                            return c;
                        } catch (Exception e) {
                            if (logWriter != null) {
                                logWriter.println("  ✗ ERREUR lors de la définition de SceneHints (compat): " + e.getMessage());
                                logWriter.flush();
                            }
                            // Si échec, continuer le flux normal
                        }
                    }
                    // Stub SceneManager pour export headless (évite MainPanel/OpenGL)
                    if (name.equals("org.concord.energy3d.scene.SceneManager")) {
                        try {
                            String resourcePath = "org/concord/energy3d/scene/SceneManager.class";
                            java.io.InputStream is = pluginClassLoader.getResourceAsStream(resourcePath);
                            if (is != null) {
                                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                                byte[] data = new byte[4096];
                                int nRead;
                                while ((nRead = is.read(data, 0, data.length)) != -1) {
                                    buffer.write(data, 0, nRead);
                                }
                                byte[] classBytes = buffer.toByteArray();
                                is.close();
                                Class<?> c = defineClass(name, classBytes, 0, classBytes.length);
                                resolveClass(c);
                                if (logWriter != null) {
                                    logWriter.println("  SceneManager (stub headless) défini via jarOnlyClassLoader");
                                    logWriter.flush();
                                }
                                return c;
                            }
                        } catch (Exception e) {
                            if (logWriter != null) {
                                logWriter.println("  ⚠ Stub SceneManager non chargé: " + e.getMessage());
                                logWriter.flush();
                            }
                        }
                    }
                    // Pour ces classes critiques, forcer l'utilisation du stub local (plugin)
                    // au lieu de la version des JARs, pour garantir la compatibilité (éviter VerifyError, méthodes manquantes)
                    if (name.equals("com.ardor3d.renderer.state.MaterialState") ||
                        name.equals("com.ardor3d.renderer.state.LightState") ||
                        name.equals("com.ardor3d.extension.effect.bloom.BloomRenderPass") ||
                        name.equals("com.ardor3d.image.util.ImageLoader") ||
                        name.equals("com.ardor3d.util.geom.BufferUtils") ||
                        name.equals("com.ardor3d.scenegraph.hint.LightCombineMode") ||
                        name.equals("com.ardor3d.scenegraph.hint.PickingHint") ||
                        name.equals("com.ardor3d.scenegraph.hint.TextureCombineMode") ||
                        name.equals("org.concord.energy3d.util.SelectUtil") ||
                        name.equals("com.ardor3d.renderer.IndexMode")) {
                        
                        try {
                            // CRITIQUE: Charger la superclasse/interface AVANT de définir le stub
                            if (name.equals("com.ardor3d.renderer.state.MaterialState") ||
                                name.equals("com.ardor3d.renderer.state.LightState")) {
                                try {
                                    loadClass("com.ardor3d.renderer.state.RenderState");
                                } catch (ClassNotFoundException e) {
                                    if (logWriter != null) logWriter.println("  ✗ ERREUR: RenderState non trouvé avant " + name);
                                    throw e;
                                }
                            } else if (name.equals("com.ardor3d.extension.effect.bloom.BloomRenderPass")) {
                                try {
                                    loadClass("com.ardor3d.renderer.pass.Pass");
                                } catch (ClassNotFoundException e) {
                                    if (logWriter != null) logWriter.println("  ✗ ERREUR: Pass non trouvé avant BloomRenderPass");
                                    throw e;
                                }
                            } else if (name.equals("com.ardor3d.image.util.ImageLoader")) {
                                try {
                                    loadClass("com.ardor3d.image.loader.ImageLoader");
                                } catch (ClassNotFoundException e) {
                                    if (logWriter != null) logWriter.println("  ✗ ERREUR: com.ardor3d.image.loader.ImageLoader non trouvé");
                                    throw e;
                                }
                            }

                            // Lire les bytes depuis le plugin
                            String resourcePath = name.replace('.', '/') + ".class";
                            java.io.InputStream is = pluginClassLoader.getResourceAsStream(resourcePath);
                            if (is == null) {
                                // Fallback: tenter de charger le stub depuis l'espace de noms _removed
                                String removedResourcePath = resourcePath.replace("com/ardor3d/", "com/ardor3d_removed/");
                                is = pluginClassLoader.getResourceAsStream(removedResourcePath);
                            }
                            
                            if (is == null) {
                                throw new ClassNotFoundException(name + ".class non trouvé dans le plugin");
                            }

                            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                            byte[] data = new byte[4096];
                            int nRead;
                            while ((nRead = is.read(data, 0, data.length)) != -1) {
                                buffer.write(data, 0, nRead);
                            }
                            byte[] classBytes = buffer.toByteArray();
                            is.close();

                            // Pour MaterialState et LightState : le bytecode compilé a souvent Object comme superclasse
                            // (RenderState absent du classpath à la compilation). Corriger avec ASM pour éviter VerifyError.
                            if (name.equals("com.ardor3d.renderer.state.MaterialState") ||
                                name.equals("com.ardor3d.renderer.state.LightState")) {
                                classBytes = fixStubSuperclass(classBytes, "java/lang/Object", "com/ardor3d/renderer/state/RenderState", logWriter);
                            }

                            // Définir la classe
                            Class<?> c = defineClass(name, classBytes, 0, classBytes.length);
                            resolveClass(c);
                            
                            if (logWriter != null) {
                                logWriter.println("  " + name + " (stub) défini explicitement via jarOnlyClassLoader");
                                logWriter.flush();
                            }
                            return c;
                        } catch (Exception e) {
                            if (logWriter != null) {
                                logWriter.println("  ✗ ERREUR lors de la définition forcée de " + name + ": " + e.getMessage());
                                e.printStackTrace(logWriter);
                                logWriter.flush();
                            }
                            // Si échec, on laisse tomber vers le super.findClass (comportement par défaut)
                        }
                    }

                    // Sphere : energy3d.jar appelle setUserData(Object) alors que cette Ardor3D ne l'a plus
                    if (name.equals("com.ardor3d.scenegraph.shape.Sphere")) {
                        try {
                            java.io.InputStream is = getResourceAsStream("com/ardor3d/scenegraph/shape/Sphere.class");
                            if (is != null) {
                                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                                byte[] data = new byte[4096];
                                int nRead;
                                while ((nRead = is.read(data, 0, data.length)) != -1) {
                                    buffer.write(data, 0, nRead);
                                }
                                byte[] classBytes = buffer.toByteArray();
                                is.close();
                                classBytes = addSetUserDataToSphere(classBytes, logWriter);
                                Class<?> c = defineClass(name, classBytes, 0, classBytes.length);
                                resolveClass(c);
                                if (logWriter != null) {
                                    logWriter.println("  Sphere (avec setUserData) défini via jarOnlyClassLoader");
                                    logWriter.flush();
                                }
                                return c;
                            }
                        } catch (Exception e) {
                            if (logWriter != null) {
                                logWriter.println("  ⚠ Sphere modifié non chargé: " + e.getMessage());
                                logWriter.flush();
                            }
                        }
                    }

                    // Vector3 : energy3d.jar appelle Vector3.isValid(ReadOnlyVector3) alors que cette Ardor3D ne l'a pas
                    if (name.equals("com.ardor3d.math.Vector3")) {
                        try {
                            java.io.InputStream is = getResourceAsStream("com/ardor3d/math/Vector3.class");
                            if (is != null) {
                                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                                byte[] data = new byte[4096];
                                int nRead;
                                while ((nRead = is.read(data, 0, data.length)) != -1) {
                                    buffer.write(data, 0, nRead);
                                }
                                byte[] classBytes = buffer.toByteArray();
                                is.close();
                                classBytes = addIsValidToVector3(classBytes, logWriter);
                                Class<?> c = defineClass(name, classBytes, 0, classBytes.length);
                                resolveClass(c);
                                if (logWriter != null) {
                                    logWriter.println("  Vector3 (avec isValid(ReadOnlyVector3)) défini via jarOnlyClassLoader");
                                    logWriter.flush();
                                }
                                return c;
                            }
                        } catch (Exception e) {
                            if (logWriter != null) {
                                logWriter.println("  ⚠ Vector3 modifié non chargé: " + e.getMessage());
                                logWriter.flush();
                            }
                        }
                    }

                    // Mesh : Foundation.init() appelle setUserData(Object) alors que cette Ardor3D ne l'a plus
                    if (name.equals("com.ardor3d.scenegraph.Mesh")) {
                        if (logWriter != null) {
                            logWriter.println("  Tentative interception Mesh pour setUserData...");
                            logWriter.flush();
                        }
                        try {
                            java.io.InputStream is = getResourceAsStream("com/ardor3d/scenegraph/Mesh.class");
                            if (is == null && logWriter != null) {
                                logWriter.println("  Mesh.class introuvable via getResourceAsStream, lecture manuelle depuis les JARs");
                                logWriter.flush();
                            }
                            // Fallback: lire depuis les URLs JAR manuellement
                            if (is == null && jarUrlsFinal != null) {
                                String resourcePath = "com/ardor3d/scenegraph/Mesh.class";
                                for (URL jarUrl : jarUrlsFinal) {
                                    try {
                                        if (!"file".equals(jarUrl.getProtocol())) continue;
                                        java.io.File jarFile = new java.io.File(jarUrl.toURI());
                                        if (!jarFile.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) continue;
                                        try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jarFile)) {
                                            java.util.jar.JarEntry entry = jf.getJarEntry(resourcePath);
                                            if (entry != null) {
                                                is = jf.getInputStream(entry);
                                                if (logWriter != null) {
                                                    logWriter.println("  Mesh.class lu depuis: " + jarFile.getName());
                                                    logWriter.flush();
                                                }
                                                break;
                                            }
                                        }
                                    } catch (Exception ignored) { }
                                }
                            }
                            if (is != null) {
                                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                                byte[] data = new byte[4096];
                                int nRead;
                                while ((nRead = is.read(data, 0, data.length)) != -1) {
                                    buffer.write(data, 0, nRead);
                                }
                                byte[] classBytes = buffer.toByteArray();
                                is.close();
                                classBytes = addSetUserDataToMesh(classBytes, logWriter);
                                Class<?> c = defineClass(name, classBytes, 0, classBytes.length);
                                resolveClass(c);
                                if (logWriter != null) {
                                    logWriter.println("  Mesh (avec setUserData) défini via jarOnlyClassLoader");
                                    logWriter.flush();
                                }
                                return c;
                            }
                        } catch (Exception e) {
                            if (logWriter != null) {
                                logWriter.println("  ⚠ Mesh modifié non chargé: " + e.getMessage());
                                logWriter.flush();
                            }
                        }
                    }

                    // Essayer de charger depuis les JARs officiels
                    try {
                        return super.findClass(name);
                    } catch (ClassNotFoundException notFound) {
                        throw notFound;
                    }
                }

            };
            
            // Créer un ClassLoader personnalisé qui délègue d'abord au ClassLoader Energy3D
            // puis au ClassLoader parent (plugin) pour les classes Java standard et les stubs
            energy3dClassLoader = new ClassLoader(pluginClassLoader) {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    // D'abord, essayer de charger depuis les JARs Energy3D
                    try {
                        return jarOnlyClassLoader.loadClass(name);
                    } catch (ClassNotFoundException e) {
                        // Si non trouvé, déléguer au parent (pour les classes Java standard et les stubs comme MaterialState)
                        return super.findClass(name);
                    }
                }
                
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    // TOUJOURS essayer les JARs Energy3D/Ardor3D en premier
                    // Cela garantit que RenderState est chargé depuis les JARs avant MaterialState
                    if (name.startsWith("org.concord.energy3d.") || 
                        name.startsWith("com.ardor3d.")) {
                        try {
                            Class<?> c = jarOnlyClassLoader.loadClass(name);
                            if (resolve) {
                                resolveClass(c);
                            }
                            return c;
                        } catch (ClassNotFoundException e) {
                            // Fallback: tenter le parent (plugin) pour les classes de compatibilité présentes dans le plugin
                            Class<?> c = super.loadClass(name, resolve);
                            if (resolve && c != null) {
                                resolveClass(c);
                            }
                            return c;
                        }
                    }
                    // Pour les autres classes, utiliser le ClassLoader parent
                    return super.loadClass(name, resolve);
                }
            };
            
            // Vérifier que RenderState est accessible
            if (logWriter != null) {
                logWriter.println("✓ ClassLoader Energy3D créé avec succès");
                logWriter.println("  Type: " + energy3dClassLoader.getClass().getName());
                logWriter.println("  Vérification de l'accessibilité de RenderState...");
                logWriter.flush();
                
                // Vérifier les URLs dans le ClassLoader
                if (jarOnlyClassLoader instanceof URLClassLoader) {
                    URLClassLoader urlLoader = (URLClassLoader) jarOnlyClassLoader;
                    URL[] urls = urlLoader.getURLs();
                    logWriter.println("  Nombre d'URLs dans le ClassLoader: " + urls.length);
                    int ardorCount = 0;
                    boolean ardorCoreFound = false;
                    for (URL url : urls) {
                        String urlStr = url.toString();
                        if (urlStr.contains("ardor3d")) {
                            ardorCount++;
                            if (urlStr.contains("ardor3d-core")) {
                                ardorCoreFound = true;
                                logWriter.println("    ✓ ardor3d-core.jar trouvé: " + urlStr);
                            }
                        }
                    }
                    logWriter.println("  Nombre de JARs Ardor3D: " + ardorCount);
                    if (!ardorCoreFound) {
                        logWriter.println("  ✗ ERREUR: ardor3d-core.jar non trouvé dans le ClassLoader!");
                    }
                    logWriter.flush();
                }
                
                try {
                    Class<?> renderStateTest = energy3dClassLoader.loadClass("com.ardor3d.renderer.state.RenderState");
                    logWriter.println("  ✓ RenderState accessible: " + renderStateTest.getName());
                    logWriter.println("  ClassLoader de RenderState: " + renderStateTest.getClassLoader().getClass().getName());
                    
                    // Vérifier que c'est bien le même ClassLoader
                    if (renderStateTest.getClassLoader() == jarOnlyClassLoader) {
                        logWriter.println("  ✓ RenderState est chargé depuis les JARs Energy3D");
                    } else {
                        logWriter.println("  ⚠ AVERTISSEMENT: RenderState ClassLoader différent du jarOnlyClassLoader");
                        logWriter.println("    RenderState ClassLoader: " + renderStateTest.getClassLoader().getClass().getName());
                        logWriter.println("    jarOnlyClassLoader: " + jarOnlyClassLoader.getClass().getName());
                    }
                    
                    // Vérifier la présence de com.ardor3d.scenegraph.hint.SceneHints et de la méthode setCastsShadows(boolean)
                    try {
                        Class<?> sceneHintsClass = energy3dClassLoader.loadClass("com.ardor3d.scenegraph.hint.SceneHints");
                        logWriter.println("  ✓ SceneHints accessible: " + sceneHintsClass.getName());
                        logWriter.println("  ClassLoader de SceneHints: " + sceneHintsClass.getClassLoader().getClass().getName());
                        boolean hasSetCasts = false;
                        for (java.lang.reflect.Method m : sceneHintsClass.getMethods()) {
                            if (m.getName().equals("setCastsShadows") && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == boolean.class) {
                                hasSetCasts = true;
                                break;
                            }
                        }
                        logWriter.println("  Méthode SceneHints.setCastsShadows(boolean) présente: " + hasSetCasts);
                        if (!hasSetCasts) {
                            logWriter.println("  ⚠ AVERTISSEMENT: setCastsShadows(absent) — certaines versions Energy3D appellent cette méthode.");
                            logWriter.println("    Util.disablePickShadowLight sera compatible si Energy3D.jar est compilé sans cet appel.");
                        }
                    } catch (ClassNotFoundException e2) {
                        logWriter.println("  ✗ ERREUR: SceneHints non accessible: " + e2.getMessage());
                        logWriter.println("  Cela peut provoquer NoSuchMethodError pendant la création de Foundation.");
                    }
                } catch (ClassNotFoundException e) {
                    logWriter.println("  ✗ ERREUR: RenderState non accessible: " + e.getMessage());
                    logWriter.println("  Cela empêchera Foundation d'être créé.");
                    logWriter.println("  Vérification manuelle de ardor3d-core.jar...");
                    // Vérifier que ardor3d-core.jar existe et contient RenderState
                    File ardor3dDirCheck = new File(energy3dJar.getParentFile(), "lib/ardor3d");
                    File ardorCoreJar = new File(ardor3dDirCheck, "ardor3d-core.jar");
                    if (ardorCoreJar.exists()) {
                        logWriter.println("  ardor3d-core.jar existe: " + ardorCoreJar.getAbsolutePath());
                        logWriter.println("  Taille: " + ardorCoreJar.length() + " bytes");
                    } else {
                        logWriter.println("  ✗ ardor3d-core.jar n'existe pas dans: " + ardor3dDirCheck.getAbsolutePath());
                    }
                }
                logWriter.flush();
            }
            
            return energy3dClassLoader;
            
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("✗ ERREUR lors de la création du ClassLoader: " + e.getMessage());
                e.printStackTrace(logWriter);
                logWriter.flush();
            }
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Corrige la superclasse dans le bytecode d'un stub (Object → RenderState) pour éviter VerifyError.
     * Utilise ASM pour réécrire uniquement le nom de la superclasse.
     */
    private static byte[] fixStubSuperclass(byte[] classBytes, String currentSuperName, String newSuperName,
            java.io.PrintWriter logWriter) {
        try {
            ClassReader cr = new ClassReader(classBytes);
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    String actualSuper = (superName != null && currentSuperName.equals(superName)) ? newSuperName : superName;
                    super.visit(version, access, name, signature, actualSuper, interfaces);
                }
            };
            cr.accept(cv, 0);
            byte[] fixed = cw.toByteArray();
            if (logWriter != null) {
                logWriter.println("  Bytecode stub corrigé: superclasse " + currentSuperName + " → " + newSuperName);
                logWriter.flush();
            }
            return fixed;
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("  ⚠ Correction ASM superclasse échouée: " + e.getMessage() + ", utilisation du bytecode original");
                logWriter.flush();
            }
            return classBytes;
        }
    }

    /**
     * Ajoute la méthode setUserData(Object) au bytecode de Sphere si absente (API Ardor3D ancienne).
     */
    private static byte[] addSetUserDataToSphere(byte[] classBytes, java.io.PrintWriter logWriter) {
        try {
            final boolean[] hasSetUserData = { false };
            ClassReader cr = new ClassReader(classBytes);
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
                    if ("setUserData".equals(methodName) && "(Ljava/lang/Object;)V".equals(descriptor)) {
                        hasSetUserData[0] = true;
                    }
                    return super.visitMethod(access, methodName, descriptor, signature, exceptions);
                }
                @Override
                public void visitEnd() {
                    super.visitEnd();
                    if (!hasSetUserData[0]) {
                        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "setUserData", "(Ljava/lang/Object;)V", null, null);
                        mv.visitCode();
                        mv.visitInsn(Opcodes.RETURN);
                        mv.visitMaxs(1, 2);
                        mv.visitEnd();
                        if (logWriter != null) {
                            logWriter.println("  Bytecode Sphere: setUserData(Object)V ajoutée");
                            logWriter.flush();
                        }
                    }
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("  ⚠ addSetUserDataToSphere échoué: " + e.getMessage());
                logWriter.flush();
            }
            return classBytes;
        }
    }

    /**
     * Ajoute la méthode statique isValid(ReadOnlyVector3) au bytecode de Vector3 si absente.
     * HousePart.isValid() appelle Vector3.isValid(ReadOnlyVector3) dans energy3d.jar.
     */
    private static byte[] addIsValidToVector3(byte[] classBytes, java.io.PrintWriter logWriter) {
        try {
            final String desc = "(Lcom/ardor3d/math/type/ReadOnlyVector3;)Z";
            final boolean[] hasIsValid = { false };
            ClassReader cr = new ClassReader(classBytes);
            // COMPUTE_FRAMES required for Java 7+ verification (stack map frames at branch targets)
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
                    if ("isValid".equals(methodName) && desc.equals(descriptor)) {
                        hasIsValid[0] = true;
                    }
                    return super.visitMethod(access, methodName, descriptor, signature, exceptions);
                }
                @Override
                public void visitEnd() {
                    super.visitEnd();
                    if (!hasIsValid[0]) {
                        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "isValid", desc, null, null);
                        mv.visitCode();
                        Label lFalse = new Label();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitJumpInsn(Opcodes.IFNULL, lFalse);
                        String ro = "com/ardor3d/math/type/ReadOnlyVector3";
                        for (String getter : new String[] { "getX", "getY", "getZ" }) {
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, ro, getter, "()D", true);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "isNaN", "(D)Z", false);
                            mv.visitJumpInsn(Opcodes.IFNE, lFalse);
                        }
                        for (String getter : new String[] { "getX", "getY", "getZ" }) {
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, ro, getter, "()D", true);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "isInfinite", "(D)Z", false);
                            mv.visitJumpInsn(Opcodes.IFNE, lFalse);
                        }
                        mv.visitInsn(Opcodes.ICONST_1);
                        mv.visitInsn(Opcodes.IRETURN);
                        mv.visitLabel(lFalse);
                        mv.visitInsn(Opcodes.ICONST_0);
                        mv.visitInsn(Opcodes.IRETURN);
                        mv.visitMaxs(0, 0); // COMPUTE_FRAMES implies maxs computed by ASM
                        mv.visitEnd();
                        if (logWriter != null) {
                            logWriter.println("  Bytecode Vector3: isValid(ReadOnlyVector3)Z ajoutée");
                            logWriter.flush();
                        }
                    }
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("  ⚠ addIsValidToVector3 échoué: " + e.getMessage());
                logWriter.flush();
            }
            return classBytes;
        }
    }

    /**
     * Ajoute la méthode setUserData(Object) au bytecode de Mesh si absente (API Ardor3D ancienne).
     * Foundation.init() appelle Mesh.setUserData(Object).
     */
    private static byte[] addSetUserDataToMesh(byte[] classBytes, java.io.PrintWriter logWriter) {
        try {
            final boolean[] hasSetUserData = { false };
            ClassReader cr = new ClassReader(classBytes);
            ClassWriter cw = new ClassWriter(cr, 0);
            ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
                    if ("setUserData".equals(methodName) && "(Ljava/lang/Object;)V".equals(descriptor)) {
                        hasSetUserData[0] = true;
                    }
                    return super.visitMethod(access, methodName, descriptor, signature, exceptions);
                }
                @Override
                public void visitEnd() {
                    super.visitEnd();
                    if (!hasSetUserData[0]) {
                        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "setUserData", "(Ljava/lang/Object;)V", null, null);
                        mv.visitCode();
                        mv.visitInsn(Opcodes.RETURN);
                        mv.visitMaxs(1, 2);
                        mv.visitEnd();
                        if (logWriter != null) {
                            logWriter.println("  Bytecode Mesh: setUserData(Object)V ajoutée");
                            logWriter.flush();
                        }
                    }
                }
            };
            cr.accept(cv, 0);
            return cw.toByteArray();
        } catch (Exception e) {
            if (logWriter != null) {
                logWriter.println("  ⚠ addSetUserDataToMesh échoué: " + e.getMessage());
                logWriter.flush();
            }
            return classBytes;
        }
    }
    
    /**
     * Crée et retourne un ClassLoader pour Energy3D (sans logs)
     * @return Le ClassLoader ou null si les JARs ne peuvent pas être chargés
     */
    public static ClassLoader getEnergy3DClassLoader() {
        return getEnergy3DClassLoader(null);
    }
    
    /**
     * Charge une classe Energy3D en utilisant le ClassLoader personnalisé
     * @param className Le nom complet de la classe
     * @param logWriter PrintWriter pour écrire les logs (peut être null)
     * @return La classe chargée
     * @throws ClassNotFoundException Si la classe n'est pas trouvée
     */
    public static Class<?> loadEnergy3DClass(String className, java.io.PrintWriter logWriter) throws ClassNotFoundException {
        ClassLoader loader = getEnergy3DClassLoader(logWriter);
        if (loader == null) {
            throw new ClassNotFoundException("Energy3D ClassLoader non disponible pour: " + className);
        }
        if (logWriter != null) {
            logWriter.println("Chargement de la classe: " + className);
            logWriter.flush();
        }
        
        // Pour MaterialState, s'assurer que RenderState est chargé d'abord
        if (className.equals("com.ardor3d.renderer.state.MaterialState")) {
            try {
                loader.loadClass("com.ardor3d.renderer.state.RenderState");
                if (logWriter != null) {
                    logWriter.println("  RenderState chargé avant MaterialState");
                    logWriter.flush();
                }
            } catch (ClassNotFoundException e) {
                if (logWriter != null) {
                    logWriter.println("  AVERTISSEMENT: RenderState non trouvé: " + e.getMessage());
                    logWriter.flush();
                }
                throw new ClassNotFoundException("RenderState doit être disponible pour charger MaterialState", e);
            }
        }
        
        try {
            return loader.loadClass(className);
        } catch (ExceptionInInitializerError e) {
            if (logWriter != null) {
                logWriter.println("  ✗ ExceptionInInitializerError lors de l'initialisation de " + className);
                Throwable cause = e.getCause();
                if (cause != null) {
                    logWriter.println("  Cause: " + cause.getClass().getName() + ": " + cause.getMessage());
                    cause.printStackTrace(new java.io.PrintWriter(logWriter, true));
                }
                logWriter.flush();
            }
            throw new ClassNotFoundException("Initialisation de " + className + " a échoué", e);
        } catch (NoClassDefFoundError e) {
            if (logWriter != null) {
                logWriter.println("  ✗ NoClassDefFoundError: " + e.getMessage());
                logWriter.println("  (La classe a probablement échoué à s'initialiser précédemment)");
                logWriter.flush();
            }
            throw new ClassNotFoundException("Classe " + className + " non disponible: " + e.getMessage(), e);
        }
    }
    
    /**
     * Charge une classe Energy3D en utilisant le ClassLoader personnalisé (sans logs)
     * @param className Le nom complet de la classe
     * @return La classe chargée
     * @throws ClassNotFoundException Si la classe n'est pas trouvée
     */
    public static Class<?> loadEnergy3DClass(String className) throws ClassNotFoundException {
        return loadEnergy3DClass(className, null);
    }
}
