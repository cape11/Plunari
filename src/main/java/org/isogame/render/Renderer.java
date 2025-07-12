
package org.isogame.render;

import org.isogame.asset.AssetManager;
import org.isogame.camera.CameraManager;

import org.isogame.world.structure.StructureManager;

import org.isogame.constants.Constants;
import org.isogame.entity.*;
import org.isogame.game.EntityManager;
import org.isogame.game.Game;
import org.isogame.gamedata.AnchorDefinition;
import org.isogame.input.InputHandler;
import org.isogame.item.Item;
import org.isogame.map.LightManager;
import org.isogame.map.Map;
import org.isogame.tile.FurnaceEntity;
import org.isogame.tile.Tile;
import org.isogame.tile.TileEntity;
import org.isogame.ui.MenuItemButton;
import org.isogame.ui.UIManager;
import org.isogame.world.World;
import org.isogame.world.structure.Wall;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.*;
import static org.isogame.constants.Constants.HOTBAR_SIZE;


import static org.isogame.constants.Constants.*;
import static org.lwjgl.glfw.GLFW.glfwGetCursorPos;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.glfw.GLFW.*;

import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

public class Renderer {
    private final CameraManager camera;
    private Map map;
    private PlayerModel player;
    private InputHandler inputHandler;
    private EntityManager entityManager;
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
    private int uiColoredVaoId, uiColoredVboId;
    private FloatBuffer uiColoredVertexBuffer;
    private static final int MAX_UI_COLORED_QUADS = 512;
    private Texture tileAtlasTexture;
    private Texture mainMenuBackgroundTexture;
    private UIManager uiManager;

    public static final int FLOATS_PER_VERTEX_TERRAIN_TEXTURED = 10;
    public static final int FLOATS_PER_VERTEX_SPRITE_TEXTURED = 10;
    public static final int FLOATS_PER_VERTEX_UI_COLORED = 7;
    public static final int FLOATS_PER_VERTEX_SHADOW = 7;

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


    // --- NEW: Shadow Rendering Resources ---
    private int particleVaoId, particleVboId;
    private FloatBuffer particleVertexBuffer;
    private static final int MAX_PARTICLE_QUADS = 1024;
    public static final int FLOATS_PER_VERTEX_PARTICLE = 7; // x,y,z, r,g,b,a

    private static final int MAX_SHADOW_QUADS = 1024; // Max shadows per frame
    private static final float[] SHADOW_COLOR = {0.0f, 0.0f, 0.0f, 0.4f}; // RGBA for shadows
    private static final float Z_OFFSET_SHADOW = 0.001f; // Just above the tile surface

    private AssetManager assetManager;


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
    private List<Particle> particleEntities = new ArrayList<>();

    public Renderer(CameraManager camera, Map map, PlayerModel player, InputHandler inputHandler) {
        this.camera = camera;
        this.map = map;
        this.player = player;
        this.inputHandler = inputHandler;
        this.entityManager = null;
        this.tileDetailRandom = new Random();
        this.projectionMatrix = new Matrix4f();
        this.activeMapChunks = new HashMap<>();
        loadAssets();
        initShaders();
        initRenderObjects();
        initUiColoredResources();
        initParticleResources();

    }


