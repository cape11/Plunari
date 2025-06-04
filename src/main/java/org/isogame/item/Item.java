package org.isogame.item;

import java.util.Objects;
import org.isogame.item.Item; // Import Item
import static org.lwjgl.glfw.GLFW.*;



public class Item {
    private String itemId;          // Unique identifier, e.g., "dirt_block", "iron_pickaxe"
    private String displayName;     // How it appears in UI, e.g., "Dirt Block", "Iron Pickaxe"
    private String description;
    private int maxStackSize;       // How many can fit in one stack (e.g., 1 for tools, 64 for resources)
    private ItemType type;          // Enum for item categories
    private float[] placeholderColor; // For UI placeholder
    // --- ADD THESE NEW FIELDS FOR ICON UVs ---
    private float iconU0, iconV0, iconU1, iconV1; // Texture coordinates for the icon
    private boolean hasIconTexture; // Flag to indicate if this item uses a texture icon
    public enum ItemType {
        RESOURCE, TOOL, EQUIPMENT, CONSUMABLE, MISC
    }


    // Constructor for items that will use placeholder color (no specific icon texture)
    public Item(String itemId, String displayName, String description, ItemType type, int maxStackSize, float[] placeholderColor) {
        this(itemId, displayName, description, type, maxStackSize, placeholderColor, false, 0f, 0f, 0f, 0f); // Calls the main constructor
    }
    // Ensure this constructor matches:
    public Item(String itemId, String displayName, String description, ItemType type, int maxStackSize, float[] placeholderColor, boolean hasIconTexture, float iconU0, float iconV0, float iconU1, float iconV1) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.maxStackSize = maxStackSize;
        this.placeholderColor = placeholderColor; //
        // --- ADD THESE ASSIGNMENTS ---
        this.hasIconTexture = hasIconTexture;
        this.iconU0 = iconU0;
        this.iconV0 = iconV0;
        this.iconU1 = iconU1;
        this.iconV1 = iconV1;// This line is important
    }

    // --- Getters ---
    public String getItemId() { return itemId; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getMaxStackSize() { return maxStackSize; }
    public ItemType getType() { return type; }
    public float[] getPlaceholderColor() { return placeholderColor; } // Getter for the color



    // --- ADD THESE NEW GETTERS ---
    public boolean hasIconTexture() { return hasIconTexture; }
    public float getIconU0() { return iconU0; }
    public float getIconV0() { return iconV0; }
    public float getIconU1() { return iconU1; }
    public float getIconV1() { return iconV1; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return Objects.equals(itemId, item.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId);
    }

    @Override
    public String toString() {
        return "Item{" +
                "itemId='" + itemId + '\'' +
                ", displayName='" + displayName + '\'' +
                '}';
    }
}