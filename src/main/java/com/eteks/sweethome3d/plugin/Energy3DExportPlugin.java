package com.eteks.sweethome3d.plugin;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.eteks.sweethome3d.model.Home;

/**
 * Plugin pour exporter les plans Sweet Home 3D vers Energy3D
 */
public class Energy3DExportPlugin extends Plugin {
    
    @Override
    public PluginAction[] getActions() {
        return new PluginAction[] {
            new ExportAction()
        };
    }
    
    /**
     * Action pour exporter le plan vers Energy3D
     */
    private class ExportAction extends PluginAction {
        
        public ExportAction() {
            super("com.eteks.sweethome3d.plugin.Energy3DExportPlugin",
                  "EXPORT_ACTION",
                  getPluginClassLoader(),
                  true);
        }
        
        @Override
        public void execute() {
            try {
                // Obtenir le Home actuel
                Home home = getHome();
                if (home == null) {
                    JOptionPane.showMessageDialog(
                        null,
                        "Aucun plan n'est ouvert dans Sweet Home 3D.\n\n" +
                        "Veuillez ouvrir ou créer un plan avant d'exporter.",
                        "Aucun plan",
                        JOptionPane.WARNING_MESSAGE
                    );
                    return;
                }
                
                // Vérifier que le niveau "terrain" existe et contient au moins des murs ou une pièce
                String validationError = PlanExporter.getExportValidationError(home);
                if (validationError != null) {
                    JOptionPane.showMessageDialog(
                        null,
                        validationError,
                        "Export impossible",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                    return;
                }
                
                // Demander à l'utilisateur où sauvegarder le fichier
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Exporter vers Energy3D");
                fileChooser.setFileFilter(new FileNameExtensionFilter(
                    "Fichiers Energy3D (*.ng3)", "ng3"));
                fileChooser.setSelectedFile(new File("plan_energy3d.ng3"));
                
                int result = fileChooser.showSaveDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File outputFile = fileChooser.getSelectedFile();
                    
                    // Ajouter l'extension .ng3 si nécessaire
                    if (!outputFile.getName().toLowerCase().endsWith(".ng3")) {
                        outputFile = new File(outputFile.getParent(), outputFile.getName() + ".ng3");
                    }
                    
                    // Demander confirmation si le fichier existe déjà
                    if (outputFile.exists()) {
                        int overwrite = JOptionPane.showConfirmDialog(
                            null,
                            "Le fichier existe déjà :\n" + outputFile.getName() + "\n\nVoulez-vous l'écraser ?",
                            "Fichier existant",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE
                        );
                        if (overwrite != JOptionPane.YES_OPTION) {
                            return;
                        }
                    }
                    
                    // Créer un fichier de log immédiatement pour le diagnostic
                    File logFile = new File(outputFile.getParentFile(), outputFile.getName() + ".log");
                    java.io.PrintWriter pluginLogWriter = null;
                    try {
                        pluginLogWriter = new java.io.PrintWriter(new java.io.FileWriter(logFile));
                        pluginLogWriter.println("=== PLUGIN EXECUTE ===");
                        pluginLogWriter.println("Fichier de sortie: " + outputFile.getAbsolutePath());
                        pluginLogWriter.println("Nombre de murs dans le plan: " + (home.getWalls() != null ? home.getWalls().size() : 0));
                        pluginLogWriter.println("Appel de PlanExporter.exportToEnergy3D()...");
                        pluginLogWriter.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    // Exporter le plan
                    boolean success = false;
                    try {
                        if (pluginLogWriter != null) {
                            pluginLogWriter.println("Appel de PlanExporter.exportToEnergy3D()...");
                            pluginLogWriter.flush();
                        }
                        
                        success = PlanExporter.exportToEnergy3D(home, outputFile);
                        
                        if (pluginLogWriter != null) {
                            pluginLogWriter.println("PlanExporter.exportToEnergy3D() retourné: " + success);
                            pluginLogWriter.println("Fichier existe: " + outputFile.exists());
                            if (outputFile.exists()) {
                                pluginLogWriter.println("Taille du fichier: " + outputFile.length() + " bytes");
                            }
                            pluginLogWriter.flush();
                        }
                    } catch (NoClassDefFoundError e) {
                        if (pluginLogWriter != null) {
                            pluginLogWriter.println("✗ ERREUR CRITIQUE: NoClassDefFoundError lors de l'appel!");
                            pluginLogWriter.println("Message: " + e.getMessage());
                            e.printStackTrace(pluginLogWriter);
                            pluginLogWriter.flush();
                        }
                        e.printStackTrace();
                        success = false;
                        
                        JOptionPane.showMessageDialog(
                            null,
                            "Erreur: Classes Energy3D non disponibles.\n\n" +
                            "Vérifiez que les dépendances Energy3D sont correctement configurées.\n\n" +
                            "Détails: " + e.getMessage(),
                            "Erreur de chargement",
                            JOptionPane.ERROR_MESSAGE
                        );
                    } catch (Throwable t) {
                        if (pluginLogWriter != null) {
                            pluginLogWriter.println("✗ EXCEPTION/ERROR lors de l'appel à PlanExporter.exportToEnergy3D():");
                            pluginLogWriter.println("Type: " + t.getClass().getName());
                            pluginLogWriter.println("Message: " + t.getMessage());
                            t.printStackTrace(pluginLogWriter);
                            pluginLogWriter.flush();
                        }
                        t.printStackTrace();
                        success = false;
                    } finally {
                        if (pluginLogWriter != null) {
                            pluginLogWriter.println("=== FIN APPEL PLUGIN ===");
                            pluginLogWriter.flush();
                            pluginLogWriter.close();
                        }
                    }
                    
                    if (success) {
                        JOptionPane.showMessageDialog(
                            null,
                            String.format(
                                "Plan exporté avec succès vers Energy3D.\n\n" +
                                "Fichier: %s\n" +
                                "Taille: %d bytes\n\n" +
                                "Le fichier est au format binaire .ng3 compatible Energy3D.",
                                outputFile.getAbsolutePath(),
                                outputFile.length()
                            ),
                            "Export réussi",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                    } else {
                        // Réafficher le message de validation si l'export a échoué pour cause de terrain
                        String validationMsg = PlanExporter.getExportValidationError(home);
                        if (validationMsg != null) {
                            JOptionPane.showMessageDialog(
                                null,
                                validationMsg,
                                "Export impossible",
                                JOptionPane.INFORMATION_MESSAGE
                            );
                            return;
                        }
                        String errorMessage = "Erreur lors de l'export du plan.\n\n";
                        
                        // Vérifier si le fichier existe
                        if (outputFile.exists()) {
                            if (outputFile.length() == 0) {
                                errorMessage += "Le fichier a été créé mais est vide.\n";
                            } else {
                                errorMessage += "Le fichier existe mais pourrait être corrompu.\n";
                            }
                        } else {
                            errorMessage += "Le fichier n'a pas été créé.\n";
                        }
                        
                        // Vérifier les permissions
                        if (outputFile.getParentFile() != null) {
                            if (!outputFile.getParentFile().canWrite()) {
                                errorMessage += "\nPas de permission d'écriture dans le répertoire: " + outputFile.getParentFile().getAbsolutePath();
                            }
                        }
                        
                        errorMessage += "\n\nVeuillez consulter le fichier log pour plus de détails:\n" + 
                                       new File(outputFile.getParentFile(), outputFile.getName() + ".log").getAbsolutePath();
                        
                        JOptionPane.showMessageDialog(
                            null,
                            errorMessage,
                            "Erreur d'export",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
                
            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                    null,
                    "Erreur lors de l'export:\n" + e.getMessage(),
                    "Erreur",
                    JOptionPane.ERROR_MESSAGE
                );
                e.printStackTrace();
            }
        }
    }
}
