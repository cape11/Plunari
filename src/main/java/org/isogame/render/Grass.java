package org.isogame.render;

import java.nio.FloatBuffer;
import java.util.Random;
// No static GL imports needed here if getThickGrassTuftsVertices just prepares data

public class Grass {

    private Grass() {}

    /**
     * Populates a FloatBuffer with vertex data for grass tufts.
     * Each vertex: x, y, r, g, b, a (6 floats)
     * Returns the number of vertices added.
     */
    public static int getThickGrassTuftsVertices(Random random,
                                                 float diamondCenterX, float diamondCenterY,
                                                 float diamondIsoWidth, float diamondIsoHeight,
                                                 int numTufts,
                                                 float baseR, float baseG, float baseB,
                                                 float currentZoom,
                                                 FloatBuffer vertexBuffer) { // Pass buffer to fill
        float tuftBaseLength = diamondIsoHeight * 0.15f;
        float tuftMaxLengthVariation = tuftBaseLength * 0.7f;
        float tuftAngleMaxLean = 0.35f;
        float tuftHorizontalSpreadFactor = 0.25f;
        float bladePixelWidth = 1.5f;
        float actualBladeWidth = bladePixelWidth * currentZoom;
        float halfIsoWidth = diamondIsoWidth / 2.0f;
        float halfIsoHeight = diamondIsoHeight / 2.0f;

        int verticesAdded = 0;
        // vertexBuffer.clear(); // Clear is done by caller (Renderer)

        for (int i = 0; i < numTufts && (vertexBuffer.remaining() >= 4 * 6); i++) { // 4 vertices * 6 floats
            float r = Math.max(0, Math.min(1, baseR + (random.nextFloat() - 0.5f) * 0.1f));
            float g = Math.max(0, Math.min(1, baseG + (random.nextFloat() - 0.5f) * 0.05f));
            float b = Math.max(0, Math.min(1, baseB + (random.nextFloat() - 0.5f) * 0.1f));
            float a = 1.0f; // Alpha for grass

            float dxRel, dyRel;
            do {
                dxRel = (random.nextFloat() - 0.5f) * 2.0f;
                dyRel = (random.nextFloat() - 0.5f) * 2.0f;
            } while (Math.abs(dxRel) + Math.abs(dyRel) > 1.0f);

            float startX = diamondCenterX + dxRel * halfIsoWidth;
            float startY = diamondCenterY + dyRel * halfIsoHeight;
            float length = tuftBaseLength + (random.nextFloat() * tuftMaxLengthVariation);
            float angleLean = (random.nextFloat() - 0.5f) * 2.0f * tuftAngleMaxLean;
            float tipX = startX + (float) Math.sin(angleLean) * length * tuftHorizontalSpreadFactor;
            float tipY = startY - length * (float) Math.cos(angleLean);
            float bladeVecX = tipX - startX;
            float bladeVecY = tipY - startY;
            float mag = (float) Math.sqrt(bladeVecX * bladeVecX + bladeVecY * bladeVecY);
            if (mag == 0) continue;

            float perpX = -bladeVecY / mag * (actualBladeWidth / 2.0f);
            float perpY = bladeVecX / mag * (actualBladeWidth / 2.0f);

            // v0: start - perpendicular
            vertexBuffer.put(startX - perpX).put(startY - perpY).put(r).put(g).put(b).put(a);
            // v1: start + perpendicular
            vertexBuffer.put(startX + perpX).put(startY + perpY).put(r).put(g).put(b).put(a);
            // v2: tip + perpendicular
            vertexBuffer.put(tipX + perpX).put(tipY + perpY).put(r).put(g).put(b).put(a);
            // v3: tip - perpendicular
            vertexBuffer.put(tipX - perpX).put(tipY - perpY).put(r).put(g).put(b).put(a);

            verticesAdded += 4;
        }
        // vertexBuffer.flip(); // Flip is done by caller
        return verticesAdded;
    }
}