package org.isogame.constants;

public class Constants {
    // Tile dimensions (visual size on screen before zoom)
    public static final int TILE_WIDTH = 64;
    public static final int TILE_HEIGHT = 32; // TILE_WIDTH / 2 for standard isometric
    public static final int TILE_THICKNESS = 6; // Visual thickness per elevation unit
    public static final int BASE_THICKNESS = 10; // Visual thickness of the base under lowest land

    // Map dimensions
    public static final int MAP_WIDTH = 150; // Reduced size for faster generation/rendering initially
    public static final int MAP_HEIGHT = 150;

    // Map generation parameters
    public static final double NOISE_SCALE = 0.20; // Controls "zoom" level of noise pattern
    public static final int ALTURA_MAXIMA = 20; // Max possible elevation units

    // Terrain thresholds (elevation units)
    public static final int NIVEL_MAR = 5;   // Below this is water
    public static final int NIVEL_ARENA = 7; // Below this is sand
    public static final int NIVEL_ROCA = 18; // Below this is grass/dirt
    public static final int NIVEL_NIEVE = 22; // Below this is rock/stone, above is snow

    // Window size
    public static final int WIDTH = 1280; // Slightly larger default window
    public static final int HEIGHT = 720;

    // Game Loop
    public static final double TARGET_FPS = 60.0;
    public static final double TARGET_TIME_PER_FRAME = 1.0 / TARGET_FPS;

    // Player
    public static final float PLAYER_MOVE_SPEED = 5.0f; // Tiles per second

    // Camera
    public static final float CAMERA_PAN_SPEED = 300.0f; // Pixels per second for keyboard pan
    public static final float CAMERA_ZOOM_SPEED = 0.1f; // Amount per scroll tick
    public static final float MIN_ZOOM = 0.5f;
    public static final float MAX_ZOOM = 3.0f;
    public static final float CAMERA_SMOOTH_FACTOR = 0.1f; // For smooth following/movement (0-1)

}