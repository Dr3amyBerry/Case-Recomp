"""Explicit offline decoding for MP3 frames carried in Director's SWA chunks.

SWA compressor registration is format metadata, not a WAV file. A trusted,
operator-selected FFmpeg executable is required. It is never fetched or invoked
implicitly for default conversion and commercial data never leaves the machine.
"""
from __future__ import annotations

import os
import shutil
import stat
import subprocess
import tempfile
from pathlib import Path

from .executables import tool_command
from .inspector import InspectionError
from .pipeline import _wave_metadata

MAX_WAV_BYTES = 16 * 1024 * 1024
MAX_ENCODED_BYTES = 2 * 1024 * 1024


def resolve_ffmpeg(binary: Path | None) -> Path:
    path = binary if binary is not None else Path(shutil.which("ffmpeg") or "")
    if not str(path) or str(path) == "." or path.is_symlink() or not path.is_file():
        raise InspectionError("FFmpeg must be installed locally or specified with --ffmpeg")
    try:
        native = len(tool_command(path)) == 1
    except InspectionError:
        native = False
    if not native or not stat.S_ISREG(path.stat().st_mode):
        raise InspectionError("FFmpeg tool is not an executable regular file")
    return path.absolute()


def swa_encoded_resource(archive, entry) -> bytes:
    """Read encoded on-disk payload without pretending the SWA codec was decoded."""
    if (entry.tag != "snd " or entry.in_ils or entry.offset < 0 or
            "swa" not in archive.codecs[entry.compression_index].lower()):
        raise InspectionError("not a supported SWA-encoded Director sound")
    begin = archive.payload_start + entry.offset
    encoded = archive.data[begin:begin + entry.compressed_size]
    if len(encoded) != entry.compressed_size or not 20 <= len(encoded) <= MAX_ENCODED_BYTES:
        raise InspectionError("invalid encoded SWA stream length")
    return encoded


def decode_swa(encoded: bytes, ffmpeg: Path) -> tuple[bytes, dict]:
    """Attempt native SWA MPEG payload decoding using explicit FFmpeg.

    All output is a bounded, private temporary WAV file. This is a decoding
    check, not equivalence to the original Macromedia SWA Xtra processing.
    """
    if not 20 <= len(encoded) <= MAX_ENCODED_BYTES:
        raise InspectionError("encoded SWA resource violates size cap")
    executable = resolve_ffmpeg(ffmpeg)
    with tempfile.TemporaryDirectory(prefix="caserecomp-swa-") as tmp:
        root = Path(tmp)
        inp, out = root / "encoded.swa", root / "decoded.wav"
        inp.write_bytes(encoded)
        command = [str(executable), "-hide_banner", "-nostdin", "-v", "error", "-xerror",
                   "-protocol_whitelist", "file,pipe", "-f", "mp3", "-i", str(inp),
                   "-vn", "-sn", "-dn", "-c:a", "pcm_s16le", "-f", "wav",
                   "-fs", str(MAX_WAV_BYTES), str(out)]
        try:
            result = subprocess.run(command, cwd=root, capture_output=True, timeout=20, shell=False,
                                    env={"PATH": os.defpath}, check=False)
        except (OSError, subprocess.TimeoutExpired) as exc:
            raise InspectionError("FFmpeg failed to execute or timed out") from exc
        if result.returncode != 0 or not out.is_file():
            raise InspectionError("FFmpeg could not decode the SWA MPEG stream")
        size = out.stat().st_size
        if size < 44 or size >= MAX_WAV_BYTES:
            raise InspectionError("decoded SWA output is empty or exceeds cap")
        wav = out.read_bytes()
        meta = _wave_metadata(wav)
        if meta["frames"] == 0 or meta["sample_width"] != 2:
            raise InspectionError("SWA decoded WAV has no PCM16 frames")
        return wav, {**meta, "format": "swa-mpeg-to-wav-ffmpeg",
                     "historical_swa_equivalence_verified": False}
