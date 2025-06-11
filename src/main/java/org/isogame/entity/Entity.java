package org.isogame.entity;

import org.isogame.game.Game;
import org.isogame.map.PathNode;
import java.util.List;

public abstract class Entity {

    // --- NEW: Health System ---
    protected int maxHealth = 20; // Default max health for entities
    protected int health = 20;    // Default current health
    protected boolean isDead = false;

    // --- Core Position & State ---
    protected float mapRow;
    protected float mapCol;
    protected float visualRow;
    protected float visualCol;
    protected static final float VISUAL_SMOOTH_FACTOR = 0.2f;

    public enum Action { IDLE, WALK, HIT, CHOPPING, SWING }
    public enum Direction { NORTH, WEST, SOUTH, EAST }

    protected Action currentAction = Action.IDLE;
    protected Direction currentDirection = Direction.SOUTH;

    // --- Animation ---
    protected int currentFrameIndex = 0;
    protected double animationTimer = 0.0;
    protected double frameDuration = 0.15;

    // --- Pathfinding ---
    protected List<PathNode> currentPath;
    protected int currentPathIndex;

    /**
     * Reduces the entity's health and handles death.
     * @param amount The amount of damage to deal.
     */
    public void takeDamage(int amount) {
        if (isDead) return; // Can't damage a dead entity

        this.health -= amount;
        if (this.health <= 0) {
            this.health = 0;
            this.isDead = true;
            onDeath();
        }
    }

    /**
     * A method that can be overridden by subclasses to handle death logic (e.g., drop items).
     */
    protected void onDeath() {
        // Base implementation does nothing.
    }

    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public boolean isDead() { return isDead; }


    public abstract void update(double deltaTime, Game game);
    public abstract int getAnimationRow();
    public abstract String getDisplayName();
    public abstract int getFrameWidth();
    public abstract int getFrameHeight();

    public float getMapRow() { return mapRow; }
    public float getMapCol() { return mapCol; }
    public float getVisualRow() { return visualRow; }
    public float getVisualCol() { return visualCol; }
    public int getTileRow() { return Math.round(mapRow); }
    public int getTileCol() { return Math.round(mapCol); }
    public Action getCurrentAction() { return currentAction; }
    public Direction getCurrentDirection() { return currentDirection; }
    public int getVisualFrameIndex() { return currentFrameIndex; }

    public void setPosition(float row, float col) {
        this.mapRow = row;
        this.mapCol = col;
        this.visualRow = row;
        this.visualCol = col;
    }
}
