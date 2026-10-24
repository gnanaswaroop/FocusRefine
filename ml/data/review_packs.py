"""Build the review packs for the human review gate (S4).

python -m ml.data.review_packs [--items testdata/golden_v2/pilot_agreed] [--out ml/review]

For every language: <out>/<lang>.html (self-contained, no network, filterable, decisions saved in the browser and
exportable as CSV) and <out>/<lang>.csv (the same rows). Also <out>/index.html linking all languages.
Rows show the app, sender, text, an English gloss for non-English rows, the label per mode, the rubric reason, and
whether the second labeler agreed. Contested items are included with both answers. ml/review/ is git-ignored.
"""
import argparse
import csv
import glob
import html
import json
import os

from ml.data import schema

ROOT = os.path.join("testdata", "golden_v2")


def load_dir(path):
    items = []
    for p in sorted(glob.glob(os.path.join(path, "*.jsonl"))):
        if p.endswith(".rejected.jsonl"):
            continue
        items.extend(schema.load_jsonl(p))
    return items


def rubric_reason(usecase_id, rubric):
    u = rubric["by_id"][usecase_id]
    label = ", ".join(schema.expected_label(usecase_id, rubric)) or "no mode (muted everywhere)"
    return f"{usecase_id} ({u['family']}): {u['desc']}. Shown in: {label}."


def build_rows(items_dir, contested_path, gloss_dir, blind_dir, rubric):
    gloss = {}
    for p in glob.glob(os.path.join(gloss_dir, "*.jsonl")):
        for g in schema.load_jsonl(p):
            gloss[g["id"]] = g
    blind = {}
    for p in glob.glob(os.path.join(blind_dir, "*.jsonl")):
        for b in schema.load_jsonl(p):
            blind[b["id"]] = b
    rows = []

    def row(item, status, blind_usecase="", blind_note=""):
        g = gloss.get(item["id"], {})
        b = blind_usecase or blind.get(item["id"], {}).get("usecase", "")
        return {
            "id": item["id"], "split": item["split"], "lang": item["lang"], "region": item["region"],
            "package": item["package"], "app_name": item["app_name"], "app_category": item["app_category"],
            "usecase": item["usecase"], "family": rubric["by_id"][item["usecase"]]["family"],
            "sender_type": item["sender_type"], "difficulty": item["difficulty"],
            "title": item["title"], "text": item["text"],
            "gloss_title": g.get("gloss_title", ""), "gloss_text": g.get("gloss_text", ""),
            "modes": [m in item["urgentIn"] for m in rubric["modes"].values()],
            "label": ", ".join(m.split(" (")[0] for m in item["urgentIn"]) or "Muted everywhere",
            "reason": rubric_reason(item["usecase"], rubric),
            "status": status, "blind_usecase": b, "blind_note": blind_note,
            "adjudicated": item["provenance"].endswith("adjudicated"),
        }

    for item in load_dir(items_dir):
        rows.append(row(item, "adjudicated by rubric" if item["provenance"].endswith("adjudicated") else "agreed"))
    if os.path.exists(contested_path):
        for c in schema.load_jsonl(contested_path):
            rows.append(row(c["item"], "CONTESTED", c.get("blind_usecase", ""), c.get("blind_note", "")))
    return rows


