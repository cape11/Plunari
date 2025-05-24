package org.isogame.constants;

public class Constants {
    // Tile dimensions
    public static final int TILE_WIDTH = 64;
    public static final int TILE_HEIGHT = 32;
    public static final int TILE_THICKNESS = 12;
    public static final int BASE_THICKNESS = 10;
    public static final float PLAYER_WORLD_RENDER_WIDTH = TILE_WIDTH * 0.75f;
    public static final float PLAYER_WORLD_RENDER_HEIGHT = TILE_HEIGHT * 1.25f;

    // Map dimensions
    public static final int MAP_WIDTH = 500;
    public static final int MAP_HEIGHT = 500;
    public static final int CHUNK_SIZE_TILES = 16;

    // Map generation
    public static final double NOISE_SCALE = 0.10;
    public static final int ALTURA_MAXIMA = 35;
    public static final int NIVEL_MAR = 9;
    public static final int NIVEL_ARENA = 10;
    public static final int NIVEL_ROCA = 30;
    public static final int NIVEL_NIEVE = 33;

    // Window size
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    // Rendering
    public static final float DEPTH_SORT_FACTOR = 0.1f; // Affects Z separation based on row/col

    // Game Loop
    public static final double TARGET_FPS = 60.0;
    public static final double TARGET_TIME_PER_FRAME = 1.0 / TARGET_FPS;

    // Player
    public static final float PLAYER_MOVE_SPEED = 5.0f; // Tiles per second

    // Camera
    public static final float CAMERA_PAN_SPEED = 300.0f;
    public static final float CAMERA_ZOOM_SPEED = 0.1f;
    public static final float MIN_ZOOM = 0.25f;
    public static final float MAX_ZOOM = 4.0f;
    public static final float CAMERA_SMOOTH_FACTOR = 0.15f;

    // Resources
    public static final String RESOURCE_DIRT = "Dirt";
    public static final String RESOURCE_STONE = "Stone";
    public static final String RESOURCE_SAND = "Sand";

    // Interaction
    public static final int MAX_INTERACTION_DISTANCE = 1;

    // --- Lighting Constants ---
    public static final int MAX_LIGHT_LEVEL = 15;
    public static final int TORCH_LIGHT_LEVEL = 14;
    public static final int SKY_LIGHT_DAY = 15;
    public static final int SKY_LIGHT_NIGHT = 4;
    public static final int LIGHT_PROPAGATION_COST = 1;
    public static final float MIN_AMBIENT_LIGHT_FACTOR = 0.2f;
}
