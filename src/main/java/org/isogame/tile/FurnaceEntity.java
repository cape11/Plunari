// src/main/java/org/isogame/tile/FurnaceEntity.java
package org.isogame.tile;

import org.isogame.constants.Constants;
import org.isogame.game.Game;
import org.isogame.item.InventorySlot;
import org.isogame.item.ItemRegistry;
import org.isogame.savegame.InventorySlotSaveData;
import org.isogame.savegame.TileEntitySaveData;

import java.util.Map;

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
    public FurnaceEntity(TileEntitySaveData saveData) {
        super(saveData.row, saveData.col);
        this.isSmelting = (boolean) saveData.customData.getOrDefault("isSmelting", false);
        this.fuelTime = (double) saveData.customData.getOrDefault("fuelTime", 0.0);
        this.cookTime = (double) saveData.customData.getOrDefault("cookTime", 0.0);

        // This is now safe and correct
        this.inputSlot.loadState(InventorySlotSaveData.fromMap((Map<String, Object>) saveData.customData.get("inputSlot")));
        this.fuelSlot.loadState(InventorySlotSaveData.fromMap((Map<String, Object>) saveData.customData.get("fuelSlot")));
        this.outputSlot.loadState(InventorySlotSaveData.fromMap((Map<String, Object>) saveData.customData.get("outputSlot")));
    }


    @Override
    public void update(double deltaTime, Game game) {
        boolean wasSmelting = isSmelting;

        if (fuelTime > 0) {
            fuelTime -= deltaTime;
        }

        if (fuelTime > 0 && !inputSlot.isEmpty() && "loose_rock".equals(inputSlot.getItem().getItemId())) {
            isSmelting = true;
            cookTime += deltaTime;

            if (cookTime >= MAX_COOK_TIME) {
                cookTime = 0;
                inputSlot.removeQuantity(1);
                outputSlot.addItem(ItemRegistry.getItem("stone"), 1);
            }
        } else {
            isSmelting = false;
            cookTime = 0;
        }

        if (fuelTime <= 0) {
            if (isSmelting && !fuelSlot.isEmpty() && "wood".equals(fuelSlot.getItem().getItemId())) {
                fuelSlot.removeQuantity(1);
                fuelTime += 10.0;
            } else {
                isSmelting = false;
            }
        }

        if (isSmelting) {
            animationTimer += deltaTime;
            if (animationTimer >= FRAME_DURATION) {
                animationTimer = 0;
                currentFrame = (currentFrame + 1) % 4;
            }
        }

        if (isSmelting && !wasSmelting) {
            game.getLightManager().addLightSource(this.row, this.col, (byte)13);
        } else if (!isSmelting && wasSmelting) {
            game.getLightManager().removeLightSource(this.row, this.col);
        }
    }

    @Override
    public void onInteract(Game game) {
        System.out.println("Interacted with Furnace. Opening UI...");
        game.getUiManager().openFurnaceUI(this);
    }

    @Override
    public TileEntitySaveData getSaveData() {
        TileEntitySaveData saveData = new TileEntitySaveData();
        saveData.type = "FURNACE";
        saveData.row = this.row;
        saveData.col = this.col;
        saveData.customData.put("isSmelting", this.isSmelting);
        saveData.customData.put("fuelTime", this.fuelTime);
        saveData.customData.put("cookTime", this.cookTime);

        // This is now safe and correct
        saveData.customData.put("inputSlot", this.inputSlot.getSaveData().toMap());
        saveData.customData.put("fuelSlot", this.fuelSlot.getSaveData().toMap());
        saveData.customData.put("outputSlot", this.outputSlot.getSaveData().toMap());
        return saveData;
    }

    public boolean isSmelting() { return isSmelting; }
    public int getCurrentFrame() { return currentFrame; }
    public InventorySlot getInputSlot() { return inputSlot; }
    public InventorySlot getFuelSlot() { return fuelSlot; }
    public InventorySlot getOutputSlot() { return outputSlot; }
}