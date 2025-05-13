package org.isogame.camera;

import org.isogame.map.Map; // Assuming your game's Map class
import org.isogame.tile.Tile; // Assuming your Tile class
// import org.joml.Matrix4f; // Only if you were using this for modern GL projection

import static org.isogame.constants.Constants.*; // Make sure TILE_WIDTH, TILE_HEIGHT, TILE_THICKNESS are here

public class CameraManager {
    private float cameraX; // Map coordinate at the center of the screen
    private float cameraY; // Map coordinate at the center of the screen
    private float targetX;
    private float targetY;

    private float zoom = 1.0f;
    private float targetZoom = 1.0f;

    private int screenWidth;  // Framebuffer width in pixels
    private int screenHeight; // Framebuffer height in pixels

    private final int mapTileWidth;  // Total tiles wide for map (for potential clamping, not used here)
    private final int mapTileHeight; // Total tiles high for map

    public CameraManager(int initialScreenWidth, int initialScreenHeight, int mapTileWidth, int mapTileHeight) {
        this.screenWidth = initialScreenWidth;
        this.screenHeight = initialScreenHeight;
        this.mapTileWidth = mapTileWidth;
        this.mapTileHeight = mapTileHeight;

        this.cameraX = mapTileWidth / 2.0f;
        this.cameraY = mapTileHeight / 2.0f;
        this.targetX = cameraX;
        this.targetY = cameraY;
    }

    public void update(double deltaTime) {
        cameraX += (targetX - cameraX) * CAMERA_SMOOTH_FACTOR; // Simplified smoothing for example
        cameraY += (targetY - cameraY) * CAMERA_SMOOTH_FACTOR;
        zoom += (targetZoom - zoom) * CAMERA_SMOOTH_FACTOR;

        if (Math.abs(targetX - cameraX) < 0.01f) cameraX = targetX;
        if (Math.abs(targetY - cameraY) < 0.01f) cameraY = targetY;
        if (Math.abs(targetZoom - zoom) < 0.01f) zoom = targetZoom;
    }

    /**
     * Converts map tile coordinates (col, row) and elevation to screen pixel coordinates.
     * The (mapCol, mapRow) usually refers to the logical grid point.
     * The projection typically places the visual "top" corner of the diamond for (0,0,0)
     * at the screen center before camera offset.
     */

    public int[] mapToScreenCoords(float mapCol, float mapRow, int elevation) {
        float effTileScreenWidth = TILE_WIDTH * zoom;       // Full width of the diamond on screen
        float effTileScreenHeight = TILE_HEIGHT * zoom;     // Full height of the diamond on screen
        float effTileThicknessPerElevationUnit = TILE_THICKNESS * zoom;

        // Calculate position relative to the camera's focus point in the world
        float worldRelCol = mapCol - this.cameraX;
        float worldRelRow = mapRow - this.cameraY;

        // Standard isometric projection:
        // Screen X = (WorldCol - WorldRow) * (TileScreenWidth / 2)
        // Screen Y = (WorldCol + WorldRow) * (TileScreenHeight / 4) OR (WorldCol + WorldRow) * (TileScreenHeightPerStep)
        // where TileScreenHeightPerStep is typically TileScreenHeight / 2 (if TILE_HEIGHT is full diamond height)
        float screenIsoX = (worldRelCol - worldRelRow) * (effTileScreenWidth / 2.0f);
        float screenIsoY = (worldRelCol + worldRelRow) * (effTileScreenHeight / 2.0f); // This uses effTileScreenHeight / 2

        // Apply elevation offset (subtract because screen Y increases downwards)
        float elevationScreenOffset = elevation * effTileThicknessPerElevationUnit;

        int finalScreenX = this.screenWidth / 2 + (int) screenIsoX;
        int finalScreenY = this.screenHeight / 2 + (int) screenIsoY - (int) elevationScreenOffset;

        return new int[]{finalScreenX, finalScreenY};
    }

