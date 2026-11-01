"""Generates isha's mascot as SVG + Android VectorDrawables from one shape list."""
import sys, os

def circle(cx, cy, r):
    return ellipse(cx, cy, r, r)

def ellipse(cx, cy, rx, ry):
    return (f"M{cx - rx:g},{cy:g}a{rx:g},{ry:g} 0 1,0 {2 * rx:g},0"
            f"a{rx:g},{ry:g} 0 1,0 {-2 * rx:g},0Z")

HAIR = "#3B2318"
HAIR_HI = "#5A3626"
SKIN = "#FFE4D2"
SKIN_SHADE = "#F9CDB4"
EYE = "#2A1712"
BLUSH = "#FF8C7A"
ORANGE = "#FF6D1F"
ORANGE_DEEP = "#E2530B"
CREAM = "#FFF6E8"

# (path, fill, alpha, stroke, strokeWidth)
FACE_CX, FACE_CY = 54, 60
SHAPES = [
    # shoulders / hoodie (cropped by the mask, reads as a bust)
    ("M26,100C26,84 38,77 54,77C70,77 82,84 82,100Z", CREAM, 1, None, 0),
    ("M44.6,78.4Q54,88.5 63.4,78.4Q58.7,77 54,77Q49.3,77 44.6,78.4Z", ORANGE, 1, None, 0),
    # space buns + scrunchies
    (circle(36, 32.6, 7), HAIR, 1, None, 0),
    (circle(72, 32.6, 7), HAIR, 1, None, 0),
    (circle(34.2, 30.8, 2.2), HAIR_HI, 1, None, 0),
    (circle(70.2, 30.8, 2.2), HAIR_HI, 1, None, 0),
    # back hair with two short side locks
    ("M29,58C29,41 40,30 54,30C68,30 79,41 79,58L79,72C79,75.5 76.5,77 74,75.5L72,74"
     "L36,74L34,75.5C31.5,77 29,75.5 29,72Z", HAIR, 1, None, 0),
    ("M37.2,40.8Q39.6,35.2 45.2,36.4Q44.2,39.8 41.2,41.8Q39,42.6 37.2,40.8Z", ORANGE, 1, None, 0),
    ("M70.8,40.8Q68.4,35.2 62.8,36.4Q63.8,39.8 66.8,41.8Q69,42.6 70.8,40.8Z", ORANGE, 1, None, 0),
    # neck
    ("M48.5,72h11v7.5q-5.5,3 -11,0Z", SKIN_SHADE, 1, None, 0),
    # face
    (ellipse(FACE_CX, FACE_CY, 18.6, 17.2), SKIN, 1, None, 0),
    # ears
    (ellipse(35.4, 62, 2.6, 3.4), SKIN, 1, None, 0),
    (ellipse(72.6, 62, 2.6, 3.4), SKIN, 1, None, 0),
    # bangs: rounded crown, soft points falling over the forehead
    ("M33,63C31,46 40.5,34.5 54,34.5C67.5,34.5 77,46 75,63"
     "C73.4,60 72.2,56.5 71.6,52.5"
     "Q68.6,50 66.4,46.2Q63.4,52.5 58.6,54"
     "Q57.2,49.5 54.6,46.4Q51.6,52 46.2,53.6"
     "Q44.6,49.8 42.6,47.6Q40.2,52 36.4,53"
     "C35.8,56.8 34.6,60.2 33,63Z", HAIR, 1, None, 0),
    # hair shine
    ("M44,39.5Q50,36.4 57,37.6Q51,38.8 46.2,42Z", HAIR_HI, 1, None, 0),
    # eyes
    (ellipse(46.6, 63.2, 3.5, 4.5), EYE, 1, None, 0),
    (ellipse(61.4, 63.2, 3.5, 4.5), EYE, 1, None, 0),
    (circle(47.9, 61.5, 1.45), "#FFFFFF", 1, None, 0),
    (circle(62.7, 61.5, 1.45), "#FFFFFF", 1, None, 0),
    (circle(45.5, 65.4, 0.7), "#FFFFFF", 0.85, None, 0),
    (circle(60.3, 65.4, 0.7), "#FFFFFF", 0.85, None, 0),
    # blush
    (ellipse(41.2, 69.2, 3.3, 1.9), BLUSH, 0.55, None, 0),
    (ellipse(66.8, 69.2, 3.3, 1.9), BLUSH, 0.55, None, 0),
    # smile
    ("M51.2,70.2Q54,73.4 56.8,70.2", None, 1, "#9A3F2C", 1.5),
    # sparkle hair clip
    ("M68.4,41.2l1.1,2.6l2.6,1.1l-2.6,1.1l-1.1,2.6l-1.1,-2.6l-2.6,-1.1l2.6,-1.1Z", "#FFD15C", 1, None, 0),
]

BG = ("M0,0h108v108h-108Z", None)


