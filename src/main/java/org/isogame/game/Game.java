// Game.java
package org.isogame.game;

import org.isogame.input.InputHandler;
import org.isogame.input.MouseHandler;
import org.isogame.camera.CameraManager;
import org.isogame.map.Map;
import org.isogame.render.Renderer;
import org.isogame.entitiy.PlayerModel;
import org.isogame.tile.Tile;

import java.util.ArrayList;
import java.util.List;

import static org.isogame.constants.Constants.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_LEQUAL; // For glDepthFunc

public class Game {
    private double lastFrameTime;

    private final long window;
    private InputHandler inputHandler;
    private MouseHandler mouseHandler;
    private final CameraManager cameraManager;
    private final Renderer renderer;
    private final Map map;
    private final PlayerModel player;

    private double pseudoTimeOfDay = 0.0;
    private final double PSEUDO_DAY_CYCLE_SPEED = 0.005;


    public Game(long window, int initialFramebufferWidth, int initialFramebufferHeight) {
        this.window = window;

        map = new Map(MAP_WIDTH, MAP_HEIGHT);
        player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
        cameraManager = new CameraManager(initialFramebufferWidth, initialFramebufferHeight, map.getWidth(), map.getHeight());
        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());

        inputHandler = new InputHandler(window, cameraManager, map, player, this);
        inputHandler.registerCallbacks(this::requestMapGeometryUpdate);

        renderer = new Renderer(cameraManager, map, player, inputHandler); // Renderer now uses CameraManager
        mouseHandler = new MouseHandler(window, cameraManager, map, inputHandler, player);

        glfwSetFramebufferSizeCallback(window, (win, fbW, fbH) -> {
            System.out.println("Framebuffer resized to: " + fbW + "x" + fbH);
            if (cameraManager != null) {
                cameraManager.updateScreenSize(fbW, fbH);
            }
            if (renderer != null) {
                renderer.onResize(fbW, fbH);
            }
        });

