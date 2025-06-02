package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants; // Used for CHUNK_SIZE_TILES and ALTURA_MAXIMA
import org.isogame.input.InputHandler;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL15.*;
// No need to import CHUNK_SIZE_TILES and ALTURA_MAXIMA if accessed via Constants.
// import static org.isogame.constants.Constants.CHUNK_SIZE_TILES;
// import static org.isogame.constants.Constants.ALTURA_MAXIMA;


public class Chunk {
    public final int chunkGridX, chunkGridY;
    public final int TILE_SIZE_IN_CHUNK; // This should be Constants.CHUNK_SIZE_TILES

    private int vaoId = 0;
    private int vboId = 0;
    private int vertexCount = 0;

    private BoundingBox boundingBox;
    private List<Renderer.TreeData> treesInChunk = new ArrayList<>();

    private boolean vboInitialized = false;
    private int vboCapacityFloats = 0;

    // Maximum vertices for a single tile column:
    // Pedestal: 2 quads * 6 vertices/quad = 12 vertices
    // Top face: 2 quads * 6 vertices/quad = 12 vertices
    // Sides: Constants.ALTURA_MAXIMA slices * 2 quads/slice (for two visible sides) * 6 vertices/quad = Constants.ALTURA_MAXIMA * 12 vertices
    // Note: The actual number of side slices is totalElevationUnits, which can be up to ALTURA_MAXIMA.
    // A tile column can have at most ALTURA_MAXIMA elevated slices that need rendering.
    private static final int MAX_VERTICES_PER_TILE_COLUMN =
            (2 * 6) + // Pedestal quads
                    (2 * 6) + // Top face quads
                    (Constants.ALTURA_MAXIMA * 2 * 6); // Max possible side quads if a tile column is a tall pillar

    private static final int MAX_EXPECTED_FLOATS_PER_CHUNK =
            Constants.CHUNK_SIZE_TILES * Constants.CHUNK_SIZE_TILES * // Number of tile columns in a chunk
                    MAX_VERTICES_PER_TILE_COLUMN * // Max vertices one column can generate
                    Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED; // Floats per vertex


    public Chunk(int chunkGridX, int chunkGridY, int chunkSizeInTilesConstant) {
        this.chunkGridX = chunkGridX;
        this.chunkGridY = chunkGridY;
        // Ensure TILE_SIZE_IN_CHUNK is correctly initialized, typically from Constants.CHUNK_SIZE_TILES
        this.TILE_SIZE_IN_CHUNK = chunkSizeInTilesConstant;
        float approxWorldX = (chunkGridX * this.TILE_SIZE_IN_CHUNK) * Constants.TILE_WIDTH * 0.75f;
        float approxWorldY = (chunkGridY * this.TILE_SIZE_IN_CHUNK) * Constants.TILE_HEIGHT * 0.5f;
        this.boundingBox = new BoundingBox(approxWorldX, approxWorldY,
                approxWorldX + this.TILE_SIZE_IN_CHUNK * Constants.TILE_WIDTH,
                approxWorldY + this.TILE_SIZE_IN_CHUNK * Constants.TILE_HEIGHT + Constants.ALTURA_MAXIMA * Constants.TILE_THICKNESS + Constants.BASE_THICKNESS);
    }

    public void setupGLResources() {
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        int stride = Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, (3 + 4) * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, (3 + 4 + 2) * Float.BYTES);
        glEnableVertexAttribArray(3);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public List<Renderer.TreeData> getTreesInChunk() {
        return treesInChunk;
    }