    /**
     * Converts screen pixel coordinates (pickScreenX, pickScreenY) to approximate map tile coordinates (col, row)
     * on the base plane (elevation 0). This is the inverse of the projection part of mapToScreenCoords.
     */
    public float[] screenToMapCoords(int pickScreenX, int pickScreenY) {
        float effTileScreenWidth = TILE_WIDTH * zoom;
        float effTileScreenHeight = TILE_HEIGHT * zoom; // Full visual height of the diamond

        // Screen coordinates relative to the center of the screen
        float screenRelativeX = pickScreenX - (this.screenWidth / 2.0f);
        float screenRelativeY = pickScreenY - (this.screenHeight / 2.0f);
        // Note: For accurate Z=0 picking, screenRelativeY should ideally be adjusted
        // if the general terrain level isn't at Z=0 screen height, but screenToAccurateMapTile handles elevation.

        // Define the scaling factors used in mapToScreenCoords' projection part
        float isoTileHalfWidthOnScreen = effTileScreenWidth / 2.0f;
        float isoTileHalfHeightOnScreen = effTileScreenHeight / 2.0f; // This corresponds to the Y scaling factor per map unit step

        // Reverse the projection:
        // screenRelativeX = (mapRelCol - mapRelRow) * isoTileHalfWidthOnScreen;
        // screenRelativeY = (mapRelCol + mapRelRow) * isoTileHalfHeightOnScreen;

        // Let termX = screenRelativeX / isoTileHalfWidthOnScreen
        // Let termY = screenRelativeY / isoTileHalfHeightOnScreen
        // mapRelCol - mapRelRow = termX
        // mapRelCol + mapRelRow = termY
        // Adding them: 2 * mapRelCol = termX + termY  => mapRelCol = (termX + termY) / 2
        // Subtracting first from second: 2 * mapRelRow = termY - termX => mapRelRow = (termY - termX) / 2

        float termX = screenRelativeX / isoTileHalfWidthOnScreen;
        float termY = screenRelativeY / isoTileHalfHeightOnScreen;

        float relativeMapCol = (termX + termY) / 2.0f;
        float relativeMapRow = (termY - termX) / 2.0f;

        // Add camera's current map position to get absolute map coordinates
        float finalMapCol = this.cameraX + relativeMapCol;
        float finalMapRow = this.cameraY + relativeMapRow;

        return new float[]{finalMapCol, finalMapRow};
    }

