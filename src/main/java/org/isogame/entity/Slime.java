package org.isogame.entity;

import org.isogame.game.Game;
import org.isogame.item.ItemRegistry;
import org.isogame.map.AStarPathfinder;
import org.isogame.map.Map;
import org.isogame.map.PathNode;
import org.isogame.tile.Tile;

import java.util.List;
import java.util.Random;

public class Slime extends Entity {

    private final Random random = new Random();
    private final AStarPathfinder pathfinder = new AStarPathfinder();

    // --- AI and Behavior Constants ---
    private static final float WANDER_SPEED = 0.8f;
    private static final float CHASE_SPEED = 2.0f;
    private static final int WANDER_RADIUS = 5;
    private static final double THINK_INTERVAL = 2.5;
    private static final double AGGRO_RADIUS = 7.0;
    private static final double ATTACK_RADIUS = 1.5;
    private static final double ATTACK_COOLDOWN = 1.8;
    private static final int ATTACK_DAMAGE = 2;

    // AI State Machine
    private enum AiState { WANDERING, CHASING, ATTACKING }
    private AiState currentState = AiState.WANDERING;
    private double aiTimer = 0.0;
    private double attackCooldownTimer = 0.0;

    // ---!!! THIS IS THE SECTION TO EDIT !!!---

    // Step 1: The top-left starting pixel of your slime animation block.
    public static final int SPRITESHEET_START_X_PIXEL = 576;
    public static final int SPRITESHEET_START_Y_PIXEL = 1664;

    // Step 2: The size of a single frame for the slime.
    public static final int FRAME_WIDTH = 64;
    public static final int FRAME_HEIGHT = 64;

    // The code will automatically calculate the starting frame column and row.
    private static final int STARTING_FRAME_COL = SPRITESHEET_START_X_PIXEL / FRAME_WIDTH; // Result: 9
    private static final int STARTING_FRAME_ROW = SPRITESHEET_START_Y_PIXEL / FRAME_HEIGHT; // Result: 26

    // Step 3: The local row for each animation *within the slime block*.
    public static final int ROW_OFFSET_IDLE = 0;   // The top row of your slime sprites
    public static final int ROW_OFFSET_ATTACK = 1; // The second row of your slime sprites
    public static final int ROW_OFFSET_DEATH = 2; // <-- NEW: The row for the death animation

    // Step 4: The number of frames in one full animation cycle.
    public static final int FRAMES_PER_IDLE_CYCLE = 4;
    public static final int FRAMES_PER_ATTACK_CYCLE = 4;
    public static final int FRAMES_PER_DEATH_CYCLE = 4; // <-- NEW: Number of frames in death animation

    public Slime(float startRow, float startCol) {
        super();
        this.maxHealth = 10;
        this.health = 10;
        this.setPosition(startRow, startCol);
        this.frameDuration = 0.25;
    }

    // ... (The update() method and AI logic remain the same)

    /**
     * This method now returns the ABSOLUTE row on the spritesheet by adding
     * the starting row offset to the direction-specific offset.
     */
    @Override
    public int getAnimationRow() {
        int relativeRow;
        switch (currentAction) {
            case SWING:
                relativeRow = ROW_OFFSET_ATTACK;
                break;
            case DEATH:
                relativeRow = ROW_OFFSET_DEATH;
                break;
            default: // IDLE, WALK
                relativeRow = ROW_OFFSET_IDLE;
                break;
        }
        return STARTING_FRAME_ROW + relativeRow;
    }



    /**
     * This method now returns the ABSOLUTE column on the spritesheet by adding
     * the starting column offset to the current animation frame.
     */
    @Override
    public int getVisualFrameIndex() {
        int maxFrames = (currentAction == Action.SWING) ? FRAMES_PER_ATTACK_CYCLE : FRAMES_PER_IDLE_CYCLE;
        return STARTING_FRAME_COL + (currentFrameIndex % maxFrames);
    }

    @Override
    public void update(double deltaTime, Game game) {
        PlayerModel player = game.getPlayer();
        if (player == null || isDead) return;

        if (currentAction == Action.DEATH) {
            updateAnimation(deltaTime);
            return;
        }

        aiTimer += deltaTime;
        if (attackCooldownTimer > 0) {
            attackCooldownTimer -= deltaTime;
        }

        float distanceToPlayer = (float) Math.sqrt(Math.pow(player.getMapCol() - mapCol, 2) + Math.pow(player.getMapRow() - mapRow, 2));

        if (currentState != AiState.ATTACKING && distanceToPlayer <= ATTACK_RADIUS && attackCooldownTimer <= 0) {
            currentState = AiState.ATTACKING;
            this.setAction(Action.SWING);
        } else if (currentState != AiState.CHASING && distanceToPlayer <= AGGRO_RADIUS && distanceToPlayer > ATTACK_RADIUS) {
            currentState = AiState.CHASING;
        } else if (distanceToPlayer > AGGRO_RADIUS && currentState != AiState.WANDERING) {
            currentState = AiState.WANDERING;
            this.currentPath = null;
        }

        switch (currentState) {
            case WANDERING:
                wander(deltaTime, game.getMap());
                break;
            case CHASING:
                chase(deltaTime, player, game.getMap());
                break;
            case ATTACKING:
                attack(deltaTime, player);
                break;
        }

        visualCol += (this.mapCol - visualCol) * VISUAL_SMOOTH_FACTOR;
        visualRow += (this.mapRow - visualRow) * VISUAL_SMOOTH_FACTOR;
        updateAnimation(deltaTime);
    }

