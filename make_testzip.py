# 生成一个测试用 ZIP 网页包:index.html + style.css + img/logo.png,验本地相对资源加载。
import os, zipfile
from PIL import Image, ImageDraw

OUT = r"E:\Tang_Project\JiYue\app\build\outputs\apk\debug\test-site.zip"

index_html = """<!DOCTYPE html>
<html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="style.css">
<title>Folio ZIP 测试</title></head>
<body>
<h1>ZIP 网页包测试</h1>
<p class="tag">如果你能看到下面的<b>样式</b>和<b>图片</b>,说明包内本地资源加载成功。</p>
<img src="img/logo.png" alt="logo" class="logo">
<div class="card">
  <p>这段文字有圆角卡片背景(来自 style.css)。</p>
  <p>子目录 img/logo.png 是包内图片(蓝色方块)。</p>
</div>
<p>跳转测试:<a href="page2.html">前往第二页 &raquo;</a></p>
</body></html>
"""

page2_html = """<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
<link rel="stylesheet" href="style.css"><title>第二页</title></head>
<body><h1>第二页</h1><p>包内多页相对跳转也正常。</p>
<p><a href="index.html">&laquo; 返回首页</a></p></body></html>
"""

style_css = """body{font-family:sans-serif;background:#F5F4EF;color:#22272E;padding:20px;line-height:1.6;}
h1{border-bottom:2px solid #22272E;padding-bottom:8px;}
.tag{background:#D6E4F0;color:#1F5C99;padding:2px 8px;border-radius:6px;}
.card{background:#fff;border-radius:12px;padding:16px;margin:16px 0;box-shadow:0 1px 4px rgba(0,0,0,.08);}
.logo{width:96px;height:96px;display:block;margin:16px 0;border-radius:12px;}
a{color:#1F5C99;}
"""

# 生成 logo.png(蓝色圆角方块 + 白字 F)
img = Image.new("RGBA", (192, 192), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rounded_rectangle([0, 0, 191, 191], radius=32, fill=(0x1F, 0x5C, 0x99, 255))
d.ellipse([66, 66, 126, 126], fill=(255, 255, 255, 255))
tmp_png = r"E:\Tang_Project\JiYue\_logo_tmp.png"
img.save(tmp_png)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("index.html", index_html)
    z.writestr("page2.html", page2_html)
    z.writestr("style.css", style_css)
    z.write(tmp_png, "img/logo.png")
os.remove(tmp_png)
print("wrote", OUT)
