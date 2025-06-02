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
import java.util.List;
import java.util.Set;
import java.util.Queue;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.Iterator;

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
    private byte currentGlobalSkyLightActual; // The sky light value calculated from time of day
    private byte lastGlobalSkyLightTargetSetInLM; // The last target value we told LightManager

    private int framesRenderedThisSecond = 0;
    private double timeAccumulatorForFps = 0.0;
    private double displayedFps = 0.0;

    private int selectedInventorySlotIndex = 0;
    private int currentRenderDistanceChunks = Constants.RENDER_DISTANCE_CHUNKS_DEFAULT;
    private boolean showInventory = false;

    private final Queue<LightManager.ChunkCoordinate> chunkRenderUpdateQueue = new LinkedList<>();
    private static final int MAX_CHUNK_GEOMETRY_UPDATES_PER_FRAME = 2; // Reduced for smoother light updates

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
        this.lightManager.setCurrentGlobalSkyLightTarget(this.currentGlobalSkyLightActual); // Init LM's target
        this.lastGlobalSkyLightTargetSetInLM = this.currentGlobalSkyLightActual;

        initializeNewGameCommonLogic();
        currentGameState = GameState.MAIN_MENU;
        refreshAvailableSaveFiles();
        setupMainMenuButtons();
    }
    // ... (UI methods, save/load placeholders, etc. keep existing logic unless directly affected) ...
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
        globalSkyRefreshNeededQueue.clear(); // Clear this queue as well
        updateActiveChunksAroundPlayer(); // Loads initial chunks, calls initializeSkylightForChunk

        // Queue all initially active chunks for a full refresh with the current target
        globalSkyRefreshNeededQueue.addAll(currentlyActiveLogicalChunks);

        System.out.println("New game. Queued " + globalSkyRefreshNeededQueue.size() + " chunks for sky refresh.");
    }

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
                globalSkyRefreshNeededQueue.remove(currentActiveCoord); // Also remove from refresh queue if it's there
                chunksStateChanged = true;
            }
        }

        for (LightManager.ChunkCoordinate newCoord : desiredCoords) {
            if (!currentlyActiveLogicalChunks.contains(newCoord)) {
                renderer.ensureChunkGraphicsLoaded(newCoord.chunkX, newCoord.chunkY);
                lightManager.initializeSkylightForChunk(newCoord); // Uses LM's currentGlobalSkyLightTarget
                currentlyActiveLogicalChunks.add(newCoord);
                if (!globalSkyRefreshNeededQueue.contains(newCoord)) { // Add to refresh queue if not already present
                    globalSkyRefreshNeededQueue.offer(newCoord);
                }
                chunksStateChanged = true;

                // Propagate light across borders with existing neighbors
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
        if (chunksStateChanged) {
            // System.out.println("Active chunks updated. Total active: " + currentlyActiveLogicalChunks.size() + ". Refresh queue size: " + globalSkyRefreshNeededQueue.size());
        }
    }

    private void initOpenGL() {
        // ... (same as before)
        System.out.println("Game.initOpenGL() - OpenGL version from context: " + glGetString(GL_VERSION));
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
    }

    public void gameLoop() {
        // ... (same as before)
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
        // ... (same as before)
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
        // ... (same as before)
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
        // ... (same, ensure currentlyActiveLogicalChunks and globalSkyRefreshNeededQueue are cleared)
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
        // ... (same as before)
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
        // ... (same as before)
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

    private byte calculateSkyLightForTime(double time) {
        // ... (same as before)
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
        boolean significantChange = Math.abs(currentGlobalSkyLightActual - lastGlobalSkyLightTargetSetInLM) >= SKY_LIGHT_UPDATE_THRESHOLD;
        boolean boundaryReached = (currentGlobalSkyLightActual == SKY_LIGHT_DAY && lastGlobalSkyLightTargetSetInLM != SKY_LIGHT_DAY) ||
                (currentGlobalSkyLightActual == SKY_LIGHT_NIGHT && lastGlobalSkyLightTargetSetInLM != SKY_LIGHT_NIGHT) ||
                (currentGlobalSkyLightActual == SKY_LIGHT_NIGHT_MINIMUM && lastGlobalSkyLightTargetSetInLM != SKY_LIGHT_NIGHT_MINIMUM);

        if (significantChange || boundaryReached) {
            if (lightManager != null) {
                lightManager.setCurrentGlobalSkyLightTarget(currentGlobalSkyLightActual);
                // Add all currently active chunks to the refresh queue if they aren't already processing for this new target
                // A more robust way would be to check if a chunk is already queued for *this specific target*
                // For now, re-adding is okay as poll() will take them one by one.
                // Make sure not to add duplicates if they are already in queue for *this same target*.
                // For simplicity, we'll just add all active ones. Game loop will process them.
                // If they are already in queue, they'll be processed eventually.
                // If they are not, they get added.
                for (LightManager.ChunkCoordinate activeCoord : currentlyActiveLogicalChunks) {
                    if (!globalSkyRefreshNeededQueue.contains(activeCoord)) { // Simple duplicate check
                        globalSkyRefreshNeededQueue.offer(activeCoord);
                    }
                }
            }
            lastGlobalSkyLightTargetSetInLM = currentGlobalSkyLightActual;
            // System.out.println("Sky light target changed to: " + currentGlobalSkyLightActual + ". Refresh queue size: " + globalSkyRefreshNeededQueue.size());
        }
    }


    private void updateGameLogic(double deltaTime) {
        pseudoTimeOfDay += deltaTime * DAY_NIGHT_CYCLE_SPEED;
        if (pseudoTimeOfDay >= 1.0) pseudoTimeOfDay -= 1.0;

        updateActiveChunksAroundPlayer();
        updateSkyLightBasedOnTimeOfDay(deltaTime);

        // Process a few chunks from the global sky refresh queue
        if (lightManager != null && !globalSkyRefreshNeededQueue.isEmpty()) {
            int refreshedThisFrame = 0;
            while (refreshedThisFrame < CHUNKS_TO_REFRESH_SKY_PER_FRAME && !globalSkyRefreshNeededQueue.isEmpty()) {
                LightManager.ChunkCoordinate coordToRefresh = globalSkyRefreshNeededQueue.poll();
                if (coordToRefresh != null && currentlyActiveLogicalChunks.contains(coordToRefresh)) { // Ensure it's still active
                    lightManager.refreshSkyLightForSingleChunk(coordToRefresh, lightManager.getCurrentGlobalSkyLightTarget());
                    // After refreshing, give some immediate processing time for this chunk's light
                    lightManager.processLightQueuesIncrementally(LightManager.BATCH_LIGHT_UPDATE_BUDGET / CHUNKS_TO_REFRESH_SKY_PER_FRAME);
                    Set<LightManager.ChunkCoordinate> dirtyFromThisRefresh = lightManager.getDirtyChunksAndClear();
                    for (LightManager.ChunkCoordinate dirtyCoord : dirtyFromThisRefresh) {
                        if (!chunkRenderUpdateQueue.contains(dirtyCoord) && currentlyActiveLogicalChunks.contains(dirtyCoord)) {
                            chunkRenderUpdateQueue.offer(dirtyCoord);
                        }
                    }
                }
                refreshedThisFrame++;
            }
        }

        if (inputHandler != null) inputHandler.handleContinuousInput(deltaTime);
        if (player != null) player.update(deltaTime);
        if (cameraManager != null) cameraManager.update(deltaTime);

        if (lightManager != null) {
            lightManager.processLightQueuesIncrementally(); // Regular incremental update for other light changes
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

    public void saveGame(String worldName) {
        // ... (same as before)
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

    public boolean loadGame(String worldNameOrFileName) {
        // ... (ensure queues are cleared and initial refresh is triggered)
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

        globalSkyRefreshNeededQueue.addAll(currentlyActiveLogicalChunks); // Queue all for refresh

        // Process torches for newly active chunks
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

        System.out.println("Game loaded successfully: " + this.currentWorldName + ". Initial sky refresh queue: " + globalSkyRefreshNeededQueue.size());
        setCurrentGameState(GameState.IN_GAME);
        refreshAvailableSaveFiles();
        return true;
    }


    public String getCurrentWorldName() { return currentWorldName; }
    public void toggleInventory() { this.showInventory = !this.showInventory; }
    public boolean isInventoryVisible() { return this.showInventory; }
    public GameState getCurrentGameState() { return currentGameState; }

    public void setCurrentGameState(GameState newState) {
        // ... (same as before)
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
            if (oldState == GameState.MAIN_MENU && currentWorldName != null) { // Returning to an existing game
                updateActiveChunksAroundPlayer(); // Ensure current area is loaded
                // Queue active chunks for sky light refresh based on current time
                lightManager.setCurrentGlobalSkyLightTarget(this.currentGlobalSkyLightActual);
                globalSkyRefreshNeededQueue.clear();
                globalSkyRefreshNeededQueue.addAll(currentlyActiveLogicalChunks);
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

            if (this.selectedInventorySlotIndex != index) { // Check if it actually changed
                this.selectedInventorySlotIndex = index;
                player.setSelectedHotbarSlotIndex(index);
                if (renderer != null) {
                    renderer.setHotbarDirty(true); // Mark hotbar for redraw
                }
            }
        } else if (index == -1 && this.selectedInventorySlotIndex != -1) { // Deselecting
            this.selectedInventorySlotIndex = -1;
            // player.setSelectedHotbarSlotIndex(-1); // If player model supports this
            if (renderer != null) {
                renderer.setHotbarDirty(true);
            }
        }
    }

    private void renderGame() {
        // ... (same as before, ensure debug overlay line for light queues is tall enough)
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
                renderer.renderDebugOverlay(10f, 10f, 1300f, 300f, debugLines); // Increased height
            }
            glEnable(GL_DEPTH_TEST);
        }
    }

    public void requestFullMapRegeneration() {
        // ... (same as before)
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
        // ... (same as before)
        if (renderer != null && CHUNK_SIZE_TILES > 0) {
            LightManager.ChunkCoordinate coord = new LightManager.ChunkCoordinate(c / CHUNK_SIZE_TILES, r / CHUNK_SIZE_TILES);
            if (!chunkRenderUpdateQueue.contains(coord) && currentlyActiveLogicalChunks.contains(coord)) {
                chunkRenderUpdateQueue.offer(coord);
            }
        }
    }
    public boolean isShowHotbar() { return this.showHotbar; }

    public void interactWithTree(int r, int c, PlayerModel playerWhoActed, Item toolUsed) {
        // ... (same as before)
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

    private boolean showDebugOverlay = true;
    public void toggleShowDebugOverlay() { this.showDebugOverlay = !this.showDebugOverlay; }

    private void cleanup() {
        // ... (same as before)
        System.out.println("Game cleanup initiated...");
        if (renderer != null) renderer.cleanup();
        System.out.println("Game cleanup complete.");
    }
}