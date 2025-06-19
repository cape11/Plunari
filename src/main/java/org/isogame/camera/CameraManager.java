package org.isogame.camera;

import org.isogame.render.Chunk;
import org.joml.Matrix4f;
import static org.isogame.constants.Constants.*;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.joml.FrustumIntersection;

import java.util.ArrayList;
import java.util.List;

public class CameraManager {
    private float cameraX;
    private float cameraY;
    private float targetX;
    private float targetY;

    private float zoom = 1.0f;
    private float targetZoom = 1.0f;

    private int screenWidthPx;
    private int screenHeightPx;

    private final Matrix4f viewMatrix;
    private boolean viewMatrixDirty = true;

    private boolean isManuallyPanning = false;

    private final Matrix4f projectionMatrixForCulling = new Matrix4f();
    private final FrustumIntersection frustumIntersection = new FrustumIntersection();

    public CameraManager(int initialScreenWidthPx, int initialScreenHeightPx, int mapTotalWidthTiles, int mapTotalHeightTiles) {
        this.screenWidthPx = initialScreenWidthPx;
        this.screenHeightPx = initialScreenHeightPx;
        this.cameraX = 0;
        this.cameraY = 0;
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

    public void startManualPan() { this.isManuallyPanning = true; }
    public void stopManualPan() { this.isManuallyPanning = false; }
    public boolean isManuallyPanning() { return this.isManuallyPanning; }

    public float getCameraX() { return cameraX; }
    public float getCameraY() { return cameraY; }
    public float getZoom() { return zoom; }
    public int getScreenWidth() { return screenWidthPx; }
    public int getScreenHeight() { return screenHeightPx; }

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

    // vvvvvvvvvvvvvvvv  START OF NEW/CORRECTED CODE  vvvvvvvvvvvvvvvv

    /**
     * NEW HELPER METHOD
     * Converts mouse screen coordinates to the game's "world" coordinates,
     * accounting for camera panning and zoom. This is the inverse of the view matrix.
     */
    private float[] screenToWorldCoords(float mouseScreenX, float mouseScreenY) {
        // Inverse of the view matrix transformation
        // 1. Un-translate from screen center
        float worldX = mouseScreenX - (this.screenWidthPx / 2.0f);
        float worldY = mouseScreenY - (this.screenHeightPx / 2.0f);

        // 2. Un-scale by zoom
        worldX /= this.zoom;
        worldY /= this.zoom;

        // 3. Un-translate by camera's visual position
        float camVisualX = (this.cameraX - this.cameraY) * (TILE_WIDTH / 2.0f);
        float camVisualY = (this.cameraX + this.cameraY) * (TILE_HEIGHT / 2.0f);
        worldX += camVisualX;
        worldY += camVisualY;

        return new float[]{worldX, worldY};
    }

    /**
     * NEW HELPER METHOD
     * Converts "world" coordinates into fractional map tile coordinates (col, row).
     * This is the inverse of the isometric projection.
     */
    private float[] worldToMapCoords(float worldX, float worldY) {
        float tileHalfWidth = TILE_WIDTH / 2.0f;
        float tileHalfHeight = TILE_HEIGHT / 2.0f;

        // The inverse formula for isometric projection
        float mapCol = (worldX / tileHalfWidth + worldY / tileHalfHeight) / 2.0f;
        float mapRow = (worldY / tileHalfHeight - worldX / tileHalfWidth) / 2.0f;

        return new float[]{mapCol, mapRow};
    }

    /**
     * REPLACEMENT METHOD - The new pixel-perfect selection logic.
     */
    public int[] screenToAccurateMapTile(int mouseScreenX, int mouseScreenY, Map gameMap) {
        // Step 1: Directly calculate the map coordinate on the ground plane (elevation 0)
        float[] worldCoords = screenToWorldCoords(mouseScreenX, mouseScreenY);
        float[] mapCoords = worldToMapCoords(worldCoords[0], worldCoords[1]);

        int baseCol = Math.round(mapCoords[0]);
        int baseRow = Math.round(mapCoords[1]);

        // Step 2: Because of elevation, the actual tile could be near the calculated base tile.
        // We now perform a small, targeted search around this accurate point to check for higher tiles.
        int searchRadius = 15; // Increased radius to ensure it finds tiles at high/low elevation
        List<int[]> candidates = new ArrayList<>();

        for (int r = baseRow - searchRadius; r <= baseRow + searchRadius; r++) {
            for (int c = baseCol - searchRadius; c <= baseCol + searchRadius; c++) {
                Tile tile = gameMap.getTile(r, c);
                if (tile == null || tile.getType() == Tile.TileType.AIR) continue;

                // Check if the mouse is inside this tile's diamond, considering its elevation
                int[] tileScreenCenter = mapToScreenCoordsForPicking((float) c, (float) r, tile.getElevation());

                if (isPointInDiamond(mouseScreenX, mouseScreenY,
                        tileScreenCenter[0], tileScreenCenter[1],
                        TILE_WIDTH * this.zoom, TILE_HEIGHT * this.zoom)) {
                    candidates.add(new int[]{c, r, tile.getElevation()});
                }
            }
        }

        // Step 3: Sort the candidates to find the one that is truly on top
        if (!candidates.isEmpty()) {
            candidates.sort((tileA, tileB) -> {
                // Primary sort: Higher elevation comes first
                int elevationDiff = Integer.compare(tileB[2], tileA[2]);
                if (elevationDiff != 0) {
                    return elevationDiff;
                }
                // Secondary sort: "Closer" tiles (higher depth value) come first
                int depthA = tileA[1] + tileA[0]; // row + col
                int depthB = tileB[1] + tileB[0];
                return Integer.compare(depthB, depthA);
            });
            // The best candidate is the first one in the sorted list
            int[] bestCandidate = candidates.get(0);
            return new int[]{bestCandidate[0], bestCandidate[1]};
        }

        return null; // No tile found
    }

    // ^^^^^^^^^^^^^^^^  END OF NEW/CORRECTED CODE  ^^^^^^^^^^^^^^^^

    public float[] screenVectorToMapVector(float screenDX, float screenDY) {
        float currentZoom = this.zoom;
        if (Math.abs(currentZoom) < 0.0001f) return new float[]{0,0};

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
        return frustumIntersection.testAab(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean isChunkVisible(Chunk.BoundingBox chunkBounds) {
        if (chunkBounds == null) return true;
        float minWorldZ = -ALTURA_MAXIMA * TILE_THICKNESS - BASE_THICKNESS;
        float maxWorldZ = TILE_HEIGHT;
        return isAABBVisible(chunkBounds.minX, chunkBounds.minY, minWorldZ,
                chunkBounds.maxX, chunkBounds.maxY, maxWorldZ);
    }
}