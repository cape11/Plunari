package org.isogame.entity;

import org.isogame.game.Game;
import org.isogame.map.AStarPathfinder;
import org.isogame.map.Map;
import org.isogame.map.PathNode;
import org.isogame.tile.Tile;

import java.util.List;
import java.util.Random;

public class Animal extends Entity {

    private double thinkTimer = 0.0;
    private double timeUntilNextThink;
    private final Random random = new Random();
    private final AStarPathfinder pathfinder = new AStarPathfinder();

    private static final int WANDER_RADIUS = 8;
    private static final float ANIMAL_SPEED = 1.5f;

    // Animation constants for a hypothetical animal sprite sheet.
    // We will borrow from the player's sheet for now.
    public static final int FRAME_WIDTH = 64;
    public static final int FRAME_HEIGHT = 64;
    public static final int ROW_WALK_SOUTH = 10; // Using player's walk rows
    public static final int ROW_WALK_WEST = 9;
    public static final int ROW_WALK_NORTH = 8;
    public static final int ROW_WALK_EAST = 11;
    public static final int FRAMES_PER_CYCLE = 9; // Using player's walk cycle frames

    public Animal(float startRow, float startCol) {
        this.setPosition(startRow, startCol);
        this.currentAction = Action.IDLE;
        this.timeUntilNextThink = 2.0 + random.nextDouble() * 5.0; // Think every 2-7 seconds
        this.frameDuration = 0.2; // Slower animation for wandering
    }

    @Override
    public void update(double deltaTime, Game game) {
        thinkTimer += deltaTime;

        if (thinkTimer >= timeUntilNextThink) {
            thinkTimer = 0;
            timeUntilNextThink = 3.0 + random.nextDouble() * 6.0;

            if (currentAction == Action.IDLE) {
                int targetR = getTileRow() + random.nextInt(WANDER_RADIUS * 2) - WANDER_RADIUS;
                int targetC = getTileCol() + random.nextInt(WANDER_RADIUS * 2) - WANDER_RADIUS;

                // --- FIX: Check if the target tile is walkable before pathfinding ---
                Tile targetTile = game.getMap().getTile(targetR, targetC);
                if (targetTile != null && targetTile.getType() != Tile.TileType.WATER && targetTile.getType() != Tile.TileType.AIR) {
                    this.currentPath = pathfinder.findPath(getTileRow(), getTileCol(), targetR, targetC, game.getMap());
                    if (this.currentPath != null && this.currentPath.size() > 1) {
                        this.currentPathIndex = 1;
                        this.currentAction = Action.WALK;
                    }
                }
            }
        }

        // --- Path Following ---
        if (currentAction == Action.WALK && currentPath != null && currentPathIndex < currentPath.size()) {
            PathNode targetNode = currentPath.get(currentPathIndex);
            float targetR = targetNode.row;
            float targetC = targetNode.col;

            float dR = targetR - mapRow;
            float dC = targetC - mapCol;

            float distance = (float) Math.sqrt(dR * dR + dC * dC);
            if (distance < 0.1f) {
                currentPathIndex++;
                if (currentPathIndex >= currentPath.size()) {
                    currentAction = Action.IDLE;
                    currentPath = null;
                }
            } else {
                float moveAmount = (float) (ANIMAL_SPEED * deltaTime);
                mapRow += (dR / distance) * moveAmount;
                mapCol += (dC / distance) * moveAmount;
                updateDirection(dC, dR);
            }
        } else if (currentAction == Action.WALK) {
            // If path is invalid or finished, go back to idle
            currentAction = Action.IDLE;
        }


        // --- Update visual position and animation ---
        visualCol += (this.mapCol - visualCol) * VISUAL_SMOOTH_FACTOR;
        visualRow += (this.mapRow - visualRow) * VISUAL_SMOOTH_FACTOR;

        if (currentAction == Action.WALK) {
            animationTimer += deltaTime;
            if (animationTimer >= frameDuration) {
                animationTimer -= frameDuration;
                currentFrameIndex = (currentFrameIndex + 1) % FRAMES_PER_CYCLE;
            }
        } else {
            currentFrameIndex = 0; // Idle frame
        }
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


    @Override
    public int getAnimationRow() {
        switch (currentDirection) {
            case NORTH: return ROW_WALK_NORTH;
            case WEST:  return ROW_WALK_WEST;
            case SOUTH: return ROW_WALK_SOUTH;
            case EAST:  return ROW_WALK_EAST;
            default:    return ROW_WALK_SOUTH;
        }
    }

    @Override
    public String getDisplayName() { return "Critter"; }
    @Override public int getFrameWidth() { return FRAME_WIDTH; }
    @Override public int getFrameHeight() { return FRAME_HEIGHT; }
}