def svg(shapes, size=108, with_bg=True, round_clip=False):
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" height="{size}">',
           '<defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">'
           '<stop offset="0" stop-color="#FFB078"/><stop offset="1" stop-color="#FF6D1F"/></linearGradient>'
           '<clipPath id="c"><circle cx="54" cy="54" r="36"/></clipPath></defs>']
    if round_clip:
        out.append('<g clip-path="url(#c)">')
    if with_bg:
        out.append('<rect width="108" height="108" fill="url(#bg)"/>')
        out.append(f'<path d="{circle(54, 50, 30)}" fill="#FFFFFF" fill-opacity="0.18"/>')
    for d, fill, alpha, stroke, sw in shapes:
        attrs = f'd="{d}"'
        attrs += f' fill="{fill}"' if fill else ' fill="none"'
        if alpha != 1:
            attrs += f' fill-opacity="{alpha}"'
        if stroke:
            attrs += f' stroke="{stroke}" stroke-width="{sw}" stroke-linecap="round"'
        out.append(f'<path {attrs}/>')
    if round_clip:
        out.append('</g>')
    out.append('</svg>')
    return "\n".join(out)


def vd_path(d, fill, alpha, stroke, sw, indent="    "):
    lines = [f'{indent}<path', f'{indent}    android:pathData="{d}"']
    if fill:
        lines.append(f'{indent}    android:fillColor="{fill}"')
        if alpha != 1:
            lines.append(f'{indent}    android:fillAlpha="{alpha}"')
    if stroke:
        lines.append(f'{indent}    android:strokeColor="{stroke}"')
        lines.append(f'{indent}    android:strokeWidth="{sw}"')
        lines.append(f'{indent}    android:strokeLineCap="round"')
    lines[-1] += " />"
    return "\n".join(lines)


HEADER = ('<?xml version="1.0" encoding="utf-8"?>\n'
          '<!-- Generated from art/isha.svg. isha\'s mascot: a chibi girl with space buns. -->\n')


def vector(body, w=108, h=108, vw=108, vh=108, aapt=False):
    ns = 'xmlns:android="http://schemas.android.com/apk/res/android"'
    if aapt:
        ns += '\n    xmlns:aapt="http://schemas.android.com/aapt"'
    return (HEADER + f'<vector {ns}\n    android:width="{w}dp"\n    android:height="{h}dp"\n'
            f'    android:viewportWidth="{vw}"\n    android:viewportHeight="{vh}">\n{body}\n</vector>\n')


GRADIENT_BG = '''    <path android:pathData="M0,0h108v108h-108Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108">
                <item android:offset="0" android:color="#FFFFB078" />
                <item android:offset="1" android:color="#FFFF6D1F" />
            </gradient>
        </aapt:attr>
    </path>
''' + vd_path(circle(54, 50, 30), "#FFFFFF", 0.18, None, 0)


def mono_shapes():
    """Single-colour silhouette for themed icons / status bar: hair + features."""
    face = ellipse(FACE_CX, FACE_CY, 18.6, 17.2)
    W = "#FFFFFFFF"
    by_index = lambda i: SHAPES[i][0]
    out = [(by_index(2), W, 1, None, 0), (by_index(3), W, 1, None, 0)]
    # back hair with the face punched out (evenOdd)
    out.append((by_index(6) + face, W, 1, None, 0, "evenOdd"))
    out.append((by_index(13), W, 1, None, 0))  # bangs
    out += [(by_index(i), W, 1, None, 0) for i in (15, 16)]  # eyes
    out.append((by_index(23), None, 1, W, 1.8))  # smile
    return out


def vd_mono(shapes, indent="    "):
    parts = []
    for s in shapes:
        d, fill, alpha, stroke, sw = s[:5]
        p = vd_path(d, fill, alpha, stroke, sw, indent)
        if len(s) > 5:
            p = p.replace(" />", f'\n{indent}    android:fillType="{s[5]}" />', 1)
        parts.append(p)
    return "\n".join(parts)


if __name__ == "__main__":
    out_dir = sys.argv[1]
    res = sys.argv[2] if len(sys.argv) > 2 else None
    open(os.path.join(out_dir, "isha.svg"), "w").write(svg(SHAPES, 512) + "\n")
    open(os.path.join(out_dir, "isha_round.svg"), "w").write(svg(SHAPES, 512, round_clip=True) + "\n")
    if res:
        fg = "\n".join(vd_path(*s) for s in SHAPES)
        open(os.path.join(res, "ic_launcher_foreground.xml"), "w").write(vector(fg))
        open(os.path.join(res, "ic_launcher_background.xml"), "w").write(vector(GRADIENT_BG, aapt=True))
        open(os.path.join(res, "ic_launcher_monochrome.xml"), "w").write(vector(vd_mono(mono_shapes())))
        # In-app avatar: the round icon cropped to the 72-unit visible circle.
        avatar = ('    <group android:translateX="-18" android:translateY="-18">\n'
                  f'        <clip-path android:pathData="{circle(54, 54, 36)}" />\n'
                  + GRADIENT_BG.replace("\n    ", "\n        ").replace("    <path", "        <path", 1) + "\n"
                  + "\n".join(vd_path(*s, indent="        ") for s in SHAPES)
                  + '\n    </group>')
        open(os.path.join(res, "isha_avatar.xml"), "w").write(vector(avatar, 72, 72, 72, 72, aapt=True))
        # Status-bar icon: the silhouette, scaled from the central 72 units.
        notif = ('    <group android:scaleX="0.3333" android:scaleY="0.3333" android:translateX="-6" android:translateY="-6.5">\n'
                 + vd_mono(mono_shapes(), indent="        ") + '\n    </group>')
        open(os.path.join(res, "ic_notification.xml"), "w").write(vector(notif, 24, 24, 24, 24))
