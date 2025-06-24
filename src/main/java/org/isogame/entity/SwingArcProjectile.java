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
        super.update(deltaTime, game);
        if (isDead) {
            return;
        }

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
                    spawnHitParticles(game, currentCheckR, currentCheckC, targetTile.getElevation());
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
                        spawnHitParticles(game, entity.getMapRow(), entity.getMapCol(), game.getMap().getTile(entity.getTileRow(), entity.getTileCol()).getElevation());
                        damagedTargets.add(entity);
                    }
                }
            }
        }
    }

    private void spawnHitParticles(Game game, float row, float col, int elevation) {
        int particleCount = 4 + random.nextInt(4); // More particles
        for (int i = 0; i < particleCount; i++) {
            float vx = (random.nextFloat() - 0.5f) * 5f;
            float vy = (random.nextFloat() - 0.5f) * 5f;
            // *** FIX: Increased upward velocity to make them more visible ***
            float vz = 3f + random.nextFloat() * 5f;

            // *** FIX: Increased lifespan to make them more visible ***
            int life = 30 + random.nextInt(30);

            float startZ = (elevation * Constants.TILE_THICKNESS) + (Constants.TILE_HEIGHT / 2.0f);

            Particle p = new Particle(row, col, startZ, vx, vy, vz, life);
            game.getEntityManager().addEntity(p);
        }
    }
}