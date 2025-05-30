package org.isogame.item;

import java.util.HashMap;
import java.util.Map;

public class ItemRegistry {

    private static final Map<String, Item> items = new HashMap<>();

    // Define placeholder colors
    private static final float[] DIRT_COLOR = {0.6f, 0.4f, 0.2f, 1.0f};   // Brownish
    private static final float[] STONE_COLOR = {0.5f, 0.5f, 0.5f, 1.0f};  // Gray
    private static final float[] SAND_COLOR = {0.9f, 0.8f, 0.6f, 1.0f};   // Light Yellowish

    // These calls expect the 6-argument constructor from Item.java
    public static final Item DIRT = registerItem(new Item("dirt", "Dirt", "A block of common soil.", Item.ItemType.RESOURCE, 64, DIRT_COLOR));
    public static final Item STONE = registerItem(new Item("stone", "Stone", "A hard piece of rock.", Item.ItemType.RESOURCE, 64, STONE_COLOR));
    public static final Item SAND = registerItem(new Item("sand", "Sand", "Fine grains of sand.", Item.ItemType.RESOURCE, 64, SAND_COLOR));

    private static Item registerItem(Item item) {
        items.put(item.getItemId(), item);
        return item;
    }

    public static Item getItem(String itemId) {
        return items.get(itemId);
    }

    static {
        System.out.println("ItemRegistry initialized with " + items.size() + " items.");
    }
}