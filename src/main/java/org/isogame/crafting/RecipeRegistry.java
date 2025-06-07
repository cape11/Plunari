// In RecipeRegistry.java

package org.isogame.crafting;

import com.google.gson.Gson;
import org.isogame.item.Item;
import org.isogame.item.ItemRegistry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecipeRegistry {

    private static final List<CraftingRecipe> recipes = new ArrayList<>();
    private static final Gson gson = new Gson();

    // The hard-coded recipe is now gone.

    /**
     * Scans the data/recipes resource folder, loads all .json files,
     * and populates the recipes list. This must be called at startup.
     */
    // In RecipeRegistry.java

    public static void loadRecipes() {
        recipes.clear();
        System.out.println("RecipeRegistry: Initializing recipe loading...");
        String resourcePath = "/data/recipes";

        try {
            URI uri = RecipeRegistry.class.getResource(resourcePath).toURI();
            Path recipePath = Paths.get(uri);
            System.out.println("  -> Reading recipes from path: " + recipePath);

            try (Stream<Path> paths = Files.walk(recipePath)) {
                List<Path> recipeFiles = paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".json"))
                        .collect(Collectors.toList());

                if (recipeFiles.isEmpty()) {
                    System.err.println("WARNING: No .json recipe files found in " + recipePath);
                    return;
                }

                for (Path file : recipeFiles) {
                    System.out.println("  -> Attempting to load file: " + file.getFileName());
                    try (InputStream is = Files.newInputStream(file);
                         Reader reader = new InputStreamReader(is)) {

                        RecipeData data = gson.fromJson(reader, RecipeData.class);
                        CraftingRecipe recipe = convertDataToRecipe(data);
                        if (recipe != null) {
                            recipes.add(recipe);
                            System.out.println("    - SUCCESS: Loaded recipe for: " + recipe.getOutputItem().getDisplayName());
                        }
                    } catch (Exception e) {
                        System.err.println("    - ERROR: Failed to load or parse recipe file: " + file.getFileName());
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("CRITICAL: Could not access recipes directory at resource path: '" + resourcePath + "'. Ensure 'src/main/resources" + resourcePath + "' exists.");
            e.printStackTrace();
        }
        System.out.println("RecipeRegistry: " + recipes.size() + " total recipes loaded.");
    }

    /**
     * Converts the raw data from JSON into a valid CraftingRecipe object
     * by resolving item IDs into actual Item objects.
     */
    private static CraftingRecipe convertDataToRecipe(RecipeData data) {
        if (data == null) return null;

        Item outputItem = ItemRegistry.getItem(data.outputItemId);
        if (outputItem == null) {
            System.err.println("Recipe Error: Output item ID '" + data.outputItemId + "' not found in ItemRegistry.");
            return null;
        }

        Map<Item, Integer> requiredItems = new HashMap<>();
        for (IngredientData ingData : data.ingredients) {
            Item requiredItem = ItemRegistry.getItem(ingData.itemId);
            if (requiredItem == null) {
                System.err.println("Recipe Error: Ingredient item ID '" + ingData.itemId + "' not found for recipe '" + data.outputItemId + "'.");
                return null; // Invalidate the whole recipe if one ingredient is missing
            }
            requiredItems.put(requiredItem, ingData.quantity);
        }

        return new CraftingRecipe(outputItem, data.outputQuantity, requiredItems);
    }


    public static List<CraftingRecipe> getAllRecipes() {
        return recipes;
    }
}