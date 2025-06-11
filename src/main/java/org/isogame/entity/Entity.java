package org.isogame.entity;

import org.isogame.game.Game;
import org.isogame.map.PathNode;
import java.util.List;

public abstract class Entity {

    // --- NEW: Health System ---
    protected int maxHealth = 20; // Default max health for entities
    protected int health = 20;    // Default current health
    protected boolean isDead = false;

    // --- Health Visualization ---
    protected double damageFlashTimer = 0.0; // Timer for flashing red when damaged
    protected static final double DAMAGE_FLASH_DURATION = 0.5; // Duration of damage flash in seconds
    protected static final float LOW_HEALTH_THRESHOLD = 0.3f; // Threshold for low health warning (30% of max health)

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
        this.damageFlashTimer = DAMAGE_FLASH_DURATION; // Trigger damage flash

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

    /**
     * Updates the damage flash timer.
     * @param deltaTime Time elapsed since last update in seconds.
     */
    public void updateHealthVisualization(double deltaTime) {
        if (damageFlashTimer > 0) {
            damageFlashTimer -= deltaTime;
            if (damageFlashTimer < 0) {
                damageFlashTimer = 0;
            }
        }
    }

    /**
     * Checks if the entity is currently flashing from damage.
     * @return True if the entity should be displayed with a damage flash effect.
     */
    public boolean isFlashingFromDamage() {
        return damageFlashTimer > 0;
    }

    /**
     * Checks if the entity is at low health (below the threshold).
     * @return True if the entity's health is below the low health threshold.
     */
    public boolean isLowHealth() {
        return !isDead && health > 0 && ((float)health / maxHealth) <= LOW_HEALTH_THRESHOLD;
    }

    /**
     * Gets the color tint for the entity based on its current state.
     * @return An array of RGBA values (0.0-1.0) for the entity's tint.
     */
    public float[] getHealthTint() {
        if (isFlashingFromDamage()) {
            // Flash red when damaged - intensity based on remaining flash time
            float flashIntensity = (float)(damageFlashTimer / DAMAGE_FLASH_DURATION);
            return new float[] {1.0f, 1.0f - (0.7f * flashIntensity), 1.0f - (0.7f * flashIntensity), 1.0f};
        } else if (isLowHealth()) {
            // Pulse between normal and red tint when at low health
            double pulseRate = 3.0; // Pulses per second
            float pulseIntensity = (float)Math.abs(Math.sin(System.currentTimeMillis() / 1000.0 * pulseRate));
            return new float[] {1.0f, 1.0f - (0.3f * pulseIntensity), 1.0f - (0.3f * pulseIntensity), 1.0f};
        }
        // Normal tint (white)
        return new float[] {1.0f, 1.0f, 1.0f, 1.0f};
    }


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
