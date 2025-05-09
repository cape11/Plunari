package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.entity.PlayerModel;
import org.isogame.input.InputHandler;
import org.isogame.map.Map;
import org.isogame.tile.Tile;

import static org.isogame.constants.Constants.*;
import static org.lwjgl.opengl.GL11.*;

public class Renderer {

    private final CameraManager camera;
    private final Map map;
    private final PlayerModel player;
    private final InputHandler inputHandler;

    private int frameCount = 0;
    private Texture playerTexture;

    public Renderer(CameraManager camera, Map map, PlayerModel player, InputHandler inputHandler) {
        this.camera = camera;
        this.map = map;
        this.player = player;
        this.inputHandler = inputHandler;
        loadAssets();
    }

    private void loadAssets() {
        // IMPORTANT: Ensure 'lpc_character.png' is in 'src/main/resources/textures/'
        // Or update this path if you named it differently or placed it elsewhere.
        this.playerTexture = Texture.loadTexture("textures/lpc_character.png");
        if (this.playerTexture == null) {
            System.err.println("CRITICAL: Player texture failed to load. Sprite rendering will not work.");
            // Consider a fallback or exiting if the texture is essential
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

    // renderTile method remains the same as the last complete version you have.
    // For brevity, I'm omitting it here but ensure it's present in your file.
    private void renderTile(int row, int col, Tile tile, boolean isSelected) {
        int elevation = tile.getElevation();
        Tile.TileType type = tile.getType();

        int[] baseCoords = camera.mapToScreenCoords(col, row, 0);
        int tileBaseX = baseCoords[0];
        int tileBaseY = baseCoords[1];

        int effTileWidth = camera.getEffectiveTileWidth();
        int effTileHeight = camera.getEffectiveTileHeight();
        int margin = Math.max(effTileWidth, effTileHeight) * 2;

        if (tileBaseX < -margin || tileBaseX > camera.getScreenWidth() + margin ||
                tileBaseY < -margin || tileBaseY > camera.getScreenHeight() + margin + (ALTURA_MAXIMA * camera.getEffectiveTileThickness())) {
            return;
        }

        float[] topColor, side1Color, side2Color, baseTopColor, baseSide1Color, baseSide2Color;
        boolean isWater = (type == Tile.TileType.WATER);

        if (isSelected) {
            topColor = new float[]{1.0f, 0.8f, 0.0f, 0.8f}; side1Color = new float[]{0.9f, 0.7f, 0.0f, 0.8f}; side2Color = new float[]{0.8f, 0.6f, 0.0f, 0.8f};
            baseTopColor = new float[]{0.5f, 0.4f, 0.0f, 0.8f}; baseSide1Color = new float[]{0.4f, 0.3f, 0.0f, 0.8f}; baseSide2Color = new float[]{0.3f, 0.2f, 0.0f, 0.8f};
        } else {
            switch (type) {
                case WATER:
                    double timeSpeed1 = 0.05, timeSpeed2 = 0.03, spatialScale1 = 0.4, spatialScale2 = 0.6;
                    double brightnessBase = 0.3, brightnessAmplitude = 0.15; double greenShiftBase = 0.3, greenShiftAmplitude = 0.1;
                    double waveFactor1 = (frameCount * timeSpeed1 + (row + col) * spatialScale1);
                    double waveFactor2 = (frameCount * timeSpeed2 + (row * 0.7 - col * 0.6) * spatialScale2);
                    double waveValue = (Math.sin(waveFactor1) + Math.cos(waveFactor2)) / 2.0;
                    float blueValue = (float) Math.max(0.1, Math.min(1.0, brightnessBase + waveValue * brightnessAmplitude));
                    float greenValue = (float) Math.max(0.0, Math.min(1.0, blueValue * (greenShiftBase + Math.sin(waveFactor1 + col * 0.5) * greenShiftAmplitude)));
                    topColor = new float[]{0.0f, greenValue, blueValue, 0.85f}; side1Color = topColor; side2Color = topColor;
                    baseTopColor = new float[]{0.05f, 0.1f, 0.2f, 1.0f}; baseSide1Color = new float[]{0.04f, 0.08f, 0.18f, 1.0f}; baseSide2Color = new float[]{0.03f, 0.06f, 0.16f, 1.0f};
                    break;
                case SAND:
                    topColor = new float[]{0.82f, 0.7f, 0.55f, 1.0f}; side1Color = new float[]{0.75f, 0.65f, 0.49f, 1.0f}; side2Color = new float[]{0.67f, 0.59f, 0.43f, 1.0f};
                    baseTopColor = new float[]{0.59f, 0.51f, 0.35f, 1.0f}; baseSide1Color = new float[]{0.51f, 0.43f, 0.27f, 1.0f}; baseSide2Color = new float[]{0.43f, 0.35f, 0.19f, 1.0f};
                    break;
                case GRASS:
                    topColor = new float[]{0.13f, 0.55f, 0.13f, 1.0f}; side1Color = new float[]{0.12f, 0.47f, 0.12f, 1.0f}; side2Color = new float[]{0.10f, 0.39f, 0.10f, 1.0f};
                    baseTopColor = new float[]{0.31f, 0.24f, 0.16f, 1.0f}; baseSide1Color = new float[]{0.27f, 0.20f, 0.14f, 1.0f}; baseSide2Color = new float[]{0.24f, 0.16f, 0.12f, 1.0f};
                    break;
                case ROCK:
                    topColor = new float[]{0.5f, 0.5f, 0.5f, 1.0f}; side1Color = new float[]{0.45f, 0.45f, 0.45f, 1.0f}; side2Color = new float[]{0.4f, 0.4f, 0.4f, 1.0f};
                    baseTopColor = new float[]{0.35f, 0.35f, 0.35f, 1.0f}; baseSide1Color = new float[]{0.3f, 0.3f, 0.3f, 1.0f}; baseSide2Color = new float[]{0.25f, 0.25f, 0.25f, 1.0f};
                    break;
                case SNOW:
                    topColor = new float[]{0.95f, 0.95f, 1.0f, 1.0f}; side1Color = new float[]{0.9f, 0.9f, 0.95f, 1.0f}; side2Color = new float[]{0.85f, 0.85f, 0.9f, 1.0f};
                    baseTopColor = new float[]{0.5f, 0.5f, 0.55f, 1.0f}; baseSide1Color = new float[]{0.45f, 0.45f, 0.5f, 1.0f}; baseSide2Color = new float[]{0.4f, 0.4f, 0.45f, 1.0f};
                    break;
                default: topColor = new float[]{1.0f, 0.0f, 1.0f, 1.0f}; side1Color = topColor; side2Color = topColor; baseTopColor = topColor; baseSide1Color = topColor; baseSide2Color = topColor; break;
            }
        }

        int effWidth = camera.getEffectiveTileWidth(); int effHeight = camera.getEffectiveTileHeight();
        int effThickness = camera.getEffectiveTileThickness(); int effBaseThickness = camera.getEffectiveBaseThickness();
        int[] base_Top = {tileBaseX + effWidth / 2, tileBaseY}; int[] base_Left = {tileBaseX, tileBaseY + effHeight / 2};
        int[] base_Right = {tileBaseX + effWidth, tileBaseY + effHeight / 2}; int[] base_Bottom = {tileBaseX + effWidth / 2, tileBaseY + effHeight};
        int[] base_Bottom_Left = {base_Left[0], base_Left[1] + effBaseThickness}; int[] base_Bottom_Right = {base_Right[0], base_Right[1] + effBaseThickness};
        int[] base_Bottom_Bottom = {base_Bottom[0], base_Bottom[1] + effBaseThickness};

        glColor4f(baseSide1Color[0], baseSide1Color[1], baseSide1Color[2], baseSide1Color[3]); glBegin(GL_QUADS);
        glVertex2f(base_Left[0], base_Left[1]); glVertex2f(base_Bottom_Left[0], base_Bottom_Left[1]); glVertex2f(base_Bottom_Bottom[0], base_Bottom_Bottom[1]); glVertex2f(base_Bottom[0], base_Bottom[1]); glEnd();
        glColor4f(baseSide2Color[0], baseSide2Color[1], baseSide2Color[2], baseSide2Color[3]); glBegin(GL_QUADS);
        glVertex2f(base_Right[0], base_Right[1]); glVertex2f(base_Bottom_Right[0], base_Bottom_Right[1]); glVertex2f(base_Bottom_Bottom[0], base_Bottom_Bottom[1]); glVertex2f(base_Bottom[0], base_Bottom[1]); glEnd();
        glColor4f(baseTopColor[0], baseTopColor[1], baseTopColor[2], baseTopColor[3]); glBegin(GL_QUADS);
        glVertex2f(base_Left[0], base_Left[1]); glVertex2f(base_Top[0], base_Top[1]); glVertex2f(base_Right[0], base_Right[1]); glVertex2f(base_Bottom[0], base_Bottom[1]); glEnd();

        if (!isWater && elevation > 0) {
            int topElevationOffset = elevation * effThickness; int topSurfaceY = tileBaseY - topElevationOffset;
            int[] final_Top = {tileBaseX + effWidth / 2, topSurfaceY}; int[] final_Left = {tileBaseX, topSurfaceY + effHeight / 2};
            int[] final_Right = {tileBaseX + effWidth, topSurfaceY + effHeight / 2}; int[] final_Bottom = {tileBaseX + effWidth / 2, topSurfaceY + effHeight};
            int groundElevationOffset = 0; int groundSurfaceY = tileBaseY - groundElevationOffset;
            int[] ground_Left = {tileBaseX, groundSurfaceY + effHeight / 2}; int[] ground_Right = {tileBaseX + effWidth, groundSurfaceY + effHeight / 2};
            int[] ground_Bottom = {tileBaseX + effWidth / 2, groundSurfaceY + effHeight};

            glColor4f(side1Color[0], side1Color[1], side1Color[2], side1Color[3]); glBegin(GL_QUADS);
            glVertex2f(ground_Left[0], ground_Left[1]); glVertex2f(ground_Bottom[0], ground_Bottom[1]); glVertex2f(final_Bottom[0], final_Bottom[1]); glVertex2f(final_Left[0], final_Left[1]); glEnd();
            glColor4f(side2Color[0], side2Color[1], side2Color[2], side2Color[3]); glBegin(GL_QUADS);
            glVertex2f(ground_Right[0], ground_Right[1]); glVertex2f(ground_Bottom[0], ground_Bottom[1]); glVertex2f(final_Bottom[0], final_Bottom[1]); glVertex2f(final_Right[0], final_Right[1]); glEnd();
            glColor4f(topColor[0], topColor[1], topColor[2], topColor[3]); glBegin(GL_QUADS);
            glVertex2f(final_Left[0], final_Left[1]); glVertex2f(final_Top[0], final_Top[1]); glVertex2f(final_Right[0], final_Right[1]); glVertex2f(final_Bottom[0], final_Bottom[1]); glEnd();
        }
    }


    private void renderPlayer() {
        if (playerTexture == null) {
            System.err.println("Player texture not loaded, cannot render player sprite.");
            // Optionally call a simplified immediate mode draw here for fallback
            // renderPlayerImmediateMode(); // You would need to create/keep this method
            return;
        }

        // --- Calculate player's base screen position FIRST ---
        int playerTileRow = player.getTileRow();
        int playerTileCol = player.getTileCol();
        Tile currentTile = map.getTile(playerTileRow, playerTileCol);
        int playerBaseElevation = (currentTile != null) ? currentTile.getElevation() : 0;

        // This gets the player's logical position on the screen at the top surface of the tile they are on
        int[] playerScreenBaseCoords = camera.mapToScreenCoords(player.getMapCol(), player.getMapRow(), playerBaseElevation);
        float screenX = playerScreenBaseCoords[0]; // Now screenX is defined
        float screenY = playerScreenBaseCoords[1]; // Now screenY is defined

        // --- Now proceed with texture rendering ---
        glEnable(GL_TEXTURE_2D);
        playerTexture.bind();
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f); // Ensure no tinting and full alpha for texture

        // Animation information from PlayerModel
        int frameWidthPx = PlayerModel.FRAME_WIDTH;   // e.g., 64
        int frameHeightPx = PlayerModel.FRAME_HEIGHT; // e.g., 64

        int animationFrameColumn = player.getVisualFrameIndex(); // This is the current frame/column
        int animationSheetRow = player.getAnimationRow();       // This gets the correct ROW for current action/direction

        // Calculate pixel coordinates of the current frame's top-left corner on the spritesheet
        float framePixelXOnSheet = animationFrameColumn * frameWidthPx;
        float framePixelYOnSheet = animationSheetRow * frameHeightPx;

        float totalSheetWidthPx = playerTexture.getWidth();
        float totalSheetHeightPx = playerTexture.getHeight();

        // Calculate normalized UV coordinates for the specific frame
        float u0 = framePixelXOnSheet / totalSheetWidthPx;
        float v0 = framePixelYOnSheet / totalSheetHeightPx;
        float u1 = (framePixelXOnSheet + frameWidthPx) / totalSheetWidthPx;
        float v1 = (framePixelYOnSheet + frameHeightPx) / totalSheetHeightPx;

        // Define sprite screen draw dimensions (scaled by camera zoom)
        float spriteDrawWidth = frameWidthPx * camera.getZoom();
        float spriteDrawHeight = frameHeightPx * camera.getZoom();

        // Calculate drawing position on screen. Anchor point: bottom-center of sprite.
        float drawX = screenX - (spriteDrawWidth / 2.0f); // Uses screenX
        float drawY = screenY - spriteDrawHeight;         // Uses screenY

        // Apply levitation offset if active (adjusts Y upwards)
        if (player.isLevitating()) {
            drawY -= (int) (Math.sin(player.getLevitateTimer()) * 8 * camera.getZoom());
        }

        glBegin(GL_QUADS);
        // Assuming STB loads textures with (0,0) at top-left, or you've used stbi_set_flip_vertically_on_load(true)
        glTexCoord2f(u0, v0); glVertex2f(drawX, drawY);                                   // Top-left vertex, Top-left UV
        glTexCoord2f(u1, v0); glVertex2f(drawX + spriteDrawWidth, drawY);                  // Top-right vertex, Top-right UV
        glTexCoord2f(u1, v1); glVertex2f(drawX + spriteDrawWidth, drawY + spriteDrawHeight); // Bottom-right vertex, Bottom-right UV
        glTexCoord2f(u0, v1); glVertex2f(drawX, drawY + spriteDrawHeight);                 // Bottom-left vertex, Bottom-left UV
        glEnd();

        playerTexture.unbind();
        glDisable(GL_TEXTURE_2D);
    }
    // renderUI method remains the same.
    // For brevity, I'm omitting it here but ensure it's present in your file.
    private void renderUI() {
        glMatrixMode(GL_MODELVIEW); glLoadIdentity(); // Reset for UI
        int yPos = 20; int yInc = 18;
        Tile selectedTile = map.getTile(inputHandler.getSelectedRow(), inputHandler.getSelectedCol());
        String selectedInfo = "Selected: (" + inputHandler.getSelectedRow() + ", " + inputHandler.getSelectedCol() + ")";
        if (selectedTile != null) { selectedInfo += " Elev: " + selectedTile.getElevation() + " Type: " + selectedTile.getType(); }
        drawText(10, yPos, "Player: (" + player.getTileRow() + ", " + player.getTileCol() + ") Act: " + player.getCurrentAction() + " Dir: " + player.getCurrentDirection() + " F:" + player.getVisualFrameIndex()); yPos += yInc;
        drawText(10, yPos, selectedInfo); yPos += yInc;
        drawText(10, yPos, String.format("Camera: (%.1f, %.1f) Zoom: %.2f", camera.getCameraX(), camera.getCameraY(), camera.getZoom())); yPos += yInc;
        drawText(10, yPos, "Move: WASD | Sel: Mouse | Elev Sel +/-: Q/E"); yPos += yInc;
        drawText(10, yPos, "Levitate: F | Center Cam: C | Regen Map: G"); yPos += yInc;
    }

    // drawText method remains the same.
    private void drawText(int x, int y, String text) {
        int charWidth = 8; int charHeight = 15; int textWidth = text.length() * charWidth;
        glColor4f(0.1f, 0.1f, 0.1f, 0.6f); glBegin(GL_QUADS);
        glVertex2f(x - 2, y - 2); glVertex2f(x + textWidth + 2, y - 2); glVertex2f(x + textWidth + 2, y + charHeight + 2); glVertex2f(x - 2, y + charHeight + 2); glEnd();
        glColor4f(1.0f, 1.0f, 1.0f, 0.8f); glBegin(GL_LINES);
        glVertex2f(x, y + charHeight / 2); glVertex2f(x+ textWidth, y + charHeight / 2); glEnd();
    }

    public void cleanup() {
        System.out.println("Renderer cleanup...");
        if (playerTexture != null) {
            playerTexture.delete();
        }
    }
}