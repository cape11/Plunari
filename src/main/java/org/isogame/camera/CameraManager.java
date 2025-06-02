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
        this.cameraX = mapTotalWidthTiles / 2.0f;
        this.cameraY = mapTotalHeightTiles / 2.0f;
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
        this.isManuallyPanning = false; // Snapping to a target usually means auto-follow should resume
        viewMatrixDirty = true;
    }

    public void setTargetPosition(float mapTileX, float mapTileY) {
        // This method is called by player-follow logic.
        // It should only update the target if the camera isn't being manually controlled.
        if (!this.isManuallyPanning) {
            this.targetX = mapTileX;
            this.targetY = mapTileY;
        }
    }

    public void moveTargetPosition(float deltaMapTileX, float deltaMapTileY) {
        // This method is called by manual drag panning.
        // It should always update the target. MouseHandler calls startManualPan() first.
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

    // --- Coordinate Transformation & Culling (using your existing methods) ---
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

    public int[] screenToAccurateMapTile(int mouseScreenX, int mouseScreenY, Map gameMap) {
        float currentZoom = this.zoom;
        float effTileRenderedWidth = TILE_WIDTH * currentZoom;
        float effTileRenderedHeight = TILE_HEIGHT * currentZoom;
        for (int r = 0; r < gameMap.getHeight(); r++) {
            for (int c = 0; c < gameMap.getWidth(); c++) {
                if (!gameMap.isValid(r, c)) continue;
                Tile tile = gameMap.getTile(r, c);
                if (tile == null) continue;
                int[] tileScreenCenter = mapToScreenCoordsForPicking((float)c, (float)r, tile.getElevation());
                if (isPointInDiamond(mouseScreenX, mouseScreenY,
                        tileScreenCenter[0], tileScreenCenter[1],
                        effTileRenderedWidth, effTileRenderedHeight)) {
                    return new int[]{c, r};
                }
            }
        }
        return null;
    }

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
        float maxWorldZ = TILE_HEIGHT; // Simplified max Z for chunk
        return isAABBVisible(chunkBounds.minX, chunkBounds.minY, minWorldZ,
                chunkBounds.maxX, chunkBounds.maxY, maxWorldZ);
    }
}