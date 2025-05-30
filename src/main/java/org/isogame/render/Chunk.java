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

    // New fields for VBO optimization
    private boolean vboInitialized = false;
    private int vboCapacityBytes = 0;

    public Chunk(int chunkGridX, int chunkGridY, int chunkSizeInTiles) {
        this.chunkGridX = chunkGridX;
        this.chunkGridY = chunkGridY;
        this.TILE_SIZE_IN_CHUNK = chunkSizeInTiles; // Use constructor param
    }

    public void setupGLResources() {
        // Generate VAO and VBO IDs. Data will be uploaded in uploadGeometry.
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        // vboInitialized remains false until first data upload
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
        // Each vertex: Pos(3) + Color(4) + UV(2) + Light(1) = 10 floats
        int maxVertsPerTileRough = 12 + 6 + (ALTURA_MAXIMA * 12); // Rough estimate
        int bufferCapacityFloats = TILE_SIZE_IN_CHUNK * TILE_SIZE_IN_CHUNK * maxVertsPerTileRough * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED;
        bufferCapacityFloats = (int)(bufferCapacityFloats * 1.1); // 10% slack

        FloatBuffer chunkData = MemoryUtil.memAllocFloat(Math.max(1, bufferCapacityFloats));

        this.vertexCount = 0; // Reset vertex count for the new geometry
        float[] currentChunkBounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE};

        for (int r_local = 0; r_local < (endTileR - startTileR); r_local++) {
            for (int c_local = 0; c_local < (endTileC - startTileC); c_local++) {
                int actualR = startTileR + r_local;
                int actualC = startTileC + c_local;

                if (fullMap.isValid(actualR, actualC)) {
                    Tile tile = fullMap.getTile(actualR, actualC);
                    if (tile != null) {
                        int estimatedVertsForNextTile = maxVertsPerTileRough;
                        if (chunkData.remaining() < estimatedVertsForNextTile * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED) {
                            System.err.println("Chunk ("+chunkGridX+","+chunkGridY+"): Ran out of estimated FloatBuffer space during data gathering. Current vertexCount: " + this.vertexCount +
                                    ". Buffer remaining: " + chunkData.remaining() + " floats. Needed approx: " +
                                    (estimatedVertsForNextTile * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED) + " floats.");
                            break;
                        }

                        int vertsAddedThisTile = rendererInstance.addSingleTileVerticesToBuffer_WorldSpace_ForChunk(
                                actualR, actualC, tile,
                                (inputHandler != null && actualR == inputHandler.getSelectedRow() && actualC == inputHandler.getSelectedCol()),
                                chunkData, currentChunkBounds);
                        this.vertexCount += vertsAddedThisTile;

                        if (tile.getTreeType() != Tile.TreeVisualType.NONE && tile.getType() != Tile.TileType.WATER) {
                            treesInChunk.add(new Renderer.TreeData(tile.getTreeType(), (float) actualC, (float) actualR, tile.getElevation()));
                        }
                    }
                }
            }
            if (chunkData.remaining() < maxVertsPerTileRough * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED && (r_local < (endTileR - startTileR) -1) ) {
                System.err.println("Chunk ("+chunkGridX+","+chunkGridY+"): Breaking outer loop due to insufficient FloatBuffer space.");
                break;
            }
        }

        this.boundingBox = new BoundingBox(currentChunkBounds[0], currentChunkBounds[1], currentChunkBounds[2], currentChunkBounds[3]);

        chunkData.flip(); // Prepare the buffer for reading by OpenGL

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        int requiredBytes = chunkData.limit() * Float.BYTES;

        if (!vboInitialized || requiredBytes > vboCapacityBytes) {
            // First time uploading, or the new data is larger than current VBO capacity.
            // This path will also be taken if vertexCount is 0, ensuring the buffer is still created.
            // System.out.println("Chunk ("+chunkGridX+","+chunkGridY+"): Initializing/Resizing VBO with glBufferData. Required Bytes: " + requiredBytes + ", Vertex Count: " + this.vertexCount);
            glBufferData(GL_ARRAY_BUFFER, chunkData, GL_DYNAMIC_DRAW); // Use GL_DYNAMIC_DRAW
            vboCapacityBytes = requiredBytes > 0 ? requiredBytes : (chunkData.capacity() * Float.BYTES); // Store new capacity, ensure it's not 0
            vboInitialized = true;

            // Set vertex attribute pointers only when VBO is (re)created or its format changes.
            // The VAO captures these attribute configurations.
            // This block should execute even if vertexCount is 0 to set up attributes for a potentially empty buffer.
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

        } else if (requiredBytes > 0) { // Only use glBufferSubData if there's actual data to upload
            // VBO is already initialized and has enough capacity, just update its content
            // System.out.println("Chunk ("+chunkGridX+","+chunkGridY+"): Updating VBO with glBufferSubData. Bytes: " + requiredBytes + ", Vertex Count: " + this.vertexCount);
            glBufferSubData(GL_ARRAY_BUFFER, 0, chunkData);
        } else {
            // If requiredBytes is 0 (meaning vertexCount is 0), we don't need to call glBufferSubData.
            // The buffer might have been initialized previously (e.g., if the chunk became empty).
            // System.out.println("Chunk ("+chunkGridX+","+chunkGridY+"): No data to upload (vertexCount is 0). VBO capacity: " + vboCapacityBytes);
        }


        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        MemoryUtil.memFree(chunkData); // Free the client-side buffer
    }

    public void render() {
        if (vaoId != 0 && vertexCount > 0) { // Only render if there are vertices
            glBindVertexArray(vaoId);
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
            // glBindVertexArray(0); // Usually unbind after all drawing of this type is done, managed by Renderer
        }
    }

    public void cleanup() {
        if (vaoId != 0) glDeleteVertexArrays(vaoId);
        if (vboId != 0) glDeleteBuffers(vboId);
        vaoId = 0; vboId = 0; vertexCount = 0;
        vboInitialized = false; vboCapacityBytes = 0;
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
        // Fallback for empty or uninitialized chunk, or chunk that became empty
        float worldChunkOriginX = (chunkGridX * TILE_SIZE_IN_CHUNK - chunkGridY * TILE_SIZE_IN_CHUNK) * (TILE_WIDTH / 2.0f);
        float worldChunkOriginY = (chunkGridX * TILE_SIZE_IN_CHUNK + chunkGridY * TILE_SIZE_IN_CHUNK) * (TILE_HEIGHT / 2.0f);
        float chunkSpanX = TILE_SIZE_IN_CHUNK * TILE_WIDTH * 1.5f; // Generous fallback
        float chunkSpanY = TILE_SIZE_IN_CHUNK * TILE_HEIGHT * 1.5f + ALTURA_MAXIMA * TILE_THICKNESS;
        return new BoundingBox(worldChunkOriginX - chunkSpanX, worldChunkOriginY - chunkSpanY,
                worldChunkOriginX + chunkSpanX, worldChunkOriginY + chunkSpanY);
    }
}
