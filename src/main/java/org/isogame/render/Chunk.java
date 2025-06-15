package org.isogame.render;

import org.isogame.constants.Constants;
import org.isogame.input.InputHandler;
import org.isogame.map.Map;
import org.isogame.tile.Tile;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL15.*;

public class Chunk {
    public final int chunkGridX, chunkGridY;
    public final int TILE_SIZE_IN_CHUNK;

    private int groundVaoId = 0, groundVboId = 0, groundVertexCount = 0;
    private int wallVaoId = 0, wallVboId = 0, wallVertexCount = 0;

    private BoundingBox boundingBox;
    private final List<Renderer.TreeData> treesInChunk = new ArrayList<>();
    private final List<Renderer.LooseRockData> looseRocksInChunk = new ArrayList<>();

    private static final int MAX_VERTICES_PER_TILE_COLUMN = (2 * 6) + (2 * 6) + (Constants.ALTURA_MAXIMA * 2 * 6);
    private static final int MAX_EXPECTED_FLOATS_PER_CHUNK = Constants.CHUNK_SIZE_TILES * Constants.CHUNK_SIZE_TILES * MAX_VERTICES_PER_TILE_COLUMN * Renderer.FLOATS_PER_VERTEX_TERRAIN_TEXTURED;

    public Chunk(int chunkGridX, int chunkGridY, int chunkSizeInTilesConstant) {
        this.chunkGridX = chunkGridX;
        this.chunkGridY = chunkGridY;
        this.TILE_SIZE_IN_CHUNK = chunkSizeInTilesConstant;
    }

    public void setupGLResources() {
        groundVaoId = glGenVertexArrays();
        groundVboId = glGenBuffers();
        configureVao(groundVaoId, groundVboId);

        wallVaoId = glGenVertexArrays();
        wallVboId = glGenBuffers();
        configureVao(wallVaoId, wallVboId);
    }

    private void configureVao(int vaoId, int vboId) {
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

    public List<Renderer.TreeData> getTreesInChunk() { return treesInChunk; }
    public List<Renderer.LooseRockData> getLooseRocksInChunk() { return looseRocksInChunk; }

    public void uploadGeometry(Map gameMap, InputHandler inputHandler, Renderer rendererInstance) {
        Tile[][] chunkLocalTiles = gameMap.getOrGenerateChunkTiles(this.chunkGridX, this.chunkGridY);
        if (chunkLocalTiles == null) {
            this.groundVertexCount = 0;
            this.wallVertexCount = 0;
            return;
        }

        treesInChunk.clear();
        looseRocksInChunk.clear();

        FloatBuffer groundDataBuffer = null;
        FloatBuffer wallDataBuffer = null;

        try {
            groundDataBuffer = MemoryUtil.memAllocFloat(MAX_EXPECTED_FLOATS_PER_CHUNK);
            wallDataBuffer = MemoryUtil.memAllocFloat(MAX_EXPECTED_FLOATS_PER_CHUNK / 4);

            float[] currentChunkVisualBounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE};
            int globalStartTileR = chunkGridY * TILE_SIZE_IN_CHUNK;
            int globalStartTileC = chunkGridX * TILE_SIZE_IN_CHUNK;

            int totalGroundVertices = 0;
            int totalWallVertices = 0;

            for (int r_local = 0; r_local < TILE_SIZE_IN_CHUNK; r_local++) {
                for (int c_local = 0; c_local < TILE_SIZE_IN_CHUNK; c_local++) {
                    Tile tile = chunkLocalTiles[r_local][c_local];
                    if (tile == null || tile.getType() == Tile.TileType.AIR) continue;

                    int actualR_mapArray = globalStartTileR + r_local;
                    int actualC_mapArray = globalStartTileC + c_local;
                    boolean isSelected = (inputHandler != null && actualR_mapArray == inputHandler.getSelectedRow() && actualC_mapArray == inputHandler.getSelectedCol());

                    if (tile.getType() == Tile.TileType.WOOD_WALL) {
                        totalWallVertices += rendererInstance.addWallVerticesToList(wallDataBuffer, actualR_mapArray, actualC_mapArray, tile, isSelected, currentChunkVisualBounds);
                    } else {
                        totalGroundVertices += rendererInstance.addSingleTileVerticesToList_WorldSpace_ForChunk(actualR_mapArray, actualC_mapArray, tile, isSelected, groundDataBuffer, currentChunkVisualBounds);
                    }

                    if (tile.getTreeType() != Tile.TreeVisualType.NONE) {
                        treesInChunk.add(new Renderer.TreeData(tile.getTreeType(), (float) actualC_mapArray, (float) actualR_mapArray, tile.getElevation()));
                    }
                    if (tile.getLooseRockType() != Tile.LooseRockType.NONE) {
                        looseRocksInChunk.add(new Renderer.LooseRockData(tile.getLooseRockType(), (float) actualC_mapArray, (float) actualR_mapArray, tile.getElevation()));
                    }
                }
            }

            this.boundingBox = new BoundingBox(currentChunkVisualBounds[0], currentChunkVisualBounds[1], currentChunkVisualBounds[2], currentChunkVisualBounds[3]);

            this.groundVertexCount = totalGroundVertices;
            uploadBufferToVBO(groundVboId, groundDataBuffer);

            this.wallVertexCount = totalWallVertices;
            uploadBufferToVBO(wallVboId, wallDataBuffer);

        } finally {
            if (groundDataBuffer != null) MemoryUtil.memFree(groundDataBuffer);
            if (wallDataBuffer != null) MemoryUtil.memFree(wallDataBuffer);
        }
    }

    private void uploadBufferToVBO(int vboId, FloatBuffer buffer) {
        buffer.flip();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    public void renderGround() {
        if (groundVaoId != 0 && groundVertexCount > 0) {
            glBindVertexArray(groundVaoId);
            glDrawArrays(GL_TRIANGLES, 0, groundVertexCount);
        }
    }

    public void renderWalls() {
        if (wallVaoId != 0 && wallVertexCount > 0) {
            glBindVertexArray(wallVaoId);
            glDrawArrays(GL_TRIANGLES, 0, wallVertexCount);
        }
    }

    public void cleanup() {
        if (groundVaoId != 0) glDeleteVertexArrays(groundVaoId);
        if (groundVboId != 0) glDeleteBuffers(groundVboId);
        if (wallVaoId != 0) glDeleteVertexArrays(wallVaoId);
        if (wallVboId != 0) glDeleteBuffers(wallVboId);
        groundVaoId = 0; groundVboId = 0; wallVaoId = 0; wallVboId = 0;
        groundVertexCount = 0; wallVertexCount = 0;
        treesInChunk.clear();
        looseRocksInChunk.clear();
    }

    public static class BoundingBox {
        public float minX, minY, maxX, maxY;
        public BoundingBox(float minX, float minY, float maxX, float maxY) {
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
        }
    }
    public BoundingBox getBoundingBox() { return this.boundingBox; }
}