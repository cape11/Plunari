package org.isogame.render;

import org.isogame.input.InputHandler;
import org.isogame.map.Map;
import org.isogame.tile.Tile; // Assuming Tile class is in org.isogame.tile
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL11.*;
import static org.isogame.constants.Constants.*; // For TILE_WIDTH, TILE_HEIGHT if used in BoundingBox estimation


public class Chunk {
    public final int chunkGridX, chunkGridY;
    public final int TILE_SIZE_IN_CHUNK;

    private int vaoId = 0;
    private int vboId = 0;
    private int vertexCount = 0;

    // World-space Axis-Aligned Bounding Box for this chunk
    private float worldMinX = Float.MAX_VALUE, worldMinY = Float.MAX_VALUE;
    private float worldMaxX = Float.MIN_VALUE, worldMaxY = Float.MIN_VALUE;
    private BoundingBox boundingBox; // To store the calculated AABB

    public Chunk(int chunkGridX, int chunkGridY, int chunkSize) {
        this.chunkGridX = chunkGridX;
        this.chunkGridY = chunkGridY;
        this.TILE_SIZE_IN_CHUNK = chunkSize;
    }

    public void setupGLResources() {
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        // Initial buffer data allocation can be done here with glBufferData(..., GL_STATIC_DRAW)
        // or in uploadGeometry if the size is better known then.
        // For now, let uploadGeometry handle the glBufferData call.
    }

    public void uploadGeometry(Map fullMap, InputHandler inputHandler, Renderer rendererInstance) {
        int startTileR = chunkGridY * TILE_SIZE_IN_CHUNK;
        int startTileC = chunkGridX * TILE_SIZE_IN_CHUNK;
        int endTileR = Math.min(startTileR + TILE_SIZE_IN_CHUNK, fullMap.getHeight());
        int endTileC = Math.min(startTileC + TILE_SIZE_IN_CHUNK, fullMap.getWidth());

        // Estimate max vertices for this chunk's data buffer
        int maxTilesInThisChunk = TILE_SIZE_IN_CHUNK * TILE_SIZE_IN_CHUNK;
        int maxChunkTileVerts = maxTilesInThisChunk * 5 * 6; // 5 quads per tile
        int maxChunkGrassVerts = maxTilesInThisChunk * 15 * 6; // 15 grass tufts
        FloatBuffer chunkData = MemoryUtil.memAllocFloat((maxChunkTileVerts + maxChunkGrassVerts) * Renderer.FLOATS_PER_VERTEX_COLORED);

        this.vertexCount = 0; // Reset vertex count for this chunk
        // Initialize bounds for this specific chunk's geometry
        float[] currentChunkBounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE};


        // Iterate tiles *within this chunk's range*
        // The painter's algorithm order for tiles within a chunk is important if not using depth buffer
        // or if there are alpha blended elements. For opaque tiles with depth buffer, simple iteration is fine.
        for (int r_local = 0; r_local < (endTileR - startTileR); r_local++) {
            for (int c_local = 0; c_local < (endTileC - startTileC); c_local++) {
                int actualR = startTileR + r_local;
                int actualC = startTileC + c_local;

                if (fullMap.isValid(actualR, actualC)) {
                    Tile tile = fullMap.getTile(actualR, actualC);
                    if (tile != null) {
                        // Call the corrected method name in Renderer
                        this.vertexCount += rendererInstance.addSingleTileVerticesToBuffer_WorldSpace_ForChunk(
                                actualR, actualC, tile,
                                (inputHandler != null && actualR == inputHandler.getSelectedRow() && actualC == inputHandler.getSelectedCol()),
                                chunkData,
                                currentChunkBounds // Pass array to update bounds
                        );

                        if (tile.getType() == Tile.TileType.GRASS && tile.getTreeType() == Tile.TreeVisualType.NONE) {
                            // Call the corrected method name in Renderer
                            this.vertexCount += rendererInstance.addGrassVerticesForTile_WorldSpace_ForChunk(
                                    actualR, actualC, tile, chunkData,
                                    currentChunkBounds); // Pass array to update bounds
                        }
                    }
                }
            }
        }

        // Store the calculated bounds for this chunk
        this.worldMinX = currentChunkBounds[0];
        this.worldMinY = currentChunkBounds[1];
        this.worldMaxX = currentChunkBounds[2];
        this.worldMaxY = currentChunkBounds[3];
        this.boundingBox = new BoundingBox(this.worldMinX, this.worldMinY, this.worldMaxX, this.worldMaxY);

        chunkData.flip();
        if (this.vertexCount > 0) {
            glBindVertexArray(vaoId);
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, chunkData, GL_STATIC_DRAW); // Upload all data for this chunk

            glVertexAttribPointer(0, 2, GL_FLOAT, false, Renderer.FLOATS_PER_VERTEX_COLORED * Float.BYTES, 0L);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 4, GL_FLOAT, false, Renderer.FLOATS_PER_VERTEX_COLORED * Float.BYTES, 2 * Float.BYTES);
            glEnableVertexAttribArray(1);
            glDisableVertexAttribArray(2); // No texture attribute for tile/grass VAO

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        } else {
            // If no vertices, ensure VBO is not left with old data or in an undefined state
            // (though glBufferData with size 0 or null data might be problematic,
            // it's better to just not draw if vertexCount is 0).
        }
        MemoryUtil.memFree(chunkData);
    }

    public void render() {
        if (vaoId != 0 && vertexCount > 0) {
            glBindVertexArray(vaoId);
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
            // Unbinding VAO after each chunk draw is safer, though Renderer can unbind last one
            // glBindVertexArray(0);
        }
    }

    public void cleanup() {
        if (vaoId != 0) glDeleteVertexArrays(vaoId);
        if (vboId != 0) glDeleteBuffers(vboId);
        vaoId = 0; vboId = 0; vertexCount = 0;
    }

    public static class BoundingBox {
        public float minX, minY, maxX, maxY;
        public BoundingBox(float minX, float minY, float maxX, float maxY) {
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
        }
    }
    public BoundingBox getBoundingBox() {
        // Return the accurately calculated bounding box if available, else a rough estimate
        if (this.boundingBox != null && this.vertexCount > 0) { // Ensure it was calculated
            return this.boundingBox;
        }
        // Fallback rough estimate (less accurate for culling)
        float approxWorldCX = (chunkGridX + 0.5f - chunkGridY - 0.5f) * TILE_SIZE_IN_CHUNK * (TILE_WIDTH / 2.0f);
        float approxWorldCY = (chunkGridX + 0.5f + chunkGridY + 0.5f) * TILE_SIZE_IN_CHUNK * (TILE_HEIGHT / 2.0f);
        float spanX = TILE_SIZE_IN_CHUNK * TILE_WIDTH * 0.75f; // Larger than actual due to iso spread
        float spanY = TILE_SIZE_IN_CHUNK * TILE_HEIGHT * 0.75f + ALTURA_MAXIMA * TILE_THICKNESS;
        return new BoundingBox(approxWorldCX - spanX, approxWorldCY - spanY, approxWorldCX + spanX, approxWorldCY + spanY);
    }
}
