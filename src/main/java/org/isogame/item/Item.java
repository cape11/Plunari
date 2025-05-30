package org.isogame.item;

import java.util.Objects;

public class Item {
    private String itemId;          // Unique identifier, e.g., "dirt_block", "iron_pickaxe"
    private String displayName;     // How it appears in UI, e.g., "Dirt Block", "Iron Pickaxe"
    private String description;
    private int maxStackSize;       // How many can fit in one stack (e.g., 1 for tools, 64 for resources)
    private ItemType type;          // Enum for item categories
    // private String iconTexturePath; // Future: For UI icon

    public enum ItemType {
        RESOURCE, TOOL, EQUIPMENT, CONSUMABLE, MISC
    }

    public Item(String itemId, String displayName, String description, ItemType type, int maxStackSize) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.maxStackSize = maxStackSize;
        // this.iconTexturePath = iconTexturePath; // Future
    }

    // --- Getters ---
    public String getItemId() { return itemId; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getMaxStackSize() { return maxStackSize; }
    public ItemType getType() { return type; }
    // public String getIconTexturePath() { return iconTexturePath; } // Future

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