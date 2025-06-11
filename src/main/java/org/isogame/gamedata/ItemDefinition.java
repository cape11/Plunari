// Create this in a new package, e.g., org.isogame.gamedata

package org.isogame.gamedata;

// This class maps directly to the structure of your new item JSON files.
public class ItemDefinition {
    public String id;
    public String displayName;
    public String description;
    public String type; // e.g., "TOOL", "RESOURCE"
    public String useStyle; // e.g., "SWING"
    public String toolType; // e.g., "AXE"

    public TextureData texture;
    public StatsData stats;

    public static class TextureData {
        public String atlas;
        public float x, y, w, h;
    }

    public static class StatsData {
        public int damage = 1;
        public int useTime = 20;
        public int useAnimation = 20;
        public float knockback = 0;
    }
}
