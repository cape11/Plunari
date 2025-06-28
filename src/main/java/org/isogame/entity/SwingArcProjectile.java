// src/main/java/org/isogame/entity/SwingArcProjectile.java
package org.isogame.entity;

import org.isogame.constants.Constants;
import org.isogame.game.Game;
import org.isogame.item.ItemRegistry;
import org.isogame.item.ToolItem;
import org.isogame.tile.Tile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class SwingArcProjectile extends Projectile {

    private final Set<Object> damagedTargets = new HashSet<>();
    private static final Random random = new Random();
    private final ToolItem.ToolType toolType;

    public SwingArcProjectile(Entity owner, int targetRow, int targetCol, int damage, float knockback, ToolItem.ToolType toolType) {
        super(owner, targetRow, targetCol, 5, damage, knockback);
        this.toolType = toolType;
    }

    @Override
    public void update(double deltaTime, Game game) {
        // --- This first part, the countdown and death check, remains the same ---
        super.update(deltaTime, game);
        if (isDead) {
            return;
        }

        // --- NEW: Particle Trail Logic ---
        // Every frame, we spawn a few particles at the projectile's current location
        // to create the visual "swoosh" of the swing.
        int particlesThisFrame = 2; // Controls the density of the trail
        for (int i = 0; i < particlesThisFrame; i++) {
            // Particles fly outwards from the center of the swing
            float vx = (random.nextFloat() - 0.5f) * 3f;
            float vy = (random.nextFloat() - 0.5f) * 3f;
            float vz = 1f + random.nextFloat() * 2f; // Slight upward drift

            // Particles have a short life to create a fading trail effect
            int life = 15 + random.nextInt(10);

            // Get the elevation of the tile the projectile is currently over
            Tile currentTile = game.getMap().getTile(this.getTileRow(), this.getTileCol());
            float startZ = (currentTile != null) ? (currentTile.getElevation() * Constants.TILE_THICKNESS) + (Constants.TILE_HEIGHT / 2.0f) : 0;

            // Create the particle and add it to the world
            Particle p = new Particle(this.getMapRow(), this.getMapCol(), startZ, vx, vy, vz, life);
            game.getEntityManager().addEntity(p);
        }
        // --- End of New Particle Logic ---


        // --- This second part, the damage logic, remains the same ---
        int centerX = this.getTileCol();
        int centerY = this.getTileRow();

        for (int r_offset = -1; r_offset <= 1; r_offset++) {
            for (int c_offset = -1; c_offset <= 1; c_offset++) {
                int currentCheckC = centerX + c_offset;
                int currentCheckR = centerY + r_offset;

                Tile targetTile = game.getMap().getTile(currentCheckR, currentCheckC);
                if (targetTile != null && targetTile.getTreeType() != Tile.TreeVisualType.NONE && !damagedTargets.contains(targetTile)) {
                    if (this.toolType == ToolItem.ToolType.AXE) {
                        targetTile.startShake();
                    }
                    targetTile.takeDamage(this.damage);
                    spawnHitParticles(game, currentCheckR, currentCheckC, targetTile.getElevation(), "wood");
                    damagedTargets.add(targetTile);

                    if (targetTile.getHealth() <= 0) {
                        targetTile.setTreeType(Tile.TreeVisualType.NONE);
                        game.getMap().queueLightUpdateForArea(currentCheckR, currentCheckC, 2, game.getLightManager());
                        if (owner instanceof PlayerModel) {
                            ((PlayerModel) owner).addItemToInventory(ItemRegistry.getItem("wood"), 3);
                        }
                    }
                }

                for (Entity entity : new ArrayList<>(game.getEntityManager().getEntities())) {
                    if (entity instanceof Particle || entity == owner || entity instanceof Projectile || damagedTargets.contains(entity)) {
                        continue;
                    }
                    if (!entity.isDead() && entity.getTileRow() == currentCheckR && entity.getTileCol() == currentCheckC) {
                        entity.takeDamage(this.damage, this.owner);
                        String entityType = (entity instanceof Slime) ? "slime" : "flesh";
                        spawnHitParticles(game, entity.getMapRow(), entity.getMapCol(), game.getMap().getTile(entity.getTileRow(), entity.getTileCol()).getElevation(), entityType);

                        damagedTargets.add(entity);
                    }
                }
            }
        }
    }

    // REPLACE the old spawnHitParticles method with this new version.

    private void spawnHitParticles(Game game, float row, float col, int elevation, String targetType) {
        int particleCount = 5 + random.nextInt(6);
        for (int i = 0; i < particleCount; i++) {
            float vx = (random.nextFloat() - 0.5f) * 7f;
            float vy = (random.nextFloat() - 0.5f) * 7f;
            float vz = 4f + random.nextFloat() * 6f;
            int life = 10 + random.nextInt(15);

            float startZ = (elevation * Constants.TILE_THICKNESS) + (Constants.TILE_HEIGHT / 2.0f);
            Particle p = new Particle(row, col, startZ, vx, vy, vz, life);

            // --- NEW: Logic to choose particle style based on the target type ---
            switch (targetType) {
                case "wood":
                    // Brown, slower particles for wood chips
                    float baseBrown = 0.4f + random.nextFloat() * 0.15f;
                    p.color = new float[]{baseBrown, baseBrown * 0.7f, baseBrown * 0.5f, 1.0f};
                    p.size = 2.0f + random.nextFloat() * 2.0f; // Slightly larger, chunkier particles
                    p.setZVelocity(vz - 2f);
                    break;
                case "slime":
                    // Bluish, goopy particles for hitting a slime
                    float baseBlue = 0.6f + random.nextFloat() * 0.2f;
                    p.color = new float[] { baseBlue * 0.3f, baseBlue * 0.7f, baseBlue, 0.9f };
                    p.size = 2.5f + random.nextFloat() * 2.5f; // A bit larger for a "splat" effect
                    p.setZVelocity(vz - 2f);
                    break;
                case "flesh":
                    // Reddish, "goopy" particles for hitting a slime or animal
                    float baseRed = 0.6f + random.nextFloat() * 0.2f;
                    p.color = new float[]{baseRed, baseRed * 0.2f, baseRed * 0.1f, 0.9f};
                    p.size = 2.0f + random.nextFloat() * 3.0f;
                    break;

                default:
                    // Default to bright sparks for hitting rock, metal, or unknown things
                    float baseGrey = 0.8f + random.nextFloat() * 0.2f;
                    p.color = new float[]{baseGrey, baseGrey, baseGrey * 0.6f, 1.0f};
                    p.size = 1.0f + random.nextFloat() * 2.0f;
                    break;
            }

            game.getEntityManager().addEntity(p);
        }
    }
}