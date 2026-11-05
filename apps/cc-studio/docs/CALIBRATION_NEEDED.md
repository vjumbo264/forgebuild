# Calibration needed — original Keyflaw 1.4.9

Only sealed shader effects need this fallback; Sharpen and Unsharp Mask have been recovered and should NOT be recalibrated from guessed AE math.

## Glow (sealed 16-pass shader vault)
Needed: PNG exports at original resolution from Keyflaw using an opaque black image with a white dot, a grey step edge and gojo_before.png. Include one all-default export (threshold=.4, softness=.4, radius=.6, falloff=.15, exposure=1.4, RGB tint=1, tintEnabled=0, tintAmount=1, chromatic=0, addMode=0, glowOnly=0). Then radius 0/.25/.6/1, exposure 0/1.4/4, glowOnly 0/1, addMode 0/1, threshold 0/.4/.8, one parameter varied at a time. Record project dimensions, app build, colour management and all settings. No reference exports were supplied or generated here. Do not claim pixel equivalence yet.

## Curves, Levels, HSL Colors (sealed)
Original parameter metadata preserved in docs/keyflaw-reference/manifest.json. Need input colour ramp / colour chart and PNG exports for defaults, identity, and one change per control, with every setting and profile recorded. Prior ms_* shaders are unverified approximations, not recovered originals.

## Plugin-host limitation
Original built-in Unsharp/Diffusion/Haze graphs retain indexed uPrev, uInput and per-pass sizes; the recovered public SuiteRegistrar passes API supports sequential shaders without exposing that full graph. An exported pack cannot be claimed to reproduce the built-in graph solely because the shaders compile. T3/T9 must prove how those dependencies are preserved in actual Keyflaw import. If unsupported, retain an explicit block rather than replacing the original effect silently.
