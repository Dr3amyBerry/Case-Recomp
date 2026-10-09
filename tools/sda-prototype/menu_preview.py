"""Render initial XUI menu declarations; no player binding or native execution."""
import argparse
import json
from pathlib import Path
from PIL import Image
from runtime import Resources, parse_xui, local_name
from fonts import FontCatalog, parse_strings, resolve_caption


def render_menu(resources):
    tree = parse_xui(resources.read("ENVS.MSE"))
    menu = next(n for n in tree.iter() if local_name(n.tag) == "mainmenu")
    catalog = FontCatalog(resources)
    strings = parse_strings(resources.read("STRINGS.TXT"))
    stage = Image.new("RGBA", (800, 600), (0, 0, 0, 255))
    report = {"mode": "initial child declarations; parent activation assumed for preview",
              "pending": ["player binding", "logo animation", "image faders", "action routing"],
              "drawn": [], "skipped": [], "actions": []}
    for node in menu:
        a = node.attrib
        kind = local_name(node.tag)
        identity = a.get("id", kind)
        if "flags" in a and "render" not in a["flags"].split():
            report["skipped"].append({"id": identity, "reason": "no render flag"})
            continue
        x, y = int(a.get("x", 0)), int(a.get("y", 0))
        if kind == "image" and "tex" in a:
            stage.alpha_composite(resources.image(catalog.textures[a["tex"]]), (x, y))
        elif kind in ("allbutton", "quitbutton"):
            texture = resources.image(catalog.textures[a["texnormal"]])
            textures = [resources.image(catalog.textures[a[key]]) for key in
                        ("texnormal", "texhover", "texpushed", "texdisabled") if key in a]
            # 0048d003 derives unspecified bounds from maximum state texture sizes.
            width = int(a.get("w", max(im.width for im in textures)))
            height = int(a.get("h", max(im.height for im in textures)))
            stage.alpha_composite(texture.crop((0, 0, min(width, texture.width), min(height, texture.height))), (x, y))
            font = catalog.get(a["font"])
            # 004882e9: normal state's pushed-only offsets do not apply.
            font.draw(stage, resolve_caption(a.get("caption", ""), strings),
                      x - 1 + width // 2 + int(a.get("globalcaptionoffsetx", 0)),
                      y - 1 + height // 2 + int(a.get("globalcaptionoffsety", 0)),
                      halign=1, valign=2)
            report["actions"].append({"kind": kind, "value": a.get("value"), "rect": [x, y, width, height]})
        elif kind == "label" and a.get("caption"):
            halign = {"left": 0, "center": 1, "right": 2}[a.get("halign", "left")]
            valign = {"top": 0, "middle": 1, "bottom": 2}[a.get("valign", "middle")]
            width, height = int(a.get("w", 0)), int(a.get("h", 0))
            # 0048aad0 maps label top/middle/bottom to text modes 3/2/0.
            anchor_x = x if halign == 0 else x - 1 + (width // 2 if halign == 1 else width)
            anchor_y = y if valign == 0 else y - 1 + (height // 2 if valign == 1 else height)
            catalog.get(a["font"]).draw(stage, resolve_caption(a["caption"], strings),
                                        anchor_x, anchor_y, halign, (3, 2, 0)[valign])
        else:
            report["skipped"].append({"id": identity, "reason": "no static drawable or unsupported component"})
            continue
        report["drawn"].append(identity)
    return stage, report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    out = Path(args.output).resolve()
    root = Path(__file__).resolve().parents[2]
    if not any(out.is_relative_to(root / directory) for directory in ("private", "local-output")):
        parser.error("output must be inside private/ or local-output/")
    stage, report = render_menu(Resources(args.resources))
    out.parent.mkdir(parents=True, exist_ok=True)
    stage.save(out)
    out.with_suffix(".json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({"drawn": len(report["drawn"]), "skipped": len(report["skipped"]),
                      "actions_recovered": len(report["actions"])}))


if __name__ == "__main__":
    main()
