"""The one definition of the prompt the student model sees.

The text itself lives in `backend/app/services/answer_text.py`, where the
assistant endpoint uses it; this module loads that file by path and passes its
names on, so teacher-data generation, training, evaluation and the live
endpoint cannot drift apart. Loading by path keeps this importable on a
machine that has the repository but not the backend's dependencies.
"""

import importlib.util
import sys
from pathlib import Path

ANSWER_TEXT_PATH = (
    Path(__file__).resolve().parent.parent
    / "backend"
    / "app"
    / "services"
    / "answer_text.py"
)


def _load():
    name = "mpc_answer_text"
    if name not in sys.modules:
        spec = importlib.util.spec_from_file_location(name, ANSWER_TEXT_PATH)
        module = importlib.util.module_from_spec(spec)
        sys.modules[name] = module
        spec.loader.exec_module(module)
    return sys.modules[name]


answer_text = _load()

LANGS = answer_text.LANGS
OIA_EMAIL = answer_text.OIA_EMAIL
OIA_PHONE = answer_text.OIA_PHONE
OIA_CONTACT_STRINGS = answer_text.OIA_CONTACT_STRINGS
SYSTEM_PROMPT = answer_text.SYSTEM_PROMPT
REFUSALS = answer_text.REFUSALS
CITATION_RE = answer_text.CITATION_RE
NO_PASSAGES = answer_text.NO_PASSAGES
refusal_text = answer_text.refusal_text
format_passages = answer_text.format_passages
build_user_content = answer_text.build_user_content
build_messages = answer_text.build_messages
cited_numbers = answer_text.cited_numbers
is_refusal = answer_text.is_refusal
