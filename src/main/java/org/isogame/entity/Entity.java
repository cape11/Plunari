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

    // --- ADDED 'SWING' ACTION ---
    public enum Action { IDLE, WALK, HIT, CHOPPING, SWING }
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

    public abstract void update(double deltaTime, Game game);
    public abstract int getAnimationRow();
    public abstract String getDisplayName();
    public abstract int getFrameWidth();
    public abstract int getFrameHeight();

    // --- Common Getters ---
    public float getMapRow() { return mapRow; }
    public float getMapCol() { return mapCol; }
    public float getVisualRow() { return visualRow; }
    public float getVisualCol() { return visualCol; }
    public int getTileRow() { return Math.round(mapRow); }
    public int getTileCol() { return Math.round(mapCol); }
    public Action getCurrentAction() { return currentAction; }
    public Direction getCurrentDirection() { return currentDirection; }
    public int getVisualFrameIndex() { return currentFrameIndex; }

    // --- Common Setters ---
    public void setPosition(float row, float col) {
        this.mapRow = row;
        this.mapCol = col;
        this.visualRow = row;
        this.visualCol = col;
    }
}
