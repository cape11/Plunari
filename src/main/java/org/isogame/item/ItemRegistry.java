// In file: org/isogame/item/ItemRegistry.java
package org.isogame.item;

import java.util.HashMap;
import java.util.Map;

public class ItemRegistry {
    private static final Map<String, Item> items = new HashMap<>();

    // --- Placeholder Colors ---
    private static final float[] DIRT_COLOR = {0.6f, 0.4f, 0.2f, 1.0f};
    private static final float[] STONE_COLOR = {0.5f, 0.5f, 0.5f, 1.0f};
    private static final float[] SAND_COLOR = {0.9f, 0.8f, 0.6f, 1.0f};
    private static final float[] WOOD_PLACEHOLDER_COLOR = {0.5f, 0.35f, 0.2f, 1.0f};
    private static final float[] STICK_PLACEHOLDER_COLOR = {0.7f, 0.5f, 0.3f, 1.0f};
    private static final float[] AXE_PLACEHOLDER_COLOR = {0.6f, 0.65f, 0.7f, 1.0f};
    private static final float[] LOOSE_ROCK_PLACEHOLDER_COLOR = {0.4f, 0.4f, 0.4f, 1.0f};

    // --- IMPORTANT: Set these to the actual dimensions of your fruit-trees.png file ---
    private static final float ATLAS_WIDTH = 1024f;
    private static final float ATLAS_HEIGHT = 2048f;

    // --- Item Definitions ---
    public static final Item DIRT = registerItem(new Item("dirt", "Dirt", "A block of common soil.", Item.ItemType.RESOURCE, 64, DIRT_COLOR));
    public static final Item STONE = registerItem(new Item("stone", "Stone", "A hard piece of rock.", Item.ItemType.RESOURCE, 64, STONE_COLOR));
    public static final Item SAND = registerItem(new Item("sand", "Sand", "Fine grains of sand.", Item.ItemType.RESOURCE, 64, SAND_COLOR));
    public static final Item WOOD = registerItem(new Item("wood", "Wood Log", "A sturdy log of wood.", Item.ItemType.RESOURCE, 64, WOOD_PLACEHOLDER_COLOR));

    public static final Item STICK = registerItem(new Item("stick", "Stick", "A basic crafting material.", Item.ItemType.RESOURCE, 64, STICK_PLACEHOLDER_COLOR,
            true, 24f/ATLAS_WIDTH, 1600f/ATLAS_HEIGHT, 88f/ATLAS_WIDTH, 1616f/ATLAS_HEIGHT));

    // This uses the coordinates from your GIMP screenshot (X:80, Y:1735, W:60, H:85)
    public static final Item CRUDE_AXE = registerItem(new Item("crude_axe", "Crude Axe", "Good for chopping trees.", Item.ItemType.TOOL, 1, AXE_PLACEHOLDER_COLOR,
            true,
            80f / ATLAS_WIDTH,           // X
            1735f / ATLAS_HEIGHT,         // Y
            (80f + 60f) / ATLAS_WIDTH,   // X + Width
            (1735f + 85f) / ATLAS_HEIGHT // Y + Height
    ));

    public static final Item LOOSE_ROCK = registerItem(new Item("loose_rock", "Loose Rock", "A small rock.", Item.ItemType.RESOURCE, 64, LOOSE_ROCK_PLACEHOLDER_COLOR,
            true, 95f/ATLAS_WIDTH, 1526f/ATLAS_HEIGHT, 164f/ATLAS_WIDTH, 1595f/ATLAS_HEIGHT));

    // --- Registry Methods ---
    private static Item registerItem(Item item) {
        items.put(item.getItemId().toLowerCase(), item);
        return item;
    }

    public static Item getItem(String itemId) {
        return items.get(itemId.toLowerCase());
    }
}