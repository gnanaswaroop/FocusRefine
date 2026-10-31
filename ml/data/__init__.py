"""Dataset tooling. The command-line tools print non-English text, so make stdout UTF-8 on every platform
(the Windows console otherwise defaults to a legacy code page and crashes on Cyrillic, Arabic or CJK text)."""
import sys

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")
