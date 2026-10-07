#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, pathlib, zipfile

def sha256(path):
    h=hashlib.sha256()
    with open(path,"rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""): h.update(chunk)
    return h.hexdigest()

def apk_record(path, name, version_code):
    p=pathlib.Path(path)
    return {"name":name,"sha256":sha256(p),"bytes":p.stat().st_size,"version_code":version_code}

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--apk",required=True); p.add_argument("--baseline-apk",required=True)
    p.add_argument("--output",required=True); p.add_argument("--git-sha",required=True)
    p.add_argument("--commit-timestamp",required=True); p.add_argument("--first-build-sha",required=True)
    a=p.parse_args()
    apk=pathlib.Path(a.apk); baseline=pathlib.Path(a.baseline_apk)
    out=pathlib.Path(a.output); out.mkdir(parents=True,exist_ok=True)
    digest=sha256(apk); size=apk.stat().st_size
    with zipfile.ZipFile(apk) as z:
        entries=sorted((i.filename,i.file_size,i.CRC) for i in z.infolist())
    entry_digest=hashlib.sha256(json.dumps(entries,separators=(",",":")).encode()).hexdigest()
    reproducible=a.first_build_sha==digest
    baseline_record=apk_record(baseline,"case-recomp-synthetic-baseline.apk",7)
    candidate_record=apk_record(apk,"case-recomp-synthetic-debug.apk",8)
    provenance={
      "format":"case-recomp-build-provenance","version":2,
      "subject":[candidate_record],
      "builder":{"id":"github-actions/android.yml:package-debug"},
      "invocation":{"variant":"debug","release_variant_enabled":False,"production_signing_configured":False},
      "materials":[{"uri":"git+https://github.com/Dr3amyBerry/Case-Recomp","digest":{"sha1":a.git_sha}}],
      "git_sha":a.git_sha,"commit_timestamp":a.commit_timestamp,
      "artifact":{**candidate_record,"zip_entry_manifest_sha256":entry_digest},
      "upgrade_baseline":baseline_record,
      "build":{"gradle":"9.6.0","agp":"9.4.1","jdk":"17","compile_sdk":36,"target_sdk":36,"min_sdk":26},
      "reproducible_same_revision":reproducible,
      "contains_original_game_assets":False
    }
    app_version="0.5.0-phase8-debug-debug"\n    app_ref=f"pkg:generic/case-recomp-synthetic-debug@{app_version}?type=apk"
    engine_ref="pkg:generic/case-recomp-engine@0.8-phase8"
    sdk_ref="pkg:generic/android-sdk@36"
    sbom={
      "bomFormat":"CycloneDX","specVersion":"1.6","version":1,
      "metadata":{"timestamp":a.commit_timestamp,
        "tools":{"components":[
          {"type":"application","name":"Gradle","version":"9.6.0"},
          {"type":"application","name":"Android Gradle Plugin","version":"9.4.1"},
          {"type":"application","name":"JDK","version":"17"}]},
        "component":{"bom-ref":app_ref,"type":"application","name":"case-recomp-synthetic-debug","version":app_version,
          "hashes":[{"alg":"SHA-256","content":digest}],
          "properties":[{"name":"caserecomp:containsOriginalGameAssets","value":"false"},
                        {"name":"caserecomp:releaseVariantEnabled","value":"false"}]}},
      "components":[
        {"bom-ref":engine_ref,"type":"library","name":"case-recomp-engine","version":"0.8-phase8","scope":"required",
         "properties":[{"name":"caserecomp:origin","value":"repository-source"}]},
        {"bom-ref":sdk_ref,"type":"framework","name":"Android SDK","version":"36","scope":"required",
         "properties":[{"name":"caserecomp:minSdk","value":"26"},{"name":"caserecomp:targetSdk","value":"36"}]}
      ],
      "dependencies":[{"ref":app_ref,"dependsOn":[engine_ref,sdk_ref]},{"ref":engine_ref,"dependsOn":[]},{"ref":sdk_ref,"dependsOn":[]}]
    }
    report={"format":"case-recomp-phase8-build-report","version":2,"apk_sha256":digest,"apk_bytes":size,
            "baseline_apk_sha256":baseline_record["sha256"],"baseline_version_code":7,"candidate_version_code":8,
            "reproducible_same_revision":reproducible,
            "preliminary_budgets":{"apk_bytes_max":15*1024*1024,"cold_start_ms_max":5000,"pss_kb_max":262144,"render_janky_percent_max":25.0},
            "notes":["Budgets are preliminary emulator guardrails, not production device targets.",
                     "OS backup remains disabled; external QA backs up only the synthetic save slot.",
                     "Upgrade QA uses the same synthetic source with versionCode 7 -> 8; it validates Android upgrade mechanics, not historical app compatibility."]}
    for name,obj in [("provenance.json",provenance),("sbom.cdx.json",sbom),("build-report.json",report)]:
        (out/name).write_text(json.dumps(obj,sort_keys=True,indent=2)+"\n",encoding="utf-8")
    (out/"case-recomp-synthetic-debug.apk").write_bytes(apk.read_bytes())
    (out/"case-recomp-synthetic-baseline.apk").write_bytes(baseline.read_bytes())
    (out/"SHA256SUMS").write_text(
        f'{candidate_record["sha256"]}  case-recomp-synthetic-debug.apk\n'
        f'{baseline_record["sha256"]}  case-recomp-synthetic-baseline.apk\n',encoding="utf-8")
    if size>report["preliminary_budgets"]["apk_bytes_max"]: raise SystemExit("APK size exceeds preliminary budget")
    if not reproducible: raise SystemExit("same-revision debug APK is not byte reproducible")

if __name__=="__main__": main()
