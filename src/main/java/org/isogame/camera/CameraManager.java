package org.isogame.camera;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
// import org.joml.Matrix4f; // Keep if you have it for other reasons
import static org.isogame.constants.Constants.*;

public class CameraManager {
    // Camera position in Map Tile coordinates (center of view)
    private float cameraX;
    private float cameraY;

    // Target position for smooth camera movement
    private float targetX;
    private float targetY;

    // Zoom level
    private float zoom = 1.0f;
    private float targetZoom = 1.0f;

    // Screen dimensions
    private int screenWidth;
    private int screenHeight;

    // Map dimensions (to potentially constrain camera)
    private final int mapWidth;
    private final int mapHeight;

    public CameraManager(int initialScreenWidth, int initialScreenHeight, int mapWidth, int mapHeight) {
        this.screenWidth = initialScreenWidth;
        this.screenHeight = initialScreenHeight;
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;

        // Initialize camera to center of map
        this.cameraX = mapWidth / 2.0f;
        this.cameraY = mapHeight / 2.0f;
        this.targetX = cameraX;
        this.targetY = cameraY;
        this.zoom = 1.0f;
        this.targetZoom = 1.0f;
    }

    /**
     * Update camera position and zoom smoothly towards targets.
     * Call this once per frame.
     * @param deltaTime Time elapsed since the last frame.
     */
    public void update(double deltaTime) {
        // Smooth position interpolation (Lerp)
        cameraX += (targetX - cameraX) * CAMERA_SMOOTH_FACTOR * (float)deltaTime * 60.0f; // Normalize smoothing to 60fps baseline
        cameraY += (targetY - cameraY) * CAMERA_SMOOTH_FACTOR * (float)deltaTime * 60.0f;

        // Smooth zoom interpolation (Lerp)
        zoom += (targetZoom - zoom) * CAMERA_SMOOTH_FACTOR * (float)deltaTime * 60.0f;

        // Snap to target if very close to avoid drifting
        if (Math.abs(targetX - cameraX) < 0.01f) cameraX = targetX;
        if (Math.abs(targetY - cameraY) < 0.01f) cameraY = targetY;
        if (Math.abs(targetZoom - zoom) < 0.01f) zoom = targetZoom;
    }

    /** Sets the target position instantly and updates current position. */
    public void setTargetPositionInstantly(float mapX, float mapY) {
        this.targetX = mapX;
        this.targetY = mapY;
        this.cameraX = mapX;
        this.cameraY = mapY;
        constrainTargets();
    }

    /** Sets the target position for the camera to smoothly move towards. */
    public void setTargetPosition(float mapX, float mapY) {
        this.targetX = mapX;
        this.targetY = mapY;
        constrainTargets();
    }

    /** Moves the target position by a delta amount. */
    public void moveTargetPosition(float dMapX, float dMapY) {
        this.targetX += dMapX;
        this.targetY += dMapY;
        constrainTargets();
    }

    /** Constrains target position to keep view mostly within map bounds (optional) */
    private void constrainTargets() {
        // Basic clamping - more sophisticated bounds check might be needed depending on desired behavior at edges
        // float margin = 5.0f / zoom; // Example margin, adjusts with zoom
        // this.targetX = Math.max(margin, Math.min(mapWidth - margin, this.targetX));
        // this.targetY = Math.max(margin, Math.min(mapHeight - margin, this.targetY));
    }

