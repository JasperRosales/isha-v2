"""Generates the launcher icon from the animated logo's path data.

The shapes and stroke widths mirror app/src/main/java/dev/jasper/ishav2/ui/splash/AnimatedLogo.kt
(its final frame, at rest). The tapered brush strokes are rebuilt here as filled
ribbons, because VectorDrawable strokes can't taper.

    python3 art/gen_logo_icon.py art app/src/main/res/drawable
"""
import math
import os
import re
import sys

# --- path data (1254 grid), copied from AnimatedLogo.kt -------------------------------
HAIR_OUTLINE = "M255,585 C270,420 330,300 450,235 C540,185 650,180 715,222 C730,212 745,210 760,214 C860,240 905,340 920,440 C935,520 945,570 950,620"
LEFT_STRAND = "M355,548 C290,620 170,650 125,705 C95,745 110,785 145,805"
BIG_SWIRL = "M248,688 C200,800 100,860 100,935 C100,1010 230,1050 555,1022"
INNER_LOCK = "M405,752 C400,870 280,900 255,970 C245,1000 260,1020 275,1025"
RIGHT_LOCK = "M885,575 C940,630 950,700 890,762"
ORBIT = "M735,865 C950,760 1180,620 1195,470 C1205,390 1120,355 1022,358"
COLLAR = "M440,912 C520,905 590,975 652,995 C700,960 745,925 790,915"
SHOULDER = "M815,912 C900,905 950,960 962,1080"
BANGS = "M400,588 C500,560 610,500 636,415"
FACE = "M435,605 C470,585 560,560 620,520 C680,470 720,420 740,365 C760,420 800,480 835,552 L820,575 L805,690 C770,730 730,760 695,788 C620,780 540,750 490,690 C470,665 450,640 435,605 Z"
EAR = "M440,603 C400,592 383,630 395,660 C405,685 445,692 490,690 Z"
NECK = "M552,755 L548,820 C545,850 520,880 485,905 L650,990 L790,915 C730,905 690,880 685,860 L680,795 Z"
CHEEK_STRAND = "M436,602 C450,650 490,690 532,692 C495,660 475,620 488,582 Z"
EYE_LEFT = "M540,618 C560,595 612,580 637,610"
LASH_LEFT = "M510,597 L545,604"
EYE_RIGHT = "M736,604 C755,578 800,566 824,582"
BROW_RIGHT = "M748,507 C770,488 792,490 802,498"
SPARKLE = "M1040,425 C1045,475 1060,495 1110,500 C1060,505 1045,525 1040,575 C1035,525 1020,505 968,500 C1020,495 1035,475 1040,425 Z"
SPARKLE_C = (1040, 500)
MINI_STARS = [(1150, 760), (190, 330)]
MINI_STAR_SCALE = 0.32
CLIP_BARS = [((392, 384), (468, 414)), ((382, 428), (448, 460))]
CLIP_BAR_WIDTH = 22

# (data, maxWidth, startTaper, endTaper, minWidth), same as LogoParts
HAIR = [
    (HAIR_OUTLINE, 20, 0.06, 0.30, 0.10),
    (LEFT_STRAND, 22, 0.10, 0.45, 0.10),
    (BIG_SWIRL, 44, 0.12, 0.55, 0.10),
    (INNER_LOCK, 22, 0.10, 0.40, 0.10),
    (RIGHT_LOCK, 22, 0.25, 0.45, 0.10),
    (BANGS, 16, 0.10, 0.35, 0.10),
    (COLLAR, 16, 0.20, 0.20, 0.25),
    (SHOULDER, 18, 0.15, 0.40, 0.10),
]
ORBIT_STROKE = (ORBIT, 34, 0.30, 0.40, 0.10)
EYES = [
    (LASH_LEFT, 11, 0.10, 0.50, 0.4),
    (EYE_LEFT, 14, 0.25, 0.30, 0.3),
    (EYE_RIGHT, 14, 0.25, 0.30, 0.3),
    (BROW_RIGHT, 11, 0.30, 0.30, 0.3),
]

INK = "#FF0A0A0B"
TILE_TOP = "#FF1D1D21"
TILE_BOTTOM = "#FF060607"
ORANGE_LIGHT = "#FFFFB866"
ORANGE = "#FFFF8A33"
ORANGE_DEEP = "#FFF2561D"
SKIN = "#FFFDE8DC"
SKIN_SHADE = "#FFF6D3C2"


