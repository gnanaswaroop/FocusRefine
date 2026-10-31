"""Show an author what they may write for one language: python -m ml.data.options --lang hi

For every use case: the label (modes where it is urgent), allowed sender types, and the apps that fit the
language's home regions. Authors pick a use case, one of its apps and a sender type, then write the notification.
"""
import argparse

from ml.data import schema


def apps_for_language(lang, catalogue):
    regions = set(schema.LANGS[lang][2])
    out = {}
    for app in catalogue.values():
        if "GLOBAL" in app["regions"] or regions & set(app["regions"]):
            out.setdefault(app["category"], []).append(app)
    return out


def main(lang):
    rubric = schema.load_rubric()
    catalogue = schema.load_catalogue()
    by_cat = apps_for_language(lang, catalogue)
    name, scripts, regions = schema.LANGS[lang]
    print(f"Language {lang} ({name}); script(s) {'/'.join(scripts)}; allowed regions: {', '.join(regions)}\n")
    for u in rubric["usecases"]:
        label = ", ".join(schema.expected_label(u["id"], rubric)) or "MUTED everywhere"
        print(f"{u['id']}  [{u['family']}]  urgent in: {label}")
        print(f"    what: {u['desc']}")
        print(f"    sender_type: {', '.join(u['senders'])}")
        apps = [a for c in u["apps"] for a in by_cat.get(c, [])]
        shown = "; ".join(f"{a['package']} = {a['name']}" for a in apps[:14])
        print(f"    apps ({len(apps)}): {shown}{' ...' if len(apps) > 14 else ''}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", required=True, choices=sorted(schema.LANGS))
    main(ap.parse_args().lang)
