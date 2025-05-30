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

    public enum ItemType {
        RESOURCE, TOOL, EQUIPMENT, CONSUMABLE, MISC
    }

    // Ensure this constructor matches:
    public Item(String itemId, String displayName, String description, ItemType type, int maxStackSize, float[] placeholderColor) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.maxStackSize = maxStackSize;
        this.placeholderColor = placeholderColor; // This line is important
    }

    // --- Getters ---
    public String getItemId() { return itemId; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getMaxStackSize() { return maxStackSize; }
    public ItemType getType() { return type; }
    public float[] getPlaceholderColor() { return placeholderColor; } // Getter for the color

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