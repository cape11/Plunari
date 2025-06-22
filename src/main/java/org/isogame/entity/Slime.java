package org.isogame.entity;

import com.google.gson.Gson;
import org.isogame.gamedata.AnimationDefinition;
import org.isogame.game.Game;
import org.isogame.item.ItemRegistry;
import org.isogame.map.AStarPathfinder;
import org.isogame.map.Map;
import org.isogame.map.PathNode;
import org.isogame.savegame.EntitySaveData;
import org.isogame.tile.Tile;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Random;

public class Slime extends Entity {

    private final Random random = new Random();
    private final AStarPathfinder pathfinder = new AStarPathfinder();

    private static final float WANDER_SPEED = 0.8f;
    private static final float CHASE_SPEED = 2.0f;
    private static final int WANDER_RADIUS = 5;
    private static final double THINK_INTERVAL = 2.5;
    private static final double AGGRO_RADIUS = 7.0;
    private static final double ATTACK_RADIUS = 1.5;
    private static final double ATTACK_COOLDOWN = 1.8;
    private static final int ATTACK_DAMAGE = 2;

    private enum AiState { WANDERING, CHASING, ATTACKING }
    private AiState currentState = AiState.WANDERING;
    private double aiTimer = 0.0;
    private double attackCooldownTimer = 0.0;

    // --- NEW: These constants will hold the *absolute* starting position from the JSON ---
    private int STARTING_FRAME_COL = 0;
    private int STARTING_FRAME_ROW = 0;

    public Slime(float startRow, float startCol) {
        super();
        this.maxHealth = 10;
        this.health = 10;
        this.setPosition(startRow, startCol);
        loadAnimationDefinition("/data/animations/slime_animations.json");
        if (this.animDef != null) {
            this.frameDuration = animDef.animations.get("idle").frameDuration;
            // --- NEW: Calculate the starting positions after loading ---
            // These values are hardcoded in the provided instructions, but could also be in the JSON
            this.STARTING_FRAME_COL = 576 / getFrameWidth();  // 576 is SPRITESHEET_START_X_PIXEL
            this.STARTING_FRAME_ROW = 1664 / getFrameHeight(); // 1664 is SPRITESHEET_START_Y_PIXEL
        }
    }

    @Override
    public void populateSaveData(EntitySaveData data) {
        super.populateSaveData(data);
        data.entityType = "SLIME";
    }

    @Override
    public int getAnimationRow() {
        if (animDef == null || animDef.animations == null) return 0;

        String animKey;
        switch (currentAction) {
            case SWING: animKey = "attack"; break;
            case DEATH: animKey = "death"; break;
            default:    animKey = "idle"; break;
        }

        AnimationDefinition.AnimationTrack track = animDef.animations.get(animKey);
        // The "row" in the JSON is now treated as a local offset
        return STARTING_FRAME_ROW + ((track != null) ? track.row : 0);
    }

    @Override
    public int getVisualFrameIndex() {
        if (animDef == null || animDef.animations == null) return 0;

        AnimationDefinition.AnimationTrack track = animDef.animations.get(currentAnimationName);
        int maxFrames = (track != null) ? track.frames : 1;

        return STARTING_FRAME_COL + (currentFrameIndex % maxFrames);
    }

    // ... (rest of the Slime class is identical to the previous version) ...

    @Override
    public int getFrameWidth() {
        return (animDef != null) ? animDef.frameWidth : 64;
    }

    @Override
    public int getFrameHeight() {
        return (animDef != null) ? animDef.frameHeight : 64;
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
            this.currentAnimationName = "attack";
        } else if (currentState != AiState.CHASING && distanceToPlayer <= AGGRO_RADIUS && distanceToPlayer > ATTACK_RADIUS) {
            currentState = AiState.CHASING;
            this.setAction(Action.WALK);
            this.currentAnimationName = "idle";
        } else if (distanceToPlayer > AGGRO_RADIUS && currentState != AiState.WANDERING) {
            currentState = AiState.WANDERING;
            this.currentPath = null;
            this.setAction(Action.IDLE);
            this.currentAnimationName = "idle";
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
            player.takeDamage(ATTACK_DAMAGE, this);
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
            setAction(Action.DEATH);
            this.currentAnimationName = "death";
            this.currentPath = null;

            if (this.owner instanceof PlayerModel) {
                PlayerModel player = (PlayerModel) this.owner;
                int lootAmount = 1 + random.nextInt(3);
                player.addItemToInventory(ItemRegistry.getItem("slime_gel"), lootAmount);
            }
        }
    }

    private void updateAnimation(double deltaTime) {
        if (animDef == null || animDef.animations == null) return;

        AnimationDefinition.AnimationTrack track = animDef.animations.get(currentAnimationName);
        if (track == null) track = animDef.animations.get("idle"); // Fallback
        if (track == null) return;

        animationTimer += deltaTime;
        if (animationTimer >= track.frameDuration) {
            animationTimer -= track.frameDuration;

            if (currentAction == Action.DEATH && currentFrameIndex >= track.frames - 1) {
                // Stay on the last frame
            } else {
                currentFrameIndex++;
            }

            if (currentFrameIndex >= track.frames) {
                if (currentAction == Action.DEATH) {
                    this.isDead = true;
                    currentFrameIndex = track.frames - 1;
                } else if (currentAction == Action.SWING) {
                    setAction(Action.IDLE);
                    this.currentAnimationName = "idle";
                    currentState = AiState.WANDERING;
                    currentFrameIndex = 0;
                } else {
                    currentFrameIndex = 0;
                }
            }
        }
    }

    @Override
    public String getDisplayName() { return "Slime"; }
}