package org.isogame.item;

import java.util.HashMap;
import java.util.Map;

public class ItemRegistry {

    private static final Map<String, Item> items = new HashMap<>();

    // Define common items
    public static final Item DIRT = registerItem(new Item("dirt", "Dirt", "A block of common soil.", Item.ItemType.RESOURCE, 64));
    public static final Item STONE = registerItem(new Item("stone", "Stone", "A hard piece of rock.", Item.ItemType.RESOURCE, 64));
    public static final Item SAND = registerItem(new Item("sand", "Sand", "Fine grains of sand.", Item.ItemType.RESOURCE, 64));
    // Add more items here as needed
    // public static final Item WOOD_PICKAXE = registerItem(new Item("wood_pickaxe", "Wooden Pickaxe", "A basic pickaxe made of wood.", Item.ItemType.TOOL, 1));

    private static Item registerItem(Item item) {
        items.put(item.getItemId(), item);
        return item;
    }

    public static Item getItem(String itemId) {
        return items.get(itemId);
    }

    // Initialize (load) all items - this is done by the static final declarations.
    // Could add a static block for more complex initializations if needed.
    static {
        System.out.println("ItemRegistry initialized with " + items.size() + " items.");
    }
}