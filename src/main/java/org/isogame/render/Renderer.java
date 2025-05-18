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
import java.util.Comparator; // Corrected import
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
    private Texture treeTexture; // Atlas for all trees
    private Font uiFont;
    private Random tileDetailRandom;

    private List<Chunk> mapChunks;

    private Shader defaultShader;
    private Matrix4f projectionMatrix; // Screen-space orthographic projection
    private Matrix4f modelViewMatrixForSprites; // Usually identity for screen-space sprites

    private int spriteVaoId = 0;
    private int spriteVboId = 0;
    private FloatBuffer spriteVertexBuffer;

    public static final int FLOATS_PER_VERTEX_TEXTURED = 8; // x,y, r,g,b,a, u,v
    public static final int FLOATS_PER_VERTEX_COLORED = 6;  // x,y, r,g,b,a

    private Texture tileAtlasTexture;

    // --- Tile Atlas UV Coordinates ---
    // These MUST accurately reflect your tile atlas (textu.png)
    private static final float ATLAS_TOTAL_WIDTH = 128.0f;
    private static final float ATLAS_TOTAL_HEIGHT = 128.0f;
    private static final float SUB_TEX_WIDTH = 64.0f;
    private static final float SUB_TEX_HEIGHT = 64.0f;

    // Top Surface Textures
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

    // Side Textures
    private static final float DEFAULT_SIDE_U0 = (1 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH; // DIRT U0
    private static final float DEFAULT_SIDE_V0 = (0 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT; // DIRT V0
    private static final float DEFAULT_SIDE_U1 = (2 * SUB_TEX_WIDTH) / ATLAS_TOTAL_WIDTH; // DIRT U1
    private static final float DEFAULT_SIDE_V1 = (1 * SUB_TEX_HEIGHT) / ATLAS_TOTAL_HEIGHT; // DIRT V1

    // Placeholder for SNOW side texture (currently uses ROCK as a fallback)
    // Define specific UVs if you have a snow side texture.
    private static final float SNOW_SIDE_ATLAS_U0 = ROCK_ATLAS_U0;
    private static final float SNOW_SIDE_ATLAS_V0 = ROCK_ATLAS_V0;
    private static final float SNOW_SIDE_ATLAS_U1 = ROCK_ATLAS_U1;
    private static final float SNOW_SIDE_ATLAS_V1 = ROCK_ATLAS_V1;

    // User request: Set to 1.0f. This means the side texture sub-region from the atlas
    // will be mapped exactly once to the geometric height of one TILE_THICKNESS unit.
    // If sides look stretched, it's because TILE_THICKNESS is visually large relative
    // to the detail in the side texture, or the side sub-texture in the atlas is small.
    private static final float SIDE_TEXTURE_DENSITY_FACTOR = 1.0f;

    private static final float DUMMY_U = 0.0f; // For untextured quads
    private static final float DUMMY_V = 0.0f;

    // Inner classes for sprite sorting
    private static class SortableItem {
        float screenYSortKey;
        int zOrder; // 0 for static (trees), 1 for dynamic (player)
        Object entity;
        float mapRow, mapCol; // For tie-breaking

        public SortableItem(PlayerModel p, CameraManager cam, Map m) {
            this.entity = p;
            this.zOrder = 1;
            this.mapRow = p.getMapRow();
            this.mapCol = p.getMapCol();
            Tile t = m.getTile(p.getTileRow(), p.getTileCol());
            int elev = (t != null) ? t.getElevation() : 0;
            int[] screenCoords = cam.mapToScreenCoordsForPicking(p.getMapCol(), p.getMapRow(), elev);
            this.screenYSortKey = screenCoords[1];
        }

        public SortableItem(TreeData tree, CameraManager cam, Map m) {
            this.entity = tree;
            this.zOrder = 0;
            this.mapRow = tree.mapRow;
            this.mapCol = tree.mapCol;
            int[] screenCoords = cam.mapToScreenCoordsForPicking(tree.mapCol, tree.mapRow, tree.elevation);
            this.screenYSortKey = screenCoords[1];
        }
    }

    private static class TreeData {
        Tile.TreeVisualType treeVisualType;
        float mapCol, mapRow;
        int elevation; // Elevation of the tile the tree is on
        float topDiamondCenterX_screen, topDiamondCenterY_screen; // Screen coords for anchor

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
        this.inputHandler = inputHandler;
        this.tileDetailRandom = new Random();
        this.projectionMatrix = new Matrix4f();
        this.modelViewMatrixForSprites = new Matrix4f().identity();

        System.out.println("Renderer: Initializing...");
        loadAssets();
        initShaders();
        initRenderObjects();
        uploadTileMapGeometry(); // Initial upload of all chunk geometries
        System.out.println("Renderer: Initialization complete.");
    }

    private void loadAssets() {
        System.out.println("Renderer: Starting asset loading...");
        String playerTexturePath = "/org/isogame/render/textures/lpc_character.png";
        this.playerTexture = Texture.loadTexture(playerTexturePath);
        if (this.playerTexture == null) {
            System.err.println("Renderer CRITICAL: Player texture FAILED to load: " + playerTexturePath);
        } else {
            System.out.println("Renderer: Player texture loaded: " + playerTexturePath + " ID: " + playerTexture.getId() + " W: " + playerTexture.getWidth() + " H: " + playerTexture.getHeight());
        }

        // IMPORTANT: Verify this path points to your correct tree atlas including palm trees.
        String treeTexturePath = "/org/isogame/render/textures/fruit-trees.png"; // Or trees_atlas.png
        this.treeTexture = Texture.loadTexture(treeTexturePath);
        if (this.treeTexture == null) {
            System.err.println("Renderer CRITICAL: Tree texture FAILED to load: " + treeTexturePath);
        } else {
            System.out.println("Renderer: Tree texture loaded: " + treeTexturePath + " ID: " + treeTexture.getId() + " W: " + treeTexture.getWidth() + " H: " + treeTexture.getHeight());
        }


        String tileAtlasPath = "/org/isogame/render/textures/textu.png";
        this.tileAtlasTexture = Texture.loadTexture(tileAtlasPath);
        if (this.tileAtlasTexture == null) {
            System.err.println("Renderer CRITICAL: Tile Atlas FAILED to load: " + tileAtlasPath);
        } else {
            System.out.println("Renderer: Tile Atlas loaded: " + tileAtlasPath + " ID: " + tileAtlasTexture.getId() + " W: " + tileAtlasTexture.getWidth() + " H: " + tileAtlasTexture.getHeight());
        }


        String fontPath = "/org/isogame/render/fonts/PressStart2P-Regular.ttf";
        try {
            this.uiFont = new Font(fontPath, 16f, this); // Pass `this` if Font constructor needs Renderer
            if (!this.uiFont.isInitialized()) {
                System.err.println("Renderer WARNING: Font not fully initialized after loading.");
            }
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
            // Create all uniforms used by the shader program
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
            System.err.println("Renderer: Map is null or CHUNK_SIZE_TILES is invalid. Cannot initialize chunks.");
        }

        // Initialize VAO/VBO for Sprites
        spriteVaoId = glGenVertexArrays();
        glBindVertexArray(spriteVaoId);

        spriteVboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);

        // Capacity for ~100 sprites. Adjust if more are needed simultaneously.
        spriteVertexBuffer = MemoryUtil.memAllocFloat(100 * 6 * FLOATS_PER_VERTEX_TEXTURED);
        glBufferData(GL_ARRAY_BUFFER, (long) spriteVertexBuffer.capacity() * Float.BYTES, GL_DYNAMIC_DRAW);

        // Vertex attributes for sprites (Position, Color, TexCoord)
        int stride = FLOATS_PER_VERTEX_TEXTURED * Float.BYTES;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L); // Position (vec2)
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 2 * Float.BYTES); // Color (vec4)
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, (2 + 4) * Float.BYTES); // TexCoord (vec2)
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        System.out.println("Renderer: Sprite VAO/VBO initialized.");
    }

    public void uploadTileMapGeometry() {
        if (mapChunks == null || mapChunks.isEmpty() || map == null) {
            System.err.println("Renderer: Cannot upload tile map geometry - chunks or map not initialized.");
            return;
        }
        System.out.println("Renderer: Uploading tile map geometry for ALL chunks...");
        for (Chunk chunk : mapChunks) {
            chunk.uploadGeometry(map, inputHandler, this);
        }
        System.out.println("Renderer: All chunk geometries uploaded.");
    }

    public void updateChunkContainingTile(int tileRow, int tileCol) {
        if (map == null || mapChunks == null || mapChunks.isEmpty() || Constants.CHUNK_SIZE_TILES <= 0) {
            System.err.println("Renderer: Cannot update chunk, map/chunks not ready or CHUNK_SIZE_TILES invalid.");
            return;
        }
        final int chunkGridX = tileCol / Constants.CHUNK_SIZE_TILES;
        final int chunkGridY = tileRow / Constants.CHUNK_SIZE_TILES;

        mapChunks.stream()
                .filter(chunk -> chunk.chunkGridX == chunkGridX && chunk.chunkGridY == chunkGridY)
                .findFirst()
                .ifPresentOrElse(
                        chunk -> {
                            System.out.println("Renderer: Updating geometry for chunk (" + chunkGridX + "," + chunkGridY + ")");
                            chunk.uploadGeometry(this.map, this.inputHandler, this);
                        },
                        () -> System.err.println("Renderer: Chunk not found for tile (" + tileRow + "," + tileCol + ")")
                );
    }

    public void onResize(int framebufferWidth, int framebufferHeight) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            System.err.println("Renderer: Invalid dimensions for onResize: " + framebufferWidth + "x" + framebufferHeight);
            return;
        }
        glViewport(0, 0, framebufferWidth, framebufferHeight);
        // This projection matrix is for screen-space elements like UI and 2D sprites.
        projectionMatrix.identity().ortho(0, framebufferWidth, framebufferHeight, 0, -1, 1);

        if (camera != null) {
            // The camera might use its own projection for the isometric world view,
            // or this one if its view matrix incorporates the necessary transformations.
            // Update camera's culling matrix if it uses this projection.
            camera.setProjectionMatrixForCulling(projectionMatrix);
            camera.forceUpdateViewMatrix(); // Screen size change can affect camera's view matrix.
        }
        System.out.println("Renderer: Screen resized to " + framebufferWidth + "x" + framebufferHeight);
    }

    /**
     * Determines the geological material type for a given absolute elevation level.
     * This is used for selecting the appropriate side texture for a cliff face slice.
     * Water tiles themselves are handled by their top surface type; this method
     * is for determining the material of a solid layer at a given elevation.
     * If water level is 9, an elevation of 8 is considered WATER material.
     */
    private Tile.TileType getMaterialTypeForElevationSlice(int elevationLevel) {
        if (elevationLevel < NIVEL_MAR) return Tile.TileType.WATER; // Technically, layers below water level
        if (elevationLevel < NIVEL_ARENA) return Tile.TileType.SAND;
        if (elevationLevel < NIVEL_ROCA) return Tile.TileType.GRASS; // Represents earth/dirt layer
        if (elevationLevel < NIVEL_NIEVE) return Tile.TileType.ROCK;
        return Tile.TileType.SNOW;
    }

    // =====================================================================================
    // REFACTORED TILE VERTEX GENERATION
    // =====================================================================================
    public int addSingleTileVerticesToBuffer_WorldSpace_ForChunk(
            int tileR, int tileC, Tile tile, boolean isSelected, FloatBuffer buffer, float[] chunkBoundsMinMax) {

        int currentTileElevation = tile.getElevation();
        Tile.TileType currentTileTopSurfaceType = tile.getType();

        // Basic geometric properties
        final float tileHalfWidth = TILE_WIDTH / 2.0f;
        final float tileHalfHeight = TILE_HEIGHT / 2.0f;
        final float elevationSliceHeight = TILE_THICKNESS; // Visual height of one elevation unit

        // World-space center of the tile at the grid plane (y=0 for this tile's column/row)
        final float tileGridPlaneCenterX = (tileC - tileR) * tileHalfWidth;
        final float tileGridPlaneCenterY = (tileC + tileR) * tileHalfHeight;

        // Diamond shape offsets relative to a center point (Y-axis is screen up/down)
        final float diamondTopOffsetY = -tileHalfHeight;    // Y-offset for the top "tip" of the diamond
        final float diamondLeftOffsetX = -tileHalfWidth;    // X-offset for the left "corner"
        final float diamondSideOffsetY = 0;                 // Y-offset for left/right "corners" (at Y-center of diamond)
        final float diamondRightOffsetX = tileHalfWidth;   // X-offset for the right "corner"
        final float diamondBottomOffsetY = tileHalfHeight;  // Y-offset for the bottom "tip"

        float[] topSurfaceColor = determineTopSurfaceColor(currentTileTopSurfaceType, isSelected);
        // For textured sides, use white tint unless selected (then use selection color)
        float[] sideTintToUse = isSelected ? topSurfaceColor : new float[]{1.0f, 1.0f, 1.0f, 1.0f};

        int verticesAdded = 0;

        // --- 1. Pedestal Sides (Below Grid Plane y=0) ---
        // Pedestal sides are always DIRT textured.
        // Water tiles typically don't have a visible pedestal in the same way,
        // their "sides" down to the water table might be handled differently or not drawn if submerged.
        if (currentTileTopSurfaceType != Tile.TileType.WATER) {
            verticesAdded += addPedestalSidesToBuffer(
                    buffer,
                    tileGridPlaneCenterX, tileGridPlaneCenterY,
                    diamondLeftOffsetX, diamondSideOffsetY,
                    diamondRightOffsetX, diamondBottomOffsetY,
                    sideTintToUse
            );
        }

        // --- 2. Tile Top Surface ---
        // Y-coordinate of this tile's actual top surface
        float currentTileTopSurfaceActualY = tileGridPlaneCenterY - (currentTileElevation * elevationSliceHeight);

        // For water tiles, their "elevation" is fixed at NIVEL_MAR by game logic.
        // The renderer just draws the top surface at that fixed Y.
        if (currentTileTopSurfaceType == Tile.TileType.WATER) {
            // Ensure water is drawn at the correct, fixed Y level (NIVEL_MAR)
            // The 'elevation' field for a water tile should be NIVEL_MAR -1 or NIVEL_MAR based on definition.
            // If NIVEL_MAR is the water surface, then a tile *at* NIVEL_MAR is the first dry land.
            // A tile *below* NIVEL_MAR is water.
            // Let's assume tile.getElevation() for water is its surface level.
            // If water is always at elev 9 (NIVEL_MAR), then currentTileElevation would be NIVEL_MAR.
            currentTileTopSurfaceActualY = tileGridPlaneCenterY - (NIVEL_MAR * elevationSliceHeight);
        }


        verticesAdded += addTopSurfaceToBuffer(
                buffer,
                currentTileTopSurfaceType, isSelected,
                tileGridPlaneCenterX, currentTileTopSurfaceActualY, // Use actual Y for the top surface
                diamondLeftOffsetX, diamondSideOffsetY,
                diamondRightOffsetX, diamondTopOffsetY, diamondBottomOffsetY,
                topSurfaceColor, // Pass the calculated actual color
                new float[]{1.0f, 1.0f, 1.0f, 1.0f} // White tint for textured non-selected tops
        );


        // --- 3. Stratified Elevated Sides (Sides above Grid Plane y=0) ---
        // These sides change texture based on the geological layer they represent.
        // Water tiles do not have elevated sides in this manner.
        if (currentTileElevation > 0 && currentTileTopSurfaceType != Tile.TileType.WATER) {
            verticesAdded += addStratifiedElevatedSidesToBuffer(
                    buffer,
                    currentTileElevation, // Total elevation of the current tile
                    tileGridPlaneCenterX, tileGridPlaneCenterY,
                    diamondLeftOffsetX, diamondSideOffsetY,
                    diamondRightOffsetX, diamondBottomOffsetY,
                    elevationSliceHeight,
                    sideTintToUse
            );
        }

        // --- Update Bounding Box for Culling ---
        updateChunkBounds(
                chunkBoundsMinMax,
                tileGridPlaneCenterX, tileGridPlaneCenterY,
                currentTileElevation, // Use the tile's actual elevation
                elevationSliceHeight,
                diamondLeftOffsetX, diamondRightOffsetX,
                diamondTopOffsetY, diamondBottomOffsetY
        );
        return verticesAdded;
    }

    /**
     * Determines the color for a tile's top surface.
     * @param surfaceType The type of the tile.
     * @param isSelected Whether the tile is selected.
     * @return RGBA color array.
     */
    private float[] determineTopSurfaceColor(Tile.TileType surfaceType, boolean isSelected) {
        if (isSelected) {
            return new float[]{1.0f, 0.8f, 0.0f, 0.8f}; // Yellowish tint for selected
        }
        // Base colors for non-selected tiles
        switch (surfaceType) {
            case WATER: return new float[]{0.05f, 0.25f, 0.5f, 0.85f};
            case SAND:  return new float[]{0.82f,0.7f,0.55f,1f};
            case GRASS: return new float[]{0.20f,0.45f,0.10f,1f};
            case ROCK:  return new float[]{0.45f,0.45f,0.45f,1f};
            case SNOW:  return new float[]{0.95f,0.95f,1.0f,1f};
            default:    return new float[]{1f,0f,1f,1f}; // Magenta for undefined
        }
    }

    /**
     * Adds vertices for the pedestal sides (below the grid plane).
     * Pedestal sides are always DIRT textured.
     */
    private int addPedestalSidesToBuffer(FloatBuffer buffer,
                                         float tileCenterX, float gridPlaneY,
                                         float diamondLeftOffsetX, float diamondSideOffsetY,
                                         float diamondRightOffsetX, float diamondBottomOffsetY,
                                         float[] tint) {
        int verticesCount = 0;
        float pedestalTopSurfaceY = gridPlaneY; // Pedestal top is at the grid plane
        float pedestalBottomSurfaceY = gridPlaneY + BASE_THICKNESS; // Pedestal bottom extends down

        // Calculate corner points for the top edge of the pedestal sides
        float pTopLx = tileCenterX + diamondLeftOffsetX;  float pTopLy = pedestalTopSurfaceY + diamondSideOffsetY;
        float pTopRx = tileCenterX + diamondRightOffsetX; float pTopRy = pedestalTopSurfaceY + diamondSideOffsetY;
        float pTopBx = tileCenterX;                       float pTopBy = pedestalTopSurfaceY + diamondBottomOffsetY; // Bottom "tip" of the top diamond

        // Calculate corner points for the bottom edge of the pedestal sides
        float pBotLx = tileCenterX + diamondLeftOffsetX;  float pBotLy = pedestalBottomSurfaceY + diamondSideOffsetY;
        float pBotRx = tileCenterX + diamondRightOffsetX; float pBotRy = pedestalBottomSurfaceY + diamondSideOffsetY;
        float pBotBx = tileCenterX;                       float pBotBy = pedestalBottomSurfaceY + diamondBottomOffsetY;

        // UVs for DIRT texture (DEFAULT_SIDE)
        float u0 = DEFAULT_SIDE_U0; float v0 = DEFAULT_SIDE_V0;
        float u1 = DEFAULT_SIDE_U1; float vSpan = DEFAULT_SIDE_V1 - v0;

        // Calculate V-coordinate repetition for the pedestal height
        float vRepeats = (BASE_THICKNESS / TILE_THICKNESS) * SIDE_TEXTURE_DENSITY_FACTOR;
        float vBottomTextureCoordinate = v0 + vSpan * vRepeats;

        // Left pedestal side quad
        verticesCount += addRectangularQuadWithTextureOrColor(buffer,
                pTopLx, pTopLy,     // Top-Left
                pTopBx, pTopBy,     // Top-Right (diamond's bottom tip)
                pBotBx, pBotBy,     // Bottom-Right (diamond's bottom tip)
                pBotLx, pBotLy,     // Bottom-Left
                tint, true, u0, v0, u1, vBottomTextureCoordinate);

        // Right pedestal side quad
        verticesCount += addRectangularQuadWithTextureOrColor(buffer,
                pTopBx, pTopBy,     // Top-Left (diamond's bottom tip)
                pTopRx, pTopRy,     // Top-Right
                pBotRx, pBotRy,     // Bottom-Right
                pBotBx, pBotBy,     // Bottom-Left (diamond's bottom tip)
                tint, true, u0, v0, u1, vBottomTextureCoordinate);
        return verticesCount;
    }

    /**
     * Adds vertices for the top visible surface of a tile.
     */
    private int addTopSurfaceToBuffer(FloatBuffer buffer, Tile.TileType topSurfaceType, boolean isSelected,
                                      float topFaceCenterX, float topFaceCenterY, // Center Y of the actual top surface
                                      float dLeftX, float dSideY, float dRightX, float dTopY, float dBottomY,
                                      float[] actualTopColor, float[] whiteTintIfTextured) {
        int verticesCount = 0;
        // Diamond points for the top surface, calculated relative to its actual center Y
        float topLx = topFaceCenterX + dLeftX;   float topLy = topFaceCenterY + dSideY;
        float topRx = topFaceCenterX + dRightX;  float topRy = topFaceCenterY + dSideY;
        float topTx = topFaceCenterX;            float topTy = topFaceCenterY + dTopY;    // Top "tip"
        float topBx = topFaceCenterX;            float topBy = topFaceCenterY + dBottomY; // Bottom "tip"

        if (topSurfaceType == Tile.TileType.WATER) { // Water is typically just colored
            verticesCount += addRectangularQuadWithTextureOrColor(buffer,
                    topLx,topLy, topTx,topTy, topRx,topRy, topBx,topBy,
                    actualTopColor, // Use the calculated water color
                    false, DUMMY_U,DUMMY_V,DUMMY_U,DUMMY_V);
        } else {
            float u0=DUMMY_U, v0=DUMMY_V, u1=DUMMY_U, v1=DUMMY_V; // Default to dummy UVs
            boolean textureThisTopSurface = false;
            // If selected, color is already the selection tint.
            // If not selected and textured, use whiteTint. Otherwise, use actualTopColor.
            float[] colorToUse = isSelected ? actualTopColor : whiteTintIfTextured;

            switch (topSurfaceType) {
                case GRASS: u0=GRASS_ATLAS_U0;v0=GRASS_ATLAS_V0;u1=GRASS_ATLAS_U1;v1=GRASS_ATLAS_V1; textureThisTopSurface=true; break;
                case SAND:  u0=SAND_ATLAS_U0; v0=SAND_ATLAS_V0; u1=SAND_ATLAS_U1; v1=SAND_ATLAS_V1; textureThisTopSurface=true; break;
                case ROCK:  u0=ROCK_ATLAS_U0; v0=ROCK_ATLAS_V0; u1=ROCK_ATLAS_U1; v1=ROCK_ATLAS_V1; textureThisTopSurface=true; break;
                // SNOW top could be textured or colored. If colored, textureThisTopSurface remains false.
            }

            if (!textureThisTopSurface || isSelected) {
                // If not textured OR if it's selected (even if textured), use the tile's actual calculated top color (which includes selection tint).
                colorToUse = actualTopColor;
            }

            if (textureThisTopSurface) { // Diamond texturing requires splitting into two triangles
                float midU=(u0+u1)/2f; float midV=(v0+v1)/2f; // Center of the texture sub-region

                // Triangle 1: Left-Corner, Top-Tip, Bottom-Tip
                buffer.put(topLx).put(topLy).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(u0).put(midV);       // UV for Left
                buffer.put(topTx).put(topTy).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(midU).put(v0);      // UV for Top-Tip
                buffer.put(topBx).put(topBy).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(midU).put(v1);      // UV for Bottom-Tip
                verticesCount += 3;

                // Triangle 2: Top-Tip, Right-Corner, Bottom-Tip
                buffer.put(topTx).put(topTy).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(midU).put(v0);      // UV for Top-Tip
                buffer.put(topRx).put(topRy).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(u1).put(midV);      // UV for Right
                buffer.put(topBx).put(topBy).put(colorToUse[0]).put(colorToUse[1]).put(colorToUse[2]).put(colorToUse[3]).put(midU).put(v1);      // UV for Bottom-Tip
                verticesCount += 3;
            } else { // Color-only top (e.g., Water, Snow, or selected non-textured tiles)
                verticesCount += addRectangularQuadWithTextureOrColor(buffer,
                        topLx,topLy, topTx,topTy, topRx,topRy, topBx,topBy,
                        colorToUse, false,DUMMY_U,DUMMY_V,DUMMY_U,DUMMY_V);
            }
        }
        return verticesCount;
    }

    /**
     * Adds vertices for the elevated sides of a tile, with textures based on geological strata.
     */
    private int addStratifiedElevatedSidesToBuffer(FloatBuffer buffer, int tileTotalElevation,
                                                   float tileGridPlaneCenterX, float tileGridPlaneCenterY,
                                                   float dLeftX, float dSideY, float dRightX, float dBottomY, // Diamond offsets
                                                   float elevationSliceHeight, float[] tint) {
        int verticesCount = 0;
        // Loop for each elevation "slice" of the tile, from its highest point down to the grid plane.
        for (int currentElevationStep = tileTotalElevation; currentElevationStep >= 1; currentElevationStep--) {
            // Determine the material of the geological layer this slice rests ON.
            // The side texture of the current slice will be based on this underlying material.
            int underlyingLayerElevation = currentElevationStep - 1;
            Tile.TileType sideMaterialType = getMaterialTypeForElevationSlice(underlyingLayerElevation);

            float sideU0, sideV0, sideU1, sideTextureVSpanInAtlas;
            // Select UVs for the side texture based on the material of the layer below
            switch (sideMaterialType) {
                case GRASS: // If the layer below is GRASS-level, the side of the block above it is DIRT.
                    sideU0=DEFAULT_SIDE_U0; sideV0=DEFAULT_SIDE_V0; sideU1=DEFAULT_SIDE_U1; sideTextureVSpanInAtlas=DEFAULT_SIDE_V1-DEFAULT_SIDE_V0; break;
                case SAND:  // If the layer below is SAND-level, the side is SAND.
                    sideU0=SAND_ATLAS_U0; sideV0=SAND_ATLAS_V0; sideU1=SAND_ATLAS_U1; sideTextureVSpanInAtlas=SAND_ATLAS_V1-SAND_ATLAS_V0; break;
                case ROCK:
                    sideU0=ROCK_ATLAS_U0; sideV0=ROCK_ATLAS_V0; sideU1=ROCK_ATLAS_U1; sideTextureVSpanInAtlas=ROCK_ATLAS_V1-ROCK_ATLAS_V0; break;
                case SNOW: // Sides of blocks above snow layer are SNOW_SIDE (using rock as placeholder)
                    sideU0=SNOW_SIDE_ATLAS_U0; sideV0=SNOW_SIDE_ATLAS_V0; sideU1=SNOW_SIDE_ATLAS_U1; sideTextureVSpanInAtlas=SNOW_SIDE_ATLAS_V1-SNOW_SIDE_ATLAS_V0; break;
                case WATER: // Sides of blocks directly above water are DIRT (like a shoreline/beach cliff base)
                default:    // Default to DIRT for any other underlying layers
                    sideU0=DEFAULT_SIDE_U0; sideV0=DEFAULT_SIDE_V0; sideU1=DEFAULT_SIDE_U1; sideTextureVSpanInAtlas=DEFAULT_SIDE_V1-DEFAULT_SIDE_V0; break;
            }

            // V-coordinates for the side texture for this one slice (TILE_THICKNESS high)
            float vTopTextureCoordinate = sideV0; // Top of the atlas sub-texture for the selected side material
            // Bottom V-coordinate, scaled by density factor for repetition.
            float vBottomTextureCoordinate = sideV0 + sideTextureVSpanInAtlas * SIDE_TEXTURE_DENSITY_FACTOR;

            // Y-coordinate for the center of the diamond face at the TOP of this slice
            float sliceTopFaceCenterY = tileGridPlaneCenterY - (currentElevationStep * elevationSliceHeight);
            // Y-coordinate for the center of the diamond face at the BOTTOM of this slice
            float sliceBottomFaceCenterY = tileGridPlaneCenterY - ((currentElevationStep - 1) * elevationSliceHeight);

            // Calculate geometric vertices for the current slice's side faces
            // Top edge of the side slice
            float sliceTopLx = tileGridPlaneCenterX + dLeftX;  float sliceTopLy = sliceTopFaceCenterY + dSideY;
            float sliceTopRx = tileGridPlaneCenterX + dRightX; float sliceTopRy = sliceTopFaceCenterY + dSideY;
            float sliceTopBx = tileGridPlaneCenterX;           float sliceTopBy = sliceTopFaceCenterY + dBottomY; // Bottom "tip" of this slice's top diamond
            // Bottom edge of the side slice
            float sliceBotLx = tileGridPlaneCenterX + dLeftX;  float sliceBotLy = sliceBottomFaceCenterY + dSideY;
            float sliceBotRx = tileGridPlaneCenterX + dRightX; float sliceBotRy = sliceBottomFaceCenterY + dSideY;
            float sliceBotBx = tileGridPlaneCenterX;           float sliceBotBy = sliceBottomFaceCenterY + dBottomY; // Bottom "tip" of this slice's bottom diamond

            // Add left side quad for this slice
            verticesCount += addRectangularQuadWithTextureOrColor(buffer,
                    sliceTopLx, sliceTopLy,     // Top-Left vertex of side face
                    sliceTopBx, sliceTopBy,     // Top-Right vertex of side face (using bottom "tip" of top diamond)
                    sliceBotBx, sliceBotBy,     // Bottom-Right vertex of side face (using bottom "tip" of bottom diamond)
                    sliceBotLx, sliceBotLy,     // Bottom-Left vertex of side face
                    tint, true, sideU0, vTopTextureCoordinate, sideU1, vBottomTextureCoordinate);

            // Add right side quad for this slice
            verticesCount += addRectangularQuadWithTextureOrColor(buffer,
                    sliceTopBx, sliceTopBy,     // Top-Left vertex of side face (using bottom "tip" of top diamond)
                    sliceTopRx, sliceTopRy,     // Top-Right vertex of side face
                    sliceBotRx, sliceBotRy,     // Bottom-Right vertex of side face
                    sliceBotBx, sliceBotBy,     // Bottom-Left vertex of side face (using bottom "tip" of bottom diamond)
                    tint, true, sideU0, vTopTextureCoordinate, sideU1, vBottomTextureCoordinate);
        }
        return verticesCount;
    }

    /**
     * Updates the chunk's bounding box based on the extents of the tile being added.
     */
    private void updateChunkBounds(float[] chunkBoundsMinMax,
                                   float tileGridPlaneCenterX, float tileGridPlaneCenterY,
                                   int currentTileElevation, float elevationSliceHeight,
                                   float dLeftX, float dRightX, float dTopY, float dBottomY) {
        // Calculate the tile's overall min/max X based on its width
        float minX = tileGridPlaneCenterX + dLeftX;
        float maxX = tileGridPlaneCenterX + dRightX;

        // Calculate the tile's min Y (highest point of its top surface)
        float minY = tileGridPlaneCenterY - (currentTileElevation * elevationSliceHeight) + dTopY;
        // Calculate the tile's max Y (lowest point of its pedestal)
        float maxY = tileGridPlaneCenterY + BASE_THICKNESS + dBottomY;

        chunkBoundsMinMax[0] = Math.min(chunkBoundsMinMax[0], minX);
        chunkBoundsMinMax[1] = Math.min(chunkBoundsMinMax[1], minY);
        chunkBoundsMinMax[2] = Math.max(chunkBoundsMinMax[2], maxX);
        chunkBoundsMinMax[3] = Math.max(chunkBoundsMinMax[3], maxY);
    }

    public int addGrassVerticesForTile_WorldSpace_ForChunk(int r,int c,Tile t,FloatBuffer b,float[] bounds){return 0;} // Stub

    private int addRectangularQuadWithTextureOrColor(FloatBuffer buffer,
                                                     float xTL, float yTL, float xTR, float yTR,
                                                     float xBR, float yBR, float xBL, float yBL,
                                                     float[] color,
                                                     boolean isTextured, float u0, float v0, float u1, float v1_tex) {
        float actual_u0, actual_v0, actual_u1_coord, actual_v1_coord;
        final float DUMMY_U_FOR_SHADER = 0.0f; // Renamed to avoid conflict if DUMMY_U is a field
        final float DUMMY_V_FOR_SHADER = 0.0f;

        if (isTextured) {
            actual_u0 = u0; actual_v0 = v0;
            actual_u1_coord = u1; actual_v1_coord = v1_tex; // v1_tex can be > v0 + span for repetition
        } else {
            actual_u0 = DUMMY_U_FOR_SHADER; actual_v0 = DUMMY_V_FOR_SHADER;
            actual_u1_coord = DUMMY_U_FOR_SHADER; actual_v1_coord = DUMMY_V_FOR_SHADER;
        }
        // Triangle 1: Top-Left, Bottom-Left, Top-Right
        buffer.put(xTL).put(yTL).put(color[0]).put(color[1]).put(color[2]).put(color[3]).put(actual_u0).put(actual_v0);
        buffer.put(xBL).put(yBL).put(color[0]).put(color[1]).put(color[2]).put(color[3]).put(actual_u0).put(actual_v1_coord);
        buffer.put(xTR).put(yTR).put(color[0]).put(color[1]).put(color[2]).put(color[3]).put(actual_u1_coord).put(actual_v0);
        // Triangle 2: Top-Right, Bottom-Left, Bottom-Right
        buffer.put(xTR).put(yTR).put(color[0]).put(color[1]).put(color[2]).put(color[3]).put(actual_u1_coord).put(actual_v0);
        buffer.put(xBL).put(yBL).put(color[0]).put(color[1]).put(color[2]).put(color[3]).put(actual_u0).put(actual_v1_coord);
        buffer.put(xBR).put(yBR).put(color[0]).put(color[1]).put(color[2]).put(color[3]).put(actual_u1_coord).put(actual_v1_coord);
        return 6; // 6 vertices for two triangles forming a quad
    }

    private void collectSortableItems(){
        sortableItems.clear();
        if(player!=null && camera!=null && map!=null) {
            sortableItems.add(new SortableItem(player,camera,map));
        }
        if(map==null || camera==null) return;

        int mapWidth = map.getWidth();
        int mapHeight = map.getHeight();
        for(int r=0; r<mapHeight; r++) {
            for(int c=0; c<mapWidth; c++) {
                Tile tile = map.getTile(r,c);
                if(tile!=null && tile.getTreeType()!=Tile.TreeVisualType.NONE && tile.getType()!=Tile.TileType.WATER){
                    int elevation = tile.getElevation();
                    int[] screenCoords = camera.mapToScreenCoordsForPicking((float)c, (float)r, elevation);
                    sortableItems.add(new SortableItem(new TreeData(tile.getTreeType(),(float)c,(float)r,elevation,screenCoords[0],screenCoords[1]),camera,map));
                }
            }
        }
    }

    private int addPlayerVerticesToBuffer_ScreenSpace(PlayerModel p, FloatBuffer buffer) {
        if (playerTexture==null||playerTexture.getId()==0||camera==null||map==null){
            if(playerTexture==null) System.err.println("Renderer: addPlayerVertices - playerTexture is null!");
            else if(playerTexture.getId()==0) System.err.println("Renderer: addPlayerVertices - playerTexture ID is 0!");
            return 0;
        }
        float r=1f,g=1f,b=1f,a=1f;
        Tile currentTile = map.getTile(p.getTileRow(),p.getTileCol());
        int playerElevationOnTile = (currentTile!=null) ? currentTile.getElevation() : 0;
        int[] screenCoords = camera.mapToScreenCoordsForPicking(p.getMapCol(),p.getMapRow(),playerElevationOnTile);
        float screenX = screenCoords[0]; float screenY = screenCoords[1];
        float currentZoom = camera.getZoom();
        float spritePixelWidth = PlayerModel.FRAME_WIDTH; float spritePixelHeight = PlayerModel.FRAME_HEIGHT;
        float scaledSpriteWidth = spritePixelWidth*currentZoom; float scaledSpriteHeight = spritePixelHeight*currentZoom;
        float drawX = screenX - scaledSpriteWidth/2f;
        float drawY = screenY - scaledSpriteHeight; // Anchor at feet
        if(p.isLevitating()) {
            drawY-=(Math.sin(p.getLevitateTimer())*8*currentZoom);
        }
        int animFrameColumn = p.getVisualFrameIndex(); int animFrameRow = p.getAnimationRow();

        if(playerTexture.getWidth()==0||playerTexture.getHeight()==0){System.err.println("Renderer: addPlayerVertices - playerTexture dimensions are zero!");return 0;}
        float texU0 = (animFrameColumn*spritePixelWidth)/playerTexture.getWidth();
        float texV0 = (animFrameRow*spritePixelHeight)/playerTexture.getHeight();
        float texU1 = ((animFrameColumn+1)*spritePixelWidth)/playerTexture.getWidth();
        float texV1 = ((animFrameRow+1)*spritePixelHeight)/playerTexture.getHeight();
        return addRectangularQuadWithTextureOrColor(buffer,drawX,drawY,drawX+scaledSpriteWidth,drawY,drawX+scaledSpriteWidth,drawY+scaledSpriteHeight,drawX,drawY+scaledSpriteHeight,new float[]{r,g,b,a},true,texU0,texV0,texU1,texV1);
    }

    private int addTreeVerticesToBuffer_ScreenSpace(TreeData tree, FloatBuffer buffer) {
        if (treeTexture==null||treeTexture.getId()==0||tree.treeVisualType==Tile.TreeVisualType.NONE||camera==null){
            if(treeTexture==null)System.err.println("Renderer: addTreeVertices - treeTexture is null!");
            else if(treeTexture.getId()==0)System.err.println("Renderer: addTreeVertices - treeTexture ID is 0!");
            return 0;
        }
        float r=1f,g=1f,b=1f,a=1f;
        float frameW=0,frameH=0,atlasU0=0,atlasV0=0,anchorOffsetYPixels=0;

        // IMPORTANT: Update this switch with your correct tree sprite definitions
        // from your tree atlas (e.g., fruit-trees.png or trees_atlas.png)
        switch(tree.treeVisualType){
            case APPLE_TREE_FRUITING: frameW=90;frameH=130;atlasU0=0;atlasV0=0;anchorOffsetYPixels=15;break;
            case PINE_TREE_SMALL:     frameW=90;frameH=180;atlasU0=90;atlasV0=0;anchorOffsetYPixels=10;break;
            // case PALM_TREE: // Example: Add your PALM_TREE definition from your working version
            //    frameW = 64; frameH = 100; atlasU0 = 180; atlasV0 = 0; anchorOffsetYPixels = 10;
            //    break;
            default: System.err.println("Renderer: Unknown tree type in addTreeVerticesToBuffer_ScreenSpace: " + tree.treeVisualType); return 0;
        }

        if(treeTexture.getWidth()==0||treeTexture.getHeight()==0){System.err.println("Renderer: addTreeVertices - treeTexture dimensions are zero!");return 0;}
        float texU0 = atlasU0/treeTexture.getWidth();
        float texV0 = atlasV0/treeTexture.getHeight();
        float texU1 = (atlasU0+frameW)/treeTexture.getWidth();
        float texV1 = (atlasV0+frameH)/treeTexture.getHeight();
        float currentZoom = camera.getZoom();
        float scaledSpriteWidth = frameW*currentZoom; float scaledSpriteHeight = frameH*currentZoom;
        float scaledAnchorOffsetY = anchorOffsetYPixels*currentZoom;
        float drawX = tree.topDiamondCenterX_screen - scaledSpriteWidth/2f;
        float drawY = tree.topDiamondCenterY_screen - (scaledSpriteHeight-scaledAnchorOffsetY);
        return addRectangularQuadWithTextureOrColor(buffer,drawX,drawY,drawX+scaledSpriteWidth,drawY,drawX+scaledSpriteWidth,drawY+scaledSpriteHeight,drawX,drawY+scaledSpriteHeight,new float[]{r,g,b,a},true,texU0,texV0,texU1,texV1);
    }

    public void render() {
        frameCount++;
        // Z-ORDERING BUG COMMENT:
        // The current rendering order (all chunks, then all sorted sprites) can cause
        // sprites (like the player) to appear in front of terrain tiles they should be behind.
        // Proper Fix:
        // 1. Enable Depth Testing: In your OpenGL initialization (e.g., Game.java or Main.java):
        //    glEnable(GL_DEPTH_TEST);
        //    glDepthFunc(GL_LEQUAL); // or GL_LESS
        // 2. Clear Depth Buffer: In Game.java's renderGame() method, before any rendering:
        //    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        // 3. Meaningful Z in Vertex Shader: Your vertex.glsl needs to output a Z value for gl_Position
        //    that reflects depth. For isometric, this could be based on world Y, or (mapRow + mapCol),
        //    and adjusted by elevation. This Z must be consistent for tiles and sprites.
        //    Example: gl_Position = uProjectionMatrix * uModelViewMatrix * vec4(aPos.x, aPos.y, calculatedZ, 1.0);
        // 4. 3D Projection: The uProjectionMatrix used for the world (map chunks & world-space sprites)
        //    must be a 3D orthographic or perspective matrix that defines a depth range (near/far planes).
        //    The camera.getViewMatrix() provides the model-view part.
        // Without these, painter's algorithm sorting is the best effort but has limitations.

        defaultShader.bind();
        // Set uProjectionMatrix for screen-space elements (UI, sprites).
        // For map chunks, uModelViewMatrix (from camera.getViewMatrix()) includes the isometric projection.
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);

        // --- 1. Render Map Chunks ---
        defaultShader.setUniform("uModelViewMatrix", camera.getViewMatrix());
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uApplySubTextureRepeat", 0); // Rely on GL_REPEAT & Java V-coords for tile sides
        defaultShader.setUniform("uSubTextureV0", DEFAULT_SIDE_V0); // Default, not actively used if uApplySubTextureRepeat is 0
        defaultShader.setUniform("uSubTextureVSpan", DEFAULT_SIDE_V1 - DEFAULT_SIDE_V0);

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
        glBindVertexArray(0); // Unbind last chunk's VAO
        if (tileAtlasTexture != null && tileAtlasTexture.getId() != 0) {
            glBindTexture(GL_TEXTURE_2D, 0); // Unbind tile atlas
        }

        // --- 2. Render Sprites (Player, Trees) ---
        collectSortableItems();
        // Sort sprites by screen Y, then by map depth, then by zOrder (player on top of trees at same pos)
        Collections.sort(sortableItems, (item1, item2) -> {
            // Primary sort: Screen Y
            if (Math.abs(item1.screenYSortKey - item2.screenYSortKey) > 0.1f) {
                return Float.compare(item1.screenYSortKey, item2.screenYSortKey);
            }
            // Secondary sort: Map depth (row + col)
            float depth1 = item1.mapRow + item1.mapCol;
            float depth2 = item2.mapRow + item2.mapCol;
            if (Math.abs(depth1 - depth2) > 0.01f) {
                return Float.compare(depth1, depth2);
            }
            // Tertiary sort: Elevation
            // CORRECTED: item1 and item2 used instead of i1 and i2
            int elev1 = (item1.entity instanceof PlayerModel) ?
                    (map.getTile(((PlayerModel)item1.entity).getTileRow(), ((PlayerModel)item1.entity).getTileCol()) != null ?
                            map.getTile(((PlayerModel)item1.entity).getTileRow(), ((PlayerModel)item1.entity).getTileCol()).getElevation() : 0)
                    : ((TreeData)item1.entity).elevation;
            int elev2 = (item2.entity instanceof PlayerModel) ?
                    (map.getTile(((PlayerModel)item2.entity).getTileRow(), ((PlayerModel)item2.entity).getTileCol()) != null ?
                            map.getTile(((PlayerModel)item2.entity).getTileRow(), ((PlayerModel)item2.entity).getTileCol()).getElevation() : 0)
                    : ((TreeData)item2.entity).elevation;
            if (elev1 != elev2) {
                return Integer.compare(elev1, elev2);
            }
            // Quaternary sort: Z-Order (player over tree)
            return Integer.compare(item1.zOrder, item2.zOrder);
        });

        defaultShader.setUniform("uModelViewMatrix", modelViewMatrixForSprites); // Switch to identity matrix for screen-space sprites
        defaultShader.setUniform("uIsFont", 0);
        defaultShader.setUniform("uApplySubTextureRepeat", 0); // Sprites don't use this

        if (spriteVaoId != 0 && spriteVboId != 0) {
            glBindVertexArray(spriteVaoId);
            glBindBuffer(GL_ARRAY_BUFFER, spriteVboId);
            defaultShader.setUniform("uHasTexture", 1);
            defaultShader.setUniform("uTextureSampler", 0); // Sprites use texture unit 0
            glActiveTexture(GL_TEXTURE0);

            for (SortableItem item : sortableItems) {
                spriteVertexBuffer.clear();
                int verticesInSprite = 0;
                Texture textureForSprite = null;
                if (item.entity instanceof PlayerModel) {
                    PlayerModel p = (PlayerModel)item.entity;
                    if(playerTexture!=null && playerTexture.getId()!=0){
                        verticesInSprite = addPlayerVerticesToBuffer_ScreenSpace(p, spriteVertexBuffer);
                        textureForSprite = playerTexture;
                    }
                } else if (item.entity instanceof TreeData) {
                    TreeData td = (TreeData)item.entity;
                    if(treeTexture!=null && treeTexture.getId()!=0){
                        verticesInSprite = addTreeVerticesToBuffer_ScreenSpace(td, spriteVertexBuffer);
                        textureForSprite = treeTexture;
                    }
                }

                if (verticesInSprite > 0 && textureForSprite != null) {
                    spriteVertexBuffer.flip();
                    textureForSprite.bind(); // Bind the correct texture for this sprite
                    glBufferSubData(GL_ARRAY_BUFFER, 0, spriteVertexBuffer); // Upload this sprite's data
                    glDrawArrays(GL_TRIANGLES, 0, verticesInSprite);
                }
            }
            glBindTexture(GL_TEXTURE_2D, 0); // Unbind last sprite texture
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }

        // --- 3. Render UI ---
        if (uiFont != null && uiFont.isInitialized()) {
            // uProjectionMatrix is already set for screen-space.
            // uModelViewMatrix is already set for screen-space (modelViewMatrixForSprites).
            renderUI(); // This will bind font texture and set uIsFont=1 internally
        }
        defaultShader.unbind();
    }

    private void renderUI() {
        if (uiFont == null || !uiFont.isInitialized() || player == null || camera == null || inputHandler == null || map == null) return;
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); // For font transparency
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
    }

    public void cleanup() {
        System.out.println("Renderer: Starting cleanup...");
        if(playerTexture!=null)playerTexture.delete();
        if(treeTexture!=null)treeTexture.delete();
        if(tileAtlasTexture!=null)tileAtlasTexture.delete();
        if(uiFont!=null)uiFont.cleanup();
        if(defaultShader!=null)defaultShader.cleanup();
        if(mapChunks!=null){for(Chunk ch:mapChunks)ch.cleanup();mapChunks.clear();System.out.println("Renderer: Map chunks cleaned up.");}
        if(spriteVaoId!=0){glDeleteVertexArrays(spriteVaoId);spriteVaoId=0;}
        if(spriteVboId!=0){glDeleteBuffers(spriteVboId);spriteVboId=0;}
        if(spriteVertexBuffer!=null){MemoryUtil.memFree(spriteVertexBuffer);spriteVertexBuffer=null;}
        System.out.println("Renderer: Sprite resources cleaned up.");
        System.out.println("Renderer: Cleanup complete.");
    }

    private void checkError(String stage) { // Kept for debugging, can be called after GL operations
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
        }
    }
}
