package org.isogame.entitiy;

import org.isogame.map.PathNode;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

public class PlayerModel {

    private float mapRow;
    private float mapCol;
    private boolean levitating = false;
    private float levitateTimer = 0;

    private List<PathNode> currentPath;
    private int currentPathIndex;
    private PathNode currentMoveTargetNode; // The specific node we are currently moving towards

    // targetVisualRow/Col are the coordinates of currentMoveTargetNode
    private float targetVisualRow;
    private float targetVisualCol;

    public static final float MOVEMENT_SPEED = 3.0f; // Tiles per second

    public enum Action { IDLE, WALK }
    public enum Direction { NORTH, WEST, SOUTH, EAST }

    private Action currentAction = Action.IDLE;
    private Direction currentDirection = Direction.SOUTH; // Default facing direction
    // private Direction movementDirection = Direction.SOUTH; // This can be removed if currentDirection is managed well
    private int currentFrameIndex = 0;
    private double animationTimer = 0.0;
    private double frameDuration = 0.1; // Duration of each animation frame in seconds

    public static final int FRAME_WIDTH = 64;
    public static final int FRAME_HEIGHT = 64;
    public static final int ROW_WALK_NORTH = 8;
    public static final int ROW_WALK_WEST = 9;
    public static final int ROW_WALK_SOUTH = 10;
    public static final int ROW_WALK_EAST = 11;
    public static final int FRAMES_PER_WALK_CYCLE = 9;

    private java.util.Map<String, Integer> inventory;
    private boolean newSegmentNeedsDirectionDetermination = false; // Renamed for clarity

    public PlayerModel(int startRow, int startCol) {
        this.mapRow = startRow;
        this.mapCol = startCol;
        this.targetVisualRow = startRow;
        this.targetVisualCol = startCol;
        this.inventory = new HashMap<>();
        this.currentPath = new ArrayList<>();
        this.currentPathIndex = -1; // Start before the first node
        this.currentMoveTargetNode = null;
    }

    public void update(double deltaTime) {
        if (levitating) {
            levitateTimer += (float) deltaTime * 5.0f;
        }

        boolean activelyMovingOnPath = false;

        if (currentPath != null && !currentPath.isEmpty()) {
            if (currentMoveTargetNode == null) { // Need to pick the next (or first) target
                if (currentPathIndex < currentPath.size() - 1) {
                    currentPathIndex++;
                    currentMoveTargetNode = currentPath.get(currentPathIndex);
                    targetVisualRow = currentMoveTargetNode.row;
                    targetVisualCol = currentMoveTargetNode.col;
                    newSegmentNeedsDirectionDetermination = true; // Flag that we need to set direction for this new segment
                } else {
                    pathFinished(); // No more nodes in the path
                }
            }

            if (currentMoveTargetNode != null) { // If we have a valid target for the current segment
                activelyMovingOnPath = true;
                setAction(Action.WALK); // Ensure action is WALK

                float moveStep = MOVEMENT_SPEED * (float) deltaTime;

                float dxToTarget = targetVisualCol - this.mapCol;
                float dyToTarget = targetVisualRow - this.mapRow;
                float distanceToTarget = (float) Math.sqrt(dxToTarget * dxToTarget + dyToTarget * dyToTarget);

                if (newSegmentNeedsDirectionDetermination && distanceToTarget > 0.001f) {
                    Direction determinedDirection;
                    if (Math.abs(dxToTarget) > Math.abs(dyToTarget)) { // More horizontal movement
                        determinedDirection = dxToTarget > 0 ? Direction.EAST : Direction.WEST;
                    } else { // More vertical or purely vertical (prioritize N/S for diagonals)
                        determinedDirection = dyToTarget > 0 ? Direction.SOUTH : Direction.NORTH;
                    }
                    setDirection(determinedDirection); // This will update currentDirection and reset animation if needed
                    newSegmentNeedsDirectionDetermination = false;
                }


                if (distanceToTarget <= moveStep || distanceToTarget < 0.001f) { // Check with a small epsilon
                    // Reached the currentMoveTargetNode (or very close)
                    this.mapRow = targetVisualRow; // Snap to the target node's exact row
                    this.mapCol = targetVisualCol; // Snap to the target node's exact col
                    currentMoveTargetNode = null; // Signal to pick next node in the next frame

                    if (currentPathIndex >= currentPath.size() - 1) { // If this was the last node
                        pathFinished();
                        activelyMovingOnPath = false;
                    }
                } else {
                    // Move towards currentMoveTargetNode
                    float moveX = (dxToTarget / distanceToTarget) * moveStep;
                    float moveY = (dyToTarget / distanceToTarget) * moveStep;
                    this.mapCol += moveX;
                    this.mapRow += moveY;
                }
            }
        }

        if (!activelyMovingOnPath && currentAction == Action.WALK) {
            setAction(Action.IDLE);
        }

        // Animation timer update
        animationTimer += deltaTime;
        if (animationTimer >= frameDuration) {
            animationTimer -= frameDuration;
            currentFrameIndex++;
            int maxFrames = (currentAction == Action.WALK) ? FRAMES_PER_WALK_CYCLE : 1; // IDLE has 1 frame (frame 0)
            if (currentFrameIndex >= maxFrames) {
                currentFrameIndex = 0;
            }
        }
    }

