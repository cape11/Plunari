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

    public CameraManager(int initialScreenWidthPx, int initialScreenHeightPx, int mapTotalWidthTiles, int mapTotalHeightTiles) {
        this.screenWidthPx = initialScreenWidthPx;
        this.screenHeightPx = initialScreenHeightPx;

        // Initial camera position (center of the map in TILE coordinates)
        this.cameraX = mapTotalWidthTiles / 2.0f;
        this.cameraY = mapTotalHeightTiles / 2.0f;
        this.targetX = this.cameraX;
        this.targetY = this.cameraY;

        this.viewMatrix = new Matrix4f();
        forceUpdateViewMatrix(); // Calculate initial view matrix
    }

    public void update(double deltaTime) {
        boolean needsViewMatrixUpdate = false;
        // Adjust smoothing factor based on deltaTime
        float smoothFactor = (float) (CAMERA_SMOOTH_FACTOR * deltaTime * 60.0); // Assuming factor tuned for 60fps
        smoothFactor = Math.min(1.0f, smoothFactor); // Clamp

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

        if (Math.abs(targetZoom - zoom) > 0.001f) { // Smaller threshold for zoom
            zoom += (targetZoom - zoom) * smoothFactor;
            if (Math.abs(targetZoom - zoom) < 0.001f) zoom = targetZoom;
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.zoom)); // Clamp zoom
            needsViewMatrixUpdate = true;
        }

        if (needsViewMatrixUpdate) {
            viewMatrixDirty = true;
        }
    }

    private void updateViewMatrix() {
        viewMatrix.identity();

        // The view matrix effectively does the following:
        // 1. Translates the "world" so that the point (cameraX_world, cameraY_world) is at the origin.
        // 2. Applies the isometric projection (to skew the world).
        // 3. Scales the result by zoom.
        // 4. Translates the result so the origin (which is now the camera's focus) is at the screen center.

        // Center the final view on screen
        viewMatrix.translate(screenWidthPx / 2.0f, screenHeightPx / 2.0f, 0);

        // Apply zoom (scales everything around the current screen center)
        viewMatrix.scale(this.zoom);

        // Convert camera's map tile coordinates (cameraX, cameraY) to its corresponding
        // "world" coordinate if the map origin (0,0) was at world origin (0,0).
        // This is the point in the world that we want to be at the center of our zoomed view.
        float camWorldX = (this.cameraX - this.cameraY) * (TILE_WIDTH / 2.0f);
        float camWorldY = (this.cameraX + this.cameraY) * (TILE_HEIGHT / 2.0f);

        // Translate the world so that camWorldX, camWorldY is now at the origin of the current scaled view.
        // This makes camWorldX, camWorldY the "center" of what's being viewed before it's shifted to screen center.
        viewMatrix.translate(-camWorldX, -camWorldY, 0);

        viewMatrixDirty = false;
        // System.out.println("ViewMatrix updated: cam(" + cameraX + "," + cameraY + "), zoom " + zoom);
    }

    public void forceUpdateViewMatrix() { // Call this after screen resize or explicit camera set
        viewMatrixDirty = true;
        getViewMatrix(); // Ensure it's updated immediately
    }

    public Matrix4f getViewMatrix() {
        if (viewMatrixDirty) {
            updateViewMatrix();
        }
        return this.viewMatrix;
    }

    // --- Getters ---
    public float getCameraX() { return cameraX; }
    public float getCameraY() { return cameraY; }
    public float getZoom() { return zoom; }
    public int getScreenWidth() { return screenWidthPx; }
    public int getScreenHeight() { return screenHeightPx; }

    // Effective dimensions for sprites (which are scaled directly, not via view matrix yet)
    public float getEffectiveTileWidth() { return TILE_WIDTH * zoom; }
    public float getEffectiveTileHeight() { return TILE_HEIGHT * zoom; }
    public float getEffectiveTileThickness() { return TILE_THICKNESS * zoom; }

    // --- Setters for camera control ---
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
            viewMatrixDirty = true; // View matrix depends on screen center
        }
    }

    // --- Coordinate Transformation for Mouse Picking (CPU-side) ---
    // This calculates the final screen position of a tile considering current camera state.
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
        int mapW = gameMap.getWidth(); int mapH = gameMap.getHeight();
        int pickedC = -1, pickedR = -1;

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
    private final Matrix4f projectionMatrixForCulling = new Matrix4f(); // Store projection matrix from Renderer
    private final FrustumIntersection frustumIntersection = new FrustumIntersection();

    public void setProjectionMatrixForCulling(Matrix4f projMatrix) {
        this.projectionMatrixForCulling.set(projMatrix);
    }

    public boolean isAABBVisible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        // Update frustum culler with current combined projection-view matrix
        // The view matrix is already inverted (camera transform).
        // The projection matrix is set by onResize in Renderer.
        // Renderer.getProjectionMatrix() * this.getViewMatrix()
        Matrix4f projViewMatrix = new Matrix4f(projectionMatrixForCulling).mul(getViewMatrix());
        frustumIntersection.set(projViewMatrix);

        // Test AABB (Axis-Aligned Bounding Box)
        // For 2.5D isometric, Z might be tricky or simplified.
        // Let's assume minZ and maxZ define the "depth" or height range in world units.
        // For simple flat culling based on X,Y footprint:
        // return frustumIntersection.testAab(minX, minY, -1.0f, maxX, maxY, 1.0f); // Assuming Z is not critical for culling chunks on a plane

        // If your TILE_THICKNESS and BASE_THICKNESS translate to world depth:
        // minZ could be -MAX_ELEVATION * TILE_THICKNESS - BASE_THICKNESS (lowest point)
        // maxZ could be 0 (highest point if elevation is negative Y offset)
        // This needs careful definition of your world coordinate Z.
        // For now, let's use a generic Z range that should encompass your geometry.
        return frustumIntersection.testAab(minX, minY, -ALTURA_MAXIMA * TILE_THICKNESS * 2.0f,
                maxX, maxY, TILE_HEIGHT); // A generous Z range
    }

    public boolean isChunkVisible(Chunk.BoundingBox chunkBounds) {
        if (chunkBounds == null) return true; // Failsafe, or handle error
        return isAABBVisible(chunkBounds.minX, chunkBounds.minY, -ALTURA_MAXIMA * TILE_THICKNESS - BASE_THICKNESS,
                chunkBounds.maxX, chunkBounds.maxY, TILE_HEIGHT); // Use a Z range based on constants
    }
}