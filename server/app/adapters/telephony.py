# -*- coding: utf-8 -*-
"""
Make synthesised speech safe to hand to a telephony provider.

Bhashini returns 8 kHz mono WAV whose samples are 32-bit IEEE floats. That is
a perfectly good file and every desktop player opens it -- but Twilio's
<Play> accepts MP3, 16-bit PCM WAV, and mu-law/a-law only. Handed a float32
WAV it either rejects the media or plays static, and the caller hears
nothing. The bug is invisible in curl (200, audio/wav, plausible byte count)
and only shows up on a real phone call, which is exactly the kind of defect
worth converting away at the source.

Pure standard library on purpose: the server's requirements are four
packages, `numpy` is not among them, and `audioop` was removed in Python
3.13, so neither is available to lean on.
"""
from __future__ import annotations

import array
import logging
import struct

log = logging.getLogger("jandwar.telephony")

TELEPHONY_RATE = 8000
#: leave a little headroom -- the upstream audio peaks slightly above 1.0 and
#: would clip audibly the moment it is quantised to integers
TARGET_PEAK = 0.95

WAVE_FORMAT_PCM = 0x0001
WAVE_FORMAT_IEEE_FLOAT = 0x0003
WAVE_FORMAT_ALAW = 0x0006
WAVE_FORMAT_MULAW = 0x0007
WAVE_FORMAT_EXTENSIBLE = 0xFFFE


class NotWav(ValueError):
    """The payload is not a RIFF/WAVE file we can rewrite."""


# ── decoding ────────────────────────────────────────────────────────────────

def _chunks(data: bytes):
    """Yield (id, payload) for each RIFF chunk, tolerating odd-sized ones."""
    if len(data) < 12 or data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise NotWav("missing RIFF/WAVE header")
    pos = 12
    while pos + 8 <= len(data):
        cid = data[pos:pos + 4]
        (size,) = struct.unpack_from("<I", data, pos + 4)
        payload = data[pos + 8:pos + 8 + size]
        yield cid, payload
        pos += 8 + size + (size & 1)      # chunks are word-aligned


