package org.isogame.game;

import org.isogame.constants.Constants;
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
import java.util.Queue; // Import Queue
import java.util.LinkedList; // Import LinkedList

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
    private final double DAY_NIGHT_CYCLE_SPEED = 0.05;
    private byte currentGlobalSkyLight;

    private byte lastUpdatedSkyLightValue;
    private static final int SKY_LIGHT_UPDATE_THRESHOLD = 2;

    // --- FPS Counter Variables ---
    private int framesRenderedThisSecond = 0;
    private double timeAccumulatorForFps = 0.0;
    private double displayedFps = 0.0;
    // --- End FPS Counter Variables ---

    private int currentRenderDistanceChunks = Constants.RENDER_DISTANCE_CHUNKS; // Initialize with default


    // New: Queue for chunk render updates and max updates per frame
    private final Queue<LightManager.ChunkCoordinate> chunkRenderUpdateQueue = new LinkedList<>();
    private static final int MAX_CHUNK_GEOMETRY_UPDATES_PER_FRAME = 3; // Adjust this value (e.g., 2 to 10)


    public Game(long window, int initialFramebufferWidth, int initialScreenHeight) {
        this.window = window;

        map = new Map(MAP_WIDTH, MAP_HEIGHT);
        lightManager = map.getLightManager();
        player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
        cameraManager = new CameraManager(initialFramebufferWidth, initialScreenHeight, map.getWidth(), map.getHeight());
        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());

        inputHandler = new InputHandler(window, cameraManager, map, player, this);
        inputHandler.registerCallbacks(this::requestFullMapRegeneration);

        renderer = new Renderer(cameraManager, map, player, inputHandler);
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

        // Initial full processing of light might still be heavy, but subsequent ones will be spread.
        if (lightManager != null) {
            for(int i=0; i < 100; i++) { // Process more initially to settle
                lightManager.processLightQueuesIncrementally();
                Set<LightManager.ChunkCoordinate> dirtyChunks = lightManager.getDirtyChunksAndClear();
                if (!dirtyChunks.isEmpty()) {
                    for (LightManager.ChunkCoordinate coord : dirtyChunks) {
                        // For initial load, we might want to process them all or use the renderer's full upload.
                        // Here, we'll add to the queue, and they'll be processed over first few frames.
                        if (!chunkRenderUpdateQueue.contains(coord)) {
                            chunkRenderUpdateQueue.offer(coord);
                        }
                    }
                } else if (i > 10) { // Stop if stable
                    break;
                }
            }
        }
        // renderer.uploadTileMapGeometry(); // This does a full, immediate upload of all chunks.
        // So, the queue might be mostly for dynamic updates after this.
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
            if (deltaTime > 0.1) deltaTime = 0.1; // Cap delta time

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
            float phase = (float) ((time - 0.40) / 0.20);
            newSkyLight = (byte) (SKY_LIGHT_DAY - phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        } else if (time >= 0.60 && time < 0.80) { // Night (now shorter)
            newSkyLight = SKY_LIGHT_NIGHT;
        } else { // Dawn (time >= 0.80 && time < 1.0) (now longer)
            float phase = (float) ((time - 0.80) / 0.20);
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

            lightManager.updateGlobalSkyLight(newCalculatedSkyLight); // This will mark chunks dirty internally in LightManager
            lastUpdatedSkyLightValue = newCalculatedSkyLight;
            // System.out.println("Game: Sky light FULL UPDATE to " + newCalculatedSkyLight + " based on time: " + pseudoTimeOfDay);
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
            lightManager.processLightQueuesIncrementally(); // This is for light value propagation, not geometry

            // Get all chunks that became dirty due to light calculations in this frame
            Set<LightManager.ChunkCoordinate> newlyDirtyChunks = lightManager.getDirtyChunksAndClear();
            if (!newlyDirtyChunks.isEmpty()) {
                for (LightManager.ChunkCoordinate coord : newlyDirtyChunks) {
                    if (!chunkRenderUpdateQueue.contains(coord)) { // Avoid adding duplicates
                        chunkRenderUpdateQueue.offer(coord);
                    }
                }
            }
        }

        // Process a limited number of chunk geometry updates from our queue
        if (renderer != null && !chunkRenderUpdateQueue.isEmpty()) {
            int updatedThisFrame = 0;
            while (!chunkRenderUpdateQueue.isEmpty() && updatedThisFrame < MAX_CHUNK_GEOMETRY_UPDATES_PER_FRAME) {
                LightManager.ChunkCoordinate coordToUpdate = chunkRenderUpdateQueue.poll(); // Get and remove from queue
                if (coordToUpdate != null) {
                    // System.out.println("Game: Processing render update for chunk: " + coordToUpdate.chunkX + "," + coordToUpdate.chunkY + ". Queue size: " + chunkRenderUpdateQueue.size());
                    renderer.updateChunkByGridCoords(coordToUpdate.chunkX, coordToUpdate.chunkY);
                    updatedThisFrame++;
                }
            }
            // if (updatedThisFrame > 0) System.out.println("Game: Updated geometry for " + updatedThisFrame + " chunks this frame. Remaining in queue: " + chunkRenderUpdateQueue.size());
        }
    }


    public int getCurrentRenderDistanceChunks() {
        return currentRenderDistanceChunks;
    }

    public void increaseRenderDistance() {
        // You can cap the maximum render distance if you want, e.g., 10
        currentRenderDistanceChunks = Math.min(currentRenderDistanceChunks + 1, 10);
        System.out.println("Render distance increased to: " + currentRenderDistanceChunks);
    }

    public void decreaseRenderDistance() {
        // You can cap the minimum render distance, e.g., 1
        currentRenderDistanceChunks = Math.max(1, currentRenderDistanceChunks - 1);
        System.out.println("Render distance decreased to: " + currentRenderDistanceChunks);
    }

    private void renderGame() {
        float rSky, gSky, bSky;
        if (currentGlobalSkyLight > SKY_LIGHT_NIGHT + (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT) / 2) {
            rSky = 0.5f; gSky = 0.7f; bSky = 1.0f; // Brighter sky for day
        } else {
            rSky = 0.05f; gSky = 0.05f; bSky = 0.15f; // Darker sky for night
        }
        glClearColor(rSky, gSky, bSky, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (renderer != null) {
            renderer.render(); // Renders terrain chunks and entities

            if (this.showDebugOverlay) {
                List<String> debugLines = new ArrayList<>();
                debugLines.add(String.format("FPS: %.1f", displayedFps));
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
                debugLines.add("Render Q Size: " + chunkRenderUpdateQueue.size());
                debugLines.add("Render Distance (Chunks): " + currentRenderDistanceChunks + " (F6/F7)"); // Add this line



                renderer.renderDebugOverlay(10f, 10f, 1000f, 220f, debugLines);
            }
        }
    }
    public void requestFullMapRegeneration() {
        System.out.println("Game: Full map regeneration requested.");
        this.chunkRenderUpdateQueue.clear(); // Clear any pending render updates

        if (map != null) {
            map.generateMap(); // This will mark all chunks dirty in LightManager internally via Map constructor or its methods
        }
        if (player != null && map != null) {
            player.setPosition(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
            player.setPath(null); // Clear player path
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
        if (lightManager != null) {
            lightManager.updateGlobalSkyLight(currentGlobalSkyLight); // This marks chunks dirty
            // Process all light propagation immediately after regeneration for a consistent start state
            for(int i=0; i < 200; i++) { // Allow more processing after full regen
                lightManager.processLightQueuesIncrementally();
                Set<LightManager.ChunkCoordinate> dirtyChunksFromLight = lightManager.getDirtyChunksAndClear();
                if (!dirtyChunksFromLight.isEmpty()) {
                    for (LightManager.ChunkCoordinate coord : dirtyChunksFromLight) {
                        if (!chunkRenderUpdateQueue.contains(coord)) { // Add to game's render queue
                            chunkRenderUpdateQueue.offer(coord);
                        }
                    }
                } else if (i > 20) { // Stop if stable
                    break;
                }
            }
        }
        System.out.println("Game: Sky light RE-INITIALIZED to " + currentGlobalSkyLight + " after map regeneration.");

        if (renderer != null) {
            System.out.println("Game: Requesting FULL map geometry upload to renderer after regeneration.");
            // This will do an immediate upload of all chunks, which is fine for a full regen.
            // The queue will handle subsequent dynamic updates.
            renderer.uploadTileMapGeometry();
        }
        System.out.println("Game: Full map regeneration processing complete.");
    }

    public void requestTileRenderUpdate(int row, int col) {
        if (renderer != null && CHUNK_SIZE_TILES > 0) {
            // Instead of direct render, add to queue so it's batched with other updates if needed
            LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(col / CHUNK_SIZE_TILES, row / CHUNK_SIZE_TILES);
            if (!chunkRenderUpdateQueue.contains(coord)) {
                chunkRenderUpdateQueue.offer(coord);
            }
            // renderer.updateChunkContainingTile(row, col); // Old direct call
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
