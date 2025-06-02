package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants;
import org.isogame.entitiy.PlayerModel;
import org.isogame.game.Game;
import org.isogame.input.InputHandler;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
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
    private Random tileDetailRandom;
    private List<Chunk> mapChunks;
    private Shader defaultShader;
    private Matrix4f projectionMatrix;
    private int spriteVaoId, spriteVboId;
    private FloatBuffer spriteVertexBuffer;
    private Texture tileAtlasTexture;
    private Font titleFont;
    private Texture mainMenuBackgroundTexture;

    public static final int FLOATS_PER_VERTEX_TERRAIN_TEXTURED = 10; // Pos(3) Color(4) UV(2) Light(1)
    public static final int FLOATS_PER_VERTEX_SPRITE_TEXTURED = 10;  // Pos(3) Color(4) UV(2) Light(1)
    public static final int FLOATS_PER_VERTEX_UI_COLORED = 7;        // Pos(3) Color(4)

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

    public static class TreeData {
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
        loadAssets();
        initShaders();
        initRenderObjects();
        uploadTileMapGeometry();
    }

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
            playerTexture = playerTexture == null || playerTexture.getId() == 0 ? null : playerTexture;
            treeTexture = treeTexture == null || treeTexture.getId() == 0 ? null : treeTexture;
            tileAtlasTexture = tileAtlasTexture == null || tileAtlasTexture.getId() == 0 ? null : tileAtlasTexture;
            mainMenuBackgroundTexture = mainMenuBackgroundTexture == null || mainMenuBackgroundTexture.getId() == 0 ? null : mainMenuBackgroundTexture;
            uiFont = uiFont == null || !uiFont.isInitialized() ? null : uiFont;
            titleFont = titleFont == null || !titleFont.isInitialized() ? null : titleFont;
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
        mapChunks = new ArrayList<>();
        if (map != null && Constants.CHUNK_SIZE_TILES > 0) { // Use Constants.CHUNK_SIZE_TILES
            int numChunksX = (int) Math.ceil((double) map.getWidth() / Constants.CHUNK_SIZE_TILES);
            int numChunksY = (int) Math.ceil((double) map.getHeight() / Constants.CHUNK_SIZE_TILES);
            for (int cy = 0; cy < numChunksY; cy++) {
                for (int cx = 0; cx < numChunksX; cx++) {
                    Chunk chunk = new Chunk(cx, cy, Constants.CHUNK_SIZE_TILES);
                    chunk.setupGLResources();
                    mapChunks.add(chunk);
                }
            }
        }

        spriteVaoId = glGenVertexArrays();
        glBindVertexArray(spriteVaoId);
        spriteVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        int initialSpriteBufferCapacityFloats = 2048 * FLOATS_PER_VERTEX_SPRITE_TEXTURED;
        spriteVertexBuffer = MemoryUtil.memAllocFloat(initialSpriteBufferCapacityFloats);
        glBufferData(GL_ARRAY_BUFFER, (long) spriteVertexBuffer.capacity() * Float.BYTES, GL_DYNAMIC_DRAW);

        int spriteStride = FLOATS_PER_VERTEX_SPRITE_TEXTURED * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, spriteStride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, spriteStride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, spriteStride, (3 + 4) * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, spriteStride, (3 + 4 + 2) * Float.BYTES);
        glEnableVertexAttribArray(3);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void uploadTileMapGeometry() {
        if (mapChunks == null || map == null || camera == null) return;
        System.out.println("Renderer: Uploading full tile map geometry for " + mapChunks.size() + " chunks.");
        for (Chunk chunk : mapChunks) {
            chunk.uploadGeometry(map, inputHandler, this, camera);
        }
    }

    public void updateChunkByGridCoords(int chunkGridX, int chunkGridY) {
        if (map == null || mapChunks == null || Constants.CHUNK_SIZE_TILES <= 0 || camera == null) return; // Use Constants.CHUNK_SIZE_TILES
        mapChunks.stream()
                .filter(c -> c.chunkGridX == chunkGridX && c.chunkGridY == chunkGridY)
                .findFirst()
                .ifPresent(chunk -> chunk.uploadGeometry(this.map, this.inputHandler, this, camera));
    }

    public void markChunkDirtyForTile(int tileRow, int tileCol) {
        if (Constants.CHUNK_SIZE_TILES > 0) { // Use Constants.CHUNK_SIZE_TILES
            updateChunkByGridCoords(tileCol / Constants.CHUNK_SIZE_TILES, tileRow / Constants.CHUNK_SIZE_TILES);
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

    private void addVertexToList(List<Float> vertexList, float x, float y, float z, float[] color, float u, float v, float light) {
        vertexList.add(x); vertexList.add(y); vertexList.add(z);
        vertexList.add(color[0]); vertexList.add(color[1]); vertexList.add(color[2]); vertexList.add(color[3]);
        vertexList.add(u); vertexList.add(v);
        vertexList.add(light);
    }

    public int addSingleTileVerticesToList_WorldSpace_ForChunk(
            int tileR, int tileC, Tile tile, boolean isSelected,
            List<Float> vertexList,
            float[] chunkBoundsMinMax) {

        if (tile.getType() == Tile.TileType.AIR) {
            return 0;
        }

        int currentTileElevation = tile.getElevation();
        Tile.TileType currentTileTopSurfaceType = tile.getType();

        final float tileGridPlaneCenterX = (tileC - tileR) * this.tileHalfWidth;
        final float tileGridPlaneCenterY = (tileC + tileR) * this.tileHalfHeight;

        final float tileBaseZ = (tileR + tileC) * DEPTH_SORT_FACTOR + (currentTileElevation * 0.005f);
        final float tileTopSurfaceZ = tileBaseZ + Z_OFFSET_TILE_TOP_SURFACE;

        float[] topSurfaceColor = determineTopSurfaceColor(currentTileTopSurfaceType, isSelected);
        float[] sideTintToUse = isSelected ? topSurfaceColor : WHITE_TINT;
        int verticesAddedCount = 0;

        float normalizedLightValue = tile.getFinalLightLevel() / (float) MAX_LIGHT_LEVEL;

        if (currentTileTopSurfaceType != Tile.TileType.WATER) {
            verticesAddedCount += addPedestalSidesToList(
                    vertexList, tileGridPlaneCenterX, tileGridPlaneCenterY, tileBaseZ + Z_OFFSET_TILE_PEDESTAL,
                    sideTintToUse, normalizedLightValue);
        }

        float currentTileTopSurfaceActualY = tileGridPlaneCenterY - (currentTileElevation * TILE_THICKNESS);
        if (currentTileTopSurfaceType == Tile.TileType.WATER) {
            currentTileTopSurfaceActualY = tileGridPlaneCenterY - (Math.max(NIVEL_MAR, currentTileElevation) * TILE_THICKNESS);
        }
        verticesAddedCount += addTopSurfaceToList(
                vertexList, currentTileTopSurfaceType, isSelected,
                tileGridPlaneCenterX, currentTileTopSurfaceActualY, tileTopSurfaceZ,
                topSurfaceColor, WHITE_TINT, normalizedLightValue);

        if (currentTileElevation > 0 && currentTileTopSurfaceType != Tile.TileType.WATER) {
            verticesAddedCount += addStratifiedElevatedSidesToList( // Corrected call
                    vertexList, currentTileElevation,
                    tileGridPlaneCenterX, tileGridPlaneCenterY, tileBaseZ + Z_OFFSET_TILE_SIDES,
                    (float)TILE_THICKNESS, // elevSliceHeight
                    sideTintToUse, normalizedLightValue);
        }
        updateChunkBounds(chunkBoundsMinMax, tileGridPlaneCenterX, tileGridPlaneCenterY,
                currentTileElevation, TILE_THICKNESS,
                this.diamondLeftOffsetX, this.diamondRightOffsetX, this.diamondTopOffsetY, this.diamondBottomOffsetY); // Corrected call


        return verticesAddedCount;
    }

    private int addPedestalSidesToList(List<Float> vertexList,
                                       float tileCenterX, float gridPlaneY, float worldZ,
                                       float[] tint, float lightVal) {
        int vCount = 0;
        float pedestalTopY = gridPlaneY;
        float pedestalBottomY = gridPlaneY + BASE_THICKNESS;

        float pTopLx = tileCenterX + this.diamondLeftOffsetX, pTopLy = pedestalTopY + this.diamondSideOffsetY;
        float pTopRx = tileCenterX + this.diamondRightOffsetX, pTopRy = pedestalTopY + this.diamondSideOffsetY;
        float pTopBx = tileCenterX, pTopBy = pedestalTopY + this.diamondBottomOffsetY;

        float pBotLx = tileCenterX + this.diamondLeftOffsetX, pBotLy = pedestalBottomY + this.diamondSideOffsetY;
        float pBotRx = tileCenterX + this.diamondRightOffsetX, pBotRy = pedestalBottomY + this.diamondSideOffsetY;
        float pBotBx = tileCenterX, pBotBy = pedestalBottomY + this.diamondBottomOffsetY;

        float u0 = DEFAULT_SIDE_U0, v0 = DEFAULT_SIDE_V0, u1 = DEFAULT_SIDE_U1, vSpan = DEFAULT_SIDE_V1 - v0;
        float vRepeats = (BASE_THICKNESS / (float) TILE_HEIGHT) * SIDE_TEXTURE_DENSITY_FACTOR;
        float vBotTex = v0 + vSpan * vRepeats;

        addVertexToList(vertexList, pTopLx, pTopLy, worldZ, tint, u0, v0, lightVal);
        addVertexToList(vertexList, pBotLx, pBotLy, worldZ, tint, u0, vBotTex, lightVal);
        addVertexToList(vertexList, pTopBx, pTopBy, worldZ, tint, u1, v0, lightVal);
        vCount += 3;
        addVertexToList(vertexList, pTopBx, pTopBy, worldZ, tint, u1, v0, lightVal);
        addVertexToList(vertexList, pBotLx, pBotLy, worldZ, tint, u0, vBotTex, lightVal);
        addVertexToList(vertexList, pBotBx, pBotBy, worldZ, tint, u1, vBotTex, lightVal);
        vCount += 3;

        addVertexToList(vertexList, pTopBx, pTopBy, worldZ, tint, u0, v0, lightVal);
        addVertexToList(vertexList, pBotBx, pBotBy, worldZ, tint, u0, vBotTex, lightVal);
        addVertexToList(vertexList, pTopRx, pTopRy, worldZ, tint, u1, v0, lightVal);
        vCount += 3;
        addVertexToList(vertexList, pTopRx, pTopRy, worldZ, tint, u1, v0, lightVal);
        addVertexToList(vertexList, pBotBx, pBotBy, worldZ, tint, u0, vBotTex, lightVal);
        addVertexToList(vertexList, pBotRx, pBotRy, worldZ, tint, u1, vBotTex, lightVal);
        vCount += 3;
        return vCount;
    }

    public Font getTitleFont() { return titleFont; }


    private int addTopSurfaceToList(List<Float> vertexList, Tile.TileType topSurfaceType, boolean isSelected,
                                    float topCenterX, float topCenterY, float worldZ,
                                    float[] actualTopColor, float[] whiteTint, float lightVal) {
        int vCount = 0;
        float topLx = topCenterX + this.diamondLeftOffsetX, topLy = topCenterY + this.diamondSideOffsetY;
        float topRx = topCenterX + this.diamondRightOffsetX, topRy = topCenterY + this.diamondSideOffsetY;
        float topTx = topCenterX, topTy = topCenterY + this.diamondTopOffsetY;
        float topBx = topCenterX, topBy = topCenterY + this.diamondBottomOffsetY;

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
            if (textureTop && !isSelected) colorToUse = whiteTint;
        }

        if (textureTop) {
            float midU = (u0 + u1) / 2f, midV = (v0 + v1Atlas) / 2f;
            addVertexToList(vertexList, topTx, topTy, worldZ, colorToUse, midU, v0, lightVal);
            addVertexToList(vertexList, topLx, topLy, worldZ, colorToUse, u0, midV, lightVal);
            addVertexToList(vertexList, topBx, topBy, worldZ, colorToUse, midU, v1Atlas, lightVal);
            vCount += 3;
            addVertexToList(vertexList, topTx, topTy, worldZ, colorToUse, midU, v0, lightVal);
            addVertexToList(vertexList, topBx, topBy, worldZ, colorToUse, midU, v1Atlas, lightVal);
            addVertexToList(vertexList, topRx, topRy, worldZ, colorToUse, u1, midV, lightVal);
            vCount += 3;
        } else {
            addVertexToList(vertexList, topTx, topTy, worldZ, colorToUse, DUMMY_U, DUMMY_V, lightVal);
            addVertexToList(vertexList, topLx, topLy, worldZ, colorToUse, DUMMY_U, DUMMY_V, lightVal);
            addVertexToList(vertexList, topBx, topBy, worldZ, colorToUse, DUMMY_U, DUMMY_V, lightVal);
            vCount += 3;
            addVertexToList(vertexList, topTx, topTy, worldZ, colorToUse, DUMMY_U, DUMMY_V, lightVal);
            addVertexToList(vertexList, topBx, topBy, worldZ, colorToUse, DUMMY_U, DUMMY_V, lightVal);
            addVertexToList(vertexList, topRx, topRy, worldZ, colorToUse, DUMMY_U, DUMMY_V, lightVal);
            vCount += 3;
        }
        return vCount;
    }

    private Tile.TileType getMaterialTypeForSideSlice(int worldElevationOfBlockItself) {
        Tile.TileType blockType = this.map.determineTileTypeFromElevation(worldElevationOfBlockItself);
        switch(blockType) {
            case GRASS: case SAND: case SNOW:
                return Tile.TileType.DIRT;
            case DIRT: return Tile.TileType.DIRT;
            case ROCK: return Tile.TileType.ROCK;
            case WATER: return Tile.TileType.DIRT; // Or SAND
            default: return Tile.TileType.DIRT;
        }
    }

    private int addStratifiedElevatedSidesToList(List<Float> vertexList, int totalElevationUnits,
                                                 float tileCenterX, float gridPlaneCenterY, float worldZ,
                                                 float elevSliceHeight, float[] tint, float lightVal) {
        int vCount = 0;
        for (int elevUnit = 1; elevUnit <= totalElevationUnits; elevUnit++) {
            // The side material should ideally be based on the actual tile whose side this is.
            // For simplicity, we'll use a default side texture or derive it.
            // Let's assume the side material is based on the block itself, or a generic one.
            // This part might need the actual Tile object if sides vary greatly per TileType.
            // For now, using DEFAULT_SIDE_U0 etc.
            float u0 = DEFAULT_SIDE_U0, v0 = DEFAULT_SIDE_V0, u1 = DEFAULT_SIDE_U1, v1Atlas = DEFAULT_SIDE_V1;
            // Example: If you want specific side textures based on the main tile type:
            // Tile mainTile = map.getTile( (int) ((gridPlaneCenterY - (tileCenterY - elevUnit * elevSliceHeight))/tileHalfHeight - (tileCenterX / tileHalfWidth)) / 2,
            //                             (int) ((gridPlaneCenterY - (tileCenterY - elevUnit * elevSliceHeight))/tileHalfHeight + (tileCenterX / tileHalfWidth)) / 2);
            // if(mainTile != null) { /* switch(mainTile.getType()) { set u0,v0... } */ }


            float vSpanAtlas = v1Atlas - v0;
            float vTopTex = v0;
            float vBotTex = v0 + vSpanAtlas * SIDE_TEXTURE_DENSITY_FACTOR;

            float sliceTopActualY = gridPlaneCenterY - (elevUnit * elevSliceHeight);
            float sliceBottomActualY = gridPlaneCenterY - ((elevUnit - 1) * elevSliceHeight);

            float sTopLx = tileCenterX + this.diamondLeftOffsetX, sTopLy = sliceTopActualY + this.diamondSideOffsetY;
            float sTopRx = tileCenterX + this.diamondRightOffsetX, sTopRy = sliceTopActualY + this.diamondSideOffsetY;
            float sTopBx = tileCenterX, sTopBy = sliceTopActualY + this.diamondBottomOffsetY;

            float sBotLx = tileCenterX + this.diamondLeftOffsetX, sBotLy = sliceBottomActualY + this.diamondSideOffsetY;
            float sBotRx = tileCenterX + this.diamondRightOffsetX, sBotRy = sliceBottomActualY + this.diamondSideOffsetY;
            float sBotBx = tileCenterX, sBotBy = sliceBottomActualY + this.diamondBottomOffsetY;

            addVertexToList(vertexList, sTopLx, sTopLy, worldZ, tint, u0, vTopTex, lightVal);
            addVertexToList(vertexList, sBotLx, sBotLy, worldZ, tint, u0, vBotTex, lightVal);
            addVertexToList(vertexList, sTopBx, sTopBy, worldZ, tint, u1, vTopTex, lightVal);
            vCount += 3;
            addVertexToList(vertexList, sTopBx, sTopBy, worldZ, tint, u1, vTopTex, lightVal);
            addVertexToList(vertexList, sBotLx, sBotLy, worldZ, tint, u0, vBotTex, lightVal);
            addVertexToList(vertexList, sBotBx, sBotBy, worldZ, tint, u1, vBotTex, lightVal);
            vCount += 3;

            addVertexToList(vertexList, sTopBx, sTopBy, worldZ, tint, u0, vTopTex, lightVal);
            addVertexToList(vertexList, sBotBx, sBotBy, worldZ, tint, u0, vBotTex, lightVal);
            addVertexToList(vertexList, sTopRx, sTopRy, worldZ, tint, u1, vTopTex, lightVal);
            vCount += 3;
            addVertexToList(vertexList, sTopRx, sTopRy, worldZ, tint, u1, vTopTex, lightVal);
            addVertexToList(vertexList, sBotBx, sBotBy, worldZ, tint, u0, vBotTex, lightVal);
            addVertexToList(vertexList, sBotRx, sBotRy, worldZ, tint, u1, vBotTex, lightVal);
            vCount += 3;
        }
        return vCount;
    }

    private void updateChunkBounds(float[] chunkBounds, float tileCenterX, float tileCenterY,
                                   int elevUnits, float elevSliceH,
                                   float dLX, float dRX, float dTY, float dBY) { // Added diamond offsets
        float minX = tileCenterX + dLX;
        float maxX = tileCenterX + dRX;
        float minY = tileCenterY - (elevUnits * elevSliceH) + dTY;
        float maxY = tileCenterY + BASE_THICKNESS + dBY;

        chunkBounds[0] = Math.min(chunkBounds[0], minX);
        chunkBounds[1] = Math.min(chunkBounds[1], minY);
        chunkBounds[2] = Math.max(chunkBounds[2], maxX);
        chunkBounds[3] = Math.max(chunkBounds[3], maxY);
    }

    // This was a placeholder, can be removed if not used.
    // public int addGrassVerticesForTile_WorldSpace_ForChunk(int r,int c,Tile t,FloatBuffer b,float[] bounds){return 0;}

    private void collectWorldEntities() {
        worldEntities.clear();
        if (player != null) {
            worldEntities.add(player);
        }
        if (mapChunks != null && camera != null && player != null && Constants.CHUNK_SIZE_TILES > 0) {
            int playerTileCol = player.getTileCol();
            int playerTileRow = player.getTileRow();
            int playerChunkX = playerTileCol / Constants.CHUNK_SIZE_TILES;
            int playerChunkY = playerTileRow / Constants.CHUNK_SIZE_TILES;

            // Get the current render distance from the Game instance
            int actualRenderDistanceEntities = Constants.RENDER_DISTANCE_CHUNKS_DEFAULT; // Default fallback
            if (this.inputHandler != null && this.inputHandler.getGameInstance() != null) {
                actualRenderDistanceEntities = this.inputHandler.getGameInstance().getCurrentRenderDistanceChunks();
            }
            for (int dy = -actualRenderDistanceEntities; dy <= actualRenderDistanceEntities; dy++) {
                for (int dx = -actualRenderDistanceEntities; dx <= actualRenderDistanceEntities; dx++) {
                    int currentChunkGridX = playerChunkX + dx;
                    int currentChunkGridY = playerChunkY + dy;
                    for (Chunk chunk : mapChunks) {
                        if (chunk.chunkGridX == currentChunkGridX && chunk.chunkGridY == currentChunkGridY) {
                            if (camera.isChunkVisible(chunk.getBoundingBox())) {
                                worldEntities.addAll(chunk.getTreesInChunk());
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    // Helper for UI/Sprite methods that directly use spriteVertexBuffer (if needed)
    // This one takes FloatBuffer directly, distinct from the List<Float> helper for terrain
    private void addVertexToSpriteBuffer(FloatBuffer buffer, float x, float y, float z, float[] color, float u, float v, float light) {
        buffer.put(x).put(y).put(z).put(color).put(u).put(v).put(light);
    }

    private int addPlayerVerticesToBuffer_WorldSpace(PlayerModel p, FloatBuffer buffer) {
        if (playerTexture == null || camera == null || map == null || playerTexture.getWidth() == 0) return 0;
        float pR=p.getMapRow(), pC=p.getMapCol();
        Tile tile = map.getTile(p.getTileRow(), p.getTileCol());
        int elev = (tile!=null) ? tile.getElevation() : 0;
        float lightVal = (tile!=null) ? tile.getFinalLightLevel()/(float)MAX_LIGHT_LEVEL : 1.0f;

        float pIsoX=(pC-pR)*(this.tileHalfWidth);
        float pIsoY=(pC+pR)*(this.tileHalfHeight)-(elev*TILE_THICKNESS);

        float tileLogicalZ = (pR + pC) * DEPTH_SORT_FACTOR + (elev * 0.005f);
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
            default:
                return 0;
        }

        float tFinalIsoX = tBaseIsoX;
        float tFinalIsoY = tBaseIsoY;

        float texU0=atlasU0val/treeTexture.getWidth(),texV0=atlasV0val/treeTexture.getHeight();
        float texU1=(atlasU0val+frameW)/treeTexture.getWidth(),texV1=(atlasV0val+frameH)/treeTexture.getHeight();
        float hTW=rendW/2.0f; float yTop=tFinalIsoY-(rendH-anchorYOff),yBot=tFinalIsoY+anchorYOff;
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

        if (mapChunks != null && player != null && camera != null && Constants.CHUNK_SIZE_TILES > 0) {
            int playerTileCol = player.getTileCol();
            int playerTileRow = player.getTileRow();
            int playerChunkX = playerTileCol / Constants.CHUNK_SIZE_TILES;
            int playerChunkY = playerTileRow / Constants.CHUNK_SIZE_TILES;

            int actualRenderDistance = Constants.RENDER_DISTANCE_CHUNKS_DEFAULT;
            if (this.inputHandler != null && this.inputHandler.getGameInstance() != null) {
                actualRenderDistance = this.inputHandler.getGameInstance().getCurrentRenderDistanceChunks();
            }

            for (int dy = -actualRenderDistance; dy <= actualRenderDistance; dy++) {
                for (int dx = -actualRenderDistance; dx <= actualRenderDistance; dx++) {
                    int currentChunkGridX = playerChunkX + dx;
                    int currentChunkGridY = playerChunkY + dy;
                    for (Chunk chunk : mapChunks) {
                        if (chunk.chunkGridX == currentChunkGridX && chunk.chunkGridY == currentChunkGridY) {
                            if (camera.isChunkVisible(chunk.getBoundingBox())) {
                                chunk.render();
                            }
                            break;
                        }
                    }
                }
            }
        }
        if (tileAtlasTexture != null && tileAtlasTexture.getId() != 0) tileAtlasTexture.unbind();

        collectWorldEntities();
        defaultShader.setUniform("uHasTexture", 1);
        defaultShader.setUniform("uTextureSampler", 0);

        if (spriteVaoId != 0 && !worldEntities.isEmpty()) {
            glBindVertexArray(spriteVaoId);
            glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
            spriteVertexBuffer.clear();
            int verticesInBatch = 0;
            Texture currentSpriteTexture = null;

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

                    if (spriteVertexBuffer.remaining() < 6 * FLOATS_PER_VERTEX_SPRITE_TEXTURED) { // Check remaining space
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

    public void renderMainMenuBackground() {
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
        glBindVertexArray(0); // Unbind after use
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    public void renderMenuButton(MenuItemButton button) {
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

        float dummyU = 0f, dummyV = 0f;
        float dummyLight = 1f; // UI elements are fully lit

        if (button.borderWidth > 0 && button.borderColor != null) {
            spriteVertexBuffer.clear();
            defaultShader.setUniform("uHasTexture", 0);
            float bx = button.x - button.borderWidth;
            float by = button.y - button.borderWidth;
            float bWidth = button.width + (2 * button.borderWidth);
            float bHeight = button.height + (2 * button.borderWidth);
            addVertexToSpriteBuffer(spriteVertexBuffer, bx, by, Z_OFFSET_UI_BORDER, button.borderColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, bx, by + bHeight, Z_OFFSET_UI_BORDER, button.borderColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, bx + bWidth, by, Z_OFFSET_UI_BORDER, button.borderColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, bx + bWidth, by, Z_OFFSET_UI_BORDER, button.borderColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, bx, by + bHeight, Z_OFFSET_UI_BORDER, button.borderColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, bx + bWidth, by + bHeight, Z_OFFSET_UI_BORDER, button.borderColor, dummyU, dummyV, dummyLight);
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
                        spriteVertexBuffer.flip();
                        glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                        glDrawArrays(GL_TRIANGLES, 0, faceVerticesToDraw);
                        spriteVertexBuffer.clear();
                        faceVerticesToDraw = 0;
                    }
                    float currentX = button.x + cellX * actualCellDrawWidth; float currentY = button.y + cellY * actualCellDrawHeight;
                    addVertexToSpriteBuffer(spriteVertexBuffer, currentX, currentY, Z_OFFSET_UI_ELEMENT, tintToUse, u0_tile, v0_tile, dummyLight);
                    addVertexToSpriteBuffer(spriteVertexBuffer, currentX, currentY + actualCellDrawHeight, Z_OFFSET_UI_ELEMENT, tintToUse, u0_tile, v1_tile, dummyLight);
                    addVertexToSpriteBuffer(spriteVertexBuffer, currentX + actualCellDrawWidth, currentY, Z_OFFSET_UI_ELEMENT, tintToUse, u1_tile, v0_tile, dummyLight);
                    addVertexToSpriteBuffer(spriteVertexBuffer, currentX + actualCellDrawWidth, currentY, Z_OFFSET_UI_ELEMENT, tintToUse, u1_tile, v0_tile, dummyLight);
                    addVertexToSpriteBuffer(spriteVertexBuffer, currentX, currentY + actualCellDrawHeight, Z_OFFSET_UI_ELEMENT, tintToUse, u0_tile, v1_tile, dummyLight);
                    addVertexToSpriteBuffer(spriteVertexBuffer, currentX + actualCellDrawWidth, currentY + actualCellDrawHeight, Z_OFFSET_UI_ELEMENT, tintToUse, u1_tile, v1_tile, dummyLight);
                    faceVerticesToDraw += 6;
                }
            }
            if (tileAtlasTexture != null) tileAtlasTexture.unbind();
        } else {
            defaultShader.setUniform("uHasTexture", 0);
            float[] topQuadColor = button.isHovered ? button.hoverBackgroundColor : button.baseBackgroundColor;
            float gradientFactor = 0.15f;
            float[] bottomQuadColor = new float[]{ Math.max(0f, topQuadColor[0] - gradientFactor), Math.max(0f, topQuadColor[1] - gradientFactor), Math.max(0f, topQuadColor[2] - gradientFactor), topQuadColor[3] };
            addVertexToSpriteBuffer(spriteVertexBuffer, button.x, button.y, Z_OFFSET_UI_ELEMENT, topQuadColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, button.x, button.y + button.height, Z_OFFSET_UI_ELEMENT, bottomQuadColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, button.x + button.width, button.y, Z_OFFSET_UI_ELEMENT, topQuadColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, button.x + button.width, button.y, Z_OFFSET_UI_ELEMENT, topQuadColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, button.x, button.y + button.height, Z_OFFSET_UI_ELEMENT, bottomQuadColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, button.x + button.width, button.y + button.height, Z_OFFSET_UI_ELEMENT, bottomQuadColor, dummyU, dummyV, dummyLight);
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
        float textWidth = uiFont.getTextWidth(button.text);
        float textX = button.x + (button.width - textWidth) / 2f; // Simple horizontal centering
        float textY = button.y + button.height / 2f + uiFont.getAscent() / 2f -2f; // Adjusted for PressStart2P
        if (uiFont != null && uiFont.isInitialized()) {
            uiFont.drawText(textX, textY, button.text, currentTextColor[0], currentTextColor[1], currentTextColor[2]);
        }
    }

    public void renderHotbar(PlayerModel player, int currentlySelectedHotbarSlot) {
        if (uiFont == null || !uiFont.isInitialized() || player == null || defaultShader == null || camera == null) return;
        float slotSize = 55f; float slotMargin = 6f; float itemRenderSize = slotSize * 0.75f;
        float itemOffset = (slotSize - itemRenderSize) / 2f; int hotbarSlotsToDisplay = HOTBAR_SIZE;
        if (hotbarSlotsToDisplay <= 0) return;
        float totalHotbarWidth = (hotbarSlotsToDisplay * slotSize) + ((Math.max(0, hotbarSlotsToDisplay - 1)) * slotMargin);
        float hotbarX = (camera.getScreenWidth() - totalHotbarWidth) / 2.0f;
        float hotbarY = camera.getScreenHeight() - slotSize - (slotMargin * 3);

        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());
        float dummyU = 0f, dummyV = 0f; float dummyLight = 1f;
        List<InventorySlot> playerInventorySlots = player.getInventorySlots();

        for (int i = 0; i < hotbarSlotsToDisplay; i++) {
            glBindVertexArray(spriteVaoId);
            glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
            spriteVertexBuffer.clear();
            int verticesForThisSlotQuads = 0;
            float currentSlotDrawX = hotbarX + i * (slotSize + slotMargin);
            InventorySlot slot = (i < playerInventorySlots.size()) ? playerInventorySlots.get(i) : null;
            boolean isSelected = (i == currentlySelectedHotbarSlot);
            boolean isEmpty = (slot == null || slot.isEmpty());
            float[] slotBgColor, slotBorderColor; float currentBorderWidth;

            if (isSelected) { slotBgColor = new float[]{0.55f,0.55f,0.3f,0.95f}; slotBorderColor = new float[]{0.9f,0.9f,0.5f,1.0f}; currentBorderWidth = 2.5f; }
            else if (!isEmpty) { slotBgColor = new float[]{0.35f,0.30f,0.20f,0.9f}; slotBorderColor = new float[]{0.20f,0.15f,0.10f,0.9f}; currentBorderWidth = 1.5f; }
            else { slotBgColor = new float[]{0.25f,0.25f,0.28f,0.8f}; slotBorderColor = new float[]{0.15f,0.15f,0.18f,0.85f}; currentBorderWidth = 1.0f; }

            defaultShader.setUniform("uIsFont", 0); defaultShader.setUniform("uHasTexture", 0);

            if (currentBorderWidth > 0) {
                float bx = currentSlotDrawX - currentBorderWidth; float by = hotbarY - currentBorderWidth;
                float bWidth = slotSize + (2 * currentBorderWidth); float bHeight = slotSize + (2 * currentBorderWidth);
                addVertexToSpriteBuffer(spriteVertexBuffer, bx, by, Z_OFFSET_UI_BORDER, slotBorderColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, bx, by + bHeight, Z_OFFSET_UI_BORDER, slotBorderColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, bx + bWidth, by, Z_OFFSET_UI_BORDER, slotBorderColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, bx + bWidth, by, Z_OFFSET_UI_BORDER, slotBorderColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, bx, by + bHeight, Z_OFFSET_UI_BORDER, slotBorderColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, bx + bWidth, by + bHeight, Z_OFFSET_UI_BORDER, slotBorderColor, dummyU, dummyV, dummyLight);
                verticesForThisSlotQuads += 6;
            }
            float gradientFactor = 0.1f; float[] topBgColor = slotBgColor;
            float[] bottomBgColor = new float[]{ Math.max(0f, topBgColor[0]-gradientFactor), Math.max(0f, topBgColor[1]-gradientFactor), Math.max(0f, topBgColor[2]-gradientFactor), topBgColor[3]};
            addVertexToSpriteBuffer(spriteVertexBuffer, currentSlotDrawX, hotbarY, Z_OFFSET_UI_PANEL, topBgColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, currentSlotDrawX, hotbarY + slotSize, Z_OFFSET_UI_PANEL, bottomBgColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, currentSlotDrawX + slotSize, hotbarY, Z_OFFSET_UI_PANEL, topBgColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, currentSlotDrawX + slotSize, hotbarY, Z_OFFSET_UI_PANEL, topBgColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, currentSlotDrawX, hotbarY + slotSize, Z_OFFSET_UI_PANEL, bottomBgColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, currentSlotDrawX + slotSize, hotbarY + slotSize, Z_OFFSET_UI_PANEL, bottomBgColor, dummyU, dummyV, dummyLight);
            verticesForThisSlotQuads += 6;

            if (!isEmpty) {
                Item item = slot.getItem(); float[] itemColor = item.getPlaceholderColor();
                float itemX = currentSlotDrawX + itemOffset; float itemY = hotbarY + itemOffset; float itemZ = Z_OFFSET_UI_ELEMENT;
                addVertexToSpriteBuffer(spriteVertexBuffer, itemX, itemY, itemZ, itemColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, itemX, itemY + itemRenderSize, itemZ, itemColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, itemX + itemRenderSize, itemY, itemZ, itemColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, itemX + itemRenderSize, itemY, itemZ, itemColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, itemX, itemY + itemRenderSize, itemZ, itemColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, itemX + itemRenderSize, itemY + itemRenderSize, itemZ, itemColor, dummyU, dummyV, dummyLight);
                verticesForThisSlotQuads += 6;
            }
            if (verticesForThisSlotQuads > 0) {
                spriteVertexBuffer.flip();
                glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                glDrawArrays(GL_TRIANGLES, 0, verticesForThisSlotQuads);
            }
            glBindVertexArray(0); glBindBuffer(GL_ARRAY_BUFFER, 0);
            if (!isEmpty && slot.getQuantity() > 1) {
                String quantityStr = String.valueOf(slot.getQuantity());
                float qtyTextWidth = uiFont.getTextWidth(quantityStr); float textPaddingFromEdge = 4f;
                float qtyTextX = currentSlotDrawX + slotSize - qtyTextWidth - textPaddingFromEdge;
                float qtyTextY = hotbarY + slotSize - textPaddingFromEdge; // Baseline for text
                uiFont.drawText(qtyTextX, qtyTextY, quantityStr, 1f, 1f, 1f);
            }
        }
        glBindVertexArray(0); glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    public void renderInventoryUI(PlayerModel player) {
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
        float dummyU = 0f, dummyV = 0f, dummyLight = 1f;
        addVertexToSpriteBuffer(spriteVertexBuffer, panelX, panelY, Z_OFFSET_UI_PANEL, panelColor, dummyU, dummyV, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, panelX, panelY + panelHeight, Z_OFFSET_UI_PANEL, panelColor, dummyU, dummyV, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, panelX + panelWidth, panelY, Z_OFFSET_UI_PANEL, panelColor, dummyU, dummyV, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, panelX + panelWidth, panelY, Z_OFFSET_UI_PANEL, panelColor, dummyU, dummyV, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, panelX, panelY + panelHeight, Z_OFFSET_UI_PANEL, panelColor, dummyU, dummyV, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, panelX + panelWidth, panelY + panelHeight, Z_OFFSET_UI_PANEL, panelColor, dummyU, dummyV, dummyLight);
        spriteVertexBuffer.flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        float[] slotColorDefault = {0.4f, 0.4f, 0.45f, 0.9f};
        float[] slotColorSelected = {0.8f, 0.8f, 0.3f, 0.95f};
        float currentSlotDrawX = panelX + slotMargin, currentSlotDrawY = panelY + slotMargin;
        int colCount = 0;

        for (int i = 0; i < slots.size(); i++) {
            InventorySlot slot = slots.get(i);
            float[] actualSlotColor = (i == selectedSlotIndex) ? slotColorSelected : slotColorDefault;
            spriteVertexBuffer.clear();
            int verticesForThisSlot = 0; // Count vertices for this slot's quads
            // Slot Background
            addVertexToSpriteBuffer(spriteVertexBuffer, currentSlotDrawX, currentSlotDrawY, Z_OFFSET_UI_ELEMENT, actualSlotColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, currentSlotDrawX, currentSlotDrawY + slotSize, Z_OFFSET_UI_ELEMENT, actualSlotColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, currentSlotDrawX + slotSize, currentSlotDrawY, Z_OFFSET_UI_ELEMENT, actualSlotColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, currentSlotDrawX + slotSize, currentSlotDrawY, Z_OFFSET_UI_ELEMENT, actualSlotColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, currentSlotDrawX, currentSlotDrawY + slotSize, Z_OFFSET_UI_ELEMENT, actualSlotColor, dummyU, dummyV, dummyLight);
            addVertexToSpriteBuffer(spriteVertexBuffer, currentSlotDrawX + slotSize, currentSlotDrawY + slotSize, Z_OFFSET_UI_ELEMENT, actualSlotColor, dummyU, dummyV, dummyLight);
            verticesForThisSlot += 6;

            if (!slot.isEmpty()) {
                Item item = slot.getItem(); float[] itemColor = item.getPlaceholderColor();
                float itemX = currentSlotDrawX + itemOffset, itemY = currentSlotDrawY + itemOffset;
                float itemZ = Z_OFFSET_UI_ELEMENT + 0.01f; // Slightly in front of slot bg
                addVertexToSpriteBuffer(spriteVertexBuffer, itemX, itemY, itemZ, itemColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, itemX, itemY + itemRenderSize, itemZ, itemColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, itemX + itemRenderSize, itemY, itemZ, itemColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, itemX + itemRenderSize, itemY, itemZ, itemColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, itemX, itemY + itemRenderSize, itemZ, itemColor, dummyU, dummyV, dummyLight);
                addVertexToSpriteBuffer(spriteVertexBuffer, itemX + itemRenderSize, itemY + itemRenderSize, itemZ, itemColor, dummyU, dummyV, dummyLight);
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
        glBindBuffer(GL_ARRAY_BUFFER, 0); glBindVertexArray(0); // Unbind after all slots

        // Render quantities (text, drawn after quads)
        currentSlotDrawX = panelX + slotMargin; currentSlotDrawY = panelY + slotMargin; colCount = 0;
        float textOffsetX = 5f;
        for (InventorySlot slot : slots) {
            if (!slot.isEmpty() && slot.getQuantity() > 1) {
                String quantityStr = String.valueOf(slot.getQuantity());
                float qtyTextWidth = uiFont.getTextWidth(quantityStr);
                float qtyTextX = currentSlotDrawX + slotSize - qtyTextWidth - textOffsetX;
                float qtyTextY = currentSlotDrawY + slotSize - textOffsetX; // Baseline for text
                uiFont.drawText(qtyTextX, qtyTextY, quantityStr, 1f, 1f, 1f);
            }
            currentSlotDrawX += slotSize + slotMargin; colCount++;
            if (colCount >= slotsPerRow) {
                colCount = 0; currentSlotDrawX = panelX + slotMargin; currentSlotDrawY += slotSize + slotMargin;
            }
        }
    }

    public Shader getDefaultShader() {
        return this.defaultShader;
    }
    public void renderDebugOverlay(float panelX, float panelY, float panelWidth, float panelHeight, List<String> lines) {
        if (uiFont == null || !uiFont.isInitialized() || defaultShader == null) return;
        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());
        float[] bgColor = {0.1f, 0.1f, 0.1f, 0.8f}; float z = Z_OFFSET_UI_PANEL + 0.1f;
        float dummyU = 0f, dummyV = 0f, dummyLight = 1f;

        glBindVertexArray(spriteVaoId);
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        spriteVertexBuffer.clear();

        addVertexToSpriteBuffer(spriteVertexBuffer, panelX, panelY, z, bgColor, dummyU, dummyV, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, panelX, panelY + panelHeight, z, bgColor, dummyU, dummyV, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, panelX + panelWidth, panelY, z, bgColor, dummyU, dummyV, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, panelX + panelWidth, panelY, z, bgColor, dummyU, dummyV, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, panelX, panelY + panelHeight, z, bgColor, dummyU, dummyV, dummyLight);
        addVertexToSpriteBuffer(spriteVertexBuffer, panelX + panelWidth, panelY + panelHeight, z, bgColor, dummyU, dummyV, dummyLight);

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

    public void cleanup() {
        if(playerTexture!=null) playerTexture.delete();
        if(treeTexture!=null) treeTexture.delete();
        if(tileAtlasTexture!=null) tileAtlasTexture.delete();
        if(mainMenuBackgroundTexture != null) mainMenuBackgroundTexture.delete();
        if(uiFont!=null) uiFont.cleanup();
        if (titleFont != null) titleFont.cleanup();
        if(defaultShader!=null) defaultShader.cleanup();
        if(mapChunks!=null) { for(Chunk ch:mapChunks) ch.cleanup(); mapChunks.clear(); }
        if(spriteVaoId!=0) { glDeleteVertexArrays(spriteVaoId); spriteVaoId=0; }
        if(spriteVboId!=0) { glDeleteBuffers(spriteVboId); spriteVboId=0; }
        if(spriteVertexBuffer!=null) { MemoryUtil.memFree(spriteVertexBuffer); spriteVertexBuffer=null; }
    }
}