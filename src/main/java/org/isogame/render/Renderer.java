package org.isogame.render;

import org.lwjgl.opengl.*;
import static org.isogame.constants.Constants.*;
import org.isogame.input.InputHandler;
import org.isogame.camera.CameraManager;
import org.isogame.game.Game;
import org.isogame.entity.PlayerModel;

import static org.lwjgl.opengl.GL11.*;

public class Renderer {
    private final Game game;
    private final InputHandler inputHandler;
    private final CameraManager camera;
    private final PlayerModel playerModel;

    public Renderer(Game game, InputHandler inputHandler, CameraManager camera) {
        this.game = game;
        this.inputHandler = inputHandler;
        this.camera = camera;
        this.playerModel = new PlayerModel();
    }

    public void render() {
        int[][] alturas = game.alturas;
        int currentRow = inputHandler.currentRow;
        int currentCol = inputHandler.currentCol;

        // Character position is now the red tile
        int characterRow = inputHandler.characterRow;
        int characterCol = inputHandler.characterCol;

        // Sort tiles for correct rendering order (back-to-front)
        // This simple approach works for isometric view
        for (int sum = 0; sum <= MAP_WIDTH + MAP_HEIGHT - 2; sum++) {
            for (int row = 0; row <= sum; row++) {
                int col = sum - row;

                if (row < MAP_HEIGHT && col < MAP_WIDTH) {
                    // Check if this is the character position
                    boolean isCharacter = (row == characterRow && col == characterCol);
                    renderTile(row, col, alturas[row][col], isCharacter);

                    // Render the player model at its position
                    if (isCharacter) {
                        renderPlayerAtPosition(row, col, alturas[row][col]);
                    }
                }
            }
        }

        // Draw UI text
        drawText(10, 20, "Pos: (" + inputHandler.currentRow + ", " + inputHandler.currentCol + ")");
        drawText(10, 35, "Altura: " + alturas[inputHandler.currentRow][inputHandler.currentCol]);
        drawText(10, 50, "G: Regenerar Mapa | F: Levitar | Q/E: Altura | Flechas: Mover");
        drawText(10, 65, "Scroll: Zoom | Middle Mouse: Pan Camera | C: Center Camera");
        drawText(10, 80, String.format("Camera: (%.1f, %.1f) Zoom: %.1f",
                camera.getCameraX(), camera.getCameraY(), camera.getZoom()));
    }

    private void renderPlayerAtPosition(int row, int col, int elevation) {
        // Get screen coordinates for the player's position
        int[] posCoords = camera.mapToScreenCoords(col, row, elevation);
        int screenX = posCoords[0];
        int screenY = posCoords[1];

        // Apply floating effect if levitating
        if (inputHandler.levitating) {
            screenY += (int) (Math.sin(inputHandler.levitateTimer) * 5);
        }

        // Render the player model at the calculated position
        // We pass the effective tile size to scale the model appropriately
        int effectiveTileSize = camera.getEffectiveTileHeight();
        playerModel.render(screenX, screenY, effectiveTileSize, game.getFrameCount());
    }

    private void renderTile(int row, int col, int elevation, boolean isCharacter) {
        // Check if this is the current selected tile
        boolean isSelected = (row == inputHandler.currentRow && col == inputHandler.currentCol);

        // Get screen coordinates for tile
        int[] baseCoords = camera.mapToScreenCoords(col, row, 0);
        int tileX = baseCoords[0];
        int tileY = baseCoords[1];


        // Fixed culling - more generous boundaries to prevent missing tiles
        int effectiveTileWidth = camera.getEffectiveTileWidth();
        int effectiveTileHeight = camera.getEffectiveTileHeight();
        int maxElevationPixels = camera.getEffectiveTileThickness() * ALTURA_MAXIMA;

        // More generous culling margins
        int margin = Math.max(effectiveTileWidth, effectiveTileHeight) * 2;

        if (tileX > camera.getScreenWidth() + margin ||
                tileX + effectiveTileWidth < -margin ||
                tileY > camera.getScreenHeight() + margin ||
                tileY + effectiveTileHeight - maxElevationPixels < -margin) {
            return;
        }

        int visualY = tileY;
        if (inputHandler.levitating) {
            visualY += (int) (Math.sin(inputHandler.levitateTimer + (row + col) * 0.3) * 5);
        }

        // Draw the tile using the coordinates
        drawTile(tileX, visualY, row, col, elevation, isCharacter);
    }

    private void drawText(int x, int y, String text) {
        // In OpenGL immediate mode, drawing text is non-trivial
        // For simplicity, we'll draw a placeholder
        glColor3f(1.0f, 1.0f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + text.length() * 8, y);
        glVertex2f(x + text.length() * 8, y + 15);
        glVertex2f(x, y + 15);
        glEnd();

        // Note: For proper text rendering, you would use a library like FTGL
    }

