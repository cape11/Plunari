// src/main/java/org/isogame/game/Game.java

package org.isogame.game;

import org.isogame.asset.AssetManager;
import org.isogame.constants.Constants;
import org.isogame.crafting.CraftingRecipe;
import org.isogame.crafting.RecipeRegistry;
import org.isogame.entity.*;
import org.isogame.input.InputHandler;
import org.isogame.input.MouseHandler;
import org.isogame.camera.CameraManager;
import org.isogame.inventory.InventorySlot;
import org.isogame.item.Item;
import org.isogame.item.ItemRegistry;
import org.isogame.map.LightManager;
import org.isogame.map.Map;
import org.isogame.render.Font;
import org.isogame.render.Renderer;
import org.isogame.savegame.*;
import org.isogame.tile.Tile;
import org.isogame.ui.MenuItemButton;
import org.isogame.ui.UIManager;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import static org.isogame.constants.Constants.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Game {
    private double lastFrameTime;

    private final long window;
    private CameraManager cameraManager;
    private Renderer renderer;
    private final InputHandler inputHandler;
    private final MouseHandler mouseHandler;
    private final EntityManager entityManager;
    private final UIManager uiManager;
    private AssetManager assetManager;

    private Map map;
    private PlayerModel player;
    private LightManager lightManager;
    private PlacementManager placementManager;

    private double pseudoTimeOfDay = 0.0005;
    private byte currentGlobalSkyLightActual;
    private byte lastGlobalSkyLightTargetSetInLM;

    private int framesRenderedThisSecond = 0;
    private double timeAccumulatorForFps = 0.0;
    private double displayedFps = 0.0;

    private int currentRenderDistanceChunks = Constants.RENDER_DISTANCE_CHUNKS_DEFAULT;
    private boolean showInventory = false;
    private boolean showHotbar = true;
    private boolean showDebugOverlay = true;

    private double spawnTimer = 0.0;
    private final Random spawnRandom = new Random();
    private static final double SPAWN_CYCLE_TIME = 5.0;
    private static final int SPAWN_RADIUS = 32;

    private final Queue<LightManager.ChunkCoordinate> chunkRenderUpdateQueue = new LinkedList<>();
    private static final int MAX_CHUNK_GEOMETRY_UPDATES_PER_FRAME = 2;
    private final Queue<LightManager.ChunkCoordinate> globalSkyRefreshNeededQueue = new LinkedList<>();
    private static final int CHUNKS_TO_REFRESH_SKY_PER_FRAME = 4;

    private GameStateManager gameStateManager;

    private String currentWorldName = null;
    private List<String> availableSaveFiles = new ArrayList<>();
    private static final String SAVES_DIRECTORY = "saves";

    private boolean isDraggingItem = false;
    private InventorySlot draggedItemStack = null;
    private int originalDragSlotIndex = -1;
    private CraftingRecipe hoveredRecipe = null;

    private List<MenuItemButton> menuButtons = new ArrayList<>();
    private Set<LightManager.ChunkCoordinate> currentlyActiveLogicalChunks = new HashSet<>();

    public Game(long window, int initialFramebufferWidth, int initialScreenHeight) {
        this.window = window;
        new File(SAVES_DIRECTORY).mkdirs();

        System.out.println("Game Constructor: Initializing core components...");
        try {
            cameraManager = new CameraManager(initialFramebufferWidth, initialScreenHeight, 1, 1);
            System.out.println("Game Constructor: CameraManager initialized.");

            this.entityManager = new EntityManager();
            renderer = new Renderer(cameraManager, null, null, null);
            renderer.onResize(initialFramebufferWidth, initialScreenHeight);
            System.out.println("Game Constructor: Renderer initialized for menu.");

            assetManager = new AssetManager(renderer);
            assetManager.loadAllAssets();
            renderer.setAssetManager(assetManager);

            ItemRegistry.loadItems();
            RecipeRegistry.loadRecipes();
            ItemRegistry.initializeItemUVs(assetManager.getTextureMapForRegistry());

            inputHandler = new InputHandler(window, cameraManager, null, null, this);
            inputHandler.registerCallbacks(this::requestFullMapRegeneration);
            System.out.println("Game Constructor: InputHandler initialized and callbacks registered.");

            mouseHandler = new MouseHandler(window, cameraManager, null, inputHandler, null, this);
            System.out.println("Game Constructor: MouseHandler initialized.");

            uiManager = new UIManager(this, renderer, null, assetManager, inputHandler);

            this.gameStateManager = new GameStateManager(this, renderer);

        } catch (Exception e) {
            System.err.println("FATAL: Exception during core component initialization in Game constructor!");
            e.printStackTrace();
            throw new RuntimeException("Core component initialization failed", e);
        }

        glfwSetFramebufferSizeCallback(window, (win, fbW, fbH) -> {
            if (cameraManager != null) cameraManager.updateScreenSize(fbW, fbH);
            if (renderer != null) renderer.onResize(fbW, fbH);
            setupMainMenuButtons();
        });

        refreshAvailableSaveFiles();
        System.out.println("Game Constructor: Finished. Initial state: MAIN_MENU.");
    }

    public void updateGameLogic(double deltaTime) {
        if (player == null || map == null || lightManager == null || entityManager == null) return;

        pseudoTimeOfDay += deltaTime * DAY_NIGHT_CYCLE_SPEED;
        if (pseudoTimeOfDay >= 1.0) pseudoTimeOfDay -= 1.0;

        updateActiveChunksAroundPlayer();
        updateSkyLightBasedOnTimeOfDay(deltaTime);

        handleDynamicSpawning(deltaTime);

        if (!globalSkyRefreshNeededQueue.isEmpty()) {
            int refreshedThisFrame = 0;
            while (refreshedThisFrame < CHUNKS_TO_REFRESH_SKY_PER_FRAME && !globalSkyRefreshNeededQueue.isEmpty()) {
                LightManager.ChunkCoordinate coordToRefresh = globalSkyRefreshNeededQueue.poll();
                if (coordToRefresh != null && currentlyActiveLogicalChunks.contains(coordToRefresh)) {
                    lightManager.refreshSkyLightForSingleChunk(coordToRefresh, lightManager.getCurrentGlobalSkyLightTarget());
                }
                refreshedThisFrame++;
            }
        }

        entityManager.update(deltaTime, this);
        inputHandler.handleContinuousInput(deltaTime);
        cameraManager.update(deltaTime);
        lightManager.processLightQueuesIncrementally();

        Set<LightManager.ChunkCoordinate> dirtyFromLighting = lightManager.getDirtyChunksAndClear();
        for (LightManager.ChunkCoordinate dirtyCoord : dirtyFromLighting) {
            if (currentlyActiveLogicalChunks.contains(dirtyCoord) && !chunkRenderUpdateQueue.contains(dirtyCoord)) {
                chunkRenderUpdateQueue.offer(dirtyCoord);
            }
        }

        if (!chunkRenderUpdateQueue.isEmpty()) {
            int updatedThisFrame = 0;
            while (!chunkRenderUpdateQueue.isEmpty() && updatedThisFrame < MAX_CHUNK_GEOMETRY_UPDATES_PER_FRAME) {
                LightManager.ChunkCoordinate coordToUpdate = chunkRenderUpdateQueue.poll();
                if (coordToUpdate != null && currentlyActiveLogicalChunks.contains(coordToUpdate)) {
                    renderer.updateChunkByGridCoords(coordToUpdate.chunkX, coordToUpdate.chunkY);
                    updatedThisFrame++;
                }
            }
        }
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

            // --- FPS Calculation (no change here) ---
            timeAccumulatorForFps += deltaTime;
            framesRenderedThisSecond++;
            if (timeAccumulatorForFps >= 1.0) {
                displayedFps = (double) framesRenderedThisSecond / timeAccumulatorForFps;
                framesRenderedThisSecond = 0;
                timeAccumulatorForFps -= 1.0;
            }

            // --- Handle Input ---
            glfwPollEvents();

            // --- Update Game Logic based on State ---
            gameStateManager.update(deltaTime);

            // --- Prepare for Rendering (THIS IS THE FIX) ---
            // Set the background color based on the current state.
            if (gameStateManager.getCurrentState() instanceof org.isogame.game.states.InGameState) {
                // Dynamic sky color for in-game
                float rSky, gSky, bSky;
                float lightRange = (float)(SKY_LIGHT_DAY - SKY_LIGHT_NIGHT_MINIMUM);
                float lightRatio = 0.5f;
                if (lightRange > 0.001f && lightManager != null) {
                    lightRatio = (float)(lightManager.getCurrentGlobalSkyLightTarget() - SKY_LIGHT_NIGHT_MINIMUM) / lightRange;
                }
                lightRatio = Math.max(0, Math.min(1, lightRatio));
                float dayR = 0.5f, dayG = 0.7f, dayB = 1.0f;
                float nightR = 0.02f, nightG = 0.02f, nightB = 0.08f;
                rSky = nightR + (dayR - nightR) * lightRatio;
                gSky = nightG + (dayG - nightG) * lightRatio;
                bSky = nightB + (dayB - nightB) * lightRatio;
                glClearColor(rSky, gSky, bSky, 1.0f);
            } else {
                // A default dark color for the main menu
                glClearColor(0.1f, 0.12f, 0.15f, 1.0f);
            }

            // Clear the screen's color and depth buffers
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);


            // --- Render Game Logic based on State ---
            gameStateManager.render(deltaTime);

            // --- Swap Buffers to Display the New Frame ---
            glfwSwapBuffers(window);
        }
        System.out.println("Game loop exited.");
        gameStateManager.getCurrentState().exit();
        cleanup();
    }


    public UIManager getUiManager() {
        return this.uiManager;
    }
    public void setCurrentGameState(org.isogame.game.GameStateManager.State newState) {
        gameStateManager.setState(newState);
    }
    // ... all other methods in Game.java remain the same
    private void initializeGameWorldCommonLogic() {
        System.out.println("Game: Initializing game world common logic for world: " + (currentWorldName != null ? currentWorldName : "NEW (Unsaved)"));
        if (map == null || player == null) {
            System.err.println("Game.initializeGameWorldCommonLogic: Map or Player is null. Cannot proceed.");
            setCurrentGameState(org.isogame.game.GameStateManager.State.MAIN_MENU); // Fallback
            return;
        }

        // Add player to the map's entity list when game starts
        entityManager.clearAllEntities();
        entityManager.addEntity(player);

        this.lightManager = map.getLightManager();
        if (this.lightManager == null) {
            System.err.println("FATAL: LightManager is null after map initialization in initializeGameWorldCommonLogic.");
            setCurrentGameState(GameStateManager.State.MAIN_MENU); return;
        }

        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());

        // Update existing InputHandler and MouseHandler with game world references
        inputHandler.updateGameReferences(map, player);
        mouseHandler.updateGameReferences(map, player, inputHandler);

        // Update the existing Renderer instance with game world references
        renderer.setGameSpecificReferences(map, player, inputHandler, entityManager);
        uiManager.updateGameReferences(player, assetManager);

        // Ensure viewport is correct if it was changed or if renderer was menu-only before
        int[] fbW = new int[1], fbH = new int[1];
        glfwGetFramebufferSize(window, fbW, fbH);
        if (fbW[0] > 0 && fbH[0] > 0) renderer.onResize(fbW[0], fbH[0]);


        pseudoTimeOfDay = 0.0005;
        currentGlobalSkyLightActual = calculateSkyLightForTime(pseudoTimeOfDay);
        lightManager.setCurrentGlobalSkyLightTarget(currentGlobalSkyLightActual);
        lastGlobalSkyLightTargetSetInLM = currentGlobalSkyLightActual;

        currentlyActiveLogicalChunks.clear();
        globalSkyRefreshNeededQueue.clear();
        chunkRenderUpdateQueue.clear();

        System.out.println("GameWorldLogic: About to update active chunks around player.");
        updateActiveChunksAroundPlayer();
        System.out.println("GameWorldLogic: Finished updating active chunks. Active: " + currentlyActiveLogicalChunks.size());
        performIntensiveInitialLightProcessing();

        System.out.println("Game world initialized. Player at: " + player.getTileRow() + "," + player.getTileCol());
        if (map != null) System.out.println("World Seed: " + map.getWorldSeed());
    }




    public List<MenuItemButton> getMainMenuButtons() { return menuButtons; }

    private void setupMainMenuButtons() {
        menuButtons.clear();
        if (cameraManager == null) {
            System.err.println("setupMainMenuButtons: CameraManager is null. This should not happen if constructor is correct.");
            // Attempt to recover, though this indicates a deeper issue.
            int[] fbW = new int[1], fbH = new int[1];
            glfwGetFramebufferSize(window, fbW, fbH);
            cameraManager = new CameraManager((fbW[0] > 0 ? fbW[0] : WIDTH) , (fbH[0] > 0 ? fbH[0] : HEIGHT),1,1);
        }

        float buttonWidth = 300f;
        float buttonHeight = 40f;
        float buttonSpacing = 15f;
        float currentY = cameraManager.getScreenHeight() * 0.25f;
        float screenCenterX = cameraManager.getScreenWidth() / 2f;
        float buttonX = screenCenterX - buttonWidth / 2f;

        MenuItemButton newWorldButton = new MenuItemButton(buttonX, currentY, buttonWidth, buttonHeight, "Create New World", "NEW_WORLD", null);
        newWorldButton.setTextureAtlasUVs(Renderer.ROCK_ATLAS_U0, Renderer.ROCK_ATLAS_V0, Renderer.ROCK_ATLAS_U1, Renderer.ROCK_ATLAS_V1);
        newWorldButton.baseTextColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        newWorldButton.hoverTextColor = new float[]{0.9f, 0.9f, 0.7f, 1.0f};
        menuButtons.add(newWorldButton);
        currentY += buttonHeight + buttonSpacing;

        if (availableSaveFiles.isEmpty()) {
            // No saves
        } else {
            float worldButtonWidth = buttonWidth * 0.75f;
            float deleteButtonWidth = buttonWidth * 0.20f;
            float totalPairWidth = worldButtonWidth + deleteButtonWidth + 5f;
            float worldButtonX = screenCenterX - totalPairWidth / 2f;
            float deleteButtonX = worldButtonX + worldButtonWidth + 5f;

            for (String saveFileName : availableSaveFiles) {
                String worldNameDisplay = saveFileName.replace(".json", "");
                MenuItemButton loadButton = new MenuItemButton(worldButtonX, currentY, worldButtonWidth, buttonHeight, "Load: " + worldNameDisplay, "LOAD_WORLD", saveFileName);
                loadButton.setTextureAtlasUVs(Renderer.DEFAULT_SIDE_U0, Renderer.DEFAULT_SIDE_V0, Renderer.DEFAULT_SIDE_U1, Renderer.DEFAULT_SIDE_V1);
                loadButton.baseTextColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
                loadButton.hoverTextColor = new float[]{0.8f, 0.9f, 0.7f, 1.0f};
                menuButtons.add(loadButton);

                MenuItemButton deleteButton = new MenuItemButton(deleteButtonX, currentY, deleteButtonWidth, buttonHeight, "DEL", "DELETE_WORLD", worldNameDisplay);
                deleteButton.setTextureAtlasUVs(Renderer.SAND_ATLAS_U0, Renderer.SAND_ATLAS_V0, Renderer.SAND_ATLAS_U1, Renderer.SAND_ATLAS_V1);
                deleteButton.setCustomColors(
                        new float[]{0.5f, 0.25f, 0.15f, 0.8f}, new float[]{0.6f, 0.3f, 0.2f, 0.9f},
                        new float[]{0.2f, 0.1f, 0.05f, 1.0f}, new float[]{0.9f, 0.8f, 0.75f, 1.0f},
                        new float[]{1.0f, 0.9f, 0.85f, 1.0f}
                );
                menuButtons.add(deleteButton);
                currentY += buttonHeight + buttonSpacing / 2;
            }
        }
        currentY += buttonSpacing;

        MenuItemButton exitButton = new MenuItemButton(buttonX, currentY, buttonWidth, buttonHeight, "Exit Game", "EXIT_GAME", null);
        exitButton.setTextureAtlasUVs(Renderer.ROCK_ATLAS_U0, Renderer.ROCK_ATLAS_V0, Renderer.ROCK_ATLAS_U1, Renderer.ROCK_ATLAS_V1);
        exitButton.baseTextColor = new float[]{0.9f, 0.88f, 0.82f, 1.0f};
        exitButton.hoverTextColor = new float[]{1.0f, 0.98f, 0.92f, 1.0f};
        menuButtons.add(exitButton);
    }

    private void performIntensiveInitialLightProcessing() {
        if (lightManager == null || currentlyActiveLogicalChunks.isEmpty()) {
            System.out.println("performIntensiveInitialLightProcessing: LightManager not ready or no active chunks.");
            return;
        }
        System.out.println("Performing intensive initial light calculation for " + currentlyActiveLogicalChunks.size() + " active chunks...");
        int initialLightSettlingPasses = Math.max(15, currentlyActiveLogicalChunks.size() / 2 + 10);
        int chunksToRefreshPerInitialPass = Math.max(CHUNKS_TO_REFRESH_SKY_PER_FRAME * 2, 8);

        globalSkyRefreshNeededQueue.clear();
        globalSkyRefreshNeededQueue.addAll(currentlyActiveLogicalChunks);

        for (int i = 0; i < initialLightSettlingPasses; i++) {
            int refreshedThisPass = 0;
            while (refreshedThisPass < chunksToRefreshPerInitialPass && !globalSkyRefreshNeededQueue.isEmpty()) {
                LightManager.ChunkCoordinate coordToRefresh = globalSkyRefreshNeededQueue.poll();
                if (coordToRefresh != null && currentlyActiveLogicalChunks.contains(coordToRefresh)) {
                    lightManager.refreshSkyLightForSingleChunk(coordToRefresh, lightManager.getCurrentGlobalSkyLightTarget());
                }
                refreshedThisPass++;
            }
            lightManager.processLightQueuesIncrementally(LightManager.BATCH_LIGHT_UPDATE_BUDGET * 2);

            Set<LightManager.ChunkCoordinate> dirtyFromThisPass = lightManager.getDirtyChunksAndClear();
            for (LightManager.ChunkCoordinate dirtyCoord : dirtyFromThisPass) {
                if (!chunkRenderUpdateQueue.contains(dirtyCoord) && currentlyActiveLogicalChunks.contains(dirtyCoord)) {
                    chunkRenderUpdateQueue.offer(dirtyCoord);
                }
            }
            int geomUpdates = 0;
            while(!chunkRenderUpdateQueue.isEmpty() && geomUpdates < MAX_CHUNK_GEOMETRY_UPDATES_PER_FRAME * 3) {
                LightManager.ChunkCoordinate coordToUpdate = chunkRenderUpdateQueue.poll();
                if (coordToUpdate != null && currentlyActiveLogicalChunks.contains(coordToUpdate) && renderer != null) {
                    renderer.updateChunkByGridCoords(coordToUpdate.chunkX, coordToUpdate.chunkY);
                    geomUpdates++;
                } else if (coordToUpdate == null) break;
            }
            if (!lightManager.isAnyLightQueueNotEmpty() && globalSkyRefreshNeededQueue.isEmpty() && i > (initialLightSettlingPasses / 3)) {
                break;
            }
        }
        int finalSweepCount = 0;
        while((lightManager.isAnyLightQueueNotEmpty() || !globalSkyRefreshNeededQueue.isEmpty()) && finalSweepCount < 10){
            if(!globalSkyRefreshNeededQueue.isEmpty()){
                LightManager.ChunkCoordinate coordToRefresh = globalSkyRefreshNeededQueue.poll();
                if (coordToRefresh != null && currentlyActiveLogicalChunks.contains(coordToRefresh)) {
                    lightManager.refreshSkyLightForSingleChunk(coordToRefresh, lightManager.getCurrentGlobalSkyLightTarget());
                }
            }
            lightManager.processLightQueuesIncrementally(LightManager.BATCH_LIGHT_UPDATE_BUDGET);
            Set<LightManager.ChunkCoordinate> dirtyFromThisPass = lightManager.getDirtyChunksAndClear();
            for (LightManager.ChunkCoordinate dirtyCoord : dirtyFromThisPass) {
                if (!chunkRenderUpdateQueue.contains(dirtyCoord) && currentlyActiveLogicalChunks.contains(dirtyCoord)) {
                    chunkRenderUpdateQueue.offer(dirtyCoord);
                }
            }
            int geomUpdates = 0;
            while(!chunkRenderUpdateQueue.isEmpty() && geomUpdates < MAX_CHUNK_GEOMETRY_UPDATES_PER_FRAME * 2) {
                LightManager.ChunkCoordinate coordToUpdate = chunkRenderUpdateQueue.poll();
                if (coordToUpdate != null && currentlyActiveLogicalChunks.contains(coordToUpdate) && renderer != null) {
                    renderer.updateChunkByGridCoords(coordToUpdate.chunkX, coordToUpdate.chunkY);
                    geomUpdates++;
                } else break;
            }
            if(!lightManager.isAnyLightQueueNotEmpty() && globalSkyRefreshNeededQueue.isEmpty()) break;
            finalSweepCount++;
        }
        System.out.println("Intensive initial light calculation complete. Render Q: " + chunkRenderUpdateQueue.size());
    }

    private List<LightManager.ChunkCoordinate> getDesiredActiveChunkCoordinates() {
        List<LightManager.ChunkCoordinate> desiredActive = new ArrayList<>();
        if (player == null || CHUNK_SIZE_TILES <= 0) { // Map can be null if player is also null (menu state)
            // System.out.println("getDesiredActiveChunkCoordinates: Player or CHUNK_SIZE_TILES invalid.");
            return desiredActive;
        }

        int playerTileCol = player.getTileCol();
        int playerTileRow = player.getTileRow();

        int playerChunkX = Math.floorDiv(playerTileCol, CHUNK_SIZE_TILES);
        int playerChunkY = Math.floorDiv(playerTileRow, CHUNK_SIZE_TILES);

        int renderDist = getCurrentRenderDistanceChunks();

        for (int dy = -renderDist; dy <= renderDist; dy++) {
            for (int dx = -renderDist; dx <= renderDist; dx++) {
                int cx = playerChunkX + dx;
                int cy = playerChunkY + dy;
                desiredActive.add(new LightManager.ChunkCoordinate(cx, cy));
            }
        }
        return desiredActive;
    }

    private void updateActiveChunksAroundPlayer() {
        if (renderer == null || lightManager == null || map == null || player == null) {
            return;
        }

        List<LightManager.ChunkCoordinate> desiredCoords = getDesiredActiveChunkCoordinates();
        Set<LightManager.ChunkCoordinate> desiredSet = new HashSet<>(desiredCoords);

        Iterator<LightManager.ChunkCoordinate> iterator = currentlyActiveLogicalChunks.iterator();
        while (iterator.hasNext()) {
            LightManager.ChunkCoordinate currentActiveCoord = iterator.next();
            if (!desiredSet.contains(currentActiveCoord)) {

                // Unload entities within the chunk that is being deactivated
                if (entityManager != null) {
                    int chunkMinR = currentActiveCoord.chunkY * CHUNK_SIZE_TILES;
                    int chunkMaxR = chunkMinR + CHUNK_SIZE_TILES;
                    int chunkMinC = currentActiveCoord.chunkX * CHUNK_SIZE_TILES;
                    int chunkMaxC = chunkMinC + CHUNK_SIZE_TILES;

                    // Use an iterator to safely remove entities while iterating
                    Iterator<Entity> entityIterator = entityManager.getEntities().iterator();
                    while (entityIterator.hasNext()) {
                        Entity entity = entityIterator.next();
                        if (entity instanceof PlayerModel) { // Don't unload the player
                            continue;
                        }
                        int entityR = entity.getTileRow();
                        int entityC = entity.getTileCol();

                        if (entityR >= chunkMinR && entityR < chunkMaxR && entityC >= chunkMinC && entityC < chunkMaxC) {
                            entityIterator.remove();
                        }
                    }
                }

                renderer.unloadChunkGraphics(currentActiveCoord.chunkX, currentActiveCoord.chunkY);
                map.unloadChunkData(currentActiveCoord.chunkX, currentActiveCoord.chunkY);
                iterator.remove();
                globalSkyRefreshNeededQueue.remove(currentActiveCoord);
            }
        }

        for (LightManager.ChunkCoordinate newCoord : desiredCoords) {
            if (!currentlyActiveLogicalChunks.contains(newCoord)) {
                map.getOrGenerateChunkTiles(newCoord.chunkX, newCoord.chunkY);
                renderer.ensureChunkGraphicsLoaded(newCoord.chunkX, newCoord.chunkY);
                lightManager.initializeSkylightForChunk(newCoord);
                currentlyActiveLogicalChunks.add(newCoord);

                if (!globalSkyRefreshNeededQueue.contains(newCoord)) {
                    globalSkyRefreshNeededQueue.offer(newCoord);
                }

                int[] dNeighborsX = {0, 0, 1, -1, 1, -1, 1, -1};
                int[] dNeighborsY = {1, -1, 0, 0, 1, 1, -1, -1};
                for (int i = 0; i < 8; i++) {
                    LightManager.ChunkCoordinate neighborCoord = new LightManager.ChunkCoordinate(
                            newCoord.chunkX + dNeighborsX[i], newCoord.chunkY + dNeighborsY[i]);
                    if (currentlyActiveLogicalChunks.contains(neighborCoord) && !neighborCoord.equals(newCoord)) {
                        map.getOrGenerateChunkTiles(neighborCoord.chunkX, neighborCoord.chunkY);
                        map.propagateLightAcrossChunkBorder(newCoord, neighborCoord, lightManager);
                        map.propagateLightAcrossChunkBorder(neighborCoord, newCoord, lightManager);
                    }
                }
            }
        }
    }

    private void updateSkyLightBasedOnTimeOfDay(double deltaTime) {
        currentGlobalSkyLightActual = calculateSkyLightForTime(pseudoTimeOfDay);
        boolean significantChange = Math.abs(currentGlobalSkyLightActual - lastGlobalSkyLightTargetSetInLM) >= SKY_LIGHT_UPDATE_THRESHOLD;
        boolean boundaryReached = (currentGlobalSkyLightActual == SKY_LIGHT_DAY && lastGlobalSkyLightTargetSetInLM != SKY_LIGHT_DAY) ||
                (currentGlobalSkyLightActual == SKY_LIGHT_NIGHT && lastGlobalSkyLightTargetSetInLM != SKY_LIGHT_NIGHT) ||
                (currentGlobalSkyLightActual == SKY_LIGHT_NIGHT_MINIMUM && lastGlobalSkyLightTargetSetInLM != SKY_LIGHT_NIGHT_MINIMUM);

        if (lightManager != null && (significantChange || boundaryReached)) {
            lightManager.setCurrentGlobalSkyLightTarget(currentGlobalSkyLightActual);
            for (LightManager.ChunkCoordinate activeCoord : currentlyActiveLogicalChunks) {
                if (!globalSkyRefreshNeededQueue.contains(activeCoord)) {
                    globalSkyRefreshNeededQueue.offer(activeCoord);
                }
            }
            lastGlobalSkyLightTargetSetInLM = currentGlobalSkyLightActual;
        }
    }

    private void handleDynamicSpawning(double deltaTime) {
        if (map == null || player == null || entityManager == null) return;

        spawnTimer += deltaTime;
        if (spawnTimer >= SPAWN_CYCLE_TIME) {
            spawnTimer = 0;

            if (entityManager.getEntities().size() >= MAX_ANIMALS + 1) { // +1 for the player
                return;
            }

            int spawnTryX = player.getTileCol() + spawnRandom.nextInt(SPAWN_RADIUS * 2) - SPAWN_RADIUS;
            int spawnTryY = player.getTileRow() + spawnRandom.nextInt(SPAWN_RADIUS * 2) - SPAWN_RADIUS;

            int chunkX = Math.floorDiv(spawnTryX, CHUNK_SIZE_TILES);
            int chunkY = Math.floorDiv(spawnTryY, CHUNK_SIZE_TILES);

            if (currentlyActiveLogicalChunks.contains(new LightManager.ChunkCoordinate(chunkX, chunkY))) {
                Tile targetTile = map.getTile(spawnTryY, spawnTryX);

                if (targetTile != null && targetTile.getType() != Tile.TileType.WATER && targetTile.getType() != Tile.TileType.AIR) {
                    if (spawnRandom.nextBoolean()) {
                        entityManager.addEntity(new Slime(spawnTryY + 0.5f, spawnTryX + 0.5f));
                    } else {
                        if (targetTile.getType() == Tile.TileType.GRASS) {
                            entityManager.addEntity(new Cow(spawnTryY + 0.5f, spawnTryX + 0.5f));
                        }
                    }
                }
            }
        }
    }

    public boolean loadGame(String worldNameOrFileName) {
        System.out.println("Game: loadGame called for " + worldNameOrFileName);
        if (worldNameOrFileName == null || worldNameOrFileName.trim().isEmpty()) {
            System.err.println("LoadGame Error: World name is null or empty.");
            return false;
        }
        String fileName = worldNameOrFileName.endsWith(".json") ? worldNameOrFileName : worldNameOrFileName + ".json";
        String filePath = Paths.get(SAVES_DIRECTORY, fileName).toString();
        System.out.println("Attempting to load game from: " + filePath);

        Gson gson = new Gson();
        GameSaveState saveState;
        try (Reader reader = new FileReader(filePath)) {
            saveState = gson.fromJson(reader, GameSaveState.class);
        } catch (IOException e) {
            System.err.println("Load game: Save file not found or unreadable - " + e.getMessage());
            return false;
        }

        if (saveState == null || saveState.mapData == null || saveState.playerData == null) {
            System.err.println("Load game: Parsed save data is null or incomplete.");
            return false;
        }

        String baseWorldName = fileName.replace(".json", "");
        System.out.println("Restoring game state for world: " + baseWorldName);
        this.currentWorldName = baseWorldName;

        this.map = new Map(saveState.mapData.worldSeed);
        if (!this.map.loadState(saveState.mapData)) { // Map load no longer loads entities
            System.err.println("Failed to load map state from save file. Aborting load.");
            this.currentWorldName = null; this.map = null;
            return false;
        }

        this.player = new PlayerModel(this.map.getCharacterSpawnRow(), this.map.getCharacterSpawnCol());

        // Load entities using the EntityManager AFTER player and map are loaded
        this.entityManager.loadState(saveState);

        this.placementManager = new PlacementManager(this, this.map, this.player);

        if (!this.player.loadState(saveState.playerData)) {
            System.err.println("Failed to load player state. Player state may be inconsistent.");
        }

        this.pseudoTimeOfDay = saveState.pseudoTimeOfDay;

        initializeGameWorldCommonLogic();

        if (cameraManager != null && player != null) {
            cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
        }

        if (this.lightManager != null && map != null && saveState.mapData.explicitlySavedChunks != null) {
            for(MapSaveData.ChunkDiskData cdd : saveState.mapData.explicitlySavedChunks) {
                LightManager.ChunkCoordinate activeChunk = new LightManager.ChunkCoordinate(cdd.chunkX, cdd.chunkY);
                if(currentlyActiveLogicalChunks.contains(activeChunk)){
                    Tile[][] tiles = map.getOrGenerateChunkTiles(activeChunk.chunkX, activeChunk.chunkY);
                    if (tiles == null) continue;
                    for (int r_local = 0; r_local < CHUNK_SIZE_TILES; r_local++) {
                        for (int c_local = 0; c_local < CHUNK_SIZE_TILES; c_local++) {
                            Tile tile = tiles[r_local][c_local];
                            if (tile != null && tile.hasTorch()) {
                                int globalR = activeChunk.chunkY * CHUNK_SIZE_TILES + r_local;
                                int globalC = activeChunk.chunkX * CHUNK_SIZE_TILES + c_local;
                                lightManager.addLightSource(globalR, globalC, (byte) TORCH_LIGHT_LEVEL);
                            }
                        }
                    }
                }
            }
            for(int i=0; i<10; i++) {
                lightManager.processLightQueuesIncrementally(LightManager.BATCH_LIGHT_UPDATE_BUDGET);
                if (!lightManager.isAnyLightQueueNotEmpty() && i > 3) break;
            }
        }

        System.out.println("Game loaded successfully: " + this.currentWorldName);
        setCurrentGameState(GameStateManager.State.IN_GAME);
        return true;
    }

    public void saveGame(String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) {
            System.err.println("SaveGame Error: World name is null or empty.");
            return;
        }
        if (player == null || map == null || entityManager == null) {
            System.err.println("SaveGame Error: Player, Map, or EntityManager is null. Cannot save.");
            return;
        }

        String baseWorldName = worldName.replace(".json", "");
        String filePath = Paths.get(SAVES_DIRECTORY, baseWorldName + ".json").toString();

        GameSaveState saveState = new GameSaveState();

        saveState.playerData = new PlayerSaveData();
        player.populateSaveData(saveState.playerData);

        saveState.mapData = new MapSaveData();
        map.populateSaveData(saveState.mapData); // Map saves its own data
        entityManager.populateSaveData(saveState); // EntityManager saves entity data into the mapData object

        saveState.pseudoTimeOfDay = this.pseudoTimeOfDay;

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = new FileWriter(filePath)) {
            gson.toJson(saveState, writer);
            System.out.println("Game saved successfully to " + filePath);
            this.currentWorldName = baseWorldName;
            refreshAvailableSaveFiles();
        } catch (IOException e) {
            System.err.println("Error saving game: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean canCraft(CraftingRecipe recipe) {
        if (player == null || recipe == null) return false;

        for (java.util.Map.Entry<Item, Integer> entry : recipe.getRequiredItems().entrySet()) {
            Item requiredItem = entry.getKey();
            int requiredQuantity = entry.getValue();
            if (player.getInventoryItemCount(requiredItem) < requiredQuantity) {
                return false;
            }
        }
        return true;
    }

    public void doCraft(CraftingRecipe recipe) {
        if (!player.hasSpaceFor(recipe.getOutputItem(), recipe.getOutputQuantity())) {
            System.out.println("Cannot craft " + recipe.getOutputItem().getDisplayName() + ": No inventory space.");
            return;
        }

        if (!canCraft(recipe)) {
            System.out.println("Cannot craft " + recipe.getOutputItem().getDisplayName() + ": Missing ingredients.");
            return;
        }

        for (java.util.Map.Entry<Item, Integer> entry : recipe.getRequiredItems().entrySet()) {
            player.consumeItem(entry.getKey(), entry.getValue());
        }

        player.addItemToInventory(recipe.getOutputItem(), recipe.getOutputQuantity());
    }

    public void initOpenGL() {
        System.out.println("Game.initOpenGL() - OpenGL version from context: " + glGetString(GL_VERSION));
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
    }

    private byte calculateSkyLightForTime(double time) {
        byte newSkyLight;
        if (time >= 0.0 && time < 0.40) {
            newSkyLight = SKY_LIGHT_DAY;
        } else if (time >= 0.40 && time < 0.60) {
            float phase = (float) ((time - 0.40) / 0.20);
            newSkyLight = (byte) (SKY_LIGHT_DAY - phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        } else if (time >= 0.60 && time < 0.90) {
            newSkyLight = SKY_LIGHT_NIGHT;
        } else {
            float phase = (float) ((time - 0.90) / 0.10);
            newSkyLight = (byte) (SKY_LIGHT_NIGHT + phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        }
        return (byte) Math.max(SKY_LIGHT_NIGHT_MINIMUM, Math.min(MAX_LIGHT_LEVEL, newSkyLight));
    }

    public void toggleHotbar() {
        this.showHotbar = !this.showHotbar;
    }

    public void createNewWorld() {
        System.out.println("Game: createNewWorld() called.");
        int nextWorldNum = 1;
        while (Files.exists(Paths.get(SAVES_DIRECTORY, "World" + nextWorldNum + ".json"))) {
            nextWorldNum++;
        }
        String newWorldName = "World" + nextWorldNum;
        System.out.println("Creating new world: " + newWorldName);

        if (renderer != null && renderer.getMap() != null && currentlyActiveLogicalChunks != null) {
            System.out.println("createNewWorld: Cleaning up graphics from previous world.");
            for(LightManager.ChunkCoordinate coord : currentlyActiveLogicalChunks) {
                if(renderer.isChunkGraphicsLoaded(coord.chunkX, coord.chunkY)) {
                    renderer.unloadChunkGraphics(coord.chunkX, coord.chunkY);
                }
            }
        }
        currentlyActiveLogicalChunks.clear();
        globalSkyRefreshNeededQueue.clear();
        chunkRenderUpdateQueue.clear();

        this.map = new Map(new Random().nextLong());
        this.player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
        this.placementManager = new PlacementManager(this, this.map, this.player);
        this.player.getInventorySlots().forEach(InventorySlot::clearSlot);
        this.player.setSelectedHotbarSlotIndex(0);

        this.currentWorldName = newWorldName;
        initializeGameWorldCommonLogic();
        saveGame(this.currentWorldName);
        refreshAvailableSaveFiles();
        setCurrentGameState(GameStateManager.State.IN_GAME);
        System.out.println("Game: New world '" + newWorldName + "' created and game state set to IN_GAME.");
    }

    public void deleteWorld(String worldName) {
        System.out.println("Game: deleteWorld() called for " + worldName);
        if (worldName == null || worldName.isEmpty()) return;
        String fileName = worldName.endsWith(".json") ? worldName : worldName + ".json";
        File saveFile = new File(SAVES_DIRECTORY, fileName);
        if (saveFile.exists()) {
            if (saveFile.delete()) {
                System.out.println("Deleted world: " + fileName);
                if (this.currentWorldName != null && (this.currentWorldName.equals(fileName.replace(".json", "")) || this.currentWorldName.equals(worldName))) {
                    this.currentWorldName = null;
                }
            } else {
                System.err.println("Failed to delete world: " + fileName);
            }
        } else {
            System.err.println("World file not found for deletion: " + fileName);
        }
        refreshAvailableSaveFiles();
    }

    public void refreshAvailableSaveFiles() {
        availableSaveFiles.clear();
        File savesDir = new File(SAVES_DIRECTORY);
        if (savesDir.exists() && savesDir.isDirectory()) {
            File[] files = savesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    availableSaveFiles.add(file.getName());
                }
                availableSaveFiles.sort(String::compareToIgnoreCase);
            }
        }
        setupMainMenuButtons();
    }

    public CraftingRecipe getHoveredRecipe() {
        return this.hoveredRecipe;
    }

    public void setHoveredRecipe(CraftingRecipe recipe) {
        this.hoveredRecipe = recipe;
    }

    public List<String> getAvailableSaveFiles() { return availableSaveFiles; }
    public String getCurrentWorldName() { return currentWorldName; }
    public void toggleInventory() { this.showInventory = !this.showInventory; }
    public boolean isInventoryVisible() { return this.showInventory; }

    public int getCurrentRenderDistanceChunks() { return currentRenderDistanceChunks; }
    public void increaseRenderDistance() { currentRenderDistanceChunks = Math.min(currentRenderDistanceChunks + 1, RENDER_DISTANCE_CHUNKS_MAX); }
    public void decreaseRenderDistance() { currentRenderDistanceChunks = Math.max(RENDER_DISTANCE_CHUNKS_MIN, currentRenderDistanceChunks - 1); }

    public int getSelectedHotbarSlotIndex() {
        return (player != null) ? player.getSelectedHotbarSlotIndex() : 0;
    }

    public void setSelectedHotbarSlotIndex(int index) {
        if (player != null) {
            player.setSelectedHotbarSlotIndex(index);
        }
    }

    public double getPseudoTimeOfDay() {
        return this.pseudoTimeOfDay;
    }

    public void requestFullMapRegeneration() {
        System.out.println("Game: Full map regeneration requested (new world with new seed).");
        if (renderer != null && renderer.getMap() != null && currentlyActiveLogicalChunks != null) {
            System.out.println("requestFullMapRegeneration: Cleaning up graphics from previous world.");
            for(LightManager.ChunkCoordinate coord : currentlyActiveLogicalChunks) {
                if(renderer.isChunkGraphicsLoaded(coord.chunkX, coord.chunkY)){
                    renderer.unloadChunkGraphics(coord.chunkX, coord.chunkY);
                }
            }
        }
        currentlyActiveLogicalChunks.clear();
        globalSkyRefreshNeededQueue.clear();
        chunkRenderUpdateQueue.clear();

        this.map = new Map(new Random().nextLong());
        this.player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
        this.player.getInventorySlots().forEach(InventorySlot::clearSlot);
        this.player.setSelectedHotbarSlotIndex(0);

        this.currentWorldName = null;
        initializeGameWorldCommonLogic();
        System.out.println("Game: Full map regeneration processing complete. World is now unsaved.");
    }

    public void requestTileRenderUpdate(int r, int c) {
        if (renderer == null || lightManager == null || map == null || CHUNK_SIZE_TILES <= 0) {
            return;
        }
        lightManager.markChunkDirty(r, c);
        System.out.println("GAME: Acknowledged tile change at (" + r + "," + c + "). Marked chunk as dirty in LightManager.");
    }

    public boolean isShowHotbar() { return this.showHotbar; }
    public Map getMap() { return this.map; }
    public EntityManager getEntityManager() { return this.entityManager; }
    public boolean isDraggingItem() { return this.isDraggingItem; }
    public InventorySlot getDraggedItemStack() { return this.draggedItemStack; }

    public void startDraggingItem(int slotIndex) {
        if (player == null || slotIndex < 0 || slotIndex >= player.getInventorySlots().size()) {
            return;
        }
        InventorySlot sourceSlot = player.getInventorySlots().get(slotIndex);
        if (sourceSlot.isEmpty()) {
            return;
        }

        this.draggedItemStack = new InventorySlot();
        this.draggedItemStack.addItem(sourceSlot.getItem(), sourceSlot.getQuantity());
        this.originalDragSlotIndex = slotIndex;
        this.isDraggingItem = true;

        sourceSlot.clearSlot();
        setHotbarDirty(true);
    }

    public PlacementManager getPlacementManager() {
        return this.placementManager;
    }

    public void stopDraggingItem(int dropSlotIndex) {
        if (!isDraggingItem || player == null) {
            isDraggingItem = false;
            return;
        }

        List<InventorySlot> slots = player.getInventorySlots();

        if (dropSlotIndex >= 0 && dropSlotIndex < slots.size()) {
            InventorySlot targetSlot = slots.get(dropSlotIndex);

            if (targetSlot.isEmpty()) {
                targetSlot.addItem(draggedItemStack.getItem(), draggedItemStack.getQuantity());
            } else {
                Item tempItem = targetSlot.getItem();
                int tempQuantity = targetSlot.getQuantity();

                targetSlot.clearSlot();
                targetSlot.addItem(draggedItemStack.getItem(), draggedItemStack.getQuantity());

                slots.get(originalDragSlotIndex).addItem(tempItem, tempQuantity);
            }
        }
        else {
            player.getInventorySlots().get(originalDragSlotIndex).addItem(draggedItemStack.getItem(), draggedItemStack.getQuantity());
        }

        this.isDraggingItem = false;
        this.draggedItemStack = null;
        this.originalDragSlotIndex = -1;
        setHotbarDirty(true);
    }
    private boolean hotbarDirty = true; // Start as dirty

    public void setHotbarDirty(boolean dirty) {
        this.hotbarDirty = dirty;
    }
    public PlayerModel getPlayer() {
        return this.player;
    }

    // Add this method to src/main/java/org/isogame/game/Game.java

    public org.isogame.game.GameStateManager getGameStateManager() {
        return this.gameStateManager;
    }

    public boolean isShowDebugOverlay() { return this.showDebugOverlay; }
    public void toggleShowDebugOverlay() { this.showDebugOverlay = !this.showDebugOverlay; }

    private void cleanup() {
        System.out.println("Game cleanup initiated...");
        if (renderer != null) renderer.cleanup();
        System.out.println("Game cleanup complete.");
    }

    public long getWindowHandle() {
        return this.window;
    }

    public LightManager getLightManager() {
        return (map != null) ? map.getLightManager() : null;
    }
}