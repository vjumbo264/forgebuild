#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

varying vec2 vTex;
uniform sampler2D uTexture;
uniform vec2 uTexelSize;
uniform float uEnabled;
uniform float uThreshold;
uniform float uSoftness;
uniform float uRadius;
uniform float uFalloff;
uniform float uExposure;
uniform float uTintR;
uniform float uTintG;
uniform float uTintB;
uniform float uTintEnabled;
uniform float uTintAmount;
uniform float uChromatic;
uniform float uAddMode;
uniform float uGlowOnly;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 extractLight(vec4 c) {
    vec3 rgb = c.rgb / max(c.a, 0.0001);
    float y = luma(rgb);
    float knee = max(uSoftness * (1.0 - uThreshold), 0.001);
    float soft = clamp((y - uThreshold + knee) / (2.0 * knee), 0.0, 1.0);
    float weight = (y > (uThreshold + knee))
        ? clamp((y - uThreshold) / max(1.0 - uThreshold, 0.0001), 0.0, 1.0)
        : soft * soft * (3.0 - 2.0 * soft);
    return rgb * weight * uExposure;
}

const int SAMPLES = 32;

void main() {
    vec4 base = texture2D(uTexture, clamp(vTex, 0.0, 1.0));
    if (uEnabled <= 0.5) {
        gl_FragColor = base;
        return;
    }
    if (uExposure <= 0.0) {
        gl_FragColor = base;
        return;
    }

    vec3 lightSum = vec3(0.0);
    float totalWeight = 0.0;
    float maxDist = max(uRadius, 0.01) * 32.0;
    float aspect = uTexelSize.y / max(uTexelSize.x, 0.0001);

    // Continuous Vogel golden-angle spiral: eliminates concentric ring bands
    for (int i = 0; i < SAMPLES; i++) {
        float fi = float(i);
        float rNorm = sqrt((fi + 0.5) / float(SAMPLES));
        float rDist = rNorm * maxDist;
        float w = exp(-rNorm * (3.5 / max(uFalloff + 0.05, 0.05)));
        float angle = fi * 2.39996323;
        vec2 dir = vec2(cos(angle) / aspect, sin(angle)) * uTexelSize * rDist;

        float spread = uChromatic * 0.4;
        vec2 dirR = dir * (1.0 + spread);
        vec2 dirG = dir;
        vec2 dirB = dir * max(0.0, 1.0 - spread);

        vec4 sR = texture2D(uTexture, clamp(vTex + dirR, 0.0, 1.0));
        vec4 sG = texture2D(uTexture, clamp(vTex + dirG, 0.0, 1.0));
        vec4 sB = texture2D(uTexture, clamp(vTex + dirB, 0.0, 1.0));

        vec3 lR = extractLight(sR);
        vec3 lG = extractLight(sG);
        vec3 lB = extractLight(sB);

        lightSum += vec3(lR.r, lG.g, lB.b) * w;
        totalWeight += w;
    }

    vec3 glow = lightSum / max(totalWeight, 0.0001);

    if (uTintEnabled > 0.5) {
        vec3 tint = vec3(uTintR, uTintG, uTintB);
        glow = mix(glow, glow * tint, clamp(uTintAmount, 0.0, 1.0));
    }

    if (uGlowOnly > 0.5) {
        float alpha = clamp(max(glow.r, max(glow.g, glow.b)), 0.0, 1.0);
        gl_FragColor = vec4(clamp(glow, 0.0, 1.0) * base.a, alpha * base.a);
        return;
    }

    vec3 baseRgb = base.rgb / max(base.a, 0.0001);
    vec3 outRgb;
    if (uAddMode > 0.5) {
        outRgb = baseRgb + glow;
    } else {
        vec3 halo = vec3(1.0) - exp(-max(glow, vec3(0.0)));
        outRgb = baseRgb + (1.0 - baseRgb) * halo;
    }

    gl_FragColor = vec4(clamp(outRgb, 0.0, 1.0) * base.a, base.a);
}
