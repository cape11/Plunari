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
            r = 0.0f + 0.1f * (1.0f - phase); g = 0.0f + 0.1f * (1.0f - phase); b = 0.1f + 0.2f * (1.0f - phase);
        } else if (pseudoTimeOfDay < 0.5) {
            float phase = (float) ((pseudoTimeOfDay - 0.25) / 0.25);
            r = 0.0f + 0.5f * phase; g = 0.0f + 0.7f * phase; b = 0.1f + 0.9f * phase;
        } else if (pseudoTimeOfDay < 0.75) {
            float phase = (float) ((pseudoTimeOfDay - 0.5) / 0.25);
            r = 0.5f - 0.1f * phase; g = 0.7f - 0.1f * phase; b = 1.0f - 0.1f * phase;
        } else {
            float phase = (float) ((pseudoTimeOfDay - 0.75) / 0.25);
            r = 0.4f * (1.0f - phase) + 0.6f * phase * (1.0f - phase);
            g = 0.6f * (1.0f - phase) + 0.2f * phase * (1.0f - phase);
            b = 0.9f * (1.0f - phase) + 0.1f * (1.0f-phase);
        }
        r = Math.max(0.0f, Math.min(1.0f, r)); g = Math.max(0.0f, Math.min(1.0f, g)); b = Math.max(0.0f, Math.min(1.0f, b));

        glClearColor(r, g, b, 1.0f);
        // --- Clear both Color and Depth buffers ---
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (renderer != null) {
            renderer.render();
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

    private void cleanup() {
        System.out.println("Game cleanup initiated...");
        if (renderer != null) {
            renderer.cleanup();
        }
        System.out.println("Game cleanup complete.");
    }
}
