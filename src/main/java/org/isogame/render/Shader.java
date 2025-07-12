package org.isogame.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import java.awt.Color; // Make sure this import exists at the top of your Shader.java file

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;
import org.joml.Matrix4f;

public class Shader {

    private final int programId;
    private int vertexShaderId;
    private int fragmentShaderId;
    private final Map<String, Integer> uniforms;

    public Shader() throws IOException {
        programId = GL20.glCreateProgram();
        if (programId == 0) {
            throw new IOException("Could not create Shader program");
        }
        uniforms = new HashMap<>();
    }

    public void createVertexShader(String shaderCode) throws IOException {
        vertexShaderId = createShader(shaderCode, GL_VERTEX_SHADER);
    }

    public void createFragmentShader(String shaderCode) throws IOException {
        fragmentShaderId = createShader(shaderCode, GL_FRAGMENT_SHADER);
    }

    protected int createShader(String shaderCode, int shaderType) throws IOException {
        int shaderId = GL20.glCreateShader(shaderType);
        if (shaderId == 0) {
            throw new IOException("Error creating shader. Type: " + shaderType);
        }

        GL20.glShaderSource(shaderId, shaderCode);
        GL20.glCompileShader(shaderId);

        if (GL20.glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new IOException("Error compiling Shader code: " + GL20.glGetShaderInfoLog(shaderId, 1024));
        }

        GL20.glAttachShader(programId, shaderId);
        return shaderId;
    }

    public void link() throws IOException {
        GL20.glLinkProgram(programId);
        if (GL20.glGetProgrami(programId, GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IOException("Error linking Shader code: " + GL20.glGetProgramInfoLog(programId, 1024));
        }

        if (vertexShaderId != 0) {
            GL20.glDetachShader(programId, vertexShaderId);
            GL20.glDeleteShader(vertexShaderId);
        }
        if (fragmentShaderId != 0) {
            GL20.glDetachShader(programId, fragmentShaderId);
            GL20.glDeleteShader(fragmentShaderId);
        }

        GL20.glValidateProgram(programId);
        if (GL20.glGetProgrami(programId, GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
            System.err.println("Warning validating Shader code: " + GL20.glGetProgramInfoLog(programId, 1024));
        }
    }

    public void createUniform(String uniformName) throws IOException {
        int uniformLocation = GL20.glGetUniformLocation(programId, uniformName);
        if (uniformLocation < 0) {
            // Don't throw an exception if a uniform is not found, it might be optimized out by the GLSL compiler if unused.
            // System.err.println("Warning: Could not find uniform: " + uniformName + " in shader program " + programId + ". It might be unused or misspelled.");
            // Instead, store -1 or handle it gracefully when setUniform is called.
            // For simplicity, we'll still put it, but check before using in setUniform.
        }
        uniforms.put(uniformName, uniformLocation);

    }

    public void setUniform(String uniformName, Matrix4f value) {
        Integer location = uniforms.get(uniformName);
        if (location != null && location >= 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                value.get(fb);
                GL20.glUniformMatrix4fv(location, false, fb);
            }
        }
    }

    public void setUniform(String uniformName, org.joml.Vector4f value) {
        Integer location = uniforms.get(uniformName);
        if (location != null && location >= 0) {
            GL20.glUniform4f(location, value.x, value.y, value.z, value.w);
        }
    }

    public void setUniform(String uniformName, int value) {
        Integer location = uniforms.get(uniformName);
        if (location != null && location >= 0) {
            GL20.glUniform1i(location, value);
        }
    }

    public void setUniform(String uniformName, float value) {
        Integer location = uniforms.get(uniformName);
        if (location != null && location >= 0) {
            GL20.glUniform1f(location, value);
        }
    }

    public void bind() {
        GL20.glUseProgram(programId);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public void cleanup() {
        unbind();
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
        }
    }

    /**
     * Sets a vec3 uniform in the shader program using a Java Color object.
     * It automatically converts the 0-255 RGB values to the 0.0-1.0 float values OpenGL needs.
     *
     * @param name The name of the uniform variable in the shader.
     * @param color The Color object to pass to the shader.
     */
    public void setUniform(String name, Color color) {
        // Check if the uniform location was stored during creation
        if (uniforms.containsKey(name)) {
            // Convert color from the 0-255 integer range to the 0.0-1.0 float range
            float r = color.getRed() / 255.0f;
            float g = color.getGreen() / 255.0f;
            float b = color.getBlue() / 255.0f;

            // Get the location from the map and pass the three float values
            glUniform3f(uniforms.get(name), r, g, b);
        } else {
            // This is a safety check to prevent crashes and help with debugging.
            // It's good practice but not strictly required to fix the error.
            System.err.println("Shader Warning: Could not find uniform named '" + name + "'");
        }
    }
    public static String loadResource(String resourcePath) throws IOException {
        StringBuilder result = new StringBuilder();
        try (InputStream in = Shader.class.getResourceAsStream(resourcePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
        } catch (NullPointerException e) {
            throw new IOException("Resource not found (NPE): " + resourcePath, e);
        }
        return result.toString();
    }
}
