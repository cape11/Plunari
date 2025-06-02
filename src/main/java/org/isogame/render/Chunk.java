package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.constants.Constants;
import org.isogame.input.InputHandler;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.lwjgl.system.MemoryUtil;
import static org.isogame.constants.Constants.*;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL15.*;
import static org.isogame.constants.Constants.*; // Assuming CHUNK_SIZE_TILES, TILE_WIDTH etc. are here

public class Chunk {
    public final int chunkGridX, chunkGridY;
    public final int TILE_SIZE_IN_CHUNK; // Should be initialized with Constants.CHUNK_SIZE_TILES

    private int vaoId = 0;
    private int vboId = 0;
    private int vertexCount = 0; // Number of actual vertices to draw

    private BoundingBox boundingBox;
    private List<Renderer.TreeData> treesInChunk = new ArrayList<>();

    private boolean vboInitialized = false;
    private int vboCapacityBytes = 0; // Current capacity of the VBO in bytes

    public Chunk(int chunkGridX, int chunkGridY, int chunkSizeInTilesConstant) {
        this.chunkGridX = chunkGridX;
        this.chunkGridY = chunkGridY;
        this.TILE_SIZE_IN_CHUNK = chunkSizeInTilesConstant;
        // Corrected BoundingBox initialization
        float approxWorldX = (chunkGridX * this.TILE_SIZE_IN_CHUNK) * Constants.TILE_WIDTH * 0.75f;
        float approxWorldY = (chunkGridY * this.TILE_SIZE_IN_CHUNK) * Constants.TILE_HEIGHT * 0.5f;
        this.boundingBox = new BoundingBox(approxWorldX, approxWorldY,
                approxWorldX + this.TILE_SIZE_IN_CHUNK * Constants.TILE_WIDTH,
                approxWorldY + this.TILE_SIZE_IN_CHUNK * Constants.TILE_HEIGHT + Constants.ALTURA_MAXIMA * Constants.TILE_THICKNESS + Constants.BASE_THICKNESS);
    }

    /**
     * Sets up the Vertex Array Object (VAO) and Vertex Buffer Object (VBO) ID.
     * Configures vertex attributes. Called once during chunk initialization.
     */
    public void setupGLResources() {
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId); // Bind VBO to this VAO for attribute setup

        // Define the structure of a single vertex
        int stride = Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED * Float.BYTES;

