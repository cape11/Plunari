package org.isogame.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

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
