"""Item schema for the golden_v2 dataset: loading the rubric and app catalogue, and checking one item.

An item is a dict with the fields in FIELDS. The label (`urgentIn`, full mode names) is a function of the use case,
as defined in rubric.json, so `validate_item` rejects any item whose label disagrees with its use case.
"""
import json
import os
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
RUBRIC_PATH = os.path.join(HERE, "rubric.json")
APPS_PATH = os.path.join(HERE, "apps.tsv")

SPLITS = ("train", "dev", "test", "challenge", "contested")
DIFFICULTIES = ("easy", "hard_negative", "near_miss")
SENDER_TYPES = ("contact", "unknown", "spam", "service", "system", "group")
FIELDS = ("id", "split", "lang", "region", "package", "app_name", "app_category", "usecase", "sender_type",
          "title", "text", "urgentIn", "difficulty", "template_family", "provenance")
MAX_TEXT = 600

# Script ranges used to check that an item is really written in its language.
SCRIPTS = {
    "Latin": [(0x41, 0x5A), (0x61, 0x7A), (0xC0, 0x24F)],
    "Devanagari": [(0x0900, 0x097F)],
    "Bengali": [(0x0980, 0x09FF)],
    "Gujarati": [(0x0A80, 0x0AFF)],
    "Tamil": [(0x0B80, 0x0BFF)],
    "Telugu": [(0x0C00, 0x0C7F)],
    "Kannada": [(0x0C80, 0x0CFF)],
    "Arabic": [(0x0600, 0x06FF), (0x0750, 0x077F)],
    "Cyrillic": [(0x0400, 0x04FF)],
    "Han": [(0x4E00, 0x9FFF), (0x3400, 0x4DBF)],
    "Kana": [(0x3040, 0x30FF)],
}
# language code -> (name, scripts that must make up most of the letters, home regions for app matching)
LANGS = {
    "en": ("English", ["Latin"], ["US", "GB", "IN"]),
    "hi": ("Hindi", ["Devanagari"], ["IN"]),
    "hi-Latn": ("Hinglish", ["Latin"], ["IN"]),
    "es": ("Spanish", ["Latin"], ["ES", "MX"]),
    "fr": ("French", ["Latin"], ["FR"]),
    "de": ("German", ["Latin"], ["DE"]),
    "pt-BR": ("Portuguese (Brazil)", ["Latin"], ["BR"]),
    "ar": ("Arabic", ["Arabic"], ["SA", "AE", "EG"]),
    "zh-Hans": ("Chinese (Simplified)", ["Han"], ["CN"]),
    "ja": ("Japanese", ["Kana", "Han"], ["JP"]),
    "ru": ("Russian", ["Cyrillic"], ["RU"]),
    "id": ("Indonesian", ["Latin"], ["ID"]),
    "ta": ("Tamil", ["Tamil"], ["IN"]),
    "te": ("Telugu", ["Telugu"], ["IN"]),
    "bn": ("Bengali", ["Bengali"], ["BD", "IN"]),
    "mr": ("Marathi", ["Devanagari"], ["IN"]),
    "kn": ("Kannada", ["Kannada"], ["IN"]),
    "gu": ("Gujarati", ["Gujarati"], ["IN"]),
}


def load_rubric(path=RUBRIC_PATH):
    with open(path, encoding="utf-8") as f:
        rubric = json.load(f)
    rubric["by_id"] = {u["id"]: u for u in rubric["usecases"]}
    return rubric


def load_catalogue(path=APPS_PATH):
    """package -> {package, name, category, regions}. Lines starting with # are comments."""
    apps = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            cols = line.split("\t")
            package, name, category, regions = cols[:4]
            apps[package] = {"package": package, "name": name, "category": category,
                             "regions": [r.strip() for r in regions.split(",")],
                             "flag": cols[4] if len(cols) > 4 else ""}
    return apps


def mode_names(codes, rubric):
    return [rubric["modes"][c] for c in codes]


def expected_label(usecase_id, rubric):
    """Full mode names (in rubric order) in which this use case must be shown."""
    return mode_names(rubric["by_id"][usecase_id]["urgentIn"], rubric)


def letters(s):
    return [ch for ch in s if unicodedata.category(ch).startswith("L")]


def script_fraction(s, scripts):
    ls = letters(s)
    if not ls:
        return 0.0
    ranges = [r for name in scripts for r in SCRIPTS[name]]
    hits = sum(1 for ch in ls if any(lo <= ord(ch) <= hi for lo, hi in ranges))
    return hits / len(ls)


def check_language(item):
    """Errors if the title+text is not mostly in the script of the declared language."""
    name, scripts, _ = LANGS[item["lang"]]
    body = f'{item["title"]} {item["text"]}'
    frac = script_fraction(body, scripts)
    errors = []
    if scripts == ["Latin"]:
        if frac < 0.9:
            errors.append(f"language {item['lang']}: only {frac:.0%} Latin letters")
        if item["lang"] == "hi-Latn" and script_fraction(body, ["Devanagari"]) > 0:
            errors.append("hi-Latn must be romanized, found Devanagari")
    elif frac < 0.6:
        errors.append(f"language {item['lang']}: only {frac:.0%} {'/'.join(scripts)} letters")
    return errors


def validate_item(item, rubric, catalogue):
    """Return a list of error strings (empty when the item is valid)."""
    errors = []
    for field in FIELDS:
        if field not in item:
            errors.append(f"missing field {field}")
    if errors:
        return errors
    if item["split"] not in SPLITS:
        errors.append(f"bad split {item['split']}")
    if item["lang"] not in LANGS:
        errors.append(f"bad lang {item['lang']}")
        return errors
    if item["difficulty"] not in DIFFICULTIES:
        errors.append(f"bad difficulty {item['difficulty']}")
    if item["sender_type"] not in SENDER_TYPES:
        errors.append(f"bad sender_type {item['sender_type']}")
    app = catalogue.get(item["package"])
    if app is None:
        errors.append(f"package {item['package']} not in catalogue")
    else:
        if item["app_name"] != app["name"]:
            errors.append(f"app_name {item['app_name']!r} != catalogue {app['name']!r}")
        if item["app_category"] != app["category"]:
            errors.append(f"app_category {item['app_category']} != catalogue {app['category']}")
    usecase = rubric["by_id"].get(item["usecase"])
    if usecase is None:
        errors.append(f"unknown usecase {item['usecase']}")
    else:
        if item["urgentIn"] != expected_label(item["usecase"], rubric):
            errors.append(f"label {item['urgentIn']} does not match rubric for {item['usecase']}")
        if app is not None and app["category"] not in usecase["apps"]:
            errors.append(f"app category {app['category']} not allowed for usecase {item['usecase']}")
        if item["sender_type"] not in usecase["senders"]:
            errors.append(f"sender_type {item['sender_type']} not allowed for usecase {item['usecase']}")
    if not isinstance(item["text"], str) or not item["text"].strip():
        errors.append("empty text")
    elif len(item["text"]) > MAX_TEXT or len(item["title"]) > MAX_TEXT:
        errors.append("text or title too long")
    if not errors:
        errors.extend(check_language(item))
    return errors


def load_jsonl(path):
    items = []
    with open(path, encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            line = line.strip()
            if line:
                try:
                    items.append(json.loads(line))
                except json.JSONDecodeError as e:
                    raise ValueError(f"{path}:{n}: invalid JSON ({e})")
    return items


def dump_jsonl(items, path):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for item in items:
            f.write(json.dumps(item, ensure_ascii=False, sort_keys=False) + "\n")
