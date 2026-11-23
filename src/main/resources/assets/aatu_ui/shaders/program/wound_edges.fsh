#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 Direction;
uniform float Strength;

in vec2 texCoord;
in vec2 oneTexel;
out vec4 fragColor;

const float REFERENCE_HEIGHT = 1080.0;
const float SAMPLE_SPACING = 6.0;

vec4 sampleOffset(vec2 offset) {
    return texture(DiffuseSampler, clamp(texCoord + offset, oneTexel * 0.5, vec2(1.0) - oneTexel * 0.5));
}

void main() {
    vec4 original = texture(DiffuseSampler, texCoord);
    vec2 distanceFromCenter = abs(texCoord * 2.0 - 1.0);
    float edge = smoothstep(0.58, 0.98, max(distanceFromCenter.x, distanceFromCenter.y));
    float amount = edge * clamp(Strength, 0.0, 1.0);
    if (amount <= 0.0) {
        fragColor = original;
        return;
    }

    vec2 stepSize = oneTexel * Direction * SAMPLE_SPACING * clamp(InSize.y / REFERENCE_HEIGHT, 0.5, 2.0);
    vec4 blurred = original * 0.2270270270;
    blurred += (sampleOffset(stepSize) + sampleOffset(-stepSize)) * 0.1945945946;
    blurred += (sampleOffset(stepSize * 2.0) + sampleOffset(-stepSize * 2.0)) * 0.1216216216;
    blurred += (sampleOffset(stepSize * 3.0) + sampleOffset(-stepSize * 3.0)) * 0.0540540541;
    blurred += (sampleOffset(stepSize * 4.0) + sampleOffset(-stepSize * 4.0)) * 0.0162162162;
    fragColor = mix(original, blurred, amount);
}
