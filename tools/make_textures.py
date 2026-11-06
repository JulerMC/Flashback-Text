"""Draws the cursor and click-ring images used by the POV overlay."""
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops

out = sys.argv[1]
SS = 4  # supersampling

def arrow_smooth():
    w, h = 64 * SS, 96 * SS
    pts = [(0, 0), (0, 16), (4, 12), (7, 18), (9, 17), (6, 11), (11, 11)]
    pts = [((8 + x * 4) * SS, (8 + y * 4) * SS) for x, y in pts]
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    outline = mask.filter(ImageFilter.MaxFilter(2 * 3 * SS + 1))
    shadow = outline.filter(ImageFilter.GaussianBlur(3 * SS))
    shadow = ImageChops.offset(shadow, 2 * SS, 3 * SS).point(lambda v: int(v * 0.4))
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    img.paste((0, 0, 0, 255), (0, 0), shadow)
    img.paste((20, 20, 24, 255), (0, 0), outline)
    img.paste((255, 255, 255, 255), (0, 0), mask)
    return img.resize((64, 96), Image.LANCZOS)

PIXEL = """
B...........
BB..........
BWB.........
BWWB........
BWWWB.......
BWWWWB......
BWWWWWB.....
BWWWWWWB....
BWWWWWWWB...
BWWWWWWWWB..
BWWWWWWWWWB.
BWWWWWWBBBBB
BWWWBWWB....
BWWBBWWB....
BWB..BWWB...
BB...BWWB...
B.....BWWB..
......BWWB..
.......BB...
"""

def arrow_pixel():
    rows = PIXEL.strip().splitlines()
    img = Image.new("RGBA", (14, 21), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == "B":
                img.putpixel((x + 1, y + 1), (0, 0, 0, 255))
            elif c == "W":
                img.putpixel((x + 1, y + 1), (255, 255, 255, 255))
    return img

def dot():
    s = 64 * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([10 * SS, 10 * SS, 54 * SS, 54 * SS], fill=(20, 20, 24, 200))
    d.ellipse([15 * SS, 15 * SS, 49 * SS, 49 * SS], fill=(255, 255, 255, 255))
    return img.resize((64, 64), Image.LANCZOS)

def ring():
    s = 64 * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([2 * SS, 2 * SS, 62 * SS, 62 * SS], outline=(255, 255, 255, 255), width=6 * SS)
    return img.resize((64, 64), Image.LANCZOS)

def icon():
    # Mod icon: a hotbar slot with a cursor over it, on Flashback-ish blue
    s = 128 * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([4 * SS, 4 * SS, 124 * SS, 124 * SS], 24 * SS, fill=(26, 115, 232, 255))
    for i in range(3):
        x0 = (14 + i * 34) * SS
        d.rounded_rectangle([x0, 46 * SS, x0 + 30 * SS, 76 * SS], 4 * SS, fill=(18, 40, 80, 255),
                            outline=(255, 255, 255, 255) if i == 1 else (120, 160, 230, 255), width=3 * SS)
    img = img.resize((128, 128), Image.LANCZOS)
    a = arrow_smooth().resize((40, 60), Image.LANCZOS)
    img.alpha_composite(a, (64, 52))
    return img

arrow_smooth().save(f"{out}/textures/gui/cursor_arrow.png")
arrow_pixel().save(f"{out}/textures/gui/cursor_pixel.png")
dot().save(f"{out}/textures/gui/cursor_dot.png")
ring().save(f"{out}/textures/gui/ring.png")
icon().save(f"{out}/icon.png")
