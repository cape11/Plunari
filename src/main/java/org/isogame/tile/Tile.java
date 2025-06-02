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
        SNOW
    }

    public enum TreeVisualType {
        NONE,
        APPLE_TREE_FRUITING,
        PINE_TREE_SMALL,
        PALM_TREE
    }

    private TileType type;
    private int elevation; // Elevation might be less relevant for AIR tiles, or could be a default
    private TreeVisualType treeType;

    private byte skyLightLevel = 0;
    private byte blockLightLevel = 0;
    private boolean hasTorch = false;

    public Tile(TileType type, int elevation) {
        this.type = type;
        this.elevation = elevation;
        this.treeType = TreeVisualType.NONE;
    }

    public TileType getType() { return type; }
    public void setType(TileType type) { this.type = type; }
    public int getElevation() { return elevation; }
    public void setElevation(int elevation) { this.elevation = elevation; }
    public TreeVisualType getTreeType() { return treeType; }
    public void setTreeType(TreeVisualType treeType) { this.treeType = treeType; }
    public byte getSkyLightLevel() { return skyLightLevel; }
    public void setSkyLightLevel(byte level) { this.skyLightLevel = (byte) Math.max(0, Math.min(MAX_LIGHT_LEVEL, level)); }
    public byte getBlockLightLevel() { return blockLightLevel; }
    public void setBlockLightLevel(byte level) { this.blockLightLevel = (byte) Math.max(0, Math.min(MAX_LIGHT_LEVEL, level)); }
    public boolean hasTorch() { return hasTorch; }
    public void setHasTorch(boolean hasTorch) { this.hasTorch = hasTorch; }
    public byte getFinalLightLevel() { return (byte) Math.max(skyLightLevel, blockLightLevel); }

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
}