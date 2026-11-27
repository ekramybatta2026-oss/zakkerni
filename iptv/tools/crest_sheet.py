"""Downloads every crest listed in Teams.kt and prints a labelled contact sheet (base64 JPEG)
so the club <-> crest mapping can be reviewed from the CI log."""
import base64, io, re, sys, urllib.request
from PIL import Image, ImageDraw

src = open(sys.argv[1], encoding="utf-8").read()
teams = re.findall(r't\((\d+), "([^"]+)"', src)
cell, cols = 110, 7
rows = (len(teams) + cols - 1) // cols
sheet = Image.new("RGB", (cols * cell, rows * (cell + 18)), "white")
draw = ImageDraw.Draw(sheet)
for i, (tid, name) in enumerate(teams):
    x, y = (i % cols) * cell, (i // cols) * (cell + 18)
    url = f"https://media.api-sports.io/football/teams/{tid}.png"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        img = Image.open(io.BytesIO(urllib.request.urlopen(req, timeout=20).read())).convert("RGBA")
        img.thumbnail((cell - 20, cell - 20))
        bg = Image.new("RGBA", img.size, "white"); bg.alpha_composite(img)
        sheet.paste(bg.convert("RGB"), (x + 10, y + 5))
        status = "ok"
    except Exception as e:
        status = f"ERR {e}"
    draw.text((x + 4, y + cell - 8), f"{tid} {name}"[:18], fill="black")
    print(f"CREST {tid} {name}: {status}")
buf = io.BytesIO(); sheet.save(buf, "JPEG", quality=70)
print("SHOT_BEGIN crests.jpg"); print(base64.b64encode(buf.getvalue()).decode()); print("SHOT_END")
