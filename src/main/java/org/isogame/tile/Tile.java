package org.isogame.tile;

import static org.isogame.constants.Constants.MAX_LIGHT_LEVEL;

public class Tile {

    public enum TileType {
        AIR, WATER, SAND, GRASS, DIRT, ROCK, SNOW, STONE_BRICK, RED_BRICK, WOOD_PLANK, STONE_WALL_SMOOTH, STONE_WALL_ROUGH
    }

    public enum LooseRockType {
        NONE, TYPE_1, TYPE_2, TYPE_3, TYPE_4, TYPE_5, TYPE_6
    }

    public enum TreeVisualType {
        NONE, APPLE_TREE_FRUITING, PINE_TREE_SMALL, PALM_TREE
    }

    private TileType type;
    private int elevation;
    private TreeVisualType treeType;
    private LooseRockType looseRockType = LooseRockType.NONE;

    private int health = 0;
    private int maxHealth = 0;

    private byte skyLightLevel = 0;
    private byte blockLightLevel = 0;
    private boolean hasTorch = false;

    // --- NEW: Fields for tree shake effect ---
    public double treeShakeTimer = 0.0;
    private static final double TREE_SHAKE_DURATION = 0.2; // How long the shake lasts in seconds

    public Tile(TileType type, int elevation) {
        this.type = type;
        this.elevation = elevation;
        this.treeType = TreeVisualType.NONE;
    }

    /**
     * --- NEW: Starts the tree shake timer. ---
     * This is called when a projectile hits the tree.
     */
    public void startShake() {
        if (this.treeType != TreeVisualType.NONE) {
            this.treeShakeTimer = TREE_SHAKE_DURATION;
        }
    }

    public void takeDamage(int amount) {
        if (this.health > 0) {
            this.health -= amount;
        }
    }

    public boolean isTransparentToSkyLight() {
        return type == TileType.AIR || type == TileType.WATER;
    }

    public boolean isSolidOpaqueBlock() {
        switch (type) {
            case AIR:
            case WATER:
                return false;
            default:
                return true;
        }
    }

    // --- Getters and Setters ---
    public TileType getType() { return type; }
    public void setType(TileType type) { this.type = type; }
    public int getElevation() { return elevation; }
    public void setElevation(int elevation) { this.elevation = elevation; }
    public TreeVisualType getTreeType() { return treeType; }
    public int getHealth() { return health; }

    public void setTreeType(TreeVisualType treeType) {
        this.treeType = treeType;
        if (treeType != TreeVisualType.NONE) {
            this.maxHealth = 50;
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
    public LooseRockType getLooseRockType() { return looseRockType; }
    public void setLooseRockType(LooseRockType looseRockType) { this.looseRockType = looseRockType; }
}
