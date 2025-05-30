package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants;
import org.isogame.entitiy.PlayerModel;
import org.isogame.input.InputHandler;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
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

    public static final int FLOATS_PER_VERTEX_TERRAIN_TEXTURED = 10;
    public static final int FLOATS_PER_VERTEX_SPRITE_TEXTURED = 10;

    // Atlas and Color constants
    private static final float ATLAS_TOTAL_WIDTH = 128.0f, ATLAS_TOTAL_HEIGHT = 128.0f;
    private static final float SUB_TEX_WIDTH = 64.0f, SUB_TEX_HEIGHT = 64.0f;
    private static final float GRASS_ATLAS_U0 = (0*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, GRASS_ATLAS_V0 = (0*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    private static final float GRASS_ATLAS_U1 = (1*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, GRASS_ATLAS_V1 = (1*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    private static final float ROCK_ATLAS_U0 = (0*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, ROCK_ATLAS_V0 = (1*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    private static final float ROCK_ATLAS_U1 = (1*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, ROCK_ATLAS_V1 = (2*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    private static final float SAND_ATLAS_U0 = (1*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, SAND_ATLAS_V0 = (1*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    private static final float SAND_ATLAS_U1 = (2*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, SAND_ATLAS_V1 = (2*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    private static final float DEFAULT_SIDE_U0 = (1*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, DEFAULT_SIDE_V0 = (0*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    private static final float DEFAULT_SIDE_U1 = (2*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, DEFAULT_SIDE_V1 = (1*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    private static final float SNOW_ATLAS_U0 = (0*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, SNOW_ATLAS_V0 = (1*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    private static final float SNOW_ATLAS_U1 = (1*SUB_TEX_WIDTH)/ATLAS_TOTAL_WIDTH, SNOW_ATLAS_V1 = (2*SUB_TEX_HEIGHT)/ATLAS_TOTAL_HEIGHT;
    private static final float SNOW_SIDE_ATLAS_U0 = ROCK_ATLAS_U0, SNOW_SIDE_ATLAS_V0 = ROCK_ATLAS_V0;
    private static final float SNOW_SIDE_ATLAS_U1 = ROCK_ATLAS_U1, SNOW_SIDE_ATLAS_V1 = ROCK_ATLAS_V1;
    private static final float SIDE_TEXTURE_DENSITY_FACTOR = 1.0f;
    private static final float DUMMY_U = 0.0f, DUMMY_V = 0.0f;
    private static final float[] SELECTED_TINT = {1.0f, 0.8f, 0.0f, 0.8f};
    private static final float[] WATER_TOP_COLOR = {0.05f, 0.25f, 0.5f, 0.85f};
    private static final float[] SAND_TOP_COLOR = {0.82f,0.7f,0.55f,1f};
    private static final float[] GRASS_TOP_COLOR = {0.20f,0.45f,0.10f,1f};
    private static final float[] ROCK_TOP_COLOR = {0.45f,0.45f,0.45f,1f};
    private static final float[] SNOW_TOP_COLOR = {0.95f,0.95f,1.0f,1f};
    private static final float[] DEFAULT_TOP_COLOR = {1f,0f,1f,1f};
    private static final float[] WHITE_TINT = {1.0f, 1.0f, 1.0f, 1.0f};

    // Z-depth offsets (smaller Z values are closer to the camera)
    private static final float Z_OFFSET_SPRITE_PLAYER = 0.1f;
    private static final float Z_OFFSET_SPRITE_TREE = 0.1f;
    private static final float Z_OFFSET_TILE_TOP_SURFACE = 0.0f;
    private static final float Z_OFFSET_TILE_SIDES = 0.01f;
    private static final float Z_OFFSET_TILE_PEDESTAL = 0.02f;


    public static class TreeData {
        Tile.TreeVisualType treeVisualType;
        float mapCol, mapRow;
        int elevation;
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
        playerTexture = Texture.loadTexture("/org/isogame/render/textures/lpc_character.png");
        treeTexture = Texture.loadTexture("/org/isogame/render/textures/fruit-trees.png");
        tileAtlasTexture = Texture.loadTexture("/org/isogame/render/textures/textu.png");
        try {
            uiFont = new Font("/org/isogame/render/fonts/PressStart2P-Regular.ttf", 16f, this);
        } catch (IOException | RuntimeException e) {
            System.err.println("Renderer CRITICAL: Failed to load UI font: " + e.getMessage());
            uiFont = null;
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

    public Shader getDefaultShader() { return defaultShader; }

    private void initRenderObjects() {
        mapChunks = new ArrayList<>();
        if (map != null && CHUNK_SIZE_TILES > 0) {
            int numChunksX = (int) Math.ceil((double) map.getWidth() / CHUNK_SIZE_TILES);
            int numChunksY = (int) Math.ceil((double) map.getHeight() / CHUNK_SIZE_TILES);
            for (int cy = 0; cy < numChunksY; cy++) {
                for (int cx = 0; cx < numChunksX; cx++) {
                    Chunk chunk = new Chunk(cx, cy, CHUNK_SIZE_TILES);
                    chunk.setupGLResources();
                    mapChunks.add(chunk);
                }
            }
        }

        spriteVaoId = glGenVertexArrays();
        glBindVertexArray(spriteVaoId);
        spriteVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        spriteVertexBuffer = MemoryUtil.memAllocFloat(200 * 6 * FLOATS_PER_VERTEX_SPRITE_TEXTURED);
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

    public void updateChunkContainingTile(int tileRow, int tileCol) {
        updateChunkByGridCoords(tileCol / CHUNK_SIZE_TILES, tileRow / CHUNK_SIZE_TILES);
    }

    public void updateChunkByGridCoords(int chunkGridX, int chunkGridY) {
        if (map == null || mapChunks == null || CHUNK_SIZE_TILES <= 0 || camera == null) return;
        mapChunks.stream()
                .filter(c -> c.chunkGridX == chunkGridX && c.chunkGridY == chunkGridY)
                .findFirst()
                .ifPresent(chunk -> {
                    chunk.uploadGeometry(this.map, this.inputHandler, this, camera);
                });
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
            case SNOW:  return SNOW_TOP_COLOR;
            default:    return DEFAULT_TOP_COLOR;
        }
    }

    public int addSingleTileVerticesToBuffer_WorldSpace_ForChunk(
            int tileR, int tileC, Tile tile, boolean isSelected, FloatBuffer buffer, float[] chunkBoundsMinMax) {

        int currentTileElevation = tile.getElevation();
        Tile.TileType currentTileTopSurfaceType = tile.getType();
        final float tileHalfWidth = TILE_WIDTH / 2.0f;
        final float tileHalfHeight = TILE_HEIGHT / 2.0f;
        final float elevationSliceHeight = TILE_THICKNESS;

        final float tileGridPlaneCenterX = (tileC - tileR) * tileHalfWidth;
        final float tileGridPlaneCenterY = (tileC + tileR) * tileHalfHeight;

        final float tileBaseZ = (tileR + tileC) * DEPTH_SORT_FACTOR + (currentTileElevation * 0.005f);
        final float tileTopSurfaceZ = tileBaseZ + Z_OFFSET_TILE_TOP_SURFACE;

        final float diamondTopOffsetY = -tileHalfHeight;
        final float diamondLeftOffsetX = -tileHalfWidth;
        final float diamondSideOffsetY = 0;
        final float diamondRightOffsetX = tileHalfWidth;
        final float diamondBottomOffsetY = tileHalfHeight;

        float[] topSurfaceColor = determineTopSurfaceColor(currentTileTopSurfaceType, isSelected);
        float[] sideTintToUse = isSelected ? topSurfaceColor : WHITE_TINT;
        int verticesAdded = 0;

        float normalizedLightValue = tile.getFinalLightLevel() / (float) MAX_LIGHT_LEVEL;

        if (currentTileTopSurfaceType != Tile.TileType.WATER) {
            verticesAdded += addPedestalSidesToBuffer(
                    buffer, tileGridPlaneCenterX, tileGridPlaneCenterY, tileBaseZ + Z_OFFSET_TILE_PEDESTAL,
                    diamondLeftOffsetX, diamondSideOffsetY, diamondRightOffsetX, diamondBottomOffsetY,
                    sideTintToUse, normalizedLightValue);
        }

        float currentTileTopSurfaceActualY = tileGridPlaneCenterY - (currentTileElevation * elevationSliceHeight);
        if (currentTileTopSurfaceType == Tile.TileType.WATER) {
            currentTileTopSurfaceActualY = tileGridPlaneCenterY - (NIVEL_MAR * elevationSliceHeight);
        }
        verticesAdded += addTopSurfaceToBuffer(
                buffer, currentTileTopSurfaceType, isSelected,
                tileGridPlaneCenterX, currentTileTopSurfaceActualY, tileTopSurfaceZ,
                diamondLeftOffsetX, diamondSideOffsetY, diamondRightOffsetX, diamondTopOffsetY, diamondBottomOffsetY,
                topSurfaceColor, WHITE_TINT, normalizedLightValue);

        if (currentTileElevation > 0 && currentTileTopSurfaceType != Tile.TileType.WATER) {
            verticesAdded += addStratifiedElevatedSidesToBuffer(
                    buffer, currentTileElevation,
                    tileGridPlaneCenterX, tileGridPlaneCenterY, tileBaseZ + Z_OFFSET_TILE_SIDES,
                    diamondLeftOffsetX, diamondSideOffsetY, diamondRightOffsetX, diamondBottomOffsetY,
                    elevationSliceHeight,
                    sideTintToUse, normalizedLightValue);
        }
        updateChunkBounds(chunkBoundsMinMax, tileGridPlaneCenterX, tileGridPlaneCenterY,
                currentTileElevation, elevationSliceHeight,
                diamondLeftOffsetX, diamondRightOffsetX, diamondTopOffsetY, diamondBottomOffsetY);

        return verticesAdded;
    }

    private int addPedestalSidesToBuffer(FloatBuffer buffer,
                                         float tileCenterX, float gridPlaneY, float worldZ,
                                         float dLeftX, float dSideY, float dRightX, float dBottomY,
                                         float[] tint, float lightVal) {
        int vCount = 0;
        float pedestalTopY = gridPlaneY; float pedestalBottomY = gridPlaneY + BASE_THICKNESS;
        float pTopLx=tileCenterX+dLeftX, pTopLy=pedestalTopY+dSideY, pTopRx=tileCenterX+dRightX, pTopRy=pedestalTopY+dSideY, pTopBx=tileCenterX, pTopBy=pedestalTopY+dBottomY;
        float pBotLx=tileCenterX+dLeftX, pBotLy=pedestalBottomY+dSideY, pBotRx=tileCenterX+dRightX, pBotRy=pedestalBottomY+dSideY, pBotBx=tileCenterX, pBotBy=pedestalBottomY+dBottomY;
        float u0=DEFAULT_SIDE_U0, v0=DEFAULT_SIDE_V0, u1=DEFAULT_SIDE_U1, vSpan=DEFAULT_SIDE_V1-v0;
        float vRepeats = (BASE_THICKNESS/(float)TILE_HEIGHT)*SIDE_TEXTURE_DENSITY_FACTOR; float vBotTex = v0 + vSpan*vRepeats;

        buffer.put(pTopLx).put(pTopLy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0).put(v0).put(lightVal);
        buffer.put(pBotLx).put(pBotLy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0).put(vBotTex).put(lightVal);
        buffer.put(pTopBx).put(pTopBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1).put(v0).put(lightVal);
        vCount+=3;
        buffer.put(pTopBx).put(pTopBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1).put(v0).put(lightVal);
        buffer.put(pBotLx).put(pBotLy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0).put(vBotTex).put(lightVal);
        buffer.put(pBotBx).put(pBotBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1).put(vBotTex).put(lightVal);
        vCount+=3;
        buffer.put(pTopBx).put(pTopBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0).put(v0).put(lightVal);
        buffer.put(pBotBx).put(pBotBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0).put(vBotTex).put(lightVal);
        buffer.put(pTopRx).put(pTopRy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1).put(v0).put(lightVal);
        vCount+=3;
        buffer.put(pTopRx).put(pTopRy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1).put(v0).put(lightVal);
        buffer.put(pBotBx).put(pBotBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0).put(vBotTex).put(lightVal);
        buffer.put(pBotRx).put(pBotRy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1).put(vBotTex).put(lightVal);
        vCount+=3;
        return vCount;
    }

    private int addTopSurfaceToBuffer(FloatBuffer buffer, Tile.TileType topSurfaceType, boolean isSelected,
                                      float topCenterX, float topCenterY, float worldZ,
                                      float dLeftX, float dSideY, float dRightX, float dTopY, float dBottomY,
                                      float[] actualTopColor, float[] whiteTint, float lightVal) {
        int vCount = 0;
        float topLx=topCenterX+dLeftX, topLy=topCenterY+dSideY, topRx=topCenterX+dRightX, topRy=topCenterY+dSideY;
        float topTx=topCenterX, topTy=topCenterY+dTopY, topBx=topCenterX, topBy=topCenterY+dBottomY;
        float[] colorToUse; boolean textureTop = false;
        float u0=DUMMY_U, v0=DUMMY_V, u1=DUMMY_U, v1Atlas=DUMMY_V;

        if (topSurfaceType == Tile.TileType.WATER) colorToUse = actualTopColor;
        else {
            switch(topSurfaceType){
                case GRASS: u0=GRASS_ATLAS_U0;v0=GRASS_ATLAS_V0;u1=GRASS_ATLAS_U1;v1Atlas=GRASS_ATLAS_V1;textureTop=true;break;
                case SAND:  u0=SAND_ATLAS_U0;v0=SAND_ATLAS_V0;u1=SAND_ATLAS_U1;v1Atlas=SAND_ATLAS_V1;textureTop=true;break;
                case ROCK:  u0=ROCK_ATLAS_U0;v0=ROCK_ATLAS_V0;u1=ROCK_ATLAS_U1;v1Atlas=ROCK_ATLAS_V1;textureTop=true;break;
                case SNOW:  u0=SNOW_ATLAS_U0;v0=SNOW_ATLAS_V0;u1=SNOW_ATLAS_U1;v1Atlas=SNOW_ATLAS_V1;textureTop=true;break;
                default: break;
            }
            colorToUse = isSelected ? actualTopColor : (textureTop ? whiteTint : actualTopColor);
        }

        if (textureTop) {
            float midU = (u0+u1)/2f, midV = (v0+v1Atlas)/2f;
            buffer.put(topTx).put(topTy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(midU).put(v0).put(lightVal);
            buffer.put(topLx).put(topLy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(u0).put(midV).put(lightVal);
            buffer.put(topBx).put(topBy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(midU).put(v1Atlas).put(lightVal);
            vCount+=3;
            buffer.put(topTx).put(topTy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(midU).put(v0).put(lightVal);
            buffer.put(topBx).put(topBy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(midU).put(v1Atlas).put(lightVal);
            buffer.put(topRx).put(topRy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(u1).put(midV).put(lightVal);
            vCount+=3;
        } else {
            buffer.put(topTx).put(topTy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(DUMMY_U).put(DUMMY_V).put(lightVal);
            buffer.put(topLx).put(topLy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(DUMMY_U).put(DUMMY_V).put(lightVal);
            buffer.put(topBx).put(topBy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(DUMMY_U).put(DUMMY_V).put(lightVal);
            vCount+=3;
            buffer.put(topTx).put(topTy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(DUMMY_U).put(DUMMY_V).put(lightVal);
            buffer.put(topBx).put(topBy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(DUMMY_U).put(DUMMY_V).put(lightVal);
            buffer.put(topRx).put(topRy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(DUMMY_U).put(DUMMY_V).put(lightVal);
            vCount+=3;
        }
        return vCount;
    }
    private Tile.TileType getMaterialTypeForElevationSlice(int elevationLevel) {
        if (elevationLevel < NIVEL_MAR) return Tile.TileType.WATER;
        if (elevationLevel < NIVEL_ARENA) return Tile.TileType.SAND;
        if (elevationLevel < NIVEL_ROCA) return Tile.TileType.GRASS;
        if (elevationLevel < NIVEL_NIEVE) return Tile.TileType.ROCK;
        return Tile.TileType.SNOW;
    }
    private int addStratifiedElevatedSidesToBuffer(FloatBuffer buffer, int totalElev,
                                                   float tileCenterX, float gridPlaneY, float worldZ,
                                                   float dLeftX, float dSideY, float dRightX, float dBottomY,
                                                   float elevSliceH, float[] tint, float lightVal) {
        int vCount = 0;
        for (int elevStep = totalElev; elevStep >= 1; elevStep--) {
            Tile.TileType sideMatType = getMaterialTypeForElevationSlice(elevStep - 1);
            float u0,v0,u1,v1Atlas,vSpanAtlas;
            switch(sideMatType){
                case GRASS: u0=DEFAULT_SIDE_U0;v0=DEFAULT_SIDE_V0;u1=DEFAULT_SIDE_U1;v1Atlas=DEFAULT_SIDE_V1;break;
                case SAND:  u0=SAND_ATLAS_U0;v0=SAND_ATLAS_V0;u1=SAND_ATLAS_U1;v1Atlas=SAND_ATLAS_V1;break;
                case ROCK:  u0=ROCK_ATLAS_U0;v0=ROCK_ATLAS_V0;u1=ROCK_ATLAS_U1;v1Atlas=ROCK_ATLAS_V1;break;
                case SNOW:  u0=SNOW_SIDE_ATLAS_U0;v0=SNOW_SIDE_ATLAS_V0;u1=SNOW_SIDE_ATLAS_U1;v1Atlas=SNOW_SIDE_ATLAS_V1;break;
                default:    u0=DEFAULT_SIDE_U0;v0=DEFAULT_SIDE_V0;u1=DEFAULT_SIDE_U1;v1Atlas=DEFAULT_SIDE_V1;break;
            }
            vSpanAtlas = v1Atlas - v0;
            float vTopTex = v0; float vBotTex = v0 + vSpanAtlas * SIDE_TEXTURE_DENSITY_FACTOR;

            float sliceTopY = gridPlaneY-(elevStep*elevSliceH); float sliceBotY = gridPlaneY-((elevStep-1)*elevSliceH);
            float sTopLx=tileCenterX+dLeftX,sTopLy=sliceTopY+dSideY,sTopRx=tileCenterX+dRightX,sTopRy=sliceTopY+dSideY,sTopBx=tileCenterX,sTopBy=sliceTopY+dBottomY;
            float sBotLx=tileCenterX+dLeftX,sBotLy=sliceBotY+dSideY,sBotRx=tileCenterX+dRightX,sBotRy=sliceBotY+dSideY,sBotBx=tileCenterX,sBotBy=sliceBotY+dBottomY;

            buffer.put(sTopLx).put(sTopLy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0).put(vTopTex).put(lightVal);
            buffer.put(sBotLx).put(sBotLy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0).put(vBotTex).put(lightVal);
            buffer.put(sTopBx).put(sTopBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1).put(vTopTex).put(lightVal);
            vCount+=3;
            buffer.put(sTopBx).put(sTopBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1).put(vTopTex).put(lightVal);
            buffer.put(sBotLx).put(sBotLy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0).put(vBotTex).put(lightVal);
            buffer.put(sBotBx).put(sBotBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1).put(vBotTex).put(lightVal);
            vCount+=3;
            buffer.put(sTopBx).put(sTopBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0).put(vTopTex).put(lightVal);
            buffer.put(sBotBx).put(sBotBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0).put(vBotTex).put(lightVal);
            buffer.put(sTopRx).put(sTopRy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1).put(vTopTex).put(lightVal);
            vCount+=3;
            buffer.put(sTopRx).put(sTopRy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1).put(vTopTex).put(lightVal);
            buffer.put(sBotBx).put(sBotBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0).put(vBotTex).put(lightVal);
            buffer.put(sBotRx).put(sBotRy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1).put(vBotTex).put(lightVal);
            vCount+=3;
        }
        return vCount;
    }
    private void updateChunkBounds(float[] chunkBounds, float tileCenterX, float tileCenterY, int elev, float elevSliceH, float dLX, float dRX, float dTY, float dBY) {
        float minX = tileCenterX + dLX;
        float maxX = tileCenterX + dRX;
        float minY = tileCenterY - (elev * elevSliceH) + dTY;
        float maxY = tileCenterY + BASE_THICKNESS + dBY;
        chunkBounds[0] = Math.min(chunkBounds[0], minX); chunkBounds[1] = Math.min(chunkBounds[1], minY);
        chunkBounds[2] = Math.max(chunkBounds[2], maxX); chunkBounds[3] = Math.max(chunkBounds[3], maxY);
    }
    public int addGrassVerticesForTile_WorldSpace_ForChunk(int r,int c,Tile t,FloatBuffer b,float[] bounds){return 0;}

    private void collectWorldEntities() {
        worldEntities.clear();
        if (player != null) {
            worldEntities.add(player);
        }

        // --- MODIFIED ENTITY COLLECTION LOGIC ---
        if (mapChunks != null && camera != null && player != null && CHUNK_SIZE_TILES > 0) { //
            int playerTileCol = player.getTileCol(); //
            int playerTileRow = player.getTileRow(); //
            int playerChunkX = playerTileCol / CHUNK_SIZE_TILES; //
            int playerChunkY = playerTileRow / CHUNK_SIZE_TILES; //

            for (int dy = -RENDER_DISTANCE_CHUNKS; dy <= RENDER_DISTANCE_CHUNKS; dy++) { //
                for (int dx = -RENDER_DISTANCE_CHUNKS; dx <= RENDER_DISTANCE_CHUNKS; dx++) { //
                    int currentChunkGridX = playerChunkX + dx;
                    int currentChunkGridY = playerChunkY + dy;

                    for (Chunk chunk : mapChunks) { //
                        if (chunk.chunkGridX == currentChunkGridX && chunk.chunkGridY == currentChunkGridY) {
                            if (camera.isChunkVisible(chunk.getBoundingBox())) { //
                                worldEntities.addAll(chunk.getTreesInChunk()); //
                            }
                            break; // Found the chunk
                        }
                    }
                }
            }
        }
    }

    private int addPlayerVerticesToBuffer_WorldSpace(PlayerModel p, FloatBuffer buffer) {
        if (playerTexture == null || camera == null || map == null || playerTexture.getWidth() == 0) return 0;
        float pR=p.getMapRow(), pC=p.getMapCol();
        Tile tile = map.getTile(p.getTileRow(), p.getTileCol());
        int elev = (tile!=null) ? tile.getElevation() : 0;
        float lightVal = (tile!=null) ? tile.getFinalLightLevel()/(float)MAX_LIGHT_LEVEL : 1.0f;

        float pIsoX=(pC-pR)*(TILE_WIDTH/2.0f);
        float pIsoY=(pC+pR)*(TILE_HEIGHT/2.0f)-(elev*TILE_THICKNESS);

        // Z for player: based on tile's logical Z, then offset to be in front
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

        buffer.put(xTL).put(yTL).put(playerWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU0).put(texV0).put(lightVal);
        buffer.put(xBL).put(yBL).put(playerWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU0).put(texV1).put(lightVal);
        buffer.put(xTR).put(yTR).put(playerWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU1).put(texV0).put(lightVal);
        buffer.put(xTR).put(yTR).put(playerWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU1).put(texV0).put(lightVal);
        buffer.put(xBL).put(yBL).put(playerWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU0).put(texV1).put(lightVal);
        buffer.put(xBR).put(yBR).put(playerWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU1).put(texV1).put(lightVal);
        return 6;
    }

    private int addTreeVerticesToBuffer_WorldSpace(TreeData tree, FloatBuffer buffer) {
        if (treeTexture == null || tree.treeVisualType == Tile.TreeVisualType.NONE || camera == null || map == null || treeTexture.getWidth() == 0) return 0;

        float tR=tree.mapRow, tC=tree.mapCol;
        int elev=tree.elevation;
        Tile tile = map.getTile(Math.round(tR), Math.round(tC));
        float lightVal = (tile!=null) ? tile.getFinalLightLevel()/(float)MAX_LIGHT_LEVEL : 1.0f;

        float tBaseIsoX=(tC-tR)*(TILE_WIDTH/2.0f);
        float tBaseIsoY=(tC+tR)*(TILE_HEIGHT/2.0f)-(elev*TILE_THICKNESS);

        float tileLogicalZ = (tR + tC) * DEPTH_SORT_FACTOR + (elev * 0.005f);
        float treeWorldZ = tileLogicalZ + Z_OFFSET_SPRITE_TREE;

        float frameW=0,frameH=0,atlasU0=0,atlasV0=0,rendW,rendH,anchorYOff;
        float visualIsoOffsetX = 0, visualIsoOffsetY = 0;

        switch(tree.treeVisualType){
            case APPLE_TREE_FRUITING: // Assuming this is "tree1"
                frameW=90;frameH=130;atlasU0=0;atlasV0=0;
                rendW=TILE_WIDTH*1.0f;rendH=rendW*(frameH/frameW);
                anchorYOff=TILE_HEIGHT*0.15f;
                break;
            case PINE_TREE_SMALL: // Another example of "tree1" if you have multiple
                frameW=90;frameH=130;atlasU0=90;atlasV0=0;
                rendW=TILE_WIDTH*1.0f;rendH=rendW*(frameH/frameW);
                anchorYOff=TILE_HEIGHT*0.1f;
                break;
            // case PALM_TREE: // Replace PALM_TREE with your actual enum name for the palm
            //     frameW = 70; frameH = 100; // Example values, replace with your palm's atlas data
            //     atlasU0 = 190; atlasV0 = 0;  // Example UVs in your treeTexture atlas
            //     rendW = TILE_WIDTH * 1.0f;
            //     rendH = rendW * (frameH/frameW);
            //     anchorYOff = TILE_HEIGHT * 0.45f; // Palm trees often have a taller visual base or trunk bottom

            //     // --- Adjust these offsets by trial and error for PALM_TREE ---
            //     // If "one up in diagonal" means it appears visually shifted towards smaller screen Y and some X direction:
            //     // To shift it visually "down" (larger screen Y) and adjust X:
            //     // visualIsoOffsetY = TILE_HEIGHT * 0.5f; // Roughly one tile step down on Y screen axis
            //     // visualIsoOffsetX = -TILE_WIDTH * 0.25f; // Example: nudge slightly left on screen
            //     // System.out.println("Applying custom offset for PALM_TREE");
            //     break;
            default:
                // If you have other tree types that work fine, add their cases or handle them here.
                // If it's an unknown type, log an error and don't render.
                // System.err.println("Undefined or unhandled TreeVisualType in Renderer: " + tree.treeVisualType);
                return 0;
        }

        float tFinalIsoX = tBaseIsoX + visualIsoOffsetX;
        float tFinalIsoY = tBaseIsoY + visualIsoOffsetY;

        float texU0=atlasU0/treeTexture.getWidth(),texV0=atlasV0/treeTexture.getHeight();
        float texU1=(atlasU0+frameW)/treeTexture.getWidth(),texV1=(atlasV0+frameH)/treeTexture.getHeight();
        float hTW=rendW/2.0f; float yTop=tFinalIsoY-(rendH-anchorYOff),yBot=tFinalIsoY+anchorYOff;
        float xBL=tFinalIsoX-hTW,yBL=yBot; float xTL=tFinalIsoX-hTW,yTL=yTop;
        float xTR=tFinalIsoX+hTW,yTR=yTop; float xBR=tFinalIsoX+hTW,yBR=yBot;

        buffer.put(xTL).put(yTL).put(treeWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU0).put(texV0).put(lightVal);
        buffer.put(xBL).put(yBL).put(treeWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU0).put(texV1).put(lightVal);
        buffer.put(xTR).put(yTR).put(treeWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU1).put(texV0).put(lightVal);
        buffer.put(xTR).put(yTR).put(treeWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU1).put(texV0).put(lightVal);
        buffer.put(xBL).put(yBL).put(treeWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU0).put(texV1).put(lightVal);
        buffer.put(xBR).put(yBR).put(treeWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU1).put(texV1).put(lightVal);
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

        // --- MODIFIED CHUNK RENDERING LOGIC ---
        if (mapChunks != null && player != null && camera != null && CHUNK_SIZE_TILES > 0) { //
            int playerTileCol = player.getTileCol(); //
            int playerTileRow = player.getTileRow(); //
            int playerChunkX = playerTileCol / CHUNK_SIZE_TILES; //
            int playerChunkY = playerTileRow / CHUNK_SIZE_TILES; //

            for (int dy = -RENDER_DISTANCE_CHUNKS; dy <= RENDER_DISTANCE_CHUNKS; dy++) { //
                for (int dx = -RENDER_DISTANCE_CHUNKS; dx <= RENDER_DISTANCE_CHUNKS; dx++) { //
                    int currentChunkGridX = playerChunkX + dx;
                    int currentChunkGridY = playerChunkY + dy;

                    // Find and render this chunk if it exists and is visible
                    for (Chunk chunk : mapChunks) { //
                        if (chunk.chunkGridX == currentChunkGridX && chunk.chunkGridY == currentChunkGridY) {
                            if (camera.isChunkVisible(chunk.getBoundingBox())) { //
                                chunk.render(); //
                            }
                            break; // Found the chunk, move to next target dx, dy
                        }
                    }
                }
            }
        }
        // --- END OF MODIFIED CHUNK RENDERING LOGIC ---

        if (tileAtlasTexture != null) glBindTexture(GL_TEXTURE_2D, 0);

        collectWorldEntities(); // This method will also be updated
        defaultShader.setUniform("uHasTexture", 1); // For sprites
        defaultShader.setUniform("uTextureSampler", 0); // For sprites

        if (spriteVaoId != 0 && !worldEntities.isEmpty()) {
            glBindVertexArray(spriteVaoId);
            glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
            spriteVertexBuffer.clear();
            int verticesInBatch = 0;
            Texture currentSpriteTexture = null;

            for (Object entity : worldEntities) {
                int verticesAdded = 0;
                Texture textureForEntity = null;
                if (entity instanceof PlayerModel && playerTexture != null) {
                    verticesAdded = addPlayerVerticesToBuffer_WorldSpace((PlayerModel)entity, spriteVertexBuffer);
                    textureForEntity = playerTexture;
                } else if (entity instanceof TreeData && treeTexture != null) {
                    verticesAdded = addTreeVerticesToBuffer_WorldSpace((TreeData)entity, spriteVertexBuffer);
                    textureForEntity = treeTexture;
                }

                if (verticesAdded > 0 && textureForEntity != null) {
                    if (currentSpriteTexture == null) currentSpriteTexture = textureForEntity;
                    else if (currentSpriteTexture.getId() != textureForEntity.getId()) {
                        if (verticesInBatch > 0) {
                            spriteVertexBuffer.flip();
                            glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                            currentSpriteTexture.bind();
                            glDrawArrays(GL_TRIANGLES, 0, verticesInBatch);
                            spriteVertexBuffer.clear(); verticesInBatch = 0;
                        }
                        currentSpriteTexture = textureForEntity;
                    }
                    verticesInBatch += verticesAdded;
                    if (spriteVertexBuffer.position() >= spriteVertexBuffer.capacity() - (6 * FLOATS_PER_VERTEX_SPRITE_TEXTURED)) {
                        if (verticesInBatch > 0 && currentSpriteTexture != null) {
                            spriteVertexBuffer.flip();
                            glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                            currentSpriteTexture.bind();
                            glDrawArrays(GL_TRIANGLES, 0, verticesInBatch);
                            spriteVertexBuffer.clear(); verticesInBatch = 0;
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
            glBindTexture(GL_TEXTURE_2D, 0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }
        defaultShader.unbind();
    }
    public void renderDebugOverlay(float panelX, float panelY, float panelWidth, float panelHeight, List<String> lines) {
        if (uiFont == null || !uiFont.isInitialized()) return;
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", new Matrix4f().identity());

        float[] bgColor = {0.1f, 0.1f, 0.1f, 0.8f}; float z = -1.0f;
        glBindVertexArray(spriteVaoId); glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
        spriteVertexBuffer.clear();
        spriteVertexBuffer.put(panelX).put(panelY).put(z).put(bgColor[0]).put(bgColor[1]).put(bgColor[2]).put(bgColor[3]).put(0f).put(0f).put(1f);
        spriteVertexBuffer.put(panelX).put(panelY+panelHeight).put(z).put(bgColor[0]).put(bgColor[1]).put(bgColor[2]).put(bgColor[3]).put(0f).put(0f).put(1f);
        spriteVertexBuffer.put(panelX+panelWidth).put(panelY).put(z).put(bgColor[0]).put(bgColor[1]).put(bgColor[2]).put(bgColor[3]).put(0f).put(0f).put(1f);
        spriteVertexBuffer.put(panelX+panelWidth).put(panelY).put(z).put(bgColor[0]).put(bgColor[1]).put(bgColor[2]).put(bgColor[3]).put(0f).put(0f).put(1f);
        spriteVertexBuffer.put(panelX).put(panelY+panelHeight).put(z).put(bgColor[0]).put(bgColor[1]).put(bgColor[2]).put(bgColor[3]).put(0f).put(0f).put(1f);
        spriteVertexBuffer.put(panelX+panelWidth).put(panelY+panelHeight).put(z).put(bgColor[0]).put(bgColor[1]).put(bgColor[2]).put(bgColor[3]).put(0f).put(0f).put(1f);
        spriteVertexBuffer.flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
        defaultShader.setUniform("uHasTexture", 0); defaultShader.setUniform("uIsFont", 0);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindBuffer(GL_ARRAY_BUFFER, 0); glBindVertexArray(0);

        float textX = panelX + 5f, textY = panelY + 5f;
        if (uiFont.getAscent() > 0) textY += uiFont.getAscent();
        for (String line : lines) {
            uiFont.drawText(textX, textY, line, 0.9f, 0.9f, 0.9f);
            textY += 18f;
        }
        glEnable(GL_DEPTH_TEST);
    }
    public void cleanup() {
        if(playerTexture!=null)playerTexture.delete();
        if(treeTexture!=null)treeTexture.delete();
        if(tileAtlasTexture!=null)tileAtlasTexture.delete();
        if(uiFont!=null)uiFont.cleanup();
        if(defaultShader!=null)defaultShader.cleanup();
        if(mapChunks!=null){for(Chunk ch:mapChunks)ch.cleanup();mapChunks.clear();}
        if(spriteVaoId!=0){glDeleteVertexArrays(spriteVaoId);spriteVaoId=0;}
        if(spriteVboId!=0){glDeleteBuffers(spriteVboId);spriteVboId=0;}
        if(spriteVertexBuffer!=null){MemoryUtil.memFree(spriteVertexBuffer);spriteVertexBuffer=null;}
    }
}
