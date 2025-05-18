package org.isogame.render;

import org.isogame.camera.CameraManager;
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
    private final InputHandler inputHandler;
    private int frameCount = 0;
    private Texture playerTexture;
    private Texture treeTexture;
    private Font uiFont;
    private Random tileDetailRandom;

    private List<Chunk> mapChunks;
    private static final int CHUNK_SIZE_TILES = 32;

    private Shader defaultShader;
    private Matrix4f projectionMatrix;
    private Matrix4f modelViewMatrixForSprites;

    private int spriteVaoId = 0;
    private int spriteVboId = 0;
    private FloatBuffer spriteVertexBuffer;

    public static final int FLOATS_PER_VERTEX_TEXTURED = 8;
    public static final int FLOATS_PER_VERTEX_COLORED = 6;

    private Texture tileAtlasTexture;

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
    private static final float ROCK_ATLAS_V0 = (1 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT;
    private static final float ROCK_ATLAS_U1 = (1 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;
    private static final float ROCK_ATLAS_V1 = (2 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT;

    private static final float SAND_ATLAS_U0 = (1 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;
    private static final float SAND_ATLAS_V0 = (1 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT;
    private static final float SAND_ATLAS_U1 = (2 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;
    private static final float SAND_ATLAS_V1 = (2 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT;

    // Side Textures - DEFAULT_SIDE is our Dirt texture (Top-Right in textu.png)
    private static final float DEFAULT_SIDE_U0 = (1 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;
    private static final float DEFAULT_SIDE_V0 = (0 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT;
    private static final float DEFAULT_SIDE_U1 = (2 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;
    private static final float DEFAULT_SIDE_V1 = (1 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT;

    private static final float SIDE_TEXTURE_DENSITY_FACTOR = 1.0f;

    private static final float DUMMY_U = 0.0f;
    private static final float DUMMY_V = 0.0f;



    private static class SortableItem { /* ... */
        float screenYSortKey; int zOrder; Object entity; float mapRow, mapCol;
        public SortableItem(PlayerModel p, CameraManager cam, Map m) { this.entity = p; this.zOrder = 1; this.mapRow = p.getMapRow(); this.mapCol = p.getMapCol(); Tile t = m.getTile(p.getTileRow(),p.getTileCol()); int elev = (t!=null)?t.getElevation():0; int[] sc = cam.mapToScreenCoordsForPicking(p.getMapCol(),p.getMapRow(),elev); this.screenYSortKey = sc[1]; }
        public SortableItem(TreeData tree, CameraManager cam, Map m) { this.entity = tree; this.zOrder = 0; this.mapRow = tree.mapRow; this.mapCol = tree.mapCol; int[] sc = cam.mapToScreenCoordsForPicking(tree.mapCol,tree.mapRow,tree.elevation); this.screenYSortKey = sc[1]; }
    }
    private static class TreeData { /* ... */
        Tile.TreeVisualType treeVisualType; float mapCol, mapRow; int elevation;
        float topDiamondCenterX_screen, topDiamondCenterY_screen;
        public TreeData(Tile.TreeVisualType type, float tc, float tr, int te, float screenAnchorX, float screenAnchorY) {this.treeVisualType=type; this.mapCol=tc; this.mapRow=tr; this.elevation=te; this.topDiamondCenterX_screen=screenAnchorX; this.topDiamondCenterY_screen=screenAnchorY;}
    }
    private List<SortableItem> sortableItems = new ArrayList<>();

    public Renderer(CameraManager camera, Map map, PlayerModel player, InputHandler inputHandler) {
        this.camera = camera;
        this.map = map;
        this.player = player;
        this.inputHandler = inputHandler;
        this.tileDetailRandom = new Random();
        projectionMatrix = new Matrix4f();
        modelViewMatrixForSprites = new Matrix4f().identity();

        loadAssets();
        checkError("Renderer Constructor - After loadAssets()");
        initShaders();
        checkError("Renderer Constructor - After initShaders()");
        initRenderObjects();
        checkError("Renderer Constructor - After initRenderObjects()");
        uploadTileMapGeometry();
        checkError("Renderer Constructor - After uploadTileMapGeometry()");
        checkError("Renderer Constructor End");
    }

    private void loadAssets() {
        System.out.println("Renderer: Starting asset loading...");
        String playerTexturePath = "/org/isogame/render/textures/lpc_character.png";
        this.playerTexture = Texture.loadTexture(playerTexturePath);
        if (this.playerTexture == null) System.err.println("Renderer CRITICAL: Player texture FAILED: " + playerTexturePath);
        else System.out.println("Renderer: Player texture loaded: " + playerTexturePath + " ID: " + playerTexture.getId());
        checkError("Renderer: loadAssets - After Player Texture Load");

        String treeTexturePath = "/org/isogame/render/textures/fruit-trees.png";
        this.treeTexture = Texture.loadTexture(treeTexturePath);
        if (this.treeTexture == null) System.err.println("Renderer CRITICAL: Tree texture FAILED: " + treeTexturePath);
        else System.out.println("Renderer: Tree texture loaded: " + treeTexturePath + " ID: " + treeTexture.getId());
        checkError("Renderer: loadAssets - After Tree Texture Load");

        System.out.println("Renderer: Attempting to load tile atlas (textu.png)...");
        String tileAtlasPath = "/org/isogame/render/textures/textu.png";
        this.tileAtlasTexture = Texture.loadTexture(tileAtlasPath);
        if (this.tileAtlasTexture == null) {
            System.err.println("Renderer CRITICAL: Tile Atlas (textu.png) FAILED to load: " + tileAtlasPath);
        } else {
            System.out.println("Renderer: Tile Atlas (textu.png) loaded: " + tileAtlasPath + " ID: " + tileAtlasTexture.getId() + " W: " + tileAtlasTexture.getWidth() + " H: " + tileAtlasTexture.getHeight());
        }
        checkError("Renderer: loadAssets - After Tile Atlas Load");

        String fontPath = "/org/isogame/render/fonts/PressStart2P-Regular.ttf";
        try {
            System.out.println("Renderer: Attempting to load font: " + fontPath);
            this.uiFont = new Font(fontPath, 16f, this);
            if (this.uiFont.isInitialized()) {
                System.out.println("Renderer: Font asset loaded and initialized: " + fontPath + " (Font Tex ID: " + uiFont.getTextureID() + ")");
            } else {
                System.err.println("Renderer WARNING: Font object created, but uiFont.isInitialized() is false for: " + fontPath);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Renderer CRITICAL: Failed to load UI font: " + fontPath + " - " + e.getMessage());
            e.printStackTrace();
            this.uiFont = null;
        }
        checkError("Renderer: loadAssets - After Font Load Attempt");
        System.out.println("Renderer: loadAssets method finished.");
    }

    private void initShaders() {
        try {
            defaultShader = new Shader();
            defaultShader.createVertexShader(Shader.loadResource("/org/isogame/render/shaders/vertex.glsl"));
            defaultShader.createFragmentShader(Shader.loadResource("/org/isogame/render/shaders/fragment.glsl"));
            defaultShader.link();
            checkError("Renderer: initShaders - After Shader Link");

            // Existing uniforms
            defaultShader.createUniform("uProjectionMatrix");
            defaultShader.createUniform("uModelViewMatrix");
            defaultShader.createUniform("uTextureSampler");
            defaultShader.createUniform("uHasTexture");
            defaultShader.createUniform("uIsFont");

            // Add the new uniforms here:
            defaultShader.createUniform("uSubTextureV0");
            defaultShader.createUniform("uSubTextureVSpan");
            defaultShader.createUniform("uApplySubTextureRepeat");
            // Shader.createUniform will throw an IOException if the uniform is not found in the shader code,
            // which is good for catching mismatches between Java and GLSL.

        } catch (IOException e) {
            System.err.println("Renderer: Error initializing shaders: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Renderer: Failed to initialize shaders", e);
        }
        System.out.println("Renderer: Shaders initialized.");
    }

    public Shader getDefaultShader() { return defaultShader; }

    private void initRenderObjects() {
        mapChunks = new ArrayList<>();
        if (map != null) {
            int numChunksX = (int) Math.ceil((double) map.getWidth() / CHUNK_SIZE_TILES);
            int numChunksY = (int) Math.ceil((double) map.getHeight() / CHUNK_SIZE_TILES);
            for (int cy = 0; cy < numChunksY; cy++) {
                for (int cx = 0; cx < numChunksX; cx++) {
                    Chunk chunk = new Chunk(cx, cy, CHUNK_SIZE_TILES);
                    chunk.setupGLResources();
                    mapChunks.add(chunk);
                }
            }
            System.out.println("Renderer: Initialized " + mapChunks.size() + " map chunk objects.");
        } else {
            System.err.println("Renderer: Map is null during initRenderObjects, cannot initialize chunks.");
        }

        spriteVaoId = glGenVertexArrays();
        glBindVertexArray(spriteVaoId);
        spriteVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        int initialSpriteBufferCapacity = 20 * 6 * FLOATS_PER_VERTEX_TEXTURED;
        spriteVertexBuffer = MemoryUtil.memAllocFloat(initialSpriteBufferCapacity);
        glBufferData(GL_ARRAY_BUFFER, (long)spriteVertexBuffer.capacity() * Float.BYTES, GL_DYNAMIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, FLOATS_PER_VERTEX_TEXTURED * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, FLOATS_PER_VERTEX_TEXTURED * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, FLOATS_PER_VERTEX_TEXTURED * Float.BYTES, (2 + 4) * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        System.out.println("Renderer: Render objects (Sprite VAO/VBO & Chunks structure) initialized.");
    }

    public void uploadTileMapGeometry() {
        System.out.println("Renderer: Orchestrating upload of STATIC tile map geometry for ALL CHUNKS (WORLD coordinates)...");
        if (mapChunks == null || mapChunks.isEmpty()) {
            System.err.println("Renderer: mapChunks list is null or empty in uploadTileMapGeometry! Check initRenderObjects().");
            return;
        }
        for (Chunk chunk : mapChunks) {
            chunk.uploadGeometry(map, inputHandler, this);
        }
        System.out.println("Renderer: All chunk geometries processed by their respective chunks.");
    }

    public void updateChunkContainingTile(int tileRow, int tileCol) {
        if (map == null || mapChunks == null || mapChunks.isEmpty()) {
            System.err.println("Renderer: Cannot update chunk, map or chunks not initialized.");
            return;
        }
        int chunkGridX = tileCol / CHUNK_SIZE_TILES;
        int chunkGridY = tileRow / CHUNK_SIZE_TILES;

        Chunk targetChunk = null;
        for (Chunk chunk : mapChunks) {
            if (chunk.chunkGridX == chunkGridX && chunk.chunkGridY == chunkGridY) {
                targetChunk = chunk;
                break;
            }
        }
        if (targetChunk != null) {
            System.out.println("Renderer: Updating geometry for chunk: (" + chunkGridX + ", " + chunkGridY + ")");
            targetChunk.uploadGeometry(this.map, this.inputHandler, this);
        } else {
            System.err.println("Renderer: Could not find chunk for tile: (" + tileRow + ", " + tileCol + ")");
        }
    }

    public void onResize(int fbWidth, int fbHeight) {
        if (fbWidth <= 0 || fbHeight <= 0) return;
        glViewport(0, 0, fbWidth, fbHeight);
        projectionMatrix.identity().ortho(0, fbWidth, fbHeight, 0, -1, 1);
        if (camera != null) {
            camera.setProjectionMatrixForCulling(projectionMatrix);
            camera.forceUpdateViewMatrix();
        }
        checkError("Renderer: onResize");
    }

    private int addRectangularQuadWithTextureOrColor(FloatBuffer buffer,
                                                     float xTL, float yTL, float xTR, float yTR,
                                                     float xBR, float yBR, float xBL, float yBL,
                                                     float[] color,
                                                     boolean isTextured, float u0, float v0, float u1, float v1_tex) {
        float[] c = color;
        float actual_u0, actual_v0, actual_u1_coord, actual_v1_coord;

        if (isTextured) {
            actual_u0 = u0; actual_v0 = v0;
            actual_u1_coord = u1; actual_v1_coord = v1_tex;
        } else {
            actual_u0 = DUMMY_U; actual_v0 = DUMMY_V;
            actual_u1_coord = DUMMY_U; actual_v1_coord = DUMMY_V;
        }

        buffer.put(xTL).put(yTL).put(c[0]).put(c[1]).put(c[2]).put(c[3]).put(actual_u0).put(actual_v0);
        buffer.put(xBL).put(yBL).put(c[0]).put(c[1]).put(c[2]).put(c[3]).put(actual_u0).put(actual_v1_coord);
        buffer.put(xTR).put(yTR).put(c[0]).put(c[1]).put(c[2]).put(c[3]).put(actual_u1_coord).put(actual_v0);

        buffer.put(xTR).put(yTR).put(c[0]).put(c[1]).put(c[2]).put(c[3]).put(actual_u1_coord).put(actual_v0);
        buffer.put(xBL).put(yBL).put(c[0]).put(c[1]).put(c[2]).put(c[3]).put(actual_u0).put(actual_v1_coord);
        buffer.put(xBR).put(yBR).put(c[0]).put(c[1]).put(c[2]).put(c[3]).put(actual_u1_coord).put(actual_v1_coord);
        return 6;
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
        float world_tc_y = world_gc_y_plane - (elevation * elevThick_worldY_offset);

        float currentTileMinX = Float.MAX_VALUE, currentTileMinY = Float.MAX_VALUE;
        float currentTileMaxX = Float.MIN_VALUE, currentTileMaxY = Float.MIN_VALUE;

        float d_top_y_rel = -halfH_unit; float d_left_x_rel = -halfW_unit; float d_left_y_rel = 0;
        float d_right_x_rel = halfW_unit; float d_right_y_rel = 0; float d_bottom_y_rel = halfH_unit;
        float fTx_world = world_gc_x;                         float fTy_world = world_tc_y + d_top_y_rel;
        float fLx_world = world_gc_x + d_left_x_rel;          float fLy_world = world_tc_y + d_left_y_rel;
        float fRx_world = world_gc_x + d_right_x_rel;         float fRy_world = world_tc_y + d_right_y_rel;
        float fBx_world = world_gc_x;                         float fBy_world = world_tc_y + d_bottom_y_rel;

        float block_base_y = world_tc_y + elevThick_worldY_offset;
        float bbLx_world = world_gc_x + d_left_x_rel;         float bbLy_world = block_base_y + d_left_y_rel;
        float bbRx_world = world_gc_x + d_right_x_rel;        float bbRy_world = block_base_y + d_right_y_rel;
        float bbBx_world = world_gc_x;                        float bbBy_world = block_base_y + d_bottom_y_rel;

        float pTopTx_world = world_gc_x;                      float pTopTy_world = world_gc_y_plane + d_top_y_rel;
        float pTopLx_world = world_gc_x + d_left_x_rel;       float pTopLy_world = world_gc_y_plane + d_left_y_rel;
        float pTopRx_world = world_gc_x + d_right_x_rel;      float pTopRy_world = world_gc_y_plane + d_right_y_rel;
        float pTopBx_world = world_gc_x;                      float pTopBy_world = world_gc_y_plane + d_bottom_y_rel;

        float pedestal_bottom_y = world_gc_y_plane + BASE_THICKNESS;
        float pBotLx_world = world_gc_x + d_left_x_rel;       float pBotLy_world = pedestal_bottom_y + d_left_y_rel;
        float pBotRx_world = world_gc_x + d_right_x_rel;      float pBotRy_world = pedestal_bottom_y + d_right_y_rel;
        float pBotBx_world = world_gc_x;                      float pBotBy_world = pedestal_bottom_y + d_bottom_y_rel;

        float[] topClr, s1Clr, s2Clr, bTopClr, bS1Clr, bS2Clr;
        boolean isW = (type == Tile.TileType.WATER);

        if (isSelected) {
            topClr = new float[]{1.0f, 0.8f, 0.0f, 0.8f}; s1Clr = new float[]{0.9f, 0.7f, 0.0f, 0.8f};
            s2Clr = new float[]{0.8f, 0.6f, 0.0f, 0.8f}; bTopClr = new float[]{0.5f, 0.4f, 0.0f, 0.8f};
            bS1Clr = new float[]{0.4f, 0.3f, 0.0f, 0.8f}; bS2Clr = new float[]{0.3f, 0.2f, 0.0f, 0.8f};
        } else {
            switch (type) {
                case WATER:
                    final double WATER_TIME_SCALE_FAST = 0.06; final double WATER_TIME_SCALE_SLOW = 0.025;
                    final long WATER_TIME_PERIOD = 3600; final double WATER_SPATIAL_SCALE_FINE = 0.35;
                    final double WATER_SPATIAL_SCALE_BROAD = 0.15; final int WATER_SPATIAL_PERIOD = 128;
                    final float BASE_BLUE_R = 0.05f; final float BASE_BLUE_G = 0.25f; final float BASE_BLUE_B = 0.5f;
                    final float WAVE_HIGHLIGHT_FACTOR = 0.15f; final float WAVE_SHADOW_FACTOR = 0.1f;
                    final float WATER_ALPHA = 0.85f;
                    final float[] SUBMERGED_SIDE_COLOR_1 = new float[]{0.04f, 0.08f, 0.18f, 1.0f};
                    final float[] SUBMERGED_SIDE_COLOR_2 = new float[]{0.03f, 0.06f, 0.16f, 1.0f};
                    double timeValFast = (this.frameCount % WATER_TIME_PERIOD) * WATER_TIME_SCALE_FAST;
                    double timeValSlow = (this.frameCount % WATER_TIME_PERIOD) * WATER_TIME_SCALE_SLOW;
                    double spatialValX_fine = (tileC % WATER_SPATIAL_PERIOD) * WATER_SPATIAL_SCALE_FINE;
                    double spatialValY_fine = (tileR % WATER_SPATIAL_PERIOD) * WATER_SPATIAL_SCALE_FINE;
                    double spatialValX_broad = (tileC % WATER_SPATIAL_PERIOD) * WATER_SPATIAL_SCALE_BROAD;
                    double spatialValY_broad = (tileR % WATER_SPATIAL_PERIOD) * WATER_SPATIAL_SCALE_BROAD;
                    double wave1 = Math.sin(timeValFast + spatialValX_fine + spatialValY_fine);
                    double wave2 = Math.cos(timeValSlow + spatialValX_broad - spatialValY_broad * 0.7);
                    double combinedWaveEffect = (wave1 * 0.6 + wave2 * 0.4);
                    float r_col = BASE_BLUE_R; float g_col = BASE_BLUE_G; float b_col = BASE_BLUE_B;
                    if (combinedWaveEffect > 0) { float highlight = (float) (combinedWaveEffect * WAVE_HIGHLIGHT_FACTOR); r_col += highlight * 0.3f; g_col += highlight * 0.7f; b_col += highlight;
                    } else { float shadow = (float) (-combinedWaveEffect * WAVE_SHADOW_FACTOR); r_col -= shadow * 0.5f; g_col -= shadow * 0.5f; b_col -= shadow; }
                    r_col = Math.max(0.0f, Math.min(1.0f, r_col)); g_col = Math.max(0.0f, Math.min(1.0f, g_col)); b_col = Math.max(0.0f, Math.min(1.0f, b_col));
                    topClr = new float[]{r_col, g_col, b_col, WATER_ALPHA}; s1Clr = topClr; s2Clr = topClr; bTopClr = topClr;
                    bS1Clr = SUBMERGED_SIDE_COLOR_1; bS2Clr = SUBMERGED_SIDE_COLOR_2;
                    break;
                case SAND: topClr = new float[]{0.82f,0.7f,0.55f,1f}; s1Clr = new float[]{0.75f,0.65f,0.49f,1f}; s2Clr = new float[]{0.67f,0.59f,0.43f,1f}; bTopClr = new float[]{0.59f,0.51f,0.35f,1f}; bS1Clr = new float[]{0.51f,0.43f,0.27f,1f}; bS2Clr = new float[]{0.43f,0.35f,0.19f,1f}; break;
                case GRASS: topClr = new float[]{0.20f,0.45f,0.10f,1f}; s1Clr = new float[]{0.18f,0.40f,0.09f,1f}; s2Clr = new float[]{0.16f,0.35f,0.08f,1f}; bTopClr = new float[]{0.35f,0.28f,0.18f,1f}; bS1Clr = new float[]{0.30f,0.23f,0.15f,1f}; bS2Clr = new float[]{0.25f,0.18f,0.12f,1f}; break;
                case ROCK: topClr = new float[]{0.45f,0.45f,0.45f,1f}; s1Clr = new float[]{0.40f,0.40f,0.40f,1f}; s2Clr = new float[]{0.35f,0.35f,0.35f,1f}; bTopClr = new float[]{0.30f,0.30f,0.30f,1f}; bS1Clr = new float[]{0.25f,0.25f,0.25f,1f}; bS2Clr = new float[]{0.20f,0.20f,0.20f,1f}; break;
                case SNOW: topClr = new float[]{0.95f,0.95f,1.0f,1f}; s1Clr = new float[]{0.90f,0.90f,0.95f,1f}; s2Clr = new float[]{0.85f,0.85f,0.90f,1f}; bTopClr = new float[]{0.5f,0.5f,0.55f,1f}; bS1Clr = new float[]{0.45f,0.45f,0.50f,1f}; bS2Clr = new float[]{0.40f,0.40f,0.45f,1f}; break;
                default: topClr = new float[]{1f,0f,1f,1f}; s1Clr = topClr; s2Clr = topClr; bTopClr = topClr; bS1Clr = topClr; bS2Clr = topClr; break;
            }
        }

        int verticesAdded = 0;
        float[] whiteTint = {1.0f, 1.0f, 1.0f, 1.0f};
        float[] sideTextureTintToUse = isSelected ? topClr : whiteTint;

        // --- Pedestal Sides (Always DIRT texture) ---
        float pedestalSideU0 = DEFAULT_SIDE_U0;
        float pedestalSideV0 = DEFAULT_SIDE_V0;
        float pedestalSideU1 = DEFAULT_SIDE_U1;
        float pedestalSideTexture_V_Span_InAtlas = DEFAULT_SIDE_V1 - DEFAULT_SIDE_V0;

        float pedestal_height_world = (float)BASE_THICKNESS;
        float pedestal_v_repeats = (pedestal_height_world / (float)TILE_THICKNESS) * SIDE_TEXTURE_DENSITY_FACTOR;
        float pedestal_v_bottom_coord = pedestalSideV0 + pedestalSideTexture_V_Span_InAtlas * pedestal_v_repeats;

        verticesAdded += addRectangularQuadWithTextureOrColor(buffer, pTopLx_world, pTopLy_world, pTopBx_world, pTopBy_world, pBotBx_world, pBotBy_world, pBotLx_world, pBotLy_world, sideTextureTintToUse, true, pedestalSideU0, pedestalSideV0, pedestalSideU1, pedestal_v_bottom_coord);
        verticesAdded += addRectangularQuadWithTextureOrColor(buffer, pTopBx_world, pTopBy_world, pTopRx_world, pTopRy_world, pBotRx_world, pBotRy_world, pBotBx_world, pBotBy_world, sideTextureTintToUse, true, pedestalSideU0, pedestalSideV0, pedestalSideU1, pedestal_v_bottom_coord);

        // --- Top Surface Logic ---
        if (isW) {
            verticesAdded += addRectangularQuadWithTextureOrColor(buffer, fLx_world, fLy_world, fTx_world, fTy_world, fRx_world, fRy_world, fBx_world, fBy_world, topClr, false, DUMMY_U, DUMMY_V, DUMMY_U, DUMMY_V);
        } else {
            float current_top_u0 = DUMMY_U, current_top_v0 = DUMMY_V, current_top_u1 = DUMMY_U, current_top_v1 = DUMMY_V;
            boolean textureThisTop = false;
            float[] finalTopColor = topClr;

            if (type == Tile.TileType.GRASS) {
                current_top_u0 = GRASS_ATLAS_U0; current_top_v0 = GRASS_ATLAS_V0; current_top_u1 = GRASS_ATLAS_U1; current_top_v1 = GRASS_ATLAS_V1;
                textureThisTop = true;
                if (!isSelected) finalTopColor = whiteTint;
            } else if (type == Tile.TileType.SAND) {
                current_top_u0 = SAND_ATLAS_U0; current_top_v0 = SAND_ATLAS_V0; current_top_u1 = SAND_ATLAS_U1; current_top_v1 = SAND_ATLAS_V1;
                textureThisTop = true;
                if (!isSelected) finalTopColor = whiteTint;
            } else if (type == Tile.TileType.ROCK) {
                current_top_u0 = ROCK_ATLAS_U0; current_top_v0 = ROCK_ATLAS_V0; current_top_u1 = ROCK_ATLAS_U1; current_top_v1 = ROCK_ATLAS_V1;
                textureThisTop = true;
                if (!isSelected) finalTopColor = whiteTint;
            } else if (type == Tile.TileType.SNOW && !isSelected) { // SNOW remains colored for top
                finalTopColor = topClr;
                textureThisTop = false;
            }

            float diamondTopLx, diamondTopLy, diamondTopTx, diamondTopTy, diamondTopRx, diamondTopRy, diamondTopBx, diamondTopBy;
            if (elevation == 0) {
                diamondTopLx = pTopLx_world; diamondTopLy = pTopLy_world; diamondTopTx = pTopTx_world; diamondTopTy = pTopTy_world;
                diamondTopRx = pTopRx_world; diamondTopRy = pTopRy_world; diamondTopBx = pTopBx_world; diamondTopBy = pTopBy_world;
                if (!textureThisTop && !isSelected && type != Tile.TileType.SNOW) finalTopColor = bTopClr;
                else if (type == Tile.TileType.SNOW && !isSelected) finalTopColor = topClr;
            } else {
                diamondTopLx = fLx_world; diamondTopLy = fLy_world; diamondTopTx = fTx_world; diamondTopTy = fTy_world;
                diamondTopRx = fRx_world; diamondTopRy = fRy_world; diamondTopBx = fBx_world; diamondTopBy = fBy_world;
            }

            if (textureThisTop) {
                float mid_u = (current_top_u0 + current_top_u1) / 2f;
                float mid_v = (current_top_v0 + current_top_v1) / 2f;
                buffer.put(diamondTopLx).put(diamondTopLy).put(finalTopColor[0]).put(finalTopColor[1]).put(finalTopColor[2]).put(finalTopColor[3]).put(current_top_u0).put(mid_v);
                buffer.put(diamondTopTx).put(diamondTopTy).put(finalTopColor[0]).put(finalTopColor[1]).put(finalTopColor[2]).put(finalTopColor[3]).put(mid_u).put(current_top_v0);
                buffer.put(diamondTopBx).put(diamondTopBy).put(finalTopColor[0]).put(finalTopColor[1]).put(finalTopColor[2]).put(finalTopColor[3]).put(mid_u).put(current_top_v1);
                verticesAdded += 3;
                buffer.put(diamondTopTx).put(diamondTopTy).put(finalTopColor[0]).put(finalTopColor[1]).put(finalTopColor[2]).put(finalTopColor[3]).put(mid_u).put(current_top_v0);
                buffer.put(diamondTopRx).put(diamondTopRy).put(finalTopColor[0]).put(finalTopColor[1]).put(finalTopColor[2]).put(finalTopColor[3]).put(current_top_u1).put(mid_v);
                buffer.put(diamondTopBx).put(diamondTopBy).put(finalTopColor[0]).put(finalTopColor[1]).put(finalTopColor[2]).put(finalTopColor[3]).put(mid_u).put(current_top_v1);
                verticesAdded += 3;
            } else {
                verticesAdded += addRectangularQuadWithTextureOrColor(buffer, diamondTopLx, diamondTopLy, diamondTopTx, diamondTopTy, diamondTopRx, diamondTopRy, diamondTopBx, diamondTopBy, finalTopColor, false, DUMMY_U, DUMMY_V, DUMMY_U, DUMMY_V);
            }

            // --- Elevated Block Sides (Type-Specific Sides, with density factor) ---
            if (elevation > 0) {
                float elevSideU0, elevSideV0, elevSideU1, elevSideTexture_V_Span_InAtlas;

                // Select side texture based on the tile's own type for its elevated sides
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
                    case SNOW: // Snow sides will use default dirt for now
                    default:   // Other unhandled types also use default dirt
                        elevSideU0 = DEFAULT_SIDE_U0; elevSideV0 = DEFAULT_SIDE_V0;
                        elevSideU1 = DEFAULT_SIDE_U1; elevSideTexture_V_Span_InAtlas = DEFAULT_SIDE_V1 - DEFAULT_SIDE_V0;
                        break;
                }

                float elevated_block_height_world = (float)TILE_THICKNESS;
                float elevated_block_v_repeats = (elevated_block_height_world / (float)TILE_THICKNESS) * SIDE_TEXTURE_DENSITY_FACTOR;
                float elevated_block_v_bottom_coord = elevSideV0 + elevSideTexture_V_Span_InAtlas * elevated_block_v_repeats;

                verticesAdded += addRectangularQuadWithTextureOrColor(buffer, fLx_world, fLy_world, fBx_world, fBy_world, bbBx_world, bbBy_world, bbLx_world, bbLy_world, sideTextureTintToUse, true, elevSideU0, elevSideV0, elevSideU1, elevated_block_v_bottom_coord);
                verticesAdded += addRectangularQuadWithTextureOrColor(buffer, fBx_world, fBy_world, fRx_world, fRy_world, bbRx_world, bbRy_world, bbBx_world, bbBy_world, sideTextureTintToUse, true, elevSideU0, elevSideV0, elevSideU1, elevated_block_v_bottom_coord);
            }
        }

        // Base Top (Surface at grid plane, below elevated blocks)
        if (elevation > 0 && !isW) {
            verticesAdded += addRectangularQuadWithTextureOrColor(buffer, pTopLx_world,pTopLy_world,pTopTx_world,pTopTy_world,pTopRx_world,pTopRy_world,pTopBx_world,pTopBy_world, bTopClr , false, DUMMY_U, DUMMY_V, DUMMY_U, DUMMY_V);
        }

        // Bounding box update
        currentTileMinX = Math.min(pTopLx_world, pBotLx_world);
        currentTileMaxX = Math.max(pTopRx_world, pBotRx_world);
        currentTileMinY = Math.min(fTy_world, pTopTy_world);
        currentTileMaxY = pBotBy_world;

        if (elevation > 0 && !isW) {
            currentTileMinX = Math.min(currentTileMinX, fLx_world);
            currentTileMaxX = Math.max(currentTileMaxX, fRx_world);
        } else if (isW) {
            currentTileMinX = Math.min(currentTileMinX, fLx_world);
            currentTileMaxX = Math.max(currentTileMaxX, fRx_world);
        }

        if (verticesAdded > 0) {
            chunkBoundsMinMax[0] = Math.min(chunkBoundsMinMax[0], currentTileMinX);
            chunkBoundsMinMax[1] = Math.min(chunkBoundsMinMax[1], currentTileMinY);
            chunkBoundsMinMax[2] = Math.max(chunkBoundsMinMax[2], currentTileMaxX);
            chunkBoundsMinMax[3] = Math.max(chunkBoundsMinMax[3], currentTileMaxY);
        }
        return verticesAdded;
    }

    public int addGrassVerticesForTile_WorldSpace_ForChunk(
            int tileR, int tileC, Tile tile, FloatBuffer buffer, float[] chunkBoundsMinMax) {
        return 0;
    }

    private int addPlayerVerticesToBuffer_ScreenSpace(PlayerModel p, FloatBuffer buffer) { /* ... as before ... */
        if (playerTexture == null || playerTexture.getId() == 0) return 0; float r=1f,g=1f,b=1f,a=1f;
        Tile cT=map.getTile(p.getTileRow(),p.getTileCol()); int elev=(cT!=null)?cT.getElevation():0;
        int[] sC=camera.mapToScreenCoordsForPicking(p.getMapCol(),p.getMapRow(),elev); float sX=sC[0],sY=sC[1];
        float cz=camera.getZoom(); float sW=PlayerModel.FRAME_WIDTH*cz;float sH=PlayerModel.FRAME_HEIGHT*cz;
        float dX=sX-sW/2f;float dY=sY-sH; if(p.isLevitating())dY-=(Math.sin(p.getLevitateTimer())*8*cz);
        float u0_tex,v0_tex,u1_tex,v1_tex_player; int afc=p.getVisualFrameIndex();int afr=p.getAnimationRow();
        float tsX=afc*PlayerModel.FRAME_WIDTH;float tsY=afr*PlayerModel.FRAME_HEIGHT;
        u0_tex=tsX/playerTexture.getWidth();v0_tex=tsY/playerTexture.getHeight();u1_tex=(tsX+PlayerModel.FRAME_WIDTH)/playerTexture.getWidth();v1_tex_player=(tsY+PlayerModel.FRAME_HEIGHT)/playerTexture.getHeight();
        return addRectangularQuadWithTextureOrColor(buffer, dX,dY, dX+sW,dY, dX+sW,dY+sH, dX,dY+sH, new float[]{r,g,b,a}, true, u0_tex,v0_tex,u1_tex,v1_tex_player);
    }

    private int addTreeVerticesToBuffer_ScreenSpace(TreeData tree, FloatBuffer buffer) { /* ... as before ... */
        if(treeTexture==null||treeTexture.getId()==0||tree.treeVisualType==Tile.TreeVisualType.NONE)return 0; float r=1f,g=1f,b=1f,a=1f;
        float u0_tex=0,v0_tex=0,u1_tex_tree=0,v1_tex_tree=0,fw=0,fh=0,aoyp=0;
        switch(tree.treeVisualType){case APPLE_TREE_FRUITING:fw=90;fh=130;u0_tex=0f/treeTexture.getWidth();v0_tex=0f/treeTexture.getHeight();u1_tex_tree=90f/treeTexture.getWidth();v1_tex_tree=130f/treeTexture.getHeight();aoyp=110;break;
            case PINE_TREE_SMALL:fw=90;fh=180;u0_tex=90f/treeTexture.getWidth();v0_tex=0f/treeTexture.getHeight();u1_tex_tree=(90f+90f)/treeTexture.getWidth();v1_tex_tree=180f/treeTexture.getHeight();aoyp=165;break; default:return 0;}
        float cz=camera.getZoom(); float sW=fw*cz,sH=fh*cz;
        float tasX=tree.topDiamondCenterX_screen; float tasY=tree.topDiamondCenterY_screen;
        float saoY=(fh-aoyp)*cz; float dX=tasX-sW/2f; float dY=tasY-(sH-saoY);
        return addRectangularQuadWithTextureOrColor(buffer, dX,dY, dX+sW,dY, dX+sW,dY+sH, dX,dY+sH, new float[]{r,g,b,a}, true, u0_tex,v0_tex,u1_tex_tree,v1_tex_tree);
    }

    public void render() { /* ... as before ... */
        frameCount++;
        defaultShader.bind();
        float subTextureV0 = DEFAULT_SIDE_V0; // e.g., 0.0f
        float subTextureVSpan = DEFAULT_SIDE_V1 - DEFAULT_SIDE_V0; // e.g., 0.5f


        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);

        defaultShader.setUniform("uModelViewMatrix", camera.getViewMatrix());
        defaultShader.setUniform("uIsFont", 0);

        if (tileAtlasTexture != null && tileAtlasTexture.getId() != 0) {
            glActiveTexture(GL_TEXTURE0);
            tileAtlasTexture.bind();
            defaultShader.setUniform("uTextureSampler", 0);
            defaultShader.setUniform("uHasTexture", 1);
        } else {
            defaultShader.setUniform("uHasTexture", 0);
        }

        int chunksRendered = 0;
        if (mapChunks != null) {
            for (Chunk chunk : mapChunks) {
                if (camera.isChunkVisible(chunk.getBoundingBox())) {
                    chunk.render();
                    chunksRendered++;
                }
            }
        }
        glBindVertexArray(0);

        if (tileAtlasTexture != null && tileAtlasTexture.getId() != 0) {
            glBindTexture(GL_TEXTURE_2D, 0);
        }

        if (frameCount % 300 == 0 && mapChunks != null && !mapChunks.isEmpty()) {
            System.out.println("Rendered " + chunksRendered + "/" + mapChunks.size() + " chunks.");
        }
        checkError("Renderer: render - after map chunks");

        collectSortableItems();
        Collections.sort(sortableItems, (item1, item2) -> {
            if (Math.abs(item1.screenYSortKey - item2.screenYSortKey) > 0.1f) return Float.compare(item1.screenYSortKey, item2.screenYSortKey);
            float d1 = item1.mapRow+item1.mapCol, d2 = item2.mapRow+item2.mapCol; if(Math.abs(d1-d2)>0.01f) return Float.compare(d1,d2);
            int elev1 = (item1.entity instanceof PlayerModel) ? (map.getTile(((PlayerModel)item1.entity).getTileRow(), ((PlayerModel)item1.entity).getTileCol()) != null ? map.getTile(((PlayerModel)item1.entity).getTileRow(), ((PlayerModel)item1.entity).getTileCol()).getElevation() : 0) : ((TreeData)item1.entity).elevation;
            int elev2 = (item2.entity instanceof PlayerModel) ? (map.getTile(((PlayerModel)item2.entity).getTileRow(), ((PlayerModel)item2.entity).getTileCol()) != null ? map.getTile(((PlayerModel)item2.entity).getTileRow(), ((PlayerModel)item2.entity).getTileCol()).getElevation() : 0) : ((TreeData)item2.entity).elevation;
            if (elev1 != elev2) return Integer.compare(elev1, elev2);
            return Integer.compare(item1.zOrder, item2.zOrder);
        });

        defaultShader.setUniform("uModelViewMatrix", modelViewMatrixForSprites);
        if (spriteVaoId != 0) {
            glBindVertexArray(spriteVaoId);
            defaultShader.setUniform("uHasTexture", 1);
            defaultShader.setUniform("uIsFont", 0);
            defaultShader.setUniform("uTextureSampler", 0);
            glActiveTexture(GL_TEXTURE0);

            for (SortableItem item : sortableItems) {
                spriteVertexBuffer.clear(); int currentSpriteVertices = 0; Texture currentTextureToBind = null;
                if (item.entity instanceof PlayerModel) { PlayerModel p = (PlayerModel)item.entity; if (playerTexture != null && playerTexture.getId() != 0) { currentSpriteVertices = addPlayerVerticesToBuffer_ScreenSpace(p, spriteVertexBuffer); currentTextureToBind = playerTexture; }
                } else if (item.entity instanceof TreeData) { TreeData td = (TreeData)item.entity; if (treeTexture != null && treeTexture.getId() != 0) { currentSpriteVertices = addTreeVerticesToBuffer_ScreenSpace(td, spriteVertexBuffer); currentTextureToBind = treeTexture; } }

                if (currentSpriteVertices > 0 && currentTextureToBind != null) {
                    spriteVertexBuffer.flip();
                    currentTextureToBind.bind();
                    glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
                    glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                    glDrawArrays(GL_TRIANGLES, 0, currentSpriteVertices);
                }
            }
            glBindTexture(GL_TEXTURE_2D, 0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
            checkError("Renderer: render - after sprites");
        }

        if (uiFont != null && uiFont.isInitialized()) {
            defaultShader.setUniform("uModelViewMatrix", modelViewMatrixForSprites);
            renderUI();
        }
        defaultShader.unbind();
    }

    private void collectSortableItems() { /* ... */
        sortableItems.clear();
        if (this.player != null) {
            sortableItems.add(new SortableItem(this.player, this.camera, this.map));
        }
        if (map == null) return;
        int mapW = map.getWidth(); int mapH = map.getHeight();
        for (int r_loop = 0; r_loop < mapH; r_loop++) {
            for (int c_loop = 0; c_loop < mapW; c_loop++) {
                Tile tile = map.getTile(r_loop, c_loop);
                if (tile != null && tile.getTreeType() != Tile.TreeVisualType.NONE && tile.getType() != Tile.TileType.WATER) {
                    int elevation = tile.getElevation();
                    int[] tileTopScreenCoords = camera.mapToScreenCoordsForPicking(c_loop, r_loop, elevation);
                    sortableItems.add(new SortableItem(
                            new TreeData(tile.getTreeType(), (float)c_loop, (float)r_loop, elevation,
                                    tileTopScreenCoords[0], tileTopScreenCoords[1]),
                            this.camera, this.map));
                }
            }
        }
    }

    private void renderUI() { /* ... */
        defaultShader.setUniform("uIsFont", 1);
        defaultShader.setUniform("uHasTexture", 1);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        int yP=20,yI=18;
        Tile selT = (map != null) ? map.getTile(inputHandler.getSelectedRow(),inputHandler.getSelectedCol()) : null;
        String selI="Selected: ("+inputHandler.getSelectedRow()+", "+inputHandler.getSelectedCol()+")";
        if(selT!=null){selI+=" Elev: "+selT.getElevation()+" Type: "+selT.getType();}

        if (uiFont != null && uiFont.isInitialized()) {
            uiFont.drawText(10f, (float)yP, "Player: ("+player.getTileRow()+", "+player.getTileCol()+") Act: "+player.getCurrentAction()+" Dir: "+player.getCurrentDirection()+" F:"+player.getVisualFrameIndex()); yP+=yI;
            uiFont.drawText(10f, (float)yP, selI); yP+=yI;
            uiFont.drawText(10f, (float)yP, String.format("Camera: (%.1f, %.1f) Zoom: %.2f",camera.getCameraX(),camera.getCameraY(),camera.getZoom())); yP+=yI;
            uiFont.drawText(10f, (float)yP, "Move: Click | Elev Sel +/-: Q/E | Dig: J"); yP+=yI;
            uiFont.drawText(10f, (float)yP, "Levitate: F | Center Cam: C | Regen Map: G"); yP+=yI; yP+=yI;
            uiFont.drawText(10f, (float)yP, "Inventory:");yP+=yI;
            java.util.Map<String,Integer> inv=player.getInventory();
            if(inv.isEmpty()){
                uiFont.drawText(20f, (float)yP, "- Empty -"); yP+=yI;
            } else {
                for(java.util.Map.Entry<String,Integer>e:inv.entrySet()){
                    uiFont.drawText(20f, (float)yP, "- "+e.getKey()+": "+e.getValue()); yP+=yI;
                }
            }
        }
        checkError("Renderer: renderUI - after drawing text");
    }

    public void cleanup() { /* ... */
        if(playerTexture!=null)playerTexture.delete();
        if(treeTexture!=null)treeTexture.delete();
        if(tileAtlasTexture!=null) tileAtlasTexture.delete();
        if(uiFont!=null)uiFont.cleanup();
        if(defaultShader!=null)defaultShader.cleanup();
        if (mapChunks != null) { for (Chunk chunk : mapChunks) { chunk.cleanup(); } mapChunks.clear(); }
        if (spriteVaoId != 0) glDeleteVertexArrays(spriteVaoId);
        if (spriteVboId != 0) glDeleteBuffers(spriteVboId);
        if (spriteVertexBuffer != null) MemoryUtil.memFree(spriteVertexBuffer);
        System.out.println("Renderer: cleanup complete.");
        checkError("Renderer: cleanup");
    }

    private void checkError(String stage) { /* ... */
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            String errorMsg = "UNKNOWN GL ERROR (" + String.format("0x%X", error) + ")";
            switch (error) {
                case GL_INVALID_ENUM: errorMsg = "GL_INVALID_ENUM"; break;
                case GL_INVALID_VALUE: errorMsg = "GL_INVALID_VALUE"; break;
                case GL_INVALID_OPERATION: errorMsg = "GL_INVALID_OPERATION"; break;
                case GL_OUT_OF_MEMORY: errorMsg = "GL_OUT_OF_MEMORY"; break;
            }
            System.err.println("Renderer: OpenGL Error at stage '" + stage + "': " + errorMsg);
        }
    }
}
