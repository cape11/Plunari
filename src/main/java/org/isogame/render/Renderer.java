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

    // --- FIELDS FOR CHUNKING ---
    private List<Chunk> mapChunks;
    private static final int CHUNK_SIZE_TILES = 32;
    // --- END OF CHUNKING FIELDS ---

    private Shader defaultShader;
    private Matrix4f projectionMatrix;
    private Matrix4f modelViewMatrixForSprites;

    private int spriteVaoId = 0;
    private int spriteVboId = 0;
    private FloatBuffer spriteVertexBuffer;

    public static final int FLOATS_PER_VERTEX_TEXTURED = 8;
    public static final int FLOATS_PER_VERTEX_COLORED = 6;

    private static class SortableItem {
        float screenYSortKey; int zOrder; Object entity; float mapRow, mapCol;
        public SortableItem(PlayerModel p, CameraManager cam, Map m) { this.entity = p; this.zOrder = 1; this.mapRow = p.getMapRow(); this.mapCol = p.getMapCol(); Tile t = m.getTile(p.getTileRow(),p.getTileCol()); int elev = (t!=null)?t.getElevation():0; int[] sc = cam.mapToScreenCoordsForPicking(p.getMapCol(),p.getMapRow(),elev); this.screenYSortKey = sc[1]; }
        public SortableItem(TreeData tree, CameraManager cam, Map m) { this.entity = tree; this.zOrder = 0; this.mapRow = tree.mapRow; this.mapCol = tree.mapCol; int[] sc = cam.mapToScreenCoordsForPicking(tree.mapCol,tree.mapRow,tree.elevation); this.screenYSortKey = sc[1]; }
    }
    private static class TreeData {
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
        checkError("Renderer Constructor - After loadAssets()"); // Check immediately after loadAssets
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
        checkError("Renderer: loadAssets - After Player Texture Load"); // ADDED CHECK

        String treeTexturePath = "/org/isogame/render/textures/fruit-trees.png";
        this.treeTexture = Texture.loadTexture(treeTexturePath);
        if (this.treeTexture == null) System.err.println("Renderer CRITICAL: Tree texture FAILED: " + treeTexturePath);
        else System.out.println("Renderer: Tree texture loaded: " + treeTexturePath + " ID: " + treeTexture.getId());
        checkError("Renderer: loadAssets - After Tree Texture Load"); // ADDED CHECK

        String fontPath = "/org/isogame/render/fonts/PressStart2P-Regular.ttf";
        try {
            System.out.println("Renderer: Attempting to load font: " + fontPath);
            this.uiFont = new Font(fontPath, 16f, this); // Font constructor has its own extensive logging
            if (this.uiFont.isInitialized()) {
                System.out.println("Renderer: Font asset loaded and initialized: " + fontPath + " (Font Tex ID: " + uiFont.getTextureID() + ")");
            } else {
                // Font constructor should throw if critical failure, but this is a fallback log
                System.err.println("Renderer WARNING: Font object created by constructor, but uiFont.isInitialized() is false for: " + fontPath + ". Font will not render.");
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Renderer CRITICAL: Failed to load UI font (Exception caught in Renderer): " + fontPath + " - " + e.getMessage());
            e.printStackTrace(); // Print stack trace from here as well
            this.uiFont = null; // Ensure uiFont is null if constructor failed
        }
        checkError("Renderer: loadAssets - After Font Load Attempt"); // ADDED CHECK
        System.out.println("Renderer: loadAssets method finished."); // New log
    }

    private void initShaders() {
        try {
            defaultShader = new Shader();
            defaultShader.createVertexShader(Shader.loadResource("/org/isogame/render/shaders/vertex.glsl"));
            defaultShader.createFragmentShader(Shader.loadResource("/org/isogame/render/shaders/fragment.glsl"));
            defaultShader.link();
            checkError("Renderer: initShaders - After Shader Link");

            defaultShader.createUniform("uProjectionMatrix");
            defaultShader.createUniform("uModelViewMatrix");
            defaultShader.createUniform("uTextureSampler");
            defaultShader.createUniform("uHasTexture");
            defaultShader.createUniform("uIsFont"); // Ensure this is created

        } catch (IOException e) {
            System.err.println("Renderer: Error initializing shaders: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Renderer: Failed to initialize shaders", e);
        }
        System.out.println("Renderer: Shaders initialized.");
        // Removed checkError from here, as it's at the end of the try block or in the constructor call
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
        // Allocate a reasonable size, e.g., for a few complex sprites or many simple ones.
        // 20 sprites * 6 vertices/sprite * FLOATS_PER_VERTEX_TEXTURED
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
        // Removed checkError from here, as it's in the constructor call
    }

    public void uploadTileMapGeometry() {
        System.out.println("Renderer: Orchestrating upload of STATIC tile map geometry for ALL CHUNKS (WORLD coordinates)...");
        if (mapChunks == null || mapChunks.isEmpty()) {
            System.err.println("Renderer: mapChunks list is null or empty in uploadTileMapGeometry! Check initRenderObjects().");
            return;
        }
        for (Chunk chunk : mapChunks) {
            chunk.uploadGeometry(map, inputHandler, this); // 'this' is the Renderer instance
        }
        System.out.println("Renderer: All chunk geometries processed by their respective chunks.");
    }

    // <-- NEW METHOD to update a specific chunk -->
    public void updateChunkContainingTile(int tileRow, int tileCol) {
        if (map == null || mapChunks == null || mapChunks.isEmpty()) {
            System.err.println("Renderer: Cannot update chunk, map or chunks not initialized.");
            return;
        }

        // Calculate which chunk the tile belongs to
        // CHUNK_SIZE_TILES is a static final in Constants, but also defined as a private static final in Renderer.
        // Ensure you're using the correct one consistently. Let's assume it's accessible.
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
            System.out.println("Renderer: Updating geometry for chunk: (" + chunkGridX + ", " + chunkGridY + ") containing tile (" + tileRow + ", " + tileCol + ")");
            // Re-upload geometry for this specific chunk.
            // The existing chunk.uploadGeometry method rebuilds the VBO based on the current map state.
            targetChunk.uploadGeometry(this.map, this.inputHandler, this); // Pass necessary arguments
        } else {
            System.err.println("Renderer: Could not find chunk for tile: (" + tileRow + ", " + tileCol + ") at chunkCoords (" + chunkGridX + ", " + chunkGridY + ")");
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

    public void render() {
        frameCount++;
        defaultShader.bind();
        defaultShader.setUniform("uProjectionMatrix", projectionMatrix);

        // --- 1. Draw static tile map CHUNKS ---
        defaultShader.setUniform("uModelViewMatrix", camera.getViewMatrix());
        defaultShader.setUniform("uHasTexture", 0);
        defaultShader.setUniform("uIsFont", 0); // Tiles are not fonts

        int chunksRendered = 0;
        if (mapChunks != null) {
            for (Chunk chunk : mapChunks) {
                if (camera.isChunkVisible(chunk.getBoundingBox())) { // Re-enabled culling
                    chunk.render();
                    chunksRendered++;
                }
            }
        }
        glBindVertexArray(0);
        if (frameCount % 300 == 0 && mapChunks != null && !mapChunks.isEmpty()) { // Added !mapChunks.isEmpty()
            System.out.println("Rendered " + chunksRendered + "/" + mapChunks.size() + " chunks.");
        }
        checkError("Renderer: render - after map chunks");

        // --- 2. Collect and sort dynamic sprites ---
        collectSortableItems();
        Collections.sort(sortableItems, (item1, item2) -> {
            if (Math.abs(item1.screenYSortKey - item2.screenYSortKey) > 0.1f) return Float.compare(item1.screenYSortKey, item2.screenYSortKey);
            float d1 = item1.mapRow+item1.mapCol, d2 = item2.mapRow+item2.mapCol; if(Math.abs(d1-d2)>0.01f) return Float.compare(d1,d2);
            int elev1 = (item1.entity instanceof PlayerModel) ? (map.getTile(((PlayerModel)item1.entity).getTileRow(), ((PlayerModel)item1.entity).getTileCol()) != null ? map.getTile(((PlayerModel)item1.entity).getTileRow(), ((PlayerModel)item1.entity).getTileCol()).getElevation() : 0) : ((TreeData)item1.entity).elevation;
            int elev2 = (item2.entity instanceof PlayerModel) ? (map.getTile(((PlayerModel)item2.entity).getTileRow(), ((PlayerModel)item2.entity).getTileCol()) != null ? map.getTile(((PlayerModel)item2.entity).getTileRow(), ((PlayerModel)item2.entity).getTileCol()).getElevation() : 0) : ((TreeData)item2.entity).elevation;
            if (elev1 != elev2) return Integer.compare(elev1, elev2);
            return Integer.compare(item1.zOrder, item2.zOrder);
        });

        // --- 3. Draw dynamic sprites (Player, Trees) ---
        defaultShader.setUniform("uModelViewMatrix", modelViewMatrixForSprites);
        if (spriteVaoId != 0) {
            glBindVertexArray(spriteVaoId);
            defaultShader.setUniform("uHasTexture", 1);
            defaultShader.setUniform("uIsFont", 0); // Sprites are NOT fonts
            defaultShader.setUniform("uTextureSampler", 0);
            glActiveTexture(GL_TEXTURE0);
            for (SortableItem item : sortableItems) {
                spriteVertexBuffer.clear(); int currentSpriteVertices = 0; Texture currentTexture = null;
                if (item.entity instanceof PlayerModel) { PlayerModel p = (PlayerModel)item.entity; if (playerTexture != null && playerTexture.getId() != 0) { currentSpriteVertices = addPlayerVerticesToBuffer_ScreenSpace(p, spriteVertexBuffer); currentTexture = playerTexture; }
                } else if (item.entity instanceof TreeData) { TreeData td = (TreeData)item.entity; if (treeTexture != null && treeTexture.getId() != 0) { currentSpriteVertices = addTreeVerticesToBuffer_ScreenSpace(td, spriteVertexBuffer); currentTexture = treeTexture; } }

                if (currentSpriteVertices > 0 && currentTexture != null) {
                    if (spriteVertexBuffer.position() < currentSpriteVertices * FLOATS_PER_VERTEX_TEXTURED) {
                        // This check is a bit off, flip should happen after all puts for currentSpriteVertices
                    }
                    spriteVertexBuffer.flip();
                    currentTexture.bind();
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

        // --- 4. Render UI ---
        if (uiFont != null && uiFont.isInitialized()) {
            renderUI(); // This method will set uIsFont to 1
        }
        defaultShader.unbind();
    }

    private void collectSortableItems() {
        sortableItems.clear();
        if (this.player != null) {
            sortableItems.add(new SortableItem(this.player, this.camera, this.map));
        }
        if (map == null) return; // Guard against null map
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

    public int addSingleTileVerticesToBuffer_WorldSpace_ForChunk(
            int tileR, int tileC, Tile tile, boolean isSelected, FloatBuffer buffer, float[] chunkBoundsMinMax) {
        int elevation = tile.getElevation();
        Tile.TileType type = tile.getType();

        float halfW_unit = TILE_WIDTH / 2.0f;
        float halfH_unit = TILE_HEIGHT / 2.0f;
        float baseThick_worldY_offset = BASE_THICKNESS;
        float elevThick_worldY_offset = TILE_THICKNESS;
        float world_gc_x = (tileC - tileR) * halfW_unit;
        float world_gc_y_plane = (tileC + tileR) * halfH_unit;
        float world_tc_y = world_gc_y_plane - (elevation * elevThick_worldY_offset);

        float currentTileMinX = Float.MAX_VALUE, currentTileMinY = Float.MAX_VALUE;
        float currentTileMaxX = Float.MIN_VALUE, currentTileMaxY = Float.MIN_VALUE;

        float[] topClr, s1Clr, s2Clr, bTopClr, bS1Clr, bS2Clr;
        boolean isW = (type == Tile.TileType.WATER);

        if (isSelected) {
            topClr = new float[]{1.0f, 0.8f, 0.0f, 0.8f};
            s1Clr = new float[]{0.9f, 0.7f, 0.0f, 0.8f};
            s2Clr = new float[]{0.8f, 0.6f, 0.0f, 0.8f};
            bTopClr = new float[]{0.5f, 0.4f, 0.0f, 0.8f};
            bS1Clr = new float[]{0.4f, 0.3f, 0.0f, 0.8f};
            bS2Clr = new float[]{0.3f, 0.2f, 0.0f, 0.8f};
        } else {
            switch (type) {
                case WATER:
                    final double WATER_TIME_SCALE_FAST = 0.06;
                    final double WATER_TIME_SCALE_SLOW = 0.025;
                    final long WATER_TIME_PERIOD = 3600;
                    final double WATER_SPATIAL_SCALE_FINE = 0.35;
                    final double WATER_SPATIAL_SCALE_BROAD = 0.15;
                    final int WATER_SPATIAL_PERIOD = 128;
                    final float BASE_BLUE_R = 0.05f;
                    final float BASE_BLUE_G = 0.25f;
                    final float BASE_BLUE_B = 0.5f;
                    final float WAVE_HIGHLIGHT_FACTOR = 0.15f;
                    final float WAVE_SHADOW_FACTOR = 0.1f;
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

                    float r = BASE_BLUE_R;
                    float g = BASE_BLUE_G;
                    float b = BASE_BLUE_B;

                    if (combinedWaveEffect > 0) {
                        float highlight = (float) (combinedWaveEffect * WAVE_HIGHLIGHT_FACTOR);
                        r += highlight * 0.3f; g += highlight * 0.7f; b += highlight;
                    } else {
                        float shadow = (float) (-combinedWaveEffect * WAVE_SHADOW_FACTOR);
                        r -= shadow * 0.5f; g -= shadow * 0.5f; b -= shadow;
                    }

                    r = Math.max(0.0f, Math.min(1.0f, r));
                    g = Math.max(0.0f, Math.min(1.0f, g));
                    b = Math.max(0.0f, Math.min(1.0f, b));

                    topClr = new float[]{r, g, b, WATER_ALPHA};
                    s1Clr = topClr; s2Clr = topClr; bTopClr = topClr;
                    bS1Clr = SUBMERGED_SIDE_COLOR_1; bS2Clr = SUBMERGED_SIDE_COLOR_2;
                    break;
                case SAND:
                    topClr = new float[]{0.82f, 0.7f, 0.55f, 1f}; s1Clr = new float[]{0.75f, 0.65f, 0.49f, 1f};
                    s2Clr = new float[]{0.67f, 0.59f, 0.43f, 1f}; bTopClr = new float[]{0.59f, 0.51f, 0.35f, 1f};
                    bS1Clr = new float[]{0.51f, 0.43f, 0.27f, 1f}; bS2Clr = new float[]{0.43f, 0.35f, 0.19f, 1f};
                    break;
                case GRASS:
                    topClr = new float[]{0.20f, 0.45f, 0.10f, 1f}; s1Clr = new float[]{0.18f, 0.40f, 0.09f, 1f};
                    s2Clr = new float[]{0.16f, 0.35f, 0.08f, 1f}; bTopClr = new float[]{0.35f, 0.28f, 0.18f, 1f};
                    bS1Clr = new float[]{0.30f, 0.23f, 0.15f, 1f}; bS2Clr = new float[]{0.25f, 0.18f, 0.12f, 1f};
                    break;
                case ROCK:
                    topClr = new float[]{0.45f, 0.45f, 0.45f, 1f}; s1Clr = new float[]{0.40f, 0.40f, 0.40f, 1f};
                    s2Clr = new float[]{0.35f, 0.35f, 0.35f, 1f}; bTopClr = new float[]{0.30f, 0.30f, 0.30f, 1f};
                    bS1Clr = new float[]{0.25f, 0.25f, 0.25f, 1f}; bS2Clr = new float[]{0.20f, 0.20f, 0.20f, 1f};
                    break;
                case SNOW:
                    topClr = new float[]{0.95f, 0.95f, 1.0f, 1f}; s1Clr = new float[]{0.90f, 0.90f, 0.95f, 1f};
                    s2Clr = new float[]{0.85f, 0.85f, 0.90f, 1f}; bTopClr = new float[]{0.5f, 0.5f, 0.55f, 1f};
                    bS1Clr = new float[]{0.45f, 0.45f, 0.50f, 1f}; bS2Clr = new float[]{0.40f, 0.40f, 0.45f, 1f};
                    break;
                default:
                    topClr = new float[]{1f, 0f, 1f, 1f}; s1Clr = topClr; s2Clr = topClr; bTopClr = topClr;
                    bS1Clr = topClr; bS2Clr = topClr;
                    break;
            }
        }

        float d_top_y_rel = -halfH_unit; float d_left_x_rel = -halfW_unit; float d_left_y_rel = 0;
        float d_right_x_rel = halfW_unit; float d_right_y_rel = 0; float d_bottom_y_rel = halfH_unit;
        float bTx = world_gc_x, bTy = world_gc_y_plane + d_top_y_rel;
        float bLx = world_gc_x + d_left_x_rel, bLy = world_gc_y_plane + d_left_y_rel;
        float bRx = world_gc_x + d_right_x_rel, bRy = world_gc_y_plane + d_right_y_rel;
        float bBx = world_gc_x, bBy = world_gc_y_plane + d_bottom_y_rel;
        float vbLx = bLx, vbLy_ = bLy + baseThick_worldY_offset;
        float vbRx = bRx, vbRy_ = bRy + baseThick_worldY_offset;
        float vbBx = bBx, vbBy_ = bBy + baseThick_worldY_offset;

        int verticesAdded=0;
        verticesAdded+=putColoredQuadVerticesAsTriangles(buffer,bLx,bLy,bBx,bBy,vbBx,vbBy_,vbLx,vbLy_,bS1Clr);
        verticesAdded+=putColoredQuadVerticesAsTriangles(buffer,bBx,bBy,bRx,bRy,vbRx,vbRy_,vbBx,vbBy_,bS2Clr);
        verticesAdded+=putColoredQuadVerticesAsTriangles(buffer,bLx,bLy,bTx,bTy,bRx,bRy,bBx,bBy,(isW?topClr:bTopClr));

        currentTileMinX = Math.min(currentTileMinX, Math.min(bLx, vbLx));
        currentTileMaxX = Math.max(currentTileMaxX, Math.max(bRx, vbRx));
        currentTileMinY = Math.min(currentTileMinY, bTy);
        currentTileMaxY = Math.max(currentTileMaxY, Math.max(vbLy_, Math.max(vbRy_, vbBy_)));

        if(!isW&&elevation>0){
            float fTx_world = world_gc_x, fTy_world = world_tc_y + d_top_y_rel;
            float fLx_world = world_gc_x + d_left_x_rel, fLy_world = world_tc_y + d_left_y_rel;
            float fRx_world = world_gc_x + d_right_x_rel, fRy_world = world_tc_y + d_right_y_rel;
            float fBx_world = world_gc_x, fBy_world = world_tc_y + d_bottom_y_rel;
            verticesAdded+=putColoredQuadVerticesAsTriangles(buffer,bLx,bLy,fLx_world,fLy_world,fBx_world,fBy_world,bBx,bBy,s1Clr);
            verticesAdded+=putColoredQuadVerticesAsTriangles(buffer,bRx,bRy,fRx_world,fRy_world,fBx_world,fBy_world,bBx,bBy,s2Clr);
            verticesAdded+=putColoredQuadVerticesAsTriangles(buffer,fLx_world,fLy_world,fTx_world,fTy_world,fRx_world,fRy_world,fBx_world,fBy_world,topClr);

            currentTileMinX = Math.min(currentTileMinX, fLx_world);
            currentTileMaxX = Math.max(currentTileMaxX, fRx_world);
            currentTileMinY = Math.min(currentTileMinY, fTy_world);
            currentTileMaxY = Math.max(currentTileMaxY, fBy_world);
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
        this.tileDetailRandom.setSeed((long)tileR * (map != null ? map.getWidth() : CHUNK_SIZE_TILES) + tileC); // Added null check for map
        int elevation = tile.getElevation();
        float halfW_unit = TILE_WIDTH / 2.0f; float halfH_unit = TILE_HEIGHT / 2.0f;
        float elevThick_unit = TILE_THICKNESS;
        float world_tile_center_x = (tileC - tileR) * halfW_unit;
        float world_tile_center_y_plane = (tileC + tileR) * halfH_unit;
        float world_tile_top_face_center_y = world_tile_center_y_plane - (elevation * elevThick_unit);
        float[] grassBaseColor = {0.10f,0.45f,0.10f,1.0f};
        if (inputHandler != null && tileR == inputHandler.getSelectedRow() && tileC == inputHandler.getSelectedCol()) {
            grassBaseColor = new float[]{0.1f,0.6f,0.1f,1.0f};
        }
        // TODO: Implement Grass.getThickGrassTuftsVertices_WorldSpace
        return 0;
    }

    private int addPlayerVerticesToBuffer_ScreenSpace(PlayerModel p, FloatBuffer buffer) {
        if (playerTexture == null || playerTexture.getId() == 0) return 0; float r=1f,g=1f,b=1f,a=1f;
        Tile cT=map.getTile(p.getTileRow(),p.getTileCol()); int elev=(cT!=null)?cT.getElevation():0;
        int[] sC=camera.mapToScreenCoordsForPicking(p.getMapCol(),p.getMapRow(),elev); float sX=sC[0],sY=sC[1];
        float cz=camera.getZoom(); float sW=PlayerModel.FRAME_WIDTH*cz;float sH=PlayerModel.FRAME_HEIGHT*cz;
        float dX=sX-sW/2f;float dY=sY-sH; if(p.isLevitating())dY-=(Math.sin(p.getLevitateTimer())*8*cz);
        float u0,v0,u1,v1t; int afc=p.getVisualFrameIndex();int afr=p.getAnimationRow();
        float tsX=afc*PlayerModel.FRAME_WIDTH;float tsY=afr*PlayerModel.FRAME_HEIGHT;
        u0=tsX/playerTexture.getWidth();v0=tsY/playerTexture.getHeight();u1=(tsX+PlayerModel.FRAME_WIDTH)/playerTexture.getWidth();v1t=(tsY+PlayerModel.FRAME_HEIGHT)/playerTexture.getHeight();
        return putTexturedQuadVerticesAsTriangles(buffer,dX,dY,dX+sW,dY,dX+sW,dY+sH,dX,dY+sH,new float[]{r,g,b,a},u0,v0,u1,v0,u1,v1t,u0,v1t);
    }

    private int addTreeVerticesToBuffer_ScreenSpace(TreeData tree, FloatBuffer buffer) {
        if(treeTexture==null||treeTexture.getId()==0||tree.treeVisualType==Tile.TreeVisualType.NONE)return 0; float r=1f,g=1f,b=1f,a=1f;
        float u0=0,v0=0,u1t=0,v1t=0,fw=0,fh=0,aoyp=0;
        switch(tree.treeVisualType){case APPLE_TREE_FRUITING:fw=90;fh=130;u0=0f/treeTexture.getWidth();v0=0f/treeTexture.getHeight();u1t=90f/treeTexture.getWidth();v1t=130f/treeTexture.getHeight();aoyp=110;break;
            case PINE_TREE_SMALL:fw=90;fh=180;u0=90f/treeTexture.getWidth();v0=0f/treeTexture.getHeight();u1t=(90f+90f)/treeTexture.getWidth();v1t=180f/treeTexture.getHeight();aoyp=165;break; default:return 0;}
        float cz=camera.getZoom(); float sW=fw*cz,sH=fh*cz;
        float tasX=tree.topDiamondCenterX_screen; float tasY=tree.topDiamondCenterY_screen;
        float saoY=(fh-aoyp)*cz; float dX=tasX-sW/2f; float dY=tasY-(sH-saoY);
        return putTexturedQuadVerticesAsTriangles(buffer,dX,dY,dX+sW,dY,dX+sW,dY+sH,dX,dY+sH,new float[]{r,g,b,a},u0,v0,u1t,v0,u1t,v1t,u0,v1t);
    }

    private int putTexturedQuadVerticesAsTriangles(FloatBuffer b,float x0,float y0,float x1,float y1,float x2,float y2,float x3,float y3,float[]c,float u0,float v0,float u1,float v1_tex,float u2_tex,float v2_tex,float u3_tex,float v3_tex){
        b.put(x0).put(y0).put(c[0]).put(c[1]).put(c[2]).put(c[3]).put(u0).put(v0);b.put(x3).put(y3).put(c[0]).put(c[1]).put(c[2]).put(c[3]).put(u3_tex).put(v3_tex);b.put(x1).put(y1).put(c[0]).put(c[1]).put(c[2]).put(c[3]).put(u1).put(v1_tex);
        b.put(x1).put(y1).put(c[0]).put(c[1]).put(c[2]).put(c[3]).put(u1).put(v1_tex);b.put(x3).put(y3).put(c[0]).put(c[1]).put(c[2]).put(c[3]).put(u3_tex).put(v3_tex);b.put(x2).put(y2).put(c[0]).put(c[1]).put(c[2]).put(c[3]).put(u2_tex).put(v2_tex);return 6;
    }
    private int putColoredQuadVerticesAsTriangles(FloatBuffer b,float xTL,float yTL,float xTR,float yTR,float xBR,float yBR,float xBL,float yBL,float[]c){
        b.put(xTL).put(yTL).put(c[0]).put(c[1]).put(c[2]).put(c[3]); b.put(xBL).put(yBL).put(c[0]).put(c[1]).put(c[2]).put(c[3]); b.put(xTR).put(yTR).put(c[0]).put(c[1]).put(c[2]).put(c[3]);
        b.put(xTR).put(yTR).put(c[0]).put(c[1]).put(c[2]).put(c[3]); b.put(xBL).put(yBL).put(c[0]).put(c[1]).put(c[2]).put(c[3]); b.put(xBR).put(yBR).put(c[0]).put(c[1]).put(c[2]).put(c[3]); return 6;
    }

    private void renderUI() {
        defaultShader.setUniform("uModelViewMatrix", modelViewMatrixForSprites);
        defaultShader.setUniform("uIsFont", 1); // Tell shader this is a font

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        int yP=20,yI=18;
        Tile selT = (map != null) ? map.getTile(inputHandler.getSelectedRow(),inputHandler.getSelectedCol()) : null; // Added null check for map
        String selI="Selected: ("+inputHandler.getSelectedRow()+", "+inputHandler.getSelectedCol()+")";
        if(selT!=null){selI+=" Elev: "+selT.getElevation()+" Type: "+selT.getType();}

        if (uiFont != null && uiFont.isInitialized()) {
            // System.out.println("[Renderer DEBUG] Attempting to draw UI text..."); // Already in your log
            uiFont.drawText(10f, (float)yP, "Player: ("+player.getTileRow()+", "+player.getTileCol()+") Act: "+player.getCurrentAction()+" Dir: "+player.getCurrentDirection()+" F:"+player.getVisualFrameIndex()); yP+=yI;
            uiFont.drawText(10f, (float)yP, selI); yP+=yI;
            uiFont.drawText(10f, (float)yP, String.format("Camera: (%.1f, %.1f) Zoom: %.2f",camera.getCameraX(),camera.getCameraY(),camera.getZoom())); yP+=yI;
            uiFont.drawText(10f, (float)yP, "Move: Click | Elev Sel +/-: Q/E | Dig: J"); yP+=yI;
            uiFont.drawText(10f, (float)yP, "Levitate: F | Center Cam: C | Regen Map: G"); yP+=yI; yP+=yI;
            uiFont.drawText(10f, (float)yP, "Inventory:");yP+=yI;
            java.util.Map<String,Integer> inv=player.getInventory();
            if(inv.isEmpty()){
                uiFont.drawText(20f, (float)yP, "- Empty -"); yP+=yI; // Added yP increment
            } else {
                for(java.util.Map.Entry<String,Integer>e:inv.entrySet()){
                    uiFont.drawText(20f, (float)yP, "- "+e.getKey()+": "+e.getValue()); yP+=yI;
                }
            }
            // System.out.println("[Renderer DEBUG] Finished attempting to draw UI text."); // Already in your log
        } else {
            if (uiFont == null) System.err.println("[Renderer WARNING] renderUI: uiFont object is null.");
            else System.err.println("[Renderer WARNING] renderUI: uiFont is not initialized. Skipping text rendering.");
        }
        // glDisable(GL_BLEND); // Consider if subsequent rendering needs blend disabled
        checkError("Renderer: renderUI - after drawing text");
    }

    public void cleanup() {
        if(playerTexture!=null)playerTexture.delete();
        if(treeTexture!=null)treeTexture.delete();
        if(uiFont!=null)uiFont.cleanup();
        if(defaultShader!=null)defaultShader.cleanup();
        if (mapChunks != null) { for (Chunk chunk : mapChunks) { chunk.cleanup(); } mapChunks.clear(); }
        if (spriteVaoId != 0) glDeleteVertexArrays(spriteVaoId);
        if (spriteVboId != 0) glDeleteBuffers(spriteVboId);
        if (spriteVertexBuffer != null) MemoryUtil.memFree(spriteVertexBuffer);
        System.out.println("Renderer: cleanup complete.");
        checkError("Renderer: cleanup");
    }

    private void checkError(String stage) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            String errorMsg = "UNKNOWN GL ERROR (" + String.format("0x%X", error) + ")";
            switch (error) {
                case GL_INVALID_ENUM: errorMsg = "GL_INVALID_ENUM"; break;
                case GL_INVALID_VALUE: errorMsg = "GL_INVALID_VALUE"; break;
                case GL_INVALID_OPERATION: errorMsg = "GL_INVALID_OPERATION"; break;
                // case GL_STACK_OVERFLOW: errorMsg = "GL_STACK_OVERFLOW"; break; // If using older GL profile
                // case GL_STACK_UNDERFLOW: errorMsg = "GL_STACK_UNDERFLOW"; break; // If using older GL profile
                case GL_OUT_OF_MEMORY: errorMsg = "GL_OUT_OF_MEMORY"; break;
            }
            System.err.println("Renderer: OpenGL Error at stage '" + stage + "': " + errorMsg);
        }
    }
}