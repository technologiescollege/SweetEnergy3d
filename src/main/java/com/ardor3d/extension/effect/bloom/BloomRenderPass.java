package com.ardor3d.extension.effect.bloom;

import com.ardor3d.renderer.Renderer;
import com.ardor3d.renderer.pass.Pass;

public class BloomRenderPass extends Pass {
    public BloomRenderPass() {
        super();
    }

    public BloomRenderPass(final Object camera, final int passes) {
        super();
    }

    @Override
    protected void doRender(final Renderer renderer) {
    }

    public void setBlurIntensityMultiplier(final float multiplier) {
    }

    public void setNrBlurPasses(final int passes) {
    }

    public void markNeedsRefresh() {
    }

    public void add(final Object obj) {
    }

    public void remove(final Object obj) {
    }

    public boolean contains(final Object obj) {
        return false;
    }
}
