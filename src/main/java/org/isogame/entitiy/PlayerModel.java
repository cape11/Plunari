
package org.isogame.entity;

public class PlayerModel {

    private float mapRow;
    private float mapCol;
    private boolean levitating = false;
    private float levitateTimer = 0;

    public enum Action {
        IDLE, WALK
    }

    public enum Direction {
        NORTH, WEST, SOUTH, EAST // Cardinal directions for now
    }

    private Action currentAction = Action.IDLE;
    private Direction currentDirection = Direction.SOUTH; // Default facing direction
    private int currentFrameIndex = 0; // This will be the COLUMN index within the animation row
    private double animationTimer = 0.0;
    private double frameDuration = 0.1; // Duration of each frame (0.1 = 10 FPS animation)

    // Sprite sheet properties (VERIFY these with your actual sprite sheet frame dimensions)
    public static final int FRAME_WIDTH = 64;  // Assuming 64px, common for LPC
    public static final int FRAME_HEIGHT = 64; // Assuming 64px, common for LPC

    // Row numbers for different aniamtions (0-indexed, BASED ON YOUR NEW INFO)
    public static final int ROW_WALK_NORTH = 8;
    public static final int ROW_WALK_WEST = 9;    // Corrected from your "East" for standard layout
    public static final int ROW_WALK_SOUTH = 10;
    public static final int ROW_WALK_EAST = 11;
    // public static final int ROW_IDLE_NORTH = 8; // Example: Idle could use frame 0 of walk north

    // Number of frames for animations
    public static final int FRAMES_PER_WALK_CYCLE = 9; // You confirmed 9 frames for each walk direction

    public PlayerModel(int startRow, int startCol) {
        this.mapRow = startRow;
        this.mapCol = startCol;
    }

    public void update(double deltaTime) {
        if (levitating) {
            levitateTimer += (float) deltaTime * 5.0f;
        }

        animationTimer += deltaTime;
        if (animationTimer >= frameDuration) {
            animationTimer -= frameDuration;
            currentFrameIndex++;

            int maxFrames = 1; // Default for static IDLE
            if (currentAction == Action.WALK) {
                maxFrames = FRAMES_PER_WALK_CYCLE;
            }
            // Add other actions here if they have different frame counts

            if (currentFrameIndex >= maxFrames) {
                currentFrameIndex = 0; // Loop animation
            }
        }
    }

    // This now returns the ROW index for the current animation
    public int getAnimationRow() {
        // For IDLE, we'll use the row of the current WALK direction.
        // getVisualFrameIndex() will handle showing frame 0 for IDLE.
        switch (currentDirection) {
            case NORTH: return ROW_WALK_NORTH;
            case WEST:  return ROW_WALK_WEST;
            case SOUTH: return ROW_WALK_SOUTH;
            case EAST:  return ROW_WALK_EAST;
            default:    return ROW_WALK_SOUTH; // Default to South
        }
    }

    // This returns the COLUMN index (which is the frame index) for the current animation
    public int getVisualFrameIndex() {
        if (currentAction == Action.IDLE) {
            return 0; // For IDLE, always show the first frame (column 0) of its animation row
        }
        return currentFrameIndex; // For other animated actions
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
            // If the action is one that depends on direction (like WALK),
            // reset its animation frame, so it starts fresh for the new direction.
            if (this.currentAction == Action.WALK) {
                this.currentFrameIndex = 0;
                this.animationTimer = 0.0;
            }
        }
    }

    // Standard Getters & Setters
    public float getMapRow() { return mapRow; }
    public float getMapCol() { return mapCol; }
    public int getTileRow() { return Math.round(mapRow); }
    public int getTileCol() { return Math.round(mapCol); }
    public boolean isLevitating() { return levitating; }
    public float getLevitateTimer() { return levitateTimer; }
    public Action getCurrentAction() { return currentAction; }
    public Direction getCurrentDirection() { return currentDirection; }
    public void setPosition(float row, float col) { this.mapRow = row; this.mapCol = col; }
    public void move(float dRow, float dCol) { this.mapRow += dRow; this.mapCol += dCol; }
    public void toggleLevitate() { this.levitating = !this.levitating; if (!this.levitating) levitateTimer = 0; }
    public void setLevitating(boolean levitating) { this.levitating = levitating; if (!this.levitating) levitateTimer = 0;}
}