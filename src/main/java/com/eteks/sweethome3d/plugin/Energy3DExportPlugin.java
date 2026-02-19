package com.eteks.sweethome3d.plugin;

import java.io.File;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.eteks.sweethome3d.model.Home;

/**
 * Plugin pour exporter les plans Sweet Home 3D vers Energy3D
 */
public class Energy3DExportPlugin extends Plugin {

    private static final String BUNDLE_BASE = "com.eteks.sweethome3d.plugin.Energy3DExportPlugin";

    /** Retourne le ResourceBundle localisé du plugin (utilise le ClassLoader du plugin). */
    private ResourceBundle getBundle() {
        ClassLoader loader = getPluginClassLoader();
        Locale locale = Locale.getDefault();
        return ResourceBundle.getBundle(BUNDLE_BASE, locale, loader);
    }

    private String getString(String key) {
        try {
            return getBundle().getString(key);
        } catch (Exception e) {
            return key;
        }
    }

    private String getString(String key, Object... args) {
        try {
            String pattern = getBundle().getString(key);
            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            return key;
        }
    }
    
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
                        getString("msg.no_plan"),
                        getString("msg.no_plan_title"),
                        JOptionPane.WARNING_MESSAGE
                    );
                    return;
                }
                
                // Vérifier que le niveau "terrain" existe et contient au moins des murs ou une pièce
                String validationKey = PlanExporter.getExportValidationError(home);
                if (validationKey != null) {
                    JOptionPane.showMessageDialog(
                        null,
                        getString(validationKey),
                        getString("msg.export_impossible_title"),
                        JOptionPane.INFORMATION_MESSAGE
                    );
                    return;
                }
                
                // Demander à l'utilisateur où sauvegarder le fichier
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle(getString("dialog.save_title"));
                fileChooser.setFileFilter(new FileNameExtensionFilter(
                    getString("file_filter.energy3d"), getString("file_filter.extension")));
                fileChooser.setSelectedFile(new File(getString("file.default_name")));
                
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
                            getString("msg.file_exists", outputFile.getName()),
                            getString("msg.file_exists_title"),
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
                        pluginLogWriter.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    // Exporter le plan
                    boolean success = false;
                    try {
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
                            getString("msg.energy3d_not_available", e.getMessage() != null ? e.getMessage() : ""),
                            getString("msg.energy3d_not_available_title"),
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
                            getString("msg.export_success", outputFile.getAbsolutePath(), outputFile.length()),
                            getString("msg.export_success_title"),
                            JOptionPane.INFORMATION_MESSAGE
                        );
                    } else {
                        // Réafficher le message de validation si l'export a échoué pour cause de terrain
                        validationKey = PlanExporter.getExportValidationError(home);
                        if (validationKey != null) {
                            JOptionPane.showMessageDialog(
                                null,
                                getString(validationKey),
                                getString("msg.export_impossible_title"),
                                JOptionPane.INFORMATION_MESSAGE
                            );
                            return;
                        }
                        String errorMessage = getString("msg.export_error_intro");
                        
                        // Vérifier si le fichier existe
                        if (outputFile.exists()) {
                            if (outputFile.length() == 0) {
                                errorMessage += getString("msg.export_error_file_empty");
                            } else {
                                errorMessage += getString("msg.export_error_file_corrupt");
                            }
                        } else {
                            errorMessage += getString("msg.export_error_file_not_created");
                        }
                        
                        // Vérifier les permissions
                        if (outputFile.getParentFile() != null) {
                            if (!outputFile.getParentFile().canWrite()) {
                                errorMessage += getString("msg.export_error_no_write_permission", outputFile.getParentFile().getAbsolutePath());
                            }
                        }
                        
                        errorMessage += getString("msg.export_error_see_log",
                            new File(outputFile.getParentFile(), outputFile.getName() + ".log").getAbsolutePath());
                        
                        JOptionPane.showMessageDialog(
                            null,
                            errorMessage,
                            getString("msg.export_error_title"),
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
                
            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                    null,
                    getString("msg.generic_error", e.getMessage() != null ? e.getMessage() : ""),
                    getString("msg.generic_error_title"),
                    JOptionPane.ERROR_MESSAGE
                );
                e.printStackTrace();
            }
        }
    }

}
