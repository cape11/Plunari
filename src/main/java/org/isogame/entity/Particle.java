package org.isogame.entity;

import org.isogame.game.Game;
import java.util.Random; // <-- Make sure this import is present

public class Particle extends Entity {

    private float dx, dy, dz; // Velocity
    private int life;        // How long the particle exists

    // ✅ FIX: Declare the missing fields
    public float[] color;
    public float size;
    private static final Random random = new Random();


    public Particle(float startRow, float startCol, float startZ, float dx, float dy, float dz, int life) {
        this.setPosition(startRow, startCol);
        this.visualRow = startRow;
        this.visualCol = startCol;
        this.health = (int) startZ; // Using health for Z
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.life = life;

        // Give particles a random brownish-grey color (like wood/dirt)
        float baseGrey = 0.4f + random.nextFloat() * 0.2f;
        this.color = new float[] { baseGrey, baseGrey * 0.8f, baseGrey * 0.6f, 1.0f };

        // Give particles a random size
        this.size = 2.0f + random.nextFloat() * 3.0f;
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
        this.dz -= 0.5f;

        this.visualRow = mapRow;
        this.visualCol = mapCol;
    }

    public int getZ() { return health; }

    // Abstract methods required by Entity
    @Override public int getAnimationRow() { return -1; } // Not animated
    @Override public String getDisplayName() { return "Particle"; }
    @Override public int getFrameWidth() { return 1; } // Non-zero to avoid division errors
    @Override public int getFrameHeight() { return 1; }
}