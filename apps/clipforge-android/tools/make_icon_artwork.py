#!/usr/bin/env python3
"""
ClipForge Android launcher-icon foreground artwork generator.

Design: a vertical-video "clip" glyph being forged — a rounded phone-tall frame
(9:16, the app's whole purpose: vertical video) with a play triangle, and a
forge spark / cut mark crossing it. Flat, geometric, high contrast, reads at
48dp. No text (illegible at icon sizes).

Output: transparent-background PNG sized for make_adaptive_icon.py, which then
scales it into the 72dp adaptive-icon safe zone.
"""
from PIL import Image, ImageDraw

SIZE = 512  # generous source resolution; make_adaptive_icon.py downsamples

# Palette (ForgeBuild engine-adjacent, Material 3 tonal):
DEEP = (23, 18, 42, 255)        # near-black violet (unused on fg, kept for reference)
FRAME = (255, 255, 255, 255)    # white frame — pops on any background color
PLAY = (255, 255, 255, 255)
ACCENT = (255, 196, 66, 255)    # amber forge-spark slash


def rounded_frame(draw, box, radius, width, color):
    """Draw a rounded-rectangle outline (stroke) via arcs + lines."""
    x0, y0, x1, y1 = box
    w = width
    # four corner arcs
    draw.arc([x0, y0, x0 + 2 * radius, y0 + 2 * radius], 180, 270, fill=color, width=w)
    draw.arc([x1 - 2 * radius, y0, x1, y0 + 2 * radius], 270, 360, fill=color, width=w)
    draw.arc([x1 - 2 * radius, y1 - 2 * radius, x1, y1], 0, 90, fill=color, width=w)
    draw.arc([x0, y1 - 2 * radius, x0 + 2 * radius, y1], 90, 180, fill=color, width=w)
    # straight edges
    draw.line([x0 + radius, y0 + w // 2, x1 - radius, y0 + w // 2], fill=color, width=w)
    draw.line([x0 + radius, y1 - w // 2, x1 - radius, y1 - w // 2], fill=color, width=w)
    draw.line([x0 + w // 2, y0 + radius, x0 + w // 2, y1 - radius], fill=color, width=w)
    draw.line([x1 - w // 2, y0 + radius, x1 - w // 2, y1 - radius], fill=color, width=w)


def main():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    cx = SIZE // 2
    # --- vertical video frame (9:16-ish), centered ---
    fh = int(SIZE * 0.62)          # frame height
    fw = int(fh * 9 / 16)          # frame width (9:16 portrait = vertical video)
    x0, y0 = cx - fw // 2, (SIZE - fh) // 2
    x1, y1 = cx + fw // 2, y0 + fh
    stroke = int(SIZE * 0.055)
    radius = int(fw * 0.28)
    rounded_frame(d, (x0, y0, x1, y1), radius, stroke, FRAME)

    # --- play triangle inside the frame ---
    tw = int(fw * 0.46)
    th = int(tw * 1.15)
    # optical centering: shift slightly right
    px, py = cx + int(tw * 0.08), SIZE // 2
    d.polygon(
        [(px - tw // 2, py - th // 2), (px - tw // 2, py + th // 2), (px + tw // 2, py)],
        fill=PLAY,
    )

    # --- forge spark: diagonal amber slash cutting across the lower-right ---
    sw = int(SIZE * 0.05)
    d.line(
        [cx + int(SIZE * 0.02), y1 + int(SIZE * 0.045),
         cx + int(SIZE * 0.22), y1 - int(SIZE * 0.055)],
        fill=ACCENT, width=sw,
    )
    # spark head dot
    hr = int(SIZE * 0.038)
    hx, hy = cx + int(SIZE * 0.22), y1 - int(SIZE * 0.055)
    d.ellipse([hx - hr, hy - hr, hx + hr, hy + hr], fill=ACCENT)

    img.save("/home/user/forgebuild/apps/clipforge-android/tools/icon_fg.png")
    print("wrote tools/icon_fg.png", img.size)


if __name__ == "__main__":
    main()
