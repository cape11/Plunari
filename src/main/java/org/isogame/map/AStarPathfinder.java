package org.isogame.map; // Or org.isogame.pathfinding

import org.isogame.tile.Tile;

import java.util.*;

public class AStarPathfinder {

    private static final int MOVE_STRAIGHT_COST = 10;
    // private static final int MOVE_DIAGONAL_COST = 14; // For diagonal movement later

    public List<PathNode> findPath(int startRow, int startCol, int endRow, int endCol, Map map) {
        PathNode startNode = new PathNode(startRow, startCol);
        PathNode endNode = new PathNode(endRow, endCol);

        // Check if start or end nodes are even walkable before starting
        // map.getTile() will generate chunk data if needed.
        if (!isWalkableNode(map, startNode) || !isWalkableNode(map, endNode)) {
            // System.out.println("AStar: Start or End node is not walkable.");
            return Collections.emptyList();
        }


        PriorityQueue<PathNode> openList = new PriorityQueue<>(Comparator.comparingDouble(node -> node.fCost));
        HashSet<PathNode> closedList = new HashSet<>();

        startNode.gCost = 0;
        startNode.hCost = calculateHeuristic(startNode, endNode);
        startNode.calculateFCost();
        openList.add(startNode);

        int iterations = 0; // Safety break for very long paths or issues
        int maxIterations = (Math.abs(startRow - endRow) + Math.abs(startCol - endCol)) * 10; // Heuristic limit
        if (maxIterations < 2000) maxIterations = 2000; // Minimum iteration cap
        if (maxIterations > 20000) maxIterations = 20000; // Maximum iteration cap to prevent freezing

        while (!openList.isEmpty()) {
            iterations++;
            if (iterations > maxIterations) {
                System.err.println("AStar: Exceeded max iterations (" + maxIterations + "). Aborting pathfind.");
                return Collections.emptyList(); // Path too long or no path found within reasonable effort
            }

            PathNode currentNode = openList.poll();

            if (currentNode.equals(endNode)) {
                return reconstructPath(currentNode);
            }

            closedList.add(currentNode);

            // Explore neighbors (4-directional for now: Up, Down, Left, Right)
            // These are offsets for global coordinates
            int[] dRow = {-1, 1, 0, 0}; // N, S, W, E
            int[] dCol = {0, 0, -1, 1};

            for (int i = 0; i < 4; i++) {
                int neighborRow = currentNode.row + dRow[i];
                int neighborCol = currentNode.col + dCol[i];

                // For an infinite map, map.isValid is not used.
                // Instead, we check if the tile itself is walkable.
                // map.getTile(neighborRow, neighborCol) will generate chunk data if needed.

                PathNode neighborNode = new PathNode(neighborRow, neighborCol);
                if (closedList.contains(neighborNode)) {
                    continue; // Already evaluated
                }

                // Check walkability and elevation difference using the specific map instance
                if (!isWalkableBetween(map, currentNode, neighborNode)) {
                    closedList.add(neighborNode); // Treat as unwalkable and explored
                    continue;
                }

                double tentativeGCost = currentNode.gCost + MOVE_STRAIGHT_COST;

                boolean inOpenList = openList.contains(neighborNode); // Check if already in open list

                if (tentativeGCost < neighborNode.gCost || !inOpenList) {
                    neighborNode.parent = currentNode;
                    neighborNode.gCost = tentativeGCost;
                    neighborNode.hCost = calculateHeuristic(neighborNode, endNode);
                    neighborNode.calculateFCost();

                    if (inOpenList) {
                        openList.remove(neighborNode); // Remove and re-add to update priority
                    }
                    openList.add(neighborNode);
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

    /**
     * Checks if a single node (tile) is inherently walkable (not water, etc.).
     */
    private boolean isWalkableNode(Map map, PathNode node) {
        Tile tile = map.getTile(node.row, node.col); // map.getTile handles chunk generation
        if (tile == null) return false; // Should not happen if map.getTile works
        if (tile.getType() == Tile.TileType.WATER || tile.getType() == Tile.TileType.AIR) return false;
        // Add other non-walkable tile types if necessary (e.g., lava, very high cliffs if elevation isn't checked elsewhere)
        return true;
    }


    /**
     * Helper for walkability, considering transition FROM fromNode TO toNode.
     * This includes checking the destination tile type and elevation difference.
     */
    private boolean isWalkableBetween(Map map, PathNode fromNode, PathNode toNode) {
        Tile toTile = map.getTile(toNode.row, toNode.col);       // map.getTile handles chunk generation
        Tile fromTile = map.getTile(fromNode.row, fromNode.col); // for these coordinates.

        if (toTile == null || fromTile == null) return false; // Should not happen if map.getTile is robust
        if (toTile.getType() == Tile.TileType.WATER || toTile.getType() == Tile.TileType.AIR) return false; // Cannot walk into water or air

        // Check elevation difference (e.g., can only step up/down 1 unit)
        int elevationDiff = Math.abs(toTile.getElevation() - fromTile.getElevation());
        if (elevationDiff > 1) { // Adjust this threshold as needed (e.g., player step height)
            return false;
        }
        return true; // Add other conditions like specific rocks being unwalkable if needed
    }
}