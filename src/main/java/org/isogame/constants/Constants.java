package org.isogame.constants;

import org.isogame.render.Renderer;

public class Constants {
    // Tile dimensions
    public static final int TILE_WIDTH = 64;
    public static final int TILE_HEIGHT = 32;
    public static final int TILE_THICKNESS = 12;
    public static final int BASE_THICKNESS = 10;
    public static final float PLAYER_WORLD_RENDER_WIDTH = TILE_WIDTH * 0.75f;
    public static final float PLAYER_WORLD_RENDER_HEIGHT = TILE_HEIGHT * 1.25f;

    // Map dimensions
    public static final int MAP_WIDTH = 200;
    public static final int MAP_HEIGHT = 200;
    public static final int CHUNK_SIZE_TILES = 16;

    // Map generation
    public static final double NOISE_SCALE = 0.02;
    public static final int ALTURA_MAXIMA = 40;
    public static final int NIVEL_MAR = 9;
    public static final int NIVEL_ARENA = 10;
    public static final int NIVEL_ROCA = 30;
    public static final int NIVEL_NIEVE = 33;

    // Window size
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 720;

    // Rendering
    public static final float DEPTH_SORT_FACTOR = 0.1f;

    // Game Loop
    public static final double TARGET_FPS = 60.0;
    public static final double TARGET_TIME_PER_FRAME = 1.0 / TARGET_FPS;

    // Player
    public static final float PLAYER_MAP_GRID_SPEED = 4.0f;

    // Camera
    public static final float CAMERA_ZOOM_SPEED = 0.1f;
    public static final float MIN_ZOOM = 0.25f;
    public static final float MAX_ZOOM = 4.0f;
    public static final float CAMERA_SMOOTH_FACTOR = 0.15f;
    public static final int RENDER_DISTANCE_CHUNKS_DEFAULT = 4;
    public static final int RENDER_DISTANCE_CHUNKS_MIN = 1;
    public static final int RENDER_DISTANCE_CHUNKS_MAX = 10;

    // Interaction
    public static final int MAX_INTERACTION_DISTANCE = 2;

    // Lighting Constants
    public static final int MAX_LIGHT_LEVEL = 15;
    public static final int TORCH_LIGHT_LEVEL = 14;
    public static final int SKY_LIGHT_DAY = 15;
    public static final int SKY_LIGHT_NIGHT = 4;
    public static final int SKY_LIGHT_NIGHT_MINIMUM = 2; // Absolute minimum, affects ambient feel at night
    public static final int LIGHT_PROPAGATION_COST = 1;

    // In Chunk.java
    private static final int MAX_VERTICES_PER_TILE_COLUMN = (2*6) + (2*6) + (ALTURA_MAXIMA * 2 * 6); // Pedestal + Top + Max Sides
    private static final int MAX_EXPECTED_FLOATS_PER_CHUNK = CHUNK_SIZE_TILES * CHUNK_SIZE_TILES * MAX_VERTICES_PER_TILE_COLUMN * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED;

    // Inventory / Hotbar
    public static final int DEFAULT_INVENTORY_SIZE = 20;
    public static final int HOTBAR_SIZE = 5;

    // Optimization Constants
    public static final double DAY_NIGHT_CYCLE_SPEED = 0.005; // Slower for smoother visual lighting transitions
    public static final int SKY_LIGHT_UPDATE_THRESHOLD = 1;
}