    private void pathFinished() {
        setAction(Action.IDLE);
        if (currentPath != null) {
            currentPath.clear();
        }
        currentPathIndex = -1;
        currentMoveTargetNode = null;
        newSegmentNeedsDirectionDetermination = false;
    }

    public void setPath(List<PathNode> path) {
        if (path != null && !path.isEmpty()) {
            this.currentPath = new ArrayList<>(path);
            this.currentPathIndex = -1;
            this.currentMoveTargetNode = null;
            this.newSegmentNeedsDirectionDetermination = true;
            setAction(Action.IDLE); // Reset to idle before starting a new path, action will become WALK in update
        } else {
            pathFinished();
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

    /**
     * Sets the player's visual facing direction.
     * If the direction changes, it resets the animation frame and timer
     * to ensure the animation for the new direction starts correctly.
     * @param newDirection The new direction to face.
     */
    public void setDirection(Direction newDirection) {
        if (this.currentDirection != newDirection) {
            this.currentDirection = newDirection;
            // Always reset animation when direction changes, whether IDLE or WALK.
            // This ensures the correct starting frame for the new direction's animation.
            this.currentFrameIndex = 0;
            this.animationTimer = 0.0;
        }
    }

    public void addResource(String resourceType, int amount) {
        inventory.put(resourceType, inventory.getOrDefault(resourceType, 0) + amount);
    }

    public int getResourceCount(String resourceType) {
        return inventory.getOrDefault(resourceType, 0);
    }

    public java.util.Map<String, Integer> getInventory() {
        return inventory;
    }

    public float getMapRow() { return mapRow; }
    public float getMapCol() { return mapCol; }
    public int getTileRow() { return Math.round(mapRow); }
    public int getTileCol() { return Math.round(mapCol); }
    public boolean isLevitating() { return levitating; }
    public float getLevitateTimer() { return levitateTimer; }
    public Action getCurrentAction() { return currentAction; }
    public Direction getCurrentDirection() { return currentDirection; }

    public void setPosition(float row, float col) {
        this.mapRow = row;
        this.mapCol = col;
        this.targetVisualRow = row;
        this.targetVisualCol = col;
        pathFinished();
    }
    public void toggleLevitate() { this.levitating = !this.levitating; if (!this.levitating) levitateTimer = 0; }
    public void setLevitating(boolean levitating) { this.levitating = levitating; if (!this.levitating) levitateTimer = 0;}
}
