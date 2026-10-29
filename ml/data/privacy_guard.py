"""Guards that keep the dataset purely synthetic: nothing that looks like real personal data may pass.

`check_text` returns a list of problems for one string. It is deliberately strict; authors are told to use
fictional names, X-masked or reserved-range phone numbers, and reserved example domains.
"""
import re

# Reserved or fictional phone ranges that are allowed to appear in full.
ALLOWED_PHONE_MARKERS = (
    r"555[\s\-.)]*01\d\d",       # US fictional 555-0100 to 555-0199
    r"7700[\s\-]*900\d{3}",      # UK Ofcom drama range 07700 900xxx
    r"7946[\s\-]*0\d{3}",        # UK Ofcom drama range 020 7946 0xxx
)
ALLOWED_DOMAIN_SUFFIXES = ("example.com", "example.org", "example.net", ".invalid", ".test", ".example")
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+\-]+@([A-Za-z0-9.\-]+\.[A-Za-z]{2,})")
URL_RE = re.compile(r"(?:https?://|www\.)([A-Za-z0-9.\-]+\.[A-Za-z]{2,})(?:[/\s?#]|$)", re.IGNORECASE)
BARE_DOMAIN_RE = re.compile(r"\b([A-Za-z0-9\-]+\.(?:com|net|org|in|io|co|me|ly|app|ru|cn|jp|de|fr|br|id))\b", re.IGNORECASE)
DIGIT_RUN_RE = re.compile(r"(?<![\dX])(?:\+?\d[\d\s\-().]{5,}\d)(?![\dX])")
OTP_WORDS = re.compile(r"\b(otp|one[- ]time (?:password|code)|verification code|security code|cvv)\b", re.IGNORECASE)


def luhn_ok(digits):
    total, alt = 0, False
    for ch in reversed(digits):
        d = int(ch)
        if alt:
            d *= 2
            if d > 9:
                d -= 9
        total += d
        alt = not alt
    return total % 10 == 0


def _domain_ok(domain):
    domain = domain.lower().rstrip(".")
    return any(domain == s.lstrip(".") or domain.endswith(s if s.startswith(".") else "." + s) or domain == s
               for s in ALLOWED_DOMAIN_SUFFIXES)


def check_text(s):
    """Problems found in one string; an empty list means it passes."""
    problems = []
    for m in EMAIL_RE.finditer(s):
        if not _domain_ok(m.group(1)):
            problems.append(f"email domain not reserved: {m.group(1)}")
    for m in URL_RE.finditer(s):
        if not _domain_ok(m.group(1)):
            problems.append(f"link domain not reserved: {m.group(1)}")
    for m in BARE_DOMAIN_RE.finditer(s):
        if not _domain_ok(m.group(1)) and not EMAIL_RE.search(s):
            problems.append(f"bare domain not reserved: {m.group(1)}")
    for m in DIGIT_RUN_RE.finditer(s):
        run = m.group(0)
        digits = re.sub(r"\D", "", run)
        if len(digits) < 7:
            continue
        if 13 <= len(digits) <= 19 and luhn_ok(digits):
            problems.append("digit run passes the Luhn check (looks like a card number)")
        elif not any(re.search(p, run) for p in ALLOWED_PHONE_MARKERS):
            problems.append(f"long digit run is not an allowed fictional number or X-mask: {run.strip()}")
    if OTP_WORDS.search(s):
        problems.append("OTP, verification or security codes are out of scope (handled by the exceptions layer)")
    return problems


def check_item(item):
    problems = []
    for field in ("title", "text"):
        problems.extend(f"{field}: {p}" for p in check_text(str(item.get(field, ""))))
    return problems
