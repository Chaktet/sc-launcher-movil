#!/bin/bash
# Publishes an SC Launcher (Android) version from a finished CI run (signed release APKs):
#   bash publica-app.sh <run id> <versionName> <versionCode> "<notas para jugadores>"
# APKs go to OVH-A /var/www/packs/movil/app/ with versioned names, then sc-app.json (last, so no app is offered a
# file that is not there yet), then tag + GitHub Release. Everything is re-downloaded through Cloudflare and checked.
set -euo pipefail
RUN=$1; V=$2; CODE=$3; NOTAS=$4
K=/c/Users/Chaktet/.ssh/Hetzner/id_ed25519
OVH="ssh -i /root/.ssh/cobblemon-backup-key ubuntu@51.79.83.226"
W=E:/tmp/app-android/rel-$V
rm -rf "$W"; mkdir -p "$W/pub"; cd "$W"
SHA=$(gh run view "$RUN" --repo Chaktet/sc-launcher-movil --json headSha --jq .headSha)
for a in arm64 arm x86_64; do gh run download "$RUN" --repo Chaktet/sc-launcher-movil -n "apk-$a" -D "$a" >/dev/null; done
cp arm64/release/*-arm64-v8a.apk "pub/SC-Launcher-$V-arm64-v8a.apk"
cp arm/release/*-armeabi-v7a.apk "pub/SC-Launcher-$V-armeabi-v7a.apk"
cp x86_64/release/*-x86_64.apk "pub/SC-Launcher-$V-x86_64.apk"
cd pub
V="$V" CODE="$CODE" NOTAS="$NOTAS" node -e '
const fs=require("fs"),c=require("crypto"),V=process.env.V;const apks={};
for(const abi of ["arm64-v8a","armeabi-v7a","x86_64"]){const f=`SC-Launcher-${V}-${abi}.apk`;const b=fs.readFileSync(f);apks[abi]={url:"https://descargas.servidorcobblemon.es/movil/app/"+f,sha256:c.createHash("sha256").update(b).digest("hex"),size:b.length}}
fs.writeFileSync("sc-app.json",JSON.stringify({versionCode:Number(process.env.CODE),versionName:V,notas:process.env.NOTAS,apks}))'
ssh -i $K root@135.181.51.104 "mkdir -p /root/app-$V"
scp -q -i $K SC-Launcher-$V-*.apk sc-app.json root@135.181.51.104:/root/app-$V/
ssh -i $K root@135.181.51.104 "cd /root/app-$V && scp -q -i /root/.ssh/cobblemon-backup-key *.apk sc-app.json ubuntu@51.79.83.226:/tmp/ && $OVH 'D=/var/www/packs/movil/app; for f in /tmp/SC-Launcher-$V-*.apk; do sudo cp \$f \$D/; sudo chown --reference=\$D/sc-app.json \$D/\$(basename \$f); sudo chmod 644 \$D/\$(basename \$f); rm -f \$f; done; sudo cp -p \$D/sc-app.json \$D/sc-app.json.bak-antes-$V; sudo cp /tmp/sc-app.json \$D/sc-app.json.nuevo; sudo chown --reference=\$D/sc-app.json \$D/sc-app.json.nuevo; sudo chmod 644 \$D/sc-app.json.nuevo; sudo mv -f \$D/sc-app.json.nuevo \$D/sc-app.json; rm -f /tmp/sc-app.json'; rm -rf /root/app-$V"
# check through Cloudflare
mkdir -p cf
curl -s "https://descargas.servidorcobblemon.es/movil/app/sc-app.json?x=$RANDOM" > cf/sc-app.json
for a in arm64-v8a armeabi-v7a x86_64; do curl -s -o "cf/$a.apk" "https://descargas.servidorcobblemon.es/movil/app/SC-Launcher-$V-$a.apk"; done
node -e '
const fs=require("fs"),c=require("crypto");const m=JSON.parse(fs.readFileSync("cf/sc-app.json"));let mal=0
for(const [abi,e] of Object.entries(m.apks)){const b=fs.readFileSync("cf/"+abi+".apk");const ok=b.length===e.size&&c.createHash("sha256").update(b).digest("hex")===e.sha256;if(!ok)mal++;console.log(abi,b.length,ok?"OK":"MAL")}
console.log("manifiesto en la web:",m.versionName,m.versionCode,mal?"FALLOS "+mal:"· los 3 coinciden");if(mal)process.exit(1)'
rm -f cf/*.apk
cd E:/PROYECTOS-MC/sc-launcher-movil
git tag "v$V" "$SHA" && git push -q origin "v$V"
gh release create "v$V" --repo Chaktet/sc-launcher-movil --title "SC Launcher $V" --notes "$NOTAS (Mismo código que la etiqueta v$V.)" "$W"/pub/*.apk | tail -1
R=$(gh run list --repo Chaktet/sc-launcher-movil --branch "v$V" --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || true)
[ -n "$R" ] && gh run cancel "$R" --repo Chaktet/sc-launcher-movil >/dev/null 2>&1 || true
curl -sI https://servidorcobblemon.es/descargas/android | grep -i "^location"