    private void drawTile(int x, int y, int row, int col, int elevation, boolean isCharacter) {
        // Determine the type of terrain and colors
        boolean isWater = elevation < NIVEL_MAR;
        boolean isSand = !isWater && elevation < NIVEL_ARENA;

        float[] topColor, side1Color, side2Color, baseTopColor, baseSide1Color, baseSide2Color;

        if (isCharacter) {
            // Red highlight for character's tile
            topColor = new float[]{1.0f, 0.0f, 0.0f, 0.7f};
            side1Color = new float[]{0.8f, 0.0f, 0.0f, 0.7f};
            side2Color = new float[]{0.6f, 0.0f, 0.0f, 0.7f};
            baseTopColor = new float[]{0.5f, 0.0f, 0.0f, 0.7f};
            baseSide1Color = new float[]{0.4f, 0.0f, 0.0f, 0.7f};
            baseSide2Color = new float[]{0.3f, 0.0f, 0.0f, 0.7f};
        } else if (isWater) {
            // Water colors with animation
            double timeSpeed1 = 0.08, timeSpeed2 = 0.05, spatialScale1 = 0.5, spatialScale2 = 0.8;
            double brightnessBase = 0.2, brightnessAmplitude = 0.12;
            double greenShiftBase = 0.1, greenShiftAmplitude = 0.05;
            double waveFactor1 = (game.getFrameCount() * timeSpeed1 + (row + col) * spatialScale1);
            double waveFactor2 = (game.getFrameCount() * timeSpeed2 + (row * 0.8 - col * 0.5) * spatialScale2);
            double waveValue = (Math.sin(waveFactor1) + Math.cos(waveFactor2)) / 2.0;

            float blueValue = (float) Math.max(0, Math.min(1.0, brightnessBase + waveValue * brightnessAmplitude));
            float greenValue = (float) Math.max(0, Math.min(1.0, blueValue * (greenShiftBase + Math.sin(waveFactor1) * greenShiftAmplitude)));

            topColor = new float[]{0.0f, greenValue, blueValue, 1.0f};
            side1Color = topColor;
            side2Color = topColor;
            baseTopColor = new float[]{0.0f, 0.04f, 0.16f, 1.0f};
            baseSide1Color = new float[]{0.0f, 0.03f, 0.14f, 1.0f};
            baseSide2Color = new float[]{0.0f, 0.02f, 0.12f, 1.0f};
        } else if (isSand) {
            // Sand colors
            topColor = new float[]{0.82f, 0.7f, 0.55f, 1.0f};
            side1Color = new float[]{0.75f, 0.65f, 0.49f, 1.0f};
            side2Color = new float[]{0.67f, 0.59f, 0.43f, 1.0f};
            baseTopColor = new float[]{0.59f, 0.51f, 0.35f, 1.0f};
            baseSide1Color = new float[]{0.51f, 0.43f, 0.27f, 1.0f};
            baseSide2Color = new float[]{0.43f, 0.35f, 0.19f, 1.0f};
        } else {
            // Grass colors
            topColor = new float[]{0.13f, 0.55f, 0.13f, 1.0f};
            side1Color = new float[]{0.12f, 0.47f, 0.12f, 1.0f};
            side2Color = new float[]{0.10f, 0.39f, 0.10f, 1.0f};
            baseTopColor = new float[]{0.31f, 0.24f, 0.16f, 1.0f};
            baseSide1Color = new float[]{0.27f, 0.20f, 0.14f, 1.0f};
            baseSide2Color = new float[]{0.24f, 0.16f, 0.12f, 1.0f};
        }

        // Get dimension values scaled by zoom level
        int effectiveTileWidth = camera.getEffectiveTileWidth();
        int effectiveTileHeight = camera.getEffectiveTileHeight();
        int effectiveBaseThickness = (int)(baseThickness * camera.getZoom());

        // Base points for the footprint
        int[] base_Top = {x + effectiveTileWidth / 2, y};
        int[] base_Left = {x, y + effectiveTileHeight / 2};
        int[] base_Right = {x + effectiveTileWidth, y + effectiveTileHeight / 2};
        int[] base_Bottom = {x + effectiveTileWidth / 2, y + effectiveTileHeight};

        // Draw the base (footprint)
        // Left face
        glColor4f(baseSide1Color[0], baseSide1Color[1], baseSide1Color[2], baseSide1Color[3]);
        glBegin(GL_QUADS);
        glVertex2f(base_Left[0], base_Left[1]);
        glVertex2f(base_Left[0], base_Left[1] + effectiveBaseThickness);
        glVertex2f(base_Bottom[0], base_Bottom[1] + effectiveBaseThickness);
        glVertex2f(base_Bottom[0], base_Bottom[1]);
        glEnd();

        // Right face
        glColor4f(baseSide2Color[0], baseSide2Color[1], baseSide2Color[2], baseSide2Color[3]);
        glBegin(GL_QUADS);
        glVertex2f(base_Right[0], base_Right[1]);
        glVertex2f(base_Right[0], base_Right[1] + effectiveBaseThickness);
        glVertex2f(base_Bottom[0], base_Bottom[1] + effectiveBaseThickness);
        glVertex2f(base_Bottom[0], base_Bottom[1]);
        glEnd();

        // Top surface
        glColor4f(baseTopColor[0], baseTopColor[1], baseTopColor[2], baseTopColor[3]);
        glBegin(GL_QUADS);
        glVertex2f(base_Left[0], base_Left[1]);
        glVertex2f(base_Top[0], base_Top[1]);
        glVertex2f(base_Right[0], base_Right[1]);
        glVertex2f(base_Bottom[0], base_Bottom[1]);
        glEnd();

        // Draw the tower/elevation if not water
        int alturaVisible = Math.max(0, elevation - (NIVEL_MAR - 1));
        if (alturaVisible > 0 && !isWater) {
            int effectiveTileThickness = camera.getEffectiveTileThickness();
            int elevationHeight = alturaVisible * effectiveTileThickness;
            int groundLevelY = y - Math.max(0, (NIVEL_MAR - 1) * effectiveTileThickness);
            int finalTopY = groundLevelY - elevationHeight;

            int[] final_Top = {x + effectiveTileWidth / 2, finalTopY};
            int[] final_Left = {x, finalTopY + effectiveTileHeight / 2};
            int[] final_Right = {x + effectiveTileWidth, finalTopY + effectiveTileHeight / 2};
            int[] final_Bottom = {x + effectiveTileWidth / 2, finalTopY + effectiveTileHeight};
            int[] towerBase_Left = {x, groundLevelY + effectiveTileHeight / 2};
            int[] towerBase_Right = {x + effectiveTileWidth, groundLevelY + effectiveTileHeight / 2};
            int[] towerBase_Bottom = {x + effectiveTileWidth / 2, groundLevelY + effectiveTileHeight};

            // Left wall
            glColor4f(side1Color[0], side1Color[1], side1Color[2], side1Color[3]);
            glBegin(GL_QUADS);
            glVertex2f(towerBase_Left[0], towerBase_Left[1]);
            glVertex2f(towerBase_Bottom[0], towerBase_Bottom[1]);
            glVertex2f(final_Bottom[0], final_Bottom[1]);
            glVertex2f(final_Left[0], final_Left[1]);
            glEnd();

            // Right wall
            glColor4f(side2Color[0], side2Color[1], side2Color[2], side2Color[3]);
            glBegin(GL_QUADS);
            glVertex2f(towerBase_Right[0], towerBase_Right[1]);
            glVertex2f(towerBase_Bottom[0], towerBase_Bottom[1]);
            glVertex2f(final_Bottom[0], final_Bottom[1]);
            glVertex2f(final_Right[0], final_Right[1]);
            glEnd();

            // Top surface
            glColor4f(topColor[0], topColor[1], topColor[2], topColor[3]);
            glBegin(GL_QUADS);
            glVertex2f(final_Left[0], final_Left[1]);
            glVertex2f(final_Top[0], final_Top[1]);
            glVertex2f(final_Right[0], final_Right[1]);
            glVertex2f(final_Bottom[0], final_Bottom[1]);
            glEnd();

            // Draw outline
            glColor4f(topColor[0] * 0.5f, topColor[1] * 0.5f, topColor[2] * 0.5f, topColor[3]);
            glBegin(GL_LINE_LOOP);
            glVertex2f(final_Left[0], final_Left[1]);
            glVertex2f(final_Top[0], final_Top[1]);
            glVertex2f(final_Right[0], final_Right[1]);
            glVertex2f(final_Bottom[0], final_Bottom[1]);
            glEnd();
        } else if (!isWater) {
            // Draw flat surface for ground level terrain
            int effectiveTileThickness = camera.getEffectiveTileThickness();
            int groundLevelY = y - Math.max(0, (NIVEL_MAR - 1) * effectiveTileThickness);
            int[] surface_Top = {x + effectiveTileWidth / 2, groundLevelY};
            int[] surface_Left = {x, groundLevelY + effectiveTileHeight / 2};
            int[] surface_Right = {x + effectiveTileWidth, groundLevelY + effectiveTileHeight / 2};
            int[] surface_Bottom = {x + effectiveTileWidth / 2, groundLevelY + effectiveTileHeight};

            glColor4f(topColor[0], topColor[1], topColor[2], topColor[3]);
            glBegin(GL_QUADS);
            glVertex2f(surface_Left[0], surface_Left[1]);
            glVertex2f(surface_Top[0], surface_Top[1]);
            glVertex2f(surface_Right[0], surface_Right[1]);
            glVertex2f(surface_Bottom[0], surface_Bottom[1]);
            glEnd();

            // Draw outline
            glColor4f(topColor[0] * 0.5f, topColor[1] * 0.5f, topColor[2] * 0.5f, topColor[3]);
            glBegin(GL_LINE_LOOP);
            glVertex2f(surface_Left[0], surface_Left[1]);
            glVertex2f(surface_Top[0], surface_Top[1]);
            glVertex2f(surface_Right[0], surface_Right[1]);
            glVertex2f(surface_Bottom[0], surface_Bottom[1]);
            glEnd();
        }
    }
}