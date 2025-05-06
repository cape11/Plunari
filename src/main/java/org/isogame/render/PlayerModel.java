package org.isogame.entity;

import static org.isogame.constants.Constants.*;

/**
 * Represents the state and properties of the player character.
 * Does NOT handle rendering directly.
 */
public class PlayerModel {

    // Position in Map coordinates (Tile units)
    // Using float for smoother movement between tiles later if desired
    private float mapRow;
    private float mapCol;

    // Current state
    private boolean levitating = false;
    private float levitateTimer = 0; // For animation

    public PlayerModel(int startRow, int startCol) {
        this.mapRow = startRow;
        this.mapCol = startCol;
    }

    public void update(double deltaTime) {
        if (levitating) {
            levitateTimer += (float) deltaTime * 5.0f; // Adjust speed as needed
        }
        // Add other player update logic here (e.g., movement interpolation)
    }

    // --- Getters ---
    public float getMapRow() {
        return mapRow;
    }

    public float getMapCol() {
        return mapCol;
    }

    public int getTileRow() {
        return Math.round(mapRow); // Current tile the player is mostly on
    }

    public int getTileCol() {
        return Math.round(mapCol);
    }

    public boolean isLevitating() {
        return levitating;
    }

    public float getLevitateTimer() {
        return levitateTimer;
    }

    // --- Setters / Modifiers ---
    public void setPosition(float row, float col) {
        // Add boundary checks if necessary (though usually done in Map/Game logic before calling this)
        this.mapRow = row;
        this.mapCol = col;
    }

    public void move(float dRow, float dCol) {
        this.mapRow += dRow;
        this.mapCol += dCol;
        // Add clamping to map bounds here or in Game logic
    }

    public void toggleLevitate() {
        this.levitating = !this.levitating;
        if (!this.levitating) {
            levitateTimer = 0; // Reset timer when stopping
        }
    }

    public void setLevitating(boolean levitating) {
        this.levitating = levitating;
        if (!this.levitating) {
            levitateTimer = 0;
        }
    }
}