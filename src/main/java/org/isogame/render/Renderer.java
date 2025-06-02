package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants;
import org.isogame.entitiy.PlayerModel;
import org.isogame.game.Game;
import org.isogame.input.InputHandler;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
import org.isogame.map.LightManager;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.isogame.ui.MenuItemButton;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.HashMap; // Changed from ArrayList to HashMap for mapChunks
import java.util.Iterator; // For removing chunks

import static org.isogame.constants.Constants.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

public class Renderer {
    private final CameraManager camera;
    private final Map map;
    private final PlayerModel player;
    private final InputHandler inputHandler;

    private Texture playerTexture;
    private Texture treeTexture;
    private Font uiFont;
    private Font titleFont;
    private Random tileDetailRandom;
    private java.util.Map<LightManager.ChunkCoordinate, Chunk> activeMapChunks;

    private Shader defaultShader;
    private Matrix4f projectionMatrix;
    private int spriteVaoId, spriteVboId;
    private FloatBuffer spriteVertexBuffer;

    private int hotbarVaoId, hotbarVboId;
    private FloatBuffer hotbarVertexDataBuffer;
    private int hotbarVertexCount = 0;
    private boolean hotbarDirty = true;

    private Texture tileAtlasTexture;
    private Texture mainMenuBackgroundTexture;

    public static final int FLOATS_PER_VERTEX_TERRAIN_TEXTURED = 10;
    public static final int FLOATS_PER_VERTEX_SPRITE_TEXTURED = 10;
    public static final int FLOATS_PER_VERTEX_UI_COLORED = 7;