        // Position attribute (vec3) - location 0
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);

        // Color attribute (vec4) - location 1, offset after 3 position floats
        glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        // Texture coordinate attribute (vec2) - location 2, offset after 3 pos + 4 color floats
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, (3 + 4) * Float.BYTES);
        glEnableVertexAttribArray(2);

        // Light attribute (float) - location 3, offset after 3 pos + 4 color + 2 texCoord floats
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, (3 + 4 + 2) * Float.BYTES);
        glEnableVertexAttribArray(3);

        glBindBuffer(GL_ARRAY_BUFFER, 0); // Unbind VBO from GL_ARRAY_BUFFER target
        glBindVertexArray(0); // Unbind VAO
    }

    public List<Renderer.TreeData> getTreesInChunk() {
        return treesInChunk;
    }

    /**
     * Gathers vertex data for all non-AIR tiles in this chunk using a List<Float>
     * and then uploads it to the GPU.
     */
    public void uploadGeometry(Map fullMap, InputHandler inputHandler, Renderer rendererInstance, CameraManager cameraManager) {
        int startTileR_mapArray = chunkGridY * TILE_SIZE_IN_CHUNK;
        int startTileC_mapArray = chunkGridX * TILE_SIZE_IN_CHUNK;
        int endTileR_mapArray = Math.min(startTileR_mapArray + TILE_SIZE_IN_CHUNK, fullMap.getHeight());
        int endTileC_mapArray = Math.min(startTileC_mapArray + TILE_SIZE_IN_CHUNK, fullMap.getWidth());

        treesInChunk.clear();
        List<Float> vertexList = new ArrayList<>(); // <<< Use ArrayList<Float> to dynamically collect vertex data

        this.vertexCount = 0; // This will store the number of vertices
        float[] currentChunkBounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE};
        boolean boundsInitializedByTile = false; // Track if any tile contributed to bounds

        for (int r_local = 0; r_local < (endTileR_mapArray - startTileR_mapArray); r_local++) {
            for (int c_local = 0; c_local < (endTileC_mapArray - startTileC_mapArray); c_local++) {
                int actualR_mapArray = startTileR_mapArray + r_local;
                int actualC_mapArray = startTileC_mapArray + c_local;

                // Loop bounds should ensure isValid, but defensive check is fine.
                // if (fullMap.isValid(actualR_mapArray, actualC_mapArray)) {
                Tile tile = fullMap.getTile(actualR_mapArray, actualC_mapArray);
                if (tile != null) { // Should always be non-null if Map.java ensures fully tiled map
                    // Only generate geometry for non-AIR tiles.
                    if (tile.getType() != Tile.TileType.AIR) {
                        // Call the renamed method that accepts a List<Float>
                        int vertsAddedThisTile = rendererInstance.addSingleTileVerticesToList_WorldSpace_ForChunk(
                                actualR_mapArray, actualC_mapArray, tile,
                                (inputHandler != null && actualR_mapArray == inputHandler.getSelectedRow() && actualC_mapArray == inputHandler.getSelectedCol()),
                                vertexList, // Pass the List<Float>
                                currentChunkBounds); // Renderer will update these bounds
                        this.vertexCount += vertsAddedThisTile; // Accumulate VERTEX count
                        if (vertsAddedThisTile > 0) {
                            boundsInitializedByTile = true;
                        }
                    }

                    // Tree logic: place trees on non-WATER, non-AIR solid ground
                    if (tile.getTreeType() != Tile.TreeVisualType.NONE &&
                            tile.getType() != Tile.TileType.WATER && // Assuming trees don't grow in water
                            tile.getType() != Tile.TileType.AIR) {   // Trees don't grow in AIR
                        treesInChunk.add(new Renderer.TreeData(tile.getTreeType(), (float) actualC_mapArray, (float) actualR_mapArray, tile.getElevation()));
                    }
                }
                // }
            }
        }

        if (!boundsInitializedByTile && this.vertexCount == 0) {
            // If chunk is entirely AIR or no geometry was generated, set some default small/point bounds
            float worldChunkCenterX = (startTileC_mapArray + TILE_SIZE_IN_CHUNK / 2.0f - startTileR_mapArray - TILE_SIZE_IN_CHUNK / 2.0f) * (Constants.TILE_WIDTH / 2.0f);
            float worldChunkCenterY = (startTileC_mapArray + TILE_SIZE_IN_CHUNK / 2.0f + startTileR_mapArray + TILE_SIZE_IN_CHUNK / 2.0f) * (Constants.TILE_HEIGHT / 2.0f);
            this.boundingBox = new BoundingBox(worldChunkCenterX, worldChunkCenterY, worldChunkCenterX, worldChunkCenterY);
        } else {
            this.boundingBox = new BoundingBox(currentChunkBounds[0], currentChunkBounds[1], currentChunkBounds[2], currentChunkBounds[3]);
        }

        FloatBuffer chunkData = null;
        if (!vertexList.isEmpty()) {
            chunkData = MemoryUtil.memAllocFloat(vertexList.size()); // Allocate exact size needed
            for (Float val : vertexList) {
                chunkData.put(val);
            }
            chunkData.flip(); // Prepare for reading by OpenGL
        } else {
            // If vertexList is empty, this.vertexCount should be 0
            this.vertexCount = 0;
        }

        glBindBuffer(GL_ARRAY_BUFFER, vboId); // Bind this chunk's VBO

        // Use chunkData.remaining() for actual number of floats to upload, times Float.BYTES
        int requiredBytes = (chunkData != null) ? chunkData.remaining() * Float.BYTES : 0;

        if (!vboInitialized || requiredBytes > vboCapacityBytes || (requiredBytes == 0 && vboCapacityBytes > 0 && this.vertexCount == 0) ) {
            if (chunkData != null && requiredBytes > 0) {
                glBufferData(GL_ARRAY_BUFFER, chunkData, GL_DYNAMIC_DRAW);
                vboCapacityBytes = chunkData.capacity() * Float.BYTES;
            } else {
                glBufferData(GL_ARRAY_BUFFER, 0L, GL_DYNAMIC_DRAW);
                vboCapacityBytes = 0;
            }            vboCapacityBytes = (chunkData != null && chunkData.capacity() > 0) ? chunkData.capacity() * Float.BYTES : 0; // Store capacity in bytes
            // Ensure a minimal non-zero capacity if we just cleared the buffer with size 0 data.
            // This helps if some driver or VAO setup expects a valid, even if tiny, buffer store.
            if (vboCapacityBytes == 0 && (chunkData == null || chunkData.capacity() == 0)) {
                vboCapacityBytes = Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED * Float.BYTES; // Min capacity for 1 vertex to avoid issues
            }
            vboInitialized = true;
            // VAO attribute pointers are set in setupGLResources and should persist
            // as they refer to this vboId and the vertex format hasn't changed.
        } else if (requiredBytes > 0 && chunkData != null) {
            // Update existing VBO content if capacity is sufficient and there's actual data
            glBufferSubData(GL_ARRAY_BUFFER, 0, chunkData);
        }
        // If requiredBytes is 0 and VBO was already initialized and also had 0 capacity, do nothing.

        glBindBuffer(GL_ARRAY_BUFFER, 0); // Unbind VBO from GL_ARRAY_BUFFER target

        if (chunkData != null) {
            MemoryUtil.memFree(chunkData); // Free the native FloatBuffer
        }
    }

    public void render() {
        if (vaoId != 0 && vertexCount > 0) { // Only render if there are vertices
            glBindVertexArray(vaoId);
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
            // glBindVertexArray(0); // Typically managed by Renderer after drawing all similar objects
        }
    }

    public void cleanup() {
        if (vaoId != 0) { glDeleteVertexArrays(vaoId); vaoId = 0; }
        if (vboId != 0) { glDeleteBuffers(vboId); vboId = 0; }
        vertexCount = 0;
        vboInitialized = false;
        vboCapacityBytes = 0;
        treesInChunk.clear();
    }

    // Static inner class BoundingBox should be accessible as Chunk.BoundingBox
    public static class BoundingBox {
        public float minX, minY, maxX, maxY;
        public BoundingBox(float minX, float minY, float maxX, float maxY) {
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
        }
    }

    public BoundingBox getBoundingBox() {
        return this.boundingBox; // Return the calculated boundingBox
    }
}