def _decode(data: bytes) -> tuple[list[float], int]:
    """WAV bytes -> (mono samples in [-1, 1], sample rate)."""
    fmt = None
    raw = None
    for cid, payload in _chunks(data):
        if cid == b"fmt " and len(payload) >= 16:
            fmt = struct.unpack_from("<HHIIHH", payload, 0)
            if fmt[0] == WAVE_FORMAT_EXTENSIBLE and len(payload) >= 26:
                # the real format code is the first field of the SubFormat GUID
                (sub,) = struct.unpack_from("<H", payload, 24)
                fmt = (sub,) + fmt[1:]
        elif cid == b"data":
            raw = payload
    if fmt is None or raw is None:
        raise NotWav("no fmt / data chunk")

    tag, channels, rate, _byte_rate, _align, bits = fmt
    channels = max(1, channels)

    if tag == WAVE_FORMAT_IEEE_FLOAT and bits == 32:
        a = array.array("f")
        a.frombytes(raw[:len(raw) // 4 * 4])
        vals = list(a)
    elif tag == WAVE_FORMAT_IEEE_FLOAT and bits == 64:
        a = array.array("d")
        a.frombytes(raw[:len(raw) // 8 * 8])
        vals = list(a)
    elif tag == WAVE_FORMAT_PCM and bits == 16:
        a = array.array("h")
        a.frombytes(raw[:len(raw) // 2 * 2])
        vals = [s / 32768.0 for s in a]
    elif tag == WAVE_FORMAT_PCM and bits == 8:
        vals = [(b - 128) / 128.0 for b in raw]          # 8-bit PCM is unsigned
    elif tag == WAVE_FORMAT_PCM and bits == 24:
        vals = []
        for i in range(0, len(raw) - 2, 3):
            v = raw[i] | (raw[i + 1] << 8) | (raw[i + 2] << 16)
            if v & 0x800000:
                v -= 0x1000000
            vals.append(v / 8388608.0)
    elif tag == WAVE_FORMAT_MULAW:
        vals = [_ulaw_decode(b) / 32768.0 for b in raw]
    else:
        raise NotWav(f"unsupported format tag {tag} / {bits}-bit")

    if channels > 1:                                      # down-mix to mono
        vals = [sum(vals[i:i + channels]) / channels
                for i in range(0, len(vals) - channels + 1, channels)]
    return vals, rate


def _resample(vals: list[float], src: int, dst: int) -> list[float]:
    """Linear interpolation. Speech at 8 kHz over a phone line does not
    justify a windowed-sinc filter, and this keeps the module dependency
    free."""
    if src == dst or not vals:
        return vals
    ratio = src / dst
    n = int(len(vals) / ratio)
    out = []
    for i in range(n):
        p = i * ratio
        j = int(p)
        frac = p - j
        a = vals[j]
        b = vals[j + 1] if j + 1 < len(vals) else a
        out.append(a + (b - a) * frac)
    return out


# ── mu-law (G.711), the codec the call actually runs on ─────────────────────

_ULAW_BIAS = 0x84
#: ITU-T G.711 works on a 14-bit magnitude, so the clip point is 8159, not
#: 32635. Encoding at full 16-bit resolution instead disagrees with every
#: reference implementation on ~0.5% of samples near the segment boundaries,
#: which is exactly the sort of "nearly right" that survives testing and then
#: sounds wrong on a phone line.
_ULAW_CLIP = 8159
_SEG_UEND = (0x3F, 0x7F, 0xFF, 0x1FF, 0x3FF, 0x7FF, 0xFFF, 0x1FFF)


def _ulaw_encode(sample: int) -> int:
    """16-bit signed PCM -> one mu-law byte. Matches ITU-T G.711."""
    sample >>= 2                              # 16-bit sample, 14-bit codec
    if sample < 0:
        sample = -sample
        mask = 0x7F
    else:
        mask = 0xFF
    if sample > _ULAW_CLIP:
        sample = _ULAW_CLIP
    sample += _ULAW_BIAS >> 2
    seg = next((i for i, end in enumerate(_SEG_UEND) if sample <= end),
               len(_SEG_UEND))
    if seg >= 8:
        return 0x7F ^ mask
    return ((seg << 4) | ((sample >> (seg + 1)) & 0x0F)) ^ mask


def _ulaw_decode(byte: int) -> int:
    byte = ~byte & 0xFF
    sign = byte & 0x80
    exponent = (byte >> 4) & 0x07
    mantissa = byte & 0x0F
    sample = ((mantissa << 3) + _ULAW_BIAS) << exponent
    sample -= _ULAW_BIAS
    return -sample if sign else sample


# ── encoding ────────────────────────────────────────────────────────────────

def _wav(pcm: bytes, rate: int, tag: int, bits: int, align: int) -> bytes:
    fmt_chunk = struct.pack("<HHIIHH", tag, 1, rate, rate * align, align, bits)
    if tag != WAVE_FORMAT_PCM:
        fmt_chunk += struct.pack("<H", 0)            # cbSize, required off-PCM
    body = (b"WAVE"
            + b"fmt " + struct.pack("<I", len(fmt_chunk)) + fmt_chunk
            + b"data" + struct.pack("<I", len(pcm)) + pcm)
    return b"RIFF" + struct.pack("<I", len(body)) + body


def to_telephony_wav(data: bytes, fmt: str = "pcm16") -> bytes:
    """
    Rewrite arbitrary WAV bytes as something a telephony provider will play.

    fmt="pcm16" -> 8 kHz mono 16-bit PCM WAV  (default; widest support)
    fmt="ulaw"  -> 8 kHz mono mu-law WAV      (what the PSTN carries anyway)

    Raises NotWav if the payload is not a WAV we understand -- callers should
    pass MP3 and friends straight through rather than mangling them.
    """
    vals, rate = _decode(data)
    if not vals:
        raise NotWav("no samples")

    vals = _resample(vals, rate, TELEPHONY_RATE)

    peak = max(abs(v) for v in vals)
    if peak > TARGET_PEAK:
        # Bhashini's output measured 1.006 -- just over full scale, which
        # clips on the way to integers.
        gain = TARGET_PEAK / peak
        vals = [v * gain for v in vals]

    ints = [max(-32768, min(32767, int(v * 32767))) for v in vals]

    if fmt == "ulaw":
        pcm = bytes(_ulaw_encode(s) for s in ints)
        return _wav(pcm, TELEPHONY_RATE, WAVE_FORMAT_MULAW, 8, 1)

    out = array.array("h", ints)
    if struct.pack("H", 1) != b"\x01\x00":           # force little-endian
        out.byteswap()
    return _wav(out.tobytes(), TELEPHONY_RATE, WAVE_FORMAT_PCM, 16, 2)


def describe(data: bytes) -> str:
    """One-line summary, for logs and the health endpoint."""
    try:
        for cid, payload in _chunks(data):
            if cid == b"fmt " and len(payload) >= 16:
                tag, ch, rate, _br, _al, bits = struct.unpack_from("<HHIIHH", payload, 0)
                name = {1: "PCM", 3: "float", 6: "alaw", 7: "ulaw",
                        0xFFFE: "extensible"}.get(tag, str(tag))
                return f"{name} {bits}-bit {ch}ch {rate}Hz {len(data)}B"
    except NotWav:
        pass
    return f"non-wav {len(data)}B"
