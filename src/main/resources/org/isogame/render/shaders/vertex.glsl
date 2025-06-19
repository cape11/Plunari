#version 330 core

// Vertex attributes
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec4 aColor;
layout (location = 2) in vec2 aTexCoord;
layout (location = 3) in float aLightValue;

// Uniforms
uniform mat4 uProjectionMatrix;
uniform mat4 uModelViewMatrix;
uniform float u_time;           // NEW: For animation
uniform bool u_isSelectedIcon;  // NEW: To toggle wobble

// Outputs to Fragment Shader
out vec4 fColor;
out vec2 fTexCoord;
out float fLightValue;

void main() {
    vec4 position = vec4(aPos, 1.0);

    if (u_isSelectedIcon) {
        float wobbleStrength = 2.0; // How many pixels to wobble by (adjust this value)
        float wobbleSpeed = 10.0;   // How fast to wobble (adjust this value)

        // Use texture coordinates to make wobble more towards edges.
        // (aTexCoord.s - 0.5) and (aTexCoord.t - 0.5) range from -0.5 to 0.5,
        // making the center (0.5, 0.5) have no displacement from this factor.
        float offsetX = sin(u_time * wobbleSpeed + aTexCoord.s * 3.14159) * (aTexCoord.t - 0.5) * wobbleStrength;
        float offsetY = cos(u_time * wobbleSpeed * 0.8 + aTexCoord.t * 3.14159) * (aTexCoord.s - 0.5) * wobbleStrength;

        // Apply the wobble. Since uModelViewMatrix for UI is likely identity,
        // this directly affects screen-space pixels if aPos is already in screen pixels.
        // If aPos is model space (e.g. 0 to 1 for quad), wobbleStrength needs to be relative to that.
        // Assuming aPos for UI icons are already in screen-like pixel coordinates before projection.
        position.x += offsetX;
        position.y += offsetY;
    }

    gl_Position = uProjectionMatrix * uModelViewMatrix * position;
    fColor = aColor;
    fTexCoord = aTexCoord;
    fLightValue = aLightValue;
}