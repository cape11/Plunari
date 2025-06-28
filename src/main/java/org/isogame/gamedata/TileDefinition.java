// Create this in: org/isogame/gamedata/TileDefinition.java
package org.isogame.gamedata;

/**
 * A data structure that holds all the necessary data for defining a tile type,
 * loaded from a JSON file. This allows for a completely data-driven tile system.
 */
public class TileDefinition {
    public String id; // e.g., "grass", "rock"
    public TextureData texture;

    /**
     * Defines the texture coordinates for a tile's surfaces.
     */
    public static class TextureData {
        public String atlas; // The name of the texture atlas, e.g., "tileAtlasTexture"
        public
        TextureCoords top;   // Coordinates for the top face of the tile
        public TextureCoords side;  // Coordinates for the side faces of the tile
    }

    /**
     * A simple class to hold the x, y, width, and height of a texture region.
     */
    public static class TextureCoords {
        public float x, y, w, h;
    }
}