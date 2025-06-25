// src/main/java/org/isogame/tile/FurnaceEntity.java
package org.isogame.tile;

import org.isogame.constants.Constants;
import org.isogame.game.Game;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.ItemRegistry;

public class FurnaceEntity extends TileEntity {

    // Furnace State
    private boolean isSmelting = false;
    private double fuelTime = 0;
    private double cookTime = 0;
    private final double MAX_COOK_TIME = 5.0; // 5 seconds to smelt an item

    // Animation State
    private int currentFrame = 0;
    private double animationTimer = 0.0;
    private final double FRAME_DURATION = 0.2; // How fast the animation plays

    // Furnace Inventory
    private final InventorySlot inputSlot = new InventorySlot();
    private final InventorySlot fuelSlot = new InventorySlot();
    private final InventorySlot outputSlot = new InventorySlot();

    public FurnaceEntity(int row, int col) {
        super(row, col);
    }

    @Override
    public void update(double deltaTime, Game game) {
        boolean wasSmelting = isSmelting;

        // If we have fuel, keep burning it
        if (fuelTime > 0) {
            fuelTime -= deltaTime;
        }

        // Check if we can start smelting
        // For this example, we'll say "wood" is fuel and "loose_rock" is the input
        if (fuelTime > 0 && !inputSlot.isEmpty() && inputSlot.getItem().getItemId().equals("loose_rock")) {
            isSmelting = true;
            cookTime += deltaTime;

            // If smelting is finished
            if (cookTime >= MAX_COOK_TIME) {
                cookTime = 0;
                inputSlot.removeQuantity(1); // Consume one ore

                // For this example, smelting a rock gives a stone.
                outputSlot.addItem(ItemRegistry.getItem("stone"), 1);
            }
        } else {
            // Stop smelting if out of fuel or input
            isSmelting = false;
            cookTime = 0;
        }

        // If we ran out of fuel completely
        if (fuelTime <= 0) {
            // Check if there's more fuel in the fuel slot
            if (isSmelting && !fuelSlot.isEmpty() && fuelSlot.getItem().getItemId().equals("wood")) {
                fuelSlot.removeQuantity(1);
                fuelTime += 10.0; // 1 wood log gives 10 seconds of fuel
            } else {
                isSmelting = false; // Truly out of fuel
            }
        }

        // Update animation if smelting
        if (isSmelting) {
            animationTimer += deltaTime;
            if (animationTimer >= FRAME_DURATION) {
                animationTimer = 0;
                currentFrame = (currentFrame + 1) % 4; // Loop through the 4 animation frames
            }
        }

        // Manage light source based on smelting state
        if (isSmelting && !wasSmelting) {
            // We just turned ON
            game.getLightManager().addLightSource(this.row, this.col, (byte)13);
        } else if (!isSmelting && wasSmelting) {
            // We just turned OFF
            game.getLightManager().removeLightSource(this.row, this.col);
        }
    }

    @Override
    public void onInteract(Game game) {
        System.out.println("Interacted with Furnace. Opening UI...");
        game.getUiManager().openFurnaceUI(this);
    }

    public boolean isSmelting() { return isSmelting; }
    public int getCurrentFrame() { return currentFrame; }
    public InventorySlot getInputSlot() { return inputSlot; }
    public InventorySlot getFuelSlot() { return fuelSlot; }
    public InventorySlot getOutputSlot() { return outputSlot; }
}