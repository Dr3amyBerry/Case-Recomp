"""Local fidelity checks for converted media.

The helpers compare decoded pixels / PCM samples rather than compressed file bytes.
They are intended for operator-supplied private references and never know about the
commercial game layout.
"""
from __future__ import annotations

from hashlib import sha256
from pathlib import Path
from io import BytesIO
import wave

from PIL import Image

from .inspector import InspectionError
from .director import read_local

MAX_PIXELS = 32_000_000
MAX_AUDIO_BYTES = 64 * 1024 * 1024


def _png_signature(path: Path) -> dict:
    data = read_local(path)
    try:
        with Image.open(BytesIO(data)) as image:
            if image.format != "PNG":
                raise InspectionError("fidelity PNG input is not PNG")
            width, height = image.size
            if width < 1 or height < 1 or width * height > MAX_PIXELS:
                raise InspectionError("fidelity PNG dimensions exceed cap")
            image.load()
            rgba = image.convert("RGBA").tobytes()
    except (OSError, ValueError, SyntaxError, Image.DecompressionBombError) as exc:
        raise InspectionError("cannot decode fidelity PNG") from exc
    return {"width": width, "height": height, "mode": "RGBA",
            "pixel_sha256": sha256(rgba).hexdigest(), "pixel_bytes": len(rgba)}


def compare_png(left: Path, right: Path) -> dict:
    """Compare PNGs after RGBA decode, ignoring encoder metadata/compression."""
    a, b = _png_signature(left), _png_signature(right)
    exact = (a["width"], a["height"], a["pixel_sha256"]) == (b["width"], b["height"], b["pixel_sha256"])
    return {"kind": "png-rgba", "left": a, "right": b,
            "dimensions_match": (a["width"], a["height"]) == (b["width"], b["height"]),
            "exact_pixels": exact, "compression_bytes_compared": False}


def _wav_signature(path: Path) -> dict:
    data = read_local(path)
    if len(data) > MAX_AUDIO_BYTES:
        raise InspectionError("fidelity WAV exceeds byte cap")
    try:
        with wave.open(BytesIO(data), "rb") as source:
            channels = source.getnchannels()
            width = source.getsampwidth()
            rate = source.getframerate()
            frames = source.getnframes()
            if channels < 1 or width < 1 or rate < 1 or frames < 1:
                raise InspectionError("invalid fidelity WAV metadata")
            pcm = source.readframes(frames)
            if source.readframes(1):
                raise InspectionError("WAV frame count is inconsistent")
    except (wave.Error, EOFError) as exc:
        raise InspectionError("cannot decode fidelity WAV") from exc
    return {"channels": channels, "sample_width": width, "rate": rate, "frames": frames,
            "pcm_sha256": sha256(pcm).hexdigest(), "pcm_bytes": len(pcm)}


def compare_wav(left: Path, right: Path) -> dict:
    """Compare decoded PCM samples and stream geometry, not RIFF container bytes."""
    a, b = _wav_signature(left), _wav_signature(right)
    geometry = all(a[key] == b[key] for key in ("channels", "sample_width", "rate", "frames"))
    return {"kind": "wav-pcm", "left": a, "right": b, "audio_geometry_match": geometry,
            "exact_pcm": geometry and a["pcm_sha256"] == b["pcm_sha256"],
            "container_bytes_compared": False}
