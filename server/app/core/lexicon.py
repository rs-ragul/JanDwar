"""
Port of ai/Lexicon.kt.

Must stay byte-for-byte behaviourally identical to the Kotlin, because the
whole point of the shared engine is that a Tamil sentence means the same thing
whether it arrives from the app, a phone call or a WhatsApp voice note.
tools/check_nlu.py replays the same fixtures against this logic.

Rules, in order:
  1. phrase forms (containing a space) are substring-only;
  2. single-word forms in Latin script must match a whole token -- "inter"
     (Intermediate) was matching inside "interested" and recording Class 12.
     Indic single-word forms may still match as substrings, because Tamil and
     Telugu agglutinate suffixes onto the stem;
  3. a token that is ITSELF a known surface form only means what it literally
     means -- fuzzy matching is reserved for words the lexicon has never seen.
     Without this, "pathu" (ten) fuzzy-matched "pashu" (cattle) and "jilla"
     (district) matched "illa" (no);
  4. otherwise per-token Levenshtein, with the first character required to
     match, and tolerance scaled by script: Indic 0 (<=5) / 1 (<=8) / 2, Latin
     0 (<=3) / 1 (<=6) / 2. Indic scripts pack a syllable into each codepoint,
     so one edit there changes the word outright -- that is how the Tamil for
     "fifth" was matching the Tamil for "tenth";
  5. anything containing a digit must match exactly -- "10th" and "12th" are one
     edit apart but mean different school years.
"""

from __future__ import annotations

import re

# `fold()` has already turned every punctuation mark except "+" into a
# space, so splitting on whitespace alone is enough -- and it is the only
# split that keeps "+2" (Class 12 in Tamil Nadu) as a single token. The
# previous character-class split discarded the "+", so the token "2"
# could never equal the surface form "+2" and the form was dead data.
# The Kotlin tokeniser splits on whitespace too; these must agree.
_WORD = re.compile(r"\s+")

# Forms must be folded the same way utterances are, or a form carrying
# punctuation can never match: the text "பி.ஏ" normalises to "பி ஏ" while the
# stored form kept its dots, so every Tamil degree abbreviation was dead.
_FOLD = re.compile(r"[^\w\u0900-\u0DFF+]+", re.UNICODE)


def fold(s: str) -> str:
    return _FOLD.sub(" ", s.lower()).strip()


def _has_digit(s: str) -> bool:
    return any(ch.isdigit() for ch in s)


def _is_indic(s: str) -> bool:
    return any("\u0900" <= c <= "\u0D7F" for c in s)


def _tolerance(a: str, b: str) -> int:
    n = max(len(a), len(b))
    if _is_indic(a) or _is_indic(b):
        # One codepoint is a whole syllable: a single edit is a different word.
        return 0 if n <= 5 else (1 if n <= 8 else 2)
    # Romanised Indic vocabulary made the old Latin floor unsafe. The lexicon
    # now holds hundreds of short transliterations -- pasu (cow), aadu (goat),
    # kada (shop), meka (goat) -- and at tolerance 1 a four-letter English or
    # Tanglish word lands on top of them: "pass" in "PUC pass aagiruken"
    # matched "pasu" and recorded a cow-rearing interest. At tolerance 2,
    # "appuram" (afterwards) matched "appalam" (a snack) and recorded a food
    # -processing interest. Both are two edits or fewer yet completely
    # unrelated, so the floor has to sit above the length of a common word.
    if n <= 4:
        return 0
    if n <= 7:
        return 1
    return 2


def _within(a: str, b: str, max_d: int) -> bool:
    """Bounded Levenshtein: True if distance(a, b) <= max_d."""
    if a == b:
        return True
    if max_d <= 0:
        return False
    la, lb = len(a), len(b)
    if abs(la - lb) > max_d:
        return False
    prev = list(range(lb + 1))
    for i in range(1, la + 1):
        cur = [i] + [0] * lb
        best = cur[0]
        ca = a[i - 1]
        for j in range(1, lb + 1):
            cost = 0 if ca == b[j - 1] else 1
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            best = min(best, cur[j])
        if best > max_d:
            return False
        prev = cur
    return prev[lb] <= max_d


