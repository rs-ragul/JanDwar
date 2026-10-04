#!/usr/bin/env python3
"""
Regenerate every launcher icon and the in-app emblem from brand/logo_premium.png.

The previous set shrank the *whole* badge -- outer bezel, inner rounded square
and all -- into the adaptive foreground. Android then masks that again, so the
launcher showed a rounded square inside a rounded square with a tiny, slightly
off-centre emblem (it filled 17% of the canvas and sat 29px left of centre).

The fix is to lift the emblem off its background properly:

  1. Crop to the inner face of the badge (drop the black surround + bezel).
  2. Fit a smooth bilinear model of the blue->teal background gradient using
     only pixels classified as background.
  3. alpha = how far each pixel departs from that model, then *unmix*
     P = a*F + (1-a)*B  ->  F = (P - (1-a)*B) / a
     so antialiased edges keep no blue fringe.
  4. Rebuild the icons with the emblem centred in the adaptive safe zone.

Run from anywhere; paths are absolute.
"""

import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

REPO = "/home/user/JanDwar"
RES = os.path.join(REPO, "app/src/main/res")
SRC = os.path.join(REPO, "brand/logo_premium.png")

# Launcher densities: (dir suffix, legacy icon px, adaptive canvas px)
DENSITIES = [
    ("mdpi", 48, 108),
    ("hdpi", 72, 162),
    ("xhdpi", 96, 216),
    ("xxhdpi", 144, 324),
    ("xxxhdpi", 192, 432),
]

# Android reserves the outer ring of an adaptive icon for masking and motion;
# only the central 66/108 is guaranteed visible. Fill a touch under that.
SAFE_FRACTION = 66.0 / 108.0
EMBLEM_FILL = 0.94          # of the safe zone


def load_inner_face(path):
    """Crop the source down to the inner face of the badge."""
    im = Image.open(path).convert("RGBA")
    a = np.asarray(im).astype(np.float32)
    rgb, alpha = a[..., :3], a[..., 3]

    # The art sits on black with a transparent/black surround. Find the badge.
    lum = rgb.mean(axis=2)
    solid = (alpha > 8) & (lum > 24)
    ys, xs = np.nonzero(solid)
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()

    # Step inside the outer bezel and the inner rim. The bezel is ~5% of the
    # badge on each side; 9% clears both it and the inner lip's highlight.
    w, h = x1 - x0, y1 - y0
    inset_x, inset_y = int(w * 0.09), int(h * 0.09)
    box = (x0 + inset_x, y0 + inset_y, x1 - inset_x, y1 - inset_y)
    return im.crop(box).convert("RGB")


