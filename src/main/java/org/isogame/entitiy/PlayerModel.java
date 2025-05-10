package org.isogame.entity;

import org.isogame.map.PathNode;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map; // Should resolve to java.util.Map

public class PlayerModel {

    private float mapRow;
    private float mapCol;
    private boolean levitating = false;
    private float levitateTimer = 0;

    // Pathfinding State
    private List<PathNode> currentPath;
    private int currentPathIndex; // Index of the *current node in the path the player is AT or just left*
    private PathNode currentMoveTargetNode; // The immediate next tile in the path we are moving towards
    private float targetVisualRow; // For smooth visual interpolation to currentMoveTargetNode.row
    private float targetVisualCol; // For smooth visual interpolation to currentMoveTargetNode.col

    public static final float MOVEMENT_SPEED = 3.0f; // Tiles per second for path following

    public enum Action { IDLE, WALK }
    public enum Direction { NORTH, WEST, SOUTH, EAST }

    private Action currentAction = Action.IDLE;
    private Direction currentDirection = Direction.SOUTH;
    private int currentFrameIndex = 0;
    private double animationTimer = 0.0;
    private double frameDuration = 0.1;

    public static final int FRAME_WIDTH = 64;
    public static final int FRAME_HEIGHT = 64;
    public static final int ROW_WALK_NORTH = 8;
    public static final int ROW_WALK_WEST = 9;
    public static final int ROW_WALK_SOUTH = 10;
    public static final int ROW_WALK_EAST = 11;
    public static final int FRAMES_PER_WALK_CYCLE = 9;

    private java.util.Map<String, Integer> inventory; // Explicitly java.util.Map

    public PlayerModel(int startRow, int startCol) {
        this.mapRow = startRow;
        this.mapCol = startCol;
        this.targetVisualRow = startRow;
        this.targetVisualCol = startCol;
        this.inventory = new HashMap<>();
        this.currentPath = new ArrayList<>(); // Initialize to avoid null pointer
        this.currentPathIndex = -1;       // No path active
        this.currentMoveTargetNode = null;
    }

    public void update(double deltaTime) {
        if (levitating) {
            levitateTimer += (float) deltaTime * 5.0f;
        }

        boolean activelyMovingOnPath = false;
        if (currentPath != null && !currentPath.isEmpty()) {
            // If no current target, try to get the next one from the path
            if (currentMoveTargetNode == null) {
                // currentPathIndex points to the node we *were* at or the start node (index 0)
                // So, the next target is currentPathIndex + 1
                if (currentPathIndex < currentPath.size() - 1) {
                    currentPathIndex++; // Advance to the next segment of the path
                    currentMoveTargetNode = currentPath.get(currentPathIndex);
                    targetVisualRow = currentMoveTargetNode.row;
                    targetVisualCol = currentMoveTargetNode.col;
                } else { // Reached end of path, or no more segments
                    pathFinished();
                }
            }

            if (currentMoveTargetNode != null) {
                activelyMovingOnPath = true;
                setAction(Action.WALK);

                float moveStep = MOVEMENT_SPEED * (float) deltaTime;
                float dx = targetVisualCol - this.mapCol;
                float dy = targetVisualRow - this.mapRow;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                if (distance <= moveStep || distance == 0.0f) {
                    this.mapRow = targetVisualRow;
                    this.mapCol = targetVisualCol;
                    currentMoveTargetNode = null; // Arrived, ready for next target in next update frame
                    if (currentPathIndex >= currentPath.size() - 1) { // Is this the final node?
                        pathFinished();
                        activelyMovingOnPath = false; // Path is done
                    }
                } else {
                    float moveX = (dx / distance) * moveStep;
                    float moveY = (dy / distance) * moveStep;
                    this.mapCol += moveX;
                    this.mapRow += moveY;

                    // Update direction for animation
                    if (Math.abs(dx) > Math.abs(dy) * 0.8) { // Favor horizontal if clearly more dx
                        setDirection(dx > 0 ? Direction.EAST : Direction.WEST);
                    } else if (Math.abs(dy) > Math.abs(dx) * 0.8) { // Favor vertical if clearly more dy
                        setDirection(dy > 0 ? Direction.SOUTH : Direction.NORTH);
                    }
                    // If dx and dy are similar, direction might not change (keeps last direction)
                }
            }
        }

        if (!activelyMovingOnPath && currentAction == Action.WALK) {
            setAction(Action.IDLE);
        }

        // Animation Update Logic
        animationTimer += deltaTime;
        if (animationTimer >= frameDuration) {
            animationTimer -= frameDuration;
            currentFrameIndex++;
            int maxFrames = (currentAction == Action.WALK) ? FRAMES_PER_WALK_CYCLE : 1; // Simpler
            if (currentAction == Action.IDLE) maxFrames = 1; // Ensure IDLE only has 1 frame for now via getVisualFrameIndex

            if (currentFrameIndex >= maxFrames) {
                currentFrameIndex = 0;
            }
        }
    }

