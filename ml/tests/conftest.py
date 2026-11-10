"""Make the flat modules of ml/ and the backend package importable."""

import sys
from pathlib import Path

ML_DIR = Path(__file__).resolve().parent.parent
for path in (ML_DIR, ML_DIR.parent / "backend"):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))
