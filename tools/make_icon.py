"""Generate the mod icon: a pixel-art 'ㅇ' under a red no-entry sign.

Drawn on a 32x32 pixel grid (Minecraft style), then upscaled with nearest-neighbour.
Usage: python3 make_icon.py <out_dir>
"""
import math
import sys
from pathlib import Path

from PIL import Image

N = 32
BG_TOP = (44, 48, 58)
BG_BOTTOM = (28, 30, 38)
RING = (240, 240, 235)
RING_SHADE = (190, 190, 185)
RED = (214, 48, 49)
RED_DARK = (160, 30, 32)

img = Image.new("RGB", (N, N))
px = img.load()
c = (N - 1) / 2

for y in range(N):
    t = y / (N - 1)
    row = tuple(round(BG_TOP[i] * (1 - t) + BG_BOTTOM[i] * t) for i in range(3))
    for x in range(N):
        px[x, y] = row

# white ㅇ ring
for y in range(N):
    for x in range(N):
        d = math.hypot(x - c, y - c)
        if 6.2 <= d <= 9.4:
            px[x, y] = RING_SHADE if (x - c) + (y - c) > 6 else RING

# red no-entry ring + diagonal slash
for y in range(N):
    for x in range(N):
        d = math.hypot(x - c, y - c)
        on_ring = 12.0 <= d <= 15.2
        # slash from top-left to bottom-right, inside the ring
        on_slash = abs((x - c) - (y - c)) <= 1.6 and d < 12.5
        if on_ring or on_slash:
            px[x, y] = RED_DARK if (on_ring and d > 14.4) else RED

out = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
out.mkdir(parents=True, exist_ok=True)
img.resize((128, 128), Image.NEAREST).save(out / "tabooword.png")
img.resize((512, 512), Image.NEAREST).save(out / "modrinth_icon.png")
print("saved", out / "tabooword.png", out / "modrinth_icon.png")
