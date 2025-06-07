package org.isogame.crafting;

import org.isogame.item.ItemRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RecipeRegistry {
    private static final List<CraftingRecipe> recipes = new ArrayList<>();

    // Define the recipe for the Crude Axe
    public static final CraftingRecipe CRUDE_AXE_RECIPE = registerRecipe(
            new CraftingRecipe(
                    ItemRegistry.CRUDE_AXE, // The output item
                    1,
                    Map.of(
                            ItemRegistry.STICK, 2,      // Requires 2 Sticks
                            ItemRegistry.LOOSE_ROCK, 3  // Requires 3 Loose Rocks
                    )
            )
    );

    // You can add more recipes here later just by defining a new static final field:
    // public static final CraftingRecipe WOODEN_PICKAXE_RECIPE = registerRecipe(...);


    /**
     * Private helper method to add a recipe to the list and return it.
     * This allows for easy definition in static final fields.
     */
    private static CraftingRecipe registerRecipe(CraftingRecipe recipe) {
        recipes.add(recipe);
        return recipe;
    }


    public static List<CraftingRecipe> getAllRecipes() {
        return recipes;
    }
}