class Lexicon:
    def __init__(self, forms: dict[str, list[str]]):
        self.phrases: dict[str, list[str]] = {}
        self.words: dict[str, list[str]] = {}
        # Which categories each single-word form literally belongs to. A token
        # found in here is a word we already know, so it must not be fuzzed
        # into some other category.
        self.owner: dict[str, set[str]] = {}
        for cat, items in forms.items():
            ph, wd = [], []
            for f in items:
                f = fold(f)
                if not f:
                    continue
                if " " in f:
                    ph.append(f)
                else:
                    wd.append(f)
                    self.owner.setdefault(f, set()).add(cat)
            self.phrases[cat] = ph
            self.words[cat] = wd

    def categories(self) -> list[str]:
        return sorted(set(self.phrases) | set(self.words))

    def forms_of(self, cat: str) -> list[str]:
        return self.phrases.get(cat, []) + self.words.get(cat, [])

    @staticmethod
    def _present(form: str, text: str, tokens: set[str]) -> bool:
        """
        Is `form` really in `text`?

        Phrases and Indic words: substring. Latin words: whole token only,
        otherwise short forms match inside unrelated longer words.
        """
        if " " in form:
            return form in text
        if _is_indic(form):
            # Indic scripts glue case and tense endings straight onto the
            # stem, so a substring match is what lets மாடு reach மாடுகள்.
            # But a *short* stem then also fires inside an unrelated word:
            # ಹೊಲ (field, farming) sits inside ಹೊಲಿಗೆ (sewing, tailoring),
            # so "ಹೊಲಿಗೆ ಕೆಲಸ ಗೊತ್ತು" -- I know tailoring -- also reported an
            # interest in agriculture. Below four codepoints the form has to
            # stand as its own token; at four or more the suffix rule applies.
            if len(form) >= 4:
                return form in text
            return form in tokens
        return form in tokens

    def has_exact(self, text: str, cat: str) -> bool:
        """No fuzz. Used where a false positive is costly."""
        t = fold(text)
        toks = set(x for x in _WORD.split(t) if x)
        return any(self._present(f, t, toks) for f in self.forms_of(cat))

    def has(self, text: str, cat: str) -> bool:
        t = fold(text)
        tokens = [x for x in _WORD.split(t) if x]
        tokset = set(tokens)
        for f in self.phrases.get(cat, []):
            if f in t:
                return True
        words = self.words.get(cat, [])
        for f in words:
            if self._present(f, t, tokset):
                return True
        for tok in tokens:
            # A word the lexicon already knows means exactly what it means.
            owners = self.owner.get(tok)
            if owners is not None and cat not in owners:
                continue
            for f in words:
                if _has_digit(f) or _has_digit(tok):
                    if tok == f:
                        return True
                    continue
                # ASR drifts the middle and the tail of a word, very rarely its
                # first letter -- but unrelated words collide there constantly.
                if tok[:1] != f[:1]:
                    continue
                if _within(tok, f, _tolerance(tok, f)):
                    return True
        return False

    def first_match(self, text: str, cats: list[str]) -> str | None:
        for c in cats:
            if self.has(text, c):
                return c
        return None

    def match_len(self, text: str, cat: str) -> int:
        """Length of the longest surface form of `cat` present in `text`."""
        t = fold(text)
        toks = set(x for x in _WORD.split(t) if x)
        best = 0
        for f in self.forms_of(cat):
            if self._present(f, t, toks) and len(f) > best:
                best = len(f)
        return best

    def best_match(self, text: str, cats: list[str]) -> str | None:
        """
        Most *specific* category wins: the one matching the longest surface
        form. "pre university" is Class 12, but it contains "university", which
        is a graduate word -- ordering alone cannot resolve that, length can.
        Ties fall back to the caller's precedence order.
        """
        best_cat, best_len = None, 0
        for c in cats:
            n = self.match_len(text, c)
            if n > best_len:
                best_cat, best_len = c, n
        if best_cat:
            return best_cat
        for c in cats:
            if self.has(text, c):
                return c
        return None
