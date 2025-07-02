package org.isogame.entity;

import org.isogame.game.Game;
import org.isogame.item.Item;

// DroppedItem now extends Entity to be part of the game world
public class DroppedItem extends Entity {

    private final Item item;
    private final int quantity;
    private int pickupDelay; // Timer in frames to prevent instant pickup
    private double bobTimer; // For a simple visual bobbing effect

    public DroppedItem(Item item, int quantity, float startRow, float startCol) {
        // We assume Item is a valid class in your project
        if (item == null) {
            throw new IllegalArgumentException("DroppedItem cannot have a null Item.");
        }
        this.item = item;
        this.quantity = quantity;
        this.setPosition(startRow, startCol);
        this.pickupDelay = 30; // Wait 30 frames (e.g., 0.5s at 60fps) before pickup
    }

    @Override
    public void update(double deltaTime, Game game) {
        // Countdown the pickup delay timer
        if (pickupDelay > 0) {
            pickupDelay--;
        }
        // Add a gentle bobbing motion for visual appeal
        bobTimer += deltaTime * 4.0; // Adjust speed of bobbing
    }

    // A check to see if the pickup delay has passed
    public boolean canBePickedUp() {
        return pickupDelay <= 0;
    }

    public Item getItem() { return item; }
    public int getQuantity() { return quantity; }

    // The z-offset for rendering the bobbing effect
    public float getBobOffset() {
        return (float) Math.sin(bobTimer) * 0.1f;
    }

    // --- Required abstract methods from Entity ---
    // These won't be used for animation, but are needed to compile.
    // The renderer will need special logic to draw this entity based on its Item.
    @Override public int getAnimationRow() { return -1; }
    @Override public String getDisplayName() { return item.getDisplayName(); } // Assumes Item has a getDisplayName() method
    @Override public int getFrameWidth() { return 16; } // Items are typically smaller
    @Override public int getFrameHeight() { return 16; }
}