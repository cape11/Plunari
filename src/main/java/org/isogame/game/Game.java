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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File; // For file operations
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files; // For file deletion
import java.nio.file.Paths; // For file paths
import java.util.ArrayList;
import java.util.Arrays; // For listing files
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
    private final double DAY_NIGHT_CYCLE_SPEED = 0.05;
    private byte currentGlobalSkyLight;

    private byte lastUpdatedSkyLightValue;
    private static final int SKY_LIGHT_UPDATE_THRESHOLD = 2;

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
    private String currentWorldName = null; // Name of the currently loaded/active world
    private List<String> availableSaveFiles = new ArrayList<>();
    private static final String SAVES_DIRECTORY = "saves"; // Directory to store save files

    public Game(long window, int initialFramebufferWidth, int initialScreenHeight) {
        this.window = window;

        // Ensure saves directory exists
        new File(SAVES_DIRECTORY).mkdirs();

        map = new Map(MAP_WIDTH, MAP_HEIGHT);
        lightManager = map.getLightManager();
        player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
        cameraManager = new CameraManager(initialFramebufferWidth, initialScreenHeight, map.getWidth(), map.getHeight());

        inputHandler = new InputHandler(window, cameraManager, map, player, this);
        inputHandler.registerCallbacks(this::requestFullMapRegeneration);

        renderer = new Renderer(cameraManager, map, player, inputHandler);
        mouseHandler = new MouseHandler(window, cameraManager, map, inputHandler, player);

        glfwSetFramebufferSizeCallback(window, (win, fbW, fbH) -> {
            if (cameraManager != null) cameraManager.updateScreenSize(fbW, fbH);
            if (renderer != null) renderer.onResize(fbW, fbH);
        });

        if (renderer != null) renderer.onResize(initialFramebufferWidth, initialScreenHeight);

        // Don't auto-load here; let the main menu handle it.
        // Initialize with a blank state for the menu.
        initializeNewGameCommonLogic(); // Prepare a blank slate for the menu screen
        currentGameState = GameState.MAIN_MENU; // Start at main menu
        refreshAvailableSaveFiles(); // Load list of saves for the menu
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
            saveGame(currentWorldName); // Auto-save current world on exit
        }
        cleanup();
    }

    private void updateMainMenu(double deltaTime) { /* For menu animations, etc. */ }

    private void renderMainMenu() {
        glClearColor(0.1f, 0.1f, 0.2f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (renderer != null && this.cameraManager != null) {
            float panelWidth = 400f; // Wider panel for world list
            float panelHeight = 300f + (availableSaveFiles.size() * 20f); // Dynamic height
            panelHeight = Math.min(panelHeight, cameraManager.getScreenHeight() * 0.8f); // Cap height

            float panelX = (this.cameraManager.getScreenWidth() - panelWidth) / 2f;
            float panelY = (this.cameraManager.getScreenHeight() - panelHeight) / 2f;

            List<String> menuLines = new ArrayList<>();
            menuLines.add("--- ISO GAME - MAIN MENU ---");
            menuLines.add("");
            menuLines.add("   Create New World (Auto-named)");
            menuLines.add("");
            menuLines.add("   Available Worlds (Click to Load):");
            if (availableSaveFiles.isEmpty()) {
                menuLines.add("     (No saved worlds found)");
            } else {
                for (int i = 0; i < availableSaveFiles.size(); i++) {
                    // Remove .json for display, add (D) for delete option
                    String displayName = availableSaveFiles.get(i).replace(".json", "");
                    menuLines.add("     " + (i + 1) + ". " + displayName + "  (DEL)");
                }
            }
            menuLines.add("");
            menuLines.add("   Exit Game");
            renderer.renderDebugOverlay(panelX, panelY, panelWidth, panelHeight, menuLines);
        }
    }

    public void createNewWorld() {
        int nextWorldNum = 1;
        while (Files.exists(Paths.get(SAVES_DIRECTORY, "World" + nextWorldNum + ".json"))) {
            nextWorldNum++;
        }
        String newWorldName = "World" + nextWorldNum;
        System.out.println("Creating new world: " + newWorldName);

        initializeNewGameCommonLogic(); // Prepare fresh map and player state
        this.currentWorldName = newWorldName; // Set current world name
        saveGame(this.currentWorldName);    // Immediately save the new world
        refreshAvailableSaveFiles();        // Update list for menu
        setCurrentGameState(GameState.IN_GAME);
    }

    public void deleteWorld(String worldName) {
        if (worldName == null || worldName.isEmpty()) return;
        String fileName = worldName.endsWith(".json") ? worldName : worldName + ".json";
        File saveFile = new File(SAVES_DIRECTORY, fileName);
        if (saveFile.exists()) {
            if (saveFile.delete()) {
                System.out.println("Deleted world: " + fileName);
                if (fileName.equals(currentWorldName) || (currentWorldName !=null && currentWorldName.equals(worldName))) {
                    currentWorldName = null; // No world loaded if current was deleted
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
                availableSaveFiles.sort(String::compareToIgnoreCase); // Sort them
            }
        }
        System.out.println("Refreshed save files: " + availableSaveFiles);
    }

    public List<String> getAvailableSaveFiles() { // For MouseHandler to know what's clickable
        return availableSaveFiles;
    }


    public void startNewGame() { // This is called by menu if "New Game" is chosen
        System.out.println("Explicitly starting New Game via menu action (after load logic)...");
        initializeNewGameCommonLogic();
        setCurrentGameState(GameState.IN_GAME);
        // For a true "new game" from menu, we might not auto-save it until player does.
        // Or, like createNewWorld, give it a default name and save.
        // For now, createNewWorld handles named creation. This method is a fallback.
        this.currentWorldName = null; // No specific world name yet for this path
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

        if (Math.abs(newCalculatedSkyLight - lastUpdatedSkyLightValue) >= SKY_LIGHT_UPDATE_THRESHOLD ||
                (skyLightActuallyChanged && (newCalculatedSkyLight == SKY_LIGHT_DAY || newCalculatedSkyLight == SKY_LIGHT_NIGHT))) {
            lightManager.updateGlobalSkyLight(newCalculatedSkyLight);
            lastUpdatedSkyLightValue = newCalculatedSkyLight;
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

    public void saveGame(String worldName) { // Now takes worldName
        if (worldName == null || worldName.trim().isEmpty()) {
            System.err.println("SaveGame Error: World name is null or empty. Cannot save.");
            // Optionally, assign a default name or prompt user if UI supported it
            // For now, just don't save if no name.
            return;
        }
        String fileName = worldName.endsWith(".json") ? worldName : worldName + ".json";
        String filePath = Paths.get(SAVES_DIRECTORY, fileName).toString();
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
            this.currentWorldName = worldName; // Update current world name on successful save
            refreshAvailableSaveFiles(); // Refresh list in case this was a new save
        } catch (IOException e) {
            System.err.println("Error saving game: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean loadGame(String worldName) { // Now takes worldName
        if (worldName == null || worldName.trim().isEmpty()) {
            System.err.println("LoadGame Error: World name is null or empty.");
            return false;
        }
        String fileName = worldName.endsWith(".json") ? worldName : worldName + ".json";
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

        System.out.println("Restoring game state for world: " + worldName);
        this.currentWorldName = worldName.replace(".json", ""); // Store the base name

        this.pseudoTimeOfDay = saveState.pseudoTimeOfDay;
        this.currentGlobalSkyLight = calculateSkyLightForTime(this.pseudoTimeOfDay);
        this.lastUpdatedSkyLightValue = this.currentGlobalSkyLight;

        if (map == null) {System.err.println("LoadGame Critical: Map object is null."); return false;}
        if (!map.loadState(saveState.mapData)) {
            System.err.println("Failed to load map state. Aborting load.");
            this.currentWorldName = null; // Clear current world name on load failure
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
        refreshAvailableSaveFiles(); // Good to refresh after a successful load too
        return true;
    }

    public String getCurrentWorldName() { // Getter for InputHandler to use for saving
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
            refreshAvailableSaveFiles(); // Refresh save list when returning to menu
            currentWorldName = null; // No world is "active" in the main menu
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
        } else if (index == -1) {
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
                renderer.renderDebugOverlay(10f, 10f, 1000f, 260f, debugLines); // Increased height
            }
        }
    }

    public void requestFullMapRegeneration() {
        System.out.println("Game: Full map regeneration requested.");
        initializeNewGameCommonLogic();
        this.currentWorldName = null; // A regenerated world is unsaved initially
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