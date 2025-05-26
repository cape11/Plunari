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
    private final LightManager lightManager;

    private double pseudoTimeOfDay = 0.0005;
    // DAY_NIGHT_CYCLE_SPEED is now in Constants.java
    private byte currentGlobalSkyLight;

    private byte lastUpdatedSkyLightValue;
    // SKY_LIGHT_UPDATE_THRESHOLD is now in Constants.java
    private long lastGlobalSkyLightUpdateTimeMs = 0; // PRIORITY B: For cooldown

    // --- FPS Counter Variables ---
    private int framesRenderedThisSecond = 0;
    private double timeAccumulatorForFps = 0.0;
    private double displayedFps = 0.0;
    // --- End FPS Counter Variables ---


    public Game(long window, int initialFramebufferWidth, int initialScreenHeight) {
        this.window = window;

        map = new Map(MAP_WIDTH, MAP_HEIGHT);
        lightManager = map.getLightManager();
        player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
        cameraManager = new CameraManager(initialFramebufferWidth, initialScreenHeight, map.getWidth(), map.getHeight());
        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());

        inputHandler = new InputHandler(window, cameraManager, map, player, this);
        inputHandler.registerCallbacks(this::requestFullMapRegeneration);

        renderer = new Renderer(cameraManager, map, player, inputHandler); // Renderer created after InputHandler
        mouseHandler = new MouseHandler(window, cameraManager, map, inputHandler, player);


        glfwSetFramebufferSizeCallback(window, (win, fbW, fbH) -> {
            if (cameraManager != null) cameraManager.updateScreenSize(fbW, fbH);
            if (renderer != null) renderer.onResize(fbW, fbH);
        });

        if (renderer != null) renderer.onResize(initialFramebufferWidth, initialScreenHeight);

        byte initialSkyLight = calculateSkyLightForTime(pseudoTimeOfDay);
        currentGlobalSkyLight = initialSkyLight;
        lastUpdatedSkyLightValue = initialSkyLight;
        lightManager.updateGlobalSkyLight(currentGlobalSkyLight);
        lastGlobalSkyLightUpdateTimeMs = System.currentTimeMillis(); // Initialize cooldown timer

        if (lightManager != null) {
            for(int i=0; i < 100; i++) { // Initial full propagation can take a bit
                lightManager.processLightQueuesIncrementally();
                Set<LightManager.ChunkCoordinate> dirtyChunks = lightManager.getDirtyChunksAndClear();
                if (!dirtyChunks.isEmpty()) {
                    for (LightManager.ChunkCoordinate coord : dirtyChunks) {
                        if (renderer != null) {
                            renderer.updateChunkByGridCoords(coord.chunkX, coord.chunkY);
                        }
                    }
                } else if (i > 10 && lightManager.isQueueEmpty()) { // Added a check if queues are empty
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
            if (deltaTime > 0.1) deltaTime = 0.1; // Cap deltaTime to prevent spiral of death

            // FPS counter
            timeAccumulatorForFps += deltaTime;
            framesRenderedThisSecond++;
            if (timeAccumulatorForFps >= 1.0) {
                displayedFps = (double)framesRenderedThisSecond / timeAccumulatorForFps;
                framesRenderedThisSecond = 0;
                timeAccumulatorForFps -= 1.0; // Subtract 1.0 instead of setting to 0 for more accuracy
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
        // Current logic seems fine, ensuring it returns a value between SKY_LIGHT_NIGHT and SKY_LIGHT_DAY
        if (time >= 0.0 && time < 0.40) { // Day
            newSkyLight = SKY_LIGHT_DAY;
        } else if (time >= 0.40 && time < 0.60) { // Dusk
            float phase = (float) ((time - 0.40) / 0.20);
            newSkyLight = (byte) (SKY_LIGHT_DAY - phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        } else if (time >= 0.60 && time < 0.80) { // Night
            newSkyLight = SKY_LIGHT_NIGHT;
        } else { // Dawn (time >= 0.80 && time < 1.0)
            float phase = (float) ((time - 0.80) / 0.20);
            newSkyLight = (byte) (SKY_LIGHT_NIGHT + phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        }
        return (byte)Math.max(SKY_LIGHT_NIGHT, Math.min(SKY_LIGHT_DAY, newSkyLight)); // Clamp to min/max defined sky lights
    }


    private void updateSkyLightBasedOnTimeOfDay(double deltaTime) {
        byte newCalculatedSkyLight = calculateSkyLightForTime(pseudoTimeOfDay);
        boolean skyLightValueTechnicallyChanged = (newCalculatedSkyLight != currentGlobalSkyLight);
        currentGlobalSkyLight = newCalculatedSkyLight; // Update current internal value immediately

        long currentTimeMs = System.currentTimeMillis();

        // PRIORITY B: Cooldown and threshold logic
        if (currentTimeMs - lastGlobalSkyLightUpdateTimeMs >= GLOBAL_SKY_LIGHT_UPDATE_COOLDOWN_MS) {
            if (Math.abs(newCalculatedSkyLight - lastUpdatedSkyLightValue) >= SKY_LIGHT_UPDATE_THRESHOLD ||
                    (skyLightValueTechnicallyChanged && (newCalculatedSkyLight == SKY_LIGHT_DAY || newCalculatedSkyLight == SKY_LIGHT_NIGHT))) {

                System.out.println("Game: Sky light UPDATE TRIGGERED. New: " + newCalculatedSkyLight + ", Oldpropagated: " + lastUpdatedSkyLightValue + ", Time: " + String.format("%.3f",pseudoTimeOfDay));
                if (lightManager != null) {
                    lightManager.updateGlobalSkyLight(newCalculatedSkyLight);
                }
                lastUpdatedSkyLightValue = newCalculatedSkyLight;
                lastGlobalSkyLightUpdateTimeMs = currentTimeMs; // Reset cooldown timer
            }
        }
    }

    private void updateGameLogic(double deltaTime) {
        pseudoTimeOfDay += deltaTime * DAY_NIGHT_CYCLE_SPEED;
        if (pseudoTimeOfDay >= 1.0) {
            pseudoTimeOfDay -= 1.0;
        }
        updateSkyLightBasedOnTimeOfDay(deltaTime); // Sky light updates are now less frequent

        if (inputHandler != null) inputHandler.handleContinuousInput(deltaTime);
        if (player != null) player.update(deltaTime);
        if (cameraManager != null) cameraManager.update(deltaTime);

        int lightProcessingCycles = 0;
        if (lightManager != null) {
            // Process queues a few times to catch up on changes, but not excessively
            for (int i=0; i< 5 && !lightManager.isQueueEmpty() ; i++){ // Limit iterations
                lightManager.processLightQueuesIncrementally();
                lightProcessingCycles++;
            }
        }

        Set<LightManager.ChunkCoordinate> dirtyChunks = lightManager.getDirtyChunksAndClear();
        if (!dirtyChunks.isEmpty()) {
            // System.out.println("Game update: " + dirtyChunks.size() + " dirty chunks to update. Light cycles: " + lightProcessingCycles);
            for (LightManager.ChunkCoordinate coord : dirtyChunks) {
                if (renderer != null) {
                    renderer.updateChunkByGridCoords(coord.chunkX, coord.chunkY);
                }
            }
        }
    }

    private void renderGame() {
        float rSky, gSky, bSky;
        // Simplified sky color based on currentGlobalSkyLight relative to night/day
        float lightRatio = (float)(currentGlobalSkyLight - SKY_LIGHT_NIGHT) / (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT);
        lightRatio = Math.max(0, Math.min(1, lightRatio)); // clamp

        float dayR = 0.5f, dayG = 0.7f, dayB = 1.0f;
        float nightR = 0.05f, nightG = 0.05f, nightB = 0.15f;

        rSky = nightR + (dayR - nightR) * lightRatio;
        gSky = nightG + (dayG - nightG) * lightRatio;
        bSky = nightB + (dayB - nightB) * lightRatio;

        glClearColor(rSky, gSky, bSky, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (renderer != null) {
            renderer.render();

            if (this.showDebugOverlay) {
                List<String> debugLines = new ArrayList<>();
                debugLines.add(String.format("FPS: %.1f", displayedFps));
                debugLines.add(String.format("Time: %.3f, SkyLight: %d (Calc: %d, LastProp: %d)", pseudoTimeOfDay, currentGlobalSkyLight, calculateSkyLightForTime(pseudoTimeOfDay), lastUpdatedSkyLightValue));
                if (player != null) debugLines.add("Player: (" + player.getTileRow() + ", " + player.getTileCol() + ") Action: " + player.getCurrentAction() + " Dir: " + player.getCurrentDirection());
                if (inputHandler != null && map != null) {
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
                debugLines.add("Toggle Torch: L | Dig: J | Elev: Q/E | Levitate: F");
                debugLines.add("Center Cam: C | Regen Map: G | Debug: F5");
                if (lightManager != null) debugLines.add("LightQ Empty: " + lightManager.isQueueEmpty() + " (S:" +lightManager.getSkyQueueSize() + " SR:"+lightManager.getSkyRemovalQueueSize() + " B:"+lightManager.getBlockQueueSize() + " BR:"+ lightManager.getBlockRemovalQueueSize()+")" );


                renderer.renderDebugOverlay(10f, 10f, 900f, 200f, debugLines); // Increased height for more lines
            }
        }
    }
    public void requestFullMapRegeneration() {
        System.out.println("Game: Full map regeneration requested.");
        if (map != null) {
            map.generateMap(); // This will internally mark all chunks dirty via LightManager
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

        // Re-initialize global sky light based on current time after map regen
        byte newInitialSkyLight = calculateSkyLightForTime(pseudoTimeOfDay);
        currentGlobalSkyLight = newInitialSkyLight;
        lastUpdatedSkyLightValue = newInitialSkyLight; // Critical to set this
        lastGlobalSkyLightUpdateTimeMs = System.currentTimeMillis(); // Reset cooldown

        if (lightManager != null) {
            lightManager.updateGlobalSkyLight(currentGlobalSkyLight); // This will re-flood sky light
            // Process light queues extensively after regeneration
            for(int i=0; i < 200 && !lightManager.isQueueEmpty(); i++) { // Allow more processing
                lightManager.processLightQueuesIncrementally();
                Set<LightManager.ChunkCoordinate> dirtyChunks = lightManager.getDirtyChunksAndClear();
                if (!dirtyChunks.isEmpty()) {
                    for (LightManager.ChunkCoordinate coord : dirtyChunks) {
                        if (renderer != null) renderer.updateChunkByGridCoords(coord.chunkX, coord.chunkY);
                    }
                } else if (i > 20 && lightManager.isQueueEmpty()) {
                    break; // Optimization: stop if queues are empty and some processing done
                }
            }
        }
        System.out.println("Game: Sky light RE-INITIALIZED to " + currentGlobalSkyLight + " after map regeneration.");

        if (renderer != null) {
            System.out.println("Game: Requesting FULL map geometry upload to renderer after regeneration.");
            renderer.uploadTileMapGeometry(); // This re-uploads all chunk geometry
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
        // mouseHandler and inputHandler are tied to GLFW window, callbacks are freed by Main
        System.out.println("Game cleanup complete.");
    }
}