    /** Adjusts the target zoom level by a relative amount. */
    public void adjustZoom(float amount) {
        this.targetZoom += amount;
        this.targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.targetZoom)); // Clamp zoom
    }

    /** Sets the absolute target zoom level. */
    public void setZoom(float zoomLevel) {
        this.targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoomLevel)); // Clamp zoom
    }

    /** Updates screen dimensions when window is resized. */
    public void updateScreenSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    // --- Coordinate Transformations ---
    // --- ADD OR VERIFY THIS METHOD ---
    public int[] screenToAccurateMapTile(int mouseScreenX, int mouseScreenY, Map gameMap) {
        // Initial guess using the base plane picking
        float[] baseMapCoords = screenToMapCoords(mouseScreenX, mouseScreenY); // Uses your existing method
        int initialGuessR = Math.round(baseMapCoords[1]);
        int initialGuessC = Math.round(baseMapCoords[0]);

        int bestR = initialGuessR;
        int bestC = initialGuessC;
        boolean foundAccurateTile = false;
        int highestFoundElevation = -Integer.MAX_VALUE; // Initialize to a very low number

        int searchRadius = 3;

        // Iterate in a way that tends to find visually "top" tiles first for tie-breaking
        // This can be complex; a simple grid search prioritizing higher elevation is often good enough.
        for (int rOffset = -searchRadius; rOffset <= searchRadius; rOffset++) {
            for (int cOffset = -searchRadius; cOffset <= searchRadius; cOffset++) {
                int currentR = initialGuessR + rOffset;
                int currentC = initialGuessC + cOffset;

                if (!gameMap.isValid(currentR, currentC)) {
                    continue;
                }

                Tile tile = gameMap.getTile(currentR, currentC);
                if (tile == null || tile.getType() == Tile.TileType.WATER) {
                    continue;
                }

                int elevation = tile.getElevation();
                int effTileDrawWidth = getEffectiveTileWidth();
                int effTileDrawHeight = getEffectiveTileHeight();

                int[] topPointScreenCoords = mapToScreenCoords(currentC, currentR, elevation);
                float diamondTopX = topPointScreenCoords[0];
                float diamondTopY = topPointScreenCoords[1];
                float diamondCenterY = diamondTopY + (effTileDrawHeight / 2.0f);

                float normX = Math.abs((mouseScreenX - diamondTopX) / (effTileDrawWidth / 2.0f));
                float normY = Math.abs((mouseScreenY - diamondCenterY) / (effTileDrawHeight / 2.0f));

                if (normX + normY <= 1.0f) { // Mouse is within this tile's top diamond
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
            return new int[]{bestC, bestR}; // Return col, row
        } else {
            return new int[]{initialGuessC, initialGuessR}; // Fallback
        }
    }

    /** Converts Map Tile coordinates (+ elevation) to Screen pixel coordinates. */
    public int[] mapToScreenCoords(float mapX, float mapY, int elevation) {
        // Effective tile dimensions based on zoom
        float effTileWidth = TILE_WIDTH * zoom;
        float effTileHeight = TILE_HEIGHT * zoom;
        float effTileThickness = TILE_THICKNESS * zoom;

        // Position relative to camera center (in tile units)
        float relativeX = mapX - cameraX;
        float relativeY = mapY - cameraY;

        // Isometric projection
        float isoX = (relativeX - relativeY) * (effTileWidth / 2.0f);
        float isoY = (relativeX + relativeY) * (effTileHeight / 2.0f);

        // Offset by elevation
        float elevationOffset = elevation * effTileThickness;

        // Final screen coordinates relative to screen center
        int screenX = screenWidth / 2 + (int) isoX;
        int screenY = screenHeight / 2 + (int) isoY - (int) elevationOffset; // Y grows downwards

        return new int[]{screenX, screenY};
    }

    /** Converts Screen pixel coordinates back to Map Tile coordinates (at elevation 0). */
    public float[] screenToMapCoords(int screenX, int screenY) {
        // Effective tile dimensions based on zoom
        float effTileWidth = TILE_WIDTH * zoom;
        float effTileHeight = TILE_HEIGHT * zoom;
        // float effTileThickness = TILE_THICKNESS * zoom; // Needed for 3D picking

        // Screen coordinates relative to screen center
        float screenRelativeX = screenX - (screenWidth / 2.0f);
        float screenRelativeY = screenY - (screenHeight / 2.0f);

        // Reverse the isometric projection (ignoring elevation for simple 2D picking)
        // isoX = (relX - relY) * w/2  =>  relX - relY = 2 * isoX / w
        // isoY = (relX + relY) * h/2  =>  relX + relY = 2 * isoY / h
        // Add equations: 2 * relX = (2*isoX/w) + (2*isoY/h) => relX = isoX/w + isoY/h
        // Sub equations: 2 * relY = (2*isoY/h) - (2*isoX/w) => relY = isoY/h - isoX/w

        // Note: screenRelativeX corresponds to isoX, screenRelativeY to isoY
        float relativeX = (screenRelativeX / effTileWidth) + (screenRelativeY / effTileHeight);
        float relativeY = (screenRelativeY / effTileHeight) - (screenRelativeX / effTileWidth);

        // Add camera position to get absolute map coordinates
        float mapX = cameraX + relativeX;
        float mapY = cameraY + relativeY;

        return new float[]{mapX, mapY};
    }

    /** Converts a vector in screen space (e.g., mouse drag) to a vector in map space. */
    public float[] screenVectorToMapVector(float screenDX, float screenDY) {
        // Similar inversion as screenToMapCoords, but for deltas (ignores camera position)
        float effTileWidth = TILE_WIDTH * zoom;
        float effTileHeight = TILE_HEIGHT * zoom;

        float mapDX = (screenDX / effTileWidth) + (screenDY / effTileHeight);
        float mapDY = (screenDY / effTileHeight) - (screenDX / effTileWidth);

        return new float[]{mapDX, mapDY};
    }


    // --- Getters for Renderer ---
    public float getCameraX() { return cameraX; }
    public float getCameraY() { return cameraY; }
    public float getZoom() { return zoom; }
    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }

    // Calculate effective dimensions based on current zoom
    public int getEffectiveTileWidth() { return (int) (TILE_WIDTH * zoom); }
    public int getEffectiveTileHeight() { return (int) (TILE_HEIGHT * zoom); }
    public int getEffectiveTileThickness() { return (int) (TILE_THICKNESS * zoom); }
    public int getEffectiveBaseThickness() { return (int) (BASE_THICKNESS * zoom); }
}