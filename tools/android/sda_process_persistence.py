"""Verify earned SDA checkpoints across actual Android process stops (WSA display2).
This is isolated persistence replay, not a new catalogue/campaign E2E run.
Only the experimental Case-Recomp package is stopped; host input is never used.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

PACKAGE = "org.rigorcore.caserecomp.synthetic.debug"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
TEST = "org.rigorcore.caserecomp.app.SdaPrivateProcessPersistenceInstrumentationTest"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--device", default="127.0.0.1:58526")
    parser.add_argument("--private-package", required=True, help="existing private ZIP path on Android")
    parser.add_argument("--checkpoint", action="append", required=True, help="case-label=host earned JSON path; scene also earns a new object")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    adb = [args.adb, "-s", args.device]

    def call(*command):
        result = subprocess.run(adb + list(command), text=True, capture_output=True, timeout=120)
        if result.returncode:
            raise RuntimeError(result.stderr or result.stdout)
        return result.stdout

    probe = call("shell", "run-as", PACKAGE, "sh", "-c", "'if test -f files/sda-process-qa/pending.json; then echo PENDING; fi'")
    if "PENDING" in probe:
        raise RuntimeError("Existing QA backup found; do not overwrite. Run the instrumentation cleanup stage first.")
    sources = []
    for item in args.checkpoint:
        label, path = item.split("=", 1)
        if not re.fullmatch(r"[a-z][a-z0-9-]{0,39}", label) or any(s[0] == label for s in sources):
            raise ValueError("unique safe case labels required")
        source = Path(path)
        json.loads(source.read_text(encoding="utf-8"))
        sources.append((label, source))
    results = []
    for label, source in sources:
        remote = "/data/local/tmp/process-qa-" + label + ".json"
        call("push", str(source), remote)
        call("shell", "chmod", "644", remote)

        def stage(name):
            text = call("shell", "am", "instrument", "-w", "-e", "class", TEST,
                        "-e", "processStage", name, "-e", "processCase", label,
                        "-e", "privateSdaPackage", args.private_package,
                        "-e", "earnedProcessCheckpoint", remote, "-e", "visualDisplayId", "2", RUNNER)
            (output / (label + "-" + name + ".log")).write_text(text, encoding="utf-8")
            if not re.search(r"OK\s*\(1 test\)", text) or "FAILURES" in text:
                raise RuntimeError(label + ": " + name + " failed; see retained log")

        try:
            stage("prepare")
            call("shell", "am", "force-stop", PACKAGE)
            stopped = call("shell", "sh", "-c", "'pidof " + PACKAGE + " || true'").strip()
            if stopped:
                raise RuntimeError("Package still running after force-stop")
            stage("verify")
            evidence = []
            for name in ("prepare", "verify"):
                target = output / (label + "-" + name + ".json")
                call("pull", "/sdcard/Android/data/" + PACKAGE + "/files/process-persistence/" + target.name, str(target))
                evidence.append(json.loads(target.read_text(encoding="utf-8")))
            if evidence[0]["pid"] == evidence[1]["pid"] or evidence[0]["state"] != evidence[1]["state"]:
                raise RuntimeError("Different PIDs and identical full states required")
            results.append({"case": label, "source": str(source.resolve()),
                            "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                            "preparePid": evidence[0]["pid"], "verifyPid": evidence[1]["pid"],
                            "phase": evidence[1]["state"]["phase"], "processAbsentAfterStop": True,
                            "fullStateEqual": True, "category": "earned-checkpoint process persistence replay"})
        finally:
            stage("cleanup")
            call("pull", "/sdcard/Android/data/" + PACKAGE + "/files/process-persistence/" + label + "-cleanup.json", str(output / (label + "-cleanup.json")))
        (output / "results.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    from html import escape
    rows = "".join("<tr><td>" + escape(r["case"]) + "</td><td>" + escape(r["phase"]) + "</td><td>" + str(r["preparePid"]) + " ? " + str(r["verifyPid"]) + "</td><td>" + "<a href='" + escape(r["case"]) + "-prepare.json'>Antes</a> / <a href='" + escape(r["case"]) + "-verify.json'>Despu?s</a> / <a href='" + escape(r["case"]) + "-cleanup.json'>Integridad</a></td></tr>" for r in results)
    (output / "report.html").write_text("<!doctype html><meta charset='utf-8'><title>SDA process persistence</title><style>body{font:16px sans-serif;max-width:1000px;margin:40px auto}td,th{padding:12px;border:1px solid #ddd}table{border-collapse:collapse}</style><h1>Persistencia real entre procesos Android</h1><p>Replay t?cnico de checkpoints ganados mediante interfaz; no es un nuevo E2E desde cat?logo. Force-stop, ausencia de PID y nuevo PID verificados. Estado completo, preferencias y archivos originales comprobados. WSA display2; sin entradas del escritorio.</p><table><tr><th>Caso</th><th>Fase</th><th>PIDs</th><th>Evidencia</th></tr>" + rows + "</table><p>Fidelidad Windows, otros dispositivos y estados no enumerados: NO VERIFICADO.</p>", encoding="utf-8")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
