package org.isogame.render;

import org.isogame.input.InputHandler;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL15.*;
// import static org.lwjgl.opengl.GL11.*; // Not strictly needed for VAO/VBO ops
import static org.isogame.constants.Constants.*; // Make sure ALTURA_MAXIMA is here


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
        // Ensure TILE_SIZE_IN_CHUNK is initialized, e.g., from Constants.CHUNK_SIZE_TILES
        // If chunkSizeInTiles is passed from Renderer, ensure it's Constants.CHUNK_SIZE_TILES
        this.TILE_SIZE_IN_CHUNK = chunkSizeInTiles;
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

        // --- MODIFIED BUFFER CAPACITY CALCULATION ---
        int maxTilesInThisChunk = TILE_SIZE_IN_CHUNK * TILE_SIZE_IN_CHUNK;

        // Maximum vertices per tile:
        // 1. Pedestal sides: 2 quads = 12 vertices
        // 2. Top face: 1 quad (or 2 triangles for diamond) = 6 vertices
        // 3. Elevated sides: ALTURA_MAXIMA levels * 2 quads/level = ALTURA_MAXIMA * 12 vertices
        //    (The "Base Top" beneath an elevated tile is now part of the top face of the conceptual tile below it,
        //     or the pedestal top, effectively reducing double counting for this max estimate.)
        int vertsForPedestal = 12;
        int vertsForTopFace = 6;
        // ALTURA_MAXIMA must be accessible here, e.g. from Constants.ALTURA_MAXIMA
        int vertsForMaxElevatedSides = ALTURA_MAXIMA * 12;

        int estimatedMaxVertsPerTile_Updated = vertsForPedestal + vertsForTopFace + vertsForMaxElevatedSides;

        // Grass detail vertices are currently 0 as addGrassVerticesForTile_WorldSpace_ForChunk is a stub.
        // If you implement it later, add its potential vertex count here.
        int estimatedMaxGrassDetailVertsPerTile = 0; // Currently 0

        // Total buffer capacity in floats
        int bufferCapacityFloats = maxTilesInThisChunk *
                (estimatedMaxVertsPerTile_Updated + estimatedMaxGrassDetailVertsPerTile) *
                Renderer.FLOATS_PER_VERTEX_TEXTURED;

        // Add a small safety margin (e.g., 10-20%) to be safe, though the calculation should be close.
        bufferCapacityFloats = (int) (bufferCapacityFloats * 1.1);

        FloatBuffer chunkData = null;
        try {
            chunkData = MemoryUtil.memAllocFloat(bufferCapacityFloats);
        } catch (OutOfMemoryError e) {
            System.err.println("Chunk: CRITICAL - OutOfMemoryError allocating FloatBuffer for chunk ("+chunkGridX+","+chunkGridY+") with capacity: " + bufferCapacityFloats + " floats.");
            System.err.println("Max tiles: " + maxTilesInThisChunk + ", Est. verts/tile: " + estimatedMaxVertsPerTile_Updated);
            // Handle this error gracefully, maybe skip chunk update or reduce detail.
            // For now, we'll let it propagate if it happens, but logging is important.
            throw e;
        }
        // --- END OF MODIFIED BUFFER CAPACITY CALCULATION ---


        this.vertexCount = 0;
        // Initialize with broad values, will be shrunk by actual geometry
        float[] currentChunkBounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE};

        for (int r_local = 0; r_local < (endTileR - startTileR); r_local++) {
            for (int c_local = 0; c_local < (endTileC - startTileC); c_local++) {
                int actualR = startTileR + r_local;
                int actualC = startTileC + c_local;

                if (fullMap.isValid(actualR, actualC)) {
                    Tile tile = fullMap.getTile(actualR, actualC);
                    if (tile != null) {
                        int countBefore = chunkData.position();
                        int vertsAddedThisTile = rendererInstance.addSingleTileVerticesToBuffer_WorldSpace_ForChunk(
                                actualR, actualC, tile,
                                (inputHandler != null && actualR == inputHandler.getSelectedRow() && actualC == inputHandler.getSelectedCol()),
                                chunkData,
                                currentChunkBounds
                        );
                        this.vertexCount += vertsAddedThisTile;

                        // This call currently adds 0 vertices as per the stub in Renderer.java
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

        chunkData.flip(); // Prepare buffer for reading by OpenGL
        if (this.vertexCount > 0) {
            glBindVertexArray(vaoId);
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, chunkData, GL_STATIC_DRAW); // Upload data

            int stride = Renderer.FLOATS_PER_VERTEX_TEXTURED * Float.BYTES;
            // Position Attribute (location = 0)
            glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L);
            glEnableVertexAttribArray(0);
            // Color/Tint Attribute (location = 1)
            glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 2 * Float.BYTES); // Offset by 2 floats (position)
            glEnableVertexAttribArray(1);
            // Texture Coordinate Attribute (location = 2)
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, (2 + 4) * Float.BYTES); // Offset by 6 floats (pos + color)
            glEnableVertexAttribArray(2);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        } else {
            // If no vertices (e.g., empty chunk), ensure VBO is cleared if it had old data
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, 0, GL_STATIC_DRAW); // Clear data
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
        MemoryUtil.memFree(chunkData); // IMPORTANT: Free the native buffer
    }

    public void render() {
        if (vaoId != 0 && vertexCount > 0) {
            glBindVertexArray(vaoId);
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
            // Unbinding VAO here is optional; Renderer can unbind the last used VAO once per frame.
            // glBindVertexArray(0);
        }
    }

    public void cleanup() {
        if (vaoId != 0) {
            glDeleteVertexArrays(vaoId);
            vaoId = 0;
        }
        if (vboId != 0) {
            glDeleteBuffers(vboId);
            vboId = 0;
        }
        vertexCount = 0;
    }

    public static class BoundingBox {
        public float minX, minY, maxX, maxY;
        public BoundingBox(float minX, float minY, float maxX, float maxY) {
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
        }
    }
    public BoundingBox getBoundingBox() {
        if (this.boundingBox != null && this.vertexCount > 0) { // Check if valid box exists
            return this.boundingBox;
        }
        // Fallback: Provide a rough, potentially larger estimate if no precise box calculated yet
        // This helps avoid null pointer issues if getBoundingBox is called before geometry upload.
        float worldChunkOriginX = (chunkGridX * TILE_SIZE_IN_CHUNK - chunkGridY * TILE_SIZE_IN_CHUNK) * (TILE_WIDTH / 2.0f);
        float worldChunkOriginY = (chunkGridX * TILE_SIZE_IN_CHUNK + chunkGridY * TILE_SIZE_IN_CHUNK) * (TILE_HEIGHT / 2.0f);
        float chunkSpanX = TILE_SIZE_IN_CHUNK * TILE_WIDTH; // Max width extent
        float chunkSpanY = TILE_SIZE_IN_CHUNK * TILE_HEIGHT + ALTURA_MAXIMA * TILE_THICKNESS; // Max height extent

        // A very rough bounding box based on chunk grid coordinates and max possible dimensions
        return new BoundingBox(
                worldChunkOriginX - chunkSpanX / 2,
                worldChunkOriginY - chunkSpanY, // Consider full height range from lowest possible base to highest top
                worldChunkOriginX + chunkSpanX / 2,
                worldChunkOriginY
        );
    }
}
