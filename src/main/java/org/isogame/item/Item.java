// In file: org/isogame/item/Item.java
package org.isogame.item;

import java.util.Objects;

public class Item {
    private final String itemId;
    private final String displayName;
    private final String description;
    private final int maxStackSize;
    private final ItemType type;
    private final float[] placeholderColor;

    // Fields for Icon UVs
    private final boolean hasIconTexture;
    private final float iconU0, iconV0, iconU1, iconV1;

    public enum ItemType {
        RESOURCE, TOOL, EQUIPMENT, CONSUMABLE, MISC
    }

    // Main constructor for items that have an icon
    public Item(String itemId, String displayName, String description, ItemType type, int maxStackSize, float[] placeholderColor,
                boolean hasIconTexture, float iconU0, float iconV0, float iconU1, float iconV1) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.maxStackSize = maxStackSize;
        this.placeholderColor = placeholderColor;
        this.hasIconTexture = hasIconTexture;
        this.iconU0 = iconU0;
        this.iconV0 = iconV0;
        this.iconU1 = iconU1;
        this.iconV1 = iconV1;
    }

    // Secondary constructor for items that do NOT have an icon
    public Item(String itemId, String displayName, String description, ItemType type, int maxStackSize, float[] placeholderColor) {
        this(itemId, displayName, description, type, maxStackSize, placeholderColor, false, 0f, 0f, 0f, 0f);
    }

    // --- Getters ---
    public String getItemId() { return itemId; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getMaxStackSize() { return maxStackSize; }
    public ItemType getType() { return type; }
    public float[] getPlaceholderColor() { return placeholderColor; }

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
}