# --- geometry ----------------------------------------------------------------------------
def parse(d):
    """Absolute M/L/C/Z only, which is all the logo uses. Returns subpaths of segments."""
    toks = re.findall(r"[MLCZ]|-?\d+(?:\.\d+)?", d)
    subpaths, segs, cur, start, i, cmd = [], [], None, None, 0, None
    while i < len(toks):
        if toks[i] in "MLCZ":
            cmd = toks[i]
            i += 1
            if cmd == "Z":
                if cur != start:
                    segs.append(("L", cur, start))
                subpaths.append((segs, True))
                segs, cur = [], start
                continue
        nums = lambda k: [(float(toks[i + 2 * j]), float(toks[i + 2 * j + 1])) for j in range(k)]
        if cmd == "M":
            if segs:
                subpaths.append((segs, False))
                segs = []
            (cur,) = nums(1)
            start = cur
            i += 2
            cmd = "L"
        elif cmd == "L":
            (p,) = nums(1)
            segs.append(("L", cur, p))
            cur = p
            i += 2
        elif cmd == "C":
            c1, c2, p = nums(3)
            segs.append(("C", cur, c1, c2, p))
            cur = p
            i += 6
    if segs:
        subpaths.append((segs, False))
    return subpaths


def seg_point(s, t):
    if s[0] == "L":
        (ax, ay), (bx, by) = s[1], s[2]
        return ax + (bx - ax) * t, ay + (by - ay) * t
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = s[1:]
    u = 1 - t
    return (u ** 3 * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t ** 3 * x3,
            u ** 3 * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t ** 3 * y3)


def polyline(d, per_seg=400):
    pts = []
    for segs, _ in parse(d):
        for s in segs:
            for k in range(per_seg + (1 if s is segs[-1] else 0)):
                pts.append(seg_point(s, k / per_seg))
    return pts


def ribbon(data, max_w, start_taper, end_taper, min_w, n=120):
    """Port of BrushStroke: arc-length samples, sine tapers at both ends."""
    pts = polyline(data)
    cum = [0.0]
    for a, b in zip(pts, pts[1:]):
        cum.append(cum[-1] + math.dist(a, b))
    total = cum[-1]

    def at(dist):
        lo, hi = 0, len(cum) - 1
        while hi - lo > 1:
            mid = (lo + hi) // 2
            if cum[mid] <= dist:
                lo = mid
            else:
                hi = mid
        span = cum[hi] - cum[lo] or 1e-9
        f = (dist - cum[lo]) / span
        (ax, ay), (bx, by) = pts[lo], pts[hi]
        tx, ty = bx - ax, by - ay
        ln = math.hypot(tx, ty) or 1e-4
        return (ax + tx * f, ay + ty * f), (-ty / ln, tx / ln)

    def ease(x):
        return 1.0 if x >= 1 else math.sin(max(x, 0.0) * math.pi / 2)

    left, right = [], []
    for i in range(n + 1):
        u = i / n
        (x, y), (nx, ny) = at(total * u)
        hw = max_w * (min_w + (1 - min_w) * ease(u / start_taper) * ease((1 - u) / end_taper)) / 2
        left.append((x + nx * hw, y + ny * hw))
        right.append((x - nx * hw, y - ny * hw))
    return [left + right[::-1]]


def shape(d):
    """A filled path as polygons-with-curves, kept as segments for exact output."""
    return parse(d)


def capsule(a, b, w, steps=10):
    """A round-capped line as a polygon (the hair clip bars)."""
    (ax, ay), (bx, by) = a, b
    ang = math.atan2(by - ay, bx - ax)
    r = w / 2
    poly = []
    for k in range(steps + 1):
        t = ang + math.pi / 2 + math.pi * k / steps
        poly.append((ax + r * math.cos(t), ay + r * math.sin(t)))
    for k in range(steps + 1):
        t = ang - math.pi / 2 + math.pi * k / steps
        poly.append((bx + r * math.cos(t), by + r * math.sin(t)))
    return [poly]


def star_at(pos, scale):
    cx, cy = SPARKLE_C
    out = []
    for segs, closed in parse(SPARKLE):
        mapped = []
        for s in segs:
            pts = [(pos[0] + (x - cx) * scale, pos[1] + (y - cy) * scale) for x, y in s[1:]]
            mapped.append((s[0], *pts))
        out.append((mapped, closed))
    return out


# --- layout: fit the artwork inside the adaptive icon's visible circle -------------------
def all_points():
    pts = []
    for st in HAIR + [ORBIT_STROKE]:
        pts += ribbon(*st)[0]
    pts += polyline(SPARKLE)
    for p in MINI_STARS:
        pts.append(p)
    return pts


