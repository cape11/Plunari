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

    private final long window;
    private InputHandler inputHandler;
    private MouseHandler mouseHandler;
    private final CameraManager cameraManager;
    private final Renderer renderer;
    private final Map map;
    private final PlayerModel player;

    private double lastFrameTime;

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
        glClearColor(0.1f, 0.2f, 0.3f, 1.0f);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        // glEnable(GL_DEPTH_TEST); // Enable if needed for complex 3D overlaps
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
        // The call to inputHandler.handleContinuousInput(deltaTime) should be here
        // Ensure that method exists and is public in your InputHandler class
        if (inputHandler != null) {
            inputHandler.handleContinuousInput(deltaTime);
        }
        if (player != null) {
            player.update(deltaTime);
        }
        if (cameraManager != null) {
            cameraManager.update(deltaTime);
        }
    }

    private void renderGame() {
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