    /**
     * Iteratively finds the most likely tile under the mouse cursor, considering elevation.
     * @param mouseScreenX The mouse's X screen coordinate.
     * @param mouseScreenY The mouse's Y screen coordinate.
     * @param gameMap The game map to query for tile data.
     * @return int[]{column, row} of the picked tile.
     */
    public int[] screenToAccurateMapTile(int mouseScreenX, int mouseScreenY, Map gameMap) {
        float[] baseMapCoords = screenToMapCoords(mouseScreenX, mouseScreenY);
        int initialGuessR = Math.round(baseMapCoords[1]);
        int initialGuessC = Math.round(baseMapCoords[0]);

        int bestR = initialGuessR;
        int bestC = initialGuessC;
        boolean foundAccurateTile = false;
        int highestFoundElevation = -Integer.MAX_VALUE; // Start very low

        int searchRadius = 4; // Increased slightly, can be tuned

        // Iterate a search area. For isometric, the visual "top" can be tricky.
        // A common approach is to iterate in rendering order (back-to-front)
        // and pick the first hit, or iterate all and pick the highest valid hit.
        // This simplified loop checks a square area.
        for (int rOffset = -searchRadius; rOffset <= searchRadius; rOffset++) {
            for (int cOffset = -searchRadius; cOffset <= searchRadius; cOffset++) {
                int currentR = initialGuessR + rOffset;
                int currentC = initialGuessC + cOffset;

                if (!gameMap.isValid(currentR, currentC)) continue;

                Tile tile = gameMap.getTile(currentR, currentC);
                if (tile == null || tile.getType() == Tile.TileType.WATER) continue; // Usually can't select water this way

                int elevation = tile.getElevation();
                float effTileDrawWidth = getEffectiveTileWidth();
                float effTileDrawHeight = getEffectiveTileHeight();

                // Calculate the screen coordinates of the *top-most point* of this tile's diamond.
                // mapToScreenCoords gives the screen (X,Y) where the (mapCol, mapRow)
                // at the given elevation projects. This is usually the "tip" or "origin" of the tile's diamond.
                int[] topPointOfDiamondScreenCoords = mapToScreenCoords(currentC, currentR, elevation);
                float diamondTopScreenX = topPointOfDiamondScreenCoords[0]; // This is the diamond's horizontal center
                float diamondTopScreenY = topPointOfDiamondScreenCoords[1]; // This is the diamond's highest Y point

                // The visual center of the diamond (for hit-testing)
                float diamondCenterScreenX = diamondTopScreenX;
                float diamondCenterScreenY = diamondTopScreenY + (effTileDrawHeight / 2.0f);

                // Check if the mouse click is within this tile's top diamond
                // Diamond check: |(mx - dcx) / (diamondHalfWidth)| + |(my - dcy) / (diamondHalfHeight)| <= 1
                float diamondHalfWidth = effTileDrawWidth / 2.0f;
                float diamondHalfHeight = effTileDrawHeight / 2.0f;

                if (diamondHalfWidth == 0 || diamondHalfHeight == 0) continue; // Avoid division by zero

                float normalizedMouseX = Math.abs((mouseScreenX - diamondCenterScreenX) / diamondHalfWidth);
                float normalizedMouseY = Math.abs((mouseScreenY - diamondCenterScreenY) / diamondHalfHeight);

                if (normalizedMouseX + normalizedMouseY <= 1.0f) { // Mouse is within this tile's top diamond
                    if (!foundAccurateTile || elevation > highestFoundElevation) {
                        bestR = currentR;
                        bestC = currentC;
                        highestFoundElevation = elevation;
                        foundAccurateTile = true;
                    }
                }
            }
        }

        if (foundAccurateTile) {
            return new int[]{bestC, bestR};
        } else {
            // Fallback to the initial base plane guess if no clear elevated tile was hit
            // This might be what's happening if the diamond check is too strict or search radius too small
            // For debugging, print when this fallback occurs:
            // System.out.println("Accurate pick failed, falling back to base plane guess: " + initialGuessC + "," + initialGuessR);
            return new int[]{initialGuessC, initialGuessR};
        }
    }


    // Getters
    public float getCameraX() { return cameraX; }
    public float getCameraY() { return cameraY; }
    public float getZoom() { return zoom; }
    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }
    public int getEffectiveTileWidth() { return (int) (TILE_WIDTH * zoom); }
    public int getEffectiveTileHeight() { return (int) (TILE_HEIGHT * zoom); }
    public int getEffectiveTileThickness() { return (int) (TILE_THICKNESS * zoom); }
    public int getEffectiveBaseThickness() { return (int) (BASE_THICKNESS * zoom); }
    public void setTargetPositionInstantly(float mapX, float mapY) { /* ... */ this.targetX = mapX; this.targetY = mapY; this.cameraX = mapX; this.cameraY = mapY; }
    public void setTargetPosition(float mapX, float mapY) { /* ... */ this.targetX = mapX; this.targetY = mapY; }
    public void moveTargetPosition(float dMapX, float dMapY) { /* ... */ this.targetX += dMapX; this.targetY += dMapY; }
    public void adjustZoom(float amount) { /* ... */ this.targetZoom += amount; this.targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.targetZoom));}
    public void setZoom(float zoomLevel) { /* ... */ this.targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoomLevel));}
    public void updateScreenSize(int width, int height) { this.screenWidth = width; this.screenHeight = height; }
    public float[] screenVectorToMapVector(float screenDX, float screenDY) { // Unchanged
        float effTileWidth = TILE_WIDTH * zoom;
        float effTileHeight = TILE_HEIGHT * zoom;
        float mapDX = (screenDX / (effTileWidth / 2.0f) + screenDY / (effTileHeight / 2.0f)) / 2.0f;
        float mapDY = (screenDY / (effTileHeight / 2.0f) - screenDX / (effTileWidth / 2.0f)) / 2.0f;
        return new float[]{mapDX, mapDY};
    }
}