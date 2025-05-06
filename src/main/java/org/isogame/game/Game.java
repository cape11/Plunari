package org.isogame.game;

import org.isogame.input.InputHandler;
import org.isogame.input.MouseHandler;
import org.isogame.camera.CameraManager;
import org.isogame.map.Map;
import org.isogame.render.Renderer;
import org.isogame.entity.PlayerModel;
import org.lwjgl.opengl.GL;

import static org.isogame.constants.Constants.*;
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

    // Timing
    private double lastFrameTime;
    private double accumulator = 0.0;


    public Game(long window) {
        this.window = window;

        // Core components
        map = new Map(MAP_WIDTH, MAP_HEIGHT);
        player = new PlayerModel(map.getCharacterSpawnRow(), map.getCharacterSpawnCol());
        cameraManager = new CameraManager(WIDTH, HEIGHT, map.getWidth(), map.getHeight());
        cameraManager.setTargetPositionInstantly(player.getMapCol(), player.getMapRow()); // Center on player start

        // Input handlers
        inputHandler = new InputHandler(window, cameraManager, map, player); // Pass necessary references
        mouseHandler = new MouseHandler(window, cameraManager, map, inputHandler); // Pass necessary references

        // Renderer
        renderer = new Renderer(cameraManager, map, player, inputHandler); // Pass necessary references

        // Initial setup
        inputHandler.registerCallbacks(map::generateMap); // Allow 'G' to regenerate map

        // Set up window resize callback AFTER CameraManager is created
        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            glViewport(0, 0, width, height);
            cameraManager.updateScreenSize(width, height);
            renderer.onResize(width, height); // Notify renderer of resize
        });

        System.out.println("Game initialized.");
    }

    private void initOpenGL() {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // It detects the available OpenGL capabilities.
        GL.createCapabilities();

        System.out.println("OpenGL version: " + glGetString(GL_VERSION));

        // Set the clear color (background)
        glClearColor(0.1f, 0.2f, 0.3f, 1.0f); // A slightly nicer blue

        // Enable depth testing if needed (though less critical for strict isometric)
        // glEnable(GL_DEPTH_TEST);

        // Enable alpha blending for transparency
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Initial projection setup
        renderer.onResize(WIDTH, HEIGHT);
    }


    public void gameLoop() {
        initOpenGL();
        lastFrameTime = glfwGetTime();

        System.out.println("Entering game loop...");

        while (!glfwWindowShouldClose(window)) {
            double currentTime = glfwGetTime();
            double deltaTime = currentTime - lastFrameTime;
            lastFrameTime = currentTime;

            // --- Input ---
            glfwPollEvents(); // Process all pending events

            // --- Update ---
            // Add accumulated time. Helps smooth updates if frame rate fluctuates.
            accumulator += deltaTime;

            // Fixed timestep updates (optional but good for physics/game logic stability)
            // while (accumulator >= TARGET_TIME_PER_FRAME) {
            //     updateGameLogic(TARGET_TIME_PER_FRAME); // Update with fixed step
            //     accumulator -= TARGET_TIME_PER_FRAME;
            // }
            // For now, just use variable delta time for simplicity
            updateGameLogic(deltaTime);


            // --- Rendering ---
            // Calculate interpolation factor for smooth rendering between fixed updates (if using accumulator)
            // final double alpha = accumulator / TARGET_TIME_PER_FRAME;
            renderGame(/* alpha */); // Pass alpha if interpolating graphics

            glfwSwapBuffers(window); // Present the rendered frame
        }

        System.out.println("Game loop exited.");
        cleanup();
    }

    private void updateGameLogic(double deltaTime) {
        inputHandler.handleContinuousInput(deltaTime); // Handle keys held down (e.g., camera pan)
        player.update(deltaTime); // Update player animation timers etc.
        cameraManager.update(deltaTime); // Update camera smoothing
        // Update other game entities or systems here
    }

    private void renderGame(/* double interpolationAlpha */) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // Clear the framebuffer

        // Renderer handles projection and view setup internally now
        renderer.render();
    }

    private void cleanup() {
        System.out.println("Cleaning up resources...");
        renderer.cleanup(); // Clean up renderer resources (textures, shaders etc.)
        // Handlers don't usually need explicit cleanup unless they hold OpenGL resources
        // GLFW cleanup happens in Main
        System.out.println("Cleanup complete.");
    }
}