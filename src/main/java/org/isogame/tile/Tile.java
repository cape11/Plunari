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
        PALM_TREE // Ensure you have this if you plan to use PALM_TREE case in Renderer
    }

    private TileType type;
    private int elevation;
    private TreeVisualType treeType;

    private byte skyLightLevel = 0;
    private byte blockLightLevel = 0;
    private boolean hasTorch = false;

    public Tile(TileType type, int elevation) {
        this.type = type;
        this.elevation = elevation;
        this.treeType = TreeVisualType.NONE;
        this.skyLightLevel = 0;
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

    public byte getFinalLightLevel() {
        return (byte) Math.max(skyLightLevel, blockLightLevel);
    }

    public boolean isTransparentToLight() {
        // Consider if water should be more transparent to sky light than block light
        return type == TileType.WATER;
    }
}
