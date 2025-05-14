package org.isogame.entitiy; // Ensure this package is correct

// Remove imports related to Renderable interface if they were added:
// import org.isogame.render.Renderable;
// import org.isogame.render.Renderer; // Only if it was used by a Renderable.render method

// Keep other necessary imports
import org.isogame.map.PathNode;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
// import java.util.Map; // For java.util.Map

// NO "implements Renderable" here
public class PlayerModel {

    private float mapRow;
    private float mapCol;
    private boolean levitating = false;
    private float levitateTimer = 0;

    private List<PathNode> currentPath;
    private int currentPathIndex;
    private PathNode currentMoveTargetNode;
    private float targetVisualRow;
    private float targetVisualCol;

    public static final float MOVEMENT_SPEED = 3.0f;

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

    private java.util.Map<String, Integer> inventory;

    public PlayerModel(int startRow, int startCol) {
        this.mapRow = startRow;
        this.mapCol = startCol;
        this.targetVisualRow = startRow;
        this.targetVisualCol = startCol;
        this.inventory = new HashMap<>();
        this.currentPath = new ArrayList<>();
        this.currentPathIndex = -1;
        this.currentMoveTargetNode = null;
    }

    public void update(double deltaTime) {
        if (levitating) {
            levitateTimer += (float) deltaTime * 5.0f;
        }

        boolean activelyMovingOnPath = false;
        if (currentPath != null && !currentPath.isEmpty()) {
            if (currentMoveTargetNode == null) {
                if (currentPathIndex < currentPath.size() - 1) {
                    currentPathIndex++;
                    currentMoveTargetNode = currentPath.get(currentPathIndex);
                    targetVisualRow = currentMoveTargetNode.row;
                    targetVisualCol = currentMoveTargetNode.col;
                } else {
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
                    currentMoveTargetNode = null;
                    if (currentPathIndex >= currentPath.size() - 1) {
                        pathFinished();
                        activelyMovingOnPath = false;
                    }
                } else {
                    float moveX = (dx / distance) * moveStep;
                    float moveY = (dy / distance) * moveStep;
                    this.mapCol += moveX;
                    this.mapRow += moveY;

                    if (Math.abs(dx) > Math.abs(dy) * 0.8) {
                        setDirection(dx > 0 ? Direction.EAST : Direction.WEST);
                    } else if (Math.abs(dy) > Math.abs(dx) * 0.8) {
                        setDirection(dy > 0 ? Direction.SOUTH : Direction.NORTH);
                    }
                }
            }
        }

        if (!activelyMovingOnPath && currentAction == Action.WALK) {
            setAction(Action.IDLE);
        }

        animationTimer += deltaTime;
        if (animationTimer >= frameDuration) {
            animationTimer -= frameDuration;
            currentFrameIndex++;
            int maxFrames = (currentAction == Action.WALK) ? FRAMES_PER_WALK_CYCLE : 1;
            if (currentAction == Action.IDLE) maxFrames = 1;

            if (currentFrameIndex >= maxFrames) {
                currentFrameIndex = 0;
            }
        }
    }

    private void pathFinished() {
        setAction(Action.IDLE);
        if (currentPath != null) currentPath.clear();
        currentPathIndex = -1;
        currentMoveTargetNode = null;
    }

    public void setPath(List<PathNode> path) {
        if (path != null && !path.isEmpty()) {
            this.currentPath = new ArrayList<>(path);
            this.currentPathIndex = 0;
            this.currentMoveTargetNode = null;
            this.targetVisualRow = path.get(0).row;
            this.targetVisualCol = path.get(0).col;
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
    public void setPosition(float row, float col) { this.mapRow = row; this.mapCol = col; this.targetVisualRow = row; this.targetVisualCol = col; }
    public void toggleLevitate() { this.levitating = !this.levitating; if (!this.levitating) levitateTimer = 0; }
    public void setLevitating(boolean levitating) { this.levitating = levitating; if (!this.levitating) levitateTimer = 0;}

    // NO RENDERABLE METHODS HERE (getScreenYSortKey, getZOrder, render(...))
}