    /**
     * Sets the game-specific context for the renderer.
     * Called when a game is loaded or a new game starts.
     */
    public void setGameSpecificReferences(Map map, PlayerModel player, InputHandler inputHandler, EntityManager entityManager) {
        this.map = map;
        this.player = player;
        this.inputHandler = inputHandler;
        this.entityManager = entityManager;
        if (this.activeMapChunks != null) {
            for (Chunk chunk : this.activeMapChunks.values()) {
                chunk.cleanup();
            }
            this.activeMapChunks.clear();
        }
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
        this.entityManager = null;
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
        if (tileAtlasTexture != null) textureMap.put("tileAtlasTexture", tileAtlasTexture);;
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
    // --- NEW METHOD ---



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
            defaultShader.createUniform("u_time");
            defaultShader.createUniform("u_isSelectedIcon");
            defaultShader.createUniform("uIsShadow");
            defaultShader.createUniform("u_ambientLightColor"); // NEW
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
                    vertexBuffer, tile, // Pass the whole tile object
                    tileGridPlaneCenterX, tileGridPlaneCenterY, tileBaseZ + Z_OFFSET_TILE_PEDESTAL,
                    sideTintToUse, normalizedLightValue);
        }

        float currentTileTopSurfaceActualY = tileGridPlaneCenterY - (currentTileElevation * TILE_THICKNESS);
        if (currentTileTopSurfaceType == Tile.TileType.WATER) {
            currentTileTopSurfaceActualY = tileGridPlaneCenterY - (Math.max(NIVEL_MAR -1, currentTileElevation) * TILE_THICKNESS);
        }

        verticesAddedCount += addTopSurfaceToList(
                vertexBuffer, tile, isSelected, // Pass the whole tile object
                tileGridPlaneCenterX, currentTileTopSurfaceActualY, tileTopSurfaceZ,
                topSurfaceColor, WHITE_TINT, normalizedLightValue);


        if (currentTileElevation > 0 && currentTileTopSurfaceType != Tile.TileType.WATER) {
            verticesAddedCount += addStratifiedElevatedSidesToList(
                    vertexBuffer, tile, // Pass the whole tile object
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






    private int addPedestalSidesToList(FloatBuffer vertexBuffer,
                                       Tile tile, // Pass in the tile
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

        // --- Data-Driven Logic Start ---
        // We'll use the "dirt" texture as the default pedestal side.
        org.isogame.gamedata.TileDefinition def = org.isogame.gamedata.TileRegistry.getTileDefinition("dirt");
        if (def == null || def.texture == null || def.texture.side == null) {
            return 0; // Cannot draw pedestal without a default side texture definition.
        }

        float u0, v0, u1, v1Atlas;
        float atlasW = tileAtlasTexture.getWidth();
        float atlasH = tileAtlasTexture.getHeight();

        if (atlasW > 0 && atlasH > 0) {
            org.isogame.gamedata.TileDefinition.TextureCoords tex = def.texture.side;
            u0 = tex.x / atlasW;
            v0 = tex.y / atlasH;
            u1 = (tex.x + tex.w) / atlasW;
            v1Atlas = (tex.y + tex.h) / atlasH;
        } else {
            return 0; // Cannot calculate UVs
        }
        // --- Data-Driven Logic End ---

        float vSpan = v1Atlas - v0;
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
                                    Tile tile, boolean isSelected,
                                    float topCenterX, float topCenterY, float worldZ,
                                    float[] actualTopColor, float[] whiteTint, float lightVal) {

        int vCount = 0;
        float topLx = topCenterX + this.diamondLeftOffsetX;
        float topLy = topCenterY + this.diamondSideOffsetY;
        float topRx = topCenterX + this.diamondRightOffsetX;
        float topRy = topCenterY + this.diamondSideOffsetY;
        float topTx = topCenterX;
        float topTy = topCenterY + this.diamondTopOffsetY;
        float topBx = topCenterX;
        float topBy = topCenterY + this.diamondBottomOffsetY;

        float[] colorToUse = actualTopColor;
        boolean textureTop = false;
        float u0 = 0, v0 = 0, u1 = 0, v1Atlas = 0;

        // Data-driven part starts here!
        if (tile.getType() != Tile.TileType.WATER && tile.getType() != Tile.TileType.AIR) {
            org.isogame.gamedata.TileDefinition def = org.isogame.gamedata.TileRegistry.getTileDefinition(tile.getType().id);

            if (def != null && def.texture != null && def.texture.top != null) {
                textureTop = true;
                // We assume the atlas is the main tileAtlasTexture.
                // This could be made more robust by checking def.texture.atlas if you use multiple atlases.
                float atlasW = tileAtlasTexture.getWidth();
                float atlasH = tileAtlasTexture.getHeight();

                if (atlasW > 0 && atlasH > 0) {
                    org.isogame.gamedata.TileDefinition.TextureCoords tex = def.texture.top;
                    u0 = tex.x / atlasW;
                    v0 = tex.y / atlasH;
                    u1 = (tex.x + tex.w) / atlasW;
                    v1Atlas = (tex.y + tex.h) / atlasH;
                } else {
                    textureTop = false; // Could not get atlas dimensions
                }

                if (textureTop && !isSelected) {
                    colorToUse = whiteTint;
                }
            }
        }

        if (textureTop) {
            float midU = (u0 + u1) / 2f;
            float midV = (v0 + v1Atlas) / 2f;
            addVertexToBuffer(vertexBuffer, topTx, topTy, worldZ, colorToUse, midU, v0, lightVal);
            addVertexToBuffer(vertexBuffer, topLx, topLy, worldZ, colorToUse, u0, midV, lightVal);
            addVertexToBuffer(vertexBuffer, topBx, topBy, worldZ, colorToUse, midU, v1Atlas, lightVal);
            vCount += 3;
            addVertexToBuffer(vertexBuffer, topTx, topTy, worldZ, colorToUse, midU, v0, lightVal);
            addVertexToBuffer(vertexBuffer, topBx, topBy, worldZ, colorToUse, midU, v1Atlas, lightVal);
            addVertexToBuffer(vertexBuffer, topRx, topRy, worldZ, colorToUse, u1, midV, lightVal);
            vCount += 3;
        } else {
            // Fallback for WATER, AIR, or tiles with no texture definition
            addVertexToBuffer(vertexBuffer, topTx, topTy, worldZ, colorToUse, 0, 0, lightVal);
            addVertexToBuffer(vertexBuffer, topLx, topLy, worldZ, colorToUse, 0, 0, lightVal);
            addVertexToBuffer(vertexBuffer, topBx, topBy, worldZ, colorToUse, 0, 0, lightVal);
            vCount += 3;
            addVertexToBuffer(vertexBuffer, topTx, topTy, worldZ, colorToUse, 0, 0, lightVal);
            addVertexToBuffer(vertexBuffer, topBx, topBy, worldZ, colorToUse, 0, 0, lightVal);
            addVertexToBuffer(vertexBuffer, topRx, topRy, worldZ, colorToUse, 0, 0, lightVal);
            vCount += 3;
        }
        return vCount;
    }

    private int addStratifiedElevatedSidesToList(FloatBuffer vertexBuffer,
                                                 Tile tile, // Pass the whole tile object
                                                 float tileCenterX, float gridPlaneCenterY, float worldZ,
                                                 float elevSliceHeight, float[] tint, float initialLightVal,
                                                 int tileR_map, int tileC_map) {
        int vCount = 0;
        float sideLightVal = initialLightVal;
        LightManager lm = (this.map != null) ? this.map.getLightManager() : null;

        if (map != null && lm != null) {
            if (tile != null && tile.getType() != Tile.TileType.WATER) {
                if (lm.isSurfaceTileExposedToSky(tileR_map, tileC_map, tile.getElevation())) {
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

        org.isogame.gamedata.TileDefinition def = org.isogame.gamedata.TileRegistry.getTileDefinition(tile.getType().id);
        if (def == null || def.texture == null || def.texture.side == null) {
            return 0;
        }

        float u0, v0, u1, v1Atlas;
        float atlasW = tileAtlasTexture.getWidth();
        float atlasH = tileAtlasTexture.getHeight();

        if (atlasW > 0 && atlasH > 0) {
            org.isogame.gamedata.TileDefinition.TextureCoords tex = def.texture.side;
            u0 = tex.x / atlasW;
            v0 = tex.y / atlasH;
            u1 = (tex.x + tex.w) / atlasW;
            v1Atlas = (tex.y + tex.h) / atlasH;
        } else {
            return 0;
        }

        // New logic starts here
        float vSpan = v1Atlas - v0; // The total vertical span of the texture
        float vRepeats = (elevSliceHeight / (float) TILE_HEIGHT) * SIDE_TEXTURE_DENSITY_FACTOR;

        for (int elevUnit = 1; elevUnit <= tile.getElevation(); elevUnit++) {
            // Calculate proportional V coordinates for this slice
            float vTopTex = v0;
            float vBotTex = v0 + (vSpan * vRepeats); // Use the calculated repeat factor

            float sliceTopActualY    = gridPlaneCenterY - (elevUnit * elevSliceHeight);
            float sliceBottomActualY = gridPlaneCenterY - ((elevUnit - 1) * elevSliceHeight);

            float sTopLx = tileCenterX + this.diamondLeftOffsetX,  sTopLy = sliceTopActualY + this.diamondSideOffsetY;
            float sTopRx = tileCenterX + this.diamondRightOffsetX, sTopRy = sliceTopActualY + this.diamondSideOffsetY;
            float sTopBx = tileCenterX,                             sTopBy = sliceTopActualY + this.diamondBottomOffsetY;
            float sBotLx = tileCenterX + this.diamondLeftOffsetX,  sBotLy = sliceBottomActualY + this.diamondSideOffsetY;
            float sBotRx = tileCenterX + this.diamondRightOffsetX, sBotRy = sliceBottomActualY + this.diamondSideOffsetY;
            float sBotBx = tileCenterX,                             sBotBy = sliceBottomActualY + this.diamondBottomOffsetY;

            // Draw the Left Face of the slice
            addVertexToBuffer(vertexBuffer, sTopLx, sTopLy, worldZ, tint, u0, vTopTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sBotLx, sBotLy, worldZ, tint, u0, vBotTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sBotBx, sBotBy, worldZ, tint, u1, vBotTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sTopLx, sTopLy, worldZ, tint, u0, vTopTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sBotBx, sBotBy, worldZ, tint, u1, vBotTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sTopBx, sTopBy, worldZ, tint, u1, vTopTex, sideLightVal);
            vCount += 6;

            // Draw the Right Face of the slice
            addVertexToBuffer(vertexBuffer, sTopBx, sTopBy, worldZ, tint, u0, vTopTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sBotBx, sBotBy, worldZ, tint, u0, vBotTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sBotRx, sBotRy, worldZ, tint, u1, vBotTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sTopBx, sTopBy, worldZ, tint, u0, vTopTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sBotRx, sBotRy, worldZ, tint, u1, vBotTex, sideLightVal);
            addVertexToBuffer(vertexBuffer, sTopRx, sTopRy, worldZ, tint, u1, vTopTex, sideLightVal);
            vCount += 6;
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


    private int addTreeVerticesToBuffer_WorldSpace(TreeData tree, FloatBuffer buffer, double deltaTime) {
        if (map == null) return 0;
        Tile tile = map.getTile(Math.round(tree.mapRow), Math.round(tree.mapCol));
        if (tile == null) return 0;

        TreeRenderData data = calculateTreeRenderData(tree);
        if (!data.isValid) return 0;

        float lightVal = tile.getFinalLightLevel() / (float)MAX_LIGHT_LEVEL;
        lightVal = Math.max(0.1f, lightVal);

        float finalIsoX = data.baseIsoX;
        if (tile.treeShakeTimer > 0) {
            float shakeAmount = 2.5f;
            finalIsoX += (tileDetailRandom.nextFloat() - 0.5f) * shakeAmount;
            tile.treeShakeTimer -= deltaTime;
        }

        float tileLogicalZ = (tree.mapRow + tree.mapCol) * DEPTH_SORT_FACTOR + (tree.elevation * 0.005f);
        float treeWorldZ = tileLogicalZ + Z_OFFSET_SPRITE_TREE;

        float halfTreeRenderWidth = data.renderWidth / 2.0f;
        float yTop = data.treeRenderAnchorY - data.renderHeight;
        float yBottom = data.treeRenderAnchorY;

        float xBL = finalIsoX - halfTreeRenderWidth;
        float yBL_sprite = yBottom;
        float xTL = finalIsoX - halfTreeRenderWidth;
        float yTL_sprite = yTop;
        float xTR = finalIsoX + halfTreeRenderWidth;
        float yTR_sprite = yTop;
        float xBR = finalIsoX + halfTreeRenderWidth;
        float yBR_sprite = yBottom;

        addVertexToSpriteBuffer(buffer, xTL, yTL_sprite, treeWorldZ, WHITE_TINT, data.texU0, data.texV0, lightVal);
        addVertexToSpriteBuffer(buffer, xBL, yBL_sprite, treeWorldZ, WHITE_TINT, data.texU0, data.texV1, lightVal);
        addVertexToSpriteBuffer(buffer, xTR, yTR_sprite, treeWorldZ, WHITE_TINT, data.texU1, data.texV0, lightVal);
        addVertexToSpriteBuffer(buffer, xTR, yTR_sprite, treeWorldZ, WHITE_TINT, data.texU1, data.texV0, lightVal);
        addVertexToSpriteBuffer(buffer, xBL, yBL_sprite, treeWorldZ, WHITE_TINT, data.texU0, data.texV1, lightVal);
        addVertexToSpriteBuffer(buffer, xBR, yBR_sprite, treeWorldZ, WHITE_TINT, data.texU1, data.texV1, lightVal);
        return 6;
    }


    public void render(World world, double deltaTime) {
        if (defaultShader == null || camera == null || world == null) {
            return;
        }

        double pseudoTimeOfDay = world.getPseudoTimeOfDay();

        // Bind the main shader and set universal uniforms
        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", camera.getViewMatrix());
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uIsSimpleUiElement", 0);
        defaultShader.setUniform("uIsShadow", 0);

        if (this.map != null && this.map.getLightManager() != null) {
            // This assumes you have the `setUniform(String, Color)` helper in your Shader.java
            defaultShader.setUniform("u_ambientLightColor", this.map.getLightManager().getAmbientLightColor());
        }

        // Render the tile map chunks
        if (map != null && assetManager.getTexture("tileAtlasTexture") != null) {
            glActiveTexture(GL_TEXTURE0);
            assetManager.getTexture("tileAtlasTexture").bind();
            defaultShader.setUniform("uTextureSampler", 0);
            defaultShader.setUniform("uHasTexture", 1);
            for (Chunk chunk : activeMapChunks.values()) {
                if (camera.isChunkVisible(chunk.getBoundingBox())) {
                    chunk.render();
                }
            }
        }

        // Prepare entities and render them
        if (map != null) {
            // *** FIX: Pass the 'world' object down to the helper method ***
            collectWorldEntities(world, deltaTime);

            renderShadows(pseudoTimeOfDay);
            renderWorldSprites(deltaTime);
        }

        // Render particles
        if (map != null && !particleEntities.isEmpty()) {
            renderParticles();
        }
    }

    // In C:/Users/capez/IdeaProjects/JavaGameLWJGL/src/main/java/org/isogame/render/Renderer.java

    public static class TreeRenderData {
        public float baseIsoX, treeRenderAnchorY;
        public float renderWidth, renderHeight;
        public float texU0, texV0, texU1, texV1;
        public boolean isValid = false;
    }

    private void collectWorldEntities(World world, double deltaTime) {
        worldEntities.clear();
        particleEntities.clear();

        // The incorrect line that caused the error has been removed.
        // We now use the 'world' object that is passed directly into this method.

        if (entityManager != null && entityManager.getEntities() != null) {
            for (Entity e : entityManager.getEntities()) {
                if (e instanceof Particle) {
                    particleEntities.add((Particle) e);
                } else {
                    worldEntities.add(e);
                }
            }
        }

        if (world != null && world.getTileEntityManager() != null) {
            worldEntities.addAll(world.getTileEntityManager().getAllTileEntities());
        }

        if (activeMapChunks != null && !activeMapChunks.isEmpty() && camera != null) {
            for (Chunk chunk : activeMapChunks.values()) {
                if (camera.isChunkVisible(chunk.getBoundingBox())) {
                    worldEntities.addAll(chunk.getTreesInChunk());
                    worldEntities.addAll(chunk.getLooseRocksInChunk());
                    worldEntities.addAll(chunk.getTorchesInChunk());
                }
            }
        }

        if (world.getStructureManager() != null) {
            renderStructures(world.getStructureManager(), deltaTime);
        }

        worldEntities.sort(Comparator.comparingDouble(e -> {
            if (e instanceof Entity) return ((Entity) e).getVisualRow() + ((Entity) e).getVisualCol();
            if (e instanceof TreeData) return ((TreeData) e).mapRow + ((TreeData) e).mapCol;
            if (e instanceof LooseRockData) return ((LooseRockData) e).mapRow + ((LooseRockData) e).mapCol;
            if (e instanceof TileEntity) return ((TileEntity) e).getRow() + ((TileEntity) e).getCol() - 0.01f;
            if (e instanceof TorchData) return ((TorchData) e).mapRow + ((TorchData) e).mapCol;

            return 0;
        }));
    }

    private void renderWorldSprites(double deltaTime) {
        if (spriteVaoId == 0 || worldEntities.isEmpty()) {
            return;
        }

        defaultShader.setUniform("uHasTexture", 1);
        defaultShader.setUniform("uTextureSampler", 0);

        glBindVertexArray(spriteVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        spriteVertexBuffer.clear();

        Texture currentBatchTexture = null;
        int verticesInCurrentBatch = 0;

        for (Object entityObj : worldEntities) {
            Texture textureForThisObject = null;

            // Determine the texture for the current object
            if (entityObj instanceof PlayerModel || entityObj instanceof Cow || entityObj instanceof Slime) {
                textureForThisObject = this.assetManager.getTexture("playerTexture");
            } else if (entityObj instanceof DroppedItem) { // 🟨 ADD THIS BLOCK 🟨
                Item item = ((DroppedItem) entityObj).getItem();
                if (item != null) {
                    // This assumes your items have an "atlasName" to find the right texture
                    textureForThisObject = getTextureByName(item.getAtlasName());
                }
            } else if (entityObj instanceof FurnaceEntity || entityObj instanceof TreeData || entityObj instanceof LooseRockData || entityObj instanceof TorchData) {
                textureForThisObject = this.assetManager.getTexture("treeTexture");
            }
            if (textureForThisObject == null) {
                continue; // Skip objects with no texture
            }

            // If the texture changes, we must flush the previous batch before starting a new one.
            if (currentBatchTexture != null && textureForThisObject.getId() != currentBatchTexture.getId()) {
                renderSpriteBatch(spriteVertexBuffer, verticesInCurrentBatch, currentBatchTexture);
                verticesInCurrentBatch = 0;
            }
            currentBatchTexture = textureForThisObject;

            // Estimate vertices to be added.
            int estimatedVertices = 6;
            if (entityObj instanceof PlayerModel) {
                estimatedVertices += 6; // For held item
            }

            // If adding the next object would overflow the buffer, flush the current batch first.
            if (spriteVertexBuffer.position() + (estimatedVertices * FLOATS_PER_VERTEX_SPRITE_TEXTURED) > spriteVertexBuffer.capacity()) {
                renderSpriteBatch(spriteVertexBuffer, verticesInCurrentBatch, currentBatchTexture);
                verticesInCurrentBatch = 0;
            }

            // --- THIS IS THE FIX: The variable is now declared here, inside the loop's scope ---
            int verticesForThisObject = 0;

            // Add the object's vertices to the buffer
            if (entityObj instanceof FurnaceEntity) {
                verticesForThisObject = addFurnaceVerticesToBuffer((FurnaceEntity) entityObj, spriteVertexBuffer);
            } else if (entityObj instanceof DroppedItem) { // 🟨 ADD THIS LINE 🟨
                verticesForThisObject = addDroppedItemVerticesToBuffer((DroppedItem) entityObj, spriteVertexBuffer);
            } else if (entityObj instanceof Entity) {
                verticesForThisObject = addGenericEntityVerticesToBuffer((Entity) entityObj, spriteVertexBuffer);
            } else if (entityObj instanceof FurnaceEntity) {
                verticesForThisObject = addFurnaceVerticesToBuffer((FurnaceEntity) entityObj, spriteVertexBuffer);
            } else if (entityObj instanceof TreeData) {
                verticesForThisObject = addTreeVerticesToBuffer_WorldSpace((TreeData) entityObj, spriteVertexBuffer, deltaTime);
            } else if (entityObj instanceof LooseRockData) {
                verticesForThisObject = addLooseRockVerticesToBuffer_WorldSpace((LooseRockData) entityObj, spriteVertexBuffer);
            } else if (entityObj instanceof TorchData) {
                verticesForThisObject = addTorchVerticesToBuffer_WorldSpace((TorchData) entityObj, spriteVertexBuffer);
            }
            verticesInCurrentBatch += verticesForThisObject;

            // Special case: Render the player's held item immediately after the player
            if (entityObj instanceof PlayerModel) {
                PlayerModel p = (PlayerModel) entityObj;
                Item heldItem = p.getHeldItem();
                AnchorDefinition.AnchorPoint anchor = p.getAnchorForCurrentFrame();
                Texture itemTexture = getTextureByName(heldItem != null ? heldItem.getAtlasName() : null);

                if (heldItem != null && anchor != null && itemTexture != null) {
                    if (itemTexture.getId() != currentBatchTexture.getId()) {
                        renderSpriteBatch(spriteVertexBuffer, verticesInCurrentBatch, currentBatchTexture);
                        verticesInCurrentBatch = 0;
                        currentBatchTexture = itemTexture;
                    }

                    if (spriteVertexBuffer.position() + (6 * FLOATS_PER_VERTEX_SPRITE_TEXTURED) > spriteVertexBuffer.capacity()) {
                        renderSpriteBatch(spriteVertexBuffer, verticesInCurrentBatch, currentBatchTexture);
                        verticesInCurrentBatch = 0;
                    }

                    float playerZ = (p.getVisualRow() + p.getVisualCol()) * DEPTH_SORT_FACTOR + (map.getTile(p.getTileRow(), p.getTileCol()).getElevation() * 0.005f) + Z_OFFSET_SPRITE_PLAYER;
                    float lightVal = map.getTile(p.getTileRow(), p.getTileCol()).getFinalLightLevel() / (float) MAX_LIGHT_LEVEL;
                    float itemZ = anchor.drawBehind ? playerZ + 0.001f : playerZ - 0.001f;

                    verticesInCurrentBatch += addHeldItemVerticesToBuffer(p, heldItem, anchor, spriteVertexBuffer, itemZ, lightVal);
                }
            }
        }

        // After the loop, render any remaining vertices in the final batch.
        if (verticesInCurrentBatch > 0) {
            renderSpriteBatch(spriteVertexBuffer, verticesInCurrentBatch, currentBatchTexture);
        }

        // Unbind everything once all batches are done.
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }


    private void renderShadows(double timeOfDay) {
        if (spriteVaoId == 0 || worldEntities.isEmpty() || map == null) return;
        if (timeOfDay < 0.01 || timeOfDay > 0.49) return;

        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);

        final float MAX_SHADOW_FACTOR = 3.5f;
        final float FADE_START_FACTOR = 2.0f;

        float sunAngle = (float) (timeOfDay / 0.5) * (float) Math.PI;
        float shadowVectorX = -(float) Math.cos(sunAngle);
        float shadowVectorY = (float) Math.sin(sunAngle) * 0.5f;
        float sunElevation = (float) Math.sin(sunAngle);
        if (sunElevation <= 0.01f) sunElevation = 0.01f;

        float rawShadowFactor = 1.0f / sunElevation;
        float shadowAlpha = SHADOW_COLOR[3];
        if (rawShadowFactor > FADE_START_FACTOR) {
            float fadeRange = MAX_SHADOW_FACTOR - FADE_START_FACTOR;
            float fadeProgress = (rawShadowFactor - FADE_START_FACTOR) / fadeRange;
            shadowAlpha = SHADOW_COLOR[3] * (1.0f - Math.max(0.0f, Math.min(1.0f, fadeProgress)));
        }
        float finalShadowFactor = Math.min(rawShadowFactor, MAX_SHADOW_FACTOR);
        float[] finalShadowColor = {SHADOW_COLOR[0], SHADOW_COLOR[1], SHADOW_COLOR[2], shadowAlpha};

        defaultShader.setUniform("uIsShadow", 1);
        defaultShader.setUniform("uHasTexture", 1);
        defaultShader.setUniform("uTextureSampler", 0);
        glBindVertexArray(spriteVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        spriteVertexBuffer.clear();
        Texture currentBatchTexture = null;
        int verticesInCurrentBatch = 0;

        for (Object entityObj : worldEntities) {
            Texture textureForThisShadow = null;
            float casterHeight = 0, casterWidth = 0;
            float baseIsoX = 0, baseIsoY = 0;
            float u0 = 0, v0 = 0, u1 = 1, v1 = 1;

            if (entityObj instanceof Entity) {
                Entity e = (Entity) entityObj;
                Tile tile = map.getTile(e.getTileRow(), e.getTileCol());
                if (tile == null) continue;
                baseIsoX = (e.getVisualCol() - e.getVisualRow()) * tileHalfWidth;
                baseIsoY = (e.getVisualCol() + e.getVisualRow()) * tileHalfHeight - (tile.getElevation() * TILE_THICKNESS);
                if (e instanceof PlayerModel || e instanceof Cow || e instanceof Slime) {
                    textureForThisShadow = this.assetManager.getTexture("playerTexture");
                    casterWidth = (e instanceof PlayerModel) ? PLAYER_WORLD_RENDER_WIDTH : e.getFrameWidth() * 0.75f;
                    casterHeight = (e instanceof PlayerModel) ? PLAYER_WORLD_RENDER_HEIGHT : e.getFrameHeight() * 0.75f;
                    int animCol = e.getVisualFrameIndex();
                    int animRow = e.getAnimationRow();
                    u0 = (float) (animCol * e.getFrameWidth()) / playerTexture.getWidth();
                    v0 = (float) (animRow * e.getFrameHeight()) / playerTexture.getHeight();
                    u1 = u0 + (float) e.getFrameWidth() / playerTexture.getWidth();
                    v1 = v0 + (float) e.getFrameHeight() / textureForThisShadow.getHeight();
                }
            } else if (entityObj instanceof TreeData) {
                TreeData tree = (TreeData) entityObj;
                TreeRenderData data = calculateTreeRenderData(tree);
                if (!data.isValid) continue;

                // ### START OF THE FIX ###
                Tile treeTile = map.getTile(Math.round(tree.mapRow), Math.round(tree.mapCol));
                // Check the tile diagonally in front of the tree (from the camera's perspective)
                Tile blockingTile = map.getTile(Math.round(tree.mapRow - 1), Math.round(tree.mapCol - 1));

                if (treeTile != null && blockingTile != null) {
                    // If the tile in front is higher than the tree's tile, it's occluded, so we skip it.
                    if (blockingTile.getElevation() > treeTile.getElevation() + 1) {
                        continue; // Skip rendering the shadow for this occluded tree
                    }
                }
                // ### END OF THE FIX ###

                textureForThisShadow = this.treeTexture;
                casterWidth = data.renderWidth;
                casterHeight = data.renderHeight;
                baseIsoX = data.baseIsoX;
                baseIsoY = data.treeRenderAnchorY; // Use the perfect anchor point
                u0 = data.texU0;
                v0 = data.texV0;
                u1 = data.texU1;
                v1 = data.texV1;
            }

            if (textureForThisShadow == null || casterHeight <= 0) continue;
            if (currentBatchTexture != null && textureForThisShadow.getId() != currentBatchTexture.getId()) {
                renderSpriteBatch(spriteVertexBuffer, verticesInCurrentBatch, currentBatchTexture);
                verticesInCurrentBatch = 0;
            }
            currentBatchTexture = textureForThisShadow;
            if (spriteVertexBuffer.position() + (6 * FLOATS_PER_VERTEX_SPRITE_TEXTURED) > spriteVertexBuffer.capacity()) {
                renderSpriteBatch(spriteVertexBuffer, verticesInCurrentBatch, currentBatchTexture);
                verticesInCurrentBatch = 0;
            }

            float u_start = u0;
            float u_end = u1;
            if (shadowVectorX < 0) {
                u_start = u1;
                u_end = u0;
            }

            float finalShadowLength = casterHeight * finalShadowFactor;
            float shadowOffsetX = shadowVectorX * finalShadowLength;
            float shadowOffsetY = shadowVectorY * finalShadowLength;

            float x1 = baseIsoX - casterWidth / 2.0f;
            float y1 = baseIsoY;
            float x2 = baseIsoX + casterWidth / 2.0f;
            float y2 = baseIsoY;

            float x3 = x2 + shadowOffsetX;
            float y3 = (y2 - casterHeight) + shadowOffsetY;
            float x4 = x1 + shadowOffsetX;
            float y4 = (y1 - casterHeight) + shadowOffsetY;

            addTexturedShadowQuadToBuffer(spriteVertexBuffer, x1, y1, x2, y2, x3, y3, x4, y4, Z_OFFSET_SHADOW, u_start, v1, u_end, v0, finalShadowColor);
            verticesInCurrentBatch += 6;
        }

        if (verticesInCurrentBatch > 0) {
            renderSpriteBatch(spriteVertexBuffer, verticesInCurrentBatch, currentBatchTexture);
        }

        glBindVertexArray(0);
        defaultShader.setUniform("uIsShadow", 0);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }
    private void addTexturedShadowQuadToBuffer(FloatBuffer buffer,
                                               float x1, float y1, float x2, float y2, // Base of the shadow quad
                                               float x3, float y3, float x4, float y4, // Projected end of the shadow
                                               float z,
                                               float u_start, float v_base,      // Texture coords for the base
                                               float u_end, float v_projected,   // Texture coords for the projection
                                               float[] shadowColor) {
        float lightVal = 1.0f; // Shadows are not affected by world light

        // The base of the shadow (x1,y1 and x2,y2) gets the v_base coordinate.
        // The projected part of the shadow (x4,y4 and x3,y3) gets the v_projected coordinate.

        // Triangle 1
        addVertexToSpriteBuffer(buffer, x1, y1, z, shadowColor, u_start, v_base, lightVal);
        addVertexToSpriteBuffer(buffer, x2, y2, z, shadowColor, u_end,   v_base, lightVal);
        addVertexToSpriteBuffer(buffer, x4, y4, z, shadowColor, u_start, v_projected, lightVal);

        // Triangle 2
        addVertexToSpriteBuffer(buffer, x2, y2, z, shadowColor, u_end,   v_base, lightVal);
        addVertexToSpriteBuffer(buffer, x3, y3, z, shadowColor, u_end,   v_projected, lightVal);
        addVertexToSpriteBuffer(buffer, x4, y4, z, shadowColor, u_start, v_projected, lightVal);
    }

    // Add this entire new method to Renderer.java
    private int addDroppedItemVerticesToBuffer(DroppedItem itemEntity, FloatBuffer buffer) {
        Item item = itemEntity.getItem();
        // Cannot draw if the item is null or we don't have map context
        if (item == null || map == null) return 0;

        // --- Position & Depth Calculation ---
        float r = itemEntity.getVisualRow();
        float c = itemEntity.getVisualCol();
        Tile tile = map.getTile(itemEntity.getTileRow(), itemEntity.getTileCol());
        if (tile == null) return 0; // Don't draw on a non-existent tile

        int elev = tile.getElevation();
        float lightVal = Math.max(0.1f, tile.getFinalLightLevel() / (float) MAX_LIGHT_LEVEL);

        float isoX = (c - r) * this.tileHalfWidth;
        // Apply the bobbing effect to the Y position
        float isoY = (c + r) * this.tileHalfHeight - (elev * TILE_THICKNESS) - itemEntity.getBobOffset();
        // Items should be rendered slightly in front of the tile they are on
        float worldZ = (r + c) * DEPTH_SORT_FACTOR + (elev * 0.005f) + Z_OFFSET_SPRITE_ANIMAL;

        // --- Size & UV Calculation ---
        float renderSize = TILE_WIDTH * 0.4f; // How big the item appears on the ground
        float xL = isoX - renderSize / 2f;
        float xR = isoX + renderSize / 2f;
        float yT = isoY - renderSize;
        float yB = isoY;

        // Use the item's own icon coordinates
        float u0 = item.getIconU0();
        float v0 = item.getIconV0();
        float u1 = item.getIconU1();
        float v1 = item.getIconV1();

        // --- Add Vertices to Buffer ---
        addVertexToSpriteBuffer(buffer, xL, yT, worldZ, WHITE_TINT, u0, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yB, worldZ, WHITE_TINT, u0, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yB, worldZ, WHITE_TINT, u1, v1, lightVal);

        addVertexToSpriteBuffer(buffer, xR, yB, worldZ, WHITE_TINT, u1, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yT, worldZ, WHITE_TINT, u1, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yT, worldZ, WHITE_TINT, u0, v0, lightVal);

        return 6; // We added 6 vertices
    }
    private int addFurnaceVerticesToBuffer(FurnaceEntity furnace, FloatBuffer buffer) {
        Texture texture = assetManager.getTexture("treeTexture");
        if (texture == null || map == null) return 0;

        float fR = furnace.getRow();
        float fC = furnace.getCol();
        Tile tile = map.getTile((int)fR, (int)fC);
        if (tile == null) return 0;

        // --- THIS IS THE FIX ---
        // 1. Get the elevation of the tile the furnace is on.
        int elev = tile.getElevation();
        float lightVal = tile.getFinalLightLevel() / (float) MAX_LIGHT_LEVEL;

        // 2. Calculate the base Z-depth INCLUDING the elevation.
        float baseWorldZ = (fR + fC) * DEPTH_SORT_FACTOR + (elev * 0.005f);

        // 3. Add a small positive offset to place the furnace slightly in front of the tile,
        // but behind the player. Z_OFFSET_SPRITE_ANIMAL (0.09f) is a good value for this.
        float furnaceWorldZ = baseWorldZ + Z_OFFSET_SPRITE_ANIMAL;
        // --- END OF FIX ---

        // The rest of the logic for positioning and animation is correct.
        float isoX = (fC - fR) * tileHalfWidth;
        float isoY = (fC + fR) * tileHalfHeight - (elev * TILE_THICKNESS);

        float renderWidth = TILE_WIDTH;
        float renderHeight = TILE_HEIGHT * 1.5f;

        float xL = isoX - renderWidth / 2f;
        float xR = isoX + renderWidth / 2f;
        float yB = isoY + TILE_HEIGHT / 2f;
        float yT = yB - renderHeight;

        float u0, v0, u1, v1;
        float texTotalWidth = texture.getWidth();
        float texTotalHeight = texture.getHeight();
        float frameWidth = 64;
        float frameHeight = 64;

        if (furnace.isSmelting()) {
            int frame = furnace.getCurrentFrame() + 1;
            float sx = frame * frameWidth;
            float sy = 1824;
            u0 = sx / texTotalWidth;
            v0 = sy / texTotalHeight;
            u1 = (sx + frameWidth) / texTotalWidth;
            v1 = (sy + frameHeight) / texTotalHeight;
        } else {
            float sx = 0;
            float sy = 1824;
            u0 = sx / texTotalWidth;
            v0 = sy / texTotalHeight;
            u1 = (sx + frameWidth) / texTotalWidth;
            v1 = (sy + frameHeight) / texTotalHeight;
        }

        addVertexToSpriteBuffer(buffer, xL, yT, furnaceWorldZ, WHITE_TINT, u0, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yB, furnaceWorldZ, WHITE_TINT, u0, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yB, furnaceWorldZ, WHITE_TINT, u1, v1, lightVal);

        addVertexToSpriteBuffer(buffer, xR, yB, furnaceWorldZ, WHITE_TINT, u1, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yT, furnaceWorldZ, WHITE_TINT, u1, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yT, furnaceWorldZ, WHITE_TINT, u0, v0, lightVal);
        return 6;
    }


    // In Renderer.java

    public void renderStructures(StructureManager structureManager, double deltaTime) {
        if (structureManager == null || structureManager.getAllWalls().isEmpty()) {
            return;
        }
        System.out.println(">>> Renderer: Attempting to render " + structureManager.getAllWalls().size() + " walls.");


        // You can create a dedicated VBO/VAO for structures if you want to batch them
        // separately, similar to how you handle sprites. For simplicity, we can reuse
        // the sprite VBO for now.
        glBindVertexArray(spriteVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        spriteVertexBuffer.clear();

        Texture wallTexture = assetManager.getTexture("textu.png"); // Or a dedicated wall texture atlas
        if (wallTexture == null) return;

        glActiveTexture(GL_TEXTURE0);
        wallTexture.bind();
        defaultShader.setUniform("uTextureSampler", 0);
        defaultShader.setUniform("uHasTexture", 1);

        int verticesToDraw = 0;
        for (Wall wall : structureManager.getAllWalls().values()) {
            // Here you would add vertices for the correct model based on wall.getCurrentShape()
            // For now, we'll just draw a placeholder quad for every wall.
            verticesToDraw += addWallVerticesToBuffer(wall, spriteVertexBuffer);
        }

        if (verticesToDraw > 0) {
            spriteVertexBuffer.flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
            glDrawArrays(GL_TRIANGLES, 0, verticesToDraw);
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }


    // In Renderer.java

    private int addWallVerticesToBuffer(Wall wall, FloatBuffer buffer) {
        if (map == null) return 0;

        Tile tile = map.getTile(wall.getRow(), wall.getCol());
        if (tile == null) return 0;

        int elev = tile.getElevation();
        float lightVal = tile.getFinalLightLevel() / (float) MAX_LIGHT_LEVEL;

        // --- THIS IS THE FIX ---

        // 1. Get your main texture atlas
        Texture tileAtlas = assetManager.getTexture("textu.png");
        if (tileAtlas == null) {
            // If this texture can't be found, we can't draw the wall.
            return 0;
        }

        // 2. Set the texture coordinates to match your wood plank texture.
        //    These values are a guess based on standard 16x16 tiles in an atlas.
        //    You might need to adjust them to match your 'textu.png' layout.
        float textureX = 64;  // The X position of the wood plank tile in your atlas
        float textureY = 16;  // The Y position of the wood plank tile in your atlas
        float tileWidth = 16;
        float tileHeight = 16;

        float u0 = textureX / tileAtlas.getWidth();
        float v0 = textureY / tileAtlas.getHeight();
        float u1 = (textureX + tileWidth) / tileAtlas.getWidth();
        float v1 = (textureY + tileHeight) / tileAtlas.getHeight();

        // --- END OF FIX ---

        float isoX = (wall.getCol() - wall.getRow()) * tileHalfWidth;
        float isoY = (wall.getCol() + wall.getRow()) * tileHalfHeight - (elev * TILE_THICKNESS);
        float wallWorldZ = (wall.getRow() + wall.getCol()) * DEPTH_SORT_FACTOR - 0.01f;

        float renderWidth = TILE_WIDTH;
        float renderHeight = TILE_HEIGHT * 2;
        float xL = isoX - renderWidth / 2f;
        float xR = isoX + renderWidth / 2f;
        float yT = isoY - renderHeight + TILE_HEIGHT;
        float yB = isoY + TILE_HEIGHT;

        addVertexToSpriteBuffer(buffer, xL, yT, wallWorldZ, WHITE_TINT, u0, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yB, wallWorldZ, WHITE_TINT, u0, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yB, wallWorldZ, WHITE_TINT, u1, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yB, wallWorldZ, WHITE_TINT, u1, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yT, wallWorldZ, WHITE_TINT, u1, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yT, wallWorldZ, WHITE_TINT, u0, v0, lightVal);

        return 6;
    }
    // --- NEW HELPER METHOD ---
    /**
     * Adds a skewed quad (a parallelogram) to the shadow vertex buffer.
     * The quad is defined by its four world-space corner points.
     */




    private void renderParticles() {
        if (particleVaoId == 0 || particleEntities.isEmpty()) {
            return;
        }

        // Prepare the shader for un-textured drawing
        defaultShader.bind();
        defaultShader.setUniform("uHasTexture", 0);

        // Bind the dedicated particle rendering resources
        glBindVertexArray(particleVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, particleVboId);
        particleVertexBuffer.clear();

        // Fill the buffer with all visible particles
        int verticesInBatch = 0;
        for (Particle particle : particleEntities) {
            if (particleVertexBuffer.remaining() < 6 * FLOATS_PER_VERTEX_PARTICLE) {
                break;
            }
            verticesInBatch += addParticleVerticesToBuffer(particle, particleVertexBuffer);
        }

        if (verticesInBatch > 0) {
            // *** THE DEFINITIVE FIX ***
            // Temporarily disable the depth test to allow blending with objects behind the particles.
            glDisable(GL_DEPTH_TEST);
            glDepthMask(false); // Also disable writing to the depth buffer for transparency

            particleVertexBuffer.flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, particleVertexBuffer);
            glDrawArrays(GL_TRIANGLES, 0, verticesInBatch);

            // *** CRITICAL CLEANUP ***
            // Re-enable the depth test for the UI and subsequent frames.
            glDepthMask(true);
            glEnable(GL_DEPTH_TEST);
        }

        // Unbind everything
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    // *** FIX: A new, simpler helper method for adding particle vertices ***
    private void addVertexToParticleBuffer(FloatBuffer buffer, float x, float y, float z, float[] color) {
        buffer.put(x).put(y).put(z);
        buffer.put(color[0]).put(color[1]).put(color[2]).put(color[3]);
    }

    private int addTorchVerticesToBuffer_WorldSpace(TorchData torch, FloatBuffer buffer) {
        // This assumes your torch animation is on the playerTexture sheet.
        // You will need to find the correct coordinates for your torch sprite.
        // Let's assume a 4-frame animation starting at (0, 24) on the sheet.
        int animCol = (int) ((GLFW.glfwGetTime() * 4) % 4); // 4 frames per second animation
        int animRow = 24; // The row of your torch animation on the sprite sheet
        int frameWidth = 16; // The width of a single torch frame
        int frameHeight = 32; // The height of a single torch frame

        float tR = torch.mapRow;
        float tC = torch.mapCol;
        int elev = torch.elevation;

        Tile tile = map.getTile(Math.round(tR), Math.round(tC));
        float lightVal = 1.0f; // Torches should be fully lit

        float tBaseIsoX = (tC - tR) * this.tileHalfWidth;
        float tBaseIsoY = (tC + tR) * this.tileHalfHeight - (elev * TILE_THICKNESS);
        float torchWorldZ = (tR + tC) * DEPTH_SORT_FACTOR - 0.01f; // In front of tile

        float renderWidth = frameWidth;
        float renderHeight = frameHeight;
        float yOffset = -TILE_HEIGHT / 2.0f; // Position it in the middle of the tile face

        float xL = tBaseIsoX - renderWidth / 2;
        float xR = tBaseIsoX + renderWidth / 2;
        float yT = tBaseIsoY + yOffset - renderHeight;
        float yB = tBaseIsoY + yOffset;

        float u0 = (float) (animCol * frameWidth) / playerTexture.getWidth();
        float v0 = (float) (animRow * frameHeight) / playerTexture.getHeight();
        float u1 = (float) ((animCol + 1) * frameWidth) / playerTexture.getWidth();
        float v1 = (float) ((animRow + 1) * frameHeight) / playerTexture.getHeight();

        addVertexToSpriteBuffer(buffer, xL, yT, torchWorldZ, WHITE_TINT, u0, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yB, torchWorldZ, WHITE_TINT, u0, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yB, torchWorldZ, WHITE_TINT, u1, v1, lightVal);

        addVertexToSpriteBuffer(buffer, xR, yB, torchWorldZ, WHITE_TINT, u1, v1, lightVal);
        addVertexToSpriteBuffer(buffer, xR, yT, torchWorldZ, WHITE_TINT, u1, v0, lightVal);
        addVertexToSpriteBuffer(buffer, xL, yT, torchWorldZ, WHITE_TINT, u0, v0, lightVal);
        return 6;
    }

    private void renderSpriteBatch(FloatBuffer buffer, int vertexCount, Texture texture) {
        if (vertexCount == 0 || texture == null) {
            return; // Nothing to draw or no texture to bind
        }

        // Prepare the buffer for reading by OpenGL
        buffer.flip();

        // Upload the vertex data to the VBO on the GPU
        // This is the call that was previously the bottleneck. Now it's called once per batch.
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);

        // Bind the texture for this specific batch
        glActiveTexture(GL_TEXTURE0);
        texture.bind();

        // Draw all sprites in the batch with a single command
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);

        // Clear the buffer for the next batch
        buffer.clear();
    }
    private int addParticleVerticesToBuffer(Particle particle, FloatBuffer buffer) {
        if (map == null) {
            return 0; // Cannot render without map context
        }

        // --- Step 1: Convert the particle's map position to isometric world coordinates ---
        float pR_visual = particle.getVisualRow();
        float pC_visual = particle.getVisualCol();

        // This is the standard isometric projection formula you use elsewhere
        float pIsoX = (pC_visual - pR_visual) * (Constants.TILE_WIDTH / 2.0f);
        float pIsoY = (pC_visual + pR_visual) * (Constants.TILE_HEIGHT / 2.0f);

        // Apply the particle's unique Z-height (for its up/down movement)
        pIsoY -= particle.getZ();

        // --- Step 2: Calculate the particle's depth for correct sorting ---
        // This ensures particles draw in front of or behind the correct tiles
        int tileR = particle.getTileRow();
        int tileC = particle.getTileCol();
        float baseDepth = (tileR + tileC) * Constants.DEPTH_SORT_FACTOR;

        // Apply a strong negative bias to ensure it's rendered in front of other objects on the same tile
        float particleWorldZ = baseDepth - 0.5f;

        // --- Step 3: Define the particle's quad (the square that gets drawn) ---
        float halfSize = particle.size / 2.0f;
        float xL = pIsoX - halfSize; // Left X
        float xR = pIsoX + halfSize; // Right X
        float yT = pIsoY - halfSize; // Top Y
        float yB = pIsoY + halfSize; // Bottom Y

        // --- Step 4: Calculate the color and fade-out effect ---
        float lifeRatio = (float) particle.getLife() / (float) particle.getMaxLife();
        float alphaFade = Math.min(1.0f, lifeRatio * 2.0f); // Fade out smoothly
        float[] finalColor = { particle.color[0], particle.color[1], particle.color[2], alphaFade };

        // --- Step 5: Add the 6 vertices for the two triangles that make the quad ---
        // Note: This now calls your existing 'addVertexToParticleBuffer' helper method.

        // Triangle 1
        addVertexToParticleBuffer(buffer, xL, yT, particleWorldZ, finalColor); // Top-Left
        addVertexToParticleBuffer(buffer, xL, yB, particleWorldZ, finalColor); // Bottom-Left
        addVertexToParticleBuffer(buffer, xR, yB, particleWorldZ, finalColor); // Bottom-Right

        // Triangle 2
        addVertexToParticleBuffer(buffer, xR, yB, particleWorldZ, finalColor); // Bottom-Right
        addVertexToParticleBuffer(buffer, xR, yT, particleWorldZ, finalColor); // Top-Right
        addVertexToParticleBuffer(buffer, xL, yT, particleWorldZ, finalColor); // Top-Left

        return 6; // We successfully added 6 vertices
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


    private int addHeldItemVerticesToBuffer(PlayerModel player, Item item, AnchorDefinition.AnchorPoint anchor, FloatBuffer buffer, float itemWorldZ, float lightVal) { // <-- CHANGE THIS LINE
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


    public Texture getTextureByName(String atlasName) {
        if (atlasName == null || atlasName.isEmpty()) {
            return null;
        }
        // This maps the string from your JSON to the loaded Texture object
        switch (atlasName) {
            case "tileAtlasTexture":
                return this.tileAtlasTexture;
            case "treeTexture":
                return this.treeTexture;
            case "playerTexture":
                return this.playerTexture;
            default:
                System.err.println("Warning: Tried to get unknown texture atlas named: " + atlasName);
                return null;
        }
    }

    // In Renderer.java



    // In Renderer.java
    public static class TorchData {
        public float mapCol, mapRow;
        public int elevation;
        public TorchData(float tc, float tr, int te) {
            this.mapCol = tc; this.mapRow = tr; this.elevation = te;
        }
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
    private void renderQuantityText(List<org.isogame.item.InventorySlot> slots, Font font,
                                    float hotbarX, float hotbarY, float slotSize,
                                    float slotMargin, int slotsToDisplay) {
        final float textPaddingFromEdge = 4f;

        for (int i = 0; i < slotsToDisplay; i++) {
            org.isogame.item.InventorySlot slot = (i < slots.size()) ? slots.get(i) : null;

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
    private void renderInventoryText(List<org.isogame.item.InventorySlot> slots, Font font,
                                     float panelX, float panelY, float slotSize,
                                     float slotMargin, int slotsPerRow) {
        float currentSlotX = panelX + slotMargin;
        float currentSlotY = panelY + slotMargin;
        int colCount = 0;
        final float textPadding = 4f;

        for (int i = 0; i < slots.size(); i++) {
            org.isogame.item.InventorySlot slot = slots.get(i);
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

    // In C:/Users/capez/IdeaProjects/JavaGameLWJGL/src/main/java/org/isogame/render/Renderer.java

    // In Renderer.java
    private TreeRenderData calculateTreeRenderData(TreeData tree) {
        TreeRenderData data = new TreeRenderData();
        if (treeTexture == null || tree.treeVisualType == Tile.TreeVisualType.NONE) {
            return data;
        }

        float tR = tree.mapRow;
        float tC = tree.mapCol;

        data.baseIsoX = (tC - tR) * this.tileHalfWidth;
        float tBaseIsoY = (tC + tR) * this.tileHalfHeight - (tree.elevation * TILE_THICKNESS);

        float frameW = 0, frameH = 0, atlasU0val = 0, atlasV0val = 0;
        float anchorPixelOffset;

        switch(tree.treeVisualType){
            case APPLE_TREE_FRUITING:
                // Using your confirmed, correct data
                frameW = 90.0f;
                frameH = 115.0f; // Correct Height
                atlasU0val = 0.0f;
                atlasV0val = 0;
                anchorPixelOffset = 115.0f; // Correct Anchor Y
                break;

            case PINE_TREE_SMALL:
                // Using your confirmed, correct data
                frameW = 90.0f;
                frameH = 115.0f; // Correct Height
                atlasU0val = 100.0f;
                atlasV0val = 0;
                anchorPixelOffset = 114.0f; // Correct Anchor Y
                break;

            default: return data;
        }

        // --- This section is now correct ---
        data.renderWidth = TILE_WIDTH * 1.0f;
        // Adjust render height based on the new aspect ratio
        data.renderHeight = data.renderWidth * (frameH / frameW);

        // Calculate the anchor's position in world units
        float anchorYOffsetInWorld = data.renderHeight * (anchorPixelOffset / frameH);

        // The final render position is the tile's base, adjusted by the sprite's height and anchor
        data.treeRenderAnchorY = tBaseIsoY - (data.renderHeight - anchorYOffsetInWorld);

        data.texU0 = atlasU0val / treeTexture.getWidth();
        data.texV0 = atlasV0val / treeTexture.getHeight();
        data.texU1 = (atlasU0val + frameW) / treeTexture.getWidth();
        data.texV1 = (atlasV0val + frameH) / treeTexture.getHeight();
        data.isValid = true;

        return data;
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





    // Fixed icon rendering method
    private void addIconToSpriteBuffer(FloatBuffer buffer, float x, float y, float size, Item item, float z) {
        float[] tint = WHITE_TINT;
        buffer.put(x).put(y).put(z).put(tint).put(item.getIconU0()).put(item.getIconV0()).put(1.0f);
        buffer.put(x).put(y + size).put(z).put(tint).put(item.getIconU0()).put(item.getIconV1()).put(1.0f);
        buffer.put(x + size).put(y + size).put(z).put(tint).put(item.getIconU1()).put(item.getIconV1()).put(1.0f);
        buffer.put(x).put(y).put(z).put(tint).put(item.getIconU0()).put(item.getIconV0()).put(1.0f);
        buffer.put(x + size).put(y + size).put(z).put(tint).put(item.getIconU1()).put(item.getIconV1()).put(1.0f);
        buffer.put(x + size).put(y).put(z).put(tint).put(item.getIconU1()).put(item.getIconV0()).put(1.0f);
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

        if(uiColoredVaoId!=0) { glDeleteVertexArrays(uiColoredVaoId); uiColoredVaoId=0; }
        if(uiColoredVboId!=0) { glDeleteBuffers(uiColoredVboId); uiColoredVboId=0; }
        if(uiColoredVertexBuffer!=null) { MemoryUtil.memFree(uiColoredVertexBuffer); uiColoredVertexBuffer=null; }
        System.out.println("Renderer: Cleanup complete.");
    }



    // Getters for Game.java to check context
    public Map getMap() { return this.map; }
    public PlayerModel getPlayer() { return this.player; }
    public InputHandler getInputHandler() { return this.inputHandler; }

    public void beginUIColoredRendering() {
        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uHasTexture", 0);
        defaultShader.setUniform("uIsSimpleUiElement", 1);
        glBindVertexArray(uiColoredVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, uiColoredVboId);
        uiColoredVertexBuffer.clear();
    }


    public void drawColoredQuad(float x, float y, float w, float h, float z, float[] color) {
        addQuadToUiColoredBuffer(uiColoredVertexBuffer, x, y, w, h, z, color);
    }


    public void drawGradientQuad(float x, float y, float w, float h, float z, float[] topColor, float[] bottomColor) {
        addGradientQuadToUiColoredBuffer(uiColoredVertexBuffer, x, y, w, h, z, topColor, bottomColor);
    }

    public void endUIColoredRendering() {
        uiColoredVertexBuffer.flip();
        if (uiColoredVertexBuffer.limit() > 0) {
            glBufferSubData(GL_ARRAY_BUFFER, 0, uiColoredVertexBuffer);
            glDrawArrays(GL_TRIANGLES, 0, uiColoredVertexBuffer.limit() / FLOATS_PER_VERTEX_UI_COLORED);
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void beginUITexturedRendering() {
        defaultShader.bind();
        defaultShader.setUniform("uHasTexture", 1);
        defaultShader.setUniform("uIsSimpleUiElement", 0);
        defaultShader.setUniform("uTextureSampler", 0);
        glBindVertexArray(spriteVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        spriteVertexBuffer.clear();
    }

    private void initParticleResources() {
        particleVaoId = glGenVertexArrays();
        glBindVertexArray(particleVaoId);
        particleVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, particleVboId);

        int bufferCapacityFloats = MAX_PARTICLE_QUADS * 6 * FLOATS_PER_VERTEX_PARTICLE;
        particleVertexBuffer = MemoryUtil.memAllocFloat(bufferCapacityFloats);
        glBufferData(GL_ARRAY_BUFFER, (long)particleVertexBuffer.capacity() * Float.BYTES, GL_DYNAMIC_DRAW);

        int stride = FLOATS_PER_VERTEX_PARTICLE * Float.BYTES;
        // Position (vec3)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        // Color (vec4)
        glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        System.out.println("[Renderer DEBUG] Particle Resources Initialized. VAO: " + particleVaoId + ", VBO: " + particleVboId);
    }

    public void bindTexture(Texture texture){
        glActiveTexture(GL_TEXTURE0);
        texture.bind();
    }

    public void drawIcon(float x, float y, float size, Item item) {
        addIconQuad(spriteVertexBuffer, x, y, 0.02f, size, item);
    }

    public void endUITexturedRendering() {
        spriteVertexBuffer.flip();
        if(spriteVertexBuffer.limit() > 0) {
            glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
            glDrawArrays(GL_TRIANGLES, 0, spriteVertexBuffer.limit() / FLOATS_PER_VERTEX_SPRITE_TEXTURED);
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }
    public void setAssetManager(AssetManager assetManager) {
        this.assetManager = assetManager;
    }
    public CameraManager getCamera() { return this.camera; }
    public Font getUiFont() { return this.uiFont; }
    public Font getTitleFont() { return this.titleFont; }
    public Shader getDefaultShader() { return this.defaultShader; }
}
