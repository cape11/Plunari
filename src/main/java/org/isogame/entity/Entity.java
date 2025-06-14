package org.isogame.entity;

import org.isogame.game.Game;
import org.isogame.map.PathNode;
import org.isogame.savegame.EntitySaveData;

import java.util.List;

public abstract class Entity {

    // Health System
    protected int maxHealth = 20;
    public int health = 20;
    protected boolean isDead = false;

    // --- Health Visualization ---
    protected double damageFlashTimer = 0.0;
    protected static final double DAMAGE_FLASH_DURATION = 0.4; // How long the flash lasts

    // Core Position & State
    protected float mapRow;
    protected float mapCol;
    protected float visualRow;
    protected float visualCol;
    protected static final float VISUAL_SMOOTH_FACTOR = 0.2f;

    public enum Action { IDLE, WALK, HIT, CHOPPING, SWING, DEATH, HOLD }
    public enum Direction { NORTH, WEST, SOUTH, EAST }

    protected Action currentAction = Action.IDLE;
    protected Direction currentDirection = Direction.SOUTH;

    // Animation
    protected int currentFrameIndex = 0;
    protected double animationTimer = 0.0;
    protected double frameDuration = 0.15;

    // Pathfinding
    protected List<PathNode> currentPath;
    protected int currentPathIndex;

    /**
     * Reduces the entity's health and handles the visual feedback and death.
     * @param amount The amount of damage to deal.
     */
    public void takeDamage(int amount, Entity source) {
        if (isDead || currentAction == Action.DEATH) return; // Don't   take damage while dying

        this.owner = source;
        this.health -= amount;
        this.damageFlashTimer = DAMAGE_FLASH_DURATION;

        if (this.health <= 0) {
            this.health = 0;
            // --- FIX: We no longer set isDead = true here. ---
            // Instead, we call onDeath(), which will trigger the animation.
            onDeath();
        }
    }



    // --- NEW METHOD FOR SAVING ---
    public void populateSaveData(EntitySaveData data) {
        // This is a generic method. Subclasses will provide the specific type.
        data.mapRow = this.mapRow;
        data.mapCol = this.mapCol;
        data.health = this.health;
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
        }
    }

    protected void updateDirection(float dC, float dR) {
        if (Math.abs(dC) > Math.abs(dR)) {
            setDirection((dC > 0) ? Direction.EAST : Direction.WEST);
        } else {
            setDirection((dR > 0) ? Direction.SOUTH : Direction.NORTH);
        }
    }

    /**
     * Called by the main game loop to tick down timers for visual effects.
     * @param deltaTime The time since the last frame.
     */
    public void updateVisualEffects(double deltaTime) {
        if (damageFlashTimer > 0) {
            damageFlashTimer -= deltaTime;
            if (damageFlashTimer < 0) {
                damageFlashTimer = 0;
            }
        }
    }

    /**
     * Gets the color tint for the entity based on its current health state.
     * This will be used by the renderer.
     * @return An array of {R, G, B, A} float values for the tint.
     */
    public float[] getHealthTint() {
        if (damageFlashTimer > 0) {
            // Flash bright red when damaged
            return new float[] {1.0f, 0.4f, 0.4f, 1.0f};
        }
        // No special tint if not damaged
        return new float[] {1.0f, 1.0f, 1.0f, 1.0f};
    }

    protected Entity owner; // Add this field to track who created/attacked this entity

    public boolean isSavable() {
        return true;
    }
    /**
     * A hook for subclasses to define what happens on death (e.g., dropping loot).
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
