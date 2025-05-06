package org.isogame;

import org.isogame.game.Game;
import org.lwjgl.glfw.*;
import org.lwjgl.system.*;

import java.nio.*;

import static org.isogame.constants.Constants.*; // Use constants for window size
import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main {

    private long window;
    private Game game;

    public void run() {
        System.out.println("Starting LWJGL Isometric Game...");
        initWindow();
        game = new Game(window); // Pass window handle to Game
        game.gameLoop(); // Game now handles initialization and loop

        // Cleanup GLFW after game loop finishes
        cleanupWindow();
    }

    private void initWindow() {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable

        // Create the window
        window = glfwCreateWindow(WIDTH, HEIGHT, "LWJGL Isometric Game", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        System.out.println("GLFW Window created successfully.");


        // --- Center Window ---
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidmode == null) {
                System.err.println("Could not get video mode for primary monitor!");
                // Position window arbitrarily if vidmode fails
                glfwSetWindowPos(window, 100, 100);
            } else {
                // Center the window
                glfwSetWindowPos(
                        window,
                        (vidmode.width() - pWidth.get(0)) / 2,
                        (vidmode.height() - pHeight.get(0)) / 2
                );
            }

        } // the stack frame is popped automatically

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);

        // Enable v-sync (swap buffers only on vertical refresh)
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);
    }

    private void cleanupWindow() {
        System.out.println("Cleaning up GLFW window...");
        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free(); // Free the error callback created in initWindow
        System.out.println("GLFW terminated.");
    }

    public static void main(String[] args) {
        // Add the required VM option for macOS if running on that OS
        // You should still add -XstartOnFirstThread to your Run Configuration in IntelliJ
        // This is just a programmatic check/reminder
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            System.out.println("macOS detected. Ensure JVM is run with -XstartOnFirstThread");
            // System.setProperty("java.awt.headless", "true"); // Might help sometimes, but -X... is key
        }

        new Main().run();
    }
}