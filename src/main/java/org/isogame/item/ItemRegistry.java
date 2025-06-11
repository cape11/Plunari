package org.isogame.item;

import com.google.gson.Gson;
import org.isogame.gamedata.ItemDefinition;
import org.isogame.render.Texture;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ItemRegistry {

    private static final Map<String, Item> itemMap = new HashMap<>();
    private static final Gson gson = new Gson();

    private static final List<String> ITEM_DEFINITION_FILES = Arrays.asList(
            "crude_axe.json",
            "dirt.json",
            "loose_rock.json",
            "sand.json",
            "stick.json",
            "stone.json",
            "wood.json"
    );

    /**
     * Loads all item definitions directly from the project's file system.
     * This is a robust method for local development to bypass classpath issues.
     */
    public static void loadItems() {
        itemMap.clear();
        // Path within the classpath, relative to the root. DO NOT start with a slash.
        String resourceFolderPath = "data/items/";
        System.out.println("ItemRegistry: Loading items from classpath folder: " + resourceFolderPath);

        for (String fileName : ITEM_DEFINITION_FILES) {
            String fullPath = resourceFolderPath + fileName;

            // Use the ClassLoader to get the resource stream, matching the working code in Font.java
            try (InputStream is = ItemRegistry.class.getClassLoader().getResourceAsStream(fullPath)) {
                if (is == null) {
                    System.err.println("CRITICAL: Cannot find resource file on classpath: " + fullPath);
                    continue; // Skip to the next file
                }

                try (Reader reader = new InputStreamReader(is)) {
                    ItemDefinition data = gson.fromJson(reader, ItemDefinition.class);
                    if (data != null && data.id != null) {
                        Item newItem = createItemFromData(data);
                        if (newItem != null) {
                            registerItem(newItem);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("ERROR: Failed to load or parse item definition from: " + fullPath);
                e.printStackTrace();
            }
        }
        System.out.println("ItemRegistry: Loaded " + itemMap.size() + " item definitions.");
    }

    private static Item createItemFromData(ItemDefinition data) {
        Item.ItemType itemType = Item.ItemType.valueOf(data.type);
        Item newItem;

        if (itemType == Item.ItemType.TOOL && data.toolType != null) {
            ToolItem.ToolType toolType = ToolItem.ToolType.valueOf(data.toolType);
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
                newItem.useStyle = UseStyle.valueOf(data.useStyle);
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
            if (item.hasIconTexture()) {
                Texture atlas = textureMap.get(item.getAtlasName());
                if (atlas != null) {
                    item.calculateUVs(atlas);
                } else {
                    if(item.getAtlasName() != null && !item.getAtlasName().isEmpty())
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
        Item item = itemMap.get(itemId.toLowerCase());
        if (item == null) {
            System.err.println("WARNING: Tried to get unknown item with ID: " + itemId);
        }
        return item;
    }
}