PAGE = """<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Review __LANG__</title><style>
:root{--bg:#0f0e17;--card:#201d33;--line:#352f52;--tx:#fff;--mut:#9ca3af;--ok:#22c55e;--no:#6b7280;--gold:#f59e0b;--vio:#8b5cf6}
body{margin:0;background:var(--bg);color:var(--tx);font:15px/1.45 system-ui,sans-serif}
header{position:sticky;top:0;background:var(--bg);border-bottom:1px solid var(--line);padding:10px 16px;z-index:5}
h1{font-size:18px;margin:0 0 8px}.bar{display:flex;flex-wrap:wrap;gap:8px;align-items:center}
select,input,button,textarea{background:var(--card);color:var(--tx);border:1px solid var(--line);border-radius:8px;padding:6px 8px;font:inherit}
button{cursor:pointer;background:var(--vio);border:0}main{padding:12px 16px;max-width:980px;margin:auto}
.card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:12px;margin:10px 0}
.card.contested{border-color:var(--gold)}.meta{color:var(--mut);font-size:13px}.title{font-weight:700;font-size:16px;margin-top:4px}
.gloss{color:#c7d2fe;margin-top:4px;font-style:italic}.chips{display:flex;flex-wrap:wrap;gap:6px;margin:8px 0}
.chip{padding:2px 9px;border-radius:999px;font-size:12px;border:1px solid var(--line);color:var(--no)}
.chip.on{background:#14532d;color:#4ade80;border-color:#14532d}.why{font-size:13px;color:var(--mut);margin:6px 0}
.row{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-top:8px}.tag{font-size:12px;padding:1px 8px;border-radius:6px;background:#292540}
.tag.gold{background:#452a1a;color:#fbbf24}.count{color:var(--mut);font-size:13px}
</style></head><body><header><h1>Review pack: __LANGNAME__ (__LANG__) <span class="count" id="count"></span></h1>
<div class="bar"><input id="q" placeholder="Search text, app, use case" size="26">
<select id="f_status"><option value="">All statuses</option></select><select id="f_family"><option value="">All use-case families</option></select>
<select id="f_cat"><option value="">All app categories</option></select><select id="f_label"><option value="">Any label</option>
<option value="urgent">Urgent in some mode</option><option value="muted">Muted everywhere</option></select>
<select id="f_diff"><option value="">Any difficulty</option><option>easy</option><option>hard_negative</option><option>near_miss</option></select>
<select id="f_dec"><option value="">Any decision</option><option value="none">Not reviewed</option><option>approve</option><option>fix</option><option>reject</option></select>
<button id="export">Download my decisions (CSV)</button></div></header><main id="list"></main>
<script>
const ROWS=__ROWS__, USECASES=__USECASES__, MODES=__MODES__, KEY="review-__LANG__";
let dec=JSON.parse(localStorage.getItem(KEY)||"{}");
const $=id=>document.getElementById(id), esc=s=>s.replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
function fill(sel,vals){[...new Set(vals)].sort().forEach(v=>{const o=document.createElement("option");o.textContent=v;sel.appendChild(o)})}
fill($("f_status"),ROWS.map(r=>r.status));fill($("f_family"),ROWS.map(r=>r.family));fill($("f_cat"),ROWS.map(r=>r.app_category));
function save(){localStorage.setItem(KEY,JSON.stringify(dec))}
function render(){
 const q=$("q").value.toLowerCase(),s=$("f_status").value,fa=$("f_family").value,c=$("f_cat").value,l=$("f_label").value,d=$("f_diff").value,dc=$("f_dec").value;
 const out=ROWS.filter(r=>(!q||(r.title+" "+r.text+" "+r.gloss_title+" "+r.gloss_text+" "+r.app_name+" "+r.usecase).toLowerCase().includes(q))
  &&(!s||r.status===s)&&(!fa||r.family===fa)&&(!c||r.app_category===c)&&(!d||r.difficulty===d)
  &&(!l||(l==="urgent")===r.modes.some(x=>x))&&(!dc||(dc==="none"?!dec[r.id]||!dec[r.id].d:(dec[r.id]||{}).d===dc)));
 $("count").textContent=out.length+" of "+ROWS.length+" shown, "+Object.values(dec).filter(x=>x.d).length+" decided";
 $("list").innerHTML=out.map(r=>{const x=dec[r.id]||{};
  const chips=MODES.map((m,i)=>`<span class="chip ${r.modes[i]?"on":""}">${esc(m)}: ${r.modes[i]?"SHOWN":"muted"}</span>`).join("");
  const gl=r.gloss_title||r.gloss_text?`<div class="gloss">English: ${esc(r.gloss_title)} | ${esc(r.gloss_text)}</div>`:"";
  const bl=r.status==="CONTESTED"?`<div class="why"><b>Second labeler said:</b> ${esc(r.blind_usecase||"n/a")} ${esc(r.blind_note)}</div>`:"";
  const opts=USECASES.map(u=>`<option ${x.u===u?"selected":""}>${u}</option>`).join("");
  return `<div class="card ${r.status==="CONTESTED"?"contested":""}"><div class="meta">${esc(r.id)} · ${esc(r.app_name)} (${esc(r.package)}) · sender: ${esc(r.sender_type)} · ${esc(r.region)} · <span class="tag ${r.status==="CONTESTED"?"gold":""}">${esc(r.status)}</span> <span class="tag">${esc(r.difficulty)}</span></div>
  <div class="title" dir="auto">${esc(r.title)}</div><div dir="auto">${esc(r.text)}</div>${gl}<div class="chips">${chips}</div>
  <div class="why"><b>Rubric:</b> ${esc(r.reason)}</div>${bl}
  <div class="row"><select data-id="${r.id}" data-k="d"><option value="">Decision…</option><option ${x.d==="approve"?"selected":""}>approve</option><option ${x.d==="fix"?"selected":""}>fix</option><option ${x.d==="reject"?"selected":""}>reject</option></select>
  <select data-id="${r.id}" data-k="u"><option value="">If fix: right use case…</option>${opts}</select>
  <input data-id="${r.id}" data-k="n" placeholder="Note" size="34" value="${esc(x.n||"")}"></div></div>`}).join("");
}
document.addEventListener("input",e=>{const t=e.target;if(t.dataset&&t.dataset.id){(dec[t.dataset.id]=dec[t.dataset.id]||{})[t.dataset.k]=t.value;save();if(t.dataset.k==="d")render()}
 else if(t.id&&t.id.startsWith("f_")||t.id==="q")render()});
$("export").onclick=()=>{const head=["id","decision","right_usecase","note"];
 const lines=[head.join(",")].concat(Object.entries(dec).filter(([k,v])=>v.d||v.n).map(([k,v])=>[k,v.d||"",v.u||"",(v.n||"").replace(/"/g,'""')].map(x=>`"${x}"`).join(",")));
 const a=document.createElement("a");a.href=URL.createObjectURL(new Blob([lines.join("\\n")],{type:"text/csv"}));a.download="decisions___LANG__.csv";a.click()};
render();
</script></body></html>"""


