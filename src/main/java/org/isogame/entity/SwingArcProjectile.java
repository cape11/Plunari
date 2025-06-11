package org.isogame.entity;

import org.isogame.game.Game;
import org.isogame.item.ItemRegistry;
import org.isogame.tile.Tile;
import java.util.ArrayList;
import java.util.Random;

public class SwingArcProjectile extends Projectile {

    private boolean hasHit = false;
    private static final Random random = new Random();

    public SwingArcProjectile(Entity owner, int targetRow, int targetCol, int damage, float knockback) {
        super(owner, targetRow, targetCol, 5, damage, knockback);
    }

    @Override
    public void update(double deltaTime, Game game) {
        super.update(deltaTime, game);
        if (hasHit || isDead) return;

        Tile targetTile = game.getMap().getTile(this.getTileRow(), this.getTileCol());
        if (targetTile != null) {

            // --- DAMAGE LOGIC FOR TREES ---
            if (targetTile.getTreeType() != Tile.TreeVisualType.NONE) {
                targetTile.takeDamage(this.damage);
                spawnHitParticles(game, this.getTileRow(), this.getTileCol(), targetTile.getElevation());
                this.hasHit = true;

                if (targetTile.getHealth() <= 0) {
                    targetTile.setTreeType(Tile.TreeVisualType.NONE);
                    game.getMap().queueLightUpdateForArea(this.getTileRow(), this.getTileCol(), 2, game.getLightManager());
                    if (owner instanceof PlayerModel) {
                        ((PlayerModel) owner).addItemToInventory(ItemRegistry.getItem("wood"), 3);                    }
                }
            }

            // --- DAMAGE LOGIC FOR ENTITIES ---
            for (Entity entity : new ArrayList<>(game.getMap().getEntities())) {
                if (entity != owner && entity != this && !entity.isDead()) {
                    if (entity.getTileRow() == this.getTileRow() && entity.getTileCol() == this.getTileCol()) {
                        entity.takeDamage(this.damage);
                        spawnHitParticles(game, entity.getMapRow(), entity.getMapCol(), game.getMap().getTile(entity.getTileRow(), entity.getTileCol()).getElevation());
                        this.hasHit = true;
                    }
                }
            }
        }
    }

    private void spawnHitParticles(Game game, float row, float col, int elevation) {
        int particleCount = 3 + random.nextInt(3); // Spawn 3-5 particles
        for (int i = 0; i < particleCount; i++) {
            float vx = (random.nextFloat() - 0.5f) * 4f;
            float vy = (random.nextFloat() - 0.5f) * 4f;
            float vz = 1f + random.nextFloat() * 4f;
            int life = 15 + random.nextInt(15);

            Particle p = new Particle(row, col, elevation, vx, vy, vz, life);
            game.getMap().getEntities().add(p);
        }
    }
}