    // ... (Atlas, Color, Z-Offset, Diamond Offset constants remain the same as your last version) ...
    public static final float ATLAS_TOTAL_WIDTH = 128.0f, ATLAS_TOTAL_HEIGHT = 128.0f;
    public static final float SUB_TEX_WIDTH = 64.0f, SUB_TEX_HEIGHT = 64.0f;
    public static final float GRASS_ATLAS_U0 = (0*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, GRASS_ATLAS_V0 = (0*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    public static final float GRASS_ATLAS_U1 = (1*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, GRASS_ATLAS_V1 = (1*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    public static final float DIRT_ATLAS_U0 = (0*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, DIRT_ATLAS_V0 = (0*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    public static final float DIRT_ATLAS_U1 = (1*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, DIRT_ATLAS_V1 = (1*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    public static final float ROCK_ATLAS_U0 = (0*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, ROCK_ATLAS_V0 = (1*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    public static final float ROCK_ATLAS_U1 = (1*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, ROCK_ATLAS_V1 = (2*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    public static final float SAND_ATLAS_U0 = (1*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, SAND_ATLAS_V0 = (1*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    public static final float SAND_ATLAS_U1 = (2*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, SAND_ATLAS_V1 = (2*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    public static final float DEFAULT_SIDE_U0 = (1*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, DEFAULT_SIDE_V0 = (0*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    public static final float DEFAULT_SIDE_U1 = (2*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, DEFAULT_SIDE_V1 = (1*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    public static final float SNOW_ATLAS_U0 = (0*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, SNOW_ATLAS_V0 = (1*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    public static final float SNOW_ATLAS_U1 = (1*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, SNOW_ATLAS_V1 = (2*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    public static final float SNOW_SIDE_ATLAS_U0 = ROCK_ATLAS_U0, SNOW_SIDE_ATLAS_V0 = ROCK_ATLAS_V0;
    public static final float SNOW_SIDE_ATLAS_U1 = ROCK_ATLAS_U1, SNOW_SIDE_ATLAS_V1 = ROCK_ATLAS_V1;


    private static final float SIDE_TEXTURE_DENSITY_FACTOR = 1.0f;
    private static final float DUMMY_U = 0.0f, DUMMY_V = 0.0f;
    private static final float[] SELECTED_TINT = {1.0f, 0.8f, 0.0f, 0.8f};
    private static final float[] DIRT_TOP_COLOR = {0.6f, 0.4f, 0.2f, 1.0f};
    private static final float[] WATER_TOP_COLOR = {0.05f, 0.25f, 0.5f, 0.85f};
    private static final float[] SAND_TOP_COLOR = {0.82f,0.7f,0.55f,1f};
    private static final float[] GRASS_TOP_COLOR = {0.20f,0.45f,0.10f,1f};
    private static final float[] ROCK_TOP_COLOR = {0.45f,0.45f,0.45f,1f};
    private static final float[] SNOW_TOP_COLOR = {0.95f,0.95f,1.0f,1f};
    private static final float[] DEFAULT_TOP_COLOR = {1f,0f,1f,1f};
    private static final float[] WHITE_TINT = {1.0f, 1.0f, 1.0f, 1.0f};

    private static final float Z_OFFSET_SPRITE_PLAYER = +0.1f;
    private static final float Z_OFFSET_SPRITE_TREE = +0.05f;
    private static final float Z_OFFSET_TILE_TOP_SURFACE = 0.0f;
    private static final float Z_OFFSET_TILE_SIDES = 0.01f;
    private static final float Z_OFFSET_TILE_PEDESTAL = 0.02f;
    private static final float Z_OFFSET_UI_BACKGROUND = 0.05f;
    private static final float Z_OFFSET_UI_PANEL = 0.04f;
    private static final float Z_OFFSET_UI_BORDER = 0.03f;
    private static final float Z_OFFSET_UI_ELEMENT = 0.02f;


    private final float tileHalfWidth = Constants.TILE_WIDTH / 2.0f;
    private final float tileHalfHeight = Constants.TILE_HEIGHT / 2.0f;
    private final float diamondTopOffsetY = -this.tileHalfHeight;
    private final float diamondLeftOffsetX = -this.tileHalfWidth;
    private final float diamondSideOffsetY = 0;
    private final float diamondRightOffsetX = this.tileHalfWidth;
    private final float diamondBottomOffsetY = this.tileHalfHeight;


    private int uiColoredVaoId, uiColoredVboId;
    private FloatBuffer uiColoredVertexBuffer;

    public static class TreeData { /* ... same ... */
        public Tile.TreeVisualType treeVisualType;
        public float mapCol, mapRow;
        public int elevation;
        public TreeData(Tile.TreeVisualType type, float tc, float tr, int te) {
            this.treeVisualType = type; this.mapCol = tc; this.mapRow = tr; this.elevation = te;
        }
    }
    private List<Object> worldEntities = new ArrayList<>();


    public Renderer(CameraManager camera, Map map, PlayerModel player, InputHandler inputHandler) {
        this.camera = camera;
        this.map = map;
        this.player = player;
        this.inputHandler = inputHandler;
        this.tileDetailRandom = new Random();
        this.projectionMatrix = new Matrix4f();
        this.activeMapChunks = new HashMap<>();
        loadAssets();
        initShaders();
        initRenderObjects();
        initHotbarGLResources();
    }

    // ... (loadAssets, initShaders, initRenderObjects, initHotbarGLResources, setHotbarDirty, chunk management methods, onResize, determineTopSurfaceColor - as in previous correct version)
    private void loadAssets() {
        try {
            playerTexture = Texture.loadTexture("/org/isogame/render/textures/lpc_character.png");
            treeTexture = Texture.loadTexture("/org/isogame/render/textures/fruit-trees.png");
            tileAtlasTexture = Texture.loadTexture("/org/isogame/render/textures/textu.png");
            mainMenuBackgroundTexture = Texture.loadTexture("/org/isogame/render/textures/main_menu_background.png");
            if (mainMenuBackgroundTexture == null) System.err.println("Renderer WARNING: Failed to load main menu background texture.");

            uiFont = new Font("/org/isogame/render/fonts/PressStart2P-Regular.ttf", 16f, this);
            titleFont = new Font("/org/isogame/render/fonts/PressStart2P-Regular.ttf", 32f, this);

        } catch (Exception e) {
            System.err.println("Renderer CRITICAL: Error loading assets: " + e.getMessage());
            e.printStackTrace();
            playerTexture = (playerTexture != null && playerTexture.getId() != 0) ? playerTexture : null;
            treeTexture = (treeTexture != null && treeTexture.getId() != 0) ? treeTexture : null;
            tileAtlasTexture = (tileAtlasTexture != null && tileAtlasTexture.getId() != 0) ? tileAtlasTexture : null;
            mainMenuBackgroundTexture = (mainMenuBackgroundTexture != null && mainMenuBackgroundTexture.getId() != 0) ? mainMenuBackgroundTexture : null;
            uiFont = (uiFont != null && uiFont.isInitialized()) ? uiFont : null;
            titleFont = (titleFont != null && titleFont.isInitialized()) ? titleFont : null;
        }
    }


    private void initShaders() {
        try {
            defaultShader = new Shader();
            defaultShader.createVertexShader(Shader.loadResource("/org/isogame/render/shaders/vertex.glsl"));
            defaultShader.createFragmentShader(Shader.loadResource("/org/isogame/render/shaders/fragment.glsl"));
            defaultShader.link();
            defaultShader.createUniform("uProjectionMatrix");
            defaultShader.createUniform("uModelViewMatrix");
            defaultShader.createUniform("uTextureSampler");
            defaultShader.createUniform("uHasTexture");
            defaultShader.createUniform("uIsFont");
        } catch (Exception e) {
            System.err.println("Renderer CRITICAL: Error initializing shaders: " + e.getMessage());
            throw new RuntimeException("Failed to init shaders", e);
        }
    }

    private void initRenderObjects() {
        spriteVaoId = glGenVertexArrays();
        glBindVertexArray(spriteVaoId);
        spriteVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        int initialSpriteBufferCapacityFloats = 2048 * 6 * FLOATS_PER_VERTEX_SPRITE_TEXTURED;
        spriteVertexBuffer = MemoryUtil.memAllocFloat(initialSpriteBufferCapacityFloats);
        glBufferData(GL_ARRAY_BUFFER, (long) spriteVertexBuffer.capacity() * Float.BYTES, GL_DYNAMIC_DRAW);

        int spriteStride = FLOATS_PER_VERTEX_SPRITE_TEXTURED * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, spriteStride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, spriteStride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, spriteStride, (3 + 4) * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, spriteStride, (3+4+2) * Float.BYTES);
        glEnableVertexAttribArray(3);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }


    private void initHotbarGLResources() {
        hotbarVaoId = glGenVertexArrays();
        glBindVertexArray(hotbarVaoId);
        hotbarVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, hotbarVboId);

        int maxHotbarVertices = Constants.HOTBAR_SIZE * 3 * 6;
        int hotbarBufferCapacityFloats = maxHotbarVertices * FLOATS_PER_VERTEX_UI_COLORED;
        if (hotbarVertexDataBuffer != null) {
            MemoryUtil.memFree(hotbarVertexDataBuffer);
        }
        hotbarVertexDataBuffer = MemoryUtil.memAllocFloat(hotbarBufferCapacityFloats);
        glBufferData(GL_ARRAY_BUFFER, (long)hotbarBufferCapacityFloats * Float.BYTES, GL_DYNAMIC_DRAW);

        int stride = FLOATS_PER_VERTEX_UI_COLORED * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        hotbarDirty = true;
    }

    public void setHotbarDirty(boolean dirty) {
        this.hotbarDirty = dirty;
    }

    public void ensureChunkGraphicsLoaded(int chunkGridX, int chunkGridY) {
        if (map == null || camera == null) return;
        LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(chunkGridX, chunkGridY);
        Chunk chunk = activeMapChunks.get(coord);
        if (chunk == null) {
            chunk = new Chunk(chunkGridX, chunkGridY, Constants.CHUNK_SIZE_TILES);
            chunk.setupGLResources();
            activeMapChunks.put(coord, chunk);
        }
        chunk.uploadGeometry(this.map, this.inputHandler, this, camera);
    }

    public boolean isChunkGraphicsLoaded(int chunkGridX, int chunkGridY) {
        return activeMapChunks.containsKey(new LightManager.ChunkCoordinate(chunkGridX, chunkGridY));
    }

    public void unloadChunkGraphics(int chunkGridX, int chunkGridY) {
        LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(chunkGridX, chunkGridY);
        Chunk chunk = activeMapChunks.remove(coord);
        if (chunk != null) {
            chunk.cleanup();
        }
    }

    public void updateChunkByGridCoords(int chunkGridX, int chunkGridY) {
        if (map == null || camera == null) return;
        LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(chunkGridX, chunkGridY);
        Chunk chunk = activeMapChunks.get(coord);
        if (chunk != null) {
            chunk.uploadGeometry(this.map, this.inputHandler, this, camera);
        } else {
            ensureChunkGraphicsLoaded(chunkGridX, chunkGridY);
        }
    }

    @Deprecated
    public void uploadTileMapGeometry() {
        for (Chunk chunk : activeMapChunks.values()) {
            chunk.uploadGeometry(map, inputHandler, this, camera);
        }
    }


    public void onResize(int fbW, int fbH) {
        if (fbW <= 0 || fbH <= 0) return;
        glViewport(0, 0, fbW, fbH);
        projectionMatrix.identity().ortho(0, fbW, fbH, 0, -2000.0f, 2000.0f);
        if (camera != null) {
            camera.setProjectionMatrixForCulling(projectionMatrix);
            camera.forceUpdateViewMatrix();
        }
    }

    private float[] determineTopSurfaceColor(Tile.TileType surfaceType, boolean isSelected) {
        if (isSelected) {
            float pulseFactor = (float) (Math.sin(org.lwjgl.glfw.GLFW.glfwGetTime() * 6.0) + 1.0) / 2.0f;
            float baseAlpha = SELECTED_TINT[3];
            float minPulseAlpha = baseAlpha * 0.5f;
            float animatedAlpha = minPulseAlpha + (baseAlpha - minPulseAlpha) * pulseFactor;
            return new float[]{SELECTED_TINT[0], SELECTED_TINT[1], SELECTED_TINT[2], animatedAlpha};
        }
        switch (surfaceType) {
            case WATER: return WATER_TOP_COLOR;
            case SAND:  return SAND_TOP_COLOR;
            case GRASS: return GRASS_TOP_COLOR;
            case ROCK:  return ROCK_TOP_COLOR;
            case DIRT:  return DIRT_TOP_COLOR;
            case SNOW:  return SNOW_TOP_COLOR;
            case AIR:   return DEFAULT_TOP_COLOR;
            default:    return DEFAULT_TOP_COLOR;
        }
    }

    /**
     * NEW: Changed to accept FloatBuffer directly.
     */
    private void addVertexToBuffer(FloatBuffer vertexBuffer, float x, float y, float z, float[] color, float u, float v, float light) {
        vertexBuffer.put(x).put(y).put(z);
        vertexBuffer.put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        vertexBuffer.put(u).put(v);
        vertexBuffer.put(light);
    }

    /**
     * MODIFIED: Now accepts FloatBuffer for vertex data collection.
     */
    public int addSingleTileVerticesToList_WorldSpace_ForChunk(
            int tileR_map, int tileC_map, Tile tile, boolean isSelected,
            FloatBuffer vertexBuffer, // MODIFIED: Changed from List<Float>
            float[] chunkBoundsMinMax) {

        if (tile.getType() == Tile.TileType.AIR) {
            return 0;
        }
        int currentTileElevation = tile.getElevation();
        Tile.TileType currentTileTopSurfaceType = tile.getType();
        final float tileGridPlaneCenterX = (tileC_map - tileR_map) * this.tileHalfWidth;
        final float tileGridPlaneCenterY = (tileC_map + tileR_map) * this.tileHalfHeight;
        final float tileBaseZ = (tileR_map + tileC_map) * DEPTH_SORT_FACTOR + (currentTileElevation * 0.005f);
        final float tileTopSurfaceZ = tileBaseZ + Z_OFFSET_TILE_TOP_SURFACE;
        float[] topSurfaceColor = determineTopSurfaceColor(currentTileTopSurfaceType, isSelected);
        float[] sideTintToUse = isSelected ? topSurfaceColor : WHITE_TINT;
        int verticesAddedCount = 0; // This will track vertices for this tile only
        float normalizedLightValue = tile.getFinalLightLevel() / (float) MAX_LIGHT_LEVEL;

        if (currentTileTopSurfaceType != Tile.TileType.WATER) {
            verticesAddedCount += addPedestalSidesToList(
                    vertexBuffer, tileGridPlaneCenterX, tileGridPlaneCenterY, tileBaseZ + Z_OFFSET_TILE_PEDESTAL,
                    sideTintToUse, normalizedLightValue);
        }
        float currentTileTopSurfaceActualY = tileGridPlaneCenterY - (currentTileElevation * TILE_THICKNESS);
        if (currentTileTopSurfaceType == Tile.TileType.WATER) {
            currentTileTopSurfaceActualY = tileGridPlaneCenterY - (Math.max(NIVEL_MAR, currentTileElevation) * TILE_THICKNESS);
        }
        verticesAddedCount += addTopSurfaceToList(
                vertexBuffer, currentTileTopSurfaceType, isSelected,
                tileGridPlaneCenterX, currentTileTopSurfaceActualY, tileTopSurfaceZ,
                topSurfaceColor, WHITE_TINT, normalizedLightValue);
        if (currentTileElevation > 0 && currentTileTopSurfaceType != Tile.TileType.WATER) {
            verticesAddedCount += addStratifiedElevatedSidesToList(
                    vertexBuffer, currentTileElevation,
                    tileGridPlaneCenterX, tileGridPlaneCenterY, tileBaseZ + Z_OFFSET_TILE_SIDES,
                    (float)TILE_THICKNESS,
                    sideTintToUse, normalizedLightValue,
                    tileR_map, tileC_map);
        }
        updateChunkBounds(chunkBoundsMinMax, tileGridPlaneCenterX, tileGridPlaneCenterY,
                currentTileElevation, TILE_THICKNESS,
                this.diamondLeftOffsetX, this.diamondRightOffsetX, this.diamondTopOffsetY, this.diamondBottomOffsetY);
        return verticesAddedCount;
    }

    private int addPedestalSidesToList(FloatBuffer vertexBuffer, // MODIFIED
                                       float tileCenterX, float gridPlaneY, float worldZ,
                                       float[] tint, float lightVal) {
        // ... (Logic remains same, calls addVertexToBuffer instead of addVertexToList) ...
        int vCount = 0;
        float pedestalTopY = gridPlaneY;
        float pedestalBottomY = gridPlaneY + BASE_THICKNESS;
        float pTopLx = tileCenterX + this.diamondLeftOffsetX, pTopLy = pedestalTopY + this.diamondSideOffsetY;
        float pTopRx = tileCenterX + this.diamondRightOffsetX, pTopRy = pedestalTopY + this.diamondSideOffsetY;
        float pTopBx = tileCenterX,                               pTopBy = pedestalTopY + this.diamondBottomOffsetY;
        float pBotLx = tileCenterX + this.diamondLeftOffsetX, pBotLy = pedestalBottomY + this.diamondSideOffsetY;
        float pBotRx = tileCenterX + this.diamondRightOffsetX, pBotRy = pedestalBottomY + this.diamondSideOffsetY;
        float pBotBx = tileCenterX,                               pBotBy = pedestalBottomY + this.diamondBottomOffsetY;
        float u0 = DEFAULT_SIDE_U0, v0 = DEFAULT_SIDE_V0, u1 = DEFAULT_SIDE_U1, vSpan = DEFAULT_SIDE_V1 - v0;
        float vRepeats = (BASE_THICKNESS / (float) TILE_HEIGHT) * SIDE_TEXTURE_DENSITY_FACTOR;
        float vBotTex = v0 + vSpan * vRepeats;

        addVertexToBuffer(vertexBuffer, pTopLx, pTopLy, worldZ, tint, u0, v0, lightVal);
        addVertexToBuffer(vertexBuffer, pBotLx, pBotLy, worldZ, tint, u0, vBotTex, lightVal);
        addVertexToBuffer(vertexBuffer, pTopBx, pTopBy, worldZ, tint, u1, v0, lightVal);
        vCount += 3;
        addVertexToBuffer(vertexBuffer, pTopBx, pTopBy, worldZ, tint, u1, v0, lightVal);
        addVertexToBuffer(vertexBuffer, pBotLx, pBotLy, worldZ, tint, u0, vBotTex, lightVal);
        addVertexToBuffer(vertexBuffer, pBotBx, pBotBy, worldZ, tint, u1, vBotTex, lightVal);
        vCount += 3;
        addVertexToBuffer(vertexBuffer, pTopBx, pTopBy, worldZ, tint, u0, v0, lightVal);
        addVertexToBuffer(vertexBuffer, pBotBx, pBotBy, worldZ, tint, u0, vBotTex, lightVal);
        addVertexToBuffer(vertexBuffer, pTopRx, pTopRy, worldZ, tint, u1, v0, lightVal);
        vCount += 3;
        addVertexToBuffer(vertexBuffer, pTopRx, pTopRy, worldZ, tint, u1, v0, lightVal);
        addVertexToBuffer(vertexBuffer, pBotBx, pBotBy, worldZ, tint, u0, vBotTex, lightVal);
        addVertexToBuffer(vertexBuffer, pBotRx, pBotRy, worldZ, tint, u1, vBotTex, lightVal);
        vCount += 3;
        return vCount;
    }

    public Font getTitleFont() { return titleFont; }

    private int addTopSurfaceToList(FloatBuffer vertexBuffer, // MODIFIED
                                    Tile.TileType topSurfaceType, boolean isSelected,
                                    float topCenterX, float topCenterY, float worldZ,
                                    float[] actualTopColor, float[] whiteTint, float lightVal) {
        // ... (Logic remains same, calls addVertexToBuffer instead of addVertexToList) ...
        int vCount = 0;
        float topLx = topCenterX + this.diamondLeftOffsetX,  topLy = topCenterY + this.diamondSideOffsetY;
        float topRx = topCenterX + this.diamondRightOffsetX, topRy = topCenterY + this.diamondSideOffsetY;
        float topTx = topCenterX,                            topTy = topCenterY + this.diamondTopOffsetY;
        float topBx = topCenterX,                            topBy = topCenterY + this.diamondBottomOffsetY;
        float[] colorToUse = actualTopColor;
        boolean textureTop = false;
        float u0 = DUMMY_U, v0 = DUMMY_V, u1 = DUMMY_U, v1Atlas = DUMMY_V;

        if (topSurfaceType != Tile.TileType.WATER && topSurfaceType != Tile.TileType.AIR) {
            textureTop = true;
            switch (topSurfaceType) {
                case GRASS: u0 = GRASS_ATLAS_U0; v0 = GRASS_ATLAS_V0; u1 = GRASS_ATLAS_U1; v1Atlas = GRASS_ATLAS_V1; break;
                case DIRT:  u0 = DIRT_ATLAS_U0;  v0 = DIRT_ATLAS_V0;  u1 = DIRT_ATLAS_U1;  v1Atlas = DIRT_ATLAS_V1;  break;
                case SAND:  u0 = SAND_ATLAS_U0;  v0 = SAND_ATLAS_V0;  u1 = SAND_ATLAS_U1;  v1Atlas = SAND_ATLAS_V1;  break;
                case ROCK:  u0 = ROCK_ATLAS_U0;  v0 = ROCK_ATLAS_V0;  u1 = ROCK_ATLAS_U1;  v1Atlas = ROCK_ATLAS_V1;  break;
                case SNOW:  u0 = SNOW_ATLAS_U0;  v0 = SNOW_ATLAS_V0;  u1 = SNOW_ATLAS_U1;  v1Atlas = SNOW_ATLAS_V1;  break;
                default: textureTop = false; break;
            }
            if (textureTop && !isSelected) {
                colorToUse = whiteTint;
            }
        }

        if (textureTop) {
            float midU = (u0 + u1) / 2f, midV = (v0 + v1Atlas) / 2f;
            addVertexToBuffer(vertexBuffer, topTx, topTy, worldZ, colorToUse, midU, v0, lightVal);
            addVertexToBuffer(vertexBuffer, topLx, topLy, worldZ, colorToUse, u0, midV, lightVal);
            addVertexToBuffer(vertexBuffer, topBx, topBy, worldZ, colorToUse, midU, v1Atlas, lightVal);
            vCount += 3;
            addVertexToBuffer(vertexBuffer, topTx, topTy, worldZ, colorToUse, midU, v0, lightVal);
            addVertexToBuffer(vertexBuffer, topBx, topBy, worldZ, colorToUse, midU, v1Atlas, lightVal);
            addVertexToBuffer(vertexBuffer, topRx, topRy, worldZ, colorToUse, u1, midV, lightVal);
            vCount += 3;
        } else {
            addVertexToBuffer(vertexBuffer, topTx, topTy, worldZ, colorToUse, DUMMY_U, DUMMY_V, lightVal);
            addVertexToBuffer(vertexBuffer, topLx, topLy, worldZ, colorToUse, DUMMY_U, DUMMY_V, lightVal);
            addVertexToBuffer(vertexBuffer, topBx, topBy, worldZ, colorToUse, DUMMY_U, DUMMY_V, lightVal);
            vCount += 3;
            addVertexToBuffer(vertexBuffer, topTx, topTy, worldZ, colorToUse, DUMMY_U, DUMMY_V, lightVal);
            addVertexToBuffer(vertexBuffer, topBx, topBy, worldZ, colorToUse, DUMMY_U, DUMMY_V, lightVal);
            addVertexToBuffer(vertexBuffer, topRx, topRy, worldZ, colorToUse, DUMMY_U, DUMMY_V, lightVal);
            vCount += 3;
        }
        return vCount;
    }

    private int addStratifiedElevatedSidesToList(FloatBuffer vertexBuffer, // MODIFIED
                                                 int totalElevationUnits,
                                                 float tileCenterX, float gridPlaneCenterY, float worldZ,
                                                 float elevSliceHeight, float[] tint, float initialLightVal,
                                                 int tileR_map, int tileC_map) {
        // ... (Logic remains same, including cliff heuristic, calls addVertexToBuffer) ...
        int vCount = 0;
        float sideLightVal = initialLightVal;
        LightManager lm = null;
        if (this.inputHandler != null && this.inputHandler.getGameInstance() != null) {
            lm = this.inputHandler.getGameInstance().getLightManager();
        }

        if (map != null && lm != null) {
            Tile topTileForColumn = map.getTile(tileR_map, tileC_map);
            if (topTileForColumn != null && topTileForColumn.getType() != Tile.TileType.WATER) {
                if (lm.isSurfaceTileExposedToSky(tileR_map, tileC_map, topTileForColumn.getElevation())) {
                    float directSkyContribution = lm.getCurrentGlobalSkyLightTarget() / (float)MAX_LIGHT_LEVEL;
                    float minLightFromSky = directSkyContribution * 0.65f;
                    sideLightVal = Math.max(sideLightVal, minLightFromSky);
                    sideLightVal = Math.min(sideLightVal, directSkyContribution);
                } else {
                    float ambientForSides = lm.getCurrentGlobalSkyLightTarget() * 0.20f / (float)MAX_LIGHT_LEVEL;
                    sideLightVal = Math.max(sideLightVal, ambientForSides);
                }
            }
        }
        sideLightVal = Math.max(0.05f, sideLightVal);

        for (int elevUnit = 1; elevUnit <= totalElevationUnits; elevUnit++) {
            float u0 = DEFAULT_SIDE_U0, v0 = DEFAULT_SIDE_V0, u1 = DEFAULT_SIDE_U1, v1Atlas = DEFAULT_SIDE_V1;
            float vSpanAtlas = v1Atlas - v0;
            float vTopTex = v0;
            float vBotTex = v0 + vSpanAtlas * SIDE_TEXTURE_DENSITY_FACTOR;
            float sliceTopActualY    = gridPlaneCenterY - (elevUnit * elevSliceHeight);
            float sliceBottomActualY = gridPlaneCenterY - ((elevUnit - 1) * elevSliceHeight);
            float sTopLx = tileCenterX + this.diamondLeftOffsetX,  sTopLy = sliceTopActualY + this.diamondSideOffsetY;
            float sTopRx = tileCenterX + this.diamondRightOffsetX, sTopRy = sliceTopActualY + this.diamondSideOffsetY;
            float sTopBx = tileCenterX,                             sTopBy = sliceTopActualY + this.diamondBottomOffsetY;
            float sBotLx = tileCenterX + this.diamondLeftOffsetX,  sBotLy = sliceBottomActualY + this.diamondSideOffsetY;
            float sBotRx = tileCenterX + this.diamondRightOffsetX, sBotRy = sliceBottomActualY + this.diamondSideOffsetY;
            float sBotBx = tileCenterX,                             sBotBy = sliceBottomActualY + this.diamondBottomOffsetY;

            addVertexToBuffer(vertexBuffer, sTopLx, sTopLy, worldZ, tint, u0, vTopTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sBotLx, sBotLy, worldZ, tint, u0, vBotTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sTopBx, sTopBy, worldZ, tint, u1, vTopTex, sideLightVal);
            vCount += 3;
            addVertexToBuffer(vertexBuffer, sTopBx, sTopBy, worldZ, tint, u1, vTopTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sBotLx, sBotLy, worldZ, tint, u0, vBotTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sBotBx, sBotBy, worldZ, tint, u1, vBotTex, sideLightVal);
            vCount += 3;
            addVertexToBuffer(vertexBuffer, sTopBx, sTopBy, worldZ, tint, u0, vTopTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sBotBx, sBotBy, worldZ, tint, u0, vBotTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sTopRx, sTopRy, worldZ, tint, u1, vTopTex, sideLightVal);
            vCount += 3;
            addVertexToBuffer(vertexBuffer, sTopRx, sTopRy, worldZ, tint, u1, vTopTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sBotBx, sBotBy, worldZ, tint, u0, vBotTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sBotRx, sBotRy, worldZ, tint, u1, vBotTex, sideLightVal);
            vCount += 3;
        }
        return vCount;
    }

    // ... (rest of Renderer.java as in previous correct version, including sprite/UI rendering)
    private void updateChunkBounds(float[] chunkBounds, float tileCenterX, float tileCenterY,
                                   int elevUnits, float elevSliceH,
                                   float dLX, float dRX, float dTY, float dBY) {
        float minX = tileCenterX + dLX;
        float maxX = tileCenterX + dRX;
        float minY = tileCenterY - (elevUnits * elevSliceH) + dTY;
        float maxY = tileCenterY + BASE_THICKNESS + dBY;
        chunkBounds[0] = Math.min(chunkBounds[0], minX);
        chunkBounds[1] = Math.min(chunkBounds[1], minY);
        chunkBounds[2] = Math.max(chunkBounds[2], maxX);
        chunkBounds[3] = Math.max(chunkBounds[3], maxY);
    }

    private void collectWorldEntities() {
        worldEntities.clear();
        if (player != null) {
            worldEntities.add(player);
        }
        if (activeMapChunks != null && !activeMapChunks.isEmpty() && camera != null) {
            for (Chunk chunk : activeMapChunks.values()) {
                if (camera.isChunkVisible(chunk.getBoundingBox())) {
                    worldEntities.addAll(chunk.getTreesInChunk());
                }
            }
        }
    }

    private void addVertexToSpriteBuffer(FloatBuffer buffer, float x, float y, float z, float[] color, float u, float v, float light) {
        buffer.put(x).put(y).put(z).put(color).put(u).put(v).put(light);
    }

    private int addPlayerVerticesToBuffer_WorldSpace(PlayerModel p, FloatBuffer buffer) {
        if (playerTexture == null || camera == null || map == null || playerTexture.getWidth() == 0) return 0;
        float pR=p.getVisualRow(), pC=p.getVisualCol();
        Tile tile = map.getTile(p.getTileRow(), p.getTileCol());
        int elev = (tile!=null) ? tile.getElevation() : 0;
        float lightVal = (tile!=null) ? tile.getFinalLightLevel()/(float)MAX_LIGHT_LEVEL : 1.0f;
        float pIsoX=(pC-pR)*(this.tileHalfWidth);
        float pIsoY=(pC+pR)*(this.tileHalfHeight)-(elev*TILE_THICKNESS);
        float logicalPR = p.getMapRow();
        float logicalPC = p.getMapCol();
        float tileLogicalZ = (logicalPR + logicalPC) * DEPTH_SORT_FACTOR + (elev * 0.005f);
        float playerWorldZ = tileLogicalZ + Z_OFFSET_SPRITE_PLAYER;
        if(p.isLevitating()) pIsoY -= (Math.sin(p.getLevitateTimer()*5f)*8);
        float hPW = PLAYER_WORLD_RENDER_WIDTH/2.0f;
        float xBL=pIsoX-hPW, yBL=pIsoY; float xTL=pIsoX-hPW, yTL=pIsoY-PLAYER_WORLD_RENDER_HEIGHT;
        float xTR=pIsoX+hPW, yTR=pIsoY-PLAYER_WORLD_RENDER_HEIGHT; float xBR=pIsoX+hPW, yBR=pIsoY;
        int animCol=p.getVisualFrameIndex(), animRow=p.getAnimationRow();
        float texU0=(animCol*(float)PlayerModel.FRAME_WIDTH)/playerTexture.getWidth();
        float texV0=(animRow*(float)PlayerModel.FRAME_HEIGHT)/playerTexture.getHeight();
        float texU1=((animCol+1)*(float)PlayerModel.FRAME_WIDTH)/playerTexture.getWidth();
        float texV1=((animRow+1)*(float)PlayerModel.FRAME_HEIGHT)/playerTexture.getHeight();
        addVertexToSpriteBuffer(buffer, xTL, yTL, playerWorldZ, WHITE_TINT, texU0, texV0, lightVal);
        addVertexToSpriteBuffer(buffer, xBL, yBL, playerWorldZ, WHITE_TINT, texU0, texV1, lightVal);
        addVertexToSpriteBuffer(buffer, xTR, yTR, playerWorldZ, WHITE_TINT, texU1, texV0, lightVal);
        addVertexToSpriteBuffer(buffer, xTR, yTR, playerWorldZ, WHITE_TINT, texU1, texV0, lightVal);
        addVertexToSpriteBuffer(buffer, xBL, yBL, playerWorldZ, WHITE_TINT, texU0, texV1, lightVal);
        addVertexToSpriteBuffer(buffer, xBR, yBR, playerWorldZ, WHITE_TINT, texU1, texV1, lightVal);
        return 6;
    }
    private int addTreeVerticesToBuffer_WorldSpace(TreeData tree, FloatBuffer buffer) {
        if (treeTexture == null || tree.treeVisualType == Tile.TreeVisualType.NONE || camera == null || map == null || treeTexture.getWidth() == 0) return 0;
        float tR=tree.mapRow, tC=tree.mapCol;
        int elev=tree.elevation;
        Tile tile = map.getTile(Math.round(tR), Math.round(tC));
        float lightVal = (tile!=null) ? tile.getFinalLightLevel()/(float)MAX_LIGHT_LEVEL : 1.0f;
        float tBaseIsoX=(tC-tR)*(this.tileHalfWidth);
        float tBaseIsoY=(tC+tR)*(this.tileHalfHeight)-(elev*TILE_THICKNESS);
        float tileLogicalZ = (tR + tC) * DEPTH_SORT_FACTOR + (elev * 0.005f);
        float treeWorldZ = tileLogicalZ + Z_OFFSET_SPRITE_TREE;
        float frameW=0,frameH=0,atlasU0val=0,atlasV0val=0,rendW,rendH,anchorYOff;
        switch(tree.treeVisualType){
            case APPLE_TREE_FRUITING:
                frameW=90;frameH=130;atlasU0val=0;atlasV0val=0;
                rendW=TILE_WIDTH*1.0f;rendH=rendW*(frameH/frameW);
                anchorYOff=TILE_HEIGHT*0.15f;
                break;
            case PINE_TREE_SMALL:
                frameW=90;frameH=130;atlasU0val=90;atlasV0val=0;
                rendW=TILE_WIDTH*1.0f;rendH=rendW*(frameH/frameW);
                anchorYOff=TILE_HEIGHT*0.1f;
                break;
            default: return 0;
        }
        float tFinalIsoX = tBaseIsoX;
        float tFinalIsoY = tBaseIsoY;
        float texU0=atlasU0val/treeTexture.getWidth(),texV0=atlasV0val/treeTexture.getHeight();
        float texU1=(atlasU0val+frameW)/treeTexture.getWidth(),texV1=(atlasV0val+frameH)/treeTexture.getHeight();
        float hTW=rendW/2.0f;
        float yTop=tFinalIsoY-(rendH-anchorYOff);
        float yBot=tFinalIsoY+anchorYOff;
        float xBL=tFinalIsoX-hTW,yBL=yBot; float xTL=tFinalIsoX-hTW,yTL=yTop;
        float xTR=tFinalIsoX+hTW,yTR=yTop; float xBR=tFinalIsoX+hTW,yBR=yBot;
        addVertexToSpriteBuffer(buffer, xTL, yTL, treeWorldZ, WHITE_TINT, texU0, texV0, lightVal);
        addVertexToSpriteBuffer(buffer, xBL, yBL, treeWorldZ, WHITE_TINT, texU0, texV1, lightVal);
        addVertexToSpriteBuffer(buffer, xTR, yTR, treeWorldZ, WHITE_TINT, texU1, texV0, lightVal);
        addVertexToSpriteBuffer(buffer, xTR, yTR, treeWorldZ, WHITE_TINT, texU1, texV0, lightVal);
        addVertexToSpriteBuffer(buffer, xBL, yBL, treeWorldZ, WHITE_TINT, texU0, texV1, lightVal);
        addVertexToSpriteBuffer(buffer, xBR, yBR, treeWorldZ, WHITE_TINT, texU1, texV1, lightVal);
        return 6;
    }

    public void render() {
        if (defaultShader == null || camera == null) return;
        defaultShader.bind();
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
        if (activeMapChunks != null && !activeMapChunks.isEmpty()) {
            for (Chunk chunk : activeMapChunks.values()) {
                if (camera.isChunkVisible(chunk.getBoundingBox())) {
                    chunk.render();
                }
            }
        }
        if (tileAtlasTexture != null && tileAtlasTexture.getId() != 0) tileAtlasTexture.unbind();
        collectWorldEntities();
        if (spriteVaoId != 0 && !worldEntities.isEmpty()) {
            glBindVertexArray(spriteVaoId);
            glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
            spriteVertexBuffer.clear();
            int verticesInBatch = 0;
            Texture currentSpriteTexture = null;
            defaultShader.setUniform("uHasTexture", 1);
            defaultShader.setUniform("uTextureSampler", 0);
            for (Object entity : worldEntities) {
                int verticesAddedThisEntity = 0;
                Texture textureForThisEntity = null;
                if (entity instanceof PlayerModel) {
                    if (playerTexture != null && playerTexture.getId() != 0) {
                        verticesAddedThisEntity = addPlayerVerticesToBuffer_WorldSpace((PlayerModel) entity, spriteVertexBuffer);
                        textureForThisEntity = playerTexture;
                    }
                } else if (entity instanceof TreeData) {
                    if (treeTexture != null && treeTexture.getId() != 0) {
                        verticesAddedThisEntity = addTreeVerticesToBuffer_WorldSpace((TreeData) entity, spriteVertexBuffer);
                        textureForThisEntity = treeTexture;
                    }
                }
                if (verticesAddedThisEntity > 0 && textureForThisEntity != null) {
                    if (currentSpriteTexture == null) {
                        currentSpriteTexture = textureForThisEntity;
                    } else if (currentSpriteTexture.getId() != textureForThisEntity.getId()) {
                        if (verticesInBatch > 0) {
                            spriteVertexBuffer.flip();
                            glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                            currentSpriteTexture.bind();
                            glDrawArrays(GL_TRIANGLES, 0, verticesInBatch);
                            spriteVertexBuffer.clear();
                            verticesInBatch = 0;
                        }
                        currentSpriteTexture = textureForThisEntity;
                    }
                    verticesInBatch += verticesAddedThisEntity;
                    if (spriteVertexBuffer.remaining() < 6 * FLOATS_PER_VERTEX_SPRITE_TEXTURED) {
                        if (verticesInBatch > 0 && currentSpriteTexture != null) {
                            spriteVertexBuffer.flip();
                            glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                            currentSpriteTexture.bind();
                            glDrawArrays(GL_TRIANGLES, 0, verticesInBatch);
                            spriteVertexBuffer.clear();
                            verticesInBatch = 0;
                        }
                    }
                }
            }
            if (verticesInBatch > 0 && currentSpriteTexture != null) {
                spriteVertexBuffer.flip();
                glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                currentSpriteTexture.bind();
                glDrawArrays(GL_TRIANGLES, 0, verticesInBatch);
            }
            if (currentSpriteTexture != null) currentSpriteTexture.unbind();
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }
    }

    public void renderHotbar(PlayerModel player, int currentlySelectedHotbarSlot) { /* ... same as previous correct version ... */
        if (uiFont == null || !uiFont.isInitialized() || player == null || defaultShader == null || camera == null) return;
        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uHasTexture", 0);
        glBindVertexArray(hotbarVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, hotbarVboId);
        if (hotbarDirty) {
            hotbarVertexDataBuffer.clear();
            hotbarVertexCount = 0;
            float slotSize = 55f; float slotMargin = 6f; float itemRenderSize = slotSize * 0.75f;
            float itemOffset = (slotSize - itemRenderSize) / 2f;
            int hotbarSlotsToDisplay = Constants.HOTBAR_SIZE;
            float totalHotbarWidth = (hotbarSlotsToDisplay * slotSize) + ((Math.max(0, hotbarSlotsToDisplay - 1)) * slotMargin);
            float hotbarX = (camera.getScreenWidth() - totalHotbarWidth) / 2.0f;
            float hotbarY = camera.getScreenHeight() - slotSize - (slotMargin * 3);
            List<InventorySlot> playerInventorySlots = player.getInventorySlots();
            for (int i = 0; i < hotbarSlotsToDisplay; i++) {
                float currentSlotDrawX = hotbarX + i * (slotSize + slotMargin);
                InventorySlot slot = (i < playerInventorySlots.size()) ? playerInventorySlots.get(i) : null;
                boolean isSelected = (i == currentlySelectedHotbarSlot);
                boolean isEmpty = (slot == null || slot.isEmpty());
                float[] slotBgColor, slotBorderColor; float currentBorderWidth;
                if (isSelected) { slotBgColor = new float[]{0.55f,0.55f,0.3f,0.95f}; slotBorderColor = new float[]{0.9f,0.9f,0.5f,1.0f}; currentBorderWidth = 2.5f; }
                else if (!isEmpty) { slotBgColor = new float[]{0.35f,0.30f,0.20f,0.9f}; slotBorderColor = new float[]{0.20f,0.15f,0.10f,0.9f}; currentBorderWidth = 1.5f; }
                else { slotBgColor = new float[]{0.25f,0.25f,0.28f,0.8f}; slotBorderColor = new float[]{0.15f,0.15f,0.18f,0.85f}; currentBorderWidth = 1.0f; }
                if (currentBorderWidth > 0) {
                    float bx = currentSlotDrawX - currentBorderWidth; float by = hotbarY - currentBorderWidth;
                    float bWidth = slotSize + (2 * currentBorderWidth); float bHeight = slotSize + (2 * currentBorderWidth);
                    addQuadToUiBuffer(hotbarVertexDataBuffer, bx, by, bWidth, bHeight, Z_OFFSET_UI_BORDER, slotBorderColor);
                    hotbarVertexCount += 6;
                }
                float gradientFactor = 0.1f; float[] topBgColor = slotBgColor;
                float[] bottomBgColor = new float[]{ Math.max(0f, topBgColor[0]-gradientFactor), Math.max(0f, topBgColor[1]-gradientFactor), Math.max(0f, topBgColor[2]-gradientFactor), topBgColor[3]};
                addGradientQuadToUiBuffer(hotbarVertexDataBuffer, currentSlotDrawX, hotbarY, slotSize, slotSize, Z_OFFSET_UI_PANEL, topBgColor, bottomBgColor);
                hotbarVertexCount += 6;
                if (!isEmpty) {
                    Item item = slot.getItem(); float[] itemColor = item.getPlaceholderColor();
                    float itemX = currentSlotDrawX + itemOffset; float itemY = hotbarY + itemOffset;
                    addQuadToUiBuffer(hotbarVertexDataBuffer, itemX, itemY, itemRenderSize, itemRenderSize, Z_OFFSET_UI_ELEMENT, itemColor);
                    hotbarVertexCount += 6;
                }
            }
            hotbarVertexDataBuffer.flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, hotbarVertexDataBuffer);
            hotbarDirty = false;
        }
        if (hotbarVertexCount > 0) {
            glDrawArrays(GL_TRIANGLES, 0, hotbarVertexCount);
        }
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        float slotSize = 55f; float slotMargin = 6f;
        int hotbarSlotsToDisplay = Constants.HOTBAR_SIZE;
        float totalHotbarWidth = (hotbarSlotsToDisplay * slotSize) + ((Math.max(0, hotbarSlotsToDisplay - 1)) * slotMargin);
        float hotbarX = (camera.getScreenWidth() - totalHotbarWidth) / 2.0f;
        float hotbarY = camera.getScreenHeight() - slotSize - (slotMargin * 3);
        List<InventorySlot> playerInventorySlots = player.getInventorySlots();
        for (int i = 0; i < hotbarSlotsToDisplay; i++) {
            InventorySlot slot = (i < playerInventorySlots.size()) ? playerInventorySlots.get(i) : null;
            if (slot != null && !slot.isEmpty() && slot.getQuantity() > 1) {
                String quantityStr = String.valueOf(slot.getQuantity());
                float currentSlotDrawX = hotbarX + i * (slotSize + slotMargin);
                float qtyTextWidth = uiFont.getTextWidthScaled(quantityStr, 1.0f); float textPaddingFromEdge = 4f;
                float qtyTextX = currentSlotDrawX + slotSize - qtyTextWidth - textPaddingFromEdge;
                float qtyTextY = hotbarY + slotSize - textPaddingFromEdge;
                uiFont.drawText(qtyTextX, qtyTextY, quantityStr, 1f, 1f, 1f);
            }
        }
    }

    private void addQuadToUiBuffer(FloatBuffer buffer, float x, float y, float w, float h, float z, float[] color) {
        buffer.put(x).put(y).put(z).put(color);
        buffer.put(x).put(y + h).put(z).put(color);
        buffer.put(x + w).put(y).put(z).put(color);
        buffer.put(x + w).put(y).put(z).put(color);
        buffer.put(x).put(y + h).put(z).put(color);
        buffer.put(x + w).put(y + h).put(z).put(color);
    }

    private void addGradientQuadToUiBuffer(FloatBuffer buffer, float x, float y, float w, float h, float z, float[] topColor, float[] bottomColor) {
        buffer.put(x).put(y).put(z).put(topColor);
        buffer.put(x).put(y + h).put(z).put(bottomColor);
        buffer.put(x + w).put(y).put(z).put(topColor);
        buffer.put(x + w).put(y).put(z).put(topColor);
        buffer.put(x).put(y + h).put(z).put(bottomColor);
        buffer.put(x + w).put(y + h).put(z).put(bottomColor);
    }

    public void renderInventoryUI(PlayerModel player) { /* ... same as previous correct version ... */
        if (uiFont == null || !uiFont.isInitialized() || player == null || camera == null || defaultShader == null) return;
        Game game = (this.inputHandler != null) ? this.inputHandler.getGameInstance() : null;
        int selectedSlotIndex = (game != null) ? game.getSelectedInventorySlotIndex() : player.getSelectedHotbarSlotIndex();

        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());
        defaultShader.setUniform("uHasTexture", 0); defaultShader.setUniform("uIsFont", 0);

        int slotsPerRow = 5;
        float slotSize = 50f, slotMargin = 10f, itemRenderSize = slotSize * 0.7f, itemOffset = (slotSize - itemRenderSize) / 2f;
        List<InventorySlot> slots = player.getInventorySlots();
        int numRows = slots.isEmpty() ? 1 : (int) Math.ceil((double) slots.size() / slotsPerRow);
        float panelWidth = (slotsPerRow * slotSize) + ((slotsPerRow + 1) * slotMargin);
        float panelHeight = (numRows * slotSize) + ((numRows + 1) * slotMargin);
        float panelX = (camera.getScreenWidth() - panelWidth) / 2.0f;
        float panelY = (camera.getScreenHeight() - panelHeight) / 2.0f;

        glBindVertexArray(spriteVaoId); glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        spriteVertexBuffer.clear();

        float[] panelColor = {0.2f, 0.2f, 0.25f, 0.85f};
        // Using the general addQuadToUiBuffer now adapted for spriteVertexBuffer
        addQuadToUiBuffer(spriteVertexBuffer, panelX, panelY, panelWidth, panelHeight, Z_OFFSET_UI_PANEL, panelColor);
        spriteVertexBuffer.flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        float[] slotColorDefault = {0.4f, 0.4f, 0.45f, 0.9f};
        float[] slotColorSelected = {0.8f, 0.8f, 0.3f, 0.95f};
        float currentSlotDrawX = panelX + slotMargin;
        float currentSlotDrawY = panelY + slotMargin;
        int colCount = 0;

        for (int i = 0; i < slots.size(); i++) {
            InventorySlot slot = slots.get(i);
            float[] actualSlotColor = (i == selectedSlotIndex) ? slotColorSelected : slotColorDefault;
            spriteVertexBuffer.clear();
            int verticesForThisSlot = 0;
            addQuadToUiBuffer(spriteVertexBuffer, currentSlotDrawX, currentSlotDrawY, slotSize, slotSize, Z_OFFSET_UI_ELEMENT, actualSlotColor);
            verticesForThisSlot += 6;

            if (!slot.isEmpty()) {
                Item item = slot.getItem(); float[] itemColor = item.getPlaceholderColor();
                float itemX = currentSlotDrawX + itemOffset; float itemY = currentSlotDrawY + itemOffset;
                addQuadToUiBuffer(spriteVertexBuffer, itemX, itemY, itemRenderSize, itemRenderSize, Z_OFFSET_UI_ELEMENT + 0.001f, itemColor);
                verticesForThisSlot += 6;
            }
            if (verticesForThisSlot > 0) {
                spriteVertexBuffer.flip();
                glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                glDrawArrays(GL_TRIANGLES, 0, verticesForThisSlot);
            }
            currentSlotDrawX += slotSize + slotMargin; colCount++;
            if (colCount >= slotsPerRow) {
                colCount = 0; currentSlotDrawX = panelX + slotMargin; currentSlotDrawY += slotSize + slotMargin;
            }
        }
        currentSlotDrawX = panelX + slotMargin; currentSlotDrawY = panelY + slotMargin; colCount = 0;
        float textOffsetX = 5f;
        for (InventorySlot slot : slots) {
            if (!slot.isEmpty() && slot.getQuantity() > 1) {
                String quantityStr = String.valueOf(slot.getQuantity());
                float qtyTextWidth = uiFont.getTextWidthScaled(quantityStr, 1.0f);
                float qtyTextX = currentSlotDrawX + slotSize - qtyTextWidth - textOffsetX;
                float qtyTextY = currentSlotDrawY + slotSize - textOffsetX;
                uiFont.drawText(qtyTextX, qtyTextY, quantityStr, 1f, 1f, 1f);
            }
            currentSlotDrawX += slotSize + slotMargin; colCount++;
            if (colCount >= slotsPerRow) {
                colCount = 0; currentSlotDrawX = panelX + slotMargin; currentSlotDrawY += slotSize + slotMargin;
            }
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0); glBindVertexArray(0);
    }


    public Shader getDefaultShader() { return this.defaultShader; }

    public void renderDebugOverlay(float panelX, float panelY, float panelWidth, float panelHeight, List<String> lines) { /* ... Same as previous correct version ... */
        if (uiFont == null || !uiFont.isInitialized() || defaultShader == null) return;
        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());
        float[] bgColor = {0.1f, 0.1f, 0.1f, 0.8f}; float z = Z_OFFSET_UI_PANEL + 0.1f;

        glBindVertexArray(spriteVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        spriteVertexBuffer.clear();

        addQuadToUiBuffer(spriteVertexBuffer, panelX, panelY, panelWidth, panelHeight, z, bgColor);

        spriteVertexBuffer.flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
        defaultShader.setUniform("uHasTexture", 0);
        defaultShader.setUniform("uIsFont", 0);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        float textX = panelX + 5f;
        float textY = panelY + 5f;
        if (uiFont.getAscent() > 0) textY += uiFont.getAscent();

        for (String line : lines) {
            uiFont.drawText(textX, textY, line, 0.9f, 0.9f, 0.9f);
            textY += 18f;
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }
    public void renderMainMenuBackground() { /* ... Same as previous correct version ... */
        if (mainMenuBackgroundTexture == null || mainMenuBackgroundTexture.getId() == 0 || defaultShader == null || camera == null) return;
        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());
        defaultShader.setUniform("uHasTexture", 1);
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uTextureSampler", 0);
        glActiveTexture(GL_TEXTURE0);
        mainMenuBackgroundTexture.bind();
        glBindVertexArray(spriteVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        spriteVertexBuffer.clear();
        float screenWidth = camera.getScreenWidth();
        float screenHeight = camera.getScreenHeight();
        float dummyLight = 1f;
        addVertexToSpriteBuffer(spriteVertexBuffer, 0, 0, Z_OFFSET_UI_BACKGROUND, WHITE_TINT, 0f, 0f, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, 0, screenHeight, Z_OFFSET_UI_BACKGROUND, WHITE_TINT, 0f, 1f, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, screenWidth, 0, Z_OFFSET_UI_BACKGROUND, WHITE_TINT, 1f, 0f, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, screenWidth, 0, Z_OFFSET_UI_BACKGROUND, WHITE_TINT, 1f, 0f, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, 0, screenHeight, Z_OFFSET_UI_BACKGROUND, WHITE_TINT, 0f, 1f, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, screenWidth, screenHeight, Z_OFFSET_UI_BACKGROUND, WHITE_TINT, 1f, 1f, dummyLight);
        spriteVertexBuffer.flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        mainMenuBackgroundTexture.unbind();
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    public void renderMenuButton(MenuItemButton button) { /* ... Same as previous correct version ... */
        if (uiFont == null || !uiFont.isInitialized() || defaultShader == null || camera == null) {
            System.err.println("Renderer: Cannot render menu button, font, shader or camera not ready.");
            return;
        }

        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());
        defaultShader.setUniform("uIsFont", 0);

        glBindVertexArray(spriteVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);

        float dummyLight = 1f;

        if (button.borderWidth > 0 && button.borderColor != null) {
            spriteVertexBuffer.clear();
            defaultShader.setUniform("uHasTexture", 0);
            float bx = button.x - button.borderWidth;
            float by = button.y - button.borderWidth;
            float bWidth = button.width + (2 * button.borderWidth);
            float bHeight = button.height + (2 * button.borderWidth);
            addQuadToUiBuffer(spriteVertexBuffer, bx, by, bWidth, bHeight, Z_OFFSET_UI_BORDER, button.borderColor);
            spriteVertexBuffer.flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }

        spriteVertexBuffer.clear();
        int faceVerticesToDraw = 0;
        if (button.useTexture && tileAtlasTexture != null && tileAtlasTexture.getId() != 0) {
            defaultShader.setUniform("uHasTexture", 1);
            glActiveTexture(GL_TEXTURE0);
            tileAtlasTexture.bind();
            float[] tintToUse = button.isHovered ? new float[]{1.05f, 1.05f, 1.02f, 1.0f} : WHITE_TINT;
            float desiredRepeatCellWidth = 32f; float desiredRepeatCellHeight = 32f;
            int numCellsX = (int) Math.max(1, Math.ceil(button.width / desiredRepeatCellWidth));
            int numCellsY = (int) Math.max(1, Math.ceil(button.height / desiredRepeatCellHeight));
            float actualCellDrawWidth = button.width / numCellsX; float actualCellDrawHeight = button.height / numCellsY;
            float u0_tile = button.u0, v0_tile = button.v0; float u1_tile = button.u1, v1_tile = button.v1;

            for (int cellY = 0; cellY < numCellsY; cellY++) {
                for (int cellX = 0; cellX < numCellsX; cellX++) {
                    if (spriteVertexBuffer.remaining() < 6 * FLOATS_PER_VERTEX_SPRITE_TEXTURED) {
                        spriteVertexBuffer.flip(); glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                        glDrawArrays(GL_TRIANGLES, 0, faceVerticesToDraw);
                        spriteVertexBuffer.clear(); faceVerticesToDraw = 0;
                    }
                    float currentX = button.x + cellX * actualCellDrawWidth;
                    float currentY = button.y + cellY * actualCellDrawHeight;
                    spriteVertexBuffer.put(currentX).put(currentY).put(Z_OFFSET_UI_ELEMENT).put(tintToUse).put(u0_tile).put(v0_tile).put(dummyLight);
                    spriteVertexBuffer.put(currentX).put(currentY + actualCellDrawHeight).put(Z_OFFSET_UI_ELEMENT).put(tintToUse).put(u0_tile).put(v1_tile).put(dummyLight);
                    spriteVertexBuffer.put(currentX + actualCellDrawWidth).put(currentY).put(Z_OFFSET_UI_ELEMENT).put(tintToUse).put(u1_tile).put(v0_tile).put(dummyLight);
                    spriteVertexBuffer.put(currentX + actualCellDrawWidth).put(currentY).put(Z_OFFSET_UI_ELEMENT).put(tintToUse).put(u1_tile).put(v0_tile).put(dummyLight);
                    spriteVertexBuffer.put(currentX).put(currentY + actualCellDrawHeight).put(Z_OFFSET_UI_ELEMENT).put(tintToUse).put(u0_tile).put(v1_tile).put(dummyLight);
                    spriteVertexBuffer.put(currentX + actualCellDrawWidth).put(currentY + actualCellDrawHeight).put(Z_OFFSET_UI_ELEMENT).put(tintToUse).put(u1_tile).put(v1_tile).put(dummyLight);
                    faceVerticesToDraw += 6;
                }
            }
            if (tileAtlasTexture != null) tileAtlasTexture.unbind();
        } else {
            defaultShader.setUniform("uHasTexture", 0);
            float[] topQuadColor = button.isHovered ? button.hoverBackgroundColor : button.baseBackgroundColor;
            float gradientFactor = 0.15f;
            float[] bottomQuadColor = new float[]{
                    Math.max(0f, topQuadColor[0] - gradientFactor),
                    Math.max(0f, topQuadColor[1] - gradientFactor),
                    Math.max(0f, topQuadColor[2] - gradientFactor),
                    topQuadColor[3]};
            addGradientQuadToUiBuffer(spriteVertexBuffer, button.x, button.y, button.width, button.height, Z_OFFSET_UI_ELEMENT, topQuadColor, bottomQuadColor);
            faceVerticesToDraw += 6;
        }

        if (faceVerticesToDraw > 0) {
            spriteVertexBuffer.flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
            glDrawArrays(GL_TRIANGLES, 0, faceVerticesToDraw);
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        float[] currentTextColor = button.isHovered ? button.hoverTextColor : button.baseTextColor;
        float textWidth = uiFont.getTextWidthScaled(button.text, 1.0f);
        float textX = button.x + (button.width - textWidth) / 2f;
        float textY = button.y + button.height / 2f + uiFont.getAscent() / 2f -2f;
        if (uiFont != null && uiFont.isInitialized()) {
            uiFont.drawText(textX, textY, button.text, currentTextColor[0], currentTextColor[1], currentTextColor[2]);
        }
    }

    public void cleanup() {
        // ... (same as previous correct version)
        if(playerTexture!=null) playerTexture.delete(); playerTexture = null;
        if(treeTexture!=null) treeTexture.delete(); treeTexture = null;
        if(tileAtlasTexture!=null) tileAtlasTexture.delete(); tileAtlasTexture = null;
        if(mainMenuBackgroundTexture != null) mainMenuBackgroundTexture.delete(); mainMenuBackgroundTexture = null;
        if(uiFont!=null) uiFont.cleanup(); uiFont = null;
        if (titleFont != null) titleFont.cleanup(); titleFont = null;
        if(defaultShader!=null) defaultShader.cleanup(); defaultShader = null;

        if(activeMapChunks!=null) {
            for(Chunk ch : activeMapChunks.values()) ch.cleanup();
            activeMapChunks.clear();
        }
        if(spriteVaoId!=0) { glDeleteVertexArrays(spriteVaoId); spriteVaoId=0; }
        if(spriteVboId!=0) { glDeleteBuffers(spriteVboId); spriteVboId=0; }
        if(spriteVertexBuffer!=null) { MemoryUtil.memFree(spriteVertexBuffer); spriteVertexBuffer=null; }

        if(hotbarVaoId!=0) { glDeleteVertexArrays(hotbarVaoId); hotbarVaoId=0; }
        if(hotbarVboId!=0) { glDeleteBuffers(hotbarVboId); hotbarVboId=0; }
        if(hotbarVertexDataBuffer!=null) { MemoryUtil.memFree(hotbarVertexDataBuffer); hotbarVertexDataBuffer=null; }
    }
}