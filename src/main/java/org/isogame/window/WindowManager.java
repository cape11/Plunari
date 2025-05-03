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

public class WindowManager {
    private long window;
    private int width = WIDTH;
    private int height = HEIGHT;
    private String title;
    private boolean resized = false;

    /**
     * Creates a window with the specified title
     * @param title The title of the window
     */
    public WindowManager(String title) {
        this.title = title;
    }

    /**
     * Initialize the window
     */
    public void init() {
        // Setup an error callback
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        // Create the window
        window = glfwCreateWindow(width, height, title, NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Setup resize callback
        glfwSetFramebufferSizeCallback(window, (window, width, height) -> {
            this.width = width;
            this.height = height;
            this.resized = true;
        });

        // Get the thread stack and push a new frame
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // Center the window
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        } // the stack frame is popped automatically

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);

        // Enable v-sync
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);
    }

    /**
     * Update method to be called each frame
     */
    public void update() {
        if (resized) {
            // Update OpenGL viewport
            glViewport(0, 0, width, height);
            resized = false;
        }
        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    /**
     * Check if the window should close
     * @return True if the window should close
     */
    public boolean windowShouldClose() {
        return glfwWindowShouldClose(window);
    }

    /**
     * Set the clear color
     * @param r Red component
     * @param g Green component
     * @param b Blue component
     * @param alpha Alpha component
     */
    public void setClearColor(float r, float g, float b, float alpha) {
        glClearColor(r, g, b, alpha);
    }

    /**
     * Clear the framebuffer
     */
    public void clear() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    /**
     * Get the window handle
     * @return The window handle
     */
    public long getWindowHandle() {
        return window;
    }

    /**
     * Set up the projection matrix based on current window dimensions
     */
    public void setupProjection() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    /**
     * Get current window width
     * @return The window width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get current window height
     * @return The window height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Check if the window was resized
     * @return True if the window was resized
     */
    public boolean isResized() {
        return resized;
    }

    /**
     * Reset the resized flag
     */
    public void resetResized() {
        this.resized = false;
    }
}