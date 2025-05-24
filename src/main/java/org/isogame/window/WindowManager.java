package org.isogame.window;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.isogame.constants.Constants.*;
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Manages the GLFW window, its creation, event polling, and cleanup.
 * Note: This class appears to be a standalone window manager but the main game loop
 * and OpenGL context creation are handled directly in `Main.java`.
 * If this class were to be the primary window handler, `Main.java`'s `initWindow`
 * and `cleanupWindow` would be part of this class's methods.
 * For now, it acts as a utility class if its methods were called from Main/Game.
 */
public class WindowManager {
    private long window;
    private int width = WIDTH;  // Default width from Constants
    private int height = HEIGHT; // Default height from Constants
    private String title;
    private boolean resized = false; // Flag to indicate if window was resized

    /**
     * Creates a window manager with the specified title.
     * @param title The title of the window.
     */
    public WindowManager(String title) {
        this.title = title;
    }

    /**
     * Initialize the GLFW window.
     * This method encapsulates the window creation logic.
     */
    public void init() {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints(); // Optional, the sensible defaults
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // The window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // The window will be resizable
        // OpenGL context hints (e.g., version) would be set here if this class managed context creation.
        // For this project, Main.java handles these hints.

        // Create the window
        window = glfwCreateWindow(this.width, this.height, this.title, NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Setup resize callback to update dimensions and set flag
        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            this.width = w;
            this.height = h;
            this.resized = true;
            // If this class managed rendering, glViewport would be called here or in update.
        });

        // Center the window
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidmode != null) {
                glfwSetWindowPos(
                        window,
                        (vidmode.width() - pWidth.get(0)) / 2,
                        (vidmode.height() - pHeight.get(0)) / 2
                );
            }
        }

        // Make the OpenGL context current (if this class were managing it)
        // glfwMakeContextCurrent(window);
        // GL.createCapabilities(); // If this class were managing it

        // Enable v-sync (if this class were managing it)
        // glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);
    }

    /**
     * Update method to be called each frame.
     * Handles buffer swapping and event polling.
     * If this class managed rendering, viewport updates on resize would also go here.
     */
    public void update() {
        if (resized) {
            // If this class was responsible for the OpenGL viewport for its window:
            // glViewport(0, 0, width, height);
            // System.out.println("WindowManager: Resized to " + width + "x" + height);
            resized = false;
        }
        // glfwSwapBuffers(window); // Done in Game's loop via Main's window
        // glfwPollEvents();      // Done in Game's loop via Main's window
    }

    /**
     * Check if the window should close.
     * @return True if the window should close, false otherwise.
     */
    public boolean windowShouldClose() {
        return glfwWindowShouldClose(window);
    }

    /**
     * Sets the clear color for OpenGL.
     * @param r Red component (0.0 - 1.0).
     * @param g Green component (0.0 - 1.0).
     * @param b Blue component (0.0 - 1.0).
     * @param alpha Alpha component (0.0 - 1.0).
     */
    public void setClearColor(float r, float g, float b, float alpha) {
        glClearColor(r, g, b, alpha);
    }

    /**
     * Clears the framebuffer (color and depth buffers).
     */
    public void clear() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Cleans up GLFW resources associated with this window.
     */
    public void cleanup() {
        if (window != NULL) {
            glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
        }
        // glfwTerminate() and freeing the error callback are typically done once globally,
        // usually by the main application class that called glfwInit().
        // If WindowManager is meant to be self-contained for multiple windows,
        // then terminate would only be called when the last window is closed.
    }

    /**
     * Get the GLFW window handle.
     * @return The window handle.
     */
    public long getWindowHandle() {
        return window;
    }

    /**
     * Sets up an orthographic projection matrix.
     * Note: This uses deprecated fixed-function pipeline calls (glMatrixMode, glLoadIdentity, glOrtho).
     * Modern OpenGL (Core Profile, as hinted in Main.java) uses shader-based projection matrices.
     * This method would conflict if shaders are managing projection, as they should in a Core Profile.
     * The `Renderer` class should handle projection via shader uniforms.
     */
    @Deprecated
    public void setupProjection() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1); // Typical 2D ortho projection
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    // Getters for window properties
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isResized() { return resized; }
    public void resetResized() { this.resized = false; }
}