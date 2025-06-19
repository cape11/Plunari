package org.isogame.entity;

import org.isogame.game.Game;

/**
 * A generic entity representing a temporary effect in the world, like a melee swing or a magic bolt.
 * It is spawned, performs an action for a short duration, and then removes itself.
 */
public abstract class Projectile extends Entity {

    protected Entity owner;         // The entity that created this projectile
    protected int timeToLive;       // How many frames the projectile exists for
    protected int damage;
    protected float knockback;

    public Projectile(Entity owner, float startRow, float startCol, int timeToLive, int damage, float knockback) {
        this.owner = owner;
        this.timeToLive = timeToLive;
        this.damage = damage;
        this.knockback = knockback;
        this.setPosition(startRow, startCol);
    }

    @Override
    public void update(double deltaTime, Game game) {
        // All projectiles have a limited lifespan.
        timeToLive--;
        if (timeToLive <= 0) {
            // --- FIX: Instead of removing itself, just mark as dead ---
            this.isDead = true;
        }
    }

    // Projectiles are typically invisible and have no standard animation.
    @Override
    public int getAnimationRow() { return -1; }
    @Override
    public String getDisplayName() { return "Projectile"; }
    @Override
    public int getFrameWidth() { return 0; }
    @Override
    public int getFrameHeight() { return 0; }
}