def emblem_from(face, want_bg=False):
    """Lift the white/orange artwork off the blue-teal gradient."""
    p = np.asarray(face).astype(np.float32) / 255.0
    h, w, _ = p.shape
    r, g, b = p[..., 0], p[..., 1], p[..., 2]

    mx = p.max(axis=2)
    mn = p.min(axis=2)
    sat = np.where(mx > 1e-6, (mx - mn) / np.maximum(mx, 1e-6), 0.0)

    # Background is blue/teal: blue or green leads, red trails, and it is
    # saturated. Everything else (white swoosh, orange accents, gold star) is
    # foreground.
    is_bg = (b > r + 0.10) & (sat > 0.25)

    # --- fit B(x,y) = c0 + c1*x + c2*y + c3*x*y per channel over background ---
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    xn, yn = xx / (w - 1.0), yy / (h - 1.0)
    basis = np.stack([np.ones_like(xn), xn, yn, xn * yn], axis=-1)

    sel = is_bg.reshape(-1)
    A = basis.reshape(-1, 4)[sel]
    bg = np.zeros_like(p)
    for c in range(3):
        coef, *_ = np.linalg.lstsq(A, p.reshape(-1, 3)[sel, c], rcond=None)
        bg[..., c] = basis @ coef
    bg = np.clip(bg, 0.0, 1.0)

    # --- alpha from distance to the modelled background -------------------
    dist = np.sqrt(((p - bg) ** 2).sum(axis=2))
    # Measured on this artwork: background distance p99 = 0.335, foreground
    # p10 = 0.924. The classes are cleanly separated, so the band sits between
    # them -- high enough to reject the badge's sheen and the emblem's drop
    # shadow, low enough to keep every antialiased edge pixel.
    lo, hi = 0.45, 0.78
    alpha = np.clip((dist - lo) / (hi - lo), 0.0, 1.0)

    # Kill speckle from the badge's own sheen, then soften by a hair so the
    # edge stays smooth after downscaling.
    am = Image.fromarray((alpha * 255).astype(np.uint8), "L")
    am = am.filter(ImageFilter.MedianFilter(5)).filter(ImageFilter.GaussianBlur(0.6))
    alpha = np.asarray(am).astype(np.float32) / 255.0

    # --- unmix so edge pixels are pure foreground, not blue-tinted --------
    safe = np.maximum(alpha, 1e-3)[..., None]
    fg = np.clip((p - (1.0 - alpha[..., None]) * bg) / safe, 0.0, 1.0)

    out = np.concatenate([fg, alpha[..., None]], axis=-1)
    im = Image.fromarray((out * 255).astype(np.uint8), "RGBA")
    im = im.crop(im.split()[3].getbbox())
    return (im, bg) if want_bg else im


