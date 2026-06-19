# 生成 Folio 的「READ」图标:白字 + 深墨底。dev 辅助脚本,非 App 一部分。
import os
from PIL import Image, ImageDraw, ImageFont

BG = (0x22, 0x27, 0x2E, 255)
FG = (255, 255, 255, 255)
FONT_PATH = r"C:\Windows\Fonts\arialbd.ttf"
RES = r"E:\Tang_Project\JiYue\app\src\main\res"
TEXT = "READ"


def fit_font(draw, text, target_w, start):
    size = start
    while size > 8:
        f = ImageFont.truetype(FONT_PATH, size)
        b = draw.textbbox((0, 0), text, font=f)
        if (b[2] - b[0]) <= target_w:
            return f
        size -= 2
    return ImageFont.truetype(FONT_PATH, 8)


def draw_center(img, text, color, width_ratio):
    d = ImageDraw.Draw(img)
    W, H = img.size
    f = fit_font(d, text, int(W * width_ratio), H)
    b = d.textbbox((0, 0), text, font=f)
    tw, th = b[2] - b[0], b[3] - b[1]
    d.text(((W - tw) / 2 - b[0], (H - th) / 2 - b[1]), text, font=f, fill=color)


def rounded(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle([0, 0, size - 1, size - 1],
                                          radius=int(size * 0.18), fill=BG)
    return img


def circle(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(img).ellipse([0, 0, size - 1, size - 1], fill=BG)
    return img


densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for dpi, sz in densities.items():
    folder = os.path.join(RES, f"mipmap-{dpi}")
    os.makedirs(folder, exist_ok=True)
    for old in ("ic_launcher.webp", "ic_launcher_round.webp"):
        p = os.path.join(folder, old)
        if os.path.exists(p):
            os.remove(p)
    sq = rounded(sz); draw_center(sq, TEXT, FG, 0.80)
    sq.save(os.path.join(folder, "ic_launcher.png"))
    ci = circle(sz); draw_center(ci, TEXT, FG, 0.72)
    ci.save(os.path.join(folder, "ic_launcher_round.png"))

# 自适应前景:432 透明,READ 落在中心安全区(~62%)
fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
safe = Image.new("RGBA", (int(432 * 0.62), int(432 * 0.30)), (0, 0, 0, 0))
draw_center(safe, TEXT, FG, 0.98)
fg.paste(safe, ((432 - safe.width) // 2, (432 - safe.height) // 2), safe)
fgdir = os.path.join(RES, "drawable-nodpi")
os.makedirs(fgdir, exist_ok=True)
fg.save(os.path.join(fgdir, "ic_launcher_foreground.png"))

old_fg = os.path.join(RES, "drawable", "ic_launcher_foreground.xml")
if os.path.exists(old_fg):
    os.remove(old_fg)

print("icons generated")
