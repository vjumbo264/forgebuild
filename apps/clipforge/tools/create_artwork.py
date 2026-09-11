#!/usr/bin/env python3
"""Generate crisp ClipForge foreground icon artwork using Pillow."""
from PIL import Image, ImageDraw
import math

size = 512
img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

# Center is (256, 256)
cx, cy = 256, 256

# Draw a vertical smartphone/clip frame (9:16 aspect aesthetic)
card_w = 190
card_h = 320
x0 = cx - card_w // 2
y0 = cy - card_h // 2
x1 = cx + card_w // 2
y1 = cy + card_h // 2
r = 28

# Outer glow / subtle border
draw.rounded_rectangle([x0 - 4, y0 - 4, x1 + 4, y1 + 4], radius=r+4, fill=(99, 102, 241, 100))
# Inner card - gradient look or deep indigo
draw.rounded_rectangle([x0, y0, x1, y1], radius=r, fill=(30, 27, 75, 240), outline=(129, 140, 248, 255), width=6)

# Filmstrip perforations on left and right edge
perf_w = 12
perf_h = 18
perf_r = 3
for py in range(y0 + 35, y1 - 35, 36):
    # Left perforation
    draw.rounded_rectangle([x0 + 8, py, x0 + 8 + perf_w, py + perf_h], radius=perf_r, fill=(129, 140, 248, 200))
    # Right perforation
    draw.rounded_rectangle([x1 - 8 - perf_w, py, x1 - 8, py + perf_h], radius=perf_r, fill=(129, 140, 248, 200))

# Central scissors / play forge spark
# Play triangle in center
tri_size = 46
p1 = (cx - 16, cy - 35)
p2 = (cx + 34, cy)
p3 = (cx - 16, cy + 35)
draw.polygon([p1, p2, p3], fill=(245, 158, 11, 255)) # amber flame play button

# Forge / Spark cut effect: cutting beam across the card
# A diagonal sleek cut line
draw.line([(cx - 75, cy + 70), (cx + 75, cy - 70)], fill=(255, 255, 255, 230), width=5)

# Spark stars
def draw_star(sx, sy, rad, color):
    draw.line([(sx - rad, sy), (sx + rad, sy)], fill=color, width=3)
    draw.line([(sx, sy - rad), (sx, sy + rad)], fill=color, width=3)
    draw.line([(sx - rad * 0.7, sy - rad * 0.7), (sx + rad * 0.7, sy + rad * 0.7)], fill=color, width=2)
    draw.line([(sx - rad * 0.7, sy + rad * 0.7), (sx + rad * 0.7, sy - rad * 0.7)], fill=color, width=2)

draw_star(cx + 65, cy - 60, 18, (254, 240, 138, 255))
draw_star(cx - 50, cy + 55, 12, (251, 191, 36, 255))
draw_star(cx + 25, cy + 85, 10, (255, 255, 255, 240))

img.save("/tmp/forgebuild/apps/clipforge/tools/icon_fg.png")
print("Saved /tmp/forgebuild/apps/clipforge/tools/icon_fg.png")
