package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.input.InputHandler;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL15.*;
import static org.isogame.constants.Constants.*;

public class Chunk {
    public final int chunkGridX, chunkGridY;
    public final int TILE_SIZE_IN_CHUNK; // Renamed from CHUNK_SIZE_TILES for clarity within class

    private int vaoId = 0;
    private int vboId = 0;
    private int vertexCount = 0;

    private BoundingBox boundingBox;
    private List<Renderer.TreeData> treesInChunk = new ArrayList<>();

    public Chunk(int chunkGridX, int chunkGridY, int chunkSizeInTiles) {
        this.chunkGridX = chunkGridX;
        this.chunkGridY = chunkGridY;
        this.TILE_SIZE_IN_CHUNK = chunkSizeInTiles; // Use constructor param
    }

    public void setupGLResources() {
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
    }

    public List<Renderer.TreeData> getTreesInChunk() {
        return treesInChunk;
    }

    public void uploadGeometry(Map fullMap, InputHandler inputHandler, Renderer rendererInstance, CameraManager cameraManager) {
        int startTileR = chunkGridY * TILE_SIZE_IN_CHUNK;
        int startTileC = chunkGridX * TILE_SIZE_IN_CHUNK;
        int endTileR = Math.min(startTileR + TILE_SIZE_IN_CHUNK, fullMap.getHeight());
        int endTileC = Math.min(startTileC + TILE_SIZE_IN_CHUNK, fullMap.getWidth());

        treesInChunk.clear();

        // Estimate buffer size
        // Each tile: pedestal (12 verts), top (6 verts), max elevation sides (ALTURA_MAXIMA * 12 verts)
        int maxVertsPerTileRough = 12 + 6 + (ALTURA_MAXIMA * 12);
        int bufferCapacityFloats = TILE_SIZE_IN_CHUNK * TILE_SIZE_IN_CHUNK * maxVertsPerTileRough * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED;
        bufferCapacityFloats = (int)(bufferCapacityFloats * 1.1); // 10% slack

        FloatBuffer chunkData = MemoryUtil.memAllocFloat(Math.max(1, bufferCapacityFloats)); // Ensure at least 1 float

        this.vertexCount = 0;
        float[] currentChunkBounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE};

        for (int r_local = 0; r_local < (endTileR - startTileR); r_local++) {
            for (int c_local = 0; c_local < (endTileC - startTileC); c_local++) {
                int actualR = startTileR + r_local;
                int actualC = startTileC + c_local;

                if (fullMap.isValid(actualR, actualC)) {
                    Tile tile = fullMap.getTile(actualR, actualC);
                    if (tile != null) {
                        // Check if buffer has enough remaining capacity before adding more vertices
                        // Estimate max vertices for one tile:
                        int estimatedVertsForNextTile = maxVertsPerTileRough; // A rough upper bound
                        if (chunkData.remaining() < estimatedVertsForNextTile * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED) {
                            System.err.println("Chunk ("+chunkGridX+","+chunkGridY+"): Ran out of buffer space. Current vertexCount: " + this.vertexCount +
                                    ". Buffer remaining: " + chunkData.remaining() + " floats. Needed approx: " +
                                    (estimatedVertsForNextTile * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED) + " floats.");
                            // Option: reallocate a larger buffer (complex) or just stop adding to this chunk for now.
                            // For now, we'll just stop and log.
                            break; // Stop adding more tiles to this chunk if buffer is full
                        }

                        int vertsAddedThisTile = rendererInstance.addSingleTileVerticesToBuffer_WorldSpace_ForChunk(
                                actualR, actualC, tile,
                                (inputHandler != null && actualR == inputHandler.getSelectedRow() && actualC == inputHandler.getSelectedCol()),
                                chunkData, currentChunkBounds);
                        this.vertexCount += vertsAddedThisTile;

                        if (tile.getTreeType() != Tile.TreeVisualType.NONE && tile.getType() != Tile.TileType.WATER) {
                            treesInChunk.add(new Renderer.TreeData(tile.getTreeType(), (float) actualC, (float) actualR, tile.getElevation()));
                        }
                        // Grass detail (if any) would be added here too
                    }
                }
            }
            if (chunkData.remaining() < maxVertsPerTileRough * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED) break; // Break outer loop too
        }

        this.boundingBox = new BoundingBox(currentChunkBounds[0], currentChunkBounds[1], currentChunkBounds[2], currentChunkBounds[3]);

        chunkData.flip();
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, chunkData, GL_STATIC_DRAW); // Upload data

        if (this.vertexCount > 0) {
            // Stride for terrain vertices (pos3, color4, uv2, light1)
            int stride = Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED * Float.BYTES;

            // Position attribute (vec3)
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
            glEnableVertexAttribArray(0);

            // Color attribute (vec4) - offset after 3 position floats
            glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 3 * Float.BYTES);
            glEnableVertexAttribArray(1);

            // Texture coordinate attribute (vec2) - offset after 3 pos + 4 color floats
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, (3 + 4) * Float.BYTES);
            glEnableVertexAttribArray(2);

            // Light attribute (float) - offset after 3 pos + 4 color + 2 texCoord floats
            glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, (3 + 4 + 2) * Float.BYTES);
            glEnableVertexAttribArray(3);
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        MemoryUtil.memFree(chunkData);
    }

    public void render() {
        if (vaoId != 0 && vertexCount > 0) {
            glBindVertexArray(vaoId);
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
            // glBindVertexArray(0); // Unbind after drawing this chunk if needed, but Renderer might manage global VAO state
        }
    }

    public void cleanup() {
        if (vaoId != 0) glDeleteVertexArrays(vaoId);
        if (vboId != 0) glDeleteBuffers(vboId);
        vaoId = 0; vboId = 0; vertexCount = 0;
        treesInChunk.clear();
    }

    public static class BoundingBox {
        public float minX, minY, maxX, maxY;
        public BoundingBox(float minX, float minY, float maxX, float maxY) {
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
        }
    }

    public BoundingBox getBoundingBox() {
        if (this.boundingBox != null && this.vertexCount > 0) return this.boundingBox;
        // Fallback for empty or uninitialized chunk
        float worldChunkOriginX = (chunkGridX * TILE_SIZE_IN_CHUNK - chunkGridY * TILE_SIZE_IN_CHUNK) * (TILE_WIDTH / 2.0f);
        float worldChunkOriginY = (chunkGridX * TILE_SIZE_IN_CHUNK + chunkGridY * TILE_SIZE_IN_CHUNK) * (TILE_HEIGHT / 2.0f);
        float chunkSpanX = TILE_SIZE_IN_CHUNK * TILE_WIDTH * 1.5f; // Generous fallback
        float chunkSpanY = TILE_SIZE_IN_CHUNK * TILE_HEIGHT * 1.5f + ALTURA_MAXIMA * TILE_THICKNESS;
        return new BoundingBox(worldChunkOriginX - chunkSpanX, worldChunkOriginY - chunkSpanY,
                worldChunkOriginX + chunkSpanX, worldChunkOriginY + chunkSpanY);
    }
}
