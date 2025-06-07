package org.isogame.game;

import org.isogame.constants.Constants;
import org.isogame.crafting.CraftingRecipe;
import org.isogame.crafting.RecipeRegistry;
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
import org.isogame.entitiy.PlayerModel;
import org.isogame.savegame.*;
import org.isogame.tile.Tile;
import org.isogame.ui.MenuItemButton;

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

/**
 * Manages the overall game state, logic, and rendering flow.
 * This class orchestrates interactions between different game components like
 * the map, player, camera, input handlers, and renderer.
 */
public class Game {
    private double lastFrameTime;

    private final long window;
    // Core components are now final and initialized once in the constructor
    private CameraManager cameraManager;
    private Renderer renderer;
    private final InputHandler inputHandler;
    private final MouseHandler mouseHandler;

    // Game world specific components, initialized when a game starts/loads
    private Map map;
    private PlayerModel player;
    private LightManager lightManager;

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



    private final Queue<LightManager.ChunkCoordinate> chunkRenderUpdateQueue = new LinkedList<>();
    private static final int MAX_CHUNK_GEOMETRY_UPDATES_PER_FRAME = 2; // Can be tuned
    private final Queue<LightManager.ChunkCoordinate> globalSkyRefreshNeededQueue = new LinkedList<>();
    private static final int CHUNKS_TO_REFRESH_SKY_PER_FRAME = 4;

    public enum GameState { MAIN_MENU, IN_GAME }
    private GameState currentGameState = GameState.MAIN_MENU;

    private String currentWorldName = null;
    private List<String> availableSaveFiles = new ArrayList<>();
    private static final String SAVES_DIRECTORY = "saves";

    private boolean isDraggingItem = false;
    private InventorySlot draggedItemStack = null;
    private int originalDragSlotIndex = -1;
    private org.isogame.crafting.CraftingRecipe hoveredRecipe = null;



    private List<MenuItemButton> menuButtons = new ArrayList<>();
    private Set<LightManager.ChunkCoordinate> currentlyActiveLogicalChunks = new HashSet<>();

    public Game(long window, int initialFramebufferWidth, int initialScreenHeight) {
        this.window = window;
        new File(SAVES_DIRECTORY).mkdirs();

        System.out.println("Game Constructor: Initializing core components...");
        try {
            cameraManager = new CameraManager(initialFramebufferWidth, initialScreenHeight, 1, 1);
            System.out.println("Game Constructor: CameraManager initialized.");

            // Renderer is initialized once. It needs to handle null map/player for menu.
            // Its internal references to map/player will be set in initializeGameWorldCommonLogic.
            renderer = new Renderer(cameraManager, null, null, null);
            renderer.onResize(initialFramebufferWidth, initialScreenHeight);
            System.out.println("Game Constructor: Renderer initialized for menu.")
            ;
// Initialize registries that depend on loaded assets.
            ItemRegistry.initializeItemUVs(renderer.getTextureMap());
            RecipeRegistry.loadRecipes(); // <<< FIX:
            // Input and Mouse Handlers are initialized once. Callbacks are registered once.
            // They will use gameInstance.getCurrentGameState() to determine behavior.
            // Their internal map/player references will be updated.
            inputHandler = new InputHandler(window, cameraManager, null, null, this);
            inputHandler.registerCallbacks(this::requestFullMapRegeneration);
            System.out.println("Game Constructor: InputHandler initialized and callbacks registered.");

            mouseHandler = new MouseHandler(window, cameraManager, null, inputHandler, null, this);
            System.out.println("Game Constructor: MouseHandler initialized.");

        } catch (Exception e) {
            System.err.println("FATAL: Exception during core component initialization in Game constructor!");
            e.printStackTrace();
            throw new RuntimeException("Core component initialization failed", e);
        }

        glfwSetFramebufferSizeCallback(window, (win, fbW, fbH) -> {
            if (cameraManager != null) cameraManager.updateScreenSize(fbW, fbH);
            if (renderer != null) renderer.onResize(fbW, fbH);
            if (currentGameState == GameState.MAIN_MENU) setupMainMenuButtons();
        });

        currentGameState = GameState.MAIN_MENU;
        refreshAvailableSaveFiles(); // This also calls setupMainMenuButtons
        System.out.println("Game Constructor: Finished. Initial state: MAIN_MENU.");
    }

