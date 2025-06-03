package org.isogame.camera;

import org.isogame.render.Chunk;
import org.joml.Matrix4f;
import static org.isogame.constants.Constants.*;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.joml.FrustumIntersection;

public class CameraManager {
    private float cameraX;          // Current camera center in MAP TILE coordinates
    private float cameraY;
    private float targetX;          // Target camera center for smooth movement
    private float targetY;

    private float zoom = 1.0f;
    private float targetZoom = 1.0f;

    private int screenWidthPx;
    private int screenHeightPx;

    private final Matrix4f viewMatrix;
    private boolean viewMatrixDirty = true;

    // --- Flag for manual panning state ---
    private boolean isManuallyPanning = false;

    private final Matrix4f projectionMatrixForCulling = new Matrix4f();
    private final FrustumIntersection frustumIntersection = new FrustumIntersection();

    public CameraManager(int initialScreenWidthPx, int initialScreenHeightPx, int mapTotalWidthTiles, int mapTotalHeightTiles) {
        this.screenWidthPx = initialScreenWidthPx;
        this.screenHeightPx = initialScreenHeightPx;
        // For an infinite map, initial camera position might be 0,0 or based on player spawn
        this.cameraX = 0; // mapTotalWidthTiles / 2.0f;
        this.cameraY = 0; // mapTotalHeightTiles / 2.0f;
        this.targetX = this.cameraX;
        this.targetY = this.cameraY;
        this.viewMatrix = new Matrix4f();
        forceUpdateViewMatrix();
    }