def write_pack(lang, rows, out_dir, rubric):
    name = schema.LANGS[lang][0]
    data = json.dumps(rows, ensure_ascii=False).replace("</", "<\\/")
    page = (PAGE.replace("__ROWS__", data).replace("__LANGNAME__", html.escape(name)).replace("__LANG__", lang)
            .replace("__USECASES__", json.dumps(sorted(rubric["by_id"])))
            .replace("__MODES__", json.dumps([m.split(" (")[0] for m in rubric["modes"].values()])))
    with open(os.path.join(out_dir, f"{lang}.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write(page)
    cols = ["id", "split", "status", "app_name", "package", "sender_type", "usecase", "label", "difficulty",
            "title", "text", "gloss_title", "gloss_text", "reason", "blind_usecase", "decision", "right_usecase", "note"]
    with open(os.path.join(out_dir, f"{lang}.csv"), "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=cols, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow(dict(r, decision="", right_usecase="", note=""))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--items", default=os.path.join(ROOT, "pilot_agreed"))
    ap.add_argument("--contested", default=os.path.join(ROOT, "contested_pilot.jsonl"))
    ap.add_argument("--gloss", default=os.path.join(ROOT, "gloss"))
    ap.add_argument("--blind", default=os.path.join(ROOT, "relabel_out"))
    ap.add_argument("--out", default=os.path.join("ml", "review"))
    args = ap.parse_args()
    rubric = schema.load_rubric()
    rows = build_rows(args.items, args.contested, args.gloss, args.blind, rubric)
    os.makedirs(args.out, exist_ok=True)
    by_lang = {}
    for r in rows:
        by_lang.setdefault(r["lang"], []).append(r)
    index = []
    for lang in sorted(by_lang):
        write_pack(lang, by_lang[lang], args.out, rubric)
        contested = sum(1 for r in by_lang[lang] if r["status"] == "CONTESTED")
        glossed = sum(1 for r in by_lang[lang] if r["gloss_text"] or r["gloss_title"])
        index.append((lang, schema.LANGS[lang][0], len(by_lang[lang]), contested, glossed))
        print(f"{lang}: {len(by_lang[lang])} rows ({contested} contested, {glossed} with English gloss)")
    li = "".join(f'<li><a href="{l}.html">{html.escape(n)} ({l})</a>: {c} items, {k} contested, {g} glossed (<a href="{l}.csv">csv</a>)</li>'
                 for l, n, c, k, g in index)
    with open(os.path.join(args.out, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write(f'<!doctype html><meta charset="utf-8"><title>Review packs</title><body style="font:16px system-ui;background:#0f0e17;color:#fff;padding:24px">'
                f'<h1>FocusRefine golden_v2: review packs</h1><p>Open a language, judge each row, mark approve / fix / reject, then download your decisions.</p><ul>{li}</ul></body>')
    print(f"index: {os.path.join(args.out, 'index.html')}")


if __name__ == "__main__":
    main()
