package org.isogame.camera;

import org.isogame.render.Chunk;
import org.joml.Matrix4f;
import static org.isogame.constants.Constants.*;
import org.isogame.map.Map; // For screenToAccurateMapTile
import org.isogame.tile.Tile; // For screenToAccurateMapTile
import org.joml.FrustumIntersection; // For JOML's frustum culling

public class CameraManager {
    private float cameraX;          // Current camera center in MAP TILE coordinates
    private float cameraY;          // Current camera center in MAP TILE coordinates
    private float targetX;          // Target camera center for smooth movement
    private float targetY;

    private float zoom = 1.0f;      // Current zoom level
    private float targetZoom = 1.0f;

    private int screenWidthPx;      // Pixel width of the framebuffer
    private int screenHeightPx;     // Pixel height of the framebuffer

    private final Matrix4f viewMatrix;
    private boolean viewMatrixDirty = true; // Flag to rebuild view matrix when camera state changes

    private final Matrix4f projectionMatrixForCulling = new Matrix4f(); // Store projection matrix from Renderer
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
        }
    }

    private void updateViewMatrix() {
        viewMatrix.identity();

        // This is the original view matrix logic that worked with a screen-space ortho projection.
        // 1. Translate to screen center. This is where the camera's focus will appear.
        viewMatrix.translate(screenWidthPx / 2.0f, screenHeightPx / 2.0f, 0);

        // 2. Apply zoom around this screen center.
        viewMatrix.scale(this.zoom);

        // 3. Convert camera's map tile coordinates (cameraX, cameraY) to its corresponding
        //    "world" coordinate (which are already isometric due to how tile vertices are generated).
        float camWorldX = (this.cameraX - this.cameraY) * (TILE_WIDTH / 2.0f);
        float camWorldY = (this.cameraX + this.cameraY) * (TILE_HEIGHT / 2.0f);

        // 4. Translate the world so that camWorldX, camWorldY (the camera's focus in the world)
        //    is now at the origin of the current scaled view (which will then be shifted to screen center by step 1).
        viewMatrix.translate(-camWorldX, -camWorldY, 0);

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
        }
    }

    public int[] mapToScreenCoordsForPicking(float mapCol, float mapRow, int elevation) {
        // This calculation assumes the view matrix transforms world to screen-like coordinates
        // (scaled by zoom and centered).
        float currentZoom = this.zoom; // Use the actual current zoom
        float effTileScreenWidth = TILE_WIDTH * currentZoom;
        float effTileScreenHeight = TILE_HEIGHT * currentZoom;
        float effTileThicknessPerElevationUnit = TILE_THICKNESS * currentZoom;

        // Relative world coordinates from camera's focus point
        float worldRelCol = mapCol - this.cameraX;
        float worldRelRow = mapRow - this.cameraY;

        // Isometric projection part (same as before, but now relative to camera focus)
        float screenIsoX = (worldRelCol - worldRelRow) * (effTileScreenWidth / 2.0f);
        float screenIsoY = (worldRelCol + worldRelRow) * (effTileScreenHeight / 2.0f);

        // Elevation offset (screen space)
        float elevationScreenOffset = elevation * effTileThicknessPerElevationUnit;

        // Final screen coordinates: start from screen center, add iso coords, subtract elevation offset
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
        int mapW = gameMap.getWidth(); int mapH = gameMap.getHeight();
        int pickedC = -1, pickedR = -1;

        // This picking logic might need adjustment if the view matrix changes significantly,
        // but mapToScreenCoordsForPicking is now consistent with the restored view matrix.
        for (int sum = 0; sum <= mapW + mapH - 2; sum++) {
            for (int r = 0; r <= sum; r++) {
                int c = sum - r;
                if (!gameMap.isValid(r, c)) continue;
                Tile tile = gameMap.getTile(r, c);
                if (tile == null) continue;
                int[] tileScreenCenter = mapToScreenCoordsForPicking((float)c, (float)r, tile.getElevation());
                if (isPointInDiamond(mouseScreenX, mouseScreenY,
                        tileScreenCenter[0], tileScreenCenter[1],
                        effTileWidth, effTileHeight)) {
                    pickedC = c; pickedR = r;
                }
            }
        }
        if (pickedR != -1) return new int[]{pickedC, pickedR};
        return null;
    }

    public float[] screenVectorToMapVector(float screenDX, float screenDY) {
        float currentZoom = this.zoom;
        float d_mc_minus_mr = screenDX / (TILE_WIDTH / 2.0f * currentZoom);
        float d_mc_plus_mr  = screenDY / (TILE_HEIGHT / 2.0f * currentZoom);
        float dMapCol = (d_mc_minus_mr + d_mc_plus_mr) / 2.0f;
        float dMapRow = (d_mc_plus_mr - d_mc_minus_mr) / 2.0f;
        return new float[]{dMapCol, dMapRow};
    }

    public void setProjectionMatrixForCulling(Matrix4f projMatrix) {
        this.projectionMatrixForCulling.set(projMatrix);
    }

    public boolean isAABBVisible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        Matrix4f projViewMatrix = new Matrix4f(projectionMatrixForCulling).mul(getViewMatrix());
        frustumIntersection.set(projViewMatrix);
        return frustumIntersection.testAab(minX, minY, minZ, maxX, maxY, maxZ); // Use provided Z
    }

    public boolean isChunkVisible(Chunk.BoundingBox chunkBounds) {
        if (chunkBounds == null) return true;
        // The Z values for chunk culling should now align with the world Y values (our current Z for terrain)
        // and the projection matrix's zNear/zFar.
        return isAABBVisible(chunkBounds.minX, chunkBounds.minY, MIN_WORLD_Z_DEPTH, // Use consistent Z range
                chunkBounds.maxX, chunkBounds.maxY, MAX_WORLD_Z_DEPTH);
    }
}
