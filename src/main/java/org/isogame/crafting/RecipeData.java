package org.isogame.crafting;

import java.util.List;

// A simple DTO for Gson to parse a recipe from a JSON file.
public class RecipeData {
    String outputItemId;
    int outputQuantity;
    List<IngredientData> ingredients;
}