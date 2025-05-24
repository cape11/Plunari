package org.isogame.tile;

import static org.isogame.constants.Constants.MAX_LIGHT_LEVEL;

public class Tile {

    public enum TileType {
        WATER, SAND, GRASS, ROCK, SNOW
    }

    public enum TreeVisualType {
        NONE,
        APPLE_TREE_FRUITING,
        PINE_TREE_SMALL,
        PALM_TREE // Example, ensure Renderer handles this if used
    }

    private TileType type;
    private int elevation;
    private TreeVisualType treeType;

    // Lighting properties
    private byte skyLightLevel = 0;     // Light received from the sky
    private byte blockLightLevel = 0;   // Light received from light-emitting blocks (e.g., torches)
    private boolean hasTorch = false;   // Does this tile itself have a torch?

    /**
     * Constructor for Tile.
     * @param type The material type of the tile.
     * @param elevation The elevation level of the tile.
     */
    public Tile(TileType type, int elevation) {
        this.type = type;
        this.elevation = elevation;
        this.treeType = TreeVisualType.NONE; // Default to no tree
        // Light levels are initialized to 0 by default, will be calculated by LightManager
    }

    // Getters and Setters
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

    public byte getSkyLightLevel() {
        return skyLightLevel;
    }

    public void setSkyLightLevel(byte level) {
        // Clamp light level to valid range
        this.skyLightLevel = (byte) Math.max(0, Math.min(MAX_LIGHT_LEVEL, level));
    }

    public byte getBlockLightLevel() {
        return blockLightLevel;
    }

    public void setBlockLightLevel(byte level) {
        // Clamp light level to valid range
        this.blockLightLevel = (byte) Math.max(0, Math.min(MAX_LIGHT_LEVEL, level));
    }

    public boolean hasTorch() {
        return hasTorch;
    }

    public void setHasTorch(boolean hasTorch) {
        this.hasTorch = hasTorch;
    }

    /**
     * Calculates the final effective light level for rendering this tile.
     * This is the maximum of sky light and block light affecting this tile.
     * @return The final light level (0-MAX_LIGHT_LEVEL).
     */
    public byte getFinalLightLevel() {
        return (byte) Math.max(skyLightLevel, blockLightLevel);
    }

    /**
     * Determines if this tile type is transparent to light propagation.
     * "Transparent" means light can pass through it with some reduction (opacity cost).
     * Non-transparent (opaque) blocks typically stop light entirely or have a very high opacity cost.
     *
     * Note: Air (represented by a null Tile object in the map grid) is handled separately
     * by the LightManager and is considered fully transparent.
     *
     * @return True if light can pass through this tile type, false otherwise.
     */
    public boolean isTransparentToLight() {
        // Water is somewhat transparent.
        // Other solid blocks are generally opaque.
        // This can be expanded (e.g., glass).
        return type == TileType.WATER;
    }
}
