"""Package authentic Mystery P.I.: The Vegas Heist resources into case-recomp-sda-content ZIP."""
import hashlib
import io
import json
import os
from pathlib import Path
import sys
import zipfile
from PIL import Image

root = Path(__file__).resolve().parent
sys.path.insert(0, str(root))

from runtime import Resources, parse_xui, local_name


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def build_package(resources_dll_path: Path, output_zip_path: Path, full_campaign: bool = False):
    res = Resources(resources_dll_path)
    output_zip_path.parent.mkdir(parents=True, exist_ok=True)

    files_data = {}

    # A catalog thumbnail of the authentic menu background, not a Windows
    # execution capture. Resolve its URI from XUI rather than a distributor file.
    tree = parse_xui(res.read("ENVS.MSE"))
    menu = next(node for node in tree.iter() if local_name(node.tag) == "mainmenu")
    background = next(node for node in menu if local_name(node.tag) == "image"
                      and node.attrib.get("x", "0") == "0"
                      and node.attrib.get("y", "0") == "0" and "tex" in node.attrib)
    textures = {node.attrib["id"]: node.attrib["uri"] for node in tree.iter()
                if local_name(node.tag) == "texture" and "id" in node.attrib and "uri" in node.attrib}
    cover = res.image(textures[background.attrib["tex"]]).copy()
    cover.thumbnail((320, 240), Image.Resampling.LANCZOS)
    cover_buf = io.BytesIO()
    cover.save(cover_buf, format="PNG")
    files_data["cover.png"] = cover_buf.getvalue()

    # Base strings
    if "STRINGS.TXT" in res.entries:
        files_data["STRINGS.TXT"] = res.read("STRINGS.TXT")

    if full_campaign:
        # Levels definition
        if "LEVELS_1.XUI" in res.entries:
            files_data["LEVELS_1.XUI"] = res.read("LEVELS_1.XUI")

        # Wordsearch text
        if "WORDSEARCH.TXT" in res.entries:
            files_data["WORDSEARCH.TXT"] = res.read("WORDSEARCH.TXT")

        # All scenes
        scenes = [k for k in res.entries if k.startswith("SCENE_") and k.endswith(".MSL")]
        # All bonus definitions
        bonuses = [k for k in res.entries if any(k.endswith(ext) for ext in (".TRG", ".TGL", ".WSG", ".JSW", ".MSE"))]

        xml_files_to_scan = scenes + bonuses
        for xname in xml_files_to_scan:
            raw = res.read(xname)
            files_data[xname] = raw
            txt_name = xname.rsplit(".", 1)[0] + ".TXT"
            if txt_name in res.entries and txt_name not in files_data:
                files_data[txt_name] = res.read(txt_name)

            try:
                tree = parse_xui(raw)
                for x in tree.iter():
                    if local_name(x.tag) == "texture":
                        uri = x.attrib.get("uri")
                        if uri and uri not in files_data:
                            entry_name = uri.upper()
                            if entry_name in res.entries:
                                files_data[uri] = res.read(entry_name)
            except Exception as e:
                print(f"Error parsing textures in {xname}: {e}")

        print(f"Full campaign: gathered {len(files_data)} files across {len(scenes)} scenes and {len(bonuses)} bonus games.")

    else:
        # Focused vault slice
        scene_name = "SCENE_VAULT.MSL"
        scene_txt = "SCENE_VAULT.TXT"
        if scene_txt in res.entries:
            files_data[scene_txt] = res.read(scene_txt)
        scene_raw = res.read(scene_name)
        files_data[scene_name] = scene_raw

        tree = parse_xui(scene_raw)
        textures = {x.attrib["id"]: x.attrib["uri"] for x in tree.iter() if local_name(x.tag) == "texture"}
        for tid, uri in textures.items():
            entry_name = uri.upper()
            if entry_name in res.entries:
                files_data[uri] = res.read(entry_name)

    # Build manifest
    files_meta = {}
    for path, data in files_data.items():
        files_meta[path] = [len(data), sha256(data)]

    manifest = {
        "format": "case-recomp-sda-content",
        "version": 1,
        "game_id": "vegas_heist",
        "cover": "cover.png",
        "files": files_meta
    }
    manifest_bytes = json.dumps(manifest, indent=2, sort_keys=True).encode("utf-8")
    files_data["manifest.json"] = manifest_bytes

    # Write zip
    with zipfile.ZipFile(output_zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for path, data in files_data.items():
            zf.writestr(path, data)

    print(f"Successfully generated package at {output_zip_path} ({output_zip_path.stat().st_size} bytes, {len(files_data)} files)")


if __name__ == "__main__":
    dll_path = Path("private/mystery-pi-vegas/game/Resources.dll")
    # Build focused vault package
    build_package(dll_path, Path("local-output/vegas_vault.zip"), full_campaign=False)
    # Build complete full campaign package
    build_package(dll_path, Path("local-output/vegas_full.zip"), full_campaign=True)
