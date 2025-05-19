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
    public final int TILE_SIZE_IN_CHUNK;

    private int vaoId = 0;
    private int vboId = 0;
    private int vertexCount = 0;

    private BoundingBox boundingBox;
    private List<Renderer.TreeData> treesInChunk = new ArrayList<>();

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

        treesInChunk.clear();

        int maxTilesInThisChunk = TILE_SIZE_IN_CHUNK * TILE_SIZE_IN_CHUNK;
        int vertsForPedestal = 12;
        int vertsForTopFace = 6;
        int vertsForMaxElevatedSides = ALTURA_MAXIMA * 12;
        int estimatedMaxVertsPerTile_Updated = vertsForPedestal + vertsForTopFace + vertsForMaxElevatedSides;
        int bufferCapacityFloats = maxTilesInThisChunk * estimatedMaxVertsPerTile_Updated * Renderer.FLOATS_PER_VERTEX_TEXTURED;
        bufferCapacityFloats = (int) (bufferCapacityFloats * 1.2);

        FloatBuffer chunkData = null;
        try {
            chunkData = MemoryUtil.memAllocFloat(bufferCapacityFloats);
        } catch (OutOfMemoryError e) {
            System.err.println("Chunk OOM: " + bufferCapacityFloats);
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
                        this.vertexCount += rendererInstance.addSingleTileVerticesToBuffer_WorldSpace_ForChunk(
                                actualR, actualC, tile,
                                (inputHandler != null && actualR == inputHandler.getSelectedRow() && actualC == inputHandler.getSelectedCol()),
                                chunkData, currentChunkBounds);

                        if (tile.getTreeType() != Tile.TreeVisualType.NONE && tile.getType() != Tile.TileType.WATER) {
                            treesInChunk.add(new Renderer.TreeData(tile.getTreeType(), (float)actualC, (float)actualR, tile.getElevation()));
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

            glVertexAttribPointer(0, Renderer.POSITION_COMPONENT_COUNT, GL_FLOAT, false, Renderer.STRIDE_TEXTURED, Renderer.POSITION_OFFSET);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, Renderer.COLOR_COMPONENT_COUNT, GL_FLOAT, false, Renderer.STRIDE_TEXTURED, Renderer.COLOR_OFFSET);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(2, Renderer.TEXCOORD_COMPONENT_COUNT, GL_FLOAT, false, Renderer.STRIDE_TEXTURED, Renderer.TEXCOORD_OFFSET);
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
        if (vaoId != 0 && vertexCount > 0) { glBindVertexArray(vaoId); glDrawArrays(GL_TRIANGLES, 0, vertexCount); }
    }
    public void cleanup() {
        if (vaoId != 0) glDeleteVertexArrays(vaoId); if (vboId != 0) glDeleteBuffers(vboId);
        vaoId = 0; vboId = 0; vertexCount = 0; treesInChunk.clear();
    }
    public static class BoundingBox {
        public float minX, minY, maxX, maxY;
        public BoundingBox(float minX, float minY, float maxX, float maxY) { this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY; }
    }
    public BoundingBox getBoundingBox() {
        if (this.boundingBox != null && this.vertexCount > 0) return this.boundingBox;
        float worldChunkOriginX = (chunkGridX*TILE_SIZE_IN_CHUNK - chunkGridY*TILE_SIZE_IN_CHUNK) * (TILE_WIDTH/2.0f);
        float worldChunkOriginY = (chunkGridX*TILE_SIZE_IN_CHUNK + chunkGridY*TILE_SIZE_IN_CHUNK) * (TILE_HEIGHT/2.0f);
        float chunkSpanX = TILE_SIZE_IN_CHUNK * TILE_WIDTH;
        float chunkSpanY = TILE_SIZE_IN_CHUNK * TILE_HEIGHT + ALTURA_MAXIMA * TILE_THICKNESS;
        return new BoundingBox(worldChunkOriginX - chunkSpanX/2, worldChunkOriginY - chunkSpanY, worldChunkOriginX + chunkSpanX/2, worldChunkOriginY);
    }
}
