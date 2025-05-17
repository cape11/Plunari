package org.isogame;

import org.isogame.game.Game;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL20;

import java.nio.*;

import static org.isogame.constants.Constants.*;
import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main {

    private long window;
    private Game game;
    private int initialFramebufferWidth;
    private int initialFramebufferHeight;

    public void run() {
        System.out.println("Starting LWJGL Isometric Game (VBO/VAO version)...");
        initWindow();
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
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        // Request an OpenGL 3.3 Core Profile context
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // Required for macOS
        glfwWindowHint(GLFW_COCOA_RETINA_FRAMEBUFFER, GLFW_TRUE);


        window = glfwCreateWindow(WIDTH, HEIGHT, "LWJGL Isometric Game (VBO/VAO)", NULL, NULL);
        if (window == NULL) {
            // Error logging from your original code
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

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetFramebufferSize(window, pWidth, pHeight);
            initialFramebufferWidth = pWidth.get(0);
            initialFramebufferHeight = pHeight.get(0);
        }
        System.out.println("Initial Logical Window Size (Points): " + WIDTH + "x" + HEIGHT);
        System.out.println("Initial Actual Framebuffer Size (Pixels): " + initialFramebufferWidth + "x" + initialFramebufferHeight);


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
            } else {
                System.err.println("Could not get video mode! Positioning window at default.");
                glfwSetWindowPos(window, 100, 100);
            }
        }

        glfwMakeContextCurrent(window);
        GL.createCapabilities(); // Initialize LWJGL's OpenGL bindings
        System.out.println("OpenGL version: " + GL11.glGetString(GL11.GL_VERSION));
        System.out.println("GLSL version: " + GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION));


        glfwSwapInterval(1); // Enable v-sync
        glfwShowWindow(window);
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