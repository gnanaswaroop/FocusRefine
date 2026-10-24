"""Turn authored items into full dataset items: python -m ml.data.assemble <authored.jsonl> <out.jsonl> --lang xx

An author writes only: split, region, package, usecase, sender_type, difficulty, title, text, template_family.
This fills in the id, app name and category (from the catalogue), the label (from the rubric) and provenance,
then validates the result. Items that fail are written to <out>.rejected.jsonl with the reasons.
"""
import argparse
import json

from ml.data import privacy_guard, schema

AUTHOR_FIELDS = ("split", "region", "package", "usecase", "sender_type", "difficulty", "title", "text", "template_family")


def assemble(authored, lang, provenance, rubric, catalogue, start=1):
    good, bad = [], []
    for n, raw in enumerate(authored, start):
        missing = [f for f in AUTHOR_FIELDS if f not in raw]
        if missing:
            bad.append({"item": raw, "errors": [f"missing author fields {missing}"]})
            continue
        app = catalogue.get(raw["package"], {})
        usecase = rubric["by_id"].get(raw["usecase"])
        item = {
            "id": f"{lang}-{raw['split']}-{n:05d}", "split": raw["split"], "lang": lang, "region": raw["region"],
            "package": raw["package"], "app_name": app.get("name", ""), "app_category": app.get("category", ""),
            "usecase": raw["usecase"], "sender_type": raw["sender_type"], "title": raw["title"].strip(),
            "text": raw["text"].strip(),
            "urgentIn": schema.expected_label(raw["usecase"], rubric) if usecase else [],
            "difficulty": raw["difficulty"], "template_family": raw["template_family"], "provenance": provenance,
        }
        errors = schema.validate_item(item, rubric, catalogue) + privacy_guard.check_item(item)
        if raw["region"] not in schema.LANGS[lang][2]:
            errors.append(f"region {raw['region']} not a home region of {lang}")
        (bad if errors else good).append({"item": item, "errors": errors} if errors else item)
    return good, bad


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("authored")
    ap.add_argument("out")
    ap.add_argument("--lang", required=True, choices=sorted(schema.LANGS))
    ap.add_argument("--provenance", default="authored-pilot-v1")
    args = ap.parse_args()
    rubric, catalogue = schema.load_rubric(), schema.load_catalogue()
    authored = schema.load_jsonl(args.authored)
    good, bad = assemble(authored, args.lang, args.provenance, rubric, catalogue)
    schema.dump_jsonl(good, args.out)
    with open(args.out + ".rejected.jsonl", "w", encoding="utf-8", newline="\n") as f:
        for b in bad:
            f.write(json.dumps(b, ensure_ascii=False) + "\n")
    print(f"{args.lang}: {len(good)} accepted, {len(bad)} rejected")
    for b in bad[:8]:
        print("  rejected:", b["errors"][:2], "|", b["item"].get("title", "")[:40] if isinstance(b["item"], dict) else "")


if __name__ == "__main__":
    main()
