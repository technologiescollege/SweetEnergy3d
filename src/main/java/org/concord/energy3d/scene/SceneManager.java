package org.concord.energy3d.scene;

import com.ardor3d.renderer.Camera;

/**
 * Stub pour SceneManager en mode headless (export .ng3 sans UI Energy3D).
 * Évite l'initialisation du vrai SceneManager qui dépend de MainPanel/OpenGL.
 */
public class SceneManager {

    private static final SceneManager instance = new SceneManager();

    public static SceneManager getInstance() {
        return instance;
    }

    public void setGridsVisible(boolean visible) {
        // no-op en headless
    }

    public boolean isFineGrid() {
        return false;
    }

    public boolean isTopView() {
        return true;
    }

    public Camera getCamera() {
        return null;
    }

    public boolean getSolarHeatMap() {
        return false;
    }

    public boolean isHeatFluxDaily() {
        return false;
    }

    public boolean areHeatFluxVectorsVisible() {
        return false;
    }

    public Object getCameraControl() {
        return null;
    }

    public void refresh() {
        // no-op
    }
}
