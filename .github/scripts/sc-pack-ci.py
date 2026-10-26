#!/usr/bin/env python3
"""CI only: builds a test update of the real mobile modpack (version <real>-ci, with one extra override file) and its
sc-pack.json, served to the emulator from the runner (http://10.0.2.2:8000), to check that a newly published pack is
applied by itself and keeps the player's options.txt."""
import hashlib, json, sys, urllib.request, zipfile, os, shutil

# Cloudflare refuses the default Python user agent (403)
UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14) SCLauncherCI"}
get = lambda url: urllib.request.urlopen(urllib.request.Request(url, headers=UA))

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
real = json.load(get("https://descargas.servidorcobblemon.es/movil/sc-pack.json"))
src = os.path.join(out, "real.mrpack")
with get(real["url"]) as r, open(src, "wb") as f:
    shutil.copyfileobj(r, f)
version = real["version"] + "-ci"
dst = os.path.join(out, f"SC-Movil-{version}.mrpack")
with zipfile.ZipFile(src) as zi, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zo:
    for item in zi.infolist():
        data = zi.read(item.filename)
        if item.filename == "modrinth.index.json":
            j = json.loads(data)
            j["versionId"] = version
            data = json.dumps(j, indent=2).encode()
        zo.writestr(item, data)
    zo.writestr("overrides/config/sc-ci-marker.txt", "sc ci update " + version + "\n")
data = open(dst, "rb").read()
json.dump({"version": version, "url": f"http://10.0.2.2:8000/SC-Movil-{version}.mrpack",
           "sha256": hashlib.sha256(data).hexdigest(), "size": len(data)}, open(os.path.join(out, "sc-pack.json"), "w"))
os.remove(src)
print("test pack", version, len(data), "bytes; real", real["version"])
