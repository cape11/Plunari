package org.isogame.render;
import java.io.IOException; // Add this line

import org.isogame.camera.CameraManager;
import org.isogame.entity.PlayerModel;
import org.isogame.input.InputHandler;
// Import your game map specifically
import org.isogame.map.Map; // This is your game's map class
import org.isogame.tile.Tile;
import org.isogame.render.Grass; // Assuming Grass.java is in this package

import java.util.Random;
// Import java.util.Map explicitly or use an alias if needed for clarity elsewhere
// For this specific use in renderUI, we will fully qualify it to avoid ambiguity.

import static org.isogame.constants.Constants.*;
import static org.lwjgl.opengl.GL11.*;

public class Renderer {
    private Font uiFont;

    private final CameraManager camera;
    private final org.isogame.map.Map map; // Use the fully qualified name or ensure no ambiguous Map import
    private final PlayerModel player;
    private final InputHandler inputHandler;

    private int frameCount = 0;
    private Texture playerTexture;
    private Random tileDetailRandom;

    public Renderer(CameraManager camera, org.isogame.map.Map map, PlayerModel player, InputHandler inputHandler) {
        this.camera = camera;
        this.map = map; // Your game map
        this.player = player;
        this.inputHandler = inputHandler;
        this.tileDetailRandom = new Random();
        loadAssets();
    }

    private void loadAssets() {
        // Path relative to src/main/resources/
        this.playerTexture = Texture.loadTexture("textures/lpc_character.png");
        if (this.playerTexture == null) {
            System.err.println("CRITICAL: Player texture failed to load. Sprite rendering will not work.");
        }
        try {
            // Replace "fonts/YourFontFile.ttf" with the actual path to your chosen .ttf file
            // For example, if you put "PressStart2P-Regular.ttf" in "src/main/resources/fonts/"
            this.uiFont = new Font("fonts/PressStart2P-Regular.ttf", 16f); // Load font at 16px size
        } catch (IOException e) {
            System.err.println("CRITICAL: Failed to load UI font.");
            e.printStackTrace();
            this.uiFont = null; // Ensure it's null if loading failed
        }
    }

