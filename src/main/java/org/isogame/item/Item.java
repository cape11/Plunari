// In Item.java

package org.isogame.item;

import java.util.Objects;

import org.isogame.entity.PlayerModel;
import org.isogame.game.Game;
import org.isogame.render.Texture; // Import Texture
import org.isogame.tile.Tile;

public class Item {
    private String itemId;
    private String displayName;
    private String description;
    private int maxStackSize;
    private ItemType type;
    private float[] placeholderColor;

    // --- NEW: Add a field for the atlas name ---
    private String atlasName;

    // These fields will temporarily store pixel coordinates (x, y, w, h)
    // before being converted to final UVs.
    private float iconU0, iconV0, iconU1, iconV1;
    private boolean hasIconTexture;

    public enum ItemType {
        RESOURCE, TOOL, EQUIPMENT, CONSUMABLE, MISC
    }

    // --- MODIFIED CONSTRUCTORS ---


    public boolean onUse(Game game, PlayerModel player, Tile targetTile, int tileR, int tileC) {
        // Default behavior for a generic item is to do nothing.
        return false;
    }

    // Constructor for items WITHOUT a texture icon
    public Item(String itemId, String displayName, String description, ItemType type, int maxStackSize, float[] placeholderColor) {
        this(itemId, displayName, description, type, maxStackSize, placeholderColor, false, null, 0, 0, 0, 0);
    }

    // Main constructor for items WITH a texture icon. It now takes atlas name and PIXEL coordinates.
    public Item(String itemId, String displayName, String description, ItemType type, int maxStackSize, float[] placeholderColor,
                boolean hasIconTexture, String atlasName, float pixelX, float pixelY, float pixelW, float pixelH) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.maxStackSize = maxStackSize;
        this.placeholderColor = placeholderColor;

        this.hasIconTexture = hasIconTexture;
        this.atlasName = atlasName;
        // Store raw pixel data first. This will be converted to UVs later.
        this.iconU0 = pixelX;
        this.iconV0 = pixelY;
        this.iconU1 = pixelW;
        this.iconV1 = pixelH;
    }

    /**
     * Converts the stored pixel coordinates into normalized UV coordinates.
     * This should be called by the ItemRegistry after textures are loaded.
     */
    public void calculateUVs(Texture atlas) {
        if (!hasIconTexture || atlas == null || atlas.getWidth() == 0 || atlas.getHeight() == 0) {
            return;
        }

        float atlasWidth = atlas.getWidth();
        float atlasHeight = atlas.getHeight();

        float pX = this.iconU0;
        float pY = this.iconV0;
        float pW = this.iconU1;
        float pH = this.iconV1;

        this.iconU0 = pX / atlasWidth;
        this.iconV0 = pY / atlasHeight;
        this.iconU1 = (pX + pW) / atlasWidth;
        this.iconV1 = (pY + pH) / atlasHeight;
    }

    // --- Getters ---
    public String getItemId() { return itemId; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getMaxStackSize() { return maxStackSize; }
    public ItemType getType() { return type; }
    public float[] getPlaceholderColor() { return placeholderColor; }
    public String getAtlasName() { return atlasName; } // Getter for atlas name

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
        return "Item{" + "itemId='" + itemId + '\'' + ", displayName='" + displayName + '\'' + '}';
    }
}