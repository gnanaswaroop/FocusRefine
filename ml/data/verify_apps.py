"""Check that every package id in apps.tsv resolves to a public store listing; drop the ones that do not.

python -m ml.data.verify_apps           # report only
python -m ml.data.verify_apps --write   # rewrite apps.tsv without the ids that failed
Read-only HTTP GETs to the public listing page, with a short pause between requests.
"""
import sys
import time
import urllib.error
import urllib.request

from ml.data import schema

URL = "https://play.google.com/store/apps/details?id={}&hl=en&gl={}"


def exists_in(package, country):
    req = urllib.request.Request(URL.format(package, country), headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status == 200
    except urllib.error.HTTPError as e:
        return False if e.code == 404 else None
    except Exception:
        return None


def exists(package, regions):
    """True if the listing resolves in any of the app's home regions or a few common ones (listings are per country)."""
    countries = [r for r in regions if r != "GLOBAL"][:3] + ["US", "IN", "GB", "BR", "ID"]
    result = False
    for c in dict.fromkeys(countries):
        r = exists_in(package, c)
        if r:
            return True
        if r is None:
            result = None
    return result


def main(write):
    catalogue = schema.load_catalogue()
    ok, bad, unknown = [], [], []
    for package, app in catalogue.items():
        if app["flag"] in ("system", "external"):   # no listing in the global store by design
            ok.append(package)
            continue
        result = exists(package, app["regions"])
        (ok if result else bad if result is False else unknown).append(package)
        time.sleep(0.25)
    print(f"verified {len(ok)}, missing {len(bad)}, could not check {len(unknown)}")
    for p in bad:
        print("  missing:", p)
    for p in unknown:
        print("  unknown:", p)
    if write and bad:
        lines = open(schema.APPS_PATH, encoding="utf-8").read().splitlines()
        keep = [l for l in lines if l.startswith("#") or not l.strip() or l.split("\t")[0] not in bad]
        with open(schema.APPS_PATH, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(keep) + "\n")
        print(f"removed {len(bad)} ids from apps.tsv")


if __name__ == "__main__":
    main("--write" in sys.argv)
