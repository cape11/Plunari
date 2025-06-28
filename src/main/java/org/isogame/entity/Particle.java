package org.isogame.entity;

import org.isogame.game.Game;
import java.util.Random;

public class Particle extends Entity {

    private float dx, dy, dz;    // Velocity
    private int life;            // How long the particle exists
    private final int maxLife;
    private float z;

    public float[] color;
    public float size;
    private static final Random random = new Random();

    public Particle(float startRow, float startCol, float startZ, float dx, float dy, float dz, int life) {
        this.setPosition(startRow, startCol);
        this.visualRow = startRow;
        this.visualCol = startCol;
        this.z = startZ; //

        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.life = life;
        this.maxLife = life;

        float baseGrey = 0.4f + random.nextFloat() * 0.2f;
        this.color = new float[] { baseGrey, baseGrey * 0.8f, baseGrey * 0.6f, 1.0f };
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
        this.z += dz * deltaTime; // ✅ FIX: Update the float z variable

        // Apply gravity
        this.dz -= 0.5f;

        this.visualRow = mapRow;
        this.visualCol = mapCol;
    }

    // ✅ FIX: getZ now returns the float z
    public float getZ() { return z; }

    @Override public int getAnimationRow() { return -1; }
    @Override public String getDisplayName() { return "Particle"; }
    @Override public int getFrameWidth() { return 1; }
    @Override public int getFrameHeight() { return 1; }

    public int getLife() { return life; }
    public int getMaxLife() { return maxLife; }
}