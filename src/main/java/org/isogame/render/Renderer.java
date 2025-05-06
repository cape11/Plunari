package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.entity.PlayerModel;
import org.isogame.input.InputHandler;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.lwjgl.opengl.*;

import static org.isogame.constants.Constants.*;
import static org.lwjgl.opengl.GL11.*;

public class Renderer {

    private final CameraManager camera;
    private final Map map;
    private final PlayerModel player;
    private final InputHandler inputHandler; // Needed for selected tile highlight

    // Simple frame counter for animations
    private int frameCount = 0;

    // Add fields for textures or font renderers here when implemented
    // private FontRenderer fontRenderer;

    public Renderer(CameraManager camera, Map map, PlayerModel player, InputHandler inputHandler) {
        this.camera = camera;
        this.map = map;
        this.player = player;
        this.inputHandler = inputHandler;

        // Initialize font renderer here if using one
        // fontRenderer = new FontRenderer("path/to/font.ttf");
    }

    /** Called when the window is resized to update projection */
    public void onResize(int width, int height) {
        glViewport(0, 0, width, height);
        // Set up orthographic projection for 2D rendering
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        // Origin at top-left corner
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    public void render() {
        frameCount++; // Increment animation frame counter

        // Set ModelView matrix to identity for each frame
        // (Camera translation is handled by mapToScreenCoords)
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Render Tiles (sorted back-to-front for correct overlap)
        renderMap();

        // Render Player
        renderPlayer();

        // Render UI (on top of everything else)
        renderUI();
    }

    private void renderMap() {
        int mapW = map.getWidth();
        int mapH = map.getHeight();

        // Simple back-to-front sorting based on row+col sum
        for (int sum = 0; sum <= mapW + mapH - 2; sum++) {
            for (int row = 0; row <= sum; row++) {
                int col = sum - row;

                if (map.isValid(row, col)) {
                    Tile tile = map.getTile(row, col);
                    if (tile != null) {
                        boolean isSelected = (row == inputHandler.getSelectedRow() && col == inputHandler.getSelectedCol());
                        renderTile(row, col, tile, isSelected);
                    }
                }
            }
        }
    }


    private void renderTile(int row, int col, Tile tile, boolean isSelected) {
        int elevation = tile.getElevation();
        Tile.TileType type = tile.getType();

        // Get screen coordinates for the base of the tile (at elevation 0)
        int[] baseCoords = camera.mapToScreenCoords(col, row, 0);
        int tileBaseX = baseCoords[0];
        int tileBaseY = baseCoords[1];

        // Basic Culling (check if tile center is roughly within screen bounds + margin)
        int effTileWidth = camera.getEffectiveTileWidth();
        int effTileHeight = camera.getEffectiveTileHeight();
        int margin = Math.max(effTileWidth, effTileHeight) * 2; // Generous margin

        if (tileBaseX < -margin || tileBaseX > camera.getScreenWidth() + margin ||
                tileBaseY < -margin || tileBaseY > camera.getScreenHeight() + margin + (ALTURA_MAXIMA * camera.getEffectiveTileThickness())) {
            return; // Don't render if tile is too far off-screen
        }

        // --- Calculate Colors based on Tile Type ---
        float[] topColor, side1Color, side2Color, baseTopColor, baseSide1Color, baseSide2Color;
        boolean isWater = (type == Tile.TileType.WATER);

        // Apply selection highlight
        if (isSelected) {
            topColor = new float[]{1.0f, 0.8f, 0.0f, 0.8f}; // Yellowish highlight
            side1Color = new float[]{0.9f, 0.7f, 0.0f, 0.8f};
            side2Color = new float[]{0.8f, 0.6f, 0.0f, 0.8f};
            baseTopColor = new float[]{0.5f, 0.4f, 0.0f, 0.8f};
            baseSide1Color = new float[]{0.4f, 0.3f, 0.0f, 0.8f};
            baseSide2Color = new float[]{0.3f, 0.2f, 0.0f, 0.8f};
        } else {
            switch (type) {
                case WATER:
                    // Animated water colors
                    double timeSpeed1 = 0.05, timeSpeed2 = 0.03, spatialScale1 = 0.4, spatialScale2 = 0.6;
                    double brightnessBase = 0.3, brightnessAmplitude = 0.15;
                    double greenShiftBase = 0.3, greenShiftAmplitude = 0.1;
                    double waveFactor1 = (frameCount * timeSpeed1 + (row + col) * spatialScale1);
                    double waveFactor2 = (frameCount * timeSpeed2 + (row * 0.7 - col * 0.6) * spatialScale2);
                    double waveValue = (Math.sin(waveFactor1) + Math.cos(waveFactor2)) / 2.0; // Range [-1, 1]

                    float blueValue = (float) Math.max(0.1, Math.min(1.0, brightnessBase + waveValue * brightnessAmplitude));
                    float greenValue = (float) Math.max(0.0, Math.min(1.0, blueValue * (greenShiftBase + Math.sin(waveFactor1 + col * 0.5) * greenShiftAmplitude)));

                    topColor = new float[]{0.0f, greenValue, blueValue, 0.85f}; // Slightly transparent water
                    side1Color = topColor; // Water sides are same color
                    side2Color = topColor;
                    // Darker base below water level
                    baseTopColor = new float[]{0.05f, 0.1f, 0.2f, 1.0f};
                    baseSide1Color = new float[]{0.04f, 0.08f, 0.18f, 1.0f};
                    baseSide2Color = new float[]{0.03f, 0.06f, 0.16f, 1.0f};
                    break;
                case SAND:
                    topColor = new float[]{0.82f, 0.7f, 0.55f, 1.0f};
                    side1Color = new float[]{0.75f, 0.65f, 0.49f, 1.0f};
                    side2Color = new float[]{0.67f, 0.59f, 0.43f, 1.0f};
                    baseTopColor = new float[]{0.59f, 0.51f, 0.35f, 1.0f}; // Dirt/base under sand
                    baseSide1Color = new float[]{0.51f, 0.43f, 0.27f, 1.0f};
                    baseSide2Color = new float[]{0.43f, 0.35f, 0.19f, 1.0f};
                    break;
                case GRASS:
                    topColor = new float[]{0.13f, 0.55f, 0.13f, 1.0f};
                    side1Color = new float[]{0.12f, 0.47f, 0.12f, 1.0f}; // Slightly darker side
                    side2Color = new float[]{0.10f, 0.39f, 0.10f, 1.0f}; // Darkest side
                    baseTopColor = new float[]{0.31f, 0.24f, 0.16f, 1.0f}; // Dirt/base under grass
                    baseSide1Color = new float[]{0.27f, 0.20f, 0.14f, 1.0f};
                    baseSide2Color = new float[]{0.24f, 0.16f, 0.12f, 1.0f};
                    break;
                case ROCK:
                    topColor = new float[]{0.5f, 0.5f, 0.5f, 1.0f}; // Grey
                    side1Color = new float[]{0.45f, 0.45f, 0.45f, 1.0f};
                    side2Color = new float[]{0.4f, 0.4f, 0.4f, 1.0f};
                    baseTopColor = new float[]{0.35f, 0.35f, 0.35f, 1.0f}; // Darker base rock
                    baseSide1Color = new float[]{0.3f, 0.3f, 0.3f, 1.0f};
                    baseSide2Color = new float[]{0.25f, 0.25f, 0.25f, 1.0f};
                    break;
                case SNOW:
                    topColor = new float[]{0.95f, 0.95f, 1.0f, 1.0f}; // White/slight blue
                    side1Color = new float[]{0.9f, 0.9f, 0.95f, 1.0f};
                    side2Color = new float[]{0.85f, 0.85f, 0.9f, 1.0f};
                    baseTopColor = new float[]{0.5f, 0.5f, 0.55f, 1.0f}; // Rock under snow
                    baseSide1Color = new float[]{0.45f, 0.45f, 0.5f, 1.0f};
                    baseSide2Color = new float[]{0.4f, 0.4f, 0.45f, 1.0f};
                    break;
                default: // Should not happen
                    topColor = new float[]{1.0f, 0.0f, 1.0f, 1.0f}; // Magenta for error
                    side1Color = topColor; side2Color = topColor;
                    baseTopColor = topColor; baseSide1Color = topColor; baseSide2Color = topColor;
                    break;
            }
        }


        // --- Calculate Tile Geometry ---
        int effWidth = camera.getEffectiveTileWidth();
        int effHeight = camera.getEffectiveTileHeight();
        int effThickness = camera.getEffectiveTileThickness();
        int effBaseThickness = camera.getEffectiveBaseThickness();

        // Base points (footprint on the grid plane at y = tileBaseY)
        int[] base_Top    = {tileBaseX + effWidth / 2, tileBaseY};
        int[] base_Left   = {tileBaseX,              tileBaseY + effHeight / 2};
        int[] base_Right  = {tileBaseX + effWidth,   tileBaseY + effHeight / 2};
        int[] base_Bottom = {tileBaseX + effWidth / 2, tileBaseY + effHeight};

        // --- Draw Base ---
        // Calculate bottom points of the base
        int[] base_Bottom_Left   = {base_Left[0],   base_Left[1]   + effBaseThickness};
        int[] base_Bottom_Right  = {base_Right[0],  base_Right[1]  + effBaseThickness};
        int[] base_Bottom_Bottom = {base_Bottom[0], base_Bottom[1] + effBaseThickness};

        // Left Base Face
        glColor4f(baseSide1Color[0], baseSide1Color[1], baseSide1Color[2], baseSide1Color[3]);
        glBegin(GL_QUADS);
        glVertex2f(base_Left[0], base_Left[1]);             // Top-Left
        glVertex2f(base_Bottom_Left[0], base_Bottom_Left[1]); // Bottom-Left
        glVertex2f(base_Bottom_Bottom[0], base_Bottom_Bottom[1]); // Bottom-Center
        glVertex2f(base_Bottom[0], base_Bottom[1]);          // Top-Center
        glEnd();

        // Right Base Face
        glColor4f(baseSide2Color[0], baseSide2Color[1], baseSide2Color[2], baseSide2Color[3]);
        glBegin(GL_QUADS);
        glVertex2f(base_Right[0], base_Right[1]);            // Top-Right
        glVertex2f(base_Bottom_Right[0], base_Bottom_Right[1]);// Bottom-Right
        glVertex2f(base_Bottom_Bottom[0], base_Bottom_Bottom[1]);// Bottom-Center
        glVertex2f(base_Bottom[0], base_Bottom[1]);         // Top-Center
        glEnd();

        // Base Top Surface (Always drawn, represents the grid plane)
        glColor4f(baseTopColor[0], baseTopColor[1], baseTopColor[2], baseTopColor[3]);
        glBegin(GL_QUADS);
        glVertex2f(base_Left[0], base_Left[1]);
        glVertex2f(base_Top[0], base_Top[1]);
        glVertex2f(base_Right[0], base_Right[1]);
        glVertex2f(base_Bottom[0], base_Bottom[1]);
        glEnd();

        // --- Draw Elevated Part (if not water) ---
        if (!isWater && elevation > 0) {
            // Calculate the ground level Y where the elevation starts
            // This depends on how you define elevation 0. If elevation 0 is the water level plane:
            // int groundLevelElevation = 0; // Or maybe NIVEL_MAR? Depends on definition. Let's assume 0 for now.
            int groundElevationOffset = 0; // elevation * effThickness where elevation is 0

            // Calculate the final top surface Y coordinate based on actual elevation
            int topElevationOffset = elevation * effThickness;
            int topSurfaceY = tileBaseY - topElevationOffset; // Subtract because Y grows downwards

            // Calculate points for the top surface
            int[] final_Top    = {tileBaseX + effWidth / 2, topSurfaceY};
            int[] final_Left   = {tileBaseX,              topSurfaceY + effHeight / 2};
            int[] final_Right  = {tileBaseX + effWidth,   topSurfaceY + effHeight / 2};
            int[] final_Bottom = {tileBaseX + effWidth / 2, topSurfaceY + effHeight};

            // Calculate points for the base of the elevated part (where it meets the ground)
            int groundSurfaceY = tileBaseY - groundElevationOffset;
            int[] ground_Left   = {tileBaseX,              groundSurfaceY + effHeight / 2};
            //int[] ground_Top    = {tileBaseX + effWidth / 2, groundSurfaceY}; // Not needed for sides
            int[] ground_Right  = {tileBaseX + effWidth,   groundSurfaceY + effHeight / 2};
            int[] ground_Bottom = {tileBaseX + effWidth / 2, groundSurfaceY + effHeight};


            // Left Wall
            glColor4f(side1Color[0], side1Color[1], side1Color[2], side1Color[3]);
            glBegin(GL_QUADS);
            glVertex2f(ground_Left[0], ground_Left[1]);     // Ground Left
            glVertex2f(ground_Bottom[0], ground_Bottom[1]); // Ground Bottom
            glVertex2f(final_Bottom[0], final_Bottom[1]);   // Top Bottom
            glVertex2f(final_Left[0], final_Left[1]);       // Top Left
            glEnd();

            // Right Wall
            glColor4f(side2Color[0], side2Color[1], side2Color[2], side2Color[3]);
            glBegin(GL_QUADS);
            glVertex2f(ground_Right[0], ground_Right[1]);    // Ground Right
            glVertex2f(ground_Bottom[0], ground_Bottom[1]); // Ground Bottom
            glVertex2f(final_Bottom[0], final_Bottom[1]);   // Top Bottom
            glVertex2f(final_Right[0], final_Right[1]);     // Top Right
            glEnd();

            // Top Surface
            glColor4f(topColor[0], topColor[1], topColor[2], topColor[3]);
            glBegin(GL_QUADS);
            glVertex2f(final_Left[0], final_Left[1]);
            glVertex2f(final_Top[0], final_Top[1]);
            glVertex2f(final_Right[0], final_Right[1]);
            glVertex2f(final_Bottom[0], final_Bottom[1]);
            glEnd();

            // Optional: Draw outline for elevated part
            // glColor4f(0.1f, 0.1f, 0.1f, 0.5f); // Dark outline
            // glBegin(GL_LINE_LOOP);
            // glVertex2f(final_Left[0], final_Left[1]);
            // glVertex2f(final_Top[0], final_Top[1]);
            // glVertex2f(final_Right[0], final_Right[1]);
            // glVertex2f(final_Bottom[0], final_Bottom[1]);
            // glEnd();
        }
    }


    private void renderPlayer() {
        int playerRow = player.getTileRow();
        int playerCol = player.getTileCol();
        Tile currentTile = map.getTile(playerRow, playerCol);
        int playerElevation = (currentTile != null) ? currentTile.getElevation() : 0;

        // Get screen coordinates for the player's base position ON the tile surface
        int[] playerBaseCoords = camera.mapToScreenCoords(player.getMapCol(), player.getMapRow(), playerElevation);
        int screenX = playerBaseCoords[0];
        int screenY = playerBaseCoords[1]; // This Y is at the top surface of the tile

        // Apply visual levitation offset if active
        if (player.isLevitating()) {
            screenY -= (int) (Math.sin(player.getLevitateTimer()) * 8 * camera.getZoom()); // Levitate vertically
        }

        // --- Draw Player Model (Refined Immediate Mode) ---
        // Size relative to effective tile size
        float effTileHeight = camera.getEffectiveTileHeight(); // Use height for vertical scaling
        float modelScale = effTileHeight * 0.8f; // Make model slightly smaller than tile height

        float bodyWidth = modelScale * 0.4f;
        float bodyHeight = modelScale * 0.5f;
        float headRadius = modelScale * 0.25f;
        float limbLength = modelScale * 0.4f;
        float limbWidth = modelScale * 0.1f;

        // Bobbing animation
        float bobOffset = (float) Math.sin(frameCount * 0.1) * (modelScale * 0.05f);

        // Center the drawing around screenX, screenY (adjusting Y for model height)
        float modelBaseY = screenY - (bodyHeight / 2.0f) + bobOffset; // Place center of body near tile top

        glPushMatrix();
        glTranslatef(screenX, modelBaseY, 0); // Move to player position

        // --- Body ---
        glColor3f(0.2f, 0.4f, 0.8f); // Blueish body
        glBegin(GL_QUADS);
        glVertex2f(-bodyWidth / 2, -bodyHeight / 2);
        glVertex2f(bodyWidth / 2, -bodyHeight / 2);
        glVertex2f(bodyWidth / 2, bodyHeight / 2);
        glVertex2f(-bodyWidth / 2, bodyHeight / 2);
        glEnd();
        // Body Outline
        glColor3f(0.1f, 0.2f, 0.4f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(-bodyWidth / 2, -bodyHeight / 2);
        glVertex2f(bodyWidth / 2, -bodyHeight / 2);
        glVertex2f(bodyWidth / 2, bodyHeight / 2);
        glVertex2f(-bodyWidth / 2, bodyHeight / 2);
        glEnd();


        // --- Head ---
        glPushMatrix();
        glTranslatef(0, -bodyHeight / 2 - headRadius, 0); // Position head above body center
        // Skin tone head
        glColor3f(0.9f, 0.7f, 0.5f);
        drawCircle(headRadius, 20); // Draw a smoother circle

        // Simple Face Features
        glColor3f(0.1f, 0.1f, 0.1f); // Black eyes/mouth
        // Eyes
        drawCircle(headRadius * 0.15f, 8, -headRadius * 0.3f, -headRadius * 0.1f); // Left eye
        drawCircle(headRadius * 0.15f, 8, headRadius * 0.3f, -headRadius * 0.1f); // Right eye
        // Smile (simple line)
        glBegin(GL_LINE_STRIP);
        glVertex2f(-headRadius * 0.3f, headRadius * 0.3f);
        glVertex2f(0, headRadius * 0.4f);
        glVertex2f(headRadius * 0.3f, headRadius * 0.3f);
        glEnd();

        glPopMatrix(); // Pop head matrix

        // --- Limbs (Very Basic) ---
        // Arms
        glColor3f(0.8f, 0.6f, 0.4f); // Skin tone arms
        drawLimb(-bodyWidth / 2, -bodyHeight * 0.2f, limbWidth, limbLength, -45); // Left arm
        drawLimb(bodyWidth / 2, -bodyHeight * 0.2f, limbWidth, limbLength, 45);  // Right arm

        // Legs
        glColor3f(0.1f, 0.2f, 0.6f); // Darker blue legs
        drawLimb(-bodyWidth / 4, bodyHeight / 2, limbWidth, limbLength, 10); // Left leg
        drawLimb(bodyWidth / 4, bodyHeight / 2, limbWidth, limbLength, -10); // Right leg


        glPopMatrix(); // Pop player model matrix

        // --- Player Rendering using Textures (Conceptual - requires texture loading) ---
        /*
        if (playerTexture != null) {
            glEnable(GL_TEXTURE_2D);
            playerTexture.bind(); // Assuming a Texture class

            glColor4f(1.0f, 1.0f, 1.0f, 1.0f); // Use white color mask for textures

            float texWidth = playerTexture.getWidth() * camera.getZoom() * 0.5f; // Scale texture size
            float texHeight = playerTexture.getHeight() * camera.getZoom() * 0.5f;

            // Adjust screenX, screenY to position texture correctly (e.g., center base)
            float drawX = screenX - texWidth / 2.0f;
            float drawY = screenY - texHeight; // Draw texture anchored at the base

            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(drawX, drawY); // Top-left
            glTexCoord2f(1, 0); glVertex2f(drawX + texWidth, drawY); // Top-right
            glTexCoord2f(1, 1); glVertex2f(drawX + texWidth, drawY + texHeight); // Bottom-right
            glTexCoord2f(0, 1); glVertex2f(drawX, drawY + texHeight); // Bottom-left
            glEnd();

            glDisable(GL_TEXTURE_2D);
        }
        */
    }

    // Helper to draw a simple filled circle in immediate mode
    private void drawCircle(float radius, int segments) {
        drawCircle(radius, segments, 0, 0); // Overload for centered circle
    }
    private void drawCircle(float radius, int segments, float offsetX, float offsetY) {
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(offsetX, offsetY); // Center point
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            float x = (float) (offsetX + Math.cos(angle) * radius);
            float y = (float) (offsetY + Math.sin(angle) * radius);
            glVertex2f(x, y);
        }
        glEnd();
    }
    // Helper to draw a simple limb (rectangle) at an angle
    private void drawLimb(float attachX, float attachY, float width, float length, float angle) {
        glPushMatrix();
        glTranslatef(attachX, attachY, 0); // Move to attachment point
        glRotatef(angle, 0, 0, 1); // Rotate

        // Draw limb extending downwards from (0,0) after rotation
        glBegin(GL_QUADS);
        glVertex2f(-width / 2, 0);
        glVertex2f(width / 2, 0);
        glVertex2f(width / 2, length);
        glVertex2f(-width / 2, length);
        glEnd();

        // Outline
        glColor3f(0.1f, 0.1f, 0.1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(-width / 2, 0);
        glVertex2f(width / 2, 0);
        glVertex2f(width / 2, length);
        glVertex2f(-width / 2, length);
        glEnd();

        glPopMatrix();
    }


    private void renderUI() {
        // --- IMPORTANT: Switch to screen-space coordinates for UI ---
        // The current projection is already set up for screen space (0,0 top-left)
        // We just need to make sure the ModelView matrix is identity.
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();


        // Example UI Text (Using placeholder drawing)
        int yPos = 20;
        int yInc = 18;
        Tile selectedTile = map.getTile(inputHandler.getSelectedRow(), inputHandler.getSelectedCol());
        String selectedInfo = "Selected: (" + inputHandler.getSelectedRow() + ", " + inputHandler.getSelectedCol() + ")";
        if (selectedTile != null) {
            selectedInfo += " Elev: " + selectedTile.getElevation() + " Type: " + selectedTile.getType();
        }

        drawText(10, yPos, "Player Pos: (" + player.getTileRow() + ", " + player.getTileCol() + ")"); yPos += yInc;
        drawText(10, yPos, selectedInfo); yPos += yInc;
        drawText(10, yPos, String.format("Camera: (%.1f, %.1f) Zoom: %.2f", camera.getCameraX(), camera.getCameraY(), camera.getZoom())); yPos += yInc;
        drawText(10, yPos, "Move: WASD | Select: Mouse Click | Elev +/-: Q/E"); yPos += yInc;
        drawText(10, yPos, "Levitate: F | Center Cam: C | Regenerate: G"); yPos += yInc;


        // Add FPS counter (simple example)
        // double fps = 1.0 / deltaTime; // Needs deltaTime passed in or stored
        // drawText(camera.getScreenWidth() - 100, 20, String.format("FPS: %.1f", fps));

        // --- Proper Text Rendering (Conceptual) ---
        /*
        if (fontRenderer != null) {
             fontRenderer.drawString(10, 20, "Player Pos: ...", 1.0f, Color.WHITE); // Example usage
             fontRenderer.drawString(10, 40, "Selected: ...", 1.0f, Color.WHITE);
             // ... etc
        }
        */
    }

    // Placeholder text drawing function (draws semi-transparent background boxes)
    private void drawText(int x, int y, String text) {
        int charWidth = 8; // Estimated width per character
        int charHeight = 15; // Estimated height
        int textWidth = text.length() * charWidth;

        // Draw a simple background box
        glColor4f(0.1f, 0.1f, 0.1f, 0.6f); // Dark semi-transparent background
        glBegin(GL_QUADS);
        glVertex2f(x - 2, y - 2);
        glVertex2f(x + textWidth + 2, y - 2);
        glVertex2f(x + textWidth + 2, y + charHeight + 2);
        glVertex2f(x - 2, y + charHeight + 2);
        glEnd();

        // Here you would use the FontRenderer to draw the actual text
        // fontRenderer.drawString(x, y, text, ...);

        // Draw a simple white line as placeholder text "glyph"
        glColor4f(1.0f, 1.0f, 1.0f, 0.8f);
        glBegin(GL_LINES);
        glVertex2f(x, y + charHeight / 2);
        glVertex2f(x+ textWidth, y + charHeight / 2);
        glEnd();
    }


    public void cleanup() {
        System.out.println("Renderer cleanup...");
        // Clean up textures, shaders, VBOs, font renderer here
        // if (fontRenderer != null) fontRenderer.cleanup();
        // if (playerTexture != null) playerTexture.delete();
    }
}