package org.isogame.game;

import org.isogame.constants.Constants;
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
import java.util.HashSet;
import java.util.Iterator;
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
// import java.util.Arrays; // Not used
import java.util.List;
import java.util.Set;
import java.util.Queue;
import java.util.LinkedList;
// import java.util.stream.Collectors; // Not used
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
    private byte currentGlobalSkyLightActual;
    private byte lastGlobalSkyLightTargetSetInLM;

    private int framesRenderedThisSecond = 0;
    private double timeAccumulatorForFps = 0.0;
    private double displayedFps = 0.0;

    private int selectedInventorySlotIndex = 0;
    private int currentRenderDistanceChunks = Constants.RENDER_DISTANCE_CHUNKS_DEFAULT;
    private boolean showInventory = false;

    private final Queue<LightManager.ChunkCoordinate> chunkRenderUpdateQueue = new LinkedList<>();
    private static final int MAX_CHUNK_GEOMETRY_UPDATES_PER_FRAME = 2; // Can be tuned

    private final Queue<LightManager.ChunkCoordinate> globalSkyRefreshNeededQueue = new LinkedList<>();
    private static final int CHUNKS_TO_REFRESH_SKY_PER_FRAME = 2; // How many chunks get full sky refresh per frame

    public enum GameState { MAIN_MENU, IN_GAME }
    private GameState currentGameState = GameState.MAIN_MENU;
    private String currentWorldName = null;
    private List<String> availableSaveFiles = new ArrayList<>();
    private static final String SAVES_DIRECTORY = "saves";
    private boolean showHotbar = true;
    private List<MenuItemButton> menuButtons = new ArrayList<>();
    private Set<LightManager.ChunkCoordinate> currentlyActiveLogicalChunks = new HashSet<>();

    public Game(long window, int initialFramebufferWidth, int initialScreenHeight) {
        this.window = window;
        new File(SAVES_DIRECTORY).mkdirs();

        map = new Map(MAP_WIDTH, MAP_HEIGHT);
        lightManager = map.getLightManager();
        player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
        cameraManager = new CameraManager(initialFramebufferWidth, initialScreenHeight, map.getWidth(), map.getHeight());
        inputHandler = new InputHandler(window, cameraManager, map, player, this);
        inputHandler.registerCallbacks(this::requestFullMapRegeneration);
        renderer = new Renderer(cameraManager, map, player, inputHandler);
        mouseHandler = new MouseHandler(window, cameraManager, map, inputHandler, player, this);

        glfwSetFramebufferSizeCallback(window, (win, fbW, fbH) -> {
            if (cameraManager != null) cameraManager.updateScreenSize(fbW, fbH);
            if (renderer != null) renderer.onResize(fbW, fbH);
            if (currentGameState == GameState.MAIN_MENU) setupMainMenuButtons();
        });

        if (renderer != null) renderer.onResize(initialFramebufferWidth, initialScreenHeight);

        this.currentGlobalSkyLightActual = calculateSkyLightForTime(pseudoTimeOfDay);
        this.lightManager.setCurrentGlobalSkyLightTarget(this.currentGlobalSkyLightActual);
        this.lastGlobalSkyLightTargetSetInLM = this.currentGlobalSkyLightActual;

        initializeNewGameCommonLogic();
        currentGameState = GameState.MAIN_MENU;
        refreshAvailableSaveFiles();
        setupMainMenuButtons();
    }

    // ... (UI methods like getMainMenuButtons, setupMainMenuButtons as before) ...
    public List<MenuItemButton> getMainMenuButtons() { return menuButtons; }

    private void setupMainMenuButtons() {
        menuButtons.clear();
        if (cameraManager == null) return;

        float buttonWidth = 300f;
        float buttonHeight = 40f;
        float buttonSpacing = 15f;
        float currentY = cameraManager.getScreenHeight() * 0.25f;
        float screenCenterX = cameraManager.getScreenWidth() / 2f;
        float buttonX = screenCenterX - buttonWidth / 2f;

        MenuItemButton newWorldButton = new MenuItemButton(buttonX, currentY, buttonWidth, buttonHeight, "Create New World", "NEW_WORLD", null);
        newWorldButton.setTextureAtlasUVs(
                Renderer.ROCK_ATLAS_U0, Renderer.ROCK_ATLAS_V0,
                Renderer.ROCK_ATLAS_U1, Renderer.ROCK_ATLAS_V1
        );
        newWorldButton.baseTextColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        newWorldButton.hoverTextColor = new float[]{0.9f, 0.9f, 0.7f, 1.0f};
        menuButtons.add(newWorldButton);
        currentY += buttonHeight + buttonSpacing;

        if (availableSaveFiles.isEmpty()) {
            currentY += buttonHeight + buttonSpacing;
        } else {
            float worldButtonWidth = buttonWidth * 0.75f;
            float deleteButtonWidth = buttonWidth * 0.20f;
            float totalPairWidth = worldButtonWidth + deleteButtonWidth + 5f;
            float worldButtonX = screenCenterX - totalPairWidth / 2f;
            float deleteButtonX = worldButtonX + worldButtonWidth + 5f;

            for (String worldFile : availableSaveFiles) {
                String worldNameDisplay = worldFile.replace(".json", "");
                MenuItemButton loadButton = new MenuItemButton(worldButtonX, currentY, worldButtonWidth, buttonHeight, "Load: " + worldNameDisplay, "LOAD_WORLD", worldFile);
                loadButton.setTextureAtlasUVs(
                        Renderer.DEFAULT_SIDE_U0, Renderer.DEFAULT_SIDE_V0,
                        Renderer.DEFAULT_SIDE_U1, Renderer.DEFAULT_SIDE_V1
                );
                loadButton.baseTextColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
                loadButton.hoverTextColor = new float[]{0.8f, 0.9f, 0.7f, 1.0f};
                menuButtons.add(loadButton);

                MenuItemButton deleteButton = new MenuItemButton(deleteButtonX, currentY, deleteButtonWidth, buttonHeight, "DEL", "DELETE_WORLD", worldNameDisplay);
                deleteButton.setTextureAtlasUVs(
                        Renderer.SAND_ATLAS_U0, Renderer.SAND_ATLAS_V0,
                        Renderer.SAND_ATLAS_U1, Renderer.SAND_ATLAS_V1
                );
                deleteButton.setCustomColors(
                        new float[]{0.5f, 0.25f, 0.15f, 0.0f},
                        new float[]{0.6f, 0.3f, 0.2f, 0.0f},
                        new float[]{0.2f, 0.1f, 0.05f, 1.0f},
                        new float[]{0.6f, 0.1f, 0.05f, 1.0f},
                        new float[]{0.8f, 0.2f, 0.1f, 1.0f}
                );
                menuButtons.add(deleteButton);
                currentY += buttonHeight + buttonSpacing / 2;
            }
        }
        currentY += buttonSpacing;

        MenuItemButton exitButton = new MenuItemButton(buttonX, currentY, buttonWidth, buttonHeight, "Exit Game", "EXIT_GAME", null);
        exitButton.setTextureAtlasUVs(
                Renderer.ROCK_ATLAS_U0, Renderer.ROCK_ATLAS_V0,
                Renderer.ROCK_ATLAS_U1, Renderer.ROCK_ATLAS_V1
        );
        exitButton.baseTextColor = new float[]{0.9f, 0.88f, 0.82f, 1.0f};
        exitButton.hoverTextColor = new float[]{1.0f, 0.98f, 0.92f, 1.0f};
        menuButtons.add(exitButton);
    }

    private void performIntensiveInitialLightProcessing() {
        System.out.println("Performing intensive initial light calculation for starting area...");
        int initialLightSettlingPasses = Math.max(25, currentlyActiveLogicalChunks.size() + 15); // More passes
        int chunksToRefreshPerInitialPass = Math.max(CHUNKS_TO_REFRESH_SKY_PER_FRAME * 3, 6); // Refresh more chunks per pass at start

        globalSkyRefreshNeededQueue.clear();
        globalSkyRefreshNeededQueue.addAll(currentlyActiveLogicalChunks);
        // System.out.println("Intensive initial light: " + globalSkyRefreshNeededQueue.size() + " chunks queued for sky refresh.");

        for (int i = 0; i < initialLightSettlingPasses; i++) {
            int refreshedThisPass = 0;
            while (refreshedThisPass < chunksToRefreshPerInitialPass && !globalSkyRefreshNeededQueue.isEmpty()) {
                LightManager.ChunkCoordinate coordToRefresh = globalSkyRefreshNeededQueue.poll();
                if (coordToRefresh != null && currentlyActiveLogicalChunks.contains(coordToRefresh)) {
                    lightManager.refreshSkyLightForSingleChunk(coordToRefresh, lightManager.getCurrentGlobalSkyLightTarget());
                }
                refreshedThisPass++;
            }
            lightManager.processLightQueuesIncrementally(LightManager.BATCH_LIGHT_UPDATE_BUDGET); // Large budget

            Set<LightManager.ChunkCoordinate> dirtyFromThisPass = lightManager.getDirtyChunksAndClear();
            for (LightManager.ChunkCoordinate dirtyCoord : dirtyFromThisPass) {
                if (!chunkRenderUpdateQueue.contains(dirtyCoord) && currentlyActiveLogicalChunks.contains(dirtyCoord)) {
                    chunkRenderUpdateQueue.offer(dirtyCoord);
                }
            }
            int geomUpdates = 0;
            while(!chunkRenderUpdateQueue.isEmpty() && geomUpdates < MAX_CHUNK_GEOMETRY_UPDATES_PER_FRAME * 4) { // More geom updates too
                LightManager.ChunkCoordinate coordToUpdate = chunkRenderUpdateQueue.poll();
                if (coordToUpdate != null && currentlyActiveLogicalChunks.contains(coordToUpdate)) {
                    renderer.updateChunkByGridCoords(coordToUpdate.chunkX, coordToUpdate.chunkY);
                    geomUpdates++;
                } else if (coordToUpdate == null) break;
            }

            if (!lightManager.isAnyLightQueueNotEmpty() && globalSkyRefreshNeededQueue.isEmpty() && i > (initialLightSettlingPasses / 2)) {
                // System.out.println("Intensive initial light settled early in " + (i + 1) + " passes.");
                break;
            }
        }
        // Final sweep
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
                if (coordToUpdate != null && currentlyActiveLogicalChunks.contains(coordToUpdate)) {
                    renderer.updateChunkByGridCoords(coordToUpdate.chunkX, coordToUpdate.chunkY);
                    geomUpdates++;
                } else break;
            }
            if(!lightManager.isAnyLightQueueNotEmpty() && globalSkyRefreshNeededQueue.isEmpty()) break;
            finalSweepCount++;
        }
        // System.out.println("Intensive initial light calculation complete. Render Q: " + chunkRenderUpdateQueue.size());
    }

    private void initializeNewGameCommonLogic() {
        System.out.println("Initializing new game common logic...");
        map.generateMap();
        pseudoTimeOfDay = 0.0005;
        currentGlobalSkyLightActual = calculateSkyLightForTime(pseudoTimeOfDay);
        lightManager.setCurrentGlobalSkyLightTarget(currentGlobalSkyLightActual);
        lastGlobalSkyLightTargetSetInLM = currentGlobalSkyLightActual;

        if (player != null) {
            player.setPosition(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
            player.getInventorySlots().forEach(InventorySlot::clearSlot);
            player.setSelectedHotbarSlotIndex(0);
        }
        if (cameraManager != null && player != null) {
            cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
        }

        currentlyActiveLogicalChunks.clear();
        globalSkyRefreshNeededQueue.clear();
        updateActiveChunksAroundPlayer();

        performIntensiveInitialLightProcessing();
        System.out.println("New game environment initialized. Player at: " + player.getTileRow() + "," + player.getTileCol());
    }

    // ... (getDesiredActiveChunkCoordinates, updateActiveChunksAroundPlayer, initOpenGL, gameLoop, UI methods, etc. as in previous response)
    private List<LightManager.ChunkCoordinate> getDesiredActiveChunkCoordinates() {
        List<LightManager.ChunkCoordinate> desiredActive = new ArrayList<>();
        if (player == null || map == null || CHUNK_SIZE_TILES <= 0) return desiredActive;
        int playerChunkX = player.getTileCol() / CHUNK_SIZE_TILES;
        int playerChunkY = player.getTileRow() / CHUNK_SIZE_TILES;
        int renderDist = getCurrentRenderDistanceChunks();
        int maxChunkX = (map.getWidth() - 1) / CHUNK_SIZE_TILES; int minChunkX = 0;
        int maxChunkY = (map.getHeight() - 1) / CHUNK_SIZE_TILES; int minChunkY = 0;
        for (int dy = -renderDist; dy <= renderDist; dy++) {
            for (int dx = -renderDist; dx <= renderDist; dx++) {
                int cx = playerChunkX + dx; int cy = playerChunkY + dy;
                if (cx >= minChunkX && cx <= maxChunkX && cy >= minChunkY && cy <= maxChunkY) {
                    desiredActive.add(new LightManager.ChunkCoordinate(cx, cy));
                }
            }
        }
        return desiredActive;
    }

    private void updateActiveChunksAroundPlayer() {
        if (renderer == null || lightManager == null || map == null) return;
        List<LightManager.ChunkCoordinate> desiredCoords = getDesiredActiveChunkCoordinates();
        Set<LightManager.ChunkCoordinate> desiredSet = new HashSet<>(desiredCoords);
        boolean chunksStateChanged = false;

        Iterator<LightManager.ChunkCoordinate> iterator = currentlyActiveLogicalChunks.iterator();
        while (iterator.hasNext()) {
            LightManager.ChunkCoordinate currentActiveCoord = iterator.next();
            if (!desiredSet.contains(currentActiveCoord)) {
                renderer.unloadChunkGraphics(currentActiveCoord.chunkX, currentActiveCoord.chunkY);
                iterator.remove();
                globalSkyRefreshNeededQueue.remove(currentActiveCoord);
                chunksStateChanged = true;
            }
        }

        for (LightManager.ChunkCoordinate newCoord : desiredCoords) {
            if (!currentlyActiveLogicalChunks.contains(newCoord)) {
                renderer.ensureChunkGraphicsLoaded(newCoord.chunkX, newCoord.chunkY);
                lightManager.initializeSkylightForChunk(newCoord);
                currentlyActiveLogicalChunks.add(newCoord);
                if (!globalSkyRefreshNeededQueue.contains(newCoord)) {
                    globalSkyRefreshNeededQueue.offer(newCoord);
                }
                chunksStateChanged = true;

                int[] dNeighborsX = {0, 0, 1, -1, 1, -1, 1, -1};
                int[] dNeighborsY = {1, -1, 0, 0, 1, 1, -1, -1};
                for (int i = 0; i < 8; i++) {
                    LightManager.ChunkCoordinate neighborCoord = new LightManager.ChunkCoordinate(
                            newCoord.chunkX + dNeighborsX[i], newCoord.chunkY + dNeighborsY[i]);
                    if (currentlyActiveLogicalChunks.contains(neighborCoord) && !neighborCoord.equals(newCoord)) {
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

        if (significantChange || boundaryReached) {
            if (lightManager != null) {
                lightManager.setCurrentGlobalSkyLightTarget(currentGlobalSkyLightActual);
                for (LightManager.ChunkCoordinate activeCoord : currentlyActiveLogicalChunks) {
                    if (!globalSkyRefreshNeededQueue.contains(activeCoord)) {
                        globalSkyRefreshNeededQueue.offer(activeCoord);
                    }
                }
            }
            lastGlobalSkyLightTargetSetInLM = currentGlobalSkyLightActual;
        }
    }

    private void updateGameLogic(double deltaTime) {
        pseudoTimeOfDay += deltaTime * DAY_NIGHT_CYCLE_SPEED;
        if (pseudoTimeOfDay >= 1.0) pseudoTimeOfDay -= 1.0;

        updateActiveChunksAroundPlayer();
        updateSkyLightBasedOnTimeOfDay(deltaTime);

        if (lightManager != null && !globalSkyRefreshNeededQueue.isEmpty()) {
            int refreshedThisFrame = 0;
            while (refreshedThisFrame < CHUNKS_TO_REFRESH_SKY_PER_FRAME && !globalSkyRefreshNeededQueue.isEmpty()) {
                LightManager.ChunkCoordinate coordToRefresh = globalSkyRefreshNeededQueue.poll();
                if (coordToRefresh != null && currentlyActiveLogicalChunks.contains(coordToRefresh)) {
                    lightManager.refreshSkyLightForSingleChunk(coordToRefresh, lightManager.getCurrentGlobalSkyLightTarget());
                    // Optional: A small, focused light processing pass for just this chunk or its borders
                    // lightManager.processLightQueuesIncrementally(LightManager.BATCH_LIGHT_UPDATE_BUDGET / (CHUNKS_TO_REFRESH_SKY_PER_FRAME * 2) );
                }
                refreshedThisFrame++;
            }
        }

        if (inputHandler != null) inputHandler.handleContinuousInput(deltaTime);
        if (player != null) player.update(deltaTime);
        if (cameraManager != null) cameraManager.update(deltaTime);

        if (lightManager != null) {
            lightManager.processLightQueuesIncrementally(); // Regular incremental update
            Set<LightManager.ChunkCoordinate> newlyDirtyChunksFromLight = lightManager.getDirtyChunksAndClear();
            if (!newlyDirtyChunksFromLight.isEmpty()) {
                for (LightManager.ChunkCoordinate coord : newlyDirtyChunksFromLight) {
                    if (!chunkRenderUpdateQueue.contains(coord) && currentlyActiveLogicalChunks.contains(coord)) {
                        chunkRenderUpdateQueue.offer(coord);
                    }
                }
            }
        }

        if (renderer != null && !chunkRenderUpdateQueue.isEmpty()) {
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
        if (worldNameOrFileName == null || worldNameOrFileName.trim().isEmpty()) { System.err.println("LoadGame Error: World name is null or empty."); return false; }
        String fileName = worldNameOrFileName.endsWith(".json") ? worldNameOrFileName : worldNameOrFileName + ".json";
        String filePath = Paths.get(SAVES_DIRECTORY, fileName).toString();
        System.out.println("Attempting to load game from: " + filePath);

        Gson gson = new Gson(); GameSaveState saveState;
        try (Reader reader = new FileReader(filePath)) { saveState = gson.fromJson(reader, GameSaveState.class); }
        catch (IOException e) { System.err.println("Load game: Save file not found or unreadable - " + e.getMessage()); return false; }

        if (saveState == null || saveState.mapData == null || saveState.playerData == null) { System.err.println("Load game: Parsed save data is null or incomplete."); return false; }

        String baseWorldName = fileName.replace(".json", "");
        System.out.println("Restoring game state for world: " + baseWorldName);
        this.currentWorldName = baseWorldName;

        this.pseudoTimeOfDay = saveState.pseudoTimeOfDay;
        this.currentGlobalSkyLightActual = calculateSkyLightForTime(this.pseudoTimeOfDay);
        this.lightManager.setCurrentGlobalSkyLightTarget(this.currentGlobalSkyLightActual);
        this.lastGlobalSkyLightTargetSetInLM = this.currentGlobalSkyLightActual;

        if (map == null || player == null || lightManager == null || cameraManager == null || renderer == null) { System.err.println("LoadGame Critical: Core components are null."); return false; }

        if(renderer != null) {
            for(LightManager.ChunkCoordinate coord : currentlyActiveLogicalChunks) {
                renderer.unloadChunkGraphics(coord.chunkX, coord.chunkY);
            }
        }
        currentlyActiveLogicalChunks.clear();
        globalSkyRefreshNeededQueue.clear();


        if (!map.loadState(saveState.mapData)) { System.err.println("Failed to load map state. Aborting load."); this.currentWorldName = null; return false; }
        if (!player.loadState(saveState.playerData)) { System.err.println("Failed to load player state. Player state may be inconsistent.");}

        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());

        System.out.println("Re-initializing active chunks, lighting and rendering for loaded game...");
        updateActiveChunksAroundPlayer();

        performIntensiveInitialLightProcessing();

        for(LightManager.ChunkCoordinate activeChunk : currentlyActiveLogicalChunks) {
            int startR = activeChunk.chunkY * CHUNK_SIZE_TILES; int endR = Math.min(startR + CHUNK_SIZE_TILES, map.getHeight());
            int startC = activeChunk.chunkX * CHUNK_SIZE_TILES; int endC = Math.min(startC + CHUNK_SIZE_TILES, map.getWidth());
            for (int r = startR; r < endR; r++) {
                for (int c = startC; c < endC; c++) {
                    if (!map.isValid(r,c)) continue;
                    Tile tile = map.getTile(r, c);
                    if (tile != null && tile.hasTorch()) {
                        lightManager.addLightSource(r, c, (byte) TORCH_LIGHT_LEVEL);
                    }
                }
            }
        }
        for(int i=0; i<10; i++) {
            lightManager.processLightQueuesIncrementally(LightManager.BATCH_LIGHT_UPDATE_BUDGET);
            if (!lightManager.isAnyLightQueueNotEmpty() && i > 3) break;
        }

        System.out.println("Game loaded successfully: " + this.currentWorldName);
        setCurrentGameState(GameState.IN_GAME);
        refreshAvailableSaveFiles();
        return true;
    }
    // ... (rest of Game.java methods as previously provided and correct) ...
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
        if (mouseHandler != null && cameraManager != null) {
            double[] xpos = new double[1];
            double[] ypos = new double[1];
            glfwGetCursorPos(window, xpos, ypos);
            int[] fbWidth = new int[1]; int[] fbHeight = new int[1]; glfwGetFramebufferSize(window, fbWidth, fbHeight);
            int[] winWidth = new int[1]; int[] winHeight = new int[1]; glfwGetWindowSize(window, winWidth, winHeight);
            double scaleX = (fbWidth[0] > 0 && winWidth[0] > 0) ? (double) fbWidth[0] / winWidth[0] : 1.0;
            double scaleY = (fbHeight[0] > 0 && winHeight[0] > 0) ? (double) fbHeight[0] / winHeight[0] : 1.0;
            float physicalMouseX = (float) (xpos[0] * scaleX);
            float physicalMouseY = (float) (ypos[0] * scaleY);
            for (MenuItemButton button : menuButtons) {
                if (button.isVisible) button.isHovered = button.isMouseOver(physicalMouseX, physicalMouseY);
                else button.isHovered = false;
            }
        }
    }
    // Inside the Game class:
    // Inside the Game class
    private byte calculateSkyLightForTime(double time) {
        byte newSkyLight;
        if (time >= 0.0 && time < 0.40) newSkyLight = SKY_LIGHT_DAY;
        else if (time >= 0.40 && time < 0.60) { // Sunset
            float phase = (float) ((time - 0.40) / 0.20);
            newSkyLight = (byte) (SKY_LIGHT_DAY - phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        } else if (time >= 0.60 && time < 0.90) newSkyLight = SKY_LIGHT_NIGHT; // Night
        else { // Sunrise
            float phase = (float) ((time - 0.90) / 0.10);
            newSkyLight = (byte) (SKY_LIGHT_NIGHT + phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        }
        return (byte) Math.max(SKY_LIGHT_NIGHT_MINIMUM, Math.min(MAX_LIGHT_LEVEL, newSkyLight));
    }
    public void toggleHotbar() {
        this.showHotbar = !this.showHotbar;
    }

    private void renderMainMenu() {
        glClearColor(0.05f, 0.05f, 0.1f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        if (renderer != null && cameraManager != null) {
            glDisable(GL_DEPTH_TEST); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            renderer.renderMainMenuBackground();
            if (renderer.getTitleFont() != null && renderer.getTitleFont().isInitialized()) {
                String title = "PLUNARI";
                Font currentTitleFont = renderer.getTitleFont();
                float titleWidth = currentTitleFont.getTextWidthScaled(title, 1.0f);
                currentTitleFont.drawText(
                        cameraManager.getScreenWidth() / 2f - titleWidth / 2f,
                        cameraManager.getScreenHeight() * 0.15f,
                        title, 0.9f, 0.85f, 0.7f);
            }
            for (MenuItemButton button : menuButtons) {
                if (button.isVisible) renderer.renderMenuButton(button);
            }
            glEnable(GL_DEPTH_TEST);
        }
    }

    public void createNewWorld() {
        int nextWorldNum = 1;
        while (Files.exists(Paths.get(SAVES_DIRECTORY, "World" + nextWorldNum + ".json"))) {
            nextWorldNum++;
        }
        String newWorldName = "World" + nextWorldNum;
        System.out.println("Creating new world: " + newWorldName);

        if(renderer != null) {
            for(LightManager.ChunkCoordinate coord : currentlyActiveLogicalChunks) {
                renderer.unloadChunkGraphics(coord.chunkX, coord.chunkY);
            }
        }
        currentlyActiveLogicalChunks.clear();
        globalSkyRefreshNeededQueue.clear();


        initializeNewGameCommonLogic();
        this.currentWorldName = newWorldName;
        saveGame(this.currentWorldName);
        refreshAvailableSaveFiles();
        setCurrentGameState(GameState.IN_GAME);
    }

    public void deleteWorld(String worldName) {
        if (worldName == null || worldName.isEmpty()) return;
        String fileName = worldName.endsWith(".json") ? worldName : worldName + ".json";
        File saveFile = new File(SAVES_DIRECTORY, fileName);
        if (saveFile.exists()) {
            if (saveFile.delete()) {
                System.out.println("Deleted world: " + fileName);
                if (this.currentWorldName != null && (this.currentWorldName.equals(fileName.replace(".json", "")) || this.currentWorldName.equals(worldName))) {
                    this.currentWorldName = null;
                }
            } else { System.err.println("Failed to delete world: " + fileName); }
        } else { System.err.println("World file not found for deletion: " + fileName); }
        refreshAvailableSaveFiles();
        setupMainMenuButtons();
    }

    public void refreshAvailableSaveFiles() {
        availableSaveFiles.clear();
        File savesDir = new File(SAVES_DIRECTORY);
        if (savesDir.exists() && savesDir.isDirectory()) {
            File[] files = savesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files != null) {
                for (File file : files) { availableSaveFiles.add(file.getName()); }
                availableSaveFiles.sort(String::compareToIgnoreCase);
            }
        }
        if (currentGameState == GameState.MAIN_MENU) { setupMainMenuButtons(); }
    }

    public List<String> getAvailableSaveFiles() { return availableSaveFiles; }
    public String getCurrentWorldName() { return currentWorldName; }
    public void toggleInventory() { this.showInventory = !this.showInventory; }
    public boolean isInventoryVisible() { return this.showInventory; }
    public GameState getCurrentGameState() { return currentGameState; }

    public void setCurrentGameState(GameState newState) {
        System.out.println("Game state changing from " + this.currentGameState + " to " + newState);
        GameState oldState = this.currentGameState;
        this.currentGameState = newState;

        if (newState == GameState.MAIN_MENU) {
            if (oldState == GameState.IN_GAME && currentWorldName != null && !currentWorldName.isEmpty()) {
                saveGame(currentWorldName);
            }
            refreshAvailableSaveFiles();
        } else if (newState == GameState.IN_GAME) {
            if (mouseHandler != null) mouseHandler.resetLeftMouseDragFlags();
            if (cameraManager != null && player != null && inputHandler != null) {
                float[] focusPoint = inputHandler.calculateCameraFocusPoint(player.getMapCol(), player.getMapRow());
                cameraManager.setTargetPositionInstantly(focusPoint[0], focusPoint[1]);
            }
            if (oldState == GameState.MAIN_MENU && currentWorldName != null) {
                updateActiveChunksAroundPlayer();
                lightManager.setCurrentGlobalSkyLightTarget(this.currentGlobalSkyLightActual);
                globalSkyRefreshNeededQueue.clear();
                globalSkyRefreshNeededQueue.addAll(currentlyActiveLogicalChunks);
                performIntensiveInitialLightProcessing(); // Also do intensive lighting when returning to game
            }
        }
    }

    public int getCurrentRenderDistanceChunks() { return currentRenderDistanceChunks; }
    public void increaseRenderDistance() { currentRenderDistanceChunks = Math.min(currentRenderDistanceChunks + 1, RENDER_DISTANCE_CHUNKS_MAX); }
    public void decreaseRenderDistance() { currentRenderDistanceChunks = Math.max(RENDER_DISTANCE_CHUNKS_MIN, currentRenderDistanceChunks - 1); }
    public int getSelectedInventorySlotIndex() { return selectedInventorySlotIndex; }

    public void setSelectedInventorySlotIndex(int index) {
        if (player != null && player.getInventorySlots() != null &&
                index >= 0 && index < Constants.HOTBAR_SIZE &&
                index < player.getInventorySlots().size()) {
            if (this.selectedInventorySlotIndex != index) {
                this.selectedInventorySlotIndex = index;
                player.setSelectedHotbarSlotIndex(index);
                if (renderer != null) renderer.setHotbarDirty(true);
            }
        } else if (index == -1 && this.selectedInventorySlotIndex != -1) {
            this.selectedInventorySlotIndex = -1;
            if (renderer != null) renderer.setHotbarDirty(true);
        }
    }

    private void renderGame() {
        float rSky, gSky, bSky;
        float lightRange = (float)(SKY_LIGHT_DAY - SKY_LIGHT_NIGHT_MINIMUM);
        float lightRatio = 0.5f;
        if (lightRange > 0 && lightManager != null) {
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

        if (renderer != null) {
            glEnable(GL_DEPTH_TEST);
            renderer.render();

            glDisable(GL_DEPTH_TEST); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            if (this.showInventory && player != null) renderer.renderInventoryUI(player);
            if (this.showHotbar && player != null) renderer.renderHotbar(player, player.getSelectedHotbarSlotIndex());

            if (this.showDebugOverlay && player != null && map != null && cameraManager != null && inputHandler != null && lightManager != null) {
                List<String> debugLines = new ArrayList<>();
                debugLines.add(String.format("FPS: %.1f", displayedFps));
                debugLines.add(String.format("Time: %.3f, SkyLight (Actual/Target): %d/%d", pseudoTimeOfDay, currentGlobalSkyLightActual, lightManager.getCurrentGlobalSkyLightTarget()));
                debugLines.add("Player: (" + player.getTileRow() + "," + player.getTileCol() + ") A:" + player.getCurrentAction() + " D:" + player.getCurrentDirection());
                if (currentWorldName != null) debugLines.add("World: " + currentWorldName); else debugLines.add("World: (Unsaved/New)");
                Tile selectedTile = map.getTile(inputHandler.getSelectedRow(), inputHandler.getSelectedCol());
                String selectedInfo = "Sel: ("+inputHandler.getSelectedRow()+","+inputHandler.getSelectedCol()+")";
                if(selectedTile!=null) selectedInfo += " E:"+selectedTile.getElevation()+" T:"+selectedTile.getType()+" SL:"+selectedTile.getSkyLightLevel()+" BL:"+selectedTile.getBlockLightLevel()+" FL:"+selectedTile.getFinalLightLevel() + (selectedTile.hasTorch()?" (T)":"") + " Tree:" + selectedTile.getTreeType().name();
                debugLines.add(selectedInfo);
                debugLines.add(String.format("Cam: (%.1f,%.1f) Z:%.2f RD:%d AC:%d SkyQ:%d", cameraManager.getCameraX(),cameraManager.getCameraY(),cameraManager.getZoom(), currentRenderDistanceChunks, currentlyActiveLogicalChunks.size(), globalSkyRefreshNeededQueue.size()));
                debugLines.add("RndQ: " + chunkRenderUpdateQueue.size() + " LightQ (SP,SR,BP,BR): " + lightManager.getSkyLightPropagationQueueSize() + "," + lightManager.getSkyLightRemovalQueueSize() + "," + lightManager.getBlockLightPropagationQueueSize() + "," + lightManager.getBlockLightRemovalQueueSize());
                debugLines.add("Hotbar: " + player.getSelectedHotbarSlotIndex() + (showInventory ? " InvShow" : ""));
                renderer.renderDebugOverlay(10f, 10f, 1300f, 300f, debugLines);
            }
            glEnable(GL_DEPTH_TEST);
        }
    }

    public void requestFullMapRegeneration() {
        System.out.println("Game: Full map regeneration requested.");
        if(renderer != null) {
            for(LightManager.ChunkCoordinate coord : currentlyActiveLogicalChunks) {
                renderer.unloadChunkGraphics(coord.chunkX, coord.chunkY);
            }
        }
        currentlyActiveLogicalChunks.clear();
        globalSkyRefreshNeededQueue.clear();
        initializeNewGameCommonLogic();
        this.currentWorldName = null;
        System.out.println("Game: Full map regeneration processing complete. World is now unsaved.");
    }

    public void requestTileRenderUpdate(int r, int c) {
        if (renderer != null && CHUNK_SIZE_TILES > 0) {
            LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(c / CHUNK_SIZE_TILES, r / CHUNK_SIZE_TILES);
            if (!chunkRenderUpdateQueue.contains(coord) && currentlyActiveLogicalChunks.contains(coord)) {
                chunkRenderUpdateQueue.offer(coord);
            }
        }
    }
    public boolean isShowHotbar() { return this.showHotbar; }

    public void interactWithTree(int r, int c, PlayerModel playerWhoActed, Item toolUsed) {
        if (!map.isValid(r, c) || playerWhoActed == null) return;
        Tile targetTile = map.getTile(r, c);
        if (targetTile == null || targetTile.getTreeType() == Tile.TreeVisualType.NONE) return;
        boolean usedAxe = toolUsed != null && toolUsed.equals(ItemRegistry.CRUDE_AXE);
        if (usedAxe) {
            targetTile.setTreeType(Tile.TreeVisualType.NONE);
            if (ItemRegistry.WOOD != null) playerWhoActed.addItemToInventory(ItemRegistry.WOOD, 3);
        } else {
            if (ItemRegistry.STICK != null) playerWhoActed.addItemToInventory(ItemRegistry.STICK, 1);
        }
        map.queueLightUpdateForArea(r, c, 1, lightManager);
    }

    // In Game.java
    public void saveGame(String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) { System.err.println("SaveGame Error: World name is null or empty."); return; }
        String baseWorldName = worldName.replace(".json", "");
        String filePath = Paths.get(SAVES_DIRECTORY, baseWorldName + ".json").toString();

        if (player == null || map == null) { System.err.println("SaveGame Error: Player or Map is null."); return; }
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
        } catch (IOException e) { System.err.println("Error saving game: " + e.getMessage()); e.printStackTrace(); }
    }

    private boolean showDebugOverlay = true;
    public void toggleShowDebugOverlay() { this.showDebugOverlay = !this.showDebugOverlay; }

    private void cleanup() {
        System.out.println("Game cleanup initiated...");
        if (renderer != null) renderer.cleanup();
        System.out.println("Game cleanup complete.");
    }

    // Getter for LightManager, needed by Renderer for cliff lighting heuristic
    public LightManager getLightManager() {
        return this.lightManager;
    }
}