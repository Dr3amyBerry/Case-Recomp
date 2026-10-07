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
    convert.add_argument("--decode-bitd", action="store_true",
                         help="Decode KEY*-owned verified BITD, including supported -102 indexed members")

    script_index = cmd.add_parser("lingo-index", help="Recover Lnam/LctX/Lscr handler names and bytecode boundaries")
    script_index.add_argument("source", type=Path)
    script_index.add_argument("--output", required=True, type=Path, help="Private create-only JSON report")
    script_index.add_argument("--show-names", action="store_true", help="Display original handler names in private report")

    compare = cmd.add_parser("compare-lingo", help="Compare two local Lingo .ls recovery directories, structurally")
    compare.add_argument("left", type=Path)
    compare.add_argument("right", type=Path)
    compare.add_argument("--output", required=True, type=Path, help="Create-only local JSON report")
    compare.add_argument("--redact-names", action="store_true", help="Use SHA-256 handler identifiers")
    compare.add_argument("--reference-movie", type=Path, help="Optional original local Director movie; cross-check handler names only")
    compare.add_argument("--left-encoding", choices=("utf-8", "mac_roman", "cp1252"), default="utf-8")
    compare.add_argument("--right-encoding", choices=("utf-8", "mac_roman", "cp1252"), default="utf-8")
    compare.add_argument("--assembly", action="store_true",
                         help="Also compare ProjectorRays .lasm to LibreShockwave .lsasm address/opcode sequences")

    check = cmd.add_parser("verify-export", help="Verify local conversion hashes/paths against manifest")
    check.add_argument("source", type=Path)

    external = cmd.add_parser("external-export", help="Explicitly invoke a separately installed recovery tool")
    external.add_argument("source", type=Path)
    external.add_argument("--output", type=Path, required=True)
    external.add_argument("--backend", choices=("projectorrays", "libreshockwave"), required=True)
    external.add_argument("--binary", type=Path, required=True)
    external.add_argument("--timeout", type=int, default=180)

    fidelity = cmd.add_parser("fidelity-file", help="Compare private decoded media without exposing game assets")
    fidelity.add_argument("left", type=Path)
    fidelity.add_argument("right", type=Path)
    fidelity.add_argument("--kind", choices=("png", "wav"), required=True)

    score = cmd.add_parser("score-structure", help="Private redacted Score/cast/timeline structural report")
    score.add_argument("source", type=Path, help="Local owned Director movie/projector")
    score.add_argument("--output", required=True, type=Path, help="Create-only private JSON report")

    scenario = cmd.add_parser("scenario-check", help="Validate public case-recomp-scenario v1 JSON")
    scenario.add_argument("source", type=Path)

    trace_plan = cmd.add_parser("trace-plan", help="Build a private redacted behavior trace plan from an owned Director movie")
    trace_plan.add_argument("source", type=Path)
    trace_plan.add_argument("--output", required=True, type=Path, help="Create-only private JSON plan")
    trace_plan.add_argument("--frame", action="append", type=int, default=[], help="Specific frame to observe; repeatable")

    trace_compare = cmd.add_parser("trace-compare", help="Compare a private trace plan with runtime observations")
    trace_compare.add_argument("plan", type=Path)
    trace_compare.add_argument("observation", type=Path)
    trace_compare.add_argument("--output", required=True, type=Path, help="Create-only private comparison JSON")

    trace_fixture = cmd.add_parser("trace-synthetic-fixture", help="Promote only a verified private comparison to a synthetic scenario-v1 fixture")
    trace_fixture.add_argument("comparison", type=Path)
    trace_fixture.add_argument("--output", required=True, type=Path)
    trace_fixture.add_argument("--id", default="verified-synthetic")

    slice_plan = cmd.add_parser("slice-plan", help="Build a private boot/menu/map/scene structural plan")
    slice_plan.add_argument("source", type=Path)
    slice_plan.add_argument("--output", required=True, type=Path)
    slice_plan.add_argument("--menu-label", required=True)
    slice_plan.add_argument("--map-label", required=True)
    slice_plan.add_argument("--scene-label", required=True)
    slice_plan.add_argument("--boot-frame", type=int, default=1)

    slice_compare = cmd.add_parser("slice-compare", help="Compare private slice structure with original-runtime observation")
    slice_compare.add_argument("spec", type=Path)
    slice_compare.add_argument("observation", type=Path)
    slice_compare.add_argument("--output", required=True, type=Path)

    slice_proof = cmd.add_parser("slice-flow-proof", help="Emit .crflow only from independently verified original-runtime evidence")
    slice_proof.add_argument("comparison", type=Path)
    slice_proof.add_argument("scenario", type=Path)
    slice_proof.add_argument("--spec", required=True, type=Path)
    slice_proof.add_argument("--observation", required=True, type=Path)
    slice_proof.add_argument("--package-id", required=True)
    slice_proof.add_argument("--scene-id", required=True)
    slice_proof.add_argument("--output", required=True, type=Path)

    capture_screen = cmd.add_parser("slice-capture-screen", help="Capture one private PNG, or a private desktop burst, from the native projector")
    capture_screen.add_argument("--output", required=True, type=Path,
                                help="New PNG normally; new directory with --burst")
    capture_screen.add_argument("--bbox", nargs=4, type=int, metavar=("LEFT","TOP","RIGHT","BOTTOM"))
    capture_screen.add_argument("--burst", action="store_true", help="Capture a numbered PNG burst plus capture-burst.json")
    capture_screen.add_argument("--count", type=int, default=240, help="Burst frame count (default: 240)")
    capture_screen.add_argument("--interval-ms", type=int, default=16, help="Burst sample interval in ms (default: 16)")

    capture_trial = cmd.add_parser("slice-capture-trial", help="Hash one controlled native-projector observation trial")
    capture_trial.add_argument("plan", type=Path)
    capture_trial.add_argument("capture_input", type=Path)
    capture_trial.add_argument("--runtime-binary", required=True, type=Path)
    capture_trial.add_argument("--output", required=True, type=Path)

    capture_finalize = cmd.add_parser("slice-capture-finalize", help="Require repeated visual consensus and emit slice observation")
    capture_finalize.add_argument("plan", type=Path)
    capture_finalize.add_argument("trials", nargs="+", type=Path)
    capture_finalize.add_argument("--timing-tolerance-ms", type=int, default=16)
    capture_finalize.add_argument("--output", required=True, type=Path)

    bundle = cmd.add_parser("private-content-package", help="Package a verified local export for app-private Android import")
    bundle.add_argument("conversion", type=Path, help="Verified convert-local output directory")
    bundle.add_argument("scenario", type=Path, help="Private or synthetic scenario-v1 JSON")
    bundle.add_argument("--output", required=True, type=Path, help="Create-only .crcontent ZIP")
    bundle.add_argument("--bindings", type=Path, help="Optional private scene/target/audio bindings JSON")
    bundle.add_argument("--trace-plan", type=Path, help="Optional private hash-only Phase 4B trace plan")

    bundle_verify = cmd.add_parser("private-content-verify", help="Verify a private Android content bundle without extracting it")
    bundle_verify.add_argument("source", type=Path)

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
                                   alpha_mode=args.alpha_mode, decode_swa=args.decode_swa, ffmpeg=args.ffmpeg,
                                   decode_bitd=args.decode_bitd)
        elif args.command == "lingo-index":
            from .lingo_index import index_movie
            from .director import exclusive_write
            from .pipeline import guard_destination
            guard_destination(args.source, args.output)
            record = index_movie(args.source, redact=not args.show_names)
            exclusive_write(args.output, (json.dumps(record, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode())
            print(json.dumps({k: v for k, v in record.items() if k != "handlers"}, sort_keys=True))
            return 0
        elif args.command == "compare-lingo":
            from .lingo_compare import compare_directories, compare_against_movie, compare_assembly_directories
            from .director import exclusive_write
            from .pipeline import guard_destination
            guard_destination(args.left, args.output)
            record = compare_directories(args.left, args.right, redact=args.redact_names,
                                         left_encoding=args.left_encoding, right_encoding=args.right_encoding)
            if args.assembly:
                record["assembly_crosscheck"] = compare_assembly_directories(
                    args.left, args.right, redact=args.redact_names,
                    left_encoding=args.left_encoding, right_encoding=args.right_encoding)
            if args.reference_movie:
                guard_destination(args.reference_movie, args.output)
                record["compiled_index_crosscheck"] = compare_against_movie(
                    args.left, args.right, args.reference_movie, redact=args.redact_names,
                    left_encoding=args.left_encoding, right_encoding=args.right_encoding)
            exclusive_write(args.output, (json.dumps(record, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode())
        elif args.command == "verify-export":
            from .pipeline import verify_export
            record = verify_export(args.source)
        elif args.command == "external-export":
            from .backends import run_backend
            record = run_backend(args.backend, args.source, args.output, args.binary, timeout=args.timeout)
        elif args.command == "fidelity-file":
            from .fidelity import compare_png, compare_wav
            record = compare_png(args.left, args.right) if args.kind == "png" else compare_wav(args.left, args.right)
        elif args.command == "score-structure":
            from .score import analyze_movie_structure
            from .director import exclusive_write
            from .pipeline import guard_destination
            guard_destination(args.source, args.output)
            record = analyze_movie_structure(args.source)
            exclusive_write(args.output, (json.dumps(record, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode())
            print(json.dumps({"schema_version": record["schema_version"], "stage": record["stage"],
                              "score": record["score"], "cast": record["cast"], "lingo": record["lingo"]},
                             ensure_ascii=False, sort_keys=True, indent=2))
            return 0
        elif args.command == "scenario-check":
            from .scenario import load_scenario, scenario_fingerprint
            document = load_scenario(args.source)
            record = {"format": document["format"], "version": document["version"],
                      "id": document["id"], "scene_count": len(document["scenes"]),
                      "event_count": len(document["events"]),
                      "fingerprint_sha256": scenario_fingerprint(document)}
        elif args.command == "trace-plan":
            from .trace import build_private_trace_plan
            from .director import exclusive_write
            from .pipeline import guard_destination
            guard_destination(args.source, args.output)
            record = build_private_trace_plan(args.source, args.frame or None)
            exclusive_write(args.output, (json.dumps(record, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode())
            print(json.dumps({"format": record["format"], "version": record["version"],
                              "kind": record["kind"], "steps": len(record["steps"])}, sort_keys=True))
            return 0
        elif args.command == "trace-compare":
            from .trace import compare_private_trace, load_json
            from .director import exclusive_write
            from .pipeline import guard_destination
            guard_destination(args.plan, args.output)
            guard_destination(args.observation, args.output)
            record = compare_private_trace(load_json(args.plan), load_json(args.observation))
            exclusive_write(args.output, (json.dumps(record, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode())
        elif args.command == "trace-synthetic-fixture":
            from .trace import load_json, synthetic_fixture_from_verified_comparison, write_synthetic_fixture
            from .pipeline import guard_destination
            guard_destination(args.comparison, args.output)
            document = synthetic_fixture_from_verified_comparison(load_json(args.comparison), args.id)
            write_synthetic_fixture(args.output, document)
            record = {"format": document["format"], "version": document["version"], "id": document["id"],
                      "scenes": len(document["scenes"]), "events": len(document["events"])}
        elif args.command == "slice-plan":
            from .vertical_slice import build_private_vertical_slice, write_json_create_only
            from .pipeline import guard_destination
            guard_destination(args.source, args.output)
            record = build_private_vertical_slice(args.source, menu_label=args.menu_label,
                                                  map_label=args.map_label, scene_label=args.scene_label,
                                                  boot_frame=args.boot_frame)
            write_json_create_only(args.output, record)
            print(json.dumps({"format": record["format"], "version": record["version"],
                              "stages": len(record["stages"]), "promotable_rules": record["promotable_rules"]}, sort_keys=True))
            return 0
        elif args.command == "slice-compare":
            from .vertical_slice import compare_vertical_slice, load_json, write_json_create_only
            from .pipeline import guard_destination
            guard_destination(args.spec, args.output); guard_destination(args.observation, args.output)
            record = compare_vertical_slice(load_json(args.spec), load_json(args.observation))
            write_json_create_only(args.output, record)
        elif args.command == "slice-flow-proof":
            from .vertical_slice import load_json, load_scenario_for_proof, verified_flow_from_evidence, write_json_create_only
            from .pipeline import guard_destination
            for source in (args.comparison, args.scenario, args.spec, args.observation):
                guard_destination(source, args.output)
            record = verified_flow_from_evidence(
                load_json(args.spec), load_json(args.observation), load_json(args.comparison),
                scenario=load_scenario_for_proof(args.scenario), scene_id=args.scene_id,
                package_id=args.package_id,
            )
            write_json_create_only(args.output, record)
        elif args.command == "slice-capture-screen":
            from .runtime_capture import capture_desktop_png, capture_desktop_png_burst
            if args.burst:
                record = capture_desktop_png_burst(
                    args.output, count=args.count, interval_ms=args.interval_ms,
                    bbox=tuple(args.bbox) if args.bbox else None,
                )
            else:
                record = capture_desktop_png(args.output, tuple(args.bbox) if args.bbox else None)
        elif args.command == "slice-capture-trial":
            from .runtime_capture import capture_trial_from_files
            from .vertical_slice import write_json_create_only
            record = capture_trial_from_files(args.plan, args.capture_input, args.runtime_binary)
            write_json_create_only(args.output, record)
        elif args.command == "slice-capture-finalize":
            from .runtime_capture import finalize_capture_from_files
            from .vertical_slice import write_json_create_only
            record = finalize_capture_from_files(args.plan, args.trials, timing_tolerance_ms=args.timing_tolerance_ms)
            write_json_create_only(args.output, record)
        elif args.command == "private-content-package":
            from .content_bundle import build_private_content_bundle
            record = build_private_content_bundle(args.conversion, args.scenario, args.output,
                                                  bindings_path=args.bindings, trace_plan_path=args.trace_plan)
        elif args.command == "private-content-verify":
            from .content_bundle import verify_private_content_bundle
            record = verify_private_content_bundle(args.source)
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
