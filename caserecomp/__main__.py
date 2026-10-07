"""Command-line interface: python -m caserecomp scan SOURCE --output report.json"""

import argparse
import sys
from pathlib import Path

from .inspector import InspectionError, report_json


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Read-only static inventory of legacy game files")
    parser.add_argument("command", choices=["scan"])
    parser.add_argument("source", type=Path, help="Local folder containing user-provided files")
    parser.add_argument("--output", type=Path, help="Write a JSON report (never the input files)")
    args = parser.parse_args(argv)
    try:
        result = report_json(args.source)
        if args.output:
            args.output.write_text(result, encoding="utf-8")
            print(f"Inventory written to {args.output}")
        else:
            print(result, end="")
        return 0
    except (InspectionError, OSError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
