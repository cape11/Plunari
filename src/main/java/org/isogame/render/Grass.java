package org.isogame.render; // Or your preferred package

import java.util.Random;
import static org.lwjgl.opengl.GL11.*;

public class Grass {

    private Grass() {} // Private constructor for utility class

    /**
     * Renders grass tufts as thin quads on a tile's top surface.
     *
     * @param random          A Random object, preferably seeded per tile.
     * @param diamondCenterX  Screen X coordinate of the center of the tile's top diamond surface.
     * @param diamondCenterY  Screen Y coordinate of the center of the tile's top diamond surface.
     * @param diamondIsoWidth The full screen width of the tile's top diamond surface.
     * @param diamondIsoHeight The full screen height of the tile's top diamond surface.
     * @param numTufts        Number of grass tufts to draw.
     * @param baseR, baseG, baseB Base color components for grass tufts.
     * @param currentZoom     Current camera zoom level (to scale blade width).
     */
    public static void renderThickGrassTufts(Random random,
                                             float diamondCenterX, float diamondCenterY,
                                             float diamondIsoWidth, float diamondIsoHeight,
                                             int numTufts,
                                             float baseR, float baseG, float baseB,
                                             float currentZoom) { // Pass current camera zoom

        float tuftBaseLength = diamondIsoHeight * 0.15f; // Grass blades about 15% of the tile's iso height
        float tuftMaxLengthVariation = tuftBaseLength * 0.7f;
        float tuftAngleMaxLean = 0.35f; // Radians, max lean from vertical
        float tuftHorizontalSpreadFactor = 0.25f; // How much they can lean sideways relative to length

        float bladePixelWidth = 1.5f; // Base width of the blade in pixels (before zoom)
        float actualBladeWidth = bladePixelWidth * currentZoom; // Scale width with zoom

        float halfIsoWidth = diamondIsoWidth / 2.0f;
        float halfIsoHeight = diamondIsoHeight / 2.0f;

        glBegin(GL_QUADS); // Begin QUADS for all blades
        for (int i = 0; i < numTufts; i++) {
            // Slightly randomize color for each tuft
            float r = Math.max(0, Math.min(1, baseR + (random.nextFloat() - 0.5f) * 0.1f));
            float g = Math.max(0, Math.min(1, baseG + (random.nextFloat() - 0.5f) * 0.05f));
            float b = Math.max(0, Math.min(1, baseB + (random.nextFloat() - 0.5f) * 0.1f));
            glColor3f(r, g, b);

            // Generate a random starting point (base of the tuft) within the diamond
            float dxRel, dyRel;
            do {
                dxRel = (random.nextFloat() - 0.5f) * 2.0f; // Random in [-1, 1]
                dyRel = (random.nextFloat() - 0.5f) * 2.0f; // Random in [-1, 1]
            } while (Math.abs(dxRel) + Math.abs(dyRel) > 1.0f); // Ensure point is inside unit diamond

            float startX = diamondCenterX + dxRel * halfIsoWidth;
            float startY = diamondCenterY + dyRel * halfIsoHeight;

            // Calculate tuft properties (length and overall lean)
            float length = tuftBaseLength + (random.nextFloat() * tuftMaxLengthVariation);
            float angleLean = (random.nextFloat() - 0.5f) * 2.0f * tuftAngleMaxLean;

            // Tip of the grass blade
            float tipX = startX + (float) Math.sin(angleLean) * length * tuftHorizontalSpreadFactor;
            float tipY = startY - length * (float) Math.cos(angleLean); // "Upwards" on screen

            // Vector representing the grass blade's direction and length
            float bladeVecX = tipX - startX;
            float bladeVecY = tipY - startY;

            // Perpendicular vector for the blade's width
            // Normalized perpendicular: (-bladeVecY, bladeVecX) / magnitude(bladeVec)
            float mag = (float) Math.sqrt(bladeVecX * bladeVecX + bladeVecY * bladeVecY);
            if (mag == 0) continue; // Avoid division by zero for zero-length blade

            float perpX = -bladeVecY / mag * (actualBladeWidth / 2.0f);
            float perpY = bladeVecX / mag * (actualBladeWidth / 2.0f);

            // Define the 4 vertices of the quad for this blade
            // v0: start - perpendicular
            // v1: start + perpendicular
            // v2: tip   + perpendicular
            // v3: tip   - perpendicular
            glVertex2f(startX - perpX, startY - perpY);
            glVertex2f(startX + perpX, startY + perpY);
            glVertex2f(tipX + perpX, tipY + perpY);
            glVertex2f(tipX - perpX, tipY - perpY);
        }
        glEnd(); // End QUADS
    }
}