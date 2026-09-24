#!/usr/bin/env python3
"""Generate EverBrowse foreground artwork: a stylized globe (white on transparent)."""
from PIL import Image, ImageDraw
import math

S = 512
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

cx = cy = S // 2
R = int(S * 0.42)
W = max(8, S // 36)  # line width

# Outer circle
d.ellipse([cx - R, cy - R, cx + R, cy + R], outline=(255, 255, 255, 255), width=W)
# Equator
d.line([cx - R, cy, cx + R, cy], fill=(255, 255, 255, 255), width=W)
# Horizontal latitude arcs
for frac in (0.5, -0.5):
    ry = int(R * 0.45)
    yoff = int(R * frac)
    d.arc([cx - R, cy + yoff - ry, cx + R, cy + yoff + ry], 0 if frac < 0 else 180, 180 if frac < 0 else 360,
          fill=(255, 255, 255, 255), width=W)
# Vertical meridian ellipses
for rx_frac in (0.45,):
    rx = int(R * rx_frac)
    d.ellipse([cx - rx, cy - R, cx + rx, cy + R], outline=(255, 255, 255, 255), width=W)
# Central meridian
d.line([cx, cy - R, cx, cy + R], fill=(255, 255, 255, 255), width=W)

img.save("/home/user/forgebuild/apps/everbrowse/tools/icon_fg.png")
print("artwork written")
