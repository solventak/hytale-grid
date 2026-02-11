#!/usr/bin/env python3
"""
Crisp 32x32 voxel conversion:
- For each output pixel, average the corresponding source region (box average)
- Map that average to the nearest color in a fixed palette (snaps gradients)
- Save 32x32 + a NEAREST upscaled preview for inspection

Usage:
  python crisp_quantize32.py input.png out_32.png out_preview.png
"""

import sys
from PIL import Image

PALETTE = [
    (30, 30, 32),
    (44, 44, 47),
    (58, 58, 61),
    (72, 72, 76),
    (88, 88, 92),
    (104, 104, 108),
    (120, 120, 125),
    (138, 138, 145),
    # optional glow accents
    (160, 120, 20),
    (200, 160, 30),
    (240, 210, 40),
    (255, 245, 180),
]

def nearest_palette_color(rgb, palette):
    r, g, b = rgb
    best = palette[0]
    best_d = 10**18
    for pr, pg, pb in palette:
        dr = r - pr
        dg = g - pg
        db = b - pb
        d = dr*dr + dg*dg + db*db
        if d < best_d:
            best_d = d
            best = (pr, pg, pb)
    return best

def box_avg(img, x0, y0, x1, y1):
    # img is RGB Image; crop then average pixels
    region = img.crop((x0, y0, x1, y1))
    px = region.load()
    w, h = region.size
    sr = sg = sb = 0
    n = w * h
    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y]
            sr += r; sg += g; sb += b
    return (sr // n, sg // n, sb // n)

def quantize_to_32(img_rgba, palette):
    src = img_rgba.convert("RGBA")
    alpha = src.split()[-1]
    rgb = src.convert("RGB")

    W, H = rgb.size
    out = Image.new("RGBA", (32, 32))
    out_px = out.load()

    # Map each target pixel to a source rectangle
    for ty in range(32):
        y0 = (ty * H) // 32
        y1 = ((ty + 1) * H) // 32
        if y1 == y0:
            y1 = min(H, y0 + 1)
        for tx in range(32):
            x0 = (tx * W) // 32
            x1 = ((tx + 1) * W) // 32
            if x1 == x0:
                x1 = min(W, x0 + 1)

            avg = box_avg(rgb, x0, y0, x1, y1)
            snapped = nearest_palette_color(avg, palette)

            # Alpha: average alpha in the same box (or just take center)
            a_region = alpha.crop((x0, y0, x1, y1))
            a_px = a_region.load()
            aw, ah = a_region.size
            sa = 0
            n = aw * ah
            for yy in range(ah):
                for xx in range(aw):
                    sa += a_px[xx, yy]
            a = sa // n

            out_px[tx, ty] = (*snapped, a)

    return out

def main():
    if len(sys.argv) < 4:
        print("Usage: python crisp_quantize32.py input.png out_32.png out_preview.png")
        sys.exit(2)

    inp, out32, outprev = sys.argv[1], sys.argv[2], sys.argv[3]

    img = Image.open(inp)
    tex32 = quantize_to_32(img, PALETTE)
    tex32.save(out32)

    # Preview: scale up with NEAREST so it never looks blurred in viewers
    preview = tex32.resize((32*16, 32*16), resample=Image.Resampling.NEAREST)
    preview.save(outprev)

    print("Wrote:", out32)
    print("Wrote:", outprev)

if __name__ == "__main__":
    main()
