package com.ardor3d.util.geom;

public final class BufferUtils {
    private BufferUtils() {}
    
    public static java.nio.FloatBuffer createVector3Buffer(final int size) {
        return java.nio.FloatBuffer.allocate(size * 3);
    }
    
    public static java.nio.FloatBuffer createVector2Buffer(final int size) {
        return java.nio.FloatBuffer.allocate(size * 2);
    }
}
