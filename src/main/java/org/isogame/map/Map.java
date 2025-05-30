package org.isogame.map;

import org.isogame.tile.Tile;
import java.util.Random;

import org.isogame.item.Item;
import org.isogame.item.ItemRegistry; // For checking item types
import org.isogame.tile.Tile;
import java.util.Random;


import static org.isogame.constants.Constants.*;

public class Map {

    private final Tile[][] tiles;
    private final int width;
    private final int height;
    private SimplexNoise noiseGenerator;
    private final Random random = new Random();
    private int characterSpawnRow;
    private int characterSpawnCol;

    private final LightManager lightManager;

    public Map(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new Tile[height][width];
        this.lightManager = new LightManager(this);

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                tiles[r][c] = new Tile(Tile.TileType.WATER, 0);
            }
        }
        generateMap();
    }

    public void generateMap() {
        System.out.println("Map: Generating map terrain...");
        this.noiseGenerator = new SimplexNoise(random.nextInt(10000));
        int[][] rawElevations = new int[height][width];

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                double noiseValue = calculateCombinedNoise((double) c, (double) r);
                int elevation = (int) (((noiseValue + 1.0) / 2.0) * (ALTURA_MAXIMA + 1)) - 1;
                rawElevations[r][c] = Math.max(0, Math.min(ALTURA_MAXIMA, elevation));
            }
        }
        smoothTerrain(rawElevations, 1);

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                int elevation = rawElevations[r][c];
                Tile.TileType type = determineTileTypeFromElevation(elevation);
                Tile currentTile = tiles[r][c];
                currentTile.setElevation(elevation);
                currentTile.setType(type);
                currentTile.setSkyLightLevel((byte) 0);
                currentTile.setBlockLightLevel((byte) 0);
                currentTile.setHasTorch(false);

                if (type == Tile.TileType.GRASS && elevation >= NIVEL_ARENA && elevation < NIVEL_ROCA) {
                    if (random.nextFloat() < 0.08) {
                        currentTile.setTreeType(random.nextBoolean() ? Tile.TreeVisualType.APPLE_TREE_FRUITING : Tile.TreeVisualType.PINE_TREE_SMALL);
                    } else {
                        currentTile.setTreeType(Tile.TreeVisualType.NONE);
                    }
                } else {
                    currentTile.setTreeType(Tile.TreeVisualType.NONE);
                }
            }
        }
        findSuitableCharacterPosition();
        System.out.println("Map: Terrain generation finished. Initializing lighting...");

        for (int r = 0; r < height; r+=CHUNK_SIZE_TILES) {
            for (int c = 0; c < width; c+=CHUNK_SIZE_TILES) {
                lightManager.markChunkDirty(r,c);
            }
        }
        if (height % CHUNK_SIZE_TILES != 0) {
            for (int c = 0; c < width; c+=CHUNK_SIZE_TILES) lightManager.markChunkDirty(height -1 ,c);
        }
        if (width % CHUNK_SIZE_TILES != 0) {
            for (int r = 0; r < height; r+=CHUNK_SIZE_TILES) lightManager.markChunkDirty(r ,width -1);
        }
        if (height % CHUNK_SIZE_TILES != 0 && width % CHUNK_SIZE_TILES != 0) {
            lightManager.markChunkDirty(height-1, width-1);
        }

        System.out.println("Map: Initial lighting will be processed by Game loop. Spawn: (" + characterSpawnRow + ", " + characterSpawnCol + ")");
    }

    private double calculateCombinedNoise(double x, double y) {
        double baseFrequency = NOISE_SCALE * 0.05;
        double mountainFrequency = NOISE_SCALE * 0.2;
        double roughnessFrequency = NOISE_SCALE * 0.8;
        double baseNoise = noiseGenerator.octaveNoise(x * baseFrequency, y * baseFrequency, 4, 0.6);
        double mountainNoise = noiseGenerator.noise(x * mountainFrequency, y * mountainFrequency) * 0.40;
        double roughnessNoise = noiseGenerator.noise(x * roughnessFrequency, y * roughnessFrequency) * 0.15;
        double combined = baseNoise + mountainNoise + roughnessNoise;
        return Math.max(-1.0, Math.min(1.0, combined));
    }


    public boolean placeBlock(int r, int c, Item itemToPlace) {
        if (!isValid(r, c) || itemToPlace == null || itemToPlace.getType() != Item.ItemType.RESOURCE) {
            System.out.println("Map: Invalid placement parameters or item not a resource.");
            return false;
        }

        Tile targetTile = getTile(r, c);
        if (targetTile == null) return false; // Should not happen if isValid

        int currentElevation = targetTile.getElevation();

        // MVP Placement Rule: Place on top of the selected tile, increasing its elevation by 1.
        // The new tile will adopt a type based on the item placed and new elevation.
        int newElevation = currentElevation + 1;

        if (newElevation > ALTURA_MAXIMA) {
            System.out.println("Map: Cannot place block, max elevation (" + ALTURA_MAXIMA + ") would be exceeded at (" + r + "," + c + ")");
            return false;
        }

        Tile.TileType newType;
        if (itemToPlace.equals(ItemRegistry.DIRT)) {
            newType = determineTileTypeFromElevation(newElevation); // Dirt can become grass, sand, etc.
            // If placing dirt into water to fill it, ensure it becomes land.
            if (targetTile.getType() == Tile.TileType.WATER && newElevation < NIVEL_ARENA) newType = Tile.TileType.SAND;
            else if (targetTile.getType() == Tile.TileType.WATER) newType = Tile.TileType.GRASS;

        } else if (itemToPlace.equals(ItemRegistry.STONE)) {
            newType = Tile.TileType.ROCK;
        } else if (itemToPlace.equals(ItemRegistry.SAND)) {
            newType = Tile.TileType.SAND;
        } else {
            System.out.println("Map: Item " + itemToPlace.getDisplayName() + " is not a recognized placeable block type for MVP.");
            return false; // Item not recognized as placeable block
        }

        // If placing in water, we might want to set elevation directly to NIVEL_MAR or NIVEL_ARENA-1
        if (targetTile.getType() == Tile.TileType.WATER) {
            if (newType == Tile.TileType.SAND) newElevation = Math.min(newElevation, NIVEL_ARENA -1);
            else if (newType == Tile.TileType.GRASS) newElevation = Math.min(newElevation, NIVEL_ROCA -1);
            // Ensure water is actually filled up to a land level
            if (newElevation < NIVEL_MAR) newElevation = NIVEL_MAR;
        }


        System.out.println("Map: Placing " + itemToPlace.getDisplayName() + " at (" + r + "," + c + "). Old Elev: " + currentElevation + ", New Elev: " + newElevation + ", New Type: " + newType);

        targetTile.setElevation(newElevation);
        targetTile.setType(newType);
        targetTile.setTreeType(Tile.TreeVisualType.NONE); // Placing a block removes trees

        // Mark affected chunks as dirty for rendering and lighting
        // The tile itself and its neighbors might need light updates
        lightManager.markChunkDirty(r, c);
        if(targetTile.hasTorch()){ // If placing on a tile that had a torch, torch might be buried
            lightManager.removeLightSource(r,c); // remove and re-add to re-calc light from new position/occlusion
            lightManager.addLightSource(r,c, (byte) TORCH_LIGHT_LEVEL);
        }
        // For lighting, changing elevation might block/unblock sky light or affect propagation.
        // The setTileElevation in Map.java (which is called by this method indirectly via targetTile.setElevation)
        // should ideally handle comprehensive light updates around the change.
        // Let's refine setTileElevation to ensure it does. (See modification for Map.setTileElevation below)

        // Request render update for the chunk containing this tile
        // This is handled by Game loop processing LightManager's dirty chunks.

        return true;
    }





    private Tile.TileType determineTileTypeFromElevation(int elevation) {
        if (elevation < NIVEL_MAR) return Tile.TileType.WATER;
        if (elevation < NIVEL_ARENA) return Tile.TileType.SAND;
        if (elevation < NIVEL_ROCA) return Tile.TileType.GRASS;
        if (elevation < NIVEL_NIEVE) return Tile.TileType.ROCK;
        return Tile.TileType.SNOW;
    }

    private void smoothTerrain(int[][] elevations, int passes) {
        if (passes <= 0) return;
        int currentHeight = elevations.length;
        int currentWidth = elevations[0].length;
        int[][] tempAlturas = new int[currentHeight][currentWidth];
        for (int pass = 0; pass < passes; pass++) {
            for (int r = 0; r < currentHeight; r++) {
                System.arraycopy(elevations[r], 0, tempAlturas[r], 0, currentWidth);
            }
            for (int r = 0; r < currentHeight; r++) {
                for (int c = 0; c < currentWidth; c++) {
                    int sum = 0;
                    int count = 0;
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            int nr = r + i;
                            int nc = c + j;
                            if (nr >= 0 && nr < currentHeight && nc >= 0 && nc < currentWidth) {
                                sum += tempAlturas[nr][nc];
                                count++;
                            }
                        }
                    }
                    if (count > 0) elevations[r][c] = sum / count;
                }
            }
        }
    }

    private void findSuitableCharacterPosition() {
        int centerRow = height / 2;
        int centerCol = width / 2;
        for (int radius = 0; radius < Math.max(width, height) / 2; radius++) {
            for (int rOffset = -radius; rOffset <= radius; rOffset++) {
                for (int cOffset = -radius; cOffset <= radius; cOffset++) {
                    if (Math.abs(rOffset) == radius || Math.abs(cOffset) == radius) {
                        int r = centerRow + rOffset;
                        int c = centerCol + cOffset;
                        if (isValid(r, c) && tiles[r][c].getType() != Tile.TileType.WATER && tiles[r][c].getElevation() >= NIVEL_MAR) {
                            characterSpawnRow = r;
                            characterSpawnCol = c;
                            return;
                        }
                    }
                }
            }
        }
        characterSpawnRow = centerRow;
        characterSpawnCol = centerCol;
    }

    public Tile getTile(int row, int col) {
        if (isValid(row, col)) return tiles[row][col];
        return null;
    }

    public boolean isValid(int row, int col) {
        return row >= 0 && row < height && col >= 0 && col < width;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getCharacterSpawnRow() { return characterSpawnRow; }
    public int getCharacterSpawnCol() { return characterSpawnCol; }
    public LightManager getLightManager() { return lightManager; }


    public void setTileElevation(int row, int col, int elevation) {
        if (isValid(row, col)) {
            Tile tile = tiles[row][col];
            int oldElevation = tile.getElevation();
            Tile.TileType oldType = tile.getType();
            boolean oldTorchState = tile.hasTorch();
            byte oldBlockLight = tile.getBlockLightLevel();

            int clampedElevation = Math.max(0, Math.min(ALTURA_MAXIMA, elevation));
            tile.setElevation(clampedElevation);
            tile.setType(determineTileTypeFromElevation(clampedElevation));

            if (tile.getTreeType() != Tile.TreeVisualType.NONE && clampedElevation < oldElevation) {
                // Tree might be destroyed if ground is lowered significantly, or if ground type changes
                tile.setTreeType(Tile.TreeVisualType.NONE);
            }

            if (oldElevation != clampedElevation || oldType != tile.getType()) {
                // Mark a 3x3 area of chunks around the tile as dirty for lighting and rendering
                // This ensures sky light and block light propagation is recalculated correctly.
                for(int dr = -1; dr <= 1; dr++) {
                    for(int dc = -1; dc <= 1; dc++) {
                        // Mark chunks for rendering
                        lightManager.markChunkDirty(row + dr, col + dc); // Marks chunk for render update

                        // More aggressively re-evaluate light for tiles in immediate vicinity if elevation changed
                        // This part is complex; LightManager's propagation should handle it if sources are updated.
                        // For MVP, ensuring torch is correctly handled is key.
                    }
                }

                // If the tile had a torch, its light source needs to be re-evaluated
                if (oldTorchState) {
                    lightManager.removeLightSource(row, col); // Remove its old light
                    tile.setBlockLightLevel((byte)0); // Explicitly set to 0 before re-adding
                    lightManager.addLightSource(row, col, (byte) TORCH_LIGHT_LEVEL); // Re-add to propagate from new state
                }
                // If elevation change uncovers/covers sky, skylight propagation will be handled by LightManager
                // based on dirty chunks marked by updateGlobalSkyLight or tile changes.
                // We might need to explicitly add the tile to skylight removal/addition queue if it's now covered/uncovered.
                // For now, rely on broad dirty chunk marking.
            }
        }
    }

    public void toggleTorch(int r, int c) {
        Tile tile = getTile(r,c);
        if (tile != null && tile.getType() != Tile.TileType.WATER) {
            if (tile.hasTorch()) {
                lightManager.removeLightSource(r, c);
                System.out.println("Map: Torch removed at (" + r + "," + c + ")");
            } else {
                lightManager.addLightSource(r, c, (byte) TORCH_LIGHT_LEVEL);
                System.out.println("Map: Torch added at (" + r + "," + c + ")");
            }
        }
    }

    public boolean isTileOpaque(int r, int c) {
        Tile tile = getTile(r, c);
        if (tile == null) {
            return true;
        }
        return !tile.isTransparentToLight();
    }
}
