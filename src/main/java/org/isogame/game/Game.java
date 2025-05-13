package org.isogame.game;

import org.isogame.input.InputHandler;
import org.isogame.input.MouseHandler;
import org.isogame.camera.CameraManager;
import org.isogame.map.Map;
import org.isogame.render.Renderer;
import org.isogame.entity.PlayerModel;

import static org.isogame.constants.Constants.*; // For MAP_WIDTH, MAP_HEIGHT etc.
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Game {

    private final long window;
    private final InputHandler inputHandler;
    private final MouseHandler mouseHandler;
    private final CameraManager cameraManager;
    private final Renderer renderer;
    private final Map map;
    private final PlayerModel player;

    private double lastFrameTime;
    private double accumulator = 0.0;

    // Constructor now takes initial framebuffer dimensions
    public Game(long window, int initialFramebufferWidth, int initialFramebufferHeight) {
        this.window = window;

        // Core components
        map = new Map(MAP_WIDTH, MAP_HEIGHT);
        player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());

        // IMPORTANT: Initialize CameraManager with actual framebuffer dimensions
        cameraManager = new CameraManager(initialFramebufferWidth, initialFramebufferHeight, map.getWidth(), map.getHeight());
        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());

        inputHandler = new InputHandler(window, cameraManager, map, player);
        // Pass player to MouseHandler for pathfinding start position
        mouseHandler = new MouseHandler(window, cameraManager, map, inputHandler, player);
        renderer = new Renderer(cameraManager, map, player, inputHandler);

        inputHandler.registerCallbacks(map::generateMap);

        // Framebuffer resize callback is crucial for runtime changes
        glfwSetFramebufferSizeCallback(window, (win, fbW, fbH) -> {
            System.out.println("Framebuffer resized to: " + fbW + "x" + fbH);
            cameraManager.updateScreenSize(fbW, fbH); // Update camera with new pixel dimensions
            if (renderer != null) {
                renderer.onResize(fbW, fbH); // This will call glViewport and update projection
            }
        });

        // Initial call to renderer.onResize with correct framebuffer size
        // This ensures viewport and projection are correct from the very start
        if (renderer != null) {
            System.out.println("Initial renderer setup with framebuffer: " + initialFramebufferWidth + "x" + initialFramebufferHeight);
            renderer.onResize(initialFramebufferWidth, initialFramebufferHeight);
        } else {
            System.err.println("Renderer was null during Game constructor's initial onResize call!");
        }
        System.out.println("Game components initialized.");
    }

    private void initOpenGL() {
        // GL.createCapabilities() is now in Main.initWindow() and should have been called before this.
        System.out.println("Game.initOpenGL() - OpenGL version from context: " + glGetString(GL_VERSION));

        glClearColor(0.1f, 0.2f, 0.3f, 1.0f); // Background color
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // The initial renderer.onResize call in the Game constructor already set up
        // the viewport and projection matrix for the renderer.
    }

    public void gameLoop() {
        initOpenGL(); // Prepare OpenGL states for this thread's context
        lastFrameTime = glfwGetTime();

        System.out.println("Entering game loop...");

        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            double deltaTime = currentTime - lastFrameTime;
            lastFrameTime = currentTime;

            // Cap deltaTime to prevent large jumps if game lags
            if (deltaTime > 0.1) { // Max step of 0.1 seconds (10 FPS equivalent)
                deltaTime = 0.1;
            }

            glfwPollEvents(); // Process window and input events

            updateGameLogic(deltaTime); // Update game state
            renderGame();               // Render the game

            glfwSwapBuffers(window);    // Display the rendered frame
        }

        System.out.println("Game loop exited.");
        cleanup();
    }

    private void updateGameLogic(double deltaTime) {
        if (inputHandler != null) inputHandler.handleContinuousInput(deltaTime);
        if (player != null) player.update(deltaTime);
        if (cameraManager != null) cameraManager.update(deltaTime);
    }

    private void renderGame() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        if (renderer != null) {
            renderer.render();
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