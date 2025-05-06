package org.isogame.tile;

public class Tile {

    public enum TileType {
        WATER, SAND, GRASS, ROCK, SNOW // Added more types
    }

    private TileType type;
    private int elevation;

    public Tile(TileType type, int elevation) {
        this.type = type;
        this.elevation = elevation;
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
}