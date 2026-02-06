package com.ardor3d.renderer.state;

import com.ardor3d.renderer.ContextCapabilities;
import com.ardor3d.renderer.state.record.StateRecord;

/**
 * Stub de base pour compilation des stubs LightState/MaterialState dans le plugin.
 * À l'exécution, le ClassLoader Energy3D charge le vrai RenderState depuis ardor3d-core.
 */
public abstract class RenderState {

    public enum StateType {
        Texture, Light, Material
    }

    protected boolean needsRefresh;

    public StateType getType() {
        return StateType.Texture;
    }

    public void setNeedsRefresh(final boolean needsRefresh) {
        this.needsRefresh = needsRefresh;
    }

    public boolean getNeedsRefresh() {
        return needsRefresh;
    }

    public abstract StateRecord createStateRecord(ContextCapabilities caps);
}
