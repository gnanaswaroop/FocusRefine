"""Duplicate and leak checks across splits: exact copies, near-duplicates and shared template families."""
import re
import unicodedata
from collections import defaultdict

EVAL_SPLITS = ("dev", "test", "challenge")


def normalize(s):
    s = unicodedata.normalize("NFKC", s).lower()
    s = re.sub(r"\d+", "#", s)
    s = re.sub(r"[^\w#]+", " ", s)
    return s.strip()


def key(item):
    return normalize(item["title"]) + " | " + normalize(item["text"])


def shingles(s, n=5):
    s = s.replace(" ", "")
    if len(s) <= n:
        return {s}
    return {s[i:i + n] for i in range(len(s) - n + 1)}


def jaccard(a, b):
    return len(a & b) / len(a | b) if a and b else 0.0


def exact_duplicates(items):
    """Groups of items with the same normalized title and text. Returns list of lists of ids."""
    seen = defaultdict(list)
    for it in items:
        seen[key(it)].append(it)
    return [[i["id"] for i in group] for group in seen.values() if len(group) > 1]


def cross_split_exact(items):
    """Exact (normalized) duplicates that sit in different splits: a real leak."""
    seen = defaultdict(set)
    ids = defaultdict(list)
    for it in items:
        k = key(it)
        seen[k].add(it["split"])
        ids[k].append(it["id"])
    return [ids[k] for k, splits in seen.items() if len(splits) > 1]


def near_duplicates(train_items, eval_items, threshold=0.85):
    """Pairs (eval_id, train_id) with Jaccard >= threshold on character 5-grams. Uses an inverted index."""
    index = defaultdict(list)
    train_sh = []
    for n, it in enumerate(train_items):
        sh = shingles(key(it))
        train_sh.append(sh)
        for g in sh:
            index[g].append(n)
    pairs = []
    for it in eval_items:
        sh = shingles(key(it))
        counts = defaultdict(int)
        for g in sh:
            for n in index.get(g, ()):
                counts[n] += 1
        for n, c in counts.items():
            if c / max(len(sh), 1) >= threshold * 0.6 and jaccard(sh, train_sh[n]) >= threshold:
                pairs.append((it["id"], train_items[n]["id"]))
    return pairs


def template_leaks(items):
    """template_family values that appear in train and in an eval split (they must be disjoint)."""
    by_family = defaultdict(set)
    for it in items:
        by_family[it["template_family"]].add(it["split"])
    return sorted(f for f, splits in by_family.items() if "train" in splits and splits & set(EVAL_SPLITS))


def held_out_app_leaks(items, held_out_packages):
    """Held-out apps must never appear in training items."""
    return [it["id"] for it in items if it["split"] == "train" and it["package"] in held_out_packages]
