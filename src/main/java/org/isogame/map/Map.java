package org.isogame.map;

import org.isogame.tile.Tile;
import java.util.Random;
import java.util.Set;

import static org.isogame.constants.Constants.*;

public class Map {

    private final Tile[][] tiles;
    private final int width;
    private final int height;
    private SimplexNoise noiseGenerator;
    private final Random random = new Random();
    private int characterSpawnRow;
    private int characterSpawnCol;

    private final LightManager lightManager; // Added LightManager

    public Map(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new Tile[height][width];
        this.lightManager = new LightManager(this); // Initialize LightManager

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                tiles[r][c] = new Tile(Tile.TileType.WATER, 0); // Default to water, elevation 0
            }
        }
        generateMap(); // This will also initialize lighting
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
                currentTile.setSkyLightLevel((byte) 0); // Reset light before recalculation
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
        lightManager.updateGlobalSkyLight((byte) SKY_LIGHT_DAY); // Initialize with full day sky light // FIX: Cast to byte
        System.out.println("Map: Initial lighting complete. Spawn: (" + characterSpawnRow + ", " + characterSpawnCol + ")");
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

            int clampedElevation = Math.max(0, Math.min(ALTURA_MAXIMA, elevation));
            tile.setElevation(clampedElevation);
            tile.setType(determineTileTypeFromElevation(clampedElevation));

            if (tile.getTreeType() != Tile.TreeVisualType.NONE) {
                tile.setTreeType(Tile.TreeVisualType.NONE);
            }

            if (oldElevation != clampedElevation || oldType != tile.getType()) {
                lightManager.updateGlobalSkyLight(getCurrentGlobalSkyLightBasedOnTime());
                lightManager.markChunkDirty(row, col); // FIX: markChunkDirty is now public in LightManager
                for(int dr = -1; dr <= 1; dr++) {
                    for(int dc = -1; dc <= 1; dc++) {
                        if(isValid(row + dr, col + dc)) {
                            lightManager.markChunkDirty(row + dr, col + dc); // FIX: markChunkDirty is now public in LightManager
                        }
                    }
                }
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

    private byte getCurrentGlobalSkyLightBasedOnTime() {
        // This should be driven by Game's pseudoTimeOfDay
        // For now, let's assume it's day.
        return (byte) SKY_LIGHT_DAY; // FIX: Cast to byte
    }

    public boolean isTileOpaque(int r, int c) {
        Tile tile = getTile(r, c);
        if (tile == null) {
            return true;
        }
        return !tile.isTransparentToLight();
    }
}
