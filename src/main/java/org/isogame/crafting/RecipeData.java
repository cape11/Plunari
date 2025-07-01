package org.isogame.crafting;

import java.util.List;

// A simple DTO for Gson to parse a recipe from a JSON file.
public class RecipeData {
    String outputItemId;
    int outputQuantity;
    List<IngredientData> ingredients;
    Double cookTime;


    // In RecipeData.java
    public String getOutputItemId() {
        return outputItemId;
    }

    public int getOutputQuantity() {
        return outputQuantity;
    }

    public java.util.List<IngredientData> getIngredients() {
        return ingredients;
    }

    public double getCookTime() {
        // This might not exist in your JSON, so we check for null
        // and return 0 if it's not there.
        return (cookTime != null) ? cookTime : 0.0;
    }
}

