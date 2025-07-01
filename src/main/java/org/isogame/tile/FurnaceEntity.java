// main/java/org/isogame/tile/FurnaceEntity.java
package org.isogame.tile;

import org.isogame.crafting.FurnaceRecipe;
import org.isogame.crafting.FurnaceRecipeRegistry;
import org.isogame.game.Game;
import org.isogame.item.InventorySlot;
import org.isogame.item.Item;
import org.isogame.savegame.InventorySlotSaveData;
import org.isogame.savegame.TileEntitySaveData;
import java.util.HashMap;
import java.util.Map;

public class FurnaceEntity extends TileEntity {

    private static final Map<String, Double> FUEL_BURN_TIMES = new HashMap<>();
    static {
        FUEL_BURN_TIMES.put("wood", 15.0);
        FUEL_BURN_TIMES.put("wood_plank", 5.0);
        FUEL_BURN_TIMES.put("slime_gel", 3.0);
    }

    private boolean isSmelting = false;
    private double fuelTime = 0;
    private double cookTime = 0;
    private double maxCookTime = 0;
    private int currentFrame = 0;
    private double animationTimer = 0.0;
    private final double FRAME_DURATION = 0.2;
    private final InventorySlot inputSlot = new InventorySlot();
    private final InventorySlot fuelSlot = new InventorySlot();
    private final InventorySlot outputSlot = new InventorySlot();

    public FurnaceEntity(int row, int col) { super(row, col); }

    public FurnaceEntity(TileEntitySaveData saveData) {
        super(saveData.row, saveData.col);
        this.fuelTime = (double) saveData.customData.getOrDefault("fuelTime", 0.0);
        this.cookTime = (double) saveData.customData.getOrDefault("cookTime", 0.0);
        this.inputSlot.loadState(InventorySlotSaveData.fromMap((Map<String, Object>) saveData.customData.get("inputSlot")));
        this.fuelSlot.loadState(InventorySlotSaveData.fromMap((Map<String, Object>) saveData.customData.get("fuelSlot")));
        this.outputSlot.loadState(InventorySlotSaveData.fromMap((Map<String, Object>) saveData.customData.get("outputSlot")));
    }

    @Override
    public void update(double deltaTime, Game game) {
        boolean wasSmelting = isSmelting;
        FurnaceRecipe currentRecipe = FurnaceRecipeRegistry.getRecipeForInput(inputSlot.getItem());

        if (fuelTime <= 0 && currentRecipe != null && canSmelt(currentRecipe)) {
            consumeFuel();
        }

        if (fuelTime > 0) {
            fuelTime -= deltaTime;
            if (currentRecipe != null && canSmelt(currentRecipe)) {
                isSmelting = true;
                maxCookTime = currentRecipe.getCookTime();
                cookTime += deltaTime;
                if (cookTime >= maxCookTime) {
                    smeltItem(currentRecipe);
                }
            } else {
                isSmelting = false;
                cookTime = 0;
            }
        } else {
            isSmelting = false;
            cookTime = 0;
        }

        if (isSmelting) {
            animationTimer += deltaTime;
            if (animationTimer >= FRAME_DURATION) {
                animationTimer = 0;
                currentFrame = (currentFrame + 1) % 4;
            }
        } else {
            currentFrame = 0;
        }

        if (isSmelting && !wasSmelting) {
            game.getLightManager().addLightSource(this.row, this.col, (byte)13);
        } else if (!isSmelting && wasSmelting) {
            game.getLightManager().removeLightSource(this.row, this.col);
        }
    }

    private void consumeFuel() {
        if (!fuelSlot.isEmpty() && isFuel(fuelSlot.getItem())) {
            Item fuelItem = fuelSlot.getItem();
            fuelTime += FUEL_BURN_TIMES.getOrDefault(fuelItem.getItemId(), 0.0);
            fuelSlot.removeQuantity(1);
        }
    }

    private boolean canSmelt(FurnaceRecipe recipe) {
        if (inputSlot.isEmpty() || recipe == null) return false;
        Item outputItem = recipe.getOutputItem();
        return outputSlot.isEmpty() || (outputSlot.getItem().equals(outputItem) && outputSlot.getQuantity() + recipe.getOutputQuantity() <= outputItem.getMaxStack());
    }

    private void smeltItem(FurnaceRecipe recipe) {
        if (canSmelt(recipe)) {
            inputSlot.removeQuantity(1);
            outputSlot.addItem(recipe.getOutputItem(), recipe.getOutputQuantity());
            cookTime = 0;
        }
    }

    public static boolean isFuel(Item item) {
        return item != null && FUEL_BURN_TIMES.containsKey(item.getItemId());
    }

    public float getCookProgress() {
        if (!isSmelting || maxCookTime <= 0) return 0f;
        return (float) (cookTime / maxCookTime);
    }

    @Override
    public void onInteract(Game game) { game.getUiManager().openFurnaceUI(this); }

    @Override
    public TileEntitySaveData getSaveData() {
        TileEntitySaveData saveData = new TileEntitySaveData();
        saveData.type = "FURNACE";
        saveData.row = this.row;
        saveData.col = this.col;
        saveData.customData.put("fuelTime", this.fuelTime);
        saveData.customData.put("cookTime", this.cookTime);
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