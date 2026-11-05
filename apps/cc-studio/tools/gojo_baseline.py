#!/usr/bin/env python3
"""Independent audit harness, NOT reference_match.py and NOT the replacement optimizer.
Test-only dependencies: numpy, opencv-python-headless, scipy, scikit-image, Pillow.
Measures original pair and legacy CPU reproduction; never substitutes target for result.
"""
import argparse
import hashlib
import json
from pathlib import Path
import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy.ndimage import gaussian_filter
from skimage.color import rgb2lab, deltaE_ciede2000


def summary(values):
    return {"mean": float(np.mean(values)), "p95": float(np.percentile(values, 95)), "count": int(values.size)}


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixtures", type=Path, default=root / "core/src/test/resources/gojo")
    parser.add_argument("--output", type=Path, default=root / "docs/verification/pivot")
    args = parser.parse_args()
    args.output.mkdir(exist_ok=True, parents=True)
    before_file = args.fixtures / "gojo_before.png"
    target_file = args.fixtures / "gojo_target.png"
    base = np.asarray(Image.open(before_file).convert("RGB")) / 255.0
    target = np.asarray(Image.open(target_file).convert("RGB")) / 255.0
    legacy = np.asarray(Image.open(args.output / "legacy_recreated.png").convert("RGB")) / 255.0
    assert base.shape == target.shape == legacy.shape == (259, 560, 3)
    h, w, _ = base.shape
    yy, xx = np.mgrid[:h, :w]
    border = (xx >= 4) & (xx < w - 4) & (yy >= 4) & (yy < h - 4)

    def edge(rgb):
        grey = cv2.cvtColor(rgb.astype(np.float32), cv2.COLOR_RGB2GRAY)
        grey = cv2.GaussianBlur(grey, (0, 0), 2)
        dx = cv2.Sobel(grey, cv2.CV_32F, 1, 0, ksize=3)
        dy = cv2.Sobel(grey, cv2.CV_32F, 0, 1, ksize=3)
        return cv2.GaussianBlur(cv2.magnitude(dx, dy), (0, 0), 1)

    warp = np.eye(2, 3, dtype=np.float32)
    correlation, warp = cv2.findTransformECC(edge(base), edge(target), warp, cv2.MOTION_AFFINE,
        (cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 300, 1e-7), None, 5)
    aligned = cv2.warpAffine(target.astype(np.float32), warp, (w, h), flags=cv2.INTER_LINEAR | cv2.WARP_INVERSE_MAP)
    tx = warp[0, 0] * xx + warp[0, 1] * yy + warp[0, 2]
    ty = warp[1, 0] * xx + warp[1, 1] * yy + warp[1, 2]
    valid = border & (tx >= 4) & (tx < w - 4) & (ty >= 4) & (ty < h - 4)
    holdout = valid & (xx >= int(w * 0.8))
    d_id_raw = deltaE_ciede2000(rgb2lab(base), rgb2lab(target))
    d_legacy_raw = deltaE_ciede2000(rgb2lab(legacy), rgb2lab(target))
    kotlin = json.loads((args.output / "legacy_kotlin_metrics.json").read_text())
    # Independent implementation cross-check; differences are sRGB matrix rounding only.
    for label, d in [("identity_ciede2000", d_id_raw), ("legacy_ciede2000", d_legacy_raw)]:
        assert abs(summary(d[border])["mean"] - kotlin[label]["mean"]) < 0.002
    d_id = deltaE_ciede2000(rgb2lab(base), rgb2lab(aligned))
    d_legacy = deltaE_ciede2000(rgb2lab(legacy), rgb2lab(aligned))
    blur = lambda a: gaussian_filter(a, sigma=(2, 2, 0))
    d_id_blur = deltaE_ciede2000(rgb2lab(blur(base)), rgb2lab(blur(aligned)))
    d_legacy_blur = deltaE_ciede2000(rgb2lab(blur(legacy)), rgb2lab(blur(aligned)))
    report = {"scope": "Audit baseline only; no reference optimizer was supplied or ported. Legacy reproduction is NOT the new match.",
        "metric": "CIEDE2000, D65 2deg sRGB; 4px border + valid affine intersection; blurred sigma=2",
        "fixture_sha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in [before_file, target_file]},
        "edge_ecc_correlation": float(correlation), "base_to_target_affine": warp.tolist(),
        "unaligned": {"identity": summary(d_id_raw[border]), "legacy": summary(d_legacy_raw[border])},
        "aligned": {"identity": summary(d_id[valid]), "legacy": summary(d_legacy[valid]),
            "identity_right20": summary(d_id[holdout]), "legacy_right20_NOT_held_out": summary(d_legacy[holdout]),
            "right20_note": "Legacy solver trained across the entire image; this region is diagnostic only, NOT held-out validation.",
            "identity_blurred_sigma2": summary(d_id_blur[valid]), "legacy_blurred_sigma2": summary(d_legacy_blur[valid])},
        "new_model": None, "new_model_gate_passed": False,
        "missing_required_input": "reference_match.py (the specified faithful-port source); not present in attachments or repository trees"}
    (args.output / "baseline_metrics.json").write_text(json.dumps(report, indent=2) + "\n")
    Image.fromarray(np.uint8(np.clip(aligned, 0, 1) * 255 + .5)).save(args.output / "target_aligned.png")
    norm = np.clip(d_legacy / 25.0, 0, 1)
    heat = np.stack([np.clip(2 * norm, 0, 1), np.clip(2 - 2 * norm, 0, 1), 1 - norm], axis=-1)
    heat[~valid] = 0.12
    heat_u8 = np.uint8(heat * 255 + .5)
    Image.fromarray(heat_u8).save(args.output / "legacy_heatmap.png")
    sheet = Image.new("RGB", (w * 2, 2 * (h + 34) + 66), (245, 245, 245))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 16)
    except OSError:
        font = ImageFont.load_default()
    panels = [(base, "BEFORE — original supplied frame"), (target, "TARGET — original supplied frame"),
        (legacy, "LEGACY CPU reproduction — NOT the new solver"), (heat_u8 / 255., "LEGACY error vs aligned target — CIEDE2000")]
    for i, (array, title) in enumerate(panels):
        x = (i % 2) * w; y = (i // 2) * (h + 34)
        draw.text((x + 8, y + 7), title, fill=(15, 20, 25), font=font)
        sheet.paste(Image.fromarray(np.uint8(np.clip(array, 0, 1) * 255 + .5)), (x, y + 34))
    sy = 2 * (h + 34) + 8
    draw.text((8, sy), "Audit only: no new fit or APK. Grey = excluded border. Error scale: 0 → 25+", fill=(15, 20, 25), font=font)
    for x in range(w):
        v = x / (w - 1)
        colour = tuple(int(vv * 255 + .5) for vv in [min(2*v,1), min(max(2-2*v,0),1), 1-v])
        draw.line((w + x, sy + 26, w + x, sy + 40), fill=colour)
    sheet.save(args.output / "gojo_legacy_audit.png")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
