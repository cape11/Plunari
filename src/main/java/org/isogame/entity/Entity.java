package org.isogame.entity;

import org.isogame.game.Game;
import org.isogame.map.PathNode;
import java.util.List;

public abstract class Entity {

    // --- Core Position & State ---
    protected float mapRow;
    protected float mapCol;
    protected float visualRow;
    protected float visualCol;
    protected static final float VISUAL_SMOOTH_FACTOR = 0.2f;

    public enum Action { IDLE, WALK, HIT, CHOPPING }
    public enum Direction { NORTH, WEST, SOUTH, EAST }

    protected Action currentAction = Action.IDLE;
    protected Direction currentDirection = Direction.SOUTH;

    // --- Animation ---
    protected int currentFrameIndex = 0;
    protected double animationTimer = 0.0;
    protected double frameDuration = 0.15; // A default animation speed

    // --- Pathfinding ---
    protected List<PathNode> currentPath;
    protected int currentPathIndex;

    // --- Abstract Methods (to be implemented by subclasses like PlayerModel) ---
    /**
     * The main update logic for the entity, called every frame.
     * @param deltaTime Time elapsed since the last frame.
     * @param game The main game instance.
     */
    public abstract void update(double deltaTime, Game game);

    /**
     * Gets the texture atlas row for the entity's current animation state.
     * @return The row index on the sprite sheet.
     */
    public abstract int getAnimationRow();

    /**
     * Gets the display name of the entity.
     * @return A string representing the entity's name.
     */
    public abstract String getDisplayName();

    /**
     * Gets the width of a single frame for this entity on its sprite sheet.
     * @return The frame width in pixels.
     */
    public abstract int getFrameWidth();

    /**
     * Gets the height of a single frame for this entity on its sprite sheet.
     * @return The frame height in pixels.
     */
    public abstract int getFrameHeight();


    // --- Common Getters (available to all entities) ---
    public float getMapRow() { return mapRow; }
    public float getMapCol() { return mapCol; }
    public float getVisualRow() { return visualRow; }
    public float getVisualCol() { return visualCol; }
    public int getTileRow() { return Math.round(mapRow); }
    public int getTileCol() { return Math.round(mapCol); }
    public Action getCurrentAction() { return currentAction; }
    public Direction getCurrentDirection() { return currentDirection; }
    public int getVisualFrameIndex() { return currentFrameIndex; }

    // --- Common Setters (available to all entities) ---
    public void setPosition(float row, float col) {
        this.mapRow = row;
        this.mapCol = col;
        this.visualRow = row;
        this.visualCol = col;
    }
}