    public void update(double deltaTime) {
        boolean needsViewMatrixUpdate = false;
        float smoothFactor = (float) (CAMERA_SMOOTH_FACTOR * deltaTime * 60.0);
        smoothFactor = Math.min(1.0f, Math.max(0.0f, smoothFactor));

        if (Math.abs(targetX - cameraX) > 0.01f) {
            cameraX += (targetX - cameraX) * smoothFactor;
            if (Math.abs(targetX - cameraX) < 0.01f) cameraX = targetX;
            needsViewMatrixUpdate = true;
        }
        if (Math.abs(targetY - cameraY) > 0.01f) {
            cameraY += (targetY - cameraY) * smoothFactor;
            if (Math.abs(targetY - cameraY) < 0.01f) cameraY = targetY;
            needsViewMatrixUpdate = true;
        }
        if (Math.abs(targetZoom - zoom) > 0.001f) {
            zoom += (targetZoom - zoom) * smoothFactor;
            if (Math.abs(targetZoom - zoom) < 0.001f) zoom = targetZoom;
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.zoom));
            needsViewMatrixUpdate = true;
        }
        if (needsViewMatrixUpdate) {
            viewMatrixDirty = true;
        }
    }

    private void updateViewMatrix() {
        viewMatrix.identity();
        viewMatrix.translate(screenWidthPx / 2.0f, screenHeightPx / 2.0f, 0);
        viewMatrix.scale(this.zoom);
        float camVisualX = (this.cameraX - this.cameraY) * (TILE_WIDTH / 2.0f);
        float camVisualY = (this.cameraX + this.cameraY) * (TILE_HEIGHT / 2.0f);
        viewMatrix.translate(-camVisualX, -camVisualY, 0);
        viewMatrixDirty = false;
    }

    public void forceUpdateViewMatrix() {
        viewMatrixDirty = true;
        getViewMatrix();
    }

    public Matrix4f getViewMatrix() {
        if (viewMatrixDirty) {
            updateViewMatrix();
        }
        return this.viewMatrix;
    }

    // --- Manual Panning State Methods ---
    public void startManualPan() {
        this.isManuallyPanning = true;
    }

    public void stopManualPan() {
        this.isManuallyPanning = false;
    }

    public boolean isManuallyPanning() {
        return this.isManuallyPanning;
    }

    // --- Getters ---
    public float getCameraX() { return cameraX; }
    public float getCameraY() { return cameraY; }
    public float getZoom() { return zoom; }
    public int getScreenWidth() { return screenWidthPx; }
    public int getScreenHeight() { return screenHeightPx; }

    // --- Setters for camera control ---
    public void setTargetPositionInstantly(float mapTileX, float mapTileY) {
        this.targetX = mapTileX;
        this.targetY = mapTileY;
        this.cameraX = mapTileX;
        this.cameraY = mapTileY;
        this.isManuallyPanning = false;
        viewMatrixDirty = true;
    }

    public void setTargetPosition(float mapTileX, float mapTileY) {
        if (!this.isManuallyPanning) {
            this.targetX = mapTileX;
            this.targetY = mapTileY;
        }
    }

    public void moveTargetPosition(float deltaMapTileX, float deltaMapTileY) {
        this.targetX += deltaMapTileX;
        this.targetY += deltaMapTileY;
    }

    public void adjustZoom(float amount) {
        this.targetZoom += amount;
        this.targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.targetZoom));
    }

    public void updateScreenSize(int newWidthPx, int newHeightPx) {
        if (this.screenWidthPx != newWidthPx || this.screenHeightPx != newHeightPx) {
            this.screenWidthPx = newWidthPx;
            this.screenHeightPx = newHeightPx;
            viewMatrixDirty = true;
        }
    }

    public int[] mapToScreenCoordsForPicking(float mapCol, float mapRow, int elevation) {
        float worldX = (mapCol - mapRow) * (TILE_WIDTH / 2.0f);
        float worldY = (mapCol + mapRow) * (TILE_HEIGHT / 2.0f) - elevation * TILE_THICKNESS;
        org.joml.Vector4f tileScreenPos = new org.joml.Vector4f(worldX, worldY, 0, 1.0f);
        getViewMatrix().transform(tileScreenPos);
        return new int[]{(int)Math.round(tileScreenPos.x), (int)Math.round(tileScreenPos.y)};
    }

    private boolean isPointInDiamond(float px, float py,
                                     float diamondCenterX, float diamondCenterY,
                                     float tileDiamondRenderedWidth, float tileDiamondRenderedHeight) {
        float diamondHalfWidth = tileDiamondRenderedWidth / 2.0f;
        float diamondHalfHeight = tileDiamondRenderedHeight / 2.0f;
        if (diamondHalfWidth <= 0.001f || diamondHalfHeight <= 0.001f) return false;
        float localX = px - diamondCenterX;
        float localY = py - diamondCenterY;
        return (Math.abs(localX) / diamondHalfWidth) + (Math.abs(localY) / diamondHalfHeight) <= 1.001f;
    }

    /**
     * Converts screen coordinates to map tile coordinates.
     * WARNING: For an infinite map, iterating the entire map is not feasible.
     * This method needs to be refactored to search a limited area around the click
     * or use a reverse transformation.
     * The current fix is a temporary measure to make it compile by using a large fixed iteration range.
     */
    public int[] screenToAccurateMapTile(int mouseScreenX, int mouseScreenY, Map gameMap) {
        float currentZoom = this.zoom;
        float effTileRenderedWidth = TILE_WIDTH * currentZoom;
        float effTileRenderedHeight = TILE_HEIGHT * currentZoom;

        // Estimate map center based on camera to narrow down search
        // This is a simplified estimation. A more robust solution would involve unprojecting the mouse click.
        int camTileX = Math.round(cameraX);
        int camTileY = Math.round(cameraY);

        // Define a search radius in tiles around the camera center
        // This value needs to be large enough to cover the visible screen area.
        // It depends on zoom and screen resolution. (e.g., 50 tiles in each direction)
        // TODO: This search radius needs to be dynamically calculated or the whole approach rethought.
        int searchRadius = (int) (Math.max(screenWidthPx, screenHeightPx) / (Math.min(effTileRenderedWidth, effTileRenderedHeight)/2) + 2*CHUNK_SIZE_TILES) ;
        searchRadius = Math.max(searchRadius, CHUNK_SIZE_TILES * 3); // Ensure at least a few chunks are checked

        int startR = camTileY - searchRadius;
        int endR = camTileY + searchRadius;
        int startC = camTileX - searchRadius;
        int endC = camTileX + searchRadius;

        // Iterate in a spiral or distance-prioritized order for better performance if possible.
        // For now, simple rectangular iteration.
        for (int r = startR; r < endR; r++) {
            for (int c = startC; c < endC; c++) {
                // With an infinite map, gameMap.isValid() is no longer the primary check.
                // We rely on gameMap.getTile() to generate tile data if it's for a new area.
                Tile tile = gameMap.getTile(r, c); // This will generate if needed
                if (tile == null) continue; // Should not happen if getTile works correctly

                int[] tileScreenCenter = mapToScreenCoordsForPicking((float)c, (float)r, tile.getElevation());
                if (isPointInDiamond(mouseScreenX, mouseScreenY,
                        tileScreenCenter[0], tileScreenCenter[1],
                        effTileRenderedWidth, effTileRenderedHeight)) {
                    return new int[]{c, r};
                }
            }
        }
        return null; // No tile found at that screen position within the search area
    }


    public float[] screenVectorToMapVector(float screenDX, float screenDY) {
        float currentZoom = this.zoom;
        if (Math.abs(currentZoom) < 0.0001f) return new float[]{0,0};
        // Inverse of the transformation in updateViewMatrix for translation part
        // worldDX = (dMapCol - dMapRow) * TILE_WIDTH_HALF
        // worldDY = (dMapCol + dMapRow) * TILE_HEIGHT_HALF
        // Screen coordinates are scaled by zoom.
        // screenDX = worldDX * zoom; screenDY = worldDY * zoom
        // screenDX / zoom = (dMapCol - dMapRow) * TILE_WIDTH_HALF  (1)
        // screenDY / zoom = (dMapCol + dMapRow) * TILE_HEIGHT_HALF  (2)
        // Let term1 = (screenDX / zoom) / TILE_WIDTH_HALF  = dMapCol - dMapRow
        // Let term2 = (screenDY / zoom) / TILE_HEIGHT_HALF = dMapCol + dMapRow
        // term1 + term2 = 2 * dMapCol => dMapCol = (term1 + term2) / 2
        // term2 - term1 = 2 * dMapRow => dMapRow = (term2 - term1) / 2

        float term1 = screenDX / ( (TILE_WIDTH / 2.0f) * currentZoom );
        float term2 = screenDY / ( (TILE_HEIGHT / 2.0f) * currentZoom );

        float dMapCol = (term1 + term2) / 2.0f;
        float dMapRow = (term2 - term1) / 2.0f;

        return new float[]{dMapCol, dMapRow};
    }

    public void setProjectionMatrixForCulling(Matrix4f projMatrix) {
        this.projectionMatrixForCulling.set(projMatrix);
    }

    public boolean isAABBVisible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        Matrix4f projViewMatrix = new Matrix4f(projectionMatrixForCulling).mul(getViewMatrix());
        frustumIntersection.set(projViewMatrix);
        // Test against Axis-Aligned Bounding Box
        return frustumIntersection.testAab(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean isChunkVisible(Chunk.BoundingBox chunkBounds) {
        if (chunkBounds == null) return true; // If no bounds, assume visible or handle error
        // Z-bounds for chunks need to be representative of the world-space Z values.
        // For isometric, Z is often used for depth sorting rather than true 3D Z.
        // Here, we're using it for frustum culling in a 2D ortho projection, so Z range is important.
        float minWorldZ = -ALTURA_MAXIMA * TILE_THICKNESS - BASE_THICKNESS; // Lowest possible point
        float maxWorldZ = TILE_HEIGHT; // Highest possible point (top of a tile at elevation 0 on a pedestal)
        // This might need adjustment based on how you define your world Z.
        return isAABBVisible(chunkBounds.minX, chunkBounds.minY, minWorldZ,
                chunkBounds.maxX, chunkBounds.maxY, maxWorldZ);
    }
}