package org.isogame.test;

import org.isogame.item.ItemRegistry;
import org.isogame.crafting.RecipeRegistry;
import org.isogame.item.Item;

/**
 * A simple test class to verify that the JSON files are properly loaded.
 */
public class ResourceLoadingTest {

    public static void main(String[] args) {
        System.out.println("Starting Resource Loading Test...");

        // Initialize the ItemRegistry
        System.out.println("\nTesting ItemRegistry loading...");
        ItemRegistry.loadItems();

        // Verify that some items were loaded
        Item woodItem = ItemRegistry.getItem("wood");
        Item stoneItem = ItemRegistry.getItem("stone");
        Item dirtItem = ItemRegistry.getItem("dirt");

        System.out.println("\nVerifying loaded items:");
        System.out.println("Wood item: " + (woodItem != null ? "LOADED - " + woodItem.getDisplayName() : "NOT LOADED"));
        System.out.println("Stone item: " + (stoneItem != null ? "LOADED - " + stoneItem.getDisplayName() : "NOT LOADED"));
        System.out.println("Dirt item: " + (dirtItem != null ? "LOADED - " + dirtItem.getDisplayName() : "NOT LOADED"));

        // Check if items were loaded
        boolean itemsLoaded = woodItem != null && stoneItem != null && dirtItem != null;
        System.out.println("All items loaded successfully: " + (itemsLoaded ? "YES" : "NO"));

        // Initialize the RecipeRegistry
        System.out.println("\nTesting RecipeRegistry loading...");
        RecipeRegistry.loadRecipes();

        // Verify that recipes were loaded
        System.out.println("\nVerifying loaded recipes:");
        int recipeCount = RecipeRegistry.getAllRecipes().size();
        System.out.println("Total recipes loaded: " + recipeCount);

        if (recipeCount > 0) {
            System.out.println("First recipe output item: " + 
                RecipeRegistry.getAllRecipes().get(0).getOutputItem().getDisplayName());
        }

        // Check if recipes were loaded
        boolean recipesLoaded = recipeCount > 0;
        System.out.println("Recipes loaded successfully: " + (recipesLoaded ? "YES" : "NO"));

        System.out.println("\nResource Loading Test completed.");
        System.out.println("TEST RESULT: " + (itemsLoaded && recipesLoaded ? "SUCCESS" : "FAILURE"));
    }
}