_P = all_points()
_minx, _maxx = min(p[0] for p in _P), max(p[0] for p in _P)
_miny, _maxy = min(p[1] for p in _P), max(p[1] for p in _P)
CX, CY = (_minx + _maxx) / 2, (_miny + _maxy) / 2
# Launchers mask the 108dp layer to (at least) a 72dp circle; keep everything within r=34.
RADIUS = max(math.dist((CX, CY), p) for p in _P)
S = 34.0 / RADIUS


def tx(p):
    return 54 + (p[0] - CX) * S, 54 + (p[1] - CY) * S


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def pt(p):
    x, y = tx(p)
    return f"{f(x)},{f(y)}"


def poly_d(polys):
    return "".join("M" + "L".join(pt(p) for p in poly) + "Z" for poly in polys)


def seg_d(subpaths):
    out = []
    for segs, closed in subpaths:
        out.append("M" + pt(segs[0][1]))
        for s in segs:
            if s[0] == "L":
                out.append("L" + pt(s[2]))
            else:
                out.append("C" + " ".join(pt(p) for p in s[2:]))
        if closed:
            out.append("Z")
    return "".join(out)


# --- VectorDrawable output --------------------------------------------------------------
def linear(x1, y1, x2, y2, stops, ind):
    a, b = tx((x1, y1)), tx((x2, y2))
    items = "\n".join(f'{ind}        <item android:offset="{o}" android:color="{c}" />' for o, c in stops)
    return (f'{ind}<aapt:attr name="android:fillColor">\n'
            f'{ind}    <gradient android:type="linear" android:startX="{f(a[0])}" android:startY="{f(a[1])}"'
            f' android:endX="{f(b[0])}" android:endY="{f(b[1])}">\n{items}\n'
            f'{ind}    </gradient>\n{ind}</aapt:attr>')


def radial(cx, cy, r, stops, ind):
    c = tx((cx, cy))
    items = "\n".join(f'{ind}        <item android:offset="{o}" android:color="{col}" />' for o, col in stops)
    return (f'{ind}<aapt:attr name="android:fillColor">\n'
            f'{ind}    <gradient android:type="radial" android:centerX="{f(c[0])}" android:centerY="{f(c[1])}"'
            f' android:gradientRadius="{f(r * S)}">\n{items}\n'
            f'{ind}    </gradient>\n{ind}</aapt:attr>')


ORANGE_GRAD = lambda ind: linear(150, 150, 1150, 1150, [(0, ORANGE_LIGHT), (0.45, ORANGE), (1, ORANGE_DEEP)], ind)
SKIN_GRAD = lambda ind: linear(0, 380, 0, 1000, [(0, SKIN), (1, SKIN_SHADE)], ind)


def path(d, fill=None, grad=None, alpha=None, even_odd=False):
    attrs = [f'android:pathData="{d}"']
    if fill:
        attrs.append(f'android:fillColor="{fill}"')
    if alpha is not None:
        attrs.append(f'android:fillAlpha="{alpha}"')
    if even_odd:
        attrs.append('android:fillType="evenOdd"')
    head = "    <path\n        " + "\n        ".join(attrs)
    if grad:
        return head + ">\n" + grad("        ") + "\n    </path>"
    return head + " />"


def vector(body, comment):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f"<!-- Generated by art/gen_logo_icon.py. {comment} -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            + body + "\n</vector>\n")


def hair_d():
    return "".join(poly_d(ribbon(*st)) for st in HAIR)


def clip_d():
    return "".join(poly_d(capsule(a, b, CLIP_BAR_WIDTH)) for a, b in CLIP_BARS)


def stars_d():
    return seg_d(parse(SPARKLE)) + "".join(seg_d(star_at(p, MINI_STAR_SCALE)) for p in MINI_STARS)


def foreground():
    sparkle_glow = tx(SPARKLE_C)
    r = 150 * 0.85
    glow_box = (f"M{f(sparkle_glow[0] - r * S)},{f(sparkle_glow[1])}"
                f"a{f(r * S)},{f(r * S)} 0 1,0 {f(2 * r * S)},0a{f(r * S)},{f(r * S)} 0 1,0 {f(-2 * r * S)},0Z")
    eyes = "".join(poly_d(ribbon(*st)) for st in EYES)
    return "\n".join([
        path(seg_d(shape(EAR)), grad=SKIN_GRAD),
        path(seg_d(shape(FACE)), grad=SKIN_GRAD),
        path(seg_d(shape(NECK)), grad=SKIN_GRAD),
        path(seg_d(shape(CHEEK_STRAND)), fill=INK),
        path(hair_d(), grad=ORANGE_GRAD),
        path(eyes, fill=INK),
        path(clip_d(), fill=ORANGE_LIGHT),
        path(poly_d(ribbon(*ORBIT_STROKE)), grad=ORANGE_GRAD),
        path(glow_box, grad=lambda ind: radial(*SPARKLE_C, r, [(0, "#73FF8A33"), (1, "#00FF8A33")], ind)),
        path(stars_d(), grad=ORANGE_GRAD),
    ])


