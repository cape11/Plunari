package org.isogame.savegame;

// This new class holds the data for any single entity to be saved.
public class EntitySaveData {
    // We need to know what KIND of entity this is to recreate it.
    public String entityType; // e.g., "PLAYER", "COW", "SLIME"

    public float mapRow;
    public float mapCol;
    public int health;
    // You can add other things here later, like current direction.
}
