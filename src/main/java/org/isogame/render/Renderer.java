package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants;
import org.isogame.entitiy.PlayerModel;
import org.isogame.input.InputHandler;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.isogame.constants.*; // Already imported via static wildcard, but kept for clarity from user file
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random; // Present in user's file

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
    private int frameCount = 0; // Present in user's file

    private Texture playerTexture;
    private Texture treeTexture;
    private Font uiFont;
    private Random tileDetailRandom; // Present in user's file, though not obviously used

    private List<Chunk> mapChunks;

    private Shader defaultShader;
    private Matrix4f projectionMatrix;
    private Matrix4f modelViewMatrixForSprites; // Used for screen-space sprites in this version

    private int spriteVaoId = 0;
    private int spriteVboId = 0;
    private FloatBuffer spriteVertexBuffer;

    // Vertex layout: x, y, z, r, g, b, a, u, v (Z was added in previous attempts, this version might have used 8)
    // The user's uploaded Renderer.java (this baseline) was from AFTER we added Z.
    // So FLOATS_PER_VERTEX_TEXTURED should be 9.
    public static final int FLOATS_PER_VERTEX_TEXTURED = 9;
    public static final int POSITION_COMPONENT_COUNT = 3; // x, y, z
    public static final int POSITION_OFFSET = 0;
    public static final int COLOR_COMPONENT_COUNT = 4;    // r, g, b, a
    public static final int COLOR_OFFSET = POSITION_COMPONENT_COUNT * Float.BYTES;
    public static final int TEXCOORD_COMPONENT_COUNT = 2; // u, v
    public static final int TEXCOORD_OFFSET = COLOR_OFFSET + COLOR_COMPONENT_COUNT * Float.BYTES;
    public static final int STRIDE_TEXTURED = FLOATS_PER_VERTEX_TEXTURED * Float.BYTES;

    private Texture tileAtlasTexture;

    // UV Coordinates from the user-provided "working old" Renderer.java
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

    public static class TreeData {
        Tile.TreeVisualType treeVisualType; float mapCol, mapRow; int elevation;
        public TreeData(Tile.TreeVisualType type, float tc, float tr, int te) {
            this.treeVisualType = type; this.mapCol = tc; this.mapRow = tr; this.elevation = te;
        }
    }
    private static class SortableItem {
        float screenYSortKey; int zOrder; Object entity; float mapRow, mapCol;
        float treeScreenAnchorX, treeScreenAnchorY; // This was used when sprites were screen-space
        public SortableItem(PlayerModel p, CameraManager cam, Map m) {
            this.entity = p; this.zOrder = 1; this.mapRow = p.getMapRow(); this.mapCol = p.getMapCol();
            Tile t = m.getTile(p.getTileRow(), p.getTileCol()); int elev = (t != null) ? t.getElevation() : 0;
            int[] screenCoords = cam.mapToScreenCoordsForPicking(p.getMapCol(), p.getMapRow(), elev);
            this.screenYSortKey = screenCoords[1];
        }
        public SortableItem(TreeData tree, CameraManager cam, Map m) {
            this.entity = tree; this.zOrder = 0; this.mapRow = tree.mapRow; this.mapCol = tree.mapCol;
            int[] screenCoords = cam.mapToScreenCoordsForPicking(tree.mapCol, tree.mapRow, tree.elevation);
            this.screenYSortKey = screenCoords[1];
            this.treeScreenAnchorX = screenCoords[0]; // Store screen anchor for screen-space rendering
            this.treeScreenAnchorY = screenCoords[1];
        }
    }
    private List<SortableItem> sortableItems = new ArrayList<>();

    public Renderer(CameraManager camera, Map map, PlayerModel player, InputHandler inputHandler) {
        this.camera = camera;
        this.map = map;
        this.player = player;
        this.inputHandler = inputHandler;
        this.projectionMatrix = new Matrix4f();
        this.modelViewMatrixForSprites = new Matrix4f().identity(); // For screen-space sprites
        this.tileDetailRandom = new Random(); // From user's file
        loadAssets();
        initShaders();
        initRenderObjects();
        uploadTileMapGeometry();
    }

    private void loadAssets() {
        String playerTexturePath = "/org/isogame/render/textures/lpc_character.png";
        this.playerTexture = Texture.loadTexture(playerTexturePath);
        String treeTexturePath = "/org/isogame/render/textures/fruit-trees.png";
        this.treeTexture = Texture.loadTexture(treeTexturePath);
        String tileAtlasPath = "/org/isogame/render/textures/textu.png";
        this.tileAtlasTexture = Texture.loadTexture(tileAtlasPath);
        String fontPath = "/org/isogame/render/fonts/PressStart2P-Regular.ttf";
        try { this.uiFont = new Font(fontPath, 16f, this); } catch (IOException | RuntimeException e) { this.uiFont = null; System.err.println("Failed to load font: " + e.getMessage());}
    }
    private void initShaders() {
        try {
            defaultShader = new Shader();
            // Ensure your shaders are compatible with the vertex data (XYZ for position)
            defaultShader.createVertexShader(Shader.loadResource("/org/isogame/render/shaders/vertex.glsl"));
            defaultShader.createFragmentShader(Shader.loadResource("/org/isogame/render/shaders/fragment.glsl"));
            defaultShader.link();
            defaultShader.createUniform("uProjectionMatrix");
            defaultShader.createUniform("uModelViewMatrix");
            defaultShader.createUniform("uTextureSampler");
            defaultShader.createUniform("uHasTexture");
            defaultShader.createUniform("uIsFont");
            defaultShader.createUniform("uSubTextureV0");
            defaultShader.createUniform("uSubTextureVSpan");
            defaultShader.createUniform("uApplySubTextureRepeat");
        } catch (IOException e) { throw new RuntimeException("Renderer: Failed to initialize shaders", e); }
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
        spriteVertexBuffer = MemoryUtil.memAllocFloat(200 * 6 * FLOATS_PER_VERTEX_TEXTURED); // Using 9 floats
        glBufferData(GL_ARRAY_BUFFER, (long) spriteVertexBuffer.capacity() * Float.BYTES, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, POSITION_COMPONENT_COUNT, GL_FLOAT, false, STRIDE_TEXTURED, POSITION_OFFSET);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, COLOR_COMPONENT_COUNT, GL_FLOAT, false, STRIDE_TEXTURED, COLOR_OFFSET);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, TEXCOORD_COMPONENT_COUNT, GL_FLOAT, false, STRIDE_TEXTURED, TEXCOORD_OFFSET);
        glEnableVertexAttribArray(2);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void uploadTileMapGeometry() {
        if (mapChunks == null || mapChunks.isEmpty() || map == null) return;
        for (Chunk chunk : mapChunks) {
            chunk.uploadGeometry(map, inputHandler, this, camera);
        }
    }
    public void updateChunkContainingTile(int tileRow, int tileCol) {
        if (map == null || mapChunks == null || mapChunks.isEmpty() || CHUNK_SIZE_TILES <= 0) return;
        final int chunkGridX = tileCol / CHUNK_SIZE_TILES;
        final int chunkGridY = tileRow / CHUNK_SIZE_TILES;
        mapChunks.stream()
                .filter(chunk -> chunk.chunkGridX == chunkGridX && chunk.chunkGridY == chunkGridY)
                .findFirst()
                .ifPresent(chunk -> chunk.uploadGeometry(this.map, this.inputHandler, this, camera));
    }

    public void onResize(int framebufferWidth, int framebufferHeight) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) return;
        glViewport(0, 0, framebufferWidth, framebufferHeight);
        // This is the projection from the user's "working" version (screen-space ortho with Z)
        projectionMatrix.identity().ortho(0.0f, (float)framebufferWidth, (float)framebufferHeight, 0.0f,
                Constants.MIN_WORLD_Z_DEPTH, Constants.MAX_WORLD_Z_DEPTH);
        if (camera != null) {
            camera.setProjectionMatrixForCulling(projectionMatrix);
            camera.forceUpdateViewMatrix();
        }
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
            case WATER: return WATER_TOP_COLOR; case SAND:  return SAND_TOP_COLOR;
            case GRASS: return GRASS_TOP_COLOR; case ROCK:  return ROCK_TOP_COLOR;
            case SNOW:  return SNOW_TOP_COLOR;  default:    return DEFAULT_TOP_COLOR;
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
        final float diamondTopOffsetY = -tileHalfHeight;
        final float diamondLeftOffsetX = -tileHalfWidth;
        final float diamondSideOffsetY = 0;
        final float diamondRightOffsetX = tileHalfWidth;
        final float diamondBottomOffsetY = tileHalfHeight;
        float[] topSurfaceColor = determineTopSurfaceColor(currentTileTopSurfaceType, isSelected);
        float[] sideTintToUse = isSelected ? topSurfaceColor : WHITE_TINT;
        int verticesAdded = 0;
        if (currentTileTopSurfaceType != Tile.TileType.WATER) {
            verticesAdded += addPedestalSidesToBuffer(buffer, tileGridPlaneCenterX, tileGridPlaneCenterY, diamondLeftOffsetX, diamondSideOffsetY, diamondRightOffsetX, diamondBottomOffsetY, sideTintToUse);
        }
        float currentTileTopSurfaceActualY = tileGridPlaneCenterY - (currentTileElevation * elevationSliceHeight);
        if (currentTileTopSurfaceType == Tile.TileType.WATER) {
            currentTileTopSurfaceActualY = tileGridPlaneCenterY - (NIVEL_MAR * elevationSliceHeight);
        }
        verticesAdded += addTopSurfaceToBuffer(buffer, currentTileTopSurfaceType, isSelected, tileGridPlaneCenterX, currentTileTopSurfaceActualY, diamondLeftOffsetX, diamondSideOffsetY, diamondRightOffsetX, diamondTopOffsetY, diamondBottomOffsetY, topSurfaceColor, WHITE_TINT);
        if (currentTileElevation > 0 && currentTileTopSurfaceType != Tile.TileType.WATER) {
            verticesAdded += addStratifiedElevatedSidesToBuffer(buffer, currentTileElevation, tileGridPlaneCenterX, tileGridPlaneCenterY, diamondLeftOffsetX, diamondSideOffsetY, diamondRightOffsetX, diamondBottomOffsetY, elevationSliceHeight, sideTintToUse);
        }
        updateChunkBounds(chunkBoundsMinMax, tileGridPlaneCenterX, tileGridPlaneCenterY, currentTileElevation, elevationSliceHeight, diamondLeftOffsetX, diamondRightOffsetX, diamondTopOffsetY, diamondBottomOffsetY);
        return verticesAdded;
    }

    private int addPedestalSidesToBuffer(FloatBuffer buffer, float tileCenterX, float gridPlaneY, float diamondLeftOffsetX, float diamondSideOffsetY, float diamondRightOffsetX, float diamondBottomOffsetY, float[] tint) {
        int verticesCount = 0;
        float pedestalTopSurfaceY = gridPlaneY;
        float pedestalBottomSurfaceY = gridPlaneY + BASE_THICKNESS;
        float pTopLx = tileCenterX + diamondLeftOffsetX;  float pTopLy = pedestalTopSurfaceY + diamondSideOffsetY;
        float pTopRx = tileCenterX + diamondRightOffsetX; float pTopRy = pedestalTopSurfaceY + diamondSideOffsetY;
        float pTopBx = tileCenterX;                       float pTopBy = pedestalTopSurfaceY + diamondBottomOffsetY;
        float pBotLx = tileCenterX + diamondLeftOffsetX;  float pBotLy = pedestalBottomSurfaceY + diamondSideOffsetY;
        float pBotRx = tileCenterX + diamondRightOffsetX; float pBotRy = pedestalBottomSurfaceY + diamondSideOffsetY;
        float pBotBx = tileCenterX;                       float pBotBy = pedestalBottomSurfaceY + diamondBottomOffsetY;
        float zTopLy = pTopLy, zTopRy = pTopRy, zTopBy = pTopBy;
        float zBotLy = pBotLy, zBotRy = pBotRy, zBotBy = pBotBy;
        float u0 = DEFAULT_SIDE_U0; float v0 = DEFAULT_SIDE_V0;
        float u1 = DEFAULT_SIDE_U1; float vSpan = DEFAULT_SIDE_V1 - v0;
        float vRepeats = (BASE_THICKNESS / TILE_THICKNESS) * SIDE_TEXTURE_DENSITY_FACTOR;
        float vBottomTextureCoordinate = v0 + vSpan * vRepeats;
        verticesCount += addRectangularQuadWithTextureOrColor(buffer, pTopLx, pTopLy, zTopLy, pTopBx, pTopBy, zTopBy, pBotBx, pBotBy, zBotBy, pBotLx, pBotLy, zBotLy, tint, true, u0, v0, u1, vBottomTextureCoordinate);
        verticesCount += addRectangularQuadWithTextureOrColor(buffer, pTopBx, pTopBy, zTopBy, pTopRx, pTopRy, zTopRy, pBotRx, pBotRy, zBotRy, pBotBx, pBotBy, zBotBy, tint, true, u0, v0, u1, vBottomTextureCoordinate);
        return verticesCount;
    }

    private int addTopSurfaceToBuffer(FloatBuffer buffer, Tile.TileType topSurfaceType, boolean isSelected, float topFaceCenterX, float topFaceCenterY, float dLeftX, float dSideY, float dRightX, float dTopY, float dBottomY, float[] actualTopColor, float[] whiteTintIfTexturedAndNotSelected) {
        int verticesCount = 0;
        float topLx = topFaceCenterX + dLeftX;   float topLy = topFaceCenterY + dSideY;
        float topRx = topFaceCenterX + dRightX;  float topRy = topFaceCenterY + dSideY;
        float topTx = topFaceCenterX;            float topTy = topFaceCenterY + dTopY;
        float topBx = topFaceCenterX;            float topBy = topFaceCenterY + dBottomY;
        float surfaceZ = topFaceCenterY;
        float[] colorToUse;
        if (topSurfaceType == Tile.TileType.WATER) {
            colorToUse = actualTopColor;
            verticesCount += addRectangularQuadWithTextureOrColor(buffer, topLx,topLy, surfaceZ, topTx,topTy, surfaceZ, topRx,topRy, surfaceZ, topBx,topBy, surfaceZ, colorToUse, false, DUMMY_U,DUMMY_V,DUMMY_U,DUMMY_V);
        } else {
            float u0=DUMMY_U, v0_tex=DUMMY_V, u1=DUMMY_U, v1_tex=DUMMY_V;
            boolean textureThisTopSurface = false;
            switch (topSurfaceType) {
                case GRASS: u0=GRASS_ATLAS_U0;v0_tex=GRASS_ATLAS_V0;u1=GRASS_ATLAS_U1;v1_tex=GRASS_ATLAS_V1; textureThisTopSurface=true; break;
                case SAND:  u0=SAND_ATLAS_U0; v0_tex=SAND_ATLAS_V0; u1=SAND_ATLAS_U1; v1_tex=SAND_ATLAS_V1; textureThisTopSurface=true; break;
                case ROCK:  u0=ROCK_ATLAS_U0; v0_tex=ROCK_ATLAS_V0; u1=ROCK_ATLAS_U1; v1_tex=ROCK_ATLAS_V1; textureThisTopSurface=true; break;
            }
            if (isSelected) colorToUse = actualTopColor;
            else if (textureThisTopSurface) colorToUse = whiteTintIfTexturedAndNotSelected;
            else colorToUse = actualTopColor;
            if (textureThisTopSurface) {
                float midU=(u0+u1)/2f; float midV=(v0_tex+v1_tex)/2f;
                buffer.put(topLx).put(topLy).put(surfaceZ).put(colorToUse).put(u0).put(midV);
                buffer.put(topTx).put(topTy).put(surfaceZ).put(colorToUse).put(midU).put(v0_tex);
                buffer.put(topBx).put(topBy).put(surfaceZ).put(colorToUse).put(midU).put(v1_tex);
                verticesCount += 3;
                buffer.put(topTx).put(topTy).put(surfaceZ).put(colorToUse).put(midU).put(v0_tex);
                buffer.put(topRx).put(topRy).put(surfaceZ).put(colorToUse).put(u1).put(midV);
                buffer.put(topBx).put(topBy).put(surfaceZ).put(colorToUse).put(midU).put(v1_tex);
                verticesCount += 3;
            } else {
                verticesCount += addRectangularQuadWithTextureOrColor(buffer, topLx,topLy, surfaceZ, topTx,topTy, surfaceZ, topRx,topRy, surfaceZ, topBx,topBy, surfaceZ, colorToUse, false,DUMMY_U,DUMMY_V,DUMMY_U,DUMMY_V);
            }
        }
        return verticesCount;
    }

    private int addStratifiedElevatedSidesToBuffer(FloatBuffer buffer, int tileTotalElevation, float tileGridPlaneCenterX, float tileGridPlaneCenterY, float dLeftX, float dSideY, float dRightX, float dBottomY, float elevationSliceHeight, float[] tint) {
        int verticesCount = 0;
        for (int currentElevationStep = tileTotalElevation; currentElevationStep >= 1; currentElevationStep--) {
            int underlyingLayerElevation = currentElevationStep - 1;
            Tile.TileType sideMaterialType = getMaterialTypeForElevationSlice(underlyingLayerElevation);
            float sideU0, sideV0, sideU1, sideTextureVSpanInAtlas;
            switch (sideMaterialType) {
                case GRASS: sideU0=DEFAULT_SIDE_U0; sideV0=DEFAULT_SIDE_V0; sideU1=DEFAULT_SIDE_U1; sideTextureVSpanInAtlas=DEFAULT_SIDE_V1-DEFAULT_SIDE_V0; break;
                case SAND:  sideU0=SAND_ATLAS_U0; sideV0=SAND_ATLAS_V0; sideU1=SAND_ATLAS_U1; sideTextureVSpanInAtlas=SAND_ATLAS_V1-SAND_ATLAS_V0; break;
                case ROCK:  sideU0=ROCK_ATLAS_U0; sideV0=ROCK_ATLAS_V0; sideU1=ROCK_ATLAS_U1; sideTextureVSpanInAtlas=ROCK_ATLAS_V1-ROCK_ATLAS_V0; break;
                case SNOW:  sideU0=SNOW_SIDE_ATLAS_U0; sideV0=SNOW_SIDE_ATLAS_V0; sideU1=SNOW_SIDE_ATLAS_U1; sideTextureVSpanInAtlas=SNOW_SIDE_ATLAS_V1-SNOW_SIDE_ATLAS_V0; break;
                default:    sideU0=DEFAULT_SIDE_U0; sideV0=DEFAULT_SIDE_V0; sideU1=DEFAULT_SIDE_U1; sideTextureVSpanInAtlas=DEFAULT_SIDE_V1-DEFAULT_SIDE_V0; break;
            }
            float vTopTextureCoordinate = sideV0;
            float vBottomTextureCoordinate = sideV0 + sideTextureVSpanInAtlas * SIDE_TEXTURE_DENSITY_FACTOR;
            float sliceTopFaceCenterY = tileGridPlaneCenterY - (currentElevationStep * elevationSliceHeight);
            float sliceBottomFaceCenterY = tileGridPlaneCenterY - (underlyingLayerElevation * elevationSliceHeight);
            float sliceTopLx = tileGridPlaneCenterX + dLeftX;  float sliceTopLy = sliceTopFaceCenterY + dSideY;
            float sliceTopRx = tileGridPlaneCenterX + dRightX; float sliceTopRy = sliceTopFaceCenterY + dSideY;
            float sliceTopBx = tileGridPlaneCenterX;           float sliceTopBy = sliceTopFaceCenterY + dBottomY;
            float sliceBotLx = tileGridPlaneCenterX + dLeftX;  float sliceBotLy = sliceBottomFaceCenterY + dSideY;
            float sliceBotRx = tileGridPlaneCenterX + dRightX; float sliceBotRy = sliceBottomFaceCenterY + dSideY;
            float sliceBotBx = tileGridPlaneCenterX;           float sliceBotBy = sliceBottomFaceCenterY + dBottomY;
            float zTopLy = sliceTopLy, zTopRy = sliceTopRy, zTopBy = sliceTopBy;
            float zBotLy = sliceBotLy, zBotRy = sliceBotRy, zBotBy = sliceBotBy;
            verticesCount += addRectangularQuadWithTextureOrColor(buffer, sliceTopLx, sliceTopLy, zTopLy, sliceTopBx, sliceTopBy, zTopBy, sliceBotBx, sliceBotBy, zBotBy, sliceBotLx, sliceBotLy, zBotLy, tint, true, sideU0, vTopTextureCoordinate, sideU1, vBottomTextureCoordinate);
            verticesCount += addRectangularQuadWithTextureOrColor(buffer, sliceTopBx, sliceTopBy, zTopBy, sliceTopRx, sliceTopRy, zTopRy, sliceBotRx, sliceBotRy, zBotRy, sliceBotBx, sliceBotBy, zBotBy, tint, true, sideU0, vTopTextureCoordinate, sideU1, vBottomTextureCoordinate);
        }
        return verticesCount;
    }

    private void updateChunkBounds(float[] chunkBoundsMinMax, float tileGridPlaneCenterX, float tileGridPlaneCenterY, int currentTileElevation, float elevationSliceHeight, float dLeftX, float dRightX, float dTopY, float dBottomY) {
        float minX = tileGridPlaneCenterX + dLeftX; float maxX = tileGridPlaneCenterX + dRightX;
        float minY = tileGridPlaneCenterY - (currentTileElevation * elevationSliceHeight) + dTopY;
        float maxY = tileGridPlaneCenterY + BASE_THICKNESS + dBottomY;
        chunkBoundsMinMax[0] = Math.min(chunkBoundsMinMax[0], minX); chunkBoundsMinMax[1] = Math.min(chunkBoundsMinMax[1], minY);
        chunkBoundsMinMax[2] = Math.max(chunkBoundsMinMax[2], maxX); chunkBoundsMinMax[3] = Math.max(chunkBoundsMinMax[3], maxY);
    }
    public int addGrassVerticesForTile_WorldSpace_ForChunk(int r,int c,Tile t,FloatBuffer b,float[] bounds){return 0;}

    private int addRectangularQuadWithTextureOrColor(FloatBuffer buffer, float xTL, float yTL, float zTL, float xTR, float yTR, float zTR, float xBR, float yBR, float zBR, float xBL, float yBL, float zBL, float[] color, boolean isTextured, float u0, float v0_tex, float u1, float v1_tex_bottom) {
        float actual_u0 = isTextured ? u0 : DUMMY_U;
        float actual_v0_top = isTextured ? v0_tex : DUMMY_V;
        float actual_u1_coord = isTextured ? u1 : DUMMY_U;
        float actual_v1_coord_bottom = isTextured ? v1_tex_bottom : DUMMY_V;
        buffer.put(xTL).put(yTL).put(zTL).put(color).put(actual_u0).put(actual_v0_top);
        buffer.put(xBL).put(yBL).put(zBL).put(color).put(actual_u0).put(actual_v1_coord_bottom);
        buffer.put(xTR).put(yTR).put(zTR).put(color).put(actual_u1_coord).put(actual_v0_top);
        buffer.put(xTR).put(yTR).put(zTR).put(color).put(actual_u1_coord).put(actual_v0_top);
        buffer.put(xBL).put(yBL).put(zBL).put(color).put(actual_u0).put(actual_v1_coord_bottom);
        buffer.put(xBR).put(yBR).put(zBR).put(color).put(actual_u1_coord).put(actual_v1_coord_bottom);
        return 6;
    }

    private void collectSortableItems() {
        sortableItems.clear(); if (player != null && camera != null && map != null) sortableItems.add(new SortableItem(player, camera, map));
        if (mapChunks == null || camera == null || map == null) return;
        for (Chunk chunk : mapChunks) if (camera.isChunkVisible(chunk.getBoundingBox())) for (TreeData tree : chunk.getTreesInChunk()) sortableItems.add(new SortableItem(tree, camera, map));
    }

    // This version is for screen-space sprite rendering, which is what the user's "working" version used.
    private int addPlayerVerticesToBuffer_ScreenSpace(PlayerModel p, FloatBuffer buffer) {
        if (playerTexture==null||playerTexture.getId()==0||camera==null||map==null) return 0;
        Tile currentTile = map.getTile(p.getTileRow(),p.getTileCol()); int playerElevationOnTile = (currentTile!=null) ? currentTile.getElevation() : 0;
        int[] screenCoords = camera.mapToScreenCoordsForPicking(p.getMapCol(),p.getMapRow(),playerElevationOnTile);
        float screenX = screenCoords[0]; float screenY = screenCoords[1]; float currentZoom = camera.getZoom();
        float spritePixelWidth = PlayerModel.FRAME_WIDTH; float spritePixelHeight = PlayerModel.FRAME_HEIGHT;
        float scaledSpriteWidth = spritePixelWidth*currentZoom; float scaledSpriteHeight = spritePixelHeight*currentZoom;
        float drawX = screenX - scaledSpriteWidth/2f; float drawY = screenY - scaledSpriteHeight;
        if(p.isLevitating()) drawY-=(Math.sin(p.getLevitateTimer())*8*currentZoom);
        int animFrameColumn = p.getVisualFrameIndex(); int animFrameRow = p.getAnimationRow();
        if(playerTexture.getWidth()==0||playerTexture.getHeight()==0) return 0;
        float texU0 = (animFrameColumn*spritePixelWidth)/playerTexture.getWidth(); float texV0 = (animFrameRow*spritePixelHeight)/playerTexture.getHeight();
        float texU1 = ((animFrameColumn+1)*spritePixelWidth)/playerTexture.getWidth(); float texV1 = ((animFrameRow+1)*spritePixelHeight)/playerTexture.getHeight();
        // Z is 0 for screen-space sprites in this rendering path
        return addRectangularQuadWithTextureOrColor(buffer,drawX,drawY,0f,drawX+scaledSpriteWidth,drawY,0f,drawX+scaledSpriteWidth,drawY+scaledSpriteHeight,0f,drawX,drawY+scaledSpriteHeight,0f,WHITE_TINT,true,texU0,texV0,texU1,texV1);
    }

    private int addTreeVerticesToBuffer_ScreenSpace(TreeData tree, FloatBuffer buffer, float screenAnchorX, float screenAnchorY) {
        if (treeTexture==null||treeTexture.getId()==0||tree.treeVisualType==Tile.TreeVisualType.NONE||camera==null) return 0;
        float frameW=0,frameH=0,atlasU0=0,atlasV0=0,anchorOffsetYPixels=0;
        switch(tree.treeVisualType){ case APPLE_TREE_FRUITING: frameW=90; frameH=130; atlasU0=0; atlasV0=0; anchorOffsetYPixels=15; break;
            case PINE_TREE_SMALL:     frameW=90; frameH=180; atlasU0=90; atlasV0=0; anchorOffsetYPixels=10; break; default: return 0;}
        if(treeTexture.getWidth()==0||treeTexture.getHeight()==0) return 0;
        float texU0 = atlasU0/treeTexture.getWidth(); float texV0 = atlasV0/treeTexture.getHeight();
        float texU1 = (atlasU0+frameW)/treeTexture.getWidth(); float texV1 = (atlasV0+frameH)/treeTexture.getHeight();
        float currentZoom = camera.getZoom(); float scaledSpriteWidth = frameW*currentZoom; float scaledSpriteHeight = frameH*currentZoom;
        float scaledAnchorOffsetY = anchorOffsetYPixels*currentZoom; float drawX = screenAnchorX - scaledSpriteWidth/2f;
        float drawY = screenAnchorY - (scaledSpriteHeight-scaledAnchorOffsetY);
        // Z is 0 for screen-space sprites in this rendering path
        return addRectangularQuadWithTextureOrColor(buffer,drawX,drawY,0f,drawX+scaledSpriteWidth,drawY,0f,drawX+scaledSpriteWidth,drawY+scaledSpriteHeight,0f,drawX,drawY+scaledSpriteHeight,0f,WHITE_TINT,true,texU0,texV0,texU1,texV1);
    }


    public void render() {
        frameCount++; // From user's file
        defaultShader.bind();

        // --- 1. Render Map Chunks (using 3D projection and view) ---
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);
        defaultShader.setUniform("uModelViewMatrix", camera.getViewMatrix());
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uApplySubTextureRepeat", 0); // As per user's "working" version for sides
        defaultShader.setUniform("uSubTextureV0", DEFAULT_SIDE_V0);
        defaultShader.setUniform("uSubTextureVSpan", DEFAULT_SIDE_V1 - DEFAULT_SIDE_V0);

        if (tileAtlasTexture != null && tileAtlasTexture.getId() != 0) {
            glActiveTexture(GL_TEXTURE0); tileAtlasTexture.bind();
            defaultShader.setUniform("uTextureSampler", 0); defaultShader.setUniform("uHasTexture", 1);
        } else { defaultShader.setUniform("uHasTexture", 0); }
        if (mapChunks != null) {
            for (Chunk chunk : mapChunks) {
                if (camera.isChunkVisible(chunk.getBoundingBox())) chunk.render();
            }
        }
        glBindVertexArray(0); // Good practice to unbind VAO after drawing a set of objects
        if (tileAtlasTexture != null && tileAtlasTexture.getId() != 0) glBindTexture(GL_TEXTURE_2D, 0);


        // --- 2. Render Sprites (Player, Trees) ---
        // This section reverts to the user's "working" (for camera/scale) screen-space sprite rendering
        Matrix4f spriteProjection = new Matrix4f().ortho(0, camera.getScreenWidth(), camera.getScreenHeight(), 0, -1, 1); // Simple 2D ortho
        defaultShader.setUniform("uProjectionMatrix", spriteProjection);
        defaultShader.setUniform("uModelViewMatrix", modelViewMatrixForSprites); // Identity for screen-space

        collectSortableItems();
        Collections.sort(sortableItems, (item1, item2) -> {
            if (Math.abs(item1.screenYSortKey - item2.screenYSortKey) > 0.1f) return Float.compare(item1.screenYSortKey, item2.screenYSortKey);
            float depth1 = item1.mapRow + item1.mapCol; float depth2 = item2.mapRow + item2.mapCol;
            if (Math.abs(depth1 - depth2) > 0.01f) return Float.compare(depth1, depth2);
            int elev1 = (item1.entity instanceof PlayerModel) ? (map.getTile(((PlayerModel)item1.entity).getTileRow(), ((PlayerModel)item1.entity).getTileCol()) != null ? map.getTile(((PlayerModel)item1.entity).getTileRow(), ((PlayerModel)item1.entity).getTileCol()).getElevation() : 0) : ((TreeData)item1.entity).elevation;
            int elev2 = (item2.entity instanceof PlayerModel) ? (map.getTile(((PlayerModel)item2.entity).getTileRow(), ((PlayerModel)item2.entity).getTileCol()) != null ? map.getTile(((PlayerModel)item2.entity).getTileRow(), ((PlayerModel)item2.entity).getTileCol()).getElevation() : 0) : ((TreeData)item2.entity).elevation;
            if (elev1 != elev2) return Integer.compare(elev1, elev2);
            return Integer.compare(item1.zOrder, item2.zOrder);
        });

        if (spriteVaoId != 0 && spriteVboId != 0) {
            glBindVertexArray(spriteVaoId); glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
            defaultShader.setUniform("uHasTexture", 1); defaultShader.setUniform("uTextureSampler", 0);
            glActiveTexture(GL_TEXTURE0); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            // Sprites do not write to depth buffer in this screen-space setup if we want them always on top
            // or sorted by painter's algorithm only.
            // However, if we want terrain to occlude them, they need to be part of the 3D scene.
            // For now, reverting to how it likely was:
            // glDepthMask(GL_FALSE); // Sprites don't fight with terrain depth, but also won't be occluded

            for (SortableItem item : sortableItems) {
                spriteVertexBuffer.clear(); int verticesInSprite = 0; Texture textureForSprite = null;
                if (item.entity instanceof PlayerModel) { PlayerModel p = (PlayerModel)item.entity; if(playerTexture!=null && playerTexture.getId()!=0){ verticesInSprite = addPlayerVerticesToBuffer_ScreenSpace(p, spriteVertexBuffer); textureForSprite = playerTexture; }
                } else if (item.entity instanceof TreeData) { TreeData td = (TreeData)item.entity; if(treeTexture!=null && treeTexture.getId()!=0){ verticesInSprite = addTreeVerticesToBuffer_ScreenSpace(td, spriteVertexBuffer, item.treeScreenAnchorX, item.treeScreenAnchorY); textureForSprite = treeTexture;} }
                if (verticesInSprite > 0 && textureForSprite != null) { spriteVertexBuffer.flip(); textureForSprite.bind(); glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer); glDrawArrays(GL_TRIANGLES, 0, verticesInSprite); }
            }
            glBindTexture(GL_TEXTURE_2D, 0);
            // glDepthMask(GL_TRUE); // Restore depth mask
        }
        glBindVertexArray(0); glBindBuffer(GL_ARRAY_BUFFER, 0);


        // --- 3. Render UI ---
        // UI uses the same spriteProjection and identity model-view
        if (uiFont != null && uiFont.isInitialized()) {
            renderUI();
        }
        defaultShader.unbind();
    }
    private void renderUI() {
        if (uiFont == null || !uiFont.isInitialized()) return;
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        // UI should generally not write to depth or be depth tested if it's an overlay
        // glDepthMask(GL_FALSE); // Optional: ensure UI doesn't write to depth
        // glDisable(GL_DEPTH_TEST); // Optional: ensure UI isn't depth tested

        int yPos = 20; final int yIncrement = 18;
        uiFont.drawText(10f, (float)yPos, "Player: ("+player.getTileRow()+", "+player.getTileCol()+") Act: "+player.getCurrentAction()+" Dir: "+player.getCurrentDirection()+" F:"+player.getVisualFrameIndex()); yPos+=yIncrement;
        Tile selectedTile = map.getTile(inputHandler.getSelectedRow(),inputHandler.getSelectedCol());
        String selectedInfo="Selected: ("+inputHandler.getSelectedRow()+", "+inputHandler.getSelectedCol()+")";
        if(selectedTile!=null){selectedInfo+=" Elev: "+selectedTile.getElevation()+" Type: "+selectedTile.getType();}
        uiFont.drawText(10f, (float)yPos, selectedInfo); yPos+=yIncrement;
        uiFont.drawText(10f, (float)yPos, String.format("Camera: (%.1f, %.1f) Zoom: %.2f",camera.getCameraX(),camera.getCameraY(),camera.getZoom())); yPos+=yIncrement;
        uiFont.drawText(10f, (float)yPos, "Move: Click | Elev Sel +/-: Q/E | Dig: J"); yPos+=yIncrement;
        uiFont.drawText(10f, (float)yPos, "Levitate: F | Center Cam: C | Regen Map: G"); yPos+=yIncrement; yPos+=yIncrement;
        uiFont.drawText(10f, (float)yPos, "Inventory:");yPos+=yIncrement;
        java.util.Map<String,Integer> inventory=player.getInventory();
        if(inventory.isEmpty()){ uiFont.drawText(20f, (float)yPos, "- Empty -");}
        else { for(java.util.Map.Entry<String,Integer> entry : inventory.entrySet()){ uiFont.drawText(20f, (float)yPos, "- "+entry.getKey()+": "+entry.getValue()); yPos+=yIncrement;}}

        // Restore depth settings if changed for UI
        // glEnable(GL_DEPTH_TEST);
        // glDepthMask(GL_TRUE);
    }
    public void cleanup() {
        if(playerTexture!=null)playerTexture.delete(); if(treeTexture!=null)treeTexture.delete(); if(tileAtlasTexture!=null)tileAtlasTexture.delete();
        if(uiFont!=null)uiFont.cleanup(); if(defaultShader!=null)defaultShader.cleanup();
        if(mapChunks!=null){for(Chunk ch:mapChunks)ch.cleanup();mapChunks.clear();}
        if(spriteVaoId!=0)glDeleteVertexArrays(spriteVaoId); if(spriteVboId!=0)glDeleteBuffers(spriteVboId);
        if(spriteVertexBuffer!=null)MemoryUtil.memFree(spriteVertexBuffer);
    }
    private void checkError(String stage) { // Kept for debugging
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            String errorMsg = "Unknown GL error"; /* Basic error reporting */
            System.err.println("Renderer: OpenGL Error at stage '" + stage + "': " + errorMsg + " (0x" + Integer.toHexString(error) + ")");
        }
    }
}
