package org.isogame.tile;

import org.isogame.game.Game;
import org.isogame.map.LightManager.ChunkCoordinate;
import org.isogame.constants.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TileEntityManager {
    // The key is a coordinate, the value is the TileEntity at that location
    private final Map<ChunkCoordinate, Map<String, TileEntity>> tileEntities = new HashMap<>();

    public void addTileEntity(TileEntity te) {
        ChunkCoordinate coord = new ChunkCoordinate(
                Math.floorDiv(te.getCol(), Constants.CHUNK_SIZE_TILES),
                Math.floorDiv(te.getRow(), Constants.CHUNK_SIZE_TILES)
        );
        String posKey = te.getRow() + ":" + te.getCol();

        tileEntities.computeIfAbsent(coord, k -> new HashMap<>()).put(posKey, te);
    }

    public void update(double deltaTime, Game game) {
        for (Map<String, TileEntity> chunkMap : tileEntities.values()) {
            for (TileEntity te : chunkMap.values()) {
                te.update(deltaTime, game);
            }
        }
    }

    public TileEntity getTileEntityAt(int row, int col) {
        ChunkCoordinate coord = new ChunkCoordinate(
                Math.floorDiv(col, Constants.CHUNK_SIZE_TILES),
                Math.floorDiv(row, Constants.CHUNK_SIZE_TILES)
        );
        String posKey = row + ":" + col;

        if (tileEntities.containsKey(coord)) {
            return tileEntities.get(coord).get(posKey);
        }
        return null;
    }
    /**
     * Gathers all TileEntity objects from all active chunks into a single list.
     * This is used by the Renderer to draw all interactive blocks.
     * @return A new list containing all active TileEntity objects.
     */
    public List<TileEntity> getAllTileEntities() {
        List<TileEntity> allEntities = new ArrayList<>();
        for (Map<String, TileEntity> chunkMap : tileEntities.values()) {
            allEntities.addAll(chunkMap.values());
        }
        return allEntities;
    }
}