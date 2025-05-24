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
    private double lastFrameTime; // Single declaration of lastFrameTime

    private final long window;
    private InputHandler inputHandler;
    private MouseHandler mouseHandler;
    private final CameraManager cameraManager;
    private final Renderer renderer;
    private final Map map;
    private final PlayerModel player;
    private final LightManager lightManager;

    private double pseudoTimeOfDay = 0.0005;
    private final double DAY_NIGHT_CYCLE_SPEED = 0.05;
    private byte currentGlobalSkyLight;

    private byte lastUpdatedSkyLightValue;
    private static final int SKY_LIGHT_UPDATE_THRESHOLD = 2;

    // --- FPS Counter Variables ---
    private int framesRenderedThisSecond = 0;
    private double timeAccumulatorForFps = 0.0;
    private double displayedFps = 0.0;
    // --- End FPS Counter Variables ---


    public Game(long window, int initialFramebufferWidth, int initialScreenHeight) { // Corrected parameter name
        this.window = window;

        map = new Map(MAP_WIDTH, MAP_HEIGHT);
        lightManager = map.getLightManager();
        player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
        cameraManager = new CameraManager(initialFramebufferWidth, initialScreenHeight, map.getWidth(), map.getHeight()); // Corrected parameter name
        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());

        inputHandler = new InputHandler(window, cameraManager, map, player, this);
        inputHandler.registerCallbacks(this::requestFullMapRegeneration);

        renderer = new Renderer(cameraManager, map, player, inputHandler);
        mouseHandler = new MouseHandler(window, cameraManager, map, inputHandler, player);

        glfwSetFramebufferSizeCallback(window, (win, fbW, fbH) -> {
            if (cameraManager != null) cameraManager.updateScreenSize(fbW, fbH);
            if (renderer != null) renderer.onResize(fbW, fbH);
        });

        if (renderer != null) renderer.onResize(initialFramebufferWidth, initialScreenHeight); // Corrected parameter name

        byte initialSkyLight = calculateSkyLightForTime(pseudoTimeOfDay);
        currentGlobalSkyLight = initialSkyLight;
        lastUpdatedSkyLightValue = initialSkyLight;
        lightManager.updateGlobalSkyLight(currentGlobalSkyLight);

        if (lightManager != null) {
            for(int i=0; i < 100; i++) {
                lightManager.processLightQueuesIncrementally();
                Set<LightManager.ChunkCoordinate> dirtyChunks = lightManager.getDirtyChunksAndClear();
                if (!dirtyChunks.isEmpty()) {
                    for (LightManager.ChunkCoordinate coord : dirtyChunks) {
                        if (renderer != null) {
                            renderer.updateChunkByGridCoords(coord.chunkX, coord.chunkY);
                        }
                    }
                } else if (i > 10) {
                    break;
                }
            }
        }
        System.out.println("Game: Initial Sky light set to " + currentGlobalSkyLight);
        System.out.println("Game components initialized.");
    }

    private void initOpenGL() {
        System.out.println("Game.initOpenGL() - OpenGL version from context: " + glGetString(GL_VERSION));
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        System.out.println("Game.initOpenGL() - Depth Test Enabled. Depth Func: GL_LEQUAL");
    }

    public void gameLoop() {
        initOpenGL();
        lastFrameTime = glfwGetTime();
        System.out.println("Entering game loop...");
        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            double deltaTime = currentTime - lastFrameTime;
            lastFrameTime = currentTime;
            if (deltaTime > 0.1) deltaTime = 0.1;

            timeAccumulatorForFps += deltaTime;
            framesRenderedThisSecond++;
            if (timeAccumulatorForFps >= 1.0) {
                displayedFps = (double)framesRenderedThisSecond / timeAccumulatorForFps;
                framesRenderedThisSecond = 0;
                timeAccumulatorForFps -= 1.0;
            }

            glfwPollEvents();
            updateGameLogic(deltaTime);
            renderGame();
            glfwSwapBuffers(window);
        }
        System.out.println("Game loop exited.");
        cleanup();
    }

    private byte calculateSkyLightForTime(double time) {
        byte newSkyLight;
        if (time >= 0.0 && time < 0.40) { // Day
            newSkyLight = SKY_LIGHT_DAY;
        } else if (time >= 0.40 && time < 0.60) { // Dusk (now longer)
            // Phase goes from 0.0 (start of dusk) to 1.0 (end of dusk)
            float phase = (float) ((time - 0.40) / 0.20); // Duration of dusk is 0.20 (0.60 - 0.40)
            newSkyLight = (byte) (SKY_LIGHT_DAY - phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        } else if (time >= 0.60 && time < 0.80) { // Night (now shorter)
            newSkyLight = SKY_LIGHT_NIGHT;
        } else { // Dawn (time >= 0.80 && time < 1.0) (now longer)
            // Phase goes from 0.0 (start of dawn) to 1.0 (end of dawn)
            float phase = (float) ((time - 0.80) / 0.20); // Duration of dawn is 0.20 (1.00 - 0.80)
            newSkyLight = (byte) (SKY_LIGHT_NIGHT + phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        }
        return (byte)Math.max(0, Math.min(MAX_LIGHT_LEVEL, newSkyLight));
    }


    private void updateSkyLightBasedOnTimeOfDay(double deltaTime) {
        byte newCalculatedSkyLight = calculateSkyLightForTime(pseudoTimeOfDay);

        boolean skyLightActuallyChanged = (newCalculatedSkyLight != currentGlobalSkyLight);
        currentGlobalSkyLight = newCalculatedSkyLight;

        if (Math.abs(newCalculatedSkyLight - lastUpdatedSkyLightValue) >= SKY_LIGHT_UPDATE_THRESHOLD ||
                (skyLightActuallyChanged && newCalculatedSkyLight == SKY_LIGHT_DAY) ||
                (skyLightActuallyChanged && newCalculatedSkyLight == SKY_LIGHT_NIGHT) ) {

            lightManager.updateGlobalSkyLight(newCalculatedSkyLight);
            lastUpdatedSkyLightValue = newCalculatedSkyLight;
            System.out.println("Game: Sky light FULL UPDATE to " + newCalculatedSkyLight + " based on time: " + pseudoTimeOfDay);
        }
    }

    private void updateGameLogic(double deltaTime) {
        pseudoTimeOfDay += deltaTime * DAY_NIGHT_CYCLE_SPEED;
        if (pseudoTimeOfDay >= 1.0) {
            pseudoTimeOfDay -= 1.0;
        }
        updateSkyLightBasedOnTimeOfDay(deltaTime);

        if (inputHandler != null) inputHandler.handleContinuousInput(deltaTime);
        if (player != null) player.update(deltaTime);
        if (cameraManager != null) cameraManager.update(deltaTime);

        if (lightManager != null) {
            lightManager.processLightQueuesIncrementally();
        }

        Set<LightManager.ChunkCoordinate> dirtyChunks = lightManager.getDirtyChunksAndClear();
        if (!dirtyChunks.isEmpty()) {
            for (LightManager.ChunkCoordinate coord : dirtyChunks) {
                if (renderer != null) {
                    renderer.updateChunkByGridCoords(coord.chunkX, coord.chunkY);
                }
            }
        }
    }

    private void renderGame() {
        float rSky, gSky, bSky;
        if (currentGlobalSkyLight > SKY_LIGHT_NIGHT + (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT) / 2) {
            rSky = 0.5f; gSky = 0.7f; bSky = 1.0f;
        } else {
            rSky = 0.05f; gSky = 0.05f; bSky = 0.15f;
        }
        glClearColor(rSky, gSky, bSky, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (renderer != null) {
            renderer.render();

            if (this.showDebugOverlay) {
                List<String> debugLines = new ArrayList<>(); // debugLines is defined here
                debugLines.add(String.format("FPS: %.1f", displayedFps)); // Now this line is in scope
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

                renderer.renderDebugOverlay(10f, 10f, 900f, 170f, debugLines);
            }
        }
    }
    public void requestFullMapRegeneration() {
        System.out.println("Game: Full map regeneration requested.");
        if (map != null) {
            map.generateMap();
        }
        if (player != null && map != null) {
            player.setPosition(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
            player.setPath(null);
            if (inputHandler != null) {
                inputHandler.setSelectedTile(player.getTileCol(), player.getTileRow());
            }
            if (cameraManager != null) {
                cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
            }
        }

        byte newInitialSkyLight = calculateSkyLightForTime(pseudoTimeOfDay);
        currentGlobalSkyLight = newInitialSkyLight;
        lastUpdatedSkyLightValue = newInitialSkyLight;
        lightManager.updateGlobalSkyLight(currentGlobalSkyLight);
        if (lightManager != null) {
            for(int i=0; i < 100; i++) {
                lightManager.processLightQueuesIncrementally();
                Set<LightManager.ChunkCoordinate> dirtyChunks = lightManager.getDirtyChunksAndClear();
                if (!dirtyChunks.isEmpty()) {
                    for (LightManager.ChunkCoordinate coord : dirtyChunks) {
                        if (renderer != null) renderer.updateChunkByGridCoords(coord.chunkX, coord.chunkY);
                    }
                } else if (i > 10) break;
            }
        }
        System.out.println("Game: Sky light RE-INITIALIZED to " + currentGlobalSkyLight + " after map regeneration.");

        if (renderer != null) {
            System.out.println("Game: Requesting FULL map geometry upload to renderer after regeneration.");
            renderer.uploadTileMapGeometry();
        }
        System.out.println("Game: Full map regeneration processing complete.");
    }
    public void requestTileRenderUpdate(int row, int col) {
        if (renderer != null) {
            renderer.updateChunkContainingTile(row, col);
        }
    }
    private boolean showDebugOverlay = true;
    public void toggleShowDebugOverlay() {
        this.showDebugOverlay = !this.showDebugOverlay;
    }
    private void cleanup() {
        System.out.println("Game cleanup initiated...");
        if (renderer != null) renderer.cleanup();
        System.out.println("Game cleanup complete.");
    }
}
