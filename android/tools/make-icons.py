#!/usr/bin/env python3
"""
Builds the Android launcher icon from the iOS app icon.

The iOS app ships one 1024x1024 illustration (a board-game box labelled
"ADOBE LEHI GAME CHECKOUT"). Android needs it shaped differently:

  * An adaptive icon whose foreground occupies a 108x108dp canvas. Launchers
    crop that to a circle/squircle/rounded-square and only guarantee the centre
    72x72dp, so the artwork is scaled down and centred to survive the mask.
  * Legacy density buckets, drawn full-bleed with the same corner radius iOS
    uses, for anything predating adaptive icons.

Run from the android/ directory:

    python3 tools/make-icons.py

Pure stdlib (zlib + struct): no Pillow, no ImageMagick, no display. Re-run this
whenever the iOS artwork changes.
"""

import os
import struct
import sys
import zlib

# --- configuration --------------------------------------------------------

# Fraction of the 108dp adaptive canvas the artwork spans. Only the centre
# 72/108 = 66.7% is guaranteed visible, so keeping this at or below ~0.92 means
# the box survives a circular mask. Lower it for more breathing room.
FOREGROUND_FRACTION = 0.70

# Legacy icons are full-bleed with the iOS squircle radius.
LEGACY_CORNER_RADIUS = 0.2237

FOREGROUND_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

SOURCE = "../ios/GameCheckout/GameCheckout/Assets.xcassets/AppIcon.appiconset/image_76540ca4.png"
RES = "app/src/main/res"

# Login-screen mark: the artwork with its backdrop keyed out, shown next to the
# "Game Checkout" title. Kept to one density-less drawable because it is displayed
# at a fixed dp size and Android scales it.
LOGIN_MARK = "app/src/main/res/drawable/login_mark.png"
LOGIN_MARK_SIZE = 384

# Background key, as sum-of-absolute-RGB difference from the sampled backdrop.
# Measured on this artwork: the drop shadow sits at 1-9 while the box silhouette
# jumps to 255+, so the gap is wide enough for a single threshold to cut cleanly.
# Only border-connected pixels are cleared, so cream tones *inside* the box are
# never touched however close to the background they happen to be.
#
# Threshold rather than a colour-ratio heuristic on purpose: the illustration's
# muted grey-greens are chromatically close enough to the cream backdrop that a
# "neutral pixel = shadow" rule also punches holes in the artwork.
KEY_THRESHOLD = 12

# Upper bound, as sum-of-absolute-RGB difference from the backdrop, for a
# pixel to be treated as shadow-or-edge rather than artwork. Measured on this
# artwork the shadow's transition tops out around 55, while the box's own
# colours start well above 200, so this sits in open space.
SHADOW_MAX_DIFF = 120

# Maximum per-channel coverage spread (max - min of 1 - observed/backdrop)
# for a pixel to still count as shadow rather than artwork. The shadow
# measures ~0.01 here and the box >= 0.12, so this sits in open space.
SHADOW_MAX_SPREAD = 0.06


# --- minimal PNG decoder (stdlib only) -----------------------------------

def _paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def _unfilter(raw, width, height, bpp, stride):
    """Reverses the per-scanline PNG filters, in place, and returns the rows."""
    out = bytearray(height * stride)
    pos = 0
    for y in range(height):
        ftype = raw[pos]
        pos += 1
        line = bytearray(raw[pos:pos + stride])
        pos += stride

        base = y * stride
        prior = base - stride
        if ftype == 0:
            pass
        elif ftype == 1:  # Sub
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif ftype == 2:  # Up
            if y:
                for i in range(stride):
                    line[i] = (line[i] + out[prior + i]) & 0xFF
        elif ftype == 3:  # Average
            for i in range(stride):
                left = line[i - bpp] if i >= bpp else 0
                up = out[prior + i] if y else 0
                line[i] = (line[i] + ((left + up) >> 1)) & 0xFF
        elif ftype == 4:  # Paeth
            for i in range(stride):
                left = line[i - bpp] if i >= bpp else 0
                up = out[prior + i] if y else 0
                upleft = out[prior + i - bpp] if (y and i >= bpp) else 0
                line[i] = (line[i] + _paeth(left, up, upleft)) & 0xFF
        else:
            sys.exit(f"error: unknown PNG filter type {ftype} on row {y}")

        out[base:base + stride] = line
    return out


