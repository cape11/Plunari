package org.isogame.entity;

import org.isogame.game.Game;
import org.isogame.item.ItemRegistry;
import org.isogame.tile.Tile;

/**
 * An invisible projectile that represents the arc of a melee swing.
 * It checks a single tile for an interactable object (like a tree) and applies its effect.
 */
public class SwingArcProjectile extends Projectile {

    private boolean hasHit = false; // Ensures the projectile only hits once

    public SwingArcProjectile(Entity owner, int targetRow, int targetCol, int damage, float knockback) {
        // The projectile exists at the target tile's location for a very short time.
        super(owner, targetRow, targetCol, 5, damage, knockback); // Lives for 5 frames
    }

    @Override
    public void update(double deltaTime, Game game) {
        super.update(deltaTime, game); // Ticks down timeToLive

        if (hasHit) return;

        Tile targetTile = game.getMap().getTile(this.getTileRow(), this.getTileCol());
        if (targetTile != null) {

            // --- This is the logic that used to be in ToolItem.useAxe() ---
            if (targetTile.getTreeType() != Tile.TreeVisualType.NONE) {
                targetTile.setTreeType(Tile.TreeVisualType.NONE);
                game.getMap().queueLightUpdateForArea(this.getTileRow(), this.getTileCol(), 2, game.getLightManager());

                if (owner instanceof PlayerModel) {
                    ((PlayerModel) owner).addItemToInventory(ItemRegistry.WOOD, 3);
                }
                this.hasHit = true;
            }

            // TODO: Add collision check for enemies here in the future
            // for (Entity e : game.getMap().getEntities()) {
            //    if (e is an enemy and collides with this projectile's location) {
            //        e.takeDamage(this.damage);
            //        hasHit = true;
            //    }
            // }
        }
    }
}
