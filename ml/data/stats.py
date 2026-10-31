"""Counts for the review report: python -m ml.data.stats <file-or-dir> [...]"""
import sys
from collections import Counter

from ml.data import schema, validate


def table(title, counter, width=34):
    print(f"\n{title}")
    for k, v in sorted(counter.items(), key=lambda kv: (-kv[1], str(kv[0]))):
        print(f"  {str(k):<{width}} {v}")


def main(paths):
    items = []
    for path in validate.find_files(paths):
        items.extend(schema.load_jsonl(path))
    rubric = schema.load_rubric()
    print(f"{len(items)} items")
    table("by split", Counter(i["split"] for i in items))
    table("by language", Counter(i["lang"] for i in items))
    table("by use-case family", Counter(rubric["by_id"][i["usecase"]]["family"] for i in items))
    table("by app category", Counter(i["app_category"] for i in items))
    table("by difficulty", Counter(i["difficulty"] for i in items))
    table("by sender type", Counter(i["sender_type"] for i in items))
    table("by label (urgent in)", Counter(" + ".join(i["urgentIn"]) or "(muted everywhere)" for i in items), 52)
    table("distinct apps used", Counter(i["package"] for i in items).most_common(0) or {"apps": len({i["package"] for i in items})})


if __name__ == "__main__":
    main(sys.argv[1:] or ["testdata/golden_v2"])
