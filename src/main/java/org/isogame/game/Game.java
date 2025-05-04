package org.isogame.game;

import org.isogame.input.InputHandler;
import org.isogame.input.MouseHandler;
import org.isogame.camera.CameraManager;
import org.lwjgl.opengl.GL;
import org.isogame.render.*;
import static org.isogame.constants.Constants.*;
import org.isogame.map.SimplexNoise;
import java.util.Random;

// Importaciones de LWJGL
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

public class Game {
    // --- Atributos ---
    private InputHandler inputHandler;
    private MouseHandler mouseHandler;
    private CameraManager cameraManager;
    private int frameCount;
    public final int[][] alturas = new int[MAP_HEIGHT][MAP_WIDTH];
    private double levitateTimer = 0;
    private long window; // Identificador de la ventana GLFW
    private org.isogame.render.Renderer renderer;
    private SimplexNoise noiseGenerator;
    private Random random = new Random();


    // --- Constructor ---
    public Game(long window) {
        this.window = window;
        this.frameCount = 0;
        this.noiseGenerator = new SimplexNoise(random.nextInt(1000));

        // Initialize camera manager with window dimensions
        cameraManager = new CameraManager(WIDTH, HEIGHT);

        // Initialize input handler and pass the window reference
        inputHandler = new InputHandler(window, alturas, ALTURA_MAXIMA, MAP_WIDTH, MAP_HEIGHT, cameraManager);
        inputHandler.registerCallbacks(this::generarMapa);


        // Initialize mouse handler
        mouseHandler = new MouseHandler(window, cameraManager, inputHandler);

        // Create renderer with the game reference
        renderer = new Renderer(this, inputHandler, cameraManager);

        // Set up window resize callback
        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            glViewport(0, 0, width, height);
            cameraManager.updateScreenSize(width, height);
        });

        System.out.println("Game initialized with window ID: " + window);
    }

    public int getFrameCount() {
        return frameCount;
    }

    public double calculateNoise(double x, double y) {
        return noiseGenerator.generateTerrain(x * NOISE_SCALE, y * NOISE_SCALE);
    }

    public void generarMapa() {
        System.out.println("Generando mapa...");

        // Create a new noise generator with a new seed
        noiseGenerator = new SimplexNoise(random.nextInt(1000));

        // Different terrain types
        for (int row = 0; row < MAP_HEIGHT; row++) {
            for (int col = 0; col < MAP_WIDTH; col++) {
                // Get noise value from our generator
                double noiseValue = calculateNoise(row, col);

                // Convert to height
                int altura = (int) (((noiseValue + 1.0) / 2.0) * ALTURA_MAXIMA);

                // Create some flat areas for better gameplay
                if (altura > NIVEL_MAR && altura < NIVEL_MAR + 3) {
                    // Small chance of flat areas
                    if (random.nextFloat() < 0.2) {
                        altura = NIVEL_MAR + 1;
                    }
                }

                // Create some scattered mountain peaks
                if (altura > ALTURA_MAXIMA * 0.7) {
                    // Small chance of increasing height further
                    if (random.nextFloat() < 0.1) {
                        altura = Math.min(ALTURA_MAXIMA, altura + random.nextInt(5));
                    }
                }

                alturas[row][col] = altura;
            }
        }

        // Smooth terrain to avoid sharp edges
        smoothTerrain(3);

        System.out.println("Mapa generado con éxito");

    }

    // Find a non-water tile to place the character
    private void findSuitableCharacterPosition() {
        int centerRow = MAP_HEIGHT / 2;
        int centerCol = MAP_WIDTH / 2;

        // Start from the center and spiral outward looking for non-water
        for (int radius = 0; radius < Math.max(MAP_WIDTH, MAP_HEIGHT) / 2; radius++) {
            for (int i = -radius; i <= radius; i++) {
                for (int j = -radius; j <= radius; j++) {
                    // Only check the perimeter of the current square
                    if (Math.abs(i) == radius || Math.abs(j) == radius) {
                        int row = centerRow + i;
                        int col = centerCol + j;

                        // Check if valid position
                        if (row >= 0 && row < MAP_HEIGHT && col >= 0 && col < MAP_WIDTH) {
                            // Check if non-water
                            if (alturas[row][col] >= NIVEL_MAR) {
                                // Found a good spot
                                inputHandler.characterRow = row;
                                inputHandler.characterCol = col;
                                inputHandler.currentRow = row;
                                inputHandler.currentCol = col;
                                cameraManager.setTargetPosition(col, row);
                                return;
                            }
                        }
                    }
                }
            }
        }

        // Fallback if somehow no land was found
        inputHandler.characterRow = centerRow;
        inputHandler.characterCol = centerCol;
        inputHandler.currentRow = centerRow;
        inputHandler.currentCol = centerCol;
        cameraManager.setTargetPosition(centerCol, centerRow);
    }
    private void smoothTerrain(int passes) {
        int[][] tempAlturas = new int[MAP_HEIGHT][MAP_WIDTH];

        for (int pass = 0; pass < passes; pass++) {
            // Copy current heights
            for (int row = 0; row < MAP_HEIGHT; row++) {
                for (int col = 0; col < MAP_WIDTH; col++) {
                    tempAlturas[row][col] = alturas[row][col];
                }
            }

            // Smooth each non-edge tile
            for (int row = 1; row < MAP_HEIGHT - 1; row++) {
                for (int col = 1; col < MAP_WIDTH - 1; col++) {
                    // Skip water tiles
                    if (alturas[row][col] < NIVEL_MAR) continue;

                    // Calculate average of surrounding tiles
                    int sum = alturas[row][col] +
                            alturas[row - 1][col] +
                            alturas[row + 1][col] +
                            alturas[row][col - 1] +
                            alturas[row][col + 1];

                    tempAlturas[row][col] = sum / 5;
                }
            }

            // Update to smoothed heights
            for (int row = 1; row < MAP_HEIGHT - 1; row++) {
                for (int col = 1; col < MAP_WIDTH - 1; col++) {
                    alturas[row][col] = tempAlturas[row][col];
                }
            }
        }
    }
    public void loop() {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        GL.createCapabilities();

        // Set the clear color to a dark blue/gray
        glClearColor(0.15f, 0.15f, 0.2f, 0.0f);

        // Enable alpha blending
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        System.out.println("Entering game loop");

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while (!glfwWindowShouldClose(window)) {
            frameCount++;
            if (inputHandler.levitating) {
                levitateTimer += 0.1;
                inputHandler.levitateTimer = (float)levitateTimer;
            }

            // Update mouse handler (for continuous operations)
            mouseHandler.update();

            // Update camera position (smooth transitions)
            cameraManager.update();

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

            // Setup orthographic projection
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, cameraManager.getScreenWidth(), cameraManager.getScreenHeight(), 0, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();

            // Draw the game
            renderer.render();

            glfwSwapBuffers(window); // swap the color buffers

            // Poll for window events. The key callback above will only be
            // invoked during this call.
            glfwPollEvents();

            // Sleep a bit to control the frame rate
            try {
                Thread.sleep(50); // ~20 FPS
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Game loop exited");
    }
}