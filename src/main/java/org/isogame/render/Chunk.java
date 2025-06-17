package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants;
import org.isogame.input.InputHandler;
import org.isogame.map.Map; // Correct import
import org.isogame.map.LightManager; // For ChunkCoordinate
import org.isogame.tile.Tile;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL15.*;
// import static org.isogame.constants.Constants.CHUNK_SIZE_TILES; // Access via Constants.
// import static org.isogame.constants.Constants.ALTURA_MAXIMA;


public class Chunk {
    public final int chunkGridX, chunkGridY;
    public final int TILE_SIZE_IN_CHUNK; // This should be Constants.CHUNK_SIZE_TILES

    private int vaoId = 0;
    private int vboId = 0;
    private int vertexCount = 0;

    private BoundingBox boundingBox;
    private List<Renderer.TreeData> treesInChunk = new ArrayList<>();
    private List<Renderer.LooseRockData> looseRocksInChunk = new ArrayList<>(); // <-- ADD THIS LINE

    private boolean vboInitialized = false;
    private int vboCapacityFloats = 0; // Stores capacity in number of floats

    // Maximum vertices for a single tile column:
    private static final int MAX_VERTICES_PER_TILE_COLUMN =
            (2 * 6) + // Pedestal quads
                    (2 * 6) + // Top face quads
                    (Constants.ALTURA_MAXIMA * 2 * 6); // Max possible side quads if a tile column is a tall pillar

    // Maximum expected floats for all vertices in a chunk
    private static final int MAX_EXPECTED_FLOATS_PER_CHUNK =
            Constants.CHUNK_SIZE_TILES * Constants.CHUNK_SIZE_TILES * // Number of tile columns
                    MAX_VERTICES_PER_TILE_COLUMN * // Max vertices one column can generate
                    Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED; // Floats per vertex


    public Chunk(int chunkGridX, int chunkGridY, int chunkSizeInTilesConstant) {
        this.chunkGridX = chunkGridX;
        this.chunkGridY = chunkGridY;
        this.TILE_SIZE_IN_CHUNK = chunkSizeInTilesConstant; // Should be Constants.CHUNK_SIZE_TILES

        // Approximate world coordinates for initial bounding box (can be refined)
        // These are for the visual representation, not logical tile coordinates.
        float approxWorldMinX = (chunkGridX * TILE_SIZE_IN_CHUNK - chunkGridY * TILE_SIZE_IN_CHUNK) * (Constants.TILE_WIDTH / 2.0f);
        float approxWorldMinY = (chunkGridX * TILE_SIZE_IN_CHUNK + chunkGridY * TILE_SIZE_IN_CHUNK) * (Constants.TILE_HEIGHT / 2.0f)
                - Constants.ALTURA_MAXIMA * Constants.TILE_THICKNESS; // Lowest possible Y

        float approxWorldMaxX = ((chunkGridX + 1) * TILE_SIZE_IN_CHUNK - (chunkGridY) * TILE_SIZE_IN_CHUNK) * (Constants.TILE_WIDTH / 2.0f);
        float approxWorldMaxY = ((chunkGridX) * TILE_SIZE_IN_CHUNK + (chunkGridY + 1) * TILE_SIZE_IN_CHUNK) * (Constants.TILE_HEIGHT / 2.0f)
                + Constants.BASE_THICKNESS; // Highest possible Y (base of tile above max elevation)


        this.boundingBox = new BoundingBox(
                Math.min(approxWorldMinX, approxWorldMaxX - TILE_SIZE_IN_CHUNK * Constants.TILE_WIDTH), // A bit of a simplification
                approxWorldMinY,
                Math.max(approxWorldMaxX, approxWorldMinX + TILE_SIZE_IN_CHUNK * Constants.TILE_WIDTH),
                approxWorldMaxY
        );
    }

