package org.isogame.render;

import org.isogame.camera.CameraManager; // Required if TreeData screen anchors were calculated here
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
    public final int TILE_SIZE_IN_CHUNK;

    private int vaoId = 0;
    private int vboId = 0;
    private int vertexCount = 0;

    private BoundingBox boundingBox;
    private List<Renderer.TreeData> treesInChunk = new ArrayList<>(); // Store TreeData specific to this chunk

    public Chunk(int chunkGridX, int chunkGridY, int chunkSizeInTiles) {
        this.chunkGridX = chunkGridX;
        this.chunkGridY = chunkGridY;
        this.TILE_SIZE_IN_CHUNK = chunkSizeInTiles;
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

        treesInChunk.clear(); // Clear previous tree data for this chunk

        int maxTilesInThisChunk = TILE_SIZE_IN_CHUNK * TILE_SIZE_IN_CHUNK;
        int vertsForPedestal = 12;
        int vertsForTopFace = 6;
        int vertsForMaxElevatedSides = ALTURA_MAXIMA * 12;
        int estimatedMaxVertsPerTile_Updated = vertsForPedestal + vertsForTopFace + vertsForMaxElevatedSides;
        int estimatedMaxGrassDetailVertsPerTile = 0;
        int bufferCapacityFloats = maxTilesInThisChunk *
                (estimatedMaxVertsPerTile_Updated + estimatedMaxGrassDetailVertsPerTile) *
                Renderer.FLOATS_PER_VERTEX_TEXTURED;
        bufferCapacityFloats = (int) (bufferCapacityFloats * 1.1);

        FloatBuffer chunkData = null;
        try {
            chunkData = MemoryUtil.memAllocFloat(bufferCapacityFloats);
        } catch (OutOfMemoryError e) {
            System.err.println("Chunk: CRITICAL - OutOfMemoryError allocating FloatBuffer for chunk ("+chunkGridX+","+chunkGridY+") with capacity: " + bufferCapacityFloats + " floats.");
            System.err.println("Max tiles: " + maxTilesInThisChunk + ", Est. verts/tile: " + estimatedMaxVertsPerTile_Updated);
            throw e;
        }

        this.vertexCount = 0;
        float[] currentChunkBounds = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE};

        for (int r_local = 0; r_local < (endTileR - startTileR); r_local++) {
            for (int c_local = 0; c_local < (endTileC - startTileC); c_local++) {
                int actualR = startTileR + r_local;
                int actualC = startTileC + c_local;

                if (fullMap.isValid(actualR, actualC)) {
                    Tile tile = fullMap.getTile(actualR, actualC);
                    if (tile != null) {
                        int vertsAddedThisTile = rendererInstance.addSingleTileVerticesToBuffer_WorldSpace_ForChunk(
                                actualR, actualC, tile,
                                (inputHandler != null && actualR == inputHandler.getSelectedRow() && actualC == inputHandler.getSelectedCol()),
                                chunkData,
                                currentChunkBounds
                        );
                        this.vertexCount += vertsAddedThisTile;

                        // Collect TreeData for this chunk
                        if (tile.getTreeType() != Tile.TreeVisualType.NONE && tile.getType() != Tile.TileType.WATER) {
                            // TreeData now only stores model data (type, map coords, elevation)
                            treesInChunk.add(new Renderer.TreeData(tile.getTreeType(), (float)actualC, (float)actualR, tile.getElevation()));
                        }

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

            int stride = Renderer.FLOATS_PER_VERTEX_TEXTURED * Float.BYTES;
            glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 2 * Float.BYTES);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, (2 + 4) * Float.BYTES);
            glEnableVertexAttribArray(2);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        } else {
            glBindBuffer(GL_ARRAY_BUFFER, vboId);
            glBufferData(GL_ARRAY_BUFFER, 0, GL_STATIC_DRAW);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
        MemoryUtil.memFree(chunkData);
    }

    public void render() {
        if (vaoId != 0 && vertexCount > 0) {
            glBindVertexArray(vaoId);
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);
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
        treesInChunk.clear();
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
        float worldChunkOriginX = (chunkGridX * TILE_SIZE_IN_CHUNK - chunkGridY * TILE_SIZE_IN_CHUNK) * (TILE_WIDTH / 2.0f);
        float worldChunkOriginY = (chunkGridX * TILE_SIZE_IN_CHUNK + chunkGridY * TILE_SIZE_IN_CHUNK) * (TILE_HEIGHT / 2.0f);
        float chunkSpanX = TILE_SIZE_IN_CHUNK * TILE_WIDTH;
        float chunkSpanY = TILE_SIZE_IN_CHUNK * TILE_HEIGHT + ALTURA_MAXIMA * TILE_THICKNESS;
        return new BoundingBox(
                worldChunkOriginX - chunkSpanX / 2,
                worldChunkOriginY - chunkSpanY,
                worldChunkOriginX + chunkSpanX / 2,
                worldChunkOriginY
        );
    }
}
