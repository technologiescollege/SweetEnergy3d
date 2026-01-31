package com.ardor3d.scenegraph.hint;

public class SceneHints {
    private boolean castsShadows;
    private boolean allPickingHints;
    private CullHint cullHint = CullHint.Inherit;
    private com.ardor3d.renderer.queue.RenderBucketType renderBucketType;
    private LightCombineMode lightCombineMode;
    private TextureCombineMode textureCombineMode;

    public SceneHints() {}
    public SceneHints(final Hintable owner) {}

    public void setCastsShadows(final boolean casts) {
        this.castsShadows = casts;
    }

    public boolean isCastsShadows() {
        return castsShadows;
    }

    public void setAllPickingHints(final boolean value) {
        this.allPickingHints = value;
    }

    public boolean isAllPickingHints() {
        return allPickingHints;
    }

    public CullHint getCullHint() {
        return cullHint;
    }

    public void setCullHint(final CullHint hint) {
        this.cullHint = hint;
    }
    
    public void setRenderBucketType(final com.ardor3d.renderer.queue.RenderBucketType type) {
        this.renderBucketType = type;
    }
    
    public com.ardor3d.renderer.queue.RenderBucketType getRenderBucketType() {
        return renderBucketType;
    }
    
    public void setLightCombineMode(final LightCombineMode mode) {
        this.lightCombineMode = mode;
    }
    
    public LightCombineMode getLightCombineMode() {
        return lightCombineMode;
    }
    
    public void setPickingHint(final PickingHint hint, final boolean value) {
        if (hint == PickingHint.Pickable) {
            this.allPickingHints = value;
        }
    }

    public void setTextureCombineMode(final TextureCombineMode mode) {
        this.textureCombineMode = mode;
    }

    public TextureCombineMode getTextureCombineMode() {
        return textureCombineMode;
    }
}
