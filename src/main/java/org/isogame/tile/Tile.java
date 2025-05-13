package org.isogame.tile;

public class Tile {

    public enum TileType {
        WATER, SAND, GRASS, ROCK, SNOW // Added more types
    }

    // Define enums ONLY for the 2-3 tree types you've chosen
    public enum TreeVisualType {
        NONE, // No tree
        APPLE_TREE_FRUITING, // Example
        PINE_TREE_SMALL      // Example
        // Add more later if you want
    }


    private TileType type;
    private int elevation;
    private boolean hasTree; //nuevo
    private TreeVisualType treeType;

    public Tile(TileType type, int elevation) {
        this.type = type;
        this.elevation = elevation;
        this.treeType = TreeVisualType.NONE; // Default to no tree
    }

    public TileType getType() {return type; }

    public void setType(TileType type) {this.type = type;}

    public int getElevation() {return elevation; }

    public void setElevation(int elevation) {this.elevation = elevation;}
    public TreeVisualType getTreeType() { return treeType; }
    public void setTreeType(TreeVisualType treeType) { this.treeType = treeType; }
}