        if (renderer != null) {
            renderer.onResize(initialFramebufferWidth, initialFramebufferHeight);
        }
        System.out.println("Game components initialized.");
    }

    private void initOpenGL() {
        System.out.println("Game.initOpenGL() - OpenGL version from context: " + glGetString(GL_VERSION));
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // --- Enable Depth Testing ---
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL); // Standard depth function
        System.out.println("Game.initOpenGL() - Depth Test Enabled.");
    }


    public void gameLoop() {
        initOpenGL(); // OpenGL specific initializations
        lastFrameTime = glfwGetTime();
        System.out.println("Entering game loop...");
        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            double deltaTime = currentTime - lastFrameTime;
            lastFrameTime = currentTime;
            if (deltaTime > 0.1) deltaTime = 0.1;

            glfwPollEvents();
            updateGameLogic(deltaTime);
            renderGame();
            glfwSwapBuffers(window);
        }
        System.out.println("Game loop exited.");
        cleanup();
    }

    private void updateGameLogic(double deltaTime) {
        if (inputHandler != null) {
            inputHandler.handleContinuousInput(deltaTime);
        }
        if (player != null) {
            player.update(deltaTime);
        }
        if (cameraManager != null) {
            cameraManager.update(deltaTime);
        }
        pseudoTimeOfDay += deltaTime * PSEUDO_DAY_CYCLE_SPEED;
        if (pseudoTimeOfDay >= 1.0) {
            pseudoTimeOfDay -= 1.0;
        }
    }

    private void renderGame() {
        float r, g, b;
        if (pseudoTimeOfDay < 0.25) {
            float phase = (float) (pseudoTimeOfDay / 0.25);
            r = 0.0f + 0.1f * (1.0f - phase);
            g = 0.0f + 0.1f * (1.0f - phase);
            b = 0.1f + 0.2f * (1.0f - phase);
        } else if (pseudoTimeOfDay < 0.5) {
            float phase = (float) ((pseudoTimeOfDay - 0.25) / 0.25);
            r = 0.0f + 0.5f * phase;
            g = 0.0f + 0.7f * phase;
            b = 0.1f + 0.9f * phase;
        } else if (pseudoTimeOfDay < 0.75) {
            float phase = (float) ((pseudoTimeOfDay - 0.5) / 0.25);
            r = 0.5f - 0.1f * phase;
            g = 0.7f - 0.1f * phase;
            b = 1.0f - 0.1f * phase;
        } else {
            float phase = (float) ((pseudoTimeOfDay - 0.75) / 0.25);
            r = 0.4f * (1.0f - phase) + 0.6f * phase * (1.0f - phase);
            g = 0.6f * (1.0f - phase) + 0.2f * phase * (1.0f - phase);
            b = 0.9f * (1.0f - phase) + 0.1f * (1.0f - phase);
        }
        r = Math.max(0.0f, Math.min(1.0f, r));
        g = Math.max(0.0f, Math.min(1.0f, g));
        b = Math.max(0.0f, Math.min(1.0f, b));

        glClearColor(r, g, b, 1.0f);
        // --- Clear both Color and Depth buffers ---
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (renderer != null) {
            renderer.render();

            if (this.showDebugOverlay) { // Check if the overlay should be shown
                List<String> debugLines = new ArrayList<>();
                // Collect all your debug information into the list
                if (player != null && cameraManager != null && inputHandler != null && map != null) {
                    debugLines.add("Player: (" + player.getTileRow() + ", " + player.getTileCol() + ") Act: " + player.getCurrentAction() + " Dir: " + player.getCurrentDirection() + " F:" + player.getVisualFrameIndex()); //
                    Tile selectedTile = map.getTile(inputHandler.getSelectedRow(), inputHandler.getSelectedCol()); //
                    String selectedInfo = "Selected: (" + inputHandler.getSelectedRow() + ", " + inputHandler.getSelectedCol() + ")"; //
                    if (selectedTile != null) {
                        selectedInfo += " Elev: " + selectedTile.getElevation() + " Type: " + selectedTile.getType(); //
                    }
                    debugLines.add(selectedInfo);
                    debugLines.add(String.format("Camera: (%.1f, %.1f) Zoom: %.2f", cameraManager.getCameraX(), cameraManager.getCameraY(), cameraManager.getZoom())); //
                    debugLines.add("Move: Click | Elev Sel +/-: Q/E | Dig: J"); //
                    debugLines.add("Levitate: F | Center Cam: C | Regen Map: G | Debug: F5"); // Updated help text
                    debugLines.add(""); // Spacer

                    debugLines.add("Inventory:"); //
                    java.util.Map<String, Integer> inventory = player.getInventory(); //
                    if (inventory.isEmpty()) { //
                        debugLines.add("  - Empty -"); //
                    } else {
                        for (java.util.Map.Entry<String, Integer> entry : inventory.entrySet()) { //
                            debugLines.add("  - " + entry.getKey() + ": " + entry.getValue()); //
                        }
                    }
                } else {
                    debugLines.add("Debug info unavailable: one or more components are null.");
                }

                // Define panel properties (adjust as needed)
                float panelX = 10f;
                float panelY = 10f;
                float estimatedLineHeight = 20f; // Based on current Renderer.renderUI()
                float textPadding = 5f;
                float panelWidth = 900f; // Increased width for potentially longer lines
                float panelHeight = (debugLines.size() * estimatedLineHeight) + (2 * textPadding);

                // Call the new method in Renderer
                renderer.renderDebugOverlay(panelX, panelY, panelWidth, panelHeight, debugLines);
            }
        }
    }


    public void requestMapGeometryUpdate() {
        if (map != null) {
            map.generateMap();
        }
        if (player != null && map != null) {
            player.setPosition(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
            player.setPath(null);
            if (inputHandler != null) {
                inputHandler.setSelectedTile(player.getTileCol(), player.getTileRow());
            }
            if (cameraManager != null) {
                cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
            }
        }
        if (renderer != null) {
            System.out.println("Game: Explicit request for FULL map geometry update due to structural change.");
            renderer.uploadTileMapGeometry();
        }
    }

    public void requestTileRenderUpdate(int row, int col) {
        if (renderer != null) {
            System.out.println("Game: Requesting render update for tile: (" + row + ", " + col + ")");
            renderer.updateChunkContainingTile(row, col);
        }
    }

    private boolean showDebugOverlay = false; // Set to true if you want it on by default

    public void toggleShowDebugOverlay() {
        this.showDebugOverlay = !this.showDebugOverlay;
        System.out.println("Debug Overlay: " + (this.showDebugOverlay ? "ON" : "OFF"));
    }

    public boolean isDebugOverlayVisible() {
        return this.showDebugOverlay;
    }

    private void cleanup() {
        System.out.println("Game cleanup initiated...");
        if (renderer != null) {
            renderer.cleanup();
        }
        System.out.println("Game cleanup complete.");
    }
}
