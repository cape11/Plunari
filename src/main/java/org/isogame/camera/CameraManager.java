package org.isogame.camera;

import static org.isogame.constants.Constants.*;

public class CameraManager {
    // Camera position in tile coordinates (can be fractional for smooth movement)
    private float cameraX;
    private float cameraY;

    // Target position for smooth camera movement
    private float targetX;
    private float targetY;

    // Movement speed for smooth camera transitions
    private float moveSpeed = 0.1f;

    // Zoom level (1.0 is default)
    private float zoom = 1.0f;
    private float targetZoom = 1.0f;
    private float zoomSpeed = 0.05f;

    // Screen dimensions (will be updated when window resizes)
    private int screenWidth;
    private int screenHeight;

    public CameraManager(int initialScreenWidth, int initialScreenHeight) {
        this.screenWidth = initialScreenWidth;
        this.screenHeight = initialScreenHeight;

        // Initialize camera position to center of map
        this.cameraX = MAP_WIDTH / 2.0f;
        this.cameraY = MAP_HEIGHT / 2.0f;
        this.targetX = cameraX;
        this.targetY = cameraY;
    }

    /**
     * Update the camera position (smooth movement)
     */
    public void update() {
        // Move camera towards target position
        float dx = targetX - cameraX;
        float dy = targetY - cameraY;

        if (Math.abs(dx) > 0.01f) {
            cameraX += dx * moveSpeed;
        } else {
            cameraX = targetX;
        }

        if (Math.abs(dy) > 0.01f) {
            cameraY += dy * moveSpeed;
        } else {
            cameraY = targetY;
        }

        // Update zoom with smooth transition
        float dz = targetZoom - zoom;
        if (Math.abs(dz) > 0.01f) {
            zoom += dz * zoomSpeed;
        } else {
            zoom = targetZoom;
        }
    }

    /**
     * Set target camera position
     */
    public void setTargetPosition(float x, float y) {
        this.targetX = x;
        this.targetY = y;
    }

    /**
     * Move camera by the specified amount
     */
    public void moveCamera(float dx, float dy) {
        this.targetX += dx;
        this.targetY += dy;

        // Constrain camera to map boundaries with some margin
        float margin = 2.0f;
        this.targetX = Math.max(margin, Math.min(MAP_WIDTH - margin, this.targetX));
        this.targetY = Math.max(margin, Math.min(MAP_HEIGHT - margin, this.targetY));
    }

    /**
     * Set zoom level
     */
    public void setZoom(float zoom) {
        this.targetZoom = Math.max(0.5f, Math.min(2.0f, zoom));
    }

    /**
     * Adjust zoom by the specified amount
     */
    public void adjustZoom(float amount) {
        this.targetZoom = Math.max(0.5f, Math.min(2.0f, this.targetZoom + amount));
    }

    /**
     * Update screen dimensions when window is resized
     */
    public void updateScreenSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    /**
     * Convert map coordinates to screen coordinates
     */
    public int[] mapToScreenCoords(float mapX, float mapY, int elevation) {
        // Calculate the isometric position
        int tileScreenWidth = (int)(TILE_WIDTH * zoom);
        int tileScreenHeight = (int)(TILE_HEIGHT * zoom);

        // Calculate the center of the screen
        int screenCenterX = screenWidth / 2;
        int screenCenterY = screenHeight / 2;

        // Calculate the offset from the center based on camera position
        float offsetX = mapX - cameraX;
        float offsetY = mapY - cameraY;

        // Calculate the isometric offset
        int isoX = (int)((offsetX - offsetY) * tileScreenWidth / 2);
        int isoY = (int)((offsetX + offsetY) * tileScreenHeight / 2);

        // Adjust for elevation
        int elevationOffset = (int)(elevation * tileThickness * zoom);

        // Calculate final screen position
        int screenX = screenCenterX + isoX;
        int screenY = screenCenterY + isoY - elevationOffset;

        return new int[] {screenX, screenY};
    }

    /**
     * Convert screen coordinates to map coordinates
     */
    public float[] screenToMapCoords(int screenX, int screenY) {
        // Calculate the center of the screen
        int screenCenterX = screenWidth / 2;
        int screenCenterY = screenHeight / 2;

        // Calculate the offset from the center
        int offsetX = screenX - screenCenterX;
        int offsetY = screenY - screenCenterY;

        // Calculate the isometric offset
        float tileScreenWidth = TILE_WIDTH * zoom;
        float tileScreenHeight = TILE_HEIGHT * zoom;

        // Convert screen to isometric coordinates
        float isoX = (offsetX / (tileScreenWidth / 2) + offsetY / (tileScreenHeight / 2)) / 2;
        float isoY = (offsetY / (tileScreenHeight / 2) - offsetX / (tileScreenWidth / 2)) / 2;

        // Add camera position to get map coordinates
        float mapX = cameraX + isoX;
        float mapY = cameraY + isoY;

        return new float[] {mapX, mapY};
    }

    /**
     * Get effective tile width after zoom
     */
    public int getEffectiveTileWidth() {
        return (int)(TILE_WIDTH * zoom);
    }

    /**
     * Get effective tile height after zoom
     */
    public int getEffectiveTileHeight() {
        return (int)(TILE_HEIGHT * zoom);
    }

    /**
     * Get effective tile thickness after zoom
     */
    public int getEffectiveTileThickness() {
        return (int)(tileThickness * zoom);
    }

    // Getters
    public float getCameraX() { return cameraX; }
    public float getCameraY() { return cameraY; }
    public float getZoom() { return zoom; }
    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }
}
