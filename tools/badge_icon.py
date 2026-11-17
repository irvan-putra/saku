#!/usr/bin/env python3
"""Launcher icon generator: one picture plus the shared version badge.

Deterministic. Same version in, same PNGs out. Inputs are pinned: Pillow
PILLOW_PIN and DejaVu Sans Bold with sha256 FONT_SHA256. The script fails
instead of falling back to another font or Pillow version, because a
different rasterizer or font changes the pixels.
    pip install pillow==12.3.0   (fonts-dejavu-core provides the font)

Badge: numbers only, a full-width strip across the bottom 24% of the icon.
Picture safety: every picture pixel is checked to lie inside the circle that
touches the icon edges (shrunk by a margin), so round launcher masks do not
clip it; the version text is checked the same way.
"""
import hashlib, math, os, re, sys
import PIL
from PIL import Image, ImageChops, ImageDraw, ImageFont

PILLOW_PIN = "12.3.0"
FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_SHA256 = "d1c3ff99f1e1ce1827a33efd4dad81f40babda06bff9e43bd7591c86662a287b"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
S = 768
STRIP_TOP = 0.76      # strip covers 76%..100% of the icon height
TEXT_SIZE = 0.115     # numeral size, fraction of icon size
SAFE_R = 0.485        # picture and text must stay inside this radius (fraction)

if PIL.__version__ != PILLOW_PIN:
    sys.exit("Pillow %s required, found %s" % (PILLOW_PIN, PIL.__version__))


def font(px):
    if not os.path.exists(FONT_PATH):
        sys.exit("missing font " + FONT_PATH)
    if hashlib.sha256(open(FONT_PATH, "rb").read()).hexdigest() != FONT_SHA256:
        sys.exit("unexpected font file " + FONT_PATH)
    return ImageFont.truetype(FONT_PATH, px)


def circle_mask(size, r=SAFE_R):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).ellipse([size * (.5 - r), size * (.5 - r), size * (.5 + r), size * (.5 + r)], fill=255)
    return m


def assert_inside_circle(layer, what, size=S, r=SAFE_R):
    alpha = layer.getchannel("A").point(lambda v: 255 if v > 8 else 0)
    outside = ImageChops.multiply(alpha, ImageChops.invert(circle_mask(size, r)))
    box = outside.getbbox()
    if box:
        sys.exit("%s leaves the round-mask safe circle at %s" % (what, box))


def new_layer(size=S):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def sparkle(d, cx, cy, r, fill, size=S):
    pts = []
    for i in range(96):
        t = i * 2 * math.pi / 96
        pts.append((cx * size + r * size * math.cos(t) ** 3, cy * size + r * size * math.sin(t) ** 3))
    d.polygon(pts, fill=fill)


def rrect(d, box, radius, fill, size=S):
    d.rounded_rectangle([c * size for c in box], radius=radius * size, fill=fill)


def compose(bg, art, version, strip, text, rounded=None, size=S, circle_check=True):
    """Background, then the picture layer, then the badge strip."""
    if circle_check:
        assert_inside_circle(art, "picture", size)
    base = Image.new("RGBA", (size, size), bg) if bg else Image.new("RGBA", (size, size), (0, 0, 0, 0))
    base.alpha_composite(art)
    d = ImageDraw.Draw(base)
    y0 = int(size * STRIP_TOP)
    d.rectangle([0, y0, size, size], fill=strip)
    tl, td = new_layer(size)
    f = font(int(size * TEXT_SIZE))
    td.text((size / 2, (y0 + size) / 2), version, font=f, fill=text, anchor="mm")
    if circle_check:
        assert_inside_circle(tl, "version text", size)
    base.alpha_composite(tl)
    if rounded:
        m = Image.new("L", (size, size), 0)
        ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1], radius=rounded * size, fill=255)
        base.putalpha(ImageChops.multiply(base.getchannel("A"), m))
    return base


def parse_version(argv, fallback=None):
    v = argv[1] if len(argv) > 1 else fallback
    if not v or not re.fullmatch(r"\d+\.\d+\.\d+", v):
        sys.exit("version must be X.Y.Z")
    return v


def write_densities(img, res_dir, name="ic_launcher.png"):
    for dens, px in DENSITIES.items():
        out = os.path.join(res_dir, "mipmap-" + dens)
        os.makedirs(out, exist_ok=True)
        img.resize((px, px), Image.LANCZOS).save(os.path.join(out, name), optimize=True)


def saku(version):
    # a shirt pocket: pocket with hem band and stitching on the shirt fabric
    BG, CREAM, HEM, STITCH = (13, 71, 68, 255), (240, 248, 244, 255), (200, 224, 216, 255), (13, 71, 68, 255)
    art, d = new_layer()
    # pocket body: flat top, pointed bottom
    pts = [(.27, .20), (.73, .20), (.73, .50), (.50, .67), (.27, .50)]
    d.polygon([(x * S, y * S) for x, y in pts], fill=CREAM)
    # hem band across the top
    d.rectangle([.27 * S, .20 * S, .73 * S, .28 * S], fill=HEM)
    # stitching along the hem and the inner edge of the pocket
    def dashes(p, q, n):
        for i in range(n):
            t0, t1 = i / n, (i + .55) / n
            d.line([(S * (p[0] + (q[0] - p[0]) * t0), S * (p[1] + (q[1] - p[1]) * t0)),
                    (S * (p[0] + (q[0] - p[0]) * t1), S * (p[1] + (q[1] - p[1]) * t1))], fill=STITCH, width=int(S * .012))
    dashes((.31, .245), (.69, .245), 9)
    dashes((.31, .33), (.31, .49), 4)
    dashes((.31, .49), (.50, .62), 5)
    dashes((.50, .62), (.69, .49), 5)
    dashes((.69, .49), (.69, .33), 4)
    # shirt button above the pocket
    d.ellipse([S * .46, S * .09, S * .54, S * .17], fill=CREAM)
    for dx, dy in ((-.014, -.014), (.014, .014)):
        d.ellipse([S * (.50 + dx - .007), S * (.13 + dy - .007), S * (.50 + dx + .007), S * (.13 + dy + .007)], fill=BG)
    return compose(BG, art, version, (6, 38, 36, 255), CREAM, rounded=.2)



def main():
    version = parse_version(sys.argv)
    root = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    write_densities(saku(version), os.path.join(root, "app/src/main/res"))
    print("wrote icons for", version)


if __name__ == "__main__":
    main()
