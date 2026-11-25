"""Turn the web atlas (artifact HTML) into the offline page bundled in the Android app.

Usage: python3 build_app_page.py <atlas.html> <android/app/src/main/assets/index.html>

It swaps CDN fonts and d3 for the bundled copies, adds a full HTML document shell,
adds the Android bridge (native Share), loads firebase-config.js so sign-in
and sync can be turned on, and adds auto-update: a build id in the page and in
version.json, which the website and the app use to pick up new versions.
"""
import re
import sys

src, dst = sys.argv[1], sys.argv[2]
s = open(src, encoding="utf-8").read()


def rep(a, b):
    global s
    if a not in s:
        sys.exit(f"pattern not found: {a[:70]!r}")
    s = s.replace(a, b, 1)


s = re.sub(r'<meta name="claude:viewport-fit-sides"[^>]*>\n', "", s)
s = re.sub(r'<link rel="preconnect"[^>]*>\n', "", s)
s = re.sub(r'<link rel="stylesheet" href="https://fonts.googleapis.com[^>]*>\n', "", s)
s = ('<!doctype html>\n<html lang="en">\n<head>\n<meta charset="utf-8">\n'
     '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n') + s

faces = [("Instrument Sans", "instrument-sans-latin", [400, 500, 600], None),
         ("Bricolage Grotesque", "bricolage-grotesque-latin", [500, 700], None),
         ("JetBrains Mono", "jetbrains-mono-greek", [400, 500], "U+0370-03FF"),
         ("JetBrains Mono", "jetbrains-mono-latin", [400, 500], None)]
ff = ""
for fam, base, weights, urange in faces:
    for w in weights:
        ur = f"unicode-range:{urange};" if urange else ""
        ff += (f'@font-face{{font-family:"{fam}";font-style:normal;font-weight:{w};font-display:swap;'
               f'src:url("fonts/{base}-{w}-normal.woff2") format("woff2");{ur}}}\n')
reset = ("html,body{margin:0}body{font-size:14px;-webkit-tap-highlight-color:transparent}"
         "img{max-width:100%}[hidden]{display:none!important}\n")
rep("<style>\n", "<style>\n" + ff + reset)
rep("</style>\n", "</style>\n</head>\n<body>\n")
rep('<script src="https://cdnjs.cloudflare.com/ajax/libs/d3/7.9.0/d3.min.js"></script>',
    '<script src="d3.min.js"></script>\n<script src="firebase-config.js"></script>')
rep('<button id="mUnlocks">Show what it unlocks</button>',
    '<button id="mUnlocks">Show what it unlocks</button>\n'
    '      ${window.AtlasNative?`<button id="mShare">Share</button>`:""}')
rep('  if(ev.target.closest("#mClose")) return closeModal();',
    '  if(ev.target.closest("#mClose")) return closeModal();\n'
    '  if(ev.target.closest("#mShare")){ shareTopic(modalId); return; }')
rep("/* ---------- Boot ---------- */", r'''/* ---------- Android bridge ---------- */
function shareTopic(id){
  const n=byId.get(id); if(!n||!window.AtlasNative) return;
  const r=resourcesFor(n); const subs=(SYL[id]||"").split(";").map(x=>x.trim()).filter(Boolean);
  const k=kissFor(n);
  const text=[`${n.label} (Level ${n.lv}, ${dName[n.d]})`,n.desc,"",
    "K.I.S.S. ladder:",...KISS_STAGES.map((st,i)=>`${st.name}: ${k[i]}`),"",
    "Topics to cover:",...subs.map((x,i)=>`${i+1}. ${x}`),"",
    "Resources:",...[...r.learn,...r.papers].map(x=>`• ${x.t}: ${x.u}`),
    ...(getMyRes(id).length ? ["","My resources:",...getMyRes(id).map(x=>`• ${myResTitle(x)}: ${x.u}`)] : [])].join("\n");
  window.AtlasNative.share(n.label, text);
}
/* ---------- Auto-update ----------
   The website checks version.json and reloads itself when a newer build is published.
   The Android app downloads newer builds itself and calls window.atlasUpdateReady(). */
(function(){
  const BUILD = window.ATLAS_BUILD; if(!BUILD) return;
  let pending = false;
  const busy = () => { const m=document.getElementById("modal"), a=document.activeElement;
    return (m && !m.hidden) || (a && /^(INPUT|TEXTAREA|SELECT)$/.test(a.tagName)); };
  function apply(){ if(!pending) return; if(busy()){ setTimeout(apply, 4000); return; } location.reload(); }
  window.atlasUpdateReady = () => { pending = true; apply(); };
  if(!/\.github\.io$/.test(location.hostname)) return;
  async function check(){
    try{
      const r = await fetch("version.json?t=" + Date.now(), {cache:"no-store"}); if(!r.ok) return;
      const v = await r.json(); if(!v.build || v.build === BUILD) return;
      // Reload at most once per new build, in case the CDN still serves the old page for a few minutes.
      let tried = null; try{ tried = sessionStorage.getItem("atlas-update-tried"); }catch(e){}
      if(tried === v.build) return;
      try{ sessionStorage.setItem("atlas-update-tried", v.build); }catch(e){}
      await fetch(location.pathname, {cache:"reload"}).catch(()=>{});
      window.atlasUpdateReady();
    }catch(e){}
  }
  setTimeout(check, 3000); setInterval(check, 15*60*1000);
  document.addEventListener("visibilitychange", () => { if(!document.hidden) check(); });
})();
/* ---------- Boot ---------- */''')
s = s.rstrip() + "\n</body>\n</html>\n"

# Build id: a hash of the page, written into the page and into version.json beside it.
import hashlib, json, os
build = hashlib.sha256(s.encode("utf-8")).hexdigest()[:12]
s = s.replace("<head>\n", f'<head>\n<script>window.ATLAS_BUILD="{build}";</script>\n', 1)
open(dst, "w", encoding="utf-8").write(s)
open(os.path.join(os.path.dirname(dst), "version.json"), "w").write(json.dumps({"build": build}) + "\n")
print("wrote", dst, len(s), "bytes, build", build)
