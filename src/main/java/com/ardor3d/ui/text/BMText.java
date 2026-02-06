package com.ardor3d.ui.text;

import com.ardor3d.scenegraph.Node;

/**
 * Stub BMText pour export headless : évite NPE quand le contexte OpenGL/AWT
 * n'est pas disponible (Foundation.init). Energy3D appelle (String, String, BMFont, Align, Justify).
 */
public class BMText extends Node {

    public enum Align { Center, South, North, SouthWest, NorthWest, NorthEast, SouthEast, East, West }
    public enum Justify { Center, Top, Bottom }
    public enum AutoScale { Off, FixedScreenSize }
    public enum AutoFade { Off }

    public BMText(String name, String text, BMFont font, Align align, Justify justify) {
        super(name);
    }

    public BMText(String name, String text, BMFont font, Align align) {
        this(name, text, font, align, Justify.Center);
    }

    public void setFontScale(double scale) {
        // no-op headless
    }

    public void setAlign(Align align) {
        // no-op headless
    }

    public void setAutoScale(AutoScale scale) {
        // no-op headless
    }

    public void setAutoFade(AutoFade fade) {
        // no-op headless
    }

    /** Présent pour compatibilité avec Foundation.init(); la superclasse Node (Ardor3D) n'expose pas setVisible(boolean) dans cette version. */
    public void setVisible(boolean visible) {
        // no-op headless
    }
}
