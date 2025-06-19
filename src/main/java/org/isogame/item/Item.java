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
    public ItemType type;
    private float[] placeholderColor;

    // --- NEW: Data-Driven Fields (Terraria-Style) ---
    public UseStyle useStyle = UseStyle.NONE; // How the item is animated/used.
    public int useTime = 20;                  // Cooldown in ticks/frames before it can be used again.
    public int useAnimation = 20;             // How long the use animation lasts in ticks/frames.
    public int damage = 0;                    // Damage dealt by the item's projectile or effect.
    public float knockback = 0;                 // Knockback strength.
    // --- End New Fields ---

    // --- NEW: Add a field for the atlas name ---
    private String atlasName;

    // These fields will temporarily store pixel coordinates (x, y, w, h)
    // before being converted to final UVs.
    private float iconU0, iconV0, iconU1, iconV1;
    private boolean hasIconTexture;

    public enum ItemType {
        RESOURCE, TOOL, EQUIPMENT, CONSUMABLE, MISC
    }

    // In the onUse method:
    public boolean onUse(Game game, PlayerModel player, Tile targetTile, int tileR, int tileC) {
        if (this.useStyle != UseStyle.NONE) {
            // --- FIX: Pass the 'game' object, not 'this' item ---
            player.useItem(game);
            return true;
        }
        return false;
    }

    // Constructor for items WITHOUT a texture icon
    public Item(String itemId, String displayName, String description, ItemType type, int maxStackSize, float[] placeholderColor) {
        this(itemId, displayName, description, type, maxStackSize, placeholderColor, false, null, 0, 0, 0, 0);
    }

    // Main constructor for items WITH a texture icon.
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
        this.iconU0 = pixelX;
        this.iconV0 = pixelY;
        this.iconU1 = pixelW;
        this.iconV1 = pixelH;
    }

    public void calculateUVs(Texture atlas) {
        if (!hasIconTexture || atlas == null || atlas.getWidth() == 0 || atlas.getHeight() == 0) {
            return;
        }
        float atlasWidth = atlas.getWidth();
        float atlasHeight = atlas.getHeight();
        float pX = this.iconU0, pY = this.iconV0, pW = this.iconU1, pH = this.iconV1;
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
    public float[] getPlaceholderColor() { return placeholderColor; }
    public String getAtlasName() { return atlasName; }
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


    public Item setStats(int damage, int useTime, int useAnimation, float knockback) {
        this.damage = damage;
        this.useTime = useTime;
        this.useAnimation = useAnimation;
        this.knockback = knockback;
        return this;
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