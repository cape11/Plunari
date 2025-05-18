package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants; // Assuming your constants are here
import org.isogame.entitiy.PlayerModel;
import org.isogame.input.InputHandler;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.isogame.constants.Constants.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Renderer {
    private final CameraManager camera;
    private final Map map;
    private final PlayerModel player;
    private final InputHandler inputHandler; // Added if needed by chunk geometry updates
    private int frameCount = 0;

    private Texture playerTexture;
    private Texture treeTexture;
    private Font uiFont;
    private Random tileDetailRandom; // For random elements like grass tufts

    private List<Chunk> mapChunks;
    // CHUNK_SIZE_TILES should be defined in Constants, e.g., public static final int CHUNK_SIZE_TILES = 32;

    private Shader defaultShader;
    private Matrix4f projectionMatrix;
    private Matrix4f modelViewMatrixForSprites; // Orthographic, screen-space

    // VBO/VAO for dynamic sprites (player, trees, etc.)
    private int spriteVaoId = 0;
    private int spriteVboId = 0;
    private FloatBuffer spriteVertexBuffer; // Buffer for sprite vertex data

    // Vertex attribute counts
    public static final int FLOATS_PER_VERTEX_TEXTURED = 8; // x,y, r,g,b,a, u,v
    public static final int FLOATS_PER_VERTEX_COLORED = 6;  // x,y, r,g,b,a (if no texture)

    private Texture tileAtlasTexture; // The main texture atlas for tiles

    // --- Tile Atlas UV Coordinates ---
    // These MUST match your actual tile atlas layout (textu.png)
    // Example: If your atlas is 128x128 and sub-textures (tile faces) are 64x64
    private static final float ATLAS_TOTAL_WIDTH = 128.0f;
    private static final float ATLAS_TOTAL_HEIGHT = 128.0f;
    private static final float SUB_TEX_WIDTH = 64.0f;
    private static final float SUB_TEX_HEIGHT = 64.0f;

    // Top Textures
    private static final float GRASS_ATLAS_U0 = (0 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;
    private static final float GRASS_ATLAS_V0 = (0 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT;
    private static final float GRASS_ATLAS_U1 = (1 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;
    private static final float GRASS_ATLAS_V1 = (1 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT;

    private static final float ROCK_ATLAS_U0 = (0 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;
    private static final float ROCK_ATLAS_V0 = (1 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT; // Assuming Rock is below Grass
    private static final float ROCK_ATLAS_U1 = (1 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;
    private static final float ROCK_ATLAS_V1 = (2 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT;

    private static final float SAND_ATLAS_U0 = (1 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;   // Assuming Sand is to the right of Dirt
    private static final float SAND_ATLAS_V0 = (1 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT; // Assuming Sand is below Dirt
    private static final float SAND_ATLAS_U1 = (2 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;
    private static final float SAND_ATLAS_V1 = (2 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT;

    // Side Textures - DEFAULT_SIDE is your DIRT texture
    private static final float DEFAULT_SIDE_U0 = (1 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH; // U0 for DIRT (e.g., top-right in a 2x2 atlas)
    private static final float DEFAULT_SIDE_V0 = (0 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT; // V0 for DIRT
    private static final float DEFAULT_SIDE_U1 = (2 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH; // U1 for DIRT
    private static final float DEFAULT_SIDE_V1 = (1 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT; // V1 for DIRT

    // This factor controls how many times the side texture repeats vertically per TILE_THICKNESS unit.
    // 1.0f means the sub-texture (defined by V0 to V1 in atlas) stretches/compresses to fit one TILE_THICKNESS height.
    // Increase for more repetitions (denser/smaller texture pattern on sides).
    private static final float SIDE_TEXTURE_DENSITY_FACTOR = 1.0f;

    // Dummy UVs for untextured quads or when texture is missing
    private static final float DUMMY_U = 0.0f;
    private static final float DUMMY_V = 0.0f;


    // For sorting sprites (player, trees)
    private static class SortableItem {
        float screenYSortKey;
        int zOrder; // 0 for static like trees, 1 for dynamic like player
        Object entity;
        float mapRow, mapCol; // For tie-breaking

        // Constructor for Player
        public SortableItem(PlayerModel p, CameraManager cam, Map m) {
            this.entity = p;
            this.zOrder = 1; // Player is dynamic, typically rendered "on top" in case of exact Y sort key match
            this.mapRow = p.getMapRow();
            this.mapCol = p.getMapCol();
            Tile t = m.getTile(p.getTileRow(), p.getTileCol());
            int elev = (t != null) ? t.getElevation() : 0;
            // Use the player's feet position for Y-sorting
            int[] screenCoords = cam.mapToScreenCoordsForPicking(p.getMapCol(), p.getMapRow(), elev);
            this.screenYSortKey = screenCoords[1];
        }

        // Constructor for TreeData
        public SortableItem(TreeData tree, CameraManager cam, Map m) {
            this.entity = tree;
            this.zOrder = 0; // Trees are static
            this.mapRow = tree.mapRow;
            this.mapCol = tree.mapCol;
            // Use the tree's base/anchor position for Y-sorting
            int[] screenCoords = cam.mapToScreenCoordsForPicking(tree.mapCol, tree.mapRow, tree.elevation);
            this.screenYSortKey = screenCoords[1]; // Y-coordinate of the tile top where the tree is anchored
        }
    }

    private static class TreeData {
        Tile.TreeVisualType treeVisualType;
        float mapCol, mapRow;
        int elevation; // Elevation of the tile the tree is on
        float topDiamondCenterX_screen, topDiamondCenterY_screen; // Screen coords of the tile top center

        public TreeData(Tile.TreeVisualType type, float tc, float tr, int te, float screenAnchorX, float screenAnchorY) {
            this.treeVisualType = type;
            this.mapCol = tc;
            this.mapRow = tr;
            this.elevation = te;
            this.topDiamondCenterX_screen = screenAnchorX;
            this.topDiamondCenterY_screen = screenAnchorY;
        }
    }

    private List<SortableItem> sortableItems = new ArrayList<>();


    public Renderer(CameraManager camera, Map map, PlayerModel player, InputHandler inputHandler) {
        this.camera = camera;
        this.map = map;
        this.player = player;
        this.inputHandler = inputHandler; // Store for use in chunk updates if selection affects geometry
        this.tileDetailRandom = new Random();
        this.projectionMatrix = new Matrix4f();
        this.modelViewMatrixForSprites = new Matrix4f().identity(); // Simple identity or screen-space ortho

        System.out.println("Renderer: Initializing...");
        loadAssets();
        checkError("Renderer Constructor - After loadAssets()");
        initShaders();
        checkError("Renderer Constructor - After initShaders()");
        initRenderObjects(); // Initializes chunks and sprite VBO/VAO
        checkError("Renderer Constructor - After initRenderObjects()");
        uploadTileMapGeometry(); // Initial upload of all chunk geometries
        checkError("Renderer Constructor - After uploadTileMapGeometry()");
        System.out.println("Renderer: Initialization complete.");
    }

    private void loadAssets() {
        System.out.println("Renderer: Starting asset loading...");
        // Load Player Texture
        String playerTexturePath = "/org/isogame/render/textures/lpc_character.png"; // Ensure path is correct
        this.playerTexture = Texture.loadTexture(playerTexturePath);
        if (this.playerTexture == null) System.err.println("Renderer CRITICAL: Player texture FAILED to load: " + playerTexturePath);
        else System.out.println("Renderer: Player texture loaded: " + playerTexturePath + " ID: " + playerTexture.getId());
        checkError("Renderer: loadAssets - After Player Texture Load");

        // Load Tree Texture Atlas
        String treeTexturePath = "/org/isogame/render/textures/fruit-trees.png"; // Ensure path is correct
        this.treeTexture = Texture.loadTexture(treeTexturePath);
        if (this.treeTexture == null) System.err.println("Renderer CRITICAL: Tree texture FAILED to load: " + treeTexturePath);
        else System.out.println("Renderer: Tree texture loaded: " + treeTexturePath + " ID: " + treeTexture.getId());
        checkError("Renderer: loadAssets - After Tree Texture Load");

        // Load Tile Atlas Texture
        System.out.println("Renderer: Attempting to load tile atlas (textu.png)...");
        String tileAtlasPath = "/org/isogame/render/textures/textu.png"; // Ensure path is correct
        this.tileAtlasTexture = Texture.loadTexture(tileAtlasPath);
        if (this.tileAtlasTexture == null) System.err.println("Renderer CRITICAL: Tile Atlas (textu.png) FAILED to load: " + tileAtlasPath);
        else System.out.println("Renderer: Tile Atlas (textu.png) loaded: " + tileAtlasPath + " ID: " + tileAtlasTexture.getId() + " W: " + tileAtlasTexture.getWidth() + " H: " + tileAtlasTexture.getHeight());
        checkError("Renderer: loadAssets - After Tile Atlas Load");


        // Load Font
        String fontPath = "/org/isogame/render/fonts/PressStart2P-Regular.ttf"; // Ensure path is correct
        try {
            System.out.println("Renderer: Attempting to load font: " + fontPath);
            this.uiFont = new Font(fontPath, 16f, this); // Pass renderer instance if Font needs it
            if (this.uiFont.isInitialized()) {
                System.out.println("Renderer: Font asset loaded and initialized: " + fontPath + " (Font Tex ID: " + uiFont.getTextureID() + ")");
            } else {
                System.err.println("Renderer WARNING: Font object created, but uiFont.isInitialized() is false for: " + fontPath);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Renderer CRITICAL: Failed to load UI font: " + fontPath + " - " + e.getMessage());
            e.printStackTrace();
            this.uiFont = null; // Ensure uiFont is null if loading fails
        }
        checkError("Renderer: loadAssets - After Font Load Attempt");
        System.out.println("Renderer: Asset loading finished.");
    }

    private void initShaders() {
        try {
            defaultShader = new Shader();
            // Ensure shader paths are correct within your resources folder
            defaultShader.createVertexShader(Shader.loadResource("/org/isogame/render/shaders/vertex.glsl"));
            defaultShader.createFragmentShader(Shader.loadResource("/org/isogame/render/shaders/fragment.glsl"));
            defaultShader.link();
            checkError("Renderer: initShaders - After Shader Link");

            // Create all necessary uniforms
            defaultShader.createUniform("uProjectionMatrix");
            defaultShader.createUniform("uModelViewMatrix");
            defaultShader.createUniform("uTextureSampler");
            defaultShader.createUniform("uHasTexture");
            defaultShader.createUniform("uIsFont");
            defaultShader.createUniform("uSubTextureV0");
            defaultShader.createUniform("uSubTextureVSpan");
            defaultShader.createUniform("uApplySubTextureRepeat");

        } catch (IOException e) {
            System.err.println("Renderer CRITICAL: Error initializing shaders: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Renderer: Failed to initialize shaders", e);
        }
        System.out.println("Renderer: Shaders initialized.");
    }

    public Shader getDefaultShader() {
        return defaultShader;
    }

    private void initRenderObjects() {
        // Initialize Chunks
        mapChunks = new ArrayList<>();
        if (map != null && Constants.CHUNK_SIZE_TILES > 0) {
            int numChunksX = (int) Math.ceil((double) map.getWidth() / Constants.CHUNK_SIZE_TILES);
            int numChunksY = (int) Math.ceil((double) map.getHeight() / Constants.CHUNK_SIZE_TILES);
            for (int cy = 0; cy < numChunksY; cy++) {
                for (int cx = 0; cx < numChunksX; cx++) {
                    Chunk chunk = new Chunk(cx, cy, Constants.CHUNK_SIZE_TILES);
                    chunk.setupGLResources(); // Creates VAO and VBO for the chunk
                    mapChunks.add(chunk);
                }
            }
            System.out.println("Renderer: Initialized " + mapChunks.size() + " map chunk objects.");
        } else {
            System.err.println("Renderer: Map is null or CHUNK_SIZE_TILES is invalid during initRenderObjects. Cannot initialize chunks.");
        }

        // Initialize VAO/VBO for Sprites (Player, Trees, etc.)
        spriteVaoId = glGenVertexArrays();
        glBindVertexArray(spriteVaoId);

        spriteVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        // Allocate buffer for sprites (e.g., enough for player + a few trees, can be resized if needed or use a larger fixed size)
        // 1 player (6 verts) + ~10 trees (60 verts) = ~66 verts. 66 * 8 floats/vert = 528 floats.
        int initialSpriteBufferCapacity = 100 * 6 * FLOATS_PER_VERTEX_TEXTURED; // Enough for 100 quads
        spriteVertexBuffer = MemoryUtil.memAllocFloat(initialSpriteBufferCapacity);
        glBufferData(GL_ARRAY_BUFFER, (long) spriteVertexBuffer.capacity() * Float.BYTES, GL_DYNAMIC_DRAW); // Dynamic draw for sprites

        // Sprite Vertex Attributes (same as chunks: Pos, Color, TexCoord)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, FLOATS_PER_VERTEX_TEXTURED * Float.BYTES, 0L); // Position
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, FLOATS_PER_VERTEX_TEXTURED * Float.BYTES, 2 * Float.BYTES); // Color
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, FLOATS_PER_VERTEX_TEXTURED * Float.BYTES, (2 + 4) * Float.BYTES); // TexCoord
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        System.out.println("Renderer: Sprite VAO/VBO initialized.");
    }

    /**
     * Uploads the geometry for all map chunks. Called once at initialization
     * and potentially if the entire map structure changes.
     */
    public void uploadTileMapGeometry() {
        System.out.println("Renderer: Uploading initial tile map geometry for ALL chunks...");
        if (mapChunks == null || mapChunks.isEmpty() || map == null) {
            System.err.println("Renderer: Cannot upload tile map geometry - chunks or map not initialized.");
            return;
        }
        for (Chunk chunk : mapChunks) {
            // Pass `this` (Renderer instance) to chunk if its geometry methods need it
            chunk.uploadGeometry(map, inputHandler, this);
        }
        System.out.println("Renderer: All chunk geometries uploaded.");
    }

    /**
     * Updates the geometry for a specific chunk that contains the given tile coordinates.
     * Useful for when a tile's properties (like elevation) change.
     * @param tileRow The map row of the changed tile.
     * @param tileCol The map column of the changed tile.
     */
    public void updateChunkContainingTile(int tileRow, int tileCol) {
        if (map == null || mapChunks == null || mapChunks.isEmpty() || Constants.CHUNK_SIZE_TILES <= 0) {
            System.err.println("Renderer: Cannot update chunk, map or chunks not initialized, or CHUNK_SIZE_TILES invalid.");
            return;
        }
        int chunkGridX = tileCol / Constants.CHUNK_SIZE_TILES;
        int chunkGridY = tileRow / Constants.CHUNK_SIZE_TILES;

        Chunk targetChunk = null;
        for (Chunk chunk : mapChunks) {
            if (chunk.chunkGridX == chunkGridX && chunk.chunkGridY == chunkGridY) {
                targetChunk = chunk;
                break;
            }
        }

        if (targetChunk != null) {
            System.out.println("Renderer: Updating geometry for chunk: (" + chunkGridX + ", " + chunkGridY + ") due to change at tile (" + tileRow + "," + tileCol + ")");
            targetChunk.uploadGeometry(this.map, this.inputHandler, this); // Pass Renderer instance
        } else {
            System.err.println("Renderer: Could not find chunk for tile: (" + tileRow + ", " + tileCol + ") to update.");
        }
    }


    public void onResize(int fbWidth, int fbHeight) {
        if (fbWidth <= 0 || fbHeight <= 0) {
            System.err.println("Renderer: Invalid dimensions for onResize: " + fbWidth + "x" + fbHeight);
            return;
        }
        glViewport(0, 0, fbWidth, fbHeight);
        // Orthographic projection for UI and 2D sprites (maps screen pixels 1:1)
        projectionMatrix.identity().ortho(0, fbWidth, fbHeight, 0, -1, 1); // Standard 2D ortho for UI

        // The camera's view matrix will be combined with this projection matrix for map rendering
        // or the camera will use its own projection for the isometric view.
        // For now, this projection is primarily for screen-space elements.
        // The actual map rendering uses camera.getViewMatrix() and a projection matrix suitable for isometric view,
        // which might be different or this one if the camera view matrix incorporates the iso projection.

        if (camera != null) {
            // If the main projection matrix is also used for culling by the camera
            camera.setProjectionMatrixForCulling(projectionMatrix); // Update camera's culling matrix
            camera.forceUpdateViewMatrix(); // Screen size change affects view matrix if it centers content
        }
        System.out.println("Renderer: Screen resized to " + fbWidth + "x" + fbHeight + ". Projection matrix updated.");
        checkError("Renderer: onResize");
    }

    public int addSingleTileVerticesToBuffer_WorldSpace_ForChunk(
            int tileR, int tileC, Tile tile, boolean isSelected, FloatBuffer buffer, float[] chunkBoundsMinMax) {

        int elevation = tile.getElevation();
        Tile.TileType type = tile.getType();

        float halfW_unit = TILE_WIDTH / 2.0f;
        float halfH_unit = TILE_HEIGHT / 2.0f;
        float elevThick_worldY_offset = TILE_THICKNESS;

        float world_gc_x = (tileC - tileR) * halfW_unit;
        float world_gc_y_plane = (tileC + tileR) * halfH_unit;

        // Relative geometric offsets for diamond shape points from its current Y-level center
        float d_top_y_rel = -halfH_unit;
        float d_left_x_rel = -halfW_unit;   float d_left_y_rel = 0;
        float d_right_x_rel = halfW_unit;  float d_right_y_rel = 0;
        float d_bottom_y_rel = halfH_unit;

        float[] topClr = {1f,1f,1f,1f}; // Default white
        float[] bTopClr = {0.5f,0.5f,0.5f,1f}; // Default grey for base top
        boolean isW = (type == Tile.TileType.WATER);

        if (isSelected) {
            topClr = new float[]{1.0f, 0.8f, 0.0f, 0.8f};
            // Other selected colors (s1Clr, s2Clr, etc.) can be defined if needed for different faces
        } else {
            switch (type) {
                case WATER: topClr = new float[]{0.05f, 0.25f, 0.5f, 0.85f}; break; // Water color
                case SAND:  topClr = new float[]{0.82f,0.7f,0.55f,1f}; bTopClr = new float[]{0.59f,0.51f,0.35f,1f}; break;
                case GRASS: topClr = new float[]{0.20f,0.45f,0.10f,1f}; bTopClr = new float[]{0.35f,0.28f,0.18f,1f}; break;
                case ROCK:  topClr = new float[]{0.45f,0.45f,0.45f,1f}; bTopClr = new float[]{0.30f,0.30f,0.30f,1f}; break;
                case SNOW:  topClr = new float[]{0.95f,0.95f,1.0f,1f}; bTopClr = new float[]{0.5f,0.5f,0.55f,1f}; break;
                default:    topClr = new float[]{1f,0f,1f,1f}; bTopClr = topClr; break; // Magenta for undefined
            }
        }
        float[] whiteTint = {1.0f, 1.0f, 1.0f, 1.0f}; // For textured surfaces not to be overly tinted by selection color
        float[] sideTextureTintToUse = isSelected ? topClr : whiteTint;


        int verticesAdded = 0;

        // --- Pedestal Sides (from grid plane y=0 down by BASE_THICKNESS) ---
        float pTopDiamondCenterY = world_gc_y_plane;
        float pBottomDiamondCenterY = world_gc_y_plane + BASE_THICKNESS;

        float pTopLx_world = world_gc_x + d_left_x_rel;    float pTopLy_world = pTopDiamondCenterY + d_left_y_rel;
        float pTopRx_world = world_gc_x + d_right_x_rel;   float pTopRy_world = pTopDiamondCenterY + d_right_y_rel;
        float pTopBx_world = world_gc_x;                   float pTopBy_world = pTopDiamondCenterY + d_bottom_y_rel;

        float pBotLx_world = world_gc_x + d_left_x_rel;    float pBotLy_world = pBottomDiamondCenterY + d_left_y_rel;
        float pBotRx_world = world_gc_x + d_right_x_rel;   float pBotRy_world = pBottomDiamondCenterY + d_right_y_rel;
        float pBotBx_world = world_gc_x;                   float pBotBy_world = pBottomDiamondCenterY + d_bottom_y_rel;

        if (!isW) {
            float pedestalSideU0 = DEFAULT_SIDE_U0;
            float pedestalSideV0 = DEFAULT_SIDE_V0;
            float pedestalSideU1 = DEFAULT_SIDE_U1;
            float pedestalSideTexture_V_Span_InAtlas = DEFAULT_SIDE_V1 - DEFAULT_SIDE_V0;
            float pedestal_height_world = BASE_THICKNESS;
            float pedestal_v_repeats = (pedestal_height_world / TILE_THICKNESS) * SIDE_TEXTURE_DENSITY_FACTOR;
            float pedestal_v_bottom_coord = pedestalSideV0 + pedestalSideTexture_V_Span_InAtlas * pedestal_v_repeats;

            verticesAdded += addRectangularQuadWithTextureOrColor(buffer, pTopLx_world, pTopLy_world, pTopBx_world, pTopBy_world, pBotBx_world, pBotBy_world, pBotLx_world, pBotLy_world, sideTextureTintToUse, true, pedestalSideU0, pedestalSideV0, pedestalSideU1, pedestal_v_bottom_coord);
            verticesAdded += addRectangularQuadWithTextureOrColor(buffer, pTopBx_world, pTopBy_world, pTopRx_world, pTopRy_world, pBotRx_world, pBotRy_world, pBotBx_world, pBotBy_world, sideTextureTintToUse, true, pedestalSideU0, pedestalSideV0, pedestalSideU1, pedestal_v_bottom_coord);
        }

        // --- Top Surface of the tile ---
        float tileTopSurfaceY = world_gc_y_plane - (elevation * elevThick_worldY_offset);
        float topFace_Lx = world_gc_x + d_left_x_rel;  float topFace_Ly = tileTopSurfaceY + d_left_y_rel;
        float topFace_Rx = world_gc_x + d_right_x_rel; float topFace_Ry = tileTopSurfaceY + d_right_y_rel;
        float topFace_Tx = world_gc_x;                 float topFace_Ty = tileTopSurfaceY + d_top_y_rel;
        float topFace_Bx = world_gc_x;                 float topFace_By = tileTopSurfaceY + d_bottom_y_rel;

        if (isW) {
            verticesAdded += addRectangularQuadWithTextureOrColor(buffer, topFace_Lx, topFace_Ly, topFace_Tx, topFace_Ty, topFace_Rx, topFace_Ry, topFace_Bx, topFace_By, topClr, false, DUMMY_U, DUMMY_V, DUMMY_U, DUMMY_V);
        } else {
            float current_top_u0 = DUMMY_U, current_top_v0 = DUMMY_V, current_top_u1 = DUMMY_U, current_top_v1 = DUMMY_V;
            boolean textureThisTop = false;
            float[] finalTopColorToUse = isSelected ? topClr : whiteTint;

            if (type == Tile.TileType.GRASS) { current_top_u0=GRASS_ATLAS_U0; current_top_v0=GRASS_ATLAS_V0; current_top_u1=GRASS_ATLAS_U1; current_top_v1=GRASS_ATLAS_V1; textureThisTop=true; }
            else if (type == Tile.TileType.SAND) { current_top_u0=SAND_ATLAS_U0; current_top_v0=SAND_ATLAS_V0; current_top_u1=SAND_ATLAS_U1; current_top_v1=SAND_ATLAS_V1; textureThisTop=true; }
            else if (type == Tile.TileType.ROCK) { current_top_u0=ROCK_ATLAS_U0; current_top_v0=ROCK_ATLAS_V0; current_top_u1=ROCK_ATLAS_U1; current_top_v1=ROCK_ATLAS_V1; textureThisTop=true; }

            if (!textureThisTop || isSelected) {
                finalTopColorToUse = topClr; // Use the tile's actual color if not textured or if selected
            }

            if (textureThisTop) {
                float mid_u = (current_top_u0 + current_top_u1) / 2f;
                float mid_v = (current_top_v0 + current_top_v1) / 2f;
                buffer.put(topFace_Lx).put(topFace_Ly).put(finalTopColorToUse[0]).put(finalTopColorToUse[1]).put(finalTopColorToUse[2]).put(finalTopColorToUse[3]).put(current_top_u0).put(mid_v);
                buffer.put(topFace_Tx).put(topFace_Ty).put(finalTopColorToUse[0]).put(finalTopColorToUse[1]).put(finalTopColorToUse[2]).put(finalTopColorToUse[3]).put(mid_u).put(current_top_v0);
                buffer.put(topFace_Bx).put(topFace_By).put(finalTopColorToUse[0]).put(finalTopColorToUse[1]).put(finalTopColorToUse[2]).put(finalTopColorToUse[3]).put(mid_u).put(current_top_v1);
                verticesAdded += 3;
                buffer.put(topFace_Tx).put(topFace_Ty).put(finalTopColorToUse[0]).put(finalTopColorToUse[1]).put(finalTopColorToUse[2]).put(finalTopColorToUse[3]).put(mid_u).put(current_top_v0);
                buffer.put(topFace_Rx).put(topFace_Ry).put(finalTopColorToUse[0]).put(finalTopColorToUse[1]).put(finalTopColorToUse[2]).put(finalTopColorToUse[3]).put(current_top_u1).put(mid_v);
                buffer.put(topFace_Bx).put(topFace_By).put(finalTopColorToUse[0]).put(finalTopColorToUse[1]).put(finalTopColorToUse[2]).put(finalTopColorToUse[3]).put(mid_u).put(current_top_v1);
                verticesAdded += 3;
            } else {
                verticesAdded += addRectangularQuadWithTextureOrColor(buffer, topFace_Lx, topFace_Ly, topFace_Tx, topFace_Ty, topFace_Rx, topFace_Ry, topFace_Bx, topFace_By, finalTopColorToUse, false, DUMMY_U, DUMMY_V, DUMMY_U, DUMMY_V);
            }

            // --- MODIFIED: Elevated Block Sides (for the entire height of the elevation above ground plane) ---
            if (elevation > 0) {
                float elevSideU0, elevSideV0, elevSideU1, elevSideTexture_V_Span_InAtlas;

                switch (type) {
                    case GRASS:
                        elevSideU0 = DEFAULT_SIDE_U0; elevSideV0 = DEFAULT_SIDE_V0;
                        elevSideU1 = DEFAULT_SIDE_U1; elevSideTexture_V_Span_InAtlas = DEFAULT_SIDE_V1 - DEFAULT_SIDE_V0;
                        break;
                    case SAND:
                        elevSideU0 = SAND_ATLAS_U0; elevSideV0 = SAND_ATLAS_V0;
                        elevSideU1 = SAND_ATLAS_U1; elevSideTexture_V_Span_InAtlas = SAND_ATLAS_V1 - SAND_ATLAS_V0;
                        break;
                    case ROCK:
                        elevSideU0 = ROCK_ATLAS_U0; elevSideV0 = ROCK_ATLAS_V0;
                        elevSideU1 = ROCK_ATLAS_U1; elevSideTexture_V_Span_InAtlas = ROCK_ATLAS_V1 - ROCK_ATLAS_V0;
                        break;
                    case SNOW:
                    default:
                        elevSideU0 = DEFAULT_SIDE_U0; elevSideV0 = DEFAULT_SIDE_V0;
                        elevSideU1 = DEFAULT_SIDE_U1; elevSideTexture_V_Span_InAtlas = DEFAULT_SIDE_V1 - DEFAULT_SIDE_V0;
                        break;
                }

                float sideTexture_V_Top = elevSideV0;
                float sideTexture_V_Bottom_OneUnit = elevSideV0 + elevSideTexture_V_Span_InAtlas * SIDE_TEXTURE_DENSITY_FACTOR;

                for (int e_step = elevation; e_step >= 1; e_step--) {
                    float sliceTopCenterY = world_gc_y_plane - (e_step * elevThick_worldY_offset);
                    float sliceBottomCenterY = world_gc_y_plane - ((e_step - 1) * elevThick_worldY_offset);

                    float s_fLx = world_gc_x + d_left_x_rel;  float s_fLy = sliceTopCenterY + d_left_y_rel;
                    float s_fRx = world_gc_x + d_right_x_rel; float s_fRy = sliceTopCenterY + d_right_y_rel;
                    float s_fBx = world_gc_x;                 float s_fBy = sliceTopCenterY + d_bottom_y_rel;

                    float sb_fLx = world_gc_x + d_left_x_rel;  float sb_fLy = sliceBottomCenterY + d_left_y_rel;
                    float sb_fRx = world_gc_x + d_right_x_rel; float sb_fRy = sliceBottomCenterY + d_right_y_rel;
                    float sb_fBx = world_gc_x;                 float sb_fBy = sliceBottomCenterY + d_bottom_y_rel;

                    verticesAdded += addRectangularQuadWithTextureOrColor(buffer,
                            s_fLx, s_fLy, s_fBx, s_fBy, sb_fBx, sb_fBy, sb_fLx, sb_fLy,
                            sideTextureTintToUse, true,
                            elevSideU0, sideTexture_V_Top,
                            elevSideU1, sideTexture_V_Bottom_OneUnit);

                    verticesAdded += addRectangularQuadWithTextureOrColor(buffer,
                            s_fBx, s_fBy, s_fRx, s_fRy, sb_fRx, sb_fRy, sb_fBx, sb_fBy,
                            sideTextureTintToUse, true,
                            elevSideU0, sideTexture_V_Top,
                            elevSideU1, sideTexture_V_Bottom_OneUnit);
                }
            }
        }

        // --- Bounding box update ---
        float minTileX = pBotLx_world;
        float maxTileX = pBotRx_world;
        float minTileY = topFace_Ty;  // Y of the highest point of the top face
        float maxTileY = pBotBy_world; // Y of the lowest point of the pedestal

        if (verticesAdded > 0) {
            chunkBoundsMinMax[0] = Math.min(chunkBoundsMinMax[0], minTileX);
            chunkBoundsMinMax[1] = Math.min(chunkBoundsMinMax[1], minTileY);
            chunkBoundsMinMax[2] = Math.max(chunkBoundsMinMax[2], maxTileX);
            chunkBoundsMinMax[3] = Math.max(chunkBoundsMinMax[3], maxTileY);
        }
        return verticesAdded;
    }

    /**
     * Stub method for adding grass detail vertices. Currently does nothing.
     * This method is called by Chunk.java but was missing.
     * @param tileR Row of the tile.
     * @param tileC Column of the tile.
     * @param tile The tile object.
     * @param buffer FloatBuffer to add vertices to.
     * @param chunkBoundsMinMax Array to update with min/max world coordinates.
     * @return Number of vertices added (always 0 for this stub).
     */
    public int addGrassVerticesForTile_WorldSpace_ForChunk(
            int tileR, int tileC, Tile tile, FloatBuffer buffer, float[] chunkBoundsMinMax) {
        // This is a stub. Implement actual grass detail rendering here if desired.
        // For now, it does nothing and returns 0 vertices.
        // Example: if (Grass.java had a method to generate textured quads for grass)
        // return Grass.getThickGrassTuftsVertices(...);
        return 0;
    }

    private int addRectangularQuadWithTextureOrColor(FloatBuffer buffer,
                                                     float xTL, float yTL, float xTR, float yTR,
                                                     float xBR, float yBR, float xBL, float yBL,
                                                     float[] color,
                                                     boolean isTextured, float u0, float v0, float u1, float v1_tex) {
        float actual_u0, actual_v0, actual_u1_coord, actual_v1_coord;
        final float DUMMY_U_SHADER = 0.0f;
        final float DUMMY_V_SHADER = 0.0f;

        if (isTextured) {
            actual_u0 = u0; actual_v0 = v0;
            actual_u1_coord = u1; actual_v1_coord = v1_tex;
        } else {
            actual_u0 = DUMMY_U_SHADER; actual_v0 = DUMMY_V_SHADER;
            actual_u1_coord = DUMMY_U_SHADER; actual_v1_coord = DUMMY_V_SHADER;
        }
        // Triangle 1: TL, BL, TR
        buffer.put(xTL).put(yTL).put(color[0]).put(color[1]).put(color[2]).put(color[3]).put(actual_u0).put(actual_v0);
        buffer.put(xBL).put(yBL).put(color[0]).put(color[1]).put(color[2]).put(color[3]).put(actual_u0).put(actual_v1_coord);
        buffer.put(xTR).put(yTR).put(color[0]).put(color[1]).put(color[2]).put(color[3]).put(actual_u1_coord).put(actual_v0);
        // Triangle 2: TR, BL, BR
        buffer.put(xTR).put(yTR).put(color[0]).put(color[1]).put(color[2]).put(color[3]).put(actual_u1_coord).put(actual_v0);
        buffer.put(xBL).put(yBL).put(color[0]).put(color[1]).put(color[2]).put(color[3]).put(actual_u0).put(actual_v1_coord);
        buffer.put(xBR).put(yBR).put(color[0]).put(color[1]).put(color[2]).put(color[3]).put(actual_u1_coord).put(actual_v1_coord);
        return 6;
    }

    private void collectSortableItems() {
        sortableItems.clear();
        if (this.player != null && this.camera != null && this.map != null) {
            sortableItems.add(new SortableItem(this.player, this.camera, this.map));
        }

        if (map == null || camera == null) return;

        // Iterate through visible map area or a reasonable range around the player/camera
        // For simplicity, iterating all tiles for now, but culling would be better for performance.
        int mapW = map.getWidth();
        int mapH = map.getHeight();
        for (int r_loop = 0; r_loop < mapH; r_loop++) {
            for (int c_loop = 0; c_loop < mapW; c_loop++) {
                Tile tile = map.getTile(r_loop, c_loop);
                if (tile != null && tile.getTreeType() != Tile.TreeVisualType.NONE && tile.getType() != Tile.TileType.WATER) {
                    int elevation = tile.getElevation();
                    // Get screen coordinates of the tile's top center to use as an anchor for the tree sprite
                    int[] tileTopScreenCoords = camera.mapToScreenCoordsForPicking((float)c_loop, (float)r_loop, elevation);
                    sortableItems.add(new SortableItem(
                            new TreeData(tile.getTreeType(), (float)c_loop, (float)r_loop, elevation,
                                    tileTopScreenCoords[0], tileTopScreenCoords[1]),
                            this.camera, this.map));
                }
            }
        }
    }


    private int addPlayerVerticesToBuffer_ScreenSpace(PlayerModel p, FloatBuffer buffer) {
        if (playerTexture == null || playerTexture.getId() == 0 || camera == null || map == null) return 0;
        float r=1f,g=1f,b=1f,a=1f; // Player tint (white = no tint)

        Tile currentTile = map.getTile(p.getTileRow(), p.getTileCol());
        int playerElevationOnTile = (currentTile != null) ? currentTile.getElevation() : 0;

        // Get screen coordinates for player's map position
        int[] screenCoords = camera.mapToScreenCoordsForPicking(p.getMapCol(), p.getMapRow(), playerElevationOnTile);
        float screenX = screenCoords[0];
        float screenY = screenCoords[1];

        float currentZoom = camera.getZoom();
        float spritePixelWidth = PlayerModel.FRAME_WIDTH; // Width of one frame in the sprite sheet
        float spritePixelHeight = PlayerModel.FRAME_HEIGHT; // Height of one frame

        float scaledSpriteWidth = spritePixelWidth * currentZoom;
        float scaledSpriteHeight = spritePixelHeight * currentZoom;

        // Anchor point: typically bottom-center of the sprite aligns with player's map coordinates
        float drawX = screenX - scaledSpriteWidth / 2f;
        float drawY = screenY - scaledSpriteHeight; // Anchor at feet; adjust if sprite sheet has padding

        if(p.isLevitating()){ // Apply levitation offset
            drawY -= (Math.sin(p.getLevitateTimer()) * 8 * currentZoom);
        }

        // Texture coordinates from sprite sheet
        int animFrameColumn = p.getVisualFrameIndex();
        int animFrameRow = p.getAnimationRow(); // This method in PlayerModel should return the correct row index in the spritesheet

        float texU0 = (animFrameColumn * spritePixelWidth) / playerTexture.getWidth();
        float texV0 = (animFrameRow * spritePixelHeight) / playerTexture.getHeight();
        float texU1 = ((animFrameColumn + 1) * spritePixelWidth) / playerTexture.getWidth();
        float texV1 = ((animFrameRow + 1) * spritePixelHeight) / playerTexture.getHeight();

        return addRectangularQuadWithTextureOrColor(buffer,
                drawX, drawY,                                       // Top-Left
                drawX + scaledSpriteWidth, drawY,                   // Top-Right
                drawX + scaledSpriteWidth, drawY + scaledSpriteHeight, // Bottom-Right
                drawX, drawY + scaledSpriteHeight,                  // Bottom-Left
                new float[]{r,g,b,a}, true,
                texU0, texV0, texU1, texV1);
    }

    private int addTreeVerticesToBuffer_ScreenSpace(TreeData tree, FloatBuffer buffer) {
        if (treeTexture == null || treeTexture.getId() == 0 || tree.treeVisualType == Tile.TreeVisualType.NONE || camera == null) return 0;
        float r=1f,g=1f,b=1f,a=1f; // Tree tint

        // Define frame properties for each tree type from the tree atlas
        float frameW = 0, frameH = 0, atlasU0 = 0, atlasV0 = 0;
        float anchorOffsetY_pixels = 0; // How many pixels from bottom of frame is the anchor point

        switch(tree.treeVisualType){
            case APPLE_TREE_FRUITING:
                frameW=90; frameH=130; atlasU0=0; atlasV0=0; anchorOffsetY_pixels = 15; // Approx. base of trunk
                break;
            case PINE_TREE_SMALL:
                frameW=90; frameH=180; atlasU0=90; atlasV0=0; anchorOffsetY_pixels = 10; // Approx. base of trunk
                break;
            default: return 0; // Unknown tree type
        }

        float texU0 = atlasU0 / treeTexture.getWidth();
        float texV0 = atlasV0 / treeTexture.getHeight();
        float texU1 = (atlasU0 + frameW) / treeTexture.getWidth();
        float texV1 = (atlasV0 + frameH) / treeTexture.getHeight();

        float currentZoom = camera.getZoom();
        float scaledSpriteWidth = frameW * currentZoom;
        float scaledSpriteHeight = frameH * currentZoom;
        float scaledAnchorOffsetY = anchorOffsetY_pixels * currentZoom;

        // tree.topDiamondCenterX_screen and tree.topDiamondCenterY_screen are the center of the tile's top face
        // We want to place the tree sprite such that its anchor point (base of trunk) is at this screen position.
        float drawX = tree.topDiamondCenterX_screen - (scaledSpriteWidth / 2f); // Center the sprite horizontally
        float drawY = tree.topDiamondCenterY_screen - (scaledSpriteHeight - scaledAnchorOffsetY); // Position based on anchor

        return addRectangularQuadWithTextureOrColor(buffer,
                drawX, drawY,
                drawX + scaledSpriteWidth, drawY,
                drawX + scaledSpriteWidth, drawY + scaledSpriteHeight,
                drawX, drawY + scaledSpriteHeight,
                new float[]{r,g,b,a}, true,
                texU0, texV0, texU1, texV1);
    }


    public void render() {
        frameCount++;
        checkError("Render Start");

        defaultShader.bind();
        // Set projection matrix for map/world rendering (likely an isometric projection combined with camera view)
        // This might be camera.getProjectionMatrix() * camera.getViewMatrix() or similar.
        // For simplicity, assuming camera.getViewMatrix() already includes the necessary projection for the world.
        // The `projectionMatrix` field is more for screen-space ortho (UI, sprites).
        Matrix4f worldProjectionView = new Matrix4f(projectionMatrix).mul(camera.getViewMatrix());
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix); // This is screen-space for UI/Sprites later
        // For map chunks, the view matrix already has the iso transform.
        // So, for chunks, uProjectionMatrix is screen-ortho, uModelViewMatrix is camera's view.

        // --- Render Map Chunks ---
        defaultShader.setUniform("uModelViewMatrix", camera.getViewMatrix());
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uApplySubTextureRepeat", 1); // Enable shader-based V-repeat logic
        // Set V0 and VSpan for the DEFAULT_SIDE texture, as the shader's repeat logic is keyed to these
        // if fTexCoord.y is generated relative to this specific texture's V0 by Java.
        defaultShader.setUniform("uSubTextureV0", DEFAULT_SIDE_V0);
        defaultShader.setUniform("uSubTextureVSpan", DEFAULT_SIDE_V1 - DEFAULT_SIDE_V0);


        if (tileAtlasTexture != null && tileAtlasTexture.getId() != 0) {
            glActiveTexture(GL_TEXTURE0);
            tileAtlasTexture.bind();
            defaultShader.setUniform("uTextureSampler", 0); // Texture unit 0
            defaultShader.setUniform("uHasTexture", 1);
        } else {
            defaultShader.setUniform("uHasTexture", 0);
        }

        int chunksRendered = 0;
        if (mapChunks != null) {
            for (Chunk chunk : mapChunks) {
                if (camera.isChunkVisible(chunk.getBoundingBox())) { // Frustum culling
                    chunk.render();
                    chunksRendered++;
                }
            }
        }
        glBindVertexArray(0); // Unbind chunk VAO
        if (tileAtlasTexture != null && tileAtlasTexture.getId() != 0) {
            glBindTexture(GL_TEXTURE_2D, 0); // Unbind atlas
        }
        // if (frameCount % 300 == 0 && mapChunks != null && !mapChunks.isEmpty()) { // Debug log
        //     System.out.println("Rendered " + chunksRendered + "/" + mapChunks.size() + " chunks.");
        // }
        checkError("Renderer: render - after map chunks");


        // --- Render Sprites (Player, Trees) ---
        collectSortableItems();
        // Sort items: by screen Y, then by map depth (row+col), then by elevation, then by zOrder (player on top)
        Collections.sort(sortableItems, (item1, item2) -> {
            if (Math.abs(item1.screenYSortKey - item2.screenYSortKey) > 0.1f) {
                return Float.compare(item1.screenYSortKey, item2.screenYSortKey);
            }
            // Tie-breaking for items at similar screen Y
            float depth1 = item1.mapRow + item1.mapCol;
            float depth2 = item2.mapRow + item2.mapCol;
            if (Math.abs(depth1 - depth2) > 0.01f) {
                return Float.compare(depth1, depth2);
            }
            // Further tie-breaking by elevation if map positions are very close
            int elev1 = (item1.entity instanceof PlayerModel) ? (map.getTile(((PlayerModel)item1.entity).getTileRow(), ((PlayerModel)item1.entity).getTileCol()) != null ? map.getTile(((PlayerModel)item1.entity).getTileRow(), ((PlayerModel)item1.entity).getTileCol()).getElevation() : 0) : ((TreeData)item1.entity).elevation;
            int elev2 = (item2.entity instanceof PlayerModel) ? (map.getTile(((PlayerModel)item2.entity).getTileRow(), ((PlayerModel)item2.entity).getTileCol()) != null ? map.getTile(((PlayerModel)item2.entity).getTileRow(), ((PlayerModel)item2.entity).getTileCol()).getElevation() : 0) : ((TreeData)item2.entity).elevation;
            if (elev1 != elev2) {
                return Integer.compare(elev1, elev2);
            }
            return Integer.compare(item1.zOrder, item2.zOrder); // Player (zOrder 1) over trees (zOrder 0)
        });

        // Use screen-space projection for sprites
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix); // Screen ortho
        defaultShader.setUniform("uModelViewMatrix", modelViewMatrixForSprites); // Identity for screen space
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uApplySubTextureRepeat", 0); // Sprites don't use this V-repeat logic

        if (spriteVaoId != 0 && spriteVboId != 0) {
            glBindVertexArray(spriteVaoId);
            glBindBuffer(GL_ARRAY_BUFFER, spriteVboId); // Bind VBO for glBufferSubData
            defaultShader.setUniform("uHasTexture", 1); // Sprites are textured
            defaultShader.setUniform("uTextureSampler", 0);
            glActiveTexture(GL_TEXTURE0);

            for (SortableItem item : sortableItems) {
                spriteVertexBuffer.clear(); // Prepare buffer for new sprite
                int currentSpriteVertices = 0;
                Texture currentTextureToBind = null;

                if (item.entity instanceof PlayerModel) {
                    PlayerModel p = (PlayerModel) item.entity;
                    if (playerTexture != null && playerTexture.getId() != 0) {
                        currentSpriteVertices = addPlayerVerticesToBuffer_ScreenSpace(p, spriteVertexBuffer);
                        currentTextureToBind = playerTexture;
                    }
                } else if (item.entity instanceof TreeData) {
                    TreeData td = (TreeData) item.entity;
                    if (treeTexture != null && treeTexture.getId() != 0) {
                        currentSpriteVertices = addTreeVerticesToBuffer_ScreenSpace(td, spriteVertexBuffer);
                        currentTextureToBind = treeTexture;
                    }
                }

                if (currentSpriteVertices > 0 && currentTextureToBind != null) {
                    spriteVertexBuffer.flip(); // Make buffer readable by OpenGL
                    currentTextureToBind.bind(); // Bind the correct texture for this sprite
                    glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer); // Upload this sprite's data
                    glDrawArrays(GL_TRIANGLES, 0, currentSpriteVertices);
                }
            }
            glBindTexture(GL_TEXTURE_2D, 0); // Unbind sprite texture
            glBindBuffer(GL_ARRAY_BUFFER, 0); // Unbind sprite VBO
            glBindVertexArray(0); // Unbind sprite VAO
            checkError("Renderer: render - after sprites");
        }

        // --- Render UI ---
        if (uiFont != null && uiFont.isInitialized()) {
            // UI uses screen-space projection and identity model-view
            defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
            defaultShader.setUniform("uModelViewMatrix", modelViewMatrixForSprites);
            renderUI(); // This will bind font texture and set uIsFont=1 internally
        }

        defaultShader.unbind();
        checkError("Render End");
    }


    private void renderUI() {
        if (uiFont == null || !uiFont.isInitialized() || player == null || camera == null || inputHandler == null || map == null) {
            // System.err.println("Renderer: UI or critical components for UI not ready.");
            return;
        }

        // Shader is already bound. Font.drawText will handle its specific uniforms (uIsFont, texture).
        // Ensure blending is enabled for font transparency
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        int yPos = 20; // Initial Y position for text
        int yIncrement = 18; // Line height

        uiFont.drawText(10f, (float)yPos, "Player: ("+player.getTileRow()+", "+player.getTileCol()+") Act: "+player.getCurrentAction()+" Dir: "+player.getCurrentDirection()+" F:"+player.getVisualFrameIndex()); yPos+=yIncrement;

        Tile selectedTile = map.getTile(inputHandler.getSelectedRow(),inputHandler.getSelectedCol());
        String selectedInfo="Selected: ("+inputHandler.getSelectedRow()+", "+inputHandler.getSelectedCol()+")";
        if(selectedTile!=null){selectedInfo+=" Elev: "+selectedTile.getElevation()+" Type: "+selectedTile.getType();}
        uiFont.drawText(10f, (float)yPos, selectedInfo); yPos+=yIncrement;

        uiFont.drawText(10f, (float)yPos, String.format("Camera: (%.1f, %.1f) Zoom: %.2f",camera.getCameraX(),camera.getCameraY(),camera.getZoom())); yPos+=yIncrement;
        uiFont.drawText(10f, (float)yPos, "Move: Click | Elev Sel +/-: Q/E | Dig: J"); yPos+=yIncrement;
        uiFont.drawText(10f, (float)yPos, "Levitate: F | Center Cam: C | Regen Map: G"); yPos+=yIncrement;
        yPos+=yIncrement; // Extra space

        uiFont.drawText(10f, (float)yPos, "Inventory:");yPos+=yIncrement;
        java.util.Map<String,Integer> inventory=player.getInventory();
        if(inventory.isEmpty()){
            uiFont.drawText(20f, (float)yPos, "- Empty -"); yPos+=yIncrement;
        } else {
            for(java.util.Map.Entry<String,Integer> entry : inventory.entrySet()){
                uiFont.drawText(20f, (float)yPos, "- "+entry.getKey()+": "+entry.getValue()); yPos+=yIncrement;
            }
        }
        // Disable blend after UI if it's not needed for other 3D rendering,
        // but usually it's fine to leave it if other things also need alpha.
        // glDisable(GL_BLEND);
        checkError("Renderer: renderUI - after drawing text");
    }

    public void cleanup() {
        System.out.println("Renderer: Starting cleanup...");
        if(playerTexture!=null) playerTexture.delete();
        if(treeTexture!=null) treeTexture.delete();
        if(tileAtlasTexture!=null) tileAtlasTexture.delete();
        if(uiFont!=null) uiFont.cleanup();

        if(defaultShader!=null) defaultShader.cleanup();

        if (mapChunks != null) {
            for (Chunk chunk : mapChunks) {
                chunk.cleanup();
            }
            mapChunks.clear();
            System.out.println("Renderer: Map chunks cleaned up.");
        }

        if (spriteVaoId != 0) {
            glDeleteVertexArrays(spriteVaoId);
            spriteVaoId = 0;
        }
        if (spriteVboId != 0) {
            glDeleteBuffers(spriteVboId);
            spriteVboId = 0;
        }
        if (spriteVertexBuffer != null) {
            MemoryUtil.memFree(spriteVertexBuffer);
            spriteVertexBuffer = null;
        }
        System.out.println("Renderer: Sprite resources cleaned up.");
        System.out.println("Renderer: Cleanup complete.");
        checkError("Renderer: cleanup - End");
    }

    private void checkError(String stage) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            String errorMsg;
            switch (error) {
                case GL_INVALID_ENUM: errorMsg = "GL_INVALID_ENUM"; break;
                case GL_INVALID_VALUE: errorMsg = "GL_INVALID_VALUE"; break;
                case GL_INVALID_OPERATION: errorMsg = "GL_INVALID_OPERATION"; break;
                case GL_STACK_OVERFLOW: errorMsg = "GL_STACK_OVERFLOW"; break;
                case GL_STACK_UNDERFLOW: errorMsg = "GL_STACK_UNDERFLOW"; break;
                case GL_OUT_OF_MEMORY: errorMsg = "GL_OUT_OF_MEMORY"; break;
                case GL_INVALID_FRAMEBUFFER_OPERATION: errorMsg = "GL_INVALID_FRAMEBUFFER_OPERATION"; break;
                default: errorMsg = "Unknown GL error (" + String.format("0x%X", error) + ")"; break;
            }
            System.err.println("Renderer: OpenGL Error at stage '" + stage + "': " + errorMsg);
            // Optionally, throw an exception or halt for critical errors during development
            // throw new RuntimeException("OpenGL error at " + stage + ": " + errorMsg);
        }
    }
}
