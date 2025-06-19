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

    // --- FIX: Use a Set to track what this swing has already damaged ---
    private final Set<Object> damagedTargets = new HashSet<>();
    private static final Random random = new Random();
    private final ToolItem.ToolType toolType; // <-- ADD THIS FIELD

    public SwingArcProjectile(Entity owner, int targetRow, int targetCol, int damage, float knockback, ToolItem.ToolType toolType) {
        // The lifetime is short because the swing is instantaneous.
        // It does all its work on the first frame and then waits to be removed.
        super(owner, targetRow, targetCol, 5, damage, knockback);
        this.toolType = toolType; // <-- Store the tool type

    }

    @Override
    public void update(double deltaTime, Game game) {
        super.update(deltaTime, game); // This handles the timeToLive countdown

        // An instantaneous projectile should only do its damage once.
        // We use the damagedTargets set to see if we've already done our work.
        if (isDead || !damagedTargets.isEmpty()) {
            return;
        }

        // The center of our Area of Effect (AoE) is the projectile's logical location
        int centerX = this.getTileCol();
        int centerY = this.getTileRow();

        // Loop through a 3x3 area centered on the projectile
        for (int r_offset = -1; r_offset <= 1; r_offset++) {
            for (int c_offset = -1; c_offset <= 1; c_offset++) {
                int currentCheckC = centerX + c_offset;
                int currentCheckR = centerY + r_offset;

                // --- Check for TREES within the AoE ---
                Tile targetTile = game.getMap().getTile(currentCheckR, currentCheckC);
                if (targetTile != null && targetTile.getTreeType() != Tile.TreeVisualType.NONE && !damagedTargets.contains(targetTile)) {
                    if (this.toolType == ToolItem.ToolType.AXE) {
                        targetTile.startShake();
                    }
                    targetTile.takeDamage(this.damage);
                    spawnHitParticles(game, currentCheckR, currentCheckC, targetTile.getElevation());
                    damagedTargets.add(targetTile); // Mark this tile as damaged by this swing

                    if (targetTile.getHealth() <= 0) {
                        targetTile.setTreeType(Tile.TreeVisualType.NONE);
                        game.getMap().queueLightUpdateForArea(currentCheckR, currentCheckC, 2, game.getLightManager());
                        if (owner instanceof PlayerModel) {
                            ((PlayerModel) owner).addItemToInventory(ItemRegistry.getItem("wood"), 3);
                        }
                    }
                }

                // --- Check for ENTITIES within the AoE ---
                // We create a copy of the list to avoid ConcurrentModificationException if an entity is killed.
                for (Entity entity : new ArrayList<>(game.getMap().getEntities())) {
                    if (damagedTargets.contains(entity)) {
                        continue; // Already hit this entity with this swing
                    }

                    // Ignore particles, the owner, and other projectiles
                    if (entity instanceof Particle || entity == owner || entity instanceof Projectile) {
                        continue;
                    }

                    if (!entity.isDead() && entity.getTileRow() == currentCheckR && entity.getTileCol() == currentCheckC) {
                        entity.takeDamage(this.damage, this.owner);
                        spawnHitParticles(game, entity.getMapRow(), entity.getMapCol(), game.getMap().getTile(entity.getTileRow(), entity.getTileCol()).getElevation());
                        damagedTargets.add(entity); // Mark this entity as damaged by this swing
                    }
                }
            }
        }
    }

    private void spawnHitParticles(Game game, float row, float col, int elevation) {
        int particleCount = 3 + random.nextInt(3);
        for (int i = 0; i < particleCount; i++) {
            float vx = (random.nextFloat() - 0.5f) * 4f;
            float vy = (random.nextFloat() - 0.5f) * 4f;
            float vz = 1f + random.nextFloat() * 4f;
            int life = 15 + random.nextInt(15);

            // The Z position of the particle starts at the tile's height + a small random offset
            float startZ = (elevation * Constants.TILE_THICKNESS) + 5.0f;

            Particle p = new Particle(row, col, startZ, vx, vy, vz, life);
            game.getMap().getEntities().add(p);
        }
    }
}
