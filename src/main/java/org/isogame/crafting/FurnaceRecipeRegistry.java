package org.isogame.crafting;

import com.google.gson.Gson;
import org.isogame.item.Item;
import org.isogame.item.ItemRegistry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class FurnaceRecipeRegistry {

    private static final Map<String, FurnaceRecipe> recipes = new HashMap<>();
    private static final String[] FURNACE_RECIPE_FILES = {
            "sand_to_glass_recipe.json",
            "rock_to_stone_recipe.json"
            // Add new furnace recipe files here
    };

    public static void loadRecipes() {
        Gson gson = new Gson();
        for (String fileName : FURNACE_RECIPE_FILES) {
            try (InputStream stream = FurnaceRecipeRegistry.class.getClassLoader().getResourceAsStream("data/recipes/furnace/" + fileName)) {
                if (stream == null) {
                    System.err.println("Could not find furnace recipe file: " + fileName);
                    continue;
                }
                RecipeData data = gson.fromJson(new InputStreamReader(stream), RecipeData.class);
                Item inputItem = ItemRegistry.getItem(data.getIngredients().get(0).getItemId());
                Item outputItem = ItemRegistry.getItem(data.getOutputItemId());

                if (inputItem != null && outputItem != null) {
                    double cookTime = (data.getCookTime() > 0) ? data.getCookTime() : 10.0;
                    FurnaceRecipe recipe = new FurnaceRecipe(inputItem, outputItem, data.getOutputQuantity(), cookTime);
                    recipes.put(inputItem.getItemId(), recipe);
                }
            } catch (Exception e) {
                System.err.println("Error loading furnace recipe: " + fileName);
                e.printStackTrace();
            }
        }
        System.out.println("Loaded " + recipes.size() + " furnace recipes.");
    }

    public static FurnaceRecipe getRecipeForInput(Item input) {
        if (input == null) return null;
        return recipes.get(input.getItemId());
    }
}