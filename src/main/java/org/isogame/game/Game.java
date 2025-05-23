package org.isogame.game;

import org.isogame.input.InputHandler;
import org.isogame.input.MouseHandler;
import org.isogame.camera.CameraManager;
import org.isogame.map.LightManager;
import org.isogame.map.Map;
import org.isogame.render.Renderer;
import org.isogame.entitiy.PlayerModel;
import org.isogame.tile.Tile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.isogame.constants.Constants.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Game {
    private double lastFrameTime;

    private final long window;
    private InputHandler inputHandler;
    private MouseHandler mouseHandler;
    private final CameraManager cameraManager;
    private final Renderer renderer;
    private final Map map;
    private final PlayerModel player;
    private final LightManager lightManager; // Added

    private double pseudoTimeOfDay = 0.25; // Start in daytime (0.0 to 0.5 is day, 0.5 to 1.0 is night)
    private final double DAY_NIGHT_CYCLE_SPEED = 0.1; // Slower cycle for testing
    private byte currentGlobalSkyLight;


    public Game(long window, int initialFramebufferWidth, int initialFramebufferHeight) {
        this.window = window;

        map = new Map(MAP_WIDTH, MAP_HEIGHT); // Map initializes its own LightManager
        lightManager = map.getLightManager(); // Get the LightManager from the map
        player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
        cameraManager = new CameraManager(initialFramebufferWidth, initialFramebufferHeight, map.getWidth(), map.getHeight());
        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());

        // Pass 'this' (Game instance) to InputHandler
        inputHandler = new InputHandler(window, cameraManager, map, player, this);
        inputHandler.registerCallbacks(this::requestFullMapRegeneration); // Changed callback name for clarity

        renderer = new Renderer(cameraManager, map, player, inputHandler);
        mouseHandler = new MouseHandler(window, cameraManager, map, inputHandler, player);

        glfwSetFramebufferSizeCallback(window, (win, fbW, fbH) -> {
            if (cameraManager != null) cameraManager.updateScreenSize(fbW, fbH);
            if (renderer != null) renderer.onResize(fbW, fbH);
        });

        if (renderer != null) renderer.onResize(initialFramebufferWidth, initialFramebufferHeight);

        // Initial sky light update
        updateSkyLightBasedOnTimeOfDay();

        System.out.println("Game components initialized.");
    }

    private void initOpenGL() {
        System.out.println("Game.initOpenGL() - OpenGL version from context: " + glGetString(GL_VERSION));
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        System.out.println("Game.initOpenGL() - Depth Test Enabled.");
    }

    public void gameLoop() {
        initOpenGL();
        lastFrameTime = glfwGetTime();
        System.out.println("Entering game loop...");
        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            double deltaTime = currentTime - lastFrameTime;
            lastFrameTime = currentTime;
            if (deltaTime > 0.1) deltaTime = 0.1; // Cap delta time

            glfwPollEvents();
            updateGameLogic(deltaTime);
            renderGame();
            glfwSwapBuffers(window);
        }
        System.out.println("Game loop exited.");
        cleanup();
    }

    private void updateSkyLightBasedOnTimeOfDay() {
        byte newSkyLight;
        // Example: 0.0 to 0.45 = Day, 0.45 to 0.55 = Dusk, 0.55 to 0.95 = Night, 0.95 to 1.0 = Dawn
        if (pseudoTimeOfDay >= 0.0 && pseudoTimeOfDay < 0.45) { // Day
            newSkyLight = SKY_LIGHT_DAY;
        } else if (pseudoTimeOfDay >= 0.45 && pseudoTimeOfDay < 0.55) { // Dusk
            float phase = (float) ((pseudoTimeOfDay - 0.45) / 0.10); // 0 to 1 during dusk
            newSkyLight = (byte) (SKY_LIGHT_DAY - phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        } else if (pseudoTimeOfDay >= 0.55 && pseudoTimeOfDay < 0.95) { // Night
            newSkyLight = SKY_LIGHT_NIGHT;
        } else { // Dawn (pseudoTimeOfDay >= 0.95 && pseudoTimeOfDay < 1.0)
            float phase = (float) ((pseudoTimeOfDay - 0.95) / 0.05); // 0 to 1 during dawn
            newSkyLight = (byte) (SKY_LIGHT_NIGHT + phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        }
        newSkyLight = (byte)Math.max(0, Math.min(MAX_LIGHT_LEVEL, newSkyLight));

        if (newSkyLight != currentGlobalSkyLight) {
            currentGlobalSkyLight = newSkyLight;
            lightManager.updateGlobalSkyLight(currentGlobalSkyLight);
            System.out.println("Game: Sky light updated to " + currentGlobalSkyLight + " based on time: " + pseudoTimeOfDay);
        }
    }

    private void updateGameLogic(double deltaTime) {
        pseudoTimeOfDay += deltaTime * DAY_NIGHT_CYCLE_SPEED;
        if (pseudoTimeOfDay >= 1.0) {
            pseudoTimeOfDay -= 1.0;
        }
        updateSkyLightBasedOnTimeOfDay(); // Update sky light based on new time

        if (inputHandler != null) inputHandler.handleContinuousInput(deltaTime);
        if (player != null) player.update(deltaTime);
        if (cameraManager != null) cameraManager.update(deltaTime);

        // Process dirty chunks for rendering
        Set<LightManager.ChunkCoordinate> dirtyChunks = lightManager.getDirtyChunksAndClear();
        if (!dirtyChunks.isEmpty()) {
            // System.out.println("Game: Processing " + dirtyChunks.size() + " dirty chunks for renderer update.");
            for (LightManager.ChunkCoordinate coord : dirtyChunks) {
                if (renderer != null) {
                    // The LightManager gives chunk grid coordinates. Convert to a tile within that chunk.
                    renderer.updateChunkByGridCoords(coord.chunkX, coord.chunkY);
                }
            }
        }
    }

    private void renderGame() {
        // Background color based on time of day (can be simpler now that lighting handles darkness)
        float rSky, gSky, bSky;
        if (currentGlobalSkyLight > SKY_LIGHT_NIGHT + (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT) / 2) { // More day than night
            rSky = 0.5f; gSky = 0.7f; bSky = 1.0f; // Light blue sky
        } else { // More night than day
            rSky = 0.05f; gSky = 0.05f; bSky = 0.15f; // Dark blue/purple sky
        }
        glClearColor(rSky, gSky, bSky, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (renderer != null) {
            renderer.render(); // Renderer handles its own objects

            if (this.showDebugOverlay) {
                List<String> debugLines = new ArrayList<>();
                debugLines.add(String.format("Time: %.2f, SkyLight: %d", pseudoTimeOfDay, currentGlobalSkyLight));
                if (player != null) debugLines.add("Player: (" + player.getTileRow() + ", " + player.getTileCol() + ")");
                if (inputHandler != null) {
                    Tile selectedTile = map.getTile(inputHandler.getSelectedRow(), inputHandler.getSelectedCol());
                    String selectedInfo = "Selected: ("+inputHandler.getSelectedRow()+","+inputHandler.getSelectedCol()+")";
                    if (selectedTile != null) {
                        selectedInfo += " Elev: " + selectedTile.getElevation() + " Type: " + selectedTile.getType();
                        selectedInfo += " SL:" + selectedTile.getSkyLightLevel() + " BL:" + selectedTile.getBlockLightLevel() + " FL:" + selectedTile.getFinalLightLevel();
                        if(selectedTile.hasTorch()) selectedInfo += " (TORCH)";
                    }
                    debugLines.add(selectedInfo);
                }
                if (cameraManager != null) debugLines.add(String.format("Camera: (%.1f, %.1f) Zoom: %.2f", cameraManager.getCameraX(), cameraManager.getCameraY(), cameraManager.getZoom()));
                debugLines.add("Toggle Torch: L | Dig: J | Elev: Q/E");
                debugLines.add("Center Cam: C | Regen Map: G | Debug: F5");

                renderer.renderDebugOverlay(10f, 10f, 550f, 200f, debugLines);
            }
        }
    }

    /**
     * Called when a full map regeneration is requested (e.g., by pressing 'G').
     * This regenerates map terrain and reinitializes lighting.
     */
    public void requestFullMapRegeneration() {
        System.out.println("Game: Full map regeneration requested.");
        if (map != null) {
            map.generateMap(); // This now also re-initializes lighting via LightManager
        }
        if (player != null && map != null) {
            player.setPosition(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
            player.setPath(null); // Clear any existing path
            if (inputHandler != null) {
                inputHandler.setSelectedTile(player.getTileCol(), player.getTileRow());
            }
            if (cameraManager != null) {
                cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
            }
        }
        if (renderer != null) {
            System.out.println("Game: Requesting FULL map geometry upload to renderer after regeneration.");
            renderer.uploadTileMapGeometry(); // Upload all chunks
        }
        // Ensure sky light is correctly set for the current time after regeneration
        updateSkyLightBasedOnTimeOfDay();
        System.out.println("Game: Full map regeneration processing complete.");
    }

    /**
     * Called when a single tile's properties change in a way that might affect rendering
     * but not necessarily lighting for the entire map (e.g., selection highlight, minor texture change).
     * If lighting is affected, LightManager will mark chunks dirty.
     * @param row The row of the tile.
     * @param col The column of the tile.
     */
    public void requestTileRenderUpdate(int row, int col) {
        // This method might be less used if LightManager handles dirty chunks directly.
        // However, it can be kept for non-lighting related visual updates if needed.
        if (renderer != null) {
            // System.out.println("Game: Requesting render update for tile: (" + row + ", " + col + ")");
            renderer.updateChunkContainingTile(row, col); // This tells renderer to rebuild a specific chunk
        }
    }


    private boolean showDebugOverlay = true; // Start with debug overlay on

    public void toggleShowDebugOverlay() {
        this.showDebugOverlay = !this.showDebugOverlay;
    }

    private void cleanup() {
        System.out.println("Game cleanup initiated...");
        if (renderer != null) renderer.cleanup();
        // LightManager cleanup if it holds resources (not in this version)
        System.out.println("Game cleanup complete.");
    }
}