    private void wander(double deltaTime, Map map) {
        setAction(Action.WALK);
        if (aiTimer > THINK_INTERVAL) {
            aiTimer = 0;
            if (currentPath == null || currentPath.isEmpty()) {
                int targetR = getTileRow() + random.nextInt(WANDER_RADIUS * 2) - WANDER_RADIUS;
                int targetC = getTileCol() + random.nextInt(WANDER_RADIUS * 2) - WANDER_RADIUS;
                Tile targetTile = map.getTile(targetR, targetC);
                if (targetTile != null && targetTile.getType() != Tile.TileType.WATER) {
                    this.currentPath = pathfinder.findPath(getTileRow(), getTileCol(), targetR, targetC, map);
                    if (this.currentPath != null && this.currentPath.size() > 1) {
                        this.currentPathIndex = 1;
                    }
                }
            }
        }
        followPath(deltaTime, WANDER_SPEED);
    }

    private void chase(double deltaTime, PlayerModel player, Map map) {
        setAction(Action.WALK);
        if (aiTimer > 1.0) {
            aiTimer = 0;
            this.currentPath = pathfinder.findPath(getTileRow(), getTileCol(), player.getTileRow(), player.getTileCol(), map);
            if (this.currentPath != null && this.currentPath.size() > 1) {
                this.currentPathIndex = 1;
            }
        }
        followPath(deltaTime, CHASE_SPEED);
    }

    private void attack(double deltaTime, PlayerModel player) {
        currentPath = null; // Stop moving
        if (currentFrameIndex == 3 && attackCooldownTimer <= 0) {
            player.takeDamage(ATTACK_DAMAGE, this); // <-- Pass 'this' as the attacker
            attackCooldownTimer = ATTACK_COOLDOWN;
        }
    }

    private void followPath(double deltaTime, float speed) {
        if (currentPath == null || currentPathIndex < 0 || currentPathIndex >= currentPath.size()) {
            currentPath = null;
            return;
        }

        PathNode targetNode = currentPath.get(currentPathIndex);
        float dR = targetNode.row - mapRow;
        float dC = targetNode.col - mapCol;

        float distance = (float) Math.sqrt(dR * dR + dC * dC);
        if (distance < 0.1f) {
            currentPathIndex++;
            if (currentPathIndex >= currentPath.size()) {
                currentPath = null;
            }
        } else {
            float moveAmount = speed * (float) deltaTime;
            mapRow += (dR / distance) * moveAmount;
            mapCol += (dC / distance) * moveAmount;
            updateDirection(dC, dR);
        }
    }

    @Override
    protected void onDeath() {
        if (currentAction != Action.DEATH) {
            System.out.println("A slime has been defeated!");
            setAction(Action.DEATH); // Set the action to DEATH
            this.currentPath = null; // Stop any current movement

            // Drop loot at the moment of death
            if (this.owner instanceof PlayerModel) {
                PlayerModel player = (PlayerModel) this.owner;
                int lootAmount = 1 + random.nextInt(3);
                player.addItemToInventory(ItemRegistry.getItem("slime_gel"), lootAmount);
            }
        }
    }

    private void updateAnimation(double deltaTime) {
        // --- FIX: Use a slower frame duration specifically for the death animation ---
        double currentFrameDuration = (currentAction == Action.DEATH) ? 0.35 : this.frameDuration;

        animationTimer += deltaTime;
        if (animationTimer >= currentFrameDuration) {
            animationTimer -= currentFrameDuration;

            int maxFrames;
            if (currentAction == Action.SWING) {
                maxFrames = FRAMES_PER_ATTACK_CYCLE;
            } else if (currentAction == Action.DEATH) {
                maxFrames = FRAMES_PER_DEATH_CYCLE;
            } else {
                maxFrames = FRAMES_PER_IDLE_CYCLE;
            }

            // --- FIX: Simplified and corrected animation loop logic ---

            // Don't advance the frame if the death animation is already on its last frame
            if (currentAction == Action.DEATH && currentFrameIndex >= maxFrames - 1) {
                // Stay on the last frame
            } else {
                currentFrameIndex++; // Advance to the next frame
            }

            // Now, check if any animation has just finished its cycle
            if (currentFrameIndex >= maxFrames) {
                if (currentAction == Action.DEATH) {
                    // When the death animation finishes, mark the entity as dead for removal
                    this.isDead = true;
                    // Clamp to the last frame so it doesn't disappear before being removed
                    currentFrameIndex = maxFrames - 1;
                } else if (currentAction == Action.SWING) {
                    // After attacking, go back to being idle
                    setAction(Action.IDLE);
                    currentState = AiState.WANDERING;
                    currentFrameIndex = 0; // Reset frame
                } else {
                    // Loop all other animations
                    currentFrameIndex = 0;
                }
            }
        }
    }

    @Override public String getDisplayName() { return "Slime"; }
    @Override public int getFrameWidth() { return FRAME_WIDTH; }
    @Override public int getFrameHeight() { return FRAME_HEIGHT; }
}
