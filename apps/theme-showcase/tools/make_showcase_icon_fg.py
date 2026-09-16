#!/usr/bin/env python3
"""Generate simple foreground artwork for the Theme Showcase adaptive icon.

Motif: three overlapping translucent discs — one per design language the app
showcases (Material 3 Expressive purple, MIUI orange, frosted-glass white) —
on transparency. No text, no emoji; safe inside the 72dp safe zone.
"""
from PIL import Image, ImageDraw, ImageFilter

S = 768  # source artwork size
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

cx, cy, r = S // 2, S // 2, int(S * 0.40)
off = int(r * 0.62)

# Material 3 Expressive — purple disc, upper-left
d.ellipse([cx - off - r // 2, cy - off // 2 - r // 2,
           cx - off + r // 2, cy - off // 2 + r // 2],
          fill=(103, 80, 164, 235))          # #6750A4

# Miuix — MIUI orange disc, upper-right
d.ellipse([cx + off - r // 2, cy - off // 2 - r // 2,
           cx + off + r // 2, cy - off // 2 + r // 2],
          fill=(255, 105, 0, 235))           # MIUI orange

# Liquid Glass — frosted white disc, bottom center (translucent + highlight)
glass = Image.new("RGBA", (S, S), (0, 0, 0, 0))
gd = ImageDraw.Draw(glass)
gd.ellipse([cx - r // 2, cy + off // 2 - r // 2,
            cx + r // 2, cy + off // 2 + r // 2],
           fill=(255, 255, 255, 150))
gd.ellipse([cx - r // 4, cy + off // 2 - r // 4,
            cx + r // 6, cy + off // 2],
           fill=(255, 255, 255, 110))        # top-left specular highlight
img = Image.alpha_composite(img, glass)

img.save("icon_fg.png")
print("wrote icon_fg.png", img.size)
