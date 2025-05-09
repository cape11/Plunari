public class PlayerModel {

    private float mapRow;
    private float mapCol;
    private boolean levitating = false;
    private float levitateTimer = 0;

    public enum Action {
        IDLE, WALK // Start with these, add more like SLASH, SPELLCAST later
    }

    public enum Direction {
        NORTH, WEST, SOUTH, EAST // Cardinal directions
        // NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST // For 8-directional movement later
    }

    private Action currentAction = Action.IDLE;
    private Direction currentDirection = Direction.SOUTH; // Default facing direction
    private int currentFrameIndex = 0;
    private double animationTimer = 0.0;
    private double frameDuration = 0.1; // Duration of each frame in seconds (e.g., 0.1 = 10 FPS animation)

    // LPC specific constants
    public static final int LPC_FRAME_WIDTH = 64;
    public static final int LPC_FRAME_HEIGHT = 64;

    // Base rows for different actions on the LPC sheet (VERIFY THESE WITH YOUR SHEET GUIDE)
    public static final int LPC_ROW_BASE_WALK = 16;   // Typical base row for walking
    public static final int LPC_ROW_BASE_SHOOT = 32;  // Typical base row for shooting (example)
    // public static final int LPC_ROW_BASE_SLASH = 24;
    // public static final int LPC_ROW_BASE_THRUST = 8;

    // Frame counts for different actions (VERIFY THESE)
    public static final int LPC_FRAMES_WALK = 9;
    public static final int LPC_FRAMES_SHOOT = 13; // Example
    // public static final int LPC_FRAMES_SLASH = 6;
    // public static final int LPC_FRAMES_THRUST = 8;


    // Constructor
    public PlayerModel(int startRow, int startCol) {
        this.mapRow = startRow;
        this.mapCol = startCol;
    }

    public void update(double deltaTime) {
        if (levitating) {
            levitateTimer += (float) deltaTime * 5.0f;
        }

        // Animation Update Logic
        animationTimer += deltaTime;
        if (animationTimer >= frameDuration) {
            animationTimer -= frameDuration; // Reset timer for the next frame interval
            currentFrameIndex++;

            int maxFrames = 1; // Default for a static IDLE or unhandled action
            if (currentAction == Action.WALK) {
                maxFrames = LPC_FRAMES_WALK;
            }
            // Add other actions here, e.g.:
            // else if (currentAction == Action.SHOOT) {
            //     maxFrames = LPC_FRAMES_SHOOT;
            // }

            if (currentFrameIndex >= maxFrames) {
                currentFrameIndex = 0; // Loop animation
            }
        }
    }

    // Determines the correct row on the sprite sheet for the current action and direction
    public int getAnimationRow() {
        int baseRow;
        switch (currentAction) {
            case WALK:
                baseRow = LPC_ROW_BASE_WALK;
                break;
            // EXAMPLE: If you add a SHOOT action
            // case SHOOT:
            //     baseRow = LPC_ROW_BASE_SHOOT;
            //     break;
            case IDLE: // For IDLE, use the walk base row. getVisualFrameIndex() will show frame 0.
            default:   // Fallback for any other undefined actions for now
                baseRow = LPC_ROW_BASE_WALK;
                break;
        }

        // This directional offset logic should align with LPC standard (N, W, S, E, NW, NE, SW, SE)
        switch (currentDirection) {
            case NORTH: return baseRow + 0;
            case WEST:  return baseRow + 1;
            case SOUTH: return baseRow + 2;
            case EAST:  return baseRow + 3;
            // For 8-directional movement, add these cases:
            // case NORTH_WEST: return baseRow + 4;
            // case NORTH_EAST: return baseRow + 5;
            // case SOUTH_WEST: return baseRow + 6;
            // case SOUTH_EAST: return baseRow + 7;
            default: return baseRow + 2; // Default to South if direction is somehow unset
        }
    }

    // Determines which frame index to actually render (handles static IDLE frame)
    public int getVisualFrameIndex() {
        if (currentAction == Action.IDLE) {
            return 0; // For IDLE, always show the first frame of the current direction's walk/base cycle
        }
        return currentFrameIndex; // For other (animated) actions
    }

    public void setAction(Action newAction) {
        if (this.currentAction != newAction) {
            this.currentAction = newAction;
            this.currentFrameIndex = 0; // Reset frame when action changes
            this.animationTimer = 0.0;  // Reset animation timer
        }
    }

    public void setDirection(Direction newDirection) {
        if (this.currentDirection != newDirection) {
            this.currentDirection = newDirection;
            // If the action is one that depends on direction (like WALK),
            // reset its animation frame, so it starts fresh for the new direction.
            if (this.currentAction == Action.WALK) { // Or any other directional animation
                this.currentFrameIndex = 0;
                this.animationTimer = 0.0;
            }
        }
    }

    // Standard Getters
    public float getMapRow() { return mapRow; }
    public float getMapCol() { return mapCol; }
    public int getTileRow() { return Math.round(mapRow); }
    public int getTileCol() { return Math.round(mapCol); }
    public boolean isLevitating() { return levitating; }
    public float getLevitateTimer() { return levitateTimer; }
    public Action getCurrentAction() { return currentAction; }
    public Direction getCurrentDirection() { return currentDirection; }
    // Using getVisualFrameIndex() for rendering is preferred over directly accessing currentFrameIndex

    // Standard Setters / Modifiers
    public void setPosition(float row, float col) {
        this.mapRow = row;
        this.mapCol = col;
    }

    public void move(float dRow, float dCol) {
        this.mapRow += dRow;
        this.mapCol += dCol;
    }

    public void toggleLevitate() {
        this.levitating = !this.levitating;
        if (!this.levitating) {
            levitateTimer = 0;
        }
    }

    public void setLevitating(boolean levitating) {
        this.levitating = levitating;
        if (!this.levitating) {
            levitateTimer = 0;
        }
    }
}