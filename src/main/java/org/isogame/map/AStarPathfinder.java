package org.isogame.map; // Or org.isogame.pathfinding

import org.isogame.tile.Tile; // Assuming Tile.TileType is used for walkability

import java.util.*;

public class AStarPathfinder {

    private static final int MOVE_STRAIGHT_COST = 10; // Cost for moving straight (scaled by 10 for integer math if preferred)
    // private static final int MOVE_DIAGONAL_COST = 14; // For diagonal movement later

    public List<PathNode> findPath(int startRow, int startCol, int endRow, int endCol, Map map) {
        PathNode startNode = new PathNode(startRow, startCol);
        PathNode endNode = new PathNode(endRow, endCol);

        PriorityQueue<PathNode> openList = new PriorityQueue<>(Comparator.comparingDouble(node -> node.fCost));
        HashSet<PathNode> closedList = new HashSet<>();

        startNode.gCost = 0;
        startNode.hCost = calculateHeuristic(startNode, endNode);
        startNode.calculateFCost();
        openList.add(startNode);

        while (!openList.isEmpty()) {
            PathNode currentNode = openList.poll();

            if (currentNode.equals(endNode)) {
                return reconstructPath(currentNode);
            }

            closedList.add(currentNode);

            // Explore neighbors (4-directional for now: Up, Down, Left, Right)
            int[] dRow = {-1, 1, 0, 0};
            int[] dCol = {0, 0, -1, 1};

            for (int i = 0; i < 4; i++) {
                int neighborRow = currentNode.row + dRow[i];
                int neighborCol = currentNode.col + dCol[i];

                if (!map.isValid(neighborRow, neighborCol)) {
                    continue; // Neighbor is outside map bounds
                }

                PathNode neighborNode = new PathNode(neighborRow, neighborCol);
                if (closedList.contains(neighborNode)) {
                    continue; // Already evaluated
                }

                // Check walkability and elevation difference
                if (!isWalkable(map, currentNode, neighborNode)) {
                    closedList.add(neighborNode); // Treat as unwalkable and explored
                    continue;
                }


                double tentativeGCost = currentNode.gCost + MOVE_STRAIGHT_COST; // Add movement cost from Map if variable

                if (tentativeGCost < neighborNode.gCost) {
                    neighborNode.parent = currentNode;
                    neighborNode.gCost = tentativeGCost;
                    neighborNode.hCost = calculateHeuristic(neighborNode, endNode);
                    neighborNode.calculateFCost();

                    if (!openList.contains(neighborNode)) {
                        openList.add(neighborNode);
                    } else {
                        // Update priority queue if node already exists with higher fCost
                        // Java's PriorityQueue doesn't have an efficient update.
                        // A common workaround is to re-add, or use a custom PriorityQueue.
                        // For simplicity, re-adding might lead to duplicates but A* handles it.
                        // Better: remove and re-add, or use a structure that supports decrease-key.
                        openList.remove(neighborNode); // Inefficient, but for demonstration
                        openList.add(neighborNode);
                    }
                }
            }
        }
        return Collections.emptyList(); // No path found
    }

    private List<PathNode> reconstructPath(PathNode endNode) {
        List<PathNode> path = new ArrayList<>();
        PathNode currentNode = endNode;
        while (currentNode != null) {
            path.add(currentNode);
            currentNode = currentNode.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private double calculateHeuristic(PathNode from, PathNode to) {
        // Manhattan distance for 4-directional movement
        return (Math.abs(from.row - to.row) + Math.abs(from.col - to.col)) * MOVE_STRAIGHT_COST;
    }

    // Helper for walkability, considering elevation
    private boolean isWalkable(Map map, PathNode fromNode, PathNode toNode) {
        Tile toTile = map.getTile(toNode.row, toNode.col);
        Tile fromTile = map.getTile(fromNode.row, fromNode.col);

        if (toTile == null || fromTile == null) return false;
        if (toTile.getType() == Tile.TileType.WATER) return false; // Cannot walk on water

        // Check elevation difference (e.g., can only step up/down 1 unit)
        int elevationDiff = Math.abs(toTile.getElevation() - fromTile.getElevation());
        if (elevationDiff > 1) { // Adjust this threshold as needed
            return false;
        }
        return true; // Add other conditions like rocks being unwalkable if needed
    }
}