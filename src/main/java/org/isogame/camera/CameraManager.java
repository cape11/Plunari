package org.isogame.camera;

import org.isogame.render.Chunk; // This is org.isogame.render.Chunk
import org.joml.Matrix4f;
import static org.isogame.constants.Constants.*;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.joml.FrustumIntersection;

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

    private final Matrix4f projectionMatrixForCulling = new Matrix4f();
    private final FrustumIntersection frustumIntersection = new FrustumIntersection();
    private final Matrix4f projViewMatrixForCulling = new Matrix4f();
    private boolean frustumCullingMatrixDirty = true;


    public CameraManager(int initialScreenWidthPx, int initialScreenHeightPx, int conceptualMapWidthTiles, int conceptualMapHeightTiles) {
        this.screenWidthPx = initialScreenWidthPx;
        this.screenHeightPx = initialScreenHeightPx;

        this.cameraX = conceptualMapWidthTiles / 2.0f;
        this.cameraY = conceptualMapHeightTiles / 2.0f;
        this.targetX = this.cameraX;
        this.targetY = this.cameraY;

        this.viewMatrix = new Matrix4f();
        forceUpdateViewMatrix();
    }

    public void update(double deltaTime) {
        boolean needsViewMatrixUpdate = false;
        float smoothFactor = (float) (CAMERA_SMOOTH_FACTOR * deltaTime * 60.0);
        smoothFactor = Math.min(1.0f, smoothFactor);

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
            frustumCullingMatrixDirty = true;
        }
    }

    private void updateViewMatrix() {
        viewMatrix.identity();
        viewMatrix.translate(screenWidthPx / 2.0f, screenHeightPx / 2.0f, 0);
        viewMatrix.scale(this.zoom);
        float camWorldX = (this.cameraX - this.cameraY) * (TILE_WIDTH / 2.0f);
        float camWorldY = (this.cameraX + this.cameraY) * (TILE_HEIGHT / 2.0f);
        viewMatrix.translate(-camWorldX, -camWorldY, 0);
        viewMatrixDirty = false;
    }

    public void forceUpdateViewMatrix() {
        viewMatrixDirty = true;
        frustumCullingMatrixDirty = true;
        getViewMatrix();
    }

    public Matrix4f getViewMatrix() {
        if (viewMatrixDirty) {
            updateViewMatrix();
        }
        return this.viewMatrix;
    }

    public float getCameraX() { return cameraX; }
    public float getCameraY() { return cameraY; }
    public float getZoom() { return zoom; }
    public int getScreenWidth() { return screenWidthPx; }
    public int getScreenHeight() { return screenHeightPx; }

    public float getEffectiveTileWidth() { return TILE_WIDTH * zoom; }
    public float getEffectiveTileHeight() { return TILE_HEIGHT * zoom; }
    public float getEffectiveTileThickness() { return TILE_THICKNESS * zoom; }

    public void setTargetPositionInstantly(float mapX, float mapY) {
        this.targetX = mapX; this.targetY = mapY;
        this.cameraX = mapX; this.cameraY = mapY;
        viewMatrixDirty = true;
        frustumCullingMatrixDirty = true;
    }
    public void setTargetPosition(float mapX, float mapY) { this.targetX = mapX; this.targetY = mapY; }
    public void moveTargetPosition(float dMapX, float dMapY) { this.targetX += dMapX; this.targetY += dMapY; }
    public void adjustZoom(float amount) {
        this.targetZoom += amount;
        this.targetZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.targetZoom));
    }
    public void updateScreenSize(int widthPx, int heightPx) {
        if (this.screenWidthPx != widthPx || this.screenHeightPx != heightPx) {
            this.screenWidthPx = widthPx;
            this.screenHeightPx = heightPx;
            viewMatrixDirty = true;
            frustumCullingMatrixDirty = true;
        }
    }

    public int[] mapToScreenCoordsForPicking(float mapCol, float mapRow, int elevation) {
        float currentZoom = this.zoom;
        float effTileScreenWidth = TILE_WIDTH * currentZoom;
        float effTileScreenHeight = TILE_HEIGHT * currentZoom;
        float effTileThicknessPerElevationUnit = TILE_THICKNESS * currentZoom;

        float worldRelCol = mapCol - this.cameraX;
        float worldRelRow = mapRow - this.cameraY;

        float screenIsoX = (worldRelCol - worldRelRow) * (effTileScreenWidth / 2.0f);
        float screenIsoY = (worldRelCol + worldRelRow) * (effTileScreenHeight / 2.0f);
        float elevationScreenOffset = elevation * effTileThicknessPerElevationUnit;

        int finalScreenX = this.screenWidthPx / 2 + (int) screenIsoX;
        int finalScreenY = this.screenHeightPx / 2 + (int) screenIsoY - (int) elevationScreenOffset;
        return new int[]{finalScreenX, finalScreenY};
    }

    private boolean isPointInDiamond(float px, float py,
                                     float diamondCenterX, float diamondCenterY,
                                     float tileDiamondScreenWidth, float tileDiamondScreenHeight) {
        float diamondHalfWidth = tileDiamondScreenWidth / 2.0f;
        float diamondHalfHeight = tileDiamondScreenHeight / 2.0f;
        if (diamondHalfWidth <= 0 || diamondHalfHeight <= 0) return false;
        float normalizedMouseX = Math.abs(px - diamondCenterX) / diamondHalfWidth;
        float normalizedMouseY = Math.abs(py - diamondCenterY) / diamondHalfHeight;
        return normalizedMouseX + normalizedMouseY <= 1.0f;
    }

    public int[] screenToAccurateMapTile(int mouseScreenX, int mouseScreenY, Map gameMap) {
        float currentZoom = this.zoom;
        float effTileWidth = TILE_WIDTH * currentZoom;
        float effTileHeight = TILE_HEIGHT * currentZoom;

        int camTileX = Map.worldToChunkCoord((int)cameraX) * CHUNK_SIZE_TILES + CHUNK_SIZE_TILES / 2; // Center of camera's chunk
        int camTileY = Map.worldToChunkCoord((int)cameraY) * CHUNK_SIZE_TILES + CHUNK_SIZE_TILES / 2;

        // Estimate how many tiles could be relevant based on screen size and zoom
        int searchRadiusHorizontal = (int) (screenWidthPx / (effTileWidth * 0.5f) / 2.0f) + CHUNK_SIZE_TILES;
        int searchRadiusVertical = (int) (screenHeightPx / (effTileHeight * 0.5f) / 2.0f) + CHUNK_SIZE_TILES;
        int searchRadius = Math.max(searchRadiusHorizontal, searchRadiusVertical);


        int searchMinWorldTileR = camTileY - searchRadius;
        int searchMaxWorldTileR = camTileY + searchRadius;
        int searchMinWorldTileC = camTileX - searchRadius;
        int searchMaxWorldTileC = camTileX + searchRadius;

        int pickedC = -1, pickedR = -1;

        // Iterate tiles. A proper isometric pick would iterate in painter's order.
        // This simple iteration relies on isPointInDiamond and picking the last found.
        for (int r = searchMinWorldTileR; r < searchMaxWorldTileR; r++) {
            for (int c = searchMinWorldTileC; c < searchMaxWorldTileC; c++) {
                Tile tile = gameMap.getTile(r, c);
                if (tile == null) continue;

                int[] tileScreenCenter = mapToScreenCoordsForPicking((float)c, (float)r, tile.getElevation());

                if (isPointInDiamond(mouseScreenX, mouseScreenY,
                        tileScreenCenter[0], tileScreenCenter[1],
                        effTileWidth, effTileHeight)) {
                    pickedC = c;
                    pickedR = r;
                }
            }
        }
        if (pickedR != -1) return new int[]{pickedC, pickedR};
        return null;
    }


    public float[] screenVectorToMapVector(float screenDX, float screenDY) {
        float currentZoom = this.zoom;
        float termX = screenDX / (TILE_WIDTH / 2.0f * currentZoom);
        float termY = screenDY / (TILE_HEIGHT / 2.0f * currentZoom);
        float dMapCol = (termX + termY) / 2.0f;
        float dMapRow = (termY - termX) / 2.0f;
        return new float[]{dMapCol, dMapRow};
    }

    public void setProjectionMatrixForCulling(Matrix4f projMatrix) {
        this.projectionMatrixForCulling.set(projMatrix);
        this.frustumCullingMatrixDirty = true;
    }

    private void updateFrustumCullingMatrix() {
        if (frustumCullingMatrixDirty) {
            // Ensure viewMatrix is up-to-date before using it
            if (viewMatrixDirty) {
                updateViewMatrix();
            }
            projViewMatrixForCulling.set(projectionMatrixForCulling).mul(viewMatrix); // Use the member viewMatrix
            frustumIntersection.set(projViewMatrixForCulling);
            frustumCullingMatrixDirty = false;
        }
    }

    public boolean isAABBVisible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        updateFrustumCullingMatrix();
        return frustumIntersection.testAab(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean isChunkVisible(Chunk.BoundingBox chunkBounds) {
        if (chunkBounds == null) return true;
        float minChunkZ = -ALTURA_MAXIMA * TILE_THICKNESS - BASE_THICKNESS - TILE_HEIGHT * 2; // Adjusted for safety
        float maxChunkZ = TILE_HEIGHT * 2; // Adjusted for safety
        return isAABBVisible(chunkBounds.minX, chunkBounds.minY, minChunkZ,
                chunkBounds.maxX, chunkBounds.maxY, maxChunkZ);
    }
}