class Image:
    """Decoded 8-bit RGB image."""

    def __init__(self, width, height, rgb):
        self.width = width
        self.height = height
        self.rgb = rgb  # bytes, 3 per pixel

    def at(self, x, y):
        if x < 0:
            x = 0
        elif x >= self.width:
            x = self.width - 1
        if y < 0:
            y = 0
        elif y >= self.height:
            y = self.height - 1
        i = (y * self.width + x) * 3
        return self.rgb[i], self.rgb[i + 1], self.rgb[i + 2]


def decode_png(path):
    with open(path, "rb") as fh:
        data = fh.read()

    if data[:8] != b"\x89PNG\r\n\x1a\n":
        sys.exit(f"error: {path} is not a PNG")

    pos = 8
    header = None
    palette = b""
    idat = bytearray()

    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        pos += 12 + length

        if tag == b"IHDR":
            header = struct.unpack(">IIBBBBB", payload)
        elif tag == b"PLTE":
            palette = payload
        elif tag == b"IDAT":
            idat += payload
        elif tag == b"IEND":
            break

    if header is None:
        sys.exit("error: PNG has no IHDR")

    width, height, depth, colour_type, compression, filt, interlace = header
    if interlace:
        sys.exit("error: interlaced PNGs are not supported")
    if depth not in (8, 16):
        sys.exit(f"error: unsupported bit depth {depth}")

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}.get(colour_type)
    if channels is None:
        sys.exit(f"error: unsupported colour type {colour_type}")

    sample_bytes = depth // 8
    bpp = channels * sample_bytes
    stride = width * bpp
    raw = zlib.decompress(bytes(idat))
    rows = _unfilter(raw, width, height, bpp, stride)

    # Normalise everything to 8-bit RGB.
    rgb = bytearray(width * height * 3)
    out = 0
    for y in range(height):
        base = y * stride
        for x in range(width):
            i = base + x * bpp
            if colour_type == 0:      # grayscale
                v = rows[i] if depth == 8 else rows[i]
                r = g = b = v
            elif colour_type == 2:    # RGB
                r, g, b = rows[i], rows[i + 1], rows[i + 2]
            elif colour_type == 3:    # palette
                idx = rows[i] * 3
                r, g, b = palette[idx], palette[idx + 1], palette[idx + 2]
            elif colour_type == 4:    # grayscale + alpha
                v = rows[i]
                r = g = b = v
            else:                     # RGBA
                r, g, b = rows[i], rows[i + 1], rows[i + 2]
            rgb[out] = r
            rgb[out + 1] = g
            rgb[out + 2] = b
            out += 3

    return Image(width, height, bytes(rgb))


# --- layout helpers -------------------------------------------------------

