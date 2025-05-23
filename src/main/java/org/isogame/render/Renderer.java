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
// import java.util.Collections; // No longer strictly needed for opaque Z-buffered sort
// import java.util.Comparator;  // No longer strictly needed for opaque Z-buffered sort
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
    private int frameCount = 0;

    private Texture playerTexture;
    private Texture treeTexture;
    private Font uiFont;
    private Random tileDetailRandom;

    private List<Chunk> mapChunks;

    private Shader defaultShader;
    private Matrix4f projectionMatrix;
    // private Matrix4f modelViewMatrixForSprites; // No longer needed if sprites use camera.getViewMatrix()

    private int spriteVaoId = 0;
    private int spriteVboId = 0;
    private FloatBuffer spriteVertexBuffer;

    public static final int FLOATS_PER_VERTEX_SPRITE_TEXTURED = 9; // x,y,z, r,g,b,a, u,v
    public static final int FLOATS_PER_VERTEX_TERRAIN_TEXTURED = 9; // x,y,z r,g,b,a, u,v

    // Factor to separate layers in Z based on map row/col
    public static final float DEPTH_SORT_FACTOR = 0.1f; // Adjust as needed

    private Texture tileAtlasTexture;

    // Tile Atlas UV Coordinates
    private static final float ATLAS_TOTAL_WIDTH = 128.0f;
    private static final float ATLAS_TOTAL_HEIGHT = 128.0f;
    private static final float SUB_TEX_WIDTH = 64.0f;
    private static final float SUB_TEX_HEIGHT = 64.0f;

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

    private static final float DEFAULT_SIDE_U0 = (1 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;
    private static final float DEFAULT_SIDE_V0 = (0 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT;
    private static final float DEFAULT_SIDE_U1 = (2 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;
    private static final float DEFAULT_SIDE_V1 = (1 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT;

    private static final float SNOW_ATLAS_U0 = (0 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;
    private static final float SNOW_ATLAS_V0 = (1 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT;
    private static final float SNOW_ATLAS_U1 = (1 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH;
    private static final float SNOW_ATLAS_V1 = (2 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT;


    private static final float SNOW_SIDE_ATLAS_U0 = ROCK_ATLAS_U0;
    private static final float SNOW_SIDE_ATLAS_V0 = ROCK_ATLAS_V0;
    private static final float SNOW_SIDE_ATLAS_U1 = ROCK_ATLAS_U1;
    private static final float SNOW_SIDE_ATLAS_V1 = ROCK_ATLAS_V1;


    private static final float SIDE_TEXTURE_DENSITY_FACTOR = 1.0f;
    private static final float DUMMY_U = 0.0f;
    private static final float DUMMY_V = 0.0f;

    private static final float[] SELECTED_TINT = {1.0f, 0.8f, 0.0f, 0.8f};
    private static final float[] WATER_TOP_COLOR = {0.05f, 0.25f, 0.5f, 0.85f};
    private static final float[] SAND_TOP_COLOR = {0.82f,0.7f,0.55f,1f};
    private static final float[] GRASS_TOP_COLOR = {0.20f,0.45f,0.10f,1f};
    private static final float[] ROCK_TOP_COLOR = {0.45f,0.45f,0.45f,1f};
    private static final float[] SNOW_TOP_COLOR = {0.95f,0.95f,1.0f,1f};
    private static final float[] DEFAULT_TOP_COLOR = {1f,0f,1f,1f};
    private static final float[] WHITE_TINT = {1.0f, 1.0f, 1.0f, 1.0f};


    public static class TreeData { // Also used by Chunk
        Tile.TreeVisualType treeVisualType;
        float mapCol, mapRow;
        int elevation;
        // screenAnchorX, screenAnchorY no longer needed here if trees are world-space

        public TreeData(Tile.TreeVisualType type, float tc, float tr, int te) {
            this.treeVisualType = type;
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

        System.out.println("Renderer: Initializing...");
        loadAssets();
        initShaders();
        initRenderObjects();
        uploadTileMapGeometry();
        System.out.println("Renderer: Initialization complete.");
    }

    private void loadAssets() {
        System.out.println("Renderer: Starting asset loading...");
        String playerTexturePath = "/org/isogame/render/textures/lpc_character.png";
        this.playerTexture = Texture.loadTexture(playerTexturePath);
        if (this.playerTexture == null) {
            System.err.println("Renderer CRITICAL: Player texture FAILED to load: " + playerTexturePath);
        }

        String treeTexturePath = "/org/isogame/render/textures/fruit-trees.png";
        this.treeTexture = Texture.loadTexture(treeTexturePath);
        if (this.treeTexture == null) {
            System.err.println("Renderer CRITICAL: Tree texture FAILED to load: " + treeTexturePath);
        }

        String tileAtlasPath = "/org/isogame/render/textures/textu.png";
        this.tileAtlasTexture = Texture.loadTexture(tileAtlasPath);
        if (this.tileAtlasTexture == null) {
            System.err.println("Renderer CRITICAL: Tile Atlas FAILED to load: " + tileAtlasPath);
        }

        String fontPath = "/org/isogame/render/fonts/PressStart2P-Regular.ttf";
        try {
            this.uiFont = new Font(fontPath, 16f, this);
        } catch (IOException | RuntimeException e) {
            System.err.println("Renderer CRITICAL: Failed to load UI font: " + fontPath + " - " + e.getMessage());
            this.uiFont = null;
        }
        System.out.println("Renderer: Asset loading finished.");
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
        mapChunks = new ArrayList<>();
        if (map != null && Constants.CHUNK_SIZE_TILES > 0) {
            int numChunksX = (int) Math.ceil((double) map.getWidth() / Constants.CHUNK_SIZE_TILES);
            int numChunksY = (int) Math.ceil((double) map.getHeight() / Constants.CHUNK_SIZE_TILES);
            for (int cy = 0; cy < numChunksY; cy++) {
                for (int cx = 0; cx < numChunksX; cx++) {
                    Chunk chunk = new Chunk(cx, cy, Constants.CHUNK_SIZE_TILES);
                    chunk.setupGLResources();
                    mapChunks.add(chunk);
                }
            }
        } else {
            System.err.println("Renderer WARNING: Map or CHUNK_SIZE_TILES invalid, cannot initialize mapChunks.");
        }


        spriteVaoId = glGenVertexArrays();
        glBindVertexArray(spriteVaoId);
        spriteVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);

        if (spriteVertexBuffer != null) MemoryUtil.memFree(spriteVertexBuffer);
        spriteVertexBuffer = MemoryUtil.memAllocFloat(100 * 6 * FLOATS_PER_VERTEX_SPRITE_TEXTURED);
        glBufferData(GL_ARRAY_BUFFER, (long) spriteVertexBuffer.capacity() * Float.BYTES, GL_DYNAMIC_DRAW);

        int spriteStride = FLOATS_PER_VERTEX_SPRITE_TEXTURED * Float.BYTES;

        glVertexAttribPointer(0, 3, GL_FLOAT, false, spriteStride, 0L);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 4, GL_FLOAT, false, spriteStride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glVertexAttribPointer(2, 2, GL_FLOAT, false, spriteStride, (3 + 4) * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        System.out.println("Renderer: Sprite VAO/VBO initialized for world-space sprites.");
    }

    public void uploadTileMapGeometry() {
        if (mapChunks == null || mapChunks.isEmpty() || map == null || camera == null) {
            System.err.println("Renderer.uploadTileMapGeometry: Pre-conditions not met.");
            return;
        }
        System.out.println("Renderer: Uploading full tile map geometry for " + mapChunks.size() + " chunks.");
        for (Chunk chunk : mapChunks) {
            chunk.uploadGeometry(map, inputHandler, this, camera);
        }
        System.out.println("Renderer: Full tile map geometry upload complete.");
    }


    public void updateChunkContainingTile(int tileRow, int tileCol) {
        if (map == null || mapChunks == null || mapChunks.isEmpty() || Constants.CHUNK_SIZE_TILES <= 0 || camera == null) {
            System.err.println("Renderer.updateChunkContainingTile: Pre-conditions not met.");
            return;
        }
        final int chunkGridX = tileCol / Constants.CHUNK_SIZE_TILES;
        final int chunkGridY = tileRow / Constants.CHUNK_SIZE_TILES;
        mapChunks.stream()
                .filter(chunk -> chunk.chunkGridX == chunkGridX && chunk.chunkGridY == chunkGridY)
                .findFirst()
                .ifPresent(chunk -> {
                    System.out.println("Renderer: Updating geometry for chunk (" + chunkGridX + "," + chunkGridY + ") containing tile (" + tileRow + "," + tileCol + ")");
                    chunk.uploadGeometry(this.map, this.inputHandler, this, camera);
                });
    }


    public void onResize(int framebufferWidth, int framebufferHeight) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            System.err.println("Renderer.onResize: Invalid dimensions " + framebufferWidth + "x" + framebufferHeight);
            return;
        }
        glViewport(0, 0, framebufferWidth, framebufferHeight);
        projectionMatrix.identity().ortho(0, framebufferWidth, framebufferHeight, 0, -1000.0f, 1000.0f);
        if (camera != null) {
            camera.setProjectionMatrixForCulling(projectionMatrix);
            camera.forceUpdateViewMatrix();
        }
        System.out.println("Renderer.onResize: Viewport and projection updated to " + framebufferWidth + "x" + framebufferHeight);
    }

    private Tile.TileType getMaterialTypeForElevationSlice(int elevationLevel) {
        if (elevationLevel < NIVEL_MAR) return Tile.TileType.WATER;
        if (elevationLevel < NIVEL_ARENA) return Tile.TileType.SAND;
        if (elevationLevel < NIVEL_ROCA) return Tile.TileType.GRASS;
        if (elevationLevel < NIVEL_NIEVE) return Tile.TileType.ROCK;
        return Tile.TileType.SNOW;
    }

    private float[] determineTopSurfaceColor(Tile.TileType surfaceType, boolean isSelected) {
        if (isSelected) return SELECTED_TINT;
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

        // Calculate consistent worldZ for all parts of this tile
        // Positive Z for further objects, assuming GL_LEQUAL depth test
        final float currentTileWorldZ = (tileR + tileC) * DEPTH_SORT_FACTOR;


        final float diamondTopOffsetY = -tileHalfHeight;
        final float diamondLeftOffsetX = -tileHalfWidth;
        final float diamondSideOffsetY = 0;
        final float diamondRightOffsetX = tileHalfWidth;
        final float diamondBottomOffsetY = tileHalfHeight;

        float[] topSurfaceColor = determineTopSurfaceColor(currentTileTopSurfaceType, isSelected);
        float[] sideTintToUse = isSelected ? topSurfaceColor : WHITE_TINT;

        int verticesAdded = 0;

        if (currentTileTopSurfaceType != Tile.TileType.WATER) {
            verticesAdded += addPedestalSidesToBuffer(
                    buffer, tileGridPlaneCenterX, tileGridPlaneCenterY, currentTileWorldZ,
                    diamondLeftOffsetX, diamondSideOffsetY, diamondRightOffsetX, diamondBottomOffsetY,
                    sideTintToUse);
        }

        float currentTileTopSurfaceActualY = tileGridPlaneCenterY - (currentTileElevation * elevationSliceHeight);
        if (currentTileTopSurfaceType == Tile.TileType.WATER) {
            currentTileTopSurfaceActualY = tileGridPlaneCenterY - (NIVEL_MAR * elevationSliceHeight);
        }

        verticesAdded += addTopSurfaceToBuffer(
                buffer, currentTileTopSurfaceType, isSelected,
                tileGridPlaneCenterX, currentTileTopSurfaceActualY, currentTileWorldZ,
                diamondLeftOffsetX, diamondSideOffsetY, diamondRightOffsetX, diamondTopOffsetY, diamondBottomOffsetY,
                topSurfaceColor,
                WHITE_TINT);

        if (currentTileElevation > 0 && currentTileTopSurfaceType != Tile.TileType.WATER) {
            verticesAdded += addStratifiedElevatedSidesToBuffer(
                    buffer, currentTileElevation,
                    tileGridPlaneCenterX, tileGridPlaneCenterY, currentTileWorldZ,
                    diamondLeftOffsetX, diamondSideOffsetY, diamondRightOffsetX, diamondBottomOffsetY,
                    elevationSliceHeight,
                    sideTintToUse);
        }
        updateChunkBounds(
                chunkBoundsMinMax, tileGridPlaneCenterX, tileGridPlaneCenterY,
                currentTileElevation, elevationSliceHeight,
                diamondLeftOffsetX, diamondRightOffsetX, diamondTopOffsetY, diamondBottomOffsetY
        );
        return verticesAdded;
    }

    private int addPedestalSidesToBuffer(FloatBuffer buffer,
                                         float tileCenterX, float gridPlaneY, float worldZ,
                                         float diamondLeftOffsetX, float diamondSideOffsetY,
                                         float diamondRightOffsetX, float diamondBottomOffsetY,
                                         float[] tint) {
        int verticesCount = 0;
        float pedestalTopSurfaceY = gridPlaneY;
        float pedestalBottomSurfaceY = gridPlaneY + BASE_THICKNESS;

        float pTopLx = tileCenterX + diamondLeftOffsetX;  float pTopLy = pedestalTopSurfaceY + diamondSideOffsetY;
        float pTopRx = tileCenterX + diamondRightOffsetX; float pTopRy = pedestalTopSurfaceY + diamondSideOffsetY;
        float pTopBx = tileCenterX;                       float pTopBy = pedestalTopSurfaceY + diamondBottomOffsetY;

        float pBotLx = tileCenterX + diamondLeftOffsetX;  float pBotLy = pedestalBottomSurfaceY + diamondSideOffsetY;
        float pBotRx = tileCenterX + diamondRightOffsetX; float pBotRy = pedestalBottomSurfaceY + diamondSideOffsetY;
        float pBotBx = tileCenterX;                       float pBotBy = pedestalBottomSurfaceY + diamondBottomOffsetY;

        float u0_tex = DEFAULT_SIDE_U0;
        float v0_tex = DEFAULT_SIDE_V0;
        float u1_tex = DEFAULT_SIDE_U1;
        float vSpanAtlas = DEFAULT_SIDE_V1 - v0_tex;

        float vRepeats = (BASE_THICKNESS / (float)TILE_HEIGHT) * SIDE_TEXTURE_DENSITY_FACTOR;
        float vBottomTextureCoordinate = v0_tex + vSpanAtlas * vRepeats;

        buffer.put(pTopLx).put(pTopLy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0_tex).put(v0_tex);
        buffer.put(pBotLx).put(pBotLy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0_tex).put(vBottomTextureCoordinate);
        buffer.put(pTopBx).put(pTopBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1_tex).put(v0_tex);
        verticesCount += 3;

        buffer.put(pTopBx).put(pTopBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1_tex).put(v0_tex);
        buffer.put(pBotLx).put(pBotLy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0_tex).put(vBottomTextureCoordinate);
        buffer.put(pBotBx).put(pBotBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1_tex).put(vBottomTextureCoordinate);
        verticesCount += 3;

        buffer.put(pTopBx).put(pTopBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0_tex).put(v0_tex);
        buffer.put(pBotBx).put(pBotBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0_tex).put(vBottomTextureCoordinate);
        buffer.put(pTopRx).put(pTopRy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1_tex).put(v0_tex);
        verticesCount += 3;

        buffer.put(pTopRx).put(pTopRy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1_tex).put(v0_tex);
        buffer.put(pBotBx).put(pBotBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u0_tex).put(vBottomTextureCoordinate);
        buffer.put(pBotRx).put(pBotRy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(u1_tex).put(vBottomTextureCoordinate);
        verticesCount += 3;

        return verticesCount;
    }

    private int addTopSurfaceToBuffer(FloatBuffer buffer, Tile.TileType topSurfaceType, boolean isSelected,
                                      float topFaceCenterX, float topFaceCenterY, float worldZ,
                                      float dLeftX, float dSideY, float dRightX, float dTopY, float dBottomY,
                                      float[] actualTopColor, float[] whiteTintIfTexturedAndNotSelected) {
        int verticesCount = 0;
        float topLx = topFaceCenterX + dLeftX;   float topLy = topFaceCenterY + dSideY;
        float topRx = topFaceCenterX + dRightX;  float topRy = topFaceCenterY + dSideY;
        float topTx = topFaceCenterX;            float topTy = topFaceCenterY + dTopY;
        float topBx = topFaceCenterX;            float topBy = topFaceCenterY + dBottomY;

        float[] colorToUse;
        boolean textureThisTopSurface = false;
        float u0_tex=DUMMY_U, v0_tex=DUMMY_V, u1_tex=DUMMY_U, v1_atlas_tex=DUMMY_V;

        if (topSurfaceType == Tile.TileType.WATER) {
            colorToUse = actualTopColor;
        } else {
            switch (topSurfaceType) {
                case GRASS: u0_tex=GRASS_ATLAS_U0;v0_tex=GRASS_ATLAS_V0;u1_tex=GRASS_ATLAS_U1;v1_atlas_tex=GRASS_ATLAS_V1; textureThisTopSurface=true; break;
                case SAND:  u0_tex=SAND_ATLAS_U0; v0_tex=SAND_ATLAS_V0; u1_tex=SAND_ATLAS_U1; v1_atlas_tex=SAND_ATLAS_V1; textureThisTopSurface=true; break;
                case ROCK:  u0_tex=ROCK_ATLAS_U0; v0_tex=ROCK_ATLAS_V0; u1_tex=ROCK_ATLAS_U1; v1_atlas_tex=ROCK_ATLAS_V1; textureThisTopSurface=true; break;
                case SNOW:  u0_tex=SNOW_ATLAS_U0; v0_tex=SNOW_ATLAS_V0; u1_tex=SNOW_ATLAS_U1; v1_atlas_tex=SNOW_ATLAS_V1; textureThisTopSurface=true; break;
            }

            if (isSelected) {
                colorToUse = actualTopColor;
            } else if (textureThisTopSurface) {
                colorToUse = whiteTintIfTexturedAndNotSelected;
            } else {
                colorToUse = actualTopColor;
            }
        }

        if (textureThisTopSurface) {
            float midU = (u0_tex + u1_tex) / 2f;
            float midV = (v0_tex + v1_atlas_tex) / 2f;

            buffer.put(topTx).put(topTy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(midU).put(v0_tex);
            buffer.put(topLx).put(topLy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(u0_tex).put(midV);
            buffer.put(topBx).put(topBy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(midU).put(v1_atlas_tex);
            verticesCount += 3;

            buffer.put(topTx).put(topTy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(midU).put(v0_tex);
            buffer.put(topBx).put(topBy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(midU).put(v1_atlas_tex);
            buffer.put(topRx).put(topRy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(u1_tex).put(midV);
            verticesCount += 3;
        } else {
            buffer.put(topTx).put(topTy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(DUMMY_U).put(DUMMY_V);
            buffer.put(topLx).put(topLy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(DUMMY_U).put(DUMMY_V);
            buffer.put(topBx).put(topBy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(DUMMY_U).put(DUMMY_V);
            verticesCount += 3;
            buffer.put(topTx).put(topTy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(DUMMY_U).put(DUMMY_V);
            buffer.put(topBx).put(topBy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(DUMMY_U).put(DUMMY_V);
            buffer.put(topRx).put(topRy).put(worldZ).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(DUMMY_U).put(DUMMY_V);
            verticesCount += 3;
        }
        return verticesCount;
    }

    private int addStratifiedElevatedSidesToBuffer(FloatBuffer buffer, int tileTotalElevation,
                                                   float tileGridPlaneCenterX, float tileGridPlaneCenterY, float worldZ,
                                                   float dLeftX, float dSideY, float dRightX, float dBottomY,
                                                   float elevationSliceHeight, float[] tint) {
        int verticesCount = 0;

        for (int currentElevationStep = tileTotalElevation; currentElevationStep >= 1; currentElevationStep--) {
            int underlyingLayerElevation = currentElevationStep - 1;
            Tile.TileType sideMaterialType = getMaterialTypeForElevationSlice(underlyingLayerElevation);

            float localSideU0, localSideV0, localSideU1, localSideV1_atlas, localSideTextureVSpanInAtlas;

            switch (sideMaterialType) {
                case GRASS:
                    localSideU0=DEFAULT_SIDE_U0; localSideV0=DEFAULT_SIDE_V0; localSideU1=DEFAULT_SIDE_U1; localSideV1_atlas=DEFAULT_SIDE_V1;
                    break;
                case SAND:
                    localSideU0=SAND_ATLAS_U0; localSideV0=SAND_ATLAS_V0; localSideU1=SAND_ATLAS_U1; localSideV1_atlas=SAND_ATLAS_V1;
                    break;
                case ROCK:
                    localSideU0=ROCK_ATLAS_U0; localSideV0=ROCK_ATLAS_V0; localSideU1=ROCK_ATLAS_U1; localSideV1_atlas=ROCK_ATLAS_V1;
                    break;
                case SNOW:
                    localSideU0=SNOW_SIDE_ATLAS_U0; localSideV0=SNOW_SIDE_ATLAS_V0; localSideU1=SNOW_SIDE_ATLAS_U1; localSideV1_atlas=SNOW_SIDE_ATLAS_V1;
                    break;
                default:
                    localSideU0=DEFAULT_SIDE_U0; localSideV0=DEFAULT_SIDE_V0; localSideU1=DEFAULT_SIDE_U1; localSideV1_atlas=DEFAULT_SIDE_V1;
                    break;
            }
            localSideTextureVSpanInAtlas = localSideV1_atlas - localSideV0;


            float vTopTextureCoordinate = localSideV0;
            float vBottomTextureCoordinate = localSideV0 + localSideTextureVSpanInAtlas * SIDE_TEXTURE_DENSITY_FACTOR;

            float sliceTopFaceCenterY = tileGridPlaneCenterY - (currentElevationStep * elevationSliceHeight);
            float sliceBottomFaceCenterY = tileGridPlaneCenterY - ((currentElevationStep - 1) * elevationSliceHeight);
            float sliceTopLx = tileGridPlaneCenterX + dLeftX;  float sliceTopLy = sliceTopFaceCenterY + dSideY;
            float sliceTopRx = tileGridPlaneCenterX + dRightX; float sliceTopRy = sliceTopFaceCenterY + dSideY;
            float sliceTopBx = tileGridPlaneCenterX;           float sliceTopBy = sliceTopFaceCenterY + dBottomY;
            float sliceBotLx = tileGridPlaneCenterX + dLeftX;  float sliceBotLy = sliceBottomFaceCenterY + dSideY;
            float sliceBotRx = tileGridPlaneCenterX + dRightX; float sliceBotRy = sliceBottomFaceCenterY + dSideY;
            float sliceBotBx = tileGridPlaneCenterX;           float sliceBotBy = sliceBottomFaceCenterY + dBottomY;

            buffer.put(sliceTopLx).put(sliceTopLy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(localSideU0).put(vTopTextureCoordinate);
            buffer.put(sliceBotLx).put(sliceBotLy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(localSideU0).put(vBottomTextureCoordinate);
            buffer.put(sliceTopBx).put(sliceTopBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(localSideU1).put(vTopTextureCoordinate);
            verticesCount += 3;

            buffer.put(sliceTopBx).put(sliceTopBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(localSideU1).put(vTopTextureCoordinate);
            buffer.put(sliceBotLx).put(sliceBotLy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(localSideU0).put(vBottomTextureCoordinate);
            buffer.put(sliceBotBx).put(sliceBotBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(localSideU1).put(vBottomTextureCoordinate);
            verticesCount += 3;

            buffer.put(sliceTopBx).put(sliceTopBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(localSideU0).put(vTopTextureCoordinate);
            buffer.put(sliceBotBx).put(sliceBotBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(localSideU0).put(vBottomTextureCoordinate);
            buffer.put(sliceTopRx).put(sliceTopRy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(localSideU1).put(vTopTextureCoordinate);
            verticesCount += 3;

            buffer.put(sliceTopRx).put(sliceTopRy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(localSideU1).put(vTopTextureCoordinate);
            buffer.put(sliceBotBx).put(sliceBotBy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(localSideU0).put(vBottomTextureCoordinate);
            buffer.put(sliceBotRx).put(sliceBotRy).put(worldZ).put(tint[0]).put(tint[1]).put(tint[2]).put(tint[3]).put(localSideU1).put(vBottomTextureCoordinate);
            verticesCount += 3;
        }
        return verticesCount;
    }

    private void updateChunkBounds(float[] chunkBoundsMinMax,
                                   float tileGridPlaneCenterX, float tileGridPlaneCenterY,
                                   int currentTileElevation, float elevationSliceHeight,
                                   float dLeftX, float dRightX, float dTopY, float dBottomY) {
        float minX = tileGridPlaneCenterX + dLeftX;
        float maxX = tileGridPlaneCenterX + dRightX;
        float minY = tileGridPlaneCenterY - (currentTileElevation * elevationSliceHeight) + dTopY;
        float maxY = tileGridPlaneCenterY + BASE_THICKNESS + dBottomY;

        chunkBoundsMinMax[0] = Math.min(chunkBoundsMinMax[0], minX);
        chunkBoundsMinMax[1] = Math.min(chunkBoundsMinMax[1], minY);
        chunkBoundsMinMax[2] = Math.max(chunkBoundsMinMax[2], maxX);
        chunkBoundsMinMax[3] = Math.max(chunkBoundsMinMax[3], maxY);
    }

    public int addGrassVerticesForTile_WorldSpace_ForChunk(int r,int c,Tile t,FloatBuffer b,float[] bounds){return 0;}


    private void collectWorldEntities() {
        worldEntities.clear();
        if (player != null) {
            worldEntities.add(player);
        }
        if (mapChunks != null && camera != null) {
            for (Chunk chunk : mapChunks) {
                if (camera.isChunkVisible(chunk.getBoundingBox())) {
                    worldEntities.addAll(chunk.getTreesInChunk());
                }
            }
        }
    }

    private int addPlayerVerticesToBuffer_WorldSpace(PlayerModel p, FloatBuffer buffer) {
        if (playerTexture == null || playerTexture.getId() == 0 || camera == null || map == null) return 0;

        float playerMapR = p.getMapRow();
        float playerMapC = p.getMapCol();
        Tile currentTile = map.getTile(p.getTileRow(), p.getTileCol());
        int playerElevationOnTile = (currentTile != null) ? currentTile.getElevation() : 0;

        float playerBaseIsoX = (playerMapC - playerMapR) * (TILE_WIDTH / 2.0f);
        float playerBaseIsoY = (playerMapC + playerMapR) * (TILE_HEIGHT / 2.0f) - (playerElevationOnTile * TILE_THICKNESS);
        // Corrected Z: positive for further objects
        // Corrected Z: positive for further objects, ensure player is slightly in front of its tile
        float tileZ = (playerMapR + playerMapC) * DEPTH_SORT_FACTOR; // Z of the tile the player is on
            float playerWorldZ = tileZ - -0.05f; // Apply a small negative offset to bring player forward

        if (p.isLevitating()) {
            playerBaseIsoY -= (Math.sin(p.getLevitateTimer()) * 8);
            // If you want levitating players to be even further in front, you could adjust playerWorldZ more here:
            // playerWorldZ = tileZ - 0.05f; // Example: larger offset when levitating
        }

        float halfPlayerWorldWidth = PLAYER_WORLD_RENDER_WIDTH / 2.0f;

        float xBL = playerBaseIsoX - halfPlayerWorldWidth;         float yBL = playerBaseIsoY;
        float xTL = playerBaseIsoX - halfPlayerWorldWidth;         float yTL = playerBaseIsoY - PLAYER_WORLD_RENDER_HEIGHT;
        float xTR = playerBaseIsoX + halfPlayerWorldWidth;         float yTR = playerBaseIsoY - PLAYER_WORLD_RENDER_HEIGHT;
        float xBR = playerBaseIsoX + halfPlayerWorldWidth;         float yBR = playerBaseIsoY;

        int animFrameColumn = p.getVisualFrameIndex();
        int animFrameRow = p.getAnimationRow();
        if (playerTexture.getWidth() == 0 || playerTexture.getHeight() == 0) return 0;

        float texU0 = (animFrameColumn * (float)PlayerModel.FRAME_WIDTH) / playerTexture.getWidth();
        float texV0 = (animFrameRow * (float)PlayerModel.FRAME_HEIGHT) / playerTexture.getHeight();
        float texU1 = ((animFrameColumn + 1) * (float)PlayerModel.FRAME_WIDTH) / playerTexture.getWidth();
        float texV1 = ((animFrameRow + 1) * (float)PlayerModel.FRAME_HEIGHT) / playerTexture.getHeight();

        buffer.put(xTL).put(yTL).put(playerWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU0).put(texV0);
        buffer.put(xBL).put(yBL).put(playerWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU0).put(texV1);
        buffer.put(xTR).put(yTR).put(playerWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU1).put(texV0);

        buffer.put(xTR).put(yTR).put(playerWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU1).put(texV0);
        buffer.put(xBL).put(yBL).put(playerWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU0).put(texV1);
        buffer.put(xBR).put(yBR).put(playerWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU1).put(texV1);
        return 6;
    }

    private int addTreeVerticesToBuffer_WorldSpace(TreeData tree, FloatBuffer buffer) {
        if (treeTexture == null || treeTexture.getId() == 0 || tree.treeVisualType == Tile.TreeVisualType.NONE || camera == null) {
            return 0;
        }

        float treeMapR = tree.mapRow;
        float treeMapC = tree.mapCol;
        int treeElevation = tree.elevation;

        float treeBaseIsoX = (treeMapC - treeMapR) * (TILE_WIDTH / 2.0f);
        float treeBaseIsoY = (treeMapC + treeMapR) * (TILE_HEIGHT / 2.0f) - (treeElevation * TILE_THICKNESS);
        // Corrected Z: positive for further objects
        float treeWorldZ = (treeMapR + treeMapC) * DEPTH_SORT_FACTOR;

        float frameW = 0, frameH = 0, atlasU0 = 0, atlasV0 = 0;
        float treeRenderWidth = TILE_WIDTH * 1.2f;
        float treeRenderHeight = TILE_HEIGHT * 2.8f;
        float treeAnchorOffsetFromBottomY = TILE_HEIGHT * 0.2f;

        switch (tree.treeVisualType) {
            case APPLE_TREE_FRUITING:
                frameW = 90; frameH = 130; atlasU0 = 0; atlasV0 = 0;
                treeRenderWidth = TILE_WIDTH * 1.2f; // Current width: 64 * 1.2 = 76.8
                // Calculate height based on width and source aspect ratio
                treeRenderHeight = treeRenderWidth * (frameH / frameW); // New: 76.8 * (130/90) = 110.93
                treeAnchorOffsetFromBottomY = TILE_HEIGHT * 0.15f;
                break;
            case PINE_TREE_SMALL:
                frameW = 90; frameH = 180; atlasU0 = 90; atlasV0 = 0;
                treeRenderWidth = TILE_WIDTH * 1.0f; // Current width: 64 * 1.0 = 64.0
                // Calculate height based on width and source aspect ratio
                treeRenderHeight = treeRenderWidth * (frameH / frameW); // New: 64.0 * (180/90) = 128.0
                treeAnchorOffsetFromBottomY = TILE_HEIGHT * 0.1f;
                break;
            default: return 0;
        }

        if (treeTexture.getWidth() == 0 || treeTexture.getHeight() == 0) return 0;

        float texU0 = atlasU0 / treeTexture.getWidth();
        float texV0 = atlasV0 / treeTexture.getHeight();
        float texU1 = (atlasU0 + frameW) / treeTexture.getWidth();
        float texV1 = (atlasV0 + frameH) / treeTexture.getHeight();

        float halfTreeWorldWidth = treeRenderWidth / 2.0f;

        float yWorldTop = treeBaseIsoY - (treeRenderHeight - treeAnchorOffsetFromBottomY);
        float yWorldBottom = treeBaseIsoY + treeAnchorOffsetFromBottomY;

        float xBL = treeBaseIsoX - halfTreeWorldWidth;      float yBL = yWorldBottom;
        float xTL = treeBaseIsoX - halfTreeWorldWidth;      float yTL = yWorldTop;
        float xTR = treeBaseIsoX + halfTreeWorldWidth;      float yTR = yWorldTop;
        float xBR = treeBaseIsoX + halfTreeWorldWidth;      float yBR = yWorldBottom;

        buffer.put(xTL).put(yTL).put(treeWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU0).put(texV0);
        buffer.put(xBL).put(yBL).put(treeWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU0).put(texV1);
        buffer.put(xTR).put(yTR).put(treeWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU1).put(texV0);

        buffer.put(xTR).put(yTR).put(treeWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU1).put(texV0);
        buffer.put(xBL).put(yBL).put(treeWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU0).put(texV1);
        buffer.put(xBR).put(yBR).put(treeWorldZ).put(WHITE_TINT[0]).put(WHITE_TINT[1]).put(WHITE_TINT[2]).put(WHITE_TINT[3]).put(texU1).put(texV1);
        return 6;
    }


    public void render() {
        frameCount++;
        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);

        // --- 1. Render Map Chunks ---
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

        if (mapChunks != null) {
            for (Chunk chunk : mapChunks) {
                if (camera.isChunkVisible(chunk.getBoundingBox())) {
                    chunk.render();
                }
            }
        }
        glBindVertexArray(0);
        if (tileAtlasTexture != null && tileAtlasTexture.getId() != 0) {
            glBindTexture(GL_TEXTURE_2D, 0);
        }

        // --- 2. Render Sprites (Player, Trees in World Space) ---
        collectWorldEntities();

        defaultShader.setUniform("uModelViewMatrix", camera.getViewMatrix());
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uHasTexture", 1);
        defaultShader.setUniform("uTextureSampler", 0);

        if (spriteVaoId != 0 && spriteVboId != 0 && !worldEntities.isEmpty()) {
            glBindVertexArray(spriteVaoId);
            glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);

            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            spriteVertexBuffer.clear();
            int verticesInBatch = 0;
            Texture currentTexture = null;

            for (Object entity : worldEntities) {
                int verticesForThisSprite = 0;
                Texture textureForThisSprite = null;

                if (entity instanceof PlayerModel) {
                    PlayerModel p = (PlayerModel) entity;
                    if (playerTexture != null && playerTexture.getId() != 0) {
                        if (spriteVertexBuffer.remaining() < FLOATS_PER_VERTEX_SPRITE_TEXTURED * 6) {
                            if (verticesInBatch > 0) {
                                spriteVertexBuffer.flip();
                                if(currentTexture != null) currentTexture.bind();
                                glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                                glDrawArrays(GL_TRIANGLES, 0, verticesInBatch);
                                spriteVertexBuffer.clear();
                                verticesInBatch = 0;
                            }
                        }
                        verticesForThisSprite = addPlayerVerticesToBuffer_WorldSpace(p, spriteVertexBuffer);
                        textureForThisSprite = playerTexture;
                    }
                } else if (entity instanceof TreeData) {
                    TreeData td = (TreeData) entity;
                    if (treeTexture != null && treeTexture.getId() != 0) {
                        if (spriteVertexBuffer.remaining() < FLOATS_PER_VERTEX_SPRITE_TEXTURED * 6) {
                            if (verticesInBatch > 0) {
                                spriteVertexBuffer.flip();
                                if(currentTexture != null) currentTexture.bind();
                                glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                                glDrawArrays(GL_TRIANGLES, 0, verticesInBatch);
                                spriteVertexBuffer.clear();
                                verticesInBatch = 0;
                            }
                        }
                        verticesForThisSprite = addTreeVerticesToBuffer_WorldSpace(td, spriteVertexBuffer);
                        textureForThisSprite = treeTexture;
                    }
                }

                if (verticesForThisSprite > 0) {
                    if (currentTexture == null) {
                        currentTexture = textureForThisSprite;
                    } else if (currentTexture.getId() != textureForThisSprite.getId()) {
                        if (verticesInBatch > 0) {
                            spriteVertexBuffer.flip();
                            currentTexture.bind();
                            glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                            glDrawArrays(GL_TRIANGLES, 0, verticesInBatch);
                            spriteVertexBuffer.clear();
                            verticesInBatch = 0;
                        }
                        currentTexture = textureForThisSprite;
                    }
                    verticesInBatch += verticesForThisSprite;
                }
            }
            if (verticesInBatch > 0 && currentTexture != null) {
                spriteVertexBuffer.flip();
                currentTexture.bind();
                glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer);
                glDrawArrays(GL_TRIANGLES, 0, verticesInBatch);
            }

            glBindTexture(GL_TEXTURE_2D, 0);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }


        defaultShader.unbind();
    }


    public void renderDebugOverlay(float panelX, float panelY, float panelWidth, float panelHeight, List<String> lines) {
        if (uiFont == null || !uiFont.isInitialized()) { //
            System.err.println("Renderer.renderDebugOverlay: uiFont not initialized. Cannot render debug text.");
            return;
        }

        // --- Setup OpenGL State for UI ---
        glEnable(GL_BLEND); //
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); //
        glDisable(GL_DEPTH_TEST); // UI should always be on top

        defaultShader.bind(); //
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix); //

        // For 2D UI, ModelView is usually identity or a simple 2D translation if needed
        Matrix4f uiModelViewMatrix = new Matrix4f().identity();
        // If panelX, panelY are already top-left screen coords, no further translation needed in matrix
        // If you want to treat panelX, panelY as offsets from an origin, you might translate uiModelViewMatrix here.
        defaultShader.setUniform("uModelViewMatrix", uiModelViewMatrix); //


        // --- 1. Draw Transparent Grey Background ---
        float[] bgColor = {0.2f, 0.2f, 0.2f, 0.75f}; // Dark grey, 75% opacity
        float z = 0.0f; // UI elements are typically at z=0 in screen space

        glBindVertexArray(spriteVaoId); //
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId); //
        spriteVertexBuffer.clear(); //

        // Quad: TL, BL, TR, TR, BL, BR
        // Top-Left
        spriteVertexBuffer.put(panelX).put(panelY).put(z)
                .put(bgColor[0]).put(bgColor[1]).put(bgColor[2]).put(bgColor[3])
                .put(0f).put(0f); // Dummy UVs
        // Bottom-Left
        spriteVertexBuffer.put(panelX).put(panelY + panelHeight).put(z)
                .put(bgColor[0]).put(bgColor[1]).put(bgColor[2]).put(bgColor[3])
                .put(0f).put(0f);
        // Top-Right
        spriteVertexBuffer.put(panelX + panelWidth).put(panelY).put(z)
                .put(bgColor[0]).put(bgColor[1]).put(bgColor[2]).put(bgColor[3])
                .put(0f).put(0f);
        // Top-Right
        spriteVertexBuffer.put(panelX + panelWidth).put(panelY).put(z)
                .put(bgColor[0]).put(bgColor[1]).put(bgColor[2]).put(bgColor[3])
                .put(0f).put(0f);
        // Bottom-Left
        spriteVertexBuffer.put(panelX).put(panelY + panelHeight).put(z)
                .put(bgColor[0]).put(bgColor[1]).put(bgColor[2]).put(bgColor[3])
                .put(0f).put(0f);
        // Bottom-Right
        spriteVertexBuffer.put(panelX + panelWidth).put(panelY + panelHeight).put(z)
                .put(bgColor[0]).put(bgColor[1]).put(bgColor[2]).put(bgColor[3])
                .put(0f).put(0f);

        spriteVertexBuffer.flip(); //
        glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer); //

        defaultShader.setUniform("uHasTexture", 0); // No texture for the background quad
        defaultShader.setUniform("uIsFont", 0);     // Not rendering font for the background
        glDrawArrays(GL_TRIANGLES, 0, 6); //

        // Unbind VBO after drawing background, VAO is still bound for text or will be rebound by font.
        // Actually, Font.drawText manages its own VAO/VBO bindings. So, unbind here.
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);


        // --- 2. Draw White Text Lines ---
        float textRenderX = panelX + 5f; // Padding inside the panel
        float currentTextY = panelY + 5f;  // Start Y, font drawing handles ascent
        if (uiFont.getAscent() > 0) { // Add ascent if available for better alignment from top
            currentTextY += uiFont.getAscent();
        }


        // The uiFont.drawText method already sets uIsFont, uHasTexture, binds texture, etc.
        for (String line : lines) {
            uiFont.drawText(textRenderX, currentTextY, line, 1.0f, 1.0f, 1.0f); // Draw white text
            currentTextY += 18f; // Adjust line height as needed, or get from font metrics
        }

        // --- Restore OpenGL State ---
        glEnable(GL_DEPTH_TEST); // Re-enable depth testing if you disabled it
        // defaultShader.unbind(); // The main render loop in Renderer.java should handle the final unbind
    }

    public void cleanup() {
        System.out.println("Renderer: Starting cleanup...");
        if(playerTexture!=null)playerTexture.delete();
        if(treeTexture!=null)treeTexture.delete();
        if(tileAtlasTexture!=null)tileAtlasTexture.delete();
        if(uiFont!=null)uiFont.cleanup();
        if(defaultShader!=null)defaultShader.cleanup();
        if(mapChunks!=null){for(Chunk ch:mapChunks)ch.cleanup();mapChunks.clear();}
        if(spriteVaoId!=0){glDeleteVertexArrays(spriteVaoId);spriteVaoId=0;}
        if(spriteVboId!=0){glDeleteBuffers(spriteVboId);spriteVboId=0;}
        if(spriteVertexBuffer!=null){MemoryUtil.memFree(spriteVertexBuffer);spriteVertexBuffer=null;}
        System.out.println("Renderer: Cleanup complete.");
    }

    private void checkError(String stage) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            String errorMsg = "Unknown GL error code: " + String.format("0x%X", error);
            System.err.println("Renderer: OpenGL Error at stage '" + stage + "': " + errorMsg);
        }
    }
}