    public void uploadGeometry(Map fullMap, InputHandler inputHandler, Renderer rendererInstance, CameraManager cameraManager) {
        int startTileR_mapArray = chunkGridY * TILE_SIZE_IN_CHUNK;
        int startTileC_mapArray = chunkGridX * TILE_SIZE_IN_CHUNK;
        int endTileR_mapArray = Math.min(startTileR_mapArray + TILE_SIZE_IN_CHUNK, fullMap.getHeight());
        int endTileC_mapArray = Math.min(startTileC_mapArray + TILE_SIZE_IN_CHUNK, fullMap.getWidth());

        treesInChunk.clear();

        FloatBuffer chunkDataBuffer = null;
        int currentFloatsInThisUpload = 0; // Track floats added

        try {
            chunkDataBuffer = MemoryUtil.memAllocFloat(MAX_EXPECTED_FLOATS_PER_CHUNK);

            float[] currentChunkBounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE};
            boolean boundsInitializedByTile = false;

            for (int r_local = 0; r_local < (endTileR_mapArray - startTileR_mapArray); r_local++) {
                for (int c_local = 0; c_local < (endTileC_mapArray - startTileC_mapArray); c_local++) {
                    int actualR_mapArray = startTileR_mapArray + r_local;
                    int actualC_mapArray = startTileC_mapArray + c_local;
                    Tile tile = fullMap.getTile(actualR_mapArray, actualC_mapArray);

                    if (tile != null && tile.getType() != Tile.TileType.AIR) {
                        // Estimate max floats for one tile (worst case) for the remaining check
                        // This is a rough check; the buffer should ideally be sized correctly from MAX_EXPECTED_FLOATS_PER_CHUNK
                        int maxFloatsPerTile = MAX_VERTICES_PER_TILE_COLUMN * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED;
                        if (chunkDataBuffer.remaining() < maxFloatsPerTile) {
                            System.err.println("Chunk VBO buffer nearly full before processing tile ("+actualR_mapArray+","+actualC_mapArray+") in chunk ("+chunkGridX+","+chunkGridY+"). Max capacity: " + MAX_EXPECTED_FLOATS_PER_CHUNK + ", Remaining: " + chunkDataBuffer.remaining() + ". Increase MAX_EXPECTED_FLOATS_PER_CHUNK.");
                            break;
                        }

                        int vertsAddedThisTile = rendererInstance.addSingleTileVerticesToList_WorldSpace_ForChunk(
                                actualR_mapArray, actualC_mapArray, tile,
                                (inputHandler != null && actualR_mapArray == inputHandler.getSelectedRow() && actualC_mapArray == inputHandler.getSelectedCol()),
                                chunkDataBuffer,
                                currentChunkBounds);
                        currentFloatsInThisUpload += vertsAddedThisTile * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED;
                        if (vertsAddedThisTile > 0) {
                            boundsInitializedByTile = true;
                        }
                    }
                    if (tile != null && tile.getTreeType() != Tile.TreeVisualType.NONE &&
                            tile.getType() != Tile.TileType.WATER && tile.getType() != Tile.TileType.AIR) {
                        treesInChunk.add(new Renderer.TreeData(tile.getTreeType(), (float) actualC_mapArray, (float) actualR_mapArray, tile.getElevation()));
                    }
                }
                if (chunkDataBuffer.remaining() < (MAX_VERTICES_PER_TILE_COLUMN * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED)) {
                    // Break outer loop too if not enough space for another worst-case tile
                    if(chunkDataBuffer.position() > 0) { // Only log if we actually added something
                        System.err.println("Chunk VBO buffer potentially full after row in chunk ("+chunkGridX+","+chunkGridY+"). Breaking outer loop.");
                    }
                    break;
                }
            }

            this.vertexCount = currentFloatsInThisUpload / Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED;

            if (!boundsInitializedByTile && this.vertexCount == 0) {
                float worldChunkCenterX = (startTileC_mapArray + TILE_SIZE_IN_CHUNK / 2.0f - startTileR_mapArray - TILE_SIZE_IN_CHUNK / 2.0f) * (Constants.TILE_WIDTH / 2.0f);
                float worldChunkCenterY = (startTileC_mapArray + TILE_SIZE_IN_CHUNK / 2.0f + startTileR_mapArray + TILE_SIZE_IN_CHUNK / 2.0f) * (Constants.TILE_HEIGHT / 2.0f);
                this.boundingBox = new BoundingBox(worldChunkCenterX, worldChunkCenterY, worldChunkCenterX, worldChunkCenterY);
            } else {
                this.boundingBox = new BoundingBox(currentChunkBounds[0], currentChunkBounds[1], currentChunkBounds[2], currentChunkBounds[3]);
            }

            chunkDataBuffer.flip();

            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            int requiredFloatsCurrent = chunkDataBuffer.remaining(); // This is the actual number of floats written

            if (!vboInitialized || requiredFloatsCurrent > vboCapacityFloats) {
                glBufferData(GL_ARRAY_BUFFER, chunkDataBuffer, GL_DYNAMIC_DRAW);
                vboCapacityFloats = chunkDataBuffer.capacity(); // Store capacity in floats
                vboInitialized = true;
            } else if (requiredFloatsCurrent > 0) {
                glBufferSubData(GL_ARRAY_BUFFER, 0, chunkDataBuffer);
            } else {
                glBufferData(GL_ARRAY_BUFFER, 0L, GL_DYNAMIC_DRAW);
                vboCapacityFloats = 0;
            }
            glBindBuffer(GL_ARRAY_BUFFER, 0);

        } catch(Exception e) {
            System.err.println("Exception during Chunk.uploadGeometry for chunk ("+chunkGridX+","+chunkGridY+"): " + e.getMessage());
            e.printStackTrace();
            this.vertexCount = 0; // Ensure no rendering if upload failed
        }
        finally {
            if (chunkDataBuffer != null) {
                MemoryUtil.memFree(chunkDataBuffer);
            }
        }
    }

    public void render() {
        if (vaoId != 0 && vertexCount > 0) {
            glBindVertexArray(vaoId);
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        }
    }

    public void cleanup() {
        if (vaoId != 0) { glDeleteVertexArrays(vaoId); vaoId = 0; }
        if (vboId != 0) { glDeleteBuffers(vboId); vboId = 0; }
        vertexCount = 0;
        vboInitialized = false;
        vboCapacityFloats = 0;
        treesInChunk.clear();
    }

    public static class BoundingBox {
        public float minX, minY, maxX, maxY;
        public BoundingBox(float minX, float minY, float maxX, float maxY) {
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
        }
    }
    public BoundingBox getBoundingBox() { return this.boundingBox; }
}