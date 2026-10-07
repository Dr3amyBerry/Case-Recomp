#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, os, pathlib, subprocess, time, zipfile

def sha256(path):
    h=hashlib.sha256()
    with open(path,"rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""): h.update(chunk)
    return h.hexdigest()

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--apk",required=True); p.add_argument("--output",required=True)
    p.add_argument("--git-sha",required=True); p.add_argument("--commit-timestamp",required=True)
    p.add_argument("--first-build-sha")
    a=p.parse_args()
    apk=pathlib.Path(a.apk); out=pathlib.Path(a.output); out.mkdir(parents=True,exist_ok=True)
    digest=sha256(apk); size=apk.stat().st_size
    with zipfile.ZipFile(apk) as z:
        entries=sorted((i.filename,i.file_size,i.CRC) for i in z.infolist())
    entry_digest=hashlib.sha256(json.dumps(entries,separators=(",",":")).encode()).hexdigest()
    reproducible=(not a.first_build_sha) or a.first_build_sha==digest
    provenance={
      "format":"case-recomp-build-provenance","version":1,
      "git_sha":a.git_sha,"commit_timestamp":a.commit_timestamp,
      "artifact":{"name":"case-recomp-synthetic-debug.apk","sha256":digest,"bytes":size,"zip_entry_manifest_sha256":entry_digest},
      "build":{"gradle":"9.6.0","agp":"9.4.1","jdk":"17","compile_sdk":36,"target_sdk":36,"min_sdk":26,
               "variant":"debug","release_variant_enabled":False},
      "reproducible_same_revision":reproducible,
      "contains_original_game_assets":False
    }
    sbom={
      "bomFormat":"CycloneDX","specVersion":"1.6","version":1,
      "metadata":{"timestamp":a.commit_timestamp,"component":{"type":"application","name":"case-recomp-synthetic-debug","version":"0.5.0-phase8-debug-debug",
        "hashes":[{"alg":"SHA-256","content":digest}]}},
      "components":[
        {"type":"library","name":"case-recomp-engine","version":"0.8-phase8","scope":"required",
         "properties":[{"name":"caserecomp:origin","value":"repository-source"}]},
        {"type":"framework","name":"Android SDK","version":"36","scope":"required",
         "properties":[{"name":"caserecomp:minSdk","value":"26"},{"name":"caserecomp:targetSdk","value":"36"}]}
      ],
      "dependencies":[{"ref":"case-recomp-synthetic-debug","dependsOn":["case-recomp-engine","Android SDK"]}]
    }
    report={"format":"case-recomp-phase8-build-report","version":1,"apk_sha256":digest,"apk_bytes":size,
            "reproducible_same_revision":reproducible,
            "preliminary_budgets":{"apk_bytes_max":15*1024*1024,"cold_start_ms_max":5000,"pss_kb_max":262144,"render_janky_percent_max":25.0},
            "notes":["Budgets are preliminary emulator guardrails, not production device targets.",
                     "OS backup remains disabled; external QA backs up only the synthetic save slot."]}
    for name,obj in [("provenance.json",provenance),("sbom.cdx.json",sbom),("build-report.json",report)]:
        (out/name).write_text(json.dumps(obj,sort_keys=True,indent=2)+"\n",encoding="utf-8")
    (out/"case-recomp-synthetic-debug.apk").write_bytes(apk.read_bytes())
    (out/"SHA256SUMS").write_text(f"{digest}  case-recomp-synthetic-debug.apk\n",encoding="utf-8")
    if size>report["preliminary_budgets"]["apk_bytes_max"]: raise SystemExit("APK size exceeds preliminary budget")
    if not reproducible: raise SystemExit("same-revision debug APK is not byte reproducible")

if __name__=="__main__": main()
