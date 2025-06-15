// File: Renderer.java

package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants;
import org.isogame.entity.*;
// import org.isogame.game.Game; // Removed to reduce coupling
import org.isogame.game.Game;
import org.isogame.input.InputHandler;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
import org.isogame.map.LightManager;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.isogame.ui.MenuItemButton;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.glfw.GLFW.*; // <<< ADD THIS LINE

import java.nio.FloatBuffer;
import java.util.*;

import static org.isogame.constants.Constants.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

public class Renderer {
    private final CameraManager camera;
    // These will be updated via setGameContext
    private Map map;
    private PlayerModel player;
    private InputHandler inputHandler; // Store InputHandler reference

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

    private int uiColoredVaoId, uiColoredVboId;
    private FloatBuffer uiColoredVertexBuffer;
    private static final int MAX_UI_COLORED_QUADS = 512;

    private Texture tileAtlasTexture;
    private Texture mainMenuBackgroundTexture;

    public static final int FLOATS_PER_VERTEX_TERRAIN_TEXTURED = 10;
    public static final int FLOATS_PER_VERTEX_SPRITE_TEXTURED = 10;
    public static final int FLOATS_PER_VERTEX_UI_COLORED = 7;

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


    // --- UPDATE THESE UVs FOR THE LOOSE ROCK if GIMP selection is correct ---
    public static final float LOOSE_ROCK_SPRITE_X_PIX = 25.0f;    // New X from GIMP
    public static final float LOOSE_ROCK_SPRITE_Y_PIX = 1550.0f;  // New Y from GIMP
    public static final float LOOSE_ROCK_SPRITE_W_PIX = 50.0f;    // Assuming still 32px wide
    public static final float LOOSE_ROCK_SPRITE_H_PIX = 50.0f;    // Assuming still 32px high

    // UVs for Rock Type 2
    public static final float LOOSE_ROCK_2_X_PIX = 75.0f;
    public static final float LOOSE_ROCK_2_Y_PIX = 1550.0f;
    // UVs for Rock Type 3
    public static final float LOOSE_ROCK_3_X_PIX = 125.0f;
    public static final float LOOSE_ROCK_3_Y_PIX = 1550.0f;
    // UVs for Rock Type 4
    public static final float LOOSE_ROCK_4_X_PIX = 175.0f;
    public static final float LOOSE_ROCK_4_Y_PIX = 1550.0f;
    // UVs for Rock Type 5
    public static final float LOOSE_ROCK_5_X_PIX = 225.0f;
    public static final float LOOSE_ROCK_5_Y_PIX = 1550.0f;
    // UVs for Rock Type 6
    public static final float LOOSE_ROCK_6_X_PIX = 275.0f;
    public static final float LOOSE_ROCK_6_Y_PIX = 1550.0f;



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




    private static final float Z_OFFSET_SPRITE_PLAYER = 0.1f;
    private static final float Z_OFFSET_SPRITE_ANIMAL = 0.09f;
    private static final float Z_OFFSET_SPRITE_TREE = 0.05f;
    private static final float Z_OFFSET_TILE_TOP_SURFACE = 0.0f;
    private static final float Z_OFFSET_TILE_SIDES = 0.01f;
    private static final float Z_OFFSET_TILE_PEDESTAL = 0.02f;
    private static final float Z_OFFSET_UI_BACKGROUND = 0.05f;
    private static final float Z_OFFSET_UI_PANEL = 0.04f;
    private static final float Z_OFFSET_UI_BORDER = 0.03f;
    private static final float Z_OFFSET_UI_ELEMENT = 0.02f;

    private final float tileHalfWidth = TILE_WIDTH / 2.0f;
    private final float tileHalfHeight = TILE_HEIGHT / 2.0f;
    private final float diamondTopOffsetY = -this.tileHalfHeight;
    private final float diamondLeftOffsetX = -this.tileHalfWidth;
    private final float diamondSideOffsetY = 0;
    private final float diamondRightOffsetX = this.tileHalfWidth;
    private final float diamondBottomOffsetY = this.tileHalfHeight;




    // Actual coordinates from crude_axe.json
    public static final float CRUDE_AXE_SPRITE_X_PIX = 35.0f;
    public static final float CRUDE_AXE_SPRITE_Y_PIX = 1665.0f;
    public static final float CRUDE_AXE_SPRITE_W_PIX = 49.0f;
    public static final float CRUDE_AXE_SPRITE_H_PIX = 56.0f;

    // Render size for the held crude axe (adjusted to maintain correct aspect ratio)
    public static final float CRUDE_AXE_RENDER_WIDTH = 17.5f;
    public static final float CRUDE_AXE_RENDER_HEIGHT = 20.0f;


    public static class TreeData {
        public Tile.TreeVisualType treeVisualType;
        public float mapCol, mapRow;
        public int elevation;
        public TreeData(Tile.TreeVisualType type, float tc, float tr, int te) {
            this.treeVisualType = type; this.mapCol = tc; this.mapRow = tr; this.elevation = te;
        }
    }

    // --- ADD THIS NEW INNER CLASS ---
    public static class LooseRockData {
        public Tile.LooseRockType rockVisualType; // In case you add more rock types later
        public float mapCol, mapRow;
        public int elevation;

        public LooseRockData(Tile.LooseRockType type, float tc, float tr, int te) {
            this.rockVisualType = type;
            this.mapCol = tc;
            this.mapRow = tr;
            this.elevation = te;
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
        initUiColoredResources();
        initHotbarGLResources();

        System.out.println("Renderer: Initialized. Map: " + (this.map != null) + ", Player: " + (this.player != null));
    }

    /**
     * Sets the game-specific context for the renderer.
     * Called when a game is loaded or a new game starts.
     */
    public void setGameSpecificReferences(Map map, PlayerModel player, InputHandler inputHandler) {
        System.out.println("Renderer: Setting game context. Current map: " + (this.map != null) + " -> New map: " + (map != null));
        this.map = map;
        this.player = player;
        this.inputHandler = inputHandler;

        if (this.activeMapChunks != null) {
            for (Chunk chunk : this.activeMapChunks.values()) {
                chunk.cleanup();
            }
            this.activeMapChunks.clear();
        }
        if (this.worldEntities != null) {
            this.worldEntities.clear();
        }
        System.out.println("Renderer: Game context set. Active chunks and entities cleared.");
    }

    public void clearGameContext() {
        System.out.println("Renderer: Clearing game context.");
        if (this.activeMapChunks != null && !this.activeMapChunks.isEmpty()) {
            System.out.println("Renderer.clearGameContext: Unloading " + this.activeMapChunks.size() + " active chunk graphics.");
            List<LightManager.ChunkCoordinate> coordsToUnload = new ArrayList<>(this.activeMapChunks.keySet());
            for (LightManager.ChunkCoordinate coord : coordsToUnload) {
                Chunk chunk = this.activeMapChunks.remove(coord);
                if (chunk != null) {
                    chunk.cleanup();
                }
            }
        }
        if (this.worldEntities != null) {
            this.worldEntities.clear();
        }
        this.map = null;
        this.player = null;
        // this.inputHandler = null; // Keep inputHandler if it's managed globally by Game
        System.out.println("Renderer: Game context cleared.");
    }


    private void initUiColoredResources() {
        uiColoredVaoId = glGenVertexArrays();
        glBindVertexArray(uiColoredVaoId);
        uiColoredVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, uiColoredVboId);

        int uiBufferCapacityFloats = MAX_UI_COLORED_QUADS * 6 * FLOATS_PER_VERTEX_UI_COLORED;
        if (uiColoredVertexBuffer != null) {
            MemoryUtil.memFree(uiColoredVertexBuffer);
        }
        uiColoredVertexBuffer = MemoryUtil.memAllocFloat(uiBufferCapacityFloats);
        if (uiColoredVertexBuffer == null) {
            System.err.println("Renderer CRITICAL: Failed to allocate uiColoredVertexBuffer!");
            return;
        }
        glBufferData(GL_ARRAY_BUFFER, (long)uiColoredVertexBuffer.capacity() * Float.BYTES, GL_DYNAMIC_DRAW);

        int stride = FLOATS_PER_VERTEX_UI_COLORED * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        System.out.println("[Renderer DEBUG] UI Colored Resources Initialized. VAO: " + uiColoredVaoId + ", VBO: " + uiColoredVboId);
    }

    public java.util.Map<String, Texture> getTextureMap() {
        java.util.Map<String, Texture> textureMap = new HashMap<>();
        if (playerTexture != null) textureMap.put("playerTexture", playerTexture);
        if (treeTexture != null) textureMap.put("treeTexture", treeTexture);
        // Add other atlases here as they are created
        return textureMap;
    }