def dominant_edge_colour(img):
    """Most common colour along the border, used to pad past the artwork."""
    counts = {}
    step = max(1, img.width // 64)
    for x in range(0, img.width, step):
        for y in (0, img.height - 1):
            c = img.at(x, y)
            counts[c] = counts.get(c, 0) + 1
    for y in range(0, img.height, step):
        for x in (0, img.width - 1):
            c = img.at(x, y)
            counts[c] = counts.get(c, 0) + 1
    return max(counts, key=counts.get)


def find_artwork_bounds(img, bg, tolerance=12):
    """Tight bounding box of non-background content."""
    def differs(x, y):
        r, g, b = img.at(x, y)
        return (abs(r - bg[0]) + abs(g - bg[1]) + abs(b - bg[2])) > tolerance

    left, right, top, bottom = img.width, -1, img.height, -1
    step = max(1, img.width // 256)
    for y in range(0, img.height, step):
        for x in range(0, img.width, step):
            if differs(x, y):
                left, right = min(left, x), max(right, x)
                top, bottom = min(top, y), max(bottom, y)

    if right < left or bottom < top:
        return 0, 0, img.width - 1, img.height - 1

    pad = int(0.01 * img.width)
    return (max(0, left - pad), max(0, top - pad),
            min(img.width - 1, right + pad), min(img.height - 1, bottom + pad))


def rounded_mask_alpha(size, radius, supersample=4):
    """Anti-aliased rounded-square alpha mask."""
    alpha = bytearray(size * size)
    limit = size - 1
    inv = 1.0 / (supersample * supersample)
    for y in range(size):
        for x in range(size):
            hits = 0
            for sy in range(supersample):
                py = y + (sy + 0.5) / supersample
                for sx in range(supersample):
                    px = x + (sx + 0.5) / supersample
                    cx = min(max(px, radius), limit - radius)
                    cy = min(max(py, radius), limit - radius)
                    dx, dy = px - cx, py - cy
                    if dx * dx + dy * dy <= radius * radius:
                        hits += 1
            alpha[y * size + x] = int(255 * hits * inv)
    return alpha


def remove_background(img, threshold=KEY_THRESHOLD, shadow_max=SHADOW_MAX_DIFF):
    """
    Returns (rgb_bytes, alpha_bytes) with the artwork's backdrop removed.

    Two things happen here, because the source has two distinct kinds of
    background:

    1. The flat cream backdrop is keyed out. A pixel is cleared only when it is
       both close to the sampled backdrop and *connected to the image border*
       through other such pixels — a flood fill, not a global colour match. The
       illustration's parchment and muted greys sit chromatically close to the
       cream, so a global key would punch holes in the box, whereas a flood fill
       stops dead at the silhouette.

    2. The drop shadow is re-authored as neutral black with real alpha. The
       artwork fakes it by darkening the cream backdrop, so those pixels are
       cream-coloured and opaque. Keying them (or leaving them) fails at both
       ends: leave them and they read as a pale halo over a dark surface, key
       them with a threshold and the shadow's darker core clears while its
       lighter rim survives, which turns a shadow into a glow. Instead the
       backdrop's contribution is removed per channel and what remains is
       expressed as alpha, so it behaves like a real shadow on any background.
    """
    from collections import deque

    w, h = img.width, img.height
    bg = dominant_edge_colour(img)
    n = w * h

    rgb = bytearray(img.rgb)
    diff = bytearray(n)
    for i in range(n):
        j = i * 3
        d = abs(rgb[j] - bg[0]) + abs(rgb[j + 1] - bg[1]) + abs(rgb[j + 2] - bg[2])
        diff[i] = 255 if d > 255 else d

    # --- 1. flood fill the flat backdrop ---------------------------------
    clear = bytearray(n)
    queue = deque()
    for x in range(w):
        for y in (0, h - 1):
            i = y * w + x
            if not clear[i] and diff[i] <= threshold:
                clear[i] = 1
                queue.append(i)
    for y in range(h):
        for x in (0, w - 1):
            i = y * w + x
            if not clear[i] and diff[i] <= threshold:
                clear[i] = 1
                queue.append(i)

    while queue:
        i = queue.popleft()
        x, y = i % w, i // w
        for k, ok in ((i - 1, x > 0), (i + 1, x + 1 < w),
                      (i - w, y > 0), (i + w, y + 1 < h)):
            if ok and not clear[k] and diff[k] <= threshold:
                clear[k] = 1
                queue.append(k)

    # --- 2. shadow / edge pixels bordering the cleared area ---------------
    # Anything reachable outward from the cleared region is either the shadow or
    # the box's anti-aliased silhouette. Both are a partial mix of backdrop and a
    # subject colour, so estimate the subject colour per channel and its coverage.
    #
    # The walk repeats until it stops changing: the shadow is a graded wash, so a
    # single pass would treat only the outermost shell of it and leave the rest
    # opaque, which is what turned the shadow into a glow.
    alpha = bytearray(n)
    for i in range(n):
        alpha[i] = 0 if clear[i] else 255

    # Each pixel is examined once; without this the walk revisits the same
    # rejected pixels forever via their neighbours.
    visited = bytearray(n)
    frontier = deque()
    for i in range(n):
        if clear[i]:
            continue
        x, y = i % w, i // w
        if ((x > 0 and clear[i - 1]) or (x + 1 < w and clear[i + 1])
                or (y > 0 and clear[i - w]) or (y + 1 < h and clear[i + w])):
            frontier.append(i)

    while frontier:
        i = frontier.popleft()
        if visited[i] or clear[i]:
            continue
        visited[i] = 1
        if diff[i] > shadow_max:
            continue

        # Per-channel coverage: 1 - observed/backdrop. A shadow that merely
        # darkens the backdrop gives the same value on all three channels;
        # artwork with its own colour does not.
        ratios = (
            1.0 - (rgb[i * 3] / bg[0] if bg[0] else 0.0),
            1.0 - (rgb[i * 3 + 1] / bg[1] if bg[1] else 0.0),
            1.0 - (rgb[i * 3 + 2] / bg[2] if bg[2] else 0.0),
        )
        coverage = sum(ratios) / 3.0
        spread = max(ratios) - min(ratios)

        # Only neutral darkening is shadow. Measured on this artwork the shadow's
        # spread is ~0.01 while the box is >= 0.12, so the test separates them
        # cleanly; a coloured pixel stops the walk instead of being recoloured.
        if coverage <= 0.02 or coverage > 1.0 or spread > SHADOW_MAX_SPREAD:
            continue

        alpha[i] = int(round(coverage * 255))

        # Re-author as black at that coverage. The pixel is a shadow, so the
        # perceptual result should be a darkening, not a tint of the backdrop.
        rgb[i * 3] = rgb[i * 3 + 1] = rgb[i * 3 + 2] = 0

        # Continue outward from this pixel.
        x, y = i % w, i // w
        for k, ok in ((i - 1, x > 0), (i + 1, x + 1 < w),
                      (i - w, y > 0), (i + w, y + 1 < h)):
            if ok and not clear[k] and not visited[k]:
                frontier.append(k)

    for i in range(n):
        if not clear[i] and alpha[i] == 0:
            alpha[i] = 255

    return bytes(rgb), alpha


def despeckle(rgb, alpha, size, min_component=12):
    """
    Clears opaque specks too small to be artwork.

    The source is a lossy-compressed illustration, so the backdrop carries faint
    JPEG noise. A few of those pixels land further than the key threshold from
    the sampled backdrop, so the flood fill cannot claim them and they survive as
    isolated dots around the silhouette. They are invisible at ~45dp but read as
    grit up close, so any tiny disconnected opaque island is dropped.
    """
    from collections import deque

    n = size * size
    visited = bytearray(n)
    remove = bytearray(n)

    for start in range(n):
        if visited[start] or alpha[start] == 0:
            continue
        component = []
        queue = deque([start])
        visited[start] = 1
        while queue:
            i = queue.popleft()
            component.append(i)
            x, y = i % size, i // size
            for k, ok in ((i - 1, x > 0), (i + 1, x + 1 < size),
                          (i - size, y > 0), (i + size, y + 1 < size)):
                if ok and not visited[k] and alpha[k] > 0:
                    visited[k] = 1
                    queue.append(k)
        if len(component) < min_component:
            for i in component:
                remove[i] = 1

    if not any(remove):
        return rgb, alpha, 0

    out_alpha = bytearray(alpha)
    out_rgb = bytearray(rgb)
    for i in range(n):
        if remove[i]:
            out_alpha[i] = 0
            out_rgb[i * 3] = out_rgb[i * 3 + 1] = out_rgb[i * 3 + 2] = 0
    return bytes(out_rgb), out_alpha, sum(remove)


def crop_to_alpha(rgb, alpha, size, padding=2, min_alpha=110):
    """
    Trims transparent margins so the subject fills its bounds.

    Requires a minimum alpha rather than merely non-zero: the drop shadow is
    a large, very faint wash, so cropping on alpha > 0 would trim almost
    nothing and leave the mark floating in empty space.
    """
    left, top, right, bottom = size, size, -1, -1
    for y in range(size):
        row = y * size
        for x in range(size):
            if alpha[row + x] >= min_alpha:
                if x < left:
                    left = x
                if x > right:
                    right = x
                if y < top:
                    top = y
                if y > bottom:
                    bottom = y
    if right < left or bottom < top:
        return rgb, alpha, size, size, 0, 0

    left = max(0, left - padding)
    top = max(0, top - padding)
    right = min(size - 1, right + padding)
    bottom = min(size - 1, bottom + padding)

    w, h = right - left + 1, bottom - top + 1
    out_rgb = bytearray(w * h * 3)
    out_alpha = bytearray(w * h)
    for y in range(h):
        src = ((y + top) * size + left) * 3
        dst = y * w * 3
        out_rgb[dst:dst + w * 3] = rgb[src:src + w * 3]
        out_alpha[y * w:(y + 1) * w] = alpha[(y + top) * size + left:(y + top) * size + right + 1]
    return bytes(out_rgb), out_alpha, w, h, left, top


def downsample(img, size):
    """Box-filters the source down to size x size."""
    if img.width == size:
        return img
    out = bytearray(size * size * 3)
    ratio = img.width / size
    for y in range(size):
        sy0, sy1 = int(y * ratio), max(int((y + 1) * ratio), int(y * ratio) + 1)
        for x in range(size):
            sx0, sx1 = int(x * ratio), max(int((x + 1) * ratio), int(x * ratio) + 1)
            r = g = b = count = 0
            for sy in range(sy0, min(sy1, img.height)):
                base = sy * img.width
                for sx in range(sx0, min(sx1, img.width)):
                    j = (base + sx) * 3
                    r += img.rgb[j]
                    g += img.rgb[j + 1]
                    b += img.rgb[j + 2]
                    count += 1
            count = max(1, count)
            o = (y * size + x) * 3
            out[o] = r // count
            out[o + 1] = g // count
            out[o + 2] = b // count
    return Image(size, size, bytes(out))


def write_rgba_png_at(path, width, height, rgb, alpha):
    """Writes pre-computed RGB + alpha buffers as an RGBA PNG."""
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        row = y * width
        for x in range(width):
            i = (row + x) * 3
            raw += bytes((rgb[i], rgb[i + 1], rgb[i + 2], alpha[row + x]))

    def chunk(tag, payload):
        return (len(payload).to_bytes(4, "big") + tag + payload
                + zlib.crc32(tag + payload).to_bytes(4, "big"))

    ihdr = (width.to_bytes(4, "big") + height.to_bytes(4, "big")
            + bytes((8, 6, 0, 0, 0)))
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)


def write_png(path, size, get_pixel, alpha=None):
    """Writes an 8-bit RGB/RGBA PNG with no external dependencies."""
    colour_type = 6 if alpha is not None else 2

    raw = bytearray()
    for y in range(size):
        raw.append(0)  # filter: none
        row = y * size
        for x in range(size):
            r, g, b = get_pixel(x, y)
            raw += bytes((r & 0xFF, g & 0xFF, b & 0xFF))
            if alpha is not None:
                raw.append(alpha[row + x] & 0xFF)

    def chunk(tag, payload):
        return (len(payload).to_bytes(4, "big") + tag + payload
                + zlib.crc32(tag + payload).to_bytes(4, "big"))

    ihdr = (size.to_bytes(4, "big") + size.to_bytes(4, "big")
            + bytes((8, colour_type, 0, 0, 0)))
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)


