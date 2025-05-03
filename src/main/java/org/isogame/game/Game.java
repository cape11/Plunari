package org.isogame.game;

import org.isogame.input.InputHandler;
import org.lwjgl.opengl.GL;
import org.isogame.render.*;
import static org.isogame.constants.Constants.*;

// Importaciones de LWJGL
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class Game {
    // --- Atributos ---
    private InputHandler inputHandler;
    private int frameCount;
    public final int[][] alturas = new int[MAP_HEIGHT][MAP_WIDTH];
    private double levitateTimer = 0;
    private long window; // Identificador de la ventana GLFW
    private org.isogame.render.Renderer renderer;

    // --- Constructor ---
    public Game(long window) {
        this.window = window;
        this.frameCount = 0;

        // Initialize input handler and pass the window reference
        inputHandler = new InputHandler(window, alturas, ALTURA_MAXIMA, MAP_WIDTH, MAP_HEIGHT);
        inputHandler.registerCallbacks(this::generarMapa);

        // Create renderer with the game reference
        renderer = new Renderer(this, inputHandler);

        System.out.println("Game initialized with window ID: " + window);
    }

    public int getFrameCount() {
        return frameCount;
    }

    public double calculateNoise(double x, double y) {
        // Simple noise function, could be replaced with a proper noise implementation
        double val = (Math.sin(x * 0.5) + Math.cos(y * 0.5) + Math.sin(x * y * 0.1)) / 3.0;
        return Math.max(-1.0, Math.min(1.0, val));
    }

    public void generarMapa() {
        System.out.println("Generando mapa...");

        for (int row = 0; row < MAP_HEIGHT; row++) {
            for (int col = 0; col < MAP_WIDTH; col++) {
                double noiseValue = calculateNoise(row * NOISE_SCALE, col * NOISE_SCALE);
                int altura = (int) (((noiseValue + 1.0) / 2.0) * ALTURA_MAXIMA);
                alturas[row][col] = altura;
            }
        }
        System.out.println("Mapa generado con éxito");
    }

    public void loop() {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        GL.createCapabilities();

        // Set the clear color to black
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        System.out.println("Entering game loop");

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while (!glfwWindowShouldClose(window)) {
            frameCount++;
            if (inputHandler.levitating) {
                levitateTimer += 0.1;
                inputHandler.levitateTimer = (float)levitateTimer;
            }

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

            // Setup orthographic projection
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, WIDTH, HEIGHT, 0, -1, 1);
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