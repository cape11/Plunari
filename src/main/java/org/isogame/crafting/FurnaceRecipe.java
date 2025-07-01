package org.isogame.crafting;

import org.isogame.item.Item;

public class FurnaceRecipe {
    private final Item inputItem;
    private final Item outputItem;
    private final int outputQuantity;
    private final double cookTime;

    public FurnaceRecipe(Item inputItem, Item outputItem, int outputQuantity, double cookTime) {
        this.inputItem = inputItem;
        this.outputItem = outputItem;
        this.outputQuantity = outputQuantity;
        this.cookTime = cookTime;
    }

    public Item getInputItem() {
        return inputItem;
    }

    public Item getOutputItem() {
        return outputItem;
    }

    public int getOutputQuantity() {
        return outputQuantity;
    }

    public double getCookTime() {
        return cookTime;
    }
}