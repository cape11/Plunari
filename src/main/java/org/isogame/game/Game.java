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
import org.isogame.ui.MenuItemButton; // Import the new MenuItemButton class

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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Queue;
import java.util.LinkedList;
import java.util.stream.Collectors;


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
    // private final double DAY_NIGHT_CYCLE_SPEED = 0.05; // Already in Constants
    private byte currentGlobalSkyLight;

    private byte lastUpdatedSkyLightValue;
    // private static final int SKY_LIGHT_UPDATE_THRESHOLD = 2; // Already in Constants

    private int framesRenderedThisSecond = 0;
    private double timeAccumulatorForFps = 0.0;
    private double displayedFps = 0.0;

    private int selectedInventorySlotIndex = 0;
    private int currentRenderDistanceChunks = Constants.RENDER_DISTANCE_CHUNKS;
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

    // New list for main menu buttons
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
        // Pass 'this' (Game instance) to MouseHandler if it needs to access menuButtons or game state
        mouseHandler = new MouseHandler(window, cameraManager, map, inputHandler, player, this);


        glfwSetFramebufferSizeCallback(window, (win, fbW, fbH) -> {
            if (cameraManager != null) cameraManager.updateScreenSize(fbW, fbH);
            if (renderer != null) renderer.onResize(fbW, fbH);
            if (currentGameState == GameState.MAIN_MENU) { // Re-calculate button positions on resize
                setupMainMenuButtons();
            }
        });

        if (renderer != null) renderer.onResize(initialFramebufferWidth, initialScreenHeight);

        initializeNewGameCommonLogic();
        currentGameState = GameState.MAIN_MENU;
        refreshAvailableSaveFiles();
        setupMainMenuButtons(); // Setup buttons initially
    }

    // Getter for MouseHandler to access menu buttons
    public List<MenuItemButton> getMainMenuButtons() {
        return menuButtons;
    }

    private void setupMainMenuButtons() {
        menuButtons.clear();
        if (cameraManager == null) return; // Cannot setup without screen dimensions

        float buttonWidth = 300f;
        float buttonHeight = 40f;
        float buttonSpacing = 15f;
        float currentY = cameraManager.getScreenHeight() * 0.25f; // Start buttons lower down
        float screenCenterX = cameraManager.getScreenWidth() / 2f;
        float buttonX = screenCenterX - buttonWidth / 2f;

        // Create New World Button
        menuButtons.add(new MenuItemButton(buttonX, currentY, buttonWidth, buttonHeight, "Create New World", "NEW_WORLD", null));
        currentY += buttonHeight + buttonSpacing;

        // Available Worlds Header (optional, could be rendered separately by Renderer)
        // For simplicity, we'll just list worlds as buttons directly

        if (availableSaveFiles.isEmpty()) {
            // Could add a non-interactive label here if desired, drawn by renderer
            // For now, just spacing
            currentY += buttonHeight + buttonSpacing;
        } else {
            float worldButtonWidth = buttonWidth * 0.75f; // Smaller button for world name
            float deleteButtonWidth = buttonWidth * 0.20f; // Smaller button for DEL
            float worldButtonX = screenCenterX - (worldButtonWidth + deleteButtonWidth + 5f) / 2f; // 5f is spacing
            float deleteButtonX = worldButtonX + worldButtonWidth + 5f;

            for (String worldFile : availableSaveFiles) {
                   String worldNameDisplay = worldFile.replace(".json", "");
                MenuItemButton loadButton = new MenuItemButton(worldButtonX, currentY, worldButtonWidth, buttonHeight, "Load: " + worldNameDisplay, "LOAD_WORLD", worldFile);
                menuButtons.add(loadButton);

                MenuItemButton deleteButton = new MenuItemButton(deleteButtonX, currentY, deleteButtonWidth, buttonHeight, "DEL", "DELETE_WORLD", worldNameDisplay);
                deleteButton.setCustomColors(
                        new float[]{0.5f, 0.25f, 0.15f, 0.9f},  // baseBg: Dark, muted terracotta/brown
                        new float[]{0.6f, 0.3f, 0.2f, 0.95f}, // hoverBg: Slightly lighter
                        new float[]{0.2f, 0.1f, 0.05f, 1.0f},  // border: Very dark brown
                        new float[]{0.9f, 0.8f, 0.75f, 1.0f},  // baseText: Light beige
                        new float[]{1.0f, 0.95f, 0.9f, 1.0f}   // hoverText: Brighter beige
                );
                deleteButton.borderWidth = 1.5f; // Slightly thinner border for the small DEL button
                menuButtons.add(deleteButton);

                currentY += buttonHeight + buttonSpacing / 2; // Less spacing for world list items
            }
        }
        currentY += buttonSpacing; // Extra space before Exit

        // Exit Game Button
        menuButtons.add(new MenuItemButton(buttonX, currentY, buttonWidth, buttonHeight, "Exit Game", "EXIT_GAME", null));
    }


    private void initializeNewGameCommonLogic() {
        System.out.println("Initializing new game common logic (or blank state for menu)...");
        map.generateMap();
        pseudoTimeOfDay = 0.0005;
        currentGlobalSkyLight = calculateSkyLightForTime(pseudoTimeOfDay);
        lastUpdatedSkyLightValue = currentGlobalSkyLight;
        lightManager.updateGlobalSkyLight(currentGlobalSkyLight);

        if (player != null) {
            player.setPosition(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
            if (player.getInventorySlots() != null) {
                player.getInventorySlots().forEach(InventorySlot::clearSlot);
            }
        }
        if (cameraManager != null && player != null) {
            cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
        }

        processInitialLightAndChunks();

        if (renderer != null) {
            renderer.uploadTileMapGeometry();
        }
        System.out.println("New game environment initialized / blank state prepared.");
    }

    private void processInitialLightAndChunks(){
        chunkRenderUpdateQueue.clear();
        if (lightManager != null) {
            for (int r = 0; r < map.getHeight(); r += CHUNK_SIZE_TILES) {
                for (int c = 0; c < map.getWidth(); c += CHUNK_SIZE_TILES) {
                    lightManager.markChunkDirty(r, c);
                }
            }
            if (map.getHeight() % CHUNK_SIZE_TILES != 0) {
                for (int c = 0; c < map.getWidth(); c+=CHUNK_SIZE_TILES) lightManager.markChunkDirty(map.getHeight() -1 ,c);
            }
            if (map.getWidth() % CHUNK_SIZE_TILES != 0) {
                for (int r = 0; r < map.getHeight(); r+=CHUNK_SIZE_TILES) lightManager.markChunkDirty(r ,map.getWidth() -1);
            }
            if (map.getHeight() % CHUNK_SIZE_TILES != 0 && map.getWidth() % CHUNK_SIZE_TILES != 0) {
                lightManager.markChunkDirty(map.getHeight()-1, map.getWidth()-1);
            }

            for (int i = 0; i < 200; i++) {
                lightManager.processLightQueuesIncrementally();
                Set<LightManager.ChunkCoordinate> dirtyChunks = lightManager.getDirtyChunksAndClear();
                if (!dirtyChunks.isEmpty()) {
                    for (LightManager.ChunkCoordinate coord : dirtyChunks) {
                        if (!chunkRenderUpdateQueue.contains(coord)) {
                            chunkRenderUpdateQueue.offer(coord);
                        }
                    }
                } else if (i > 50) {
                    break;
                }
            }
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
        if (currentGameState == GameState.IN_GAME && currentWorldName != null) {
            saveGame(currentWorldName);
        }
        cleanup();
    }

    private void updateMainMenu(double deltaTime) {
        // Update hover state for buttons - can be done here or in MouseHandler's cursorPosCallback
        if (mouseHandler != null && cameraManager != null) {
            double[] xpos = new double[1];
            double[] ypos = new double[1];
            glfwGetCursorPos(window, xpos, ypos);

            // Scale mouse coords if necessary (similar to MouseHandler)
            int[] fbWidth = new int[1]; int[] fbHeight = new int[1];
            glfwGetFramebufferSize(window, fbWidth, fbHeight);
            int[] winWidth = new int[1]; int[] winHeight = new int[1];
            glfwGetWindowSize(window, winWidth, winHeight);

            double scaleX = (fbWidth[0] > 0 && winWidth[0] > 0) ? (double)fbWidth[0]/winWidth[0] : 1.0;
            double scaleY = (fbHeight[0] > 0 && winHeight[0] > 0) ? (double)fbHeight[0]/winHeight[0] : 1.0;
            float physicalMouseX = (float)(xpos[0] * scaleX);
            float physicalMouseY = (float)(ypos[0] * scaleY);

            for (MenuItemButton button : menuButtons) {
                button.isHovered = button.isMouseOver(physicalMouseX, physicalMouseY);
            }
        }
    }

    private void renderMainMenu() {
        glClearColor(0.1f, 0.1f, 0.2f, 1.0f); // Dark blue background
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (renderer != null && cameraManager != null) {
            // Title Text (optional, rendered by Renderer if needed)
            if (renderer.getUiFont() != null && renderer.getUiFont().isInitialized()) {
                String title = "ISO GAME";
                float titleWidth = renderer.getUiFont().getTextWidth(title) * 2f; // Scale title up
                renderer.getUiFont().drawTextScaled( // Assuming you add/have a drawTextScaled method
                        cameraManager.getScreenWidth() / 2f - titleWidth / 2f,
                        cameraManager.getScreenHeight() * 0.1f,
                        title, 2.0f, 0.9f, 0.9f, 1.0f); // White title
            }

            // Render all defined menu buttons
            for (MenuItemButton button : menuButtons) {
                if (button.isVisible) {
                    renderer.renderMenuButton(button); // Pass the whole button object
                }
            }
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
        // No need to call setupMainMenuButtons here as we are leaving the menu
    }

    public void deleteWorld(String worldName) {
        if (worldName == null || worldName.isEmpty()) return;
        String fileName = worldName.endsWith(".json") ? worldName : worldName + ".json";
        File saveFile = new File(SAVES_DIRECTORY, fileName);
        if (saveFile.exists()) {
            if (saveFile.delete()) {
                System.out.println("Deleted world: " + fileName);
                if (this.currentWorldName != null && (this.currentWorldName.equals(fileName.replace(".json","")) || this.currentWorldName.equals(worldName)) ){
                    this.currentWorldName = null;
                }
            } else {
                System.err.println("Failed to delete world: " + fileName);
            }
        } else {
            System.err.println("World file not found for deletion: " + fileName);
        }
        refreshAvailableSaveFiles();
        setupMainMenuButtons(); // Re-setup buttons as the list of worlds changed
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
        System.out.println("Refreshed save files: " + availableSaveFiles);
        // If in main menu, update buttons
        if (currentGameState == GameState.MAIN_MENU) {
            setupMainMenuButtons();
        }
    }

    public List<String> getAvailableSaveFiles() {
        return availableSaveFiles;
    }


    public void startNewGame() {
        System.out.println("Explicitly starting New Game via menu action (after load logic)...");
        initializeNewGameCommonLogic();
        setCurrentGameState(GameState.IN_GAME);
        this.currentWorldName = null;
    }

    private byte calculateSkyLightForTime(double time) {
        byte newSkyLight;
        if (time >= 0.0 && time < 0.40) newSkyLight = SKY_LIGHT_DAY;
        else if (time >= 0.40 && time < 0.60) {
            float phase = (float) ((time - 0.40) / 0.20);
            newSkyLight = (byte) (SKY_LIGHT_DAY - phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        } else if (time >= 0.60 && time < 0.80) newSkyLight = SKY_LIGHT_NIGHT;
        else {
            float phase = (float) ((time - 0.80) / 0.20);
            newSkyLight = (byte) (SKY_LIGHT_NIGHT + phase * (SKY_LIGHT_DAY - SKY_LIGHT_NIGHT));
        }
        return (byte) Math.max(0, Math.min(MAX_LIGHT_LEVEL, newSkyLight));
    }

    private void updateSkyLightBasedOnTimeOfDay(double deltaTime) {
        byte newCalculatedSkyLight = calculateSkyLightForTime(pseudoTimeOfDay);
        boolean skyLightActuallyChanged = (newCalculatedSkyLight != currentGlobalSkyLight);
        currentGlobalSkyLight = newCalculatedSkyLight;

        if (Math.abs(newCalculatedSkyLight - lastUpdatedSkyLightValue) >= Constants.SKY_LIGHT_UPDATE_THRESHOLD ||
                (skyLightActuallyChanged && (newCalculatedSkyLight == SKY_LIGHT_DAY || newCalculatedSkyLight == SKY_LIGHT_NIGHT))) {
            lightManager.updateGlobalSkyLight(newCalculatedSkyLight);
            lastUpdatedSkyLightValue = newCalculatedSkyLight;
        }
    }

    private void updateGameLogic(double deltaTime) {
        pseudoTimeOfDay += deltaTime * Constants.DAY_NIGHT_CYCLE_SPEED; // Use constant
        if (pseudoTimeOfDay >= 1.0) pseudoTimeOfDay -= 1.0;
        updateSkyLightBasedOnTimeOfDay(deltaTime);

        if (inputHandler != null) inputHandler.handleContinuousInput(deltaTime);
        if (player != null) player.update(deltaTime);
        if (cameraManager != null) cameraManager.update(deltaTime);

        if (lightManager != null) {
            lightManager.processLightQueuesIncrementally();
            Set<LightManager.ChunkCoordinate> newlyDirtyChunks = lightManager.getDirtyChunksAndClear();
            if (!newlyDirtyChunks.isEmpty()) {
                for (LightManager.ChunkCoordinate coord : newlyDirtyChunks) {
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
        if (worldName == null || worldName.trim().isEmpty()) {
            System.err.println("SaveGame Error: World name is null or empty. Cannot save.");
            return;
        }
        String baseWorldName = worldName.replace(".json", "");
        String filePath = Paths.get(SAVES_DIRECTORY, baseWorldName + ".json").toString();
        System.out.println("Attempting to save game to: " + filePath);


        if (player == null || map == null) {
            System.err.println("SaveGame Error: Player or Map is null.");
            return;
        }
        GameSaveState saveState = new GameSaveState();

        saveState.playerData = new PlayerSaveData();
        saveState.playerData.mapRow = player.getMapRow();
        saveState.playerData.mapCol = player.getMapCol();
        saveState.playerData.inventory = new ArrayList<>();
        if (player.getInventorySlots() != null) {
            for (InventorySlot slot : player.getInventorySlots()) {
                if (slot != null && !slot.isEmpty()) {
                    InventorySlotSaveData slotData = new InventorySlotSaveData();
                    slotData.itemId = slot.getItem().getItemId();
                    slotData.quantity = slot.getQuantity();
                    saveState.playerData.inventory.add(slotData);
                } else {
                    saveState.playerData.inventory.add(null);
                }
            }
        }

        saveState.mapData = new MapSaveData();
        saveState.mapData.width = map.getWidth();
        saveState.mapData.height = map.getHeight();
        saveState.mapData.tiles = new ArrayList<>();
        for (int r = 0; r < map.getHeight(); r++) {
            List<TileSaveData> rowList = new ArrayList<>();
            for (int c = 0; c < map.getWidth(); c++) {
                Tile tile = map.getTile(r, c);
                TileSaveData tileData = new TileSaveData();
                if (tile != null) {
                    tileData.typeOrdinal = tile.getType().ordinal();
                    tileData.elevation = tile.getElevation();
                    tileData.hasTorch = tile.hasTorch();
                    tileData.skyLightLevel = tile.getSkyLightLevel();
                    tileData.blockLightLevel = tile.getBlockLightLevel();
                    tileData.treeTypeOrdinal = tile.getTreeType().ordinal();
                } else {
                    tileData.typeOrdinal = Tile.TileType.WATER.ordinal();
                    tileData.treeTypeOrdinal = Tile.TreeVisualType.NONE.ordinal();
                }
                rowList.add(tileData);
            }
            saveState.mapData.tiles.add(rowList);
        }
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

    public boolean loadGame(String worldNameOrFileName) {
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

        if (saveState == null) {
            System.err.println("Load game: Failed to parse save data (saveState is null).");
            return false;
        }
        if (saveState.mapData == null || saveState.playerData == null) {
            System.err.println("Load game: Parsed save data is incomplete (mapData or playerData is null).");
            return false;
        }

        String baseWorldName = fileName.replace(".json", "");
        System.out.println("Restoring game state for world: " + baseWorldName);
        this.currentWorldName = baseWorldName;

        this.pseudoTimeOfDay = saveState.pseudoTimeOfDay;
        this.currentGlobalSkyLight = calculateSkyLightForTime(this.pseudoTimeOfDay);
        this.lastUpdatedSkyLightValue = this.currentGlobalSkyLight;

        if (map == null) {System.err.println("LoadGame Critical: Map object is null."); return false;}
        if (!map.loadState(saveState.mapData)) {
            System.err.println("Failed to load map state. Aborting load.");
            this.currentWorldName = null;
            return false;
        }

        if (player == null) {System.err.println("LoadGame Critical: Player object is null."); return false;}
        if (!player.loadState(saveState.playerData)) {
            System.err.println("Failed to load player state. Player state may be inconsistent.");
        }

        if(cameraManager != null && player != null) {
            cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
        }

        System.out.println("Re-initializing lighting and rendering for loaded game...");
        if(lightManager == null) {System.err.println("LoadGame Critical: LightManager is null."); return false;}
        lightManager.updateGlobalSkyLight(this.currentGlobalSkyLight);

        for (int r = 0; r < map.getHeight(); r++) {
            for (int c = 0; c < map.getWidth(); c++) {
                Tile tile = map.getTile(r, c);
                if (tile != null && tile.hasTorch()) {
                    lightManager.addLightSource(r, c, (byte) TORCH_LIGHT_LEVEL);
                }
            }
        }

        processInitialLightAndChunks();

        if (renderer != null) {
            renderer.uploadTileMapGeometry();
        }

        System.out.println("Game loaded successfully: " + this.currentWorldName);
        setCurrentGameState(GameState.IN_GAME);
        refreshAvailableSaveFiles(); // Though we are leaving menu, good to keep it consistent for next time
        return true;
    }

    public String getCurrentWorldName() {
        return currentWorldName;
    }

    public void toggleInventory() {
        this.showInventory = !this.showInventory;
        System.out.println("Inventory visible: " + this.showInventory);
    }

    public boolean isInventoryVisible() { return this.showInventory; }
    public GameState getCurrentGameState() { return currentGameState; }
    public void setCurrentGameState(GameState newState) {
        System.out.println("Game state changing from " + this.currentGameState + " to " + newState);
        this.currentGameState = newState;
        if (newState == GameState.MAIN_MENU) {
            refreshAvailableSaveFiles(); // This will call setupMainMenuButtons
            currentWorldName = null;
        } else if (newState == GameState.IN_GAME) {
            // Ensure mouse isn't stuck from menu UI handling
            if (mouseHandler != null) mouseHandler.resetLeftMouseDragFlags();
        }
    }
    public int getCurrentRenderDistanceChunks() { return currentRenderDistanceChunks; }
    public void increaseRenderDistance() {
        currentRenderDistanceChunks = Math.min(currentRenderDistanceChunks + 1, 10);
        System.out.println("Render distance increased to: " + currentRenderDistanceChunks);
    }
    public void decreaseRenderDistance() {
        currentRenderDistanceChunks = Math.max(1, currentRenderDistanceChunks - 1);
        System.out.println("Render distance decreased to: " + currentRenderDistanceChunks);
    }
    public int getSelectedInventorySlotIndex() { return selectedInventorySlotIndex; }
    public void setSelectedInventorySlotIndex(int index) {
        if (player != null && player.getInventorySlots() != null && index >= 0 && index < player.getInventorySlots().size()) {
            this.selectedInventorySlotIndex = index;
        } else if (index == -1) { // Allow deselecting
            this.selectedInventorySlotIndex = -1;
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
            if (this.showInventory && player != null) {
                renderer.renderInventoryUI(player);
            }
            if (this.showDebugOverlay) {
                List<String> debugLines = new ArrayList<>();
                debugLines.add(String.format("FPS: %.1f", displayedFps));
                debugLines.add(String.format("Time: %.2f, SkyLight: %d", pseudoTimeOfDay, currentGlobalSkyLight));
                if (player != null) {
                    debugLines.add("Player: (" + player.getTileRow() + ", " + player.getTileCol() + ")");
                    if (currentWorldName != null) {
                        debugLines.add("World: " + currentWorldName);
                    } else {
                        debugLines.add("World: (Unsaved/New)");
                    }
                }
                if (inputHandler != null) {
                    Tile selectedTile = map.getTile(inputHandler.getSelectedRow(), inputHandler.getSelectedCol());
                    String selectedInfo = "Selected: ("+inputHandler.getSelectedRow()+","+inputHandler.getSelectedCol()+")";
                    if (selectedTile != null) {
                        selectedInfo += " Elev: " + selectedTile.getElevation() + " Type: " + selectedTile.getType();
                        selectedInfo += " SL:" + selectedTile.getSkyLightLevel() + " BL:" + selectedTile.getBlockLightLevel() + " FL:" + selectedTile.getFinalLightLevel();
                        if(selectedTile.hasTorch()) selectedInfo += " (TORCH)";
                        selectedInfo += " Tree: " + selectedTile.getTreeType().name();
                    }
                    debugLines.add(selectedInfo);
                }
                if (cameraManager != null) debugLines.add(String.format("Camera: (%.1f, %.1f) Zoom: %.2f", cameraManager.getCameraX(), cameraManager.getCameraY(), cameraManager.getZoom()));
                debugLines.add("I:Inv|L:Torch|J:Dig|Q/E:Elev|C:CenterCam|G:Regen|F5:Debug|F6/7:RDist|F9:Save");
                debugLines.add("Render Q Size: " + chunkRenderUpdateQueue.size());
                debugLines.add("Selected Slot: " + selectedInventorySlotIndex);

                // Tweak debug overlay position and size if needed
                float debugPanelHeight = 20f * debugLines.size() + 10f; // Dynamic height
                debugPanelHeight = Math.min(debugPanelHeight, cameraManager.getScreenHeight() * 0.5f); // Cap height
                float debugPanelWidth = 450f; // Slightly wider for long lines
                renderer.renderDebugOverlay(10f, 10f, debugPanelWidth, debugPanelHeight, debugLines);
            }
        }
    }

    public void requestFullMapRegeneration() {
        System.out.println("Game: Full map regeneration requested.");
        initializeNewGameCommonLogic();
        this.currentWorldName = null;
        System.out.println("Game: Full map regeneration processing complete. World is now unsaved.");
    }

    public void requestTileRenderUpdate(int row, int col) {
        if (renderer != null && CHUNK_SIZE_TILES > 0) {
            LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(col / CHUNK_SIZE_TILES, row / CHUNK_SIZE_TILES);
            if (!chunkRenderUpdateQueue.contains(coord)) {
                chunkRenderUpdateQueue.offer(coord);
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