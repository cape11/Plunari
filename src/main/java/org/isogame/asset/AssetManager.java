package org.isogame.asset;

import org.isogame.render.Font;
import org.isogame.render.Renderer;
import org.isogame.render.Texture;

import java.util.HashMap;
import java.util.Map;

/**
 * A centralized class for loading, managing, and providing access to game assets
 * like textures and fonts.
 */
public class AssetManager {

    private final Map<String, Texture> textureMap = new HashMap<>();
    private final Map<String, Font> fontMap = new HashMap<>();
    private final Renderer renderer; // Font loading needs a renderer reference

    public AssetManager(Renderer renderer) {
        this.renderer = renderer;
    }

    public void loadAllAssets() {
        System.out.println("AssetManager: Loading all assets...");
        try {
            // Load Textures
            // Use more descriptive names that match your JSON data
            loadTexture("tileAtlasTexture", "/org/isogame/render/textures/textu.png");
            loadTexture("mainMenuBackground", "/org/isogame/render/textures/main_menu_background.png");
            loadTexture("playerTexture", "/org/isogame/render/textures/lpc_character.png");
            loadTexture("treeTexture", "/org/isogame/render/textures/fruit-trees.png"); // <-- RENAME THIS KEY

            // Load Fonts
            loadFont("ui", "/org/isogame/render/fonts/PressStart2P-Regular.ttf", 16f);
            loadFont("title", "/org/isogame/render/fonts/PressStart2P-Regular.ttf", 32f);

            System.out.println("AssetManager: All assets loaded.");
        } catch (Exception e) {
            System.err.println("CRITICAL: Exception during asset loading!");
            e.printStackTrace();
        }
    }

    private void loadTexture(String name, String path) {
        Texture texture = Texture.loadTexture(path);
        if (texture != null) {
            textureMap.put(name, texture);
        } else {
            System.err.println("Failed to load texture: " + name + " from path: " + path);
        }
    }

    private void loadFont(String name, String path, float size) {
        try {
            Font font = new Font(path, size, this.renderer);
            fontMap.put(name, font);
        } catch (Exception e) {
            System.err.println("Failed to load font: " + name + " from path: " + path);
        }
    }

    public Texture getTexture(String name) {
        Texture texture = textureMap.get(name);
        if (texture == null) {
            System.err.println("Warning: Tried to get unknown texture named: " + name);
        }
        return texture;
    }

    public Font getFont(String name) {
        Font font = fontMap.get(name);
        if (font == null) {
            System.err.println("Warning: Tried to get unknown font named: " + name);
        }
        return font;
    }

    public Map<String, Texture> getTextureMapForRegistry() {
        Map<String, Texture> registryMap = new HashMap<>();
        // Use the new, consistent keys
        registryMap.put("tileAtlasTexture", getTexture("tileAtlasTexture"));
        registryMap.put("playerTexture", getTexture("playerTexture"));
        registryMap.put("treeTexture", getTexture("treeTexture")); // <-- UPDATE THIS KEY
        return registryMap;
    }

    public void cleanup() {
        System.out.println("AssetManager: Cleaning up assets...");
        for (Texture texture : textureMap.values()) {
            texture.delete();
        }
        textureMap.clear();

        for (Font font : fontMap.values()) {
            font.cleanup();
        }
        fontMap.clear();
        System.out.println("AssetManager: Cleanup complete.");
    }
}