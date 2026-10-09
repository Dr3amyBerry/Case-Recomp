"""Build a private dependency graph from existing Ghidra exports, without running a PE."""
import argparse
import collections
import csv
import json
from pathlib import Path


def read_rows(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--export", action="append", required=True)
    parser.add_argument("--roles", default=str(Path(__file__).with_name("roles.json")))
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    functions, edges = {}, set()
    completed = set()
    for directory in map(Path, args.export):
        for row in read_rows(directory / "functions.tsv"):
            functions[row["address"]] = row
        for row in read_rows(directory / "calls.tsv"):
            edges.add((row["caller"], row["callee"]))
        for row in read_rows(directory / "decompile-status.tsv"):
            if row["completed"] == "true":
                completed.add(row["address"])
        # Discovery exports write function inventory before new roots are defined.
        for file in directory.glob("????????.c"):
            functions.setdefault(file.stem, {"address": file.stem, "name": "FUN_" + file.stem})
    roles = json.loads(Path(args.roles).read_text(encoding="utf-8"))["roles"]
    seeds = {address for addresses in roles.values() for address in addresses}
    missing = sorted(seeds - completed)
    if missing:
        raise ValueError("role roots not decompiled: " + ", ".join(missing))
    children = collections.defaultdict(set)
    for caller, callee in edges:
        children[caller].add(callee)
    distances = {x: 0 for x in seeds}
    queue = collections.deque(seeds)
    while queue:
        caller = queue.popleft()
        if distances[caller] >= 3:
            continue
        for callee in children[caller]:
            if callee not in distances:
                distances[callee] = distances[caller] + 1
                queue.append(callee)
    nodes = set(distances)
    report = {
        "schema": 1, "roles": roles,
        "method": "three levels of recovered direct calls from reviewed roots",
        "limits": ["indirect calls are not closed", "prototypes may be wrong",
                   "missing Ghidra functions are not implied absent"],
        "nodes": [{"address": a, "depth": distances[a], "decompiled": a in completed,
                   "name": functions.get(a, {}).get("name", "unknown")} for a in sorted(nodes)],
        "direct_edges": [list(x) for x in sorted(edges) if x[0] in nodes and x[1] in nodes],
    }
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"reviewed_roots": len(seeds), "nodes": len(nodes),
                      "direct_edges": len(report["direct_edges"])}))


if __name__ == "__main__":
    main()
