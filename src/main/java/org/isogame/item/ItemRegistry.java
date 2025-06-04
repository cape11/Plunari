package org.isogame.item;

import java.util.HashMap;
import java.util.Map;

public class ItemRegistry {

    private static final Map<String, Item> items = new HashMap<>();

    // --- Define placeholder colors for new items ---
    private static final float[] DIRT_COLOR = {0.6f, 0.4f, 0.2f, 1.0f};   // Brownish
    private static final float[] STONE_COLOR = {0.5f, 0.5f, 0.5f, 1.0f};  // Gray
    private static final float[] SAND_COLOR = {0.9f, 0.8f, 0.6f, 1.0f};   // Light Yellowish

    // New colors for new items
    private static final float[] WOOD_PLACEHOLDER_COLOR = {0.5f, 0.35f, 0.2f, 1.0f};  // Brown for Wood
    private static final float[] STICK_PLACEHOLDER_COLOR = {0.7f, 0.5f, 0.3f, 1.0f};   // Lighter Brown for Stick
    private static final float[] AXE_PLACEHOLDER_COLOR = {0.6f, 0.65f, 0.7f, 1.0f}; // Greyish for Axe

    // --- Existing Item Definitions ---
    public static final Item DIRT = registerItem(new Item("dirt", "Dirt", "A block of common soil.", Item.ItemType.RESOURCE, 64, DIRT_COLOR));
    public static final Item STONE = registerItem(new Item("stone", "Stone", "A hard piece of rock.", Item.ItemType.RESOURCE, 64, STONE_COLOR));
    public static final Item SAND = registerItem(new Item("sand", "Sand", "Fine grains of sand.", Item.ItemType.RESOURCE, 64, SAND_COLOR));
    private static final float[] LOOSE_ROCK_PLACEHOLDER_COLOR = {0.4f, 0.4f, 0.4f, 1.0f}; // Darker Gray for Loose Rock

    // --- ADD THESE NEW ITEM DEFINITIONS ---
    public static final Item WOOD = registerItem(new Item(
            "wood",
            "Wood Log",
            "A sturdy log of wood, useful for crafting.",
            Item.ItemType.RESOURCE, // Wood is a resource
            64,                     // Max stack size
            WOOD_PLACEHOLDER_COLOR
    ));

    public static final Item STICK = registerItem(new Item(
            "stick",
            "Stick",
            "A simple wooden stick, a basic crafting material.",
            Item.ItemType.RESOURCE,
            64,
            STICK_PLACEHOLDER_COLOR,
            // hasIconTexture, iconU0, iconV0, iconU1, iconV1
            true, 0.0234375f, 0.390625f, 0.0859375f, 0.40625f // <-- UPDATED UVs for STICK
    ));

    public static final Item CRUDE_AXE = registerItem(new Item(
            "crude_axe",
            "Crude Axe",
            "A basic axe, good for chopping down trees.",
            Item.ItemType.TOOL,     // Axe is a tool
            1,                      // Tools usually don't stack
            AXE_PLACEHOLDER_COLOR
            // If you later add icon UVs to Item constructor, you'd add them here too.
    ));


    // --- NEW ITEM DEFINITION FOR LOOSE ROCK ---
    public static final Item LOOSE_ROCK = registerItem(new Item(
            "loose_rock",
            "Loose Rock",
            "A small rock, easily picked up. Might be useful.",
            Item.ItemType.RESOURCE,
            64,
            LOOSE_ROCK_PLACEHOLDER_COLOR,
            // hasIconTexture, iconU0, iconV0, iconU1, iconV1
            true, 0.029296875f, 0.3662109375f, 0.060546875f, 0.3740234375f
    ));
// --- END OF NEW ITEM DEFINITIONS ---

    /// //////////////////////////////

    private static Item registerItem(Item item) {
        items.put(item.getItemId().toLowerCase(), item); // Store with lowercase ID for consistent retrieval
        return item;
    }

    public static Item getItem(String itemId) {
        return items.get(itemId.toLowerCase()); // Retrieve with lowercase ID
    }

    static {
        // This block runs when the class is loaded, ensuring items are registered.
        // The static final fields above already call registerItem.
        // This print can be helpful for debugging.
        if (items.isEmpty()) {
            //This might happen if static final fields are not initialized before this static block.
            //However, standard Java execution order should handle it.
            System.out.println("ItemRegistry static block: Items map is unexpectedly empty before explicit print.");
        }
        System.out.println("ItemRegistry initialized. Number of items registered: " + items.size());
        // items.forEach((id, item) -> System.out.println("Registered: " + id + " -> " + item.getDisplayName())); // For detailed debug
    }
}