def background():
    # Full-bleed tile; the launcher's mask gives it its shape.
    return path("M0,0h108v108h-108Z",
                grad=lambda ind: radial(470, 330, 1150, [(0, TILE_TOP), (1, TILE_BOTTOM)], ind))


def monochrome():
    # Themed icons are one colour, so the eyes are cut out of the face instead of inked on.
    face_with_eyes = seg_d(shape(FACE)) + "".join(poly_d(ribbon(*st)) for st in EYES)
    return "\n".join([
        path(seg_d(shape(EAR)), fill="#FFFFFFFF"),
        path(seg_d(shape(NECK)), fill="#FFFFFFFF"),
        path(face_with_eyes, fill="#FFFFFFFF", even_odd=True),
        path(hair_d() + clip_d(), fill="#FFFFFFFF"),
        path(poly_d(ribbon(*ORBIT_STROKE)), fill="#FFFFFFFF"),
        path(stars_d(), fill="#FFFFFFFF"),
    ])


def avatar():
    """In-app avatar: tile + logo, cropped to the central 72 units (the circle the logo was fitted to)."""
    body = background() + "\n" + foreground()
    body = "\n".join("    " + l if l else l for l in body.split("\n"))
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- Generated by art/gen_logo_icon.py. isha's logo as the in-app avatar. -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="72dp"\n    android:height="72dp"\n'
            '    android:viewportWidth="72"\n    android:viewportHeight="72">\n'
            '    <group android:translateX="-18" android:translateY="-18">\n'
            + body + "\n    </group>\n</vector>\n")


def preview_svg():
    """Static preview of the icon on its tile, for eyeballing without a device."""
    body = foreground() + "\n"
    # crude but adequate VectorDrawable → SVG conversion for this generator's own output
    body = re.sub(r'<path\s+android:pathData="([^"]+)"\s+android:fillColor="#(..)(......)"( android:fillAlpha="[^"]*")?( android:fillType="evenOdd")?\s*/>',
                  lambda m: f'<path d="{m.group(1)}" fill="#{m.group(3)}"/>', body)
    grads, n = [], [0]

    def grad_path(m):
        n[0] += 1
        gid = f"g{n[0]}"
        g = m.group(2)
        stops = "".join(f'<stop offset="{o}" stop-color="#{c[3:]}" stop-opacity="{int(c[1:3], 16) / 255:.2f}"/>'
                        for o, c in re.findall(r'offset="([^"]+)" android:color="([^"]+)"', g))
        if 'type="linear"' in g:
            a = dict(re.findall(r'android:(start[XY]|end[XY])="([^"]+)"', g))
            grads.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{a["startX"]}" y1="{a["startY"]}" x2="{a["endX"]}" y2="{a["endY"]}">{stops}</linearGradient>')
        else:
            a = dict(re.findall(r'android:(center[XY]|gradientRadius)="([^"]+)"', g))
            grads.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{a["centerX"]}" cy="{a["centerY"]}" r="{a["gradientRadius"]}">{stops}</radialGradient>')
        return f'<path d="{m.group(1)}" fill="url(#{gid})"/>'

    body = re.sub(r'<path\s+android:pathData="([^"]+)">(.*?)</path>', grad_path, body, flags=re.S)
    bg = re.sub(r'<path\s+android:pathData="([^"]+)">(.*?)</path>', grad_path, background(), flags=re.S)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="512" height="512">'
            f'<defs><clipPath id="m"><rect x="18" y="18" width="72" height="72" rx="16"/></clipPath>{"".join(grads)}</defs>'
            f'<g clip-path="url(#m)">{bg}{body}</g></svg>\n')


if __name__ == "__main__":
    art_dir, res = sys.argv[1], sys.argv[2]
    open(os.path.join(res, "ic_launcher_foreground.xml"), "w").write(vector(foreground(), "isha's logo, final frame."))
    open(os.path.join(res, "ic_launcher_background.xml"), "w").write(vector(background(), "The logo's dark tile."))
    open(os.path.join(res, "ic_launcher_monochrome.xml"), "w").write(vector(monochrome(), "Themed-icon silhouette."))
    open(os.path.join(res, "isha_avatar.xml"), "w").write(avatar())
    open(os.path.join(art_dir, "isha-logo-icon.svg"), "w").write(preview_svg())
    print(f"scale {S:.4f}, centre ({CX:.0f},{CY:.0f}), radius {RADIUS:.0f}")
