package org.isogame.crafting;

import org.isogame.item.Item;
import java.util.Map;

/**
 * A data class that represents a single crafting recipe.
 * It holds the output item and a map of required ingredients.
 */
public class CraftingRecipe {
    private final Item outputItem;
    private final int outputQuantity;
    private final Map<Item, Integer> requiredItems; // Key: Item, Value: Quantity required

    public CraftingRecipe(Item outputItem, int outputQuantity, Map<Item, Integer> requiredItems) {
        this.outputItem = outputItem;
        this.outputQuantity = outputQuantity;
        this.requiredItems = requiredItems;
    }

    // --- Getters ---

    public Item getOutputItem() {
        return outputItem;
    }

    public int getOutputQuantity() {
        return outputQuantity;
    }

    public Map<Item, Integer> getRequiredItems() {
        return requiredItems;
    }
}