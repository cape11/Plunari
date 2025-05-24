package org.isogame.game;

import org.isogame.input.InputHandler;
import org.isogame.input.MouseHandler;
import org.isogame.camera.CameraManager;
import org.isogame.map.LightManager;
import org.isogame.map.Map;
import org.isogame.map.ChunkManager;
import org.isogame.map.ChunkCoordinate;
import org.isogame.map.ChunkData;
import org.isogame.render.Renderer;
import org.isogame.entitiy.PlayerModel;
import org.isogame.tile.Tile;

import java.util.ArrayList;
import java.util.HashSet;
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
    private final ChunkManager chunkManager;

    private double pseudoTimeOfDay = 0.25;
    private final double DAY_NIGHT_CYCLE_SPEED = 0.05;
    private byte currentGlobalSkyLight;

    private double chunkManagementTimer = 0.0;
    private static final double CHUNK_MANAGEMENT_INTERVAL = 0.25; // Seconds

    private static final int MAX_LIGHTING_ITERATIONS_PER_TICK = 4; // Max times to loop light queue processing per game tick


    public Game(long window, int initialFramebufferWidth, int initialFramebufferHeight) {
        this.window = window;

        map = new Map();
        chunkManager = map.getChunkManager();
        lightManager = map.getLightManager();
        player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());

        manageChunks();

        int conceptualMapSizeForCameraInit = CHUNK_LOAD_RADIUS * 2 * CHUNK_SIZE_TILES;
        cameraManager = new CameraManager(initialFramebufferWidth, initialFramebufferHeight, conceptualMapSizeForCameraInit, conceptualMapSizeForCameraInit);
        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());

        inputHandler = new InputHandler(window, cameraManager, map, player, this);
        inputHandler.registerCallbacks(this::requestFullMapRegeneration);

        renderer = new Renderer(cameraManager, map, player, inputHandler, chunkManager);
        mouseHandler = new MouseHandler(window, cameraManager, map, inputHandler, player);

        glfwSetFramebufferSizeCallback(window, (win, fbW, fbH) -> {
            if (cameraManager != null) cameraManager.updateScreenSize(fbW, fbH);
            if (renderer != null) renderer.onResize(fbW, fbH);
        });

        if (renderer != null) renderer.onResize(initialFramebufferWidth, initialFramebufferHeight);

        // Initialize sky light and process queue
        byte initialSkyLight = calculateSkyLightValue(this.pseudoTimeOfDay);
        this.currentGlobalSkyLight = initialSkyLight; // Set initial before any processing
        this.lightManager.updateGlobalSkyLightForSpecificChunks(this.currentGlobalSkyLight, this.renderer.getRenderedChunkCoordinates());
        processInitialSkyLightQueue();


        System.out.println("Game components initialized. Player at: " + player.getTileRow() + "," + player.getTileCol());
    }

    private void processInitialSkyLightQueue() {
        // This initial processing uses the iterative approach as well
        int iterations = 0;
        while(iterations < MAX_LIGHTING_ITERATIONS_PER_TICK && !lightManager.isSkyLightQueueEmpty()){
            Set<ChunkCoordinate> initialSkyLightChunks = lightManager.getSkyLightRecalculationQueueAndClear();
            if (initialSkyLightChunks.isEmpty()) break;

            // System.out.println("Initial sky light processing pass " + (iterations + 1) + " for " + initialSkyLightChunks.size() + " chunks.");
            for (ChunkCoordinate coord : initialSkyLightChunks) {
                lightManager.initializeLightingForNewChunk(coord.chunkX, coord.chunkY, currentGlobalSkyLight);
                if (renderer != null) {
                    renderer.updateRenderChunk(coord);
                }
            }
            iterations++;
        }
        if (!lightManager.isSkyLightQueueEmpty()) {
            System.out.println("Warning: Initial sky light processing hit max iterations, " + lightManager.getSkyLightQueueSize() + " updates pending.");
        }
        // System.out.println("Initial sky light processing complete after " + iterations + " iterations.");
    }


    private void initOpenGL() {
        System.out.println("Game.initOpenGL() - OpenGL version from context: " + glGetString(GL_VERSION));
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
    }

    public void gameLoop() {
        initOpenGL();
        lastFrameTime = glfwGetTime();
        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            double deltaTime = currentTime - lastFrameTime;
            lastFrameTime = currentTime;
            if (deltaTime > 0.1) deltaTime = 0.1;

            glfwPollEvents();
            updateGameLogic(deltaTime);
            renderGame();
            glfwSwapBuffers(window);
        }
        cleanup();
    }

    private void manageChunks() {
        if (player == null || chunkManager == null || renderer == null || lightManager == null) return;

        int playerChunkX = Map.worldToChunkCoord(player.getTileCol());
        int playerChunkY = Map.worldToChunkCoord(player.getTileRow());

        Set<ChunkCoordinate> currentlyRenderedCoords = renderer.getRenderedChunkCoordinates();
        Set<ChunkCoordinate> requiredCoords = new HashSet<>();

        for (int dy = -CHUNK_LOAD_RADIUS; dy <= CHUNK_LOAD_RADIUS; dy++) {
            for (int dx = -CHUNK_LOAD_RADIUS; dx <= CHUNK_LOAD_RADIUS; dx++) {
                requiredCoords.add(new ChunkCoordinate(playerChunkX + dx, playerChunkY + dy));
            }
        }

        for (ChunkCoordinate coord : requiredCoords) {
            if (!currentlyRenderedCoords.contains(coord)) {
                ChunkData chunkData = chunkManager.getChunk(coord.chunkX, coord.chunkY);
                if (chunkData != null) {
                    renderer.addRenderChunk(coord, chunkData);
                    lightManager.enqueueSkyLightRecalculationForChunk(coord.chunkX, coord.chunkY);
                }
            }
        }

        Set<ChunkCoordinate> toUnload = new HashSet<>();
        int unloadDist = CHUNK_LOAD_RADIUS + CHUNK_UNLOAD_RADIUS_OFFSET;
        for (ChunkCoordinate loadedCoord : currentlyRenderedCoords) {
            if (Math.abs(loadedCoord.chunkX - playerChunkX) > unloadDist ||
                    Math.abs(loadedCoord.chunkY - playerChunkY) > unloadDist) {
                toUnload.add(loadedCoord);
            }
        }

        for (ChunkCoordinate coord : toUnload) {
            renderer.removeRenderChunk(coord);
            chunkManager.unloadChunk(coord.chunkX, coord.chunkY);
            lightManager.onChunkUnloaded(coord.chunkX, coord.chunkY);
        }
    }

    private byte calculateSkyLightValue(double timeOfDay) {
        byte newSkyLightVal;
        if (timeOfDay >= 0.0 && timeOfDay < 0.45) { newSkyLightVal = SKY_LIGHT_DAY; } // Day
        else if (timeOfDay >= 0.45 && timeOfDay < 0.55) { // Sunset
            float phase = (float) ((timeOfDay - 0.45) / 0.10);
            newSkyLightVal = (byte) (SKY_LIGHT_DAY - phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        } else if (timeOfDay >= 0.55 && timeOfDay < 0.95) { newSkyLightVal = SKY_LIGHT_NIGHT; } // Night
        else { // Sunrise (timeOfDay >= 0.95)
            float phase = (float) ((timeOfDay - 0.95) / 0.05);
            newSkyLightVal = (byte) (SKY_LIGHT_NIGHT + phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        }
        return (byte)Math.max(0, Math.min(MAX_LIGHT_LEVEL, newSkyLightVal));
    }

    private void updateSkyLightBasedOnTimeOfDay() {
        byte newCalculatedSkyLight = calculateSkyLightValue(pseudoTimeOfDay);

        if (newCalculatedSkyLight != currentGlobalSkyLight) {
            currentGlobalSkyLight = newCalculatedSkyLight;
            // System.out.println("Global skylight changed to " + currentGlobalSkyLight + ". Updating rendered chunks.");
            if (renderer != null && lightManager != null) {
                Set<ChunkCoordinate> renderedChunks = renderer.getRenderedChunkCoordinates();
                if (!renderedChunks.isEmpty()) {
                    lightManager.updateGlobalSkyLightForSpecificChunks(currentGlobalSkyLight, renderedChunks);
                }
            }
        }
    }


    private void updateGameLogic(double deltaTime) {
        pseudoTimeOfDay += deltaTime * DAY_NIGHT_CYCLE_SPEED;
        if (pseudoTimeOfDay >= 1.0) pseudoTimeOfDay -= 1.0;
        updateSkyLightBasedOnTimeOfDay(); // This will now trigger mass update more reliably

        if (inputHandler != null) inputHandler.handleContinuousInput(deltaTime);
        if (player != null) player.update(deltaTime);
        if (cameraManager != null) cameraManager.update(deltaTime);

        chunkManagementTimer += deltaTime;
        if (chunkManagementTimer >= CHUNK_MANAGEMENT_INTERVAL) {
            manageChunks();
            chunkManagementTimer = 0.0;
        }

        // Iterative processing of skylight queue
        int iterations = 0;
        while (iterations < MAX_LIGHTING_ITERATIONS_PER_TICK && !lightManager.isSkyLightQueueEmpty()) {
            Set<ChunkCoordinate> dirtySkyLightChunks = lightManager.getSkyLightRecalculationQueueAndClear();
            if (dirtySkyLightChunks.isEmpty()) break;

            for (ChunkCoordinate coord : dirtySkyLightChunks) {
                lightManager.initializeLightingForNewChunk(coord.chunkX, coord.chunkY, currentGlobalSkyLight);
                if (renderer != null) {
                    renderer.updateRenderChunk(coord);
                }
            }
            iterations++;
        }
        // if (iterations > 0) System.out.println("Lighting iterations this tick: " + iterations);
        if (iterations == MAX_LIGHTING_ITERATIONS_PER_TICK && !lightManager.isSkyLightQueueEmpty()) {
            // System.out.println("Warning: Max light iterations reached, " + lightManager.getSkyLightQueueSize() + " light updates pending for next tick.");
        }


        Set<ChunkCoordinate> dirtyBlockLightChunks = lightManager.getDirtyChunksAndClear();
        if (!dirtyBlockLightChunks.isEmpty()) {
            for (ChunkCoordinate coord : dirtyBlockLightChunks) {
                if (renderer != null) {
                    renderer.updateRenderChunk(coord); // This updates chunk mesh based on new light
                }
            }
        }
    }

    private void renderGame() {
        float rSky, gSky, bSky;
        // Smoother sky color transition
        float lightRatio = (float) currentGlobalSkyLight / MAX_LIGHT_LEVEL; // 0.0 (darkest) to 1.0 (brightest)

        float dayR = 0.5f, dayG = 0.7f, dayB = 1.0f;
        float nightR = 0.02f, nightG = 0.02f, nightB = 0.1f; // Darker night

        // Interpolate between night and day colors
        rSky = nightR + (dayR - nightR) * lightRatio;
        gSky = nightG + (dayG - nightG) * lightRatio;
        bSky = nightB + (dayB - nightB) * lightRatio;

        glClearColor(Math.max(0,rSky), Math.max(0,gSky), Math.max(0,bSky), 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (renderer != null) {
            renderer.render();

            if (this.showDebugOverlay) {
                List<String> debugLines = new ArrayList<>();
                debugLines.add(String.format("Time: %.2f, SkyLight: %d", pseudoTimeOfDay, currentGlobalSkyLight));
                if (player != null) debugLines.add("Player: (" + player.getTileRow() + ", " + player.getTileCol() + ") Chunk: (" + Map.worldToChunkCoord(player.getTileCol()) + "," + Map.worldToChunkCoord(player.getTileRow()) + ")");
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
                debugLines.add("Chunks Loaded(Logic): " + chunkManager.getLoadedChunksCount() + " Rendered(GPU): " + renderer.getRenderedChunkCount());
                debugLines.add("LightQueue: " + lightManager.getSkyLightQueueSize()); // Display queue size
                debugLines.add("Toggle Torch: L | Dig: J | Elev: Q/E | Debug: F5 | Regen: G");
                renderer.renderDebugOverlay(10f, 10f, 600f, 240f, debugLines); // Increased height for new line
            }
        }
    }

    public void requestFullMapRegeneration() {
        System.out.println("Game: Full map regeneration requested.");
        if (chunkManager != null && renderer != null) {
            Set<ChunkCoordinate> toClear = new HashSet<>(renderer.getRenderedChunkCoordinates());
            System.out.println("Clearing " + toClear.size() + " rendered chunks for regeneration.");
            for (ChunkCoordinate coord : toClear) {
                renderer.removeRenderChunk(coord);
                chunkManager.unloadChunk(coord.chunkX, coord.chunkY);
                lightManager.onChunkUnloaded(coord.chunkX, coord.chunkY);
            }
        }
        if (player != null && inputHandler != null) {
            inputHandler.setSelectedTile(player.getTileCol(), player.getTileRow());
        }
        if (player != null && cameraManager != null) {
            cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
        }

        manageChunks();

        // Force initial full update of sky light and process queue
        this.currentGlobalSkyLight = calculateSkyLightValue(this.pseudoTimeOfDay);
        if (renderer != null && lightManager != null) {
            lightManager.updateGlobalSkyLightForSpecificChunks(this.currentGlobalSkyLight, renderer.getRenderedChunkCoordinates());
        }
        processInitialSkyLightQueue(); // Re-process for new chunks

        System.out.println("Game: Chunk regeneration around player complete.");
    }

    private boolean showDebugOverlay = true;
    public void toggleShowDebugOverlay() { this.showDebugOverlay = !this.showDebugOverlay; }

    private void cleanup() {
        System.out.println("Game cleanup initiated...");
        if (renderer != null) renderer.cleanup();
        System.out.println("Game cleanup complete.");
    }
}
