package org.isogame.world.structure;

import java.util.HashMap;
import java.util.Map;

public class StructureManager {

    // Using a Map for quick lookups by position
    private final Map<String, Wall> walls = new HashMap<>();

    public Wall getWallAt(int row, int col) {
        return walls.get(row + ":" + col);
    }

    public void addWall(int row, int col, String wallTypeId) {
        if (getWallAt(row, col) != null) {
            return; // Don't place a wall on top of another
        }
        System.out.println(">>> StructureManager: Placing wall '" + wallTypeId + "' at " + row + "," + col);

        Wall newWall = new Wall(row, col, wallTypeId);
        walls.put(row + ":" + col, newWall);

        // Crucially, update the new wall and its neighbors
        updateWallAndNeighbors(row, col);
    }

    public void removeWall(int row, int col) {
        walls.remove(row + ":" + col);
        // After removing, we still need to update the neighbors
        updateWallAndNeighbors(row, col);
    }

    private void updateWallAndNeighbors(int row, int col) {
        // Update the central wall (even if it's being removed, its neighbors need to know)
        updateWallAdjacency(row, col);

        // Update the 4 neighbors
        updateWallAdjacency(row - 1, col); // North
        updateWallAdjacency(row + 1, col); // South
        updateWallAdjacency(row, col + 1); // East
        updateWallAdjacency(row, col - 1); // West
    }

    private void updateWallAdjacency(int row, int col) {
        Wall wall = getWallAt(row, col);
        if (wall == null) {
            return; // No wall at this position to update
        }

        int mask = 0;
        if (getWallAt(row - 1, col) != null) mask |= 1; // North
        if (getWallAt(row, col + 1) != null) mask |= 2; // East
        if (getWallAt(row + 1, col) != null) mask |= 4; // South
        if (getWallAt(row, col - 1) != null) mask |= 8; // West

        wall.updateAdjacencyMask(mask);
    }

    public Map<String, Wall> getAllWalls() {
        return walls;
    }
}