# --- build ----------------------------------------------------------------

def main():
    if not os.path.exists(SOURCE):
        sys.exit(f"error: source icon not found at {SOURCE}\n"
                 f"Run this from the android/ directory.")

    print(f"source: {SOURCE}")
    img = decode_png(SOURCE)
    print(f"decoded {img.width}x{img.height}")

    bg = dominant_edge_colour(img)
    print(f"background colour: rgb{bg}")

    left, top, right, bottom = find_artwork_bounds(img, bg)
    art_w, art_h = right - left + 1, bottom - top + 1
    print(f"artwork bounds: ({left},{top})-({right},{bottom}) = {art_w}x{art_h}")

    for bucket, canvas in FOREGROUND_SIZES.items():
        span = max(1, int(round(canvas * FOREGROUND_FRACTION)))
        scale = span / art_w
        span_h = max(1, int(round(art_h * scale)))
        off_x, off_y = (canvas - span) // 2, (canvas - span_h) // 2

        def pixel(x, y, off_x=off_x, off_y=off_y, span=span, span_h=span_h, scale=scale):
            if off_x <= x < off_x + span and off_y <= y < off_y + span_h:
                return img.at(int((x - off_x) / scale) + left,
                              int((y - off_y) / scale) + top)
            return bg

        path = f"{RES}/drawable-{bucket}/ic_launcher_foreground.png"
        write_png(path, canvas, pixel)
        print(f"  {path}  {canvas}px (art {span}x{span_h} at {off_x},{off_y})")

    for bucket, size in LEGACY_SIZES.items():
        scale = size / art_w
        radius = max(1, int(round(size * LEGACY_CORNER_RADIUS)))
        alpha = rounded_mask_alpha(size, radius)

        def pixel(x, y, scale=scale):
            return img.at(int(x / scale) + left, int(y / scale) + top)

        for name in ("ic_launcher", "ic_launcher_round"):
            path = f"{RES}/mipmap-{bucket}/{name}.png"
            write_png(path, size, pixel, alpha=alpha)
        print(f"  {RES}/mipmap-{bucket}/ic_launcher[_round].png  {size}px")

    # --- login-screen mark: artwork with the backdrop keyed out -------------
    # Keyed at a reduced resolution: the mark renders at ~120dp, and a smaller
    # buffer keeps the flood fill quick.
    working = downsample(img, LOGIN_MARK_SIZE)
    mark_rgb, mark_alpha = remove_background(working)
    mark_rgb, mark_alpha, speckles = despeckle(mark_rgb, mark_alpha, LOGIN_MARK_SIZE)
    mark_rgb, mark_alpha, mark_w, mark_h, _, _ = crop_to_alpha(
        mark_rgb, mark_alpha, LOGIN_MARK_SIZE)
    write_rgba_png_at(LOGIN_MARK, mark_w, mark_h, mark_rgb, mark_alpha)
    print(f"  {LOGIN_MARK}  {mark_w}x{mark_h}px "
          f"(trimmed to the artwork, {speckles} speckle px removed)")

    print("done")


if __name__ == "__main__":
    main()
