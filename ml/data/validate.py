"""Validate dataset files: python -m ml.data.validate <file-or-dir> [...]

Runs schema, privacy guard and leak checks over every .jsonl file found. Exit status 1 if anything fails.
"""
import os
import sys
from collections import Counter

from ml.data import leakcheck, privacy_guard, schema


def find_files(paths):
    out = []
    for p in paths:
        if os.path.isdir(p):
            for root, _, files in os.walk(p):
                out.extend(os.path.join(root, f) for f in sorted(files) if f.endswith(".jsonl"))
        else:
            out.append(p)
    return out


def run(paths, max_show=15, check_leaks=True):
    rubric = schema.load_rubric()
    catalogue = schema.load_catalogue()
    items, problems = [], []
    for path in find_files(paths):
        for n, item in enumerate(schema.load_jsonl(path), 1):
            where = f"{os.path.basename(path)}:{n} {item.get('id', '?')}"
            for e in schema.validate_item(item, rubric, catalogue):
                problems.append((where, "schema", e))
            for e in privacy_guard.check_item(item):
                problems.append((where, "privacy", e))
            items.append(item)
    ids = Counter(i.get("id") for i in items)
    for i, c in ids.items():
        if c > 1:
            problems.append((str(i), "schema", f"duplicate id ({c} times)"))
    if check_leaks:
        for group in leakcheck.cross_split_exact(items):
            problems.append((",".join(group[:3]), "leak", "same text in more than one split"))
        for fam in leakcheck.template_leaks(items):
            problems.append((fam, "leak", "template_family in train and an eval split"))
    kinds = Counter(k for _, k, _ in problems)
    print(f"{len(items)} items, {len(problems)} problems {dict(kinds)}")
    for where, kind, msg in problems[:max_show]:
        print(f"  [{kind}] {where}: {msg}")
    if len(problems) > max_show:
        print(f"  ... and {len(problems) - max_show} more")
    return items, problems


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: python -m ml.data.validate <file-or-dir> [...]")
    _, probs = run(sys.argv[1:])
    sys.exit(1 if probs else 0)