    public void onResize(int width, int height) {
        glViewport(0, 0, width, height);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    public void render() {
        frameCount++;
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        renderMap();
        renderPlayer();
        renderUI();
    }

    private void renderMap() {
        int mapW = map.getWidth();
        int mapH = map.getHeight();
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

    private void renderTile(int tileR, int tileC, Tile tile, boolean isSelected) {
        int elevation = tile.getElevation();
        Tile.TileType type = tile.getType();

        int[] baseScreenCoords = camera.mapToScreenCoords(tileC, tileR, 0);
        int tileBaseX = baseScreenCoords[0];
        int tileBaseY = baseScreenCoords[1];

        int effWidth = camera.getEffectiveTileWidth();
        int effHeight = camera.getEffectiveTileHeight();
        int effThickness = camera.getEffectiveTileThickness();

        int margin = Math.max(effWidth, effHeight) * 2;
        if (tileBaseX < -margin || tileBaseX > camera.getScreenWidth() + margin ||
                tileBaseY < -margin - (ALTURA_MAXIMA * effThickness) || tileBaseY > camera.getScreenHeight() + margin + effHeight) {
            return;
        }

        float[] topColor = {1f, 0f, 1f, 1f}, side1Color = {1f, 0f, 1f, 1f}, side2Color = {1f, 0f, 1f, 1f},
                baseTopColor = {1f, 0f, 1f, 1f}, baseSide1Color = {1f, 0f, 1f, 1f}, baseSide2Color = {1f, 0f, 1f, 1f};
        boolean isWater = (type == Tile.TileType.WATER);

        if (isSelected) {
            topColor = new float[]{1.0f, 0.8f, 0.0f, 0.8f};
            side1Color = new float[]{0.9f, 0.7f, 0.0f, 0.8f};
            side2Color = new float[]{0.8f, 0.6f, 0.0f, 0.8f};
            baseTopColor = new float[]{0.5f, 0.4f, 0.0f, 0.8f};
            baseSide1Color = new float[]{0.4f, 0.3f, 0.0f, 0.8f};
            baseSide2Color = new float[]{0.3f, 0.2f, 0.0f, 0.8f};
        } else {
            switch (type) {
                case WATER:
                    double timeSpeed1 = 0.05, timeSpeed2 = 0.03, spatialScale1 = 0.4, spatialScale2 = 0.6;
                    double brightnessBase = 0.3, brightnessAmplitude = 0.15;
                    double greenShiftBase = 0.3, greenShiftAmplitude = 0.1;
                    double waveFactor1 = (frameCount * timeSpeed1 + (tileR + tileC) * spatialScale1);
                    double waveFactor2 = (frameCount * timeSpeed2 + (tileR * 0.7 - tileC * 0.6) * spatialScale2);
                    double waveValue = (Math.sin(waveFactor1) + Math.cos(waveFactor2)) / 2.0;
                    float blueValue = (float) Math.max(0.1, Math.min(1.0, brightnessBase + waveValue * brightnessAmplitude));
                    float greenValue = (float) Math.max(0.0, Math.min(1.0, blueValue * (greenShiftBase + Math.sin(waveFactor1 + tileC * 0.5) * greenShiftAmplitude)));
                    topColor = new float[]{0.0f, greenValue, blueValue, 0.85f};
                    side1Color = topColor;
                    side2Color = topColor;
                    baseTopColor = new float[]{0.05f, 0.1f, 0.2f, 1.0f};
                    baseSide1Color = new float[]{0.04f, 0.08f, 0.18f, 1.0f};
                    baseSide2Color = new float[]{0.03f, 0.06f, 0.16f, 1.0f};
                    break;
                case SAND:
                    topColor = new float[]{0.82f, 0.7f, 0.55f, 1.0f};
                    side1Color = new float[]{0.75f, 0.65f, 0.49f, 1.0f};
                    side2Color = new float[]{0.67f, 0.59f, 0.43f, 1.0f};
                    baseTopColor = new float[]{0.59f, 0.51f, 0.35f, 1.0f};
                    baseSide1Color = new float[]{0.51f, 0.43f, 0.27f, 1.0f};
                    baseSide2Color = new float[]{0.43f, 0.35f, 0.19f, 1.0f};
                    break;
                case GRASS:
                    topColor = new float[]{0.13f, 0.55f, 0.13f, 1.0f};
                    side1Color = new float[]{0.12f, 0.47f, 0.12f, 1.0f};
                    side2Color = new float[]{0.10f, 0.39f, 0.10f, 1.0f};
                    baseTopColor = new float[]{0.31f, 0.24f, 0.16f, 1.0f};
                    baseSide1Color = new float[]{0.27f, 0.20f, 0.14f, 1.0f};
                    baseSide2Color = new float[]{0.24f, 0.16f, 0.12f, 1.0f};
                    break;
                case ROCK:
                    topColor = new float[]{0.5f, 0.5f, 0.5f, 1.0f};
                    side1Color = new float[]{0.45f, 0.45f, 0.45f, 1.0f};
                    side2Color = new float[]{0.4f, 0.4f, 0.4f, 1.0f};
                    baseTopColor = new float[]{0.35f, 0.35f, 0.35f, 1.0f};
                    baseSide1Color = new float[]{0.3f, 0.3f, 0.3f, 1.0f};
                    baseSide2Color = new float[]{0.25f, 0.25f, 0.25f, 1.0f};
                    break;
                case SNOW:
                    topColor = new float[]{0.95f, 0.95f, 1.0f, 1.0f};
                    side1Color = new float[]{0.9f, 0.9f, 0.95f, 1.0f};
                    side2Color = new float[]{0.85f, 0.85f, 0.9f, 1.0f};
                    baseTopColor = new float[]{0.5f, 0.5f, 0.55f, 1.0f};
                    baseSide1Color = new float[]{0.45f, 0.45f, 0.5f, 1.0f};
                    baseSide2Color = new float[]{0.4f, 0.4f, 0.45f, 1.0f};
                    break;
                // default case already handled by initialization of colors
            }
        }

        int effBaseThickness = camera.getEffectiveBaseThickness();
        int[] b_Top = {tileBaseX + effWidth / 2, tileBaseY};
        int[] b_Left = {tileBaseX, tileBaseY + effHeight / 2};
        int[] b_Right = {tileBaseX + effWidth, tileBaseY + effHeight / 2};
        int[] b_Bottom = {tileBaseX + effWidth / 2, tileBaseY + effHeight};
        int[] b_Bottom_Left = {b_Left[0], b_Left[1] + effBaseThickness};
        int[] b_Bottom_Right = {b_Right[0], b_Right[1] + effBaseThickness};
        int[] b_Bottom_Bottom = {b_Bottom[0], b_Bottom[1] + effBaseThickness};

        glColor4f(baseSide1Color[0], baseSide1Color[1], baseSide1Color[2], baseSide1Color[3]);
        glBegin(GL_QUADS);
        glVertex2f(b_Left[0], b_Left[1]);
        glVertex2f(b_Bottom_Left[0], b_Bottom_Left[1]);
        glVertex2f(b_Bottom_Bottom[0], b_Bottom_Bottom[1]);
        glVertex2f(b_Bottom[0], b_Bottom[1]);
        glEnd();
        glColor4f(baseSide2Color[0], baseSide2Color[1], baseSide2Color[2], baseSide2Color[3]);
        glBegin(GL_QUADS);
        glVertex2f(b_Right[0], b_Right[1]);
        glVertex2f(b_Bottom_Right[0], b_Bottom_Right[1]);
        glVertex2f(b_Bottom_Bottom[0], b_Bottom_Bottom[1]);
        glVertex2f(b_Bottom[0], b_Bottom[1]);
        glEnd();
        glColor4f(baseTopColor[0], baseTopColor[1], baseTopColor[2], baseTopColor[3]);
        glBegin(GL_QUADS);
        glVertex2f(b_Left[0], b_Left[1]);
        glVertex2f(b_Top[0], b_Top[1]);
        glVertex2f(b_Right[0], b_Right[1]);
        glVertex2f(b_Bottom[0], b_Bottom[1]);
        glEnd();

        float topDiamondCenterX = 0, topDiamondCenterY = 0, topDiamondIsoWidth = 0, topDiamondIsoHeight = 0;

        if (!isWater && elevation > 0) {
            int topElevationOffset = elevation * effThickness;
            int topSurfacePlaneY = tileBaseY - topElevationOffset;
            int[] final_Top = {tileBaseX + effWidth / 2, topSurfacePlaneY};
            int[] final_Left = {tileBaseX, topSurfacePlaneY + effHeight / 2};
            int[] final_Right = {tileBaseX + effWidth, topSurfacePlaneY + effHeight / 2};
            int[] final_Bottom = {tileBaseX + effWidth / 2, topSurfacePlaneY + effHeight};
            int groundSurfaceY = tileBaseY;
            int[] ground_Left = {tileBaseX, groundSurfaceY + effHeight / 2};
            int[] ground_Right = {tileBaseX + effWidth, groundSurfaceY + effHeight / 2};
            int[] ground_Bottom = {tileBaseX + effWidth / 2, groundSurfaceY + effHeight};

            glColor4f(side1Color[0], side1Color[1], side1Color[2], side1Color[3]);
            glBegin(GL_QUADS);
            glVertex2f(ground_Left[0], ground_Left[1]);
            glVertex2f(ground_Bottom[0], ground_Bottom[1]);
            glVertex2f(final_Bottom[0], final_Bottom[1]);
            glVertex2f(final_Left[0], final_Left[1]);
            glEnd();
            glColor4f(side2Color[0], side2Color[1], side2Color[2], side2Color[3]);
            glBegin(GL_QUADS);
            glVertex2f(ground_Right[0], ground_Right[1]);
            glVertex2f(ground_Bottom[0], ground_Bottom[1]);
            glVertex2f(final_Bottom[0], final_Bottom[1]);
            glVertex2f(final_Right[0], final_Right[1]);
            glEnd();
            glColor4f(topColor[0], topColor[1], topColor[2], topColor[3]);
            glBegin(GL_QUADS);
            glVertex2f(final_Left[0], final_Left[1]);
            glVertex2f(final_Top[0], final_Top[1]);
            glVertex2f(final_Right[0], final_Right[1]);
            glVertex2f(final_Bottom[0], final_Bottom[1]);
            glEnd();

            topDiamondCenterX = final_Top[0];
            topDiamondCenterY = (final_Top[1] + final_Bottom[1]) / 2.0f;
            topDiamondIsoWidth = final_Right[0] - final_Left[0];
            topDiamondIsoHeight = final_Bottom[1] - final_Top[1];
        } else if (!isWater) {
            glColor4f(topColor[0], topColor[1], topColor[2], topColor[3]);
            glBegin(GL_QUADS);
            glVertex2f(b_Left[0], b_Left[1]);
            glVertex2f(b_Top[0], b_Top[1]);
            glVertex2f(b_Right[0], b_Right[1]);
            glVertex2f(b_Bottom[0], b_Bottom[1]);
            glEnd();

            topDiamondCenterX = b_Top[0];
            topDiamondCenterY = (b_Top[1] + b_Bottom[1]) / 2.0f;
            topDiamondIsoWidth = b_Right[0] - b_Left[0];
            topDiamondIsoHeight = b_Bottom[1] - b_Top[1];
        }

        if (type == Tile.TileType.GRASS && !isWater && topDiamondIsoWidth > 0) {
            long seed = (long) tileR * map.getWidth() + tileC;
            this.tileDetailRandom.setSeed(seed);
            Grass.renderThickGrassTufts(this.tileDetailRandom,
                    topDiamondCenterX, topDiamondCenterY,
                    topDiamondIsoWidth, topDiamondIsoHeight,
                    15, // More density
                    topColor[0] * 0.75f, topColor[1] * 0.85f, topColor[2] * 0.7f,
                    camera.getZoom());
        }
    }

    private void renderPlayer() {
        if (playerTexture == null) {
            System.err.println("Player texture not loaded, cannot render player sprite.");
            return;
        }
        int playerTileRow = player.getTileRow();
        int playerTileCol = player.getTileCol();
        Tile currentTile = map.getTile(playerTileRow, playerTileCol);
        int playerBaseElevation = (currentTile != null) ? currentTile.getElevation() : 0;
        int[] playerScreenBaseCoords = camera.mapToScreenCoords(player.getMapCol(), player.getMapRow(), playerBaseElevation);
        float screenX = playerScreenBaseCoords[0];
        float screenY = playerScreenBaseCoords[1];

        glEnable(GL_TEXTURE_2D);
        playerTexture.bind();
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        int frameWidthPx = PlayerModel.FRAME_WIDTH;
        int frameHeightPx = PlayerModel.FRAME_HEIGHT;
        int animationFrameColumn = player.getVisualFrameIndex();
        int animationSheetRow = player.getAnimationRow();
        float framePixelXOnSheet = animationFrameColumn * frameWidthPx;
        float framePixelYOnSheet = animationSheetRow * frameHeightPx;
        float totalSheetWidthPx = playerTexture.getWidth();
        float totalSheetHeightPx = playerTexture.getHeight();
        float u0 = framePixelXOnSheet / totalSheetWidthPx;
        float v0 = framePixelYOnSheet / totalSheetHeightPx;
        float u1 = (framePixelXOnSheet + frameWidthPx) / totalSheetWidthPx;
        float v1 = (framePixelYOnSheet + frameHeightPx) / totalSheetHeightPx;
        float spriteDrawWidth = frameWidthPx * camera.getZoom();
        float spriteDrawHeight = frameHeightPx * camera.getZoom();
        float drawX = screenX - (spriteDrawWidth / 2.0f);
        float drawY = screenY - spriteDrawHeight;
        if (player.isLevitating()) {
            drawY -= (int) (Math.sin(player.getLevitateTimer()) * 8 * camera.getZoom());
        }

        glBegin(GL_QUADS);
        glTexCoord2f(u0, v0);
        glVertex2f(drawX, drawY);
        glTexCoord2f(u1, v0);
        glVertex2f(drawX + spriteDrawWidth, drawY);
        glTexCoord2f(u1, v1);
        glVertex2f(drawX + spriteDrawWidth, drawY + spriteDrawHeight);
        glTexCoord2f(u0, v1);
        glVertex2f(drawX, drawY + spriteDrawHeight);
        glEnd();
        playerTexture.unbind();
        glDisable(GL_TEXTURE_2D);
    }

    private void renderUI() {
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        int yPos = 20;
        int yInc = 18;
        Tile selectedTile = map.getTile(inputHandler.getSelectedRow(), inputHandler.getSelectedCol());
        String selectedInfo = "Selected: (" + inputHandler.getSelectedRow() + ", " + inputHandler.getSelectedCol() + ")";
        if (selectedTile != null) {
            selectedInfo += " Elev: " + selectedTile.getElevation() + " Type: " + selectedTile.getType();
        }
        drawText(10, yPos, "Player: (" + player.getTileRow() + ", " + player.getTileCol() + ") Act: " + player.getCurrentAction() + " Dir: " + player.getCurrentDirection() + " F:" + player.getVisualFrameIndex());
        yPos += yInc;
        drawText(10, yPos, selectedInfo);
        yPos += yInc;
        drawText(10, yPos, String.format("Camera: (%.1f, %.1f) Zoom: %.2f", camera.getCameraX(), camera.getCameraY(), camera.getZoom()));
        yPos += yInc;
        drawText(10, yPos, "Move: WASD | Sel: Mouse | Elev Sel +/-: Q/E | DIG J |");
        yPos += yInc;
        drawText(10, yPos, "Levitate: F | Center Cam: C | Regen Map: G");
        yPos += yInc;

        yPos += yInc;
        drawText(10, yPos, "Inventory:");
        yPos += yInc;

        // Explicitly use java.util.Map here to avoid ambiguity
        java.util.Map<String, Integer> inventory = player.getInventory();

        if (inventory.isEmpty()) {
            drawText(20, yPos, "- Empty -");
            yPos += yInc;
        } else {
            for (java.util.Map.Entry<String, Integer> entry : inventory.entrySet()) {
                drawText(20, yPos, "- " + entry.getKey() + ": " + entry.getValue());
                yPos += yInc;
            }
        }
    }

    private void drawText(int x, int y, String text) { // Changed parameters to int for simplicity
        if (uiFont != null) {
            // uiFont.drawText sets its own color, or you can add r,g,b parameters
            uiFont.drawText((float) x, (float) y, text); // Default white text
        } else {
            // Your old placeholder drawing as a fallback
            int charWidth = 8;
            int charHeight = 15;
            int textWidth = text.length() * charWidth;
            glColor4f(0.1f, 0.1f, 0.1f, 0.6f);
            glBegin(GL_QUADS);
            glVertex2f(x - 2, y - 2);
            glVertex2f(x + textWidth + 2, y - 2);
            glVertex2f(x + textWidth + 2, y + charHeight + 2);
            glVertex2f(x - 2, y + charHeight + 2);
            glEnd();
            glColor4f(1.0f, 1.0f, 1.0f, 0.8f);
            glBegin(GL_LINES);
            glVertex2f(x, y + charHeight / 2.0f);
            glVertex2f(x + textWidth, y + charHeight / 2.0f);
            glEnd();
        }
    }

    public void cleanup() {
        System.out.println("Renderer cleanup...");
        if (playerTexture != null) {
            playerTexture.delete();
        }

        if (uiFont != null) {
            uiFont.cleanup(); // Important!
        }
    }
}