// In ItemRegistry.java

package org.isogame.item;

import org.isogame.render.Renderer;
import org.isogame.render.Texture;
import java.util.HashMap;
import java.util.Map;

public class ItemRegistry {

    private static final Map<String, Item> items = new HashMap<>();

    // Placeholder colors
    private static final float[] DIRT_COLOR = {0.6f, 0.4f, 0.2f, 1.0f};
    private static final float[] STONE_COLOR = {0.5f, 0.5f, 0.5f, 1.0f};
    private static final float[] SAND_COLOR = {0.9f, 0.8f, 0.6f, 1.0f};
    private static final float[] WOOD_PLACEHOLDER_COLOR = {0.5f, 0.35f, 0.2f, 1.0f};
    private static final float[] STICK_PLACEHOLDER_COLOR = {0.7f, 0.5f, 0.3f, 1.0f};
    private static final float[] AXE_PLACEHOLDER_COLOR = {0.6f, 0.65f, 0.7f, 1.0f};
    private static final float[] LOOSE_ROCK_PLACEHOLDER_COLOR = {0.4f, 0.4f, 0.4f, 1.0f};

    // --- Item Definitions using Pixel Coordinates ---
    public static final Item DIRT = registerItem(new Item("dirt", "Dirt", "A block of common soil.", Item.ItemType.RESOURCE, 64, DIRT_COLOR));
    public static final Item STONE = registerItem(new Item("stone", "Stone", "A hard piece of rock.", Item.ItemType.RESOURCE, 64, STONE_COLOR));
    public static final Item SAND = registerItem(new Item("sand", "Sand", "Fine grains of sand.", Item.ItemType.RESOURCE, 64, SAND_COLOR));

    public static final Item WOOD = registerItem(new Item("wood", "Wood Log", "A sturdy log of wood.", Item.ItemType.RESOURCE, 64, WOOD_PLACEHOLDER_COLOR,
            true, "treeTexture",
            /* YOUR_WOOD_ICON_X_PIXEL_COORD */ 90.0f,  // <--- SET THIS to your wood icon's X
            /* YOUR_WOOD_ICON_Y_PIXEL_COORD */ 1674.0f,  // <--- SET THIS to your wood icon's Y
            /* YOUR_WOOD_ICON_WIDTH_PIXEL */ 64.0f,   // <--- SET THIS to your wood icon's width
            /* YOUR_WOOD_ICON_HEIGHT_PIXEL */ 32.0f)); // <--- SET THIS to your wood icon's height

    public static final Item STICK = registerItem(new Item("stick", "Stick", "A basic crafting material.", Item.ItemType.RESOURCE, 64, STICK_PLACEHOLDER_COLOR,
            true, "treeTexture", 24, 1600, 64, 64)); // hasIcon, atlasName, px, py, pw, ph

    public static final Item LOOSE_ROCK = registerItem(new Item("loose_rock", "Loose Rock", "A small rock, easily picked up.", Item.ItemType.RESOURCE, 64, LOOSE_ROCK_PLACEHOLDER_COLOR,
            true, "treeTexture", 95, 1526, 69, 69));

    public static final Item CRUDE_AXE = registerItem(new ToolItem("crude_axe", "Crude Axe", "A simple axe.",
            true, "treeTexture",
            35f,   // New X coordinate
            1668f, // New Y coordinate
            49f,   // New Width
            53f,   // New Height
            ToolItem.ToolType.AXE));



    /**
     * Initializes the final UV coordinates for all textured items.
     * This MUST be called after the Renderer has loaded its textures.
     * @param textureMap A map of atlas names to Texture objects from the Renderer.
     */
    public static void initializeItemUVs(Map<String, Texture> textureMap) {
        System.out.println("ItemRegistry: Initializing item UV coordinates...");
        for (Item item : items.values()) {
            if (item.hasIconTexture()) {
                Texture atlas = textureMap.get(item.getAtlasName());
                if (atlas != null) {
                    item.calculateUVs(atlas);
                } else {
                    System.err.println("WARNING: Item '" + item.getItemId() + "' needs atlas '" + item.getAtlasName() + "', which was not found in the texture map provided by the Renderer.");
                }
            }
        }
        System.out.println("ItemRegistry: UV coordinate initialization complete.");
    }

    private static Item registerItem(Item item) {
        items.put(item.getItemId().toLowerCase(), item);
        return item;
    }

    public static Item getItem(String itemId) {
        return items.get(itemId.toLowerCase());
    }
}