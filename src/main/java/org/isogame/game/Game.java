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
    private byte lastUpdatedSkyLightValueToLightManager;

    private int framesRenderedThisSecond = 0;
    private double timeAccumulatorForFps = 0.0;
    private double displayedFps = 0.0;

    private int selectedInventorySlotIndex = 0;
    private int currentRenderDistanceChunks = Constants.RENDER_DISTANCE_CHUNKS_DEFAULT;
    private boolean showInventory = false;

    private final Queue<LightManager.ChunkCoordinate> chunkRenderUpdateQueue = new LinkedList<>();
    private static final int MAX_CHUNK_GEOMETRY_UPDATES_PER_FRAME = 3;

    public enum GameState {
        MAIN_MENU,
        IN_GAME,
    }

    private GameState currentGameState = GameState.MAIN_MENU;
    private String currentWorldName = null;
    private List<String> availableSaveFiles = new ArrayList<>();
    private static final String SAVES_DIRECTORY = "saves";
    private boolean showHotbar = true;
    private List<MenuItemButton> menuButtons = new ArrayList<>();

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
            if (currentGameState == GameState.MAIN_MENU) {
                setupMainMenuButtons();
            }
        });

        if (renderer != null) renderer.onResize(initialFramebufferWidth, initialScreenHeight);

        this.currentGlobalSkyLightActual = calculateSkyLightForTime(pseudoTimeOfDay);
        this.lastUpdatedSkyLightValueToLightManager = (byte)(this.currentGlobalSkyLightActual + 1);

        initializeNewGameCommonLogic();
        currentGameState = GameState.MAIN_MENU;
        refreshAvailableSaveFiles();
        setupMainMenuButtons();
    }

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

    private void initializeNewGameCommonLogic() {
        System.out.println("Initializing new game common logic...");
        map.generateMap();
        pseudoTimeOfDay = 0.0005;
        currentGlobalSkyLightActual = calculateSkyLightForTime(pseudoTimeOfDay);
        lastUpdatedSkyLightValueToLightManager = (byte)(currentGlobalSkyLightActual -1);

        if (player != null) {
            player.setPosition(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
            if (player.getInventorySlots() != null) {
                player.getInventorySlots().forEach(InventorySlot::clearSlot);
            }
            player.setSelectedHotbarSlotIndex(0); // Reset hotbar
        }
        if (cameraManager != null && player != null) {
            cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
        }

        updateSkyLightBasedOnTimeOfDay(0.0);
        processInitialLightAndChunks();

        if (renderer != null) {
            renderer.uploadTileMapGeometry();
        }
        System.out.println("New game environment initialized.");
    }

    private List<LightManager.ChunkCoordinate> getActiveChunkCoordinates() {
        List<LightManager.ChunkCoordinate> active = new ArrayList<>();
        if (player == null || map == null || CHUNK_SIZE_TILES <= 0) { // Removed lightManager null check, should exist if map exists
            return active;
        }

        int playerTileCol = player.getTileCol();
        int playerTileRow = player.getTileRow();
        int playerChunkX = playerTileCol / CHUNK_SIZE_TILES;
        int playerChunkY = playerTileRow / CHUNK_SIZE_TILES;
        int renderDist = getCurrentRenderDistanceChunks();

        // For a finite map, clamp chunk coordinates to map boundaries
        int maxChunkX = (map.getWidth() - 1) / CHUNK_SIZE_TILES;
        int minChunkX = 0;
        int maxChunkY = (map.getHeight() - 1) / CHUNK_SIZE_TILES;
        int minChunkY = 0;

        for (int dy = -renderDist; dy <= renderDist; dy++) {
            for (int dx = -renderDist; dx <= renderDist; dx++) {
                int cx = playerChunkX + dx;
                int cy = playerChunkY + dy;

                if (cx >= minChunkX && cx <= maxChunkX && cy >= minChunkY && cy <= maxChunkY) {
                    active.add(new LightManager.ChunkCoordinate(cx, cy));
                }
                // For true infinite map, this clamping would be removed, and map/chunk system would handle on-demand creation.
            }
        }
        return active;
    }

    private void processInitialLightAndChunks() {
        chunkRenderUpdateQueue.clear();
        if (lightManager != null && map != null) {
            List<LightManager.ChunkCoordinate> activeChunks = getActiveChunkCoordinates();
            for(LightManager.ChunkCoordinate coord : activeChunks) {
                lightManager.initializeSkylightForChunk(coord);
            }

            int lightProcessingPasses = 50; // Process queues a bit to settle initial state
            for (int i = 0; i < lightProcessingPasses; i++) {
                lightManager.processLightQueuesIncrementally();
                Set<LightManager.ChunkCoordinate> dirtyChunksFromLight = lightManager.getDirtyChunksAndClear();
                if (!dirtyChunksFromLight.isEmpty()) {
                    for (LightManager.ChunkCoordinate coord : dirtyChunksFromLight) {
                        if (!chunkRenderUpdateQueue.contains(coord)) {
                            chunkRenderUpdateQueue.offer(coord);
                        }
                    }
                } else if (i > 10 && !lightManager.isAnyLightQueueNotEmpty()) {
                    // System.out.println("Initial light queues emptied early at pass " + i);
                    break;
                }
            }
            // System.out.println("Initial light processed. Render queue size: " + chunkRenderUpdateQueue.size() +
            //                    " Light queues (SProp,SRem,BProp,BRem): " +
            //                    lightManager.getSkyLightPropagationQueueSize() + "," +
            //                    lightManager.getSkyLightRemovalQueueSize() + "," +
            //                    lightManager.getBlockLightPropagationQueueSize() + "," +
            //                    lightManager.getBlockLightRemovalQueueSize());
        }
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
                float titleWidth = currentTitleFont.getTextWidthScaled(title, 1.0f); // Use getTextWidthScaled or ensure Font.drawText handles scaling correctly.
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
        // System.out.println("Refreshed save files: " + availableSaveFiles);
        if (currentGameState == GameState.MAIN_MENU) { setupMainMenuButtons(); }
    }

    public List<String> getAvailableSaveFiles() { return availableSaveFiles; }

    private byte calculateSkyLightForTime(double time) {
        byte newSkyLight;
        if (time >= 0.0 && time < 0.40) newSkyLight = SKY_LIGHT_DAY;
        else if (time >= 0.40 && time < 0.60) {
            float phase = (float) ((time - 0.40) / 0.20);
            newSkyLight = (byte) (SKY_LIGHT_DAY - phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        } else if (time >= 0.60 && time < 0.90) newSkyLight = SKY_LIGHT_NIGHT;
        else {
            float phase = (float) ((time - 0.90) / 0.10);
            newSkyLight = (byte) (SKY_LIGHT_NIGHT + phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        }
        return (byte) Math.max(SKY_LIGHT_NIGHT_MINIMUM, Math.min(MAX_LIGHT_LEVEL, newSkyLight));
    }

    private void updateSkyLightBasedOnTimeOfDay(double deltaTime) {
        currentGlobalSkyLightActual = calculateSkyLightForTime(pseudoTimeOfDay);
        boolean significantChange = Math.abs(currentGlobalSkyLightActual - lastUpdatedSkyLightValueToLightManager) >= SKY_LIGHT_UPDATE_THRESHOLD;
        boolean boundaryReached = (currentGlobalSkyLightActual == SKY_LIGHT_DAY && lastUpdatedSkyLightValueToLightManager != SKY_LIGHT_DAY) ||
                (currentGlobalSkyLightActual == SKY_LIGHT_NIGHT && lastUpdatedSkyLightValueToLightManager != SKY_LIGHT_NIGHT) ||
                (currentGlobalSkyLightActual == SKY_LIGHT_NIGHT_MINIMUM && lastUpdatedSkyLightValueToLightManager != SKY_LIGHT_NIGHT_MINIMUM);


        if (significantChange || boundaryReached) {
            List<LightManager.ChunkCoordinate> activeChunks = getActiveChunkCoordinates();
            if (lightManager != null && !activeChunks.isEmpty()) { // Only update if there are active chunks
                lightManager.updateGlobalSkyLightForActiveChunks(currentGlobalSkyLightActual, activeChunks);
            }
            lastUpdatedSkyLightValueToLightManager = currentGlobalSkyLightActual;
        }
    }

    private void updateGameLogic(double deltaTime) {
        pseudoTimeOfDay += deltaTime * DAY_NIGHT_CYCLE_SPEED;
        if (pseudoTimeOfDay >= 1.0) pseudoTimeOfDay -= 1.0;
        updateSkyLightBasedOnTimeOfDay(deltaTime);

        if (inputHandler != null) inputHandler.handleContinuousInput(deltaTime);
        if (player != null) player.update(deltaTime);
        if (cameraManager != null) cameraManager.update(deltaTime);

        if (lightManager != null) {
            lightManager.processLightQueuesIncrementally();
            Set<LightManager.ChunkCoordinate> newlyDirtyChunksFromLight = lightManager.getDirtyChunksAndClear();
            if (!newlyDirtyChunksFromLight.isEmpty()) {
                for (LightManager.ChunkCoordinate coord : newlyDirtyChunksFromLight) {
                    if (!chunkRenderUpdateQueue.contains(coord)) {
                        chunkRenderUpdateQueue.offer(coord);
                    }
                }
            }
        }

        if (renderer != null && !chunkRenderUpdateQueue.isEmpty()) {
            int updatedThisFrame = 0;
            while (!chunkRenderUpdateQueue.isEmpty() && updatedThisFrame < MAX_CHUNK_GEOMETRY_UPDATES_PER_FRAME) {
                LightManager.ChunkCoordinate coordToUpdate = chunkRenderUpdateQueue.poll();
                if (coordToUpdate != null) {
                    renderer.updateChunkByGridCoords(coordToUpdate.chunkX, coordToUpdate.chunkY);
                    updatedThisFrame++;
                }
            }
        }
    }

    public void saveGame(String worldName) {
        if (worldName == null || worldName.trim().isEmpty()) { System.err.println("SaveGame Error: World name is null or empty."); return; }
        String baseWorldName = worldName.replace(".json", "");
        String filePath = Paths.get(SAVES_DIRECTORY, baseWorldName + ".json").toString();
        // System.out.println("Attempting to save game to: " + filePath);

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
        this.lastUpdatedSkyLightValueToLightManager = (byte)(this.currentGlobalSkyLightActual - 1); // Force update in LightManager

        if (map == null || player == null || lightManager == null || cameraManager == null) { System.err.println("LoadGame Critical: Core components are null."); return false; }

        if (!map.loadState(saveState.mapData)) { System.err.println("Failed to load map state. Aborting load."); this.currentWorldName = null; return false; }
        if (!player.loadState(saveState.playerData)) { System.err.println("Failed to load player state. Player state may be inconsistent.");}

        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());

        System.out.println("Re-initializing lighting and rendering for loaded game...");

        List<LightManager.ChunkCoordinate> activeChunks = getActiveChunkCoordinates();
        if (!activeChunks.isEmpty()) {
            lightManager.updateGlobalSkyLightForActiveChunks(currentGlobalSkyLightActual, activeChunks);
        }

        for(LightManager.ChunkCoordinate activeChunk : activeChunks) {
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
        processInitialLightAndChunks();

        if (renderer != null) { renderer.uploadTileMapGeometry(); }
        System.out.println("Game loaded successfully: " + this.currentWorldName);
        setCurrentGameState(GameState.IN_GAME);
        refreshAvailableSaveFiles();
        return true;
    }

    public String getCurrentWorldName() { return currentWorldName; }
    public void toggleInventory() { this.showInventory = !this.showInventory; }
    public boolean isInventoryVisible() { return this.showInventory; }
    public GameState getCurrentGameState() { return currentGameState; }

    public void setCurrentGameState(GameState newState) {
        System.out.println("Game state changing from " + this.currentGameState + " to " + newState);
        this.currentGameState = newState;
        if (newState == GameState.MAIN_MENU) {
            refreshAvailableSaveFiles();
            currentWorldName = null;
        } else if (newState == GameState.IN_GAME) {
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
    public int getSelectedInventorySlotIndex() { return selectedInventorySlotIndex; }

    public void setSelectedInventorySlotIndex(int index) {
        if (player != null && player.getInventorySlots() != null &&
                index >= 0 && index < Constants.HOTBAR_SIZE &&
                index < player.getInventorySlots().size()) {
            this.selectedInventorySlotIndex = index;
            player.setSelectedHotbarSlotIndex(index);
        } else if (index == -1) { // Allow deselecting if game supports it
            this.selectedInventorySlotIndex = -1;
            // player.setSelectedHotbarSlotIndex(-1);
        }
    }

    private void renderGame() {
        float rSky, gSky, bSky;
        // Ensure SKY_LIGHT_DAY is greater than SKY_LIGHT_NIGHT_MINIMUM to avoid division by zero or negative ratios
        float lightRange = (float)(SKY_LIGHT_DAY - SKY_LIGHT_NIGHT_MINIMUM);
        float lightRatio = 0.5f; // Default to a mid-value if range is 0
        if (lightRange > 0) {
            lightRatio = (float)(lightManager.getCurrentGlobalSkyLightValue() - SKY_LIGHT_NIGHT_MINIMUM) / lightRange;
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
                debugLines.add(String.format("Time: %.3f, SkyLight (LM/Target): %d/%d", pseudoTimeOfDay, lightManager.getCurrentGlobalSkyLightValue(), currentGlobalSkyLightActual));
                debugLines.add("Player: (" + player.getTileRow() + "," + player.getTileCol() + ") A:" + player.getCurrentAction() + " D:" + player.getCurrentDirection());
                if (currentWorldName != null) debugLines.add("World: " + currentWorldName); else debugLines.add("World: (Unsaved/New)");
                Tile selectedTile = map.getTile(inputHandler.getSelectedRow(), inputHandler.getSelectedCol());
                String selectedInfo = "Sel: ("+inputHandler.getSelectedRow()+","+inputHandler.getSelectedCol()+")";
                if(selectedTile!=null) selectedInfo += " E:"+selectedTile.getElevation()+" T:"+selectedTile.getType()+" SL:"+selectedTile.getSkyLightLevel()+" BL:"+selectedTile.getBlockLightLevel()+" FL:"+selectedTile.getFinalLightLevel() + (selectedTile.hasTorch()?" (T)":"") + " Tree:" + selectedTile.getTreeType().name();
                debugLines.add(selectedInfo);
                debugLines.add(String.format("Cam: (%.1f,%.1f) Z:%.2f RD:%d", cameraManager.getCameraX(),cameraManager.getCameraY(),cameraManager.getZoom(), currentRenderDistanceChunks));
                debugLines.add("RndQ: " + chunkRenderUpdateQueue.size() + " LightQ (SP,SR,BP,BR): " + lightManager.getSkyLightPropagationQueueSize() + "," + lightManager.getSkyLightRemovalQueueSize() + "," + lightManager.getBlockLightPropagationQueueSize() + "," + lightManager.getBlockLightRemovalQueueSize());
                debugLines.add("Hotbar: " + player.getSelectedHotbarSlotIndex() + (showInventory ? " InvShow" : ""));
                renderer.renderDebugOverlay(10f, 10f, 1300f, 260f, debugLines);
            }
            glEnable(GL_DEPTH_TEST);
        }
    }

    public void requestFullMapRegeneration() {
        System.out.println("Game: Full map regeneration requested.");
        initializeNewGameCommonLogic();
        this.currentWorldName = null;
        System.out.println("Game: Full map regeneration processing complete. World is now unsaved.");
    }

    public void requestTileRenderUpdate(int r, int c) {
        if (renderer != null && CHUNK_SIZE_TILES > 0) {
            LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(c / CHUNK_SIZE_TILES, r / CHUNK_SIZE_TILES);
            if (!chunkRenderUpdateQueue.contains(coord)) {
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
        // System.out.println("Game: Player " + (usedAxe ? "chopping" : "hitting") + " tree at (" + r + "," + c + ")");

        if (usedAxe) {
            targetTile.setTreeType(Tile.TreeVisualType.NONE); // Remove tree
            if (ItemRegistry.WOOD != null) playerWhoActed.addItemToInventory(ItemRegistry.WOOD, 3);
        } else {
            if (ItemRegistry.STICK != null) playerWhoActed.addItemToInventory(ItemRegistry.STICK, 1);
        }
        // Tree removal/interaction might affect lighting (e.g. unblocking sky)
        // and definitely requires a render update for the chunk.
        // map.setTileElevation(r,c, targetTile.getElevation()); // This can trigger broader light updates if needed
        // Or more directly:
        map.getLightManager().markChunkDirty(r,c); // Mark for render
        // If tree removal unblocks sky light significantly, re-evaluate sky light for this tile and neighbors
        map.getLightManager().getSkyLightPropagationQueue().add(new LightManager.LightNode(r,c, (byte)0)); // Force re-check with current global
        // Propagate light from neighbors into this tile too
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr;
                int nc = c + dc;
                if (map.isValid(nr, nc)) {
                    Tile neighbor = map.getTile(nr, nc);
                    if (neighbor != null) {
                        if (neighbor.getSkyLightLevel() > 1) map.getLightManager().getSkyLightPropagationQueue().add(new LightManager.LightNode(nr, nc, neighbor.getSkyLightLevel()));
                        if (neighbor.getBlockLightLevel() > 1) map.getLightManager().getBlockLightPropagationQueue().add(new LightManager.LightNode(nr, nc, neighbor.getBlockLightLevel()));
                    }
                }
            }
        }


    }

    private boolean showDebugOverlay = true;
    public void toggleShowDebugOverlay() { this.showDebugOverlay = !this.showDebugOverlay; }

    private void cleanup() {
        System.out.println("Game cleanup initiated...");
        if (renderer != null) renderer.cleanup();
        System.out.println("Game cleanup complete.");
    }
}