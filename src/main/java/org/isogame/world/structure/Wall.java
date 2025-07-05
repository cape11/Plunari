package org.isogame.world.structure;

public class Wall {
    private int row, col;
    private String wallTypeId; // e.g., "stone_wall_rough"
    private int adjacencyMask = 0;

    public enum Adjacency {
        // Combinations of (N, E, S, W) bits
        POST(0),                 // 0000
        END_CAP_S(1),            // 0001 (End piece facing North)
        END_CAP_W(2),            // 0010 (End piece facing East)
        STRAIGHT_EW(10),         // 1010 (East-West)
        STRAIGHT_NS(5),          // 0101 (North-South)
        CORNER_NE(3),            // 0011
        CORNER_NW(9),            // 1001
        CORNER_SE(6),            // 0110
        CORNER_SW(12),           // 1100
        T_JUNCTION_NES(7),       // 0111
        T_JUNCTION_NSW(13),      // 1101
        T_JUNCTION_ESW(14),      // 1110
        T_JUNCTION_NEW(11),      // 1011
        CROSS(15);               // 1111

        public final int mask;
        Adjacency(int mask) { this.mask = mask; }
    }

    public Wall(int row, int col, String wallTypeId) {
        this.row = row;
        this.col = col;
        this.wallTypeId = wallTypeId;
    }

    public void updateAdjacencyMask(int mask) {
        this.adjacencyMask = mask;
    }

    public Adjacency getCurrentShape() {
        for (Adjacency shape : Adjacency.values()) {
            if (shape.mask == this.adjacencyMask) {
                return shape;
            }
        }
        // Default to a post if no match is found
        return Adjacency.POST;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }
    public String getWallTypeId() { return wallTypeId; }
}