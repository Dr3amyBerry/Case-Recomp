"""Headless inspection of original SDA font definitions and localized captions."""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image
from runtime import Resources
from fonts import FontCatalog, parse_strings, resolve_caption, utf16_units


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    out = Path(args.output)
    # Generated original media must remain outside versioned source directories.
    root = Path(__file__).resolve().parents[2]
    resolved = out.resolve()
    if not any(resolved.is_relative_to(root / directory) for directory in ("private", "local-output")):
        parser.error("output must be inside this repository's private/ or local-output/")
    out.mkdir(parents=True, exist_ok=True)
    resources = Resources(args.resources)
    catalog = FontCatalog(resources)
    strings = parse_strings(resources.read("STRINGS.TXT"))
    report = {"resources_sha256": hashlib.sha256(Path(args.resources).read_bytes()).hexdigest(),
              "mode": "static atlas recovery; native differential comparison pending",
              "localization_entries": len(strings), "fonts": []}
    for name, definition in catalog.definitions.items():
        uri = catalog.textures[definition["tex"]]
        entry = {"id": name, "texture": uri}
        if uri.upper() not in resources.entries:
            entry["status"] = "missing resource; no fallback"
        else:
            font = catalog.get(name)
            units = utf16_units(definition.get("characterset", ""))
            entry.update(status="decoded", size=list(font.image.size),
                         closed_runs=len(font.runs), charset_units=len(units),
                         duplicate_units=len(units) - len(set(units)),
                         count_matches=len(font.runs) == len(units))
        report["fonts"].append(entry)
    # Diagnostic sheet: top-left samples, not reconstructed label/button layout.
    samples = (("fnt_menuinfo", ("@ID_OPTIONS", "@ID_HELP")),
               ("fnt_dialogup_med", ("@ID_OPTIONS", "@ID_INSTRUCTIONS")),
               ("fnt_pdainfo_sml", ("@ID_sc_vault_aerosolcan", "@ID_sc_vault_axe")))
    strings.update(parse_strings(resources.read("SCENE_VAULT.TXT")))
    sheet = Image.new("RGBA", (800, 240), (45, 52, 62, 255))
    report["samples"] = []
    for row, (name, captions) in enumerate(samples):
        font = catalog.get(name)
        for column, caption in enumerate(captions):
            text = resolve_caption(caption, strings)
            if text.startswith("@"):
                raise ValueError(f"unresolved sample: {caption}")
            font.draw(sheet, text, 20 + column * 380, 20 + row * 75 + font.image.height)
        report["samples"].append({"font": name, "caption_ids": list(captions)})
    sheet.save(out / "original-fonts.png")
    (out / "atlas-scan.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({"definitions": len(report["fonts"]),
                      "decoded": sum(e["status"] == "decoded" for e in report["fonts"]),
                      "mismatches": [e["id"] for e in report["fonts"] if e.get("count_matches") is False],
                      "missing": [e["id"] for e in report["fonts"] if e["status"] != "decoded"],
                      "localization_entries": report["localization_entries"]}))


if __name__ == "__main__":
    main()