    private void initializeGameWorldCommonLogic() {
        System.out.println("Game: Initializing game world common logic for world: " + (currentWorldName != null ? currentWorldName : "NEW (Unsaved)"));
        if (map == null || player == null) {
            System.err.println("Game.initializeGameWorldCommonLogic: Map or Player is null. Cannot proceed.");
            setCurrentGameState(GameState.MAIN_MENU); // Fallback
            return;
        }
        this.lightManager = map.getLightManager();
        if (this.lightManager == null) {
            System.err.println("FATAL: LightManager is null after map initialization in initializeGameWorldCommonLogic.");
            setCurrentGameState(GameState.MAIN_MENU); return;
        }

        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());

        // Update existing InputHandler and MouseHandler with game world references
        inputHandler.updateGameReferences(map, player);
        mouseHandler.updateGameReferences(map, player, inputHandler);

        // Update the existing Renderer instance with game world references
        // This requires Renderer to have a method to accept these.
        renderer.setGameSpecificReferences(map, player, inputHandler);
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
            // System.out.println("updateActiveChunksAroundPlayer: Skipping, critical component null.");
            return;
        }
        // System.out.println("updateActiveChunksAroundPlayer: Starting update.");

        List<LightManager.ChunkCoordinate> desiredCoords = getDesiredActiveChunkCoordinates();
        if (desiredCoords.isEmpty() && player == null) { // If player is null, desiredCoords will be empty.
            // System.out.println("updateActiveChunksAroundPlayer: No player, no desired chunks to process.");
            // Ensure all previously active chunks are unloaded if player becomes null (e.g., returning to menu conceptually)
            if (!currentlyActiveLogicalChunks.isEmpty()) {
                System.out.println("updateActiveChunksAroundPlayer: Player is null, unloading all active chunks.");
                Iterator<LightManager.ChunkCoordinate> iter = currentlyActiveLogicalChunks.iterator();
                while(iter.hasNext()){
                    LightManager.ChunkCoordinate coord = iter.next();
                    renderer.unloadChunkGraphics(coord.chunkX, coord.chunkY);
                    map.unloadChunkData(coord.chunkX, coord.chunkY);
                    iter.remove();
                }
            }
            return;
        }


        Set<LightManager.ChunkCoordinate> desiredSet = new HashSet<>(desiredCoords);

        Iterator<LightManager.ChunkCoordinate> iterator = currentlyActiveLogicalChunks.iterator();
        while (iterator.hasNext()) {
            LightManager.ChunkCoordinate currentActiveCoord = iterator.next();
            if (!desiredSet.contains(currentActiveCoord)) {
                // System.out.println("updateActiveChunksAroundPlayer: Unloading chunk " + currentActiveCoord);
                renderer.unloadChunkGraphics(currentActiveCoord.chunkX, currentActiveCoord.chunkY);
                map.unloadChunkData(currentActiveCoord.chunkX, currentActiveCoord.chunkY);
                iterator.remove();
                globalSkyRefreshNeededQueue.remove(currentActiveCoord);
            }
        }

        for (LightManager.ChunkCoordinate newCoord : desiredCoords) {
            if (!currentlyActiveLogicalChunks.contains(newCoord)) {
                // System.out.println("updateActiveChunksAroundPlayer: Loading new chunk " + newCoord);
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
        // System.out.println("updateActiveChunksAroundPlayer: Finished update. Active: " + currentlyActiveLogicalChunks.size());
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

    private void updateGameLogic(double deltaTime) {
        if (player == null || map == null || lightManager == null || cameraManager == null || inputHandler == null || renderer == null) {
            // This state should ideally not be reached if game state transitions are correct.
            // System.err.println("Game.updateGameLogic: Critical component is null. Skipping update. CurrentState: " + currentGameState);
            return;
        }

        pseudoTimeOfDay += deltaTime * DAY_NIGHT_CYCLE_SPEED;
        if (pseudoTimeOfDay >= 1.0) pseudoTimeOfDay -= 1.0;

        updateActiveChunksAroundPlayer();
        updateSkyLightBasedOnTimeOfDay(deltaTime);

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

        inputHandler.handleContinuousInput(deltaTime);
        player.update(deltaTime);
        cameraManager.update(deltaTime);

        lightManager.processLightQueuesIncrementally();
        Set<LightManager.ChunkCoordinate> newlyDirtyChunksFromLight = lightManager.getDirtyChunksAndClear();
        if (!newlyDirtyChunksFromLight.isEmpty()) {
            for (LightManager.ChunkCoordinate coord : newlyDirtyChunksFromLight) {
                if (!chunkRenderUpdateQueue.contains(coord) && currentlyActiveLogicalChunks.contains(coord)) {
                    chunkRenderUpdateQueue.offer(coord);
                }
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
        if (!this.map.loadState(saveState.mapData)) {
            System.err.println("Failed to load map state from save file. Aborting load.");
            this.currentWorldName = null; this.map = null;
            return false;
        }

        this.player = new PlayerModel(this.map.getCharacterSpawnRow(), this.map.getCharacterSpawnCol());
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
        setCurrentGameState(GameState.IN_GAME); // This will also refresh save files if logic is consistent
        // refreshAvailableSaveFiles(); // Not strictly needed here if setCurrentGameState handles it
        return true;
    }

    public void saveGame(String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) {
            System.err.println("SaveGame Error: World name is null or empty.");
            return;
        }
        if (player == null || map == null) {
            System.err.println("SaveGame Error: Player or Map is null. Cannot save.");
            return;
        }

        String baseWorldName = worldName.replace(".json", "");
        String filePath = Paths.get(SAVES_DIRECTORY, baseWorldName + ".json").toString();

        GameSaveState saveState = new GameSaveState();

        saveState.playerData = new PlayerSaveData();
        player.populateSaveData(saveState.playerData);

        saveState.mapData = new MapSaveData();
        map.populateSaveData(saveState.mapData);

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

    /**
     * Checks if the player has enough items in their inventory to craft a given recipe.
     * @param recipe The recipe to check.
     * @return true if the recipe can be crafted, false otherwise.
     */
    public boolean canCraft(CraftingRecipe recipe) {
        if (player == null || recipe == null) return false;

        for (java.util.Map.Entry<Item, Integer> entry : recipe.getRequiredItems().entrySet()) {
            Item requiredItem = entry.getKey();
            int requiredQuantity = entry.getValue();
            // Use the new helper method in PlayerModel
            if (player.getInventoryItemCount(requiredItem) < requiredQuantity) {
                return false; // Player doesn't have enough of this item
            }
        }
        return true; // Player has all required items
    }

    /**
     * Executes the craft if possible. Consumes ingredients and adds the output to the player's inventory.
     * @param recipe The recipe to craft.
     */
    public void doCraft(CraftingRecipe recipe) {
        // FIX 1: Check if the player has space for the output item BEFORE consuming ingredients.
        // The hasSpaceFor method already exists in your PlayerModel.
        if (!player.hasSpaceFor(recipe.getOutputItem(), recipe.getOutputQuantity())) {
            System.out.println("Cannot craft " + recipe.getOutputItem().getDisplayName() + ": No inventory space.");
            return; // Exit without consuming ingredients
        }

        if (!canCraft(recipe)) {
            System.out.println("Cannot craft " + recipe.getOutputItem().getDisplayName() + ": Missing ingredients.");
            return;
        }

        // Consume ingredients using the new helper method
        for (java.util.Map.Entry<Item, Integer> entry : recipe.getRequiredItems().entrySet()) {
            player.consumeItem(entry.getKey(), entry.getValue());
        }

        // Add the crafted item to the player's inventory
        player.addItemToInventory(recipe.getOutputItem(), recipe.getOutputQuantity());

        // FIX 2: Mark the UI as dirty so it redraws with the new inventory state.
        setHotbarDirty(true);

        System.out.println("Crafted: " + recipe.getOutputItem().getDisplayName());
    }




    public void initOpenGL() {
        System.out.println("Game.initOpenGL() - OpenGL version from context: " + glGetString(GL_VERSION));
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
    }

    public void gameLoop() {
        initOpenGL();
        lastFrameTime = glfwGetTime();
        System.out.println("Entering game loop... Initial state: " + currentGameState);

        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            double deltaTime = currentTime - lastFrameTime;
            lastFrameTime = currentTime;
            if (deltaTime > 0.1) deltaTime = 0.1;

            timeAccumulatorForFps += deltaTime;
            framesRenderedThisSecond++;
            if (timeAccumulatorForFps >= 1.0) {
                displayedFps = (double) framesRenderedThisSecond / timeAccumulatorForFps;
                framesRenderedThisSecond = 0;
                timeAccumulatorForFps -= 1.0;
            }

            glfwPollEvents();

            switch (currentGameState) {
                case MAIN_MENU:
                    updateMainMenu(deltaTime);
                    renderMainMenu();
                    break;
                case IN_GAME:
                    updateGameLogic(deltaTime);
                    renderGame();
                    break;
            }
            glfwSwapBuffers(window);
        }
        System.out.println("Game loop exited.");
        if (currentGameState == GameState.IN_GAME && currentWorldName != null && !currentWorldName.trim().isEmpty()) {
            saveGame(currentWorldName);
        }
        cleanup();
    }

    private void updateMainMenu(double deltaTime) {
        // MouseHandler's GLFW callback handles hover.
        if (cameraManager == null || menuButtons == null) return;
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

    private void renderMainMenu() {
        Font titleFontToUse = null;
        Font generalUiFont = null;

        if (renderer == null || cameraManager == null) {
            System.err.println("RenderMainMenu: Renderer or CameraManager is null. Cannot render menu.");
            glClearColor(0.05f, 0.05f, 0.1f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            return;
        }

        titleFontToUse = renderer.getTitleFont();
        generalUiFont = renderer.getUiFont();
        glClearColor(0.1f, 0.12f, 0.15f, 1.0f);

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        renderer.renderMainMenuBackground();

        if (titleFontToUse != null && titleFontToUse.isInitialized()) {
            String title = "PLUNARI";
            float titleWidth = titleFontToUse.getTextWidthScaled(title, 1.0f);
            titleFontToUse.drawText(
                    cameraManager.getScreenWidth() / 2f - titleWidth / 2f,
                    cameraManager.getScreenHeight() * 0.15f,
                    title, 0.9f, 0.85f, 0.7f);
        } else if (generalUiFont != null && generalUiFont.isInitialized()){
            String title = "PLUNARI";
            float titleWidth = generalUiFont.getTextWidthScaled(title, 2.0f);
            generalUiFont.drawTextScaled(cameraManager.getScreenWidth() / 2f - titleWidth / 2f,
                    cameraManager.getScreenHeight() * 0.15f, title, 2.0f, 0.9f,0.85f,0.7f);
        } else {
            System.err.println("RenderMainMenu: No valid font available for title.");
        }

        if (menuButtons != null) {
            for (MenuItemButton button : menuButtons) {
                if (button.isVisible) {
                    renderer.renderMenuButton(button);
                }
            }
        }
        glEnable(GL_DEPTH_TEST);
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
        this.player.getInventorySlots().forEach(InventorySlot::clearSlot);
        this.player.setSelectedHotbarSlotIndex(0);

        this.currentWorldName = newWorldName;
        initializeGameWorldCommonLogic();
        saveGame(this.currentWorldName);
        refreshAvailableSaveFiles();
        setCurrentGameState(GameState.IN_GAME);
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
        if (currentGameState == GameState.MAIN_MENU) {
            setupMainMenuButtons();
        }
    }

    // Add these new getter and setter methods to the class
    public org.isogame.crafting.CraftingRecipe getHoveredRecipe() {
        return this.hoveredRecipe;
    }

    public void setHoveredRecipe(org.isogame.crafting.CraftingRecipe recipe) {
        this.hoveredRecipe = recipe;
    }




    public List<String> getAvailableSaveFiles() { return availableSaveFiles; }
    public String getCurrentWorldName() { return currentWorldName; }
    public void toggleInventory() { this.showInventory = !this.showInventory; }
    public boolean isInventoryVisible() { return this.showInventory; }
    public GameState getCurrentGameState() { return currentGameState; }

    public void setCurrentGameState(GameState newState) {
        System.out.println("Game state changing from " + this.currentGameState + " to " + newState + " (World: " + currentWorldName + ")");
        GameState oldState = this.currentGameState;
        this.currentGameState = newState;

        if (newState == GameState.MAIN_MENU) {
            if (oldState == GameState.IN_GAME && currentWorldName != null && !currentWorldName.isEmpty() && map != null && player != null) {
                System.out.println("setCurrentGameState: Saving game " + currentWorldName + " before returning to menu.");
                saveGame(currentWorldName);
            }
            // When returning to menu, ensure menu-specific context for handlers and renderer
            System.out.println("setCurrentGameState: Transitioning to MAIN_MENU. Resetting/Re-initializing UI components.");
            if (renderer != null) {
                renderer.cleanup(); // Clean up in-game renderer resources
            }
            try {
                // Create a fresh renderer instance for the menu
                renderer = new Renderer(cameraManager, null, null, null);
                int[] fbW = new int[1], fbH = new int[1];
                glfwGetFramebufferSize(window, fbW, fbH);
                if (fbW[0] > 0 && fbH[0] > 0) renderer.onResize(fbW[0], fbH[0]);
                else renderer.onResize(WIDTH, HEIGHT); // Fallback

                // Input handlers also need to be context-aware or reset
                inputHandler.updateGameReferences(null, null); // Clear game world context
                mouseHandler.updateGameReferences(null, null, inputHandler); // Clear game world context

                System.out.println("setCurrentGameState: UI components re-initialized for MAIN_MENU.");
            } catch (Exception e) {
                System.err.println("Error re-initializing components for MAIN_MENU: " + e.getMessage());
                e.printStackTrace();
            }
            refreshAvailableSaveFiles();
        } else if (newState == GameState.IN_GAME) {
            // This state is typically entered via createNewWorld() or loadGame(),
            // which already call initializeGameWorldCommonLogic().
            if (map == null || player == null) {
                System.err.println("setCurrentGameState: Attempting to enter IN_GAME without map/player. Forcing MAIN_MENU.");
                this.currentGameState = GameState.MAIN_MENU; // Revert
                refreshAvailableSaveFiles(); // This will re-setup menu
                return;
            }
            // If initializeGameWorldCommonLogic wasn't called by create/load (e.g. direct state set)
            // or if renderer is not set up for the game world.
            if (renderer == null || renderer.getMap() != map || lightManager == null) {
                System.out.println("setCurrentGameState: IN_GAME state set, ensuring common logic is initialized/updated.");
                initializeGameWorldCommonLogic();
            }

            if (mouseHandler != null) mouseHandler.resetLeftMouseDragFlags();
            if (cameraManager != null && player != null && inputHandler != null) {
                float[] focusPoint = inputHandler.calculateCameraFocusPoint(player.getMapCol(), player.getMapRow());
                cameraManager.setTargetPositionInstantly(focusPoint[0], focusPoint[1]);
            }
        }
    }


    public int getCurrentRenderDistanceChunks() { return currentRenderDistanceChunks; }
    public void increaseRenderDistance() { currentRenderDistanceChunks = Math.min(currentRenderDistanceChunks + 1, RENDER_DISTANCE_CHUNKS_MAX); }
    public void decreaseRenderDistance() { currentRenderDistanceChunks = Math.max(RENDER_DISTANCE_CHUNKS_MIN, currentRenderDistanceChunks - 1); }

    public int getSelectedHotbarSlotIndex() {
        return (player != null) ? player.getSelectedHotbarSlotIndex() : 0;
    }

    public void setSelectedHotbarSlotIndex(int index) {
        if (player != null) {
            player.setSelectedHotbarSlotIndex(index);
            if (renderer != null) renderer.setHotbarDirty(true);
        }
    }

    private void renderGame() {
        if (renderer == null || lightManager == null || map == null || player == null || cameraManager == null) {
            System.err.println("Game.renderGame: Critical component is null. Skipping render. CurrentState: " + currentGameState);
            glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            return;
        }

        float rSky, gSky, bSky;
        float lightRange = (float)(SKY_LIGHT_DAY - SKY_LIGHT_NIGHT_MINIMUM);
        float lightRatio = 0.5f;
        if (lightRange > 0.001f) {
            lightRatio = (float)(lightManager.getCurrentGlobalSkyLightTarget() - SKY_LIGHT_NIGHT_MINIMUM) / lightRange;
        }
        lightRatio = Math.max(0, Math.min(1, lightRatio));

        float dayR = 0.5f, dayG = 0.7f, dayB = 1.0f;
        float nightR = 0.02f, nightG = 0.02f, nightB = 0.08f;

        rSky = nightR + (dayR - nightR) * lightRatio;
        gSky = nightG + (dayG - nightG) * lightRatio;
        bSky = nightB + (dayB - nightB) * lightRatio;

        glClearColor(rSky, gSky, bSky, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glEnable(GL_DEPTH_TEST);
        renderer.render();

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        if (this.showInventory) renderer.renderInventoryAndCraftingUI(player);
        if (this.showHotbar) renderer.renderHotbar(player, player.getSelectedHotbarSlotIndex());

        if (this.showDebugOverlay && inputHandler != null) {
            List<String> debugLines = new ArrayList<>();
            debugLines.add(String.format("FPS: %.1f", displayedFps));
            debugLines.add(String.format("Time: %.3f, SkyLight (Actual/Target): %d/%d", pseudoTimeOfDay, currentGlobalSkyLightActual, lightManager.getCurrentGlobalSkyLightTarget()));
            debugLines.add("Player: (" + player.getTileRow() + "," + player.getTileCol() + ") V(" + String.format("%.1f",player.getVisualRow()) + "," + String.format("%.1f",player.getVisualCol()) + ") A:" + player.getCurrentAction() + " D:" + player.getCurrentDirection());
            if (currentWorldName != null && map != null) debugLines.add("World: " + currentWorldName + " (Seed: " + map.getWorldSeed() + ")");
            else debugLines.add("World: (Unsaved/New)");

            Tile selectedTile = (map != null) ? map.getTile(inputHandler.getSelectedRow(), inputHandler.getSelectedCol()) : null;
            String selectedInfo = "Sel: ("+inputHandler.getSelectedRow()+","+inputHandler.getSelectedCol()+")";
            if(selectedTile!=null) selectedInfo += " E:"+selectedTile.getElevation()+" T:"+selectedTile.getType()+" SL:"+selectedTile.getSkyLightLevel()+" BL:"+selectedTile.getBlockLightLevel()+" FL:"+selectedTile.getFinalLightLevel() + (selectedTile.hasTorch()?" (T)":"") + " Tree:" + selectedTile.getTreeType().name();
            debugLines.add(selectedInfo);
            debugLines.add(String.format("Cam: (%.1f,%.1f) Z:%.2f RD:%d AC:%d SkyQ:%d", cameraManager.getCameraX(),cameraManager.getCameraY(),cameraManager.getZoom(), currentRenderDistanceChunks, currentlyActiveLogicalChunks.size(), globalSkyRefreshNeededQueue.size()));
            if (lightManager != null) {
                debugLines.add("RndQ: " + chunkRenderUpdateQueue.size() + " LightQ (SP,SR,BP,BR): " + lightManager.getSkyLightPropagationQueueSize() + "," + lightManager.getSkyLightRemovalQueueSize() + "," + lightManager.getBlockLightPropagationQueueSize() + "," + lightManager.getBlockLightRemovalQueueSize());
            }
            debugLines.add("Hotbar Sel: " + player.getSelectedHotbarSlotIndex() + (showInventory ? " InvShow" : ""));
            renderer.renderDebugOverlay(10f, 10f, 1300f, 300f, debugLines);
        }
        glEnable(GL_DEPTH_TEST);
    }

    public void requestFullMapRegeneration() {
        System.out.println("Game: Full map regeneration requested (new world with new seed).");
        if (renderer != null && renderer.getMap() != null && currentlyActiveLogicalChunks != null) { // Check if renderer has an active map
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
        if (renderer != null && CHUNK_SIZE_TILES > 0 && map != null) {
            int chunkX = Math.floorDiv(c, CHUNK_SIZE_TILES);
            int chunkY = Math.floorDiv(r, CHUNK_SIZE_TILES);
            LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(chunkX, chunkY);
            if (!chunkRenderUpdateQueue.contains(coord) && currentlyActiveLogicalChunks.contains(coord)) {
                chunkRenderUpdateQueue.offer(coord);
            }
        }
    }
    public boolean isShowHotbar() { return this.showHotbar; }

    public void interactWithTree(int r, int c, PlayerModel playerWhoActed, Item toolUsed) {
        if (map == null || playerWhoActed == null) return;

        Tile targetTile = map.getTile(r, c);
        if (targetTile == null || targetTile.getTreeType() == Tile.TreeVisualType.NONE) return;

        boolean usedAxe = toolUsed != null && toolUsed.equals(ItemRegistry.CRUDE_AXE);
        if (usedAxe) {
            targetTile.setTreeType(Tile.TreeVisualType.NONE);
            if (ItemRegistry.WOOD != null) playerWhoActed.addItemToInventory(ItemRegistry.WOOD, 3);
        } else {
            if (ItemRegistry.STICK != null) playerWhoActed.addItemToInventory(ItemRegistry.STICK, 1);
        }
        map.markChunkAsModified(Math.floorDiv(c, CHUNK_SIZE_TILES), Math.floorDiv(r, CHUNK_SIZE_TILES));
        if (lightManager != null) map.queueLightUpdateForArea(r, c, 1, lightManager);
        requestTileRenderUpdate(r,c);
    }


    public boolean isDraggingItem() {
        return this.isDraggingItem;
    }

    public InventorySlot getDraggedItemStack() {
        return this.draggedItemStack;
    }
    /**
     * Called by MouseHandler when a drag begins.
     * @param slotIndex The inventory slot index where the drag started.
     */
    public void startDraggingItem(int slotIndex) {
        if (player == null || slotIndex < 0 || slotIndex >= player.getInventorySlots().size()) {
            return;
        }
        InventorySlot sourceSlot = player.getInventorySlots().get(slotIndex);
        if (sourceSlot.isEmpty()) {
            return; // Cannot drag an empty slot
        }

        this.draggedItemStack = new InventorySlot();
        this.draggedItemStack.addItem(sourceSlot.getItem(), sourceSlot.getQuantity()); // Create a copy
        this.originalDragSlotIndex = slotIndex;
        this.isDraggingItem = true;

        sourceSlot.clearSlot(); // Remove item from the original slot
        setHotbarDirty(true); // Mark UI for redraw
    }

    /**
     * Called by MouseHandler when the mouse button is released.
     * @param dropSlotIndex The inventory slot index where the item was dropped, or -1 if outside.
     */
    public void stopDraggingItem(int dropSlotIndex) {
        if (!isDraggingItem || player == null) {
            isDraggingItem = false;
            return;
        }

        List<InventorySlot> slots = player.getInventorySlots();

        // Case 1: Dropped on a valid slot
        if (dropSlotIndex >= 0 && dropSlotIndex < slots.size()) {
            InventorySlot targetSlot = slots.get(dropSlotIndex);

            // If target slot is empty, just place the item there.
            if (targetSlot.isEmpty()) {
                targetSlot.addItem(draggedItemStack.getItem(), draggedItemStack.getQuantity());
            } else {
                // If target slot is not empty, swap the items.
                // Store the target's items temporarily.
                Item tempItem = targetSlot.getItem();
                int tempQuantity = targetSlot.getQuantity();

                // Place dragged item in the target slot.
                targetSlot.clearSlot();
                targetSlot.addItem(draggedItemStack.getItem(), draggedItemStack.getQuantity());

                // Place the target's original items back in the source slot.
                slots.get(originalDragSlotIndex).addItem(tempItem, tempQuantity);
            }
        }
        // Case 2: Dropped outside the inventory or on an invalid slot
        else {
            // Return the item to its original slot.
            player.getInventorySlots().get(originalDragSlotIndex).addItem(draggedItemStack.getItem(), draggedItemStack.getQuantity());
        }

        // Reset drag state
        this.isDraggingItem = false;
        this.draggedItemStack = null;
        this.originalDragSlotIndex = -1;
        setHotbarDirty(true); // Mark UI for redraw
    }

    // Helper method to mark hotbar for redraw, if it's not already in Game.java
    public void setHotbarDirty(boolean dirty) {
        if (renderer != null) {
            renderer.setHotbarDirty(true);
        }
    }


    public boolean isShowDebugOverlay() { return this.showDebugOverlay; }
    public void toggleShowDebugOverlay() { this.showDebugOverlay = !this.showDebugOverlay; }

    private void cleanup() {
        System.out.println("Game cleanup initiated...");
        if (renderer != null) renderer.cleanup();
        // If InputHandler or MouseHandler held any specific resources that need freeing (unlikely for these classes),
        // they would also have cleanup methods called here.
        System.out.println("Game cleanup complete.");
    }

    public long getWindowHandle() {
        return this.window;
    }

    public LightManager getLightManager() {
        return (map != null) ? map.getLightManager() : null;
    }}
