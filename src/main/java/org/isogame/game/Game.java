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
    private final double DAY_NIGHT_CYCLE_SPEED = 0.01; // Adjusted for potentially faster observation
    private byte currentGlobalSkyLight;

    private double chunkManagementTimer = 0.0;
    private static final double CHUNK_MANAGEMENT_INTERVAL = 0.25;

    // Max chunks for full skylight update per tick.
    // Performance Note: Overall game performance, especially lag with many chunks,
    // is also heavily influenced by CHUNK_LOAD_RADIUS in Constants.java.
    // A large radius means more chunks to generate, manage, light, and render.
    private static final int MAX_SKYLIGHT_RECALCS_PER_TICK = 8;


    public Game(long window, int initialFramebufferWidth, int initialFramebufferHeight) {
        this.window = window;

        map = new Map();
        chunkManager = map.getChunkManager();
        lightManager = map.getLightManager();
        player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());

        manageChunks(); // Load initial set of chunks

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

        this.currentGlobalSkyLight = calculateSkyLightValue(this.pseudoTimeOfDay);
        lightManager.updateGlobalSkyLightForAllLoadedChunks(); // Initial lighting queue for all loaded chunks

        System.out.println("Game components initialized. Player at: " + player.getTileRow() + "," + player.getTileCol());
        System.out.println("Initial global skylight: " + currentGlobalSkyLight);
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
            if (deltaTime > 0.1) deltaTime = 0.1; // Cap delta time

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
        timeOfDay = timeOfDay - Math.floor(timeOfDay); // Ensure [0,1)
        byte newSkyLightVal;
        if (timeOfDay >= 0.0 && timeOfDay < 0.45) { newSkyLightVal = SKY_LIGHT_DAY; }
        else if (timeOfDay >= 0.45 && timeOfDay < 0.55) { float phase = (float)((timeOfDay - 0.45) / 0.10); newSkyLightVal = (byte)(SKY_LIGHT_DAY - phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT)); }
        else if (timeOfDay >= 0.55 && timeOfDay < 0.95) { newSkyLightVal = SKY_LIGHT_NIGHT; }
        else { float phase = (float)((timeOfDay - 0.95) / 0.05); newSkyLightVal = (byte)(SKY_LIGHT_NIGHT + phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT)); }
        return (byte)Math.max(0, Math.min(MAX_LIGHT_LEVEL, newSkyLightVal));
    }

    private void updateSkyLightBasedOnTimeOfDay() {
        byte newCalculatedSkyLight = calculateSkyLightValue(pseudoTimeOfDay);
        if (newCalculatedSkyLight != currentGlobalSkyLight) {
            currentGlobalSkyLight = newCalculatedSkyLight;
            if (lightManager != null) {
                lightManager.updateGlobalSkyLightForAllLoadedChunks();
            }
        }
    }

    private void updateGameLogic(double deltaTime) {
        pseudoTimeOfDay += deltaTime * DAY_NIGHT_CYCLE_SPEED;
        updateSkyLightBasedOnTimeOfDay();

        if (inputHandler != null) inputHandler.handleContinuousInput(deltaTime);
        if (player != null) player.update(deltaTime);
        if (cameraManager != null) cameraManager.update(deltaTime);

        chunkManagementTimer += deltaTime;
        if (chunkManagementTimer >= CHUNK_MANAGEMENT_INTERVAL) {
            manageChunks();
            chunkManagementTimer = 0.0;
        }

        // --- Lighting Update Phase ---
        lightManager.resetTickProcessedSet(); // Clear the set of chunks processed for lighting this tick
        int skylightRecalcsDoneThisTick = 0;

        // Get all chunks currently in the main queue for skylight recalculation.
        // This batch represents chunks that were either newly loaded, affected by global changes,
        // or affected by neighbors in previous ticks.
        Set<ChunkCoordinate> currentProcessingBatch = lightManager.getSkyLightRecalculationQueueAsSetAndClear();
        Set<ChunkCoordinate> nextTickProcessingCandidates = new HashSet<>(); // Chunks to defer if limit is hit

        // Iterate while there's work in the current batch and we haven't hit the tick's processing limit.
        while (skylightRecalcsDoneThisTick < MAX_SKYLIGHT_RECALCS_PER_TICK && !currentProcessingBatch.isEmpty()) {
            // Get an arbitrary chunk from the current batch to process
            ChunkCoordinate coordToProcess = currentProcessingBatch.iterator().next();
            currentProcessingBatch.remove(coordToProcess); // Remove it from this tick's immediate batch

            // Double-check if it was somehow processed earlier in this same lighting phase
            // (e.g., if added back by a neighbor processed just before it).
            if (lightManager.wasChunkProcessedThisTick(coordToProcess)) {
                continue;
            }

            // Perform the actual lighting calculation for this chunk.
            // initializeSkylightForChunk will mark coordToProcess as processed for this tick internally.
            Set<ChunkCoordinate> newlyAffectedNeighbors = lightManager.initializeSkylightForChunk(
                    coordToProcess.chunkX, coordToProcess.chunkY, currentGlobalSkyLight
            );
            skylightRecalcsDoneThisTick++; // Increment count *after* processing

            // Handle neighbors affected by the lighting change in coordToProcess.
            for (ChunkCoordinate neighbor : newlyAffectedNeighbors) {
                // Only consider neighbors not already fully processed in this tick.
                if (!lightManager.wasChunkProcessedThisTick(neighbor)) {
                    // Check if we have "budget" to potentially process this neighbor in the current tick.
                    // This is a heuristic: considers recalcs done + remaining in current batch.
                    if (skylightRecalcsDoneThisTick + currentProcessingBatch.size() < MAX_SKYLIGHT_RECALCS_PER_TICK) {
                        currentProcessingBatch.add(neighbor); // Add to current batch for potential processing this tick
                    } else {
                        nextTickProcessingCandidates.add(neighbor); // Defer to next tick's main queue
                    }
                }
            }
        }

        // Any chunks remaining in currentProcessingBatch (if the loop exited due to MAX_SKYLIGHT_RECALCS_PER_TICK)
        // must also be deferred to the next tick.
        nextTickProcessingCandidates.addAll(currentProcessingBatch);

        // Enqueue all deferred chunks back into the LightManager's main queue for subsequent ticks.
        if (!nextTickProcessingCandidates.isEmpty()) {
            lightManager.enqueueSkyLightRecalculationForChunks(nextTickProcessingCandidates);
        }

        // After lighting calculations for this tick are done, get all chunks that became dirty
        // (had light changes) and need their renderer mesh updated.
        Set<ChunkCoordinate> chunksForRendererUpdate = lightManager.getDirtyChunksForRendererAndClear();
        if (renderer != null && !chunksForRendererUpdate.isEmpty()) {
            for (ChunkCoordinate coord : chunksForRendererUpdate) {
                renderer.updateRenderChunk(coord); // Rebuilds the chunk's mesh based on new light values
            }
        }
    }

    private void renderGame() {
        float rSky, gSky, bSky;
        float lightRatio = (float) currentGlobalSkyLight / MAX_LIGHT_LEVEL;
        float dayR = 0.5f, dayG = 0.7f, dayB = 1.0f;
        float nightR = 0.02f, nightG = 0.02f, nightB = 0.1f;
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
                if (inputHandler != null && map != null) {
                    Tile selectedTile = map.getTile(inputHandler.getSelectedRow(), inputHandler.getSelectedCol());
                    String selectedInfo = "Selected: ("+inputHandler.getSelectedRow()+","+inputHandler.getSelectedCol()+")";
                    if (selectedTile != null) {
                        selectedInfo += " Elev: " + selectedTile.getElevation() + " Type: " + selectedTile.getType();
                        selectedInfo += " SL:" + selectedTile.getSkyLightLevel() + " BL:" + selectedTile.getBlockLightLevel() + " FL:" + selectedTile.getFinalLightLevel();
                        if(selectedTile.hasTorch()) selectedInfo += " (TORCH)";
                    } else { selectedInfo += " (No tile data)"; }
                    debugLines.add(selectedInfo);
                }
                if (cameraManager != null) debugLines.add(String.format("Camera: (%.1f, %.1f) Zoom: %.2f", cameraManager.getCameraX(), cameraManager.getCameraY(), cameraManager.getZoom()));
                if (chunkManager != null && renderer != null) debugLines.add("Chunks Loaded(L): " + chunkManager.getLoadedChunksCount() + " Rendered(R): " + renderer.getRenderedChunkCount());
                if (lightManager != null) debugLines.add("LightQ (Sky): " + lightManager.getSkyLightQueueSize());
                debugLines.add("Controls: Torch:L Dig:J Elev:Q/E Debug:F5 Regen:G");
                renderer.renderDebugOverlay(10f, 10f, 600f, 240f, debugLines);
            }
        }
    }

    public void requestFullMapRegeneration() {
        System.out.println("Game: Full map regeneration requested.");
        if (chunkManager != null && renderer != null) {
            Set<ChunkCoordinate> toClear = new HashSet<>(renderer.getRenderedChunkCoordinates());
            for (ChunkCoordinate coord : toClear) {
                renderer.removeRenderChunk(coord);
                chunkManager.unloadChunk(coord.chunkX, coord.chunkY);
                lightManager.onChunkUnloaded(coord.chunkX, coord.chunkY);
            }
        }
        if (player != null && map != null) {
            player.setPosition(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
            if (inputHandler != null) inputHandler.setSelectedTile(player.getTileCol(), player.getTileRow());
            if (cameraManager != null) cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
        }
        manageChunks(); // Reload chunks around new player position
        this.currentGlobalSkyLight = calculateSkyLightValue(this.pseudoTimeOfDay); // Recalculate current global
        if (lightManager != null) lightManager.updateGlobalSkyLightForAllLoadedChunks(); // Queue all new chunks
        System.out.println("Game: Map regeneration setup complete.");
    }

    private boolean showDebugOverlay = true;
    public void toggleShowDebugOverlay() { this.showDebugOverlay = !this.showDebugOverlay; }

    private void cleanup() {
        System.out.println("Game cleanup initiated...");
        if (renderer != null) renderer.cleanup();
        System.out.println("Game cleanup complete.");
    }
}
