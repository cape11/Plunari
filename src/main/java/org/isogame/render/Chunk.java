package org.isogame.render;

import org.isogame.camera.CameraManager;
import org.isogame.input.InputHandler;
import org.isogame.map.Map;
import org.isogame.map.ChunkData;
import org.isogame.tile.Tile;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL15.*;
import static org.isogame.constants.Constants.*;

public class Chunk { // This is org.isogame.render.Chunk
    public final int chunkGridX, chunkGridY;

    private int vaoId = 0;
    private int vboId = 0;
    private int vertexCount = 0;

    private BoundingBox boundingBox;
    private final List<Renderer.TreeData> treesInChunk = new ArrayList<>();

    public Chunk(int chunkGridX, int chunkGridY, int chunkSizeInTiles_ignored) {
        this.chunkGridX = chunkGridX;
        this.chunkGridY = chunkGridY;
    }

    public void setupGLResources() {
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
    }

    public List<Renderer.TreeData> getTreesInChunk() {
        return treesInChunk;
    }

    public void uploadGeometry(Map mapRefForTileAccess, InputHandler inputHandler, Renderer rendererInstance,
                               CameraManager cameraManager, ChunkData chunkData) {
        if (chunkData == null) {
            this.vertexCount = 0;
            this.treesInChunk.clear();
            this.boundingBox = new BoundingBox(0,0,0,0);
            if (vaoId != 0 && vboId != 0) {
                glBindVertexArray(vaoId);
                glBindBuffer(GL_ARRAY_BUFFER, vboId);
                glBufferData(GL_ARRAY_BUFFER, 0, GL_STATIC_DRAW);
                glBindBuffer(GL_ARRAY_BUFFER, 0);
                glBindVertexArray(0);
            }
            return;
        }

        treesInChunk.clear();

        int maxVertsPerTileRough = 12 + 6 + (ALTURA_MAXIMA * 12);
        int bufferCapacityFloats = CHUNK_SIZE_TILES * CHUNK_SIZE_TILES * maxVertsPerTileRough * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED;
        bufferCapacityFloats = Math.max(1, (int)(bufferCapacityFloats * 1.1));

        FloatBuffer geometryBuffer = MemoryUtil.memAllocFloat(bufferCapacityFloats);

        this.vertexCount = 0;
        float[] currentChunkBounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

        int worldStartX = chunkGridX * CHUNK_SIZE_TILES;
        int worldStartY = chunkGridY * CHUNK_SIZE_TILES;

        for (int localY = 0; localY < CHUNK_SIZE_TILES; localY++) {
            for (int localX = 0; localX < CHUNK_SIZE_TILES; localX++) {
                Tile tile = chunkData.getTile(localX, localY);
                if (tile != null) {
                    if (geometryBuffer.remaining() < maxVertsPerTileRough * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED) {
                        System.err.println("Chunk ("+chunkGridX+","+chunkGridY+"): Ran out of buffer space during geometry generation.");
                        break;
                    }

                    int worldR = worldStartY + localY;
                    int worldC = worldStartX + localX;

                    int vertsAddedThisTile = rendererInstance.addSingleTileVerticesToBuffer_WorldSpace_ForChunk(
                            worldR, worldC, tile,
                            (inputHandler != null && worldR == inputHandler.getSelectedRow() && worldC == inputHandler.getSelectedCol()),
                            geometryBuffer, currentChunkBounds);
                    this.vertexCount += vertsAddedThisTile;

                    if (tile.getTreeType() != Tile.TreeVisualType.NONE && tile.getType() != Tile.TileType.WATER) {
                        treesInChunk.add(new Renderer.TreeData(tile.getTreeType(), (float) worldC, (float) worldR, tile.getElevation()));
                    }
                }
            }
            if (geometryBuffer.remaining() < maxVertsPerTileRough * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED) break;
        }

        this.boundingBox = new BoundingBox(currentChunkBounds[0], currentChunkBounds[1], currentChunkBounds[2], currentChunkBounds[3]);

        geometryBuffer.flip();
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, geometryBuffer, GL_STATIC_DRAW);

        if (this.vertexCount > 0) {
            int stride = Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED * Float.BYTES;
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 3 * Float.BYTES);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, (3 + 4) * Float.BYTES);
            glEnableVertexAttribArray(2);
            glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, (3 + 4 + 2) * Float.BYTES);
            glEnableVertexAttribArray(3);
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        MemoryUtil.memFree(geometryBuffer);
    }

    public void render() {
        if (vaoId != 0 && vertexCount > 0) {
            glBindVertexArray(vaoId);
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
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
        if (this.boundingBox != null && this.vertexCount > 0 &&
                this.boundingBox.minX != Float.MAX_VALUE) { // Check if bounds were actually calculated
            return this.boundingBox;
        }
        // Fallback for empty or uninitialized chunk (or if bounds were not properly set)
        float worldChunkTileOriginX = chunkGridX * CHUNK_SIZE_TILES;
        float worldChunkTileOriginY = chunkGridY * CHUNK_SIZE_TILES;

        float minPossibleIsoX = (worldChunkTileOriginX - (worldChunkTileOriginY + CHUNK_SIZE_TILES -1)) * (TILE_WIDTH/2.0f) - TILE_WIDTH;
        float maxPossibleIsoX = ((worldChunkTileOriginX + CHUNK_SIZE_TILES -1) - worldChunkTileOriginY) * (TILE_WIDTH/2.0f) + TILE_WIDTH;
        float minPossibleIsoY = (worldChunkTileOriginX + worldChunkTileOriginY) * (TILE_HEIGHT/2.0f) - (ALTURA_MAXIMA * TILE_THICKNESS) - TILE_HEIGHT - BASE_THICKNESS;
        float maxPossibleIsoY = ((worldChunkTileOriginX + CHUNK_SIZE_TILES -1) + (worldChunkTileOriginY + CHUNK_SIZE_TILES -1)) * (TILE_HEIGHT/2.0f) + BASE_THICKNESS + TILE_HEIGHT;

        return new BoundingBox(minPossibleIsoX, minPossibleIsoY, maxPossibleIsoX, maxPossibleIsoY);
    }
}
