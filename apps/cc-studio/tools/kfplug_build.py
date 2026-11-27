#!/usr/bin/env python3
"""Validate a Keyflow plugin folder and zip it into a .kfplug (plugin api 1).
Usage: python3 kfplug_build.py <pack_folder> [output.kfplug]
Folder: manifest.json, effects/<id>/effect.json + .frag files, optional icon.png, LICENSE.txt.
Exit 1 on any ERROR (nothing written)."""
import json, re, sys, zipfile
from pathlib import Path

CATS = {"Blur","Warna","Stilisasi","Distorsi","Gerak","Generator","Keying","Transisi"}
PTYPES = {"scalar","bool","choice"}
OKEXT = {".json",".frag",".png",".txt"}
errs, warns = [], []
E = errs.append; W = warns.append
def uname(i): return "u" + i[0].upper() + i[1:]
BUILTIN = {"uTexture":"sampler2D","uInput":"sampler2D","uOriginal":"sampler2D","uPrev":"sampler2D",
           "uTexelSize":"vec2","uSrcTexelSize":"vec2","uTime":"float","uLayerDuration":"float","uPyramidScale":"float"}
RESERVED = {"uEffectBackdrop","uLut","uSpectrum","uMap","uLayerToLocal","uLocalToUv"}

def jload(p, tag):
    def nodup(pairs):
        d = {}
        for k, v in pairs:
            if k in d: E(f"{tag}: duplicate key '{k}'")
            d[k] = v
        return d
    try: return json.loads(p.read_text(encoding="utf-8"), object_pairs_hook=nodup)
    except Exception as ex: E(f"{tag}: invalid JSON ({ex}); no comments/trailing commas allowed"); return None

def shader(tag, src, pids):
    if len(src.encode()) > 32768: E(f"{tag}: over 32 KB")
    if re.search(r"#\s*(version|extension)", src): E(f"{tag}: #version/#extension not allowed")
    if "gl_FragColor" not in src: E(f"{tag}: never writes gl_FragColor")
    if re.search(r"\b(while|do)\b\s*[({]", src): E(f"{tag}: while/do loops are refused")
    if re.search(r"\btexture\s*\(", src): E(f"{tag}: use texture2D()")
    for m in re.finditer(r"\bfor\s*\(([^;]*);([^;]*);", src):
        mm = re.search(r"(\d+)\s*$", m.group(2).strip())
        if not mm: E(f"{tag}: for bound must be a constant: {m.group(2).strip()}")
        elif int(mm.group(1)) > 256: E(f"{tag}: loop over 256 iterations")
    code = re.sub(r"//[^\n]*|/\*.*?\*/", "", src, flags=re.S)
    if code.count("{") != code.count("}") or code.count("(") != code.count(")"): E(f"{tag}: unbalanced braces/parens")
    if "sin(" in code and "1e-4" not in code and "max(" not in code: W(f"{tag}: no max() guard visible for divisions")
    for typ, n in re.findall(r"uniform\s+(\w+)\s+(u\w+)\s*;", src):
        if n in RESERVED: E(f"{tag}: {n} is reserved for a later api")
        elif n in BUILTIN:
            if BUILTIN[n] != typ: E(f"{tag}: {n} must be {BUILTIN[n]}")
        elif n in pids:
            if typ != "float": E(f"{tag}: {n} must be float")
        else: W(f"{tag}: {n} has no matching param (value will be 0)")
    return set(re.findall(r"uniform\s+\w+\s+(u\w+)\s*;", src))

