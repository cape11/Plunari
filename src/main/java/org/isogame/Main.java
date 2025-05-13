package org.isogame;

import org.isogame.game.Game; // Ensure Game class is imported
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.*;
import org.lwjgl.PointerBuffer; // For glfwGetError

import java.nio.*;

import static org.isogame.constants.Constants.*; // For WIDTH, HEIGHT from constants
import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main {

    private long window;
    private Game game;
    private int initialFramebufferWidth;  // To store the actual pixel width
    private int initialFramebufferHeight; // To store the actual pixel height

    public void run() {
        System.out.println("Starting LWJGL Isometric Game...");
        initWindow(); // This will now set initialFramebufferWidth/Height

        // Pass the true framebuffer dimensions to the Game constructor
        game = new Game(window, initialFramebufferWidth, initialFramebufferHeight);
        game.gameLoop();

        cleanupWindow();
    }

    private void initWindow() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // Keep window hidden until setup is complete
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        // For macOS Retina displays, explicitly request a HiDPI capable framebuffer
        glfwWindowHint(GLFW_COCOA_RETINA_FRAMEBUFFER, GLFW_TRUE);

        // Create the window using logical width/height from constants
        window = glfwCreateWindow(WIDTH, HEIGHT, "LWJGL Isometric Game", NULL, NULL);
        if (window == NULL) {
            try (MemoryStack stack = stackPush()) {
                PointerBuffer description = stack.mallocPointer(1);
                int error = glfwGetError(description);
                if (error != GLFW_NO_ERROR) {
                    System.err.println("GLFW Error during window creation: " + error + " - " + MemoryUtil.memUTF8Safe(description.get(0)));
                }
            }
            throw new RuntimeException("Failed to create the GLFW window");
        }
        System.out.println("GLFW Window created (Handle: " + window + ")");

        // IMPORTANT: Get the ACTUAL framebuffer size in pixels
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetFramebufferSize(window, pWidth, pHeight);
            initialFramebufferWidth = pWidth.get(0);
            initialFramebufferHeight = pHeight.get(0);
        }
        System.out.println("Initial Logical Window Size (Points): " + WIDTH + "x" + HEIGHT);
        System.out.println("Initial Actual Framebuffer Size (Pixels): " + initialFramebufferWidth + "x" + initialFramebufferHeight);


        // Center the window on the primary monitor (uses window size in screen coordinates)
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1); // For window size, not framebuffer
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidmode != null) {
                glfwSetWindowPos(
                        window,
                        (vidmode.width() - pWidth.get(0)) / 2,
                        (vidmode.height() - pHeight.get(0)) / 2
                );
            } else {
                System.err.println("Could not get video mode for primary monitor! Positioning window at default.");
                glfwSetWindowPos(window, 100, 100); // Fallback position
            }
        }

        glfwMakeContextCurrent(window);
        GL.createCapabilities(); // Initialize LWJGL's OpenGL bindings for the current context
        System.out.println("OpenGL version reported by context: " + GL11.glGetString(GL11.GL_VERSION));

        glfwSwapInterval(1); // Enable v-sync

        glfwShowWindow(window); // Show the window only after all setup
    }

    private void cleanupWindow() {
        System.out.println("Cleaning up GLFW window...");
        if (window != NULL) {
            glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
        }
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
        System.out.println("GLFW terminated.");
    }

    public static void main(String[] args) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            System.out.println("macOS detected. Ensure JVM is run with -XstartOnFirstThread VM Option.");
        }
        new Main().run();
    }
}