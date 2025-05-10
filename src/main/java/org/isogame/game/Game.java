package org.isogame.game;

import org.isogame.input.InputHandler;
import org.isogame.input.MouseHandler;
import org.isogame.camera.CameraManager;
import org.isogame.map.Map;
import org.isogame.render.Renderer;
import org.isogame.entity.PlayerModel;
// GL import is not strictly needed here if not directly calling GL.createCapabilities()
// import org.lwjgl.opengl.GL; // Can be removed if GL.createCapabilities() is solely in Main

import static org.isogame.constants.Constants.*;
import static org.lwjgl.glfw.GLFW.*; // For glfwGetTime, glfwWindowShouldClose, glfwSwapBuffers, glfwPollEvents
import static org.lwjgl.opengl.GL11.*; // For glGetString, glClearColor, GL_BLEND, etc.

public class Game {

    private final long window;
    private final InputHandler inputHandler;
    private final MouseHandler mouseHandler;
    private final CameraManager cameraManager;
    private final Renderer renderer;
    private final Map map;
    private final PlayerModel player;

    // Timing
    private double lastFrameTime;
    private double accumulator = 0.0; // For fixed timestep update (currently not fully implemented but stubbed)

    public Game(long window) {
        this.window = window;

        // Core components
        map = new Map(MAP_WIDTH, MAP_HEIGHT); // Ensure MAP_WIDTH, MAP_HEIGHT are appropriate
        player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
        cameraManager = new CameraManager(WIDTH, HEIGHT, map.getWidth(), map.getHeight());
        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow());

        // Input handlers
        inputHandler = new InputHandler(window, cameraManager, map, player);
        mouseHandler = new MouseHandler(window, cameraManager, map, inputHandler, player); // MODIFIED

        // Renderer
        renderer = new Renderer(cameraManager, map, player, inputHandler);

        // Initial setup
        inputHandler.registerCallbacks(map::generateMap); // Allow 'G' to regenerate map

        // Set up window resize callback
        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            // glViewport is called by renderer.onResize
            cameraManager.updateScreenSize(w, h);
            if (renderer != null) { // Check if renderer is initialized
                renderer.onResize(w, h);
            }
        });

        System.out.println("Game components initialized.");
    }

    private void initOpenGL() {
        // Assuming GL.createCapabilities() was called in Main.initWindow()
        // after glfwMakeContextCurrent and before new Game()

        // It's good to print this to confirm context is working from Game's perspective too
        System.out.println("Game.initOpenGL() - OpenGL version: " + glGetString(GL_VERSION));

        glClearColor(0.1f, 0.2f, 0.3f, 1.0f); // Background color

        glEnable(GL_BLEND); // For transparency
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Initial projection and viewport setup is handled by renderer.onResize
        // This ensures it's set correctly after renderer is constructed.
        if (renderer != null) {
            renderer.onResize(WIDTH, HEIGHT); // Initial call to set up projection
        } else {
            System.err.println("Renderer is null during initOpenGL, cannot set initial projection.");
        }
    }

    public void gameLoop() {
        initOpenGL(); // Prepare OpenGL states
        lastFrameTime = glfwGetTime(); // Initialize lastFrameTime

        System.out.println("Entering game loop...");

        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            double deltaTime = currentTime - lastFrameTime;
            lastFrameTime = currentTime;

            // Cap deltaTime to prevent spiral of death if game lags significantly
            if (deltaTime > 0.1) { // e.g., if deltaTime is more than 100ms (10 FPS)
                deltaTime = 0.1;
            }

            glfwPollEvents(); // Process all pending window and input events

            // Update game logic
            // Accumulator logic for fixed timestep is commented out for simplicity with variable step
            // accumulator += deltaTime;
            // while (accumulator >= TARGET_TIME_PER_FRAME) {
            //     updateGameLogic(TARGET_TIME_PER_FRAME);
            //     accumulator -= TARGET_TIME_PER_FRAME;
            // }
            updateGameLogic(deltaTime); // Using variable delta time

            renderGame(); // Render the current state

            glfwSwapBuffers(window); // Swap the front and back buffers
        }

        System.out.println("Game loop exited.");
        cleanup();
    }

    private void updateGameLogic(double deltaTime) {
        if (inputHandler != null) inputHandler.handleContinuousInput(deltaTime);
        if (player != null) player.update(deltaTime);
        if (cameraManager != null) cameraManager.update(deltaTime);
        // Update other game entities or systems
    }

    private void renderGame() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // Clear buffers

        if (renderer != null) {
            renderer.render();
        }
    }

    private void cleanup() {
        System.out.println("Game cleanup initiated...");
        if (renderer != null) {
            renderer.cleanup();
        }
        // Other game-specific cleanup if needed
        System.out.println("Game cleanup complete.");
    }
}