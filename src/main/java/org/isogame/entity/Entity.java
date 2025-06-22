package org.isogame.entity;

import com.google.gson.Gson;
import org.isogame.gamedata.AnimationDefinition; // <-- IMPORTANT IMPORT
import org.isogame.game.Game;
import org.isogame.map.PathNode;
import org.isogame.savegame.EntitySaveData;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

public abstract class Entity {

    // Health System
    protected int maxHealth = 20;
    public int health = 20;
    protected boolean isDead = false;

    // Health Visualization
    protected double damageFlashTimer = 0.0;
    protected static final double DAMAGE_FLASH_DURATION = 0.4;

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
    protected AnimationDefinition animDef; // <-- NEW FIELD
    protected String currentAnimationName = "idle"; // <-- NEW FIELD

    // Pathfinding
    protected List<PathNode> currentPath;
    protected int currentPathIndex;

    protected Entity owner;

    // This is the single, correct method for loading animation data
    protected void loadAnimationDefinition(String jsonPath) {
        try (InputStream is = getClass().getResourceAsStream(jsonPath)) {
            if (is == null) {
                System.err.println("CRITICAL: Cannot find animation definition file: " + jsonPath);
                return;
            }
            try (Reader reader = new InputStreamReader(is)) {
                this.animDef = new Gson().fromJson(reader, AnimationDefinition.class);
                System.out.println("Successfully loaded animation definition for: " + getClass().getSimpleName());
            }
        } catch (Exception e) {
            System.err.println("ERROR: Exception while loading animation definition " + jsonPath);
            e.printStackTrace();
        }
    }

    public void takeDamage(int amount, Entity source) {
        if (isDead || currentAction == Action.DEATH) return;

        this.owner = source;
        this.health -= amount;
        this.damageFlashTimer = DAMAGE_FLASH_DURATION;

        if (this.health <= 0) {
            this.health = 0;
            onDeath();
        }
    }

    public void populateSaveData(EntitySaveData data) {
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

    public void updateVisualEffects(double deltaTime) {
        if (damageFlashTimer > 0) {
            damageFlashTimer -= deltaTime;
            if (damageFlashTimer < 0) {
                damageFlashTimer = 0;
            }
        }
    }

    public float[] getHealthTint() {
        if (damageFlashTimer > 0) {
            return new float[] {1.0f, 0.4f, 0.4f, 1.0f};
        }
        return new float[] {1.0f, 1.0f, 1.0f, 1.0f};
    }

    public boolean isSavable() {
        return true;
    }

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