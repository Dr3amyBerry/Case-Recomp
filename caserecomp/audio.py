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


_MPEG_BITRATES = {  # kbps by (MPEG-1?, layer III) bitrate index
    True: (0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320),
    False: (0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160),
}
_MPEG_RATES = {3: (44100, 48000, 32000), 2: (22050, 24000, 16000), 0: (11025, 12000, 8000)}


def _mpeg_frame_size(data: bytes, pos: int) -> int:
    """Length of the MPEG audio layer III frame at pos, or 0 when no valid frame header is there."""
    if pos + 4 > len(data):
        return 0
    header = int.from_bytes(data[pos:pos + 4], "big")
    version, layer = (header >> 19) & 3, (header >> 17) & 3
    bitrate_index, rate_index, padding = (header >> 12) & 15, (header >> 10) & 3, (header >> 9) & 1
    if (header >> 21) != 0x7FF or version == 1 or layer != 1 or bitrate_index in (0, 15) or rate_index == 3:
        return 0
    bitrate = _MPEG_BITRATES[version == 3][bitrate_index] * 1000
    return (144 if version == 3 else 72) * bitrate // _MPEG_RATES[version][rate_index] + padding


def swa_mpeg_audio(encoded: bytes) -> bytes:
    """MPEG audio of a Shockwave Audio stream, playable as .mp3 without a decoder.

    SWA wraps MPEG layer III frames in a small header; the frames must form an
    unbroken chain to the end of the payload or the stream is rejected.
    """
    if not 20 <= len(encoded) <= MAX_ENCODED_BYTES:
        raise InspectionError("encoded SWA resource violates size cap")
    start = next((i for i in range(min(len(encoded) - 4, 512)) if _mpeg_frame_size(encoded, i)), -1)
    if start < 0:
        raise InspectionError("SWA stream holds no MPEG audio frames")
    pos = start
    while pos < len(encoded):
        size = _mpeg_frame_size(encoded, pos)
        if size <= 0:
            raise InspectionError("SWA MPEG frames are not contiguous")
        pos += size
    if pos != len(encoded):
        raise InspectionError("truncated SWA MPEG frame")
    return encoded[start:]


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
