package com.ardor3d.renderer.state;

import com.ardor3d.renderer.ContextCapabilities;
import com.ardor3d.renderer.state.record.StateRecord;

public class LightState extends RenderState {
    private boolean enabled = true;

    public LightState() {
        super();
    }

    @Override
    public StateType getType() {
        // Certaines versions d'Ardor3D n'ont plus StateType.Light
        return StateType.Texture;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public StateRecord createStateRecord(final ContextCapabilities caps) {
        return new LightStateRecord();
    }

    private static class LightStateRecord extends StateRecord {
    }
}
