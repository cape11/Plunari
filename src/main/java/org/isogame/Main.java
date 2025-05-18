package org.isogame;

import org.isogame.game.Game;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.*;
import org.lwjgl.PointerBuffer; // Keep this if glfwGetError needs it, though modern LWJGL often uses direct string returns.
import org.lwjgl.opengl.GL20; // For GL_SHADING_LANGUAGE_VERSION

import java.nio.*;

import static org.isogame.constants.Constants.*;
import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main {

    private long window; // The handle for the GLFW window
    private Game game;   // The main game logic controller
    private int initialFramebufferWidth;  // Actual pixel width of the framebuffer
    private int initialFramebufferHeight; // Actual pixel height of the framebuffer

    /**
     * Main execution method. Initializes the window, creates the game instance,
     * starts the game loop, and handles cleanup.
     */
    public void run() {
        System.out.println("Starting LWJGL Isometric Game (VBO/VAO version)...");
        initWindow(); // Initialize GLFW and create the window
        // Create the game instance, passing the window handle and initial framebuffer dimensions
        game = new Game(window, initialFramebufferWidth, initialFramebufferHeight);
        game.gameLoop(); // Start the main game loop
        cleanupWindow(); // Clean up GLFW resources when the game loop exits
    }

    /**
     * Initializes the GLFW window and OpenGL context.
     */
    private void initWindow() {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints(); // optional, the sensible defaults
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // The window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // The window will be resizable

        // Request an OpenGL 3.3 Core Profile context
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // Required for macOS
        glfwWindowHint(GLFW_COCOA_RETINA_FRAMEBUFFER, GLFW_TRUE); // Enable full resolution on Retina displays (macOS)


        // Create the window
        window = glfwCreateWindow(WIDTH, HEIGHT, "LWJGL Isometric Game (VBO/VAO)", NULL, NULL);
        if (window == NULL) {
            // Log detailed error if window creation fails
            try (MemoryStack stack = stackPush()) {
                PointerBuffer description = stack.mallocPointer(1);
                int error = glfwGetError(description); // Get the last GLFW error
                String errorMessage = (description.get(0) != NULL) ? MemoryUtil.memUTF8Safe(description.get(0)) : "Unknown GLFW error";
                System.err.println("GLFW Error during window creation: " + error + " - " + errorMessage);
            }
            throw new RuntimeException("Failed to create the GLFW window");
        }
        System.out.println("GLFW Window created (Handle: " + window + ")");

        // Get the initial framebuffer size in pixels (may differ from window size due to DPI scaling)
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetFramebufferSize(window, pWidth, pHeight);
            initialFramebufferWidth = pWidth.get(0);
            initialFramebufferHeight = pHeight.get(0);
        }
        System.out.println("Initial Logical Window Size (Points/Screen Coords): " + WIDTH + "x" + HEIGHT);
        System.out.println("Initial Actual Framebuffer Size (Pixels): " + initialFramebufferWidth + "x" + initialFramebufferHeight);


        // Center the window on the primary monitor
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1); // To store window width in screen coordinates
            IntBuffer pHeight = stack.mallocInt(1); // To store window height in screen coordinates
            glfwGetWindowSize(window, pWidth, pHeight); // Get window size

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor()); // Get video mode of primary monitor
            if (vidmode != null) {
                glfwSetWindowPos(
                        window,
                        (vidmode.width() - pWidth.get(0)) / 2,
                        (vidmode.height() - pHeight.get(0)) / 2
                );
            } else {
                System.err.println("Could not get video mode for primary monitor! Positioning window at default (100,100).");
                glfwSetWindowPos(window, 100, 100); // Fallback position
            }
        }

        // Make the OpenGL context current for this thread
        glfwMakeContextCurrent(window);
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities();

        // Print OpenGL and GLSL version information
        System.out.println("OpenGL version: " + GL11.glGetString(GL11.GL_VERSION));
        System.out.println("GLSL version: " + GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));


        // Enable v-sync (swap buffers only once per vertical refresh)
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);
    }

    /**
     * Cleans up GLFW resources upon application termination.
     */
    private void cleanupWindow() {
        System.out.println("Cleaning up GLFW window...");
        if (window != NULL) {
            // Free the window callbacks and destroy the window
            glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
        }

        // Terminate GLFW and free the error callback
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null); // Remove the error callback
        if (callback != null) {
            callback.free(); // Free the old callback
        }
        System.out.println("GLFW terminated.");
    }

    /**
     * The main entry point of the application.
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        // A common workaround for macOS: ensure JVM runs on the first thread.
        // This is often needed for AWT/Swing and sometimes for GLFW if issues arise.
        // Users typically add "-XstartOnFirstThread" as a VM option in their IDE run configuration for macOS.
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            System.out.println("macOS detected. If you encounter windowing or rendering issues, " +
                    "ensure the JVM is run with the -XstartOnFirstThread VM Option.");
        }
        new Main().run();
    }
}
