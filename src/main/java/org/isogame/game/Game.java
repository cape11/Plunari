// Game.java
package org.isogame.game;

import org.isogame.input.InputHandler;
import org.isogame.input.MouseHandler;
import org.isogame.camera.CameraManager;
import org.isogame.map.Map;
import org.isogame.render.Renderer;
import org.isogame.entitiy.PlayerModel;

import static org.isogame.constants.Constants.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Game {
    private double lastFrameTime; // <--- FIRST DECLARATION (Correct, as a class field)

    private final long window;
    private InputHandler inputHandler;
    private MouseHandler mouseHandler;
    private final CameraManager cameraManager;
    private final Renderer renderer;
    private final Map map;
    private final PlayerModel player;

    // ... existing fields ...
    private double pseudoTimeOfDay = 0.0; // 0.0 to 1.0, represents a full cycle
    private final double PSEUDO_DAY_CYCLE_SPEED = 0.005; // Adjust for speed (smaller is slower)
    // You might want this much smaller for a slow visual change
    // e.g., 0.0005 for a cycle every ~33 seconds at 60fps
// ...

    public Game(long window, int initialFramebufferWidth, int initialFramebufferHeight) {
        this.window = window;

        map = new Map(MAP_WIDTH, MAP_HEIGHT);
        player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
        cameraManager = new CameraManager(initialFramebufferWidth, initialFramebufferHeight, map.getWidth(), map.getHeight());
        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());

        // Pass 'this' (Game instance) to InputHandler so it can call methods on Game
        inputHandler = new InputHandler(window, cameraManager, map, player, this);
        inputHandler.registerCallbacks(this::requestMapGeometryUpdate); // This is for 'G' key (full map regen)

        renderer = new Renderer(cameraManager, map, player, inputHandler);
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
        // Initial clear color can be set here, or just rely on renderGame()
        // glClearColor(0.1f, 0.2f, 0.3f, 1.0f); // Default dark blueish
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }


    public void gameLoop() {
        initOpenGL();
        lastFrameTime = glfwGetTime();
        System.out.println("Entering game loop...");
        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            double deltaTime = currentTime - lastFrameTime;
            lastFrameTime = currentTime;
            if (deltaTime > 0.1) deltaTime = 0.1; // Cap delta time to prevent large jumps

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

        // Update pseudo time of day
        pseudoTimeOfDay += deltaTime * PSEUDO_DAY_CYCLE_SPEED;
        if (pseudoTimeOfDay >= 1.0) {
            pseudoTimeOfDay -= 1.0; // Loop back
        }
    }

    private void renderGame() {
        // Calculate sky color based on pseudoTimeOfDay
        float r, g, b;

        if (pseudoTimeOfDay < 0.25) { // Night (0.0 - 0.25) -> Dark Blue to Black
            float phase = (float) (pseudoTimeOfDay / 0.25); // 0 to 1
            r = 0.0f + 0.1f * (1.0f - phase); // From 0.1 (dark blue) to 0.0 (black)
            g = 0.0f + 0.1f * (1.0f - phase);
            b = 0.1f + 0.2f * (1.0f - phase); // From 0.3 (dark blue) to 0.1 (darker)
        } else if (pseudoTimeOfDay < 0.5) { // Dawn/Morning (0.25 - 0.5) -> Dark Blue to Light Blue/Orange
            float phase = (float) ((pseudoTimeOfDay - 0.25) / 0.25); // 0 to 1
            // Transition from night blue (0.0, 0.0, 0.1) to a morning sky blue (0.5, 0.7, 1.0)
            r = 0.0f + 0.5f * phase;
            g = 0.0f + 0.7f * phase;
            b = 0.1f + 0.9f * phase;
            // Optional: Add a hint of orange for sunrise
            // r += 0.3f * phase * (1.0f - phase); // peaks in middle of sunrise
        } else if (pseudoTimeOfDay < 0.75) { // Day (0.5 - 0.75) -> Light Blue
            float phase = (float) ((pseudoTimeOfDay - 0.5) / 0.25); // 0 to 1
            // Transition from morning sky blue (0.5, 0.7, 1.0) to a slightly different day blue (0.4, 0.6, 0.9)
            r = 0.5f - 0.1f * phase;
            g = 0.7f - 0.1f * phase;
            b = 1.0f - 0.1f * phase;
        } else { // Dusk/Evening (0.75 - 1.0) -> Light Blue to Orange/Dark Blue
            float phase = (float) ((pseudoTimeOfDay - 0.75) / 0.25); // 0 to 1
            // Transition from day blue (0.4, 0.6, 0.9) to night blue (0.0, 0.0, 0.1)
            // Add some orange/red for sunset
            r = 0.4f * (1.0f - phase) + 0.6f * phase * (1.0f - phase); // Day blue fades, orange hint
            g = 0.6f * (1.0f - phase) + 0.2f * phase * (1.0f - phase); // Day blue fades, orange hint
            b = 0.9f * (1.0f - phase) + 0.1f * (1.0f-phase); // Day blue fades towards darker blue
        }

        // Clamp colors
        r = Math.max(0.0f, Math.min(1.0f, r));
        g = Math.max(0.0f, Math.min(1.0f, g));
        b = Math.max(0.0f, Math.min(1.0f, b));

        glClearColor(r, g, b, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT); // Add | GL_DEPTH_BUFFER_BIT if depth test enabled

        if (renderer != null) {
            renderer.render();
        }
    }

    // This is for full map regeneration (e.g., 'G' key)
    public void requestMapGeometryUpdate() {
        if (map != null) {
            map.generateMap();
        }
        if (player != null && map != null) {
            player.setPosition(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
            player.setPath(null); // Clear any existing path
            if (inputHandler != null) {
                inputHandler.setSelectedTile(player.getTileCol(), player.getTileRow());
            }
            if (cameraManager != null) {
                cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());
            }
        }
        if (renderer != null) {
            System.out.println("Game: Explicit request for FULL map geometry update due to structural change.");
            renderer.uploadTileMapGeometry(); // Rebuilds ALL chunks
        }
    }

    // Method for handling targeted render updates for single tiles (e.g., after Q/E elevation change)
    public void requestTileRenderUpdate(int row, int col) {
        if (renderer != null) {
            System.out.println("Game: Requesting render update for tile: (" + row + ", " + col + ")");
            renderer.updateChunkContainingTile(row, col); // This method needs to exist in Renderer
        }
    }

    private void cleanup() {
        System.out.println("Game cleanup initiated...");
        if (renderer != null) {
            renderer.cleanup();
        }
        System.out.println("Game cleanup complete.");
    }
}