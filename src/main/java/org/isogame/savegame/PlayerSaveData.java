package org.isogame.savegame;

import java.util.List;

public class PlayerSaveData {
    public float mapRow;
    public float mapCol;
    public List<InventorySlotSaveData> inventory;
    // Add other player stats if needed: levitating, animation state (less common to save)
}