def effect(root, d, manifest_ids):
    eid = d.name
    m = jload(d/"effect.json", f"{eid}/effect.json") if (d/"effect.json").exists() else (E(f"{eid}: effect.json missing") or None)
    if m is None: return
    if m.get("id") != eid: E(f"{eid}: id '{m.get('id')}' must equal folder name")
    if not re.fullmatch(r"[a-z0-9_-]{1,32}", str(m.get("id",""))): E(f"{eid}: id must be [a-z0-9_-], max 32")
    if not m.get("name"): E(f"{eid}: name missing")
    if m.get("category") not in CATS: E(f"{eid}: category must be one of {sorted(CATS)}")
    params = m.get("params", [])
    if len(params) > 40: E(f"{eid}: more than 40 params")
    pm = {}
    for p in params:
        i = p.get("id", ""); pm[i] = p
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9]{0,31}", i): E(f"{eid}: bad param id '{i}'")
        if not p.get("label"): E(f"{eid}: param '{i}' needs a label")
        t = p.get("type", "scalar")
        if t not in PTYPES: E(f"{eid}: param '{i}' type must be scalar/bool/choice"); continue
        if t == "scalar":
            if not all(isinstance(p.get(k), (int,float)) and not isinstance(p.get(k), bool) for k in ("default","min","max")):
                E(f"{eid}: scalar '{i}' needs numeric default/min/max")
            elif not (p["min"] < p["max"] and p["min"] <= p["default"] <= p["max"]): E(f"{eid}: scalar '{i}' default outside min..max (or min>=max)")
            if "displayScale" in p and not p["displayScale"] > 0: E(f"{eid}: '{i}' displayScale must be > 0")
        elif t == "bool":
            if p.get("default") not in (0, 1): E(f"{eid}: bool '{i}' default must be 0 or 1")
        else:
            o = p.get("options")
            if not (isinstance(o, list) and len(o) >= 2): E(f"{eid}: choice '{i}' needs 2+ options")
            elif not (isinstance(p.get("default"), int) and 0 <= p["default"] < len(o)): E(f"{eid}: choice '{i}' default must be an index")
    if len(pm) != len(params): E(f"{eid}: duplicate param ids")
    def has(n, kinds=None): return n in pm and (kinds is None or pm[n].get("type","scalar") in kinds)
    for c in m.get("colors", []):
        for k in "rgb":
            if not has(c.get(k), {"scalar"}): E(f"{eid}: colors.{k} must name a scalar param")
        if "enabled" in c and not has(c["enabled"], {"bool"}): E(f"{eid}: colors.enabled must name a bool param")
    for c in m.get("points", []):
        for k in ("x","y"):
            if not has(c.get(k), {"scalar"}): E(f"{eid}: points.{k} must name a scalar param")
    for pr in m.get("presets", []):
        for k, v in pr.get("values", {}).items():
            if k not in pm: E(f"{eid}: preset '{pr.get('id')}' uses unknown param '{k}'")
            elif pm[k].get("type","scalar") == "scalar" and not (pm[k]["min"] <= v <= pm[k]["max"]): E(f"{eid}: preset '{pr.get('id')}' {k} outside range")
    for key in ("newInstanceValues","thumbnailValues"):
        for k in m.get(key, {}):
            if k not in pm: E(f"{eid}: {key} uses unknown param '{k}'")
    for p in params:
        for k in ("hiddenWhenOn","shownForChoice"):
            if k in p and p[k] not in pm: E(f"{eid}: '{p['id']}' {k} names unknown param")
    if "thumbnailStyle" in m and m["thumbnailStyle"] not in ("full","split"): E(f"{eid}: thumbnailStyle must be full or split")
    if "thumbnailInset" in m and not (0 <= m["thumbnailInset"] < 0.5): E(f"{eid}: thumbnailInset must be 0..<0.5")
    ps = m.get("passes", [])
    if not (1 <= len(ps) <= 8): E(f"{eid}: needs 1 to 8 passes"); return
    used, files = set(), []
    for n, p in enumerate(ps):
        f = p.get("shader")
        if not f or not (d/f).exists(): E(f"{eid}: pass {n} shader '{f}' missing"); continue
        files.append(f)
        used |= shader(f"{eid}/{f}", (d/f).read_text(encoding="utf-8"), {uname(i) for i in pm})
        if "prevPass" in p and not (isinstance(p["prevPass"], int) and 0 <= p["prevPass"] < n): E(f"{eid}: pass {n} prevPass must be an earlier pass index")
        if "scale" in p and not (0 < p["scale"] <= 1): E(f"{eid}: pass {n} scale must be 1, 1/2 ... 1/256")
        if "requiredParam" in p and p["requiredParam"] not in pm: E(f"{eid}: pass {n} requiredParam unknown")
    for i in pm:
        if uname(i) not in used: W(f"{eid}: param '{i}' is never declared in any shader (slider will do nothing)")
    return files

def main():
    if len(sys.argv) < 2: sys.exit(__doc__)
    root = Path(sys.argv[1]).resolve()
    if not (root/"manifest.json").exists(): sys.exit("ERROR: manifest.json must be at the pack root")
    mf = jload(root/"manifest.json", "manifest.json") or {}
    if mf.get("format") != 1 or mf.get("api") != 1: E("manifest: format and api must be 1")
    if not re.fullmatch(r"[a-z0-9_-]+(\.[a-z0-9_-]+){1,4}", str(mf.get("id",""))): E("manifest: id needs 2-5 dot parts of [a-z0-9_-]")
    for k in ("name","version"):
        if not mf.get(k): E(f"manifest: {k} must not be empty")
    if not (isinstance(mf.get("versionCode"), int) and mf["versionCode"] >= 1): E("manifest: versionCode must be a whole number >= 1")
    for k in ("author","description","license"):
        if k not in mf: W(f"manifest: {k} missing (shown on the pack card)")
    ed = {p.name: p for p in (root/"effects").glob("*") if p.is_dir()}
    listed = mf.get("effects", [])
    for n in listed:
        if n not in ed: E(f"manifest lists '{n}' but effects/{n}/ is missing")
    for n in ed:
        if n not in listed: W(f"effects/{n}/ is not listed in manifest (ignored)")
    allf = [p for p in root.rglob("*") if p.is_file()]
    for p in allf:
        r = p.relative_to(root).as_posix()
        if p.suffix.lower() not in OKEXT or p.name in (".DS_Store","Thumbs.db"): E(f"{r}: file type not allowed")
        if not r.isascii() or ":" in r or "\\" in r: E(f"{r}: entry name must be plain ASCII, no ':' or '\\'")
    if len(allf) > 64: E("more than 64 entries")
    if sum(p.stat().st_size for p in allf) > 8*1024*1024: E("over 8 MB extracted")
    packed = {}
    for n in listed:
        if n in ed: packed[n] = effect(root, ed[n], listed)
    for w in warns: print("WARN ", w)
    for e in errs: print("ERROR", e)
    if errs: sys.exit(f"\n{len(errs)} error(s). Pack NOT written.")
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else root.parent / f"{mf['id']}.kfplug"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(root/"manifest.json", "manifest.json")
        for n in ("icon.png","LICENSE.txt"):
            if (root/n).exists(): z.write(root/n, n)
        for n in listed:
            z.write(ed[n]/"effect.json", f"effects/{n}/effect.json")
            for f in dict.fromkeys(packed[n] or []): z.write(ed[n]/f, f"effects/{n}/{f}")
    if out.stat().st_size > 2*1024*1024: sys.exit("ERROR: zipped pack over 2 MB")
    with zipfile.ZipFile(out) as z:
        assert z.testzip() is None; print("\nOK. Wrote", out); [print("  ", x) for x in z.namelist()]
if __name__ == "__main__": main()
