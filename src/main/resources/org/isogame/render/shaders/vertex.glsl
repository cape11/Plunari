#version 330 core

// Vertex attributes
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec4 aColor;
layout (location = 2) in vec2 aTexCoord;
layout (location = 3) in float aLightValue;

// Uniforms
uniform mat4 uProjectionMatrix;
uniform mat4 uModelViewMatrix;

// Outputs to Fragment Shader
out vec4 fColor;
out vec2 fTexCoord;
out float fLightValue;

void main() {
    gl_Position = uProjectionMatrix * uModelViewMatrix * vec4(aPos, 1.0);
    fColor = aColor;
    fTexCoord = aTexCoord;
    fLightValue = aLightValue;
}