def fit_centre(emblem, canvas, fill):
    """Scale emblem to `fill` of `canvas` on its longest side and centre it."""
    target = canvas * fill
    s = target / max(emblem.size)
    n = emblem.resize(
        (max(1, round(emblem.width * s)), max(1, round(emblem.height * s))),
        Image.LANCZOS,
    )
    out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    out.paste(n, ((canvas - n.width) // 2, (canvas - n.height) // 2), n)
    return out


def gradient(size, c0, c1, c2):
    """Diagonal 3-stop gradient matching the badge face."""
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    t = np.clip((xx + yy) / (2.0 * (size - 1)), 0.0, 1.0)
    a, bcol, c = (np.array(x, np.float32) for x in (c0, c1, c2))
    lo = a + (bcol - a) * np.clip(t / 0.55, 0, 1)[..., None]
    hi = bcol + (c - bcol) * np.clip((t - 0.55) / 0.45, 0, 1)[..., None]
    rgb = np.where((t < 0.55)[..., None], lo, hi)
    return Image.fromarray(rgb.astype(np.uint8), "RGB").convert("RGBA")


def rounded_mask(size, radius_frac):
    m = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle(
        (0, 0, size * 4 - 1, size * 4 - 1), radius=int(size * 4 * radius_frac), fill=255
    )
    return m.resize((size, size), Image.LANCZOS)


def circle_mask(size):
    m = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(m).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    return m.resize((size, size), Image.LANCZOS)


def main():
    face = load_inner_face(SRC)
    emblem, bgmodel = emblem_from(face, want_bg=True)
    print("source face %s -> emblem %s (%.0f%% of face)"
          % (face.size, emblem.size, 100.0 * emblem.width / face.width))

    # Sample the gradient from the *fitted background model*, never from raw
    # pixels: the centre of the badge is the white emblem, which sampled as a
    # near-white #DAE2EB and wrecked the middle stop.
    bgpx = np.clip(bgmodel * 255.0, 0, 255)
    h, w, _ = bgpx.shape

    def at(fx, fy):
        return tuple(bgpx[int(h * fy), int(w * fx)].round().astype(int))

    c0, c1, c2 = at(0.06, 0.06), at(0.50, 0.50), at(0.94, 0.94)
    print("gradient stops: #%02X%02X%02X -> #%02X%02X%02X -> #%02X%02X%02X"
          % (*c0, *c1, *c2))

    # ---- in-app emblem: tight crop, transparent, one asset per density ----
    # The largest on-screen use is 84dp (splash). Ship a 96dp box so even a
    # 4x screen draws it from real pixels instead of upscaling a 144px asset.
    EMBLEM_DP = 96
    for suffix, scale in [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2),
                          ("xxhdpi", 3), ("xxxhdpi", 4)]:
        side = int(round(EMBLEM_DP * scale))
        k = side / max(emblem.size)
        e = emblem.resize(
            (max(1, round(emblem.width * k)), max(1, round(emblem.height * k))),
            Image.LANCZOS,
        )
        d = os.path.join(RES, "drawable-" + suffix)
        os.makedirs(d, exist_ok=True)
        e.save(os.path.join(d, "ic_brand_emblem.png"))
        print("  ic_brand_emblem %-8s %s" % (suffix, e.size))

    # ---- launcher icons ---------------------------------------------------
    for suffix, legacy, canvas in DENSITIES:
        d = os.path.join(RES, "mipmap-" + suffix)
        os.makedirs(d, exist_ok=True)

        # Adaptive foreground: emblem centred inside the safe zone.
        fg = fit_centre(emblem, canvas, SAFE_FRACTION * EMBLEM_FILL)
        fg.save(os.path.join(d, "ic_launcher_foreground.png"))

        # Monochrome (themed icons): same geometry, flat white silhouette.
        mono = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        mono.putalpha(fg.split()[3])
        mono = Image.merge(
            "RGBA",
            (Image.new("L", (canvas, canvas), 255),) * 3 + (fg.split()[3],),
        )
        mono.save(os.path.join(d, "ic_launcher_monochrome.png"))

        # Legacy icons for API < 26: composite, then mask. No double bezel.
        base = gradient(legacy, c0, c1, c2)
        art = fit_centre(emblem, legacy, 0.66)
        base.alpha_composite(art)

        sq = base.copy()
        sq.putalpha(rounded_mask(legacy, 0.22))
        sq.save(os.path.join(d, "ic_launcher.png"))

        rd = base.copy()
        rd.putalpha(circle_mask(legacy))
        rd.save(os.path.join(d, "ic_launcher_round.png"))

    print("wrote launcher icons for %d densities" % len(DENSITIES))

    # ---- adaptive background vector matched to the badge ------------------
    xml = """<?xml version="1.0" encoding="utf-8"?>
<!-- Brand gradient sampled from brand/logo_premium.png. Full bleed: the
     launcher applies its own mask, so this must not draw a rounded shape. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr xmlns:aapt="http://schemas.android.com/aapt" name="android:fillColor">
            <gradient
                android:startX="0" android:startY="0"
                android:endX="108" android:endY="108"
                android:type="linear">
                <item android:offset="0.0" android:color="#FF%02X%02X%02X" />
                <item android:offset="0.55" android:color="#FF%02X%02X%02X" />
                <item android:offset="1.0" android:color="#FF%02X%02X%02X" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
""" % (*c0, *c1, *c2)
    with open(os.path.join(RES, "drawable/ic_launcher_background.xml"), "w") as f:
        f.write(xml)
    print("wrote drawable/ic_launcher_background.xml")

    # ---- report geometry so regressions are visible -----------------------
    chk = Image.open(os.path.join(RES, "mipmap-xxxhdpi/ic_launcher_foreground.png"))
    bb = chk.split()[3].getbbox()
    cx = (bb[0] + bb[2]) / 2.0
    cy = (bb[1] + bb[3]) / 2.0
    print("foreground 432px: bbox=%s centre=(%.1f,%.1f) want (216,216) "
          "longest side=%d (%.0f%% of canvas)"
          % (bb, cx, cy, max(bb[2] - bb[0], bb[3] - bb[1]),
             100.0 * max(bb[2] - bb[0], bb[3] - bb[1]) / 432))


if __name__ == "__main__":
    main()
