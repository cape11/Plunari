package org.isogame.crafting;

import com.google.gson.Gson;
import org.isogame.item.Item;
import org.isogame.item.ItemRegistry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeRegistry {

    private static final List<CraftingRecipe> recipes = new ArrayList<>();
    private static final Gson gson = new Gson();

    private static final List<String> RECIPE_FILES = Arrays.asList(
            "rude_axe_recipe.json",
            "wooden_sword_recipe.json",
            "wood_plank_recipe.json", // <-- ADD THIS LINE
            "stone_brick_recipe.json", // <-- ADD THIS
            "torch_recipe.json" // <-- ADDED
    );


    /**
     * Loads each recipe definition file individually from the classpath.
     */
    public static void loadRecipes() {
        recipes.clear();
        System.out.println("RecipeRegistry: Initializing recipe loading (New Path)...");
        // Path to recipe JSON files in resources directory
        String resourceFolder = "/data/recipes/";

        for (String fileName : RECIPE_FILES) {
            String fullPath = resourceFolder + fileName;
            System.out.println("  -> Attempting to load: " + fullPath);
            try (InputStream is = RecipeRegistry.class.getResourceAsStream(fullPath)) {
                if (is == null) {
                    System.err.println("    - CRITICAL FAILURE: Cannot find resource file on classpath: " + fullPath);
                    continue;
                }

                try (Reader reader = new InputStreamReader(is)) {
                    RecipeData data = gson.fromJson(reader, RecipeData.class);
                    CraftingRecipe recipe = convertDataToRecipe(data);
                    if (recipe != null) {
                        recipes.add(recipe);
                        System.out.println("    - SUCCESS: Loaded recipe for: " + recipe.getOutputItem().getDisplayName());
                    } else {
                        System.err.println("    - FAILED: Could not create recipe from " + fileName);
                    }
                }
            } catch (Exception e) {
                System.err.println("    - ERROR: Exception while processing " + fullPath);
                e.printStackTrace();
            }
        }
        System.out.println("RecipeRegistry: " + recipes.size() + " total recipes loaded.");
    }

    private static CraftingRecipe convertDataToRecipe(RecipeData data) {
        if (data == null || data.outputItemId == null) return null;

        Item outputItem = ItemRegistry.getItem(data.outputItemId);
        if (outputItem == null) {
            System.err.println("Recipe Error: Output item ID '" + data.outputItemId + "' not found. Skipping recipe.");
            return null;
        }

        Map<Item, Integer> requiredItems = new HashMap<>();
        if (data.ingredients != null) {
            for (IngredientData ingData : data.ingredients) {
                if (ingData.itemId == null) continue;
                Item requiredItem = ItemRegistry.getItem(ingData.itemId);
                if (requiredItem == null) {
                    System.err.println("Recipe Error: Ingredient '" + ingData.itemId + "' not found for recipe '" + data.outputItemId + "'. Skipping recipe.");
                    return null;
                }
                requiredItems.put(requiredItem, ingData.quantity);
            }
        }
        return new CraftingRecipe(outputItem, data.outputQuantity, requiredItems);
    }

    public static List<CraftingRecipe> getAllRecipes() {
        return recipes;
    }
}
