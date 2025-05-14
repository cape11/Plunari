package org.isogame.camera;

import org.isogame.map.Map;
import org.isogame.tile.Tile;

import static org.isogame.constants.Constants.*;

public class CameraManager {
    private float cameraX;
    private float cameraY;
    private float targetX;
    private float targetY;

    private float zoom = 1.0f;
    private float targetZoom = 1.0f;

    private int screenWidth;
    private int screenHeight;

    // private final int mapTileWidth; // Not strictly needed if map object is passed around
    // private final int mapTileHeight;

    public CameraManager(int initialScreenWidth, int initialScreenHeight, int mapTileWidth, int mapTileHeight) {
        this.screenWidth = initialScreenWidth;
        this.screenHeight = initialScreenHeight;
        // this.mapTileWidth = mapTileWidth; // Store if needed for clamping camera, etc.
        // this.mapTileHeight = mapTileHeight;

        this.cameraX = mapTileWidth / 2.0f;
        this.cameraY = mapTileHeight / 2.0f;
        this.targetX = cameraX;
        this.targetY = cameraY;
    }

    public void update(double deltaTime) {
        cameraX += (targetX - cameraX) * CAMERA_SMOOTH_FACTOR;
        cameraY += (targetY - cameraY) * CAMERA_SMOOTH_FACTOR;
        zoom += (targetZoom - zoom) * CAMERA_SMOOTH_FACTOR;

        if (Math.abs(targetX - cameraX) < 0.01f) cameraX = targetX;
        if (Math.abs(targetY - cameraY) < 0.01f) cameraY = targetY;
        if (Math.abs(targetZoom - zoom) < 0.01f) zoom = targetZoom;
    }

    /**
     * Converts map tile coordinates (col, row) and elevation to screen pixel coordinates.
     * This is your existing method for converting a specific map point to screen.
     */
    public int[] mapToScreenCoords(float mapCol, float mapRow, int elevation) {
        float effTileScreenWidth = TILE_WIDTH * zoom;
        float effTileScreenHeight = TILE_HEIGHT * zoom;
        float effTileThicknessPerElevationUnit = TILE_THICKNESS * zoom;

        float worldRelCol = mapCol - this.cameraX;
        float worldRelRow = mapRow - this.cameraY;

        float screenIsoX = (worldRelCol - worldRelRow) * (effTileScreenWidth / 2.0f);
        float screenIsoY = (worldRelCol + worldRelRow) * (effTileScreenHeight / 2.0f);

        float elevationScreenOffset = elevation * effTileThicknessPerElevationUnit;

        int finalScreenX = this.screenWidth / 2 + (int) screenIsoX;
        int finalScreenY = this.screenHeight / 2 + (int) screenIsoY - (int) elevationScreenOffset;

        return new int[]{finalScreenX, finalScreenY};
    }

    /**
     * Converts screen pixel coordinates to approximate map tile coordinates (col, row)
     * on the base plane (elevation 0). Does NOT consider actual map tile elevations.
     * Used for an initial guess in accurate picking.
     */
    public float[] screenToMapCoords_BasePlaneOnly(int pickScreenX, int pickScreenY) {
        float effTileScreenWidth = TILE_WIDTH * zoom;
        float effTileScreenHeight = TILE_HEIGHT * zoom;

        float screenRelativeX = pickScreenX - (this.screenWidth / 2.0f);
        float screenRelativeY = pickScreenY - (this.screenHeight / 2.0f);

        float isoTileHalfWidthOnScreen = effTileScreenWidth / 2.0f;
        float isoTileHalfHeightOnScreen = effTileScreenHeight / 2.0f;

        float termX = screenRelativeX / isoTileHalfWidthOnScreen;
        float termY = screenRelativeY / isoTileHalfHeightOnScreen;

        float relativeMapCol = (termX + termY) / 2.0f;
        float relativeMapRow = (termY - termX) / 2.0f;

        float finalMapCol = this.cameraX + relativeMapCol;
        float finalMapRow = this.cameraY + relativeMapRow;

        return new float[]{finalMapCol, finalMapRow}; // Returns {mapCol, mapRow}
    }


