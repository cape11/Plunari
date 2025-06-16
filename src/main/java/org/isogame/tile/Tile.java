package org.isogame.tile;

import static org.isogame.constants.Constants.MAX_LIGHT_LEVEL;

public class Tile {

    public enum TileType {

        AIR, // <<< ADDED AIR TILE TYPE
        WATER,
        SAND,
        GRASS,
        DIRT,
        ROCK,
        SNOW,
        STONE_BRICK,
        RED_BRICK,         // <-- ADD THIS
        WOOD_PLANK,        // <-- ADD THIS
        STONE_WALL_SMOOTH, // <-- ADD THIS
        STONE_WALL_ROUGH   // <-- ADD THIS
    }
    public enum LooseRockType { NONE, TYPE_1 , TYPE_2,
        TYPE_3, // New variation 2
        TYPE_4, // New variation 3
        TYPE_5, // New variation 4
        TYPE_6  }
    private LooseRockType looseRockType = LooseRockType.NONE; // New field for loose rocks

    public enum TreeVisualType {
        NONE,
        APPLE_TREE_FRUITING,
        PINE_TREE_SMALL,
        PALM_TREE
    }

    private TileType type;
    private int elevation; // Elevation might be less relevant for AIR tiles, or could be a default
    private TreeVisualType treeType;

    // --- NEW: Health for tile features like trees ---
    private int health = 0;
    private int maxHealth = 0;


    private byte skyLightLevel = 0;
    private byte blockLightLevel = 0;
    private boolean hasTorch = false;

    public Tile(TileType type, int elevation) {
        this.type = type;
        this.elevation = elevation;
        this.treeType = TreeVisualType.NONE;
    }



    public boolean isTransparentToSkyLight() {
        switch(type) {
            case AIR:   // <<< AIR IS TRANSPARENT
            case WATER: // Water can also be transparent if desired, or change this
                return true;
            default:
                return false;
        }
    }

    public boolean isSolidOpaqueBlock() {
        switch(type) {
            case AIR:   // <<< AIR IS NOT SOLID/OPAQUE
            case WATER: // Water is also not typically a "solid opaque block"
                return false;
            case GRASS:
            case DIRT:
            case SAND:
            case ROCK:
            case SNOW:
                return true;
            default:
                // For any unknown types, consider them solid by default, or choose other behavior
                return true;
        }

    }

    public TileType getType() { return type; }
    public void setType(TileType type) { this.type = type; }
    public int getElevation() { return elevation; }
    public void setElevation(int elevation) { this.elevation = elevation; }
    public TreeVisualType getTreeType() { return treeType; }

    public int getHealth() { return health; }


    public void takeDamage(int amount) {
        if (this.health > 0) {
            this.health -= amount;
        }
    }


    public void setTreeType(TreeVisualType treeType) {
        this.treeType = treeType;
        if (treeType != TreeVisualType.NONE) {
            this.maxHealth = 50; // All trees have 50 health
            this.health = this.maxHealth;
        } else {
            this.health = 0;
            this.maxHealth = 0;
        }
    }



    public byte getSkyLightLevel() { return skyLightLevel; }
    public void setSkyLightLevel(byte level) { this.skyLightLevel = (byte) Math.max(0, Math.min(MAX_LIGHT_LEVEL, level)); }
    public byte getBlockLightLevel() { return blockLightLevel; }
    public void setBlockLightLevel(byte level) { this.blockLightLevel = (byte) Math.max(0, Math.min(MAX_LIGHT_LEVEL, level)); }
    public boolean hasTorch() { return hasTorch; }
    public void setHasTorch(boolean hasTorch) { this.hasTorch = hasTorch; }
    public byte getFinalLightLevel() { return (byte) Math.max(skyLightLevel, blockLightLevel); }
    public LooseRockType getLooseRockType() {
        return looseRockType;
    }

    public void setLooseRockType(LooseRockType looseRockType) {
        this.looseRockType = looseRockType;
    }
}