    private void pathFinished() {
        setAction(Action.IDLE);
        if (currentPath != null) currentPath.clear(); // Clear the path list
        currentPathIndex = -1;
        currentMoveTargetNode = null;
        // Keep player at final mapRow, mapCol
        System.out.println("Player reached destination or path cleared.");
    }

    public void setPath(List<PathNode> path) {
        if (path != null && !path.isEmpty()) {
            this.currentPath = new ArrayList<>(path); // Take a copy
            // First node in path is player's current location, so actual first target is index 1
            this.currentPathIndex = 0; // Start at the beginning of the path (current location)
            this.currentMoveTargetNode = null; // Let update() pick the first actual move target
            // Visual position should already be player's current position
            this.targetVisualRow = path.get(0).row;
            this.targetVisualCol = path.get(0).col;
            System.out.println("Path set with " + this.currentPath.size() + " nodes.");
        } else {
            pathFinished(); // Clear everything and set to IDLE
            System.out.println("Path set to null or empty.");
        }
    }

    public int getAnimationRow() {
        switch (currentDirection) {
            case NORTH: return ROW_WALK_NORTH;
            case WEST:  return ROW_WALK_WEST;
            case SOUTH: return ROW_WALK_SOUTH;
            case EAST:  return ROW_WALK_EAST;
            default:    return ROW_WALK_SOUTH;
        }
    }

    public int getVisualFrameIndex() {
        if (currentAction == Action.IDLE) return 0;
        return currentFrameIndex;
    }

    public void setAction(Action newAction) {
        if (this.currentAction != newAction) {
            this.currentAction = newAction;
            this.currentFrameIndex = 0;
            this.animationTimer = 0.0;
        }
    }

    public void setDirection(Direction newDirection) {
        if (this.currentDirection != newDirection) {
            this.currentDirection = newDirection;
            if (this.currentAction == Action.WALK) {
                this.currentFrameIndex = 0;
                this.animationTimer = 0.0;
            }
        }
    }

    public void addResource(String resourceType, int amount) {
        inventory.put(resourceType, inventory.getOrDefault(resourceType, 0) + amount);
        System.out.println("Player collected: " + amount + " " + resourceType + ". Total: " + getResourceCount(resourceType));
    }

    public int getResourceCount(String resourceType) {
        return inventory.getOrDefault(resourceType, 0);
    }

    public java.util.Map<String, Integer> getInventory() { // Explicitly java.util.Map
        return inventory;
    }

    public float getMapRow() { return mapRow; }
    public float getMapCol() { return mapCol; }
    public int getTileRow() { return Math.round(mapRow); } // Current logical tile
    public int getTileCol() { return Math.round(mapCol); } // Current logical tile
    public boolean isLevitating() { return levitating; }
    public float getLevitateTimer() { return levitateTimer; }
    public Action getCurrentAction() { return currentAction; }
    public Direction getCurrentDirection() { return currentDirection; }
    public void setPosition(float row, float col) { this.mapRow = row; this.mapCol = col; this.targetVisualRow = row; this.targetVisualCol = col; }
    // public void move(float dRow, float dCol) { this.mapRow += dRow; this.mapCol += dCol; } // Direct move, likely not used now
    public void toggleLevitate() { this.levitating = !this.levitating; if (!this.levitating) levitateTimer = 0; }
    public void setLevitating(boolean levitating) { this.levitating = levitating; if (!this.levitating) levitateTimer = 0;}
}