    private void loadAssets() {
        System.out.println("Renderer: Loading assets...");
        try {
            tileAtlasTexture = Texture.loadTexture("/org/isogame/render/textures/textu.png");
            mainMenuBackgroundTexture = Texture.loadTexture("/org/isogame/render/textures/main_menu_background.png");
            uiFont = new Font("/org/isogame/render/fonts/PressStart2P-Regular.ttf", 16f, this);
            titleFont = new Font("/org/isogame/render/fonts/PressStart2P-Regular.ttf", 32f, this);
            playerTexture = Texture.loadTexture("/org/isogame/render/textures/lpc_character.png");
            treeTexture = Texture.loadTexture("/org/isogame/render/textures/fruit-trees.png");
            System.out.println("Renderer: Assets loaded.");

        } catch (Exception e) {
            System.err.println("Renderer CRITICAL: Error loading assets: " + e.getMessage());
            e.printStackTrace();
            if (tileAtlasTexture == null) System.err.println("Failed to load: tileAtlasTexture");
            if (mainMenuBackgroundTexture == null) System.err.println("Failed to load: mainMenuBackgroundTexture");
            if (uiFont == null) System.err.println("Failed to load: uiFont");
            if (titleFont == null) System.err.println("Failed to load: titleFont");
            if (playerTexture == null) System.err.println("Failed to load: playerTexture");
            if (treeTexture == null) System.err.println("Failed to load: treeTexture");
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
            defaultShader.createUniform("uIsSimpleUiElement");
            defaultShader.createUniform("u_time");             // NEW
            defaultShader.createUniform("u_isSelectedIcon");   // NEW
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

        int maxHotbarVertices = HOTBAR_SIZE * 3 * 6;
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
        if (map == null || camera == null) {
            System.err.println("Renderer.ensureChunkGraphicsLoaded: Map or Camera is null. Cannot load chunk graphics for (" + chunkGridX + "," + chunkGridY + ")");
            return;
        }
        LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(chunkGridX, chunkGridY);
        Chunk chunk = activeMapChunks.get(coord);
        if (chunk == null) {
            chunk = new Chunk(chunkGridX, chunkGridY, CHUNK_SIZE_TILES);
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
        if (map == null || camera == null) {
            System.err.println("Renderer.updateChunkByGridCoords: Map or Camera is null. Cannot update chunk (" + chunkGridX + "," + chunkGridY + ")");
            return;
        }
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
        System.out.println("Renderer: Full tile map geometry upload requested (all active chunks).");
        if (map == null) {
            System.err.println("Renderer.uploadTileMapGeometry: Map is null. Cannot upload.");
            return;
        }
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

        // Add this line to force the hotbar's vertex buffer to be rebuilt.
        this.hotbarDirty = true;

        // You should do this for any other UI elements that cache their geometry, too.
        // For example: this.inventoryDirty = true;
    }

    private float[] determineTopSurfaceColor(Tile.TileType surfaceType, boolean isSelected) {
        if (isSelected) {
            float pulseFactor = (float) (Math.sin(GLFW.glfwGetTime() * 6.0) + 1.0) / 2.0f;
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

    private void addVertexToBuffer(FloatBuffer vertexBuffer, float x, float y, float z, float[] color, float u, float v, float light) {
        vertexBuffer.put(x).put(y).put(z);
        vertexBuffer.put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        vertexBuffer.put(u).put(v);
        vertexBuffer.put(light);
    }

    public int addSingleTileVerticesToList_WorldSpace_ForChunk(
            int tileR_map, int tileC_map, Tile tile, boolean isSelected,
            FloatBuffer vertexBuffer,
            float[] chunkVisualBounds) {

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

        int verticesAddedCount = 0;
        float normalizedLightValue = tile.getFinalLightLevel() / (float) MAX_LIGHT_LEVEL;
        normalizedLightValue = Math.max(0.05f, normalizedLightValue);

        if (currentTileTopSurfaceType != Tile.TileType.WATER) {
            verticesAddedCount += addPedestalSidesToList(
                    vertexBuffer, tileGridPlaneCenterX, tileGridPlaneCenterY, tileBaseZ + Z_OFFSET_TILE_PEDESTAL,
                    sideTintToUse, normalizedLightValue);
        }

        float currentTileTopSurfaceActualY = tileGridPlaneCenterY - (currentTileElevation * TILE_THICKNESS);
        if (currentTileTopSurfaceType == Tile.TileType.WATER) {
            currentTileTopSurfaceActualY = tileGridPlaneCenterY - (Math.max(NIVEL_MAR -1, currentTileElevation) * TILE_THICKNESS);
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

        updateChunkVisualBounds(chunkVisualBounds, tileGridPlaneCenterX, tileGridPlaneCenterY,
                currentTileElevation, TILE_THICKNESS,
                this.diamondLeftOffsetX, this.diamondRightOffsetX,
                this.diamondTopOffsetY, this.diamondBottomOffsetY);

        return verticesAddedCount;
    }

    public Font getTitleFont() { return titleFont; }
    public Font getUiFont() { return uiFont; }


    private int addPedestalSidesToList(FloatBuffer vertexBuffer,
                                       float tileCenterX, float gridPlaneY, float worldZ,
                                       float[] tint, float lightVal) {
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


    private int addTopSurfaceToList(FloatBuffer vertexBuffer,
                                    Tile.TileType topSurfaceType, boolean isSelected,
                                    float topCenterX, float topCenterY, float worldZ,
                                    float[] actualTopColor, float[] whiteTint, float lightVal) {
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
            float midU = (u0 + u1) / 2f; float midV = (v0 + v1Atlas) / 2f;
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

    private int addStratifiedElevatedSidesToList(FloatBuffer vertexBuffer,
                                                 int totalElevationUnits,
                                                 float tileCenterX, float gridPlaneCenterY, float worldZ,
                                                 float elevSliceHeight, float[] tint, float initialLightVal,
                                                 int tileR_map, int tileC_map) {
        int vCount = 0;
        float sideLightVal = initialLightVal;

        LightManager lm = null;
        // Access LightManager via the Map instance stored in this Renderer
        if (this.map != null) {
            lm = this.map.getLightManager();
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
            Tile topTile = (map != null) ? map.getTile(tileR_map, tileC_map) : null;
            if (topTile != null) {
                if (topTile.getType() == Tile.TileType.SNOW || (topTile.getType() == Tile.TileType.ROCK && topTile.getElevation() >= NIVEL_NIEVE -2)) {
                    u0 = SNOW_SIDE_ATLAS_U0; v0 = SNOW_SIDE_ATLAS_V0; u1 = SNOW_SIDE_ATLAS_U1; v1Atlas = SNOW_SIDE_ATLAS_V1;
                } else if (topTile.getType() == Tile.TileType.ROCK || topTile.getType() == Tile.TileType.DIRT) {
                    u0 = ROCK_ATLAS_U0; v0 = ROCK_ATLAS_V0; u1 = ROCK_ATLAS_U1; v1Atlas = ROCK_ATLAS_V1;
                }
            }


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

    private void updateChunkVisualBounds(float[] chunkVisualBounds, float tileCenterX, float tileCenterY,
                                         int elevUnits, float elevSliceH,
                                         float diamondLeftOffsetX, float diamondRightOffsetX,
                                         float diamondTopOffsetY, float diamondBottomOffsetY) {
        float tileMinX = tileCenterX + diamondLeftOffsetX;
        float tileMaxX = tileCenterX + diamondRightOffsetX;
        float tileMinY = tileCenterY - (elevUnits * elevSliceH) + diamondTopOffsetY;
        float tileMaxY = tileCenterY + BASE_THICKNESS + diamondBottomOffsetY;

        chunkVisualBounds[0] = Math.min(chunkVisualBounds[0], tileMinX);
        chunkVisualBounds[1] = Math.min(chunkVisualBounds[1], tileMinY);
        chunkVisualBounds[2] = Math.max(chunkVisualBounds[2], tileMaxX);
        chunkVisualBounds[3] = Math.max(chunkVisualBounds[3], tileMaxY);
    }

    private void collectWorldEntities() {
        worldEntities.clear();
        if (map != null && map.getEntities() != null) {
            worldEntities.addAll(map.getEntities());
        }

        // Add trees and rocks from visible chunks
        if (activeMapChunks != null && !activeMapChunks.isEmpty() && camera != null) {
            for (Chunk chunk : activeMapChunks.values()) {
                if (camera.isChunkVisible(chunk.getBoundingBox())) {
                    worldEntities.addAll(chunk.getTreesInChunk());
                    worldEntities.addAll(chunk.getLooseRocksInChunk());
                }
            }
        }

        // Sort everything by a calculated Y-depth for correct draw order
        worldEntities.sort(Comparator.comparingDouble(e -> {
            if (e instanceof Entity) {
                Entity entity = (Entity) e;
                return entity.getVisualRow() + entity.getVisualCol();
            } else if (e instanceof TreeData) {
                TreeData tree = (TreeData) e;
                return tree.mapRow + tree.mapCol;
            } else if (e instanceof LooseRockData) {
                LooseRockData rock = (LooseRockData) e;
                return rock.mapRow + rock.mapCol;
            }
            return 0; // Default case
        }));
    }



    private void addVertexToSpriteBuffer(FloatBuffer buffer, float x, float y, float z, float[] color, float u, float v, float light) {
        buffer.put(x).put(y).put(z);
        buffer.put(color[0]).put(color[1]).put(color[2]).put(color[3]); // <-- This was the bug
        buffer.put(u).put(v);
        buffer.put(light);
    }


    private int addPlayerVerticesToBuffer_WorldSpace(PlayerModel p, FloatBuffer buffer) {
        if (playerTexture == null || camera == null || map == null) return 0;

        float pR = p.getVisualRow();
        float pC = p.getVisualCol();
        Tile tile = map.getTile(p.getTileRow(), p.getTileCol());
        int elev = (tile != null) ? tile.getElevation() : 0;
        float lightVal = (tile != null) ? tile.getFinalLightLevel() / (float) MAX_LIGHT_LEVEL : 1.0f;
        lightVal = Math.max(0.1f, lightVal);

        float pIsoX = (pC - pR) * this.tileHalfWidth;
        float pIsoY = (pC + pR) * this.tileHalfHeight - (elev * TILE_THICKNESS);
        float playerWorldZ = (pR + pC) * DEPTH_SORT_FACTOR + (elev * 0.005f) + Z_OFFSET_SPRITE_PLAYER;

        if (p.isLevitating()) {
            pIsoY -= (Math.sin(p.getLevitateTimer() * 5f) * 8);
        }

        float halfPlayerRenderWidth = PLAYER_WORLD_RENDER_WIDTH / 2.0f;
        float playerRenderHeight = PLAYER_WORLD_RENDER_HEIGHT;

        float xL = pIsoX - halfPlayerRenderWidth;
        float xR = pIsoX + halfPlayerRenderWidth;
        float yT = pIsoY - playerRenderHeight;
        float yB = pIsoY;

        int animCol = p.getVisualFrameIndex();
        int animRow = p.getAnimationRow();

        float u0 = (float) (animCol * p.getFrameWidth()) / playerTexture.getWidth();
        float v0 = (float) (animRow * p.getFrameHeight()) / playerTexture.getHeight();
        float u1 = (float) ((animCol + 1) * p.getFrameWidth()) / playerTexture.getWidth();
        float v1 = (float) ((animRow + 1) * p.getFrameHeight()) / playerTexture.getHeight();

        // Get the tint from the entity to apply the damage flash
        float[] tint = p.getHealthTint();

        addVertexToSpriteBuffer(buffer, xL, yT, playerWorldZ, tint, u0, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yB, playerWorldZ, tint, u0, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yB, playerWorldZ, tint, u1, v1, lightVal);

        addVertexToSpriteBuffer(buffer, xR, yB, playerWorldZ, tint, u1, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yT, playerWorldZ, tint, u1, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yT, playerWorldZ, tint, u0, v0, lightVal);
        return 6;
    }

    private int addAnimalVerticesToBuffer_WorldSpace(Animal animal, FloatBuffer buffer) {
        if (playerTexture == null || camera == null || map == null) return 0;

        float aR = animal.getVisualRow();
        float aC = animal.getVisualCol();
        Tile tile = map.getTile(animal.getTileRow(), animal.getTileCol());
        int elev = (tile != null) ? tile.getElevation() : 0;
        float lightVal = (tile != null) ? tile.getFinalLightLevel() / (float) MAX_LIGHT_LEVEL : 1.0f;
        lightVal = Math.max(0.1f, lightVal);

        float aIsoX = (aC - aR) * this.tileHalfWidth;
        float aIsoY = (aC + aR) * this.tileHalfHeight - (elev * TILE_THICKNESS);
        float animalWorldZ = (aR + aC) * DEPTH_SORT_FACTOR + (elev * 0.005f) + Z_OFFSET_SPRITE_ANIMAL;

        float renderWidth = animal.getFrameWidth() * 0.75f;
        float renderHeight = animal.getFrameHeight() * 0.75f;
        float halfRenderWidth = renderWidth / 2.0f;

        float xL = aIsoX - halfRenderWidth;
        float xR = aIsoX + halfRenderWidth;
        float yT = aIsoY - renderHeight;
        float yB = aIsoY;

        int animCol = animal.getVisualFrameIndex();
        int animRow = animal.getAnimationRow();

        float u0 = (float) (animCol * animal.getFrameWidth()) / playerTexture.getWidth();
        float v0 = (float) (animRow * animal.getFrameHeight()) / playerTexture.getHeight();
        float u1 = (float) ((animCol + 1) * animal.getFrameWidth()) / playerTexture.getWidth();
        float v1 = (float) ((animRow + 1) * animal.getFrameHeight()) / playerTexture.getHeight();

        // Get the tint from the entity to apply the damage flash
        float[] tint = animal.getHealthTint();

        addVertexToSpriteBuffer(buffer, xL, yT, animalWorldZ, tint, u0, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yB, animalWorldZ, tint, u0, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yB, animalWorldZ, tint, u1, v1, lightVal);

        addVertexToSpriteBuffer(buffer, xR, yB, animalWorldZ, tint, u1, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yT, animalWorldZ, tint, u1, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yT, animalWorldZ, tint, u0, v0, lightVal);
        return 6;
    }

    public void renderPlayerHealthBar(PlayerModel player) {
        if (player == null || camera == null || defaultShader == null || uiColoredVaoId == 0 || uiFont == null) {
            return;
        }

        // --- Layout Calculation (to position above hotbar) ---
        float slotSize = 55f;
        float slotMargin = 6f;
        float hotbarY = camera.getScreenHeight() - slotSize - (slotMargin * 3);

        float barWidth = 200f;
        float barHeight = 20f;
        float barX = (camera.getScreenWidth() - barWidth) / 2.0f; // Centered
        float barY = hotbarY - barHeight - slotMargin; // Positioned above the hotbar
        float border = 2f;

        // --- Health Calculation ---
        float healthPercentage = (float) player.getHealth() / (float) player.getMaxHealth();
        float currentHealthWidth = barWidth * healthPercentage;

        // --- Colors ---
        float[] bgColor = {0.1f, 0.1f, 0.1f, 0.8f};
        float[] healthColor = {0.8f, 0.2f, 0.2f, 0.9f};
        float[] borderColor = {0.8f, 0.8f, 0.8f, 1.0f};

        // --- Rendering ---
        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());
        defaultShader.setUniform("uHasTexture", 0);
        defaultShader.setUniform("uIsSimpleUiElement", 1);

        glBindVertexArray(uiColoredVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, uiColoredVboId);
        uiColoredVertexBuffer.clear();

        // Border
        addQuadToUiColoredBuffer(uiColoredVertexBuffer, barX - border, barY - border, barWidth + (border * 2), barHeight + (border * 2), Z_OFFSET_UI_BORDER, borderColor);
        // Background
        addQuadToUiColoredBuffer(uiColoredVertexBuffer, barX, barY, barWidth, barHeight, Z_OFFSET_UI_PANEL, bgColor);
        // Current Health
        if (currentHealthWidth > 0) {
            addQuadToUiColoredBuffer(uiColoredVertexBuffer, barX, barY, currentHealthWidth, barHeight, Z_OFFSET_UI_ELEMENT, healthColor);
        }

        uiColoredVertexBuffer.flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, uiColoredVertexBuffer);
        glDrawArrays(GL_TRIANGLES, 0, 18); // 3 quads * 6 vertices/quad

        glBindVertexArray(0);

        // Render health text over the bar
        if (uiFont.isInitialized()) {
            String healthText = player.getHealth() + " / " + player.getMaxHealth();
            float textWidth = uiFont.getTextWidth(healthText);
            float textX = barX + (barWidth - textWidth) / 2;
            float textY = barY + barHeight / 2 + uiFont.getAscent() / 2 - 2f;
            uiFont.drawText(textX, textY, healthText, 1f, 1f, 1f);
        }
    }


    private int addLooseRockVerticesToBuffer_WorldSpace(LooseRockData rock, FloatBuffer buffer) {
        // Ensure the texture for rocks (treeTexture in this case) is loaded
        if (treeTexture == null || rock.rockVisualType == Tile.LooseRockType.NONE || camera == null || map == null || treeTexture.getWidth() == 0) {
            return 0;
        }

        // --- This top part remains the same ---
        float rR = rock.mapRow; // Rock's map row
        float rC = rock.mapCol; // Rock's map column
        int elev = rock.elevation;

        Tile tile = map.getTile(Math.round(rR), Math.round(rC));
        float lightVal = (tile != null) ? tile.getFinalLightLevel() / (float)MAX_LIGHT_LEVEL : 1.0f;
        lightVal = Math.max(0.1f, lightVal); // Ensure minimum brightness

        float rockBaseIsoX = (rC - rR) * this.tileHalfWidth;
        float rockBaseIsoY = (rC + rR) * this.tileHalfHeight - (elev * TILE_THICKNESS);

        float tileLogicalZ = (rR + rC) * DEPTH_SORT_FACTOR + (elev * 0.005f);
        float rockWorldZ = tileLogicalZ + Z_OFFSET_SPRITE_TREE;

        float renderWidth = Constants.TILE_WIDTH * 0.4f; // Adjust size as desired
        float renderHeight = renderWidth;

        // --- This is the new logic ---
        float texTotalWidth = treeTexture.getWidth();
        float texTotalHeight = treeTexture.getHeight();
        float spriteX, spriteY, spriteW, spriteH;

        // Select the correct sprite coordinates based on the rock type
        switch (rock.rockVisualType) {
            case TYPE_2:
                spriteX = LOOSE_ROCK_2_X_PIX;
                spriteY = LOOSE_ROCK_2_Y_PIX;
                break;
            case TYPE_3:
                spriteX = LOOSE_ROCK_3_X_PIX;
                spriteY = LOOSE_ROCK_3_Y_PIX;
                break;
            case TYPE_4:
                spriteX = LOOSE_ROCK_4_X_PIX;
                spriteY = LOOSE_ROCK_4_Y_PIX;
                break;
            case TYPE_5:
                spriteX = LOOSE_ROCK_5_X_PIX;
                spriteY = LOOSE_ROCK_5_Y_PIX;
                break;
            case TYPE_6:
                spriteX = LOOSE_ROCK_6_X_PIX;
                spriteY = LOOSE_ROCK_6_Y_PIX;
                break;
            case TYPE_1:
            default: // Default to TYPE_1 if something goes wrong
                spriteX = LOOSE_ROCK_SPRITE_X_PIX;
                spriteY = LOOSE_ROCK_SPRITE_Y_PIX;
                break;
        }
        // Assuming all rock sprites have the same dimensions
        spriteW = LOOSE_ROCK_SPRITE_W_PIX;
        spriteH = LOOSE_ROCK_SPRITE_H_PIX;

        // Calculate final UVs from the selected sprite coordinates
        float u0 = spriteX / texTotalWidth;
        float v0 = spriteY / texTotalHeight;
        float u1 = (spriteX + spriteW) / texTotalWidth;
        float v1 = (spriteY + spriteH) / texTotalHeight;

        // --- The vertex generation part remains the same ---
        float halfRockRenderWidth = renderWidth / 2.0f;
        float yTop = rockBaseIsoY - renderHeight;
        float yBottom = rockBaseIsoY;

        float xBL = rockBaseIsoX - halfRockRenderWidth;
        float yBL_sprite = yBottom;
        float xTL = rockBaseIsoX - halfRockRenderWidth;
        float yTL_sprite = yTop;
        float xTR = rockBaseIsoX + halfRockRenderWidth;
        float yTR_sprite = yTop;
        float xBR = rockBaseIsoX + halfRockRenderWidth;
        float yBR_sprite = yBottom;

        addVertexToSpriteBuffer(buffer, xTL, yTL_sprite, rockWorldZ, WHITE_TINT, u0, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xBL, yBL_sprite, rockWorldZ, WHITE_TINT, u0, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xTR, yTR_sprite, rockWorldZ, WHITE_TINT, u1, v0, lightVal);

        addVertexToSpriteBuffer(buffer, xTR, yTR_sprite, rockWorldZ, WHITE_TINT, u1, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xBL, yBL_sprite, rockWorldZ, WHITE_TINT, u0, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xBR, yBR_sprite, rockWorldZ, WHITE_TINT, u1, v1, lightVal);

        return 6;
    }

    private int addTreeVerticesToBuffer_WorldSpace(TreeData tree, FloatBuffer buffer) {
        if (treeTexture == null || tree.treeVisualType == Tile.TreeVisualType.NONE || camera == null || map == null || treeTexture.getWidth() == 0) return 0;

        float tR = tree.mapRow;
        float tC = tree.mapCol;
        int elev = tree.elevation;

        Tile tile = map.getTile(Math.round(tR), Math.round(tC));
        float lightVal = (tile != null) ? tile.getFinalLightLevel() / (float)MAX_LIGHT_LEVEL : 1.0f;
        lightVal = Math.max(0.1f, lightVal);

        float tBaseIsoX = (tC - tR) * this.tileHalfWidth;
        float tBaseIsoY = (tC + tR) * this.tileHalfHeight - (elev * TILE_THICKNESS);

        float tileLogicalZ = (tR + tC) * DEPTH_SORT_FACTOR + (elev * 0.005f);
        float treeWorldZ = tileLogicalZ + Z_OFFSET_SPRITE_TREE;

        float frameW=0, frameH=0, atlasU0val=0, atlasV0val=0;
        float renderWidth, renderHeight;
        float anchorYOffsetFromBase;

        switch(tree.treeVisualType){
            case APPLE_TREE_FRUITING:
                frameW = 90; frameH = 130; atlasU0val = 0; atlasV0val = 0;
                renderWidth = TILE_WIDTH * 1.0f;
                renderHeight = renderWidth * (frameH / frameW);
                anchorYOffsetFromBase = TILE_HEIGHT * 0.15f;
                break;
            case PINE_TREE_SMALL:
                frameW = 90; frameH = 130; atlasU0val = 90; atlasV0val = 0;
                renderWidth = TILE_WIDTH * 1.0f;
                renderHeight = renderWidth * (frameH / frameW);
                anchorYOffsetFromBase = TILE_HEIGHT * 0.1f;
                break;
            default: return 0;
        }

        float treeRenderAnchorY = tBaseIsoY + anchorYOffsetFromBase;

        float texU0 = atlasU0val / treeTexture.getWidth();
        float texV0 = atlasV0val / treeTexture.getHeight();
        float texU1 = (atlasU0val + frameW) / treeTexture.getWidth();
        float texV1 = (atlasV0val + frameH) / treeTexture.getHeight();

        float halfTreeRenderWidth = renderWidth / 2.0f;
        float yTop = treeRenderAnchorY - renderHeight;
        float yBottom = treeRenderAnchorY;

        float xBL = tBaseIsoX - halfTreeRenderWidth; float yBL_sprite = yBottom;
        float xTL = tBaseIsoX - halfTreeRenderWidth; float yTL_sprite = yTop;
        float xTR = tBaseIsoX + halfTreeRenderWidth; float yTR_sprite = yTop;
        float xBR = tBaseIsoX + halfTreeRenderWidth; float yBR_sprite = yBottom;

        addVertexToSpriteBuffer(buffer, xTL, yTL_sprite, treeWorldZ, WHITE_TINT, texU0, texV0, lightVal);
        addVertexToSpriteBuffer(buffer, xBL, yBL_sprite, treeWorldZ, WHITE_TINT, texU0, texV1, lightVal);
        addVertexToSpriteBuffer(buffer, xTR, yTR_sprite, treeWorldZ, WHITE_TINT, texU1, texV0, lightVal);
        addVertexToSpriteBuffer(buffer, xTR, yTR_sprite, treeWorldZ, WHITE_TINT, texU1, texV0, lightVal);
        addVertexToSpriteBuffer(buffer, xBL, yBL_sprite, treeWorldZ, WHITE_TINT, texU0, texV1, lightVal);
        addVertexToSpriteBuffer(buffer, xBR, yBR_sprite, treeWorldZ, WHITE_TINT, texU1, texV1, lightVal);
        return 6;
    }


    public void render() {
        if (defaultShader == null || camera == null) {
            return;
        }

        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", camera.getViewMatrix());
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uIsSimpleUiElement", 0);
        defaultShader.setUniform("u_isSelectedIcon", 0);

        // --- 1. RENDER WORLD TERRAIN ---
        if (map != null && tileAtlasTexture != null && tileAtlasTexture.getId() != 0) {
            glActiveTexture(GL_TEXTURE0);
            tileAtlasTexture.bind();
            defaultShader.setUniform("uTextureSampler", 0);
            defaultShader.setUniform("uHasTexture", 1);
            for (Chunk chunk : activeMapChunks.values()) {
                if (camera.isChunkVisible(chunk.getBoundingBox())) {
                    chunk.render();
                }
            }
            tileAtlasTexture.unbind();
        }

        // --- 2. RENDER ALL SPRITES (Player, Trees, Rocks, etc.) ---
        if (map != null) {
            collectWorldEntities();
            renderWorldSprites(); // This single call now handles everything correctly.
        }
    }

    private void renderWorldSprites() {
        if (spriteVaoId == 0 || worldEntities.isEmpty()) return;

        glBindVertexArray(spriteVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        spriteVertexBuffer.clear();

        int verticesInBatch = 0;
        Texture currentBatchTexture = null;

        defaultShader.setUniform("uHasTexture", 1);
        defaultShader.setUniform("uTextureSampler", 0);

        for (Object entityObj : worldEntities) {
            Texture textureForThisEntity;
            int verticesAdded = 0;
            boolean isPlayer = entityObj instanceof PlayerModel;

            // Determine texture for the entity itself
            if (entityObj instanceof Particle) {
                // Particles don't use a texture, so we set the texture to null.
                // This will force a batch break, which is what we want.
                textureForThisEntity = null;
            } else if (entityObj instanceof Entity) {
                textureForThisEntity = playerTexture;
            } else if (entityObj instanceof TreeData || entityObj instanceof LooseRockData) {
                textureForThisEntity = treeTexture;
            } else {
                continue;
            }

            // --- BATCH BREAKING LOGIC ---
            // If texture changes, draw the previous batch
            if (currentBatchTexture != null && (textureForThisEntity == null || textureForThisEntity.getId() != currentBatchTexture.getId())) {
                renderSpriteBatch(spriteVertexBuffer, verticesInBatch, currentBatchTexture);
                spriteVertexBuffer.clear();
                verticesInBatch = 0;
            }
            currentBatchTexture = textureForThisEntity;

            // --- ADD VERTICES TO BUFFER ---
            if (entityObj instanceof Particle) {
                verticesAdded = addParticleVerticesToBuffer((Particle) entityObj, spriteVertexBuffer);
            } else if (entityObj instanceof Entity) {
                verticesAdded = addGenericEntityVerticesToBuffer((Entity) entityObj, spriteVertexBuffer);
            } else if (entityObj instanceof TreeData) {
                verticesAdded = addTreeVerticesToBuffer_WorldSpace((TreeData) entityObj, spriteVertexBuffer);
            } else if (entityObj instanceof LooseRockData) {
                verticesAdded = addLooseRockVerticesToBuffer_WorldSpace((LooseRockData) entityObj, spriteVertexBuffer);
            }
            verticesInBatch += verticesAdded;

            // --- NEW: SPECIAL HANDLING FOR PLAYER'S WEAPON ---
            if (isPlayer) {
                PlayerModel p = (PlayerModel) entityObj;
                Item heldItem = p.getHeldItem();
                PlayerModel.AnchorPoint anchor = p.getAnchorForCurrentFrame();

                // Check if holding an item that needs the *other* texture sheet
                if (heldItem != null && anchor != null && treeTexture != null) {
                    // The item needs drawing. First, draw everything we've batched so far (including the player model).
                    renderSpriteBatch(spriteVertexBuffer, verticesInBatch, currentBatchTexture);
                    spriteVertexBuffer.clear();
                    verticesInBatch = 0;

                    // Now, add the item's vertices to the empty buffer
                    float playerZ = (p.getVisualRow() + p.getVisualCol()) * DEPTH_SORT_FACTOR + (map.getTile(p.getTileRow(), p.getTileCol()).getElevation() * 0.005f) + Z_OFFSET_SPRITE_PLAYER;
                    float lightVal = map.getTile(p.getTileRow(), p.getTileCol()).getFinalLightLevel() / (float) MAX_LIGHT_LEVEL;
                    float itemZ = anchor.drawBehind ? playerZ + 0.001f : playerZ - 0.001f;

                    int itemVerts = addHeldItemVerticesToBuffer(p, heldItem, anchor, spriteVertexBuffer, itemZ, lightVal);

                    // Draw the item immediately in its own batch, using the correct texture (treeTexture)
                    renderSpriteBatch(spriteVertexBuffer, itemVerts, treeTexture);
                    spriteVertexBuffer.clear();

                    // Set the current texture back to the player texture for the next entities in the loop
                    currentBatchTexture = playerTexture;
                }
            }
        }

        // Render any remaining vertices after the loop finishes
        if (verticesInBatch > 0) {
            renderSpriteBatch(spriteVertexBuffer, verticesInBatch, currentBatchTexture);
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }



    private void renderSpriteBatch(FloatBuffer buffer, int vertexCount, Texture texture) {
        if (vertexCount == 0 || texture == null) return;

        buffer.flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
        glActiveTexture(GL_TEXTURE0);
        texture.bind();
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        texture.unbind(); // Good practice to unbind
    }

    private int addParticleVerticesToBuffer(Particle particle, FloatBuffer buffer) {
        if (map == null) return 0;

        // Get particle's base position
        float pR = particle.getVisualRow();
        float pC = particle.getVisualCol();

        // Calculate isometric screen position
        float pIsoX = (pC - pR) * tileHalfWidth;
        float pIsoY = (pC + pR) * tileHalfHeight - particle.getZ(); // Use the particle's own Z

        // Use a high Z-depth value to ensure particles draw in front of most things
        float particleWorldZ = (pR + pC) * DEPTH_SORT_FACTOR - 0.1f;

        // Define the quad size based on the particle's size property
        float halfSize = particle.size / 2.0f;
        float xL = pIsoX - halfSize;
        float xR = pIsoX + halfSize;
        float yT = pIsoY - halfSize;
        float yB = pIsoY + halfSize;

        // Use the particle's color
        float[] tint = particle.color;

        // Fade the particle out as it nears the end of its life
        // We can use the lightValue attribute for this, re-purposing it as an alpha multiplier
        float alpha_fade = 1.0f; // You could calculate this based on particle.life

        // Add vertices. Since it's a simple colored square, texture coordinates (u,v) are irrelevant (0,0).
        // We pass the alpha fade in the 'light' attribute slot.
        addVertexToSpriteBuffer(buffer, xL, yT, particleWorldZ, tint, 0, 0, alpha_fade);
        addVertexToSpriteBuffer(buffer, xL, yB, particleWorldZ, tint, 0, 0, alpha_fade);
        addVertexToSpriteBuffer(buffer, xR, yB, particleWorldZ, tint, 0, 0, alpha_fade);

        addVertexToSpriteBuffer(buffer, xR, yB, particleWorldZ, tint, 0, 0, alpha_fade);
        addVertexToSpriteBuffer(buffer, xR, yT, particleWorldZ, tint, 0, 0, alpha_fade);
        addVertexToSpriteBuffer(buffer, xL, yT, particleWorldZ, tint, 0, 0, alpha_fade);

        return 6;
    }

    private int addGenericEntityVerticesToBuffer(Entity entity, FloatBuffer buffer) {
        if (playerTexture == null || camera == null || map == null) return 0;

        float eR = entity.getVisualRow();
        float eC = entity.getVisualCol();
        Tile tile = map.getTile(entity.getTileRow(), entity.getTileCol());
        int elev = (tile != null) ? tile.getElevation() : 0;
        float lightVal = (tile != null) ? tile.getFinalLightLevel() / (float) MAX_LIGHT_LEVEL : 1.0f;
        lightVal = Math.max(0.1f, lightVal);

        float eIsoX = (eC - eR) * this.tileHalfWidth;
        float eIsoY = (eC + eR) * this.tileHalfHeight - (elev * TILE_THICKNESS);

        // Determine Z-depth based on entity type
        float entityWorldZ = (eR + eC) * DEPTH_SORT_FACTOR + (elev * 0.005f);
        if (entity instanceof PlayerModel) {
            entityWorldZ += Z_OFFSET_SPRITE_PLAYER;
        } else {
            entityWorldZ += Z_OFFSET_SPRITE_ANIMAL;
        }

        float renderWidth = (entity instanceof PlayerModel) ? PLAYER_WORLD_RENDER_WIDTH : entity.getFrameWidth() * 0.75f;
        float renderHeight = (entity instanceof PlayerModel) ? PLAYER_WORLD_RENDER_HEIGHT : entity.getFrameHeight() * 0.75f;

        float halfRenderWidth = renderWidth / 2.0f;
        float xL = eIsoX - halfRenderWidth;
        float xR = eIsoX + halfRenderWidth;
        float yT = eIsoY - renderHeight;
        float yB = eIsoY;

        int animCol = entity.getVisualFrameIndex();
        int animRow = entity.getAnimationRow();

        float u0 = (float) (animCol * entity.getFrameWidth()) / playerTexture.getWidth();
        float v0 = (float) (animRow * entity.getFrameHeight()) / playerTexture.getHeight();
        float u1 = (float) ((animCol + 1) * entity.getFrameWidth()) / playerTexture.getWidth();
        float v1 = (float) ((animRow + 1) * entity.getFrameHeight()) / playerTexture.getHeight();

        float[] tint = entity.getHealthTint();

        addVertexToSpriteBuffer(buffer, xL, yT, entityWorldZ, tint, u0, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yB, entityWorldZ, tint, u0, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yB, entityWorldZ, tint, u1, v1, lightVal);

        addVertexToSpriteBuffer(buffer, xR, yB, entityWorldZ, tint, u1, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yT, entityWorldZ, tint, u1, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yT, entityWorldZ, tint, u0, v0, lightVal);

        return 6; // It always adds 6 vertices
    }


    private int addHeldItemVerticesToBuffer(PlayerModel player, Item item, PlayerModel.AnchorPoint anchor, FloatBuffer buffer, float itemWorldZ, float lightVal) {
        if (treeTexture == null || map == null || anchor == null) return 0;

        float pR = player.getVisualRow();
        float pC = player.getVisualCol();
        Tile tile = map.getTile(player.getTileRow(), player.getTileCol());
        int elev = (tile != null) ? tile.getElevation() : 0;

        // FIX: Removed duplicate variable declarations for 'lightVal' and 'itemWorldZ'.
        // We now use the values passed into the method.

        float playerRenderWidth = Constants.PLAYER_WORLD_RENDER_WIDTH;
        float playerRenderHeight = Constants.PLAYER_WORLD_RENDER_HEIGHT;
        float playerBaseIsoX = (pC - pR) * tileHalfWidth;
        float playerBaseIsoY = (pC + pR) * tileHalfHeight - (elev * TILE_THICKNESS);

        float playerCenterX = playerBaseIsoX;
        float playerCenterY = playerBaseIsoY - playerRenderHeight / 2.0f;

        float pixelToWorldScale = playerRenderWidth / (float) player.getFrameWidth();
        float scaledAnchorDx = anchor.dx * pixelToWorldScale;
        float scaledAnchorDy = anchor.dy * pixelToWorldScale;

        float renderW = CRUDE_AXE_RENDER_WIDTH;
        float renderH = CRUDE_AXE_RENDER_HEIGHT;
        float pivotX = renderW * 0.2f;
        float pivotY = renderH * 0.8f;

        Matrix4f modelMatrix = new Matrix4f();
        modelMatrix.translate(playerCenterX + scaledAnchorDx, playerCenterY + scaledAnchorDy, 0);
        modelMatrix.rotateZ((float) Math.toRadians(anchor.rotation));
        modelMatrix.translate(-pivotX, -pivotY, 0);

        Vector4f vertTopLeft     = new Vector4f(0, 0, 0, 1).mul(modelMatrix);
        Vector4f vertBottomLeft  = new Vector4f(0, renderH, 0, 1).mul(modelMatrix);
        Vector4f vertTopRight    = new Vector4f(renderW, 0, 0, 1).mul(modelMatrix);
        Vector4f vertBottomRight = new Vector4f(renderW, renderH, 0, 1).mul(modelMatrix);

        float u0 = item.getIconU0(), v0 = item.getIconV0();
        float u1 = item.getIconU1(), v1 = item.getIconV1();

        addVertexToSpriteBuffer(buffer, vertTopLeft.x, vertTopLeft.y, itemWorldZ, WHITE_TINT, u0, v0, lightVal);
        addVertexToSpriteBuffer(buffer, vertBottomLeft.x, vertBottomLeft.y, itemWorldZ, WHITE_TINT, u0, v1, lightVal);
        addVertexToSpriteBuffer(buffer, vertTopRight.x, vertTopRight.y, itemWorldZ, WHITE_TINT, u1, v0, lightVal);

        addVertexToSpriteBuffer(buffer, vertTopRight.x, vertTopRight.y, itemWorldZ, WHITE_TINT, u1, v0, lightVal);
        addVertexToSpriteBuffer(buffer, vertBottomLeft.x, vertBottomLeft.y, itemWorldZ, WHITE_TINT, u0, v1, lightVal);
        addVertexToSpriteBuffer(buffer, vertBottomRight.x, vertBottomRight.y, itemWorldZ, WHITE_TINT, u1, v1, lightVal);

        return 6;
    }


    public void renderHotbar(PlayerModel playerForHotbar, int currentlySelectedHotbarSlot) {
        Font currentUiFont = getUiFont();
        if (currentUiFont == null || !currentUiFont.isInitialized() ||
                playerForHotbar == null || defaultShader == null || camera == null) {
            return;
        }

        // Cache hotbar layout calculations
        final float slotSize = 55f;
        final float slotMargin = 6f;
        final int hotbarSlotsToDisplay = HOTBAR_SIZE;
        final float totalHotbarWidth = (hotbarSlotsToDisplay * slotSize) +
                ((Math.max(0, hotbarSlotsToDisplay - 1)) * slotMargin);
        final float hotbarX = (camera.getScreenWidth() - totalHotbarWidth) / 2.0f;
        final float hotbarY = camera.getScreenHeight() - slotSize - (slotMargin * 3);
        final float itemRenderSize = slotSize * 0.9f;
        final float itemOffset = (slotSize - itemRenderSize) / 2f;
        float pR = player.getVisualRow();

        List<InventorySlot> playerInventorySlots = playerForHotbar.getInventorySlots();

        // --- Part 1: Draw Hotbar Slot Backgrounds and Borders ---
        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uHasTexture", 0);
        defaultShader.setUniform("uIsSimpleUiElement", 1);

        glBindVertexArray(hotbarVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, hotbarVboId);

        // Only rebuild if dirty or selection changed
        if (hotbarDirty || hasSelectionChanged(currentlySelectedHotbarSlot)) {
            hotbarVertexDataBuffer.clear();
            hotbarVertexCount = 0;

            // Single pass for backgrounds and borders
            for (int i = 0; i < hotbarSlotsToDisplay; i++) {
                float currentSlotDrawX = hotbarX + i * (slotSize + slotMargin);
                InventorySlot slot = (i < playerInventorySlots.size()) ? playerInventorySlots.get(i) : null;
                boolean isSelected = (i == currentlySelectedHotbarSlot);
                boolean isEmpty = (slot == null || slot.isEmpty());

                // Determine colors and border width based on state
                SlotStyle style = getSlotStyle(isSelected, isEmpty);

                // Draw slot background with gradient
                addGradientQuadToUiColoredBuffer(hotbarVertexDataBuffer,
                        currentSlotDrawX, hotbarY, slotSize, slotSize,
                        Z_OFFSET_UI_PANEL, style.topBgColor, style.bottomBgColor);
                hotbarVertexCount += 6;

                // Draw slot border if needed
                if (style.borderWidth > 0) {
                    addQuadToUiColoredBuffer(hotbarVertexDataBuffer,
                            currentSlotDrawX - style.borderWidth,
                            hotbarY - style.borderWidth,
                            slotSize + (2 * style.borderWidth),
                            slotSize + (2 * style.borderWidth),
                            Z_OFFSET_UI_BORDER, style.borderColor);
                    hotbarVertexCount += 6;
                }

                // Add icon border for items with textures
                if (slot != null && !slot.isEmpty() && slot.getItem().hasIconTexture()) {
                    float iconVisualX = currentSlotDrawX + itemOffset;
                    float iconVisualY = hotbarY + itemOffset;
                    float iconBorderSize = 1.0f;
                    float[] iconBorderColor = {1.0f, 1.0f, 1.0f, 0.8f};

                    addQuadToUiColoredBuffer(hotbarVertexDataBuffer,
                            iconVisualX - iconBorderSize,
                            iconVisualY - iconBorderSize,
                            itemRenderSize + (2 * iconBorderSize),
                            itemRenderSize + (2 * iconBorderSize),
                            Z_OFFSET_UI_ELEMENT - 0.002f, iconBorderColor);
                    hotbarVertexCount += 6;
                }
            }

            hotbarVertexDataBuffer.flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, hotbarVertexDataBuffer);
            hotbarDirty = false;
            updateLastSelectedSlot(currentlySelectedHotbarSlot);
        }

        // Draw all colored quads at once
        if (hotbarVertexCount > 0) {
            glDrawArrays(GL_TRIANGLES, 0, hotbarVertexCount);
        }

        // --- Part 2: Draw Item Icons ---
        if (treeTexture != null && treeTexture.getId() != 0) {
            defaultShader.setUniform("uHasTexture", 1);
            defaultShader.setUniform("uIsSimpleUiElement", 0);
            defaultShader.setUniform("uTextureSampler", 0);

            glActiveTexture(GL_TEXTURE0);
            treeTexture.bind();

            glBindVertexArray(spriteVaoId);
            glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
            spriteVertexBuffer.clear();
            int iconVertexCount = 0;

            for (int i = 0; i < hotbarSlotsToDisplay; i++) {
                InventorySlot slot = (i < playerInventorySlots.size()) ? playerInventorySlots.get(i) : null;

                if (slot != null && !slot.isEmpty()) {
                    Item item = slot.getItem();
                    if (item.hasIconTexture()) {
                        boolean isSelected = (i == currentlySelectedHotbarSlot);

                        // Set shader uniforms for selection effects
                        defaultShader.setUniform("u_isSelectedIcon", isSelected ? 1 : 0);
                        if (isSelected) {
                            defaultShader.setUniform("u_time", (float) GLFW.glfwGetTime());
                        }

                        float currentSlotDrawX = hotbarX + i * (slotSize + slotMargin);
                        float iconX = currentSlotDrawX + itemOffset;
                        float iconY = hotbarY + itemOffset;
                        float iconZ = Z_OFFSET_UI_ELEMENT;

                        // Add icon quad vertices
                        addIconQuad(spriteVertexBuffer, iconX, iconY, iconZ,
                                itemRenderSize, item);
                        iconVertexCount += 6;
                    }
                }
            }

            if (iconVertexCount > 0) {
                spriteVertexBuffer.flip();
                glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                glDrawArrays(GL_TRIANGLES, 0, iconVertexCount);
            }

            treeTexture.unbind();
        }

        // --- Part 3: Draw Quantity Text ---
        renderQuantityText(playerInventorySlots, currentUiFont, hotbarX, hotbarY,
                slotSize, slotMargin, hotbarSlotsToDisplay);

        // Cleanup
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        defaultShader.unbind();
    }

    // Helper method to determine slot styling
    private SlotStyle getSlotStyle(boolean isSelected, boolean isEmpty) {
        SlotStyle style = new SlotStyle();

        if (isSelected) {
            style.topBgColor = new float[]{0.9f, 0.9f, 0.3f, 0.75f};
            style.bottomBgColor = new float[]{0.8f, 0.8f, 0.2f, 0.75f};
            style.borderColor = new float[]{1.0f, 0.6f, 0.0f, 0.85f};
            style.borderWidth = 2.5f;
        } else if (!isEmpty) {
            style.topBgColor = new float[]{0.8f, 0.8f, 0.4f, 0.65f};
            style.bottomBgColor = new float[]{0.7f, 0.7f, 0.3f, 0.65f};
            style.borderColor = new float[]{0.9f, 0.5f, 0.1f, 0.75f};
            style.borderWidth = 1.5f;
        } else {
            style.topBgColor = new float[]{0.7f, 0.7f, 0.5f, 0.55f};
            style.bottomBgColor = new float[]{0.6f, 0.6f, 0.4f, 0.55f};
            style.borderColor = new float[]{0.8f, 0.4f, 0.0f, 0.65f};
            style.borderWidth = 1.0f;
        }

        return style;
    }

    // Helper method to add icon quad vertices
    private void addIconQuad(FloatBuffer buffer, float x, float y, float z,
                             float size, Item item) {
        float[] tint = WHITE_TINT;

        // Triangle 1
        buffer.put(x).put(y).put(z);
        buffer.put(tint);
        buffer.put(item.getIconU0()).put(item.getIconV0()).put(1.0f);

        buffer.put(x).put(y + size).put(z);
        buffer.put(tint);
        buffer.put(item.getIconU0()).put(item.getIconV1()).put(1.0f);

        buffer.put(x + size).put(y + size).put(z);
        buffer.put(tint);
        buffer.put(item.getIconU1()).put(item.getIconV1()).put(1.0f);

        // Triangle 2
        buffer.put(x).put(y).put(z);
        buffer.put(tint);
        buffer.put(item.getIconU0()).put(item.getIconV0()).put(1.0f);

        buffer.put(x + size).put(y + size).put(z);
        buffer.put(tint);
        buffer.put(item.getIconU1()).put(item.getIconV1()).put(1.0f);

        buffer.put(x + size).put(y).put(z);
        buffer.put(tint);
        buffer.put(item.getIconU1()).put(item.getIconV0()).put(1.0f);
    }

    // Helper method for quantity text rendering
    private void renderQuantityText(List<InventorySlot> slots, Font font,
                                    float hotbarX, float hotbarY, float slotSize,
                                    float slotMargin, int slotsToDisplay) {
        final float textPaddingFromEdge = 4f;

        for (int i = 0; i < slotsToDisplay; i++) {
            InventorySlot slot = (i < slots.size()) ? slots.get(i) : null;

            if (slot != null && !slot.isEmpty() && slot.getQuantity() > 1) {
                String quantityStr = String.valueOf(slot.getQuantity());
                float currentSlotDrawX = hotbarX + i * (slotSize + slotMargin);
                float qtyTextWidth = font.getTextWidthScaled(quantityStr, 1.0f);
                float qtyTextX = currentSlotDrawX + slotSize - qtyTextWidth - textPaddingFromEdge;
                float qtyTextY = hotbarY + slotSize - textPaddingFromEdge;

                font.drawText(qtyTextX, qtyTextY, quantityStr, 1f, 1f, 1f);
            }
        }
    }

    // Helper class for slot styling
    private static class SlotStyle {
        float[] topBgColor;
        float[] bottomBgColor;
        float[] borderColor;
        float borderWidth;
    }

    // Selection tracking methods
    private int lastSelectedSlot = -1;

    private boolean hasSelectionChanged(int currentSlot) {
        return lastSelectedSlot != currentSlot;
    }

    private void updateLastSelectedSlot(int slot) {
        lastSelectedSlot = slot;
    }

    private void addQuadToUiBuffer(FloatBuffer buffer, float x, float y, float w, float h, float z, float[] color) {
        buffer.put(x).put(y).put(z).put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        buffer.put(x).put(y + h).put(z).put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        buffer.put(x + w).put(y).put(z).put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        buffer.put(x + w).put(y).put(z).put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        buffer.put(x).put(y + h).put(z).put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        buffer.put(x + w).put(y + h).put(z).put(color[0]).put(color[1]).put(color[2]).put(color[3]);
    }

    private void addGradientQuadToUiBuffer(FloatBuffer buffer, float x, float y, float w, float h, float z, float[] topColor, float[] bottomColor) {
        buffer.put(x).put(y).put(z).put(topColor[0]).put(topColor[1]).put(topColor[2]).put(topColor[3]);
        buffer.put(x).put(y + h).put(z).put(bottomColor[0]).put(bottomColor[1]).put(bottomColor[2]).put(bottomColor[3]);
        buffer.put(x + w).put(y).put(z).put(topColor[0]).put(topColor[1]).put(topColor[2]).put(topColor[3]);
        buffer.put(x + w).put(y).put(z).put(topColor[0]).put(topColor[1]).put(topColor[2]).put(topColor[3]);
        buffer.put(x).put(y + h).put(z).put(bottomColor[0]).put(bottomColor[1]).put(bottomColor[2]).put(bottomColor[3]);
        buffer.put(x + w).put(y + h).put(z).put(bottomColor[0]).put(bottomColor[1]).put(bottomColor[2]).put(bottomColor[3]);
    }

    public void renderInventoryAndCraftingUI(PlayerModel playerForInventory) {
        Font currentUiFont = getUiFont();
        Font titleFont = getTitleFont();
        if (currentUiFont == null || !currentUiFont.isInitialized() ||
                titleFont == null || !titleFont.isInitialized() ||
                playerForInventory == null || camera == null ||
                defaultShader == null || uiColoredVaoId == 0) {
            return;
        }

        Game game = (this.inputHandler != null) ? this.inputHandler.getGameInstance() : null;
        if (game == null) return;

        // --- 1. LAYOUT CALCULATIONS ---
        final float slotSize = 50f;
        final float slotMargin = 10f;
        final float panelMarginX = 30f;
        final float topMarginY = 40f;
        final float marginBetweenPanels = 20f;

        // Inventory Panel (Top-Right)
        final int invSlotsPerRow = 5;
        List<InventorySlot> slots = playerForInventory.getInventorySlots();
        final int invNumRows = slots.isEmpty() ? 1 : (int) Math.ceil((double) slots.size() / invSlotsPerRow);
        final float invPanelWidth = (invSlotsPerRow * slotSize) + ((invSlotsPerRow + 1) * slotMargin);
        final float invPanelHeight = (invNumRows * slotSize) + ((invNumRows + 1) * slotMargin);
        final float invPanelX = camera.getScreenWidth() - invPanelWidth - panelMarginX;
        final float invPanelY = topMarginY;

        // Crafting Panel (Below Inventory)
        List<org.isogame.crafting.CraftingRecipe> allRecipes = org.isogame.crafting.RecipeRegistry.getAllRecipes();
        final float recipeRowHeight = 50f;
        final float craftPanelWidth = invPanelWidth;
        final float craftPanelHeight = (allRecipes.size() * recipeRowHeight) + (slotMargin * 2) + 30f;
        final float craftPanelX = invPanelX;
        final float craftPanelY = invPanelY + invPanelHeight + marginBetweenPanels;

        // --- 2. RENDER PANEL BACKGROUNDS ---
        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uHasTexture", 0);
        defaultShader.setUniform("uIsSimpleUiElement", 1);

        glBindVertexArray(uiColoredVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, uiColoredVboId);
        uiColoredVertexBuffer.clear();

        // Panel backgrounds with more visible colors
        float[] invPanelColor = {0.15f, 0.15f, 0.2f, 0.95f}; // Darker, more opaque
        float[] craftPanelColor = {0.1f, 0.1f, 0.15f, 0.95f}; // Even darker for contrast

        addQuadToUiColoredBuffer(uiColoredVertexBuffer, invPanelX, invPanelY,
                invPanelWidth, invPanelHeight, Z_OFFSET_UI_PANEL, invPanelColor);
        addQuadToUiColoredBuffer(uiColoredVertexBuffer, craftPanelX, craftPanelY,
                craftPanelWidth, craftPanelHeight, Z_OFFSET_UI_PANEL, craftPanelColor);

        int backgroundVertexCount = 12; // 2 panels * 6 vertices each

        // --- 3. RENDER INVENTORY SLOTS ---
        float currentSlotX = invPanelX + slotMargin;
        float currentSlotY = invPanelY + slotMargin;
        int colCount = 0;
        int slotVertexCount = 0;

        for (int i = 0; i < slots.size(); i++) {
            InventorySlot slot = slots.get(i);
            boolean isSelected = (i == game.getSelectedHotbarSlotIndex() && i < HOTBAR_SIZE);
            boolean hasItem = (slot != null && !slot.isEmpty());

            // More visible slot colors
            float[] slotColor;
            if (isSelected) {
                slotColor = new float[]{0.9f, 0.8f, 0.2f, 0.9f}; // Bright yellow for selected
            } else if (hasItem) {
                slotColor = new float[]{0.5f, 0.5f, 0.6f, 0.8f}; // Light grey for items
            } else {
                slotColor = new float[]{0.3f, 0.3f, 0.35f, 0.7f}; // Medium grey for empty
            }

            addQuadToUiColoredBuffer(uiColoredVertexBuffer, currentSlotX, currentSlotY,
                    slotSize, slotSize, Z_OFFSET_UI_ELEMENT, slotColor);
            slotVertexCount += 6;

            // Add slot border for better visibility
            float borderWidth = isSelected ? 2.0f : 1.0f;
            float[] borderColor = isSelected ?
                    new float[]{1.0f, 0.9f, 0.0f, 1.0f} : // Gold border for selected
                    new float[]{0.6f, 0.6f, 0.6f, 0.8f};  // Grey border for others

            addQuadBorderToUiColoredBuffer(uiColoredVertexBuffer, currentSlotX, currentSlotY,
                    slotSize, slotSize, borderWidth,
                    Z_OFFSET_UI_ELEMENT + 0.001f, borderColor);
            slotVertexCount += 24; // 4 border quads * 6 vertices each

            currentSlotX += slotSize + slotMargin;
            colCount++;
            if (colCount >= invSlotsPerRow) {
                colCount = 0;
                currentSlotX = invPanelX + slotMargin;
                currentSlotY += slotSize + slotMargin;
            }
        }

        // --- 4. RENDER CRAFT BUTTONS ---
        float currentRecipeY = craftPanelY + slotMargin + 30f;
        int buttonVertexCount = 0;

        for (org.isogame.crafting.CraftingRecipe recipe : allRecipes) {
            if (game.canCraft(recipe)) {
                float craftButtonWidth = 70f;
                float craftButtonHeight = 25f;
                float craftButtonX = craftPanelX + craftPanelWidth - craftButtonWidth - slotMargin;
                float craftButtonY = currentRecipeY + (recipeRowHeight - craftButtonHeight) / 2f - 2f;

                float[] buttonColor = {0.2f, 0.6f, 0.2f, 0.9f}; // Brighter green
                addQuadToUiColoredBuffer(uiColoredVertexBuffer, craftButtonX, craftButtonY,
                        craftButtonWidth, craftButtonHeight,
                        Z_OFFSET_UI_BORDER, buttonColor);
                buttonVertexCount += 6;
            }
            currentRecipeY += recipeRowHeight;
        }

        // Draw all colored elements
        int totalColoredVertices = backgroundVertexCount + slotVertexCount + buttonVertexCount;
        if (totalColoredVertices > 0) {
            uiColoredVertexBuffer.flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, uiColoredVertexBuffer);
            glDrawArrays(GL_TRIANGLES, 0, totalColoredVertices);
        }

        // --- 5. RENDER ITEM ICONS ---
        if (treeTexture != null && treeTexture.getId() != 0) {
            defaultShader.setUniform("uHasTexture", 1);
            defaultShader.setUniform("uIsSimpleUiElement", 0);
            defaultShader.setUniform("uTextureSampler", 0);

            glActiveTexture(GL_TEXTURE0);
            treeTexture.bind();
            glBindVertexArray(spriteVaoId);
            glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
            spriteVertexBuffer.clear();

            int totalIconVertices = 0;
            final float itemRenderSize = slotSize * 0.8f; // Slightly smaller for better visibility
            final float itemOffset = (slotSize - itemRenderSize) / 2f;

            // Inventory Icons
            currentSlotX = invPanelX + slotMargin;
            currentSlotY = invPanelY + slotMargin;
            colCount = 0;

            for (int i = 0; i < slots.size(); i++) {
                InventorySlot slot = slots.get(i);
                if (slot != null && !slot.isEmpty() && slot.getItem().hasIconTexture()) {
                    addIconToSpriteBuffer(spriteVertexBuffer,
                            currentSlotX + itemOffset,
                            currentSlotY + itemOffset,
                            itemRenderSize, slot.getItem(),
                            Z_OFFSET_UI_ELEMENT + 0.002f);
                    totalIconVertices += 6;
                }

                currentSlotX += slotSize + slotMargin;
                colCount++;
                if (colCount >= invSlotsPerRow) {
                    colCount = 0;
                    currentSlotX = invPanelX + slotMargin;
                    currentSlotY += slotSize + slotMargin;
                }
            }

            // Recipe Icons
            final float recipeIconSize = 32f;
            currentRecipeY = craftPanelY + slotMargin + 30f;

            for (org.isogame.crafting.CraftingRecipe recipe : allRecipes) {
                addIconToSpriteBuffer(spriteVertexBuffer,
                        craftPanelX + slotMargin,
                        currentRecipeY + (recipeRowHeight - recipeIconSize) / 2f - 2f,
                        recipeIconSize, recipe.getOutputItem(),
                        Z_OFFSET_UI_ELEMENT + 0.002f);
                totalIconVertices += 6;
                currentRecipeY += recipeRowHeight;
            }

            if (totalIconVertices > 0) {
                spriteVertexBuffer.flip();
                glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                glDrawArrays(GL_TRIANGLES, 0, totalIconVertices);
            }

            treeTexture.unbind();
        }

        // --- 6. RENDER TEXT ---
        renderInventoryText(slots, currentUiFont, invPanelX, invPanelY,
                slotSize, slotMargin, invSlotsPerRow);
        renderCraftingText(allRecipes, game, currentUiFont, titleFont,
                craftPanelX, craftPanelY, craftPanelWidth, recipeRowHeight);

        // --- 7. RENDER DRAGGED ITEM ---
        if (game.isDraggingItem() && game.getDraggedItemStack() != null) {
            renderDraggedItem(game, currentUiFont);
        }

        // --- 8. RENDER TOOLTIP ---
        if (!game.isDraggingItem()) {
            double[] xpos = new double[1];
            double[] ypos = new double[1];
            glfwGetCursorPos(game.getWindowHandle(), xpos, ypos);
            renderCraftingTooltip(game.getHoveredRecipe(), (float)xpos[0], (float)ypos[0]);
        }

        // Cleanup
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        defaultShader.unbind();
    }

    // Helper method to add quad borders
    private void addQuadBorderToUiColoredBuffer(FloatBuffer buffer, float x, float y,
                                                float width, float height, float borderWidth,
                                                float z, float[] color) {
        // Top border
        addQuadToUiColoredBuffer(buffer, x - borderWidth, y - borderWidth,
                width + 2 * borderWidth, borderWidth, z, color);
        // Bottom border
        addQuadToUiColoredBuffer(buffer, x - borderWidth, y + height,
                width + 2 * borderWidth, borderWidth, z, color);
        // Left border
        addQuadToUiColoredBuffer(buffer, x - borderWidth, y,
                borderWidth, height, z, color);
        // Right border
        addQuadToUiColoredBuffer(buffer, x + width, y,
                borderWidth, height, z, color);
    }

    // Optimized text rendering methods
    private void renderInventoryText(List<InventorySlot> slots, Font font,
                                     float panelX, float panelY, float slotSize,
                                     float slotMargin, int slotsPerRow) {
        float currentSlotX = panelX + slotMargin;
        float currentSlotY = panelY + slotMargin;
        int colCount = 0;
        final float textPadding = 4f;

        for (int i = 0; i < slots.size(); i++) {
            InventorySlot slot = slots.get(i);
            if (slot != null && !slot.isEmpty() && slot.getQuantity() > 1) {
                String quantityStr = String.valueOf(slot.getQuantity());
                float textWidth = font.getTextWidth(quantityStr);
                float textX = currentSlotX + slotSize - textWidth - textPadding;
                float textY = currentSlotY + slotSize - textPadding;

                // Add text shadow for better visibility
                font.drawText(textX + 1, textY + 1, quantityStr, 0f, 0f, 0f); // Shadow
                font.drawText(textX, textY, quantityStr, 1f, 1f, 1f); // Main text
            }

            currentSlotX += slotSize + slotMargin;
            colCount++;
            if (colCount >= slotsPerRow) {
                colCount = 0;
                currentSlotX = panelX + slotMargin;
                currentSlotY += slotSize + slotMargin;
            }
        }
    }

    private void renderCraftingText(List<org.isogame.crafting.CraftingRecipe> recipes,
                                    Game game, Font font, Font titleFont,
                                    float panelX, float panelY, float panelWidth,
                                    float rowHeight) {
        // Title
        String title = "Crafting";
        float titleWidth = titleFont.getTextWidthScaled(title, 0.5f);
        titleFont.drawTextWithSpacing(panelX + (panelWidth - titleWidth) / 2f,
                panelY + 5f, title, 0.5f, -15.0f, 1f, 1f, 1f);

        // Recipe text
        float currentRecipeY = panelY + 40f; // Start below title
        final float recipeIconSize = 32f;

        for (org.isogame.crafting.CraftingRecipe recipe : recipes) {
            boolean canCraft = game.canCraft(recipe);
            float[] textColor = canCraft ?
                    new float[]{1f, 1f, 1f} :
                    new float[]{0.6f, 0.6f, 0.6f}; // Less grey, more visible

            font.drawText(panelX + 10f + recipeIconSize + 8f,
                    currentRecipeY + 20f,
                    recipe.getOutputItem().getDisplayName(),
                    textColor[0], textColor[1], textColor[2]);

            if (canCraft) {
                float craftButtonWidth = 70f;
                String craftText = "CRAFT";
                float craftTextWidth = font.getTextWidth(craftText);
                float craftTextX = panelX + panelWidth - craftButtonWidth +
                        (craftButtonWidth - craftTextWidth) / 2f - 10f;

                font.drawText(craftTextX, currentRecipeY + 22f, craftText,
                        0.9f, 1f, 0.9f);
            }

            currentRecipeY += rowHeight;
        }
    }



    private void renderDraggedItem(Game game, Font font) {
        double[] xpos = new double[1];
        double[] ypos = new double[1];
        glfwGetCursorPos(game.getWindowHandle(), xpos, ypos);

        // Convert cursor position to framebuffer coordinates
        int[] fbW = new int[1], fbH = new int[1], winW = new int[1], winH = new int[1];
        glfwGetFramebufferSize(game.getWindowHandle(), fbW, fbH);
        glfwGetWindowSize(game.getWindowHandle(), winW, winH);

        double scaleX = (fbW[0] > 0 && winW[0] > 0) ? (double)fbW[0] / winW[0] : 1.0;
        double scaleY = (fbH[0] > 0 && winH[0] > 0) ? (double)fbH[0] / winH[0] : 1.0;

        float mouseX = (float)(xpos[0] * scaleX);
        float mouseY = (float)(ypos[0] * scaleY);

        InventorySlot draggedSlot = game.getDraggedItemStack();
        Item draggedItem = draggedSlot.getItem();
        final float itemRenderSize = 50f;
        final float iconX = mouseX - (itemRenderSize / 2f);
        final float iconY = mouseY - (itemRenderSize / 2f);

        if (draggedItem.hasIconTexture() && treeTexture != null && treeTexture.getId() != 0) {
            defaultShader.bind();
            defaultShader.setUniform("uHasTexture", 1);
            defaultShader.setUniform("uIsSimpleUiElement", 0);

            glActiveTexture(GL_TEXTURE0);
            treeTexture.bind();
            glBindVertexArray(spriteVaoId);
            glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
            spriteVertexBuffer.clear();

            addIconToSpriteBuffer(spriteVertexBuffer, iconX, iconY, itemRenderSize,
                    draggedItem, Z_OFFSET_UI_ELEMENT + 0.01f);
            spriteVertexBuffer.flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
            glDrawArrays(GL_TRIANGLES, 0, 6);

            treeTexture.unbind();
        }

        // Quantity text for dragged item
        if (draggedSlot.getQuantity() > 1) {
            String quantityStr = String.valueOf(draggedSlot.getQuantity());
            float textWidth = font.getTextWidth(quantityStr);
            float textX = iconX + itemRenderSize - textWidth - 4f;
            float textY = iconY + itemRenderSize - 4f;

            // Add shadow for visibility
            font.drawText(textX + 1, textY + 1, quantityStr, 0f, 0f, 0f);
            font.drawText(textX, textY, quantityStr, 1f, 1f, 1f);
        }
    }

    // Fixed icon rendering method
    private void addIconToSpriteBuffer(FloatBuffer buffer, float x, float y,
                                       float size, Item item, float z) {
        float[] tint = WHITE_TINT;

        // Triangle 1
        buffer.put(x).put(y).put(z);
        buffer.put(tint);
        buffer.put(item.getIconU0()).put(item.getIconV0()).put(1.0f);

        buffer.put(x).put(y + size).put(z);
        buffer.put(tint);
        buffer.put(item.getIconU0()).put(item.getIconV1()).put(1.0f);

        buffer.put(x + size).put(y + size).put(z);
        buffer.put(tint);
        buffer.put(item.getIconU1()).put(item.getIconV1()).put(1.0f);

        // Triangle 2
        buffer.put(x).put(y).put(z);
        buffer.put(tint);
        buffer.put(item.getIconU0()).put(item.getIconV0()).put(1.0f);

        buffer.put(x + size).put(y + size).put(z);
        buffer.put(tint);
        buffer.put(item.getIconU1()).put(item.getIconV1()).put(1.0f);

        buffer.put(x + size).put(y).put(z);
        buffer.put(tint);
        buffer.put(item.getIconU1()).put(item.getIconV0()).put(1.0f);
    }

    private void renderCraftingTooltip(org.isogame.crafting.CraftingRecipe recipe,
                                       float mouseX, float mouseY) {
        if (recipe == null) return;

        Font font = getUiFont();
        Game game = (this.inputHandler != null) ? this.inputHandler.getGameInstance() : null;
        if (font == null || game == null || player == null) return;

        // Calculate tooltip size and position
        int ingredientCount = recipe.getRequiredItems().size();
        final float tooltipWidth = 200f;
        final float tooltipHeight = 25f + (ingredientCount * 20f);
        final float tooltipX = mouseX - tooltipWidth - 15f;
        final float tooltipY = mouseY;

        // Draw tooltip background
        defaultShader.bind();
        defaultShader.setUniform("uHasTexture", 0);
        defaultShader.setUniform("uIsSimpleUiElement", 1);

        glBindVertexArray(uiColoredVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, uiColoredVboId);
        uiColoredVertexBuffer.clear();

        float[] tooltipBg = {0.05f, 0.05f, 0.05f, 0.95f}; // Very dark background
        addQuadToUiColoredBuffer(uiColoredVertexBuffer, tooltipX, tooltipY,
                tooltipWidth, tooltipHeight,
                Z_OFFSET_UI_BORDER + 0.01f, tooltipBg);

        uiColoredVertexBuffer.flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, uiColoredVertexBuffer);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        // Draw tooltip text
        float textY = tooltipY + 20f;
        font.drawText(tooltipX + 10f, textY, "Requires:", 1f, 1f, 1f);
        textY += 20f;

        for (java.util.Map.Entry<Item, Integer> entry : recipe.getRequiredItems().entrySet()) {
            Item requiredItem = entry.getKey();
            int requiredAmount = entry.getValue();
            int playerAmount = player.getInventoryItemCount(requiredItem);

            String text = requiredItem.getDisplayName() + ": " + playerAmount + " / " + requiredAmount;
            float[] textColor = (playerAmount >= requiredAmount) ?
                    new float[]{0.6f, 1f, 0.6f} :
                    new float[]{1f, 0.6f, 0.6f};

            font.drawText(tooltipX + 15f, textY, text, textColor[0], textColor[1], textColor[2]);
            textY += 20f;
        }
    }


    public Shader getDefaultShader() { return this.defaultShader; }

    public void renderDebugOverlay(float panelX, float panelY, float panelWidth, float panelHeight, List<String> lines) {
        Font currentUiFont = getUiFont();
        if (currentUiFont == null || !currentUiFont.isInitialized() || defaultShader == null || uiColoredVaoId == 0 || camera == null) {
            return;
        }

        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());
        defaultShader.setUniform("uHasTexture", 0);
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uIsSimpleUiElement", 1);

        glBindVertexArray(uiColoredVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, uiColoredVboId);
        uiColoredVertexBuffer.clear();

        float[] bgColor = {0.1f, 0.1f, 0.1f, 0.8f};
        addQuadToUiColoredBuffer(uiColoredVertexBuffer, panelX, panelY, panelWidth, panelHeight, Z_OFFSET_UI_PANEL + 0.1f, bgColor);

        uiColoredVertexBuffer.flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, uiColoredVertexBuffer);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        defaultShader.setUniform("uIsSimpleUiElement", 0);

        float textX = panelX + 5f;
        float textY = panelY + 5f;
        if (currentUiFont.getAscent() > 0) {
            textY += currentUiFont.getAscent();
        }

        for (String line : lines) {
            currentUiFont.drawText(textX, textY, line, 0.9f, 0.9f, 0.9f);
            textY += 18f;
        }
    }


    public void renderMainMenuBackground() {
        if (mainMenuBackgroundTexture == null || mainMenuBackgroundTexture.getId() == 0 || defaultShader == null || camera == null || spriteVaoId == 0) {
            return;
        }

        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());
        defaultShader.setUniform("uHasTexture", 1);
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uIsSimpleUiElement", 0);
        defaultShader.setUniform("uTextureSampler", 0);

        glActiveTexture(GL_TEXTURE0);
        mainMenuBackgroundTexture.bind();

        glBindVertexArray(spriteVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        spriteVertexBuffer.clear();

        float screenWidth = camera.getScreenWidth();
        float screenHeight = camera.getScreenHeight();

        addVertexToSpriteBuffer(spriteVertexBuffer, 0, 0, Z_OFFSET_UI_BACKGROUND, WHITE_TINT, 0f, 0f, 1f);
        addVertexToSpriteBuffer(spriteVertexBuffer, 0, screenHeight, Z_OFFSET_UI_BACKGROUND, WHITE_TINT, 0f, 1f, 1f);
        addVertexToSpriteBuffer(spriteVertexBuffer, screenWidth, 0, Z_OFFSET_UI_BACKGROUND, WHITE_TINT, 1f, 0f, 1f);
        addVertexToSpriteBuffer(spriteVertexBuffer, screenWidth, 0, Z_OFFSET_UI_BACKGROUND, WHITE_TINT, 1f, 0f, 1f);
        addVertexToSpriteBuffer(spriteVertexBuffer, 0, screenHeight, Z_OFFSET_UI_BACKGROUND, WHITE_TINT, 0f, 1f, 1f);
        addVertexToSpriteBuffer(spriteVertexBuffer, screenWidth, screenHeight, Z_OFFSET_UI_BACKGROUND, WHITE_TINT, 1f, 1f, 1f);

        spriteVertexBuffer.flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        mainMenuBackgroundTexture.unbind();
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private int addSlimeVerticesToBuffer_WorldSpace(Slime slime, FloatBuffer buffer) {
        if (playerTexture == null || camera == null || map == null) return 0;

        float sR = slime.getVisualRow();
        float sC = slime.getVisualCol();
        Tile tile = map.getTile(slime.getTileRow(), slime.getTileCol());
        int elev = (tile != null) ? tile.getElevation() : 0;
        float lightVal = (tile != null) ? tile.getFinalLightLevel() / (float) MAX_LIGHT_LEVEL : 1.0f;
        lightVal = Math.max(0.1f, lightVal);

        float sIsoX = (sC - sR) * this.tileHalfWidth;
        float sIsoY = (sC + sR) * this.tileHalfHeight - (elev * TILE_THICKNESS);
        float slimeWorldZ = (sR + sC) * DEPTH_SORT_FACTOR + (elev * 0.005f) + Z_OFFSET_SPRITE_ANIMAL; // Same layer as animal

        // Adjust render size as needed
        float renderWidth = slime.getFrameWidth() * 0.7f;
        float renderHeight = slime.getFrameHeight() * 0.7f;
        float halfRenderWidth = renderWidth / 2.0f;

        float xL = sIsoX - halfRenderWidth;
        float xR = sIsoX + halfRenderWidth;
        float yT = sIsoY - renderHeight;
        float yB = sIsoY;

        int animCol = slime.getVisualFrameIndex();
        int animRow = slime.getAnimationRow();

        // UVs are calculated based on the playerTexture sheet
        float u0 = (float) (animCol * slime.getFrameWidth()) / playerTexture.getWidth();
        float v0 = (float) (animRow * slime.getFrameHeight()) / playerTexture.getHeight();
        float u1 = (float) ((animCol + 1) * slime.getFrameWidth()) / playerTexture.getWidth();
        float v1 = (float) ((animRow + 1) * slime.getFrameHeight()) / playerTexture.getHeight();

        float[] tint = slime.getHealthTint();

        addVertexToSpriteBuffer(buffer, xL, yT, slimeWorldZ, tint, u0, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yB, slimeWorldZ, tint, u0, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yB, slimeWorldZ, tint, u1, v1, lightVal);

        addVertexToSpriteBuffer(buffer, xR, yB, slimeWorldZ, tint, u1, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yT, slimeWorldZ, tint, u1, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yT, slimeWorldZ, tint, u0, v0, lightVal);
        return 6;
    }


    public void renderMenuButton(MenuItemButton button) {
        Font currentUiFont = getUiFont();
        if (currentUiFont == null || !currentUiFont.isInitialized() || defaultShader == null || camera == null) {
            return;
        }

        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());
        defaultShader.setUniform("uIsFont", 0);


        if (button.borderWidth > 0 && button.borderColor != null) {
            defaultShader.setUniform("uHasTexture", 0);
            defaultShader.setUniform("uIsSimpleUiElement", 1);
            glBindVertexArray(uiColoredVaoId);
            glBindBuffer(GL_ARRAY_BUFFER, uiColoredVboId);
            uiColoredVertexBuffer.clear();
            float bx = button.x - button.borderWidth;
            float by = button.y - button.borderWidth;
            float bWidth = button.width + (2 * button.borderWidth);
            float bHeight = button.height + (2 * button.borderWidth);
            addQuadToUiColoredBuffer(uiColoredVertexBuffer, bx, by, bWidth, bHeight, Z_OFFSET_UI_BORDER, button.borderColor);
            uiColoredVertexBuffer.flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, uiColoredVertexBuffer);
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }


        if (button.useTexture && tileAtlasTexture != null && tileAtlasTexture.getId() != 0) {
            defaultShader.setUniform("uHasTexture", 1);
            defaultShader.setUniform("uIsSimpleUiElement", 0);
            defaultShader.setUniform("uTextureSampler", 0);
            glActiveTexture(GL_TEXTURE0);
            tileAtlasTexture.bind();
            glBindVertexArray(spriteVaoId);
            glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
            spriteVertexBuffer.clear();

            float[] tintToUse = button.isHovered ? new float[]{1.1f, 1.1f, 1.05f, 1.0f} : WHITE_TINT;

            addVertexToSpriteBuffer(spriteVertexBuffer, button.x, button.y, Z_OFFSET_UI_ELEMENT, tintToUse, button.u0, button.v0, 1f);
            addVertexToSpriteBuffer(spriteVertexBuffer, button.x, button.y + button.height, Z_OFFSET_UI_ELEMENT, tintToUse, button.u0, button.v1, 1f);
            addVertexToSpriteBuffer(spriteVertexBuffer, button.x + button.width, button.y, Z_OFFSET_UI_ELEMENT, tintToUse, button.u1, button.v0, 1f);

            addVertexToSpriteBuffer(spriteVertexBuffer, button.x + button.width, button.y, Z_OFFSET_UI_ELEMENT, tintToUse, button.u1, button.v0, 1f);
            addVertexToSpriteBuffer(spriteVertexBuffer, button.x, button.y + button.height, Z_OFFSET_UI_ELEMENT, tintToUse, button.u0, button.v1, 1f);
            addVertexToSpriteBuffer(spriteVertexBuffer, button.x + button.width, button.y + button.height, Z_OFFSET_UI_ELEMENT, tintToUse, button.u1, button.v1, 1f);

            spriteVertexBuffer.flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
            glDrawArrays(GL_TRIANGLES, 0, 6);
            tileAtlasTexture.unbind();
        } else {
            defaultShader.setUniform("uHasTexture", 0);
            defaultShader.setUniform("uIsSimpleUiElement", 1);
            glBindVertexArray(uiColoredVaoId);
            glBindBuffer(GL_ARRAY_BUFFER, uiColoredVboId);
            uiColoredVertexBuffer.clear();
            float[] topQuadColor = button.isHovered ? button.hoverBackgroundColor : button.baseBackgroundColor;
            float gradientFactor = 0.15f;
            float[] bottomQuadColor = new float[]{
                    Math.max(0f, topQuadColor[0] - gradientFactor),
                    Math.max(0f, topQuadColor[1] - gradientFactor),
                    Math.max(0f, topQuadColor[2] - gradientFactor),
                    topQuadColor[3] };
            addGradientQuadToUiColoredBuffer(uiColoredVertexBuffer, button.x, button.y, button.width, button.height, Z_OFFSET_UI_ELEMENT, topQuadColor, bottomQuadColor);
            uiColoredVertexBuffer.flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, uiColoredVertexBuffer);
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        defaultShader.setUniform("uIsSimpleUiElement", 0);

        float[] currentTextColor = button.isHovered ? button.hoverTextColor : button.baseTextColor;
        float textWidth = currentUiFont.getTextWidthScaled(button.text, 1.0f);
        float textX = button.x + (button.width - textWidth) / 2f;
        float textY = button.y + button.height / 2f + currentUiFont.getAscent() / 2f -2f;
        if (currentUiFont.isInitialized()) {
            currentUiFont.drawText(textX, textY, button.text, currentTextColor[0], currentTextColor[1], currentTextColor[2]);
        }
    }
    private void addQuadToUiColoredBuffer(FloatBuffer buffer, float x, float y, float w, float h, float z, float[] color) {
        buffer.put(x).put(y).put(z).put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        buffer.put(x).put(y + h).put(z).put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        buffer.put(x + w).put(y).put(z).put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        buffer.put(x + w).put(y).put(z).put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        buffer.put(x).put(y + h).put(z).put(color[0]).put(color[1]).put(color[2]).put(color[3]);
        buffer.put(x + w).put(y + h).put(z).put(color[0]).put(color[1]).put(color[2]).put(color[3]);
    }

    private void addGradientQuadToUiColoredBuffer(FloatBuffer buffer, float x, float y, float w, float h, float z, float[] topColor, float[] bottomColor) {
        buffer.put(x).put(y).put(z).put(topColor[0]).put(topColor[1]).put(topColor[2]).put(topColor[3]);
        buffer.put(x).put(y + h).put(z).put(bottomColor[0]).put(bottomColor[1]).put(bottomColor[2]).put(bottomColor[3]);
        buffer.put(x + w).put(y).put(z).put(topColor[0]).put(topColor[1]).put(topColor[2]).put(topColor[3]);
        buffer.put(x + w).put(y).put(z).put(topColor[0]).put(topColor[1]).put(topColor[2]).put(topColor[3]);
        buffer.put(x).put(y + h).put(z).put(bottomColor[0]).put(bottomColor[1]).put(bottomColor[2]).put(bottomColor[3]);
        buffer.put(x + w).put(y + h).put(z).put(bottomColor[0]).put(bottomColor[1]).put(bottomColor[2]).put(bottomColor[3]);
    }

    public void cleanup() {
        System.out.println("Renderer: Cleaning up resources...");
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

        if(uiColoredVaoId!=0) { glDeleteVertexArrays(uiColoredVaoId); uiColoredVaoId=0; }
        if(uiColoredVboId!=0) { glDeleteBuffers(uiColoredVboId); uiColoredVboId=0; }
        if(uiColoredVertexBuffer!=null) { MemoryUtil.memFree(uiColoredVertexBuffer); uiColoredVertexBuffer=null; }
        System.out.println("Renderer: Cleanup complete.");
    }



    // Getters for Game.java to check context
    public Map getMap() { return this.map; }
    public PlayerModel getPlayer() { return this.player; }
    public InputHandler getInputHandler() { return this.inputHandler; }
}