    /**
     * Iteratively finds the most likely tile under the mouse cursor, considering elevation.
     * This is the method your Input/MouseHandler should call for accurate picking.
     */
    public int[] screenToAccurateMapTile(int mouseScreenX, int mouseScreenY, Map gameMap) {
        // Use the base plane conversion for an initial guess
        float[] baseMapCoords = screenToMapCoords_BasePlaneOnly(mouseScreenX, mouseScreenY);

        int initialGuessC = Math.round(baseMapCoords[0]); // col is index 0
        int initialGuessR = Math.round(baseMapCoords[1]); // row is index 1

        int bestC = -1, bestR = -1;
        double bestVisualDepth = -Double.MAX_VALUE;

        // int mapW = gameMap.getWidth(); // Not needed if using searchRadius
        // int mapH = gameMap.getHeight();
        int effTileWidth = getEffectiveTileWidth(); // Cached for use in isPointInDiamond
        int effTileHeight = getEffectiveTileHeight(); // Cached

        int searchRadius = 5; // How far around the initial guess to check. Adjust as needed.
        for (int rOffset = -searchRadius; rOffset <= searchRadius; rOffset++) {
            for (int cOffset = -searchRadius; cOffset <= searchRadius; cOffset++) {
                int r = initialGuessR + rOffset;
                int c = initialGuessC + cOffset;

                if (!gameMap.isValid(r, c)) {
                    continue;
                }

                Tile tile = gameMap.getTile(r, c);
                if (tile == null) {
                    continue;
                }
                // Consider if you want to be able to select water tiles:
                // if (tile.getType() == Tile.TileType.WATER && tile.getElevation() < NIVEL_MAR) continue;


                int elevation = tile.getElevation();
                // Use THE existing mapToScreenCoords method to get the tile's screen position
                int[] tileScreenCoords = this.mapToScreenCoords((float)c, (float)r, elevation);

                float topX = tileScreenCoords[0];
                float topY = tileScreenCoords[1];

                // Pass effTileWidth and effTileHeight to isPointInDiamond if it needs them
                if (isPointInDiamond((float)mouseScreenX, (float)mouseScreenY,
                        topX, topY,
                        effTileWidth, effTileHeight)) { // Simplified call
                    double currentVisualDepth = calculateVisualDepth(c, r, elevation);
                    if (currentVisualDepth > bestVisualDepth) {
                        bestVisualDepth = currentVisualDepth;
                        bestR = r;
                        bestC = c;
                    }
                }
            }
        }

        if (bestR != -1) {
            return new int[]{bestC, bestR}; // Return as {col, row}
        }
        return null;
    }

    private double calculateVisualDepth(int c, int r, int elevation) {
        return elevation * 1000.0 + (r + c);
    }

    // Helper method to check if a point is inside a diamond based on its top point and dimensions
    private boolean isPointInDiamond(float px, float py,
                                     float diamondTopScreenX, float diamondTopScreenY,
                                     int tileDiamondScreenWidth, int tileDiamondScreenHeight) {
        // Diamond center:
        float diamondCenterX = diamondTopScreenX;
        float diamondCenterY = diamondTopScreenY + (tileDiamondScreenHeight / 2.0f);

        float diamondHalfWidth = tileDiamondScreenWidth / 2.0f;
        float diamondHalfHeight = tileDiamondScreenHeight / 2.0f;

        if (diamondHalfWidth == 0 || diamondHalfHeight == 0) return false;

        float normalizedMouseX = Math.abs((px - diamondCenterX) / diamondHalfWidth);
        float normalizedMouseY = Math.abs((py - diamondCenterY) / diamondHalfHeight);

        return normalizedMouseX + normalizedMouseY <= 1.001f; // Add small epsilon
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

    public void setTargetPositionInstantly(float mapX, float mapY) { this.targetX = mapX; this.targetY = mapY; this.cameraX = mapX; this.cameraY = mapY; }
    public void setTargetPosition(float mapX, float mapY) { this.targetX = mapX; this.targetY = mapY; }
    public void moveTargetPosition(float dMapX, float dMapY) { this.targetX += dMapX; this.targetY += dMapY; }
    public void adjustZoom(float amount) { this.targetZoom += amount; this.targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.targetZoom));}
    public void setZoom(float zoomLevel) { this.targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoomLevel));}
    public void updateScreenSize(int width, int height) { this.screenWidth = width; this.screenHeight = height; }

    public float[] screenVectorToMapVector(float screenDX, float screenDY) {
        float effTileWidth = TILE_WIDTH * zoom;
        float effTileHeight = TILE_HEIGHT * zoom;
        float mapDX = (screenDX / (effTileWidth / 2.0f) + screenDY / (effTileHeight / 2.0f)) / 2.0f;
        float mapDY = (screenDY / (effTileHeight / 2.0f) - screenDX / (effTileWidth / 2.0f)) / 2.0f;
        return new float[]{mapDX, mapDY};
    }
}