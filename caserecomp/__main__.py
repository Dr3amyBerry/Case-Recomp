"""Offline CLI for bounded PE and Director XFIR analysis and local extraction."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .director import extract_movie, extract_resources, open_archive
from .inspector import InspectionError, report_json


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Read-only Director analysis and explicit local extraction")
    cmd = parser.add_subparsers(dest="command", required=True)

    scan = cmd.add_parser("scan", help="Read-only inventory with SHA-256 and PE/Director signatures")
    scan.add_argument("source", type=Path, help="Local input folder")
    scan.add_argument("--output", type=Path, help="Optional JSON report path (create-only)")

    summary = cmd.add_parser("director-map", help="Inspect XFIR/Afterburner resources without extracting")
    summary.add_argument("source", type=Path, help="Local .cct or Director projector .exe")
    summary.add_argument("--details", action="store_true", help="List resource IDs, sizes, tags and codecs")
    summary.add_argument("--links", action="store_true", help="Include structural KEY* cast-owner relationship counts")

    movie = cmd.add_parser("extract-movie", help="Extract an embedded Director movie locally (no execution)")
    movie.add_argument("source", type=Path, help="Locally owned Windows PE projector")
    movie.add_argument("--output", required=True, type=Path, help="New destination .dcr file")

    resources = cmd.add_parser("extract-resources", help="Extract selected raw Director resources locally")
    resources.add_argument("source", type=Path, help="Local .cct, .dcr or PE projector")
    resources.add_argument("--output", required=True, type=Path, help="New output directory")
    resources.add_argument("--tag", action="append", default=[], help="Exactly four-character resource tag; repeatable")
    resources.add_argument("--id", action="append", type=int, default=[], help="Resource ID; repeatable")

    verify = cmd.add_parser("verify-local", help="Validate local Director archives; no extracted output")
    verify.add_argument("source", type=Path, help="Local directory containing owned game data")

    convert = cmd.add_parser("convert-local", help="Local verified JPEG/PNG/PCM extraction; proprietary files stay private")
    convert.add_argument("source", type=Path)
    convert.add_argument("--output", type=Path, required=True)
    convert.add_argument("--image-format", choices=("jpg", "png"), default="jpg")
    convert.add_argument("--include-bytecode", action="store_true", help="Raw Lscr only; not Lingo source")
    convert.add_argument("--include-raw", action="store_true", help="Retain undecoded ALFA/BITD/XMED and metadata")
    convert.add_argument("--alpha-mode", choices=("off", "best-effort", "strict"), default="off",
                         help="Apply KEY*-linked PackBits ALFA to PNG; strict rejects invalid masks")
    convert.add_argument("--decode-swa", action="store_true", help="Opt-in local FFmpeg WAV decode of SWA MPEG payloads")
    convert.add_argument("--ffmpeg", type=Path, help="Trusted local FFmpeg executable, used only with --decode-swa")

    compare = cmd.add_parser("compare-lingo", help="Compare two local Lingo .ls recovery directories, structurally")
    compare.add_argument("left", type=Path)
    compare.add_argument("right", type=Path)
    compare.add_argument("--output", required=True, type=Path, help="Create-only local JSON report")
    compare.add_argument("--redact-names", action="store_true", help="Use SHA-256 handler identifiers")

    check = cmd.add_parser("verify-export", help="Verify local conversion hashes/paths against manifest")
    check.add_argument("source", type=Path)

    external = cmd.add_parser("external-export", help="Explicitly invoke a separately installed recovery tool")
    external.add_argument("source", type=Path)
    external.add_argument("--output", type=Path, required=True)
    external.add_argument("--backend", choices=("projectorrays", "libreshockwave"), required=True)
    external.add_argument("--binary", type=Path, required=True)
    external.add_argument("--timeout", type=int, default=180)

    args = parser.parse_args(argv)
    try:
        if args.command == "scan":
            output = report_json(args.source)
            if args.output:
                from .director import exclusive_write
                exclusive_write(args.output, output.encode("utf-8"))
                print(f"Inventory written to {args.output}")
            else:
                print(output, end="")
            return 0
        if args.command == "verify-local":
            from .verification import verify_directory
            record = verify_directory(args.source)
        elif args.command == "director-map":
            archive, offset = open_archive(args.source)
            record = {"source_name": args.source.name, "movie_offset": offset, **archive.summary(resource_details=args.details)}
            if args.links:
                from .relationships import CastRelationships
                record["relationships"] = CastRelationships(archive).summary(archive)
        elif args.command == "extract-movie":
            record = extract_movie(args.source, args.output)
        elif args.command == "convert-local":
            from .pipeline import convert_local
            record = convert_local(args.source, args.output, image_format=args.image_format,
                                   include_bytecode=args.include_bytecode, include_raw=args.include_raw,
                                   alpha_mode=args.alpha_mode, decode_swa=args.decode_swa, ffmpeg=args.ffmpeg)
        elif args.command == "compare-lingo":
            from .lingo_compare import compare_directories
            from .director import exclusive_write
            record = compare_directories(args.left, args.right, redact=args.redact_names)
            exclusive_write(args.output, (json.dumps(record, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode())
        elif args.command == "verify-export":
            from .pipeline import verify_export
            record = verify_export(args.source)
        elif args.command == "external-export":
            from .backends import run_backend
            record = run_backend(args.backend, args.source, args.output, args.binary, timeout=args.timeout)
        elif args.command == "extract-resources":
            if any(len(tag) != 4 for tag in args.tag):
                raise InspectionError("each --tag must be exactly four characters, e.g. 'Lscr'")
            record = extract_resources(args.source, args.output, tags=set(args.tag), ids=set(args.id))
        else:
            raise InspectionError("invalid subcommand")
        print(json.dumps(record, ensure_ascii=False, sort_keys=True, indent=2))
        return 0
    except (InspectionError, OSError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
