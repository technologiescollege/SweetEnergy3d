package com.ardor3d.renderer;

/**
 * Stub pour compatibilité : energy3d.jar (Wall.init) référence IndexMode.Quads
 * et MeshData appelle IndexMode.getPrimitiveCount(mode, indexCount).
 */
public enum IndexMode {
    Points,
    Lines,
    LineStrip,
    LineLoop,
    Triangles,
    TriangleStrip,
    TriangleFan,
    Quads;

    /** Appelé par MeshData.updatePrimitiveCounts ; retourne le nombre de primitives pour le mode donné. */
    public static int getPrimitiveCount(IndexMode mode, int indexCount) {
        if (mode == null || indexCount <= 0) return 0;
        switch (mode) {
            case Points: return indexCount;
            case Lines: return indexCount / 2;
            case LineStrip: case LineLoop: return Math.max(0, indexCount - 1);
            case Triangles: return indexCount / 3;
            case TriangleStrip: case TriangleFan: return Math.max(0, indexCount - 2);
            case Quads: return indexCount / 4;
            default: return 0;
        }
    }
}
