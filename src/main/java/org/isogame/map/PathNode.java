package org.isogame.map; // Or org.isogame.pathfinding

// Using a record for simplicity and immutability if appropriate, or a class
public class PathNode {
    public final int row;
    public final int col;
    public double gCost; // Cost from start to this node
    public double hCost; // Heuristic cost from this node to target
    public double fCost; // gCost + hCost
    public PathNode parent; // To reconstruct the path

    public PathNode(int row, int col) {
        this.row = row;
        this.col = col;
        this.gCost = Double.MAX_VALUE;
        this.hCost = 0;
        this.fCost = Double.MAX_VALUE;
        this.parent = null;
    }

    public void calculateFCost() {
        this.fCost = this.gCost + this.hCost;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PathNode pathNode = (PathNode) o;
        return row == pathNode.row && col == pathNode.col;
    }

    @Override
    public int hashCode() {
        int result = row;
        result = 31 * result + col;
        return result;
    }

    @Override
    public String toString() {
        return "PathNode[" + row + "," + col + "]";
    }
}