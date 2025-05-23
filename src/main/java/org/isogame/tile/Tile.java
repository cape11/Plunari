package org.isogame.tile;

import static org.isogame.constants.Constants.MAX_LIGHT_LEVEL;

public class Tile {

    public enum TileType {
        WATER, SAND, GRASS, ROCK, SNOW
    }

    public enum TreeVisualType {
        NONE,
        APPLE_TREE_FRUITING,
        PINE_TREE_SMALL
    }

    private TileType type;
    private int elevation;
    private TreeVisualType treeType;

    // Lighting properties
    private byte skyLightLevel = 0;   // Light from the sky (0-15)
    private byte blockLightLevel = 0; // Light from torches, etc. (0-15)
    private boolean hasTorch = false; // True if this tile itself is a torch source

    public Tile(TileType type, int elevation) {
        this.type = type;
        this.elevation = elevation;
        this.treeType = TreeVisualType.NONE;
        // Initialize light levels (sky light will be set by LightManager)
        this.skyLightLevel = 0; // Default to dark, LightManager will illuminate
        this.blockLightLevel = 0;
    }

    public TileType getType() {
        return type;
    }

    public void setType(TileType type) {
        this.type = type;
    }

    public int getElevation() {
        return elevation;
    }

    public void setElevation(int elevation) {
        this.elevation = elevation;
    }

    public TreeVisualType getTreeType() {
        return treeType;
    }

    public void setTreeType(TreeVisualType treeType) {
        this.treeType = treeType;
    }

    // --- Lighting Getters and Setters ---
    public byte getSkyLightLevel() {
        return skyLightLevel;
    }

    public void setSkyLightLevel(byte level) {
        this.skyLightLevel = (byte) Math.max(0, Math.min(MAX_LIGHT_LEVEL, level));
    }

    public byte getBlockLightLevel() {
        return blockLightLevel;
    }

    public void setBlockLightLevel(byte level) {
        this.blockLightLevel = (byte) Math.max(0, Math.min(MAX_LIGHT_LEVEL, level));
    }

    public boolean hasTorch() {
        return hasTorch;
    }

    public void setHasTorch(boolean hasTorch) {
        this.hasTorch = hasTorch;
    }

    /**
     * Gets the effective light level for rendering this tile.
     * This is the maximum of sky light and block light.
     * @return The final light level (0-15).
     */
    public byte getFinalLightLevel() {
        return (byte) Math.max(skyLightLevel, blockLightLevel);
    }

    /**
     * Checks if light can pass through this tile.
     * For example, air, water (to some extent), and glass would be transparent.
     * Solid blocks like GRASS, ROCK, SAND, SNOW are generally not.
     * @return true if light can pass, false otherwise.
     */
    public boolean isTransparentToLight() {
        // Water is somewhat transparent to sky light, but might block block light propagation more.
        // For simplicity here, let's say only WATER allows light through easily.
        // Grass, Rock, Sand, Snow are opaque.
        return type == TileType.WATER; // Or more complex logic: e.g. type == TileType.AIR (if you add it)
    }
}
