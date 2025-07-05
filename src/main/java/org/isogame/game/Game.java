// src/main/java/org/isogame/game/Game.java
package org.isogame.game;

import org.isogame.asset.AssetManager;
import org.isogame.constants.Constants;
import org.isogame.crafting.CraftingRecipe;
import org.isogame.crafting.FurnaceRecipeRegistry;
import org.isogame.crafting.RecipeRegistry;
import org.isogame.game.EntityManager;
import org.isogame.entity.PlayerModel;
import org.isogame.input.InputHandler;
import org.isogame.input.MouseHandler;
import org.isogame.camera.CameraManager;
import org.isogame.item.InventorySlot;
import org.isogame.item.Item; // <--- FIX: Added missing import
import org.isogame.item.ItemRegistry;
import org.isogame.map.LightManager;
import org.isogame.map.Map;
import org.isogame.render.Renderer;
import org.isogame.savegame.*;
import org.isogame.tile.FurnaceEntity;
import org.isogame.ui.MenuItemButton;
import org.isogame.ui.UIManager;
import org.isogame.world.World;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.isogame.world.structure.StructureManager;

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
import java.util.Queue;
import java.util.Random;

import static org.isogame.constants.Constants.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Game {
    private double lastFrameTime;

    // Core Engine Components
    private final long window;
    private final CameraManager cameraManager;
    private final Renderer renderer;
    private final InputHandler inputHandler;
    private final MouseHandler mouseHandler;
    private final UIManager uiManager;
    private final AssetManager assetManager;
    private final org.isogame.game.GameStateManager gameStateManager;

    // The single instance of the World, encapsulating all simulation logic.
    private World world;

    // Game-level state (UI, session, etc.)
    private int framesRenderedThisSecond = 0;
    private double timeAccumulatorForFps = 0.0;
    private double displayedFps = 0.0;
    private int currentRenderDistanceChunks = Constants.RENDER_DISTANCE_CHUNKS_DEFAULT;
    private boolean showInventory = false;
    private boolean showHotbar = true;
    private boolean showDebugOverlay = true;
    private String currentWorldName = null;
    private List<String> availableSaveFiles = new ArrayList<>();
    private static final String SAVES_DIRECTORY = "saves";

    // UI Interaction State
    private boolean isDraggingItem = false;
    private InventorySlot draggedItemStack = null;
    private int originalDragSlotIndex = -1;
    private String originalDragSource = null;
    private CraftingRecipe hoveredRecipe = null;
    private List<MenuItemButton> menuButtons = new ArrayList<>();
    private boolean hotbarDirty = true;


    public Game(long window, int initialFramebufferWidth, int initialScreenHeight) {
        this.window = window;
        new File(SAVES_DIRECTORY).mkdirs();

        System.out.println("Game Constructor: Initializing core components...");
        try {
            cameraManager = new CameraManager(initialFramebufferWidth, initialScreenHeight, 1, 1);
            // Renderer is initialized for the menu state, without game world references
            renderer = new Renderer(cameraManager, null, null, null);
            renderer.onResize(initialFramebufferWidth, initialScreenHeight);

            assetManager = new AssetManager(renderer);
            assetManager.loadAllAssets();
            renderer.setAssetManager(assetManager);

            ItemRegistry.loadItems();
            RecipeRegistry.loadRecipes();
            FurnaceRecipeRegistry.loadRecipes();
            org.isogame.gamedata.TileRegistry.loadTileDefinitions(); //
            ItemRegistry.initializeItemUVs(assetManager.getTextureMapForRegistry());

            // Input handlers are initialized without world-specific references
            inputHandler = new InputHandler(window, cameraManager, null, null, this);
            inputHandler.registerCallbacks(this::requestFullMapRegeneration);
            mouseHandler = new MouseHandler(window, cameraManager, null, inputHandler, null, this);

            uiManager = new UIManager(this, renderer, null, assetManager, inputHandler);
            this.gameStateManager = new org.isogame.game.GameStateManager(this, renderer);
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


    // In Game.java, add this new method
    public void dropItemOnFurnace(String slotType) {
        if (!isDraggingItem() || uiManager.getActiveFurnace() == null) return;

        FurnaceEntity furnace = uiManager.getActiveFurnace();
        InventorySlot targetSlot = null;
        boolean isValidDrop = false;

        switch (slotType) {
            case "INPUT":
                targetSlot = furnace.getInputSlot();
                // Any item can be an input, for now. The furnace will just ignore invalid ones.
                isValidDrop = true;
                break;
            case "FUEL":
                targetSlot = furnace.getFuelSlot();
                // Check if the dragged item is a valid fuel type.
                if (FurnaceEntity.isFuel(getDraggedItemStack().getItem())) {
                    isValidDrop = true;
                }
                break;
        }

        if (isValidDrop && targetSlot != null && targetSlot.isEmpty()) {
            // The drop is valid and the target slot is empty, so move the item.
            targetSlot.addItem(draggedItemStack.getItem(), draggedItemStack.getQuantity());
            // Clear the dragged item since the drop was successful.
            this.isDraggingItem = false;
            this.draggedItemStack = null;
            this.originalDragSlotIndex = -1;
        } else {
            // The drop was invalid (e.g., wrong item type or slot was full),
            // so return the item to its original inventory slot.
            stopDraggingItem(originalDragSlotIndex);
        }
    }


    /**
     * Main update logic method. In the refactored design, this simply delegates
     * the simulation update to the current World instance.
     * @param deltaTime The time elapsed since the last frame.
     */
    public void updateGameLogic(double deltaTime) {
        if (world != null) {
            world.update(deltaTime);
        }
        // All other logic (time, chunk updates, spawning, lighting) is now inside world.update()
    }

    public void gameLoop() {
        initOpenGL();
        lastFrameTime = glfwGetTime();

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

            // *** FIX: These calls were missing and are crucial for game feel and input ***
            inputHandler.handleContinuousInput(deltaTime);
            cameraManager.update(deltaTime);

            gameStateManager.update(deltaTime);

            if (gameStateManager.getCurrentState() instanceof org.isogame.game.states.InGameState) {
                float rSky, gSky, bSky;
                float lightRatio = 0.5f;
                if (getLightManager() != null) {
                    float lightRange = (float)(SKY_LIGHT_DAY - SKY_LIGHT_NIGHT_MINIMUM);
                    lightRatio = lightRange > 0 ? (float)(getLightManager().getCurrentGlobalSkyLightTarget() - SKY_LIGHT_NIGHT_MINIMUM) / lightRange : 0;
                }
                float dayR = 0.5f, dayG = 0.7f, dayB = 1.0f;
                float nightR = 0.02f, nightG = 0.02f, nightB = 0.08f;
                rSky = nightR + (dayR - nightR) * lightRatio;
                gSky = nightG + (dayG - nightG) * lightRatio;
                bSky = nightB + (dayB - nightB) * lightRatio;
                glClearColor(rSky, gSky, bSky, 1.0f);
            } else {
                glClearColor(0.1f, 0.12f, 0.15f, 1.0f);
            }
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            gameStateManager.render(deltaTime);
            glfwSwapBuffers(window);
        }
        gameStateManager.getCurrentState().exit();
        cleanup();
    }

    /**
     * Updates engine components (Renderer, Input, UI) with references
     * from the newly created or loaded World instance.
     */
    private void initializeGameWorldReferences() {
        if (this.world == null) {
            System.err.println("Attempted to initialize references with a null world.");
            return;
        }

        PlayerModel player = world.getPlayer();
        Map map = world.getMap();
        EntityManager entityManager = world.getEntityManager();

        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
        inputHandler.updateGameReferences(map, player);
        mouseHandler.updateGameReferences(map, player, inputHandler);
        renderer.setGameSpecificReferences(map, player, inputHandler, entityManager);
        uiManager.updateGameReferences(player, assetManager);

        int[] fbW = new int[1], fbH = new int[1];
        glfwGetFramebufferSize(window, fbW, fbH);
        if (fbW[0] > 0 && fbH[0] > 0) renderer.onResize(fbW[0], fbH[0]);

        System.out.println("Game references updated from world instance.");
        forceFullRenderUpdate();

    }

    public void createNewWorld() {
        System.out.println("Game: createNewWorld() called.");
        int nextWorldNum = 1;
        while (Files.exists(Paths.get(SAVES_DIRECTORY, "World" + nextWorldNum + ".json"))) {
            nextWorldNum++;
        }
        String newWorldName = "World" + nextWorldNum;
        System.out.println("Creating new world: " + newWorldName);

        // Clear old graphics if a world was already loaded
        if (renderer.getMap() != null) {
            renderer.clearGameContext();
        }

        // Create the new World instance
        this.world = new World(this, new Random().nextLong());
        this.currentWorldName = newWorldName;

        // Link the engine components to the new world
        initializeGameWorldReferences();
        forceFullRenderUpdate();
        initializeGameWorldReferences();
        saveGame(this.currentWorldName);
        refreshAvailableSaveFiles();
        setCurrentGameState(org.isogame.game.GameStateManager.State.IN_GAME);
        System.out.println("Game: New world '" + newWorldName + "' created and game state set to IN_GAME.");
    }

    public boolean loadGame(String worldNameOrFileName) {
        System.out.println("Game: loadGame called for " + worldNameOrFileName);
        String fileName = worldNameOrFileName.endsWith(".json") ? worldNameOrFileName : worldNameOrFileName + ".json";
        String filePath = Paths.get(SAVES_DIRECTORY, fileName).toString();
        System.out.println("Attempting to load game from: " + filePath);

        GameSaveState saveState;
        try (Reader reader = new FileReader(filePath)) {
            saveState = new Gson().fromJson(reader, GameSaveState.class);
        } catch (IOException e) {
            System.err.println("Load game: Save file not found or unreadable - " + e.getMessage());
            return false;
        }

        if (saveState == null || saveState.mapData == null || saveState.playerData == null) {
            System.err.println("Load game: Parsed save data is null or incomplete.");
            return false;
        }

        // Clear old graphics if a world was already loaded
        if (renderer.getMap() != null) {
            renderer.clearGameContext();
        }

        // Create the world from the save state
        this.world = new World(this, saveState);
        this.currentWorldName = fileName.replace(".json", "");

        // Link the engine components to the new world
        initializeGameWorldReferences();
        forceFullRenderUpdate();

        System.out.println("Game loaded successfully: " + this.currentWorldName);
        setCurrentGameState(org.isogame.game.GameStateManager.State.IN_GAME);
        return true;
    }

    public void saveGame(String worldName) {
        if (worldName == null || worldName.trim().isEmpty() || this.world == null) {
            System.err.println("SaveGame Error: World name is null or world instance does not exist.");
            return;
        }
        String filePath = Paths.get(SAVES_DIRECTORY, worldName.replace(".json", "") + ".json").toString();

        GameSaveState saveState = new GameSaveState();
        this.world.getEntityManager().removeDeadEntities();

        world.populateSaveData(saveState);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = new FileWriter(filePath)) {
            gson.toJson(saveState, writer);
            System.out.println("Game saved successfully to " + filePath);
            this.currentWorldName = worldName.replace(".json", "");
            refreshAvailableSaveFiles();
        } catch (IOException e) {
            System.err.println("Error saving game: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void forceFullRenderUpdate() {
        if (world == null || renderer == null) return;

        // Step 1: Tell the world to queue up all visible chunks for a render update
        world.queueAllChunksForRenderUpdate();

        // Step 2: Manually drain the queue by calling the renderer, ignoring the per-frame limit
        Queue<LightManager.ChunkCoordinate> queue = world.getChunkRenderUpdateQueue();
        System.out.println("Forcing full render update on " + queue.size() + " chunks.");
        while (!queue.isEmpty()) {
            LightManager.ChunkCoordinate coord = queue.poll();
            renderer.updateChunkByGridCoords(coord.chunkX, coord.chunkY);
        }
    }

    // In Game.java
    public void startDraggingItemFromFurnace(String slotType) {
        if (isDraggingItem() || uiManager.getActiveFurnace() == null) return;

        FurnaceEntity furnace = uiManager.getActiveFurnace();
        InventorySlot sourceSlot = null;

        // Determine which slot we are taking from
        switch (slotType) {
            case "INPUT":   sourceSlot = furnace.getInputSlot(); break;
            case "FUEL":    sourceSlot = furnace.getFuelSlot(); break;
            case "OUTPUT":  sourceSlot = furnace.getOutputSlot(); break;
        }

        if (sourceSlot != null && !sourceSlot.isEmpty()) {
            // Start the drag operation
            this.draggedItemStack = new InventorySlot();
            this.draggedItemStack.addItem(sourceSlot.getItem(), sourceSlot.getQuantity());
            this.isDraggingItem = true;

            // Remember where the item came from
            this.originalDragSource = slotType;
            this.originalDragSlotIndex = -1; // Not from the main inventory

            // Clear the source slot
            sourceSlot.clearSlot();
            setHotbarDirty(true);
        }
    }


    // --- Other Methods ---

    public void initOpenGL() {
        System.out.println("Game.initOpenGL() - OpenGL version from context: " + glGetString(GL_VERSION));
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
    }

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
        menuButtons.add(newWorldButton);
        currentY += buttonHeight + buttonSpacing;

        float worldButtonWidth = buttonWidth * 0.75f;
        float deleteButtonWidth = buttonWidth * 0.20f;
        float totalPairWidth = worldButtonWidth + deleteButtonWidth + 5f;
        float worldButtonX = screenCenterX - totalPairWidth / 2f;
        float deleteButtonX = worldButtonX + worldButtonWidth + 5f;

        for (String saveFileName : availableSaveFiles) {
            String worldNameDisplay = saveFileName.replace(".json", "");
            MenuItemButton loadButton = new MenuItemButton(worldButtonX, currentY, worldButtonWidth, buttonHeight, "Load: " + worldNameDisplay, "LOAD_WORLD", saveFileName);
            menuButtons.add(loadButton);

            MenuItemButton deleteButton = new MenuItemButton(deleteButtonX, currentY, deleteButtonWidth, buttonHeight, "DEL", "DELETE_WORLD", worldNameDisplay);
            menuButtons.add(deleteButton);
            currentY += buttonHeight + buttonSpacing / 2;
        }
        currentY += buttonSpacing;

        MenuItemButton exitButton = new MenuItemButton(buttonX, currentY, buttonWidth, buttonHeight, "Exit Game", "EXIT_GAME", null);
        menuButtons.add(exitButton);
    }

    public void requestFullMapRegeneration() {
        System.out.println("Game: Full map regeneration requested (new world with new seed).");
        if (renderer.getMap() != null) {
            renderer.clearGameContext();
        }
        this.world = new World(this, new Random().nextLong());
        this.currentWorldName = null; // Unsaved world
        initializeGameWorldReferences();
        System.out.println("Game: Full map regeneration processing complete. World is now unsaved.");
    }

    public void deleteWorld(String worldName) {
        System.out.println("Game: deleteWorld() called for " + worldName);
        if (worldName == null || worldName.isEmpty()) return;
        String fileName = worldName.endsWith(".json") ? worldName : worldName + ".json";
        File saveFile = new File(SAVES_DIRECTORY, fileName);
        if (saveFile.exists()) {
            if (saveFile.delete()) {
                System.out.println("Deleted world: " + fileName);
                if (this.currentWorldName != null && this.currentWorldName.equals(worldName)) {
                    this.currentWorldName = null;
                }
            } else {
                System.err.println("Failed to delete world: " + fileName);
            }
        }
        refreshAvailableSaveFiles();
    }

    public void setSelectedHotbarSlotIndex(int index) {
        if (getPlayer() != null) {
            getPlayer().setSelectedHotbarSlotIndex(index);
        }
    }
    public void requestTileRenderUpdate(int r, int c) {
        if (world != null) {
            world.requestTileRenderUpdate(r, c);
        }
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

    public boolean canCraft(CraftingRecipe recipe) {
        PlayerModel player = getPlayer();
        if (player == null || recipe == null) return false;

        for (java.util.Map.Entry<Item, Integer> entry : recipe.getRequiredItems().entrySet()) {
            if (player.getInventoryItemCount(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public void doCraft(CraftingRecipe recipe) {
        PlayerModel player = getPlayer();
        if (player == null) return;

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

    public void stopDraggingItem(int dropSlotIndex) {
        if (!isDraggingItem) return;

        // Case 1: Dropped on a valid inventory slot
        if (dropSlotIndex != -1 && getPlayer() != null) {
            InventorySlot targetSlot = getPlayer().getInventorySlots().get(dropSlotIndex);
            if (targetSlot.isEmpty()) {
                targetSlot.addItem(draggedItemStack.getItem(), draggedItemStack.getQuantity());
            } else { // Swap items
                // Put the target's item back where the drag started
                if ("INVENTORY".equals(originalDragSource)) {
                    getPlayer().getInventorySlots().get(originalDragSlotIndex).addItem(targetSlot.getItem(), targetSlot.getQuantity());
                } // Note: You could add logic here to return items to the furnace too

                // Put the dragged item in the target slot
                targetSlot.clearSlot();
                targetSlot.addItem(draggedItemStack.getItem(), draggedItemStack.getQuantity());
            }
        } else { // Case 2: Dropped outside or on an invalid slot, return item to its origin
            if ("INVENTORY".equals(originalDragSource)) {
                getPlayer().getInventorySlots().get(originalDragSlotIndex).addItem(draggedItemStack.getItem(), draggedItemStack.getQuantity());
            } else if (originalDragSource != null && uiManager.getActiveFurnace() != null) {
                // Return the item to the correct furnace slot
                switch (originalDragSource) {
                    case "INPUT":   uiManager.getActiveFurnace().getInputSlot().addItem(draggedItemStack.getItem(), draggedItemStack.getQuantity()); break;
                    case "FUEL":    uiManager.getActiveFurnace().getFuelSlot().addItem(draggedItemStack.getItem(), draggedItemStack.getQuantity()); break;
                    case "OUTPUT":  uiManager.getActiveFurnace().getOutputSlot().addItem(draggedItemStack.getItem(), draggedItemStack.getQuantity()); break;
                }
            }
        }

        // Reset all dragging state variables
        this.isDraggingItem = false;
        this.draggedItemStack = null;
        this.originalDragSlotIndex = -1;
        this.originalDragSource = null;
        setHotbarDirty(true);
    }

    public void startDraggingItem(int slotIndex) {
        PlayerModel player = getPlayer();
        if (player == null || slotIndex < 0 || slotIndex >= player.getInventorySlots().size()) return;
        InventorySlot sourceSlot = player.getInventorySlots().get(slotIndex);
        if (sourceSlot.isEmpty()) return;

        this.draggedItemStack = new InventorySlot();
        this.draggedItemStack.addItem(sourceSlot.getItem(), sourceSlot.getQuantity());
        this.originalDragSlotIndex = slotIndex;
        this.isDraggingItem = true;
        this.originalDragSource = "INVENTORY";

        sourceSlot.clearSlot();
        setHotbarDirty(true);
    }

    private void cleanup() {
        System.out.println("Game cleanup initiated...");
        if (renderer != null) renderer.cleanup();
        System.out.println("Game cleanup complete.");
    }


    // --- Getters and Setters ---

    public float getMouseX() {
        return (mouseHandler != null) ? mouseHandler.getMouseX() : 0;
    }

    public float getMouseY() {
        return (mouseHandler != null) ? mouseHandler.getMouseY() : 0;
    }


    public World getWorld() { return this.world; }
    public int getOriginalDragSlotIndex() {return this.originalDragSlotIndex;}

    public Renderer getRenderer() { return this.renderer; }
    public UIManager getUiManager() { return this.uiManager; }
    public org.isogame.game.GameStateManager getGameStateManager() { return this.gameStateManager; }
    public void setCurrentGameState(org.isogame.game.GameStateManager.State newState) { gameStateManager.setState(newState); }
    public List<MenuItemButton> getMainMenuButtons() { return menuButtons; }

    // Delegated Getters to the World object
    public Map getMap() { return (world != null) ? world.getMap() : null; }
    public PlayerModel getPlayer() { return (world != null) ? world.getPlayer() : null; }
    public EntityManager getEntityManager() { return (world != null) ? world.getEntityManager() : null; }
    public LightManager getLightManager() { return (world != null) ? world.getLightManager() : null; }
    public PlacementManager getPlacementManager() { return (world != null) ? world.getPlacementManager() : null; }
    public StructureManager getStructureManager() {
        return (world != null) ? world.getStructureManager() : null;
    }
    public double getPseudoTimeOfDay() { return (world != null) ? world.getPseudoTimeOfDay() : 0.25; }

    // Game-level state getters/setters
    public int getCurrentRenderDistanceChunks() { return currentRenderDistanceChunks; }
    public void increaseRenderDistance() { this.currentRenderDistanceChunks = Math.min(this.currentRenderDistanceChunks + 1, RENDER_DISTANCE_CHUNKS_MAX); }
    public void decreaseRenderDistance() { this.currentRenderDistanceChunks = Math.max(RENDER_DISTANCE_CHUNKS_MIN, this.currentRenderDistanceChunks - 1); }
    public boolean isInventoryVisible() { return this.showInventory; }
    public void toggleInventory() { this.showInventory = !this.showInventory; }
    public boolean isShowHotbar() { return this.showHotbar; }
    public void toggleHotbar() { this.showHotbar = !this.showHotbar; }
    public boolean isShowDebugOverlay() { return this.showDebugOverlay; }
    public void toggleShowDebugOverlay() { this.showDebugOverlay = !this.showDebugOverlay; }
    public String getCurrentWorldName() { return currentWorldName; }
    public CraftingRecipe getHoveredRecipe() { return this.hoveredRecipe; }
    public void setHoveredRecipe(CraftingRecipe recipe) { this.hoveredRecipe = recipe; }
    public void setHotbarDirty(boolean dirty) { this.hotbarDirty = dirty; }
    public boolean isDraggingItem() { return this.isDraggingItem; }
    public InventorySlot getDraggedItemStack() { return this.draggedItemStack; }
    public long getWindowHandle() { return this.window; }
}