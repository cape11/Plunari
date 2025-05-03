package org.isogame.render;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import static org.isogame.constants.Constants.*;
import org.isogame.input.InputHandler;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;
import org.isogame.game.Game;

public class Renderer {
    private final Game game;
    private final InputHandler inputHandler;

    public Renderer(Game game, InputHandler inputHandler) {
        this.game = game;
        this.inputHandler = inputHandler; // Now inputHandler is defined and usable
    }


    public void render() {
        int[][] alturas = game.alturas;
        int currentRow = inputHandler.currentRow; // Get player row from InputHandler
        int currentCol = inputHandler.currentCol; // Get player col from InputHandler
        System.out.println("Rendering map centered at row " + currentRow + ", col " + currentCol);

        for (int row = 0; row < alturas.length; row++) {
            for (int col = 0; col < alturas[0].length; col++) {
                int altura = alturas[row][col];
                // Use altura for rendering logic
            }
        }

        // We'll use immediate mode for simplicity in migration
        // In a real project, you should use modern OpenGL with VBOs/VAOs

        int mapPixelWidth = (MAP_WIDTH + MAP_HEIGHT) * TILE_WIDTH / 2;
        int mapPixelHeight = (MAP_WIDTH + MAP_HEIGHT) * TILE_HEIGHT / 2;
        int offsetX = (WIDTH - mapPixelWidth) / 2;
        int offsetY = (HEIGHT - mapPixelHeight) / 2 + (ALTURA_MAXIMA * tileThickness / 2);

        for (int row = 0; row < MAP_HEIGHT; row++) {
            for (int col = 0; col < MAP_WIDTH; col++) {
                int elevation = alturas[row][col];
                int tileX = (col - row) * TILE_WIDTH / 2 + offsetX + (MAP_WIDTH * TILE_WIDTH / 2);
                int tileY = (col + row) * TILE_HEIGHT / 2 + offsetY;

                // Simple culling
                if (tileX > WIDTH || tileX + TILE_WIDTH < 0 || tileY > HEIGHT || tileY + TILE_HEIGHT + baseThickness < 0) {
                    continue;
                }

                int visualY = tileY;
                if (inputHandler.levitating) {
                    visualY += (int) (Math.sin(inputHandler.levitateTimer + (row + col) * 0.3) * 5);
                }

                // Check if this is the current selected tile
                boolean isSelected = (row == inputHandler.currentRow && col == inputHandler.currentCol);

                // Draw the tile
                drawTile(tileX, visualY, row, col, elevation, isSelected);
            }
        }

        // Draw UI text
        drawText(10, 20, "Pos: (" + inputHandler.currentRow + ", " + inputHandler.currentCol + ")");
        drawText(10, 35, "Altura: " + alturas[inputHandler.currentRow][inputHandler.currentCol]);
        drawText(10, 50, "G: Regenerar Mapa | S: Levitar | A/Z: Altura | Flechas: Mover");
    }

    private void drawText(int x, int y, String text) {
        // In OpenGL immediate mode, drawing text is non-trivial
        // For simplicity in this migration, we'll draw a placeholder
        // In a real application, you would use a text rendering library like FTGL

        // Draw a small rectangle as a placeholder
        glColor3f(1.0f, 1.0f, 1.0f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + text.length() * 8, y);
        glVertex2f(x + text.length() * 8, y + 15);
        glVertex2f(x, y + 15);
        glEnd();

        // Note: For proper text rendering, you would use a library like FTGL or implement
        // texture-based font rendering. This is just a placeholder.
    }

    private void drawTile(int x, int y, int row, int col, int elevation, boolean isSelected) {
        // Determine the type of terrain and colors
        boolean isWater = elevation < NIVEL_MAR;
        boolean isSand = !isWater && elevation < NIVEL_ARENA;

        float[] topColor, side1Color, side2Color, baseTopColor, baseSide1Color, baseSide2Color;

        if (isSelected) {
            // Red highlight for selected tile
            topColor = new float[]{1.0f, 0.0f, 0.0f, 0.7f};
            side1Color = new float[]{0.8f, 0.0f, 0.0f, 0.7f};
            side2Color = new float[]{0.6f, 0.0f, 0.0f, 0.7f};
            baseTopColor = new float[]{0.5f, 0.0f, 0.0f, 0.7f};
            baseSide1Color = new float[]{0.4f, 0.0f, 0.0f, 0.7f};
            baseSide2Color = new float[]{0.3f, 0.0f, 0.0f, 0.7f};
        } else if (isWater) {
            // Water colors
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

        // Base points for the footprint
        int[] base_Top = {x + TILE_WIDTH / 2, y};
        int[] base_Left = {x, y + TILE_HEIGHT / 2};
        int[] base_Right = {x + TILE_WIDTH, y + TILE_HEIGHT / 2};
        int[] base_Bottom = {x + TILE_WIDTH / 2, y + TILE_HEIGHT};

        // Draw the base (footprint)
        // Left face
        glColor4f(baseSide1Color[0], baseSide1Color[1], baseSide1Color[2], baseSide1Color[3]);
        glBegin(GL_QUADS);
        glVertex2f(base_Left[0], base_Left[1]);
        glVertex2f(base_Left[0], base_Left[1] + baseThickness);
        glVertex2f(base_Bottom[0], base_Bottom[1] + baseThickness);
        glVertex2f(base_Bottom[0], base_Bottom[1]);
        glEnd();

        // Right face
        glColor4f(baseSide2Color[0], baseSide2Color[1], baseSide2Color[2], baseSide2Color[3]);
        glBegin(GL_QUADS);
        glVertex2f(base_Right[0], base_Right[1]);
        glVertex2f(base_Right[0], base_Right[1] + baseThickness);
        glVertex2f(base_Bottom[0], base_Bottom[1] + baseThickness);
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
            int elevationHeight = alturaVisible * tileThickness;
            int groundLevelY = y - Math.max(0, (NIVEL_MAR - 1) * tileThickness);
            int finalTopY = groundLevelY - elevationHeight;

            int[] final_Top = {x + TILE_WIDTH / 2, finalTopY};
            int[] final_Left = {x, finalTopY + TILE_HEIGHT / 2};
            int[] final_Right = {x + TILE_WIDTH, finalTopY + TILE_HEIGHT / 2};
            int[] final_Bottom = {x + TILE_WIDTH / 2, finalTopY + TILE_HEIGHT};
            int[] towerBase_Left = {x, groundLevelY + TILE_HEIGHT / 2};
            int[] towerBase_Right = {x + TILE_WIDTH, groundLevelY + TILE_HEIGHT / 2};
            int[] towerBase_Bottom = {x + TILE_WIDTH / 2, groundLevelY + TILE_HEIGHT};

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
            int groundLevelY = y - Math.max(0, (NIVEL_MAR - 1) * tileThickness);
            int[] surface_Top = {x + TILE_WIDTH / 2, groundLevelY};
            int[] surface_Left = {x, groundLevelY + TILE_HEIGHT / 2};
            int[] surface_Right = {x + TILE_WIDTH, groundLevelY + TILE_HEIGHT / 2};
            int[] surface_Bottom = {x + TILE_WIDTH / 2, groundLevelY + TILE_HEIGHT};

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

