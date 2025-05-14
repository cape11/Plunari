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
        System.out.printf("--- ACCURATE PICK (Drawing Order, Last Hit) --- Mouse Screen (X:%d, Y:%d)%n", mouseScreenX, mouseScreenY);

        int effTileWidth = getEffectiveTileWidth();
        int effTileHeight = getEffectiveTileHeight();
        int mapW = gameMap.getWidth();
        int mapH = gameMap.getHeight();

        int pickedC = -1;
        int pickedR = -1;
        int pickedElevation = -Integer.MIN_VALUE; // For logging the picked tile's elevation

        // Iterate in your standard map drawing order (back-most to front-most)
        // Your drawing order is for (sum = 0 to MAX_SUM), then for (r = 0 to sum), c = sum - r.
        for (int sum = 0; sum <= mapW + mapH - 2; sum++) {
            for (int r = 0; r <= sum; r++) {
                int c = sum - r;

                if (!gameMap.isValid(r, c)) {
                    continue;
                }

                Tile tile = gameMap.getTile(r, c);
                if (tile == null) {
                    continue;
                }
                // Optional: Skip certain unselectable tiles
                // if (tile.getType() == Tile.TileType.WATER && tile.getElevation() < NIVEL_MAR) continue;

                int elevation = tile.getElevation();
                int[] tileScreenCoords = this.mapToScreenCoords((float)c, (float)r, elevation);
                float topX = tileScreenCoords[0];
                float topY = tileScreenCoords[1];

            /*
            // Optional Debug: Print info for tiles being checked
            if (Math.abs(topX - mouseScreenX) < effTileWidth * 1.5 && Math.abs(topY + effTileHeight/2.0f - mouseScreenY) < effTileHeight * 1.5) {
                 System.out.printf("    Checking Tile (C:%d, R:%d, E:%d) -> ScreenTop(X:%.0f, Y:%.0f)%n", c, r, elevation, topX, topY);
            }
            */

                if (isPointInDiamond((float)mouseScreenX, (float)mouseScreenY,
                        topX, topY,
                        effTileWidth, effTileHeight)) {
                    // This tile's diamond contains the mouse. Since we iterate in drawing order,
                    // this one is potentially "on top" of previous hits.
                    // The last one that satisfies this condition will be the topmost.
                    // System.out.printf("    >>>> HIT DIAMOND for Tile (C:%d, R:%d, E:%d) <<<<%n", c, r, elevation);
                    pickedC = c;
                    pickedR = r;
                    pickedElevation = elevation; // Store for logging
                }
            }
        }

        if (pickedR != -1) { // Check if any tile was picked
            System.out.printf("--- ACCURATE PICK END --- Selected (Col:%d, Row:%d, Elev:%d) by drawing order/last hit%n", pickedC, pickedR, pickedElevation);
            return new int[]{pickedC, pickedR};
        } else {
            // If still no tile selected, try the base plane guess as a last resort if you want
            // For debugging, it's better to see when this happens.
            float[] baseGuess = screenToMapCoords_BasePlaneOnly(mouseScreenX, mouseScreenY);
            System.out.printf("--- ACCURATE PICK END --- No tile selected (iterated all). Base guess was (C:%.0f, R:%.0f)%n", baseGuess[0], baseGuess[1]);
            return null;
        }
    }

    // The isPointInDiamond, mapToScreenCoords, screenToMapCoords_BasePlaneOnly methods
// remain the same as in the previous "best method" attempt.
// Ensure they are present and correct.
// For example:
    // In CameraManager.java

    // THIS METHOD IS THE ONE TO CHANGE FOR PICKING LOGIC
    private boolean isPointInDiamond(float px, float py,
                                     float diamondAnchorX_from_mapToScreenCoords, // This is the X from mapToScreenCoords
                                     float diamondAnchorY_from_mapToScreenCoords, // This is the Y from mapToScreenCoords
                                     int tileDiamondScreenWidth,
                                     int tileDiamondScreenHeight) {

        float diamondCenterX = diamondAnchorX_from_mapToScreenCoords; // Assume anchor X is center X
        // HYPOTHESIS: Assume diamondAnchorY_from_mapToScreenCoords IS THE CENTER Y
        float diamondCenterY = diamondAnchorY_from_mapToScreenCoords;
        // OLD (if diamondAnchorY was top tip): float diamondCenterY = diamondAnchorY_from_mapToScreenCoords + (tileDiamondScreenHeight / 2.0f);


        float diamondHalfWidth = tileDiamondScreenWidth / 2.0f;
        float diamondHalfHeight = tileDiamondScreenHeight / 2.0f;

        if (diamondHalfWidth <= 0 || diamondHalfHeight <= 0) return false;

        float normalizedMouseX = Math.abs((px - diamondCenterX) / diamondHalfWidth);
        float normalizedMouseY = Math.abs((py - diamondCenterY) / diamondHalfHeight);

        boolean hit = normalizedMouseX + normalizedMouseY <= 1.0f;

        // Optional: Add a temporary debug print here to see what's happening
        // if (Math.abs(px - 691) < 5 && Math.abs(py - 182) < 5) { // Example: mouse around your previous test click
        //     System.out.printf("isPointInDiamond check: mouse(%.0f,%.0f) vs diamondCenter(%.0f,%.0f) halfW:%.0f halfH:%.0f -> Hit: %b%n",
        //                       px, py, diamondCenterX, diamondCenterY, diamondHalfWidth, diamondHalfHeight, hit);
        // }

        return hit;
    }
// And ensure mapToScreenCoords and screenToMapCoords_BasePlaneOnly are correctly defined
// as per previous discussions.


    private double calculateVisualDepth(int c, int r, int elevation) {
        return elevation * 1000.0 + (r + c);
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