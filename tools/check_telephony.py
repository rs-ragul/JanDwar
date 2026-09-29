# -*- coding: utf-8 -*-
"""
Regression test for the telephony audio conversion.

The bug this guards: Bhashini returns 32-bit IEEE-float WAV, which Twilio's
<Play> will not play. curl reported 200 / audio/wav / a plausible byte count,
so nothing looked wrong until a real call produced silence.

    python3 tools/check_telephony.py
"""
from __future__ import annotations

import math
import pathlib
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))

from app.adapters.telephony import (  # noqa: E402
    NotWav, TELEPHONY_RATE, describe, to_telephony_wav, _decode, _ulaw_decode,
    _ulaw_encode)

FAILS: list[str] = []


def check(name: str, ok: bool, detail: str = "") -> None:
    print(f"  {'PASS' if ok else 'FAIL'}  {name:<46} {detail}")
    if not ok:
        FAILS.append(name)


def make_wav(tag: int, bits: int, rate: int, channels: int, samples) -> bytes:
    """Build a WAV of the given flavour from floats in [-1, 1]."""
    if tag == 3 and bits == 32:
        pcm = b"".join(struct.pack("<f", s) for s in samples)
    elif tag == 1 and bits == 16:
        pcm = b"".join(struct.pack("<h", max(-32768, min(32767, int(s * 32767))))
                       for s in samples)
    elif tag == 1 and bits == 8:
        pcm = bytes(max(0, min(255, int(s * 127) + 128)) for s in samples)
    elif tag == 7:
        pcm = bytes(_ulaw_encode(max(-32768, min(32767, int(s * 32767))))
                    for s in samples)
    else:
        raise ValueError("unsupported test format")
    align = channels * bits // 8
    fmt = struct.pack("<HHIIHH", tag, channels, rate, rate * align, align, bits)
    if tag != 1:
        fmt += struct.pack("<H", 0)
    body = (b"WAVE" + b"fmt " + struct.pack("<I", len(fmt)) + fmt
            + b"data" + struct.pack("<I", len(pcm)) + pcm)
    return b"RIFF" + struct.pack("<I", len(body)) + body


def tone(n: int, freq: float, rate: int, amp: float = 0.5):
    return [amp * math.sin(2 * math.pi * freq * i / rate) for i in range(n)]


print("telephony conversion\n" + "-" * 70)

# 1. the actual failing case: float32 in, 16-bit PCM out
src = make_wav(3, 32, 8000, 1, tone(8000, 440, 8000))
out = to_telephony_wav(src)
d = describe(out)
check("float32 -> 16-bit PCM", "PCM 16-bit 1ch 8000Hz" in d, d)

# 2. mu-law on request
d = describe(to_telephony_wav(src, "ulaw"))
check("float32 -> mu-law", "ulaw 8-bit 1ch 8000Hz" in d, d)

# 3. never emits float, whatever goes in
for tag, bits, rate, ch in [(3, 32, 8000, 1), (3, 32, 16000, 1), (1, 16, 22050, 2),
                            (1, 8, 8000, 1), (7, 8, 8000, 1)]:
    w = make_wav(tag, bits, rate, ch, tone(2000 * ch, 300, rate))
    got = describe(to_telephony_wav(w))
    ok = "PCM 16-bit 1ch 8000Hz" in got
    check(f"tag={tag} {bits}-bit {rate}Hz {ch}ch -> telephony", ok, got)

# 4. resampling preserves duration
src16 = make_wav(3, 32, 16000, 1, tone(16000, 440, 16000))     # 1.0 s
vals, rate = _decode(to_telephony_wav(src16))
dur = len(vals) / rate
check("16 kHz -> 8 kHz keeps duration", abs(dur - 1.0) < 0.01,
      f"{dur:.3f}s at {rate}Hz")

# 5. clipping is removed -- the real payload peaked at 1.006
hot = make_wav(3, 32, 8000, 1, [1.006 * math.sin(i / 5.0) for i in range(4000)])
vals, _ = _decode(to_telephony_wav(hot))
peak = max(abs(v) for v in vals)
check("peak normalised below full scale", peak <= 0.96, f"peak {peak:.3f}")

# 6. quiet audio is left alone, not pumped up
quiet = make_wav(3, 32, 8000, 1, tone(4000, 440, 8000, amp=0.1))
vals, _ = _decode(to_telephony_wav(quiet))
peak = max(abs(v) for v in vals)
check("quiet audio not amplified", 0.09 < peak < 0.11, f"peak {peak:.3f}")

# 7. stereo is down-mixed
st = make_wav(1, 16, 8000, 2, tone(4000, 440, 8000))
check("stereo -> mono", "1ch" in describe(to_telephony_wav(st)),
      describe(to_telephony_wav(st)))

# 8. mu-law codec matches ITU-T G.711 exactly, over every 16-bit value
worst = max(abs(_ulaw_decode(_ulaw_encode(v)) - v) for v in range(-32768, 32768))
# 644 is the true G.711 bound: half the 1024-wide top segment plus the
# 2-bit truncation to the codec's 14-bit magnitude.
check("mu-law round-trip within G.711 bound", worst <= 644, f"max err {worst}")

try:
    import audioop  # removed from the stdlib in 3.13; present on older runtimes
except ImportError:
    print("  skip  mu-law vs reference codec                  audioop unavailable")
else:
    enc_bad = sum(1 for v in range(-32768, 32768)
                  if _ulaw_encode(v) != audioop.lin2ulaw(struct.pack("<h", v), 2)[0])
    dec_bad = sum(1 for b in range(256)
                  if _ulaw_decode(b) != struct.unpack("<h", audioop.ulaw2lin(bytes([b]), 2))[0])
    check("mu-law encoder matches reference (65536 values)", enc_bad == 0,
          f"{enc_bad} mismatches")
    check("mu-law decoder matches reference (256 values)", dec_bad == 0,
          f"{dec_bad} mismatches")

# 9. non-WAV raises rather than corrupting -- the route passes those through
try:
    to_telephony_wav(b"ID3\x04\x00\x00not audio")
    check("non-WAV rejected", False, "no exception raised")
except NotWav:
    check("non-WAV rejected", True, "NotWav raised")

# 10. output is a valid RIFF the decoder can read back
out = to_telephony_wav(src)
vals, rate = _decode(out)
check("output re-decodes", rate == TELEPHONY_RATE and len(vals) > 0,
      f"{len(vals)} samples @ {rate}Hz")

print("-" * 70)
if FAILS:
    print(f"{len(FAILS)} FAILED: {FAILS}")
    sys.exit(1)
print("all telephony checks pass")
