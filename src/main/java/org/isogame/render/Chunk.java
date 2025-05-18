package org.isogame.render;

import org.isogame.input.InputHandler;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL15.*;
// import static org.lwjgl.opengl.GL11.*; // Not strictly needed for VAO/VBO ops
import static org.isogame.constants.Constants.*;


public class Chunk {
    public final int chunkGridX, chunkGridY;
    public final int TILE_SIZE_IN_CHUNK; // This is CHUNK_SIZE_TILES from Constants

    private int vaoId = 0;
    private int vboId = 0;
    private int vertexCount = 0;

    private BoundingBox boundingBox;

    public Chunk(int chunkGridX, int chunkGridY, int chunkSizeInTiles) {
        this.chunkGridX = chunkGridX;
        this.chunkGridY = chunkGridY;
        this.TILE_SIZE_IN_CHUNK = chunkSizeInTiles; // Should be Constants.CHUNK_SIZE_TILES
    }

    public void setupGLResources() {
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
    }

    public void uploadGeometry(Map fullMap, InputHandler inputHandler, Renderer rendererInstance) {
        int startTileR = chunkGridY * TILE_SIZE_IN_CHUNK;
        int startTileC = chunkGridX * TILE_SIZE_IN_CHUNK;
        int endTileR = Math.min(startTileR + TILE_SIZE_IN_CHUNK, fullMap.getHeight());
        int endTileC = Math.min(startTileC + TILE_SIZE_IN_CHUNK, fullMap.getWidth());

        // Estimate max vertices for this chunk's data buffer
        // Each tile part (top, 2 sides, base top, 2 base sides) is 2 triangles = 6 vertices.
        // Max 6 parts = 36 vertices per tile.
        // Grass tufts are separate. Let's estimate 5 tufts * 4 verts/tuft = 20 verts for grass details.
        // Total ~56 vertices per tile.
        // Stride is now FLOATS_PER_VERTEX_TEXTURED (8).
        int maxTilesInThisChunk = TILE_SIZE_IN_CHUNK * TILE_SIZE_IN_CHUNK;
        int estimatedMaxVertsPerTile = 6 * 6; // 6 quads, 6 verts per quad
        int estimatedMaxGrassDetailVertsPerTile = 5 * 6; // 5 tufts, assuming each is a quad (though Grass.java uses 4 verts for a line)
        // Let's use a more generous estimate for buffer
        int bufferCapacity = maxTilesInThisChunk * (estimatedMaxVertsPerTile + estimatedMaxGrassDetailVertsPerTile) * Renderer.FLOATS_PER_VERTEX_TEXTURED;
        FloatBuffer chunkData = MemoryUtil.memAllocFloat(bufferCapacity);

        this.vertexCount = 0;
        float[] currentChunkBounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE};

        for (int r_local = 0; r_local < (endTileR - startTileR); r_local++) {
            for (int c_local = 0; c_local < (endTileC - startTileC); c_local++) {
                int actualR = startTileR + r_local;
                int actualC = startTileC + c_local;

                if (fullMap.isValid(actualR, actualC)) {
                    Tile tile = fullMap.getTile(actualR, actualC);
                    if (tile != null) {
                        // This method now needs to put 8 floats per vertex
                        this.vertexCount += rendererInstance.addSingleTileVerticesToBuffer_WorldSpace_ForChunk(
                                actualR, actualC, tile,
                                (inputHandler != null && actualR == inputHandler.getSelectedRow() && actualC == inputHandler.getSelectedCol()),
                                chunkData,
                                currentChunkBounds
                        );

                        // Grass details - this method also needs to ensure it puts 8 floats per vertex
                        // For this step, addGrassVerticesForTile_WorldSpace_ForChunk in Renderer will return 0.
                        if (tile.getType() == Tile.TileType.GRASS && tile.getTreeType() == Tile.TreeVisualType.NONE) {
                            this.vertexCount += rendererInstance.addGrassVerticesForTile_WorldSpace_ForChunk(
                                    actualR, actualC, tile, chunkData,
                                    currentChunkBounds);
                        }
                    }
                }
            }
        }

        this.boundingBox = new BoundingBox(currentChunkBounds[0], currentChunkBounds[1], currentChunkBounds[2], currentChunkBounds[3]);

        chunkData.flip();
        if (this.vertexCount > 0) {
            glBindVertexArray(vaoId);
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, chunkData, GL_STATIC_DRAW);

            // *** IMPORTANT: VAO Setup for 8 floats per vertex ***
            int stride = Renderer.FLOATS_PER_VERTEX_TEXTURED * Float.BYTES;

            // Position Attribute (location = 0)
            glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L);
            glEnableVertexAttribArray(0);

            // Color/Tint Attribute (location = 1)
            glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 2 * Float.BYTES); // Offset by 2 floats (position)
            glEnableVertexAttribArray(1);

            // Texture Coordinate Attribute (location = 2)
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, (2 + 4) * Float.BYTES); // Offset by 6 floats (pos + color)
            glEnableVertexAttribArray(2); // Make sure this attribute is enabled

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        } else {
            // If no vertices, still good to ensure VBO is cleared if it had old data
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, 0, GL_STATIC_DRAW); // Clear data
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
        MemoryUtil.memFree(chunkData);
    }

    public void render() {
        if (vaoId != 0 && vertexCount > 0) {
            glBindVertexArray(vaoId);
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
            // Unbinding VAO here is optional, Renderer can unbind last one.
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
        if (this.boundingBox != null && this.vertexCount > 0) {
            return this.boundingBox;
        }
        // Fallback rough estimate (from your provided code)
        float approxWorldCX = (chunkGridX + 0.5f - chunkGridY - 0.5f) * TILE_SIZE_IN_CHUNK * (TILE_WIDTH / 2.0f);
        float approxWorldCY = (chunkGridX + 0.5f + chunkGridY + 0.5f) * TILE_SIZE_IN_CHUNK * (TILE_HEIGHT / 2.0f);
        float spanX = TILE_SIZE_IN_CHUNK * TILE_WIDTH * 0.75f;
        float spanY = TILE_SIZE_IN_CHUNK * TILE_HEIGHT * 0.75f + ALTURA_MAXIMA * TILE_THICKNESS;
        return new BoundingBox(approxWorldCX - spanX, approxWorldCY - spanY, approxWorldCX + spanX, approxWorldCY + spanY);
    }
}
