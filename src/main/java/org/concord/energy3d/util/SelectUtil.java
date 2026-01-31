package org.concord.energy3d.util;

import org.concord.energy3d.model.HousePart;
import org.concord.energy3d.model.PickedHousePart;

import com.ardor3d.scenegraph.Mesh;

/**
 * Stub for headless export: no picking (no camera/OpenGL).
 * Foundation.setPreviewPoint calls SelectUtil.pickPart(x, y, null); when this returns null,
 * Foundation uses the point list instead. Other callers get null and can handle it.
 */
public class SelectUtil {

    public static PickedHousePart pickPart(final int x, final int y) {
        return null;
    }

    public static PickedHousePart pickPart(final int x, final int y, final Mesh mesh) {
        return null;
    }

    public static PickedHousePart pickPart(final int x, final int y, final HousePart housePart) {
        return null;
    }

    public static PickedHousePart pickPart(final int x, final int y, final Class<?>[] typesOfHousePart, final boolean allowPickOnLockedPart) {
        return null;
    }

    public static PickedHousePart selectHousePart(final int x, final int y, final boolean edit) {
        return null;
    }

    public static int getPickResultsNumber() {
        return 0;
    }

    public static void setCurrentEditPointMesh(final Mesh mesh) {
        // no-op
    }

    public static Mesh getCurrentEditPointMesh() {
        return null;
    }

    public static void nextPickLayer() {
        // no-op
    }

    public static void setPickLayer(final int layer) {
        // no-op
    }
}
