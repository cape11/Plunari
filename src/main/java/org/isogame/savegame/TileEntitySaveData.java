package org.isogame.savegame;

import java.util.HashMap;
import java.util.Map;

public class TileEntitySaveData {
    public String type; // e.g., "FURNACE"
    public int row;
    public int col;

    // A flexible way to store extra data for different tile entities
    public Map<String, Object> customData = new HashMap<>();
}