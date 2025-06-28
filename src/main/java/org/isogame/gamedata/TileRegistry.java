// Create this in: org/isogame/gamedata/TileRegistry.java
package org.isogame.gamedata;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TileRegistry {

    private static final Map<String, TileDefinition> tileDefMap = new HashMap<>();
    private static final Gson gson = new Gson();

    // A list of all the tile data files we need to load.
    private static final List<String> TILE_FILES = Arrays.asList(
            "grass.json",
            "dirt.json",
            "rock.json",
            "sand.json",
            "snow.json",
            "wood_plank.json",
            "red_brick.json",
            "stone_wall_smooth.json",
            "stone_wall_rough.json"
    );

    /**
     * Loads all tile definitions from the JSON files in the resources/data/tiles directory.
     */
    public static void loadTileDefinitions() {
        tileDefMap.clear();
        System.out.println("TileRegistry: Initializing tile definition loading...");
        String resourceFolder = "/data/tiles/";

        for (String fileName : TILE_FILES) {
            String fullPath = resourceFolder + fileName;
            try (InputStream is = TileRegistry.class.getResourceAsStream(fullPath)) {
                if (is == null) {
                    System.err.println("    - CRITICAL FAILURE: Cannot find resource file on classpath: " + fullPath);
                    continue;
                }

                try (Reader reader = new InputStreamReader(is)) {
                    TileDefinition def = gson.fromJson(reader, TileDefinition.class);
                    if (def != null && def.id != null) {
                        tileDefMap.put(def.id, def);
                        System.out.println("    - SUCCESS: Loaded tile definition: " + def.id);
                    } else {
                        System.err.println("    - FAILED: Could not parse tile definition from " + fileName);
                    }
                }
            } catch (Exception e) {
                System.err.println("    - ERROR: Exception while processing " + fullPath);
                e.printStackTrace();
            }
        }
        System.out.println("TileRegistry: " + tileDefMap.size() + " total tile definitions loaded.");
    }

    /**
     * Retrieves a tile definition by its unique ID.
     *
     * @param id The ID of the tile definition (e.g., "grass").
     * @return The corresponding TileDefinition, or null if not found.
     */
    public static TileDefinition getTileDefinition(String id) {
        if (id == null) return null;
        return tileDefMap.get(id);
    }
}