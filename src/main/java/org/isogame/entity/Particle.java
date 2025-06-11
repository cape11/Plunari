package org.isogame.entity;

import org.isogame.game.Game;

/**
 * A simple, visual-only entity that moves in a direction and fades over time.
 */
public class Particle extends Entity {

    private float dx, dy, dz; // Velocity
    private int life;        // How long the particle exists

    public Particle(float startRow, float startCol, float startZ, float dx, float dy, float dz, int life) {
        this.setPosition(startRow, startCol);
        this.visualRow = startRow; // Set visual position immediately
        this.visualCol = startCol;
        // We can repurpose the 'health' field from Entity to store Z position for rendering
        this.health = (int) startZ;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.life = life;
    }

    @Override
    public void update(double deltaTime, Game game) {
        life--;
        if (life <= 0) {
            this.isDead = true;
        }

        // Apply velocity
        this.mapRow += dy * deltaTime;
        this.mapCol += dx * deltaTime;
        this.health += dz * deltaTime; // Z position update

        // Apply gravity
        this.dz -= 0.5f; // Simple gravity

        this.visualRow = mapRow;
        this.visualCol = mapCol;
    }

    public int getZ() { return health; } // Use health to store Z

    // Particles don't need these, but the abstract class requires them
    @Override public int getAnimationRow() { return 0; }
    @Override public String getDisplayName() { return "Particle"; }
    @Override public int getFrameWidth() { return 0; }
    @Override public int getFrameHeight() { return 0; }
}
