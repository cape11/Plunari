package org.isogame.item;

import com.google.gson.Gson;
import org.isogame.gamedata.ItemDefinition;
import org.isogame.render.Texture;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemRegistry {

    private static final Map<String, Item> itemMap = new HashMap<>();
    private static final Gson gson = new Gson();

    private static final List<String> ITEM_FILES = Arrays.asList(
            "crude_axe.json",
            "dirt.json",
            "loose_rock.json",
            "sand.json",
            "stick.json",
            "stone.json",
            "wood.json",
            "slime_gel.json",
            "torch.json",         // <-- ADD THIS LINE
            "wooden_sword.json",
            "wood_plank.json",
            "stone_brick.json"// <-- AND THIS LINE
    );

    /**
     * Loads each item definition file individually from the classpath.
     * The files are now located inside the main package structure for reliability.
     */
    public static void loadItems() {
        itemMap.clear();
        System.out.println("ItemRegistry: Initializing item loading (New Path)...");
        // Path to item JSON files in resources directory
        String resourceFolder = "/data/items/";

        for (String fileName : ITEM_FILES) {
            String fullPath = resourceFolder + fileName;
            System.out.println("  -> Attempting to load: " + fullPath);
            try (InputStream is = ItemRegistry.class.getResourceAsStream(fullPath)) {
                if (is == null) {
                    System.err.println("    - CRITICAL FAILURE: Cannot find resource file on classpath: " + fullPath);
                    continue;
                }

                try (Reader reader = new InputStreamReader(is)) {
                    ItemDefinition data = gson.fromJson(reader, ItemDefinition.class);
                    Item newItem = createItemFromData(data);
                    if (newItem != null) {
                        registerItem(newItem);
                        System.out.println("    - SUCCESS: Loaded item: " + newItem.getDisplayName());
                    } else {
                        System.err.println("    - FAILED: Could not create item from " + fileName);
                    }
                }
            } catch (Exception e) {
                System.err.println("    - ERROR: Exception while processing " + fullPath);
                e.printStackTrace();
            }
        }
        System.out.println("ItemRegistry: " + itemMap.size() + " total item definitions loaded.");
    }

    private static Item createItemFromData(ItemDefinition data) {
        if (data == null || data.id == null || data.type == null) {
            System.err.println("Skipping item with null data, id, or type.");
            return null;
        }
        Item.ItemType itemType;
        try {
            itemType = Item.ItemType.valueOf(data.type.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("ERROR: Invalid item type '" + data.type + "' in JSON file.");
            return null;
        }

        Item newItem;
        if (itemType == Item.ItemType.TOOL && data.toolType != null) {
            ToolItem.ToolType toolType = ToolItem.ToolType.valueOf(data.toolType.toUpperCase());
            newItem = new ToolItem(
                    data.id, data.displayName, data.description,
                    true, data.texture.atlas,
                    data.texture.x, data.texture.y, data.texture.w, data.texture.h,
                    toolType
            );
        } else {
            newItem = new Item(
                    data.id, data.displayName, data.description, itemType, 64, null,
                    data.texture != null, data.texture != null ? data.texture.atlas : "",
                    data.texture != null ? data.texture.x : 0, data.texture != null ? data.texture.y : 0,
                    data.texture != null ? data.texture.w : 0, data.texture != null ? data.texture.h : 0
            );
        }

        if (data.stats != null) {
            if (data.useStyle != null) {
                newItem.useStyle = UseStyle.valueOf(data.useStyle.toUpperCase());
            }
            newItem.damage = data.stats.damage;
            newItem.useTime = data.stats.useTime;
            newItem.useAnimation = data.stats.useAnimation;
            newItem.knockback = data.stats.knockback;
        }
        return newItem;
    }

    public static void initializeItemUVs(Map<String, Texture> textureMap) {
        System.out.println("ItemRegistry: Initializing item UV coordinates...");
        for (Item item : itemMap.values()) {
            if (item.hasIconTexture() && item.getAtlasName() != null && !item.getAtlasName().isEmpty()) {
                Texture atlas = textureMap.get(item.getAtlasName());
                if (atlas != null) {
                    item.calculateUVs(atlas);
                } else {
                    System.err.println("WARNING: Item '" + item.getItemId() + "' needs atlas '" + item.getAtlasName() + "', which was not found.");
                }
            }
        }
        System.out.println("ItemRegistry: UV coordinate initialization complete.");
    }

    private static void registerItem(Item item) {
        itemMap.put(item.getItemId().toLowerCase(), item);
    }

    public static Item getItem(String itemId) {
        if (itemId == null) {
            System.err.println("WARNING: Tried to get an item with a null ID.");
            return null;
        }
        Item item = itemMap.get(itemId.toLowerCase());
        if (item == null) {
            System.err.println("WARNING: Tried to get unknown item with ID: " + itemId);
        }
        return item;
    }
}