    public void setupGLResources() {
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        // Define vertex attributes (position, color, texCoord, light)
        int stride = Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED * Float.BYTES;
        // Position (vec3)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);
        // Color (vec4)
        glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        // TexCoord (vec2)
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, (3 + 4) * Float.BYTES);
        glEnableVertexAttribArray(2);
        // Light (float)
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, (3 + 4 + 2) * Float.BYTES);
        glEnableVertexAttribArray(3);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public List<Renderer.TreeData> getTreesInChunk() {
        return treesInChunk;
    }

    public List<Renderer.LooseRockData> getLooseRocksInChunk() { // <-- ADD THIS METHOD
        return looseRocksInChunk;
    }

    /**
     * Uploads geometry for this chunk to the GPU.
     * Retrieves tile data from the Map object, which may generate it on demand.
     */
    public void uploadGeometry(Map gameMap, InputHandler inputHandler, Renderer rendererInstance, CameraManager cameraManager) {
        // Get the Tile[][] data for this specific chunk from the Map object.
        // The Map object's getOrGenerateChunkTiles will handle creating it if it doesn't exist.
        Tile[][] chunkLocalTiles = gameMap.getOrGenerateChunkTiles(this.chunkGridX, this.chunkGridY);
        if (chunkLocalTiles == null) {
            System.err.println("Chunk.uploadGeometry: Failed to get or generate tile data for chunk (" + chunkGridX + "," + chunkGridY + ")");
            this.vertexCount = 0;
            return;
        }

        treesInChunk.clear();
        looseRocksInChunk.clear(); // <-- ADD THIS LINE

        FloatBuffer chunkDataBuffer = null;
        int currentFloatsInThisUpload = 0;

        try {
            chunkDataBuffer = MemoryUtil.memAllocFloat(MAX_EXPECTED_FLOATS_PER_CHUNK);

            // For accurate bounding box calculation based on actual tiles in this chunk
            float[] currentChunkVisualBounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE};
            boolean boundsInitializedByTile = false;

            // Global starting tile coordinates for this chunk
            int globalStartTileR = chunkGridY * TILE_SIZE_IN_CHUNK;
            int globalStartTileC = chunkGridX * TILE_SIZE_IN_CHUNK;

            for (int r_local = 0; r_local < TILE_SIZE_IN_CHUNK; r_local++) {
                for (int c_local = 0; c_local < TILE_SIZE_IN_CHUNK; c_local++) {
                    Tile tile = chunkLocalTiles[r_local][c_local]; // Access tile from the local chunkTileData

                    if (tile != null && tile.getType() != Tile.TileType.AIR) {
                        int maxFloatsPerTile = MAX_VERTICES_PER_TILE_COLUMN * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED;
                        if (chunkDataBuffer.remaining() < maxFloatsPerTile) {
                            System.err.println("Chunk VBO buffer nearly full in chunk ("+chunkGridX+","+chunkGridY+"). Max: " + MAX_EXPECTED_FLOATS_PER_CHUNK + ", Rem: " + chunkDataBuffer.remaining());
                            break; // Break inner loop
                        }

                        // Calculate global map coordinates for this tile
                        int actualR_mapArray = globalStartTileR + r_local;
                        int actualC_mapArray = globalStartTileC + c_local;

                        int vertsAddedThisTile = rendererInstance.addSingleTileVerticesToList_WorldSpace_ForChunk(
                                actualR_mapArray, actualC_mapArray, tile,
                                (inputHandler != null && actualR_mapArray == inputHandler.getSelectedRow() && actualC_mapArray == inputHandler.getSelectedCol()),
                                chunkDataBuffer,
                                currentChunkVisualBounds); // Pass bounds array to be updated
                        currentFloatsInThisUpload += vertsAddedThisTile * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED;
                        if (vertsAddedThisTile > 0) {
                            boundsInitializedByTile = true;
                        }
                    }
                    if (tile != null && tile.getTreeType() != Tile.TreeVisualType.NONE &&
                            tile.getType() != Tile.TileType.WATER && tile.getType() != Tile.TileType.AIR) {
                        // Store trees with their global map coordinates
                        treesInChunk.add(new Renderer.TreeData(tile.getTreeType(),
                                (float) (globalStartTileC + c_local),
                                (float) (globalStartTileR + r_local),
                                tile.getElevation()));
                    }


                    // Check for and store loose rocks  <-- ADD THIS BLOCK STARTING HERE
                    if (tile != null && tile.getLooseRockType() != Tile.LooseRockType.NONE &&
                            tile.getType() != Tile.TileType.WATER && tile.getType() != Tile.TileType.AIR) { // Don't place rocks in water/air or on non-existent tiles
                        // Assuming Renderer.LooseRockData will have a constructor like: (Type, col, row, elevation)
                        // We'll define LooseRockData in Renderer.java later.
                        looseRocksInChunk.add(new Renderer.LooseRockData(
                                tile.getLooseRockType(),
                                (float) (globalStartTileC + c_local),
                                (float) (globalStartTileR + r_local),
                                tile.getElevation()
                        ));



                    }

                }

                if (chunkDataBuffer.remaining() < (MAX_VERTICES_PER_TILE_COLUMN * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED)) {
                    if(chunkDataBuffer.position() > 0) {
                        System.err.println("Chunk VBO buffer potentially full after row in chunk ("+chunkGridX+","+chunkGridY+"). Breaking outer loop.");
                    }
                    break; // Break outer loop
                }
            }

            this.vertexCount = currentFloatsInThisUpload / Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED;

            if (boundsInitializedByTile) {
                this.boundingBox = new BoundingBox(currentChunkVisualBounds[0], currentChunkVisualBounds[1], currentChunkVisualBounds[2], currentChunkVisualBounds[3]);
            } else if (this.vertexCount == 0) { // If chunk is empty (e.g., all AIR)
                float worldChunkCenterX = (globalStartTileC + TILE_SIZE_IN_CHUNK / 2.0f - globalStartTileR - TILE_SIZE_IN_CHUNK / 2.0f) * (Constants.TILE_WIDTH / 2.0f);
                float worldChunkCenterY = (globalStartTileC + TILE_SIZE_IN_CHUNK / 2.0f + globalStartTileR + TILE_SIZE_IN_CHUNK / 2.0f) * (Constants.TILE_HEIGHT / 2.0f);
                this.boundingBox = new BoundingBox(worldChunkCenterX, worldChunkCenterY, worldChunkCenterX, worldChunkCenterY); // Minimal bounding box
            }
            // Else, keep the approximate bounding box from constructor if some error occurred but some vertices were generated.

            chunkDataBuffer.flip();

            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            int requiredFloatsCurrent = chunkDataBuffer.remaining();

            if (!vboInitialized || requiredFloatsCurrent > vboCapacityFloats) {
                glBufferData(GL_ARRAY_BUFFER, chunkDataBuffer, GL_DYNAMIC_DRAW);
                vboCapacityFloats = chunkDataBuffer.capacity(); // Store capacity in floats
                vboInitialized = true;
            } else if (requiredFloatsCurrent > 0) {
                glBufferSubData(GL_ARRAY_BUFFER, 0, chunkDataBuffer);
            } else { // No data to upload (e.g., empty chunk)
                glBufferData(GL_ARRAY_BUFFER, 0L, GL_DYNAMIC_DRAW); // Clear buffer
                // vboCapacityFloats can remain as is, or be set to 0.
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
            // glBindVertexArray(0); // Usually unbind after all rendering of this type
        }
    }

    public void cleanup() {
        if (vaoId != 0) { glDeleteVertexArrays(vaoId); vaoId = 0; }
        if (vboId != 0) { glDeleteBuffers(vboId); vboId = 0; }
        vertexCount = 0;
        vboInitialized = false;
        vboCapacityFloats = 0;
        treesInChunk.clear();
        looseRocksInChunk.clear();
        torchesInChunk.clear(); // <-- Clear the torch list

    }

    public static class BoundingBox {
        public float minX, minY, maxX, maxY;
        public BoundingBox(float minX, float minY, float maxX, float maxY) {
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
        }
    }

    // Add this new list with the others
    private final List<Renderer.TorchData> torchesInChunk = new ArrayList<>();

    // Add a getter for it
    public List<Renderer.TorchData> getTorchesInChunk() { return torchesInChunk; }
    public BoundingBox getBoundingBox() { return this.boundingBox; }
}
