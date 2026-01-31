package com.ardor3d.renderer.state;

import com.ardor3d.math.ColorRGBA;
import com.ardor3d.renderer.ContextCapabilities;
import com.ardor3d.renderer.state.record.StateRecord;

public class MaterialState extends RenderState {
    public enum ColorMaterial {
        None, Diffuse, Ambient, Emissive, Specular, AmbientAndDiffuse;
    }

    private ColorRGBA diffuse = new ColorRGBA(0.8f, 0.8f, 0.8f, 1f);
    private ColorRGBA emissive = new ColorRGBA(0f, 0f, 0f, 1f);
    private ColorMaterial colorMaterial = ColorMaterial.None;

    public MaterialState() {
        super();
    }

    public void setColorMaterial(final ColorMaterial colorMaterial) {
        this.colorMaterial = colorMaterial;
        setNeedsRefresh(true);
    }

    public ColorMaterial getColorMaterial() {
        return colorMaterial;
    }

    @Override
    public StateType getType() {
        // Certaines versions de Ardor3D n'ont plus StateType.Material
        return StateType.Texture;
    }

    public void setDiffuse(final ColorRGBA color) {
        this.diffuse = color;
    }

    public ColorRGBA getDiffuse() {
        return diffuse;
    }

    public void setEmissive(final ColorRGBA color) {
        this.emissive = color;
    }

    public ColorRGBA getEmissive() {
        return emissive;
    }

    @Override
    public StateRecord createStateRecord(final ContextCapabilities caps) {
        return new MaterialStateRecord();
    }

    private static class MaterialStateRecord extends StateRecord {
    }
}
